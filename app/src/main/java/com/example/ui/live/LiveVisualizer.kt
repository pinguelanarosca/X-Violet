package com.example.ui.live

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val thinkingColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 4f

            // Dynamic User Amplitude Expansion
            val userRadius = baseRadius + (userAmplitude * 60.dp.toPx())
            // Dynamic Model Amplitude Expansion
            val modelRadius = baseRadius + (modelAmplitude * 80.dp.toPx())

            val effectiveRadius = if (modelAmplitude > 0.05f) {
                modelRadius
            } else if (userAmplitude > 0.05f) {
                userRadius
            } else if (isThinking) {
                baseRadius * pulseScale
            } else {
                baseRadius
            }

            val activeColor = when {
                modelAmplitude > 0.05f -> secondaryColor
                userAmplitude > 0.05f -> primaryColor
                isThinking -> thinkingColor
                else -> primaryColor.copy(alpha = 0.5f)
            }

            // Glowing outer ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.6f),
                        activeColor.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = effectiveRadius * 1.5f
                ),
                center = center,
                radius = effectiveRadius * 1.5f
            )

            // Inner Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor,
                        activeColor.copy(alpha = 0.7f)
                    ),
                    center = center,
                    radius = effectiveRadius
                ),
                center = center,
                radius = effectiveRadius
            )

            // Concentric Stroke Wave
            if (userAmplitude > 0.1f || modelAmplitude > 0.1f || isThinking) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.8f),
                    center = center,
                    radius = effectiveRadius * 1.25f,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}
