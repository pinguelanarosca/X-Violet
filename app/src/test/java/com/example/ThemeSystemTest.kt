package com.example

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.settings.SettingsRepository
import com.example.storage.dao.AppSettingDao
import com.example.storage.entities.AppSettingEntity
import com.example.ui.customize.CustomizeScreen
import com.example.ui.theme.*
import com.example.ui.theme.steampunk.SteampunkEngineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testThemeTypeEnumerationAndResources() {
        val themes = ThemeType.values()
        assertEquals("Devem existir exatamente 5 temas", 5, themes.size)

        val expected = listOf(
            ThemeType.STEAMPUNK,
            ThemeType.MIDNIGHT,
            ThemeType.NEON,
            ThemeType.NATURE,
            ThemeType.MINIMAL
        )
        assertEquals(expected, themes.toList())

        themes.forEach { theme ->
            assertTrue("Título em string resource deve ser válido", theme.getTitleRes() != 0)
            assertTrue("Descrição em string resource deve ser válida", theme.getDescriptionRes() != 0)
            val colors = theme.getPreviewColors()
            assertEquals("Cada tema deve fornecer 4 amostras de cores", 4, colors.size)
        }
    }

    @Test
    fun testThemeResolutionCompleteness() {
        ThemeType.values().forEach { type ->
            val def = resolveThemeDefinition(type)
            assertEquals("O tipo resolvido deve coincidir com o solicitado", type, def.type)
            assertNotNull("ColorScheme deve estar preenchido", def.colorScheme)
            assertNotNull("Tipografia deve estar presente", def.typography)
            assertNotNull("Formas (Shapes) devem estar presentes", def.shapes)
            assertNotNull("Estilo visual deve estar presente", def.visualStyle)
            assertNotNull("Visualizador do Live deve estar presente", def.liveVisualizerContent)
            assertNotNull("Mini-visualizador do Live deve estar presente", def.liveMiniVisualizerContent)
        }
    }

    @Test
    fun testSteampunkThemeSpecificIdentity() {
        val steampunk = resolveThemeDefinition(ThemeType.STEAMPUNK)

        // Verificações semânticas da identidade Steampunk
        assertEquals(SteampunkBrassPrimary, steampunk.colorScheme.primary)
        assertEquals(SteampunkBackground, steampunk.colorScheme.background)
        assertEquals(SteampunkSurface, steampunk.colorScheme.surface)
        assertEquals(SteampunkError, steampunk.colorScheme.error)

        assertTrue("Steampunk deve conter rebites industriais", steampunk.visualStyle.hasRivets)
        assertTrue("Steampunk deve ter acabamento metálico", steampunk.visualStyle.hasMetallicSheen)
        assertEquals(SteampunkBrassPrimary, steampunk.visualStyle.brassAccent)
        assertEquals(SteampunkBronzeSecondary, steampunk.visualStyle.bronzeAccent)
        assertEquals(SteampunkCopperTertiary, steampunk.visualStyle.copperAccent)
        assertEquals(SteampunkFurnaceAmber, steampunk.visualStyle.furnaceGlow)
        assertEquals(SteampunkGaugeNeedle, steampunk.visualStyle.gaugeNeedle)
        assertEquals(SteampunkGreenPatina, steampunk.visualStyle.liveStateColors.listening)
    }

    @Test
    fun testThemePersistenceViaSettingsRepository() = runTest {
        val fakeDao = FakeAppSettingDao()
        val repository = SettingsRepository(fakeDao)

        // 1. Padrão inicial
        val initialTheme = repository.getSetting("app_theme", ThemeType.STEAMPUNK.name)
        assertEquals(ThemeType.STEAMPUNK.name, initialTheme)

        // 2. Salva e recupera novo tema (NEON)
        repository.setSetting("app_theme", ThemeType.NEON.name)
        val savedNeon = repository.getSetting("app_theme", ThemeType.STEAMPUNK.name)
        assertEquals(ThemeType.NEON.name, savedNeon)
        val resolvedNeon = ThemeType.valueOf(savedNeon)
        assertEquals(ThemeType.NEON, resolvedNeon)

        // 3. Salva e recupera STEAMPUNK
        repository.setSetting("app_theme", ThemeType.STEAMPUNK.name)
        val savedSteampunk = repository.getSetting("app_theme", ThemeType.MIDNIGHT.name)
        assertEquals(ThemeType.STEAMPUNK.name, savedSteampunk)

        // 4. Fallback seguro contra valores desconhecidos
        val invalidTheme = "TEMA_INEXISTENTE"
        val resolvedFallback = runCatching { ThemeType.valueOf(invalidTheme) }.getOrDefault(ThemeType.STEAMPUNK)
        assertEquals(ThemeType.STEAMPUNK, resolvedFallback)
    }

    @Test
    fun testMyApplicationThemeProvidesLocals() {
        var observedTheme: ThemeType? = null
        var observedDef: AppThemeDefinition? = null

        composeTestRule.setContent {
            MyApplicationTheme(themeType = ThemeType.STEAMPUNK) {
                observedTheme = LocalTheme.current
                observedDef = LocalAppTheme.current
                Text("Steampunk ativo")
            }
        }

        assertEquals(ThemeType.STEAMPUNK, observedTheme)
        assertEquals(ThemeType.STEAMPUNK, observedDef?.type)
        assertEquals(SteampunkBrassPrimary, observedDef?.colorScheme?.primary)
    }

    @Test
    fun testMyApplicationThemeDynamicSwitch() {
        var activeTheme by mutableStateOf(ThemeType.STEAMPUNK)

        composeTestRule.setContent {
            MyApplicationTheme(themeType = activeTheme) {
                Text(
                    text = "Tema: ${LocalTheme.current.name}",
                    modifier = Modifier.testTag("current_theme_label")
                )
            }
        }

        composeTestRule.onNodeWithTag("current_theme_label").assertTextEquals("Tema: STEAMPUNK")

        // Troca dinâmica para MIDNIGHT
        activeTheme = ThemeType.MIDNIGHT
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("current_theme_label").assertTextEquals("Tema: MIDNIGHT")
    }

    @Test
    fun testCustomizeScreenThemeSelectorSelection() {
        var selectedTheme = ThemeType.STEAMPUNK

        composeTestRule.setContent {
            MyApplicationTheme(themeType = selectedTheme) {
                CustomizeScreen(
                    currentTheme = selectedTheme,
                    onThemeChange = { selectedTheme = it },
                    onNavigateToProviders = {},
                    onNavigateToHowSpeaks = {},
                    onNavigateToHowThinks = {},
                    onNavigateToWhatRemembers = {},
                    onNavigateToWhatCanDo = {},
                    onNavigateToHowConverses = {}
                )
            }
        }

        // Verifica container do seletor
        composeTestRule.onNodeWithTag("card_theme_selector_container").assertExists()

        // Verifica que todos os cards de temas existem
        ThemeType.values().forEach { theme ->
            composeTestRule.onNodeWithTag("theme_card_${theme.name.lowercase()}").assertExists()
        }

        // Clica no tema NEON
        composeTestRule.onNodeWithTag("theme_card_neon").performClick()
        assertEquals("Clicar no card deve disparar onThemeChange com NEON", ThemeType.NEON, selectedTheme)

        // Clica no tema STEAMPUNK
        composeTestRule.onNodeWithTag("theme_card_steampunk").performClick()
        assertEquals("Clicar no card deve restaurar STEAMPUNK", ThemeType.STEAMPUNK, selectedTheme)
    }

    @Test
    fun testSteampunkEngineStatesEnum() {
        val states = SteampunkEngineState.values()
        assertTrue(states.contains(SteampunkEngineState.DISCONNECTED))
        assertTrue(states.contains(SteampunkEngineState.LISTENING_IDLE))
        assertTrue(states.contains(SteampunkEngineState.USER_SPEAKING))
        assertTrue(states.contains(SteampunkEngineState.THINKING))
        assertTrue(states.contains(SteampunkEngineState.MODEL_SPEAKING))
    }

    private class FakeAppSettingDao : AppSettingDao {
        private val settings = mutableMapOf<String, String>()

        override suspend fun getSetting(key: String): String? {
            return settings[key]
        }

        override suspend fun setSetting(setting: AppSettingEntity) {
            settings[setting.key] = setting.value
        }

        override fun getAllSettingsFlow(): Flow<List<AppSettingEntity>> {
            val list = settings.map { AppSettingEntity(it.key, it.value, System.currentTimeMillis()) }
            return flowOf(list)
        }
    }
}
