package com.example

import com.example.errors.AppError
import com.example.providers.ProviderRegistry
import com.example.providers.contracts.AIProvider
import com.example.providers.contracts.ModelProvider
import com.example.providers.model.*
import com.example.security.AndroidKeyStoreCryptoEngine
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.HubDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProviderContractsTest {

    private lateinit var database: HubDatabase
    private lateinit var blobStorage: VaultBlobStorage
    private lateinit var vault: KeystoreVault
    private lateinit var registry: ProviderRegistry

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val testBlobDir = File(context.filesDir, "vault_blobs_provider_test").apply { mkdirs() }
        blobStorage = VaultBlobStorage(testBlobDir)
        vault = KeystoreVault(
            vaultDao = database.vaultDao(),
            blobStorage = blobStorage,
            cryptoEngine = TestCryptoEngine()
        )
        registry = ProviderRegistry(vault)
    }

    @Test
    fun testProviderRegistryResolvesDefaultProviders() {
        val gemini = registry.getProvider("gemini")
        assertNotNull("Google Gemini deve estar registrado por padrão", gemini)
        assertEquals("gemini", gemini?.id)
        assertEquals("Google Gemini", gemini?.displayName)

        val openai = registry.getProvider("openai")
        assertNotNull("OpenAI deve estar registrado por padrão", openai)

        val anthropic = registry.getProvider("anthropic")
        assertNotNull("Anthropic deve estar registrado por padrão", anthropic)

        val groq = registry.getProvider("groq")
        assertNotNull("Groq deve estar registrado por padrão", groq)

        val openrouter = registry.getProvider("openrouter")
        assertNotNull("OpenRouter deve estar registrado por padrão", openrouter)

        val xai = registry.getProvider("xai")
        assertNotNull("xAI deve estar registrado por padrão", xai)

        val customOpenAi = registry.getProvider("custom_openai")
        assertNotNull("Custom OpenAI deve estar registrado por padrão", customOpenAi)

        assertEquals(7, registry.getAllProviders().size)
    }

    @Test
    fun testDynamicRegistrationOfFutureProviderWithoutTouchingExistingCode() {
        // Register a custom/future provider dynamically
        val mockCustomProvider = object : AIProvider, ModelProvider {
            override val id: String = "local_ollama"
            override val displayName: String = "Local Ollama"
            override val defaultBaseUrl: String = "http://localhost:11434"
            override val defaultModelId: String = "llama3:latest"
            override val capabilities: ProviderCapabilities = ProviderCapabilities(
                supportsText = true,
                supportsToolCalling = true,
                maxContextTokens = 32768
            )

            override suspend fun validateCredentials(): Result<Unit> = Result.success(Unit)
            override fun isConfiguredFlow(): Flow<Boolean> = flowOf(true)

            override suspend fun generateText(request: ChatRequest, correlationId: String): Result<ChatResponse> {
                return Result.success(ChatResponse(text = "Resposta do Ollama local", finishReason = "stop"))
            }

            override fun streamText(request: ChatRequest, correlationId: String): Flow<StreamEvent> = flowOf(
                StreamEvent.Started(correlationId),
                StreamEvent.TextDelta("Resposta do Ollama local"),
                StreamEvent.Completed("Resposta do Ollama local", "stop")
            )

            override suspend fun listModels(correlationId: String): Result<List<ModelInfo>> {
                return Result.success(listOf(ModelInfo("llama3:latest", "Llama 3 Local", id, 32768, 4096)))
            }
        }

        registry.registerProvider(mockCustomProvider)

        val retrieved = registry.getProvider("local_ollama")
        assertNotNull("Novo provedor registrado dinamicamente deve ser recuperável", retrieved)
        assertEquals("Local Ollama", retrieved?.displayName)
        assertTrue(retrieved?.capabilities?.supportsToolCalling == true)
        assertEquals(8, registry.getAllProviders().size)
    }

    @Test
    fun testProviderCapabilitiesParametersAndRanges() {
        val gemini = registry.getProvider("gemini")!!
        assertTrue(gemini.capabilities.supportsText)
        assertTrue(gemini.capabilities.supportsImageInput)
        assertTrue(gemini.capabilities.supportsAudioInput)
        assertTrue(gemini.capabilities.supportsAudioOutput)
        assertTrue(gemini.capabilities.supportsLiveStreaming)
        assertTrue(gemini.capabilities.supportsToolCalling)
        assertTrue(gemini.capabilities.supportsReasoningEffort)
        assertEquals(0.0f..2.0f, gemini.capabilities.temperatureRange)
        assertTrue(gemini.capabilities.maxContextTokens >= 1000000)
    }

    @Test
    fun testProviderValidationReturnsAppErrorAuthWhenNoKeyConfigured() = runBlocking {
        val openai = registry.getProvider("openai")!!
        val validationResult = openai.validateCredentials()
        assertFalse(validationResult.isSuccess)
        val error = validationResult.exceptionOrNull()
        assertTrue("Erro de validação deve ser AppError.Auth", error is AppError.Auth)
        assertEquals("ERR_OPENAI_NO_KEY", (error as AppError.Auth).code)
    }

    @Test
    fun testProviderValidationSucceedsWhenKeySavedInVault() = runBlocking {
        val gemini = registry.getProvider("gemini")!!
        vault.saveKey("gemini", "AIzaSyValidGeminiKey1234567890")

        val validationResult = gemini.validateCredentials()
        assertTrue(validationResult.isSuccess)
    }

    @Test
    fun testStreamEventHierarchy() {
        val events = listOf<StreamEvent>(
            StreamEvent.Started("corr_1"),
            StreamEvent.TextDelta("Olá, "),
            StreamEvent.TextDelta("Mundo!"),
            StreamEvent.ReasoningDelta("Pensando no cumprimento..."),
            StreamEvent.ToolCall("call_1", "buscar_clima", "{\"cidade\":\"SP\"}"),
            StreamEvent.Usage(10, 5, 15),
            StreamEvent.Completed("Olá, Mundo!", "stop"),
            StreamEvent.Failed(AppError.Network(technicalCause = "Desconectado"), "Olá, ")
        )
        assertEquals(8, events.size)
    }
}
