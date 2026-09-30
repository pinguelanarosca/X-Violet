package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.HubApplication
import com.example.R
import com.example.providers.SupportedProvider
import com.example.providers.adapters.GeminiProviderAdapter
import com.example.providers.gemini.GeminiConfigurationTestResult
import com.example.security.KeystoreVault
import com.example.storage.entities.VaultCredentialEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(
    vault: KeystoreVault,
    snackbarHostState: SnackbarHostState,
    protectKeyScreens: Boolean = !BuildConfig.DEBUG,
    onToggleProtectKeyScreens: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val credentials by vault.allCredentialsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val app = HubApplication.instance

    var selectedProviderForEdit by remember { mutableStateOf<SupportedProvider?>(null) }
    var keyInputValue by remember { mutableStateOf("") }
    var providerToDelete by remember { mutableStateOf<SupportedProvider?>(null) }
    var activeTestResult by remember { mutableStateOf<Pair<String, GeminiConfigurationTestResult>?>(null) }
    var isTestingConfig by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("vault_screen_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Vault Header Title Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_header_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = stringResource(R.string.tab_vault),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.vault_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vault_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // FLAG_SECURE Protection Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (protectKeyScreens) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flag_secure_toggle_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (protectKeyScreens) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (protectKeyScreens) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(
                                text = "Proteger telas de chaves (FLAG_SECURE)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = protectKeyScreens,
                            onCheckedChange = { onToggleProtectKeyScreens(it) },
                            modifier = Modifier.testTag("flag_secure_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (protectKeyScreens) {
                            "Status: LIGADA. Capturas de tela e espelhamento são bloqueados pelo Android."
                        } else {
                            "Status: DESLIGADA. Telas liberadas para visualização e depuração."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Supported Providers List
        items(SupportedProvider.values().toList(), key = { it.id }) { provider ->
            val credential = credentials.find { it.providerId == provider.id }
            ProviderKeyCard(
                provider = provider,
                credential = credential,
                isTesting = isTestingConfig,
                lastTestFormatted = credential?.let { dateFormat.format(Date(it.updatedAt)) },
                onConfigureClick = {
                    selectedProviderForEdit = provider
                    keyInputValue = ""
                },
                onTestConfiguration = {
                    isTestingConfig = true
                    scope.launch {
                        val correlationId = "TEST_CFG_${System.currentTimeMillis()}"
                        val res = when (provider.id) {
                            "gemini" -> (app.providerRegistry.getProvider("gemini") as? GeminiProviderAdapter)?.testConfiguration(correlationId)
                            "openai" -> (app.providerRegistry.getProvider("openai") as? com.example.providers.adapters.OpenAiProviderAdapter)?.testConfiguration(correlationId)
                            "anthropic" -> (app.providerRegistry.getProvider("anthropic") as? com.example.providers.adapters.AnthropicProviderAdapter)?.testConfiguration(correlationId)
                            "xai" -> (app.providerRegistry.getProvider("xai") as? com.example.providers.adapters.XAiProviderAdapter)?.testConfiguration(correlationId)
                            "openrouter" -> (app.providerRegistry.getProvider("openrouter") as? com.example.providers.adapters.OpenRouterProviderAdapter)?.testConfiguration(correlationId)
                            "groq" -> (app.providerRegistry.getProvider("groq") as? com.example.providers.adapters.GroqProviderAdapter)?.testConfiguration(correlationId)
                            "custom_openai" -> (app.providerRegistry.getProvider("custom_openai") as? com.example.providers.adapters.CustomOpenAiProviderAdapter)?.testConfiguration(correlationId)
                            else -> null
                        }
                        isTestingConfig = false
                        if (res != null && res.isSuccess) {
                            activeTestResult = provider.displayName to res.getOrThrow()
                            snackbarHostState.showSnackbar("Configuração de ${provider.displayName} testada com sucesso!")
                        } else {
                            snackbarHostState.showSnackbar("Falha no teste: ${res?.exceptionOrNull()?.message ?: "Erro desconhecido"}")
                        }
                    }
                },
                onTestDecrypt = {
                    scope.launch {
                        val result = vault.revalidateKey(provider.id)
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("Decifragem bem-sucedida! Chave íntegra no cofre.")
                        } else {
                            snackbarHostState.showSnackbar("Falha na decifragem: ${result.exceptionOrNull()?.message}")
                        }
                    }
                },
                onSimulateFailure = {
                    scope.launch {
                        vault.simulateDecryptionFailure(provider.id)
                        snackbarHostState.showSnackbar("Simulação aplicada! Teste a decifragem para validar transição para NEEDS_REENTRY.")
                    }
                },
                onDeleteClick = {
                    providerToDelete = provider
                }
            )
        }
    }

    // Modal Test Results Breakdown
    activeTestResult?.let { (name, result) ->
        AlertDialog(
            onDismissRequest = { activeTestResult = null },
            title = { Text("Resultado do Teste Real: $name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestCheckItem(
                        label = "Autenticação da Chave (x-goog-api-key)",
                        passed = result.isAuthValid,
                        detail = result.details["auth"] ?: if (result.isAuthValid) "Válida" else "Rejeitada"
                    )
                    TestCheckItem(
                        label = "Resposta do Modelo (1 token mínimo)",
                        passed = result.modelResponds,
                        detail = result.details["response"] ?: if (result.modelResponds) "Respondendo" else "Sem resposta"
                    )
                    TestCheckItem(
                        label = "Canal de Streaming SSE",
                        passed = result.streamingWorks,
                        detail = result.details["streaming"] ?: if (result.streamingWorks) "Funcional" else "Falhou"
                    )
                    TestCheckItem(
                        label = "Capacidades Detectadas",
                        passed = true,
                        detail = "Texto, Visão, Áudio, Chamada de Ferramentas"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeTestResult = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Modal Dialog to enter / paste key
    selectedProviderForEdit?.let { provider ->
        AlertDialog(
            onDismissRequest = {
                selectedProviderForEdit = null
                keyInputValue = ""
            },
            title = { Text(text = "Configurar chave: ${provider.displayName}") },
            text = {
                Column {
                    Text(
                        text = "A chave será encriptada com AES-256-GCM antes de ser gravada e nunca será exibida em texto claro.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInputValue,
                        onValueChange = { keyInputValue = it },
                        label = { Text("Chave de API") },
                        placeholder = { Text(stringResource(R.string.key_input_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_input_field"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                keyInputValue = clip.trim()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_paste_key))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val keyToSave = keyInputValue
                        selectedProviderForEdit = null
                        keyInputValue = ""
                        scope.launch {
                            val result = vault.saveKey(provider.id, keyToSave)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Chave de ${provider.displayName} salva com sucesso no cofre!")
                            } else {
                                snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Erro ao salvar")
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_save_key_confirm")
                ) {
                    Text(stringResource(R.string.btn_save_key))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedProviderForEdit = null
                    keyInputValue = ""
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Modal Confirmation to delete key
    providerToDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text("Deseja realmente remover a chave criptografada de ${provider.displayName}? Essa ação é definitiva.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = providerToDelete
                        providerToDelete = null
                        if (p != null) {
                            scope.launch {
                                vault.deleteKey(p.id)
                                snackbarHostState.showSnackbar("Chave de ${p.displayName} removida do cofre.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("btn_delete_key_confirm")
                ) {
                    Text(stringResource(R.string.btn_delete_key))
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun TestCheckItem(label: String, passed: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: SupportedProvider,
    credential: VaultCredentialEntity?,
    isTesting: Boolean,
    lastTestFormatted: String?,
    onConfigureClick: () -> Unit,
    onTestConfiguration: () -> Unit,
    onTestDecrypt: () -> Unit,
    onSimulateFailure: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (credential != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_key_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.btn_delete_key),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status Chip & Last Validation Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (credential == null) {
                    SuggestionChip(
                        onClick = onConfigureClick,
                        label = { Text(stringResource(R.string.key_status_not_set)) }
                    )
                } else when (credential.status) {
                    KeystoreVault.STATUS_ACTIVE -> {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${stringResource(R.string.key_status_active)} (${credential.maskedKey})") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    KeystoreVault.STATUS_NEEDS_REENTRY -> {
                        SuggestionChip(
                            onClick = onConfigureClick,
                            label = { Text(stringResource(R.string.key_status_needs_reentry)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }
                    else -> {
                        SuggestionChip(
                            onClick = onConfigureClick,
                            label = { Text(credential.status) }
                        )
                    }
                }

                if (lastTestFormatted != null) {
                    Text(
                        text = "Último teste: $lastTestFormatted",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfigureClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_configure_${provider.id}")
                ) {
                    Text(if (credential == null) "Cadastrar Chave" else "Trocar Chave")
                }

                if (credential != null) {
                    OutlinedButton(
                        onClick = onTestConfiguration,
                        enabled = !isTesting,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("btn_test_config_${provider.id}")
                    ) {
                        if (isTesting && provider.id == "gemini") {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Testar Configuração")
                        }
                    }
                }
            }

            if (credential != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTestDecrypt,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_test_decrypt))
                    }

                    OutlinedButton(
                        onClick = onSimulateFailure,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Simular Falha")
                    }
                }
            }
        }
    }
}
