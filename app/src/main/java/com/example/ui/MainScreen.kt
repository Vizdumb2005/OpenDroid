package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AgentMode
import com.example.data.sandbox.Sandbox
import com.example.ui.components.AgentHeader
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiffScreen
import com.example.ui.screens.KnowledgeBaseScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ErrorRed
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
  val terminalEntries by viewModel.terminalEntries.collectAsState()
  val gitCommits by viewModel.gitCommits.collectAsState()
  val knowledgeItems by viewModel.knowledgeItems.collectAsState()
  val mem0Memories by viewModel.mem0Memories.collectAsState()
  val activeProjectName by viewModel.activeProjectName.collectAsState()
  val sandboxState by viewModel.sandboxState.collectAsState()
  val sandboxInstallLog by viewModel.sandboxInstallLog.collectAsState()

  val activeSession = viewModel.activeSession
  val currentMode = activeSession?.agentMode ?: AgentMode.BUILD
  val modifiedCount = workspaceFiles.values.count { it.isModified }

  BackHandler(
    enabled = uiState.selectedFile != null || uiState.activeTab != AppTab.CHAT
  ) {
    if (uiState.selectedFile != null) {
      viewModel.selectFile(null)
    } else {
      viewModel.setTab(AppTab.CHAT)
    }
  }

  // If Sandbox is not yet Ready, show the First-Run Setup & Installer Card
  if (sandboxState !is Sandbox.InstallState.Ready) {
    SandboxInstallerScreen(
      state = sandboxState,
      logs = sandboxInstallLog,
      onStartInstall = { viewModel.triggerSandboxInstall() },
      modifier = modifier
    )
    return
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

        // Tab 2: Linux Sandbox Terminal
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

        // Tab 3: Workspace Files & Mobile IDE
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

        // Tab 5: Mem0 Long-Term Memory & Directives
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

        // Tab 6: Settings / Config
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
            activeProjectName = activeProjectName,
            onSelectFile = { file -> viewModel.selectFile(file) },
            onSaveFile = { path, content -> viewModel.saveFileContent(path, content) },
            onRevertFile = { path -> viewModel.revertFile(path) },
            onCreateFile = { path, content ->
              viewModel.saveFileContent(path, content)
              viewModel.selectFile(viewModel.workspaceFiles.value[path])
            },
            onDeleteFile = { path -> viewModel.deleteFile(path) },
            onAskAgent = { prompt ->
              viewModel.setTab(AppTab.CHAT)
              viewModel.sendPrompt(prompt)
            },
            onRunFile = { path -> viewModel.runFileInSandbox(path) },
            onSwitchProject = { name -> viewModel.switchProject(name) }
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

        AppTab.KNOWLEDGE -> {
          KnowledgeBaseScreen(
            items = knowledgeItems,
            onAddItem = { title, content, cat -> viewModel.addKnowledgeItem(title, content, cat) },
            onToggleItem = { id -> viewModel.toggleKnowledgeItem(id) },
            onDeleteItem = { id -> viewModel.deleteKnowledgeItem(id) },
            mem0Memories = mem0Memories,
            onAddMem0Memory = { text, cat -> viewModel.addMem0Memory(text, cat) },
            onDeleteMem0Memory = { id -> viewModel.deleteMem0Memory(id) },
            onClearAllMem0 = { viewModel.clearAllMem0Memories() }
          )
        }

        AppTab.SETTINGS -> {
          SettingsScreen(
            viewModel = viewModel
          )
        }
      }
    }
  }
}

@Composable
fun SandboxInstallerScreen(
  state: Sandbox.InstallState,
  logs: List<String>,
  onStartInstall: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  LaunchedEffect(logs.size) {
    if (logs.isNotEmpty()) {
      listState.animateScrollToItem(logs.size - 1)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderHighlight, RoundedCornerShape(16.dp))
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // App icon badge
        Box(
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(1.5.dp, TerminalCyan, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = TerminalCyan,
            modifier = Modifier.size(28.dp)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "OpenDroid Linux Sandbox Setup",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Official Ubuntu 26.04.1 LTS (ARM64) User-Space PRoot Environment",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // State indicator badge
        val (stateText, stateColor) = when (state) {
          Sandbox.InstallState.NotInstalled -> "Not Installed" to TextMuted
          is Sandbox.InstallState.Downloading -> "Downloading Rootfs (${state.progressPercent}%)" to TerminalCyan
          Sandbox.InstallState.Verifying -> "Verifying SHA-256 Checksum..." to PlanPurpleGlow
          Sandbox.InstallState.Extracting -> "Extracting Ubuntu Rootfs..." to WarningOrange
          Sandbox.InstallState.Provisioning -> "Installing OpenDroid Core & Mem0 Environment..." to BuildGreenGlow
          Sandbox.InstallState.Ready -> "Ready" to BuildGreenGlow
          is Sandbox.InstallState.Error -> "Installation Error" to ErrorRed
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(stateColor.copy(alpha = 0.15f))
            .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(stateColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(stateText, style = MaterialTheme.typography.labelSmall, color = stateColor, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar
        when (state) {
          is Sandbox.InstallState.Downloading -> {
            LinearProgressIndicator(
              progress = { state.progressPercent / 100f },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              color = TerminalCyan,
              trackColor = SurfaceElevated
            )
          }
          Sandbox.InstallState.Verifying,
          Sandbox.InstallState.Extracting,
          Sandbox.InstallState.Provisioning -> {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              color = stateColor,
              trackColor = SurfaceElevated
            )
          }
          else -> {
            Spacer(modifier = Modifier.height(6.dp))
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Terminal Console Output Window
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(8.dp)
        ) {
          if (logs.isEmpty()) {
            Text(
              "Installation logs will appear here...\nTap 'Begin Ubuntu Setup' to download and bootstrap your sandbox.",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              fontSize = 10.sp
            )
          } else {
            LazyColumn(state = listState) {
              items(logs) { line ->
                Text(
                  text = line,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true)) ErrorRed else TerminalCyan,
                  fontSize = 10.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Button
        val isWorking = state is Sandbox.InstallState.Downloading ||
            state == Sandbox.InstallState.Verifying ||
            state == Sandbox.InstallState.Extracting ||
            state == Sandbox.InstallState.Provisioning

        Button(
          onClick = onStartInstall,
          enabled = !isWorking,
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
          if (isWorking) {
            Text("Installing Environment...", color = TextPrimary, fontWeight = FontWeight.Bold)
          } else if (state is Sandbox.InstallState.Error) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry Setup", color = TextPrimary, fontWeight = FontWeight.Bold)
          } else {
            Icon(Icons.Default.Download, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Begin Ubuntu 26.04 Setup", color = TextPrimary, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
