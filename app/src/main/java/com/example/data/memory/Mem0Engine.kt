package com.example.data.memory

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Mem0Memory(
  val id: String,
  val memory: String,
  val userId: String = "default_user",
  val agentId: String = "opencode_agent",
  val category: String = "preference", // preference, fact, context, constraint, architecture
  val confidence: Float = 0.95f,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

class Mem0Engine(context: Context) {

  private val prefs: SharedPreferences = context.getSharedPreferences("opencode_mem0_store", Context.MODE_PRIVATE)

  private val _memories = MutableStateFlow<List<Mem0Memory>>(emptyList())
  val memories: StateFlow<List<Mem0Memory>> = _memories.asStateFlow()

  init {
    loadMemories()
  }

  private fun loadMemories() {
    val raw = prefs.getString("mem0_entries", null)
    if (raw != null) {
      try {
        val arr = JSONArray(raw)
        val list = mutableListOf<Mem0Memory>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            Mem0Memory(
              id = obj.getString("id"),
              memory = obj.getString("memory"),
              userId = obj.optString("userId", "default_user"),
              agentId = obj.optString("agentId", "opencode_agent"),
              category = obj.optString("category", "preference"),
              confidence = obj.optDouble("confidence", 0.95).toFloat(),
              createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
              updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
          )
        }
        _memories.value = list
        return
      } catch (e: Exception) {
        // Fall through
      }
    }

    // Initial essential memory entries for OpenCode on Ubuntu ARM64
    val initial = listOf(
      Mem0Memory(
        id = UUID.randomUUID().toString(),
        memory = "Target platform is Ubuntu Server 26.04.1 LTS (aarch64 ARM64).",
        category = "architecture"
      ),
      Mem0Memory(
        id = UUID.randomUUID().toString(),
        memory = "User prefers zero hardcoded demo data; all responses must be live and authentic.",
        category = "preference"
      ),
      Mem0Memory(
        id = UUID.randomUUID().toString(),
        memory = "OpenCode package version is stable linux-x64-deb / aarch64 native binary.",
        category = "context"
      )
    )
    _memories.value = initial
    persist(initial)
  }

  private fun persist(list: List<Mem0Memory>) {
    val arr = JSONArray()
    list.forEach { mem ->
      val obj = JSONObject().apply {
        put("id", mem.id)
        put("memory", mem.memory)
        put("userId", mem.userId)
        put("agentId", mem.agentId)
        put("category", mem.category)
        put("confidence", mem.confidence.toDouble())
        put("createdAt", mem.createdAt)
        put("updatedAt", mem.updatedAt)
      }
      arr.put(obj)
    }
    prefs.edit().putString("mem0_entries", arr.toString()).apply()
  }

  fun add(text: String, category: String = "fact", userId: String = "default_user"): Mem0Memory {
    val memory = Mem0Memory(
      id = UUID.randomUUID().toString(),
      memory = text.trim(),
      userId = userId,
      category = category
    )
    val updated = listOf(memory) + _memories.value
    _memories.value = updated
    persist(updated)
    return memory
  }

  fun delete(id: String) {
    val updated = _memories.value.filterNot { it.id == id }
    _memories.value = updated
    persist(updated)
  }

  fun clearAll() {
    _memories.value = emptyList()
    prefs.edit().remove("mem0_entries").apply()
  }

  /**
   * Search relevant memories for context augmentation using semantic & token matching.
   */
  fun search(query: String, limit: Int = 5): List<Mem0Memory> {
    if (query.isBlank()) return _memories.value.take(limit)
    val tokens = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }

    val scored = _memories.value.map { mem ->
      val memLower = mem.memory.lowercase()
      var score = 0
      tokens.forEach { token ->
        if (memLower.contains(token)) score += 2
      }
      if (memLower.contains(query.lowercase())) score += 5
      mem to score
    }

    return scored
      .sortedByDescending { it.second }
      .take(limit)
      .map { it.first }
  }

  /**
   * Automatically extract atomic long-term memories from user prompt and assistant response.
   */
  fun extractMemoriesFromInteraction(userPrompt: String, assistantResponse: String) {
    val lowerPrompt = userPrompt.lowercase()

    // Detect preference patterns
    if (lowerPrompt.contains("i prefer") || lowerPrompt.contains("always use") || lowerPrompt.contains("never use")) {
      add(userPrompt, category = "preference")
    } else if (lowerPrompt.contains("remember that") || lowerPrompt.contains("my project uses")) {
      val fact = userPrompt.removePrefix("remember that").removePrefix("my project uses").trim()
      add(fact, category = "fact")
    }
  }

  /**
   * Build formatted Mem0 prompt section for agent reasoning context.
   */
  fun getFormattedContextForQuery(query: String): String {
    val relevant = search(query, limit = 5)
    if (relevant.isEmpty()) return ""

    return buildString {
      appendLine("\n--- Mem0 Long-Term Memory Context ---")
      relevant.forEach { mem ->
        appendLine("• [${mem.category.uppercase()}] ${mem.memory}")
      }
      appendLine("--------------------------------------\n")
    }
  }
}
