package com.example.resilience

import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

object Watchdog {

    suspend fun <T> runWithWatchdog(
        timeoutMs: Long,
        operationName: String,
        category: LogCategory = LogCategory.PROVIDER,
        block: suspend () -> T
    ): T {
        return try {
            withTimeout(timeoutMs) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            HubLogger.log(
                level = LogLevel.ERROR,
                category = category,
                message = "Watchdog disparado: '$operationName' permaneceu travado por mais de ${timeoutMs}ms."
            )
            throw AppError.Timeout(
                code = "ERR_WATCHDOG_TIMEOUT",
                userMessage = "A operação '$operationName' demorou muito para responder e foi interrompida pelo sistema.",
                technicalCause = "Watchdog atingiu limite de ${timeoutMs}ms para '$operationName'"
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}
