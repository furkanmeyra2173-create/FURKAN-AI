package com.furkanai.assistant

enum class AiProvider(val id: String, val title: String, val defaultModel: String) {
    AUTO("auto", "Otomatik AI", ""),
    GEMINI("gemini", "Google Gemini", "gemini-3.8-flash"),
    OPENAI("openai", "OpenAI", "gpt-6-luna"),
    GROK("grok", "Grok (xAI)", "grok-4.7"),
    OPENROUTER("openrouter", "OpenRouter", "openrouter/auto");

    companion object {
        fun fromId(id: String?): AiProvider = values().firstOrNull { it.id == id } ?: GEMINI
    }
}
