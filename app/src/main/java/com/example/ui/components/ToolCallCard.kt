package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ToolCall
import com.example.data.model.ToolStatus
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun ToolCallCard(
  tool: ToolCall,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current

  val icon = when (tool.name) {
    "execute_bash" -> Icons.Default.Terminal
    "write_file" -> Icons.Default.Description
    "read_file" -> Icons.Default.Code
    else -> Icons.Default.PlayArrow
  }

  val toolDisplayTitle = when (tool.name) {
    "execute_bash" -> "bash: ${tool.arguments["command"] ?: ""}"
    "write_file" -> "write: ${tool.arguments["path"] ?: ""}"
    "read_file" -> "read: ${tool.arguments["path"] ?: ""}"
    else -> tool.name
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(SurfaceCard)
      .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
      .testTag("tool_call_${tool.name}")
  ) {
    Column {
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { expanded = !expanded }
          .padding(horizontal = 12.dp, vertical = 8.dp),
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
            tint = TerminalCyan,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = toolDisplayTitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          when (tool.status) {
            ToolStatus.RUNNING, ToolStatus.PENDING -> {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = WarningOrange
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Running", style = MaterialTheme.typography.labelSmall, color = WarningOrange, fontSize = 10.sp)
            }
            ToolStatus.COMPLETED -> {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = BuildGreenGlow,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Done", style = MaterialTheme.typography.labelSmall, color = BuildGreenGlow, fontSize = 10.sp)
            }
            ToolStatus.FAILED -> {
              Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Failed",
                tint = ErrorRed,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Error", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontSize = 10.sp)
            }
            else -> {}
          }

          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Expand",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // Collapsible Content
      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(CodeBackground)
            .border(0.5.dp, BorderHighlight)
            .padding(10.dp)
        ) {
          // Arguments
          if (tool.arguments.isNotEmpty()) {
            Text("Arguments:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            tool.arguments.forEach { (k, v) ->
              Text("  $k: $v", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
          }

          // Result output
          if (!tool.result.isNullOrBlank()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Output:", style = MaterialTheme.typography.labelSmall, color = BuildGreenGlow)
              IconButton(
                onClick = { clipboard.setText(AnnotatedString(tool.result)) },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  Icons.Default.ContentCopy,
                  contentDescription = "Copy output",
                  tint = TextSecondary,
                  modifier = Modifier.size(14.dp)
                )
              }
            }
            Text(
              text = tool.result,
              style = MaterialTheme.typography.labelSmall,
              color = TextPrimary,
              modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(4.dp))
                .padding(8.dp)
            )
          }

          // Diff if any
          if (!tool.diff.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Diff:", style = MaterialTheme.typography.labelSmall, color = TerminalCyan)
            Text(
              text = tool.diff,
              style = MaterialTheme.typography.labelSmall,
              color = BuildGreenGlow,
              modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(4.dp))
                .padding(8.dp)
            )
          }

          if (!tool.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Error: ${tool.errorMessage}", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
          }
        }
      }
    }
  }
}
