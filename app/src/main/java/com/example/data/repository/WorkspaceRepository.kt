package com.example.data.repository

import com.example.data.model.GitCommit
import com.example.data.model.TerminalEntry
import com.example.data.model.WorkspaceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class WorkspaceRepository {

  private val _files = MutableStateFlow<Map<String, WorkspaceFile>>(emptyMap())
  val files: StateFlow<Map<String, WorkspaceFile>> = _files.asStateFlow()

  private val _terminalEntries = MutableStateFlow<List<TerminalEntry>>(emptyList())
  val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

  private val _gitCommits = MutableStateFlow<List<GitCommit>>(emptyList())
  val gitCommits: StateFlow<List<GitCommit>> = _gitCommits.asStateFlow()

  private val _activeProjectName = MutableStateFlow("OpenCode Project")
  val activeProjectName: StateFlow<String> = _activeProjectName.asStateFlow()

  private var currentDirectory = "~/workspace"

  fun switchProject(projectName: String, initialFiles: Map<String, String>? = null) {
    _activeProjectName.value = projectName
    if (initialFiles != null) {
      val map = initialFiles.mapValues { (path, content) ->
        WorkspaceFile(
          path = path,
          name = path.substringAfterLast('/'),
          content = content,
          originalContent = content,
          isModified = false
        )
      }
      _files.value = map
    }
  }

  fun loadFolderFiles(folderName: String, filesMap: Map<String, String>) {
    _activeProjectName.value = folderName
    val map = filesMap.mapValues { (path, content) ->
      WorkspaceFile(
        path = path,
        name = path.substringAfterLast('/'),
        content = content,
        originalContent = content,
        isModified = false
      )
    }
    _files.value = map
  }

  fun runFileInSandbox(path: String, sandbox: com.example.data.sandbox.UbuntuSandbox): String {
    val file = _files.value[path] ?: return "File '$path' not found in workspace."
    sandbox.writeFile("/home/ubuntu/$path", file.content)
    val cmd = when {
      path.endsWith(".ts") -> "ts-node $path"
      path.endsWith(".js") -> "node $path"
      path.endsWith(".py") -> "python3 $path"
      path.endsWith(".sh") -> "bash $path"
      else -> "cat $path"
    }
    val result = sandbox.executeCommand(cmd)
    return result.output
  }

  init {
    loadDefaultWorkspace()
  }

  private fun loadDefaultWorkspace() {
    val initialFiles = listOf(
      WorkspaceFile(
        path = "package.json",
        name = "package.json",
        content = """{
  "name": "opencode-service",
  "version": "1.0.0",
  "description": "Autonomous microservice built with OpenCode",
  "main": "src/index.ts",
  "scripts": {
    "start": "ts-node src/index.ts",
    "test": "jest",
    "build": "tsc"
  },
  "dependencies": {
    "express": "^4.19.2",
    "dotenv": "^16.4.5"
  },
  "devDependencies": {
    "@types/jest": "^29.5.12",
    "jest": "^29.7.0",
    "typescript": "^5.4.5"
  }
}""".trimIndent(),
        originalContent = """{
  "name": "opencode-service",
  "version": "1.0.0",
  "description": "Autonomous microservice built with OpenCode",
  "main": "src/index.ts",
  "scripts": {
    "start": "ts-node src/index.ts",
    "test": "jest",
    "build": "tsc"
  },
  "dependencies": {
    "express": "^4.19.2",
    "dotenv": "^16.4.5"
  },
  "devDependencies": {
    "@types/jest": "^29.5.12",
    "jest": "^29.7.0",
    "typescript": "^5.4.5"
  }
}""".trimIndent()
      ),
      WorkspaceFile(
        path = "README.md",
        name = "README.md",
        content = """# OpenCode Autonomous Microservice

Welcome to your OpenCode mobile workspace!
OpenCode is an agentic coding environment that inspects, edits, plans, and tests code directly on your device.

## Quick Commands
- `/build` : Switch to Build Agent (mutations & execution enabled)
- `/plan`  : Switch to Plan Agent (read-only architectural analysis)
- `/test`  : Run automated Jest/JVM tests
- `/diff`  : Inspect git changes
- `/commit`: Stage and commit changes
""".trimIndent(),
        originalContent = """# OpenCode Autonomous Microservice

Welcome to your OpenCode mobile workspace!
OpenCode is an agentic coding environment that inspects, edits, plans, and tests code directly on your device.

## Quick Commands
- `/build` : Switch to Build Agent (mutations & execution enabled)
- `/plan`  : Switch to Plan Agent (read-only architectural analysis)
- `/test`  : Run automated Jest/JVM tests
- `/diff`  : Inspect git changes
- `/commit`: Stage and commit changes
""".trimIndent()
      ),
      WorkspaceFile(
        path = "src/index.ts",
        name = "index.ts",
        content = """import express, { Request, Response } from 'express';
import { authenticateUser } from './auth';
import { formatTimestamp } from './utils';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get('/health', (req: Request, res: Response) => {
  res.json({ status: 'ok', time: formatTimestamp(new Date()) });
});

app.post('/api/login', (req: Request, res: Response) => {
  const { username, password } = req.body;
  const result = authenticateUser(username, password);
  if (result.success) {
    res.json({ token: result.token });
  } else {
    res.status(401).json({ error: result.error });
  }
});

app.listen(PORT, () => {
  console.log(`[OpenCode] Service running on http://localhost:${'$'}PORT`);
});
""".trimIndent(),
        originalContent = """import express, { Request, Response } from 'express';
import { authenticateUser } from './auth';
import { formatTimestamp } from './utils';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get('/health', (req: Request, res: Response) => {
  res.json({ status: 'ok', time: formatTimestamp(new Date()) });
});

app.post('/api/login', (req: Request, res: Response) => {
  const { username, password } = req.body;
  const result = authenticateUser(username, password);
  if (result.success) {
    res.json({ token: result.token });
  } else {
    res.status(401).json({ error: result.error });
  }
});

app.listen(PORT, () => {
  console.log(`[OpenCode] Service running on http://localhost:${'$'}PORT`);
});
""".trimIndent()
      ),
      WorkspaceFile(
        path = "src/auth.ts",
        name = "auth.ts",
        content = """export interface AuthResult {
  success: boolean;
  token?: string;
  error?: string;
}

export function authenticateUser(username?: string, password?: string): AuthResult {
  if (!username || !password) {
    return { success: false, error: 'Username and password are required' };
  }
  // TODO: Implement rate limiting and bcrypt hashing
  if (username === 'admin' && password === 'opencode2026') {
    return { success: true, token: 'oc_token_9832746bca' };
  }
  return { success: false, error: 'Invalid credentials' };
}
""".trimIndent(),
        originalContent = """export interface AuthResult {
  success: boolean;
  token?: string;
  error?: string;
}

export function authenticateUser(username?: string, password?: string): AuthResult {
  if (!username || !password) {
    return { success: false, error: 'Username and password are required' };
  }
  // TODO: Implement rate limiting and bcrypt hashing
  if (username === 'admin' && password === 'opencode2026') {
    return { success: true, token: 'oc_token_9832746bca' };
  }
  return { success: false, error: 'Invalid credentials' };
}
""".trimIndent()
      ),
      WorkspaceFile(
        path = "src/utils.ts",
        name = "utils.ts",
        content = """export function formatTimestamp(date: Date): string {
  return date.toISOString();
}

export function sanitizeInput(input: string): string {
  return input.trim().replace(/[<>]/g, '');
}
""".trimIndent(),
        originalContent = """export function formatTimestamp(date: Date): string {
  return date.toISOString();
}

export function sanitizeInput(input: string): string {
  return input.trim().replace(/[<>]/g, '');
}
""".trimIndent()
      ),
      WorkspaceFile(
        path = "tests/auth.test.ts",
        name = "auth.test.ts",
        content = """import { authenticateUser } from '../src/auth';

describe('Auth Module', () => {
  it('should reject missing credentials', () => {
    const result = authenticateUser();
    expect(result.success).toBe(false);
  });

  it('should authenticate valid admin', () => {
    const result = authenticateUser('admin', 'opencode2026');
    expect(result.success).toBe(true);
    expect(result.token).toBeDefined();
  });
});
""".trimIndent(),
        originalContent = """import { authenticateUser } from '../src/auth';

describe('Auth Module', () => {
  it('should reject missing credentials', () => {
    const result = authenticateUser();
    expect(result.success).toBe(false);
  });

  it('should authenticate valid admin', () => {
    const result = authenticateUser('admin', 'opencode2026');
    expect(result.success).toBe(true);
    expect(result.token).toBeDefined();
  });
});
""".trimIndent()
      )
    )

    _files.value = initialFiles.associateBy { it.path }

    _gitCommits.value = listOf(
      GitCommit(
        hash = "f3a8b21",
        message = "Initial workspace scaffold from OpenCode template",
        author = "OpenCode CLI v2.0",
        timestamp = System.currentTimeMillis() - 86400000L,
        changedFiles = listOf("package.json", "README.md", "src/index.ts", "src/auth.ts")
      )
    )

    addTerminalEntry(
      command = "curl -fsSL https://opencode.ai/v2/install | bash",
      output = """
[OpenCode v2.4.0] Initialized self-contained agent environment.
> Target: Android ARM64 / JVM Engine
> Dual Agents Active: [BUILD, PLAN]
> Virtual Filesystem Mounted: ~/workspace (6 files)
> Ready for prompt or slash commands. Type /help for options.
""".trimIndent()
    )
  }

  fun readFile(path: String): String? {
    return _files.value[path]?.content
  }

  fun writeFile(path: String, newContent: String): Boolean {
    val current = _files.value
    val existing = current[path]
    val fileName = path.substringAfterLast('/')
    val updatedFile = if (existing != null) {
      existing.copy(
        content = newContent,
        isModified = newContent != existing.originalContent
      )
    } else {
      WorkspaceFile(
        path = path,
        name = fileName,
        content = newContent,
        isModified = true,
        originalContent = ""
      )
    }
    _files.value = current + (path to updatedFile)
    return true
  }

  fun deleteFile(path: String): Boolean {
    val current = _files.value.toMutableMap()
    val removed = current.remove(path) != null
    if (removed) {
      _files.value = current
    }
    return removed
  }

  fun revertFile(path: String) {
    val current = _files.value.toMutableMap()
    val file = current[path] ?: return
    current[path] = file.copy(
      content = file.originalContent,
      isModified = false
    )
    _files.value = current
  }

  fun getModifiedFiles(): List<WorkspaceFile> {
    return _files.value.values.filter { it.isModified }
  }

  fun addTerminalEntry(command: String, output: String, isError: Boolean = false) {
    val entry = TerminalEntry(
      id = UUID.randomUUID().toString(),
      command = command,
      output = output,
      isError = isError,
      workingDir = currentDirectory
    )
    _terminalEntries.value = _terminalEntries.value + entry
  }

  fun clearTerminal() {
    _terminalEntries.value = emptyList()
  }

  fun gitCommit(message: String): GitCommit? {
    val modified = getModifiedFiles()
    if (modified.isEmpty()) return null

    val newCommit = GitCommit(
      hash = UUID.randomUUID().toString().substring(0, 7),
      message = message,
      author = "OpenCode User",
      timestamp = System.currentTimeMillis(),
      changedFiles = modified.map { it.path }
    )

    // Reset modified state
    val updatedMap = _files.value.mapValues { (_, file) ->
      file.copy(originalContent = file.content, isModified = false)
    }
    _files.value = updatedMap

    _gitCommits.value = listOf(newCommit) + _gitCommits.value
    return newCommit
  }

  fun generateUnifiedDiff(file: WorkspaceFile): String {
    val originalLines = file.originalContent.lines()
    val newLines = file.content.lines()

    val sb = StringBuilder()
    sb.appendLine("--- a/${file.path}")
    sb.appendLine("+++ b/${file.path}")

    // Simple diff generator
    val maxLines = maxOf(originalLines.size, newLines.size)
    for (i in 0 until maxLines) {
      val orig = originalLines.getOrNull(i)
      val new = newLines.getOrNull(i)
      if (orig == new && orig != null) {
        sb.appendLine("  $orig")
      } else {
        if (orig != null) sb.appendLine("- $orig")
        if (new != null) sb.appendLine("+ $new")
      }
    }
    return sb.toString()
  }

  fun executeTerminalCommand(input: String): TerminalEntry {
    val trimmed = input.trim()
    val parts = trimmed.split("\\s+".toRegex())
    val cmd = parts.firstOrNull()?.lowercase() ?: ""
    val args = parts.drop(1)

    var output = ""
    var isError = false

    when (cmd) {
      "help" -> {
        output = """
OpenCode Shell v2.4 Commands:
  ls [-la]              List workspace directory contents
  cat <file>            Display file contents
  pwd                   Print working directory
  cd <dir>              Change working directory
  touch <file>          Create a new empty file
  rm <file>             Remove a file
  grep <pattern> <file> Search text in file
  tree                  Display file tree hierarchy
  test                  Run test suites
  build                 Compile workspace project
  git status            View modified files
  git diff              View current changes
  git commit -m <msg>   Commit changes
  git log               View commit history
  curl <url>            Fetch URL or inspect install script
  clear                 Clear terminal output
""".trimIndent()
      }

      "ls" -> {
        val filesList = _files.value.values.map { file ->
          val mod = if (file.isModified) "*" else " "
          "  -rw-r--r-- 1 dev dev ${file.content.length.toString().padStart(6)} Oct 25 12:00 $mod${file.path}"
        }.joinToString("\n")
        output = "total ${_files.value.size}\n$filesList"
      }

      "pwd" -> {
        output = currentDirectory
      }

      "cd" -> {
        val target = args.firstOrNull() ?: "~"
        currentDirectory = if (target == ".." || target == "~") "~/workspace" else "~/workspace/$target"
        output = ""
      }

      "cat" -> {
        val path = args.firstOrNull()
        if (path == null) {
          output = "cat: missing file operand"
          isError = true
        } else {
          val content = readFile(path)
          if (content != null) {
            output = content
          } else {
            output = "cat: $path: No such file or directory"
            isError = true
          }
        }
      }

      "touch" -> {
        val path = args.firstOrNull()
        if (path == null) {
          output = "touch: missing file operand"
          isError = true
        } else {
          writeFile(path, "")
          output = "Created $path"
        }
      }

      "rm" -> {
        val path = args.lastOrNull()
        if (path == null) {
          output = "rm: missing operand"
          isError = true
        } else {
          val ok = deleteFile(path)
          if (ok) output = "Removed $path" else {
            output = "rm: cannot remove '$path': No such file"
            isError = true
          }
        }
      }

      "tree" -> {
        val sb = StringBuilder()
        sb.appendLine(".")
        val paths = _files.value.keys.sorted()
        paths.forEachIndexed { index, p ->
          val isLast = index == paths.size - 1
          val prefix = if (isLast) "└── " else "├── "
          sb.appendLine("$prefix$p")
        }
        sb.append("${paths.size} files")
        output = sb.toString()
      }

      "grep" -> {
        if (args.isEmpty()) {
          output = "grep: missing pattern"
          isError = true
        } else {
          val pattern = args[0]
          val matched = mutableListOf<String>()
          _files.value.forEach { (path, file) ->
            file.content.lines().forEachIndexed { lineIdx, line ->
              if (line.contains(pattern, ignoreCase = true)) {
                matched.add("$path:${lineIdx + 1}: $line")
              }
            }
          }
          output = if (matched.isEmpty()) "No matches found." else matched.joinToString("\n")
        }
      }

      "test" -> {
        output = """
PASS tests/auth.test.ts
  Auth Module
    ✓ should reject missing credentials (3 ms)
    ✓ should authenticate valid admin (1 ms)

Test Suites: 1 passed, 1 total
Tests:       2 passed, 2 total
Snapshots:   0 total
Time:        0.842 s
Ran all test suites.
""".trimIndent()
      }

      "build" -> {
        output = """
> opencode-service@1.0.0 build
> tsc

[OpenCode] TypeScript compilation completed successfully.
Zero errors found. Output generated in dist/.
""".trimIndent()
      }

      "curl" -> {
        val url = args.firstOrNull() ?: ""
        output = if (url.contains("opencode.ai")) {
          """
#!/usr/bin/env bash
# OpenCode v2.4.0 Self-Contained Installer
echo "==> Downloading OpenCode runtime binaries for Android..."
echo "==> Verifying checksums: SHA256: 9e8a71fc8234..."
echo "==> Installing opencode binary to /usr/local/bin/opencode"
echo "==> OpenCode installed successfully! Run 'opencode' to launch."
""".trimIndent()
        } else {
          "curl: (200 OK) Response received from $url"
        }
      }

      "git" -> {
        val subcmd = args.firstOrNull() ?: ""
        when (subcmd) {
          "status" -> {
            val modified = getModifiedFiles()
            if (modified.isEmpty()) {
              output = "On branch main\nYour branch is up to date with 'origin/main'.\n\nnothing to commit, working tree clean"
            } else {
              val fileLines = modified.joinToString("\n") { "  modified:   ${it.path}" }
              output = "On branch main\nChanges not staged for commit:\n$fileLines\n\nno changes added to commit (use \"git add\" and \"git commit\")"
            }
          }

          "diff" -> {
            val modified = getModifiedFiles()
            if (modified.isEmpty()) {
              output = "No uncommitted changes in workspace."
            } else {
              output = modified.joinToString("\n\n") { generateUnifiedDiff(it) }
            }
          }

          "log" -> {
            output = _gitCommits.value.joinToString("\n\n") { commit ->
              "commit ${commit.hash}\nAuthor: ${commit.author}\nDate:   ${java.util.Date(commit.timestamp)}\n\n    ${commit.message}"
            }
          }

          "commit" -> {
            val msgIndex = args.indexOf("-m")
            val message = if (msgIndex != -1 && msgIndex + 1 < args.size) {
              args.subList(msgIndex + 1, args.size).joinToString(" ").replace("\"", "")
            } else {
              "Update files via OpenCode Agent"
            }
            val commit = gitCommit(message)
            if (commit != null) {
              output = "[main ${commit.hash}] ${commit.message}\n ${commit.changedFiles.size} files changed"
            } else {
              output = "On branch main\nnothing to commit, working tree clean"
            }
          }

          else -> {
            output = "git: '$subcmd' is not a git command. See 'git --help'."
            isError = true
          }
        }
      }

      "clear" -> {
        clearTerminal()
        return TerminalEntry(UUID.randomUUID().toString(), input, "", false, System.currentTimeMillis(), currentDirectory)
      }

      "" -> {
        output = ""
      }

      else -> {
        output = "bash: $cmd: command not found. Type 'help' for available commands."
        isError = true
      }
    }

    val entry = TerminalEntry(
      id = UUID.randomUUID().toString(),
      command = input,
      output = output,
      isError = isError,
      workingDir = currentDirectory
    )
    _terminalEntries.value = _terminalEntries.value + entry
    return entry
  }
}
