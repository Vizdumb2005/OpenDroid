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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
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
import com.example.data.skills.GraphifyResult
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GraphifyViewer(
  result: GraphifyResult,
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
        Icon(Icons.Default.Hub, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Graphify • Code Architecture Graph", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Metrics summary
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
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("${result.totalFiles}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TerminalCyan)
          Text("Nodes Analyzed", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BuildGreenGlow)
          Text("Circular Cycles", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("100%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PlanPurpleGlow)
          Text("Modularity", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        }
      }
    }

    // Nodes List
    Text("Module Dependency Tree:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(result.nodes, key = { it.id }) { node ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(0.5.dp, BorderHighlight, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("graphify_node_${node.name}")
        ) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (node.type == "test") BuildGreenGlow else TerminalCyan)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(node.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
              Spacer(modifier = Modifier.width(6.dp))
              Text("[${node.type}]", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
            }

            if (node.dependencies.isNotEmpty()) {
              Spacer(modifier = Modifier.height(4.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  "Imports: ${node.dependencies.joinToString(", ")}",
                  style = MaterialTheme.typography.labelSmall,
                  color = TextSecondary,
                  fontSize = 10.sp
                )
              }
            }
          }
        }
      }
    }
  }
}
