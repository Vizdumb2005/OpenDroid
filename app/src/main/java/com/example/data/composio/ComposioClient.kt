package com.example.data.composio

import com.example.data.security.SecretStore
import com.example.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ComposioToolkit(
  val slug: String,
  val name: String,
  val description: String,
  val connected: Boolean,
  val connectedAccountId: String?,
  val noAuth: Boolean,
)

data class ComposioSession(val id: String, val mcpUrl: String)

/**
 * Real Composio integration through its Tool Router sessions API.
 * The session's hosted MCP endpoint is what OpenCode talks to (see OpenCodeConfig).
 */
class ComposioClient(private val settings: AppSettings, private val secrets: SecretStore) {

  companion object {
    private const val BASE = "https://backend.composio.dev/api/v3.1"
    private const val BASE_V3 = "https://backend.composio.dev/api/v3"
    const val CALLBACK = "opendroid://composio-callback"
  }

  private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
  private val json = "application/json".toMediaType()

  val hasKey: Boolean get() = secrets.get(SecretStore.COMPOSIO_API_KEY).isNotBlank()

  private fun apiKey() = secrets.get(SecretStore.COMPOSIO_API_KEY).also {
    if (it.isBlank()) throw IOException("Add your Composio API key in Settings first")
  }

  private suspend fun call(method: String, url: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
    val rb = body?.toString()?.toRequestBody(json) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(json)
    val req = Request.Builder().url(url).header("x-api-key", apiKey()).method(method, rb).build()
    http.newCall(req).execute().use { r ->
      val text = r.body?.string().orEmpty()
      if (!r.isSuccessful) {
        val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
        throw IOException(if (r.code == 401) "Composio rejected the API key" else msg ?: "Composio HTTP ${r.code}")
      }
      text
    }
  }

  /** Stable per-install user id so connected accounts survive app restarts. */
  private fun userId(): String {
    var id = secrets.get("composio_user_id")
    if (id.isBlank()) {
      id = "opendroid-" + java.util.UUID.randomUUID().toString().take(12)
      secrets.put("composio_user_id", id)
    }
    return id
  }

  /** Returns the stored session if it is still valid, otherwise creates one. */
  suspend fun ensureSession(): ComposioSession {
    val id = settings.composioSessionId
    if (id.isNotBlank() && settings.composioMcpUrl.isNotBlank()) {
      try {
        call("GET", "$BASE_V3/tool_router/session/$id")
        return ComposioSession(id, settings.composioMcpUrl)
      } catch (_: Exception) { /* fall through to create */ }
    }
    val res = JSONObject(call("POST", "$BASE/tool_router/session", JSONObject().put("user_id", userId())))
    val s = ComposioSession(res.getString("session_id"), res.getJSONObject("mcp").getString("url"))
    settings.composioSessionId = s.id
    settings.composioMcpUrl = s.mcpUrl
    return s
  }

  suspend fun listSessions(): Result<List<ComposioSession>> = runCatching {
    val s = ensureSession()
    listOf(s)
  }

  suspend fun toolkits(search: String? = null): List<ComposioToolkit> {
    val s = ensureSession()
    val q = buildString {
      append("?limit=100")
      if (!search.isNullOrBlank()) append("&search=").append(java.net.URLEncoder.encode(search, "UTF-8"))
    }
    val items = JSONObject(call("GET", "$BASE_V3/tool_router/session/${s.id}/toolkits$q")).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).map {
      val o = items.getJSONObject(it)
      val acct = o.optJSONObject("connected_account")
      ComposioToolkit(
        slug = o.getString("slug"),
        name = o.optString("name", o.getString("slug")),
        description = o.optJSONObject("meta")?.optString("description").orEmpty(),
        connected = acct != null && acct.optString("status").equals("ACTIVE", ignoreCase = true),
        connectedAccountId = acct?.optString("id"),
        noAuth = o.optBoolean("is_no_auth"),
      )
    }
  }

  /** Starts an OAuth/auth flow and returns the URL to open in a browser. */
  suspend fun connect(slug: String): String {
    val s = ensureSession()
    val res = JSONObject(call("POST", "$BASE_V3/tool_router/session/${s.id}/link", JSONObject().put("toolkit", slug).put("callback_url", CALLBACK)))
    return res.getString("redirect_url")
  }

  suspend fun disconnect(connectedAccountId: String) { call("DELETE", "$BASE_V3/connected_accounts/$connectedAccountId") }

  /** Headers OpenCode must send to the hosted MCP endpoint. */
  fun mcpHeaders(): Map<String, String> = mapOf("x-api-key" to secrets.get(SecretStore.COMPOSIO_API_KEY))
}
