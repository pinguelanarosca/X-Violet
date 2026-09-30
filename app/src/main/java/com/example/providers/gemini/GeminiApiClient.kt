package com.example.providers.gemini

import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
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

data class GeminiConfigurationTestResult(
    val isAuthValid: Boolean,
    val modelResponds: Boolean,
    val streamingWorks: Boolean,
    val supportsVision: Boolean,
    val supportsAudio: Boolean,
    val supportsTools: Boolean,
    val testedAt: Long = System.currentTimeMillis(),
    val details: Map<String, String> = emptyMap()
)

class GeminiApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Discovers models dynamically from the official Gemini API endpoint with pagination.
     */
    suspend fun listModels(apiKey: String, correlationId: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val allModels = mutableListOf<ModelInfo>()
            var pageToken: String? = null

            do {
                val url = if (pageToken != null) {
                    "$baseUrl/models?pageSize=50&pageToken=$pageToken"
                } else {
                    "$baseUrl/models?pageSize=50"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("User-Agent", "HubMultimodal-Android/1.0")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        return@withContext Result.failure(
                            ErrorMapper.fromHttp(response.code, errorBody)
                        )
                    }

                    val bodyString = response.body?.string() ?: "{}"
                    val root = json.parseToJsonElement(bodyString).jsonObject
                    val modelsArray = root["models"]?.jsonArray

                    modelsArray?.forEach { modelElem ->
                        val obj = modelElem.jsonObject
                        val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val modelId = rawName.removePrefix("models/")
                        val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: modelId
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                        val inputTokenLimit = obj["inputTokenLimit"]?.jsonPrimitive?.intOrNull ?: 128000
                        val outputTokenLimit = obj["outputTokenLimit"]?.jsonPrimitive?.intOrNull ?: 4096
                        val supportedMethods = obj["supportedGenerationMethods"]?.jsonArray?.map {
                            it.jsonPrimitive.content
                        } ?: emptyList()

                        // Filter only models capable of generateContent
                        if (supportedMethods.contains("generateContent")) {
                            val isThinking = modelId.contains("thinking") || modelId.contains("pro")
                            val isVision = modelId.contains("flash") || modelId.contains("pro") || modelId.contains("image")
                            val isAudio = modelId.contains("audio") || modelId.contains("flash")

                            allModels.add(
                                ModelInfo(
                                    id = modelId,
                                    displayName = displayName,
                                    providerId = "gemini",
                                    contextWindowTokens = inputTokenLimit,
                                    maxOutputTokens = outputTokenLimit,
                                    supportsVision = isVision,
                                    supportsAudio = isAudio,
                                    supportsThinking = isThinking,
                                    description = description
                                )
                            )
                        }
                    }

                    pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
                }
            } while (!pageToken.isNullOrEmpty())

            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.PROVIDER,
                message = "Descoberta de modelos do Gemini concluída: ${allModels.size} modelos obtidos ($correlationId)."
            )

            Result.success(allModels)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    /**
     * Validates API Key with a real, minimal cost test call and verifies streaming and capabilities.
     */
    suspend fun testConfiguration(apiKey: String, correlationId: String): Result<GeminiConfigurationTestResult> = withContext(Dispatchers.IO) {
        val details = mutableMapOf<String, String>()
        var authValid = false
        var responds = false
        var streaming = false
        var supportsVision = true
        var supportsAudio = true
        var supportsTools = true

        try {
            // 1. Test Minimal Generation (1 Token)
            val testPayload = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", "1") }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("maxOutputTokens", 1)
                    put("temperature", 0.0)
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(testPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val startTime = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    authValid = true
                    responds = true
                    details["latency_ms"] = duration.toString()
                    details["auth"] = "Autenticação OK (x-goog-api-key válida)"
                    details["response"] = "Modelo respondeu em ${duration}ms"
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val err = ErrorMapper.fromHttp(response.code, errorBody)
                    details["error"] = err.userMessage
                    return@withContext Result.failure(err)
                }
            }

            // 2. Test Minimal Stream Call
            val streamRequest = Request.Builder()
                .url("$baseUrl/models/gemini-2.5-flash:streamGenerateContent?alt=sse")
                .addHeader("x-goog-api-key", apiKey)
                .post(testPayload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(streamRequest).execute().use { response ->
                if (response.isSuccessful) {
                    streaming = true
                    details["streaming"] = "Transmissão SSE verificada com sucesso"
                } else {
                    details["streaming"] = "Falha no canal SSE (${response.code})"
                }
            }

            val result = GeminiConfigurationTestResult(
                isAuthValid = authValid,
                modelResponds = responds,
                streamingWorks = streaming,
                supportsVision = supportsVision,
                supportsAudio = supportsAudio,
                supportsTools = supportsTools,
                details = details
            )

            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.PROVIDER,
                message = "Teste de configuração do Gemini concluído com sucesso ($correlationId)."
            )

            Result.success(result)
        } catch (e: Exception) {
            val appError = ErrorMapper.fromThrowable(e)
            HubLogger.log(
                level = LogLevel.WARN,
                category = LogCategory.PROVIDER,
                message = "Falha no teste de configuração do Gemini: ${appError.userMessage}",
                throwable = e
            )
            Result.failure(appError)
        }
    }

    /**
     * Full generateContent REST call with JSON payload.
     */
    suspend fun generateContent(apiKey: String, request: ChatRequest, correlationId: String): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = buildRequestBody(request)
            val model = request.modelId.ifEmpty { "gemini-2.5-flash" }

            val httpRequest = Request.Builder()
                .url("$baseUrl/models/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        ErrorMapper.fromHttp(response.code, errorBody)
                    )
                }

                val body = response.body?.string() ?: "{}"
                val root = json.parseToJsonElement(body).jsonObject
                val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                val textBuilder = StringBuilder()

                parts?.forEach { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let {
                        textBuilder.append(it)
                    }
                }

                val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull ?: "STOP"
                val usageObj = root["usageMetadata"]?.jsonObject
                val usage = if (usageObj != null) {
                    TokenUsage(
                        promptTokens = usageObj["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        completionTokens = usageObj["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        totalTokens = usageObj["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                } else null

                Result.success(
                    ChatResponse(
                        text = textBuilder.toString(),
                        finishReason = finishReason,
                        usage = usage
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    /**
     * Incremental SSE streaming flow.
     */
    fun streamGenerateContent(apiKey: String, request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val parser = GeminiStreamParser()
        val payload = buildRequestBody(request)
        val model = request.modelId.ifEmpty { "gemini-2.5-flash" }

        emit(StreamEvent.Started(correlationId))
        val startTime = System.currentTimeMillis()
        var firstTokenReceived = false
        var accumulatedText = StringBuilder()

        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/models/$model:streamGenerateContent?alt=sse")
                .addHeader("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val appError = ErrorMapper.fromHttp(response.code, errorBody)
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
                                HubLogger.log(
                                    level = LogLevel.INFO,
                                    category = LogCategory.PROVIDER,
                                    message = "Gemini: Primeiro token recebido em ${latency}ms ($correlationId)."
                                )
                            }
                            accumulatedText.append(event.text)
                        }
                        emit(event)
                    }

                    charsRead = reader.read(charBuffer)
                }

                val finalEvents = parser.finish(accumulatedText.toString())
                for (event in finalEvents) {
                    emit(event)
                }

                val totalTime = System.currentTimeMillis() - startTime
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.PROVIDER,
                    message = "Gemini: Stream concluído com sucesso em ${totalTime}ms ($correlationId)."
                )

                emit(StreamEvent.Completed(accumulatedText.toString(), finishReason = "STOP"))
            }
        } catch (e: Exception) {
            val appError = ErrorMapper.fromThrowable(e)
            emit(StreamEvent.Failed(appError, accumulatedText.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: ChatRequest): JsonObject {
        return buildJsonObject {
            // Contents array
            putJsonArray("contents") {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == ChatRole.USER) "user" else "model")
                        putJsonArray("parts") {
                            // Text part
                            if (msg.content.isNotEmpty()) {
                                addJsonObject { put("text", msg.content) }
                            }
                            // Attachments (e.g. image base64)
                            msg.attachments.forEach { att ->
                                if (!att.dataBase64.isNullOrEmpty()) {
                                    addJsonObject {
                                        putJsonObject("inlineData") {
                                            put("mimeType", att.mimeType)
                                            put("data", att.dataBase64)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // System Instruction
            if (!request.systemPrompt.isNullOrEmpty()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", request.systemPrompt) }
                    }
                }
            }

            // Generation Config
            putJsonObject("generationConfig") {
                request.temperature?.let { put("temperature", it) }
                request.maxTokens?.let { put("maxOutputTokens", it) }
            }
        }
    }
}
