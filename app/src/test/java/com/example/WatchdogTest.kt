package com.example

import com.example.errors.AppError
import com.example.resilience.Watchdog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WatchdogTest {

    @Test
    fun testWatchdogCompletesNormallyWhenWithinTimeout() = runBlocking {
        val result = Watchdog.runWithWatchdog(timeoutMs = 1000L, operationName = "QuickOp") {
            delay(50L)
            "Operacao rapida"
        }
        assertEquals("Operacao rapida", result)
    }

    @Test
    fun testWatchdogThrowsTimeoutWhenStuck() = runBlocking {
        try {
            Watchdog.runWithWatchdog(timeoutMs = 100L, operationName = "StuckOp") {
                delay(1000L)
                "Nunca conclui"
            }
            fail("Deveria lancar AppError.Timeout")
        } catch (e: AppError.Timeout) {
            assertEquals("ERR_WATCHDOG_TIMEOUT", e.code)
            assertTrue(e.userMessage.contains("StuckOp"))
        }
    }
}
