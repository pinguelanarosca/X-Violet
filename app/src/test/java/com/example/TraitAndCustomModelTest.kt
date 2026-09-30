package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.models.*
import com.example.providers.model.ProviderCapabilities
import com.example.storage.HubDatabase
import com.example.storage.entities.CustomModelEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TraitAndCustomModelTest {

    private lateinit var context: Context
    private lateinit var db: HubDatabase
    private lateinit var repository: CustomModelRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        repository = CustomModelRepository(db.customModelDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testTraitTranslatorClampingOpenAI() {
        val capabilities = ProviderCapabilities(
            temperatureRange = 0.0f..2.0f
        )

        val minConfig = TraitTranslator.compile(
            traits = TraitValues(criatividade = 0),
            reasoningValue = 0,
            userInstructions = "",
            providerId = "openai",
            baseModelId = "gpt-4o",
            capabilities = capabilities
        )

        val maxConfig = TraitTranslator.compile(
            traits = TraitValues(criatividade = 100),
            reasoningValue = 100,
            userInstructions = "",
            providerId = "openai",
            baseModelId = "gpt-4o",
            capabilities = capabilities
        )

        assertEquals("Temperatura mínima clamping OpenAI deve ser 0.0", 0.0f, minConfig.temperature, 0.01f)
        assertEquals("Temperatura máxima clamping OpenAI deve ser 2.0", 2.0f, maxConfig.temperature, 0.01f)
    }

    @Test
    fun testTraitTranslatorClampingAnthropic() {
        val capabilities = ProviderCapabilities(
            temperatureRange = 0.0f..1.0f
        )

        val maxConfig = TraitTranslator.compile(
            traits = TraitValues(criatividade = 100),
            reasoningValue = 50,
            userInstructions = "",
            providerId = "anthropic",
            baseModelId = "claude-3-5-sonnet",
            capabilities = capabilities
        )

        assertEquals("Temperatura máxima Anthropic não pode passar de 1.0", 1.0f, maxConfig.temperature, 0.01f)
    }

    @Test
    fun testReasoningLevelMapping() {
        assertEquals(ReasoningLevel.INSTANTANEO, ReasoningLevel.fromValue(10))
        assertEquals(ReasoningLevel.LEVE, ReasoningLevel.fromValue(30))
        assertEquals(ReasoningLevel.EQUILIBRADO, ReasoningLevel.fromValue(50))
        assertEquals(ReasoningLevel.PROFUNDO, ReasoningLevel.fromValue(70))
        assertEquals(ReasoningLevel.EXTREMO, ReasoningLevel.fromValue(90))
    }

    @Test
    fun testPrivateProfileDefinition() {
        val privateProfile = ProfilePresets.LIST.first { it.id == "profile_private" }
        assertFalse("Memória deve ser desativada no perfil Privado", privateProfile.isMemoryEnabled)
        assertFalse("Histórico não deve ser salvo no perfil Privado", privateProfile.isHistorySaved)
        assertNotNull("Perfil Privado deve conter mensagem de aviso", privateProfile.warningMessage)
    }

    @Test
    fun testSafeImportDisablesTools() = runBlocking {
        val exportJson = repository.exportModelToJson(
            CustomModelEntity(
                id = "test_export",
                name = "Modelo Teste",
                providerId = "gemini",
                baseModelId = "gemini-2.5-flash"
            )
        )

        val importResult = repository.importModelFromJson(exportJson)
        assertTrue("Importação deve ter sucesso", importResult.isSuccess)

        val imported = importResult.getOrThrow()
        assertTrue("Flags do modelo importado devem conter ferramentas desativadas por segurança", imported.featureFlagsJson.contains("\"tools\":false"))
    }
}
