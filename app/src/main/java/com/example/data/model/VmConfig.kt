package com.example.data.model

data class VmConfig(
  val allocatedRamMb: Int = 2048,
  val allocatedDiskGb: Int = 8,
  val isCustom: Boolean = false,
  val recommendedRamMb: Int = 2048,
  val recommendedDiskGb: Int = 8
)

data class DeviceHardwareInfo(
  val totalRamMb: Int,
  val availableRamMb: Int,
  val totalStorageGb: Int,
  val availableStorageGb: Int,
  val cpuCores: Int,
  val architecture: String
)

data class ChatAttachment(
  val id: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Long,
  val uriString: String? = null,
  val contentSnippet: String? = null,
  val isImage: Boolean = false
)

data class KnowledgeItem(
  val id: String,
  val title: String,
  val content: String,
  val category: String = "Convention", // Convention, Architecture, Rule, Snippet
  val isEnabled: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class SlashCommand(
  val command: String, // e.g. "/build"
  val name: String,
  val description: String,
  val usage: String,
  val category: SlashCategory
)

enum class SlashCategory {
  AGENT,
  SKILL,
  LINUX,
  PROJECT,
  UTILITY
}
