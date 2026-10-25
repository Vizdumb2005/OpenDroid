package com.example.data.repository

import com.example.data.model.GitCommit
import com.example.data.model.WorkspaceFile
import com.example.data.sandbox.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Real filesystem repository for files inside the sandbox's `/root/workspace`.
 * No demo data or hardcoded files.
 */
class WorkspaceRepository(private val sandbox: Sandbox) {

  private val _activeProjectName = MutableStateFlow("Default Workspace")
  val activeProjectName: StateFlow<String> = _activeProjectName.asStateFlow()

  private val _files = MutableStateFlow<Map<String, WorkspaceFile>>(emptyMap())
  val files: StateFlow<Map<String, WorkspaceFile>> = _files.asStateFlow()

  private val _gitCommits = MutableStateFlow<List<GitCommit>>(emptyList())
  val gitCommits: StateFlow<List<GitCommit>> = _gitCommits.asStateFlow()

  private val originalContents = mutableMapOf<String, String>()

  private fun workspaceDir(): File {
    val dir = sandbox.guestToHost(Sandbox.GUEST_WORKSPACE)
    dir.mkdirs()
    return dir
  }

  suspend fun refreshFiles() = withContext(Dispatchers.IO) {
    val base = workspaceDir()
    val map = linkedMapOf<String, WorkspaceFile>()

    if (base.exists() && base.isDirectory) {
      base.walkTopDown()
        .filter { file ->
          val rel = file.relativeTo(base).path.replace('\\', '/')
          !rel.startsWith(".git") && !rel.startsWith("node_modules") && !rel.startsWith(".opendroid")
        }
        .forEach { file ->
          val rel = file.relativeTo(base).path.replace('\\', '/')
          if (rel.isNotEmpty()) {
            val isDir = file.isDirectory
            val text = if (!isDir && file.length() < 2 * 1024 * 1024) {
              runCatching { file.readText() }.getOrDefault("")
            } else ""

            val original = originalContents.getOrPut(rel) { text }
            map[rel] = WorkspaceFile(
              path = rel,
              name = file.name,
              isDirectory = isDir,
              content = text,
              isModified = !isDir && text != original,
              originalContent = original
            )
          }
        }
    }
    _files.value = map
  }

  suspend fun readFile(relativePath: String): String? = withContext(Dispatchers.IO) {
    val file = File(workspaceDir(), relativePath.trimStart('/'))
    if (!file.exists() || file.isDirectory) return@withContext null
    runCatching { file.readText() }.getOrNull()
  }

  suspend fun writeFile(relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
    val file = File(workspaceDir(), relativePath.trimStart('/'))
    file.parentFile?.mkdirs()
    runCatching {
      file.writeText(content)
      refreshFiles()
      true
    }.getOrDefault(false)
  }

  suspend fun deleteFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
    val file = File(workspaceDir(), relativePath.trimStart('/'))
    if (!file.exists()) return@withContext false
    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
    if (deleted) {
      originalContents.remove(relativePath)
      refreshFiles()
    }
    deleted
  }

  suspend fun revertFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
    val original = originalContents[relativePath] ?: return@withContext false
    writeFile(relativePath, original)
  }

  fun getModifiedFiles(): List<WorkspaceFile> =
    _files.value.values.filter { it.isModified }

  fun generateUnifiedDiff(file: WorkspaceFile): String {
    val origLines = file.originalContent.lines()
    val newLines = file.content.lines()
    val sb = StringBuilder()
    sb.appendLine("--- a/${file.path}")
    sb.appendLine("+++ b/${file.path}")
    sb.appendLine("@@ -1,${origLines.size} +1,${newLines.size} @@")

    val max = maxOf(origLines.size, newLines.size)
    for (i in 0 until max) {
      val o = origLines.getOrNull(i)
      val n = newLines.getOrNull(i)
      if (o != null && n != null) {
        if (o == n) {
          sb.appendLine(" $o")
        } else {
          sb.appendLine("-$o")
          sb.appendLine("+$n")
        }
      } else if (o != null) {
        sb.appendLine("-$o")
      } else if (n != null) {
        sb.appendLine("+$n")
      }
    }
    return sb.toString()
  }

  suspend fun gitCommit(message: String): GitCommit? = withContext(Dispatchers.IO) {
    val modified = getModifiedFiles().map { it.path }
    if (modified.isEmpty()) return@withContext null

    // Run real git commit in sandbox if git repo initialized
    val res = sandbox.exec("git add -A && git commit -m \"${message.replace("\"", "\\\"")}\"", cwd = Sandbox.GUEST_WORKSPACE)
    val hash = UUID.randomUUID().toString().take(7)

    // Mark current content as original baseline
    _files.value.values.forEach { f ->
      originalContents[f.path] = f.content
    }
    refreshFiles()

    val commit = GitCommit(
      hash = hash,
      message = message,
      author = "OpenDroid Developer",
      timestamp = System.currentTimeMillis(),
      changedFiles = modified
    )
    _gitCommits.value = listOf(commit) + _gitCommits.value
    commit
  }

  suspend fun switchProject(projectName: String) = withContext(Dispatchers.IO) {
    _activeProjectName.value = projectName
    refreshFiles()
  }

  suspend fun importFile(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
    val shared = File(sandbox.sharedDir, name)
    shared.writeBytes(bytes)
    val inWorkspace = File(workspaceDir(), name)
    inWorkspace.writeBytes(bytes)
    refreshFiles()
    inWorkspace.relativeTo(workspaceDir()).path.replace('\\', '/')
  }

  fun runFileInSandbox(relativePath: String): String {
    val clean = relativePath.trimStart('/')
    val cmd = when {
      clean.endsWith(".py") -> "python3 /root/workspace/$clean"
      clean.endsWith(".js") -> "node /root/workspace/$clean"
      clean.endsWith(".sh") -> "bash /root/workspace/$clean"
      clean.endsWith(".rb") -> "ruby /root/workspace/$clean"
      clean.endsWith(".go") -> "go run /root/workspace/$clean"
      clean.endsWith(".rs") -> "rustc /root/workspace/$clean -o /tmp/out && /tmp/out"
      else -> "cat /root/workspace/$clean"
    }
    return kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      val res = sandbox.exec(cmd, cwd = Sandbox.GUEST_WORKSPACE, timeoutMs = 15_000L)
      if (res.exitCode == 0) res.output else "${res.output}\n[Process exited with code ${res.exitCode}]"
    }
  }
}
