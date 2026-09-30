package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// 1. NEON CYBER VISUALIZER (Cyberpunk matrix / electric pulse)
// ==========================================
@Composable
fun NeonLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_grid")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 600 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_pulse"
    )
    val gridAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid_angle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(280.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseR = size.minDimension * 0.38f
                val activeAmp = if (modelAmplitude > 0.05f) modelAmplitude else userAmplitude

                // Electric Cyan/Magenta Auras
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPrimary.copy(alpha = 0.45f * pulse), NeonSecondary.copy(alpha = 0.25f), Color.Transparent),
                        center = center,
                        radius = baseR * 1.5f * (1f + activeAmp * 0.4f)
                    ),
                    center = center,
                    radius = baseR * 1.5f * (1f + activeAmp * 0.4f)
                )

                // Laser Concentric Octagonal / Hexagonal Ring Pulse
                val rings = 4
                for (r in 1..rings) {
                    val ringRadius = baseR * (0.6f + r * 0.22f) * (1f + activeAmp * 0.15f)
                    drawCircle(
                        color = if (r % 2 == 0) NeonPrimary.copy(alpha = 0.7f) else NeonSecondary.copy(alpha = 0.6f),
                        center = center,
                        radius = ringRadius,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Core cyber laser dot
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, NeonPrimary, NeonSecondary),
                        center = center,
                        radius = baseR * 0.4f
                    ),
                    center = center,
                    radius = baseR * 0.4f
                )
            }
        }
        ThemedStatusPill(
            label = if (!isConnected) "SISTEMA OFFLINE" else if (modelAmplitude > 0.05f) "SÍNTESE DE VOZ CYBER" else if (userAmplitude > 0.05f) "RECONHECENDO FREQUÊNCIA" else if (isThinking) "PROCESSANDO REDE NEURAL" else "CYBER MATRIZ ONLINE",
            color = NeonPrimary
        )
    }
}

@Composable
fun NeonLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val amp = (userAmplitude + modelAmplitude).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(NeonSurface),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.35f * (1f + amp * 0.3f)
            drawCircle(color = NeonPrimary, center = center, radius = r, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(color = NeonSecondary, center = center, radius = r * 0.5f)
        }
    }
}

// ==========================================
// 2. MIDNIGHT VISUALIZER (Cosmic void / nebula orb)
// ==========================================
@Composable
fun MidnightLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "midnight_void")
    val cosmicOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cosmic_orbit"
    )
    val starlightBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starlight_breath"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(280.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseR = size.minDimension * 0.36f
                val activeAmp = (modelAmplitude + userAmplitude).coerceIn(0f, 1f)

                // Nebula Deep Space Radial Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(MidnightTertiary.copy(alpha = 0.5f), MidnightPrimary.copy(alpha = 0.3f), Color.Transparent),
                        center = center,
                        radius = baseR * 1.6f * starlightBreath
                    ),
                    center = center,
                    radius = baseR * 1.6f * starlightBreath
                )

                // Orbital Stellar Dots
                val stars = 6
                for (s in 0 until stars) {
                    val angle = Math.toRadians((cosmicOrbit + (s * 360.0 / stars)))
                    val orbitDist = baseR * (1.1f + activeAmp * 0.25f)
                    val sx = center.x + orbitDist * cos(angle).toFloat()
                    val sy = center.y + orbitDist * sin(angle).toFloat()
                    drawCircle(color = MidnightPrimary, center = Offset(sx, sy), radius = 3.dp.toPx())
                }

                // Core Cosmic Pearl
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, MidnightPrimary, MidnightBackground),
                        center = center,
                        radius = baseR * 0.75f
                    ),
                    center = center,
                    radius = baseR * 0.75f
                )
            }
        }
        ThemedStatusPill(
            label = if (!isConnected) "VÁCUO SILENCIOSO" else if (modelAmplitude > 0.05f) "TRANSMISSÃO CÓSMICA" else if (userAmplitude > 0.05f) "RECEBENDO SINAL ESTELAR" else if (isThinking) "NÉBULA COMPUTANDO" else "CANAL ESTELAR ABERTO",
            color = MidnightPrimary
        )
    }
}

@Composable
fun MidnightLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MidnightSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.32f
            drawCircle(color = MidnightPrimary.copy(alpha = 0.4f), center = center, radius = r * 1.3f)
            drawCircle(color = MidnightPrimary, center = center, radius = r)
        }
    }
}

// ==========================================
// 3. NATURE VISUALIZER (Botanical / organic ripple)
// ==========================================
@Composable
fun NatureLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nature_breath")
    val leafPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leaf_pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(280.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseR = size.minDimension * 0.35f
                val activeAmp = (modelAmplitude + userAmplitude).coerceIn(0f, 1f)

                // Chlorophyll Ripple Auras
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NaturePrimary.copy(alpha = 0.4f * leafPulse), NatureSecondary.copy(alpha = 0.2f), Color.Transparent),
                        center = center,
                        radius = baseR * 1.5f
                    ),
                    center = center,
                    radius = baseR * 1.5f
                )

                // Botanical Wave Rings
                val rings = 3
                for (w in 1..rings) {
                    val wr = baseR * (0.8f + w * 0.25f + activeAmp * 0.2f)
                    drawCircle(
                        color = NaturePrimary.copy(alpha = 0.5f / w),
                        center = center,
                        radius = wr,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Core Organic Seed
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, NaturePrimary, NatureSecondary),
                        center = center,
                        radius = baseR * 0.65f
                    ),
                    center = center,
                    radius = baseR * 0.65f
                )
            }
        }
        ThemedStatusPill(
            label = if (!isConnected) "NATUREZA EM REPOUSO" else if (modelAmplitude > 0.05f) "VOZ DA FLORESTA" else if (userAmplitude > 0.05f) "OUVINDO RESPIRAÇÃO" else if (isThinking) "BROTANDO RESPOSTA..." else "ECOSSISTEMA VIVO",
            color = NaturePrimary
        )
    }
}

@Composable
fun NatureLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(NaturePrimaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.34f
            drawCircle(color = NaturePrimary, center = center, radius = r)
        }
    }
}

// ==========================================
// 4. MINIMAL VISUALIZER (Monochromatic precision)
// ==========================================
@Composable
fun MinimalLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "minimal_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "min_pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(280.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseR = size.minDimension * 0.35f
                val activeAmp = (modelAmplitude + userAmplitude).coerceIn(0f, 1f)

                // Clean concentric circle
                drawCircle(
                    color = MinimalPrimary.copy(alpha = 0.15f),
                    center = center,
                    radius = baseR * 1.3f * (1f + activeAmp * 0.2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = MinimalPrimary,
                    center = center,
                    radius = baseR * pulse * (1f + activeAmp * 0.1f),
                    style = Stroke(width = 3.dp.toPx())
                )
                // Minimal Center Dot
                drawCircle(
                    color = MinimalPrimary,
                    center = center,
                    radius = 8.dp.toPx()
                )
            }
        }
        ThemedStatusPill(
            label = if (!isConnected) "Desconectado" else if (modelAmplitude > 0.05f) "Falando" else if (userAmplitude > 0.05f) "Ouvindo" else if (isThinking) "Processando" else "Pronto",
            color = MinimalPrimary
        )
    }
}

@Composable
fun MinimalLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MinimalSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = MinimalPrimary, center = center, radius = 5.dp.toPx())
        }
    }
}

@Composable
private fun ThemedStatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
