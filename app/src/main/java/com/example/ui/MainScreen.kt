package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AgentMode
import com.example.ui.components.AgentHeader
import com.example.ui.components.GraphifyViewer
import com.example.ui.components.PonytailViewer
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiffScreen
import com.example.ui.screens.FileTransferScreen
import com.example.ui.screens.KnowledgeBaseScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun MainScreen(
  viewModel: MainViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val workspaceFiles by viewModel.workspaceFiles.collectAsState()
  val sandboxFiles by viewModel.sandboxFiles.collectAsState()
  val terminalEntries by viewModel.terminalEntries.collectAsState()
  val gitCommits by viewModel.gitCommits.collectAsState()
  val knowledgeItems by viewModel.knowledgeItems.collectAsState()

  val activeSession = viewModel.activeSession
  val currentMode = activeSession?.agentMode ?: AgentMode.BUILD
  val modifiedCount = workspaceFiles.values.count { it.isModified }

  // Back handling: if skill overlay is open, close it; if editor open, close it; else go to chat
  BackHandler(
    enabled = uiState.activeGraphifyResult != null ||
        uiState.activePonytailReport != null ||
        uiState.selectedFile != null ||
        uiState.activeTab != AppTab.CHAT
  ) {
    when {
      uiState.activeGraphifyResult != null -> viewModel.closeGraphify()
      uiState.activePonytailReport != null -> viewModel.closePonytail()
      uiState.selectedFile != null -> viewModel.selectFile(null)
      else -> viewModel.setTab(AppTab.CHAT)
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .statusBarsPadding(),
    topBar = {
      AgentHeader(
        currentMode = currentMode,
        activeSession = activeSession,
        allSessions = uiState.sessions,
        onSwitchMode = { newMode -> viewModel.switchMode(newMode) },
        onSwitchSession = { sessionId -> viewModel.switchSession(sessionId) },
        onCreateSession = { viewModel.createSession() }
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = SurfaceCard,
        modifier = Modifier
          .fillMaxWidth()
          .border(0.5.dp, BorderSubtle)
          .navigationBarsPadding()
          .testTag("main_bottom_nav_bar")
      ) {
        // Tab 1: Agent Chat
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.CHAT,
          onClick = { viewModel.setTab(AppTab.CHAT) },
          icon = {
            Icon(
              imageVector = Icons.Default.SmartToy,
              contentDescription = "Agent",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Agent",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.CHAT) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow,
            selectedTextColor = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_agent")
        )

        // Tab 2: Free-use Ubuntu Sandbox Terminal
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.TERMINAL,
          onClick = { viewModel.setTab(AppTab.TERMINAL) },
          icon = {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = "Terminal",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Terminal",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.TERMINAL) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BuildGreenGlow,
            selectedTextColor = BuildGreenGlow,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_terminal")
        )

        // Tab 3: Workspace Files
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.WORKSPACE,
          onClick = { viewModel.setTab(AppTab.WORKSPACE) },
          icon = {
            Icon(
              imageVector = Icons.Default.Folder,
              contentDescription = "Files",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Files",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.WORKSPACE) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TerminalCyan,
            selectedTextColor = TerminalCyan,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_files")
        )

        // Tab 4: Diffs
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.DIFFS,
          onClick = { viewModel.setTab(AppTab.DIFFS) },
          icon = {
            if (modifiedCount > 0) {
              BadgedBox(
                badge = {
                  Badge(
                    containerColor = WarningOrange,
                    contentColor = SurfaceDark
                  ) {
                    Text("$modifiedCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Difference,
                  contentDescription = "Diffs",
                  modifier = Modifier.size(20.dp)
                )
              }
            } else {
              Icon(
                imageVector = Icons.Default.Difference,
                contentDescription = "Diffs",
                modifier = Modifier.size(20.dp)
              )
            }
          },
          label = {
            Text(
              "Diffs",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.DIFFS) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = WarningOrange,
            selectedTextColor = WarningOrange,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_diffs")
        )

        // Tab 5: Android ⇄ Sandbox Transfers
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.TRANSFERS,
          onClick = { viewModel.setTab(AppTab.TRANSFERS) },
          icon = {
            Icon(
              imageVector = Icons.Default.SwapHoriz,
              contentDescription = "Transfers",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Transfers",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.TRANSFERS) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BuildGreenGlow,
            selectedTextColor = BuildGreenGlow,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_transfers")
        )

        // Tab 6: Universal Persistent Knowledge Base
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.KNOWLEDGE,
          onClick = { viewModel.setTab(AppTab.KNOWLEDGE) },
          icon = {
            Icon(
              imageVector = Icons.Default.Psychology,
              contentDescription = "Memory",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Memory",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.KNOWLEDGE) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = PlanPurpleGlow,
            selectedTextColor = PlanPurpleGlow,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_memory")
        )

        // Tab 7: Settings / Config
        NavigationBarItem(
          selected = uiState.activeTab == AppTab.SETTINGS,
          onClick = { viewModel.setTab(AppTab.SETTINGS) },
          icon = {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Config",
              modifier = Modifier.size(20.dp)
            )
          },
          label = {
            Text(
              "Config",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (uiState.activeTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal,
              fontSize = 10.sp
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TextPrimary,
            selectedTextColor = TextPrimary,
            indicatorColor = SurfaceElevated,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextMuted
          ),
          modifier = Modifier.testTag("nav_tab_config")
        )
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (uiState.activeTab) {
        AppTab.CHAT -> {
          ChatScreen(
            session = activeSession,
            isGenerating = uiState.isGenerating,
            statusMessage = uiState.statusMessage,
            preferredModel = uiState.preferredModel,
            activeAttachments = uiState.activeAttachments,
            onAddAttachment = { att -> viewModel.addAttachment(att) },
            onRemoveAttachment = { id -> viewModel.removeAttachment(id) },
            onSendPrompt = { prompt -> viewModel.sendPrompt(prompt) },
            onSwitchMode = { newMode -> viewModel.switchMode(newMode) }
          )
        }

        AppTab.TERMINAL -> {
          TerminalScreen(
            sandbox = viewModel.ubuntuSandbox,
            history = terminalEntries,
            vmConfig = uiState.vmConfig,
            onExecuteCommand = { cmd -> viewModel.executeTerminalCommand(cmd) },
            onClear = { viewModel.clearTerminal() }
          )
        }

        AppTab.WORKSPACE -> {
          WorkspaceScreen(
            files = workspaceFiles,
            selectedFile = uiState.selectedFile,
            onSelectFile = { file -> viewModel.selectFile(file) },
            onSaveFile = { path, content -> viewModel.saveFileContent(path, content) },
            onRevertFile = { path -> viewModel.revertFile(path) },
            onCreateFile = { path, content ->
              viewModel.saveFileContent(path, content)
              viewModel.selectFile(viewModel.workspaceFiles.value[path])
            },
            onDeleteFile = { path -> viewModel.workspaceRepository.deleteFile(path) },
            onAskAgent = { prompt ->
              viewModel.setTab(AppTab.CHAT)
              viewModel.sendPrompt(prompt)
            }
          )
        }

        AppTab.DIFFS -> {
          DiffScreen(
            modifiedFiles = viewModel.workspaceRepository.getModifiedFiles(),
            gitCommits = gitCommits,
            commitMessage = uiState.commitMessageInput,
            onCommitMessageChange = { msg -> viewModel.setCommitMessageInput(msg) },
            onCommit = { viewModel.commitModifiedFiles() },
            onRevertFile = { path -> viewModel.revertFile(path) },
            diffGenerator = { file -> viewModel.workspaceRepository.generateUnifiedDiff(file) }
          )
        }

        AppTab.TRANSFERS -> {
          FileTransferScreen(
            sandboxFiles = sandboxFiles,
            onImportFile = { name, content -> viewModel.importFromAndroid(name, content) },
            onOpenInTerminal = { path ->
              viewModel.setTab(AppTab.TERMINAL)
              viewModel.executeTerminalCommand("cat $path")
            }
          )
        }

        AppTab.KNOWLEDGE -> {
          KnowledgeBaseScreen(
            items = knowledgeItems,
            onAddItem = { title, content, cat -> viewModel.knowledgeRepository.addItem(title, content, cat) },
            onToggleItem = { id -> viewModel.knowledgeRepository.toggleItem(id) },
            onDeleteItem = { id -> viewModel.knowledgeRepository.deleteItem(id) }
          )
        }

        AppTab.SETTINGS -> {
          SettingsScreen(
            preferredModel = uiState.preferredModel,
            autoApproveTools = uiState.autoApproveTools,
            vmConfig = uiState.vmConfig,
            deviceInfo = viewModel.deviceInfo,
            onUpdateModel = { model -> viewModel.updatePreferredModel(model) },
            onUpdateAutoApprove = { autoApprove -> viewModel.updateAutoApprove(autoApprove) },
            onUpdateVmConfig = { config -> viewModel.updateVmConfig(config) }
          )
        }
      }

      // Graphify Modal Overlay
      if (uiState.activeGraphifyResult != null) {
        GraphifyViewer(
          result = uiState.activeGraphifyResult!!,
          onClose = { viewModel.closeGraphify() }
        )
      }

      // Ponytail Modal Overlay
      if (uiState.activePonytailReport != null) {
        PonytailViewer(
          report = uiState.activePonytailReport!!,
          onApplyFix = { path -> viewModel.saveFileContent(path, "// Fixed via Ponytail") },
          onClose = { viewModel.closePonytail() }
        )
      }
    }
  }
}
