package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitCommit
import com.example.data.model.WorkspaceFile
import com.example.ui.components.DiffViewer
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun DiffScreen(
  modifiedFiles: List<WorkspaceFile>,
  gitCommits: List<GitCommit>,
  commitMessage: String,
  onCommitMessageChange: (String) -> Unit,
  onCommit: () -> Unit,
  onRevertFile: (String) -> Unit,
  diffGenerator: (WorkspaceFile) -> String,
  modifier: Modifier = Modifier
) {
  var selectedModifiedFile by remember { mutableStateOf<WorkspaceFile?>(null) }

  // Sync selected file if modifiedFiles list changes
  val activeFile = selectedModifiedFile?.let { sel ->
    modifiedFiles.find { it.path == sel.path }
  } ?: modifiedFiles.firstOrNull()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceCard)
        .border(0.5.dp, BorderSubtle)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Difference,
          contentDescription = null,
          tint = TerminalCyan,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Git Working Tree",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "(${modifiedFiles.size} changed)",
          style = MaterialTheme.typography.labelSmall,
          color = if (modifiedFiles.isNotEmpty()) WarningOrange else TextMuted
        )
      }
    }

    if (modifiedFiles.isEmpty()) {
      // Clean working tree empty state
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = BuildGreenGlow,
            modifier = Modifier.size(28.dp)
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Working Tree is Clean",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "All files are synced with the latest git commit. Use the agent to make code changes.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }
    } else {
      // Commit Controls Bar
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(SurfaceElevated)
          .border(0.5.dp, BorderHighlight)
          .padding(12.dp)
      ) {
        Text(
          text = "Stage & Commit Changes:",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commitMessage,
            onValueChange = onCommitMessageChange,
            placeholder = { Text("feat: update logic via OpenDroid agent", color = TextMuted, fontSize = 12.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = BuildGreenGlow,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("commit_message_input")
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onCommit,
            colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
            modifier = Modifier.testTag("commit_changes_button")
          ) {
            Icon(Icons.Default.Commit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Commit", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      // Modified Files Tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SurfaceCard)
          .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        modifiedFiles.forEach { file ->
          val isSelected = activeFile?.path == file.path
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) SurfaceElevated else SurfaceCard)
              .border(1.dp, if (isSelected) TerminalCyan else BorderSubtle, RoundedCornerShape(6.dp))
              .clickable { selectedModifiedFile = file }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("diff_tab_${file.path}")
          ) {
            Text(
              text = file.name,
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) TerminalCyan else TextSecondary,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }

      // Diff Viewer for selected file
      if (activeFile != null) {
        Box(modifier = Modifier.weight(1f)) {
          DiffViewer(
            file = activeFile,
            diffText = diffGenerator(activeFile),
            onRevert = { onRevertFile(activeFile.path) }
          )
        }
      }
    }

    // Git Commit Log History
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceCard)
        .border(0.5.dp, BorderSubtle)
        .padding(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Commit, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Commit History", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
      }
      Spacer(modifier = Modifier.height(6.dp))
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(gitCommits) { commit ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(SurfaceElevated)
              .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(commit.message, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
              Text("commit ${commit.hash} • ${commit.author}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
            }
            Text(
              "${commit.changedFiles.size} files",
              style = MaterialTheme.typography.labelSmall,
              color = TerminalCyan,
              fontSize = 10.sp
            )
          }
        }
      }
    }
  }
}
