package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.skills.IssueSeverity
import com.example.data.skills.PonytailReport
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
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
fun PonytailViewer(
  report: PonytailReport,
  onApplyFix: (filePath: String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Ponytail • Automated Code Review", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Health Score Banner
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("Repository Health Score", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Text("${report.score}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (report.score >= 80) BuildGreenGlow else WarningOrange)
        }
        Text(
          "${report.issues.size} Findings",
          style = MaterialTheme.typography.labelSmall,
          color = TerminalCyan,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Findings List
    Text("Findings & Anti-Patterns:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(report.issues) { issue ->
        val badgeColor = when (issue.severity) {
          IssueSeverity.CRITICAL -> ErrorRed
          IssueSeverity.WARNING -> WarningOrange
          IssueSeverity.INFO -> TerminalCyan
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(0.5.dp, BorderHighlight, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("ponytail_issue_${issue.rule}")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = issue.severity.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = badgeColor,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("${issue.filePath}:${issue.line}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(issue.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)

            if (issue.suggestedFix != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Text("Fix: ${issue.suggestedFix}", style = MaterialTheme.typography.labelSmall, color = BuildGreenGlow, fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}
