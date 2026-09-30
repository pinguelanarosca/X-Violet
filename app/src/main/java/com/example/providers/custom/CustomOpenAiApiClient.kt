package com.example.providers.custom

import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.network.UrlSecurityValidator
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

class CustomOpenAiApiClient(
    private val providerId: String,
    private val providerTag: String,
    private val baseUrlSupplier: suspend () -> String,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun getValidatedBaseUrl(): Result<String> {
        val raw = baseUrlSupplier().trim()
        val validation = UrlSecurityValidator.validateBaseUrl(raw)
        return when (validation) {
            is UrlSecurityValidator.ValidationResult.Valid -> Result.success(validation.normalizedUrl)
            is UrlSecurityValidator.ValidationResult.Invalid -> Result.failure(validation.error)
        }
    }

    suspend fun listModels(apiKey: String, correlationId: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val baseRes = getValidatedBaseUrl()
        if (baseRes.isFailure) return@withContext Result.failure(baseRes.exceptionOrNull()!!)
        val baseUrl = baseRes.getOrThrow()

        try {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("User-Agent", "HubMultimodal-Android/1.0")

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            if (providerId == "openrouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/aistudio/hub")
                requestBuilder.addHeader("X-Title", "HubMultimodal")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(mapCustomError(response.code, errorBody, providerId))
                }

                val bodyString = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(bodyString).jsonObject
                val dataArray = root["data"]?.jsonArray ?: JsonArray(emptyList())

                val models = mutableListOf<ModelInfo>()
                dataArray.forEach { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (id.isNotEmpty()) {
                        val name = item.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: id
                        val isVision = id.contains("vision") || id.contains("4o") || id.contains("vl")
                        val isThinking = id.contains("r1") || id.contains("reason") || id.contains("o1") || id.contains("o3")
                        models.add(
                            ModelInfo(
                                id = id,
                                displayName = name,
                                providerId = providerId,
                                contextWindowTokens = 128000,
                                maxOutputTokens = 16384,
                                supportsVision = isVision,
                                supportsThinking = isThinking
                            )
                        )
                    }
                }

                if (models.isEmpty()) {
                    models.add(ModelInfo("default-model", "Modelo Padrão (Compatível)", providerId, 128000, 8192))
                }

                Result.success(models.take(100))
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    suspend fun testConfiguration(apiKey: String, correlationId: String, testModelId: String): Result<GeminiConfigurationTestResult> = withContext(Dispatchers.IO) {
        val baseRes = getValidatedBaseUrl()
        if (baseRes.isFailure) return@withContext Result.failure(baseRes.exceptionOrNull()!!)
        val baseUrl = baseRes.getOrThrow()

        val details = mutableMapOf<String, String>()
        var authValid = false
        var responds = false
        var streaming = false

        try {
            val testPayload = buildJsonObject {
                put("model", testModelId.ifEmpty { "default-model" })
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
                put("max_tokens", 1)
            }

            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(testPayload.toString().toRequestBody(jsonMediaType))

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            if (providerId == "openrouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/aistudio/hub")
                requestBuilder.addHeader("X-Title", "HubMultimodal")
            }

            val startTime = System.currentTimeMillis()
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    authValid = true
                    responds = true
                    details["latency_ms"] = duration.toString()
                    details["auth"] = "Autenticação OK ($providerTag)"
                    details["response"] = "Modelo respondeu em ${duration}ms"
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val err = mapCustomError(response.code, errorBody, providerId)
                    details["error"] = err.userMessage
                    return@withContext Result.failure(err)
                }
            }

            // Test Stream
            val streamPayload = buildJsonObject {
                put("model", testModelId.ifEmpty { "default-model" })
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "1")
                    }
                }
                put("max_tokens", 1)
                put("stream", true)
            }
            val streamBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(streamPayload.toString().toRequestBody(jsonMediaType))
            if (apiKey.isNotBlank()) streamBuilder.addHeader("Authorization", "Bearer $apiKey")

            okHttpClient.newCall(streamBuilder.build()).execute().use { response ->
                streaming = response.isSuccessful
                details["streaming"] = if (streaming) "Transmissão SSE OK" else "Falha SSE (${response.code})"
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
        val baseRes = getValidatedBaseUrl()
        if (baseRes.isFailure) return@withContext Result.failure(baseRes.exceptionOrNull()!!)
        val baseUrl = baseRes.getOrThrow()

        try {
            val payload = buildCustomRequestBody(request, stream = false)
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(payload.toString().toRequestBody(jsonMediaType))

            if (apiKey.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            if (providerId == "openrouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/aistudio/hub")
                requestBuilder.addHeader("X-Title", "HubMultimodal")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(mapCustomError(response.code, errorBody, providerId))
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
        val baseRes = getValidatedBaseUrl()
        if (baseRes.isFailure) {
            emit(StreamEvent.Failed(baseRes.exceptionOrNull() as? AppError ?: AppError.InvalidRequest(code = "ERR_BASE_URL", userMessage = "URL inválida", technicalCause = "URL validation error"), null))
            return@flow
        }
        val baseUrl = baseRes.getOrThrow()

        val parser = GenericSseParser(providerTag)
        val payload = buildCustomRequestBody(request, stream = true)

        emit(StreamEvent.Started(correlationId))
        val startTime = System.currentTimeMillis()
        var firstTokenReceived = false
        val accumulatedText = StringBuilder()

        try {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(payload.toString().toRequestBody(jsonMediaType))

            if (apiKey.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            if (providerId == "openrouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/aistudio/hub")
                requestBuilder.addHeader("X-Title", "HubMultimodal")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val appError = mapCustomError(response.code, errorBody, providerId)
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
                                HubLogger.log(LogLevel.INFO, LogCategory.PROVIDER, "$providerTag: Primeiro token em ${latency}ms ($correlationId).")
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

    private fun buildCustomRequestBody(request: ChatRequest, stream: Boolean): JsonObject {
        return buildJsonObject {
            put("model", request.modelId.ifEmpty { "default-model" })
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
            }
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
        }
    }

    private fun mapCustomError(code: Int, body: String, providerId: String): AppError {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val errorObj = root["error"]?.jsonObject
            val message = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: body

            when (code) {
                401 -> AppError.Auth("ERR_${providerId.uppercase()}_AUTH", "Chave de autenticação não aceita por $providerTag.", "HTTP 401: $message")
                402 -> AppError.Quota("ERR_${providerId.uppercase()}_NO_CREDITS", "Saldo ou créditos insuficientes na conta ($providerTag).", "HTTP 402: $message")
                429 -> AppError.Quota("ERR_${providerId.uppercase()}_RATE_LIMIT", "Limite de taxa atingido para $providerTag.", "HTTP 429: $message")
                404 -> AppError.ModelUnavailable("ERR_${providerId.uppercase()}_NOT_FOUND", "Endpoint ou modelo não encontrado no servidor $providerTag.", "HTTP 404: $message")
                500, 502, 503 -> AppError.ProviderUnavailable("ERR_${providerId.uppercase()}_SERVER_ERROR", "Servidor de $providerTag temporariamente inacessível ou sobrecarregado.", "HTTP $code: $message")
                else -> ErrorMapper.fromHttp(code, body)
            }
        } catch (e: Exception) {
            ErrorMapper.fromHttp(code, body)
        }
    }
}
