package com.example.data.memory

import com.example.data.knowledge.UniversalKnowledgeRepository
import com.example.data.model.WorkspaceFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class CompiledMemoryEvent(
  val id: String = UUID.randomUUID().toString(),
  val statement: String,
  val category: String,
  val source: String,
  val timestamp: Long = System.currentTimeMillis()
)

class AgentMemoryCompiler(
  private val mem0Engine: Mem0Engine,
  private val knowledgeRepository: UniversalKnowledgeRepository
) {

  private val _compilationEvents = MutableStateFlow<List<CompiledMemoryEvent>>(emptyList())
  val compilationEvents: StateFlow<List<CompiledMemoryEvent>> = _compilationEvents.asStateFlow()

  /**
   * Automatically compile structured memories from an agent interaction.
   */
  fun compileFromInteraction(
    userPrompt: String,
    agentResponse: String,
    files: Map<String, WorkspaceFile> = emptyMap()
  ): List<CompiledMemoryEvent> {
    val newEvents = mutableListOf<CompiledMemoryEvent>()
    val lowerPrompt = userPrompt.lowercase()
    val lowerResponse = agentResponse.lowercase()

    // 1. Analyze and compile user preferences
    if (lowerPrompt.contains("prefer") || lowerPrompt.contains("always") || lowerPrompt.contains("never") || lowerPrompt.contains("use ")) {
      val prefText = userPrompt.replace("(?i)(please|can you|i want you to)".toRegex(), "").trim()
      if (prefText.length in 10..150) {
        val memory = mem0Engine.add(prefText, category = "preference")
        newEvents.add(
          CompiledMemoryEvent(
            statement = memory.memory,
            category = "preference",
            source = "User Prompt Instruction"
          )
        )
      }
    }

    // 2. Analyze codebase tech stack from workspace files
    if (files.isNotEmpty()) {
      compileFromWorkspaceFiles(files, newEvents)
    }

    // 3. Extract architectural decisions from agent response
    if (lowerResponse.contains("architecture") || lowerResponse.contains("database") || lowerResponse.contains("pattern")) {
      val firstParagraph = agentResponse.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: ""
      if (firstParagraph.isNotBlank() && firstParagraph.length in 20..180) {
        val archMem = mem0Engine.add("Decision: $firstParagraph", category = "architecture")
        newEvents.add(
          CompiledMemoryEvent(
            statement = archMem.memory,
            category = "architecture",
            source = "Agent Reasoning Synthesis"
          )
        )
      }
    }

    if (newEvents.isNotEmpty()) {
      _compilationEvents.value = newEvents + _compilationEvents.value
    }

    return newEvents
  }

  /**
   * Automatically compile project architecture facts from workspace files.
   */
  fun compileFromWorkspaceFiles(
    files: Map<String, WorkspaceFile>,
    eventCollector: MutableList<CompiledMemoryEvent> = mutableListOf()
  ): List<CompiledMemoryEvent> {
    val existingMemories = mem0Engine.memories.value.map { it.memory.lowercase() }

    // Detect package.json dependencies
    val pkgJson = files["package.json"]?.content
    if (pkgJson != null) {
      if (pkgJson.contains("typescript") && !existingMemories.any { it.contains("typescript") }) {
        val mem = mem0Engine.add("Workspace uses TypeScript with strict compiler options.", category = "architecture")
        eventCollector.add(CompiledMemoryEvent(statement = mem.memory, category = "architecture", source = "package.json"))
      }
      if (pkgJson.contains("express") && !existingMemories.any { it.contains("express") }) {
        val mem = mem0Engine.add("Backend runtime uses Express.js microservice architecture.", category = "architecture")
        eventCollector.add(CompiledMemoryEvent(statement = mem.memory, category = "architecture", source = "package.json"))
      }
    }

    // Detect Python / Kotlin / Shell
    if (files.keys.any { it.endsWith(".py") } && !existingMemories.any { it.contains("python") }) {
      val mem = mem0Engine.add("Project contains Python scripts executing in Ubuntu ARM64 environment.", category = "fact")
      eventCollector.add(CompiledMemoryEvent(statement = mem.memory, category = "fact", source = "File Scanner"))
    }

    return eventCollector
  }
}
