package com.example.errors

import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.logging.LogSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLException

object ErrorMapper {

    fun fromHttp(
        statusCode: Int,
        responseBody: String? = null,
        headers: Map<String, String>? = null
    ): AppError {
        val sanitizedBody = LogSanitizer.sanitize(responseBody)
        val truncatedSnippet = sanitizedBody.take(300)

        // Parse Retry-After header if present
        val retryAfterHeader = headers?.entries?.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value
        val retryAfterMs = parseRetryAfter(retryAfterHeader)

        return when (statusCode) {
            401, 403 -> AppError.Auth(
                code = "ERR_HTTP_$statusCode",
                technicalCause = "HTTP $statusCode não autorizado: $truncatedSnippet"
            )
            404 -> AppError.ModelUnavailable(
                code = "ERR_HTTP_404",
                technicalCause = "HTTP 404 modelo ou rota não encontrada: $truncatedSnippet"
            )
            429 -> AppError.Quota(
                code = "ERR_HTTP_429",
                technicalCause = "HTTP 429 excesso de taxa / cota esgotada: $truncatedSnippet",
                retryAfterMs = retryAfterMs
            )
            400 -> {
                if (truncatedSnippet.contains("safety", ignoreCase = true) || truncatedSnippet.contains("blocked", ignoreCase = true)) {
                    AppError.SafetyBlocked(
                        code = "ERR_SAFETY_BLOCKED",
                        technicalCause = "Bloqueio de segurança/moderação: $truncatedSnippet"
                    )
                } else {
                    AppError.InvalidRequest(
                        code = "ERR_HTTP_400",
                        technicalCause = "HTTP 400 Requisição inválida: $truncatedSnippet"
                    )
                }
            }
            in 500..599 -> AppError.ProviderUnavailable(
                code = "ERR_HTTP_$statusCode",
                technicalCause = "HTTP $statusCode Erro do servidor do provedor: $truncatedSnippet"
            )
            else -> AppError.InvalidResponse(
                code = "ERR_HTTP_$statusCode",
                technicalCause = "HTTP status code inesperado $statusCode: $truncatedSnippet",
                sanitizedSnippet = truncatedSnippet
            )
        }
    }

    fun fromThrowable(throwable: Throwable): AppError {
        if (throwable is AppError) return throwable
        if (throwable is CancellationException) {
            return AppError.Cancelled(
                technicalCause = throwable.message ?: "Cancelado cooperativamente"
            )
        }

        val sanitizedMsg = LogSanitizer.sanitize(throwable.message ?: throwable.javaClass.simpleName)

        return when (throwable) {
            is SocketTimeoutException -> AppError.Timeout(
                technicalCause = "Socket timeout: $sanitizedMsg"
            )
            is UnknownHostException -> AppError.Network(
                technicalCause = "Falha de DNS (host desconhecido): $sanitizedMsg"
            )
            is ConnectException -> AppError.Network(
                technicalCause = "Falha de conexão com o servidor: $sanitizedMsg"
            )
            is SSLException -> AppError.Network(
                code = "ERR_SSL_TLS",
                userMessage = "Erro no certificado SSL/TLS de segurança da conexão.",
                technicalCause = "Falha no handshake TLS/SSL: $sanitizedMsg"
            )
            is IOException -> AppError.Network(
                technicalCause = "Falha de E/S de rede: $sanitizedMsg"
            )
            is GeneralSecurityException -> AppError.Crypto(
                technicalCause = "Exceção criptográfica no Keystore: $sanitizedMsg"
            )
            is android.database.sqlite.SQLiteException -> AppError.Storage(
                technicalCause = "Erro SQLite: $sanitizedMsg"
            )
            else -> AppError.Unknown(
                technicalCause = "Exceção inesperada (${throwable.javaClass.simpleName}): $sanitizedMsg"
            )
        }
    }

    private fun parseRetryAfter(headerValue: String?): Long? {
        if (headerValue.isNullOrBlank()) return null
        return try {
            val seconds = headerValue.trim().toLong()
            seconds * 1000L
        } catch (e: NumberFormatException) {
            // Could be HTTP Date format, or unparseable
            null
        }
    }
}

inline fun <T> runCatchingApp(
    category: LogCategory = LogCategory.APP,
    correlationId: String? = null,
    block: () -> T
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        // ALWAYS rethrow CancellationException per Rule 10!
        throw e
    } catch (t: Throwable) {
        val appError = ErrorMapper.fromThrowable(t)
        HubLogger.log(
            level = LogLevel.ERROR,
            category = category,
            message = "Operação falhou: ${appError.userMessage}",
            throwable = t,
            correlationId = correlationId
        )
        Result.failure(appError)
    }
}

fun CoroutineScope.safeLaunch(
    category: LogCategory = LogCategory.APP,
    correlationId: String? = null,
    onError: ((AppError) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) {
            return@CoroutineExceptionHandler
        }
        val appError = ErrorMapper.fromThrowable(throwable)
        HubLogger.log(
            level = LogLevel.ERROR,
            category = category,
            message = "Erro capturado no safeLaunch: ${appError.userMessage}",
            throwable = throwable,
            correlationId = correlationId
        )
        onError?.invoke(appError)
    }

    return launch(exceptionHandler) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val appError = ErrorMapper.fromThrowable(t)
            HubLogger.log(
                level = LogLevel.ERROR,
                category = category,
                message = "Erro tratado no bloco safeLaunch: ${appError.userMessage}",
                throwable = t,
                correlationId = correlationId
            )
            onError?.invoke(appError)
        }
    }
}
