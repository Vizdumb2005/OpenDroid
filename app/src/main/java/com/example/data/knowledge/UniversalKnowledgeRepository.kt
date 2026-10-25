package com.example.data.knowledge

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.KnowledgeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class UniversalKnowledgeRepository(context: Context) {

  private val prefs: SharedPreferences = context.getSharedPreferences("opencode_universal_kb", Context.MODE_PRIVATE)

  private val _items = MutableStateFlow<List<KnowledgeItem>>(emptyList())
  val items: StateFlow<List<KnowledgeItem>> = _items.asStateFlow()

  init {
    loadKnowledgeBase()
  }

  private fun loadKnowledgeBase() {
    val rawJson = prefs.getString("kb_items", null)
    if (rawJson != null) {
      try {
        val array = JSONArray(rawJson)
        val list = mutableListOf<KnowledgeItem>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          list.add(
            KnowledgeItem(
              id = obj.getString("id"),
              title = obj.getString("title"),
              content = obj.getString("content"),
              category = obj.optString("category", "Convention"),
              isEnabled = obj.optBoolean("isEnabled", true),
              createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
          )
        }
        _items.value = list
        return
      } catch (e: Exception) {
        // Fallback to default
      }
    }

    // Default universal guidelines
    val defaults = listOf(
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        title = "Clean Architecture Standards",
        content = "Prefer separation of concerns: keep data models immutable, repository layers decoupled, and ViewModel business logic distinct from Compose UI presentation.",
        category = "Architecture",
        isEnabled = true
      ),
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        title = "Security & Zero Trust",
        content = "Never store plaintext credentials or API secrets in code or git commits. Validate all inputs and enforce strict role boundaries in auth endpoints.",
        category = "Security",
        isEnabled = true
      ),
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        title = "Test-Driven Reliability",
        content = "Write unit tests for core domain transformations and edge cases before deploying features. Check return types and handle nullable values gracefully.",
        category = "Testing",
        isEnabled = true
      ),
      KnowledgeItem(
        id = UUID.randomUUID().toString(),
        title = "UI Performance Guidelines",
        content = "In Jetpack Compose, use remember and derivedStateOf for heavy calculations to prevent redundant recompositions.",
        category = "Performance",
        isEnabled = true
      )
    )
    _items.value = defaults
    saveToPrefs(defaults)
  }

  private fun saveToPrefs(list: List<KnowledgeItem>) {
    val array = JSONArray()
    list.forEach { item ->
      val obj = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("content", item.content)
        put("category", item.category)
        put("isEnabled", item.isEnabled)
        put("createdAt", item.createdAt)
      }
      array.put(obj)
    }
    prefs.edit().putString("kb_items", array.toString()).apply()
  }

  fun addItem(title: String, content: String, category: String = "Convention") {
    val newItem = KnowledgeItem(
      id = UUID.randomUUID().toString(),
      title = title.trim(),
      content = content.trim(),
      category = category,
      isEnabled = true
    )
    val updated = _items.value + newItem
    _items.value = updated
    saveToPrefs(updated)
  }

  fun toggleItem(id: String) {
    val updated = _items.value.map {
      if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
    }
    _items.value = updated
    saveToPrefs(updated)
  }

  fun deleteItem(id: String) {
    val updated = _items.value.filterNot { it.id == id }
    _items.value = updated
    saveToPrefs(updated)
  }

  fun getKnowledgePromptInjection(): String {
    val active = _items.value.filter { it.isEnabled }
    if (active.isEmpty()) return ""

    return buildString {
      appendLine("\n### Universal Persistent Knowledge Base:")
      active.forEach { item ->
        appendLine("- [${item.category}] **${item.title}**: ${item.content}")
      }
    }
  }
}
