package com.example.ui.live

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import com.example.live.LiveSessionManager

/**
 * Barra flutuante persistente estilo Gemini Live Dynamic Island / Mini-Player.
 * Permite que a animação de fala e o controle da chamada continuem visíveis e ativos
 * enquanto o usuário navega pelo restante do aplicativo (Chat, Painel, Personalização, Logs, etc).
 */
@Composable
fun LiveFloatingBar(
    app: HubApplication,
    onExpandLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveManager = app.liveSessionManager
    val sessionState by liveManager.sessionState.collectAsState()

    val isConnected = sessionState is LiveSessionManager.SessionState.Connected
    val isConnecting = sessionState is LiveSessionManager.SessionState.Connecting ||
            sessionState is LiveSessionManager.SessionState.Reconnecting

    // Não exibe se inativo ou desconectado
    if (!isConnected && !isConnecting) return

    val isMicMuted by liveManager.isMicMuted.collectAsState()
    val isProactiveMuted by liveManager.isProactiveAudioMuted.collectAsState()
    val isModelThinking by liveManager.isModelThinking.collectAsState()

    val userAmplitude by liveManager.audioEngine.userAmplitude.collectAsState()
    val modelAmplitude by liveManager.audioEngine.modelAmplitude.collectAsState()
    val durationSeconds by liveManager.callDurationSeconds.collectAsState()

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    val geminiCyan = Color(0xFF00E5FF)
    val geminiViolet = Color(0xFF7C4DFF)

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(listOf(geminiCyan, geminiViolet, geminiCyan))
        ),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("floating_live_bar")
    ) {
        Row(
            modifier = Modifier
                .clickable { onExpandLive() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Lado Esquerdo: Mini-Visualizador Pulsante com Onda Orgânica
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                GeminiLiveMiniVisualizer(
                    userAmplitude = userAmplitude,
                    modelAmplitude = modelAmplitude,
                    isThinking = isModelThinking
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFFFC107))
                        )
                        Text(
                            text = if (isConnected) "Gemini Live • $formattedDuration" else "Conectando...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = when {
                            modelAmplitude > 0.05f -> "Gemini falando..."
                            userAmplitude > 0.05f -> "Ouvindo você..."
                            isModelThinking -> "Pensando..."
                            isMicMuted -> "Microfone mutado"
                            else -> "Ao vivo em segundo plano"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Lado Direito: Ações Rápidas de Mudo e Encerrar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Botão de Mute do Microfone
                IconButton(
                    onClick = { liveManager.toggleMicMute() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_floating_mic_mute")
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMicMuted) "Ativar Microfone" else "Silenciar Microfone",
                        tint = if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Botão de Encerrar Chamada
                FilledIconButton(
                    onClick = { liveManager.stopLiveSession() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_floating_end_call")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Encerrar Sessão Live",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
