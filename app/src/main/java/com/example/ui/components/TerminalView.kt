package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalEntry
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

@Composable
fun TerminalView(
  entries: List<TerminalEntry>,
  onExecuteCommand: (String) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier
) {
  var commandInput by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val chipScroll = rememberScrollState()

  val quickCommands = listOf("ls -la", "test", "build", "git status", "git diff", "cat src/auth.ts", "tree", "help")

  LaunchedEffect(entries.size) {
    if (entries.isNotEmpty()) {
      listState.animateScrollToItem(entries.size - 1)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CodeBackground)
  ) {
    // Terminal Top Header Bar
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
          imageVector = Icons.Default.Terminal,
          contentDescription = null,
          tint = TerminalCyan,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "opencode-bash • ~/workspace",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
      }

      IconButton(
        onClick = onClear,
        modifier = Modifier
          .size(28.dp)
          .testTag("terminal_clear_button")
      ) {
        Icon(
          imageVector = Icons.Default.Clear,
          contentDescription = "Clear terminal",
          tint = TextSecondary,
          modifier = Modifier.size(14.dp)
        )
      }
    }

    // Quick Command Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceElevated)
        .horizontalScroll(chipScroll)
        .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      quickCommands.forEach { cmd ->
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceCard)
            .border(0.5.dp, BorderHighlight, RoundedCornerShape(4.dp))
            .clickable { onExecuteCommand(cmd) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("terminal_quick_chip_$cmd")
        ) {
          Text(
            text = cmd,
            style = MaterialTheme.typography.labelSmall,
            color = TerminalCyan,
            fontSize = 11.sp
          )
        }
      }
    }

    // Terminal Output Log
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(entries, key = { it.id }) { entry ->
        Column(modifier = Modifier.fillMaxWidth()) {
          // Command line
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "dev@opencode:${entry.workingDir}$ ",
              style = MaterialTheme.typography.labelSmall,
              color = BuildGreenGlow,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = entry.command,
              style = MaterialTheme.typography.labelSmall,
              color = TextPrimary,
              fontWeight = FontWeight.Bold
            )
          }

          // Output
          if (entry.output.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = entry.output,
              style = MaterialTheme.typography.labelSmall,
              color = if (entry.isError) ErrorRed else TextSecondary,
              lineHeight = 16.sp
            )
          }
        }
      }
    }

    // Input Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(SurfaceCard)
        .border(0.5.dp, BorderSubtle)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "$ ",
        style = MaterialTheme.typography.labelMedium,
        color = BuildGreenGlow,
        fontWeight = FontWeight.Bold
      )

      OutlinedTextField(
        value = commandInput,
        onValueChange = { commandInput = it },
        placeholder = {
          Text("Type command (e.g. test, git status, cat README.md)...", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        },
        textStyle = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            if (commandInput.isNotBlank()) {
              onExecuteCommand(commandInput)
              commandInput = ""
            }
          }
        ),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = CodeBackground,
          unfocusedContainerColor = CodeBackground,
          focusedBorderColor = TerminalCyan,
          unfocusedBorderColor = BorderSubtle
        ),
        modifier = Modifier
          .weight(1f)
          .testTag("terminal_input_field")
      )

      Spacer(modifier = Modifier.width(6.dp))

      IconButton(
        onClick = {
          if (commandInput.isNotBlank()) {
            onExecuteCommand(commandInput)
            commandInput = ""
          }
        },
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(BuildGreenGlow.copy(alpha = 0.2f))
          .testTag("terminal_run_button")
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Run command",
          tint = BuildGreenGlow,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
