package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.providers.adapters.*
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.HubDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OtherProvidersAdaptersTest {

    private lateinit var db: HubDatabase
    private lateinit var blobDir: File
    private lateinit var vault: KeystoreVault

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        blobDir = File(context.filesDir, "test_adapters_blobs").apply { mkdirs() }
        vault = KeystoreVault(
            vaultDao = db.vaultDao(),
            blobStorage = VaultBlobStorage(blobDir),
            cryptoEngine = TestCryptoEngine()
        )
    }

    @After
    fun tearDown() {
        db.close()
        blobDir.deleteRecursively()
    }

    @Test
    fun testOpenAiAdapterCapabilitiesAndCredentialValidation() = runBlocking {
        val adapter = OpenAiProviderAdapter(vault, db.aiModelDao())
        assertEquals("openai", adapter.id)
        assertEquals("OpenAI", adapter.displayName)
        assertTrue(adapter.capabilities.supportsImageInput)
        assertTrue(adapter.capabilities.supportsAudioInput)
        assertTrue(adapter.capabilities.supportsToolCalling)

        // Without key
        val authFail = adapter.validateCredentials()
        assertTrue(authFail.isFailure)

        // Save key
        vault.saveKey("openai", "sk-proj-test1234567890abcdef")
        val authSuccess = adapter.validateCredentials()
        assertTrue(authSuccess.isSuccess)
    }

    @Test
    fun testAnthropicAdapterCapabilitiesAndModels() = runBlocking {
        val adapter = AnthropicProviderAdapter(vault, db.aiModelDao())
        assertEquals("anthropic", adapter.id)
        assertTrue(adapter.capabilities.supportsText)
        assertTrue(adapter.capabilities.supportsReasoningEffort)

        val models = adapter.listModels("test_corr").getOrThrow()
        assertTrue(models.any { it.id.contains("claude-3-7-sonnet") })
        assertTrue(models.any { it.id.contains("claude-3-5-haiku") })
    }

    @Test
    fun testXAiAndOpenRouterAdaptersContract() = runBlocking {
        val xai = XAiProviderAdapter(vault, db.aiModelDao())
        assertEquals("xai", xai.id)
        val xaiModels = xai.listModels("test_corr").getOrThrow()
        assertTrue(xaiModels.any { it.id.contains("grok") })

        val openRouter = OpenRouterProviderAdapter(vault, db.aiModelDao())
        assertEquals("openrouter", openRouter.id)
        val openRouterModels = openRouter.listModels("test_corr").getOrThrow()
        assertTrue(openRouterModels.isNotEmpty())
    }

    @Test
    fun testCustomOpenAiAdapterUrlPersistenceAndSecurity() = runBlocking {
        val adapter = CustomOpenAiProviderAdapter(vault, db.appSettingDao(), db.aiModelDao())
        assertEquals("custom_openai", adapter.id)

        // Default URL is localhost
        val defaultUrl = adapter.getSavedCustomUrl()
        assertEquals("http://localhost:11434/v1", defaultUrl)

        // Save new custom URL
        adapter.saveCustomUrl("http://192.168.1.100:11434/v1")
        val savedUrl = adapter.getSavedCustomUrl()
        assertEquals("http://192.168.1.100:11434/v1", savedUrl)
    }
}
