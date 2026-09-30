package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.logging.CrashHandler
import com.example.logging.CrashlyticsManager
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashlyticsIntegrationTest {

    @Test
    fun testCrashlyticsManagerSafeInitializationWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Initializing in test environment without google-services.json must NOT throw or crash
        CrashlyticsManager.init(context)

        // 2. Logging and custom keys must be safe even when offline / uninitialized
        CrashlyticsManager.log("Teste de breadcrumb Crashlytics")
        CrashlyticsManager.setCustomKey("test_key", "test_value")

        // 3. Exception recording must never throw even without Firebase initialized
        val testException = IllegalStateException("Excecao de teste para estabilidade e depuracao")
        CrashlyticsManager.recordException(testException, "TesteUnitario")
    }

    @Test
    fun testCrashHandlerIntegratesWithCrashlyticsAndSavesLocalReport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var fallbackCalled = false
        val fallbackHandler = Thread.UncaughtExceptionHandler { _, _ ->
            fallbackCalled = true
        }

        val handler = CrashHandler(context, fallbackHandler)
        val dummyThread = Thread.currentThread()
        val dummyException = RuntimeException("Falha de teste capturada pelo CrashHandler")

        handler.uncaughtException(dummyThread, dummyException)

        // Verify fallback was called
        assertTrue("Handler padrao deve ser acionado apos persistencia", fallbackCalled)

        // Verify local report was saved
        val report = CrashHandler.getLastCrashReport(context)
        assertNotNull("Relatorio local sanitizado deve existir", report)
        assertTrue(report!!.contains("Falha de teste capturada pelo CrashHandler"))

        // Cleanup
        CrashHandler.clearLastCrashReport(context)
    }

    @Test
    fun testHubLoggerForwardsErrorToCrashlyticsSafely() {
        // Logging an ERROR level with throwable must forward to CrashlyticsManager safely
        val exception = IllegalArgumentException("Erro de validacao testando Crashlytics")
        HubLogger.log(
            level = LogLevel.ERROR,
            category = LogCategory.APP,
            message = "Erro critico reportado ao logger",
            throwable = exception
        )
    }
}
