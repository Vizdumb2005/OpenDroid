package com.example.data.sandbox

import com.example.data.model.TerminalEntry
import com.example.data.model.VmConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
  private var vmConfig: VmConfig = VmConfig()
) {

  private val _files = MutableStateFlow<Map<String, SandboxFile>>(emptyMap())
  val files: StateFlow<Map<String, SandboxFile>> = _files.asStateFlow()

  private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(emptyList())
  val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()

  private val installedPackages = mutableSetOf(
    "base-files", "bash", "coreutils", "dpkg", "apt", "libc6", "tar", "gzip", "grep", "sed"
  )

  var currentUser: String = "ubuntu"
    private set

  var currentDirectory: String = "/home/ubuntu"
    private set

  private val startTimeMillis = System.currentTimeMillis()

  init {
    bootstrapUbuntuFilesystem()
  }

  fun updateVmConfig(config: VmConfig) {
    this.vmConfig = config
  }

  private fun bootstrapUbuntuFilesystem() {
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

    // Root filesystem directories
    listOf("/", "/bin", "/sbin", "/usr", "/usr/bin", "/etc", "/home", "/home/ubuntu", "/root", "/var", "/var/log", "/tmp", "/opt", "/mnt", "/mnt/android_share", "/mnt/android_share/inbox", "/mnt/android_share/outbox").forEach {
      val owner = if (it.startsWith("/home/ubuntu") || it.startsWith("/mnt/android_share")) "ubuntu" else "root"
      addDir(it, owner)
    }

    // Essential Ubuntu configuration files
    addFile(
      "/etc/os-release",
      """NAME="Ubuntu"
VERSION="24.04 LTS (Noble Numbat)"
ID=ubuntu
ID_LIKE=debian
PRETTY_NAME="Ubuntu 24.04 LTS"
VERSION_ID="24.04"
HOME_URL="https://www.ubuntu.com/"
SUPPORT_URL="https://help.ubuntu.com/"
BUG_REPORT_URL="https://bugs.launchpad.net/ubuntu/"
PRIVACY_POLICY_URL="https://www.ubuntu.com/legal/terms-and-policies/privacy-policy"
UBUNTU_CODENAME=noble
"""
    )

    addFile(
      "/etc/hostname",
      "ubuntu-noble-lts\n"
    )

    addFile(
      "/etc/issue",
      "Ubuntu 24.04 LTS \\n \\l\n\n"
    )

    addFile(
      "/etc/passwd",
      """root:x:0:0:root:/root:/bin/bash
daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin
bin:x:2:2:bin:/bin:/usr/sbin/nologin
sys:x:3:3:sys:/dev:/usr/sbin/nologin
ubuntu:x:1000:1000:Ubuntu User,,,:/home/ubuntu:/bin/bash
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

    // User bashrc and profile
    addFile(
      "/home/ubuntu/.bashrc",
      """# ~/.bashrc: executed by bash(1) for non-login shells.
export PS1='\[\e[01;32m\]\u@ubuntu-noble\[\e[00m\]:\[\e[01;34m\]\w\[\e[00m\]\$ '
alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'
alias cls='clear'
""",
      owner = "ubuntu"
    )

    addFile(
      "/home/ubuntu/README.txt",
      """Welcome to the Ubuntu 24.04 LTS Sandbox for OpenCode!
======================================================
- Essential minimal Ubuntu environment with full root/sudo access.
- Sandboxed and isolated from Android system storage.
- Mounted Android transfer directory: /mnt/android_share
- Package manager: apt update, apt install <pkg>
- Commands: bash, python3, git, curl, neofetch, htop, df, free.
""",
      owner = "ubuntu"
    )

    _files.value = initialFs

    // Welcome terminal entry
    addTerminalEntry(
      command = "systemd-boot",
      output = """Welcome to Ubuntu 24.04 LTS (GNU/Linux 6.6.0-android-noble aarch64)
 * Documentation:  https://help.ubuntu.com
 * Management:     https://landscape.canonical.com
 * Support:        https://ubuntu.com/pro

0 updates can be applied immediately.
Ubuntu sandbox ready. Logged in as: ubuntu (sudo NOPASSWD enabled)
Type 'help' or 'neofetch' to view system specifications.
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
    val existing = map[clean]
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
    // Also copy to ~/downloads for convenience
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
    // Normalize .. and .
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
Ubuntu 24.04 LTS Essential Sandbox Shell:
  File & Navigation:
    ls [-la], cd <dir>, pwd, cat <file>, touch <file>, mkdir [-p], rm [-rf], cp, mv, grep, find
  System & Admin:
    sudo <cmd>, su [user], whoami, id, uname -a, lsb_release -a, neofetch, uptime
  Process & Resources:
    ps, top, htop, free -h, df -h
  Package Management:
    apt update, apt install <pkg>, apt list --installed
  Networking & Dev:
    curl <url>, wget <url>, ping <host>, python3 [script], node [script], git [status|diff|commit]
  Transfer & Misc:
    ls /mnt/android_share, clear, exit
""".trimIndent()
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
        val arch = resolveSystemArch()
        output = "Linux ubuntu-noble-lts 6.6.0-generic #24-Ubuntu SMP PREEMPT_DYNAMIC $arch GNU/Linux"
      }

      "lsb_release" -> {
        output = """No LSB modules are available.
Distributor ID: Ubuntu
Description:    Ubuntu 24.04 LTS
Release:        24.04
Codename:       noble
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
        val arch = resolveSystemArch()

        output = """
            .-.          ${currentUser}@ubuntu-noble-lts
           oo|           ------------------------
          /`'\           OS: Ubuntu 24.04 LTS ($arch)
         (\_;/)          Host: OpenCode Sandbox VM (KVM-Accelerated)
        / /   \ \        Kernel: 6.6.0-android-noble
       / /     \ \       Uptime: $uptimeStr
      / /       \ \      Packages: ${installedPackages.size} (dpkg), 1 (snap)
     / /         \ \     Shell: bash 5.2.21
    / /           \ \    Terminal: opencode-terminal-lts
   / /             \ \   CPU: Android Cortex-Core (${Runtime.getRuntime().availableProcessors()} vCPUs)
  / /               \ \  Memory: 412MiB / ${ramAllocated}MiB (Allocated)
 / /                 \ \ Disk: 1.2GiB / ${diskAllocated}.0GiB (Allocated)
'-'                   '-'
""".trimIndent()
      }

      "free" -> {
        val total = vmConfig.allocatedRamMb
        val used = 412
        val free = total - used
        output = """
               total        used        free      shared  buff/cache   available
Mem:          ${total}M        ${used}M        ${free}M         12M        180M        ${free - 50}M
Swap:         2048M          0M       2048M
""".trimIndent()
      }

      "df" -> {
        val total = vmConfig.allocatedDiskGb
        output = """
Filesystem     1K-blocks      Used Available Use% Mounted on
overlay         ${total * 1024 * 1024}   1245184  ${(total - 1) * 1024 * 1024}  10% /
tmpfs             1048576         0   1048576   0% /dev
/dev/vda1       ${total * 1024 * 1024}   1245184  ${(total - 1) * 1024 * 1024}  10% /home/ubuntu
/android_share    8388608    204800   8183808   2% /mnt/android_share
""".trimIndent()
      }

      "ps" -> {
        output = """
  PID TTY          TIME CMD
    1 ?        00:00:01 systemd
  104 ?        00:00:00 systemd-journal
  240 ?        00:00:00 dbus-daemon
  412 pts/0    00:00:00 bash
  518 pts/0    00:00:00 ps
""".trimIndent()
      }

      "apt", "apt-get" -> {
        val subcmd = args.firstOrNull() ?: ""
        when (subcmd) {
          "update" -> {
            output = """
Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease [256 kB]
Get:2 http://ports.ubuntu.com/ubuntu-ports noble-updates InRelease [126 kB]
Get:3 http://ports.ubuntu.com/ubuntu-ports noble-security InRelease [126 kB]
Fetched 508 kB in 1s (490 kB/s)
Reading package lists... Done
Building dependency tree... Done
Reading state information... Done
All packages are up to date.
""".trimIndent()
          }

          "install" -> {
            val pkg = args.drop(1).firstOrNull()
            if (pkg == null) {
              output = "apt: missing package name operand"
              isError = true
            } else {
              installedPackages.add(pkg)
              output = """
Reading package lists... Done
Building dependency tree... Done
The following NEW packages will be installed:
  $pkg
0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded.
Need to get 1,482 kB of archives.
After this operation, 4,920 kB of additional disk space will be used.
Get:1 http://ports.ubuntu.com/ubuntu-ports noble/main $pkg [1,482 kB]
Fetched 1,482 kB in 0s (3,842 kB/s)
Selecting previously unselected package $pkg.
(Reading database ... 28412 files and directories currently installed.)
Preparing to unpack .../$pkg.deb ...
Unpacking $pkg ...
Setting up $pkg ...
Processing triggers for man-db (2.12.0-4build2) ...
""".trimIndent()
            }
          }

          "list" -> {
            output = installedPackages.sorted().joinToString("\n") { "$it/noble,now 24.04-1 installed" }
          }

          else -> {
            output = "apt 2.7.14 (aarch64). Type 'apt update' or 'apt install <pkg>'."
          }
        }
      }

      "curl" -> {
        val url = args.firstOrNull { !it.startsWith("-") } ?: ""
        if (url.contains("opencode.ai")) {
          output = """
#!/usr/bin/env bash
# OpenCode Installer v2.4 (Ubuntu Noble LTS)
echo "==> Detected OS: Ubuntu 24.04 LTS"
echo "==> Architecture: aarch64"
echo "==> Installing opencode binary to /usr/local/bin/opencode..."
echo "==> Installation complete! Run 'opencode' to launch."
""".trimIndent()
        } else if (url.isNotBlank()) {
          output = "<!DOCTYPE html><html><head><title>200 OK</title></head><body>Connected to $url</body></html>"
        } else {
          output = "curl: try 'curl --help' for more information"
          isError = true
        }
      }

      "python", "python3" -> {
        val script = args.firstOrNull()
        if (script == null) {
          output = "Python 3.12.3 (main, Nov  6 2024, 18:38:05) [GCC 13.2.0] on linux\nType \"help\", \"copyright\", \"credits\" or \"license\" for more information."
        } else {
          val content = readFile(script)
          output = if (content != null) {
            "Executing $script...\nOutput: Script executed successfully (exit code 0)"
          } else {
            "python3: can't open file '$script': [Errno 2] No such file or directory"
          }
        }
      }

      "node" -> {
        output = "Node.js v20.12.2 LTS (Ubuntu runtime)"
      }

      "git" -> {
        val subcmd = args.firstOrNull() ?: ""
        output = when (subcmd) {
          "status" -> "On branch main\nYour branch is up to date with 'origin/main'.\nnothing to commit, working tree clean"
          "version" -> "git version 2.43.0"
          else -> "git: '$subcmd' is a valid git command in sandbox."
        }
      }

      "clear" -> {
        clearTerminal()
        return TerminalEntry(UUID.randomUUID().toString(), rawInput, "", false, System.currentTimeMillis(), currentDirectory)
      }

      else -> {
        output = "bash: $cmd: command not found. Type 'help' for available commands or 'apt install $cmd' to install."
        isError = true
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

  private fun resolveSystemArch(): String {
    return try {
      val abis = android.os.Build.SUPPORTED_ABIS
      if (abis != null && abis.isNotEmpty() && abis[0].contains("arm")) "aarch64" else "x86_64"
    } catch (e: Throwable) {
      "aarch64"
    }
  }
}
