package com.example.ui.dashboard

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

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
            .padding(16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Painel de Controle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Visão geral dos módulos de inteligência, contexto, sentidos e ferramentas do Assistente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Cérebro
        item {
            DashboardModuleCard(
                icon = Icons.Default.Psychology,
                title = stringResource(R.string.card_brain_title),
                description = stringResource(R.string.card_brain_desc),
                onClick = onNavigateToBrain
            )
        }

        // 2. Memória
        item {
            DashboardModuleCard(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.card_memory_title),
                description = stringResource(R.string.card_memory_desc),
                onClick = onNavigateToMemory
            )
        }

        // 3. Voz & Áudio
        item {
            DashboardModuleCard(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.card_voice_title),
                description = stringResource(R.string.card_voice_desc),
                onClick = onNavigateToVoice
            )
        }

        // 4. Visão & Câmera
        item {
            DashboardModuleCard(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.card_vision_title),
                description = stringResource(R.string.card_vision_desc),
                onClick = onNavigateToVision
            )
        }

        // 5. Ferramentas
        item {
            DashboardModuleCard(
                icon = Icons.Default.Build,
                title = stringResource(R.string.card_tools_title),
                description = stringResource(R.string.card_tools_desc),
                onClick = onNavigateToTools
            )
        }

        // 6. MCP Exa & GitHub
        item {
            DashboardModuleCard(
                icon = Icons.Default.Hub,
                title = "MCP Exa & GitHub",
                description = "Configuração de chaves, testes de conexão e política exclusiva de busca Exa Search.",
                onClick = onNavigateToMcp
            )
        }

        // 7. Inspetor de Payload Bruto
        item {
            DashboardModuleCard(
                icon = Icons.Default.Code,
                title = "Inspetor de Payload Bruto",
                description = "Visualização e edição completa de requisições e respostas HTTP JSON brutas.",
                onClick = onNavigateToPayloadInspector
            )
        }

        // 6. Diagnóstico & Cofre
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToDiagnostic() }
                    .testTag("card_dashboard_diagnostic")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_diagnostic_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardModuleCard(
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
            .testTag("card_module_${title.lowercase().replace(" ", "_")}")
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
