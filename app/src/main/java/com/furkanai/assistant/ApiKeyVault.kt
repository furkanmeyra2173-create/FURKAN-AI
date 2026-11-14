package com.furkanai.assistant

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Keeps each user's own provider key encrypted with an AES key held by Android Keystore.
 * The plaintext key is only returned in memory when a provider request is made.
 */
object ApiKeyVault {
    private const val PREFS = "furkan_ai_encrypted_keys"
    private const val KEY_ALIAS = "furkan_ai_api_key_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, provider: AiProvider, apiKey: String) {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "API anahtarı boş olamaz." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(cleanKey.toByteArray(StandardCharsets.UTF_8))
        val payload = cipher.iv + encrypted

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(provider.id, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun read(context: Context, provider: AiProvider): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(provider.id, null) ?: return null

        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= 12) return null
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun delete(context: Context, provider: AiProvider) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(provider.id)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
