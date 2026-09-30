package com.example.ui.customize

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.HubApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatCanDoSettingsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = app.settingsRepository

    var toolsEnabled by remember { mutableStateOf(true) }
    var webSearchEnabled by remember { mutableStateOf(true) }
    var visionEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var screenCaptureEnabled by remember { mutableStateOf(true) }
    var liveAudioEnabled by remember { mutableStateOf(true) }
    var appControlEnabled by remember { mutableStateOf(true) }
    var autoExecuteTools by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        toolsEnabled = settingsRepo.getSetting("cap_tools_enabled", "true").toBoolean()
        webSearchEnabled = settingsRepo.getSetting("cap_web_search", "true").toBoolean()
        visionEnabled = settingsRepo.getSetting("cap_vision", "true").toBoolean()
        cameraEnabled = settingsRepo.getSetting("cap_camera", "true").toBoolean()
        screenCaptureEnabled = settingsRepo.getSetting("cap_screen", "true").toBoolean()
        liveAudioEnabled = settingsRepo.getSetting("cap_live_audio", "true").toBoolean()
        appControlEnabled = settingsRepo.getSetting("cap_app_control", "true").toBoolean()
        autoExecuteTools = settingsRepo.getSetting("cap_auto_execute", "false").toBoolean()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações: O Que Pode Fazer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_what_can_do")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Permissões de Ferramentas, Sensores e Ações Autônomas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            CapabilityOptionToggle(
                title = "Chamada de Ferramentas (Tools)",
                desc = "Permite ao modelo invocar ferramentas para buscar dados e calcular.",
                checked = toolsEnabled,
                testTag = "switch_cap_tools"
            ) { toolsEnabled = it }

            CapabilityOptionToggle(
                title = "Pesquisa na Web",
                desc = "Permite buscar informações em tempo real na internet.",
                checked = webSearchEnabled,
                testTag = "switch_cap_web_search"
            ) { webSearchEnabled = it }

            CapabilityOptionToggle(
                title = "Visão Computacional (Imagens)",
                desc = "Permite analisar fotos e anexos enviados pelo usuário.",
                checked = visionEnabled,
                testTag = "switch_cap_vision"
            ) { visionEnabled = it }

            CapabilityOptionToggle(
                title = "Câmera em Tempo Real (CameraX)",
                desc = "Permite transmitir o feed da câmera para análise multimodal.",
                checked = cameraEnabled,
                testTag = "switch_cap_camera"
            ) { cameraEnabled = it }

            CapabilityOptionToggle(
                title = "Compartilhamento de Tela (MediaProjection)",
                desc = "Permite transmitir captura de tela para assistência em tempo real.",
                checked = screenCaptureEnabled,
                testTag = "switch_cap_screen"
            ) { screenCaptureEnabled = it }

            CapabilityOptionToggle(
                title = "Áudio e Voz Bidirecional (Modo Live)",
                desc = "Permite comunicação por voz em tempo real via WebSocket.",
                checked = liveAudioEnabled,
                testTag = "switch_cap_live_audio"
            ) { liveAudioEnabled = it }

            CapabilityOptionToggle(
                title = "Controle do Aplicativo",
                desc = "Permite executar ações internas de navegação e diagnósticos.",
                checked = appControlEnabled,
                testTag = "switch_cap_app_control"
            ) { appControlEnabled = it }

            HorizontalDivider()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (autoExecuteTools) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Execução Automática de Ferramentas", fontWeight = FontWeight.Bold)
                        Text("Executa ferramentas sem solicitar confirmação prévia do usuário. Requer atenção à segurança.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoExecuteTools,
                        onCheckedChange = { autoExecuteTools = it },
                        modifier = Modifier.testTag("switch_cap_auto_execute")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.setSetting("cap_tools_enabled", toolsEnabled.toString())
                        settingsRepo.setSetting("cap_web_search", webSearchEnabled.toString())
                        settingsRepo.setSetting("cap_vision", visionEnabled.toString())
                        settingsRepo.setSetting("cap_camera", cameraEnabled.toString())
                        settingsRepo.setSetting("cap_screen", screenCaptureEnabled.toString())
                        settingsRepo.setSetting("cap_live_audio", liveAudioEnabled.toString())
                        settingsRepo.setSetting("cap_app_control", appControlEnabled.toString())
                        settingsRepo.setSetting("cap_auto_execute", autoExecuteTools.toString())
                        Toast.makeText(context, "Permissões de ferramentas salvas com sucesso!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_what_can_do")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Permissões")
            }
        }
    }
}

@Composable
private fun CapabilityOptionToggle(
    title: String,
    desc: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
