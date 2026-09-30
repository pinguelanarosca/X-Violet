package com.example.providers.common

import com.example.errors.AppError
import com.example.providers.model.StreamEvent
import kotlinx.serialization.json.*

/**
 * Robust SSE parser for standard OpenAI-compatible endpoints (OpenAI, xAI, OpenRouter, Groq, vLLM, Ollama, etc.).
 * Handles:
 * - Chunk fragmentation and partial lines
 * - Split UTF-8 sequences across packets
 * - Multiple `data:` lines and `data: [DONE]`
 * - Keep-alive comments (`: ping` or `: keep-alive`)
 * - Mid-stream JSON error messages
 * - Token usage extraction in final stream event
 */
class GenericSseParser(private val providerTag: String = "OpenAI-Compat") {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var lineBuffer = StringBuilder()

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

        // Comments and keep-alive lines start with ':'
        if (line.startsWith(":")) {
            return emptyList()
        }

        val payload = when {
            line.startsWith("data:") -> line.removePrefix("data:").trim()
            line.startsWith("{") -> line.trim()
            else -> return emptyList()
        }

        if (payload == "[DONE]" || payload.isEmpty()) {
            return emptyList()
        }

        try {
            val element = json.parseToJsonElement(payload).jsonObject

            // Check for mid-stream error object: {"error": {"message": "...", "type": "...", "code": ...}}
            if (element.containsKey("error")) {
                val errorObj = element["error"]?.jsonObject
                val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Erro durante transmissão SSE"
                val code = errorObj?.get("code")?.jsonPrimitive?.contentOrNull ?: "ERR_STREAM_PAYLOAD"
                events.add(
                    StreamEvent.Failed(
                        AppError.InvalidResponse(
                            code = "ERR_${providerTag.uppercase()}_STREAM",
                            userMessage = "Erro retornado pelo provedor $providerTag: $message",
                            technicalCause = "SSE payload error: code=$code, msg=$message",
                            sanitizedSnippet = message
                        ),
                        null
                    )
                )
                return events
            }

            // Parse choices delta
            val choices = element["choices"]?.jsonArray
            if (choices != null && choices.isNotEmpty()) {
                val firstChoice = choices[0].jsonObject
                val delta = firstChoice["delta"]?.jsonObject

                // Content text delta
                val text = delta?.get("content")?.jsonPrimitive?.contentOrNull
                if (!text.isNullOrEmpty()) {
                    events.add(StreamEvent.TextDelta(text))
                }

                // Reasoning / Thinking delta (e.g. DeepSeek R1 / o1 / o3)
                val reasoning = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                    ?: delta?.get("reasoning")?.jsonPrimitive?.contentOrNull
                if (!reasoning.isNullOrEmpty()) {
                    events.add(StreamEvent.ReasoningDelta(reasoning))
                }

                // Tool Calls delta
                val toolCalls = delta?.get("tool_calls")?.jsonArray
                toolCalls?.forEach { tc ->
                    val tcObj = tc.jsonObject
                    val id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "call_${System.currentTimeMillis()}"
                    val fn = tcObj["function"]?.jsonObject
                    val name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: "tool"
                    val args = fn?.get("arguments")?.jsonPrimitive?.contentOrNull ?: ""
                    events.add(StreamEvent.ToolCall(id = id, toolName = name, argumentsJson = args))
                }

                val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull
                if (finishReason != null && finishReason != "stop") {
                    when (finishReason) {
                        "length" -> events.add(StreamEvent.Completed("", finishReason = "MAX_TOKENS"))
                        "content_filter" -> events.add(
                            StreamEvent.Failed(
                                AppError.SafetyBlocked(
                                    code = "ERR_CONTENT_FILTER",
                                    userMessage = "A resposta foi interrompida pelos filtros de conteúdo do provedor.",
                                    technicalCause = "finish_reason == content_filter"
                                ),
                                null
                            )
                        )
                    }
                }
            }

            // Usage in stream_options
            val usage = element["usage"]?.jsonObject
            if (usage != null) {
                val promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val completionTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val totalTokens = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: (promptTokens + completionTokens)
                events.add(StreamEvent.Usage(promptTokens, completionTokens, totalTokens))
            }

        } catch (e: Exception) {
            // Ignore corrupted chunk gracefully
        }

        return events
    }
}
