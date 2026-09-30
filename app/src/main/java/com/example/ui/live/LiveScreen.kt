package com.example.ui.live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import com.example.live.LiveSessionManager
import com.example.vision.ScreenCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    app: HubApplication = HubApplication.get(),
    onNavigateBack: () -> Unit = {},
    onOpenLiveAgents: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val liveManager = app.liveSessionManager
    val cameraManager = app.cameraVisionManager
    val audioRoutingManager = app.audioRoutingManager
    val liveAgentRepository = app.liveAgentRepository

    val activeAgent by liveAgentRepository.activeAgent.collectAsState()

    val sessionState by liveManager.sessionState.collectAsState()
    val isAudioActive by liveManager.isAudioActive.collectAsState()
    val isMicMuted by liveManager.isMicMuted.collectAsState()
    val isCameraActive by liveManager.isCameraActive.collectAsState()
    val isScreenShareActive by liveManager.isScreenShareActive.collectAsState()

    val isModelThinking by liveManager.isModelThinking.collectAsState()
    val isProactiveMuted by liveManager.isProactiveAudioMuted.collectAsState()

    val userAmplitude by liveManager.audioEngine.userAmplitude.collectAsState()
    val modelAmplitude by liveManager.audioEngine.modelAmplitude.collectAsState()

    val userTranscript by liveManager.userTranscript.collectAsState()
    val modelTranscript by liveManager.modelTranscript.collectAsState()
    val durationSeconds by liveManager.callDurationSeconds.collectAsState()
    val liveMessages by liveManager.liveMessages.collectAsState()

    val appTheme = com.example.ui.theme.LocalAppTheme.current

    val activeAudioDevice by audioRoutingManager.activeCommunicationDevice.collectAsState()
    val availableDevices by audioRoutingManager.availableDevices.collectAsState()

    var showScreenShareWarningModal by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChatDrawer by remember { mutableStateOf(false) }
    var showAudioRouteMenu by remember { mutableStateOf(false) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val isConnected = sessionState is LiveSessionManager.SessionState.Connected
    val isConnectingOrReconnecting = sessionState is LiveSessionManager.SessionState.Connecting ||
            sessionState is LiveSessionManager.SessionState.Reconnecting

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    // Screen Capture MediaProjection Launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.startCapture(context, result.resultCode, result.data!!)
        } else {
            liveManager.setScreenShareActive(false)
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedPermanently = false
            liveManager.startLiveSession()
        } else {
            permissionDeniedPermanently = true
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            liveManager.setCameraActive(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showSettingsSheet = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val activeColor = runCatching {
                                Color(android.graphics.Color.parseColor(activeAgent?.avatarColorHex ?: "#00E5FF"))
                            }.getOrDefault(MaterialTheme.colorScheme.primary)

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(activeColor)
                            )

                            Text(
                                text = activeAgent?.name ?: "Gemini Live",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (activeAgent?.isNarrator == true) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Narrador",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isConnected) {
                                Surface(
                                    color = appTheme.visualStyle.liveStateColors.listening.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(appTheme.visualStyle.liveStateColors.listening)
                                        )
                                        Text(
                                            text = formattedDuration,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = appTheme.visualStyle.liveStateColors.listening
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "${activeAgent?.roleTag ?: liveManager.selectedModelId} • Voz ${liveManager.selectedVoice}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_live_minimize")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Minimizar Modo Live"
                        )
                    }
                },
                actions = {
                    // Menu de Agentes do Live
                    IconButton(
                        onClick = { onOpenLiveAgents?.invoke() ?: run { showSettingsSheet = true } },
                        modifier = Modifier.testTag("btn_live_agents_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Agentes do Modo Live",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Menu de Roteamento de Áudio
                    Box {
                        IconButton(
                            onClick = { showAudioRouteMenu = true },
                            modifier = Modifier.testTag("btn_audio_route_menu")
                        ) {
                            Icon(
                                imageVector = if (activeAudioDevice?.name?.contains("Bluetooth", ignoreCase = true) == true)
                                    Icons.Default.BluetoothAudio else Icons.Default.Hearing,
                                contentDescription = "Rotas de Áudio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showAudioRouteMenu,
                            onDismissRequest = { showAudioRouteMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Padrão do Sistema / Alto-falante") },
                                onClick = {
                                    audioRoutingManager.clearCommunicationDevice()
                                    showAudioRouteMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                            )
                            availableDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text(dev.name) },
                                    onClick = {
                                        audioRoutingManager.selectCommunicationDevice(dev)
                                        showAudioRouteMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (dev.name.contains("Bluetooth", ignoreCase = true)) Icons.Default.Bluetooth else Icons.Default.Headphones,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Botão de Configurações Live
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("btn_live_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configurações Live",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Dock de Controle Estilo Gemini Live
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Mudo Microfone
                    FilledTonalIconButton(
                        onClick = { liveManager.toggleMicMute() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isMicMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("btn_toggle_mic_mute")
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Desmutar Microfone" else "Mutar Microfone",
                            tint = if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botão Câmera
                    FilledTonalIconButton(
                        onClick = {
                            if (isCameraActive) {
                                liveManager.setCameraActive(false)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isCameraActive) appTheme.visualStyle.liveStateColors.listening.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("btn_toggle_camera")
                    ) {
                        Icon(
                            imageVector = if (isCameraActive) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Câmera",
                            tint = if (isCameraActive) appTheme.visualStyle.liveStateColors.listening else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botão Central de Chamada (Iniciar / Encerrar)
                    Button(
                        onClick = {
                            if (isConnected || isConnectingOrReconnecting) {
                                liveManager.stopLiveSession()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected || isConnectingOrReconnecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("btn_start_stop_live")
                    ) {
                        Icon(
                            imageVector = if (isConnected || isConnectingOrReconnecting) Icons.Default.CallEnd else Icons.Default.GraphicEq,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected || isConnectingOrReconnecting) "Encerrar" else "Conectar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Botão Compartilhamento de Tela
                    FilledTonalIconButton(
                        onClick = {
                            if (isScreenShareActive) {
                                ScreenCaptureService.stopCapture(context)
                            } else {
                                showScreenShareWarningModal = true
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isScreenShareActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("btn_toggle_screen_share")
                    ) {
                        Icon(
                            imageVector = if (isScreenShareActive) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                            contentDescription = "Compartilhar Tela",
                            tint = if (isScreenShareActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botão Abrir Chat In-Call
                    BadgedBox(
                        badge = {
                            if (liveMessages.isNotEmpty()) {
                                Badge { Text("${liveMessages.size}") }
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = { showChatDrawer = true },
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("btn_toggle_live_chat")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Abrir Chat da Chamada",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Feed da Câmera PiP Flutuante
            if (isCameraActive) {
                LiveCameraPiP(
                    cameraManager = cameraManager,
                    onCloseCamera = { liveManager.setCameraActive(false) },
                    onFlipCamera = {
                        val activity = context as? androidx.lifecycle.LifecycleOwner
                        if (activity != null) {
                            cameraManager.toggleCamera(activity, null)
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Card Ativo de Compartilhamento de Tela
            if (isScreenShareActive) {
                LiveScreenShareCard(
                    onStopScreenShare = { ScreenCaptureService.stopCapture(context) }
                )
            }

            // Visualizador Temático do Modo Live
            appTheme.liveVisualizerContent(
                userAmplitude,
                modelAmplitude,
                isModelThinking,
                isConnected,
                Modifier.padding(vertical = 12.dp)
            )

            // Ticker de Legendas em Tempo Real
            LiveSubtitleTicker(
                userTranscript = userTranscript,
                modelTranscript = modelTranscript,
                isThinking = isModelThinking
            )

            // Banner Informativo: Operação com Tela Desligada & Segundo Plano
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_live_background_info")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneLocked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Segundo Plano & Tela Desligada Ativo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A conversa por voz continua mesmo bloqueando a tela ou usando outros apps.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showSettingsSheet = true }) {
                        Text("Opções", fontSize = 11.sp)
                    }
                }
            }

            // Erro de Falha de Conexão
            if (sessionState is LiveSessionManager.SessionState.Failed) {
                val err = (sessionState as LiveSessionManager.SessionState.Failed).error
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Erro: ${err.userMessage}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Sugestão: ${err.suggestedAction}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }

    // Modal de Aviso de Privacidade do Compartilhamento de Tela
    if (showScreenShareWarningModal) {
        AlertDialog(
            onDismissRequest = { showScreenShareWarningModal = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Aviso de Privacidade - Tela") },
            text = {
                Text(
                    "Qualquer senha, mensagem privada ou conteúdo exibido na tela será transmitido em tempo real ao modelo Gemini Live.\n\nVocê pode interromper a qualquer momento."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showScreenShareWarningModal = false
                        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                    }
                ) {
                    Text("Iniciar Compartilhamento")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScreenShareWarningModal = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Sheet de Configurações Granulares do Modo Live
    if (showSettingsSheet) {
        LiveSettingsSheet(
            app = app,
            liveManager = liveManager,
            onDismiss = { showSettingsSheet = false },
            onOpenAgentManager = onOpenLiveAgents
        )
    }

    // Drawer / Sheet de Chat da Chamada Ao Vivo
    if (showChatDrawer) {
        LiveChatDrawer(
            messages = liveMessages,
            onSendMessage = { text -> liveManager.sendTextMessage(text) },
            onDismiss = { showChatDrawer = false }
        )
    }
}
