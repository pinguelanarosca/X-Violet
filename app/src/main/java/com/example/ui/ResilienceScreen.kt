package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.HubApplication
import com.example.R
import com.example.errors.AppError
import com.example.logging.CrashHandler
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.resilience.CircuitBreakerState
import com.example.resilience.Watchdog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ResilienceScreen(
    app: HubApplication,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val isConnected by app.connectivityObserver.isConnected.collectAsStateWithLifecycle()
    val geminiCircuitState by app.geminiCircuitBreaker.state.collectAsStateWithLifecycle()
    val openAiCircuitState by app.openAiCircuitBreaker.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("resilience_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Network Connectivity Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_status_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.NetworkCheck else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Column {
                        Text(
                            text = if (isConnected) stringResource(R.string.network_status_online) else stringResource(R.string.network_status_offline),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isConnected) {
                                "Monitoramento contínuo via ConnectivityManager.NetworkCallback"
                            } else {
                                "Sem conexão detectada pelo sistema operacional. Chamadas falham rápido com AppError.Network."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Circuit Breaker Status Cards
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("circuit_breaker_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status dos Circuit Breakers (Instâncias Reais)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CircuitRow(name = "Google Gemini", state = geminiCircuitState)
                    Spacer(modifier = Modifier.height(6.dp))
                    CircuitRow(name = "OpenAI", state = openAiCircuitState)
                }
            }
        }

        // Diagnostics & Simulation Actions (strictly DEBUG only per Item 8)
        if (BuildConfig.DEBUG) {
            item {
                Text(
                    text = "Simulações de Resiliência (Apenas Build Debug)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                // 1. Retry with Backoff & Jitter (Executes REAL RetryPolicy production code)
                Button(
                    onClick = {
                        scope.launch {
                            HubLogger.log(
                                level = LogLevel.INFO,
                                category = LogCategory.APP,
                                message = "Iniciando teste de RetryPolicy com backoff exponencial e jitter..."
                            )
                            val result = app.globalRetryPolicy.execute(
                                isIdempotent = true,
                                correlationId = "TEST_RETRY_${System.currentTimeMillis()}"
                            ) { attempt ->
                                if (attempt < 3) {
                                    throw AppError.Network(
                                        code = "ERR_SIMULATED_FAIL",
                                        technicalCause = "Falha simulada na tentativa $attempt"
                                    )
                                }
                                "Sucesso obtido na tentativa $attempt!"
                            }

                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("RetryPolicy real: ${result.getOrNull()} (3 tentativas)")
                            } else {
                                snackbarHostState.showSnackbar("Falha no retry: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_test_retry")
                ) {
                    Text(stringResource(R.string.btn_test_retry))
                }
            }

            item {
                // 2. Quota with Retry-After header (Executes REAL RetryPolicy code respecting Quota.retryAfterMs)
                Button(
                    onClick = {
                        scope.launch {
                            HubLogger.log(
                                level = LogLevel.INFO,
                                category = LogCategory.PROVIDER,
                                message = "Simulando Quota 429 com cabeçalho Retry-After: 2 segundos..."
                            )
                            val result = app.globalRetryPolicy.execute(
                                isIdempotent = true,
                                correlationId = "TEST_QUOTA_${System.currentTimeMillis()}"
                            ) { attempt ->
                                if (attempt == 1) {
                                    throw AppError.Quota(
                                        code = "ERR_SIMULATED_429",
                                        technicalCause = "Simulação HTTP 429 Rate Limit",
                                        retryAfterMs = 2000L
                                    )
                                }
                                "Recuperado com sucesso após respeitar o Retry-After!"
                            }
                            snackbarHostState.showSnackbar(result.getOrDefault("Falha"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_test_quota")
                ) {
                    Text(stringResource(R.string.btn_test_quota))
                }
            }

            item {
                // 3. Trip / Reset Circuit Breaker (Executes REAL CircuitBreaker state transitions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                app.geminiCircuitBreaker.tripForTesting()
                                snackbarHostState.showSnackbar("Circuit Breaker de Gemini ABERTO (Instável).")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_trip_circuit"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Abrir Circuito")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                app.geminiCircuitBreaker.reset()
                                snackbarHostState.showSnackbar("Circuit Breaker de Gemini restabelecido.")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_reset_circuit")
                    ) {
                        Text("Restabelecer")
                    }
                }
            }

            item {
                // 4. Test Watchdog (Executes REAL Watchdog.runWithWatchdog)
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                snackbarHostState.showSnackbar("Iniciando tarefa com watchdog real de 2s...")
                                Watchdog.runWithWatchdog(
                                    timeoutMs = 2000L,
                                    operationName = "Conexão de Teste"
                                ) {
                                    // Simulate stuck task beyond timeout
                                    delay(5000L)
                                    "Nunca deve chegar aqui"
                                }
                            } catch (e: AppError.Timeout) {
                                snackbarHostState.showSnackbar("Watchdog funcionou com sucesso: ${e.userMessage}")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_test_watchdog")
                ) {
                    Text(stringResource(R.string.btn_test_watchdog))
                }
            }

            item {
                // 5. Test Database Corruption Recovery (Executes REAL DatabaseManager.recoverFromCorruption)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            app.databaseManager.recoverFromCorruption(
                                Exception("Simulação de SQLiteDatabaseCorruptException para validação de recuperação")
                            )
                            snackbarHostState.showSnackbar("Recuperação disparada! Backup de segurança criado.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_test_db_corrupt")
                ) {
                    Text(stringResource(R.string.btn_test_db_corrupt))
                }
            }

            item {
                // 6. Simulate Unexpected Crash Flag (Writes REAL crash report file for AbnormalShutdownManager)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val crashFile = File(context.filesDir, CrashHandler.CRASH_FILE_NAME)
                            FileOutputStream(crashFile).use {
                                it.write("=== HUB SIMULATED CRASH ===\njava.lang.RuntimeException: Falha simulada para teste de encerramento anormal\n".toByteArray())
                            }
                            snackbarHostState.showSnackbar("Relatório de crash simulado gravado. Ao reiniciar o app, o aviso será apresentado.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_simulate_crash"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_simulate_crash))
                }
            }
        }
    }
}

@Composable
private fun CircuitRow(name: String, state: CircuitBreakerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, color, text) = when (state) {
                is CircuitBreakerState.Closed -> Triple(
                    Icons.Default.HourglassEmpty,
                    MaterialTheme.colorScheme.outline,
                    "Fechado (sem chamadas ainda)"
                )
                is CircuitBreakerState.Open -> Triple(
                    Icons.Default.Warning,
                    MaterialTheme.colorScheme.error,
                    "Aberto (Instável)"
                )
                is CircuitBreakerState.HalfOpen -> Triple(
                    Icons.Default.Info,
                    MaterialTheme.colorScheme.tertiary,
                    "Meio-aberto (Sonda)"
                )
            }
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
