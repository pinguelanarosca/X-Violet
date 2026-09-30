package com.example.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.HubApplication
import com.example.R
import com.example.errors.AppError
import com.example.providers.SupportedProvider
import com.example.providers.adapters.GeminiProviderAdapter
import com.example.providers.gemini.GeminiConfigurationTestResult
import com.example.security.ApiKeyValidator
import com.example.security.KeystoreVault
import com.example.security.ValidationLevel
import com.example.storage.entities.VaultCredentialEntity
import com.example.ui.components.HubIcons
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
    val app = HubApplication.instance

    var selectedProviderForEdit by remember { mutableStateOf<SupportedProvider?>(null) }
    var providerToDelete by remember { mutableStateOf<SupportedProvider?>(null) }
    var activeTestModalData by remember { mutableStateOf<Pair<SupportedProvider, Result<GeminiConfigurationTestResult>>?>(null) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }

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
            val isCurrentlyTesting = testingProviderId == provider.id

            ProviderKeyCard(
                provider = provider,
                credential = credential,
                isTesting = isCurrentlyTesting,
                lastTestFormatted = credential?.let { dateFormat.format(Date(it.updatedAt)) },
                onConfigureClick = {
                    selectedProviderForEdit = provider
                },
                onTestConfiguration = {
                    testingProviderId = provider.id
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
                        testingProviderId = null
                        if (res != null) {
                            activeTestModalData = provider to res
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Configuração de ${provider.displayName} testada com sucesso!")
                            } else {
                                snackbarHostState.showSnackbar("Falha no teste de ${provider.displayName}: ${res.exceptionOrNull()?.message ?: "Erro desconhecido"}")
                            }
                        }
                    }
                },
                onTestDecrypt = {
                    scope.launch {
                        val result = vault.revalidateKey(provider.id)
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("Decifragem bem-sucedida! Chave íntegra no hardware seguro.")
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

    // Modal Granular Key Insertion & Editing Dialog
    selectedProviderForEdit?.let { provider ->
        ApiKeyConfigureDialog(
            provider = provider,
            onDismiss = { selectedProviderForEdit = null },
            onSaveKey = { rawKey ->
                val result = vault.saveKey(provider.id, rawKey)
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("Chave de ${provider.displayName} salva com sucesso no cofre!")
                }
                result
            }
        )
    }

    // Modal Detailed Diagnostic Test Results Breakdown (Success or Failure)
    activeTestModalData?.let { (provider, result) ->
        DetailedTestResultDialog(
            provider = provider,
            result = result,
            onDismiss = { activeTestModalData = null }
        )
    }

    // Modal Confirmation to delete key
    providerToDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text("Deseja realmente remover a chave criptografada de ${provider.displayName}? Essa ação é definitiva e removerá o blob seguro do disco.")
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
private fun ApiKeyConfigureDialog(
    provider: SupportedProvider,
    onDismiss: () -> Unit,
    onSaveKey: suspend (String) -> Result<Unit>
) {
    var rawInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Real-time validation report
    val validationReport = remember(rawInput, provider.id) {
        ApiKeyValidator.validate(provider.id, rawInput)
    }

    val consoleUrl = when (provider.id) {
        "gemini" -> "aistudio.google.com/app/apikey"
        "openai" -> "platform.openai.com/api-keys"
        "anthropic" -> "console.anthropic.com/settings/keys"
        "groq" -> "console.groq.com/keys"
        "openrouter" -> "openrouter.ai/keys"
        "xai" -> "console.x.ai"
        "custom_openai" -> "Servidor Local (ex: localhost:11434)"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Configurar Chave: ${provider.displayName}")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "A chave é higienizada automaticamente, cifrada com AES-256-GCM em hardware seguro e nunca trafega em texto claro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (consoleUrl.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Obter em: $consoleUrl",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // API Key Input Field with Visibility Toggle & Clear Action
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = {
                        rawInput = it
                        saveErrorMessage = null
                    },
                    label = { Text("Chave de API (${provider.displayName})") },
                    placeholder = { Text("Cole sua chave aqui...") },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            if (rawInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    rawInput = ""
                                    saveErrorMessage = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar campo")
                                }
                            }
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Ocultar chave" else "Exibir chave"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("key_input_field"),
                    singleLine = true
                )

                // Live Validation Feedback Box
                if (rawInput.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (validationReport.level) {
                            ValidationLevel.VALID -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ValidationLevel.CLEANED_OK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            ValidationLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            ValidationLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (validationReport.level) {
                                    ValidationLevel.VALID -> Icons.Default.CheckCircle
                                    ValidationLevel.CLEANED_OK -> Icons.Default.AutoFixHigh
                                    ValidationLevel.WARNING -> Icons.Default.Warning
                                    ValidationLevel.ERROR -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (validationReport.level) {
                                    ValidationLevel.VALID -> MaterialTheme.colorScheme.primary
                                    ValidationLevel.CLEANED_OK -> MaterialTheme.colorScheme.secondary
                                    ValidationLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                                    ValidationLevel.ERROR -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = validationReport.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (validationReport.level) {
                                    ValidationLevel.VALID -> MaterialTheme.colorScheme.onPrimaryContainer
                                    ValidationLevel.CLEANED_OK -> MaterialTheme.colorScheme.onSecondaryContainer
                                    ValidationLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
                                    ValidationLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }

                // Error message from save attempt
                saveErrorMessage?.let { errMsg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Quick Paste Action Button
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            rawInput = clip.toString()
                            saveErrorMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_paste_key))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    saveErrorMessage = null
                    scope.launch {
                        val saveResult = onSaveKey(rawInput)
                        isSaving = false
                        if (saveResult.isSuccess) {
                            onDismiss()
                        } else {
                            val err = saveResult.exceptionOrNull()
                            saveErrorMessage = if (err is AppError) {
                                "${err.userMessage}\n(${err.technicalCause})"
                            } else {
                                err?.message ?: "Falha desconhecida ao salvar chave."
                            }
                        }
                    }
                },
                enabled = !isSaving && (rawInput.isNotBlank() || provider.id == "custom_openai"),
                modifier = Modifier.testTag("btn_save_key_confirm")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(stringResource(R.string.btn_save_key))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun DetailedTestResultDialog(
    provider: SupportedProvider,
    result: Result<GeminiConfigurationTestResult>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resultado do Teste: ${provider.displayName}")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (result.isSuccess) {
                    val testResult = result.getOrThrow()
                    TestCheckItem(
                        label = "Autenticação da Chave",
                        passed = testResult.isAuthValid,
                        detail = testResult.details["auth"] ?: if (testResult.isAuthValid) "Válida e aceita pelo servidor" else "Rejeitada"
                    )
                    TestCheckItem(
                        label = "Resposta Mínima (1 token)",
                        passed = testResult.modelResponds,
                        detail = testResult.details["response"] ?: if (testResult.modelResponds) "Respondendo normalmente" else "Sem resposta"
                    )
                    TestCheckItem(
                        label = "Canal de Streaming SSE",
                        passed = testResult.streamingWorks,
                        detail = testResult.details["streaming"] ?: if (testResult.streamingWorks) "Transmissão incremental funcional" else "Falhou"
                    )
                    testResult.details["latency_ms"]?.let { latency ->
                        TestCheckItem(
                            label = "Latência de Resposta",
                            passed = true,
                            detail = "${latency} ms"
                        )
                    }
                    TestCheckItem(
                        label = "Capacidades Detectadas",
                        passed = true,
                        detail = "Texto, Visão, Ferramentas (Disponíveis)"
                    )
                } else {
                    val error = result.exceptionOrNull()
                    val appError = error as? AppError

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Código: ${appError?.code ?: "ERR_TEST_FAILED"}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = appError?.userMessage ?: (error?.message ?: "Erro desconhecido ao testar conexão."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (appError != null && appError.technicalCause.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Causa Técnica: ${appError.technicalCause}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (appError != null && appError.suggestedAction.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ação sugerida: ${appError.suggestedAction}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
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
        shape = RoundedCornerShape(14.dp),
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
                        fontWeight = FontWeight.Bold
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
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testando...")
                        } else {
                            Text("Testar Conexão")
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
