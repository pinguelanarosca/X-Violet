package com.example.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.LocalAppTheme

/**
 * Ponto de entrada do visualizador do Live que delega diretamente para a identidade
 * visual e dinâmica fornecida pelo tema atual (LocalAppTheme).
 */
@Composable
fun GeminiLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val appTheme = LocalAppTheme.current
    appTheme.liveVisualizerContent(
        userAmplitude,
        modelAmplitude,
        isThinking,
        isConnected,
        modifier
    )
}

/**
 * Ponto de entrada do mini-visualizador do Live (para barras flutuantes e mini-players)
 * que delega para a identidade do tema atual.
 */
@Composable
fun GeminiLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val appTheme = LocalAppTheme.current
    appTheme.liveMiniVisualizerContent(
        userAmplitude,
        modelAmplitude,
        isThinking,
        modifier
    )
}

enum class LiveState {
    DISCONNECTED,
    LISTENING_IDLE,
    USER_SPEAKING,
    THINKING,
    MODEL_SPEAKING
}
