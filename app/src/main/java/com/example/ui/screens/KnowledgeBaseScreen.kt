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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
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
import com.example.data.memory.Mem0Memory
import com.example.data.model.KnowledgeItem
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

enum class MemoryViewSection {
  MEM0_STORE,
  UNIVERSAL_KNOWLEDGE
}

@Composable
fun KnowledgeBaseScreen(
  items: List<KnowledgeItem>,
  onAddItem: (title: String, content: String, category: String) -> Unit,
  onToggleItem: (id: String) -> Unit,
  onDeleteItem: (id: String) -> Unit,
  mem0Memories: List<Mem0Memory> = emptyList(),
  onAddMem0Memory: (text: String, category: String) -> Unit = { _, _ -> },
  onDeleteMem0Memory: (id: String) -> Unit = {},
  onClearAllMem0: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var activeSection by remember { mutableStateOf(MemoryViewSection.MEM0_STORE) }
  var searchQuery by remember { mutableStateOf("") }

  // Dialog state for Universal Knowledge
  var showAddKbDialog by remember { mutableStateOf(false) }
  var newTitle by remember { mutableStateOf("") }
  var newContent by remember { mutableStateOf("") }
  var newCategory by remember { mutableStateOf("Convention") }

  // Dialog state for Mem0
  var showAddMem0Dialog by remember { mutableStateOf(false) }
  var newMem0Text by remember { mutableStateOf("") }
  var newMem0Category by remember { mutableStateOf("preference") }

  // Dialog for Universal Knowledge
  if (showAddKbDialog) {
    AlertDialog(
      onDismissRequest = { showAddKbDialog = false },
      title = { Text("Add Universal Knowledge Directive", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Title:", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            placeholder = { Text("e.g., Code Formatting", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth().testTag("kb_input_title")
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
            modifier = Modifier.fillMaxWidth().testTag("kb_input_content")
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
              showAddKbDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.testTag("kb_confirm_add_button")
        ) {
          Text("Save Directive", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showAddKbDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = SurfaceCard
    )
  }

  // Dialog for Mem0
  if (showAddMem0Dialog) {
    AlertDialog(
      onDismissRequest = { showAddMem0Dialog = false },
      title = { Text("Add Mem0 Long-Term Memory", color = TextPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Memory Category:", color = TextSecondary, fontSize = 11.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("preference", "fact", "architecture", "context").forEach { cat ->
              val isSelected = newMem0Category == cat
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSelected) SurfaceElevated else SurfaceCard)
                  .border(1.dp, if (isSelected) BuildGreenGlow else BorderSubtle, RoundedCornerShape(4.dp))
                  .clickable { newMem0Category = cat }
                  .padding(horizontal = 6.dp, vertical = 4.dp)
              ) {
                Text(cat.uppercase(), fontSize = 10.sp, color = if (isSelected) BuildGreenGlow else TextSecondary)
              }
            }
          }

          Text("Memory Statement:", color = TextSecondary, fontSize = 11.sp)
          OutlinedTextField(
            value = newMem0Text,
            onValueChange = { newMem0Text = it },
            placeholder = { Text("e.g., Target OS is Ubuntu 26.04.1 LTS Server ARM64", color = TextMuted) },
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CodeBackground,
              unfocusedContainerColor = CodeBackground,
              focusedBorderColor = BuildGreenGlow,
              unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth().testTag("mem0_input_text")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newMem0Text.isNotBlank()) {
              onAddMem0Memory(newMem0Text.trim(), newMem0Category)
              newMem0Text = ""
              showAddMem0Dialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
          modifier = Modifier.testTag("mem0_confirm_add_button")
        ) {
          Text("Store Memory", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showAddMem0Dialog = false }) {
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
    verticalArrangement = Arrangement.spacedBy(12.dp)
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
          Text("Persistent Knowledge & Mem0", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
          Text("Long-term memory layer for autonomous agent reasoning", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
        }
      }

      IconButton(
        onClick = {
          if (activeSection == MemoryViewSection.MEM0_STORE) {
            showAddMem0Dialog = true
          } else {
            showAddKbDialog = true
          }
        },
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(SurfaceElevated)
          .testTag("kb_add_button")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add memory", tint = BuildGreenGlow, modifier = Modifier.size(20.dp))
      }
    }

    // Section Toggle Tabs
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(SurfaceCard)
        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
        .padding(4.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(if (activeSection == MemoryViewSection.MEM0_STORE) SurfaceElevated else SurfaceCard)
          .clickable { activeSection = MemoryViewSection.MEM0_STORE }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            "Mem0 Memories (${mem0Memories.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (activeSection == MemoryViewSection.MEM0_STORE) BuildGreenGlow else TextSecondary
          )
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(if (activeSection == MemoryViewSection.UNIVERSAL_KNOWLEDGE) SurfaceElevated else SurfaceCard)
          .clickable { activeSection = MemoryViewSection.UNIVERSAL_KNOWLEDGE }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Psychology, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            "Directives (${items.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (activeSection == MemoryViewSection.UNIVERSAL_KNOWLEDGE) PlanPurpleGlow else TextSecondary
          )
        }
      }
    }

    // Search bar for memories/directives
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search memories and directives...", color = TextMuted, fontSize = 12.sp) },
      leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
      },
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = CodeBackground,
        unfocusedContainerColor = CodeBackground,
        focusedBorderColor = TerminalCyan,
        unfocusedBorderColor = BorderSubtle
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("memory_search_input")
    )

    if (activeSection == MemoryViewSection.MEM0_STORE) {
      // Mem0 Memory Store View
      val filteredMemories = if (searchQuery.isBlank()) {
        mem0Memories
      } else {
        mem0Memories.filter {
          it.memory.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
      }

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (filteredMemories.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text("No Mem0 memories recorded yet.", color = TextMuted, fontSize = 13.sp)
            }
          }
        } else {
          items(filteredMemories, key = { it.id }) { mem ->
            val catColor = when (mem.category.lowercase()) {
              "architecture" -> PlanPurpleGlow
              "preference" -> BuildGreenGlow
              "constraint" -> WarningOrange
              else -> TerminalCyan
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .testTag("mem0_item_${mem.id}")
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = mem.category.uppercase(),
                      style = MaterialTheme.typography.labelSmall,
                      color = catColor,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier
                        .background(SurfaceElevated, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "confidence: ${(mem.confidence * 100).toInt()}%",
                      style = MaterialTheme.typography.labelSmall,
                      color = TextMuted,
                      fontSize = 9.sp
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = mem.memory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                  )
                }

                IconButton(
                  onClick = { onDeleteMem0Memory(mem.id) },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    } else {
      // Universal Knowledge Directives View
      val filteredItems = if (searchQuery.isBlank()) {
        items
      } else {
        items.filter {
          it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
      }

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (filteredItems.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text("No directives matching filter.", color = TextMuted, fontSize = 13.sp)
            }
          }
        } else {
          items(filteredItems, key = { it.id }) { item ->
            val categoryColor = when (item.category) {
              "Architecture" -> PlanPurpleGlow
              "Security" -> WarningOrange
              "Testing" -> BuildGreenGlow
              else -> TerminalCyan
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(1.dp, if (item.isEnabled) BorderSubtle else BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
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

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = item.content,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (item.isEnabled) TextSecondary else TextMuted,
                  lineHeight = 16.sp,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }
    }
  }
}
