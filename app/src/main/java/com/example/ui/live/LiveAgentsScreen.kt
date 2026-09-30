package com.example.ui.live

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import com.example.live.LiveAgent
import com.example.live.LiveVoiceModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAgentsScreen(
    app: HubApplication,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveAgentRepository = app.liveAgentRepository
    val liveSessionManager = app.liveSessionManager

    val agents by liveAgentRepository.agents.collectAsState()
    val voiceModels by liveAgentRepository.voiceModels.collectAsState()
    val activeAgent by liveAgentRepository.activeAgent.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var agentToEdit by remember { mutableStateOf<LiveAgent?>(null) }
    var showAgentEditorDialog by remember { mutableStateOf(false) }

    var voiceModelToEdit by remember { mutableStateOf<LiveVoiceModel?>(null) }
    var showVoiceEditorDialog by remember { mutableStateOf(false) }

    // Dialogs
    if (showAgentEditorDialog) {
        LiveAgentEditorDialog(
            agentToEdit = agentToEdit,
            liveAgentRepository = liveAgentRepository,
            onDismiss = {
                showAgentEditorDialog = false
                agentToEdit = null
            },
            onAgentSaved = { savedAgent ->
                if (activeAgent?.id == savedAgent.id) {
                    liveSessionManager.syncFromActiveAgent()
                }
            }
        )
    }

    if (showVoiceEditorDialog) {
        LiveVoiceEditorDialog(
            voiceModelToEdit = voiceModelToEdit,
            liveAgentRepository = liveAgentRepository,
            onDismiss = {
                showVoiceEditorDialog = false
                voiceModelToEdit = null
            },
            onVoiceModelSaved = {
                liveSessionManager.syncFromActiveAgent()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agentes & Vozes do Modo Live") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_live_agents")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = {
                                agentToEdit = null
                                showAgentEditorDialog = true
                            },
                            modifier = Modifier.testTag("btn_add_live_agent")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Novo Agente")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                voiceModelToEdit = null
                                showVoiceEditorDialog = true
                            },
                            modifier = Modifier.testTag("btn_add_voice_model")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Gerar Modelo de Voz")
                        }
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
        ) {
            // Tab Selector: Agentes (N) vs Modelos de Voz (M)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Agentes Live (${agents.size})") },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Modelos de Voz (${voiceModels.size})") },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // Tab Agentes Live
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Active Agent Card Hero
                    activeAgent?.let { currentActive ->
                        item {
                            ActiveAgentHeroCard(
                                agent = currentActive,
                                voiceModels = voiceModels,
                                onEdit = {
                                    agentToEdit = currentActive
                                    showAgentEditorDialog = true
                                }
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Catálogo de Agentes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = {
                                    agentToEdit = null
                                    showAgentEditorDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Criar Agente", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    items(agents) { agent ->
                        val isActive = activeAgent?.id == agent.id
                        val voiceModel = voiceModels.find { it.id == agent.voiceModelId }
                        LiveAgentCard(
                            agent = agent,
                            voiceModel = voiceModel,
                            isActive = isActive,
                            onActivate = {
                                liveAgentRepository.setActiveAgent(agent)
                                liveSessionManager.syncFromActiveAgent()
                            },
                            onEdit = {
                                agentToEdit = agent
                                showAgentEditorDialog = true
                            },
                            onDelete = {
                                liveAgentRepository.deleteAgent(agent.id)
                            }
                        )
                    }
                }
            } else {
                // Tab Modelos de Voz
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gerador Granular de Modelos de Voz", fontWeight = FontWeight.Bold)
                                    Text("Crie e ajuste modelos vocais personalizados com controle milimétrico de pitch, cadência, energia e ressonância.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modelos Disponíveis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    voiceModelToEdit = null
                                    showVoiceEditorDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gerar Modelo de Voz", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    items(voiceModels) { vm ->
                        LiveVoiceModelCard(
                            voiceModel = vm,
                            onEdit = {
                                voiceModelToEdit = vm
                                showVoiceEditorDialog = true
                            },
                            onDelete = {
                                liveAgentRepository.deleteVoiceModel(vm.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveAgentHeroCard(
    agent: LiveAgent,
    voiceModels: List<LiveVoiceModel>,
    onEdit: () -> Unit
) {
    val voice = voiceModels.find { it.id == agent.voiceModelId }
    val parsedColor = runCatching { Color(android.graphics.Color.parseColor(agent.avatarColorHex)) }.getOrDefault(Color(0xFF00E5FF))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, parsedColor.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Text("ATIVO NO MODO LIVE", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar Agente Ativo", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(parsedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (agent.isNarrator) Icons.Default.AutoStories else Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(agent.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (agent.isNarrator) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Text("Voz Narrador", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(agent.roleTag, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("Voz: ${voice?.name ?: agent.voiceModelId} • Modelo: ${agent.modelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (agent.description.isNotBlank()) {
                Text(agent.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun LiveAgentCard(
    agent: LiveAgent,
    voiceModel: LiveVoiceModel?,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val parsedColor = runCatching { Color(android.graphics.Color.parseColor(agent.avatarColorHex)) }.getOrDefault(Color(0xFF00E5FF))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().testTag("card_agent_${agent.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(parsedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (agent.isNarrator) Icons.Default.AutoStories else Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(agent.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (agent.isNarrator) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("Narrador", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(agent.roleTag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (isActive) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Ativo", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Button(
                        onClick = onActivate,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_activate_agent_${agent.id}")
                    ) {
                        Text("Ativar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (agent.description.isNotBlank()) {
                Text(agent.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Traits Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Voz: ${voiceModel?.name ?: "Puck"}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Temp: ${String.format("%.1f", agent.temperature)}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(agent.responseModality, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "Configurar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceModelCard(
    voiceModel: LiveVoiceModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_voice_model_${voiceModel.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(voiceModel.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (voiceModel.isCustom) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("Gerada", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("Base: ${voiceModel.baseVoice} • Estilo: ${voiceModel.accentOrStyle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (voiceModel.isCustom) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Pitch: ${String.format("%.2f", voiceModel.pitch)}x", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Speed: ${String.format("%.2f", voiceModel.speed)}x", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Calor: ${(voiceModel.warmth * 100).toInt()}%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Energia: ${(voiceModel.energy * 100).toInt()}%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (voiceModel.styleDescription.isNotBlank()) {
                Text(voiceModel.styleDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
