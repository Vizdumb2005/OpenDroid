package com.example.data.ai

import com.example.data.model.OpenCodeModel
import com.example.data.model.OpenCodeModelFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenCodeModelCatalog {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  // Canonical OpenCode catalog as shown in OpenCode Model Settings
  private val canonicalCatalog = listOf(
    OpenCodeModel(
      id = "bigpickle",
      displayName = "BigPickle",
      isFree = true,
      isDefault = true,
      features = OpenCodeModelFeature(hasWeb = true, hasTerminal = true, hasText = true, extraBadge = "Default"),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "OpenCode primary reasoning and coding foundation model. Always free."
    ),
    OpenCodeModel(
      id = "beam-free",
      displayName = "Beam",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasTerminal = true, hasText = true),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "High-throughput autonomous agent model with terminal & web access."
    ),
    OpenCodeModel(
      id = "deepseek-v4.1-flash-free",
      displayName = "DeepSeek V4.1 Flash",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasVision = true, extraBadge = "+2"),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Fast multilingual coding & multimodal inference model."
    ),
    OpenCodeModel(
      id = "flint-40b-free",
      displayName = "Flint 40B",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasTerminal = true, hasText = true),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "40B parameter dense architecture model with deep code comprehension."
    ),
    OpenCodeModel(
      id = "glm-5.3-flash-free",
      displayName = "GLM-5.3-Flash",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasText = true, extraBadge = "+3"),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Low-latency code generation and agentic task breakdown."
    ),
    OpenCodeModel(
      id = "jev-1.13-free",
      displayName = "Jev 1.13",
      isFree = true,
      features = OpenCodeModelFeature(hasVision = true, hasText = true),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "-",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Vision-guided architectural analysis and UI mock inspect."
    ),
    OpenCodeModel(
      id = "mimo-v2.6-flash-free",
      displayName = "MiMo-V2.6-Flash",
      isFree = true,
      features = OpenCodeModelFeature(hasAudio = true, extraBadge = "+4"),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Multimodal streaming model with audio & code token processing."
    ),
    OpenCodeModel(
      id = "nemotron-3-ultra-free",
      displayName = "Nemotron 3 Ultra",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasTerminal = true, hasText = true),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Nvidia Nemotron 3 Ultra high-accuracy code synthesis."
    ),
    OpenCodeModel(
      id = "nemotron-3.5-lightning-free",
      displayName = "Nemotron 3.5 Lightning",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasTerminal = true, hasText = true),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Ultra-fast code refinement and unit test generation."
    ),
    OpenCodeModel(
      id = "nemotron-3.5-super-vl-free",
      displayName = "Nemotron 3.5 Super VL",
      isFree = true,
      features = OpenCodeModelFeature(hasWeb = true, hasVision = true, extraBadge = "+2"),
      inputCost = "$0.00",
      outputCost = "$0.00",
      cacheReadCost = "$0.00",
      cacheWriteCost = "-",
      isEnabled = true,
      description = "Super visual-language model for multimodal coding."
    )
  )

  private val _models = MutableStateFlow<List<OpenCodeModel>>(canonicalCatalog)
  val models: StateFlow<List<OpenCodeModel>> = _models.asStateFlow()

  var defaultModelId: String = "bigpickle"
    private set

  /**
   * Fetch dynamically currently offered free models from OpenCode catalog endpoint.
   */
  suspend fun fetchLiveModels(): List<OpenCodeModel> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("https://opencode.ai/api/models")
        .header("Accept", "application/json")
        .build()

      val response = httpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val body = response.body?.string()
        if (!body.isNullOrBlank()) {
          val parsedList = parseOpenCodeModelsJson(body)
          if (parsedList.isNotEmpty()) {
            // Ensure BigPickle is present, free, and marked default
            val hasBigPickle = parsedList.any { it.id.equals("bigpickle", ignoreCase = true) }
            val mergedList = if (!hasBigPickle) {
              listOf(canonicalCatalog.first()) + parsedList
            } else {
              parsedList.map {
                if (it.id.equals("bigpickle", ignoreCase = true)) {
                  it.copy(isFree = true, isDefault = true, isEnabled = true)
                } else it
              }
            }
            _models.value = mergedList
            return@withContext mergedList
          }
        }
      }
    } catch (e: Exception) {
      // Network unreachable or offline - use canonical catalog from OpenCode
    }

    _models.value = canonicalCatalog
    canonicalCatalog
  }

  fun toggleModelEnabled(modelId: String) {
    // BigPickle is always free & enabled
    if (modelId == "bigpickle") return
    val updated = _models.value.map {
      if (it.id == modelId) it.copy(isEnabled = !it.isEnabled) else it
    }
    _models.value = updated
  }

  fun getModel(id: String): OpenCodeModel? {
    return _models.value.find { it.id == id } ?: _models.value.find { it.id == "bigpickle" }
  }

  private fun parseOpenCodeModelsJson(json: String): List<OpenCodeModel> {
    val result = mutableListOf<OpenCodeModel>()
    try {
      val arr = if (json.trim().startsWith("[")) JSONArray(json) else JSONObject(json).optJSONArray("models") ?: JSONArray()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val id = obj.optString("id", obj.optString("name", ""))
        val isFree = obj.optBoolean("free", obj.optBoolean("is_free", true))
        if (id.isNotBlank() && isFree) {
          result.add(
            OpenCodeModel(
              id = id,
              displayName = obj.optString("displayName", obj.optString("name", id)),
              isFree = true,
              isDefault = id.equals("bigpickle", ignoreCase = true),
              inputCost = obj.optString("inputCost", "$0.00"),
              outputCost = obj.optString("outputCost", "$0.00"),
              isEnabled = true,
              description = obj.optString("description", "OpenCode dynamic free catalog model")
            )
          )
        }
      }
    } catch (e: Exception) {
      // Ignored
    }
    return result
  }
}
