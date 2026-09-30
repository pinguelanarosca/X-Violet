package com.example.ui.live

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizador oficial imersivo do Gemini Live.
 * Apresenta orbe holográfica orgânica, anéis de deformação harmônica de frequência
 * e auras fluidas com as cores de assinatura Gemini (Cyan, Electric Blue, Violet, Magenta).
 */
@Composable
fun GeminiLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_ambient")

    // Rotação suave contínua da aura de gradiente
    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 2500 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_rotation"
    )

    // Pulsação suave (breathing) do núcleo
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // Paleta de cores signature Steampunk
    val steampunkGold = Color(0xFFFFD700)
    val steampunkBronze = Color(0xFFCD7F32)
    val steampunkCopper = Color(0xFFB87333)
    val steampunkBlack = Color(0xFF121212)
    val steampunkDarkGold = Color(0xFF8B6508)

    val activeState = when {
        !isConnected -> LiveState.DISCONNECTED
        modelAmplitude > 0.05f -> LiveState.MODEL_SPEAKING
        userAmplitude > 0.05f -> LiveState.USER_SPEAKING
        isThinking -> LiveState.THINKING
        else -> LiveState.LISTENING_IDLE
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.28f

                // 1. Aura de Fundo Cósmica Fluida (Steampunk)
                val auraRadius = baseRadius * (if (activeState == LiveState.MODEL_SPEAKING) 2.2f else 1.8f) * breathScale
                val dynamicAuraColors = when (activeState) {
                    LiveState.MODEL_SPEAKING -> listOf(
                        steampunkGold.copy(alpha = 0.5f + (modelAmplitude * 0.4f).coerceAtMost(0.5f)),
                        steampunkBronze.copy(alpha = 0.35f),
                        steampunkCopper.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                    LiveState.USER_SPEAKING -> listOf(
                        steampunkCopper.copy(alpha = 0.6f + (userAmplitude * 0.3f).coerceAtMost(0.4f)),
                        steampunkGold.copy(alpha = 0.35f),
                        steampunkBronze.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                    LiveState.THINKING -> listOf(
                        steampunkBronze.copy(alpha = 0.55f),
                        steampunkGold.copy(alpha = 0.35f),
                        steampunkCopper.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                    LiveState.LISTENING_IDLE -> listOf(
                        steampunkGold.copy(alpha = 0.35f),
                        steampunkBronze.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                    LiveState.DISCONNECTED -> listOf(
                        Color.Gray.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = dynamicAuraColors,
                        center = center,
                        radius = auraRadius
                    ),
                    center = center,
                    radius = auraRadius
                )

                // 2. Ondas Harmônicas de Fala e Frequência do Usuário (Steampunk Ripples)
                if (userAmplitude > 0.05f) {
                    val rippleCount = 3
                    for (i in 1..rippleCount) {
                        val rippleScale = 1.0f + (i * 0.28f) + (userAmplitude * 0.5f)
                        val rippleAlpha = (0.7f - (i * 0.2f)).coerceIn(0.1f, 0.9f)
                        drawCircle(
                            color = steampunkGold.copy(alpha = rippleAlpha),
                            center = center,
                            radius = baseRadius * rippleScale,
                            style = Stroke(width = (3.dp - (i * 0.5f).dp).toPx())
                        )
                    }
                }

                // 3. Ondas Harmônicas da Voz do Modelo (Aurora Waves)
                if (modelAmplitude > 0.05f) {
                    val wavePoints = 48
                    val wavePath = Path()
                    val waveRadius = baseRadius * (1.15f + modelAmplitude * 0.6f)

                    for (j in 0 until wavePoints) {
                        val angle = (j.toFloat() / wavePoints) * 2 * Math.PI.toFloat()
                        val harmonic = sin(angle * 5 + Math.toRadians(auraRotation.toDouble())).toFloat() * (16.dp.toPx() * modelAmplitude)
                        val r = waveRadius + harmonic
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)

                        if (j == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    wavePath.close()

                    drawPath(
                        path = wavePath,
                        brush = Brush.sweepGradient(
                            listOf(steampunkGold, steampunkBronze, steampunkCopper, steampunkGold),
                            center = center
                        ),
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }

                // 4. Anel Orbital Constelação (Thinking / Processamento)
                if (isThinking) {
                    drawOrbitalConstellation(
                        center = center,
                        radius = baseRadius * 1.35f,
                        rotation = auraRotation,
                        color = steampunkGold
                    )
                }

                // 5. Orbe Central Viva do Gemini Live (Steampunk Core)
                val effectiveCoreRadius = baseRadius * breathScale * (1f + (if (activeState == LiveState.MODEL_SPEAKING) modelAmplitude * 0.3f else userAmplitude * 0.2f))
                val coreGradient = Brush.radialGradient(
                    colors = when (activeState) {
                        LiveState.MODEL_SPEAKING -> listOf(Color.White, steampunkGold, steampunkBronze, steampunkBlack)
                        LiveState.USER_SPEAKING -> listOf(Color.White, steampunkGold, steampunkCopper, steampunkBlack)
                        LiveState.THINKING -> listOf(Color.White, steampunkBronze, steampunkCopper, steampunkBlack)
                        LiveState.LISTENING_IDLE -> listOf(Color.White.copy(alpha = 0.9f), steampunkGold, steampunkBlack)
                        LiveState.DISCONNECTED -> listOf(Color.LightGray, Color.DarkGray, Color.Black)
                    },
                    center = center,
                    radius = effectiveCoreRadius
                )


                drawCircle(
                    brush = coreGradient,
                    center = center,
                    radius = effectiveCoreRadius
                )

                // Borda Luminosa Fluida
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(steampunkGold, steampunkBronze, steampunkCopper, steampunkGold),
                        center = center
                    ),
                    center = center,
                    radius = effectiveCoreRadius,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pílula com Estado Dinâmico e Ícone de Atividade
        LiveStatusPill(activeState = activeState)
    }
}

/**
 * Pílula informativa estilizada com status ao vivo.
 */
@Composable
private fun LiveStatusPill(activeState: LiveState) {
    val (label, icon, color) = when (activeState) {
        LiveState.MODEL_SPEAKING -> Triple("Gemini falando...", Icons.Default.VolumeUp, Color(0xFF7C4DFF))
        LiveState.USER_SPEAKING -> Triple("Ouvindo você...", Icons.Default.Mic, Color(0xFF00E5FF))
        LiveState.THINKING -> Triple("Processando raciocínio...", Icons.Default.AutoAwesome, Color(0xFFFF4081))
        LiveState.LISTENING_IDLE -> Triple("Pronto • Pode falar", Icons.Default.GraphicEq, Color(0xFF2979FF))
        LiveState.DISCONNECTED -> Triple("Sessão desconectada", Icons.Default.GraphicEq, Color.Gray)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Mini-Visualizador compacto para a barra persistente flutuante de navegação / segundo plano.
 */
@Composable
fun GeminiLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_gemini_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mini_pulse"
    )

    val steampunkGold = Color(0xFFFFD700)
    val steampunkBronze = Color(0xFFCD7F32)
    val steampunkBlack = Color(0xFF121212)

    val activeColor = when {
        modelAmplitude > 0.05f -> steampunkBronze
        userAmplitude > 0.05f -> steampunkGold
        isThinking -> Color(0xFF8B4513) // SaddleBrown, dark steampunk tone
        else -> steampunkGold.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(steampunkBlack),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.35f
            val dynamicR = baseR * pulse * (1f + (userAmplitude + modelAmplitude) * 0.4f)

            // Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.7f), Color.Transparent),
                    center = center,
                    radius = dynamicR * 1.4f
                ),
                center = center,
                radius = dynamicR * 1.4f
            )

            // Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, activeColor),
                    center = center,
                    radius = dynamicR
                ),
                center = center,
                radius = dynamicR
            )
        }
    }
}

private fun DrawScope.drawOrbitalConstellation(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color
) {
    val nodeCount = 6
    for (i in 0 until nodeCount) {
        val angleDeg = rotation + (i * 360f / nodeCount)
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val nodeX = center.x + radius * cos(angleRad).toFloat()
        val nodeY = center.y + radius * sin(angleRad).toFloat()

        drawCircle(
            color = color.copy(alpha = 0.85f),
            center = Offset(nodeX, nodeY),
            radius = 3.dp.toPx()
        )
    }

    drawCircle(
        color = color.copy(alpha = 0.25f),
        center = center,
        radius = radius,
        style = Stroke(width = 1.dp.toPx())
    )
}

enum class LiveState {
    DISCONNECTED,
    LISTENING_IDLE,
    USER_SPEAKING,
    THINKING,
    MODEL_SPEAKING
}
