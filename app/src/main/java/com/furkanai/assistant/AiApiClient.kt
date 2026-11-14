package com.furkanai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ChatMessage(
    val role: String,
    val text: String
)

object AiApiClient {
    private const val SYSTEM_PROMPT =
        "You are FURKAN AI, a helpful multilingual personal assistant. " +
        "Reply in the language the user is using unless asked otherwise. " +
        "Be clear, friendly, practical, and honest. Never claim to perform a phone action " +
        "unless the Android app has actually confirmed it."

    suspend fun reply(
        provider: AiProvider,
        model: String,
        apiKey: String,
        history: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Önce kendi API anahtarını eklemelisin." }
        require(model.isNotBlank()) { "Model adı boş olamaz." }

        when (provider) {
            AiProvider.GEMINI -> gemini(model.trim(), apiKey.trim(), history)
            AiProvider.OPENAI -> openAiResponses(
                "https://api.openai.com/v1/responses", model.trim(), apiKey.trim(), history
            )
            AiProvider.GROK -> openAiResponses(
                "https://api.x.ai/v1/responses", model.trim(), apiKey.trim(), history
            )
            AiProvider.OPENROUTER -> openRouter(model.trim(), apiKey.trim(), history)
        }
    }

    private fun gemini(model: String, key: String, history: List<ChatMessage>): String {
        val contents = JSONArray()
        history.takeLast(24).forEach { message ->
            val parts = JSONArray().put(JSONObject().put("text", message.text))
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "model" else "user")
                    .put("parts", parts)
            )
        }

        val system = JSONObject().put(
            "parts",
            JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
        )
        val body = JSONObject()
            .put("systemInstruction", system)
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 2048))

        val encodedModel = URLEncoder.encode(model, "UTF-8")
        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                encodedModel + ":generateContent",
            key,
            body,
            useGeminiHeader = true
        )

        val candidates = response.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini boş yanıt döndürdü.")
        val result = StringBuilder()
        for (index in 0 until parts.length()) {
            val text = parts.optJSONObject(index)?.optString("text").orEmpty()
            if (text.isNotBlank()) {
                if (result.isNotEmpty()) result.append("\n")
                result.append(text)
            }
        }
        return result.toString().ifBlank { "Gemini yanıt oluşturamadı." }
    }

    private fun openAiResponses(
        endpoint: String,
        model: String,
        key: String,
        history: List<ChatMessage>
    ): String {
        val transcript = history.takeLast(24).joinToString("\n\n") { message ->
            val speaker = if (message.role == "assistant") "FURKAN AI" else "Kullanıcı"
            speaker + ": " + message.text
        }
        val body = JSONObject()
            .put("model", model)
            .put("instructions", SYSTEM_PROMPT)
            .put("input", transcript)
            .put("store", false)

        return extractResponsesText(
            postJson(endpoint, key, body, useGeminiHeader = false)
        )
    }

    private fun openRouter(model: String, key: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(24).forEach { message ->
            messages.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "assistant" else "user")
                    .put("content", message.text)
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", 2048)

        val response = postJson(
            "https://openrouter.ai/api/v1/chat/completions",
            key,
            body,
            useGeminiHeader = false,
            isOpenRouter = true
        )
        val choices = response.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("OpenRouter boş yanıt döndürdü.")
        return readContent(message.opt("content")).ifBlank {
            "OpenRouter yanıt oluşturamadı."
        }
    }

    private fun extractResponsesText(response: JSONObject): String {
        val direct = response.optString("output_text")
        if (direct.isNotBlank() && direct != "null") return direct

        val output = response.optJSONArray("output") ?: return ""
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val item = content.optJSONObject(j) ?: continue
                val text = item.optString("text")
                if (text.isNotBlank() && text != "null") {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(text)
                }
            }
        }
        return result.toString().ifBlank { "AI boş yanıt döndürdü." }
    }

    private fun readContent(value: Any?): String {
        if (value is String) return value
        if (value is JSONArray) {
            val result = StringBuilder()
            for (i in 0 until value.length()) {
                val item = value.optJSONObject(i) ?: continue
                val text = item.optString("text")
                if (text.isNotBlank()) {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(text)
                }
            }
            return result.toString()
        }
        return ""
    }

    private fun postJson(
        endpoint: String,
        key: String,
        body: JSONObject,
        useGeminiHeader: Boolean,
        isOpenRouter: Boolean = false
    ): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (useGeminiHeader) {
                connection.setRequestProperty("x-goog-api-key", key)
            } else {
                connection.setRequestProperty("Authorization", "Bearer " + key)
            }
            if (isOpenRouter) {
                connection.setRequestProperty("X-Title", "FURKAN AI")
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    val error = JSONObject(raw).optJSONObject("error")
                    error?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: raw.take(500)
                } catch (_: Exception) {
                    raw.take(500)
                }
                throw IllegalStateException("API hatası (" + status + "): " + detail)
            }
            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
