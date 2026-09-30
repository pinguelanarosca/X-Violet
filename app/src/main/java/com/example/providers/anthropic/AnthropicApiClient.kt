package com.example.providers.anthropic

import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.providers.gemini.GeminiConfigurationTestResult
import com.example.providers.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AnthropicApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.anthropic.com/v1"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun listModels(apiKey: String, correlationId: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        // Return official curated list of current Claude models with true specs
        Result.success(
            listOf(
                ModelInfo(
                    id = "claude-3-7-sonnet-latest",
                    displayName = "Claude 3.7 Sonnet (Thinking)",
                    providerId = "anthropic",
                    contextWindowTokens = 200000,
                    maxOutputTokens = 8192,
                    supportsVision = true,
                    supportsThinking = true,
                    description = "Modelo híbrido de raciocínio avançado da Anthropic."
                ),
                ModelInfo(
                    id = "claude-3-5-sonnet-latest",
                    displayName = "Claude 3.5 Sonnet",
                    providerId = "anthropic",
                    contextWindowTokens = 200000,
                    maxOutputTokens = 8192,
                    supportsVision = true,
                    supportsThinking = false,
                    description = "Modelo inteligente de alta precisão e visão."
                ),
                ModelInfo(
                    id = "claude-3-5-haiku-latest",
                    displayName = "Claude 3.5 Haiku",
                    providerId = "anthropic",
                    contextWindowTokens = 200000,
                    maxOutputTokens = 8192,
                    supportsVision = true,
                    supportsThinking = false,
                    description = "Modelo ultrarrápido e econômico da Anthropic."
                )
            )
        )
    }

    suspend fun testConfiguration(apiKey: String, correlationId: String): Result<GeminiConfigurationTestResult> = withContext(Dispatchers.IO) {
        val details = mutableMapOf<String, String>()
        var authValid = false
        var responds = false
        var streaming = false

        try {
            val testPayload = buildJsonObject {
                put("model", "claude-3-5-haiku-latest")
                put("max_tokens", 1)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(testPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val startTime = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    authValid = true
                    responds = true
                    details["latency_ms"] = duration.toString()
                    details["auth"] = "Autenticação OK (x-api-key válida)"
                    details["response"] = "Modelo respondeu em ${duration}ms"
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val err = mapAnthropicError(response.code, errorBody)
                    details["error"] = err.userMessage
                    return@withContext Result.failure(err)
                }
            }

            // Test Stream
            val streamPayload = buildJsonObject {
                put("model", "claude-3-5-haiku-latest")
                put("max_tokens", 1)
                put("stream", true)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
            }
            val streamRequest = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(streamPayload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(streamRequest).execute().use { response ->
                streaming = response.isSuccessful
                details["streaming"] = if (streaming) "Transmissão SSE Claude OK" else "Falha SSE Claude (${response.code})"
            }

            val result = GeminiConfigurationTestResult(
                isAuthValid = authValid,
                modelResponds = responds,
                streamingWorks = streaming,
                supportsVision = true,
                supportsAudio = false,
                supportsTools = true,
                details = details
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    suspend fun generateContent(apiKey: String, request: ChatRequest, correlationId: String): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = buildAnthropicRequestBody(request, stream = false)
            val httpRequest = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(mapAnthropicError(response.code, errorBody))
                }

                val body = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(body).jsonObject
                val contentArray = root["content"]?.jsonArray
                val textBuilder = StringBuilder()

                contentArray?.forEach { block ->
                    val blockObj = block.jsonObject
                    if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                        blockObj["text"]?.jsonPrimitive?.contentOrNull?.let { textBuilder.append(it) }
                    }
                }

                val stopReason = root["stop_reason"]?.jsonPrimitive?.contentOrNull ?: "end_turn"
                val usageObj = root["usage"]?.jsonObject
                val usage = if (usageObj != null) {
                    val input = usageObj["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                    val output = usageObj["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                    TokenUsage(input, output, input + output)
                } else null

                Result.success(ChatResponse(text = textBuilder.toString(), finishReason = stopReason, usage = usage))
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    fun streamGenerateContent(apiKey: String, request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val parser = AnthropicStreamParser()
        val payload = buildAnthropicRequestBody(request, stream = true)

        emit(StreamEvent.Started(correlationId))
        val startTime = System.currentTimeMillis()
        var firstTokenReceived = false
        val accumulatedText = StringBuilder()

        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val appError = mapAnthropicError(response.code, errorBody)
                    emit(StreamEvent.Failed(appError, accumulatedText.toString()))
                    return@flow
                }

                val source = response.body?.source() ?: return@flow
                val reader = BufferedReader(InputStreamReader(source.inputStream(), StandardCharsets.UTF_8))
                val charBuffer = CharArray(1024)
                var charsRead = reader.read(charBuffer)

                while (charsRead != -1) {
                    val chunk = String(charBuffer, 0, charsRead)
                    val events = parser.processChunk(chunk, correlationId)

                    for (event in events) {
                        if (event is StreamEvent.TextDelta) {
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                val latency = System.currentTimeMillis() - startTime
                                HubLogger.log(LogLevel.INFO, LogCategory.PROVIDER, "Claude: Primeiro token em ${latency}ms ($correlationId).")
                            }
                            accumulatedText.append(event.text)
                        }
                        emit(event)
                    }
                    charsRead = reader.read(charBuffer)
                }

                val finalEvents = parser.finish(accumulatedText.toString())
                for (event in finalEvents) emit(event)

                emit(StreamEvent.Completed(accumulatedText.toString(), finishReason = "end_turn"))
            }
        } catch (e: Exception) {
            emit(StreamEvent.Failed(ErrorMapper.fromThrowable(e), accumulatedText.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildAnthropicRequestBody(request: ChatRequest, stream: Boolean): JsonObject {
        return buildJsonObject {
            put("model", request.modelId.ifEmpty { "claude-3-7-sonnet-latest" })
            put("max_tokens", request.maxTokens ?: 4096)
            if (stream) put("stream", true)
            request.temperature?.let { put("temperature", it) }
            if (!request.systemPrompt.isNullOrEmpty()) {
                put("system", request.systemPrompt)
            }

            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                        if (msg.attachments.isEmpty()) {
                            put("content", msg.content)
                        } else {
                            putJsonArray("content") {
                                if (msg.content.isNotEmpty()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    }
                                }
                                msg.attachments.forEach { att ->
                                    if (!att.dataBase64.isNullOrEmpty()) {
                                        addJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", att.mimeType)
                                                put("data", att.dataBase64)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mapAnthropicError(code: Int, body: String): AppError {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val errorObj = root["error"]?.jsonObject
            val type = errorObj?.get("type")?.jsonPrimitive?.contentOrNull ?: ""
            val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: body

            when (code) {
                401 -> AppError.Auth("ERR_CLAUDE_AUTH", "Chave de API do Claude inválida.", "HTTP 401: $message")
                429 -> AppError.Quota("ERR_CLAUDE_QUOTA", "Limite de taxa do Claude atingido.", "HTTP 429: $message")
                529 -> AppError.ProviderUnavailable("ERR_CLAUDE_OVERLOAD", "Serviço da Anthropic sobrecarregado no momento.", "HTTP 529 overloaded: $message")
                404 -> AppError.ModelUnavailable("ERR_CLAUDE_MODEL", "Modelo Claude não disponível na sua conta.", "HTTP 404: $message")
                else -> ErrorMapper.fromHttp(code, body)
            }
        } catch (e: Exception) {
            ErrorMapper.fromHttp(code, body)
        }
    }
}
