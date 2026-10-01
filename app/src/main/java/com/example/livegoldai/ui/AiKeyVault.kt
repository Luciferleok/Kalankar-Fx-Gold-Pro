package com.example.livegoldai.ui

import android.content.SharedPreferences
import com.example.livegoldai.data.ai.AiProviderConfig
import com.example.livegoldai.data.ai.AiProviderId
import com.example.livegoldai.data.ai.AiRole
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores AI API keys encrypted with an AES key that lives in the Android Keystore.
 * The Keystore key never leaves this phone, so a backup of the app data cannot be decrypted elsewhere
 * (after a restore the keys simply read as empty and must be entered again).
 * Model / role / on-off settings are not secret and are stored in plain preferences.
 */
class AiKeyVault(private val prefs: SharedPreferences) {

    private val alias = "kfx_ai_provider_keys_v1"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun putKey(id: AiProviderId, apiKey: String): Boolean = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = c.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        prefs.edit().putString("ai_key_" + id.name, enc.encodeToString(c.iv) + ":" + enc.encodeToString(ct)).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun getKey(id: AiProviderId): String = try {
        val stored = prefs.getString("ai_key_" + id.name, null)
        if (stored.isNullOrBlank() || !stored.contains(':')) "" else {
            val dec = Base64.getDecoder()
            val iv = dec.decode(stored.substringBefore(':'))
            val ct = dec.decode(stored.substringAfter(':'))
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(c.doFinal(ct), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        ""
    }

    fun removeKey(id: AiProviderId) {
        prefs.edit().remove("ai_key_" + id.name).apply()
    }

    // ---- non-secret settings
    fun loadConfig(id: AiProviderId): AiProviderConfig = AiProviderConfig(
        id = id,
        apiKey = getKey(id),
        model = prefs.getString("ai_model_" + id.name, null)?.takeIf { it.isNotBlank() } ?: id.defaultModel,
        role = AiRole.fromName(prefs.getString("ai_role_" + id.name, null), id.defaultRole),
        enabled = prefs.getBoolean("ai_on_" + id.name, true)
    )

    fun saveSettings(id: AiProviderId, model: String, role: AiRole, enabled: Boolean) {
        prefs.edit()
            .putString("ai_model_" + id.name, model.trim())
            .putString("ai_role_" + id.name, role.name)
            .putBoolean("ai_on_" + id.name, enabled)
            .apply()
    }

    companion object {
        /** "sk-proj-abc...wxyz" -> "sk-p…wxyz". Never shows the full key. */
        fun mask(key: String): String {
            val k = key.trim()
            return when {
                k.isEmpty() -> ""
                k.length <= 8 -> "••••"
                else -> k.take(4) + "…" + k.takeLast(4)
            }
        }
    }
}
