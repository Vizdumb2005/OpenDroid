package com.example.data.model

enum class AgentMode(val label: String, val description: String) {
  BUILD(
    label = "BUILD",
    description = "Full agent access: creates/edits files, executes shell commands, and applies diffs."
  ),
  PLAN(
    label = "PLAN",
    description = "Read-only architecture agent: analyzes files, drafts plans, and explores approaches safely."
  )
}
