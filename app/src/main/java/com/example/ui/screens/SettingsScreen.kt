package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig
import com.example.service.OpenCodeBackgroundService
import com.example.ui.components.VmAllocationCard
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
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
fun SettingsScreen(
  preferredModel: String,
  autoApproveTools: Boolean,
  vmConfig: VmConfig,
  deviceInfo: DeviceHardwareInfo,
  onUpdateModel: (String) -> Unit,
  onUpdateAutoApprove: (Boolean) -> Unit,
  onUpdateVmConfig: (VmConfig) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE) }
  var runInBackground by remember { mutableStateOf(prefs.getBoolean("run_in_background", true)) }
  var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }

  // Exclusively free models from the OpenCode catalog
  val freeOpenCodeModels = listOf(
    "opencode/free-flash" to "OpenCode Free Flash (Ultra-fast code synthesis, free tier)",
    "opencode/free-coder" to "OpenCode Free Coder (Qwen 2.5 Coder 32B specialized coding model, free tier)",
    "opencode/free-llama" to "OpenCode Free Llama (Llama 3.3 70B architecture & planning, free tier)",
    "opencode/local-embedded" to "OpenCode Local Sandbox (On-device engine, 100% offline & zero cost)"
  )

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .verticalScroll(scrollState)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(Icons.Default.Settings, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text("OpenCode Configuration", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("VM Sandbox, Free Models & Background Services", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
      }
    }

    // VM Hardware Allocation Card
    VmAllocationCard(
      vmConfig = vmConfig,
      deviceInfo = deviceInfo,
      onUpdateConfig = onUpdateVmConfig
    )

    // OpenCode Free Models Catalog Card
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("OpenCode Free Model Catalog", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Text(
            "NO API KEY REQUIRED",
            style = MaterialTheme.typography.labelSmall,
            color = BuildGreenGlow,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier
              .background(BuildGreenGlow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "OpenCode operates exclusively on free models. Paid tiers and external key configurations are omitted.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        freeOpenCodeModels.forEach { (modelId, desc) ->
          val isSelected = preferredModel == modelId
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) SurfaceElevated else SurfaceCard)
              .border(1.dp, if (isSelected) PlanPurpleGlow else BorderSubtle, RoundedCornerShape(8.dp))
              .clickable { onUpdateModel(modelId) }
              .padding(12.dp)
              .testTag("free_model_$modelId"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modelId, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "FREE",
                  style = MaterialTheme.typography.labelSmall,
                  color = BuildGreenGlow,
                  fontWeight = FontWeight.Bold,
                  fontSize = 9.sp
                )
              }
              Text(desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
            }
            if (isSelected) {
              Icon(Icons.Default.Check, contentDescription = "Selected", tint = PlanPurpleGlow, modifier = Modifier.size(18.dp))
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }
      }
    }

    // Background Processing & Battery Optimization Exemption Card
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Background & Battery Optimization", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Run in Background Switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Keep VM Sandbox Active in Background", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Keeps Ubuntu terminal and long-running agent tasks executing when switching apps.", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = runInBackground,
            onCheckedChange = {
              runInBackground = it
              prefs.edit().putBoolean("run_in_background", it).apply()
              if (it) OpenCodeBackgroundService.startService(context) else OpenCodeBackgroundService.stopService(context)
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = TextPrimary,
              checkedTrackColor = BuildGreen,
              uncheckedThumbColor = TextMuted,
              uncheckedTrackColor = SurfaceElevated
            ),
            modifier = Modifier.testTag("switch_run_in_background")
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Start on Boot Switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Startup on Device Boot", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Automatically initialize the sandboxed Ubuntu runtime when device powers on.", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = startOnBoot,
            onCheckedChange = {
              startOnBoot = it
              prefs.edit().putBoolean("start_on_boot", it).apply()
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = TextPrimary,
              checkedTrackColor = BuildGreen,
              uncheckedThumbColor = TextMuted,
              uncheckedTrackColor = SurfaceElevated
            ),
            modifier = Modifier.testTag("switch_start_on_boot")
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Battery Optimization Exclusion Button
        OutlinedButton(
          onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
              val packageName = context.packageName
              if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                  data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_request_battery_optimization")
        ) {
          Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Request Battery Optimization Exclusion", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Unrestricted Data Saver Button
        OutlinedButton(
          onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
              val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
              }
              try {
                context.startActivity(intent)
              } catch (e: Exception) {
                // Fallback to app details
                val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(appIntent)
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_unrestricted_data_access")
        ) {
          Icon(Icons.Default.DataUsage, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Unrestricted Background Data Access", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Auto-Approve Tool Execution Card
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Auto-Approve Tool Execution", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
              "Allows OpenCode Build Agent to run commands and write files inside the sandbox without manual confirmation.",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              fontSize = 11.sp
            )
          }
          Switch(
            checked = autoApproveTools,
            onCheckedChange = onUpdateAutoApprove,
            colors = SwitchDefaults.colors(
              checkedThumbColor = TextPrimary,
              checkedTrackColor = BuildGreen,
              uncheckedThumbColor = TextMuted,
              uncheckedTrackColor = SurfaceElevated
            ),
            modifier = Modifier.testTag("auto_approve_switch")
          )
        }
      }
    }
  }
}
