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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VmAllocationCard(
  vmConfig: VmConfig,
  deviceInfo: DeviceHardwareInfo,
  onUpdateConfig: (VmConfig) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
      .testTag("vm_allocation_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Memory, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Ubuntu Sandbox VM Allocation", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Device Specs & Dynamic Recommendation Banner
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceElevated)
          .border(0.5.dp, BorderHighlight, RoundedCornerShape(8.dp))
          .padding(10.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              "Hardware Recommendation for this Device:",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = BuildGreenGlow
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Device Specs: ${(deviceInfo.totalRamMb / 1024.0).toInt()} GB RAM (${(deviceInfo.availableRamMb / 1024.0).toInt()} GB Avail) • ${deviceInfo.totalStorageGb} GB Storage • ${deviceInfo.cpuCores} Cores (${deviceInfo.architecture})",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Optimal: ${vmConfig.recommendedRamMb} MB RAM • ${vmConfig.recommendedDiskGb} GB Disk",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )

            Button(
              onClick = {
                onUpdateConfig(
                  vmConfig.copy(
                    allocatedRamMb = vmConfig.recommendedRamMb,
                    allocatedDiskGb = vmConfig.recommendedDiskGb,
                    isCustom = false
                  )
                )
              },
              colors = ButtonDefaults.buttonColors(containerColor = BuildGreen),
              modifier = Modifier
                .height(28.dp)
                .testTag("apply_vm_recommendation_button")
            ) {
              Text("Apply Recommended", fontSize = 10.sp, color = TextPrimary)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // RAM Slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Allocated RAM:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
          "${vmConfig.allocatedRamMb} MB (${vmConfig.allocatedRamMb / 1024.0} GB)",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = TerminalCyan
        )
      }
      Slider(
        value = vmConfig.allocatedRamMb.toFloat(),
        onValueChange = { onUpdateConfig(vmConfig.copy(allocatedRamMb = it.toInt(), isCustom = true)) },
        valueRange = 512f..8192f,
        steps = 14,
        colors = SliderDefaults.colors(
          thumbColor = TerminalCyan,
          activeTrackColor = TerminalCyan,
          inactiveTrackColor = SurfaceElevated
        ),
        modifier = Modifier.testTag("vm_ram_slider")
      )

      // Disk Space Slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Allocated Disk Space:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
          "${vmConfig.allocatedDiskGb} GB Storage",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = PlanPurpleGlow
        )
      }
      Slider(
        value = vmConfig.allocatedDiskGb.toFloat(),
        onValueChange = { onUpdateConfig(vmConfig.copy(allocatedDiskGb = it.toInt(), isCustom = true)) },
        valueRange = 2f..32f,
        steps = 14,
        colors = SliderDefaults.colors(
          thumbColor = PlanPurpleGlow,
          activeTrackColor = PlanPurpleGlow,
          inactiveTrackColor = SurfaceElevated
        ),
        modifier = Modifier.testTag("vm_disk_slider")
      )

      // Preset Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          Triple("Minimal", 1024, 4),
          Triple("Balanced", 2048, 8),
          Triple("Performance", 4096, 16)
        ).forEach { (label, r, d) ->
          val isSelected = vmConfig.allocatedRamMb == r && vmConfig.allocatedDiskGb == d
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) SurfaceElevated else SurfaceCard)
              .border(1.dp, if (isSelected) TerminalCyan else BorderSubtle, RoundedCornerShape(6.dp))
              .clickable {
                onUpdateConfig(vmConfig.copy(allocatedRamMb = r, allocatedDiskGb = d, isCustom = true))
              }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isSelected) TerminalCyan else TextPrimary)
              Text("${r / 1024}G / ${d}G", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
            }
          }
        }
      }
    }
  }
}
