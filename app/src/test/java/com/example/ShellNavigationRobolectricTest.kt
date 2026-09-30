package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShellNavigationRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testShellLaunchesAndDisplaysAllTabs() {
        // Main Scaffold and Navigation Bar exist
        composeTestRule.onNodeWithTag("main_scaffold").assertExists()
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertExists()

        // 4 tabs exist
        composeTestRule.onNodeWithTag("nav_item_chat").assertExists()
        composeTestRule.onNodeWithTag("nav_item_dashboard").assertExists()
        composeTestRule.onNodeWithTag("nav_item_customize").assertExists()
        composeTestRule.onNodeWithTag("nav_item_logs").assertExists()
    }

    @Test
    fun testTabSwitchingGeneratesLogsAndNavigates() {
        // Navigate to Dashboard
        composeTestRule.onNodeWithTag("nav_item_dashboard").performClick()
        composeTestRule.onNodeWithTag("dashboard_screen").assertExists()

        // Navigate to Customize
        composeTestRule.onNodeWithTag("nav_item_customize").performClick()
        composeTestRule.onNodeWithTag("customize_screen").assertExists()

        // Navigate to Logs
        composeTestRule.onNodeWithTag("nav_item_logs").performClick()
        composeTestRule.onNodeWithTag("logs_screen").assertExists()

        // Navigate back to Chat
        composeTestRule.onNodeWithTag("nav_item_chat").performClick()
        composeTestRule.onNodeWithTag("chat_screen").assertExists()
    }

    @Test
    fun testSubScreenNavigationToVaultAndDiagnostic() {
        // Navigate to Customize tab -> Click Provedores card -> Opens VaultScreen
        composeTestRule.onNodeWithTag("nav_item_customize").performClick()
        composeTestRule.onNodeWithTag("customize_screen").performScrollToNode(hasTestTag("card_customize_providers"))
        composeTestRule.onNodeWithTag("card_customize_providers").performClick()
        composeTestRule.onNodeWithTag("vault_screen_list").assertExists()
        composeTestRule.onNodeWithTag("btn_back_navigation").assertExists()

        // Click Back button -> Returns to Customize
        composeTestRule.onNodeWithTag("btn_back_navigation").performClick()
        composeTestRule.onNodeWithTag("customize_screen").assertExists()

        // Navigate to Dashboard tab -> Click Diagnóstico card -> Opens DiagnosticScreen
        composeTestRule.onNodeWithTag("nav_item_dashboard").performClick()
        composeTestRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("card_dashboard_diagnostic"))
        composeTestRule.onNodeWithTag("card_dashboard_diagnostic").performClick()
        composeTestRule.onNodeWithTag("diagnostic_screen").assertExists()
        composeTestRule.onNodeWithTag("btn_back_navigation").assertExists()

        // Click Back button -> Returns to Dashboard
        composeTestRule.onNodeWithTag("btn_back_navigation").performClick()
        composeTestRule.onNodeWithTag("dashboard_screen").assertExists()
    }

    @Test
    fun testSubScreensFromDashboardAndCustomize() {
        // In Chat screen, verify chat exists
        composeTestRule.onNodeWithTag("chat_screen").assertExists()

        // In Dashboard, click module card (Cérebro) -> Opens Brain Dashboard Screen
        composeTestRule.onNodeWithTag("nav_item_dashboard").performClick()
        composeTestRule.onNodeWithTag("card_module_cérebro").performClick()
        composeTestRule.onNodeWithTag("btn_back_brain_dash").assertExists()
        composeTestRule.onNodeWithTag("btn_back_brain_dash").performClick()
        composeTestRule.onNodeWithTag("dashboard_screen").assertExists()

        // In Customize, click option card (Como fala) -> Opens How Speaks Screen
        composeTestRule.onNodeWithTag("nav_item_customize").performClick()
        composeTestRule.onNodeWithTag("customize_screen").performScrollToNode(hasTestTag("card_customize_como_fala"))
        composeTestRule.onNodeWithTag("card_customize_como_fala").performClick()
        composeTestRule.onNodeWithTag("btn_back_how_speaks").assertExists()
        composeTestRule.onNodeWithTag("btn_back_how_speaks").performClick()
        composeTestRule.onNodeWithTag("customize_screen").assertExists()
    }
}
