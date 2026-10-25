package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentMode
import com.example.data.model.AgentSession
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.ui.components.AttachmentBar
import com.example.ui.components.SlashCommandPopup
import com.example.ui.components.ToolCallCard
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BuildGreen
import com.example.ui.theme.BuildGreenGlow
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.PlanPurple
import com.example.ui.theme.PlanPurpleGlow
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.UUID

@Composable
fun ChatScreen(
  session: AgentSession?,
  isGenerating: Boolean,
  statusMessage: String?,
  preferredModel: String,
  activeAttachments: List<ChatAttachment>,
  onAddAttachment: (ChatAttachment) -> Unit,
  onRemoveAttachment: (String) -> Unit,
  onSendPrompt: (String) -> Unit,
  onSwitchMode: (AgentMode) -> Unit,
  modifier: Modifier = Modifier
) {
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val chipScroll = rememberScrollState()
  val context = LocalContext.current

  val currentMode = session?.agentMode ?: AgentMode.BUILD
  val messages = session?.messages ?: emptyList()

  // File & Image Picker for chat context
  val attachmentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attached_file"
      val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
      val isImage = mimeType.startsWith("image/")
      val snippet = try {
        if (!isImage) {
          context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(2000) }
        } else null
      } catch (e: Exception) {
        null
      }

      val attachment = ChatAttachment(
        id = UUID.randomUUID().toString(),
        name = name,
        mimeType = mimeType,
        sizeBytes = 24576L,
        uriString = uri.toString(),
        contentSnippet = snippet,
        isImage = isImage
      )
      onAddAttachment(attachment)
    }
  }

  val promptSuggestions = if (currentMode == AgentMode.BUILD) {
    listOf(
      "/superpower Add JWT auth with tests",
      "/ponytail Run code review",
      "/graphify",
      "/terminal",
      "/test",
      "/diff"
    )
  } else {
    listOf(
      "/graphify Analyze module trees",
      "/ponytail Inspect security rules",
      "Draft step-by-step plan for database integration",
      "/build",
      "/help"
    )
  }

  LaunchedEffect(messages.size, isGenerating) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Box(modifier = modifier.fillMaxSize().background(SurfaceDark)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Agent Mode & Free Model Header Banner
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(if (currentMode == AgentMode.BUILD) BuildGreen.copy(alpha = 0.12f) else PlanPurple.copy(alpha = 0.12f))
          .border(
            0.5.dp,
            if (currentMode == AgentMode.BUILD) BuildGreenGlow.copy(alpha = 0.3f) else PlanPurpleGlow.copy(alpha = 0.3f)
          )
          .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (currentMode == AgentMode.BUILD) "BUILD AGENT" else "PLAN AGENT",
            style = MaterialTheme.typography.labelSmall,
            color = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "• $preferredModel [FREE]",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp
          )
        }

        Text(
          text = if (currentMode == AgentMode.BUILD) "Switch to Plan" else "Switch to Build",
          style = MaterialTheme.typography.labelSmall,
          color = TerminalCyan,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clickable {
              onSwitchMode(if (currentMode == AgentMode.BUILD) AgentMode.PLAN else AgentMode.BUILD)
            }
            .padding(4.dp)
            .testTag("chat_switch_mode_pill")
        )
      }

      // Message List
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        items(messages, key = { it.id }) { message ->
          ChatMessageBubble(message = message)
        }

        if (isGenerating && !statusMessage.isNullOrBlank()) {
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
              )
            }
          }
        }
      }

      // Quick Prompt Suggestion Chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SurfaceDark)
          .horizontalScroll(chipScroll)
          .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        promptSuggestions.forEach { suggestion ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(SurfaceCard)
              .border(0.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
              .clickable {
                inputText = suggestion
                onSendPrompt(suggestion)
                inputText = ""
              }
              .padding(horizontal = 10.dp, vertical = 5.dp)
              .testTag("prompt_chip_${suggestion.take(10)}")
          ) {
            Text(
              text = suggestion,
              style = MaterialTheme.typography.labelSmall,
              color = if (suggestion.startsWith("/")) TerminalCyan else TextSecondary,
              fontSize = 11.sp
            )
          }
        }
      }

      // Attachment Preview Bar
      AttachmentBar(
        attachments = activeAttachments,
        onRemoveAttachment = onRemoveAttachment
      )

      // Chat Input Area
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SurfaceCard)
          .border(0.5.dp, BorderSubtle)
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Attachment Button (Paperclip)
        IconButton(
          onClick = { attachmentLauncher.launch("*/*") },
          modifier = Modifier
            .size(40.dp)
            .testTag("chat_attachment_button")
        ) {
          Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = "Attach file or image",
            tint = if (activeAttachments.isNotEmpty()) BuildGreenGlow else TextSecondary,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = {
            Text(
              "Type '/' for OpenCode slash commands & skills...",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          },
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
          maxLines = 4,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (inputText.isNotBlank() && !isGenerating) {
                onSendPrompt(inputText)
                inputText = ""
              }
            }
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CodeBackground,
            unfocusedContainerColor = CodeBackground,
            focusedBorderColor = if (currentMode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow,
            unfocusedBorderColor = BorderSubtle
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("chat_input_field")
        )

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
          onClick = {
            if (inputText.isNotBlank() && !isGenerating) {
              onSendPrompt(inputText)
              inputText = ""
            }
          },
          enabled = (inputText.isNotBlank() || activeAttachments.isNotEmpty()) && !isGenerating,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              if ((inputText.isNotBlank() || activeAttachments.isNotEmpty()) && !isGenerating) {
                if (currentMode == AgentMode.BUILD) BuildGreen else PlanPurple
              } else SurfaceElevated
            )
            .testTag("chat_send_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send prompt",
            tint = if ((inputText.isNotBlank() || activeAttachments.isNotEmpty()) && !isGenerating) TextPrimary else TextMuted,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // Slash Commands Auto-Complete Popup (Floating directly above input box)
    if (inputText.startsWith("/")) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(bottom = 70.dp, start = 8.dp, end = 8.dp)
      ) {
        SlashCommandPopup(
          filterQuery = inputText,
          onSelectCommand = { cmd ->
            if (cmd.usage.contains("<") || cmd.usage.contains("[")) {
              inputText = "${cmd.command} "
            } else {
              onSendPrompt(cmd.command)
              inputText = ""
            }
          }
        )
      }
    }
  }
}

@Composable
fun ChatMessageBubble(
  message: ChatMessage,
  modifier: Modifier = Modifier
) {
  val isUser = message.sender == "user"
  val isSystem = message.sender == "system"

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(bottom = 3.dp, start = 4.dp, end = 4.dp)
    ) {
      if (!isUser) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (message.mode == AgentMode.BUILD) BuildGreenGlow else PlanPurpleGlow)
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        text = if (isUser) "You" else if (isSystem) "System" else "OpenCode Agent (${message.mode.name})",
        style = MaterialTheme.typography.labelSmall,
        color = if (isUser) TextMuted else if (isSystem) TerminalCyan else TextSecondary,
        fontSize = 11.sp
      )
    }

    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(if (isUser) SurfaceElevated else SurfaceCard)
        .border(
          1.dp,
          if (isUser) BorderHighlight else BorderSubtle,
          RoundedCornerShape(10.dp)
        )
        .padding(12.dp)
    ) {
      Column {
        // Render attachments if attached to this message
        if (message.attachments.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            message.attachments.forEach { att ->
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(SurfaceDark)
                  .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  if (att.isImage) Icons.Default.Image else Icons.Default.Description,
                  contentDescription = null,
                  tint = TerminalCyan,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(att.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 10.sp)
              }
            }
          }
        }

        if (message.text.isNotBlank()) {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            lineHeight = 20.sp
          )
        }

        if (message.toolCalls.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Operations Executed:",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.toolCalls.forEach { tool ->
              ToolCallCard(tool = tool)
            }
          }
        }
      }
    }
  }
}
