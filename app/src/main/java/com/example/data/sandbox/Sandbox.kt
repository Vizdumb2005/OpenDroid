package com.example.data.sandbox

import android.content.Context
import android.os.Build
import android.system.Os
import com.example.service.OpenDroidService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Lifecycle of the on-device Ubuntu userland. */
typealias SandboxState = Sandbox.InstallState

data class ExecResult(val exitCode: Int, val output: String)

/** Resource caps applied to every process started inside the sandbox. */
data class SandboxLimits(
  val ramMb: Int = 2048,
  val cpuNice: Int = 0,
)

/**
 * A real Ubuntu Base 26.04.1 root filesystem executed in user space through proot.
 *
 * Supports both arm64-v8a physical devices and x86_64 emulators natively.
 * proot, its loader and libtalloc ship as jniLibs. There is no hypervisor:
 * processes run natively, and "root" inside the sandbox is proot's fake-root (uid 0) mapping.
 */
class Sandbox(private val context: Context) {

  sealed interface InstallState {
    data object NotInstalled : InstallState
    data class Downloading(val progressPercent: Int) : InstallState
    data object Verifying : InstallState
    data object Extracting : InstallState
    data object Provisioning : InstallState
    data object Ready : InstallState
    data class Error(val message: String) : InstallState
  }

  data class ArchInfo(
    val tarball: String,
    val expectedSha256: String,
    val opencodeArchive: String,
    val opencodeUrl: String,
    val archName: String,
  )

  companion object {
    const val UBUNTU_VERSION = "26.04.1"
    const val UBUNTU_BASE_URL =
      "https://cdimage.ubuntu.com/ubuntu-base/releases/$UBUNTU_VERSION/release"
    const val TARBALL = "ubuntu-base-$UBUNTU_VERSION-base-arm64.tar.gz"
    const val UBUNTU_URL = "$UBUNTU_BASE_URL/$TARBALL"
    const val EXPECTED_SHA256 = "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
    const val GUEST_HOME = "/root"
    const val GUEST_WORKSPACE = "/root/workspace"
    const val GUEST_SHARED = "/mnt/shared"
    private const val READY_MARKER = ".opendroid-provisioned"

    fun getArchInfo(): ArchInfo {
      val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
      return if (primaryAbi.contains("x86_64") || primaryAbi.contains("amd64")) {
        ArchInfo(
          tarball = "ubuntu-base-$UBUNTU_VERSION-base-amd64.tar.gz",
          expectedSha256 = "a496a960472ce474a59590b8987d3a1135d3cbef1991f3b1abe8cacfea8bf85a",
          opencodeArchive = "opencode-linux-x64.tar.gz",
          opencodeUrl = "https://github.com/sst/opencode/releases/latest/download/opencode-linux-x64.tar.gz",
          archName = "x86_64"
        )
      } else {
        ArchInfo(
          tarball = "ubuntu-base-$UBUNTU_VERSION-base-arm64.tar.gz",
          expectedSha256 = "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd",
          opencodeArchive = "opencode-linux-arm64.tar.gz",
          opencodeUrl = "https://github.com/sst/opencode/releases/latest/download/opencode-linux-arm64.tar.gz",
          archName = "arm64"
        )
      }
    }
  }

  val root = File(context.filesDir, "sandbox")
  val rootfs = File(root, "rootfs")
  val sharedDir = File(root, "shared")
  private val libDir = File(root, "lib")
  private val tmpDir = File(root, "tmp")
  private val downloadsDir = File(root, "downloads")
  private val nativeDir get() = context.applicationInfo.nativeLibraryDir

  @Volatile var limits = SandboxLimits()

  private val _state = MutableStateFlow<InstallState>(initialState())
  val state: StateFlow<InstallState> = _state.asStateFlow()

  private val _installLog = MutableStateFlow<List<String>>(emptyList())
  val installLog: StateFlow<List<String>> = _installLog.asStateFlow()

  /** Set after a proot start failure to retry without seccomp acceleration (some kernels need it). */
  @Volatile var noSeccomp = false

  private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile private var isInstalling = false

  // HTTP/1.1 with clean connection pool prevents stale socket aborts and HTTP/2 stream resets
  private val http = OkHttpClient.Builder()
    .protocols(listOf(Protocol.HTTP_1_1))
    .retryOnConnectionFailure(true)
    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private fun initialState(): InstallState =
    if (File(rootfs, "root/$READY_MARKER").exists() && File(rootfs, "bin/bash").exists()) {
      InstallState.Ready
    } else {
      InstallState.NotInstalled
    }

  val isReady: Boolean get() = _state.value is InstallState.Ready
  fun isInstalled(): Boolean = isReady

  fun startInstall(): Job = synchronized(this) {
    if (isReady || isInstalling) {
      return Job().apply { complete() }
    }
    isInstalling = true
    return installScope.launch {
      try {
        OpenDroidService.start(context)
        install()
      } finally {
        isInstalling = false
      }
    }
  }

  fun guestToHost(guestPath: String): File = File(rootfs, guestPath.trimStart('/'))

  private fun log(line: String) {
    _installLog.value = (_installLog.value + line).takeLast(400)
  }

  // ------------------------------------------------------------------------------------------
  // Process launching
  // ------------------------------------------------------------------------------------------

  /** Builds the proot command line for running [guestCommand] under bash inside the rootfs. */
  fun buildProcess(
    guestCommand: String,
    cwd: String = GUEST_WORKSPACE,
    extraEnv: Map<String, String> = emptyMap(),
    binds: List<String> = emptyList(),
  ): ProcessBuilder {
    ensureHostDirs()
    val args = mutableListOf(
      File(nativeDir, "libproot.so").absolutePath,
      "--link2symlink",
      "-0",
      "--kill-on-exit",
      "-r", rootfs.absolutePath,
      "-b", "/dev",
      "-b", "/dev/urandom:/dev/random",
      "-b", "/proc",
      "-b", "/sys",
      "-b", "${sharedDir.absolutePath}:$GUEST_SHARED",
    )
    binds.forEach { args += listOf("-b", it) }
    args += listOf("-w", cwd, "/usr/bin/env", "-i")
    val env = linkedMapOf(
      "HOME" to GUEST_HOME,
      "USER" to "root",
      "LOGNAME" to "root",
      "SHELL" to "/bin/bash",
      "TERM" to "xterm-256color",
      "LANG" to "C.UTF-8",
      "DEBIAN_FRONTEND" to "noninteractive",
      "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
      "TMPDIR" to "/tmp",
      "NODE_OPTIONS" to "--max-old-space-size=${(limits.ramMb * 0.6).toInt().coerceAtLeast(256)}",
      "MALLOC_ARENA_MAX" to "2",
    )
    env.putAll(extraEnv)
    env.forEach { (k, v) -> args += "$k=$v" }
    args += listOf("/bin/bash", "-c", guestCommand)

    val pb = ProcessBuilder(args)
    pb.directory(root)
    pb.redirectErrorStream(true)
    val e = pb.environment()
    e["PROOT_LOADER"] = File(nativeDir, "libproot-loader.so").absolutePath
    e["PROOT_TMP_DIR"] = tmpDir.absolutePath
    e["LD_LIBRARY_PATH"] = "${libDir.absolutePath}:$nativeDir"
    if (noSeccomp) e["PROOT_NO_SECCOMP"] = "1"
    return pb
  }

  /** Runs a command to completion, streaming merged stdout/stderr line by line. */
  suspend fun exec(
    command: String,
    cwd: String = GUEST_WORKSPACE,
    timeoutMs: Long = 900_000,
    extraEnv: Map<String, String> = emptyMap(),
    onLine: (String) -> Unit = {},
  ): ExecResult = withContext(Dispatchers.IO) {
    val proc = buildProcess(command, cwd, extraEnv).start()
    val out = StringBuilder()
    val reader = Thread {
      proc.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { l ->
          synchronized(out) { out.appendLine(l) }
          onLine(l)
        }
      }
    }.also { it.isDaemon = true; it.start() }
    val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!finished) {
      proc.destroyForcibly()
      reader.join(1000)
      return@withContext ExecResult(124, synchronized(out) { out.toString() } + "\n[timed out after ${timeoutMs / 1000}s]")
    }
    reader.join(2000)
    ExecResult(proc.exitValue(), synchronized(out) { out.toString() })
  }

  // ------------------------------------------------------------------------------------------
  // Installation & Provisioning
  // ------------------------------------------------------------------------------------------

  private fun ensureHostDirs() {
    listOf(root, rootfs, sharedDir, libDir, tmpDir, downloadsDir).forEach { it.mkdirs() }
    val link = File(libDir, "libtalloc.so.2")
    val target = File(nativeDir, "libtalloc.so").absolutePath
    try {
      if (link.exists() || link.isSymlink()) link.delete()
      Os.symlink(target, link.absolutePath)
    } catch (_: Exception) {
      File(nativeDir, "libtalloc.so").copyTo(link, overwrite = true)
    }
  }

  private fun File.isSymlink(): Boolean = try {
    Os.lstat(absolutePath); java.nio.file.Files.isSymbolicLink(toPath())
  } catch (_: Exception) { false }

  /** Downloads, verifies, extracts and provisions the sandbox. Safe to resume. */
  suspend fun install() = withContext(Dispatchers.IO) {
    try {
      ensureHostDirs()
      val arch = getArchInfo()
      val marker = File(rootfs, "root/$READY_MARKER")

      // Clean recovery: if previous install was interrupted mid-extraction, clear incomplete rootfs
      if (!marker.exists() && (!File(rootfs, "usr/bin/env").exists() || !File(rootfs, "bin/bash").exists())) {
        if (rootfs.exists()) {
          log("Preparing clean rootfs directory")
          rootfs.deleteRecursively()
          rootfs.mkdirs()
        }
      }

      if (!File(rootfs, "usr/bin/env").exists() || !File(rootfs, "bin/bash").exists()) {
        val tar = File(downloadsDir, arch.tarball)
        downloadUbuntuBase(tar, arch)
        extract(tar)
        tar.delete()
      }

      // Pre-extract official OpenCode binary into /usr/local/bin
      installOpenCodeBinary(arch)

      configureGuest()
      provision()
      marker.parentFile?.mkdirs()
      marker.writeText(System.currentTimeMillis().toString())
      _state.value = InstallState.Ready
      OpenDroidService.updateStatus(context, "OpenDroid: Linux Sandbox Ready")
      log("OpenDroid environment initialized successfully")
    } catch (e: Exception) {
      log("ERROR: ${e.message}")
      _state.value = InstallState.Error(e.message ?: e.javaClass.simpleName)
      OpenDroidService.updateStatus(context, "OpenDroid Setup: Error (${e.message})")
    }
  }

  /** Wipes the rootfs so the next [install] starts clean. */
  suspend fun reset() = withContext(Dispatchers.IO) {
    rootfs.deleteRecursively()
    _installLog.value = emptyList()
    _state.value = InstallState.NotInstalled
  }

  private fun downloadUbuntuBase(dest: File, arch: ArchInfo) {
    _state.value = InstallState.Verifying
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Checking ${arch.tarball}...")

    var expected = arch.expectedSha256
    try {
      val sumsResp = http.newCall(Request.Builder().url("$UBUNTU_BASE_URL/SHA256SUMS").build()).execute()
      sumsResp.use {
        if (it.isSuccessful) {
          val text = it.body?.string().orEmpty()
          val found = text.lineSequence().firstOrNull { l -> l.trim().endsWith(arch.tarball) }
            ?.trim()?.substringBefore(' ')
          if (!found.isNullOrBlank()) expected = found
        }
      }
    } catch (_: Exception) {
      log("Offline or network timeout checking SHA256SUMS, using verified release hash")
    }

    if (dest.exists() && sha256(dest) == expected) {
      log("Using verified cached ${arch.tarball}")
      return
    }

    log("Downloading ${arch.tarball} (${arch.archName})...")
    downloadFile("$UBUNTU_BASE_URL/${arch.tarball}", dest, "Ubuntu Base", expectedTotalBytes = 35_000_000L)

    _state.value = InstallState.Verifying
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Verifying Checksum...")
    val actual = sha256(dest)
    if (actual != expected) {
      dest.delete()
      throw IOException("Checksum mismatch for ${arch.tarball}")
    }
    log("Checksum verified cleanly")
  }

  private fun installOpenCodeBinary(arch: ArchInfo) {
    val opencodeBin = guestToHost("/usr/local/bin/opencode")
    if (opencodeBin.exists() && opencodeBin.length() > 5_000_000) {
      log("OpenCode binary already present in /usr/local/bin/opencode")
      return
    }

    val dest = File(downloadsDir, arch.opencodeArchive)
    log("Downloading official OpenCode engine (${arch.archName})...")
    downloadFile(arch.opencodeUrl, dest, "OpenCode Core", expectedTotalBytes = 78_000_000L)

    log("Extracting OpenCode to /usr/local/bin/opencode...")
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Unpacking OpenCode...")
    opencodeBin.parentFile?.mkdirs()
    TarArchiveInputStream(GZIPInputStream(BufferedInputStream(dest.inputStream(), 1 shl 16))).use { tin ->
      var e = tin.nextEntry as TarArchiveEntry?
      while (e != null) {
        if (e.name.endsWith("opencode") || e.name == "opencode") {
          FileOutputStream(opencodeBin).use { out -> tin.copyTo(out, 64 * 1024) }
          safeChmod(opencodeBin, 0b111_101_101)
          break
        }
        e = tin.nextEntry as TarArchiveEntry?
      }
    }
    dest.delete()
    log("OpenCode official binary deployed")
  }

  /**
   * Resilient, chunked, resumable download with HTTP 206 Range support,
   * exponential backoff retries, and rate-limited notification updates.
   */
  private fun downloadFile(url: String, dest: File, label: String, expectedTotalBytes: Long? = null) {
    val partFile = File(dest.parentFile, dest.name + ".part")
    val maxRetries = 6
    var attempt = 0
    var lastNotifTime = 0L
    var lastLoggedPct = -1

    while (attempt < maxRetries) {
      attempt++
      var existingBytes = if (partFile.exists()) partFile.length() else 0L

      try {
        val reqBuilder = Request.Builder()
          .url(url)
          .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) OpenDroid/1.0")

        if (existingBytes > 0) {
          reqBuilder.header("Range", "bytes=$existingBytes-")
          log("Resuming $label from byte $existingBytes (attempt $attempt/$maxRetries)...")
        } else {
          log("Downloading $label (attempt $attempt/$maxRetries)...")
        }

        val response = http.newCall(reqBuilder.build()).execute()
        response.use { resp ->
          val isPartial = resp.code == 206
          val isOk = resp.code == 200

          if (!isPartial && !isOk) {
            throw IOException("Download failed with HTTP ${resp.code} from $url")
          }

          if (isOk && existingBytes > 0) {
            log("Server returned full stream (HTTP 200); resetting $label download from offset 0")
            partFile.delete()
            existingBytes = 0L
          }

          val body = resp.body ?: throw IOException("Empty body from $url")
          val contentLength = body.contentLength()
          val totalBytes = if (isPartial) {
            existingBytes + contentLength
          } else if (contentLength > 0) {
            contentLength
          } else {
            expectedTotalBytes ?: -1L
          }

          val append = isPartial && existingBytes > 0
          FileOutputStream(partFile, append).use { fileOut ->
            body.byteStream().use { streamIn ->
              val buffer = ByteArray(64 * 1024)
              var bytesRead: Int
              var downloadedBytes = existingBytes

              while (streamIn.read(buffer).also { bytesRead = it } != -1) {
                fileOut.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                if (totalBytes > 0) {
                  val pct = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                  _state.value = InstallState.Downloading(pct)

                  val now = System.currentTimeMillis()
                  if (now - lastNotifTime >= 1500L || pct == 100) {
                    lastNotifTime = now
                    OpenDroidService.updateStatus(context, "OpenDroid Setup: Downloading $label ($pct%)")
                  }

                  val step = pct / 25
                  if (step > lastLoggedPct) {
                    lastLoggedPct = step
                    log("  ...$label $pct% (${downloadedBytes / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB)")
                  }
                }
              }
              fileOut.flush()
            }
          }

          if (totalBytes > 0 && partFile.length() < totalBytes) {
            throw IOException("Stream closed early: received ${partFile.length()} of $totalBytes bytes")
          }

          if (dest.exists()) dest.delete()
          if (!partFile.renameTo(dest)) {
            partFile.copyTo(dest, overwrite = true)
            partFile.delete()
          }
          log("$label download complete (${dest.length()} bytes)")
          return
        }
      } catch (e: Exception) {
        log("Network interrupted during $label download: ${e.message ?: e.javaClass.simpleName}")
        if (attempt >= maxRetries) {
          throw IOException("Failed to download $label after $maxRetries attempts: ${e.message}", e)
        }
        val backoffMs = (attempt * 1500L).coerceAtMost(6000L)
        log("Resuming in ${backoffMs / 1000}s...")
        Thread.sleep(backoffMs)
      }
    }
  }

  private fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { s ->
      val buf = ByteArray(64 * 1024)
      while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  private fun extract(tar: File) {
    _state.value = InstallState.Extracting
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Extracting sandbox rootfs...")
    log("Extracting rootfs filesystem...")
    rootfs.mkdirs()
    var count = 0
    val deferredLinks = mutableListOf<TarArchiveEntry>()
    TarArchiveInputStream(GZIPInputStream(BufferedInputStream(tar.inputStream(), 1 shl 16))).use { tin ->
      var e = tin.nextEntry as TarArchiveEntry?
      while (e != null) {
        val rel = e.name.removePrefix("./").trimEnd('/')
        if (rel.isNotEmpty()) {
          val out = File(rootfs, rel)
          if (!out.canonicalPath.startsWith(rootfs.canonicalPath)) throw IOException("Unsafe path in archive: ${e.name}")
          when {
            e.isDirectory -> {
              out.mkdirs()
              safeChmod(out, e.mode or 0b111_000_000)
            }
            e.isSymbolicLink -> {
              out.parentFile?.mkdirs()
              if (out.exists() || out.isSymlink()) out.delete()
              Os.symlink(e.linkName, out.absolutePath)
            }
            e.isLink -> deferredLinks += e
            e.isFile -> {
              out.parentFile?.mkdirs()
              if (out.exists() || out.isSymlink()) out.delete()
              FileOutputStream(out).use { tin.copyTo(it, 64 * 1024) }
              safeChmod(out, e.mode or 0b110_000_000)
            }
            else -> Unit
          }
        }
        if (++count % 1000 == 0) log("  ...$count entries extracted")
        e = tin.nextEntry as TarArchiveEntry?
      }
    }
    // Hard links become copies
    for (l in deferredLinks) {
      val dst = File(rootfs, l.name.removePrefix("./"))
      val src = File(rootfs, l.linkName.removePrefix("./"))
      if (src.exists()) {
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        safeChmod(dst, l.mode or 0b110_000_000)
      }
    }
    log("Extracted $count filesystem entries")
  }

  private fun safeChmod(f: File, mode: Int) {
    try { Os.chmod(f.absolutePath, mode and 0b111_111_111_111) } catch (_: Exception) {}
  }

  /** Writes the small amount of guest configuration proot needs (DNS, apt, user dirs). */
  private fun configureGuest() {
    _state.value = InstallState.Provisioning
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Configuring sandbox...")
    fun put(path: String, text: String) {
      val f = guestToHost(path)
      f.parentFile?.mkdirs()
      if (f.exists() || f.isSymlink()) f.delete()
      f.writeText(text)
    }
    put("/etc/resolv.conf", dnsServers().joinToString("\n") { "nameserver $it" } + "\n")
    put("/etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
    put("/etc/hostname", "opendroid\n")
    put(
      "/etc/apt/apt.conf.d/99opendroid",
      """
      APT::Sandbox::User "root";
      Dpkg::Options { "--force-unsafe-io"; };
      Acquire::Retries "5";
      Acquire::ForceIPv4 "true";
      Acquire::http::Timeout "30";
      Acquire::https::Timeout "30";
      Acquire::http::Pipeline-Depth "0";
      """.trimIndent() + "\n"
    )
    put("/etc/dpkg/dpkg.cfg.d/opendroid", "force-unsafe-io\n")
    guestToHost("/root/workspace").mkdirs()
    guestToHost("/mnt/shared").mkdirs()
    guestToHost("/tmp").mkdirs()
    safeChmod(guestToHost("/tmp"), 0b111_111_111_111 or 0x200)

    // Ship the provisioning payload
    listOf("provision.sh", "memory_server.py", "memory_mcp.py").forEach { name ->
      val dst = guestToHost("/opt/opendroid/$name")
      dst.parentFile?.mkdirs()
      context.assets.open("sandbox/$name").use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
      safeChmod(dst, 0b111_101_101)
    }
  }

  private fun dnsServers(): List<String> {
    val found = try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
      cm.getLinkProperties(cm.activeNetwork)?.dnsServers?.mapNotNull { it.hostAddress?.substringBefore('%') }.orEmpty()
    } catch (_: Exception) { emptyList() }
    return (found + listOf("1.1.1.1", "8.8.8.8")).distinct()
  }

  private suspend fun provision() {
    _state.value = InstallState.Provisioning
    OpenDroidService.updateStatus(context, "OpenDroid Setup: Running sandbox bootstrap...")
    log("Bootstrapping sandbox environment (apt, opencode, mem0)...")
    val res = exec("bash /opt/opendroid/provision.sh", cwd = "/root", timeoutMs = 900_000L) { line ->
      log(line)
    }
    if (res.exitCode != 0) {
      if (!noSeccomp && res.output.contains("seccomp", ignoreCase = true)) {
        noSeccomp = true
        log("Retrying without seccomp acceleration")
        provision()
        return
      }
      throw IOException("Provisioning failed (exit ${res.exitCode}). See log.")
    }
  }
}
