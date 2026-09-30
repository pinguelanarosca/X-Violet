package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.errors.AbnormalShutdownManager
import com.example.storage.DatabaseManager
import com.example.storage.HubDatabase
import com.example.storage.entities.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemRecoveryRobolectricTest {

    @Test
    fun testAbnormalShutdownDetectionAndRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AbnormalShutdownManager(context)

        // 1. Normal clean start
        manager.clearFlag()
        manager.onAppStart()
        assertFalse(manager.wasAbnormalShutdown)

        // 2. Simulate abnormal termination: flag file is left behind on disk
        val flagFile = File(context.filesDir, "session_active.flag")
        assertTrue("Flag de sessão ativa deve ter sido criada no disco", flagFile.exists())

        // Create a simulated crash file
        val crashFile = File(context.filesDir, "last_crash.txt")
        FileOutputStream(crashFile).use {
            it.write("=== HUB CRASH REPORT ===\nException: java.lang.RuntimeException: Falha simulada\n".toByteArray())
        }

        // 3. Re-instantiate manager simulating app restart after crash
        val restartManager = AbnormalShutdownManager(context)
        restartManager.onAppStart()

        // 4. Verify abnormal shutdown was detected
        assertTrue("Deveria ter detectado encerramento anormal", restartManager.wasAbnormalShutdown)
        assertNotNull("Deveria conter relatório de crash sanitizado", restartManager.lastCrashReport)
        assertTrue(restartManager.lastCrashReport!!.contains("Falha simulada"))

        // 5. Clean exit
        restartManager.onAppCleanExit()
        assertFalse("Flag deve ter sido removida no encerramento limpo", flagFile.exists())
    }

    @Test
    fun testDatabaseCorruptRecoveryAndBackup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(Dispatchers.IO)
            val dbManager = DatabaseManager(context, scope)

            // Write a test log entry
            val db = dbManager.database
            db.logDao().insertBatch(
                listOf(
                    LogEntryEntity(
                        timestamp = System.currentTimeMillis(),
                        level = "INFO",
                        category = "STORAGE",
                        message = "Log antes da corrupcao de teste"
                    )
                )
            )
            val testStorageLogsBefore = db.logDao().getRecentLogsList().filter {
                it.category == "STORAGE" && it.message == "Log antes da corrupcao de teste"
            }
            assertEquals(1, testStorageLogsBefore.size)

            // Force corrupt recovery
            val cause = Exception("Simulacao forcada de arquivo corrompido")
            dbManager.recoverFromCorruption(cause)

            // Verify backup was generated on disk
            val dbDir = context.getDatabasePath(HubDatabase.DATABASE_NAME).parentFile
            val backupFiles = dbDir?.listFiles { file ->
                file.name.startsWith("${HubDatabase.DATABASE_NAME}_corrupt_backup_")
            } ?: emptyArray()

            assertTrue("Deveria ter criado pelo menos um arquivo de backup de banco corrompido", backupFiles.isNotEmpty())

            // Verify notification event is emitted
            val notification = dbManager.recoveryEvent.value
            assertNotNull("Evento de recuperacao deve ter sido emitido", notification)
            assertTrue(notification!!.contains("recriado de forma segura com backup"))

            // Verify new database is accessible and does not contain the old pre-corrupted data
            val newDb = dbManager.database
            val testStorageLogsAfter = newDb.logDao().getRecentLogsList().filter {
                it.category == "STORAGE" && it.message == "Log antes da corrupcao de teste"
            }
            assertEquals(0, testStorageLogsAfter.size)
        }
    }
}
