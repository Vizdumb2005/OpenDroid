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
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkspaceFile
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
fun DiffViewer(
  file: WorkspaceFile,
  diffText: String,
  onRevert: () -> Unit,
  modifier: Modifier = Modifier
) {
  val lines = diffText.lines()

  val additions = lines.count { it.startsWith("+") && !it.startsWith("+++") }
  val deletions = lines.count { it.startsWith("-") && !it.startsWith("---") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CodeBackground)
  ) {
    // Header
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
          imageVector = Icons.Default.Difference,
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "+$additions",
          style = MaterialTheme.typography.labelSmall,
          color = BuildGreenGlow,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "-$deletions",
          style = MaterialTheme.typography.labelSmall,
          color = ErrorRed,
          fontWeight = FontWeight.Bold
        )
      }

      IconButton(
        onClick = onRevert,
        modifier = Modifier
          .size(28.dp)
          .testTag("diff_revert_file_button")
      ) {
        Icon(
          imageVector = Icons.Default.Restore,
          contentDescription = "Revert this file",
          tint = ErrorRed,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Line by Line Diff List
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    ) {
      items(lines) { line ->
        val isAddition = line.startsWith("+") && !line.startsWith("+++")
        val isDeletion = line.startsWith("-") && !line.startsWith("---")
        val isHeader = line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@")

        val bgColor = when {
          isAddition -> BuildGreenGlow.copy(alpha = 0.15f)
          isDeletion -> ErrorRed.copy(alpha = 0.15f)
          isHeader -> SurfaceElevated
          else -> Color.Transparent
        }

        val textColor = when {
          isAddition -> BuildGreenGlow
          isDeletion -> ErrorRed
          isHeader -> TerminalCyan
          else -> TextPrimary
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}
