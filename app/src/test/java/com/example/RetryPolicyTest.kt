package com.example

import com.example.errors.AppError
import com.example.resilience.RetryConfig
import com.example.resilience.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class RetryPolicyTest {

    private val fixedRandom = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.0
    }

    @Test
    fun testShouldRetryOnlyOnRetryableAndIdempotent() {
        val policy = RetryPolicy(RetryConfig(maxAttempts = 3), fixedRandom)

        val retryableError = AppError.Network(technicalCause = "Falha de rede")
        val authError = AppError.Auth(technicalCause = "Chave inválida")
        val invalidReqError = AppError.InvalidRequest(technicalCause = "HTTP 400")

        // Retryable and idempotent
        assertTrue(policy.shouldRetry(retryableError, isIdempotent = true, currentAttempt = 1))
        assertTrue(policy.shouldRetry(retryableError, isIdempotent = true, currentAttempt = 2))
        assertFalse(policy.shouldRetry(retryableError, isIdempotent = true, currentAttempt = 3)) // Exceeded max attempts

        // Non-idempotent operation should never retry
        assertFalse(policy.shouldRetry(retryableError, isIdempotent = false, currentAttempt = 1))

        // Non-retryable errors should never retry
        assertFalse(policy.shouldRetry(authError, isIdempotent = true, currentAttempt = 1))
        assertFalse(policy.shouldRetry(invalidReqError, isIdempotent = true, currentAttempt = 1))
    }

    @Test
    fun testCalculateBackoffRespectsRetryAfter() {
        val policy = RetryPolicy(RetryConfig(initialDelayMs = 500L, maxDelayMs = 30000L), fixedRandom)

        val quotaErrorWithHeader = AppError.Quota(technicalCause = "Rate limit", retryAfterMs = 5000L)
        val delay = policy.calculateBackoff(attempt = 1, quotaErrorWithHeader)
        assertEquals(5000L, delay)
    }

    @Test
    fun testExponentialBackoffFormula() {
        val policy = RetryPolicy(RetryConfig(initialDelayMs = 500L, multiplier = 2.0, maxDelayMs = 10000L, jitterFactor = 0.0), fixedRandom)
        val error = AppError.Network(technicalCause = "Timeout")

        val delay1 = policy.calculateBackoff(1, error)
        val delay2 = policy.calculateBackoff(2, error)
        val delay3 = policy.calculateBackoff(3, error)

        assertEquals(500L, delay1)
        assertEquals(1000L, delay2)
        assertEquals(2000L, delay3)
    }

    @Test
    fun testExecuteSucceedsAfterRetries() = runBlocking {
        val policy = RetryPolicy(RetryConfig(maxAttempts = 3, initialDelayMs = 10L), fixedRandom)
        var attemptsRan = 0

        val result = policy.execute(isIdempotent = true) { attempt ->
            attemptsRan = attempt
            if (attempt < 2) {
                throw AppError.Network(technicalCause = "Erro temporario")
            }
            "Resultado Sucesso"
        }

        assertTrue(result.isSuccess)
        assertEquals("Resultado Sucesso", result.getOrNull())
        assertEquals(2, attemptsRan)
    }

    @Test
    fun testExecuteNeverSwallowsCancellationException() = runBlocking {
        val policy = RetryPolicy(RetryConfig(maxAttempts = 3), fixedRandom)

        try {
            policy.execute<String>(isIdempotent = true) {
                throw CancellationException("Cancelado pelo usuario")
            }
            fail("Deveria ter relancado CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelado pelo usuario", e.message)
        }
    }
}
