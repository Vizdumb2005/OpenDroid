package com.example.data.model

data class TerminalEntry(
  val id: String,
  val command: String,
  val output: String,
  val isError: Boolean = false,
  val timestamp: Long = System.currentTimeMillis(),
  val workingDir: String = "~/workspace"
)

data class GitCommit(
  val hash: String,
  val message: String,
  val author: String = "OpenCode Agent",
  val timestamp: Long = System.currentTimeMillis(),
  val changedFiles: List<String> = emptyList()
)
