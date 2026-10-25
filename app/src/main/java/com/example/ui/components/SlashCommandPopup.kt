package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SlashCategory
import com.example.data.model.SlashCommand
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

val ALL_OPENDROID_COMMANDS = listOf(
  SlashCommand("/build", "Build Mode", "Switch to Build agent mode with file editing and shell tool access", "/build", SlashCategory.AGENT),
  SlashCommand("/plan", "Plan Mode", "Switch to Plan agent mode for safe architectural breakdown", "/plan", SlashCategory.AGENT),
  SlashCommand("/terminal", "Linux Terminal", "Switch to Ubuntu 26.04.1 LTS ARM64 root terminal", "/terminal", SlashCategory.LINUX),
  SlashCommand("/files", "Workspace Files", "Browse repository structure and open mobile programmer IDE", "/files", SlashCategory.PROJECT),
  SlashCommand("/diff", "Git Diffs", "Inspect unified diff of modified files in working tree", "/diff", SlashCategory.PROJECT),
  SlashCommand("/mem0", "Mem0 Long-Term Memory", "Query or manage local vector memory store (/mem0 list, /mem0 add, /mem0 clear)", "/mem0 [add <text>|clear]", SlashCategory.SKILL),
  SlashCommand("/composio", "Composio Tool Router", "Inspect active cloud developer tool sessions and MCP bridges", "/composio", SlashCategory.SKILL),
  SlashCommand("/doctor", "System Diagnostics", "Run hardware diagnostics, RAM/disk limits, and daemon health check", "/doctor", SlashCategory.UTILITY),
  SlashCommand("/test", "Run Tests", "Execute automated test suites in sandbox", "/test", SlashCategory.LINUX),
  SlashCommand("/commit", "Git Commit", "Stage and commit changes to local git history", "/commit <message>", SlashCategory.PROJECT),
  SlashCommand("/context", "Context Directives", "View active prompt directives and AGENTS.md rules", "/context", SlashCategory.UTILITY),
  SlashCommand("/clear", "Clear Timeline", "Clear messages in the current session", "/clear", SlashCategory.UTILITY),
  SlashCommand("/help", "Help Reference", "Display full OpenDroid command index", "/help", SlashCategory.UTILITY)
)

@Composable
fun SlashCommandPopup(
  filterQuery: String,
  onSelectCommand: (SlashCommand) -> Unit,
  modifier: Modifier = Modifier
) {
  val cleanQuery = filterQuery.trim().lowercase()
  val filtered = ALL_OPENDROID_COMMANDS.filter {
    cleanQuery == "/" || it.command.lowercase().contains(cleanQuery) || it.name.lowercase().contains(cleanQuery.removePrefix("/"))
  }

  if (filtered.isEmpty()) return

  Card(
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    shape = RoundedCornerShape(10.dp),
    elevation = CardDefaults.cardElevation(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, BorderHighlight, RoundedCornerShape(10.dp))
      .testTag("slash_command_popup")
  ) {
    Column(modifier = Modifier.padding(6.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("OpenDroid Commands & Tools", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text("${filtered.size} available", style = MaterialTheme.typography.labelSmall, color = TerminalCyan, fontSize = 10.sp)
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 240.dp)
      ) {
        items(filtered, key = { it.command }) { cmd ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .clickable { onSelectCommand(cmd) }
              .padding(horizontal = 8.dp, vertical = 8.dp)
              .testTag("slash_item_${cmd.command.removePrefix("/")}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              val icon = when (cmd.category) {
                SlashCategory.AGENT -> Icons.Default.AutoAwesome
                SlashCategory.LINUX -> Icons.Default.Terminal
                SlashCategory.PROJECT -> Icons.Default.Folder
                SlashCategory.SKILL -> Icons.Default.Extension
                SlashCategory.UTILITY -> Icons.Default.Tune
              }
              val iconColor = when (cmd.category) {
                SlashCategory.AGENT -> BuildGreenGlow
                SlashCategory.LINUX -> TerminalCyan
                SlashCategory.PROJECT -> PlanPurpleGlow
                SlashCategory.SKILL -> WarningOrange
                SlashCategory.UTILITY -> TextSecondary
              }

              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
              )

              Spacer(modifier = Modifier.width(8.dp))

              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = cmd.command,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = cmd.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                  )
                }
                Text(
                  text = cmd.description,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextMuted,
                  fontSize = 10.sp,
                  maxLines = 1
                )
              }
            }

            Box(
              modifier = Modifier
                .background(SurfaceElevated, RoundedCornerShape(4.dp))
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
              Text(
                text = cmd.category.name,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 9.sp
              )
            }
          }
        }
      }
    }
  }
}
