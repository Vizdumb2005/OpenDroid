package com.example.data.model

enum class ToolStatus {
  PENDING,
  APPROVED,
  RUNNING,
  COMPLETED,
  FAILED,
  REJECTED
}

data class ToolCall(
  val id: String,
  val name: String, // e.g., execute_bash, write_file, read_file, search_code, apply_diff
  val arguments: Map<String, String>,
  val status: ToolStatus = ToolStatus.COMPLETED,
  val result: String? = null,
  val diff: String? = null,
  val errorMessage: String? = null
)
