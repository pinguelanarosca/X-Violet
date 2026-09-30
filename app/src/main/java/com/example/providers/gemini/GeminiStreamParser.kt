package com.example.providers.gemini

import com.example.errors.AppError
import com.example.providers.model.StreamEvent
import kotlinx.serialization.json.*

class GeminiStreamParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var lineBuffer = StringBuilder()

    /**
     * Parses incoming chunk text (which may contain multiple lines or partial lines)
     * and emits zero or more StreamEvent objects.
     */
    fun processChunk(chunk: String, correlationId: String): List<StreamEvent> {
        lineBuffer.append(chunk)
        val events = mutableListOf<StreamEvent>()

        var newlineIndex = lineBuffer.indexOf('\n')
        while (newlineIndex != -1) {
            val line = lineBuffer.substring(0, newlineIndex).trim()
            lineBuffer.delete(0, newlineIndex + 1)

            if (line.isNotEmpty()) {
                val parsed = parseSseLine(line, correlationId)
                events.addAll(parsed)
            }
            newlineIndex = lineBuffer.indexOf('\n')
        }

        return events
    }

    /**
     * Flushes remaining buffer when stream finishes.
     */
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

        // SSE data line e.g., "data: {"candidates": ...}"
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

            // Check for mid-stream error object: {"error": {"code": 400, "message": "..."}}
            if (element.containsKey("error")) {
                val errorObj = element["error"]?.jsonObject
                val code = errorObj?.get("code")?.jsonPrimitive?.contentOrNull ?: "ERR_GEMINI_STREAM"
                val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Erro no fluxo do Gemini"
                events.add(
                    StreamEvent.Failed(
                        AppError.InvalidResponse(
                            code = "ERR_GEMINI_STREAM_ERROR",
                            userMessage = "Erro retornado durante a transmissão do Gemini: $message",
                            technicalCause = "HTTP/SSE payload error: code=$code, msg=$message",
                            sanitizedSnippet = message
                        ),
                        null
                    )
                )
                return events
            }

            // Parse Candidates
            val candidates = element["candidates"]?.jsonArray
            if (candidates != null && candidates.isNotEmpty()) {
                val firstCandidate = candidates[0].jsonObject
                val content = firstCandidate["content"]?.jsonObject
                val parts = content?.get("parts")?.jsonArray

                if (parts != null) {
                    for (part in parts) {
                        val partObj = part.jsonObject
                        val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                        if (!text.isNullOrEmpty()) {
                            events.add(StreamEvent.TextDelta(text))
                        }

                        // Function call / Tool Call
                        val functionCall = partObj["functionCall"]?.jsonObject
                        if (functionCall != null) {
                            val name = functionCall["name"]?.jsonPrimitive?.contentOrNull ?: "tool"
                            val args = functionCall["args"]?.toString() ?: "{}"
                            events.add(StreamEvent.ToolCall(id = "call_${System.currentTimeMillis()}", toolName = name, argumentsJson = args))
                        }
                    }
                }

                val finishReason = firstCandidate["finishReason"]?.jsonPrimitive?.contentOrNull
                if (finishReason != null && finishReason != "STOP") {
                    when (finishReason) {
                        "SAFETY" -> {
                            events.add(
                                StreamEvent.Failed(
                                    AppError.SafetyBlocked(
                                        code = "ERR_SAFETY_BLOCKED",
                                        userMessage = "A resposta foi interrompida pelas políticas de segurança do Gemini.",
                                        technicalCause = "finishReason == SAFETY"
                                    ),
                                    null
                                )
                            )
                        }
                        "MAX_TOKENS" -> {
                            events.add(StreamEvent.Completed("", finishReason = "MAX_TOKENS"))
                        }
                        "RECITATION" -> {
                            events.add(
                                StreamEvent.Failed(
                                    AppError.SafetyBlocked(
                                        code = "ERR_RECITATION_BLOCKED",
                                        userMessage = "A transmissão foi bloqueada por conter recitação de conteúdo protegido.",
                                        technicalCause = "finishReason == RECITATION"
                                    ),
                                    null
                                )
                            )
                        }
                    }
                }
            }

            // Parse usage metadata
            val usage = element["usageMetadata"]?.jsonObject
            if (usage != null) {
                val promptTokens = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                val candidatesTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                val totalTokens = usage["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: (promptTokens + candidatesTokens)
                events.add(StreamEvent.Usage(promptTokens, candidatesTokens, totalTokens))
            }

        } catch (e: Exception) {
            // Malformed line snippet is skipped or ignored gracefully per Rule 10
        }

        return events
    }
}
