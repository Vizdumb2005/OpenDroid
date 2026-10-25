package com.example.data.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class OcSession(val id: String, val title: String, val created: Long, val updated: Long, val directory: String)

data class OcModel(
  val id: String,
  val name: String,
  val providerId: String,
  val free: Boolean,
  val toolCall: Boolean,
  val reasoning: Boolean,
  val vision: Boolean,
  val inputCost: Double,
  val outputCost: Double,
  val cacheReadCost: Double,
  val cacheWriteCost: Double,
  val contextLimit: Long,
)

data class OcProvider(val id: String, val name: String, val connected: Boolean, val models: List<OcModel>)

data class OcPermission(
  val id: String,
  val sessionId: String,
  val permission: String,
  val patterns: List<String>,
  val callId: String?,
)

data class OcFileDiff(val path: String, val status: String, val additions: Int, val deletions: Int, val patch: String)

sealed interface OcEvent {
  data class PartUpdated(val sessionId: String, val part: JSONObject) : OcEvent
  data class PartDelta(val sessionId: String, val messageId: String, val partId: String, val field: String, val delta: String) : OcEvent
  data class MessageUpdated(val sessionId: String, val info: JSONObject) : OcEvent
  data class MessageRemoved(val sessionId: String, val messageId: String) : OcEvent
  data class SessionStatus(val sessionId: String, val type: String) : OcEvent
  data class SessionIdle(val sessionId: String) : OcEvent
  data class SessionError(val sessionId: String?, val message: String) : OcEvent
  data class SessionUpdated(val session: OcSession) : OcEvent
  data class PermissionAsked(val request: OcPermission) : OcEvent
  data class FileEdited(val path: String) : OcEvent
  data object Connected : OcEvent
}

/**
 * Typed client for the unmodified `opencode serve` HTTP API (see GET /doc on the running server).
 * Only public endpoints are used; the OpenCode binary is not patched in any way.
 */
class OpenCodeClient(
  private val baseUrl: String,
  private val password: () -> String,
  private val username: String = "opencode",
) {
  constructor(secretStore: com.example.data.security.SecretStore, baseUrl: String = "http://127.0.0.1:4096") : this(
    baseUrl = baseUrl,
    password = { secretStore.serverPassword() }
  )

  private val http = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  private val sse = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

  private val json = "application/json".toMediaType()

  private fun req(path: String) = Request.Builder()
    .url(baseUrl + path)
    .header("Authorization", Credentials.basic(username, password()))
    .header("x-opencode-directory", "/root/workspace")

  private suspend fun call(builder: Request.Builder): String = withContext(Dispatchers.IO) {
    http.newCall(builder.build()).execute().use { r ->
      val body = r.body?.string().orEmpty()
      if (!r.isSuccessful) throw IOException("OpenCode ${r.code}: ${body.take(300)}")
      body
    }
  }

  suspend fun health(): Boolean = try {
    JSONObject(call(req("/global/health"))).optBoolean("healthy", false)
  } catch (_: Exception) { false }

  suspend fun version(): String = try {
    JSONObject(call(req("/global/health"))).optString("version")
  } catch (_: Exception) { "" }

  private fun parseSession(o: JSONObject) = OcSession(
    id = o.getString("id"),
    title = o.optString("title", "Session"),
    created = o.optJSONObject("time")?.optLong("created") ?: 0L,
    updated = o.optJSONObject("time")?.optLong("updated") ?: 0L,
    directory = o.optString("directory"),
  )

  suspend fun listSessions(): List<OcSession> {
    val arr = JSONArray(call(req("/session")))
    return (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
      .filter { !it.title.startsWith("Child session") }
      .sortedByDescending { it.updated }
  }

  suspend fun createSession(title: String? = null): OcSession {
    val body = JSONObject().apply { if (title != null) put("title", title) }
    return parseSession(JSONObject(call(req("/session").post(body.toString().toRequestBody(json)))))
  }

  suspend fun deleteSession(id: String) { call(req("/session/$id").delete()) }

  suspend fun messages(sessionId: String): JSONArray = JSONArray(call(req("/session/$sessionId/message")))

  /** Starts a turn; progress and the reply arrive through [events]. */
  suspend fun prompt(
    sessionId: String,
    text: String,
    agent: String = "build",
    providerId: String? = null,
    modelId: String? = null,
    files: List<Triple<String, String, String>> = emptyList(), // (mime, filename, data-url)
  ): Result<Unit> = runCatching {
    val parts = JSONArray().put(JSONObject().put("type", "text").put("text", text))
    files.forEach { (mime, name, url) ->
      parts.put(JSONObject().put("type", "file").put("mime", mime).put("filename", name).put("url", url))
    }
    val body = JSONObject().put("agent", agent).put("parts", parts)
    if (!providerId.isNullOrBlank() && !modelId.isNullOrBlank()) {
      body.put("model", JSONObject().put("providerID", providerId).put("modelID", modelId))
    }
    call(req("/session/$sessionId/prompt_async").post(body.toString().toRequestBody(json)))
  }

  suspend fun promptResult(
    sessionId: String,
    text: String,
    agent: String = "build",
    providerId: String? = null,
    modelId: String? = null,
  ): Result<Unit> = prompt(sessionId, text, agent, providerId, modelId)

  suspend fun abort(sessionId: String) { call(req("/session/$sessionId/abort").post("{}".toRequestBody(json))) }

  suspend fun replyPermission(requestId: String, reply: String) {
    val body = JSONObject().put("reply", reply)
    call(req("/permission/$requestId/reply").post(body.toString().toRequestBody(json)))
  }

  suspend fun pendingPermissions(): List<OcPermission> {
    val arr = JSONArray(call(req("/permission")))
    return (0 until arr.length()).map { parsePermission(arr.getJSONObject(it)) }
  }

  private fun parsePermission(o: JSONObject) = OcPermission(
    id = o.getString("id"),
    sessionId = o.getString("sessionID"),
    permission = o.optString("permission"),
    patterns = o.optJSONArray("patterns")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
    callId = o.optJSONObject("tool")?.optString("callID"),
  )

  suspend fun diff(sessionId: String): List<OcFileDiff> {
    val arr = JSONArray(call(req("/session/$sessionId/diff")))
    return (0 until arr.length()).map {
      val o = arr.getJSONObject(it)
      OcFileDiff(
        path = o.optString("path", o.optString("file")),
        status = o.optString("status", "modified"),
        additions = o.optInt("additions"),
        deletions = o.optInt("deletions"),
        patch = o.optString("patch"),
      )
    }
  }

  /** Providers with their models; `connected` providers have credentials or are free. */
  suspend fun providers(): List<OcProvider> {
    val o = JSONObject(call(req("/provider")))
    val all = o.optJSONArray("all") ?: JSONArray()
    val connected = o.optJSONArray("connected")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
    return (0 until all.length()).map { i ->
      val p = all.getJSONObject(i)
      val pid = p.getString("id")
      val models = p.optJSONObject("models") ?: JSONObject()
      OcProvider(
        id = pid,
        name = p.optString("name", pid),
        connected = pid in connected,
        models = models.keys().asSequence().map { k -> parseModel(pid, models.getJSONObject(k)) }.toList(),
      )
    }
  }

  private fun parseModel(pid: String, m: JSONObject): OcModel {
    val cost = m.optJSONObject("cost")
    val cache = cost?.optJSONObject("cache")
    val caps = m.optJSONObject("capabilities")
    val input = cost?.optDouble("input", 0.0) ?: 0.0
    val output = cost?.optDouble("output", 0.0) ?: 0.0
    return OcModel(
      id = m.getString("id"),
      name = m.optString("name", m.getString("id")),
      providerId = pid,
      free = input == 0.0 && output == 0.0,
      toolCall = caps?.optBoolean("toolcall", true) ?: true,
      reasoning = caps?.optBoolean("reasoning", false) ?: false,
      vision = caps?.optJSONObject("input")?.optBoolean("image", false) ?: false,
      inputCost = input,
      outputCost = output,
      cacheReadCost = cache?.optDouble("read", 0.0) ?: 0.0,
      cacheWriteCost = cache?.optDouble("write", 0.0) ?: 0.0,
      contextLimit = m.optJSONObject("limit")?.optLong("context") ?: 0L,
    )
  }

  suspend fun setApiKey(providerId: String, key: String) {
    val body = JSONObject().put("type", "api").put("key", key)
    call(req("/auth/$providerId").put(body.toString().toRequestBody(json)))
  }

  suspend fun config(): JSONObject = JSONObject(call(req("/config")))

  suspend fun fileContent(path: String): String =
    JSONObject(call(req("/file/content?path=" + java.net.URLEncoder.encode(path, "UTF-8")))).optString("content")

  suspend fun mcpStatus(): JSONObject = JSONObject(call(req("/mcp")))

  /** Server-sent events stream. Completes (throws) if the connection drops; callers should retry. */
  fun events(): Flow<OcEvent> = flow {
    val response = sse.newCall(req("/event").header("Accept", "text/event-stream").build()).execute()
    response.use { r ->
      if (!r.isSuccessful) throw IOException("event stream HTTP ${r.code}")
      emit(OcEvent.Connected)
      val src = r.body!!.source()
      val data = StringBuilder()
      while (currentCoroutineContext().isActive) {
        val line = src.readUtf8Line() ?: break
        if (line.startsWith("data:")) {
          data.append(line.removePrefix("data:").trimStart())
        } else if (line.isEmpty() && data.isNotEmpty()) {
          parseEvent(data.toString())?.let { emit(it) }
          data.clear()
        }
      }
    }
  }.flowOn(Dispatchers.IO)

  private fun parseEvent(raw: String): OcEvent? = try {
    var o = JSONObject(raw)
    o.optJSONObject("payload")?.let { o = it } // /global/event wraps the event
    val props = o.optJSONObject("properties") ?: JSONObject()
    when (o.optString("type")) {
      "message.part.updated" -> OcEvent.PartUpdated(props.getString("sessionID"), props.getJSONObject("part"))
      "message.part.delta" -> OcEvent.PartDelta(
        props.getString("sessionID"), props.getString("messageID"), props.getString("partID"),
        props.getString("field"), props.getString("delta"),
      )
      "message.updated" -> OcEvent.MessageUpdated(props.getString("sessionID"), props.getJSONObject("info"))
      "message.removed" -> OcEvent.MessageRemoved(props.getString("sessionID"), props.getString("messageID"))
      "session.status" -> OcEvent.SessionStatus(props.getString("sessionID"), props.getJSONObject("status").optString("type"))
      "session.idle" -> OcEvent.SessionIdle(props.getString("sessionID"))
      "session.error" -> OcEvent.SessionError(
        props.optString("sessionID").ifBlank { null },
        props.optJSONObject("error")?.let { e -> e.optJSONObject("data")?.optString("message") ?: e.optString("name") } ?: "Unknown error",
      )
      "session.updated", "session.created" -> props.optJSONObject("info")?.let { OcEvent.SessionUpdated(parseSession(it)) }
      "permission.asked" -> OcEvent.PermissionAsked(parsePermission(props))
      "file.edited" -> OcEvent.FileEdited(props.optString("file"))
      else -> null
    }
  } catch (_: Exception) { null }
}
