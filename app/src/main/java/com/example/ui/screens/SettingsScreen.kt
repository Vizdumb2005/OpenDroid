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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import com.example.data.composio.ComposioManager
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.OpenCodeModel
import com.example.data.model.VmConfig
import com.example.service.OpenCodeBackgroundService
import com.example.ui.components.VmAllocationCard
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

@Composable
fun SettingsScreen(
  preferredModel: String,
  autoApproveTools: Boolean,
  vmConfig: VmConfig,
  deviceInfo: DeviceHardwareInfo,
  models: List<OpenCodeModel>,
  onUpdateModel: (String) -> Unit,
  onToggleModelEnabled: (String) -> Unit,
  onUpdateAutoApprove: (Boolean) -> Unit,
  onUpdateVmConfig: (VmConfig) -> Unit,
  composioManager: ComposioManager? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE) }
  var runInBackground by remember { mutableStateOf(prefs.getBoolean("run_in_background", true)) }
  var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }

  val scrollState = rememberScrollState()
  val tableScrollState = rememberScrollState()

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
        Text("OpenCode Settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Official OpenCode Model Catalog, Composio & Linux VM Sandbox", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
      }
    }

    // VM Hardware Allocation Card
    VmAllocationCard(
      vmConfig = vmConfig,
      deviceInfo = deviceInfo,
      onUpdateConfig = onUpdateVmConfig
    )

    // OpenCode Free Models Catalog Table (exact representation of OpenCode model catalog)
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("OpenCode Model Catalog", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }
          Text(
            "ALL MODELS FREE ($0.00)",
            style = MaterialTheme.typography.labelSmall,
            color = BuildGreenGlow,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier
              .background(BuildGreenGlow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "OpenCode official free models catalog. BigPickle is always free and default.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Table Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Model", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
          Text("Features", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
          Text("Input", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
          Text("Output", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
          Text("Cache", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
          Text("Active", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Table Rows
        models.forEach { model ->
          val isSelected = preferredModel.equals(model.id, ignoreCase = true)

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) SurfaceElevated else SurfaceCard)
              .border(1.dp, if (isSelected) PlanPurpleGlow else BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
              .clickable { onUpdateModel(model.id) }
              .padding(horizontal = 8.dp, vertical = 8.dp)
              .testTag("model_row_${model.id}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Model Column
            Column(modifier = Modifier.weight(2.2f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = model.displayName,
                  color = TextPrimary,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (model.isDefault) "Default" else "Free",
                  color = if (model.isDefault) PlanPurpleGlow else BuildGreenGlow,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                )
              }
              Text(
                text = model.id,
                color = if (model.id.contains("free") || model.id == "bigpickle") WarningOrange else TextMuted,
                fontSize = 10.sp
              )
            }

            // Features Column (Badges)
            Row(
              modifier = Modifier.weight(1.3f),
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (model.features.hasWeb) {
                Icon(Icons.Default.Language, contentDescription = "Web", tint = TerminalCyan, modifier = Modifier.size(13.dp))
              }
              if (model.features.hasTerminal) {
                Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = BuildGreenGlow, modifier = Modifier.size(13.dp))
              }
              if (model.features.hasVision) {
                Icon(Icons.Default.Visibility, contentDescription = "Vision", tint = PlanPurpleGlow, modifier = Modifier.size(13.dp))
              }
              if (model.features.extraBadge != null) {
                Text(
                  model.features.extraBadge,
                  color = TextSecondary,
                  fontSize = 8.sp,
                  modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp)
                )
              }
            }

            // Pricing Columns
            Text(model.inputCost, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.weight(0.9f))
            Text(model.outputCost, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.weight(0.9f))
            Text(model.cacheReadCost, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(0.9f))

            // Enabled Switch
            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
              Switch(
                checked = model.isEnabled,
                onCheckedChange = { onToggleModelEnabled(model.id) },
                enabled = !model.isDefault, // BigPickle is always enabled
                colors = SwitchDefaults.colors(
                  checkedThumbColor = TextPrimary,
                  checkedTrackColor = BuildGreen,
                  uncheckedThumbColor = TextMuted,
                  uncheckedTrackColor = SurfaceElevated
                ),
                modifier = Modifier.size(28.dp).testTag("toggle_model_${model.id}")
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
        }
      }
    }

    // Composio Tooling Integration Card
    if (composioManager != null) {
      val apps = composioManager.connectedApps.value
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Extension, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Composio Agent Tooling", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Text(
              "CONNECTED",
              style = MaterialTheme.typography.labelSmall,
              color = BuildGreenGlow,
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp,
              modifier = Modifier
                .background(BuildGreenGlow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "Integrates 250+ developer tools directly with OpenCode agents and terminal.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          apps.forEach { app ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(app.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(app.category, color = TextMuted, fontSize = 9.sp)
                }
                Text(app.description, color = TextSecondary, fontSize = 10.sp)
              }
              Switch(
                checked = app.isConnected,
                onCheckedChange = { composioManager.toggleAppConnection(app.id) },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = TextPrimary,
                  checkedTrackColor = BuildGreen,
                  uncheckedThumbColor = TextMuted,
                  uncheckedTrackColor = SurfaceCard
                ),
                modifier = Modifier.size(28.dp).testTag("composio_toggle_${app.id}")
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
          }
        }
      }
    }

    // Background Processing & Battery Optimization Card
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Background & Battery Optimization", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Run in background switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Keep VM Sandbox Running in Background", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Uses Foreground Service with persistent notification", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = runInBackground,
            onCheckedChange = { checked ->
              runInBackground = checked
              prefs.edit().putBoolean("run_in_background", checked).apply()
              if (checked) {
                OpenCodeBackgroundService.startService(context)
              } else {
                OpenCodeBackgroundService.stopService(context)
              }
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

        Spacer(modifier = Modifier.height(8.dp))

        // Start on boot switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Start on Device Boot", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Automatically boot Ubuntu VM when device starts", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = startOnBoot,
            onCheckedChange = { checked ->
              startOnBoot = checked
              prefs.edit().putBoolean("start_on_boot", checked).apply()
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

        Spacer(modifier = Modifier.height(10.dp))

        // Battery Optimization Exemption Button
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        OutlinedButton(
          onClick = {
            try {
              val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
              }
              context.startActivity(intent)
            } catch (e: Exception) {
              val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
              context.startActivity(fallbackIntent)
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("btn_battery_optimization")
        ) {
          Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = if (isIgnoringBatteryOptimizations) BuildGreenGlow else WarningOrange, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isIgnoringBatteryOptimizations) "Battery Optimization Excluded (Unrestricted)" else "Request Battery Optimization Exclusion",
            color = if (isIgnoringBatteryOptimizations) BuildGreenGlow else TextPrimary,
            fontSize = 11.sp
          )
        }
      }
    }
  }
}
