package com.example.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.HubIconBadge
import com.example.ui.components.HubIcons
import com.example.ui.components.PulsingLiveDot

@Composable
fun DashboardScreen(
    onNavigateToBrain: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToVision: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToPayloadInspector: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // System Glance & Health Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PulsingLiveDot(
                                dotColor = MaterialTheme.colorScheme.primary,
                                size = 7.dp
                            )
                            Text(
                                text = "CENTRAL DE CONTROLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ONLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Arquitetura Multimodal Unificada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Gerenciamento dinâmico dos 7 subsistemas de raciocínio, memória, sentidos e conectividade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section Title: Módulos Operacionais
        item {
            Text(
                text = "Módulos do Sistema",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        // 1. Cérebro
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Brain,
                title = stringResource(R.string.card_brain_title),
                description = stringResource(R.string.card_brain_desc),
                badgeText = "LLM / MoE",
                onClick = onNavigateToBrain
            )
        }

        // 2. Memória
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Memory,
                title = stringResource(R.string.card_memory_title),
                description = stringResource(R.string.card_memory_desc),
                badgeText = "Room / FTS",
                onClick = onNavigateToMemory
            )
        }

        // 3. Voz & Áudio
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Voice,
                title = stringResource(R.string.card_voice_title),
                description = stringResource(R.string.card_voice_desc),
                badgeText = "16/24 kHz",
                onClick = onNavigateToVoice
            )
        }

        // 4. Visão & Câmera
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Vision,
                title = stringResource(R.string.card_vision_title),
                description = stringResource(R.string.card_vision_desc),
                badgeText = "CameraX",
                onClick = onNavigateToVision
            )
        }

        // 5. Ferramentas
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Tools,
                title = stringResource(R.string.card_tools_title),
                description = stringResource(R.string.card_tools_desc),
                badgeText = "Workflows",
                onClick = onNavigateToTools
            )
        }

        // 6. MCP Exa & GitHub
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Mcp,
                title = "MCP Exa & GitHub",
                description = "Configuração de chaves, testes de conexão e política exclusiva de busca Exa Search.",
                badgeText = "Conectores",
                onClick = onNavigateToMcp
            )
        }

        // 7. Inspetor de Payload Bruto
        item {
            ModernDashboardModuleCard(
                icon = HubIcons.Payload,
                title = "Inspetor de Payload Bruto",
                description = "Visualização e edição completa de requisições e respostas HTTP JSON brutas.",
                badgeText = "JSON / Telemetria",
                onClick = onNavigateToPayloadInspector
            )
        }

        // 8. Diagnóstico do Sistema
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToDiagnostic() }
                    .testTag("card_dashboard_diagnostic")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HubIconBadge(
                        icon = HubIcons.Security,
                        contentDescription = null,
                        size = 46.dp,
                        iconSize = 24.dp,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.card_diagnostic_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.card_diagnostic_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModernDashboardModuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    badgeText: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("card_module_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HubIconBadge(
                icon = icon,
                contentDescription = null,
                size = 44.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
