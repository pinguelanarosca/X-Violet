package com.example.providers.anthropic

import com.example.errors.AppError
import com.example.providers.model.StreamEvent
import kotlinx.serialization.json.*

/**
 * SSE Parser for Anthropic Claude Messages API (`/v1/messages`).
 * Handles event types:
 * - `event: message_start` -> contains message metadata and initial input_tokens
 * - `event: content_block_start`
 * - `event: content_block_delta` -> text_delta or thinking_delta
 * - `event: content_block_stop`
 * - `event: message_delta` -> stop_reason, output_tokens
 * - `event: message_stop` -> completed
 * - `event: ping` -> ignored keep-alive
 * - `event: error` -> AppError mapping
 */
class AnthropicStreamParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var lineBuffer = StringBuilder()
    private var currentEventType = "message"
    private var promptTokens = 0
    private var completionTokens = 0

    fun processChunk(chunk: String, correlationId: String): List<StreamEvent> {
        lineBuffer.append(chunk)
        val events = mutableListOf<StreamEvent>()

        var newlineIndex = lineBuffer.indexOf('\n')
        while (newlineIndex != -1) {
            val line = lineBuffer.substring(0, newlineIndex).trim()
            lineBuffer.delete(0, newlineIndex + 1)

            if (line.isNotEmpty()) {
                events.addAll(parseSseLine(line, correlationId))
            }
            newlineIndex = lineBuffer.indexOf('\n')
        }

        return events
    }

    fun finish(fullAccumulatedText: String): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        val remaining = lineBuffer.toString().trim()
        if (remaining.isNotEmpty()) {
            events.addAll(parseSseLine(remaining, "FLUSH"))
        }
        lineBuffer.clear()
        return events
    }

    fun parseSseLine(line: String, correlationId: String): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()

        if (line.startsWith(":")) {
            return emptyList()
        }

        if (line.startsWith("event:")) {
            currentEventType = line.removePrefix("event:").trim()
            return emptyList()
        }

        val payload = when {
            line.startsWith("data:") -> line.removePrefix("data:").trim()
            line.startsWith("{") -> line.trim()
            else -> return emptyList()
        }

        if (payload.isEmpty()) return emptyList()

        try {
            val element = json.parseToJsonElement(payload).jsonObject

            // Check for error event or error object
            if (currentEventType == "error" || element.containsKey("error")) {
                val errorObj = element["error"]?.jsonObject ?: element
                val errorType = errorObj["type"]?.jsonPrimitive?.contentOrNull ?: "ERR_ANTHROPIC"
                val message = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "Erro na transmissão do Claude"

                val appError = when (errorType) {
                    "overloaded_error" -> AppError.ProviderUnavailable(
                        code = "ERR_CLAUDE_OVERLOAD",
                        userMessage = "O serviço da Anthropic está temporariamente sobrecarregado.",
                        technicalCause = "Anthropic 529 overloaded_error: $message"
                    )
                    "rate_limit_error" -> AppError.Quota(
                        code = "ERR_CLAUDE_QUOTA",
                        userMessage = "Limite de requisições do Claude atingido.",
                        technicalCause = "Anthropic 429 rate_limit_error: $message"
                    )
                    "authentication_error" -> AppError.Auth(
                        code = "ERR_CLAUDE_AUTH",
                        userMessage = "Chave de API do Claude inválida ou revogada.",
                        technicalCause = "Anthropic 401: $message"
                    )
                    else -> AppError.InvalidResponse(
                        code = "ERR_CLAUDE_STREAM",
                        userMessage = "Erro retornado pelo Claude: $message",
                        technicalCause = "$errorType: $message"
                    )
                }

                events.add(StreamEvent.Failed(appError, null))
                return events
            }

            when (currentEventType) {
                "message_start" -> {
                    val message = element["message"]?.jsonObject
                    val usage = message?.get("usage")?.jsonObject
                    promptTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                }

                "content_block_delta" -> {
                    val delta = element["delta"]?.jsonObject
                    val deltaType = delta?.get("type")?.jsonPrimitive?.contentOrNull

                    when (deltaType) {
                        "text_delta" -> {
                            val text = delta["text"]?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrEmpty()) {
                                events.add(StreamEvent.TextDelta(text))
                            }
                        }
                        "thinking_delta" -> {
                            val thinking = delta["thinking"]?.jsonPrimitive?.contentOrNull
                            if (!thinking.isNullOrEmpty()) {
                                events.add(StreamEvent.ReasoningDelta(thinking))
                            }
                        }
                        "input_json_delta" -> {
                            // Tool use input json fragment
                        }
                    }
                }

                "message_delta" -> {
                    val usage = element["usage"]?.jsonObject
                    if (usage != null) {
                        completionTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: completionTokens
                        val total = promptTokens + completionTokens
                        events.add(StreamEvent.Usage(promptTokens, completionTokens, total))
                    }
                    val delta = element["delta"]?.jsonObject
                    val stopReason = delta?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                    if (stopReason == "max_tokens") {
                        events.add(StreamEvent.Completed("", finishReason = "MAX_TOKENS"))
                    }
                }

                "message_stop" -> {
                    events.add(StreamEvent.Completed("", finishReason = "STOP"))
                }
            }

        } catch (e: Exception) {
            // Malformed chunk skipped
        }

        return events
    }
}
