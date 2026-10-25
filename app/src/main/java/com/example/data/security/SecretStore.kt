package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores API keys and the local server password, encrypted with an Android Keystore AES-GCM key.
 * Values never touch disk in plain text.
 */
class SecretStore(context: Context) {

  private val prefs = context.getSharedPreferences("opendroid_secrets", Context.MODE_PRIVATE)

  private fun key(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    gen.init(
      KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return gen.generateKey()
  }

  fun put(name: String, value: String) {
    if (value.isEmpty()) { prefs.edit().remove(name).apply(); return }
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, key())
    val ct = c.doFinal(value.toByteArray())
    val blob = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    prefs.edit().putString(name, blob).apply()
  }

  fun get(name: String): String {
    val blob = prefs.getString(name, null) ?: return ""
    return try {
      val (iv, ct) = blob.split(":")
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
      String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
    } catch (_: Exception) { "" }
  }

  /** Random per-install password for the localhost OpenCode server. */
  fun serverPassword(): String {
    val existing = get(SERVER_PASSWORD)
    if (existing.isNotEmpty()) return existing
    val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
    val pw = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    put(SERVER_PASSWORD, pw)
    return pw
  }

  fun getComposioKey(): String = get(COMPOSIO_API_KEY)
  fun saveComposioKey(key: String) = put(COMPOSIO_API_KEY, key)
  fun getMem0ApiKey(): String = get(MEMORY_API_KEY)
  fun saveMem0ApiKey(key: String) = put(MEMORY_API_KEY, key)

  companion object {
    private const val ALIAS = "opendroid_secret_key"
    const val SERVER_PASSWORD = "server_password"
    const val COMPOSIO_API_KEY = "composio_api_key"
    const val MEMORY_API_KEY = "memory_api_key"
  }
}
