package com.example.data.model

data class ChatMessage(
  val id: String,
  val sender: String, // "user", "assistant", "system"
  val text: String,
  val mode: AgentMode = AgentMode.BUILD,
  val timestamp: Long = System.currentTimeMillis(),
  val toolCalls: List<ToolCall> = emptyList(),
  val attachments: List<ChatAttachment> = emptyList(),
  val isStreaming: Boolean = false,
  val thoughts: String? = null
)
