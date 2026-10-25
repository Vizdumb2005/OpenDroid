package com.example.data.memory

import com.example.data.sandbox.Sandbox
import com.example.data.security.SecretStore
import com.example.data.settings.AppSettings
import com.example.data.settings.MemoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Memory(val id: String, val text: String, val createdAt: String, val updatedAt: String)

data class Mem0Memory(
  val id: String,
  val memory: String,
  val category: String = "fact",
  val timestamp: Long = System.currentTimeMillis(),
  val confidence: Float = 0.95f
)

data class MemoryHealth(val running: Boolean, val configured: Boolean, val message: String)

/** Talks to the real `mem0ai` library running inside the sandbox (see assets/sandbox/memory_server.py). */
class MemoryClient(
  private val sandbox: Sandbox,
  private val settings: AppSettings,
  private val secrets: SecretStore,
) {
  companion object { const val BASE = "http://127.0.0.1:8765" }

  private val http = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS) // mem0 add() runs an LLM call
    .build()
  private val json = "application/json".toMediaType()

  private suspend fun call(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
    val rb = body?.toString()?.toRequestBody(json)
    val req = Request.Builder().url(BASE + path).method(method, rb ?: if (method == "GET") null else "".toRequestBody(json)).build()
    http.newCall(req).execute().use { r ->
      val text = r.body?.string().orEmpty()
      if (!r.isSuccessful) throw IOException(runCatching { JSONObject(text).optString("detail") }.getOrNull()?.ifBlank { null } ?: "Memory service HTTP ${r.code}")
      text
    }
  }

  suspend fun health(): MemoryHealth = try {
    val o = JSONObject(call("GET", "/health"))
    MemoryHealth(true, o.optBoolean("ok"), o.optString("error"))
  } catch (e: Exception) { MemoryHealth(false, false, e.message ?: "Memory service is not running") }

  /** Writes mem0.json for the chosen provider and tells the service to reload it. */
  suspend fun applyConfig() {
    val key = secrets.get(SecretStore.MEMORY_API_KEY)
    val p = settings.memoryProvider
    val base = settings.memoryBaseUrl.trim()
    fun cfg(model: String, extra: JSONObject.() -> Unit = {}) = JSONObject().put("model", model).put("api_key", key).apply(extra)
    val llm: JSONObject
    val emb: JSONObject
    when (p) {
      MemoryProvider.OPENAI -> {
        llm = JSONObject().put("provider", "openai").put("config", cfg(p.llmModel) { if (base.isNotEmpty()) put("openai_base_url", base) })
        emb = JSONObject().put("provider", "openai").put("config", cfg(p.embedModel) { if (base.isNotEmpty()) put("openai_base_url", base) })
      }
      MemoryProvider.GEMINI -> {
        llm = JSONObject().put("provider", "gemini").put("config", cfg(p.llmModel))
        emb = JSONObject().put("provider", "gemini").put("config", cfg(p.embedModel) { put("embedding_dims", p.dims) })
      }
    }
    val root = JSONObject().put("llm", llm).put("embedder", emb).put("embedding_dims", p.dims)
    withContext(Dispatchers.IO) {
      val f = sandbox.guestToHost("/root/.opendroid/mem0.json")
      f.parentFile?.mkdirs()
      // Provider changes alter embedding dimensions; a stale vector store would reject new vectors.
      val dimsFile = sandbox.guestToHost("/root/.opendroid/mem0.dims")
      if (dimsFile.exists() && dimsFile.readText().trim() != p.dims.toString()) {
        sandbox.guestToHost("/root/.opendroid/qdrant").deleteRecursively()
      }
      dimsFile.writeText(p.dims.toString())
      f.writeText(root.toString())
      f.setReadable(false, false); f.setReadable(true, true)
    }
    if (health().running) call("POST", "/reload")
  }

  private fun parse(arr: JSONArray) = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    Mem0Memory(
      id = o.getString("id"),
      memory = o.optString("memory", o.optString("text")),
      category = o.optJSONObject("metadata")?.optString("category", "fact") ?: "fact",
      timestamp = System.currentTimeMillis()
    )
  }

  suspend fun all(): List<Mem0Memory> = parse(JSONObject(call("GET", "/all")).optJSONArray("results") ?: JSONArray())

  suspend fun search(query: String, limit: Int = 8): List<Mem0Memory> =
    parse(JSONObject(call("POST", "/search", JSONObject().put("query", query).put("limit", limit))).optJSONArray("results") ?: JSONArray())

  suspend fun add(text: String) { call("POST", "/add", JSONObject().put("text", text)) }

  suspend fun update(id: String, text: String) { call("PUT", "/memory/$id", JSONObject().put("text", text)) }

  suspend fun delete(id: String) { call("DELETE", "/memory/$id") }

  suspend fun deleteAll() { call("DELETE", "/all") }

  suspend fun listMemories(): Result<List<Mem0Memory>> = runCatching { all() }
  suspend fun addMemory(text: String, category: String = "fact"): Result<Unit> = runCatching { add(text) }
  suspend fun deleteMemory(id: String): Result<Unit> = runCatching { delete(id) }
  suspend fun clearAll(): Result<Unit> = runCatching { deleteAll() }
}
