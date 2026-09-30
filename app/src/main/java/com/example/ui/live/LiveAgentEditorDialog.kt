package com.example.ui.live

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.live.LiveAgent
import com.example.live.LiveAgentPersonality
import com.example.live.LiveAgentRepository
import com.example.live.LiveVoiceModel
import java.util.UUID

@Composable
fun LiveAgentEditorDialog(
    agentToEdit: LiveAgent? = null,
    liveAgentRepository: LiveAgentRepository,
    onDismiss: () -> Unit,
    onAgentSaved: (LiveAgent) -> Unit
) {
    val voiceModels by liveAgentRepository.voiceModels.collectAsState()

    var name by remember { mutableStateOf(agentToEdit?.name ?: "") }
    var roleTag by remember { mutableStateOf(agentToEdit?.roleTag ?: "Assistente de Voz") }
    var description by remember { mutableStateOf(agentToEdit?.description ?: "") }
    var avatarColorHex by remember { mutableStateOf(agentToEdit?.avatarColorHex ?: "#00E5FF") }
    var voiceModelId by remember { mutableStateOf(agentToEdit?.voiceModelId ?: (voiceModels.firstOrNull()?.id ?: "voice_puck")) }
    var isNarrator by remember { mutableStateOf(agentToEdit?.isNarrator ?: false) }
    var modelId by remember { mutableStateOf(agentToEdit?.modelId ?: "gemini-3.8-live") }
    var responseModality by remember { mutableStateOf(agentToEdit?.responseModality ?: "AUDIO") }

    // Personalidade Granular
    var humor by remember { mutableFloatStateOf(agentToEdit?.personality?.humor ?: 0.5f) }
    var formality by remember { mutableFloatStateOf(agentToEdit?.personality?.formality ?: 0.5f) }
    var creativity by remember { mutableFloatStateOf(agentToEdit?.personality?.creativity ?: 0.7f) }
    var reasoningDepth by remember { mutableFloatStateOf(agentToEdit?.personality?.reasoningDepth ?: 0.6f) }
    var empathy by remember { mutableFloatStateOf(agentToEdit?.personality?.empathy ?: 0.8f) }
    var verbosity by remember { mutableFloatStateOf(agentToEdit?.personality?.verbosity ?: 0.4f) }
    var proactivity by remember { mutableFloatStateOf(agentToEdit?.personality?.proactivity ?: 0.7f) }

    // Granular Payload
    var temperature by remember { mutableFloatStateOf(agentToEdit?.temperature ?: 0.7f) }
    var topP by remember { mutableFloatStateOf(agentToEdit?.topP ?: 0.95f) }
    var topK by remember { mutableIntStateOf(agentToEdit?.topK ?: 40) }
    var maxOutputTokens by remember { mutableIntStateOf(agentToEdit?.maxOutputTokens ?: 2048) }
    var presencePenalty by remember { mutableFloatStateOf(agentToEdit?.presencePenalty ?: 0.0f) }
    var frequencyPenalty by remember { mutableFloatStateOf(agentToEdit?.frequencyPenalty ?: 0.0f) }
    var vadSensitivity by remember { mutableFloatStateOf(agentToEdit?.vadSensitivity ?: 0.7f) }
    var interruptionEnabled by remember { mutableStateOf(agentToEdit?.interruptionEnabled ?: true) }

    // System Instructions
    var systemInstructions by remember { mutableStateOf(agentToEdit?.systemInstructions ?: "") }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showVoiceEditorDialog by remember { mutableStateOf(false) }

    if (showVoiceEditorDialog) {
        LiveVoiceEditorDialog(
            liveAgentRepository = liveAgentRepository,
            onDismiss = { showVoiceEditorDialog = false },
            onVoiceModelSaved = { newVoice ->
                voiceModelId = newVoice.id
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(6.dp)
                .testTag("dialog_agent_editor")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val parsedColor = runCatching { Color(android.graphics.Color.parseColor(avatarColorHex)) }.getOrDefault(Color(0xFF00E5FF))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parsedColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isNarrator) Icons.Default.AutoStories else Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (agentToEdit == null) "Novo Agente Live" else "Editar Agente Live",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalidade, voz, narrador e payload granular",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_agent_editor")) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabs: 0: Perfil & Voz, 1: Personalidade, 2: Payload & Modelo, 3: Instruções
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Perfil & Voz") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Personalidade") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Payload") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Instruções") })
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> ProfileAndVoiceTab(
                            name = name,
                            onNameChange = { name = it },
                            roleTag = roleTag,
                            onRoleTagChange = { roleTag = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            avatarColorHex = avatarColorHex,
                            onColorChange = { avatarColorHex = it },
                            voiceModelId = voiceModelId,
                            onVoiceModelChange = { voiceModelId = it },
                            isNarrator = isNarrator,
                            onIsNarratorChange = { isNarrator = it },
                            voiceModels = voiceModels,
                            onOpenVoiceEditor = { showVoiceEditorDialog = true }
                        )
                        1 -> PersonalityTab(
                            humor = humor,
                            onHumorChange = { humor = it },
                            formality = formality,
                            onFormalityChange = { formality = it },
                            creativity = creativity,
                            onCreativityChange = { creativity = it },
                            reasoningDepth = reasoningDepth,
                            onReasoningDepthChange = { reasoningDepth = it },
                            empathy = empathy,
                            onEmpathyChange = { empathy = it },
                            verbosity = verbosity,
                            onVerbosityChange = { verbosity = it },
                            proactivity = proactivity,
                            onProactivityChange = { proactivity = it }
                        )
                        2 -> GranularPayloadTab(
                            modelId = modelId,
                            onModelIdChange = { modelId = it },
                            responseModality = responseModality,
                            onModalityChange = { responseModality = it },
                            temperature = temperature,
                            onTemperatureChange = { temperature = it },
                            topP = topP,
                            onTopPChange = { topP = it },
                            topK = topK,
                            onTopKChange = { topK = it },
                            maxOutputTokens = maxOutputTokens,
                            onMaxTokensChange = { maxOutputTokens = it },
                            presencePenalty = presencePenalty,
                            onPresencePenaltyChange = { presencePenalty = it },
                            frequencyPenalty = frequencyPenalty,
                            onFrequencyPenaltyChange = { frequencyPenalty = it },
                            vadSensitivity = vadSensitivity,
                            onVadChange = { vadSensitivity = it },
                            interruptionEnabled = interruptionEnabled,
                            onInterruptionChange = { interruptionEnabled = it }
                        )
                        3 -> SystemInstructionsTab(
                            systemInstructions = systemInstructions,
                            onInstructionsChange = { systemInstructions = it },
                            onCompileFromPersonality = {
                                val tempAgent = LiveAgent(
                                    id = agentToEdit?.id ?: "temp",
                                    name = name.ifBlank { "Assistente" },
                                    roleTag = roleTag,
                                    description = description,
                                    personality = LiveAgentPersonality(
                                        humor = humor,
                                        formality = formality,
                                        creativity = creativity,
                                        reasoningDepth = reasoningDepth,
                                        empathy = empathy,
                                        verbosity = verbosity,
                                        proactivity = proactivity
                                    ),
                                    isNarrator = isNarrator
                                )
                                val selectedVoice = voiceModels.find { it.id == voiceModelId }
                                systemInstructions = liveAgentRepository.compileSystemPromptForAgent(tempAgent, selectedVoice)
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val savedAgent = LiveAgent(
                                id = agentToEdit?.id ?: "agent_${UUID.randomUUID().toString().take(8)}",
                                name = name.ifBlank { "Novo Agente" },
                                roleTag = roleTag.ifBlank { "Assistente Live" },
                                description = description,
                                avatarColorHex = avatarColorHex,
                                voiceModelId = voiceModelId,
                                modelId = modelId,
                                personality = LiveAgentPersonality(
                                    humor = humor,
                                    formality = formality,
                                    creativity = creativity,
                                    reasoningDepth = reasoningDepth,
                                    empathy = empathy,
                                    verbosity = verbosity,
                                    proactivity = proactivity
                                ),
                                systemInstructions = systemInstructions,
                                temperature = temperature,
                                topP = topP,
                                topK = topK,
                                maxOutputTokens = maxOutputTokens,
                                presencePenalty = presencePenalty,
                                frequencyPenalty = frequencyPenalty,
                                vadSensitivity = vadSensitivity,
                                interruptionEnabled = interruptionEnabled,
                                isNarrator = isNarrator,
                                responseModality = responseModality
                            )
                            liveAgentRepository.saveAgent(savedAgent)
                            onAgentSaved(savedAgent)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_save_agent_dialog")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (agentToEdit == null) "Criar Agente Live" else "Salvar Agente")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAndVoiceTab(
    name: String,
    onNameChange: (String) -> Unit,
    roleTag: String,
    onRoleTagChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    avatarColorHex: String,
    onColorChange: (String) -> Unit,
    voiceModelId: String,
    onVoiceModelChange: (String) -> Unit,
    isNarrator: Boolean,
    onIsNarratorChange: (Boolean) -> Unit,
    voiceModels: List<LiveVoiceModel>,
    onOpenVoiceEditor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome do Agente") },
            placeholder = { Text("Ex: Atlas, Cronos Narrador, Sofia...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_agent_name")
        )

        OutlinedTextField(
            value = roleTag,
            onValueChange = onRoleTagChange,
            label = { Text("Função / Especialidade") },
            placeholder = { Text("Ex: Engenheiro Sênior, Narrador Literário, Tutor...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_agent_role_tag")
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Descrição do Agente") },
            placeholder = { Text("Propósito e escopo de atuação do agente no Modo Live...") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Cor do Holograma / Avatar", fontWeight = FontWeight.SemiBold)
        val colors = listOf("#00E5FF", "#7C4DFF", "#2979FF", "#FF4081", "#FF9100", "#00E676")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { hex ->
                val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Cyan)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable { onColorChange(hex) }
                        .border(
                            width = if (avatarColorHex == hex) 3.dp else 1.dp,
                            color = if (avatarColorHex == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }

        HorizontalDivider()

        // Seleção de Modelo de Voz & Narrador
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Voz do Agente no Modo Live", fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onOpenVoiceEditor,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_open_voice_generator")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gerar Modelo de Voz", style = MaterialTheme.typography.labelSmall)
            }
        }

        voiceModels.forEach { vm ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (voiceModelId == vm.id)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVoiceModelChange(vm.id) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = voiceModelId == vm.id,
                        onClick = { onVoiceModelChange(vm.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(vm.name, fontWeight = FontWeight.Bold)
                            if (vm.isCustom) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Personalizado", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text(
                            text = "Base: ${vm.baseVoice} • Pitch: ${String.format("%.1f", vm.pitch)}x • Vel: ${String.format("%.1f", vm.speed)}x • ${vm.accentOrStyle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Toggle Modo Narrador
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isNarrator) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Voz do Narrador do Modo Live", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Configura o agente para entonação literária imersiva, cadência de audiolivro e pausas teatrais calculadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isNarrator,
                    onCheckedChange = onIsNarratorChange,
                    modifier = Modifier.testTag("switch_is_narrator")
                )
            }
        }
    }
}

@Composable
private fun PersonalityTab(
    humor: Float,
    onHumorChange: (Float) -> Unit,
    formality: Float,
    onFormalityChange: (Float) -> Unit,
    creativity: Float,
    onCreativityChange: (Float) -> Unit,
    reasoningDepth: Float,
    onReasoningDepthChange: (Float) -> Unit,
    empathy: Float,
    onEmpathyChange: (Float) -> Unit,
    verbosity: Float,
    onVerbosityChange: (Float) -> Unit,
    proactivity: Float,
    onProactivityChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Controle Granular da Personalidade", fontWeight = FontWeight.Bold)

        // Humor
        PersonalitySlider(
            title = "Humor & Leveza",
            value = humor,
            onValueChange = onHumorChange,
            leftLabel = "Sério & Austero",
            rightLabel = "Descontraído & Espirituoso"
        )

        // Formalidade
        PersonalitySlider(
            title = "Formalidade da Linguagem",
            value = formality,
            onValueChange = onFormalityChange,
            leftLabel = "Coloquial / Gírias",
            rightLabel = "Culto / Acadêmico"
        )

        // Criatividade
        PersonalitySlider(
            title = "Criatividade & Analogias",
            value = creativity,
            onValueChange = onCreativityChange,
            leftLabel = "Literal / Pragmático",
            rightLabel = "Imaginativo / Poético"
        )

        // Profundidade de Raciocínio
        PersonalitySlider(
            title = "Profundidade de Raciocínio",
            value = reasoningDepth,
            onValueChange = onReasoningDepthChange,
            leftLabel = "Direto ao Ponto",
            rightLabel = "Meticuloso Passo a Passo"
        )

        // Empatia
        PersonalitySlider(
            title = "Empatia Vocal & Acolhimento",
            value = empathy,
            onValueChange = onEmpathyChange,
            leftLabel = "Objetivo / Neutro",
            rightLabel = "Altamente Acolhedor"
        )

        // Concisão / Verbosidade
        PersonalitySlider(
            title = "Extensão das Falas (Verbosidade)",
            value = verbosity,
            onValueChange = onVerbosityChange,
            leftLabel = "Telegráfico / 1-2 Frases",
            rightLabel = "Detalhado / Didático"
        )

        // Proatividade
        PersonalitySlider(
            title = "Proatividade de Diálogo",
            value = proactivity,
            onValueChange = onProactivityChange,
            leftLabel = "Reativo (Só Responde)",
            rightLabel = "Proativo (Faz Perguntas)"
        )
    }
}

@Composable
private fun PersonalitySlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    leftLabel: String,
    rightLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.0f..1.0f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rightLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GranularPayloadTab(
    modelId: String,
    onModelIdChange: (String) -> Unit,
    responseModality: String,
    onModalityChange: (String) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    topP: Float,
    onTopPChange: (Float) -> Unit,
    topK: Int,
    onTopKChange: (Int) -> Unit,
    maxOutputTokens: Int,
    onMaxTokensChange: (Int) -> Unit,
    presencePenalty: Float,
    onPresencePenaltyChange: (Float) -> Unit,
    frequencyPenalty: Float,
    onFrequencyPenaltyChange: (Float) -> Unit,
    vadSensitivity: Float,
    onVadChange: (Float) -> Unit,
    interruptionEnabled: Boolean,
    onInterruptionChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configurações Granulares de Payload Live", fontWeight = FontWeight.Bold)

        // Modelo Live
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

        // Modalidade
        Text("Modalidade de Resposta", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

        // Temperatura
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Temperatura: ${String.format("%.2f", temperature)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = temperature, onValueChange = onTemperatureChange, valueRange = 0.0f..2.0f)

        // Top P
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Top P: ${String.format("%.2f", topP)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = topP, onValueChange = onTopPChange, valueRange = 0.1f..1.0f)

        // Top K
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Top K: $topK", fontWeight = FontWeight.Medium)
        }
        Slider(value = topK.toFloat(), onValueChange = { onTopKChange(it.toInt()) }, valueRange = 1f..100f)

        // Max Output Tokens
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tokens Máximos de Resposta: $maxOutputTokens", fontWeight = FontWeight.Medium)
        }
        Slider(value = maxOutputTokens.toFloat(), onValueChange = { onMaxTokensChange(it.toInt()) }, valueRange = 512f..8192f)

        HorizontalDivider()

        // Penalidades
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Penalidade de Presença: ${String.format("%.2f", presencePenalty)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = presencePenalty, onValueChange = onPresencePenaltyChange, valueRange = -2.0f..2.0f)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Penalidade de Frequência: ${String.format("%.2f", frequencyPenalty)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = frequencyPenalty, onValueChange = onFrequencyPenaltyChange, valueRange = -2.0f..2.0f)

        HorizontalDivider()

        // VAD e Interrupção
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sensibilidade VAD (Detecção de Fala): ${String.format("%.2f", vadSensitivity)}", fontWeight = FontWeight.Medium)
        }
        Slider(value = vadSensitivity, onValueChange = onVadChange, valueRange = 0.1f..1.0f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Interrupção Natural da Fala", fontWeight = FontWeight.SemiBold)
                Text("Permite ao usuário interromper a fala do modelo ao começar a falar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = interruptionEnabled, onCheckedChange = onInterruptionChange)
        }
    }
}

@Composable
private fun SystemInstructionsTab(
    systemInstructions: String,
    onInstructionsChange: (String) -> Unit,
    onCompileFromPersonality: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Instruções de Sistema do Agente", fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onCompileFromPersonality,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_compile_system_prompt")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gerar Diretrizes", style = MaterialTheme.typography.labelSmall)
            }
        }

        OutlinedTextField(
            value = systemInstructions,
            onValueChange = onInstructionsChange,
            label = { Text("System Instructions (Payload Live)") },
            placeholder = { Text("Escreva as regras, comportamento, missão e estilo verbal do agente...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .testTag("input_agent_system_instructions"),
            maxLines = 12
        )

        Text("Modelos Rápidos de Início", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SuggestionChip(
                onClick = {
                    onInstructionsChange("Você é um assistente de voz nativo extremamente empático e solícito. Mantenha respostas curtas e agradáveis.")
                },
                label = { Text("Assistente") }
            )
            SuggestionChip(
                onClick = {
                    onInstructionsChange("Você é um narrador profissional de audiolivros. Crie descrições atmosféricas, suspense controlado e inflexões dramáticas.")
                },
                label = { Text("Narrador") }
            )
            SuggestionChip(
                onClick = {
                    onInstructionsChange("Você é um arquiteto de software sênior. Discuta código com precisão milimétrica, foco em padrões de projeto e performance.")
                },
                label = { Text("Engenheiro") }
            )
        }
    }
}
