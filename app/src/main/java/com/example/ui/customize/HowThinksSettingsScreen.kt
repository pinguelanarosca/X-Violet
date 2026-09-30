package com.example.ui.customize

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.HubApplication
import com.example.models.ReasoningLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowThinksSettingsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = app.settingsRepository

    var reasoningValue by remember { mutableIntStateOf(50) }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topP by remember { mutableFloatStateOf(0.95f) }
    var selfCorrectionEnabled by remember { mutableStateOf(true) }
    var fallbackChainEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        reasoningValue = settingsRepo.getSetting("default_reasoning_value", "50").toIntOrNull() ?: 50
        temperature = settingsRepo.getSetting("default_temperature", "0.7").toFloatOrNull() ?: 0.7f
        topP = settingsRepo.getSetting("default_top_p", "0.95").toFloatOrNull() ?: 0.95f
        selfCorrectionEnabled = settingsRepo.getSetting("self_correction_enabled", "true").toBoolean()
        fallbackChainEnabled = settingsRepo.getSetting("fallback_chain_enabled", "true").toBoolean()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações: Como Pensa") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_how_thinks")) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Cognição, Raciocínio e Parâmetros Técnicos Padrão",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Nível de Raciocínio (5 Níveis)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val level = ReasoningLevel.fromValue(reasoningValue)
                    Text("Nível de Raciocínio: ${level.displayName} ($reasoningValue / 100)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = when(level) {
                            ReasoningLevel.INSTANTANEO -> "Respostas imediatas sem esforço de raciocínio profundo."
                            ReasoningLevel.LEVE -> "Verificação leve e direta."
                            ReasoningLevel.EQUILIBRADO -> "Equilíbrio ideal entre velocidade e profundidade analítica."
                            ReasoningLevel.PROFUNDO -> "Análise detalhada com passos intermediários."
                            ReasoningLevel.EXTREMO -> "Raciocínio máximo com revisão e auto-correção rigorosa."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = reasoningValue.toFloat(),
                        onValueChange = { reasoningValue = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("slider_reasoning_level")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0 (Instantâneo)", style = MaterialTheme.typography.labelSmall)
                        Text("50 (Equilibrado)", style = MaterialTheme.typography.labelSmall)
                        Text("100 (Extremo)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Temperatura
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Temperatura (Criatividade / Aleatoriedade)", fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", temperature), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..2.0f,
                    modifier = Modifier.testTag("slider_temperature")
                )
            }

            // Top P
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Top P (Nucleus Sampling)", fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.2f", topP), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.testTag("slider_top_p")
                )
            }

            HorizontalDivider()

            // Auto-correção e Verificação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Etapa de Auto-Correção e Verificação", fontWeight = FontWeight.SemiBold)
                    Text("Analisa e revisa a própria resposta antes de exibi-la quando o raciocínio nativo não estiver disponível", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = selfCorrectionEnabled,
                    onCheckedChange = { selfCorrectionEnabled = it },
                    modifier = Modifier.testTag("switch_self_correction")
                )
            }

            // Cadeia de Fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cadeia de Fallback Automático", fontWeight = FontWeight.SemiBold)
                    Text("Alterna para provedor secundário em caso de falha de rede ou quota esgotada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = fallbackChainEnabled,
                    onCheckedChange = { fallbackChainEnabled = it },
                    modifier = Modifier.testTag("switch_fallback_chain")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.setSetting("default_reasoning_value", reasoningValue.toString())
                        settingsRepo.setSetting("default_temperature", temperature.toString())
                        settingsRepo.setSetting("default_top_p", topP.toString())
                        settingsRepo.setSetting("self_correction_enabled", selfCorrectionEnabled.toString())
                        settingsRepo.setSetting("fallback_chain_enabled", fallbackChainEnabled.toString())
                        Toast.makeText(context, "Configurações cognitivas salvas com sucesso!", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_how_thinks")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Configurações de Pensamento")
            }
        }
    }
}
