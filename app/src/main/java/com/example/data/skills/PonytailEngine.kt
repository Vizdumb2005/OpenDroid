package com.example.data.skills

import com.example.data.model.WorkspaceFile

data class CodeIssue(
  val filePath: String,
  val line: Int,
  val rule: String,
  val message: String,
  val severity: IssueSeverity, // CRITICAL, WARNING, INFO
  val suggestedFix: String? = null
)

enum class IssueSeverity {
  CRITICAL,
  WARNING,
  INFO
}

data class PonytailReport(
  val issues: List<CodeIssue>,
  val score: Int, // 0 to 100
  val summary: String
)

object PonytailEngine {

  fun reviewCodebase(files: Map<String, WorkspaceFile>): PonytailReport {
    val issues = mutableListOf<CodeIssue>()

    files.values.forEach { file ->
      file.content.lines().forEachIndexed { index, line ->
        val lineNum = index + 1

        // Check for hardcoded credentials
        if (line.contains("password ===") || line.contains("password == '")) {
          issues.add(
            CodeIssue(
              filePath = file.path,
              line = lineNum,
              rule = "SEC001-HARDCODED-CREDENTIALS",
              message = "Hardcoded password check detected. Move to env vars or password hashing (e.g. bcrypt).",
              severity = IssueSeverity.CRITICAL,
              suggestedFix = "use hashPassword(password) with process.env.AUTH_SECRET"
            )
          )
        }

        // Check for TODOs
        if (line.contains("// TODO") || line.contains("/* TODO")) {
          issues.add(
            CodeIssue(
              filePath = file.path,
              line = lineNum,
              rule = "CODE002-UNRESOLVED-TODO",
              message = "Unresolved TODO detected: ${line.trim()}",
              severity = IssueSeverity.INFO
            )
          )
        }

        // Check for missing strict typing
        if (line.contains(": any") || line.contains("any[]")) {
          issues.add(
            CodeIssue(
              filePath = file.path,
              line = lineNum,
              rule = "TYPE003-ANY-TYPE-USAGE",
              message = "Untyped 'any' usage diminishes TypeScript type safety.",
              severity = IssueSeverity.WARNING,
              suggestedFix = "Replace 'any' with explicit interface or unknown"
            )
          )
        }
      }
    }

    val score = (100 - (issues.count { it.severity == IssueSeverity.CRITICAL } * 20) - (issues.count { it.severity == IssueSeverity.WARNING } * 5)).coerceIn(40, 100)

    val summary = buildString {
      appendLine("Ponytail Code Review Summary:")
      appendLine("• Code Health Score: $score/100")
      appendLine("• Total Findings: ${issues.size} (Critical: ${issues.count { it.severity == IssueSeverity.CRITICAL }}, Warnings: ${issues.count { it.severity == IssueSeverity.WARNING }})")
      if (issues.isEmpty()) {
        appendLine("Clean code! No anti-patterns or security vulnerabilities detected.")
      }
    }

    return PonytailReport(issues, score, summary)
  }
}
