package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.composio.ComposioClient
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.memory.Mem0Memory
import com.example.data.memory.MemoryClient
import com.example.data.model.AgentMode
import com.example.data.model.AgentSession
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.GitCommit
import com.example.data.model.KnowledgeItem
import com.example.data.model.TerminalEntry
import com.example.data.model.ToolCall
import com.example.data.model.VmConfig
import com.example.data.model.WorkspaceFile
import com.example.data.opencode.ChatAssembler
import com.example.data.opencode.OpenCodeClient
import com.example.data.repository.WorkspaceRepository
import com.example.data.runtime.SandboxRuntime
import com.example.data.sandbox.ResourceAllocator
import com.example.data.sandbox.Sandbox
import com.example.data.sandbox.TerminalManager
import com.example.data.security.SecretStore
import com.example.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab(val title: String) {
  CHAT("Agent"),
  TERMINAL("Terminal"),
  WORKSPACE("Files"),
  DIFFS("Diffs"),
  KNOWLEDGE("Memory"),
  SETTINGS("Config")
}

data class MainUiState(
  val sessions: List<AgentSession> = emptyList(),
  val activeSessionId: String = "",
  val activeTab: AppTab = AppTab.CHAT,
  val selectedFile: WorkspaceFile? = null,
  val isGenerating: Boolean = false,
  val statusMessage: String? = null,
  val autoApproveTools: Boolean = true,
  val preferredModel: String = "bigpickle",
  val commitMessageInput: String = "",
  val activeAttachments: List<ChatAttachment> = emptyList(),
  val vmConfig: VmConfig = VmConfig()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val appSettings: AppSettings = AppSettings(application)
  val secretStore: SecretStore = SecretStore(application)
  val resourceAllocator: ResourceAllocator = ResourceAllocator(application)
  val sandbox: Sandbox = Sandbox(application)
  val composioClient: ComposioClient = ComposioClient(appSettings, secretStore)
  val runtime: SandboxRuntime = SandboxRuntime(application, sandbox, secretStore, appSettings, composioClient)
  val openCodeClient: OpenCodeClient = OpenCodeClient(secretStore)
  val memoryClient: MemoryClient = MemoryClient(sandbox, appSettings, secretStore)
  val workspaceRepository: WorkspaceRepository = WorkspaceRepository(sandbox)
  val terminalManager: TerminalManager = TerminalManager(sandbox)
  val knowledgeRepository: KnowledgeRepository = KnowledgeRepository(application, sandbox)

  val deviceInfo: DeviceHardwareInfo = resourceAllocator.getDeviceHardwareInfo()

  private val _uiState = MutableStateFlow(MainUiState(preferredModel = appSettings.preferredModel))
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  val sandboxState: StateFlow<Sandbox.InstallState> = sandbox.state
  val sandboxInstallLog: StateFlow<List<String>> = sandbox.installLog
  val runtimeStatus: StateFlow<SandboxRuntime.RuntimeStatus> = runtime.status

  val activeProjectName: StateFlow<String> = workspaceRepository.activeProjectName
  val workspaceFiles: StateFlow<Map<String, WorkspaceFile>> = workspaceRepository.files
  val terminalEntries: StateFlow<List<TerminalEntry>> = terminalManager.history
  val gitCommits: StateFlow<List<GitCommit>> = workspaceRepository.gitCommits
  val knowledgeItems: StateFlow<List<KnowledgeItem>> = knowledgeRepository.items

  private val _mem0Memories = MutableStateFlow<List<Mem0Memory>>(emptyList())
  val mem0Memories: StateFlow<List<Mem0Memory>> = _mem0Memories.asStateFlow()

  init {
    val optimalBudget = resourceAllocator.computeBudget()
    val initialConfig = VmConfig(
      allocatedRamMb = appSettings.ramLimitMb.takeIf { it > 0 } ?: optimalBudget.ramLimitMb,
      allocatedDiskGb = appSettings.storageLimitGb.takeIf { it > 0 } ?: optimalBudget.storageLimitGb,
      recommendedRamMb = optimalBudget.ramLimitMb,
      recommendedDiskGb = optimalBudget.storageLimitGb
    )

    val initialSession = AgentSession(
      id = UUID.randomUUID().toString(),
      title = "OpenDroid Autonomous Session",
      agentMode = AgentMode.BUILD,
      messages = listOf(
        ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "assistant",
          text = """👋 Welcome to **OpenDroid**!
- **Sandboxed Linux**: Real Ubuntu 26.04.1 LTS (ARM64) running in user-space via PRoot.
- **Autonomous Core Engine**: Connected to official headless server on localhost with live SSE streaming.
- **Mem0 Long-Term Memory**: Persistent vector memory running locally inside sandbox with fast MCP tools.
- **Composio Tool Router**: Cloud developer tool integration (GitHub, Jira, Linear, Browserbase, Shell).
- **Mobile Programmer IDE**: Mobile symbol bar, syntax highlighting, and live diffs under `/root/workspace`.

Type **`/`** to explore OpenDroid slash commands, or ask me to build, code, run, or diagnose anything!""",
          mode = AgentMode.BUILD
        )
      )
    )

    _uiState.update {
      it.copy(
        sessions = listOf(initialSession),
        activeSessionId = initialSession.id,
        vmConfig = initialConfig,
        autoApproveTools = appSettings.autoApproveTools
      )
    }

    // Observe sandbox state reactively and start daemons when ready
    viewModelScope.launch(Dispatchers.IO) {
      sandbox.state.collect { state ->
        if (state is Sandbox.InstallState.Ready) {
          knowledgeRepository.loadDirectives()
          runtime.startAll()
          workspaceRepository.refreshFiles()
          refreshMemories()
        }
      }
    }
  }

  val activeSession: AgentSession?
    get() = _uiState.value.sessions.find { it.id == _uiState.value.activeSessionId }

  fun triggerSandboxInstall() {
    sandbox.startInstall()
  }

  fun setTab(tab: AppTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun switchMode(newMode: AgentMode) {
    val currentId = _uiState.value.activeSessionId
    _uiState.update { state ->
      val updated = state.sessions.map { s ->
        if (s.id == currentId) s.copy(agentMode = newMode, updatedAt = System.currentTimeMillis()) else s
      }
      state.copy(sessions = updated)
    }
  }

  fun createSession(title: String = "New Session") {
    val newSession = AgentSession(
      id = UUID.randomUUID().toString(),
      title = title,
      agentMode = _uiState.value.sessions.firstOrNull()?.agentMode ?: AgentMode.BUILD,
      messages = listOf(
        ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "assistant",
          text = "Started new OpenDroid session in mode **${_uiState.value.sessions.firstOrNull()?.agentMode?.name ?: "BUILD"}**.",
          mode = _uiState.value.sessions.firstOrNull()?.agentMode ?: AgentMode.BUILD
        )
      )
    )
    _uiState.update { state ->
      state.copy(
        sessions = state.sessions + newSession,
        activeSessionId = newSession.id
      )
    }
  }

  fun switchSession(sessionId: String) {
    _uiState.update { it.copy(activeSessionId = sessionId) }
  }

  fun addAttachment(attachment: ChatAttachment) {
    _uiState.update { it.copy(activeAttachments = it.activeAttachments + attachment) }
    if (attachment.contentSnippet != null) {
      viewModelScope.launch(Dispatchers.IO) {
        workspaceRepository.writeFile("inbox/${attachment.name}", attachment.contentSnippet)
      }
    }
  }

  fun removeAttachment(id: String) {
    _uiState.update { it.copy(activeAttachments = it.activeAttachments.filterNot { it.id == id }) }
  }

  fun selectFile(file: WorkspaceFile?) {
    _uiState.update { it.copy(selectedFile = file) }
    if (file != null) {
      setTab(AppTab.WORKSPACE)
    }
  }

  fun saveFileContent(path: String, newContent: String) {
    viewModelScope.launch(Dispatchers.IO) {
      workspaceRepository.writeFile(path, newContent)
      val updated = workspaceRepository.files.value[path]
      _uiState.update { it.copy(selectedFile = updated) }
    }
  }

  fun revertFile(path: String) {
    viewModelScope.launch(Dispatchers.IO) {
      workspaceRepository.revertFile(path)
      val updated = workspaceRepository.files.value[path]
      _uiState.update { it.copy(selectedFile = updated) }
    }
  }

  fun executeTerminalCommand(command: String) {
    viewModelScope.launch(Dispatchers.IO) {
      terminalManager.execute(command)
      workspaceRepository.refreshFiles()
    }
  }

  fun clearTerminal() {
    terminalManager.clear()
  }

  fun setCommitMessageInput(msg: String) {
    _uiState.update { it.copy(commitMessageInput = msg) }
  }

  fun commitModifiedFiles() {
    val msg = _uiState.value.commitMessageInput.ifBlank { "Update files via OpenDroid" }
    viewModelScope.launch(Dispatchers.IO) {
      val commit = workspaceRepository.gitCommit(msg)
      if (commit != null) {
        _uiState.update { it.copy(commitMessageInput = "") }
        terminalManager.addEntry("git commit -m \"$msg\"", "[main ${commit.hash}] $msg")
      }
    }
  }

  fun updateVmConfig(config: VmConfig) {
    appSettings.ramLimitMb = config.allocatedRamMb
    appSettings.storageLimitGb = config.allocatedDiskGb
    _uiState.update { it.copy(vmConfig = config) }
  }

  fun updatePreferredModel(model: String) {
    appSettings.preferredModel = model
    _uiState.update { it.copy(preferredModel = model) }
  }

  fun updateAutoApprove(autoApprove: Boolean) {
    appSettings.autoApproveTools = autoApprove
    _uiState.update { it.copy(autoApproveTools = autoApprove) }
  }

  fun runFileInSandbox(path: String): String {
    return workspaceRepository.runFileInSandbox(path)
  }

  fun switchProject(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      workspaceRepository.switchProject(name)
    }
  }

  fun deleteFile(path: String) {
    viewModelScope.launch(Dispatchers.IO) {
      workspaceRepository.deleteFile(path)
    }
  }

  fun addKnowledgeItem(title: String, content: String, category: String) {
    viewModelScope.launch(Dispatchers.IO) {
      knowledgeRepository.addItem(title, content, category)
    }
  }

  fun toggleKnowledgeItem(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
      knowledgeRepository.toggleItem(id)
    }
  }

  fun deleteKnowledgeItem(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
      knowledgeRepository.deleteItem(id)
    }
  }

  fun refreshMemories() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val result = memoryClient.listMemories()
        result.onSuccess { list ->
          _mem0Memories.value = list
        }
      } catch (_: Exception) { }
    }
  }

  fun addMem0Memory(text: String, category: String = "fact") {
    viewModelScope.launch(Dispatchers.IO) {
      memoryClient.addMemory(text, category)
      refreshMemories()
    }
  }

  fun deleteMem0Memory(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
      memoryClient.deleteMemory(id)
      refreshMemories()
    }
  }

  fun clearAllMem0Memories() {
    viewModelScope.launch(Dispatchers.IO) {
      memoryClient.clearAll()
      refreshMemories()
    }
  }

  fun sendPrompt(userText: String) {
    val trimmed = userText.trim()
    val attachments = _uiState.value.activeAttachments
    if ((trimmed.isBlank() && attachments.isEmpty()) || _uiState.value.isGenerating) return

    val currentSession = activeSession ?: return
    val currentMode = currentSession.agentMode

    if (trimmed.startsWith("/")) {
      handleSlashCommand(trimmed, currentSession)
      return
    }

    val userMessage = ChatMessage(
      id = UUID.randomUUID().toString(),
      sender = "user",
      text = trimmed,
      mode = currentMode,
      attachments = attachments
    )

    val assistantMsgId = UUID.randomUUID().toString()
    val initialAssistantMessage = ChatMessage(
      id = assistantMsgId,
      sender = "assistant",
      text = "",
      mode = currentMode,
      isStreaming = true
    )

    _uiState.update { it.copy(activeAttachments = emptyList(), isGenerating = true, statusMessage = "OpenDroid agent processing...") }

    updateSessionMessages(currentSession.id) { currentList ->
      currentList + userMessage + initialAssistantMessage
    }

    viewModelScope.launch(Dispatchers.IO) {
      val assembler = ChatAssembler()
      val promptToSend = buildString {
        append(trimmed)
        if (attachments.isNotEmpty()) {
          append("\n\nAttachments:\n")
          attachments.forEach { att ->
            append("- ${att.name} (${att.mimeType})\n")
            if (att.contentSnippet != null) {
              append("```\n${att.contentSnippet}\n```\n")
            }
          }
        }
      }

      val sseJob = launch {
        openCodeClient.events().collect { sseEvent ->
          val streamEvent = assembler.consume(sseEvent)
          if (streamEvent != null) {
            val (msgText, tools, status) = assembler.snapshot()
            if (status != null) {
              _uiState.update { it.copy(statusMessage = status) }
            }
            updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
              msg.copy(
                text = msgText,
                toolCalls = tools,
                isStreaming = true
              )
            }
          }
        }
      }

      try {
        val promptResult = openCodeClient.prompt(currentSession.id, promptToSend)
        if (promptResult.isFailure) {
          updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
            msg.copy(
              text = "Agent error: ${promptResult.exceptionOrNull()?.localizedMessage ?: "Failed to reach daemon"}",
              isStreaming = false
            )
          }
        } else {
          val (finalText, finalTools, _) = assembler.snapshot()
          updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
            msg.copy(
              text = finalText.ifBlank { "Task processed in sandbox." },
              toolCalls = finalTools,
              isStreaming = false
            )
          }

          // Auto extract long-term memory via Mem0 if meaningful
          if (finalText.length > 30) {
            launch {
              memoryClient.addMemory("OpenDroid task: ${trimmed.take(120)}", "interaction")
              refreshMemories()
            }
          }
        }
      } catch (e: Exception) {
        updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
          msg.copy(
            text = "OpenDroid execution error: ${e.localizedMessage ?: "Unknown error"}",
            isStreaming = false
          )
        }
      } finally {
        sseJob.cancel()
        _uiState.update { it.copy(isGenerating = false, statusMessage = null) }
        workspaceRepository.refreshFiles()
      }
    }
  }

  private fun handleSlashCommand(command: String, session: AgentSession) {
    val parts = command.removePrefix("/").split("\\s+".toRegex())
    val cmd = parts.firstOrNull()?.lowercase() ?: ""
    val args = parts.drop(1).joinToString(" ")

    when (cmd) {
      "build" -> switchMode(AgentMode.BUILD)
      "plan" -> switchMode(AgentMode.PLAN)
      "terminal" -> setTab(AppTab.TERMINAL)
      "files" -> setTab(AppTab.WORKSPACE)
      "diff" -> setTab(AppTab.DIFFS)
      "context", "memory" -> setTab(AppTab.KNOWLEDGE)
      "config", "settings" -> setTab(AppTab.SETTINGS)
      "doctor" -> {
        val info = deviceInfo
        val vm = _uiState.value.vmConfig
        val st = runtime.status.value
        val msg = """### OpenDroid Doctor Diagnostics:
- **Device Hardware**: ${info.totalRamMb} MB Total RAM (${info.availableRamMb} MB Available)
- **Host Storage**: ${info.totalStorageGb} GB Total (${info.availableStorageGb} GB Free)
- **CPU Platform**: ${info.cpuCores} cores (${info.architecture})
- **Sandbox Rootfs**: Ubuntu 26.04.1 LTS ARM64 (PRoot user-space fake-root)
- **Daemon Status**: Autonomous Engine: ${if (st.openCodeRunning) "RUNNING (127.0.0.1:4096)" else "STOPPED"} • Mem0 Server: ${if (st.mem0Running) "RUNNING (127.0.0.1:8765)" else "STOPPED"}
- **Watchdog RSS**: ${st.rssMb} MB used / ${vm.allocatedRamMb} MB budget
- **Composio Key**: ${if (secretStore.getComposioKey().isNullOrBlank()) "NOT CONFIGURED" else "CONFIGURED"}
- **Mem0 LLM Key**: ${if (secretStore.getMem0ApiKey().isNullOrBlank()) "NOT CONFIGURED" else "CONFIGURED"}"""
        val sysMsg = ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = msg, mode = session.agentMode)
        updateSessionMessages(session.id) { it + sysMsg }
      }
      "mem0" -> {
        viewModelScope.launch(Dispatchers.IO) {
          val subcmd = args.lowercase().trim()
          val sysMsg = when {
            subcmd.startsWith("add ") -> {
              val text = subcmd.removePrefix("add ").trim()
              memoryClient.addMemory(text, "fact")
              refreshMemories()
              ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = "Mem0: Stored long-term memory: \"$text\"", mode = session.agentMode)
            }
            subcmd == "clear" -> {
              memoryClient.clearAll()
              refreshMemories()
              ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = "Mem0: Cleared all long-term memories.", mode = session.agentMode)
            }
            else -> {
              refreshMemories()
              val memories = _mem0Memories.value
              val listText = if (memories.isEmpty()) {
                "Mem0: No long-term memories recorded yet. Use `/mem0 add <text>` or chat with OpenDroid."
              } else {
                "### Mem0 Long-Term Memory Store (${memories.size} memories):\n" +
                  memories.joinToString("\n") { "• [${it.category.uppercase()}] ${it.memory}" }
              }
              ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = listText, mode = session.agentMode)
            }
          }
          updateSessionMessages(session.id) { it + sysMsg }
        }
      }
      "composio" -> {
        viewModelScope.launch(Dispatchers.IO) {
          val key = secretStore.getComposioKey()
          val text = if (key.isNullOrBlank()) {
            "Composio API key not set. Configure your Composio API key in the Config tab."
          } else {
            val sessionsResult = composioClient.listSessions()
            sessionsResult.fold(
              onSuccess = { list ->
                "### Composio Tool Router (${list.size} active sessions):\n" +
                  list.joinToString("\n") { "• Session `${it.id}`: MCP URL = ${it.mcpUrl ?: "N/A"}" }
              },
              onFailure = { err -> "Composio error: ${err.localizedMessage}" }
            )
          }
          val sysMsg = ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = text, mode = session.agentMode)
          updateSessionMessages(session.id) { it + sysMsg }
        }
      }
      "test" -> {
        viewModelScope.launch(Dispatchers.IO) {
          val entry = terminalManager.execute("pytest || npm test || echo 'No test suite discovered in /root/workspace'")
          val sysMsg = ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = "```\n${entry.output}\n```", mode = session.agentMode)
          updateSessionMessages(session.id) { it + sysMsg }
        }
      }
      "clear" -> {
        updateSessionMessages(session.id) { emptyList() }
      }
      "help" -> {
        val helpMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = """### OpenDroid Slash Commands & Capabilities:
- `/build` : Switch to Build mode (file editing & sandbox execution)
- `/plan`  : Switch to Plan mode (architectural design)
- `/terminal`: Open real Ubuntu 26.04.1 LTS ARM64 bash shell
- `/files` : Open workspace explorer & programmer IDE
- `/diff`  : Inspect unified Git diffs
- `/mem0`  : Query or manage Mem0 long-term memories
- `/composio`: Inspect Composio tool router sessions
- `/doctor`: Run device hardware & daemon health check
- `/test`  : Run project test runner in sandbox
- `/clear` : Clear timeline messages""",
          mode = session.agentMode
        )
        updateSessionMessages(session.id) { it + helpMsg }
      }
      else -> {
        val unknownMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = "Unknown command `/$cmd`. Type `/help` or `/` to view all OpenDroid commands.",
          mode = session.agentMode
        )
        updateSessionMessages(session.id) { it + unknownMsg }
      }
    }
  }

  private fun updateSessionMessages(sessionId: String, transform: (List<ChatMessage>) -> List<ChatMessage>) {
    _uiState.update { state ->
      val updated = state.sessions.map { s ->
        if (s.id == sessionId) {
          s.copy(messages = transform(s.messages), updatedAt = System.currentTimeMillis())
        } else s
      }
      state.copy(sessions = updated)
    }
  }

  private fun updateAssistantMessage(sessionId: String, msgId: String, transform: (ChatMessage) -> ChatMessage) {
    updateSessionMessages(sessionId) { list ->
      list.map { if (it.id == msgId) transform(it) else it }
    }
  }

  override fun onCleared() {
    super.onCleared()
  }
}
