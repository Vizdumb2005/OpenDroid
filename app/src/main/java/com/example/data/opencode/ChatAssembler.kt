package com.example.data.opencode

import com.example.data.model.AgentMode
import com.example.data.model.ChatMessage
import com.example.data.model.ToolCall
import com.example.data.model.ToolStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Folds OpenCode's message / part / delta events into the UI's [ChatMessage] list.
 * Not thread-safe: call from a single coroutine (the ViewModel's event collector).
 */
class ChatAssembler {

  private class Msg(val id: String) {
    var role: String = "assistant"
    var created: Long = 0L
    var completed: Boolean = false
    var agent: String = "build"
    var error: String? = null
    val parts = LinkedHashMap<String, JSONObject>()
  }

  private val messages = LinkedHashMap<String, Msg>()

  fun reset() = messages.clear()

  fun load(array: JSONArray) {
    messages.clear()
    for (i in 0 until array.length()) {
      val item = array.getJSONObject(i)
      onMessage(item.getJSONObject("info"))
      val parts = item.optJSONArray("parts") ?: continue
      for (j in 0 until parts.length()) onPart(parts.getJSONObject(j))
    }
  }

  fun onMessage(info: JSONObject) {
    val id = info.optString("id").ifBlank { return }
    val m = messages.getOrPut(id) { Msg(id) }
    m.role = info.optString("role", m.role)
    m.agent = info.optString("agent", m.agent)
    val time = info.optJSONObject("time")
    m.created = time?.optLong("created") ?: m.created
    m.completed = time?.has("completed") == true || info.has("finish")
    info.optJSONObject("error")?.let { e ->
      m.error = e.optJSONObject("data")?.optString("message")?.ifBlank { null } ?: e.optString("name")
    }
  }

  fun onPart(part: JSONObject) {
    val mid = part.optString("messageID").ifBlank { return }
    val pid = part.optString("id").ifBlank { return }
    val m = messages.getOrPut(mid) { Msg(mid) }
    m.parts[pid] = part
  }

  fun onDelta(messageId: String, partId: String, field: String, delta: String) {
    val part = messages[messageId]?.parts?.get(partId) ?: return
    part.put(field, part.optString(field) + delta)
  }

  fun remove(messageId: String) { messages.remove(messageId) }

  fun render(): List<ChatMessage> =
    messages.values.sortedBy { it.id }.mapNotNull { toChat(it) }

  private fun toChat(m: Msg): ChatMessage? {
    val parts = m.parts.values.toList()
    val text = parts.filter { it.optString("type") == "text" && !it.optBoolean("ignored") }
      .joinToString("") { it.optString("text") }
    val reasoning = parts.filter { it.optString("type") == "reasoning" }
      .joinToString("\n") { it.optString("text") }.ifBlank { null }
    val tools = parts.filter { it.optString("type") == "tool" }.map { toTool(it) }
    if (m.role == "user" && text.isBlank()) return null
    val mode = if (m.agent == "plan") AgentMode.PLAN else AgentMode.BUILD
    val body = if (m.error != null && text.isBlank()) "Error: ${m.error}" else text
    return ChatMessage(
      id = m.id,
      sender = if (m.role == "user") "user" else "assistant",
      text = body,
      mode = mode,
      timestamp = m.created,
      toolCalls = tools,
      isStreaming = m.role == "assistant" && !m.completed && m.error == null,
      thoughts = reasoning,
    )
  }

  private fun toTool(p: JSONObject): ToolCall {
    val state = p.optJSONObject("state") ?: JSONObject()
    val input = state.optJSONObject("input") ?: JSONObject()
    val args = linkedMapOf<String, String>()
    input.keys().forEach { k -> args[k] = input.opt(k)?.toString().orEmpty() }
    val status = when (state.optString("status")) {
      "pending" -> ToolStatus.PENDING
      "running" -> ToolStatus.RUNNING
      "completed" -> ToolStatus.COMPLETED
      "error" -> ToolStatus.FAILED
      else -> ToolStatus.PENDING
    }
    val diff = state.optJSONObject("metadata")?.optString("diff")?.ifBlank { null }
    return ToolCall(
      id = p.optString("callID", p.optString("id")),
      name = p.optString("tool"),
      arguments = args,
      status = status,
      result = state.optString("output").ifBlank { null },
      diff = diff,
      errorMessage = state.optString("error").ifBlank { null },
    )
  }

  private val textAccumulator = StringBuilder()
  private val toolAccumulator = mutableListOf<ToolCall>()
  private var latestStatus: String? = null

  fun snapshot(): Triple<String, List<ToolCall>, String?> {
    return Triple(textAccumulator.toString(), toolAccumulator.toList(), latestStatus)
  }

  fun consume(event: OcEvent): ChatStreamEvent? {
    return when (event) {
      is OcEvent.PartUpdated -> {
        onPart(event.part)
        val type = event.part.optString("type")
        when (type) {
          "text" -> {
            val t = event.part.optString("text")
            if (t.isNotBlank() && !textAccumulator.contains(t)) {
              textAccumulator.append(t)
            }
            ChatStreamEvent.Content(textAccumulator.toString())
          }
          "tool" -> {
            val tool = toTool(event.part)
            val idx = toolAccumulator.indexOfFirst { it.id == tool.id }
            if (idx != -1) toolAccumulator[idx] = tool else toolAccumulator.add(tool)
            ChatStreamEvent.Tool(tool)
          }
          else -> null
        }
      }
      is OcEvent.PartDelta -> {
        onDelta(event.messageId, event.partId, event.field, event.delta)
        if (event.field == "text") {
          textAccumulator.append(event.delta)
          ChatStreamEvent.Content(event.delta)
        } else null
      }
      is OcEvent.MessageUpdated -> {
        onMessage(event.info)
        null
      }
      is OcEvent.SessionStatus -> {
        latestStatus = event.type
        ChatStreamEvent.Status(event.type)
      }
      is OcEvent.SessionError -> {
        latestStatus = "Error: ${event.message}"
        ChatStreamEvent.Status("Error: ${event.message}")
      }
      else -> null
    }
  }

  fun consumeSse(event: String, data: String): ChatStreamEvent? {
    return try {
      val json = JSONObject(data)
      val type = json.optString("type")
      when {
        event == "status" || type == "status" -> {
          val st = json.optString("status", data)
          latestStatus = st
          ChatStreamEvent.Status(st)
        }
        type == "content" -> {
          val t = json.optString("text")
          textAccumulator.append(t)
          ChatStreamEvent.Content(t)
        }
        type == "tool_call" -> {
          val id = json.optString("id")
          val name = json.optString("tool")
          val argsObj = json.optJSONObject("args")
          val argsMap = mutableMapOf<String, String>()
          argsObj?.keys()?.forEach { k -> argsMap[k] = argsObj.opt(k)?.toString().orEmpty() }
          val tool = ToolCall(id = id, name = name, arguments = argsMap, status = ToolStatus.RUNNING)
          val idx = toolAccumulator.indexOfFirst { it.id == id }
          if (idx != -1) toolAccumulator[idx] = tool else toolAccumulator.add(tool)
          ChatStreamEvent.Tool(tool)
        }
        type == "tool_result" -> {
          val id = json.optString("id")
          val result = json.optString("result")
          val idx = toolAccumulator.indexOfFirst { it.id == id }
          if (idx != -1) {
            val updated = toolAccumulator[idx].copy(result = result, status = ToolStatus.COMPLETED)
            toolAccumulator[idx] = updated
            ChatStreamEvent.Tool(updated)
          } else null
        }
        event == "message.part.delta" -> {
          val delta = json.optString("delta")
          textAccumulator.append(delta)
          ChatStreamEvent.Content(delta)
        }
        else -> null
      }
    } catch (_: Exception) {
      if (data.isNotBlank()) {
        textAccumulator.append(data)
        ChatStreamEvent.Content(data)
      } else null
    }
  }
}

sealed class ChatStreamEvent {
  data class Content(val text: String) : ChatStreamEvent()
  data class Tool(val tool: ToolCall) : ChatStreamEvent()
  data class Status(val status: String) : ChatStreamEvent()
}
