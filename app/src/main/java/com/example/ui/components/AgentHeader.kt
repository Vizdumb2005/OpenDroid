package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentMode
import com.example.data.model.AgentSession
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.PlanPurple
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AgentHeader(
  currentMode: AgentMode,
  activeSession: AgentSession?,
  allSessions: List<AgentSession>,
  onSwitchMode: (AgentMode) -> Unit,
  onSwitchSession: (String) -> Unit,
  onCreateSession: () -> Unit,
  modifier: Modifier = Modifier
) {
  var sessionDropdownExpanded by remember { mutableStateOf(false) }

  val modeColor by animateColorAsState(
    targetValue = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow,
    label = "modeColor"
  )

  Surface(
    color = SurfaceDark,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
      // Top row: Brand & Mode Switcher
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Brand logo
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.testTag("brand_logo")
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(SurfaceCard)
              .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = "OpenCode Logo",
              tint = BuildGreenGlow,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "OpenCode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "v2.4",
            style = MaterialTheme.typography.labelSmall,
            color = TerminalCyan,
            modifier = Modifier
              .background(SurfaceCard, RoundedCornerShape(4.dp))
              .border(0.5.dp, BorderSubtle, RoundedCornerShape(4.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp)
          )
        }

        // Dual Agent Mode Switcher Pill
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(2.dp)
            .testTag("mode_switcher_pill"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // BUILD Button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(if (currentMode == AgentMode.BUILD) BuildGreen.copy(alpha = 0.25f) else Color.Transparent)
              .border(
                1.dp,
                if (currentMode == AgentMode.BUILD) BuildGreenGlow else Color.Transparent,
                RoundedCornerShape(16.dp)
              )
              .clickable { onSwitchMode(AgentMode.BUILD) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("mode_build_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (currentMode == AgentMode.BUILD) BuildGreenGlow else TextMuted)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "BUILD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (currentMode == AgentMode.BUILD) BuildGreenGlow else TextSecondary
              )
            }
          }

          // PLAN Button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(if (currentMode == AgentMode.PLAN) PlanPurple.copy(alpha = 0.25f) else Color.Transparent)
              .border(
                1.dp,
                if (currentMode == AgentMode.PLAN) PlanPurpleGlow else Color.Transparent,
                RoundedCornerShape(16.dp)
              )
              .clickable { onSwitchMode(AgentMode.PLAN) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("mode_plan_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (currentMode == AgentMode.PLAN) PlanPurpleGlow else TextMuted)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "PLAN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (currentMode == AgentMode.PLAN) PlanPurpleGlow else TextSecondary
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Bottom Row: Session Selector & Mode Descriptor
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Session Dropdown
        Box {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(SurfaceCard)
              .border(0.5.dp, BorderSubtle, RoundedCornerShape(6.dp))
              .clickable { sessionDropdownExpanded = true }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("session_selector_dropdown"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = activeSession?.title ?: "Select Session",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
              maxLines = 1
            )
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = "Select session",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }

          DropdownMenu(
            expanded = sessionDropdownExpanded,
            onDismissRequest = { sessionDropdownExpanded = false },
            modifier = Modifier
              .background(SurfaceCard)
              .border(1.dp, BorderSubtle)
          ) {
            allSessions.forEach { session ->
              DropdownMenuItem(
                text = {
                  Column {
                    Text(session.title, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("${session.agentMode.name} mode • ${session.messages.size} msgs", color = TextSecondary, fontSize = 11.sp)
                  }
                },
                onClick = {
                  onSwitchSession(session.id)
                  sessionDropdownExpanded = false
                }
              )
            }
            DropdownMenuItem(
              text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Add, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("New Session", color = BuildGreenGlow, fontWeight = FontWeight.Bold)
                }
              },
              onClick = {
                onCreateSession()
                sessionDropdownExpanded = false
              }
            )
          }
        }

        // Mode Status text
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (currentMode == AgentMode.BUILD) Icons.Default.Code else Icons.Default.Psychology,
            contentDescription = null,
            tint = modeColor,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (currentMode == AgentMode.BUILD) "Active Tool Execution" else "Read-Only Analysis",
            style = MaterialTheme.typography.labelSmall,
            color = modeColor
          )
        }
      }
    }
  }
}
