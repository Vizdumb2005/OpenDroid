package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.sandbox.SandboxFile
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun FileTransferScreen(
  sandboxFiles: Map<String, SandboxFile>,
  onImportFile: (name: String, content: String) -> Unit,
  onOpenInTerminal: (path: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var lastTransferStatus by remember { mutableStateOf<String?>(null) }

  // System file picker to import from Android into Ubuntu sandbox
  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val resolver = context.contentResolver
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file.txt"
        val inputStream = resolver.openInputStream(uri)
        val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        onImportFile(fileName, content)
        lastTransferStatus = "Imported $fileName into /mnt/android_share/inbox/"
      } catch (e: Exception) {
        lastTransferStatus = "Import failed: ${e.localizedMessage}"
      }
    }
  }

  val sharedFiles = sandboxFiles.values.filter {
    it.path.startsWith("/mnt/android_share") || it.path.startsWith("/home/ubuntu/downloads")
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(SurfaceDark)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(22.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Android ⇄ Ubuntu Sandbox Transfers", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    }

    // Transfer Action Cards
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Import Button Card
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
          .clickable { filePicker.launch("*/*") }
          .testTag("transfer_import_from_android_button")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("Import to Ubuntu", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("From Android storage into sandbox /mnt/inbox", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        }
      }

      // Quick Mount Info Card
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Icon(Icons.Default.Folder, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("Sandbox Isolation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Android protected • Transfers via /mnt/android_share", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        }
      }
    }

    if (lastTransferStatus != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceElevated)
          .border(0.5.dp, BorderHighlight, RoundedCornerShape(8.dp))
          .padding(10.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Check, contentDescription = null, tint = BuildGreenGlow, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(lastTransferStatus!!, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
      }
    }

    // Shared Files List
    Column(modifier = Modifier.weight(1f)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Files in /mnt/android_share (${sharedFiles.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      }
      Spacer(modifier = Modifier.height(8.dp))

      if (sharedFiles.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Description, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No files currently transferred.", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("Use 'Import to Ubuntu' above or save files in /mnt/android_share", color = TextMuted, fontSize = 11.sp)
          }
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(sharedFiles, key = { it.path }) { file ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(file.path, style = MaterialTheme.typography.labelSmall, color = TerminalCyan, fontSize = 10.sp)
                Text("${file.sizeBytes} bytes • ${file.permissions} ${file.owner}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
              }

              // Export to Android Share action
              IconButton(
                onClick = {
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, file.content)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Export to Android"))
                },
                modifier = Modifier.testTag("export_file_${file.name}")
              ) {
                Icon(Icons.Default.Share, contentDescription = "Export to Android", tint = BuildGreenGlow, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    }
  }
}
