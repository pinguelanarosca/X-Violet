package com.example

import com.example.errors.AppError
import com.example.resilience.CircuitBreaker
import com.example.resilience.CircuitBreakerState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CircuitBreakerTest {

    @Test
    fun testInitialStateIsClosed() {
        val breaker = CircuitBreaker("TestBreaker", failureThreshold = 3)
        assertEquals(CircuitBreakerState.Closed, breaker.state.value)
    }

    @Test
    fun testTripsToOpenAfterThresholdFailures() = runBlocking {
        val breaker = CircuitBreaker("TestBreaker", failureThreshold = 3, initialCooldownMs = 10000L)

        val retryableError = AppError.ProviderUnavailable(technicalCause = "503 Service Unavailable")

        // Failure 1
        breaker.onFailure(retryableError)
        assertEquals(CircuitBreakerState.Closed, breaker.state.value)

        // Failure 2
        breaker.onFailure(retryableError)
        assertEquals(CircuitBreakerState.Closed, breaker.state.value)

        // Failure 3 -> Trips to Open
        breaker.onFailure(retryableError)
        assertTrue(breaker.state.value is CircuitBreakerState.Open)
    }

    @Test
    fun testRejectsExecutionWhenOpen() = runBlocking {
        val breaker = CircuitBreaker("TestBreaker", failureThreshold = 2, initialCooldownMs = 50000L)
        breaker.tripForTesting()

        try {
            breaker.execute { "Nao deve rodar" }
            fail("Deveria lancar AppError.ProviderUnavailable com circuito aberto")
        } catch (e: AppError.ProviderUnavailable) {
            assertEquals("ERR_CIRCUIT_OPEN", e.code)
        }
    }

    @Test
    fun testSuccessResetsFailures() = runBlocking {
        val breaker = CircuitBreaker("TestBreaker", failureThreshold = 3)
        breaker.onFailure(AppError.Network(technicalCause = "Falha 1"))
        breaker.onSuccess()
        assertEquals(CircuitBreakerState.Closed, breaker.state.value)

        // Needs 3 more failures to trip
        breaker.onFailure(AppError.Network(technicalCause = "Falha 1 apos reset"))
        breaker.onFailure(AppError.Network(technicalCause = "Falha 2 apos reset"))
        assertEquals(CircuitBreakerState.Closed, breaker.state.value)
    }
}
