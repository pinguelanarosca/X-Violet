package com.example.ui.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.HubApplication
import com.example.live.LiveSessionManager
import kotlinx.coroutines.delay

@Composable
fun BackgroundDiagnosticTab(
    app: HubApplication,
    modifier: Modifier = Modifier
) {
    val liveManager = app.liveSessionManager
    val audioRouting = app.audioRoutingManager
    val batteryManager = app.hyperOsBatteryManager

    val sessionState by liveManager.sessionState.collectAsState()
    val availableDevices by audioRouting.availableDevices.collectAsState()
    val activeDevice by audioRouting.activeCommunicationDevice.collectAsState()
    val routingLog by audioRouting.lastRoutingEventMessage.collectAsState()

    var showHyperOsModal by remember { mutableStateOf(false) }
    var isIgnoringBattery by remember { mutableStateOf(batteryManager.isIgnoringBatteryOptimizations()) }

    // Teste Guiado de Segundo Plano
    var isTestRunning by remember { mutableStateOf(false) }
    var backgroundTestSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isTestRunning) {
        if (isTestRunning) {
            while (isTestRunning) {
                delay(1000L)
                backgroundTestSeconds++
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Foreground Service & WakeLock Status
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Serviço em Primeiro Plano (LiveService)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Estado do Serviço:")
                    Text(
                        text = if (sessionState is LiveSessionManager.SessionState.Connected) "Ativo (Microphone FGS)" else "Inativo",
                        fontWeight = FontWeight.Bold,
                        color = if (sessionState is LiveSessionManager.SessionState.Connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Partial WakeLock:")
                    Text(
                        text = if (sessionState is LiveSessionManager.SessionState.Connected) "Adquirido (30m Timeout)" else "Liberado",
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MediaSession (LockScreen):")
                    Text(
                        text = if (sessionState is LiveSessionManager.SessionState.Connected) "Ativa" else "Inativa",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Card 2: HyperOS & Battery Optimization
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Otimização de Bateria e HyperOS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Isenção de Bateria do Android:")
                    Text(
                        text = if (isIgnoringBattery) "Isento (Sem restrições)" else "Otimizado pelo sistema",
                        fontWeight = FontWeight.Bold,
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            batteryManager.openBatteryOptimizationSettings()
                            isIgnoringBattery = batteryManager.isIgnoringBatteryOptimizations()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_battery_opt_settings")
                    ) {
                        Text("Configurar Bateria")
                    }

                    Button(
                        onClick = { showHyperOsModal = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_hyperos_instructions")
                    ) {
                        Text("Instruções HyperOS")
                    }
                }
            }
        }

        // Card 3: Audio Routing & Dispositivos Conectados
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Rotas de Áudio e Dispositivos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Text("Dispositivo Ativo: ${activeDevice?.name ?: "Padrão do Sistema (Automático)"}", fontWeight = FontWeight.Bold)

                if (routingLog.isNotBlank()) {
                    Text("Último evento de rota: $routingLog", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Text("Dispositivos de Comunicação Detectados:", style = MaterialTheme.typography.labelMedium)

                availableDevices.forEach { dev ->
                    OutlinedCard(
                        onClick = { audioRouting.selectCommunicationDevice(dev) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dev.name)
                            if (activeDevice?.id == dev.id) {
                                Icon(Icons.Default.Check, contentDescription = "Ativo", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Text(
                    text = "Aviso: Fones Bluetooth em modo de chamada (SCO) utilizam amostragem de áudio reduzida pela HAL do Bluetooth.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Card 4: Teste Guiado de Segundo Plano
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Teste Guiado de Segundo Plano", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Clique abaixo, bloqueie a tela ou troque de aplicativo durante uma chamada Live. O contador medirá o tempo contínuo de áudio sem interrupção.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Tempo em Segundo Plano: ${backgroundTestSeconds}s",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isTestRunning = !isTestRunning
                            if (isTestRunning) backgroundTestSeconds = 0
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTestRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isTestRunning) "Parar Teste" else "Iniciar Teste Guiado")
                    }

                    OutlinedButton(onClick = { backgroundTestSeconds = 0; isTestRunning = false }) {
                        Text("Zerar")
                    }
                }
            }
        }
    }

    // HyperOS Instruction Modal
    if (showHyperOsModal) {
        AlertDialog(
            onDismissRequest = { showHyperOsModal = false },
            title = { Text("Configuração para Xiaomi HyperOS / MIUI") },
            text = { Text(batteryManager.getXiaomiHyperOsInstructions()) },
            confirmButton = {
                TextButton(onClick = { showHyperOsModal = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}
