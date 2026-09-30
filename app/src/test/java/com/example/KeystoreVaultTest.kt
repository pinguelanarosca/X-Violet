package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.errors.AppError
import com.example.security.AndroidKeyStoreCryptoEngine
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.DatabaseManager
import com.example.storage.HubDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreVaultTest {

    private lateinit var context: Context
    private lateinit var db: HubDatabase
    private lateinit var blobDir: File
    private lateinit var blobStorage: VaultBlobStorage
    private lateinit var testCryptoEngine: TestCryptoEngine
    private lateinit var vault: KeystoreVault

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        blobDir = File(context.filesDir, "test_vault_blobs").apply { mkdirs() }
        blobStorage = VaultBlobStorage(blobDir)
        testCryptoEngine = TestCryptoEngine()
        vault = KeystoreVault(
            vaultDao = db.vaultDao(),
            blobStorage = blobStorage,
            cryptoEngine = testCryptoEngine
        )
    }

    @After
    fun tearDown() {
        db.close()
        blobDir.deleteRecursively()
    }

    @Test
    fun testSaveAndReadRoundtrip() {
        runBlocking {
            val rawKey = "AIzaSyTestKey1234567890abcdef"
            val saveResult = vault.saveKey("gemini", rawKey)
            assertTrue("Salvar chave deve ser sucesso", saveResult.isSuccess)

            val readResult = vault.getDecryptedKey("gemini")
            assertTrue("Ler chave deve ser sucesso", readResult.isSuccess)
            assertEquals(rawKey, readResult.getOrNull())

            // Verify metadata in Room
            val credential = db.vaultDao().getCredential("gemini")
            assertNotNull(credential)
            assertEquals("••••cdef", credential?.maskedKey)
            assertEquals(KeystoreVault.STATUS_ACTIVE, credential?.status)
        }
    }

    @Test
    fun testBlobOnDiskDoesNotContainPlaintextKey() {
        runBlocking {
            val rawKey = "sk-superSecretToken1234567890abcdef"
            vault.saveKey("openai", rawKey)

            val blobFile = File(blobDir, "openai.vault")
            assertTrue("Arquivo .vault deve existir no disco", blobFile.exists())

            val diskBytes = blobFile.readBytes()
            val diskText = String(diskBytes, Charsets.ISO_8859_1)

            assertFalse("O arquivo em disco NAO pode conter a chave em texto claro", diskText.contains(rawKey))
            assertFalse("O arquivo em disco NAO pode conter parte sensivel em texto claro", diskText.contains("superSecretToken"))
        }
    }

    @Test
    fun testTamperedBlobLeadsToNeedsReentryWithoutDeleting() {
        runBlocking {
            val rawKey = "gsk_validGroqKey1234567890"
            vault.saveKey("groq", rawKey)

            // Corrupt the blob on disk
            vault.simulateDecryptionFailure("groq")

            // Attempt decryption: must not throw, must return failure with AppError.Crypto
            val decryptResult = vault.getDecryptedKey("groq")
            assertFalse("Decifragem deve falhar apos adulteracao", decryptResult.isSuccess)
            val error = decryptResult.exceptionOrNull()
            assertTrue("Erro deve ser AppError.Crypto", error is AppError.Crypto)

            // Must NOT delete the record: status in DB must transition to NEEDS_REENTRY
            val credential = db.vaultDao().getCredential("groq")
            assertNotNull("Credencial nao pode ter sido apagada", credential)
            assertEquals(KeystoreVault.STATUS_NEEDS_REENTRY, credential?.status)
            assertTrue("Arquivo blob adulterado deve permanecer no disco", File(blobDir, "groq.vault").exists())
        }
    }

    @Test
    fun testAadMismatchBetweenProvidersFailsDecryption() {
        runBlocking {
            val rawKey = "xai-testToken1234567890"
            vault.saveKey("xai", rawKey)

            // Read payload from xai and write it as anthropic
            val payload = blobStorage.readBlob("xai")
            assertNotNull(payload)
            blobStorage.saveBlob("anthropic", payload!!)

            // Try decrypting with anthropic (AAD is "anthropic", but ciphertext was authenticated with AAD "xai")
            val decryptResult = vault.getDecryptedKey("anthropic")
            assertFalse("Decifragem deve falhar devido a incompatibilidade de AAD", decryptResult.isSuccess)
            assertTrue(decryptResult.exceptionOrNull() is AppError.Crypto)
        }
    }

    @Test
    fun testDatabaseCorruptRecoveryPreservesVaultKeyDecryption() {
        runBlocking {
            // Use real DatabaseManager backed by real file
            val scope = CoroutineScope(Dispatchers.IO)
            val dbManager = DatabaseManager(context, scope)
            val fileBlobDir = File(context.filesDir, "vault_blobs_persistence_test").apply { mkdirs() }
            val persistenceBlobStorage = VaultBlobStorage(fileBlobDir)

            val persistentVault = KeystoreVault(
                vaultDao = dbManager.database.vaultDao(),
                blobStorage = persistenceBlobStorage,
                cryptoEngine = testCryptoEngine
            )

            val rawKey = "AIzaSyPermanentKeyThatSurvivesCorruption12345"
            val saveResult = persistentVault.saveKey("gemini", rawKey)
            assertTrue(saveResult.isSuccess)

            // Simulate database file corruption and recovery
            dbManager.recoverFromCorruption(Exception("Simulacao de banco corrompido para testar sobrevivencia do cofre"))

            // Create vault instance pointing to the newly recreated database
            val restoredVault = KeystoreVault(
                vaultDao = dbManager.database.vaultDao(),
                blobStorage = persistenceBlobStorage,
                cryptoEngine = testCryptoEngine
            )

            // The key MUST still be decryptable because blobs are outside SQLite!
            val readAfterCorruption = restoredVault.getDecryptedKey("gemini")
            assertTrue("A chave DEVE continuar decifravel mesmo apos o banco ter sido corrompido e recriado!", readAfterCorruption.isSuccess)
            assertEquals(rawKey, readAfterCorruption.getOrNull())

            fileBlobDir.deleteRecursively()
        }
    }

    @Test
    fun testAndroidKeyStoreThrowsAppErrorCryptoWhenKeystoreUnavailable() {
        // In local JVM/Robolectric without AndroidKeyStore hardware,
        // AndroidKeyStoreCryptoEngine must throw AppError.Crypto and NEVER fall back to software encryption.
        val prodEngine = AndroidKeyStoreCryptoEngine("TestNonExistentAlias")
        try {
            prodEngine.encrypt("test".toByteArray(), "aad".toByteArray())
            org.junit.Assert.fail("Deveria lancar AppError.Crypto e nao retornar chave em software")
        } catch (e: Throwable) {
            assertTrue("Excecao DEVE ser AppError.Crypto, nunca fallback para software: ${e.javaClass.name}", e is AppError.Crypto)
            val cryptoError = e as AppError.Crypto
            assertEquals("ERR_KEYSTORE_INIT", cryptoError.code)
        }
    }

    @Test
    fun testProviderIdValidationRejectsPathTraversalAndInvalidNames() {
        val invalidIds = listOf(
            "../x",
            "a/b",
            "",
            "a".repeat(65),
            "chave_π",
            "provider..name",
            "../../etc/passwd",
            "provider with spaces",
            "provider@id"
        )

        for (invalidId in invalidIds) {
            try {
                blobStorage.saveBlob(invalidId, com.example.security.EncryptedPayload(ByteArray(12), "data".toByteArray()))
                org.junit.Assert.fail("Deveria falhar para providerId inválido ou tentativa de path traversal: '$invalidId'")
            } catch (e: Throwable) {
                val isExpected = e is IllegalArgumentException || e is SecurityException
                assertTrue("Exceção esperada (IllegalArgumentException ou SecurityException) para '$invalidId', veio: ${e.javaClass.simpleName}", isExpected)
            }

            try {
                blobStorage.readBlob(invalidId)
                org.junit.Assert.fail("readBlob deveria falhar para providerId inválido: '$invalidId'")
            } catch (e: Throwable) {
                val isExpected = e is IllegalArgumentException || e is SecurityException
                assertTrue(isExpected)
            }
        }

        // Verify that nothing was written outside baseDir
        val outsideFile = File(context.filesDir, "x.vault")
        assertFalse("Nenhum arquivo pode escapar da pasta vault_blobs!", outsideFile.exists())
    }

    @Test
    fun testDatabaseCorruptRecoveryReconcilesOrphanBlobs() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO)
            val dbManager = DatabaseManager(context, scope)
            val fileBlobDir = File(context.filesDir, "vault_blobs_reconcile_test").apply { mkdirs() }
            val persistenceBlobStorage = VaultBlobStorage(fileBlobDir)

            val persistentVault = KeystoreVault(
                vaultDao = dbManager.database.vaultDao(),
                blobStorage = persistenceBlobStorage,
                cryptoEngine = testCryptoEngine
            )

            // Save keys for two providers
            persistentVault.saveKey("gemini", "AIzaSyGeminiKey1234567890")
            persistentVault.saveKey("openai", "sk-proj-OpenAiKey1234567890")

            // Confirm they are in DB
            assertNotNull(dbManager.database.vaultDao().getCredential("gemini"))
            assertNotNull(dbManager.database.vaultDao().getCredential("openai"))

            // Simulate database file corruption and recovery -> Room database is recreated from scratch
            dbManager.recoverFromCorruption(Exception("Simulacao de corrupcao total do banco"))

            // In the recreated database, metadata is initially empty
            val recreatedDb = dbManager.database
            assertNull(recreatedDb.vaultDao().getCredential("gemini"))
            assertNull(recreatedDb.vaultDao().getCredential("openai"))

            // Blobs on disk still exist!
            assertTrue(persistenceBlobStorage.hasBlob("gemini"))
            assertTrue(persistenceBlobStorage.hasBlob("openai"))

            // Instantiate vault and run reconciliation
            val reconciledVault = KeystoreVault(
                vaultDao = recreatedDb.vaultDao(),
                blobStorage = persistenceBlobStorage,
                cryptoEngine = testCryptoEngine
            )
            val recoveredProviders = reconciledVault.reconcileOrphanBlobs()

            assertEquals(2, recoveredProviders.size)
            assertTrue(recoveredProviders.contains("gemini"))
            assertTrue(recoveredProviders.contains("openai"))

            // Verify that entries came back in Room with status NEEDS_REENTRY ("recuperada, precisa revalidar")
            val geminiCredential = recreatedDb.vaultDao().getCredential("gemini")
            assertNotNull("Entrada do Gemini deve ter retornado", geminiCredential)
            assertEquals(KeystoreVault.STATUS_NEEDS_REENTRY, geminiCredential?.status)

            val openAiCredential = recreatedDb.vaultDao().getCredential("openai")
            assertNotNull("Entrada da OpenAI deve ter retornado", openAiCredential)
            assertEquals(KeystoreVault.STATUS_NEEDS_REENTRY, openAiCredential?.status)

            // Revalidation recovers active status and plaintext key!
            val revalidateResult = reconciledVault.revalidateKey("gemini")
            assertTrue("Revalidação deve suceder com integridade", revalidateResult.isSuccess)
            assertEquals("AIzaSyGeminiKey1234567890", revalidateResult.getOrNull())

            val revalidatedGeminiCredential = recreatedDb.vaultDao().getCredential("gemini")
            assertEquals(KeystoreVault.STATUS_ACTIVE, revalidatedGeminiCredential?.status)
            assertEquals("••••7890", revalidatedGeminiCredential?.maskedKey)

            fileBlobDir.deleteRecursively()
        }
    }
}
