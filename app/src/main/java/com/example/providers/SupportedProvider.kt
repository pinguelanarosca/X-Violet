package com.example.providers

enum class SupportedProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val isMultimodalLiveSupported: Boolean
) {
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com", true),
    OPENAI("openai", "OpenAI", "https://api.openai.com", true),
    ANTHROPIC("anthropic", "Anthropic (Claude)", "https://api.anthropic.com", false),
    GROQ("groq", "Groq", "https://api.groq.com/openai", false),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api", false),
    XAI("xai", "xAI (Grok)", "https://api.x.ai", false),
    CUSTOM_OPENAI("custom_openai", "Compatível com OpenAI (Local / Custom)", "http://localhost:11434/v1", false)
}
