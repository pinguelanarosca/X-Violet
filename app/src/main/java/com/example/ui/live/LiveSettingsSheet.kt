package com.example.ui.live

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import com.example.live.LiveAgent
import com.example.live.LiveSessionManager
import com.example.live.LiveVoiceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSettingsSheet(
    app: HubApplication,
    liveManager: LiveSessionManager,
    onDismiss: () -> Unit,
    onOpenAgentManager: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val liveAgentRepository = app.liveAgentRepository

    val agents by liveAgentRepository.agents.collectAsState()
    val voiceModels by liveAgentRepository.voiceModels.collectAsState()
    val activeAgent by liveAgentRepository.activeAgent.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showVoiceEditorDialog by remember { mutableStateOf(false) }

    // Live parameters
    var modelId by remember { mutableStateOf(liveManager.selectedModelId) }
    var voiceName by remember { mutableStateOf(liveManager.selectedVoice) }
    var systemPrompt by remember { mutableStateOf(liveManager.systemInstructions) }
    var temperature by remember { mutableFloatStateOf(liveManager.temperature) }
    var topP by remember { mutableFloatStateOf(liveManager.topP) }
    var topK by remember { mutableIntStateOf(liveManager.topK) }
    var maxTokens by remember { mutableIntStateOf(liveManager.maxOutputTokens) }
    var presencePenalty by remember { mutableFloatStateOf(liveManager.presencePenalty) }
    var frequencyPenalty by remember { mutableFloatStateOf(liveManager.frequencyPenalty) }
    var exaOnlyPolicy by remember { mutableStateOf(liveManager.exaOnlySearchPolicy) }
    var responseModality by remember { mutableStateOf(liveManager.responseModality) }

    if (showVoiceEditorDialog) {
        LiveVoiceEditorDialog(
            liveAgentRepository = liveAgentRepository,
            onDismiss = { showVoiceEditorDialog = false },
            onVoiceModelSaved = { newVoice ->
                voiceName = newVoice.baseVoice
                liveManager.selectedVoice = newVoice.baseVoice
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Configurações do Modo Live",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Agentes, voz, narrador e payload granular",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onOpenAgentManager != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenAgentManager()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_sheet_open_agent_manager")
                    ) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gerenciar Agentes", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Agentes & Voz") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Payload Granular") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Instruções") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Segundo Plano") })
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> AgentAndVoiceTab(
                        activeAgent = activeAgent,
                        agents = agents,
                        voiceModels = voiceModels,
                        onSelectAgent = { ag ->
                            liveAgentRepository.setActiveAgent(ag)
                            liveManager.applyAgent(ag, voiceModels.find { it.id == ag.voiceModelId })
                            modelId = liveManager.selectedModelId
                            voiceName = liveManager.selectedVoice
                            systemPrompt = liveManager.systemInstructions
                            temperature = liveManager.temperature
                            topP = liveManager.topP
                            topK = liveManager.topK
                            maxTokens = liveManager.maxOutputTokens
                            presencePenalty = liveManager.presencePenalty
                            frequencyPenalty = liveManager.frequencyPenalty
                            responseModality = liveManager.responseModality
                        },
                        selectedVoiceName = voiceName,
                        onSelectVoice = { v ->
                            voiceName = v
                            liveManager.selectedVoice = v
                        },
                        onOpenVoiceGenerator = { showVoiceEditorDialog = true }
                    )
                    1 -> GranularPayloadTab(
                        modelId = modelId,
                        onModelIdChange = { modelId = it; liveManager.selectedModelId = it },
                        temperature = temperature,
                        onTemperatureChange = { temperature = it; liveManager.temperature = it },
                        topP = topP,
                        onTopPChange = { topP = it; liveManager.topP = it },
                        topK = topK,
                        onTopKChange = { topK = it; liveManager.topK = it },
                        maxTokens = maxTokens,
                        onMaxTokensChange = { maxTokens = it; liveManager.maxOutputTokens = it },
                        presencePenalty = presencePenalty,
                        onPresencePenaltyChange = { presencePenalty = it; liveManager.presencePenalty = it },
                        frequencyPenalty = frequencyPenalty,
                        onFrequencyPenaltyChange = { frequencyPenalty = it; liveManager.frequencyPenalty = it },
                        responseModality = responseModality,
                        onModalityChange = { responseModality = it; liveManager.responseModality = it }
                    )
                    2 -> InstructionsAndPolicyTab(
                        systemPrompt = systemPrompt,
                        onSystemPromptChange = { systemPrompt = it; liveManager.systemInstructions = it },
                        exaOnlyPolicy = exaOnlyPolicy,
                        onExaOnlyPolicyChange = { exaOnlyPolicy = it; liveManager.exaOnlySearchPolicy = it }
                    )
                    3 -> BackgroundTab(context = context)
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("btn_close_live_settings")
            ) {
                Text("Confirmar e Fechar")
            }
        }
    }
}

@Composable
private fun AgentAndVoiceTab(
    activeAgent: LiveAgent?,
    agents: List<LiveAgent>,
    voiceModels: List<LiveVoiceModel>,
    onSelectAgent: (LiveAgent) -> Unit,
    selectedVoiceName: String,
    onSelectVoice: (String) -> Unit,
    onOpenVoiceGenerator: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Agente Live Ativo na Sessão", fontWeight = FontWeight.Bold)

        // Seletor de Agentes
        agents.forEach { agent ->
            val isSelected = activeAgent?.id == agent.id
            val parsedColor = runCatching { Color(android.graphics.Color.parseColor(agent.avatarColorHex)) }.getOrDefault(Color(0xFF00E5FF))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAgent(agent) }
                    .testTag("agent_card_${agent.id}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(parsedColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (agent.isNarrator) Icons.Default.AutoStories else Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(agent.name, fontWeight = FontWeight.Bold)
                            if (agent.isNarrator) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("Narrador", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp)
                                }
                            }
                        }
                        Text(agent.roleTag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectAgent(agent) }
                    )
                }
            }
        }

        HorizontalDivider()

        // Seleção de Timbre de Voz
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Voz do Assistente / Narrador", fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onOpenVoiceGenerator,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_sheet_generate_voice")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gerar Modelo de Voz", style = MaterialTheme.typography.labelSmall)
            }
        }

        val baseVoices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            baseVoices.forEach { v ->
                FilterChip(
                    selected = selectedVoiceName == v,
                    onClick = { onSelectVoice(v) },
                    label = { Text(v) }
                )
            }
        }

        if (voiceModels.any { it.isCustom }) {
            Text("Modelos de Voz Personalizados Gerados", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            voiceModels.filter { it.isCustom }.forEach { vm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectVoice(vm.baseVoice) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(vm.name, fontWeight = FontWeight.Bold)
                            Text("Base: ${vm.baseVoice} • Pitch: ${vm.pitch}x • Speed: ${vm.speed}x • ${vm.accentOrStyle}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (selectedVoiceName == vm.baseVoice) {
                            Text("Selecionada", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GranularPayloadTab(
    modelId: String,
    onModelIdChange: (String) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    topP: Float,
    onTopPChange: (Float) -> Unit,
    topK: Int,
    onTopKChange: (Int) -> Unit,
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit,
    presencePenalty: Float,
    onPresencePenaltyChange: (Float) -> Unit,
    frequencyPenalty: Float,
    onFrequencyPenaltyChange: (Float) -> Unit,
    responseModality: String,
    onModalityChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Modelo Gemini Live", fontWeight = FontWeight.SemiBold)
        val models = listOf("gemini-3.8-live", "gemini-2.5-flash-live", "gemini-2.0-flash-exp")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEach { m ->
                FilterChip(
                    selected = modelId == m,
                    onClick = { onModelIdChange(m) },
                    label = { Text(m) }
                )
            }
        }

        Text("Modalidade de Resposta", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = responseModality == "AUDIO",
                onClick = { onModalityChange("AUDIO") },
                label = { Text("Áudio (Voz)") }
            )
            FilterChip(
                selected = responseModality == "TEXT",
                onClick = { onModalityChange("TEXT") },
                label = { Text("Texto Apenas") }
            )
        }

        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Temperatura: ${String.format("%.2f", temperature)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = temperature, onValueChange = onTemperatureChange, valueRange = 0.0f..2.0f)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Top P: ${String.format("%.2f", topP)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = topP, onValueChange = onTopPChange, valueRange = 0.1f..1.0f)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Top K: $topK", fontWeight = FontWeight.Medium)
        }
        Slider(value = topK.toFloat(), onValueChange = { onTopKChange(it.toInt()) }, valueRange = 1f..100f)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tokens Máximos: $maxTokens", fontWeight = FontWeight.Medium)
        }
        Slider(value = maxTokens.toFloat(), onValueChange = { onMaxTokensChange(it.toInt()) }, valueRange = 512f..8192f)

        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Penalidade de Presença: ${String.format("%.2f", presencePenalty)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = presencePenalty, onValueChange = onPresencePenaltyChange, valueRange = -2.0f..2.0f)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Penalidade de Frequência: ${String.format("%.2f", frequencyPenalty)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = frequencyPenalty, onValueChange = onFrequencyPenaltyChange, valueRange = -2.0f..2.0f)
    }
}

@Composable
private fun InstructionsAndPolicyTab(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    exaOnlyPolicy: Boolean,
    onExaOnlyPolicyChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Instruções de Sistema no Modo Live", fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            label = { Text("Instruções do Sistema (System Instructions)") },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            maxLines = 6
        )

        Text("Modelos Pré-definidos", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = { onSystemPromptChange("Você é um assistente de voz conciso e empático. Responda com frases curtas e diretas.") },
                label = { Text("Assistente Pessoal") }
            )
            SuggestionChip(
                onClick = { onSystemPromptChange("Você é um narrador teatral e literário. Entoe com voz rica, pausas dramáticas e cadência cinematográfica.") },
                label = { Text("Narrador") }
            )
        }

        HorizontalDivider()

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Política: Exclusivo Exa Search no Live", fontWeight = FontWeight.Bold)
                    Text("Bloqueia busca do Google no Live e exige o uso exclusivo do Exa MCP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = exaOnlyPolicy, onCheckedChange = onExaOnlyPolicyChange)
            }
        }
    }
}

@Composable
private fun BackgroundTab(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Execução em Segundo Plano com Tela Desligada", fontWeight = FontWeight.Bold)
                Text(
                    "O LiveService mantém um Foreground Service com tipo 'microphone' e um Partial WakeLock ativo. A conversa por áudio continuará operando mesmo se você bloquear a tela ou alternar para outro app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Otimização de Bateria HyperOS / MIUI", fontWeight = FontWeight.Bold)
                Text(
                    "Para garantir que o HyperOS não encerre o processo do microfone com a tela desligada por longos períodos, configure a bateria do aplicativo como 'Sem Restrições'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir Configuração de Bateria")
                }
            }
        }
    }
}
