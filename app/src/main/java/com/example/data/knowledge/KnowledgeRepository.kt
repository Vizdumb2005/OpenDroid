package com.example.data.knowledge

import android.content.Context
import com.example.data.model.KnowledgeItem
import com.example.data.sandbox.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists persistent project instructions, architectural guidelines, and conventions.
 * Automatically synchronizes enabled directives to `/root/.config/opencode/AGENTS.md`
 * so the OpenCode autonomous agent always adheres to user-defined project rules.
 */
class KnowledgeRepository(private val context: Context, private val sandbox: Sandbox) {

  private val prefs = context.getSharedPreferences("opendroid_knowledge", Context.MODE_PRIVATE)

  private val _items = MutableStateFlow<List<KnowledgeItem>>(loadItems())
  val items: StateFlow<List<KnowledgeItem>> = _items.asStateFlow()

  private fun loadItems(): List<KnowledgeItem> {
    val raw = prefs.getString("knowledge_items_json", null) ?: return defaultDirectives()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        KnowledgeItem(
          id = o.getString("id"),
          title = o.getString("title"),
          content = o.getString("content"),
          category = o.optString("category", "Convention"),
          isEnabled = o.optBoolean("isEnabled", true),
          createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
      }
    } catch (_: Exception) {
      defaultDirectives()
    }
  }

  private fun defaultDirectives(): List<KnowledgeItem> = listOf(
    KnowledgeItem(
      id = "dir_1",
      title = "Clean Architecture Invariant",
      content = "Separate data models, sandbox runtimes, and Compose UI layers. Avoid hardcoded mock data.",
      category = "Architecture",
      isEnabled = true
    ),
    KnowledgeItem(
      id = "dir_2",
      title = "Linux Sandbox Permissions",
      content = "All shell scripts and executables inside /root/workspace should maintain appropriate POSIX permissions.",
      category = "Rule",
      isEnabled = true
    )
  )

  private suspend fun persistAndSync() = withContext(Dispatchers.IO) {
    val list = _items.value
    val arr = JSONArray()
    list.forEach { item ->
      arr.put(JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("content", item.content)
        put("category", item.category)
        put("isEnabled", item.isEnabled)
        put("createdAt", item.createdAt)
      })
    }
    prefs.edit().putString("knowledge_items_json", arr.toString()).apply()

    // Sync active directives into sandbox ~/.config/opencode/AGENTS.md
    val agentRulesFile = sandbox.guestToHost("/root/.config/opencode/AGENTS.md")
    agentRulesFile.parentFile?.mkdirs()

    val enabled = list.filter { it.isEnabled }
    val rulesText = buildString {
      appendLine("# Project Directives & Coding Standards")
      appendLine("Follow these instructions strictly when writing or refactoring code:")
      enabled.forEach {
        appendLine("## [${it.category}] ${it.title}")
        appendLine(it.content)
        appendLine()
      }
    }
    agentRulesFile.writeText(rulesText)
  }

  suspend fun loadDirectives() = persistAndSync()

  suspend fun addItem(title: String, content: String, category: String) {
    val item = KnowledgeItem(
      id = UUID.randomUUID().toString(),
      title = title.trim(),
      content = content.trim(),
      category = category.trim().ifBlank { "Convention" },
      isEnabled = true
    )
    _items.value = _items.value + item
    persistAndSync()
  }

  suspend fun toggleItem(id: String) {
    _items.value = _items.value.map {
      if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
    }
    persistAndSync()
  }

  suspend fun deleteItem(id: String) {
    _items.value = _items.value.filterNot { it.id == id }
    persistAndSync()
  }
}
