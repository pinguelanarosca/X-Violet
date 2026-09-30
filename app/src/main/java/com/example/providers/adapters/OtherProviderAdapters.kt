package com.example.providers.adapters

import com.example.errors.AppError
import com.example.providers.anthropic.AnthropicApiClient
import com.example.providers.contracts.AIProvider
import com.example.providers.contracts.ModelProvider
import com.example.providers.contracts.ToolProvider
import com.example.providers.custom.CustomOpenAiApiClient
import com.example.providers.gemini.GeminiConfigurationTestResult
import com.example.providers.model.*
import com.example.providers.openai.OpenAiApiClient
import com.example.security.KeystoreVault
import com.example.storage.dao.AiModelDao
import com.example.storage.dao.AppSettingDao
import com.example.storage.entities.AiModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

abstract class BaseHttpProviderAdapter(
    override val id: String,
    override val displayName: String,
    override val defaultBaseUrl: String,
    override val defaultModelId: String,
    protected val keystoreVault: KeystoreVault,
    protected val aiModelDao: AiModelDao? = null
) : AIProvider, ModelProvider, ToolProvider {

    override suspend fun validateCredentials(): Result<Unit> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        return if (keyResult.isSuccess && keyResult.getOrThrow().isNotBlank()) {
            Result.success(Unit)
        } else {
            Result.failure(
                AppError.Auth(
                    code = "ERR_${id.uppercase()}_NO_KEY",
                    userMessage = "Nenhuma chave de API configurada para $displayName.",
                    technicalCause = "Keystore não contém chave válida para '$id'"
                )
            )
        }
    }

    override fun isConfiguredFlow(): Flow<Boolean> {
        return keystoreVault.allCredentialsFlow.map { list ->
            list.any { it.providerId == id && it.status == KeystoreVault.STATUS_ACTIVE }
        }
    }

    override fun availableTools(): List<ToolDefinition> = emptyList()

    override suspend fun executeTool(call: ToolCall, correlationId: String): Result<ToolResult> {
        return Result.failure(
            AppError.UnsupportedParameter(
                parameterName = call.toolName,
                userMessage = "Execução de ferramentas prevista para o Prompt 3 (Tools).",
                technicalCause = "ToolProvider stub"
            )
        )
    }

    protected suspend fun cacheDiscoveredModels(models: List<ModelInfo>) = withContext(Dispatchers.IO) {
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
                // Ignore caching errors
            }
        }
    }
}

// -------------------------------------------------------------
// 1. OPENAI
// -------------------------------------------------------------
class OpenAiProviderAdapter(
    keystoreVault: KeystoreVault,
    aiModelDao: AiModelDao? = null,
    val apiClient: OpenAiApiClient = OpenAiApiClient()
) : BaseHttpProviderAdapter(
    id = "openai",
    displayName = "OpenAI",
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModelId = "gpt-4o-mini",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsAudioInput = true,
        supportsAudioOutput = true,
        supportsToolCalling = true,
        supportsReasoningEffort = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 1.0f,
        maxContextTokens = 128000,
        maxOutputTokens = 16384,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.testConfiguration(key, correlationId)
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val res = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (res.isSuccess) {
                val list = res.getOrThrow()
                cacheDiscoveredModels(list)
                return Result.success(list)
            }
        }
        return Result.success(
            listOf(
                ModelInfo("gpt-4o", "GPT-4o", id, 128000, 16384, supportsVision = true),
                ModelInfo("gpt-4o-mini", "GPT-4o Mini", id, 128000, 16384, supportsVision = true),
                ModelInfo("o1", "o1 (Reasoning)", id, 200000, 100000, supportsThinking = true),
                ModelInfo("o3-mini", "o3-mini (Reasoning)", id, 200000, 100000, supportsThinking = true)
            )
        )
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrElse {
            emit(StreamEvent.Failed(it as? AppError ?: AppError.Auth(technicalCause = "Sem chave"), null))
            return@flow
        }
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}

// -------------------------------------------------------------
// 2. ANTHROPIC (CLAUDE)
// -------------------------------------------------------------
class AnthropicProviderAdapter(
    keystoreVault: KeystoreVault,
    aiModelDao: AiModelDao? = null,
    val apiClient: AnthropicApiClient = AnthropicApiClient()
) : BaseHttpProviderAdapter(
    id = "anthropic",
    displayName = "Anthropic (Claude)",
    defaultBaseUrl = "https://api.anthropic.com/v1",
    defaultModelId = "claude-3-7-sonnet-latest",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsToolCalling = true,
        supportsReasoningEffort = true,
        temperatureRange = 0.0f..1.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 200000,
        maxOutputTokens = 8192,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.testConfiguration(key, correlationId)
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val res = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (res.isSuccess) {
                val list = res.getOrThrow()
                cacheDiscoveredModels(list)
                return Result.success(list)
            }
        }
        return apiClient.listModels("", correlationId)
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrElse {
            emit(StreamEvent.Failed(it as? AppError ?: AppError.Auth(technicalCause = "Sem chave"), null))
            return@flow
        }
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}

// -------------------------------------------------------------
// 3. xAI (GROK)
// -------------------------------------------------------------
class XAiProviderAdapter(
    keystoreVault: KeystoreVault,
    aiModelDao: AiModelDao? = null,
    val apiClient: CustomOpenAiApiClient = CustomOpenAiApiClient("xai", "xAI (Grok)", { "https://api.x.ai/v1" })
) : BaseHttpProviderAdapter(
    id = "xai",
    displayName = "xAI (Grok)",
    defaultBaseUrl = "https://api.x.ai/v1",
    defaultModelId = "grok-2-latest",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsToolCalling = true,
        supportsReasoningEffort = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 131072,
        maxOutputTokens = 8192,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.testConfiguration(key, correlationId, defaultModelId)
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val res = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (res.isSuccess) {
                val list = res.getOrThrow()
                cacheDiscoveredModels(list)
                return Result.success(list)
            }
        }
        return Result.success(
            listOf(
                ModelInfo("grok-2-latest", "Grok 2", id, 131072, 8192, supportsVision = true),
                ModelInfo("grok-2-vision-latest", "Grok 2 Vision", id, 131072, 8192, supportsVision = true),
                ModelInfo("grok-beta", "Grok Beta", id, 131072, 8192)
            )
        )
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrElse {
            emit(StreamEvent.Failed(it as? AppError ?: AppError.Auth(technicalCause = "Sem chave"), null))
            return@flow
        }
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}

// -------------------------------------------------------------
// 4. OPENROUTER
// -------------------------------------------------------------
class OpenRouterProviderAdapter(
    keystoreVault: KeystoreVault,
    aiModelDao: AiModelDao? = null,
    val apiClient: CustomOpenAiApiClient = CustomOpenAiApiClient("openrouter", "OpenRouter", { "https://openrouter.ai/api/v1" })
) : BaseHttpProviderAdapter(
    id = "openrouter",
    displayName = "OpenRouter",
    defaultBaseUrl = "https://openrouter.ai/api/v1",
    defaultModelId = "meta-llama/llama-3.3-70b-instruct",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsToolCalling = true,
        supportsReasoningEffort = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 128000,
        maxOutputTokens = 16384,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.testConfiguration(key, correlationId, defaultModelId)
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val res = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (res.isSuccess) {
                val list = res.getOrThrow()
                cacheDiscoveredModels(list)
                return Result.success(list)
            }
        }
        return Result.success(
            listOf(
                ModelInfo("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B Instruct", id, 128000, 8192),
                ModelInfo("anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet (via OpenRouter)", id, 200000, 8192, supportsVision = true, supportsThinking = true),
                ModelInfo("deepseek/deepseek-r1", "DeepSeek R1 (via OpenRouter)", id, 128000, 8192, supportsThinking = true),
                ModelInfo("openai/gpt-4o", "GPT-4o (via OpenRouter)", id, 128000, 16384, supportsVision = true)
            )
        )
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrElse {
            emit(StreamEvent.Failed(it as? AppError ?: AppError.Auth(technicalCause = "Sem chave"), null))
            return@flow
        }
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}

// -------------------------------------------------------------
// 5. GROQ
// -------------------------------------------------------------
class GroqProviderAdapter(
    keystoreVault: KeystoreVault,
    aiModelDao: AiModelDao? = null,
    val apiClient: CustomOpenAiApiClient = CustomOpenAiApiClient("groq", "Groq", { "https://api.groq.com/openai/v1" })
) : BaseHttpProviderAdapter(
    id = "groq",
    displayName = "Groq",
    defaultBaseUrl = "https://api.groq.com/openai/v1",
    defaultModelId = "llama-3.3-70b-versatile",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsToolCalling = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 128000,
        maxOutputTokens = 32768,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.testConfiguration(key, correlationId, defaultModelId)
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val keyResult = keystoreVault.getDecryptedKey(id)
        if (keyResult.isSuccess) {
            val res = apiClient.listModels(keyResult.getOrThrow(), correlationId)
            if (res.isSuccess) {
                val list = res.getOrThrow()
                cacheDiscoveredModels(list)
                return Result.success(list)
            }
        }
        return Result.success(
            listOf(
                ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", id, 128000, 32768),
                ModelInfo("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill Llama 70B", id, 128000, 8192, supportsThinking = true)
            )
        )
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrElse { return Result.failure(it) }
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrElse {
            emit(StreamEvent.Failed(it as? AppError ?: AppError.Auth(technicalCause = "Sem chave"), null))
            return@flow
        }
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}

// -------------------------------------------------------------
// 6. COMPATÍVEL COM OPENAI (CUSTOM / LOCAL / OLLAMA / VLLM)
// -------------------------------------------------------------
class CustomOpenAiProviderAdapter(
    keystoreVault: KeystoreVault,
    private val appSettingDao: AppSettingDao? = null,
    aiModelDao: AiModelDao? = null
) : BaseHttpProviderAdapter(
    id = "custom_openai",
    displayName = "Compatível com OpenAI (Local / Custom)",
    defaultBaseUrl = "http://localhost:11434/v1",
    defaultModelId = "default-model",
    keystoreVault = keystoreVault,
    aiModelDao = aiModelDao
) {
    val apiClient: CustomOpenAiApiClient = CustomOpenAiApiClient(
        providerId = "custom_openai",
        providerTag = "Compatível com OpenAI",
        baseUrlSupplier = { getSavedCustomUrl() }
    )

    suspend fun getSavedCustomUrl(): String {
        return appSettingDao?.getSetting("custom_openai_base_url") ?: defaultBaseUrl
    }

    suspend fun saveCustomUrl(url: String) {
        appSettingDao?.setSetting(com.example.storage.entities.AppSettingEntity("custom_openai_base_url", url.trim()))
    }

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsText = true,
        supportsImageInput = true,
        supportsToolCalling = true,
        temperatureRange = 0.0f..2.0f,
        defaultTemperature = 0.7f,
        maxContextTokens = 128000,
        maxOutputTokens = 8192,
        returnsTokenUsage = true
    )

    suspend fun testConfiguration(correlationId: String): Result<GeminiConfigurationTestResult> {
        val key = keystoreVault.getDecryptedKey(id).getOrDefault("")
        val testRes = apiClient.testConfiguration(key, correlationId, defaultModelId)
        return testRes
    }

    override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
        val key = keystoreVault.getDecryptedKey(id).getOrDefault("")
        val res = apiClient.listModels(key, correlationId)
        if (res.isSuccess) {
            val list = res.getOrThrow()
            cacheDiscoveredModels(list)
            return Result.success(list)
        }
        return Result.success(listOf(ModelInfo("default-model", "Modelo do Servidor Local (Não verificado)", id, 128000, 8192)))
    }

    override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
        val key = keystoreVault.getDecryptedKey(id).getOrDefault("")
        return apiClient.generateContent(key, request, correlationId)
    }

    override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flow {
        val key = keystoreVault.getDecryptedKey(id).getOrDefault("")
        apiClient.streamGenerateContent(key, request, correlationId).collect { emit(it) }
    }
}
