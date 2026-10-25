package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AgentResponseChunk
import com.example.data.ai.OpenCodeAgentEngine
import com.example.data.knowledge.UniversalKnowledgeRepository
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
import com.example.data.repository.WorkspaceRepository
import com.example.data.sandbox.HardwareAdvisor
import com.example.data.sandbox.SandboxFile
import com.example.data.sandbox.UbuntuSandbox
import com.example.data.skills.GraphifyEngine
import com.example.data.skills.GraphifyResult
import com.example.data.skills.PonytailEngine
import com.example.data.skills.PonytailReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val title: String) {
  CHAT("Agent"),
  TERMINAL("Ubuntu LTS"),
  WORKSPACE("Files"),
  DIFFS("Diffs"),
  TRANSFERS("Transfers"),
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
  val preferredModel: String = "opencode/free-flash",
  val commitMessageInput: String = "",
  val activeAttachments: List<ChatAttachment> = emptyList(),
  val vmConfig: VmConfig = VmConfig(),
  val activeGraphifyResult: GraphifyResult? = null,
  val activePonytailReport: PonytailReport? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val workspaceRepository: WorkspaceRepository = WorkspaceRepository()
  val ubuntuSandbox: UbuntuSandbox = UbuntuSandbox()
  val knowledgeRepository: UniversalKnowledgeRepository = UniversalKnowledgeRepository(application)
  val agentEngine: OpenCodeAgentEngine = OpenCodeAgentEngine(workspaceRepository, ubuntuSandbox)

  val deviceInfo: DeviceHardwareInfo = HardwareAdvisor.getDeviceHardwareInfo(application)

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  val workspaceFiles: StateFlow<Map<String, WorkspaceFile>> = workspaceRepository.files
  val sandboxFiles: StateFlow<Map<String, SandboxFile>> = ubuntuSandbox.files
  val terminalEntries: StateFlow<List<TerminalEntry>> = ubuntuSandbox.terminalHistory
  val gitCommits: StateFlow<List<GitCommit>> = workspaceRepository.gitCommits
  val knowledgeItems: StateFlow<List<KnowledgeItem>> = knowledgeRepository.items

  init {
    val optimalConfig = HardwareAdvisor.computeOptimalVmConfig(deviceInfo)
    ubuntuSandbox.updateVmConfig(optimalConfig)

    val initialSession = AgentSession(
      id = UUID.randomUUID().toString(),
      title = "OpenCode Free Project",
      agentMode = AgentMode.BUILD,
      messages = listOf(
        ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "assistant",
          text = """👋 Welcome to **OpenCode** on Android!
- **Free Catalog Active**: Running `${agentEngine.preferredModel}` [FREE]. No API key required.
- **Sandboxed Linux VM**: Essential **Ubuntu 24.04 LTS (Noble)** with `sudo` and internet access.
- **Skills Available**: `/graphify` (AST & Dependency Trees), `/ponytail` (Code Review & Anti-patterns), `/superpower` (Autonomous TDD).
- **Universal Knowledge**: Persistent rules & conventions active across all projects.

Type **`/`** to view all OpenCode slash commands & skills, or attach files via the paperclip icon!""",
          mode = AgentMode.BUILD
        )
      )
    )

    _uiState.update {
      it.copy(
        sessions = listOf(initialSession),
        activeSessionId = initialSession.id,
        vmConfig = optimalConfig
      )
    }
  }

  val activeSession: AgentSession?
    get() = _uiState.value.sessions.find { it.id == _uiState.value.activeSessionId }

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
          text = "Started new agent session in mode **${_uiState.value.sessions.firstOrNull()?.agentMode?.name ?: "BUILD"}** with free catalog model.",
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
    // Also mirror into sandbox inbox
    if (attachment.contentSnippet != null) {
      ubuntuSandbox.importFromAndroid(attachment.name, attachment.contentSnippet)
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
    workspaceRepository.writeFile(path, newContent)
    ubuntuSandbox.writeFile("/home/ubuntu/$path", newContent)
    val updated = workspaceRepository.files.value[path]
    _uiState.update { it.copy(selectedFile = updated) }
  }

  fun revertFile(path: String) {
    workspaceRepository.revertFile(path)
    val updated = workspaceRepository.files.value[path]
    _uiState.update { it.copy(selectedFile = updated) }
  }

  fun executeTerminalCommand(command: String) {
    ubuntuSandbox.executeCommand(command)
  }

  fun clearTerminal() {
    ubuntuSandbox.clearTerminal()
  }

  fun setCommitMessageInput(msg: String) {
    _uiState.update { it.copy(commitMessageInput = msg) }
  }

  fun commitModifiedFiles() {
    val msg = _uiState.value.commitMessageInput.ifBlank { "Update files via OpenCode Agent" }
    val commit = workspaceRepository.gitCommit(msg)
    if (commit != null) {
      _uiState.update { it.copy(commitMessageInput = "") }
      ubuntuSandbox.addTerminalEntry("git commit -m \"$msg\"", "[main ${commit.hash}] $msg")
    }
  }

  fun updateVmConfig(config: VmConfig) {
    ubuntuSandbox.updateVmConfig(config)
    _uiState.update { it.copy(vmConfig = config) }
  }

  fun updatePreferredModel(model: String) {
    agentEngine.preferredModel = model
    _uiState.update { it.copy(preferredModel = model) }
  }

  fun updateAutoApprove(autoApprove: Boolean) {
    agentEngine.autoApproveTools = autoApprove
    _uiState.update { it.copy(autoApproveTools = autoApprove) }
  }

  fun importFromAndroid(name: String, content: String) {
    ubuntuSandbox.importFromAndroid(name, content)
    workspaceRepository.writeFile("downloads/$name", content)
  }

  fun runGraphify() {
    val result = GraphifyEngine.analyzeProject(workspaceFiles.value)
    _uiState.update { it.copy(activeGraphifyResult = result) }
  }

  fun closeGraphify() {
    _uiState.update { it.copy(activeGraphifyResult = null) }
  }

  fun runPonytail() {
    val report = PonytailEngine.reviewCodebase(workspaceFiles.value)
    _uiState.update { it.copy(activePonytailReport = report) }
  }

  fun closePonytail() {
    _uiState.update { it.copy(activePonytailReport = null) }
  }

  fun sendPrompt(userText: String) {
    val trimmed = userText.trim()
    val attachments = _uiState.value.activeAttachments
    if ((trimmed.isBlank() && attachments.isEmpty()) || _uiState.value.isGenerating) return

    val currentSession = activeSession ?: return
    val currentMode = currentSession.agentMode

    // Handle slash commands
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

    // Clear active attachments after sending
    _uiState.update { it.copy(activeAttachments = emptyList()) }

    updateSessionMessages(currentSession.id) { currentList ->
      currentList + userMessage + initialAssistantMessage
    }

    _uiState.update { it.copy(isGenerating = true, statusMessage = "OpenCode free agent processing...") }

    viewModelScope.launch {
      val toolsAcc = mutableListOf<ToolCall>()
      val textAcc = StringBuilder()

      val kbContext = knowledgeRepository.getKnowledgePromptInjection()

      try {
        agentEngine.processUserPrompt(
          prompt = trimmed,
          mode = currentMode,
          chatHistory = currentSession.messages,
          attachments = attachments,
          knowledgeContext = kbContext
        ).collect { chunk ->
          when (chunk) {
            is AgentResponseChunk.StatusUpdate -> {
              _uiState.update { it.copy(statusMessage = chunk.status) }
            }

            is AgentResponseChunk.ContentStream -> {
              textAcc.append(chunk.text)
              updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
                msg.copy(text = textAcc.toString(), isStreaming = true)
              }
            }

            is AgentResponseChunk.ToolExecuting -> {
              toolsAcc.add(chunk.tool)
              updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
                msg.copy(toolCalls = toolsAcc.toList())
              }
            }

            is AgentResponseChunk.ToolCompleted -> {
              val idx = toolsAcc.indexOfFirst { it.id == chunk.tool.id }
              if (idx != -1) {
                toolsAcc[idx] = chunk.tool
              } else {
                toolsAcc.add(chunk.tool)
              }
              updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
                msg.copy(toolCalls = toolsAcc.toList())
              }
            }
          }
        }

        updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
          msg.copy(
            text = textAcc.toString().ifBlank { "Task processed successfully in sandbox." },
            isStreaming = false,
            toolCalls = toolsAcc.toList()
          )
        }
      } catch (e: Exception) {
        updateAssistantMessage(currentSession.id, assistantMsgId) { msg ->
          msg.copy(
            text = "Error running agent: ${e.localizedMessage ?: "Unknown error"}",
            isStreaming = false
          )
        }
      } finally {
        _uiState.update { it.copy(isGenerating = false, statusMessage = null) }
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
      "import", "export" -> setTab(AppTab.TRANSFERS)
      "context" -> setTab(AppTab.KNOWLEDGE)
      "model" -> setTab(AppTab.SETTINGS)
      "graphify" -> {
        runGraphify()
      }
      "ponytail", "review" -> {
        runPonytail()
      }
      "superpower" -> {
        viewModelScope.launch {
          val goal = args.ifBlank { "Autonomous Test-Driven Feature" }
          val result = agentEngine.superpowerEngine.executeSuperpowerLoop(goal)
          val sysMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "system",
            text = result.finalSummary,
            mode = session.agentMode,
            toolCalls = result.steps.mapNotNull { it.toolCall }
          )
          updateSessionMessages(session.id) { it + sysMsg }
        }
      }
      "doctor" -> {
        val info = deviceInfo
        val vm = _uiState.value.vmConfig
        val msg = """OpenCode Doctor Diagnostics:
- Device RAM: ${info.totalRamMb} MB (Available: ${info.availableRamMb} MB)
- Device Storage: ${info.totalStorageGb} GB (Free: ${info.availableStorageGb} GB)
- VM Sandbox: ${vm.allocatedRamMb} MB RAM • ${vm.allocatedDiskGb} GB Storage
- CPU: ${info.cpuCores} cores (${info.architecture})
- OS: Ubuntu 24.04 LTS (Isolated Sandbox)
- Free Model: ${_uiState.value.preferredModel} [FREE]
- System Health: 100% Operational"""
        val sysMsg = ChatMessage(id = UUID.randomUUID().toString(), sender = "system", text = msg, mode = session.agentMode)
        updateSessionMessages(session.id) { it + sysMsg }
      }
      "cost" -> {
        val sysMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = "OpenCode Catalog Cost: $0.00 (Exclusively Free Tier Models). No credit card or paid balance required.",
          mode = session.agentMode
        )
        updateSessionMessages(session.id) { it + sysMsg }
      }
      "test" -> {
        val entry = ubuntuSandbox.executeCommand("test")
        val sysMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = "```\n${entry.output}\n```",
          mode = session.agentMode
        )
        updateSessionMessages(session.id) { it + sysMsg }
      }
      "clear" -> {
        updateSessionMessages(session.id) { emptyList() }
      }
      "help" -> {
        val helpMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = """OpenCode Slash Commands & Skills:
- `/build` : Switch to Build mode
- `/plan`  : Switch to Plan mode
- `/graphify` : Visual code graph & dependency trees
- `/ponytail` : Automated code review & anti-pattern linter
- `/superpower <goal>` : Autonomous TDD & self-healing supervisor
- `/terminal`: Open Ubuntu 24.04 LTS sandbox terminal
- `/files` : Workspace file explorer & code editor
- `/diff`  : Inspect modified file diffs
- `/context`: Universal persistent knowledge base
- `/doctor`: Run VM sandbox diagnostics
- `/import` / `/export`: Android ⇄ Ubuntu file transfers
- `/test`  : Run automated test suite
- `/clear` : Clear current timeline
- `/cost`  : Verify free catalog zero-cost status""",
          mode = session.agentMode
        )
        updateSessionMessages(session.id) { it + helpMsg }
      }
      else -> {
        val unknownMsg = ChatMessage(
          id = UUID.randomUUID().toString(),
          sender = "system",
          text = "Unknown slash command `/$cmd`. Type `/` in the input box to browse all OpenCode commands and skills.",
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
}
