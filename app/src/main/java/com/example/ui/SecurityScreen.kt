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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("security_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.security_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nosso compromisso é transparência total: sem falsas promessas de segurança absoluta. Veja abaixo exatamente como suas chaves são protegidas e os limites físicos do ambiente Android.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Interactive Diagnostic using real AndroidKeyStoreCryptoEngine
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keystore_diagnostic_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = "Diagnóstico do Motor Criptográfico Real",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Executa o ciclo completo de cifragem, decifragem, adulteração e troca de AAD utilizando diretamente o AndroidKeyStoreCryptoEngine de produção.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    KeystoreLiveDiagnostic()
                }
            }
        }

        // What is protected
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.security_what_is_protected),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.security_protected_bullets),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
                    )
                }
            }
        }

        // What is NOT protected
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.security_what_is_not_protected),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.security_not_protected_bullets),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Arquitetura 100% Client-Side",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "O Hub comunica diretamente seu dispositivo aos servidores oficiais de cada provedor (Gemini, OpenAI, Anthropic, etc). Não existe nenhum backend intermediário, nenhum serviço do Firebase, telemetria ou proxy coletando seus dados ou chaves.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun KeystoreLiveDiagnostic() {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var diagnosticLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var diagnosticStatus by remember { mutableStateOf<Boolean?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                isRunning = true
                diagnosticLog = listOf("Iniciando testes com AndroidKeyStoreCryptoEngine real...")
                diagnosticStatus = null

                scope.launch(Dispatchers.IO) {
                    val logs = mutableListOf<String>()
                    var success = true
                    try {
                        val engine = com.example.security.AndroidKeyStoreCryptoEngine("HubVaultDiagnosticMasterKey")
                        val testSecret = "DIAG_SECRET_TOKEN_${System.currentTimeMillis()}"
                        val aad = "diagnostic_provider".toByteArray(Charsets.UTF_8)

                        // Step 1: Encrypt
                        logs.add("1. Cifrando segredo de teste com AES-256-GCM...")
                        val encrypted = engine.encrypt(testSecret.toByteArray(Charsets.UTF_8), aad)
                        logs.add("   ✓ Cifrado com sucesso. IV: ${encrypted.iv.size} bytes, CipherText: ${encrypted.cipherText.size} bytes")

                        // Step 2: Decrypt
                        logs.add("2. Decifrando com AAD correto...")
                        val decryptedBytes = engine.decrypt(encrypted.cipherText, encrypted.iv, aad)
                        val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
                        if (decryptedStr == testSecret) {
                            logs.add("   ✓ Decifrado com sucesso. Texto coincide perfeitamente.")
                        } else {
                            logs.add("   ✗ Falha: texto decifrado não confere.")
                            success = false
                        }

                        // Step 3: Tampered blob
                        logs.add("3. Testando detecção de adulteração (Integridade GCM)...")
                        val corruptedCipher = encrypted.cipherText.clone()
                        corruptedCipher[corruptedCipher.size - 1] = (corruptedCipher[corruptedCipher.size - 1].toInt() xor 0xFF).toByte()
                        try {
                            engine.decrypt(corruptedCipher, encrypted.iv, aad)
                            logs.add("   ✗ Falha de segurança: blob adulterado foi aceito!")
                            success = false
                        } catch (t: Throwable) {
                            logs.add("   ✓ Rejeitado corretamente com erro de integridade: ${t.javaClass.simpleName}")
                        }

                        // Step 4: Swapped AAD
                        logs.add("4. Testando rejeição com AAD trocado...")
                        val wrongAad = "different_provider_aad".toByteArray(Charsets.UTF_8)
                        try {
                            engine.decrypt(encrypted.cipherText, encrypted.iv, wrongAad)
                            logs.add("   ✗ Falha de segurança: decifrado com AAD divergente!")
                            success = false
                        } catch (t: Throwable) {
                            logs.add("   ✓ Rejeitado corretamente com erro de autenticação AAD: ${t.javaClass.simpleName}")
                        }

                        diagnosticStatus = success
                    } catch (t: Throwable) {
                        logs.add("✗ Falha geral na execução do motor: ${t.javaClass.simpleName}: ${t.message}")
                        diagnosticStatus = false
                    } finally {
                        diagnosticLog = logs
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_keystore_diagnostic_button")
        ) {
            Text(if (isRunning) "Executando testes no KeyStore..." else "Testar cofre agora (Hardware Real)")
        }

        if (diagnosticLog.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (diagnosticStatus) {
                        true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostic_results_card")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    diagnosticLog.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = when {
                                line.contains("✓") -> MaterialTheme.colorScheme.primary
                                line.contains("✗") -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

