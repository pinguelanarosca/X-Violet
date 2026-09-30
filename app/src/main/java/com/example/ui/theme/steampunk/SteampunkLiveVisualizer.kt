package com.example.ui.theme.steampunk

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizador Temático Oficial STEAMPUNK para o Modo Live.
 * Transforma a chamada ao vivo em uma máquina mecânica vitoriana funcional:
 * - Caldeira e fornalha a vapor com pulso térmico;
 * - Engrenagens de bronze e latão engrenadas e rotativas;
 * - Manômetro de pressão (gauge) com ponteiro analógico oscilando com a amplitude real;
 * - Anéis de rebites industriais e anéis de expansão de vapor.
 */
@Composable
fun SteampunkLiveVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "steampunk_engine")

    // Rotação básica do mecanismo mecânico
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    !isConnected -> 60000 // Máquina desligada / quase parada
                    isThinking -> 2200    // Processamento mecânico rápido
                    modelAmplitude > 0.05f -> (1800 - (modelAmplitude * 800).toInt()).coerceAtLeast(800)
                    userAmplitude > 0.05f -> (2500 - (userAmplitude * 1000).toInt()).coerceAtLeast(1000)
                    else -> 6000          // Marcha lenta constante
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "gear_rotation"
    )

    // Pulso térmico da fornalha (breathing da chama)
    val furnaceBreath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 700 else 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "furnace_breath"
    )

    // Pequena vibração do ponteiro analógico
    val needleJitter by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needle_jitter"
    )

    val activeState = when {
        !isConnected -> SteampunkEngineState.DISCONNECTED
        modelAmplitude > 0.05f -> SteampunkEngineState.MODEL_SPEAKING
        userAmplitude > 0.05f -> SteampunkEngineState.USER_SPEAKING
        isThinking -> SteampunkEngineState.THINKING
        else -> SteampunkEngineState.LISTENING_IDLE
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .testTag("steampunk_live_visualizer")
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.46f

                // 1. FUNDO DA FORNALHA (BOILER CHAMBER / STEAM FIREBOX)
                val fireIntensity = when (activeState) {
                    SteampunkEngineState.DISCONNECTED -> 0.15f
                    SteampunkEngineState.LISTENING_IDLE -> 0.45f
                    SteampunkEngineState.USER_SPEAKING -> 0.65f + (userAmplitude * 0.35f).coerceAtMost(0.35f)
                    SteampunkEngineState.THINKING -> 0.85f
                    SteampunkEngineState.MODEL_SPEAKING -> 0.75f + (modelAmplitude * 0.45f).coerceAtMost(0.45f)
                }

                val furnaceColors = listOf(
                    SteampunkFurnaceAmber.copy(alpha = fireIntensity * furnaceBreath),
                    SteampunkBronzeSecondary.copy(alpha = 0.5f * fireIntensity),
                    SteampunkCopperTertiary.copy(alpha = 0.3f),
                    SteampunkBackground
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = furnaceColors,
                        center = center,
                        radius = outerRadius * 1.05f
                    ),
                    center = center,
                    radius = outerRadius
                )

                // 2. DISPERSÃO DE VAPOR EM EXPANSÃO (STEAM PULSES)
                if (activeState == SteampunkEngineState.USER_SPEAKING || activeState == SteampunkEngineState.MODEL_SPEAKING) {
                    val steamAmp = if (activeState == SteampunkEngineState.MODEL_SPEAKING) modelAmplitude else userAmplitude
                    val ringCount = 3
                    for (i in 1..ringCount) {
                        val steamScale = 0.85f + (i * 0.12f) + (steamAmp * 0.18f)
                        val steamAlpha = (0.45f - (i * 0.12f)).coerceIn(0.08f, 0.4f)
                        drawCircle(
                            color = SteampunkSteamWhite.copy(alpha = steamAlpha),
                            center = center,
                            radius = outerRadius * steamScale,
                            style = Stroke(width = (2.5f - (i * 0.5f)).dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // 3. ENGRENAGENS MECÂNICAS ENGRENADAS (INTERLOCKING GEARS)
                // Engrenagem 1: Satélite Noroeste (Bronze, 8 dentes)
                val gear1Center = Offset(center.x - outerRadius * 0.48f, center.y - outerRadius * 0.45f)
                val gear1Radius = outerRadius * 0.38f
                drawSteampunkGear(
                    center = gear1Center,
                    radius = gear1Radius,
                    teeth = 8,
                    rotation = -baseRotation * 1.5f,
                    gearColor = SteampunkBronzeSecondary,
                    rimColor = SteampunkBrassPrimary
                )

                // Engrenagem 2: Satélite Sudeste (Latão, 10 dentes)
                val gear2Center = Offset(center.x + outerRadius * 0.45f, center.y + outerRadius * 0.42f)
                val gear2Radius = outerRadius * 0.42f
                drawSteampunkGear(
                    center = gear2Center,
                    radius = gear2Radius,
                    teeth = 10,
                    rotation = -baseRotation * 1.2f,
                    gearColor = SteampunkBrassPrimary,
                    rimColor = SteampunkCopperTertiary
                )

                // Engrenagem 3: Engrenagem Mestre Central (Cobre envelhecido, 14 dentes)
                drawSteampunkGear(
                    center = center,
                    radius = outerRadius * 0.72f,
                    teeth = 14,
                    rotation = baseRotation,
                    gearColor = SteampunkCopperTertiary,
                    rimColor = SteampunkBrassPrimary
                )

                // 4. MANÔMETRO INDUSTRIAL CENTRAL (VICTORIAN PRESSURE GAUGE)
                val gaugeRadius = outerRadius * 0.54f

                // Borda chanfrada de bronze escuro do manômetro
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SteampunkSurfaceVariant, SteampunkBrassContainer, SteampunkBackground),
                        center = center,
                        radius = gaugeRadius * 1.08f
                    ),
                    center = center,
                    radius = gaugeRadius * 1.08f
                )

                // Mostrador em pergaminho envelhecido (Aged parchment face)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SteampunkGaugeDial, Color(0xFFE2D6C0), Color(0xFFC7B698)),
                        center = center,
                        radius = gaugeRadius
                    ),
                    center = center,
                    radius = gaugeRadius
                )

                // Graduações do manômetro (Tick Marks 0 a 100 PSI)
                val markCount = 12
                val startAngleDeg = 135f
                val sweepAngleDeg = 270f
                for (m in 0 until markCount) {
                    val angleDeg = startAngleDeg + (m.toFloat() / (markCount - 1)) * sweepAngleDeg
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val innerR = gaugeRadius * 0.78f
                    val outerR = gaugeRadius * 0.92f
                    val p1 = Offset(center.x + innerR * cos(rad).toFloat(), center.y + innerR * sin(rad).toFloat())
                    val p2 = Offset(center.x + outerR * cos(rad).toFloat(), center.y + outerR * sin(rad).toFloat())

                    val isRedZone = m >= 9
                    drawPath(
                        path = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                        },
                        color = if (isRedZone) SteampunkError else SteampunkBrassOnPrimary,
                        style = Stroke(width = if (m % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Arco de zona de sobrepressão (Redline Arc)
                drawArc(
                    color = SteampunkError.copy(alpha = 0.8f),
                    startAngle = startAngleDeg + sweepAngleDeg * 0.75f,
                    sweepAngle = sweepAngleDeg * 0.25f,
                    useCenter = false,
                    topLeft = Offset(center.x - gaugeRadius * 0.85f, center.y - gaugeRadius * 0.85f),
                    size = androidx.compose.ui.geometry.Size(gaugeRadius * 1.7f, gaugeRadius * 1.7f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Deflexão do Ponteiro Analógico (Needle deflection baseada em pressão real)
                val targetFraction = when (activeState) {
                    SteampunkEngineState.DISCONNECTED -> 0.0f
                    SteampunkEngineState.LISTENING_IDLE -> 0.22f
                    SteampunkEngineState.USER_SPEAKING -> 0.35f + (userAmplitude * 0.55f).coerceIn(0f, 0.6f)
                    SteampunkEngineState.THINKING -> 0.70f + (furnaceBreath - 1f) * 0.15f
                    SteampunkEngineState.MODEL_SPEAKING -> 0.50f + (modelAmplitude * 0.50f).coerceIn(0f, 0.5f)
                }

                val currentNeedleAngle = startAngleDeg + (targetFraction * sweepAngleDeg) + (if (isConnected) needleJitter else 0f)
                val needleRad = Math.toRadians(currentNeedleAngle.toDouble())
                val needleLength = gaugeRadius * 0.82f
                val tip = Offset(center.x + needleLength * cos(needleRad).toFloat(), center.y + needleLength * sin(needleRad).toFloat())
                val counterTip = Offset(center.x - (gaugeRadius * 0.22f) * cos(needleRad).toFloat(), center.y - (gaugeRadius * 0.22f) * sin(needleRad).toFloat())

                // Desenha o ponteiro estilizado
                val needlePath = Path().apply {
                    moveTo(tip.x, tip.y)
                    val perpRad1 = needleRad + Math.PI / 2
                    val baseW = 4.dp.toPx()
                    lineTo(
                        center.x + baseW * cos(perpRad1).toFloat(),
                        center.y + baseW * sin(perpRad1).toFloat()
                    )
                    lineTo(counterTip.x, counterTip.y)
                    val perpRad2 = needleRad - Math.PI / 2
                    lineTo(
                        center.x + baseW * cos(perpRad2).toFloat(),
                        center.y + baseW * sin(perpRad2).toFloat()
                    )
                    close()
                }
                drawPath(path = needlePath, color = SteampunkGaugeNeedle)

                // Cubo central / Rebite do pivô do ponteiro
                drawCircle(
                    color = SteampunkBrassPrimary,
                    center = center,
                    radius = 7.dp.toPx()
                )
                drawCircle(
                    color = SteampunkBrassOnPrimary,
                    center = center,
                    radius = 3.5.dp.toPx()
                )

                // 5. ANEL EXTERNO INDUSTRIAL COM REBITES DE BRONZE (RIVETED RIM)
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            SteampunkBrassPrimary,
                            SteampunkBronzeSecondary,
                            SteampunkCopperTertiary,
                            SteampunkBrassPrimary
                        ),
                        center = center
                    ),
                    center = center,
                    radius = outerRadius,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Rebites na moldura (12 rebites espaçados em 360 graus)
                val rivetCount = 12
                for (r in 0 until rivetCount) {
                    val rAngle = Math.toRadians((r * 360.0 / rivetCount))
                    val rx = center.x + (outerRadius - 2.dp.toPx()) * cos(rAngle).toFloat()
                    val ry = center.y + (outerRadius - 2.dp.toPx()) * sin(rAngle).toFloat()

                    // Sombra do rebite
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.7f),
                        center = Offset(rx + 1.dp.toPx(), ry + 1.dp.toPx()),
                        radius = 2.8.dp.toPx()
                    )
                    // Corpo de latão do rebite
                    drawCircle(
                        color = SteampunkRivetColor,
                        center = Offset(rx, ry),
                        radius = 2.8.dp.toPx()
                    )
                    // Ponto de luz no topo do rebite
                    drawCircle(
                        color = SteampunkBrassOnContainer,
                        center = Offset(rx - 0.8.dp.toPx(), ry - 0.8.dp.toPx()),
                        radius = 1.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pílula Temática Steampunk de Estado
        SteampunkStatusPill(state = activeState)
    }
}

/**
 * Desenha uma engrenagem com dentes chanfrados e orifícios de alívio no estilo mecânico.
 */
private fun DrawScope.drawSteampunkGear(
    center: Offset,
    radius: Float,
    teeth: Int,
    rotation: Float,
    gearColor: Color,
    rimColor: Color
) {
    val toothDepth = radius * 0.22f
    val innerRadius = radius - toothDepth
    val gearPath = Path()

    val stepAngle = 2 * Math.PI / teeth
    val toothWidthRatio = 0.45

    for (i in 0 until teeth) {
        val startAngle = i * stepAngle + Math.toRadians(rotation.toDouble())
        val p1 = Offset(
            center.x + innerRadius * cos(startAngle).toFloat(),
            center.y + innerRadius * sin(startAngle).toFloat()
        )
        val p2 = Offset(
            center.x + radius * cos(startAngle + stepAngle * 0.15).toFloat(),
            center.y + radius * sin(startAngle + stepAngle * 0.15).toFloat()
        )
        val p3 = Offset(
            center.x + radius * cos(startAngle + stepAngle * (0.15 + toothWidthRatio)).toFloat(),
            center.y + radius * sin(startAngle + stepAngle * (0.15 + toothWidthRatio)).toFloat()
        )
        val p4 = Offset(
            center.x + innerRadius * cos(startAngle + stepAngle * (0.30 + toothWidthRatio)).toFloat(),
            center.y + innerRadius * sin(startAngle + stepAngle * (0.30 + toothWidthRatio)).toFloat()
        )

        if (i == 0) gearPath.moveTo(p1.x, p1.y) else gearPath.lineTo(p1.x, p1.y)
        gearPath.lineTo(p2.x, p2.y)
        gearPath.lineTo(p3.x, p3.y)
        gearPath.lineTo(p4.x, p4.y)
    }
    gearPath.close()

    // Corpo da engrenagem com gradiente metálico
    drawPath(
        path = gearPath,
        brush = Brush.radialGradient(
            colors = listOf(gearColor.copy(alpha = 0.85f), gearColor.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.6f)),
            center = center,
            radius = radius
        )
    )

    // Borda dos dentes
    drawPath(
        path = gearPath,
        color = rimColor.copy(alpha = 0.75f),
        style = Stroke(width = 1.8.dp.toPx())
    )

    // Furos de alívio circulares estilo vitoriano
    val holeRadius = innerRadius * 0.20f
    val holeDistance = innerRadius * 0.55f
    val holeCount = 4
    for (h in 0 until holeCount) {
        val hAngle = Math.toRadians((rotation + h * (360.0 / holeCount)))
        val hx = center.x + holeDistance * cos(hAngle).toFloat()
        val hy = center.y + holeDistance * sin(hAngle).toFloat()
        drawCircle(
            color = SteampunkBackground.copy(alpha = 0.8f),
            center = Offset(hx, hy),
            radius = holeRadius
        )
        drawCircle(
            color = rimColor.copy(alpha = 0.5f),
            center = Offset(hx, hy),
            radius = holeRadius,
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // Cubo do eixo central
    drawCircle(
        color = rimColor,
        center = center,
        radius = innerRadius * 0.22f
    )
    drawCircle(
        color = Color.Black,
        center = center,
        radius = innerRadius * 0.10f
    )
}

/**
 * Pílula informativa estilizada como placa de latão gravada.
 */
@Composable
private fun SteampunkStatusPill(state: SteampunkEngineState) {
    val (label, icon, color) = when (state) {
        SteampunkEngineState.DISCONNECTED -> Triple("MÁQUINA EM REPOUSO", Icons.Default.GraphicEq, SteampunkOutline)
        SteampunkEngineState.LISTENING_IDLE -> Triple("CALDEIRA PRONTA • PODE FALAR", Icons.Default.GraphicEq, SteampunkBrassPrimary)
        SteampunkEngineState.USER_SPEAKING -> Triple("VÁLVULA DE ENTRADA ATIVA", Icons.Default.Mic, SteampunkFurnaceAmber)
        SteampunkEngineState.THINKING -> Triple("ENGRENAGENS EM CÁLCULO...", Icons.Default.AutoAwesome, SteampunkBronzeSecondary)
        SteampunkEngineState.MODEL_SPEAKING -> Triple("TRANSMITINDO VOZ • FORNALHA VIVA", Icons.Default.VolumeUp, SteampunkCopperTertiary)
    }

    Surface(
        color = SteampunkSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, color.copy(alpha = 0.7f)),
        modifier = Modifier.padding(horizontal = 8.dp)
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
                color = SteampunkOnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Mini-Visualizador compacto do Steampunk para a barra flutuante (LiveFloatingBar).
 */
@Composable
fun SteampunkLiveMiniVisualizer(
    userAmplitude: Float,
    modelAmplitude: Float,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "steampunk_mini_engine")
    val miniRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 1400 else 4000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_gear_rot"
    )

    val activeAmp = (userAmplitude + modelAmplitude).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SteampunkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.42f

            // Halo âmbar
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SteampunkFurnaceAmber.copy(alpha = 0.4f + activeAmp * 0.5f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseR * 1.3f
                ),
                center = center,
                radius = baseR * 1.3f
            )

            // Mini Engrenagem rotativa
            drawSteampunkGear(
                center = center,
                radius = baseR * (1f + activeAmp * 0.15f),
                teeth = 6,
                rotation = miniRotation,
                gearColor = SteampunkBronzeSecondary,
                rimColor = SteampunkBrassPrimary
            )

            // Núcleo de fogo central
            drawCircle(
                color = if (activeAmp > 0.05f) SteampunkFurnaceAmber else SteampunkBrassPrimary,
                center = center,
                radius = baseR * 0.28f
            )
        }
    }
}

enum class SteampunkEngineState {
    DISCONNECTED,
    LISTENING_IDLE,
    USER_SPEAKING,
    THINKING,
    MODEL_SPEAKING
}
