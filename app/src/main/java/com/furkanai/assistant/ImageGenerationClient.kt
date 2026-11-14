package com.furkanai.assistant

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import android.util.Base64

object ImageGenerationClient {
    suspend fun generateAndSave(context: Context, apiKey: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Önce OpenAI API anahtarını kaydet." }
            require(prompt.isNotBlank()) { "Görsel açıklaması boş olamaz." }

            val connection = URL("https://api.openai.com/v1/images/generations")
                .openConnection() as HttpURLConnection
            val bytes: ByteArray
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 180000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                val body = JSONObject()
                    .put("model", "gpt-image-2.6-sunburst")
                    .put("prompt", prompt)
                    .put("size", "1024x1024")
                    .put("quality", "medium")
                    .put("n", 1)
                    .put("output_format", "png")
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val error = try {
                        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(400)
                    } catch (_: Exception) { raw.take(400) }
                    throw IllegalStateException("Görsel API hatası ($code): $error")
                }
                val encoded = JSONObject(raw).optJSONArray("data")
                    ?.optJSONObject(0)?.optString("b64_json")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: throw IllegalStateException("API görsel verisi döndürmedi.")
                bytes = Base64.decode(encoded, Base64.DEFAULT)
            } finally {
                connection.disconnect()
            }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FURKAN-AI-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FURKAN AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Görsel galeriye kaydedilemedi.")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Görsel dosyası açılamadı.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
            uri.toString()
        }
}
