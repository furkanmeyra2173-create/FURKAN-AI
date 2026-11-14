package com.furkanai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ChatMessage(val role: String, val text: String)

object AiApiClient {
    private const val DEFAULT_PROMPT =
        "You are FURKAN AI, a helpful multilingual personal assistant. Reply in the user's language. " +
        "Be practical, clear and honest. Never claim an action was performed unless confirmed. " +
        "Ask before sensitive or irreversible actions."

    suspend fun reply(
        provider: AiProvider,
        model: String,
        apiKey: String,
        history: List<ChatMessage>,
        systemPrompt: String = DEFAULT_PROMPT,
        webSearch: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        require(provider != AiProvider.AUTO) { "Otomatik mod önce sağlayıcı seçmeli." }
        require(apiKey.isNotBlank()) { "API anahtarı eklenmemiş." }
        require(model.isNotBlank()) { "Model adı boş olamaz." }
        when (provider) {
            AiProvider.GEMINI -> gemini(model, apiKey, history, systemPrompt, webSearch)
            AiProvider.OPENAI -> responses("https://api.openai.com/v1/responses", model, apiKey, history, systemPrompt, webSearch)
            AiProvider.GROK -> responses("https://api.x.ai/v1/responses", model, apiKey, history, systemPrompt, webSearch)
            AiProvider.OPENROUTER -> openRouter(model, apiKey, history, systemPrompt, webSearch)
            AiProvider.AUTO -> error("Otomatik mod yönlendirilmeden kullanılamaz.")
        }
    }

    private fun gemini(model: String, key: String, history: List<ChatMessage>, prompt: String, search: Boolean): String {
        val contents = JSONArray()
        history.takeLast(32).forEach {
            contents.put(JSONObject().put("role", if (it.role == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
        if (search) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
            URLEncoder.encode(model, "UTF-8") + ":generateContent"
        val response = post(endpoint, key, body, true)
        val parts = response.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini boş yanıt döndürdü.")
        val result = StringBuilder()
        for (i in 0 until parts.length()) {
            val value = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (value.isNotBlank()) {
                if (result.isNotEmpty()) result.append("\n")
                result.append(value)
            }
        }
        return result.toString().ifBlank { "Gemini yanıt oluşturamadı." }
    }

    private fun responses(
        endpoint: String, model: String, key: String, history: List<ChatMessage>,
        prompt: String, search: Boolean
    ): String {
        val input = JSONArray()
        history.takeLast(32).forEach {
            input.put(JSONObject().put("role", if (it.role == "assistant") "assistant" else "user").put("content", it.text))
        }
        val body = JSONObject().put("model", model).put("instructions", prompt)
            .put("input", input).put("store", false)
        if (search) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        return extractResponses(post(endpoint, key, body, false))
    }

    private fun openRouter(
        model: String, key: String, history: List<ChatMessage>, prompt: String, search: Boolean
    ): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", prompt))
        history.takeLast(32).forEach {
            messages.put(JSONObject().put("role", if (it.role == "assistant") "assistant" else "user").put("content", it.text))
        }
        val body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", 4096)
        if (search) body.put("tools", JSONArray().put(JSONObject().put("type", "openrouter:web_search")))
        val response = post("https://openrouter.ai/api/v1/chat/completions", key, body, false, true)
        val message = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("OpenRouter boş yanıt döndürdü.")
        return readContent(message.opt("content")).ifBlank { "OpenRouter yanıt oluşturamadı." }
    }

    private fun extractResponses(response: JSONObject): String {
        val direct = response.optString("output_text")
        if (direct.isNotBlank() && direct != "null") return direct
        val output = response.optJSONArray("output") ?: return ""
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val contents = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until contents.length()) {
                val text = contents.optJSONObject(j)?.optString("text").orEmpty()
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
                val text = value.optJSONObject(i)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(text)
                }
            }
            return result.toString()
        }
        return ""
    }

    private fun post(
        endpoint: String, key: String, body: JSONObject,
        geminiKey: Boolean, openRouter: Boolean = false
    ): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 180000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(if (geminiKey) "x-goog-api-key" else "Authorization",
                if (geminiKey) key else "Bearer $key")
            if (openRouter) connection.setRequestProperty("X-Title", "FURKAN AI")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() && it != "null" } ?: raw.take(500)
                } catch (_: Exception) { raw.take(500) }
                throw IllegalStateException("API hatası ($code): $detail")
            }
            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
