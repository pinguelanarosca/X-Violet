package com.example.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.LocalAppTheme

@Composable
fun LiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val appTheme = LocalAppTheme.current
    appTheme.liveVisualizerContent(
        userAmplitude,
        modelAmplitude,
        isThinking,
        true,
        modifier
    )
}
