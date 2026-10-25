package com.example.data.model

data class AgentSession(
  val id: String,
  val title: String,
  val agentMode: AgentMode = AgentMode.BUILD,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val messages: List<ChatMessage> = emptyList(),
  val currentBranch: String = "main"
)
