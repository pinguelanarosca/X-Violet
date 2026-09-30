package com.example.resilience

import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class CircuitBreakerState {
    object Closed : CircuitBreakerState() {
        override fun toString(): String = "Fechado"
    }
    data class Open(val openedAt: Long, val cooldownMs: Long) : CircuitBreakerState() {
        override fun toString(): String = "Aberto (Instável)"
    }
    object HalfOpen : CircuitBreakerState() {
        override fun toString(): String = "Meio-aberto (Sonda)"
    }
}

class CircuitBreaker(
    val name: String,
    val failureThreshold: Int = 3,
    val initialCooldownMs: Long = 10000L,
    val maxCooldownMs: Long = 60000L
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<CircuitBreakerState>(CircuitBreakerState.Closed)
    val state: StateFlow<CircuitBreakerState> = _state.asStateFlow()

    private var consecutiveFailures = 0
    private var currentCooldownMs = initialCooldownMs
    private var probeInProgress = false

    suspend fun <T> execute(block: suspend () -> T): T {
        canExecuteOrThrow()

        return try {
            val result = block()
            onSuccess()
            result
        } catch (t: Throwable) {
            onFailure(t)
            throw t
        }
    }

    suspend fun canExecuteOrThrow() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            when (val s = _state.value) {
                is CircuitBreakerState.Closed -> {
                    // Normal execution allowed
                }
                is CircuitBreakerState.Open -> {
                    if (now - s.openedAt >= s.cooldownMs) {
                        // Cooldown elapsed: transition to Half-Open
                        _state.value = CircuitBreakerState.HalfOpen
                        probeInProgress = true
                        HubLogger.log(
                            level = LogLevel.INFO,
                            category = LogCategory.PROVIDER,
                            message = "[$name] Cooldown expirado. Transição para Meio-Aberto (executando chamada de sonda)."
                        )
                    } else {
                        val remainingSec = (s.cooldownMs - (now - s.openedAt)) / 1000L
                        throw AppError.ProviderUnavailable(
                            code = "ERR_CIRCUIT_OPEN",
                            userMessage = "O serviço está instável temporariamente. Circuit breaker aberto. Tente em ${remainingSec}s.",
                            technicalCause = "CircuitBreaker [$name] está em estado ABERTO. Cooldown restante: ${remainingSec}s."
                        )
                    }
                }
                is CircuitBreakerState.HalfOpen -> {
                    if (probeInProgress) {
                        // Sonda já em andamento, rejeita chamadas concorrentes
                        throw AppError.ProviderUnavailable(
                            code = "ERR_CIRCUIT_HALF_OPEN_BUSY",
                            userMessage = "O serviço está sendo testado (sonda em andamento). Aguarde um momento.",
                            technicalCause = "CircuitBreaker [$name] Meio-aberto: sonda em execução."
                        )
                    } else {
                        probeInProgress = true
                    }
                }
            }
        }
    }

    suspend fun onSuccess() {
        mutex.withLock {
            consecutiveFailures = 0
            currentCooldownMs = initialCooldownMs
            probeInProgress = false
            if (_state.value != CircuitBreakerState.Closed) {
                _state.value = CircuitBreakerState.Closed
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.PROVIDER,
                    message = "[$name] Chamada bem-sucedida. Circuit breaker restabelecido para FECHADO."
                )
            }
        }
    }

    suspend fun onFailure(throwable: Throwable) {
        // Only count retryable errors towards circuit tripping (Rule 11)
        val isRetryable = if (throwable is AppError) throwable.retryable else false
        if (!isRetryable && throwable !is java.io.IOException) {
            return
        }

        mutex.withLock {
            val now = System.currentTimeMillis()
            when (_state.value) {
                is CircuitBreakerState.Closed -> {
                    consecutiveFailures++
                    HubLogger.log(
                        level = LogLevel.WARN,
                        category = LogCategory.PROVIDER,
                        message = "[$name] Falha retentável registrada: $consecutiveFailures/$failureThreshold"
                    )
                    if (consecutiveFailures >= failureThreshold) {
                        _state.value = CircuitBreakerState.Open(now, currentCooldownMs)
                        HubLogger.log(
                            level = LogLevel.ERROR,
                            category = LogCategory.PROVIDER,
                            message = "[$name] Limite de falhas atingido ($failureThreshold). Circuit breaker ABERTO por ${currentCooldownMs / 1000}s."
                        )
                    }
                }
                is CircuitBreakerState.HalfOpen -> {
                    probeInProgress = false
                    // Sonda falhou: reabre o circuito com cooldown crescente
                    currentCooldownMs = (currentCooldownMs * 2).coerceAtMost(maxCooldownMs)
                    _state.value = CircuitBreakerState.Open(now, currentCooldownMs)
                    HubLogger.log(
                        level = LogLevel.ERROR,
                        category = LogCategory.PROVIDER,
                        message = "[$name] Sonda falhou. Circuit breaker reaberto com cooldown estendido para ${currentCooldownMs / 1000}s."
                    )
                }
                is CircuitBreakerState.Open -> {
                    // Already open
                }
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            consecutiveFailures = 0
            currentCooldownMs = initialCooldownMs
            probeInProgress = false
            _state.value = CircuitBreakerState.Closed
        }
    }

    suspend fun tripForTesting() {
        mutex.withLock {
            consecutiveFailures = failureThreshold
            _state.value = CircuitBreakerState.Open(System.currentTimeMillis(), initialCooldownMs)
        }
    }
}
