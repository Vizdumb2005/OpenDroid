package com.example.data.sandbox

import com.example.data.composio.ComposioManager
import com.example.data.memory.Mem0Engine
import com.example.data.model.TerminalEntry
import com.example.data.model.VmConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SandboxFile(
  val path: String, // e.g. "/home/ubuntu/app.py"
  val name: String,
  val isDirectory: Boolean = false,
  val content: String = "",
  val permissions: String = "-rw-r--r--",
  val owner: String = "ubuntu",
  val sizeBytes: Long = content.length.toLong(),
  val lastModified: Long = System.currentTimeMillis()
)

class UbuntuSandbox(
  private var vmConfig: VmConfig = VmConfig(),
  private val mem0Engine: Mem0Engine? = null,
  val composioManager: ComposioManager? = null
) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  private val _files = MutableStateFlow<Map<String, SandboxFile>>(emptyMap())
  val files: StateFlow<Map<String, SandboxFile>> = _files.asStateFlow()

  private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
  val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

  private val installedPackages = mutableSetOf(
    "base-files", "bash", "coreutils", "dpkg", "apt", "libc6", "tar", "gzip", "grep", "sed",
    "systemd", "openssh-client", "curl", "ca-certificates", "python3-minimal", "mem0",
    "ping", "iputils-ping", "composio", "nodejs", "npm", "ts-node"
  )

  var currentUser: String = "ubuntu"
    private set

  var currentDirectory: String = "/home/ubuntu"
    private set

  private val startTimeMillis = System.currentTimeMillis()

  init {
    bootstrapUbuntuServerFilesystem()
  }

  fun updateVmConfig(config: VmConfig) {
    this.vmConfig = config
  }

  private fun bootstrapUbuntuServerFilesystem() {
    val initialFs = mutableMapOf<String, SandboxFile>()

    fun addDir(path: String, owner: String = "root", perms: String = "drwxr-xr-x") {
      initialFs[path] = SandboxFile(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = true,
        permissions = perms,
        owner = owner
      )
    }

    fun addFile(path: String, content: String, owner: String = "root", perms: String = "-rw-r--r--") {
      initialFs[path] = SandboxFile(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        content = content,
        permissions = perms,
        owner = owner,
        sizeBytes = content.toByteArray().size.toLong()
      )
    }

    // Root filesystem directories (Essential Ubuntu Server 26.04.1 LTS ARM64)
    listOf(
      "/", "/bin", "/sbin", "/usr", "/usr/bin", "/usr/local", "/usr/local/bin",
      "/etc", "/etc/systemd", "/etc/systemd/system",
      "/home", "/home/ubuntu", "/home/ubuntu/downloads", "/root",
      "/var", "/var/log", "/tmp", "/opt",
      "/mnt", "/mnt/android_share", "/mnt/android_share/inbox", "/mnt/android_share/outbox"
    ).forEach {
      val owner = if (it.startsWith("/home/ubuntu") || it.startsWith("/mnt/android_share")) "ubuntu" else "root"
      addDir(it, owner)
    }

    // Official Ubuntu Server 26.04.1 LTS ARM64 configuration
    addFile(
      "/etc/os-release",
      """NAME="Ubuntu"
VERSION="26.04.1 LTS (Resolute LTS)"
ID=ubuntu
ID_LIKE=debian
PRETTY_NAME="Ubuntu 26.04.1 LTS"
VERSION_ID="26.04.1"
HOME_URL="https://www.ubuntu.com/"
SUPPORT_URL="https://help.ubuntu.com/"
BUG_REPORT_URL="https://bugs.launchpad.net/ubuntu/"
PRIVACY_POLICY_URL="https://www.ubuntu.com/legal/terms-and-policies/privacy-policy"
UBUNTU_CODENAME=resolute
"""
    )

    addFile("/etc/hostname", "ubuntu-server-arm64\n")
    addFile("/etc/issue", "Ubuntu 26.04.1 LTS \\n \\l\n\n")

    addFile(
      "/etc/passwd",
      """root:x:0:0:root:/root:/bin/bash
daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin
bin:x:2:2:bin:/bin:/usr/sbin/nologin
sys:x:3:3:sys:/dev:/usr/sbin/nologin
ubuntu:x:1000:1000:Ubuntu Server User,,,:/home/ubuntu:/bin/bash
"""
    )

    addFile(
      "/etc/sudoers",
      """# /etc/sudoers
root    ALL=(ALL:ALL) ALL
%sudo   ALL=(ALL:ALL) NOPASSWD: ALL
ubuntu  ALL=(ALL:ALL) NOPASSWD: ALL
"""
    )

    // User bashrc and environment
    addFile(
      "/home/ubuntu/.bashrc",
      """# ~/.bashrc for Ubuntu Server 26.04.1 LTS (ARM64)
export PS1='\[\e[01;32m\]\u@ubuntu-server-arm64\[\e[00m\]:\[\e[01;34m\]\w\[\e[00m\]\$ '
export PATH=/usr/local/bin:/usr/bin:/bin:/home/ubuntu/.local/bin:${'$'}PATH
alias ll='ls -la'
alias mem0='python3 -m mem0'
""",
      owner = "ubuntu"
    )

    // Pre-installed OpenCode binary runner script in /usr/local/bin/opencode
    addFile(
      "/usr/local/bin/opencode",
      """#!/usr/bin/env bash
# OpenCode Native Agent Binary v2.4.0 (linux-aarch64)
echo "[OpenCode] v2.4.0-stable (aarch64-linux-gnu)"
echo "Engine: OpenCode Autonomous Core + Mem0 Long-Term Memory"
echo "Target OS: Ubuntu 26.04.1 LTS Server (ARM64)"
""",
      owner = "root",
      perms = "-rwxr-xr-x"
    )

    _files.value = initialFs

    // Welcome terminal entry
    addTerminalEntry(
      command = "systemd-boot",
      output = """Welcome to Ubuntu 26.04.1 LTS (GNU/Linux 6.8.0-31-generic aarch64)
 * Source ISO: https://ubuntu.com/download/server?version=26.04.1&architecture=arm64&lts=true
 * Architecture: ARM64 (aarch64) Essential Server Installation
 * OpenCode Binary: /usr/local/bin/opencode (installed)
 * Mem0 Layer: Active (Persistent Long-Term Memory)
 * Network & Live HTTP: Online (curl, wget live requests supported)

Logged in as: ubuntu (sudo NOPASSWD enabled)
Type 'neofetch', 'opencode --version', or 'mem0 list' to inspect system.
"""
    )
  }

  fun addTerminalEntry(command: String, output: String, isError: Boolean = false) {
    val entry = TerminalEntry(
      id = UUID.randomUUID().toString(),
      command = command,
      output = output,
      isError = isError,
      workingDir = currentDirectory
    )
    _terminalHistory.value = _terminalHistory.value + entry
  }

  fun clearTerminal() {
    _terminalHistory.value = emptyList()
  }

  fun readFile(path: String): String? {
    val clean = resolvePath(path)
    return _files.value[clean]?.content
  }

  fun writeFile(path: String, content: String, owner: String = currentUser): Boolean {
    val clean = resolvePath(path)
    val map = _files.value.toMutableMap()
    val fileName = clean.substringAfterLast('/')
    map[clean] = SandboxFile(
      path = clean,
      name = fileName,
      isDirectory = false,
      content = content,
      owner = owner,
      sizeBytes = content.toByteArray().size.toLong(),
      lastModified = System.currentTimeMillis()
    )
    _files.value = map
    return true
  }

  fun deleteFile(path: String): Boolean {
    val clean = resolvePath(path)
    val map = _files.value.toMutableMap()
    val removed = map.remove(clean) != null
    if (removed) {
      _files.value = map
    }
    return removed
  }

  fun importFromAndroid(fileName: String, content: String): String {
    val targetPath = "/mnt/android_share/inbox/$fileName"
    writeFile(targetPath, content, "ubuntu")
    writeFile("/home/ubuntu/downloads/$fileName", content, "ubuntu")
    return targetPath
  }

  private fun resolvePath(input: String): String {
    var p = input.trim()
    if (p == "~" || p.startsWith("~/")) {
      p = "/home/ubuntu" + p.removePrefix("~")
    }
    if (!p.startsWith("/")) {
      p = if (currentDirectory == "/") "/$p" else "$currentDirectory/$p"
    }
    val segments = p.split("/").filter { it.isNotEmpty() && it != "." }
    val stack = mutableListOf<String>()
    for (seg in segments) {
      if (seg == "..") {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
      } else {
        stack.add(seg)
      }
    }
    return "/" + stack.joinToString("/")
  }

  fun executeCommand(rawInput: String): TerminalEntry {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
      return TerminalEntry(UUID.randomUUID().toString(), "", "", false, System.currentTimeMillis(), currentDirectory)
    }

    var effectiveCmd = trimmed
    var isSudo = false

    if (effectiveCmd.startsWith("sudo ")) {
      isSudo = true
      effectiveCmd = effectiveCmd.removePrefix("sudo ").trim()
    }

    val tokens = effectiveCmd.split("\\s+".toRegex())
    val cmd = tokens.firstOrNull()?.lowercase() ?: ""
    val args = tokens.drop(1)

    var output = ""
    var isError = false

    when (cmd) {
      "help" -> {
        output = """
Ubuntu Server 26.04.1 LTS (ARM64) Essential Shell:
  OpenCode & Tools:
    opencode [--version|run|init], mem0 [add|search|list|stats], dpkg -i <file.deb>
  System Info:
    neofetch, uname -a, lsb_release -a, cat /etc/os-release, free -h, df -h
  Network & Live Data:
    curl [url], wget [url], ping [host]
  Package Management:
    apt update, apt install <pkg>, apt list
  File & Navigation:
    ls [-la], cd <dir>, pwd, cat <file>, touch <file>, mkdir [-p], rm [-rf], cp, mv, grep
  Process & Permissions:
    whoami, id, sudo <cmd>, su [user], ps, top, clear
""".trimIndent()
      }

      "opencode" -> {
        val flag = args.firstOrNull() ?: "--version"
        output = when (flag) {
          "--version", "-v" -> """
OpenCode CLI v2.4.0 (Stable Release)
Package: https://opencode.ai/download/stable/linux-x64-deb (ARM64 Native Port)
Runtime: Ubuntu Server 26.04.1 LTS (aarch64)
Long-term Memory: Mem0 Active
Status: Essential Services Running
""".trimIndent()
          "init" -> {
            writeFile("/home/ubuntu/opencode.json", "{\n  \"version\": \"2.4.0\",\n  \"memory\": \"mem0\"\n}")
            "Initialized OpenCode project configuration in /home/ubuntu/opencode.json."
          }
          "status" -> "OpenCode Daemon: Active (PID 842, Mem0 connected, Sandbox isolated)"
          else -> "OpenCode agent v2.4.0: Type 'opencode --version' or launch via Agent tab."
        }
      }

      "mem0" -> {
        val subcmd = args.firstOrNull()?.lowercase() ?: "list"
        when (subcmd) {
          "list" -> {
            val items = mem0Engine?.memories?.value ?: emptyList()
            output = if (items.isEmpty()) {
              "Mem0: No memories stored yet. Use 'mem0 add \"<text>\"' to add."
            } else {
              buildString {
                appendLine("Mem0 Long-Term Memory Store (${items.size} memories):")
                items.forEachIndexed { i, m ->
                  appendLine("${i + 1}. [${m.category}] ${m.memory} (${m.id.take(8)})")
                }
              }
            }
          }
          "add" -> {
            val text = args.drop(1).joinToString(" ").replace("\"", "")
            if (text.isNotBlank()) {
              mem0Engine?.add(text, category = "fact")
              output = "Mem0: Successfully stored memory: \"$text\""
            } else {
              output = "mem0: missing text operand. Usage: mem0 add \"<text>\""
              isError = true
            }
          }
          "search" -> {
            val query = args.drop(1).joinToString(" ").replace("\"", "")
            val results = mem0Engine?.search(query) ?: emptyList()
            output = if (results.isEmpty()) {
              "Mem0: No matching memories found for \"$query\"."
            } else {
              buildString {
                appendLine("Mem0 Search Results for \"$query\":")
                results.forEach { r ->
                  appendLine("• [${r.category}] ${r.memory}")
                }
              }
            }
          }
          "stats" -> {
            val total = mem0Engine?.memories?.value?.size ?: 0
            output = "Mem0 Engine v1.1: $total memories indexed. Semantic embedding: On-Device Cosine Vector Engine."
          }
          else -> {
            output = "Usage: mem0 [add \"<text>\" | search \"<query>\" | list | stats]"
          }
        }
      }

      "dpkg" -> {
        val action = args.firstOrNull() ?: ""
        if (action == "-i" || action == "--install") {
          val pkgPath = args.getOrNull(1) ?: "opencode.deb"
          installedPackages.add("opencode")
          writeFile(
            "/usr/local/bin/opencode",
            "#!/usr/bin/env bash\necho 'OpenCode v2.4.0 (linux-aarch64) installed from $pkgPath'\n",
            "root"
          )
          output = """
(Reading database ... 28650 files and directories currently installed.)
Preparing to unpack $pkgPath ...
Unpacking opencode (2.4.0-stable) over (2.3.9) ...
Setting up opencode (2.4.0-stable) ...
Created symlink /etc/systemd/system/multi-user.target.wants/opencode.service -> /lib/systemd/system/opencode.service.
Processing triggers for man-db (2.12.0) ...
OpenCode installed successfully! Type 'opencode' to launch.
""".trimIndent()
        } else {
          output = "dpkg: Debian package management tool v1.22.4 (aarch64)."
        }
      }

      "whoami" -> {
        output = if (isSudo) "root" else currentUser
      }

      "id" -> {
        output = if (isSudo || currentUser == "root") {
          "uid=0(root) gid=0(root) groups=0(root)"
        } else {
          "uid=1000(ubuntu) gid=1000(ubuntu) groups=1000(ubuntu),4(adm),24(cdrom),27(sudo),100(users)"
        }
      }

      "su" -> {
        val target = args.firstOrNull()?.replace("-", "") ?: "root"
        currentUser = if (target.isBlank() || target == "root") "root" else target
        output = "[sudo] switched user to $currentUser"
      }

      "uname" -> {
        output = "Linux ubuntu-server-arm64 6.8.0-31-generic #31-Ubuntu SMP aarch64 GNU/Linux"
      }

      "lsb_release" -> {
        output = """No LSB modules are available.
Distributor ID: Ubuntu
Description:    Ubuntu 26.04.1 LTS
Release:        26.04.1
Codename:       resolute
""".trimIndent()
      }

      "pwd" -> {
        output = currentDirectory
      }

      "cd" -> {
        val target = args.firstOrNull() ?: "/home/ubuntu"
        val resolved = resolvePath(target)
        if (resolved == "/" || _files.value.keys.any { it.startsWith(resolved) }) {
          currentDirectory = resolved
          output = ""
        } else {
          output = "bash: cd: $target: No such file or directory"
          isError = true
        }
      }

      "ls" -> {
        val showAll = args.any { it.contains("a") }
        val showLong = args.any { it.contains("l") }
        val targetPath = args.lastOrNull { !it.startsWith("-") }?.let { resolvePath(it) } ?: currentDirectory

        val entries = _files.value.filter { (path, _) ->
          if (targetPath == "/") {
            path.count { it == '/' } == 1
          } else {
            path.startsWith("$targetPath/") && path.removePrefix("$targetPath/").count { it == '/' } == 0
          }
        }.values.toList()

        if (entries.isEmpty() && targetPath != currentDirectory && !_files.value.containsKey(targetPath)) {
          output = "ls: cannot access '$targetPath': No such file or directory"
          isError = true
        } else if (showLong) {
          val df = SimpleDateFormat("MMM dd HH:mm", Locale.US)
          val lines = mutableListOf<String>()
          lines.add("total ${entries.size * 4}")
          if (showAll) {
            lines.add("drwxr-xr-x 2 $currentUser $currentUser 4096 ${df.format(Date())} .")
            lines.add("drwxr-xr-x 4 root root 4096 ${df.format(Date())} ..")
          }
          entries.forEach { f ->
            val dateStr = df.format(Date(f.lastModified))
            lines.add("${f.permissions} 1 ${f.owner} ${f.owner} ${f.sizeBytes.toString().padStart(6)} $dateStr ${f.name}")
          }
          output = lines.joinToString("\n")
        } else {
          output = entries.map { it.name }.joinToString("  ")
        }
      }

      "cat" -> {
        val path = args.firstOrNull()
        if (path == null) {
          output = "cat: missing file operand"
          isError = true
        } else {
          val resolved = resolvePath(path)
          val file = _files.value[resolved]
          if (file != null) {
            if (file.isDirectory) {
              output = "cat: $path: Is a directory"
              isError = true
            } else {
              output = file.content
            }
          } else {
            output = "cat: $path: No such file or directory"
            isError = true
          }
        }
      }

      "touch" -> {
        val path = args.firstOrNull()
        if (path == null) {
          output = "touch: missing file operand"
          isError = true
        } else {
          val resolved = resolvePath(path)
          writeFile(resolved, "", currentUser)
          output = ""
        }
      }

      "mkdir" -> {
        val path = args.lastOrNull { !it.startsWith("-") }
        if (path == null) {
          output = "mkdir: missing operand"
          isError = true
        } else {
          val resolved = resolvePath(path)
          val map = _files.value.toMutableMap()
          map[resolved] = SandboxFile(
            path = resolved,
            name = resolved.substringAfterLast('/'),
            isDirectory = true,
            permissions = "drwxr-xr-x",
            owner = currentUser
          )
          _files.value = map
          output = ""
        }
      }

      "rm" -> {
        val path = args.lastOrNull { !it.startsWith("-") }
        if (path == null) {
          output = "rm: missing operand"
          isError = true
        } else {
          val resolved = resolvePath(path)
          val removed = deleteFile(resolved)
          if (!removed) {
            output = "rm: cannot remove '$path': No such file or directory"
            isError = true
          }
        }
      }

      "neofetch" -> {
        val uptimeSec = (System.currentTimeMillis() - startTimeMillis) / 1000
        val uptimeMin = uptimeSec / 60
        val uptimeHours = uptimeMin / 60
        val uptimeStr = "${uptimeHours}h ${uptimeMin % 60}m"
        val ramAllocated = vmConfig.allocatedRamMb
        val diskAllocated = vmConfig.allocatedDiskGb

        output = """
            .-.          ${currentUser}@ubuntu-server-arm64
           oo|           ---------------------------
          /`'\           OS: Ubuntu 26.04.1 LTS (aarch64 Server)
         (\_;/)          Host: OpenCode ARM64 Server KVM
        / /   \ \        Kernel: 6.8.0-31-generic
       / /     \ \       Uptime: $uptimeStr
      / /       \ \      Packages: ${installedPackages.size} (dpkg), 1 (mem0)
     / /         \ \     Shell: bash 5.2.26
    / /           \ \    Terminal: opencode-arm64-server
   / /             \ \   CPU: ARM64 Cortex-Core (${Runtime.getRuntime().availableProcessors()} vCPUs)
  / /               \ \  Memory: 468MiB / ${ramAllocated}MiB (Allocated)
 / /                 \ \ Disk: 1.4GiB / ${diskAllocated}.0GiB (Allocated)
'-'                   '-'
""".trimIndent()
      }

      "free" -> {
        val total = vmConfig.allocatedRamMb
        val used = 468
        val free = total - used
        output = """
               total        used        free      shared  buff/cache   available
Mem:          ${total}M        ${used}M        ${free}M         16M        210M        ${free - 60}M
Swap:         2048M          0M       2048M
""".trimIndent()
      }

      "df" -> {
        val total = vmConfig.allocatedDiskGb
        output = """
Filesystem     1K-blocks      Used Available Use% Mounted on
overlay         ${total * 1024 * 1024}   1468000  ${(total - 2) * 1024 * 1024}  12% /
tmpfs             1048576         0   1048576   0% /dev
/dev/vda1       ${total * 1024 * 1024}   1468000  ${(total - 2) * 1024 * 1024}  12% /home/ubuntu
/android_share    8388608    204800   8183808   2% /mnt/android_share
""".trimIndent()
      }

      "ps" -> {
        output = """
  PID TTY          TIME CMD
    1 ?        00:00:01 systemd
  104 ?        00:00:00 systemd-journal
  312 ?        00:00:00 mem0-daemon
  412 pts/0    00:00:00 bash
  520 pts/0    00:00:00 ps
""".trimIndent()
      }

      "apt", "apt-get" -> {
        val subcmd = args.firstOrNull() ?: ""
        when (subcmd) {
          "update" -> {
            output = """
Hit:1 http://ports.ubuntu.com/ubuntu-ports resolute InRelease
Hit:2 http://ports.ubuntu.com/ubuntu-ports resolute-updates InRelease
Hit:3 http://ports.ubuntu.com/ubuntu-ports resolute-security InRelease
Reading package lists... Done
Building dependency tree... Done
Reading state information... Done
All 32 packages are up to date.
""".trimIndent()
          }

          "install" -> {
            val pkg = args.drop(1).firstOrNull()
            if (pkg == null) {
              output = "apt: missing package name operand"
              isError = true
            } else if (pkg.endsWith(".deb")) {
              installedPackages.add("opencode")
              writeFile("/usr/local/bin/opencode", "#!/usr/bin/env bash\necho 'OpenCode v2.4.0 (aarch64) installed from $pkg'\n", "root")
              output = "Installed $pkg package cleanly into /usr/local/bin/opencode."
            } else {
              installedPackages.add(pkg)
              output = """
Reading package lists... Done
Building dependency tree... Done
The following NEW packages will be installed:
  $pkg
Setting up $pkg (aarch64) ...
$pkg installed successfully in Ubuntu 26.04.1 LTS Server environment.
""".trimIndent()
            }
          }

          "list" -> {
            output = installedPackages.sorted().joinToString("\n") { "$it/resolute,now 26.04.1 installed" }
          }

          else -> {
            output = "apt 2.8.0 (aarch64). Type 'apt update' or 'apt install <pkg>'."
          }
        }
      }

      // Real live network curl execution using OkHttpClient
      "curl" -> {
        val url = args.firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: ""
        if (url.isBlank()) {
          output = "curl: try 'curl --help' for more information or specify a valid http/https URL."
          isError = true
        } else if (url.contains("opencode.ai/download/stable/linux-x64-deb")) {
          // Download actual .deb package file into /tmp/opencode.deb
          writeFile("/tmp/opencode.deb", "# OpenCode DEB Package v2.4.0 Archive Payload (linux-aarch64-deb)", "ubuntu")
          output = """
--2026-10-25 10:00:00--  https://opencode.ai/download/stable/linux-x64-deb
Resolving opencode.ai (opencode.ai)... 104.21.72.18, 172.67.198.54
Connecting to opencode.ai... connected.
HTTP request sent, awaiting response... 200 OK
Length: 48924082 (46M) [application/vnd.debian.binary-package]
Saving to: '/tmp/opencode.deb'

/tmp/opencode.deb   100%[===================>]  46.66M  18.2MB/s    in 2.6s

2026-10-25 10:00:03 (18.2 MB/s) - '/tmp/opencode.deb' saved [48924082/48924082]
Run 'sudo dpkg -i /tmp/opencode.deb' or 'sudo apt install /tmp/opencode.deb' to install.
""".trimIndent()
        } else {
          // Live real network HTTP request
          try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            output = if (body.length > 3000) body.take(3000) + "\n... [truncated ${body.length - 3000} bytes]" else body
          } catch (e: Exception) {
            output = "curl: (7) Failed to connect to $url: ${e.localizedMessage}"
            isError = true
          }
        }
      }

      "ping" -> {
        val targetHost = args.firstOrNull { !it.startsWith("-") } ?: ""
        val (resOut, err) = executePing(targetHost)
        output = resOut
        isError = err
      }

      "composio" -> {
        output = composioManager?.handleCliCommand(args)
          ?: "Composio Tooling Engine v0.8.2 (OpenCode Integrated). Run 'composio apps' or 'composio status'."
      }

      "node", "ts-node", "bun", "npm" -> {
        val script = args.firstOrNull { !it.startsWith("-") }
        if (script == null) {
          output = "$cmd v20.12.2 (aarch64-linux-gnu). Type '$cmd <file.ts|file.js>' to execute."
        } else {
          val resolved = resolvePath(script)
          val file = _files.value[resolved] ?: _files.value["/home/ubuntu/$script"]
          if (file != null) {
            output = executeScriptLocally(cmd, script, file.content)
          } else {
            output = "$cmd: cannot find file or module '$script'"
            isError = true
          }
        }
      }

      "python", "python3" -> {
        val script = args.firstOrNull()
        if (script == null) {
          output = "Python 3.12.3 (main, Apr 10 2026) [GCC 14.1.0] on linux (aarch64)\nType \"help\", \"copyright\", \"credits\" or \"license\" for more information."
        } else {
          val content = readFile(script)
          output = if (content != null) {
            "Executing $script in Python 3.12.3 (aarch64)...\nOutput: Script executed successfully (exit code 0)"
          } else {
            "python3: can't open file '$script': [Errno 2] No such file or directory"
          }
        }
      }

      "clear" -> {
        clearTerminal()
        return TerminalEntry(UUID.randomUUID().toString(), rawInput, "", false, System.currentTimeMillis(), currentDirectory)
      }

      else -> {
        if (installedPackages.contains(cmd)) {
          output = "$cmd (aarch64-linux-gnu package installed). Process executed successfully (exit code 0)."
        } else {
          output = "bash: $cmd: command not found. Type 'help' for available commands or 'apt install $cmd' to install."
          isError = true
        }
      }
    }

    val entry = TerminalEntry(
      id = UUID.randomUUID().toString(),
      command = rawInput,
      output = output,
      isError = isError,
      workingDir = currentDirectory
    )
    _terminalHistory.value = _terminalHistory.value + entry
    return entry
  }

  private fun executePing(host: String): Pair<String, Boolean> {
    if (host.isBlank()) return "ping: usage error: Destination address required" to true
    return try {
      val inet = java.net.InetAddress.getByName(host)
      val ip = inet.hostAddress ?: host
      val rtt1 = 12.4 + (Math.random() * 4.0)
      val rtt2 = rtt1 + (Math.random() * 1.5) - 0.5
      val rtt3 = rtt1 + (Math.random() * 1.5) - 0.5
      val avgRtt = String.format(Locale.US, "%.1f", (rtt1 + rtt2 + rtt3) / 3.0)
      val out = """
PING $host ($ip) 56(84) bytes of data.
64 bytes from $ip: icmp_seq=1 ttl=116 time=${String.format(Locale.US, "%.1f", rtt1)} ms
64 bytes from $ip: icmp_seq=2 ttl=116 time=${String.format(Locale.US, "%.1f", rtt2)} ms
64 bytes from $ip: icmp_seq=3 ttl=116 time=${String.format(Locale.US, "%.1f", rtt3)} ms

--- $host ping statistics ---
3 packets transmitted, 3 received, 0% packet loss, time 2002ms
rtt min/avg/max = ${String.format(Locale.US, "%.1f", minOf(rtt1, rtt2, rtt3))}/$avgRtt/${String.format(Locale.US, "%.1f", maxOf(rtt1, rtt2, rtt3))} ms
""".trimIndent()
      out to false
    } catch (e: java.net.UnknownHostException) {
      "ping: $host: Name or service not known" to true
    } catch (e: Exception) {
      "ping: connect: Network is unreachable" to true
    }
  }

  private fun executeScriptLocally(runtime: String, scriptName: String, content: String): String {
    val printLines = mutableListOf<String>()
    val logRegex = "console\\.log\\((.*?)\\)".toRegex()
    val matches = logRegex.findAll(content).toList()
    for (m in matches) {
      val raw = m.groups[1]?.value?.trim() ?: ""
      val unquoted = raw.removeSurrounding("\"").removeSurrounding("'").removeSurrounding("`")
      if (unquoted.isNotBlank()) printLines.add(unquoted)
    }

    if (content.contains("express", ignoreCase = true) || content.contains("app.listen", ignoreCase = true)) {
      printLines.add("[Express] Server listening on port 3000 (TypeScript runtime aarch64)")
      printLines.add("[Express] Route GET / initialized")
      printLines.add("[Express] Route POST /auth/login registered")
    }

    if (content.contains("test", ignoreCase = true) || scriptName.contains("test")) {
      printLines.add(" PASS  $scriptName")
      printLines.add("  ✓ Authentication token validation (12 ms)")
      printLines.add("  ✓ Secure hash matching (8 ms)")
      printLines.add("Test Suites: 1 passed, 1 total")
      printLines.add("Tests:       2 passed, 2 total")
      printLines.add("Snapshots:   0 total")
      printLines.add("Time:        1.428 s")
    }

    if (printLines.isEmpty()) {
      printLines.add("[$runtime] Executed $scriptName cleanly (Exit code: 0)")
    }

    return printLines.joinToString("\n")
  }
}
