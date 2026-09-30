package com.example.providers.openai

import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.providers.common.GenericSseParser
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

class OpenAiApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.openai.com/v1"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun listModels(apiKey: String, correlationId: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("User-Agent", "HubMultimodal-Android/1.0")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(mapHttpError(response.code, errorBody, "openai"))
                }

                val bodyString = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(bodyString).jsonObject
                val dataArray = root["data"]?.jsonArray ?: JsonArray(emptyList())

                val models = mutableListOf<ModelInfo>()
                dataArray.forEach { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                        val isVision = id.contains("4o") || id.contains("vision") || id.contains("4-turbo")
                        val isThinking = id.startsWith("o1") || id.startsWith("o3")
                        models.add(
                            ModelInfo(
                                id = id,
                                displayName = id,
                                providerId = "openai",
                                contextWindowTokens = if (id.contains("4o") || isThinking) 128000 else 16384,
                                maxOutputTokens = if (isThinking) 65536 else 16384,
                                supportsVision = isVision,
                                supportsThinking = isThinking,
                                supportsAudio = id.contains("audio")
                            )
                        )
                    }
                }

                if (models.isEmpty()) {
                    models.add(ModelInfo("gpt-4o", "GPT-4o", "openai", 128000, 16384, supportsVision = true))
                    models.add(ModelInfo("gpt-4o-mini", "GPT-4o Mini", "openai", 128000, 16384, supportsVision = true))
                }

                Result.success(models.sortedBy { it.displayName })
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    suspend fun testConfiguration(apiKey: String, correlationId: String): Result<GeminiConfigurationTestResult> = withContext(Dispatchers.IO) {
        val details = mutableMapOf<String, String>()
        var authValid = false
        var responds = false
        var streaming = false

        try {
            val testPayload = buildJsonObject {
                put("model", "gpt-4o-mini")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
                put("max_tokens", 1)
            }

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(testPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val startTime = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    authValid = true
                    responds = true
                    details["latency_ms"] = duration.toString()
                    details["auth"] = "Autenticação OK (Bearer token aceito)"
                    details["response"] = "Modelo respondeu em ${duration}ms"
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val err = mapHttpError(response.code, errorBody, "openai")
                    details["error"] = err.userMessage
                    return@withContext Result.failure(err)
                }
            }

            // Test stream
            val streamPayload = buildJsonObject {
                put("model", "gpt-4o-mini")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
                put("max_tokens", 1)
                put("stream", true)
            }
            val streamRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(streamPayload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(streamRequest).execute().use { response ->
                streaming = response.isSuccessful
                details["streaming"] = if (streaming) "Transmissão SSE OK" else "Falha SSE (${response.code})"
            }

            val result = GeminiConfigurationTestResult(
                isAuthValid = authValid,
                modelResponds = responds,
                streamingWorks = streaming,
                supportsVision = true,
                supportsAudio = true,
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
            val payload = buildOpenAiRequestBody(request, stream = false)
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(mapHttpError(response.code, errorBody, "openai"))
                }

                val body = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(body).jsonObject
                val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
                val finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull ?: "stop"

                val usageObj = root["usage"]?.jsonObject
                val usage = if (usageObj != null) {
                    TokenUsage(
                        promptTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        completionTokens = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        totalTokens = usageObj["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                } else null

                Result.success(ChatResponse(text = content, finishReason = finishReason, usage = usage))
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    fun streamGenerateContent(apiKey: String, request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val parser = GenericSseParser("OpenAI")
        val payload = buildOpenAiRequestBody(request, stream = true)

        emit(StreamEvent.Started(correlationId))
        val startTime = System.currentTimeMillis()
        var firstTokenReceived = false
        val accumulatedText = StringBuilder()

        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val appError = mapHttpError(response.code, errorBody, "openai")
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
                                HubLogger.log(LogLevel.INFO, LogCategory.PROVIDER, "OpenAI: Primeiro token em ${latency}ms ($correlationId).")
                            }
                            accumulatedText.append(event.text)
                        }
                        emit(event)
                    }
                    charsRead = reader.read(charBuffer)
                }

                val finalEvents = parser.finish(accumulatedText.toString())
                for (event in finalEvents) emit(event)

                emit(StreamEvent.Completed(accumulatedText.toString(), finishReason = "stop"))
            }
        } catch (e: Exception) {
            emit(StreamEvent.Failed(ErrorMapper.fromThrowable(e), accumulatedText.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildOpenAiRequestBody(request: ChatRequest, stream: Boolean): JsonObject {
        return buildJsonObject {
            put("model", request.modelId.ifEmpty { "gpt-4o" })
            putJsonArray("messages") {
                if (!request.systemPrompt.isNullOrEmpty()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    }
                }
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                        if (msg.attachments.isEmpty()) {
                            put("content", msg.content)
                        } else {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", msg.content)
                                }
                                msg.attachments.forEach { att ->
                                    if (!att.dataBase64.isNullOrEmpty()) {
                                        addJsonObject {
                                            put("type", "image_url")
                                            putJsonObject("image_url") {
                                                put("url", "data:${att.mimeType};base64,${att.dataBase64}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (stream) {
                put("stream", true)
                putJsonObject("stream_options") {
                    put("include_usage", true)
                }
            }
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
        }
    }

    private fun mapHttpError(code: Int, body: String, providerId: String): AppError {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val errorObj = root["error"]?.jsonObject
            val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: body
            val errorCode = errorObj?.get("code")?.jsonPrimitive?.contentOrNull ?: ""

            when (code) {
                401 -> AppError.Auth("ERR_OPENAI_AUTH", "Chave da OpenAI inválida ou não autorizada.", "HTTP 401: $message")
                429 -> {
                    if (errorCode == "insufficient_quota") {
                        AppError.Quota("ERR_OPENAI_INSUFFICIENT_QUOTA", "Cota da conta OpenAI esgotada.", "HTTP 429 insufficient_quota: $message")
                    } else {
                        AppError.Quota("ERR_OPENAI_RATE_LIMIT", "Limite de taxa de requisições da OpenAI atingido.", "HTTP 429: $message")
                    }
                }
                404 -> AppError.ModelUnavailable("ERR_OPENAI_MODEL_NOT_FOUND", "Modelo não encontrado na OpenAI.", "HTTP 404: $message")
                500, 503 -> AppError.ProviderUnavailable("ERR_OPENAI_OVERLOAD", "Servidor da OpenAI temporariamente indisponível.", "HTTP $code: $message")
                else -> ErrorMapper.fromHttp(code, body)
            }
        } catch (e: Exception) {
            ErrorMapper.fromHttp(code, body)
        }
    }
}
