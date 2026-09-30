package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * HubIcons provides a modern, elevated set of iconography components for the
 * entire application with custom badge frames, radiant background tints, and
 * theme-aware highlights.
 */
object HubIcons {
    val Chat: ImageVector = Icons.AutoMirrored.Filled.Chat
    val Dashboard: ImageVector = Icons.Default.Dashboard
    val Customize: ImageVector = Icons.Default.Tune
    val Logs: ImageVector = Icons.AutoMirrored.Filled.ListAlt

    val Live: ImageVector = Icons.Default.GraphicEq
    val Brain: ImageVector = Icons.Default.Psychology
    val Memory: ImageVector = Icons.Default.Storage
    val Voice: ImageVector = Icons.Default.RecordVoiceOver
    val Vision: ImageVector = Icons.Default.Visibility
    val Tools: ImageVector = Icons.Default.Build
    val Mcp: ImageVector = Icons.Default.Hub
    val Payload: ImageVector = Icons.Default.Code
    val KeyVault: ImageVector = Icons.Default.VpnKey
    val Security: ImageVector = Icons.Default.Shield
    val Sparkles: ImageVector = Icons.Default.AutoAwesome
    val Send: ImageVector = Icons.AutoMirrored.Filled.Send
    val Stop: ImageVector = Icons.Default.Stop
    val Attach: ImageVector = Icons.Default.AttachFile
    val Photo: ImageVector = Icons.Default.AddPhotoAlternate
    val Mic: ImageVector = Icons.Default.Mic
    val Settings: ImageVector = Icons.Default.Settings
    val History: ImageVector = Icons.Default.History
    val ChevronRight: ImageVector = Icons.Default.ChevronRight
    val Copy: ImageVector = Icons.Default.ContentCopy
    val Refresh: ImageVector = Icons.Default.Refresh
    val Check: ImageVector = Icons.Default.Check
}

/**
 * HubIconBadge wraps any icon inside a glowing, rounded squircle badge with subtle
 * gradients, border highlight, and depth elevation.
 */
@Composable
fun HubIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    iconTint: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    shapeRadius: Dp = 14.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, RoundedCornerShape(shapeRadius))
            .clip(RoundedCornerShape(shapeRadius))
            .background(
                Brush.linearGradient(
                    listOf(
                        containerColor,
                        containerColor.copy(alpha = 0.35f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(shapeRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Pulsing live status dot with animated expansion ring.
 */
@Composable
fun PulsingLiveDot(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size * 1.8f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * scale)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}
