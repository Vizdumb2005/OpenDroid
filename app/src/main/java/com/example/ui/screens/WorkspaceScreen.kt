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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
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
fun WorkspaceScreen(
  files: Map<String, WorkspaceFile>,
  selectedFile: WorkspaceFile?,
  onSelectFile: (WorkspaceFile?) -> Unit,
  onSaveFile: (String, String) -> Unit,
  onRevertFile: (String) -> Unit,
  onCreateFile: (String, String) -> Unit,
  onDeleteFile: (String) -> Unit,
  onAskAgent: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var showNewFileDialog by remember { mutableStateOf(false) }
  var newFilePath by remember { mutableStateOf("") }

  if (showNewFileDialog) {
    AlertDialog(
      onDismissRequest = { showNewFileDialog = false },
      title = { Text("Create New File", color = TextPrimary) },
      text = {
        Column {
          Text("Enter path relative to workspace root (e.g., src/services.ts):", color = TextSecondary, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = newFilePath,
            onValueChange = { newFilePath = it },
            placeholder = { Text("src/my_feature.ts", color = TextMuted) },
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
            if (newFilePath.isNotBlank()) {
              onCreateFile(newFilePath.trim(), "")
              showNewFileDialog = false
              newFilePath = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.testTag("new_file_confirm_button")
        ) {
          Text("Create", color = TextPrimary)
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
        onAskAgent = onAskAgent
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
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Folder,
              contentDescription = null,
              tint = TerminalCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Workspace Project Tree",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "(${files.size} files)",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary
            )
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
              contentDescription = "New File",
              tint = BuildGreenGlow,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        // List of workspace files
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(files.values.sortedBy { it.path }) { file ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(1.dp, if (file.isModified) WarningOrange.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(8.dp))
                .clickable { onSelectFile(file) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("workspace_file_item_${file.path}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  imageVector = if (file.extension == "ts" || file.extension == "js" || file.extension == "kt") Icons.Default.Code else Icons.Default.Description,
                  contentDescription = null,
                  tint = if (file.isModified) WarningOrange else TerminalCyan,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = file.path,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary
                    )
                    if (file.isModified) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "MODIFIED",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningOrange,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                  Text(
                    text = "${file.content.lines().size} lines • ${file.content.length} bytes",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { onDeleteFile(file.path) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Icon(
                  imageVector = Icons.Default.ChevronRight,
                  contentDescription = "Open file",
                  tint = TextSecondary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
