package com.example.providers.adapters

import com.example.errors.AppError
import com.example.providers.contracts.AIProvider
import com.example.providers.contracts.LiveProvider
import com.example.providers.contracts.ModelProvider
import com.example.providers.contracts.ToolProvider
import com.example.providers.gemini.GeminiApiClient
import com.example.providers.gemini.GeminiConfigurationTestResult
import com.example.providers.model.*
import com.example.security.KeystoreVault
import com.example.storage.dao.AiModelDao
import com.example.storage.entities.AiModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GeminiProviderAdapter(
    private val keystoreVault: KeystoreVault,
    private val aiModelDao: AiModelDao? = null,
    private val apiClient: GeminiApiClient = GeminiApiClient()
) : AIProvider, ModelProvider, LiveProvider, ToolProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val defaultBaseUrl: String = "https://generativelanguage.googleapis.com"
    override val defaultModelId: String = "gemini-2.5-flash"

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportsVideoInput = true,
        supportsLiveStreaming = true,
        supportsToolCalling = true,
        supportsReasoningEffort = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 1048576,
        maxOutputTokens = 8192,
        returnsTokenUsage = true
    )

    override suspend fun validateCredentials(): Result<Unit> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isFailure) {
            return Result.failure(
                AppError.Auth(
                    code = "ERR_GEMINI_NO_KEY",
                    userMessage = "Nenhuma chave de API configurada para o Google Gemini.",
                    technicalCause = "Keystore não contém chave ativa para '$id'"
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isFailure) {
            return Result.failure(
                AppError.Auth(
                    code = "ERR_GEMINI_NO_KEY",
                    userMessage = "Nenhuma chave configurada no cofre para testar.",
                    technicalCause = "Key missing in vault"
                )
            )
        }
        return apiClient.testConfiguration(keyResult.getOrThrow(), correlationId)
    }

    override fun isConfiguredFlow(): Flow<Boolean> {
        return keystoreVault.allCredentialsFlow.map { list ->
            list.any { it.providerId == id && it.status == KeystoreVault.STATUS_ACTIVE }
        }
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val apiResult = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (apiResult.isSuccess) {
                val models = apiResult.getOrThrow()
                // Cache into Room
                aiModelDao?.let { dao ->
                    try {
                        val entities = models.map { m ->
                            AiModelEntity(
                                id = m.id,
                                providerId = id,
                                modelName = m.id,
                                displayName = m.displayName,
                                capabilitiesJson = "[generateContent]",
                                isDiscovered = true
                            )
                        }
                        dao.clearForProvider(id)
                        dao.insertBatch(entities)
                    } catch (e: Exception) {
                        // DB caching error ignored gracefully
                    }
                }
                return@withContext Result.success(models)
            }
        }

        // Fallback to cached models in Room if available
        aiModelDao?.let { dao ->
            val cached = dao.getModelsForProviderList(id)
            if (cached.isNotEmpty()) {
                val models = cached.map {
                    ModelInfo(
                        id = it.id,
                        displayName = "${it.displayName} (cache)",
                        providerId = id,
                        contextWindowTokens = 1048576,
                        maxOutputTokens = 8192,
                        supportsVision = true,
                        supportsAudio = true,
                        supportsThinking = true
                    )
                }
                return@withContext Result.success(models)
            }
        }

        // Default known models if no cache yet
        Result.success(
            listOf(
                ModelInfo(
                    id = "gemini-2.5-flash",
                    displayName = "Gemini 2.5 Flash (Padrão)",
                    providerId = id,
                    contextWindowTokens = 1048576,
                    maxOutputTokens = 8192,
                    supportsVision = true,
                    supportsAudio = true,
                    supportsThinking = true,
                    description = "Modelo rápido multimodal de última geração da Google."
                ),
                ModelInfo(
                    id = "gemini-2.5-pro",
                    displayName = "Gemini 2.5 Pro",
                    providerId = id,
                    contextWindowTokens = 2097152,
                    maxOutputTokens = 8192,
                    supportsVision = true,
                    supportsAudio = true,
                    supportsThinking = true,
                    description = "Modelo de alto raciocínio e grande contexto."
                )
            )
        )
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isFailure) {
            return Result.failure(keyResult.exceptionOrNull() as? AppError ?: AppError.Auth(technicalCause = "Sem chave no cofre"))
        }
        return apiClient.generateContent(keyResult.getOrThrow(), request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isFailure) {
            emit(StreamEvent.Failed(keyResult.exceptionOrNull() as? AppError ?: AppError.Auth(technicalCause = "Sem chave no cofre"), null))
            return@flow
        }
        apiClient.streamGenerateContent(keyResult.getOrThrow(), request, correlationId).collect {
            emit(it)
        }
    }

    override suspend fun connectLive(sessionConfig: LiveSessionConfig, correlationId: String): Result<LiveSession> {
        return Result.failure(
            AppError.ProviderUnavailable(
                code = "ERR_LIVE_NOT_IMPLEMENTED",
                userMessage = "Conexão Live multimodal bidirecional prevista para o Prompt 2 (Mídia).",
                technicalCause = "WebSocket LiveSession"
            )
        )
    }

    override fun availableTools(): List<ToolDefinition> = emptyList()

    override suspend fun executeTool(call: ToolCall, correlationId: String): Result<ToolResult> {
        return Result.failure(
            AppError.UnsupportedParameter(
                parameterName = call.toolName,
                userMessage = "Execução de ferramentas prevista para o Prompt 3.",
                technicalCause = "ToolProvider stub"
            )
        )
    }
}
