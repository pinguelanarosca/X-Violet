package com.example.resilience

import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 30000L,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.25
)

class RetryPolicy(
    val config: RetryConfig = RetryConfig(),
    private val random: Random = Random.Default
) {
    suspend fun <T> execute(
        isIdempotent: Boolean = true,
        correlationId: String? = null,
        onRetry: ((attempt: Int, error: AppError, delayMs: Long) -> Unit)? = null,
        block: suspend (attempt: Int) -> T
    ): Result<T> {
        var attempt = 1

        while (true) {
            try {
                val result = block(attempt)
                return Result.success(result)
            } catch (e: CancellationException) {
                // Rule 10: Never catch CancellationException as an ordinary retryable failure
                throw e
            } catch (e: AppError) {
                if (shouldRetry(e, isIdempotent, attempt)) {
                    val waitTime = calculateBackoff(attempt, e)
                    HubLogger.log(
                        level = LogLevel.WARN,
                        category = LogCategory.PROVIDER,
                        message = "Tentativa $attempt falhou com erro retentável (${e.code}). Aguardando ${waitTime}ms para próxima tentativa...",
                        correlationId = correlationId
                    )
                    onRetry?.invoke(attempt, e, waitTime)
                    delay(waitTime)
                    attempt++
                } else {
                    return Result.failure(e)
                }
            } catch (t: Throwable) {
                // Non-AppError unknown exception
                val appError = com.example.errors.ErrorMapper.fromThrowable(t)
                if (shouldRetry(appError, isIdempotent, attempt)) {
                    val waitTime = calculateBackoff(attempt, appError)
                    HubLogger.log(
                        level = LogLevel.WARN,
                        category = LogCategory.PROVIDER,
                        message = "Tentativa $attempt falhou com erro genérico retentável. Aguardando ${waitTime}ms...",
                        throwable = t,
                        correlationId = correlationId
                    )
                    onRetry?.invoke(attempt, appError, waitTime)
                    delay(waitTime)
                    attempt++
                } else {
                    return Result.failure(appError)
                }
            }
        }
    }

    fun shouldRetry(error: AppError, isIdempotent: Boolean, currentAttempt: Int): Boolean {
        // Must have remaining attempts
        if (currentAttempt >= config.maxAttempts) return false

        // Must be marked as retryable
        if (!error.retryable) return false

        // Operation MUST be idempotent (Rule 11)
        if (!isIdempotent) return false

        // Never retry user cancellation or authentication errors
        if (error is AppError.Cancelled || error is AppError.Auth || error is AppError.InvalidRequest || error is AppError.ModelUnavailable) {
            return false
        }

        return true
    }

    fun calculateBackoff(attempt: Int, error: AppError): Long {
        // If Quota error explicitly provided Retry-After header, respect it
        if (error is AppError.Quota && error.retryAfterMs != null && error.retryAfterMs > 0) {
            return minOf(error.retryAfterMs, config.maxDelayMs)
        }

        val baseDelay = config.initialDelayMs * Math.pow(config.multiplier, (attempt - 1).toDouble()).toLong()
        val cappedDelay = minOf(baseDelay, config.maxDelayMs)

        // Apply full jitter within range: [cappedDelay * (1 - jitterFactor), cappedDelay * (1 + jitterFactor)]
        val minJitter = (cappedDelay * (1.0 - config.jitterFactor)).toLong().coerceAtLeast(0L)
        val maxJitter = (cappedDelay * (1.0 + config.jitterFactor)).toLong()
        val jittered = if (maxJitter > minJitter) {
            minJitter + (random.nextDouble() * (maxJitter - minJitter)).toLong()
        } else {
            cappedDelay
        }

        return jittered.coerceIn(config.initialDelayMs, config.maxDelayMs)
    }
}
