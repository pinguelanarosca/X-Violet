package com.example

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.HubDatabase
import com.example.ui.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: HubDatabase
    private lateinit var blobDir: File
    private lateinit var vault: KeystoreVault

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        blobDir = File(context.filesDir, "compose_test_blobs").apply { mkdirs() }
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
    fun testVaultScreenRendersHeaderAndCardsVisible() {
        composeTestRule.setContent {
            MyApplicationTheme {
                VaultScreen(
                    vault = vault,
                    snackbarHostState = SnackbarHostState(),
                    protectKeyScreens = false
                )
            }
        }

        // Verify Vault header title card exists and is displayed
        composeTestRule.onNodeWithTag("vault_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cofre de Chaves de API").assertIsDisplayed()

        // Verify FLAG_SECURE toggle card exists and is displayed
        composeTestRule.onNodeWithTag("flag_secure_toggle_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flag_secure_switch").assertIsDisplayed()

        // Verify provider cards exist and first card (Gemini) is displayed
        composeTestRule.onNodeWithTag("provider_card_gemini").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Gemini").assertIsDisplayed()
    }
}
