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
  val arguments: Map<String, String> = emptyMap(),
  val status: ToolStatus = ToolStatus.COMPLETED,
  val result: String? = null,
  val diff: String? = null,
  val errorMessage: String? = null
) {
  val toolName: String get() = name
  val inputSnippet: String get() = arguments["command"] ?: arguments.values.firstOrNull().orEmpty()
  val outputSnippet: String get() = result.orEmpty()
  val isRunning: Boolean get() = status == ToolStatus.RUNNING
  val isSuccess: Boolean get() = status == ToolStatus.COMPLETED
}
