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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.VmConfig
import com.example.data.runtime.SandboxRuntime
import com.example.data.sandbox.Sandbox
import com.example.service.OpenDroidService
import com.example.ui.MainViewModel
import com.example.ui.components.VmAllocationCard
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsState()
  val runtimeStatus by viewModel.runtimeStatus.collectAsState()
  val sandboxState by viewModel.sandboxState.collectAsState()

  val appSettings = viewModel.appSettings
  val secretStore = viewModel.secretStore

  var runInBackground by remember { mutableStateOf(appSettings.runInBackground) }
  var startOnBoot by remember { mutableStateOf(appSettings.startOnBoot) }
  var autoApprove by remember { mutableStateOf(appSettings.autoApproveTools) }

  var composioApiKey by remember { mutableStateOf(secretStore.getComposioKey() ?: "") }
  var composioStatusMsg by remember { mutableStateOf<String?>(null) }
  var isVerifyingComposio by remember { mutableStateOf(false) }

  var mem0ApiKey by remember { mutableStateOf(secretStore.getMem0ApiKey() ?: "") }
  var mem0Provider by remember { mutableStateOf(appSettings.mem0Provider) }

  var showResetDialog by remember { mutableStateOf(false) }

  val scrollState = rememberScrollState()

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("Reinstall Ubuntu Sandbox?", color = TextPrimary) },
      text = {
        Text(
          "This will reset the Ubuntu rootfs and reinstall the official OpenDroid core engine and Mem0 environments. Files in /root/workspace will be preserved if possible.",
          color = TextSecondary,
          fontSize = 12.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            viewModel.triggerSandboxInstall()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
        ) {
          Text("Reinstall Now", color = TextPrimary)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showResetDialog = false }) {
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
        Text("OpenDroid Configuration", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("ARM64 Linux Sandbox, Daemons, Composio & Mem0", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
      }
    }

    // Live Runtime Daemon Monitor Card
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
            Icon(Icons.Default.Memory, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Runtime Daemon Supervisor", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
          }

          IconButton(
            onClick = {
              scope.launch(Dispatchers.IO) {
                viewModel.runtime.startAll()
              }
            },
            modifier = Modifier.size(28.dp)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Restart Daemons", tint = TerminalCyan, modifier = Modifier.size(16.dp))
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Daemon Status Rows
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("OpenDroid Core Engine", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Local Headless Daemon (127.0.0.1:4096)", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (runtimeStatus.openCodeRunning) BuildGreenGlow else ErrorRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              if (runtimeStatus.openCodeRunning) "ACTIVE" else "STOPPED",
              style = MaterialTheme.typography.labelSmall,
              color = if (runtimeStatus.openCodeRunning) BuildGreenGlow else ErrorRed,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Mem0 Engine Server", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("FastAPI Long-Term Vector Memory (127.0.0.1:8765)", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (runtimeStatus.mem0Running) BuildGreenGlow else ErrorRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              if (runtimeStatus.mem0Running) "ACTIVE" else "STOPPED",
              style = MaterialTheme.typography.labelSmall,
              color = if (runtimeStatus.mem0Running) BuildGreenGlow else ErrorRed,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // RSS Watchdog Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Watchdog Memory Governor", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Active process RSS memory tracking", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
          }
          Text(
            "${runtimeStatus.rssMb} MB / ${uiState.vmConfig.allocatedRamMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = if (runtimeStatus.rssMb > uiState.vmConfig.allocatedRamMb * 0.85) WarningOrange else TerminalCyan,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // VM Hardware Allocation Card
    VmAllocationCard(
      vmConfig = uiState.vmConfig,
      deviceInfo = viewModel.deviceInfo,
      onUpdateConfig = { cfg -> viewModel.updateVmConfig(cfg) }
    )

    // Composio Tool Router Integration
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Extension, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Composio Tool Router Integration", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "Connects OpenDroid to 250+ cloud developer tools (GitHub, Linear, Slack, Browserbase, Linux Shell) via Tool Router Sessions and hosted MCP.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = composioApiKey,
          onValueChange = {
            composioApiKey = it
            secretStore.saveComposioKey(it.trim())
          },
          label = { Text("Composio API Key", color = TextSecondary, fontSize = 11.sp) },
          placeholder = { Text("user_...", color = TextMuted, fontSize = 11.sp) },
          textStyle = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TerminalCyan,
            unfocusedBorderColor = BorderSubtle,
            focusedContainerColor = CodeBackground,
            unfocusedContainerColor = CodeBackground
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = {
              if (composioApiKey.isNotBlank()) {
                isVerifyingComposio = true
                composioStatusMsg = "Verifying..."
                scope.launch(Dispatchers.IO) {
                  val result = viewModel.composioClient.listSessions()
                  withContext(Dispatchers.Main) {
                    isVerifyingComposio = false
                    result.fold(
                      onSuccess = { list -> composioStatusMsg = "Verified! (${list.size} active sessions)" },
                      onFailure = { err -> composioStatusMsg = "Error: ${err.localizedMessage ?: "Invalid key"}" }
                    )
                  }
                }
              }
            },
            enabled = !isVerifyingComposio && composioApiKey.isNotBlank(),
            modifier = Modifier.height(36.dp)
          ) {
            Text("Verify API Key", fontSize = 11.sp, color = TerminalCyan)
          }

          if (composioStatusMsg != null) {
            Text(
              text = composioStatusMsg!!,
              style = MaterialTheme.typography.labelSmall,
              color = if (composioStatusMsg!!.startsWith("Verified")) BuildGreenGlow else ErrorRed,
              fontSize = 11.sp
            )
          }
        }
      }
    }

    // Mem0 Long-Term Memory Configuration
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Key, contentDescription = null, tint = PlanPurpleGlow, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Mem0 Memory Embeddings & LLM", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "Mem0 runs locally inside the Ubuntu sandbox with Qdrant vector storage. Configure your LLM API key for automatic memory extraction.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Provider Selector Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("openai", "gemini", "ollama").forEach { prov ->
            val isSel = mem0Provider.equals(prov, ignoreCase = true)
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSel) PlanPurpleGlow.copy(alpha = 0.2f) else SurfaceElevated)
                .border(1.dp, if (isSel) PlanPurpleGlow else BorderSubtle, RoundedCornerShape(6.dp))
                .clickable {
                  mem0Provider = prov
                  appSettings.mem0Provider = prov
                }
                .padding(vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                prov.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSel) PlanPurpleGlow else TextSecondary
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = mem0ApiKey,
          onValueChange = {
            mem0ApiKey = it
            secretStore.saveMem0ApiKey(it.trim())
          },
          label = { Text("$mem0Provider API Key", color = TextSecondary, fontSize = 11.sp) },
          placeholder = { Text("sk-...", color = TextMuted, fontSize = 11.sp) },
          textStyle = MaterialTheme.typography.labelSmall.copy(color = TextPrimary),
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PlanPurpleGlow,
            unfocusedBorderColor = BorderSubtle,
            focusedContainerColor = CodeBackground,
            unfocusedContainerColor = CodeBackground
          )
        )
      }
    }

    // Background Processing & Battery Optimization
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
          Text("Background Execution & Battery Exemption", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Run in background switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Keep Daemons Running in Background", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Runs persistent OpenDroid foreground service with wake lock", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = runInBackground,
            onCheckedChange = { checked ->
              runInBackground = checked
              appSettings.runInBackground = checked
              if (checked) {
                OpenDroidService.start(context)
              } else {
                OpenDroidService.stop(context)
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
            Text("Automatically boot Ubuntu sandbox & daemons when phone turns on", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
          }
          Switch(
            checked = startOnBoot,
            onCheckedChange = { checked ->
              startOnBoot = checked
              appSettings.startOnBoot = checked
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

        Spacer(modifier = Modifier.height(10.dp))

        // Phantom Process Killer Info Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .border(0.5.dp, BorderHighlight, RoundedCornerShape(8.dp))
            .padding(10.dp)
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text("Android 12+ Phantom Process Killer Notice", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = WarningOrange)
              Text(
                "Android 12+ limits background child processes to 32 max. If long builds get killed, run via ADB:\n`adb shell \"/system/bin/device_config put activity_manager max_phantom_processes 2147483647\"`",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
              )
            }
          }
        }
      }
    }

    // Sandbox Maintenance & Reinstall
    Card(
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text("Sandbox Maintenance", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "Ubuntu Base 26.04.1 rootfs installed at `/data/data/.../files/rootfs`.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = { showResetDialog = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Reset / Reinstall Ubuntu Sandbox", fontSize = 11.sp, color = ErrorRed)
        }
      }
    }
  }
}
