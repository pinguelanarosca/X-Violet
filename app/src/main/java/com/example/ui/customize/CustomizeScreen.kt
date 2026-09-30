package com.example.ui.customize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ThemeType

@Composable
fun CustomizeScreen(
    currentTheme: ThemeType,
    onThemeChange: (ThemeType) -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToHowSpeaks: () -> Unit,
    onNavigateToHowThinks: () -> Unit,
    onNavigateToWhatRemembers: () -> Unit,
    onNavigateToWhatCanDo: () -> Unit,
    onNavigateToHowConverses: () -> Unit,
    onNavigateToLiveAgents: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themes = ThemeType.values()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("customize_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Personalizar Assistente",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Nova aba de seleção de temas
        item {
            Text("Temas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { theme ->
                    FilterChip(
                        selected = currentTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
        // ... rest of the content

        // 1. Provedores de IA (Accessible from 1A Vault!)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToProviders() }
                    .testTag("card_customize_providers")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_providers_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.card_providers_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Agentes e Vozes do Modo Live (Destaque do Modo Live)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToLiveAgents() }
                    .testTag("card_customize_live_agents")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Agentes & Vozes do Modo Live",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modelos de voz granulares, criação de agentes, voz do narrador, personalidade e controle de payload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // 2. Como fala
        item {
            CustomizeOptionCard(
                icon = Icons.Default.RecordVoiceOver,
                title = stringResource(R.string.card_how_speaks_title),
                description = stringResource(R.string.card_how_speaks_desc),
                onClick = onNavigateToHowSpeaks
            )
        }

        // 3. Como pensa
        item {
            CustomizeOptionCard(
                icon = Icons.Default.Lightbulb,
                title = stringResource(R.string.card_how_thinks_title),
                description = stringResource(R.string.card_how_thinks_desc),
                onClick = onNavigateToHowThinks
            )
        }

        // 4. O que lembra
        item {
            CustomizeOptionCard(
                icon = Icons.Default.AutoStories,
                title = stringResource(R.string.card_what_remembers_title),
                description = stringResource(R.string.card_what_remembers_desc),
                onClick = onNavigateToWhatRemembers
            )
        }

        // 5. O que pode fazer
        item {
            CustomizeOptionCard(
                icon = Icons.Default.Checklist,
                title = stringResource(R.string.card_what_can_do_title),
                description = stringResource(R.string.card_what_can_do_desc),
                onClick = onNavigateToWhatCanDo
            )
        }

        // 6. Como conversa
        item {
            CustomizeOptionCard(
                icon = Icons.Default.ChatBubbleOutline,
                title = stringResource(R.string.card_how_converses_title),
                description = stringResource(R.string.card_how_converses_desc),
                onClick = onNavigateToHowConverses
            )
        }
    }
}

@Composable
private fun CustomizeOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("card_customize_${title.lowercase().replace(" ", "_").replace(":", "")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
