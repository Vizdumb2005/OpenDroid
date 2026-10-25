package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkspaceFile
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxFunction
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun CodeEditorView(
  file: WorkspaceFile,
  onSave: (String) -> Unit,
  onRevert: () -> Unit,
  onClose: () -> Unit,
  onAskAgent: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var editorContent by remember(file.path) { mutableStateOf(file.content) }
  val isDirty = editorContent != file.originalContent

  val verticalScroll = rememberScrollState()
  val horizontalScroll = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CodeBackground)
  ) {
    // Toolbar Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceCard)
        .border(0.5.dp, BorderSubtle)
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Description,
          contentDescription = null,
          tint = TerminalCyan,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = file.path,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
        if (isDirty || file.isModified) {
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "● Modified",
            style = MaterialTheme.typography.labelSmall,
            color = WarningOrange,
            fontSize = 11.sp
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        // Ask Agent Button
        IconButton(
          onClick = { onAskAgent("Refactor and review ${file.path}") },
          modifier = Modifier
            .size(32.dp)
            .testTag("editor_ask_agent_button")
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Ask Agent",
            tint = BuildGreenGlow,
            modifier = Modifier.size(16.dp)
          )
        }

        // Revert Button
        if (isDirty || file.isModified) {
          IconButton(
            onClick = {
              editorContent = file.originalContent
              onRevert()
            },
            modifier = Modifier
              .size(32.dp)
              .testTag("editor_revert_button")
          ) {
            Icon(
              imageVector = Icons.Default.Restore,
              contentDescription = "Revert changes",
              tint = ErrorRed,
              modifier = Modifier.size(16.dp)
            )
          }

          // Save Button
          IconButton(
            onClick = { onSave(editorContent) },
            modifier = Modifier
              .size(32.dp)
              .testTag("editor_save_button")
          ) {
            Icon(
              imageVector = Icons.Default.Save,
              contentDescription = "Save file",
              tint = BuildGreenGlow,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        // Close Button
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(32.dp)
            .testTag("editor_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close file",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    // Code lines and text field
    val lines = editorContent.lines()
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(verticalScroll)
    ) {
      // Line numbers column
      Column(
        modifier = Modifier
          .width(42.dp)
          .background(SurfaceElevated.copy(alpha = 0.5f))
          .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.End
      ) {
        lines.forEachIndexed { index, _ ->
          Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
          )
        }
      }

      // Code text area with horizontal scrolling
      Box(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(horizontalScroll)
          .padding(8.dp)
      ) {
        BasicTextField(
          value = editorContent,
          onValueChange = { editorContent = it },
          textStyle = TextStyle(
            fontFamily = JetBrainsMono,
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 18.sp
          ),
          cursorBrush = SolidColor(BuildGreenGlow),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("code_editor_input")
        )
      }
    }

    // Editor Status Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceCard)
        .border(0.5.dp, BorderSubtle)
        .padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Lines: ${lines.size}  •  Chars: ${editorContent.length}",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        fontSize = 10.sp
      )
      Text(
        text = "UTF-8  •  ${file.extension.uppercase()}",
        style = MaterialTheme.typography.labelSmall,
        color = TerminalCyan,
        fontSize = 10.sp
      )
    }
  }
}
