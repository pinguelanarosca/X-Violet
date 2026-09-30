package com.example.providers

import com.example.providers.adapters.*
import com.example.providers.contracts.AIProvider
import com.example.security.KeystoreVault
import com.example.storage.dao.AiModelDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class ProviderRegistry(
    private val keystoreVault: KeystoreVault,
    private val aiModelDao: AiModelDao? = null,
    private val appSettingDao: com.example.storage.dao.AppSettingDao? = null
) {
    private val providersMap = ConcurrentHashMap<String, AIProvider>()
    private val _activeProviderId = MutableStateFlow("gemini")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _activeModelId = MutableStateFlow("gemini-2.5-flash")
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    init {
        // Register default built-in adapters
        registerProvider(GeminiProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(OpenAiProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(AnthropicProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(GroqProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(OpenRouterProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(XAiProviderAdapter(keystoreVault, aiModelDao))
        registerProvider(CustomOpenAiProviderAdapter(keystoreVault, appSettingDao, aiModelDao))
    }

    /**
     * Registers a new AIProvider adapter dynamically without modifying the rest of the application.
     */
    fun registerProvider(provider: AIProvider) {
        providersMap[provider.id] = provider
    }

    fun getProvider(id: String): AIProvider? {
        return providersMap[id]
    }

    fun getAllProviders(): List<AIProvider> {
        return providersMap.values.toList()
    }

    fun getActiveProvider(): AIProvider {
        return providersMap[_activeProviderId.value]
            ?: providersMap["gemini"]
            ?: throw IllegalStateException("Nenhum provedor padrão registrado no ProviderRegistry")
    }

    fun setActiveProviderId(id: String) {
        if (providersMap.containsKey(id)) {
            _activeProviderId.value = id
            val provider = providersMap[id]
            if (provider is com.example.providers.contracts.ModelProvider) {
                _activeModelId.value = provider.defaultModelId
            }
        }
    }

    fun setActiveModelId(modelId: String) {
        _activeModelId.value = modelId
    }

    suspend fun isKeyConfigured(providerId: String): Boolean {
        return keystoreVault.getDecryptedKey(providerId).isSuccess
    }
}
