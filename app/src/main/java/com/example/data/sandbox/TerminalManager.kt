package com.example.data.sandbox

import com.example.data.model.TerminalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages interactive command execution within the real Ubuntu ARM64 sandbox.
 */
class TerminalManager(private val sandbox: Sandbox) {

  private val _terminalHistory = MutableStateFlow<List<TerminalEntry>>(
    listOf(
      TerminalEntry(
        id = UUID.randomUUID().toString(),
        command = "login",
        output = """Welcome to OpenDroid Linux Runtime
Ubuntu 26.04.1 LTS (GNU/Linux aarch64)
proot userland active (uid 0 fake-root enabled)
Workspace mounted at: /root/workspace
Shared host directory mounted at: /mnt/shared
Type 'opencode --version', 'neofetch', or 'apt update' to start.""",
        isError = false,
        workingDir = "/root/workspace"
      )
    )
  )
  val terminalHistory: StateFlow<List<TerminalEntry>> = _terminalHistory.asStateFlow()
  val history: StateFlow<List<TerminalEntry>> get() = _terminalHistory.asStateFlow()

  private val _currentDirectory = MutableStateFlow("/root/workspace")
  val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

  val currentUser: String = "root"

  fun addEntry(command: String, output: String, isError: Boolean = false) {
    val entry = TerminalEntry(
      id = UUID.randomUUID().toString(),
      command = command,
      output = output,
      isError = isError,
      workingDir = _currentDirectory.value
    )
    _terminalHistory.value = _terminalHistory.value + entry
  }

  fun clear() {
    _terminalHistory.value = emptyList()
  }

  suspend fun execute(rawCommand: String): TerminalEntry = withContext(Dispatchers.IO) {
    val trimmed = rawCommand.trim()
    if (trimmed.isEmpty()) {
      return@withContext TerminalEntry(UUID.randomUUID().toString(), "", "", false, System.currentTimeMillis(), _currentDirectory.value)
    }

    if (trimmed == "clear") {
      clear()
      return@withContext TerminalEntry(UUID.randomUUID().toString(), "clear", "", false, System.currentTimeMillis(), _currentDirectory.value)
    }

    // Handle cd commands locally to update state
    if (trimmed.startsWith("cd ") || trimmed == "cd") {
      val target = trimmed.removePrefix("cd").trim().ifEmpty { "/root" }
      val targetDir = if (target.startsWith("/")) target else "${_currentDirectory.value.trimEnd('/')}/$target"
      val hostDir = sandbox.guestToHost(targetDir)
      if (hostDir.exists() && hostDir.isDirectory) {
        val canonical = "/" + hostDir.relativeTo(sandbox.rootfs).path.replace('\\', '/')
        _currentDirectory.value = canonical
        val entry = TerminalEntry(UUID.randomUUID().toString(), trimmed, "", false, System.currentTimeMillis(), canonical)
        _terminalHistory.value = _terminalHistory.value + entry
        return@withContext entry
      }
    }

    // Execute real command in sandbox
    val entryId = UUID.randomUUID().toString()
    val initialEntry = TerminalEntry(
      id = entryId,
      command = trimmed,
      output = "Running...",
      isError = false,
      workingDir = _currentDirectory.value
    )
    _terminalHistory.value = _terminalHistory.value + initialEntry

    val outputBuilder = StringBuilder()
    val res = sandbox.exec(trimmed, cwd = _currentDirectory.value) { line ->
      outputBuilder.appendLine(line)
      _terminalHistory.value = _terminalHistory.value.map {
        if (it.id == entryId) it.copy(output = outputBuilder.toString().trimEnd()) else it
      }
    }

    val finalEntry = TerminalEntry(
      id = entryId,
      command = trimmed,
      output = res.output.trimEnd(),
      isError = res.exitCode != 0,
      workingDir = _currentDirectory.value
    )
    _terminalHistory.value = _terminalHistory.value.map {
      if (it.id == entryId) finalEntry else it
    }
    finalEntry
  }
}
