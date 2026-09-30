package com.example.providers.model

import com.example.errors.AppError

sealed class StreamEvent {
    data class Started(val correlationId: String) : StreamEvent()
    data class TextDelta(val text: String) : StreamEvent()
    data class ReasoningDelta(val reasoning: String) : StreamEvent()
    data class ToolCall(val id: String, val toolName: String, val argumentsJson: String) : StreamEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : StreamEvent()
    data class Completed(val fullText: String, val finishReason: String) : StreamEvent()
    data class Failed(val error: AppError, val partialText: String?) : StreamEvent()
}
