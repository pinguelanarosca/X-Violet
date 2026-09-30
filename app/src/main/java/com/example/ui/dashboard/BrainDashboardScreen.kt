package com.example.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.HubApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainDashboardScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val providerRegistry = app.providerRegistry
    val activeModelId by providerRegistry.activeModelId.collectAsState()

    val geminiState by app.geminiCircuitBreaker.state.collectAsState()
    val openAiState by app.openAiCircuitBreaker.state.collectAsState()
    val anthropicState by app.anthropicCircuitBreaker.state.collectAsState()
    val groqState by app.groqCircuitBreaker.state.collectAsState()
    val openRouterState by app.openRouterCircuitBreaker.state.collectAsState()
    val xAiState by app.xAiCircuitBreaker.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel: Cérebro & Inferência") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_brain_dash")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Modelo Ativo Atual", style = MaterialTheme.typography.bodySmall)
                        Text(activeModelId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Status dos Circuit Breakers por Provedor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            ProviderCircuitCard("Gemini API", geminiState.toString())
            ProviderCircuitCard("OpenAI API", openAiState.toString())
            ProviderCircuitCard("Anthropic Claude", anthropicState.toString())
            ProviderCircuitCard("Groq", groqState.toString())
            ProviderCircuitCard("OpenRouter", openRouterState.toString())
            ProviderCircuitCard("xAI Grok", xAiState.toString())

            HorizontalDivider()

            Text("Métricas de Uso de Tokens (Sessão Atual)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricRow("Tokens de Entrada (Prompt)", "1,245")
                    MetricRow("Tokens de Saída (Completion)", "840")
                    MetricRow("Tokens de Raciocínio (Thinking)", "320")
                    MetricRow("Custo Estimado", "$0.0042 USD")
                }
            }
        }
    }
}

@Composable
private fun ProviderCircuitCard(providerName: String, stateText: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(providerName, fontWeight = FontWeight.SemiBold)
            Surface(
                color = if (stateText.contains("Fechado")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
