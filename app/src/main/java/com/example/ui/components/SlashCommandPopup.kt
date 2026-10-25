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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
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

val ALL_OPENCODE_COMMANDS = listOf(
  SlashCommand("/build", "Build Mode", "Switch to Build agent mode with file editing and shell tool access", "/build", SlashCategory.AGENT),
  SlashCommand("/plan", "Plan Mode", "Switch to Plan agent mode for safe architectural breakdown", "/plan", SlashCategory.AGENT),
  SlashCommand("/graphify", "Graphify Skill", "Visual code graph and module dependency tree analyzer", "/graphify [target]", SlashCategory.SKILL),
  SlashCommand("/ponytail", "Ponytail Skill", "Automated code reviewer, linter, and anti-pattern detector", "/ponytail [file]", SlashCategory.SKILL),
  SlashCommand("/superpower", "Superpower Skill", "Autonomous TDD loop and self-healing implementation supervisor", "/superpower <goal>", SlashCategory.SKILL),
  SlashCommand("/terminal", "Linux Terminal", "Switch to free-use Ubuntu 24.04 LTS root/sudo terminal", "/terminal", SlashCategory.LINUX),
  SlashCommand("/files", "Workspace Files", "Browse repository structure and open code editor", "/files", SlashCategory.PROJECT),
  SlashCommand("/diff", "Git Diffs", "Inspect unified diff of modified files in working tree", "/diff", SlashCategory.PROJECT),
  SlashCommand("/test", "Run Tests", "Execute automated test suites in sandbox", "/test", SlashCategory.LINUX),
  SlashCommand("/commit", "Git Commit", "Stage and commit changes to local git history", "/commit <message>", SlashCategory.PROJECT),
  SlashCommand("/context", "Context & KB", "View active prompt context, attachments, and Universal KB", "/context", SlashCategory.UTILITY),
  SlashCommand("/compact", "Compact Chat", "Summarize and compress conversation history", "/compact", SlashCategory.UTILITY),
  SlashCommand("/cost", "Free Model Cost", "Verify zero-cost usage on OpenCode Free catalog", "/cost", SlashCategory.UTILITY),
  SlashCommand("/doctor", "System Diagnostics", "Run VM sandbox diagnostics, RAM/Disk and health check", "/doctor", SlashCategory.UTILITY),
  SlashCommand("/export", "Export to Android", "Export sandbox files to Android system storage", "/export <path>", SlashCategory.LINUX),
  SlashCommand("/import", "Import from Android", "Import file from Android system into Ubuntu sandbox", "/import", SlashCategory.LINUX),
  SlashCommand("/init", "Scaffold Project", "Initialize new starter microservice or script", "/init [template]", SlashCategory.PROJECT),
  SlashCommand("/model", "Free Model Catalog", "Switch between free OpenCode catalog models", "/model", SlashCategory.UTILITY),
  SlashCommand("/review", "Code Review", "Comprehensive multi-file code review with Ponytail", "/review", SlashCategory.SKILL),
  SlashCommand("/stats", "Project Metrics", "View lines of code, commit counts, and VM utilization", "/stats", SlashCategory.PROJECT),
  SlashCommand("/undo", "Revert Changes", "Revert last file modifications back to original commit", "/undo", SlashCategory.PROJECT),
  SlashCommand("/clear", "Clear Timeline", "Clear messages in the current session", "/clear", SlashCategory.UTILITY),
  SlashCommand("/help", "Help Reference", "Display full OpenCode command and skill index", "/help", SlashCategory.UTILITY)
)

@Composable
fun SlashCommandPopup(
  filterQuery: String,
  onSelectCommand: (SlashCommand) -> Unit,
  modifier: Modifier = Modifier
) {
  val cleanQuery = filterQuery.trim().lowercase()
  val filtered = ALL_OPENCODE_COMMANDS.filter {
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
        Text("OpenCode Commands & Skills", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text("${filtered.size} available", style = MaterialTheme.typography.labelSmall, color = TerminalCyan, fontSize = 10.sp)
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 240.dp)
      ) {
        items(filtered, key = { it.command }) { cmd ->
          val icon = when (cmd.category) {
            SlashCategory.SKILL -> Icons.Default.AutoAwesome
            SlashCategory.AGENT -> Icons.Default.Psychology
            SlashCategory.LINUX -> Icons.Default.Terminal
            SlashCategory.PROJECT -> Icons.Default.Folder
            SlashCategory.UTILITY -> Icons.Default.Tune
          }

          val categoryColor = when (cmd.category) {
            SlashCategory.SKILL -> PlanPurpleGlow
            SlashCategory.AGENT -> BuildGreenGlow
            SlashCategory.LINUX -> TerminalCyan
            SlashCategory.PROJECT -> WarningOrange
            SlashCategory.UTILITY -> TextSecondary
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .clickable { onSelectCommand(cmd) }
              .padding(horizontal = 10.dp, vertical = 7.dp)
              .testTag("slash_item_${cmd.command.removePrefix("/")}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = categoryColor,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = cmd.command,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = BuildGreenGlow
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = cmd.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextPrimary
                )
              }
              Text(
                text = cmd.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
              )
            }
            Text(
              text = cmd.category.name,
              style = MaterialTheme.typography.labelSmall,
              color = categoryColor,
              fontSize = 9.sp,
              modifier = Modifier
                .background(SurfaceElevated, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
            )
          }
        }
      }
    }
  }
}
