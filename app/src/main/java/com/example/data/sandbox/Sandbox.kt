package com.example.data.sandbox

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
 * A real Ubuntu Base 26.04.1 (arm64) root filesystem executed in user space through proot.
 *
 * proot, its loader and libtalloc ship as jniLibs (the only app-owned location Android lets
 * us execve from on API 29+). There is no hypervisor: processes run natively, and "root" inside
 * the sandbox is proot's fake-root (uid 0) mapping.
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

  private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private fun initialState(): InstallState =
    if (File(rootfs, "root/$READY_MARKER").exists()) InstallState.Ready else InstallState.NotInstalled

  val isReady: Boolean get() = _state.value is InstallState.Ready
  fun isInstalled(): Boolean = isReady

  suspend fun startInstall() = install()

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
    timeoutMs: Long = 120_000,
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
  // Installation
  // ------------------------------------------------------------------------------------------

  private fun ensureHostDirs() {
    listOf(root, rootfs, sharedDir, libDir, tmpDir, downloadsDir).forEach { it.mkdirs() }
    // libtalloc's soname is libtalloc.so.2 but jniLibs entries must end in ".so".
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

  /** Downloads, verifies, extracts and provisions the sandbox. Safe to re-run (resumes from rootfs). */
  suspend fun install() = withContext(Dispatchers.IO) {
    try {
      ensureHostDirs()
      val marker = File(rootfs, "root/$READY_MARKER")
      if (!File(rootfs, "usr/bin/env").exists()) {
        val tar = File(downloadsDir, TARBALL)
        downloadUbuntuBase(tar)
        extract(tar)
        tar.delete()
      }
      configureGuest()
      provision()
      marker.parentFile?.mkdirs()
      marker.writeText(System.currentTimeMillis().toString())
      _state.value = InstallState.Ready
    } catch (e: Exception) {
      log("ERROR: ${e.message}")
      _state.value = InstallState.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  /** Wipes the rootfs so the next [install] starts clean. */
  suspend fun reset() = withContext(Dispatchers.IO) {
    rootfs.deleteRecursively()
    _installLog.value = emptyList()
    _state.value = InstallState.NotInstalled
  }

  private fun downloadUbuntuBase(dest: File) {
    _state.value = InstallState.Verifying
    val sums = http.newCall(Request.Builder().url("$UBUNTU_BASE_URL/SHA256SUMS").build()).execute().use {
      if (!it.isSuccessful) throw IOException("SHA256SUMS HTTP ${it.code}")
      it.body!!.string()
    }
    val expected = sums.lineSequence().firstOrNull { it.trim().endsWith(TARBALL) }
      ?.trim()?.substringBefore(' ')
      ?: throw IOException("No checksum for $TARBALL")

    if (dest.exists() && sha256(dest) == expected) {
      log("Using cached $TARBALL")
      return
    }
    log("Downloading $TARBALL")
    val resp = http.newCall(Request.Builder().url("$UBUNTU_BASE_URL/$TARBALL").build()).execute()
    resp.use {
      if (!it.isSuccessful) throw IOException("Download HTTP ${it.code}")
      val body = it.body!!
      val total = body.contentLength().takeIf { n -> n > 0 }
      var done = 0L
      FileOutputStream(dest).use { out ->
        body.byteStream().use { input ->
          val buf = ByteArray(64 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            val pct = total?.let { t -> ((done.toDouble() / t) * 100).toInt().coerceIn(0, 100) } ?: 0
            _state.value = InstallState.Downloading(pct)
          }
        }
      }
    }
    _state.value = InstallState.Verifying
    val actual = sha256(dest)
    if (actual != expected) {
      dest.delete()
      throw IOException("Checksum mismatch for $TARBALL")
    }
    log("Checksum verified")
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
    log("Extracting rootfs")
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
              FileOutputStream(out).use { tin.copyTo(it) }
              safeChmod(out, e.mode or 0b110_000_000)
            }
            else -> Unit // device nodes are bound from the host /dev
          }
        }
        if (++count % 500 == 0) log("  ...$count entries")
        e = tin.nextEntry as TarArchiveEntry?
      }
    }
    // Hard links become copies (Android app storage may not support them).
    for (l in deferredLinks) {
      val dst = File(rootfs, l.name.removePrefix("./"))
      val src = File(rootfs, l.linkName.removePrefix("./"))
      if (src.exists()) { dst.parentFile?.mkdirs(); src.copyTo(dst, overwrite = true); safeChmod(dst, l.mode or 0b110_000_000) }
    }
    log("Extracted $count entries")
  }

  private fun safeChmod(f: File, mode: Int) {
    try { Os.chmod(f.absolutePath, mode and 0b111_111_111_111) } catch (_: Exception) {}
  }

  /** Writes the small amount of guest configuration proot needs (DNS, apt, user dirs). */
  private fun configureGuest() {
    _state.value = InstallState.Provisioning
    fun put(path: String, text: String) {
      val f = guestToHost(path)
      f.parentFile?.mkdirs()
      if (f.exists() || f.isSymlink()) f.delete()
      f.writeText(text)
    }
    put("/etc/resolv.conf", dnsServers().joinToString("\n") { "nameserver $it" } + "\n")
    put("/etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
    put("/etc/hostname", "opendroid\n")
    // apt normally drops to the unprivileged _apt user via setuid, which proot cannot do.
    put("/etc/apt/apt.conf.d/99opendroid", "APT::Sandbox::User \"root\";\nDpkg::Options { \"--force-unsafe-io\"; };\nAcquire::Retries \"3\";\n")
    put("/etc/dpkg/dpkg.cfg.d/opendroid", "force-unsafe-io\n")
    guestToHost("/root/workspace").mkdirs()
    guestToHost("/mnt/shared").mkdirs()
    guestToHost("/tmp").mkdirs()
    safeChmod(guestToHost("/tmp"), 0b111_111_111_111 or 0x200)
    // Ship the provisioning payload.
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
    log("Provisioning guest (apt, opencode, mem0)...")
    val res = exec("bash /opt/opendroid/provision.sh", cwd = "/root", timeoutMs = 60 * 60_000L) { line ->
      log(line)
    }
    if (res.exitCode != 0) {
      // Some Android kernels reject proot's seccomp acceleration; retry once without it.
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
