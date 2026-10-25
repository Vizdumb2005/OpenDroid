package com.example.data.model

data class WorkspaceFile(
  val path: String, // e.g. "src/index.ts"
  val name: String,
  val isDirectory: Boolean = false,
  val content: String = "",
  val isModified: Boolean = false,
  val originalContent: String = ""
) {
  val extension: String
    get() = if (isDirectory) "" else name.substringAfterLast('.', "")
}
