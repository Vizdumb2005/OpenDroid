package com.example.data.settings

import android.content.Context
import com.example.data.security.SecretStore

/** Memory (Mem0) provider presets. Embedding dims must match the embedder model. */
enum class MemoryProvider(val label: String, val llmModel: String, val embedModel: String, val dims: Int) {
  OPENAI("OpenAI", "gpt-4o-mini", "text-embedding-3-small", 1536),
  GEMINI("Google Gemini", "gemini-2.5-flash", "models/gemini-embedding-001", 768),
}

/** Non-secret preferences. Secrets live in [SecretStore]. */
class AppSettings(context: Context) {
  private val p = context.getSharedPreferences("opendroid_settings", Context.MODE_PRIVATE)

  var startOnBoot: Boolean
    get() = p.getBoolean("start_on_boot", true)
    set(v) = p.edit().putBoolean("start_on_boot", v).apply()

  var keepRunning: Boolean
    get() = p.getBoolean("keep_running", true)
    set(v) = p.edit().putBoolean("keep_running", v).apply()

  var autoResources: Boolean
    get() = p.getBoolean("auto_resources", true)
    set(v) = p.edit().putBoolean("auto_resources", v).apply()

  var ramMb: Int
    get() = p.getInt("ram_mb", 0)
    set(v) = p.edit().putInt("ram_mb", v).apply()

  var diskGb: Int
    get() = p.getInt("disk_gb", 0)
    set(v) = p.edit().putInt("disk_gb", v).apply()

  var memoryProvider: MemoryProvider
    get() = runCatching { MemoryProvider.valueOf(p.getString("mem_provider", "OPENAI")!!) }.getOrDefault(MemoryProvider.OPENAI)
    set(v) = p.edit().putString("mem_provider", v.name).apply()

  var memoryBaseUrl: String
    get() = p.getString("mem_base_url", "") ?: ""
    set(v) = p.edit().putString("mem_base_url", v).apply()

  var preferredProvider: String
    get() = p.getString("pref_provider", "") ?: ""
    set(v) = p.edit().putString("pref_provider", v).apply()

  var preferredModel: String
    get() = p.getString("pref_model", "") ?: ""
    set(v) = p.edit().putString("pref_model", v).apply()

  var composioSessionId: String
    get() = p.getString("composio_session", "") ?: ""
    set(v) = p.edit().putString("composio_session", v).apply()

  var composioMcpUrl: String
    get() = p.getString("composio_mcp_url", "") ?: ""
    set(v) = p.edit().putString("composio_mcp_url", v).apply()

  var onboardingDone: Boolean
    get() = p.getBoolean("onboarding_done", false)
    set(v) = p.edit().putBoolean("onboarding_done", v).apply()

  var runInBackground: Boolean
    get() = keepRunning
    set(v) { keepRunning = v }

  var autoApproveTools: Boolean
    get() = p.getBoolean("auto_approve", true)
    set(v) = p.edit().putBoolean("auto_approve", v).apply()

  var mem0Provider: String
    get() = memoryProvider.name.lowercase()
    set(v) {
      memoryProvider = when (v.lowercase()) {
        "gemini" -> MemoryProvider.GEMINI
        else -> MemoryProvider.OPENAI
      }
    }

  var ramLimitMb: Int
    get() = ramMb
    set(v) { ramMb = v }

  var storageLimitGb: Int
    get() = diskGb
    set(v) { diskGb = v }
}
