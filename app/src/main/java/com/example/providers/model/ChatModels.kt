package com.example.providers.model

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class ChatAttachment(
    val mimeType: String,
    val dataBase64: String? = null,
    val fileUri: String? = null
)

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null
)

data class ChatRequest(
    val modelId: String,
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val reasoningEffort: String? = null, // "low", "medium", "high"
    val tools: List<ToolDefinition> = emptyList()
)

data class ChatResponse(
    val text: String,
    val finishReason: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: TokenUsage? = null
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
