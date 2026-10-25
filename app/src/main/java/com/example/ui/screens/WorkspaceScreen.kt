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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
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
import com.example.data.model.WorkspaceFile
import com.example.ui.components.CodeEditorView
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
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
fun WorkspaceScreen(
  files: Map<String, WorkspaceFile>,
  selectedFile: WorkspaceFile?,
  activeProjectName: String = "OpenCode Project",
  onSelectFile: (WorkspaceFile?) -> Unit,
  onSaveFile: (String, String) -> Unit,
  onRevertFile: (String) -> Unit,
  onCreateFile: (String, String) -> Unit,
  onDeleteFile: (String) -> Unit,
  onAskAgent: (String) -> Unit,
  onRunFile: (String) -> String = { "" },
  onSwitchProject: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var showNewFileDialog by remember { mutableStateOf(false) }
  var showProjectDialog by remember { mutableStateOf(false) }
  var newFileName by remember { mutableStateOf("") }
  var selectedExtension by remember { mutableStateOf(".ts") }
  var newProjectName by remember { mutableStateOf("") }

  val extensionChips = listOf(".ts", ".py", ".js", ".json", ".sh", ".kt", ".md", ".html")

  // Create New File Dialog
  if (showNewFileDialog) {
    AlertDialog(
      onDismissRequest = { showNewFileDialog = false },
      title = { Text("Create New Workspace File", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("File extension:", color = TextSecondary, fontSize = 11.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            extensionChips.take(4).forEach { ext ->
              val isSel = selectedExtension == ext
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSel) SurfaceElevated else SurfaceCard)
                  .border(1.dp, if (isSel) TerminalCyan else BorderSubtle, RoundedCornerShape(4.dp))
                  .clickable { selectedExtension = ext }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(ext, fontSize = 11.sp, color = if (isSel) TerminalCyan else TextSecondary, fontWeight = FontWeight.Bold)
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            extensionChips.drop(4).forEach { ext ->
              val isSel = selectedExtension == ext
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSel) SurfaceElevated else SurfaceCard)
                  .border(1.dp, if (isSel) TerminalCyan else BorderSubtle, RoundedCornerShape(4.dp))
                  .clickable { selectedExtension = ext }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(ext, fontSize = 11.sp, color = if (isSel) TerminalCyan else TextSecondary, fontWeight = FontWeight.Bold)
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("File path (e.g., src/auth, models/user):", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newFileName,
            onValueChange = { newFileName = it },
            placeholder = { Text("src/my_feature$selectedExtension", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_file_input_field")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newFileName.isNotBlank()) {
              val raw = newFileName.trim()
              val finalPath = if (raw.contains(".")) raw else "$raw$selectedExtension"
              onCreateFile(finalPath, "")
              showNewFileDialog = false
              newFileName = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.testTag("new_file_confirm_button")
        ) {
          Text("Create File", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showNewFileDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = SurfaceCard
    )
  }

  // Project Folder Selector Dialog
  if (showProjectDialog) {
    AlertDialog(
      onDismissRequest = { showProjectDialog = false },
      title = { Text("Workspace & Project Folder", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Current Project: $activeProjectName", color = TerminalCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

          Text("Switch Project Folder:", color = TextSecondary, fontSize = 11.sp)
          listOf(
            "OpenCode Microservice" to "src/index.ts, src/auth.ts, express setup",
            "Python AI Workspace" to "scripts/main.py, requirements.txt",
            "Clean Empty Project" to "Empty workspace directory for new development"
          ).forEach { (pName, desc) ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated)
                .border(1.dp, if (activeProjectName == pName) TerminalCyan else BorderSubtle, RoundedCornerShape(8.dp))
                .clickable {
                  onSwitchProject(pName)
                  showProjectDialog = false
                }
                .padding(10.dp)
            ) {
              Column {
                Text(pName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(desc, color = TextSecondary, fontSize = 10.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("Or create named project folder:", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newProjectName,
            onValueChange = { newProjectName = it },
            placeholder = { Text("my-new-app", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth().testTag("new_project_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newProjectName.isNotBlank()) {
              onSwitchProject(newProjectName.trim())
              newProjectName = ""
              showProjectDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen)
        ) {
          Text("Switch", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showProjectDialog = false }) {
          Text("Close", color = TextSecondary)
        }
      },
      containerColor = SurfaceCard
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
  ) {
    if (selectedFile != null) {
      CodeEditorView(
        file = selectedFile,
        onSave = { content -> onSaveFile(selectedFile.path, content) },
        onRevert = { onRevertFile(selectedFile.path) },
        onClose = { onSelectFile(null) },
        onAskAgent = onAskAgent,
        onRunFile = onRunFile
      )
    } else {
      // File Tree / Explorer View
      Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .border(0.5.dp, BorderSubtle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showProjectDialog = true }
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = "Project Folder",
              tint = TerminalCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = activeProjectName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
              )
              Text(
                text = "${files.size} workspace files • tap to switch folder",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { showProjectDialog = true },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(Icons.Default.CreateNewFolder, contentDescription = "Switch folder", tint = TerminalCyan, modifier = Modifier.size(18.dp))
            }

            IconButton(
              onClick = { showNewFileDialog = true },
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceElevated)
                .testTag("workspace_add_file_button")
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create new file",
                tint = BuildGreenGlow,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        // Files List
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (files.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Workspace is empty. Tap '+' to create code files.",
                  color = TextMuted,
                  fontSize = 12.sp
                )
              }
            }
          }

          items(files.values.toList(), key = { it.path }) { file ->
            val isCode = file.extension in listOf("ts", "js", "py", "kt", "json", "sh")
            val icon = if (isCode) Icons.Default.Code else Icons.Default.Description
            val iconTint = when (file.extension) {
              "ts", "js" -> TerminalCyan
              "py" -> WarningOrange
              "json" -> PlanPurpleGlow
              "sh" -> BuildGreenGlow
              else -> TextSecondary
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(
                  width = 1.dp,
                  color = if (file.isModified) WarningOrange.copy(alpha = 0.5f) else BorderSubtle,
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable { onSelectFile(file) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("workspace_file_item_${file.path}")
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = file.path,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                      )
                      if (file.isModified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = "●",
                          color = WarningOrange,
                          fontSize = 8.sp
                        )
                      }
                    }
                    Text(
                      text = "${file.content.lines().size} lines  •  ${file.content.length} bytes",
                      style = MaterialTheme.typography.labelSmall,
                      color = TextMuted,
                      fontSize = 10.sp
                    )
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { onDeleteFile(file.path) },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = "Delete file",
                      tint = TextMuted,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
