package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatAttachment
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AttachmentBar(
  attachments: List<ChatAttachment>,
  onRemoveAttachment: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  if (attachments.isEmpty()) return

  val scrollState = rememberScrollState()

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(SurfaceElevated)
      .horizontalScroll(scrollState)
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .testTag("attachment_bar"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text("Context:", style = MaterialTheme.typography.labelSmall, color = TextMuted)

    attachments.forEach { att ->
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceCard)
          .border(0.5.dp, BorderSubtle, RoundedCornerShape(8.dp))
          .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
          .testTag("attachment_chip_${att.name}"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (att.isImage) Icons.Default.Image else Icons.Default.Description,
          contentDescription = null,
          tint = TerminalCyan,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = att.name,
          style = MaterialTheme.typography.labelSmall,
          color = TextPrimary,
          fontSize = 11.sp,
          maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "(${att.sizeBytes / 1024} KB)",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 9.sp
        )
        IconButton(
          onClick = { onRemoveAttachment(att.id) },
          modifier = Modifier
            .size(20.dp)
            .testTag("attachment_remove_${att.name}")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove attachment",
            tint = TextMuted,
            modifier = Modifier.size(12.dp)
          )
        }
      }
    }
  }
}
