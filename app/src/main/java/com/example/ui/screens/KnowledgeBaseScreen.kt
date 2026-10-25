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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.data.model.KnowledgeItem
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.ErrorRed
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
fun KnowledgeBaseScreen(
  items: List<KnowledgeItem>,
  onAddItem: (title: String, content: String, category: String) -> Unit,
  onToggleItem: (id: String) -> Unit,
  onDeleteItem: (id: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var showAddDialog by remember { mutableStateOf(false) }
  var newTitle by remember { mutableStateOf("") }
  var newContent by remember { mutableStateOf("") }
  var newCategory by remember { mutableStateOf("Convention") }

  if (showAddDialog) {
    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Universal Knowledge", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Title:", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            placeholder = { Text("e.g., API Error Formatting", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("kb_input_title")
          )

          Text("Category:", color = TextSecondary, fontSize = 11.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Architecture", "Convention", "Security", "Testing").forEach { cat ->
              val isSelected = newCategory == cat
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSelected) SurfaceElevated else SurfaceCard)
                  .border(1.dp, if (isSelected) PlanPurpleGlow else BorderSubtle, RoundedCornerShape(4.dp))
                  .clickable { newCategory = cat }
                  .padding(horizontal = 6.dp, vertical = 4.dp)
              ) {
                Text(cat, fontSize = 10.sp, color = if (isSelected) PlanPurpleGlow else TextSecondary)
              }
            }
          }

          Text("Rule / Content:", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newContent,
            onValueChange = { newContent = it },
            placeholder = { Text("Describe the rule or pattern for the agent...", color = TextMuted) },
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("kb_input_content")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newTitle.isNotBlank() && newContent.isNotBlank()) {
              onAddItem(newTitle.trim(), newContent.trim(), newCategory)
              newTitle = ""
              newContent = ""
              showAddDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.testTag("kb_confirm_add_button")
        ) {
          Text("Save Knowledge", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showAddDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = SurfaceCard
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Psychology, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text("Universal Knowledge Base", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          Text("Persistent memory across all sessions & projects", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
        }
      }

      IconButton(
        onClick = { showAddDialog = true },
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(SurfaceElevated)
          .testTag("kb_add_button")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add rule", tint = BuildGreenGlow, modifier = Modifier.size(20.dp))
      }
    }

    // Knowledge Items List
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(items, key = { it.id }) { item ->
        val categoryColor = when (item.category) {
          "Architecture" -> PlanPurpleGlow
          "Security" -> WarningOrange
          "Testing" -> BuildGreenGlow
          else -> TerminalCyan
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, if (item.isEnabled) BorderSubtle else BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("kb_item_${item.title}")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = item.category.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = categoryColor,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (item.isEnabled) TextPrimary else TextMuted
                )
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                  checked = item.isEnabled,
                  onCheckedChange = { onToggleItem(item.id) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = BuildGreen,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceElevated
                  ),
                  modifier = Modifier
                    .size(32.dp)
                    .testTag("kb_toggle_${item.title}")
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                  onClick = { onDeleteItem(item.id) },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = item.content,
              style = MaterialTheme.typography.bodyMedium,
              color = if (item.isEnabled) TextSecondary else TextMuted,
              lineHeight = 18.sp,
              fontSize = 12.sp
            )
          }
        }
      }
    }
  }
}
