package com.example.data.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.example.data.composio.ComposioClient
import com.example.data.model.VmConfig
import com.example.data.sandbox.ResourceAllocator
import com.example.data.sandbox.Sandbox
import com.example.data.sandbox.SandboxLimits
import com.example.data.security.SecretStore
import com.example.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class ServiceStatus { STOPPED, STARTING, RUNNING, FAILED }

private fun Process.pidCompat(): Int? = try {
  val field = javaClass.getDeclaredField("pid")
  field.isAccessible = true
  field.getInt(this)
} catch (_: Throwable) {
  try {
    val method = javaClass.getMethod("pid")
    (method.invoke(this) as Number).toInt()
  } catch (_: Throwable) { null }
}

typealias RuntimeStatus = SandboxRuntime.RuntimeStatus

/**
 * Owns the long-running processes inside the sandbox (OpenCode server, Mem0 service),
 * restarts them if they die, and enforces the RAM budget with a watchdog.
 */
class SandboxRuntime(
  private val context: Context,
  val sandbox: Sandbox,
  private val secrets: SecretStore,
  private val settings: AppSettings,
  private val composio: ComposioClient = ComposioClient(settings, secrets),
) {
  data class RuntimeStatus(
    val openCodeRunning: Boolean = false,
    val mem0Running: Boolean = false,
    val rssMb: Int = 0
  )
  companion object {
    const val OPENCODE_PORT = 4096
    const val OPENCODE_URL = "http://127.0.0.1:$OPENCODE_PORT"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var jobs: List<Job> = emptyList()
  private val procs = java.util.concurrent.ConcurrentHashMap<String, Process>()

  private val _opencode = MutableStateFlow(ServiceStatus.STOPPED)
  val opencode: StateFlow<ServiceStatus> = _opencode.asStateFlow()
  private val _memory = MutableStateFlow(ServiceStatus.STOPPED)
  val memory: StateFlow<ServiceStatus> = _memory.asStateFlow()
  private val _rssMb = MutableStateFlow(0)
  val rssMb: StateFlow<Int> = _rssMb.asStateFlow()

  private val _status = MutableStateFlow(RuntimeStatus())
  val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

  fun updateStatus() {
    _status.value = RuntimeStatus(
      openCodeRunning = _opencode.value == ServiceStatus.RUNNING,
      mem0Running = _memory.value == ServiceStatus.RUNNING,
      rssMb = _rssMb.value
    )
  }

  fun startAll() = start()
  fun stopAll() = stop()

  private val _vm = MutableStateFlow(ResourceAllocator.effective(context, settings))
  val vm: StateFlow<VmConfig> = _vm.asStateFlow()
  private val _log = MutableStateFlow<List<String>>(emptyList())
  val log: StateFlow<List<String>> = _log.asStateFlow()
  private val _watchdogKills = MutableStateFlow(0)
  val watchdogKills: StateFlow<Int> = _watchdogKills.asStateFlow()

  val isRunning: Boolean get() = jobs.any { it.isActive }

  private fun addLog(src: String, line: String) {
    _log.value = (_log.value + "[$src] $line").takeLast(300)
  }

  fun applyResources() {
    val cfg = ResourceAllocator.effective(context, settings)
    _vm.value = cfg
    sandbox.limits = SandboxLimits(ramMb = cfg.allocatedRamMb)
  }

  /** Reduce the budget under system memory pressure (called from onTrimMemory). */
  fun onMemoryPressure(level: Int) {
    val cur = _vm.value
    val reduced = (cur.allocatedRamMb * if (level >= 15) 0.6 else 0.8).toInt().coerceAtLeast(512)
    if (reduced < cur.allocatedRamMb) {
      _vm.value = cur.copy(allocatedRamMb = reduced)
      sandbox.limits = SandboxLimits(ramMb = reduced)
      addLog("governor", "memory pressure ($level): RAM budget lowered to ${reduced} MB")
    }
  }

  fun start() {
    if (isRunning || !sandbox.isReady) return
    applyResources()
    writeGuestConfig()
    jobs = listOf(
      scope.launch { supervise("opencode", _opencode) { startOpenCode() } },
      scope.launch { supervise("memory", _memory) { startMemory() } },
      scope.launch { watchdog() },
    )
  }

  fun stop() {
    jobs.forEach { it.cancel() }
    jobs = emptyList()
    procs.values.forEach { it.destroyForcibly() }
    procs.clear()
    _opencode.value = ServiceStatus.STOPPED
    _memory.value = ServiceStatus.STOPPED
    _rssMb.value = 0
    updateStatus()
  }

  fun restart() { stop(); start() }

  /** Writes ~/.config/opencode/opencode.json (MCP servers) and global agent rules. */
  fun writeGuestConfig() {
    val mcp = JSONObject()
    mcp.put("memory", JSONObject()
      .put("type", "local")
      .put("command", org.json.JSONArray(listOf("/opt/mem0/bin/python", "/opt/opendroid/memory_mcp.py")))
      .put("enabled", true))
    if (composio.hasKey && settings.composioMcpUrl.isNotBlank()) {
      val headers = JSONObject()
      composio.mcpHeaders().forEach { (k, v) -> headers.put(k, v) }
      mcp.put("composio", JSONObject().put("type", "remote").put("url", settings.composioMcpUrl).put("headers", headers).put("enabled", true))
    }
    val cfg = JSONObject()
      .put("\$schema", "https://opencode.ai/config.json")
      .put("autoupdate", false)
      .put("mcp", mcp)
    val dir = sandbox.guestToHost("/root/.config/opencode")
    dir.mkdirs()
    val f = File(dir, "opencode.json")
    f.writeText(cfg.toString(2))
    f.setReadable(false, false); f.setReadable(true, true)
    File(dir, "AGENTS.md").writeText(
      """
      # Environment
      You run inside an Ubuntu 26.04.1 (arm64) sandbox on an Android phone, started by the OpenDroid app.
      - Projects live in /root/workspace. /mnt/shared exchanges files with the phone.
      - You are root (sudo works). Network access is available.
      - Use the `memory_search` tool before answering questions about the user's preferences or past work,
        and `memory_add` to save durable facts, decisions and conventions.
      - The device has limited RAM; avoid very heavy builds and clean up background processes.
      """.trimIndent() + "\n"
    )
  }

  // ------------------------------------------------------------------------------------------

  private suspend fun supervise(name: String, status: MutableStateFlow<ServiceStatus>, launch: () -> Process) {
    var backoff = 2_000L
    while (scope.isActive) {
      status.value = ServiceStatus.STARTING
      updateStatus()
      val started = System.currentTimeMillis()
      try {
        val p = launch()
        procs[name] = p
        Thread {
          p.inputStream.bufferedReader().useLines { it.forEach { l -> addLog(name, l) } }
        }.apply { isDaemon = true; start() }
        // Considered healthy once the port answers.
        scope.launch {
          repeat(90) {
            if (!p.isAlive) return@launch
            if (probe(name)) { status.value = ServiceStatus.RUNNING; updateStatus(); return@launch }
            delay(1_000)
          }
        }
        p.waitFor()
        addLog(name, "exited with ${p.exitValue()}")
      } catch (e: Exception) {
        addLog(name, "failed to start: ${e.message}")
      }
      procs.remove(name)
      status.value = ServiceStatus.FAILED
      updateStatus()
      if (System.currentTimeMillis() - started > 60_000) backoff = 2_000L
      delay(backoff)
      backoff = (backoff * 2).coerceAtMost(30_000L)
    }
  }

  private fun probe(name: String): Boolean = try {
    val url = if (name == "opencode") "$OPENCODE_URL/global/health" else "http://127.0.0.1:8765/health"
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 800; c.readTimeout = 800
    if (name == "opencode") {
      val cred = android.util.Base64.encodeToString("opencode:${secrets.serverPassword()}".toByteArray(), android.util.Base64.NO_WRAP)
      c.setRequestProperty("Authorization", "Basic $cred")
    }
    (c.responseCode in 200..299).also { c.disconnect() }
  } catch (_: Exception) { false }

  private fun startOpenCode(): Process = sandbox.buildProcess(
    "exec opencode serve --hostname 127.0.0.1 --port $OPENCODE_PORT",
    cwd = Sandbox.GUEST_WORKSPACE,
    extraEnv = mapOf(
      "OPENCODE_SERVER_PASSWORD" to secrets.serverPassword(),
      "OPENCODE_DISABLE_AUTOUPDATE" to "true",
      "OPENCODE_DISABLE_LSP_DOWNLOAD" to "false",
    ),
  ).start()

  private fun startMemory(): Process = sandbox.buildProcess(
    "exec /opt/mem0/bin/python /opt/opendroid/memory_server.py",
    cwd = "/root",
    extraEnv = mapOf("MEM0_TELEMETRY" to "false"),
  ).start()

  // ------------------------------------------------------------------------------------------
  // Resource governor
  // ------------------------------------------------------------------------------------------

  private suspend fun watchdog() {
    while (scope.isActive) {
      delay(5_000)
      val roots = procs.values.mapNotNull { it.pidCompat() }
      if (roots.isEmpty()) { _rssMb.value = 0; continue }
      val tree = descendantsRss(roots)
      val totalMb = (tree.values.sum() / 1024).toInt()
      _rssMb.value = totalMb
      updateStatus()
      val cap = _vm.value.allocatedRamMb
      if (totalMb > cap) {
        // Kill the single biggest worker (never the proot launchers), supervisors restart services if needed.
        val victim = tree.filterKeys { it !in roots }.maxByOrNull { it.value }
        if (victim != null) {
          addLog("governor", "RSS ${totalMb} MB exceeds ${cap} MB budget; terminating pid ${victim.key} (${victim.value / 1024} MB)")
          try { Os.kill(victim.key, OsConstants.SIGTERM) } catch (_: Exception) {}
          delay(3_000)
          try { Os.kill(victim.key, OsConstants.SIGKILL) } catch (_: Exception) {}
          _watchdogKills.value += 1
        }
      }
    }
  }

  /** pid -> RSS in KiB for every descendant of [roots] (including the roots). */
  private fun descendantsRss(roots: List<Int>): Map<Int, Long> {
    val parent = HashMap<Int, Int>()
    val rss = HashMap<Int, Long>()
    File("/proc").listFiles()?.forEach { d ->
      val pid = d.name.toIntOrNull() ?: return@forEach
      try {
        val stat = File(d, "stat").readText()
        val after = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        parent[pid] = after[1].toInt()
        File(d, "status").useLines { lines ->
          lines.firstOrNull { it.startsWith("VmRSS:") }?.let { l ->
            rss[pid] = l.filter { it.isDigit() }.toLongOrNull() ?: 0L
          }
        }
      } catch (_: Exception) {}
    }
    val result = HashMap<Int, Long>()
    fun isDescendant(pid: Int): Boolean {
      var cur = pid
      var hops = 0
      while (hops++ < 64) {
        if (cur in roots) return true
        cur = parent[cur] ?: return false
      }
      return false
    }
    parent.keys.filter { isDescendant(it) }.forEach { result[it] = rss[it] ?: 0L }
    return result
  }
}
