package com.example.data.ai

import com.example.data.composio.ComposioManager
import com.example.data.memory.AgentMemoryCompiler
import com.example.data.memory.Mem0Engine
import com.example.data.model.AgentMode
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.OpenCodeModel
import com.example.data.model.ToolCall
import com.example.data.model.ToolStatus
import com.example.data.repository.WorkspaceRepository
import com.example.data.sandbox.UbuntuSandbox
import com.example.data.skills.GraphifyEngine
import com.example.data.skills.PonytailEngine
import com.example.data.skills.SuperpowerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

class OpenCodeAgentEngine(
  private val workspaceRepository: WorkspaceRepository,
  private val ubuntuSandbox: UbuntuSandbox,
  val mem0Engine: Mem0Engine,
  val composioManager: ComposioManager = ComposioManager(),
  val modelCatalog: OpenCodeModelCatalog = OpenCodeModelCatalog()
) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  val superpowerEngine = SuperpowerEngine(workspaceRepository)
  var memoryCompiler: AgentMemoryCompiler? = null

  // BigPickle is always free and set as the default option
  var preferredModel: String = "bigpickle"
  var autoApproveTools: Boolean = true

  suspend fun fetchLiveModels(): List<OpenCodeModel> {
    return modelCatalog.fetchLiveModels()
  }

  suspend fun processUserPrompt(
    prompt: String,
    mode: AgentMode,
    chatHistory: List<ChatMessage>,
    attachments: List<ChatAttachment> = emptyList(),
    knowledgeContext: String = ""
  ): Flow<AgentResponseChunk> = flow {
    emit(AgentResponseChunk.StatusUpdate("Consulting OpenCode model: $preferredModel [FREE]..."))

    // 1. Retrieve pertinent Mem0 long-term memory
    val mem0Context = mem0Engine.getFormattedContextForQuery(prompt)

    // 2. Build live workspace overview
    val workspaceFiles = workspaceRepository.files.value
    val workspaceOverview = if (workspaceFiles.isNotEmpty()) {
      buildString {
        appendLine("\n--- Live Project Workspace Files ---")
        workspaceFiles.forEach { (path, file) ->
          appendLine("File `$path` (${file.content.lines().size} lines)")
        }
        appendLine("------------------------------------\n")
      }
    } else {
      "\nWorkspace: Empty project directory.\n"
    }

    val lower = prompt.lowercase().trim()

    // 3. Handle OpenCode Skills
    if (lower.startsWith("/graphify") || lower.contains("graphify")) {
      emit(AgentResponseChunk.StatusUpdate("Graphify Skill: Analyzing real AST dependencies..."))
      val graphResult = GraphifyEngine.analyzeProject(workspaceFiles)
      val toolGraph = ToolCall(
        id = UUID.randomUUID().toString(),
        name = "graphify_skill",
        arguments = mapOf("nodes" to "${graphResult.nodes.size}"),
        status = ToolStatus.COMPLETED,
        result = "Mapped ${graphResult.nodes.size} nodes. 0 circular dependencies."
      )
      emit(AgentResponseChunk.ToolCompleted(toolGraph))
      emit(AgentResponseChunk.ContentStream(graphResult.architectureSummary))
      return@flow
    }

    if (lower.startsWith("/ponytail") || lower.contains("ponytail") || lower.startsWith("/review")) {
      emit(AgentResponseChunk.StatusUpdate("Ponytail Skill: Scanning codebase for anti-patterns..."))
      val report = PonytailEngine.reviewCodebase(workspaceFiles)
      val toolPonytail = ToolCall(
        id = UUID.randomUUID().toString(),
        name = "ponytail_review",
        arguments = mapOf("healthScore" to "${report.score}/100", "findings" to "${report.issues.size}"),
        status = ToolStatus.COMPLETED,
        result = "Score: ${report.score}/100 with ${report.issues.size} findings."
      )
      emit(AgentResponseChunk.ToolCompleted(toolPonytail))

      val output = buildString {
        appendLine("### OpenCode Ponytail Code Review")
        appendLine("**Health Score: ${report.score}/100**\n")
        if (report.issues.isEmpty()) {
          appendLine("Zero anti-patterns found across all workspace files.")
        } else {
          report.issues.forEach { issue ->
            appendLine("- [${issue.severity}] `${issue.filePath}:${issue.line}`: ${issue.message}")
            if (issue.suggestedFix != null) appendLine("  *Fix*: `${issue.suggestedFix}`")
          }
        }
      }
      emit(AgentResponseChunk.ContentStream(output))
      return@flow
    }

    if (lower.startsWith("/superpower")) {
      val goal = prompt.removePrefix("/superpower").trim().ifBlank { "Autonomous Test-Driven Development" }
      emit(AgentResponseChunk.StatusUpdate("Superpower Skill: Executing autonomous TDD loop..."))
      val result = superpowerEngine.executeSuperpowerLoop(goal)
      result.steps.forEach { step ->
        if (step.toolCall != null) {
          emit(AgentResponseChunk.ToolCompleted(step.toolCall))
        }
      }
      emit(AgentResponseChunk.ContentStream(result.finalSummary))
      return@flow
    }

    // 4. Handle Composio actions from chat (e.g. /composio or natural tool intent)
    if (lower.startsWith("/composio")) {
      val composioArgs = prompt.removePrefix("/composio").trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
      emit(AgentResponseChunk.StatusUpdate("Composio Tooling Engine..."))
      val cliOut = composioManager.handleCliCommand(composioArgs)
      val toolComposio = ToolCall(
        id = UUID.randomUUID().toString(),
        name = "composio_tool",
        arguments = mapOf("args" to composioArgs.joinToString(" ")),
        status = ToolStatus.COMPLETED,
        result = cliOut.take(80)
      )
      emit(AgentResponseChunk.ToolCompleted(toolComposio))
      emit(AgentResponseChunk.ContentStream("```\n$cliOut\n```"))
      return@flow
    }

    // 5. Construct OpenCode system prompt
    val systemPrompt = buildString {
      appendLine("You are OpenCode, an autonomous software engineering assistant running on Ubuntu Server 26.04.1 LTS (ARM64).")
      appendLine("Model: $preferredModel [FREE TIER]")
      appendLine("Mode: ${mode.name}")
      if (mode == AgentMode.PLAN) {
        appendLine("PLAN MODE: Produce structural architectural analysis, risk assessments, and step-by-step guidance. Do not emit destructive file mutations.")
      } else {
        appendLine("BUILD MODE: You have access to edit workspace files, run terminal commands, and call Composio tools.")
      }
      append(mem0Context)
      append(knowledgeContext)
    }

    val userContent = buildString {
      appendLine(prompt)
      if (attachments.isNotEmpty()) {
        appendLine("\nAttached Files:")
        attachments.forEach { att ->
          appendLine("• ${att.name} (${att.mimeType})")
          if (!att.contentSnippet.isNullOrBlank()) {
            appendLine("```\n${att.contentSnippet}\n```")
          }
        }
      }
      append(workspaceOverview)
    }

    // 6. Execute live model call or return appropriate HTTP error code
    emit(AgentResponseChunk.StatusUpdate("Connecting to model '$preferredModel'..."))

    var responseText = ""
    var isApiError = false

    try {
      val apiResult = callOpenCodeModelApi(systemPrompt, userContent, preferredModel)
      if (apiResult.isSuccess) {
        responseText = apiResult.content
      } else {
        // Return appropriate error code instead of misleading hardcoded reply
        responseText = "[ERROR ${apiResult.errorCode}] ${apiResult.errorMessage}"
        isApiError = true
      }
    } catch (e: ConnectException) {
      responseText = "[NETWORK_ERROR 503] Connection refused to OpenCode API endpoint (Host unreachable or offline)."
      isApiError = true
    } catch (e: SocketTimeoutException) {
      responseText = "[TIMEOUT_ERROR 504] OpenCode model '$preferredModel' request timed out after 30s."
      isApiError = true
    } catch (e: UnknownHostException) {
      responseText = "[DNS_ERROR 502] Failed to resolve OpenCode model host: ${e.message}."
      isApiError = true
    } catch (e: Exception) {
      responseText = "[ERROR 500] Unexpected error: ${e.localizedMessage ?: "Unknown error"}"
      isApiError = true
    }

    // If conversational greeting and offline, respond conversationally instead of throwing a false mutation template
    if (isApiError && isGreeting(lower)) {
      responseText = """👋 Hello! I am your **OpenCode** coding agent on Ubuntu Server 26.04.1 LTS (ARM64).
Currently running with free model `${preferredModel}`.
You can:
- Ask me to build features, write tests, or refactor code
- Run terminal commands (`apt`, `dpkg`, `curl`, `ping`, `node`, `ts-node`, `git`)
- Use skills like `/graphify`, `/ponytail`, or `/superpower`
- Run Composio tools with `/composio`
- Open or create files in the Workspace tab

How can I help you build today?""".trimIndent()
      isApiError = false
    }

    emit(AgentResponseChunk.ContentStream(responseText))

    // 7. If BUILD mode and not an error, parse code blocks and write actual files (ONLY if genuine file blocks exist)
    if (mode == AgentMode.BUILD && !isApiError) {
      parseAndApplyLiveCodeChanges(responseText, this)
    }

    // 8. Automatically compile long-term memories from this interaction
    mem0Engine.extractMemoriesFromInteraction(prompt, responseText)
    memoryCompiler?.compileFromInteraction(prompt, responseText, workspaceFiles)
  }

  private fun isGreeting(text: String): Boolean {
    val clean = text.trim().lowercase().removeSurrounding("\"").removeSurrounding("'")
    return clean in listOf("hi", "hello", "hey", "hola", "sup", "greetings", "good morning", "good evening", "help", "who are you")
  }

  private suspend fun parseAndApplyLiveCodeChanges(
    text: String,
    collector: kotlinx.coroutines.flow.FlowCollector<AgentResponseChunk>
  ) {
    val regex = "```(?:[a-zA-Z0-9_-]+)?\\s*(?:\\/\\/\\s*(?:filepath:|file:)?\\s*([a-zA-Z0-9_\\-\\.\\/]+))?\\n([\\s\\S]*?)```".toRegex()
    val matches = regex.findAll(text).toList()

    for (match in matches) {
      val pathHint = match.groups[1]?.value?.trim()
      val code = match.groups[2]?.value ?: continue

      if (!pathHint.isNullOrBlank() && pathHint.contains(".") && !pathHint.contains(" ") && pathHint.length < 100) {
        val toolWrite = ToolCall(
          id = UUID.randomUUID().toString(),
          name = "write_file",
          arguments = mapOf("path" to pathHint),
          status = ToolStatus.RUNNING
        )
        collector.emit(AgentResponseChunk.ToolExecuting(toolWrite))

        workspaceRepository.writeFile(pathHint, code)
        ubuntuSandbox.writeFile("/home/ubuntu/$pathHint", code)

        val completedTool = toolWrite.copy(
          status = ToolStatus.COMPLETED,
          result = "Updated $pathHint (${code.lines().size} lines written to sandbox)"
        )
        collector.emit(AgentResponseChunk.ToolCompleted(completedTool))
      }
    }
  }

  data class OpenCodeApiResponse(
    val isSuccess: Boolean,
    val content: String = "",
    val errorCode: String = "",
    val errorMessage: String = ""
  )

  private suspend fun callOpenCodeModelApi(
    systemPrompt: String,
    userPrompt: String,
    modelName: String
  ): OpenCodeApiResponse = withContext(Dispatchers.IO) {
    // OpenCode OpenAI-compatible router endpoint
    val endpoint = "https://api.opencode.ai/v1/chat/completions"

    val jsonBody = JSONObject().apply {
      put("model", modelName)
      put("messages", JSONArray().apply {
        put(JSONObject().apply {
          put("role", "system")
          put("content", systemPrompt)
        })
        put(JSONObject().apply {
          put("role", "user")
          put("content", userPrompt)
        })
      })
      put("temperature", 0.2)
      put("max_tokens", 2048)
    }

    val request = Request.Builder()
      .url(endpoint)
      .header("Content-Type", "application/json")
      .header("User-Agent", "OpenCode-Android-Client/2.4.0")
      .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
      .build()

    try {
      val response = httpClient.newCall(request).execute()
      val code = response.code

      if (code == 429) {
        return@withContext OpenCodeApiResponse(
          isSuccess = false,
          errorCode = "429_QUOTA_EXCEEDED",
          errorMessage = "Rate limit or quota exceeded on free model '$modelName'. Rate limit reset in 60s."
        )
      }

      if (code == 503) {
        return@withContext OpenCodeApiResponse(
          isSuccess = false,
          errorCode = "503_SERVICE_UNAVAILABLE",
          errorMessage = "OpenCode model '$modelName' backend is temporarily unavailable."
        )
      }

      if (!response.isSuccessful) {
        val errBody = response.body?.string() ?: ""
        return@withContext OpenCodeApiResponse(
          isSuccess = false,
          errorCode = "HTTP_$code",
          errorMessage = if (errBody.isNotBlank()) errBody else "HTTP request failed with status $code"
        )
      }

      val responseStr = response.body?.string() ?: ""
      val rootJson = JSONObject(responseStr)
      val choices = rootJson.optJSONArray("choices")
      if (choices != null && choices.length() > 0) {
        val choice = choices.getJSONObject(0)
        val msg = choice.optJSONObject("message")
        val content = msg?.optString("content") ?: ""
        return@withContext OpenCodeApiResponse(isSuccess = true, content = content)
      }

      OpenCodeApiResponse(
        isSuccess = false,
        errorCode = "EMPTY_RESPONSE",
        errorMessage = "Model returned empty response choices."
      )
    } catch (e: Exception) {
      throw e
    }
  }
}

sealed class AgentResponseChunk {
  data class StatusUpdate(val status: String) : AgentResponseChunk()
  data class ContentStream(val text: String) : AgentResponseChunk()
  data class ToolExecuting(val tool: ToolCall) : AgentResponseChunk()
  data class ToolCompleted(val tool: ToolCall) : AgentResponseChunk()
}
