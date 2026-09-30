package com.example.ui.models

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HubApplication
import com.example.models.*
import com.example.storage.entities.CustomModelEntity
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModelEditorScreen(
    app: HubApplication,
    existingModelId: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.customModelRepository
    val providerRegistry = app.providerRegistry

    var currentTab by remember { mutableIntStateOf(0) }

    // State for Custom Model
    var modelId by remember { mutableStateOf(existingModelId ?: "custom_${UUID.randomUUID()}") }
    var name by remember { mutableStateOf("Meu Modelo Personalizado") }
    var selectedProviderId by remember { mutableStateOf("gemini") }
    var selectedBaseModelId by remember { mutableStateOf("gemini-2.5-flash") }
    var instructions by remember { mutableStateOf("") }

    var traits by remember { mutableStateOf(TraitValues()) }
    var reasoningValue by remember { mutableIntStateOf(50) } // Equilibrado

    // Feature Flags
    var isMemoryEnabled by remember { mutableStateOf(true) }
    var isHistorySaved by remember { mutableStateOf(true) }
    var isToolsEnabled by remember { mutableStateOf(true) }
    var isVisionEnabled by remember { mutableStateOf(true) }
    var isCameraEnabled by remember { mutableStateOf(true) }
    var isScreenEnabled by remember { mutableStateOf(true) }
    var isAudioEnabled by remember { mutableStateOf(true) }
    var isSearchEnabled by remember { mutableStateOf(true) }
    var isAppControlEnabled by remember { mutableStateOf(true) }
    var isAutoExecuteToolsEnabled by remember { mutableStateOf(false) }

    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var isDefault by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }

    // Load existing model if editing
    LaunchedEffect(existingModelId) {
        if (existingModelId != null) {
            val existing = repo.getById(existingModelId)
            if (existing != null) {
                modelId = existing.id
                name = existing.name
                selectedProviderId = existing.providerId
                selectedBaseModelId = existing.baseModelId
                instructions = existing.instructions
                reasoningValue = existing.reasoningLevel
                isFavorite = existing.isFavorite
                isDefault = existing.isDefault

                try {
                    traits = Json.decodeFromString(existing.traitsJson)
                } catch (e: Exception) {
                    // Ignore decode
                }
            }
        }
    }

    // Provider Capabilities for Compiler
    val currentProvider = providerRegistry.getProvider(selectedProviderId)
    val capabilities = currentProvider?.capabilities ?: com.example.providers.model.ProviderCapabilities()

    // Compile Prompt and Traits in real-time
    val compiledConfig = remember(traits, reasoningValue, instructions, selectedProviderId, selectedBaseModelId) {
        TraitTranslator.compile(
            traits = traits,
            reasoningValue = reasoningValue,
            userInstructions = instructions,
            providerId = selectedProviderId,
            baseModelId = selectedBaseModelId,
            capabilities = capabilities
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingModelId != null) "Editar Modelo Personalizado" else "Criar Modelo Personalizado") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isFavorite = !isFavorite
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            PrimaryTabRow(selectedTabIndex = currentTab) {
                Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text("Visão Geral") })
                Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text("Traços & Raciocínio") })
                Tab(selected = currentTab == 2, onClick = { currentTab = 2 }, text = { Text("Capacidades") })
                Tab(selected = currentTab == 3, onClick = { currentTab = 3 }, text = { Text("Avançado") })
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    0 -> OverviewTab(
                        name = name,
                        onNameChange = { name = it; nameError = if (it.isBlank()) "O nome do modelo é obrigatório" else null },
                        nameError = nameError,
                        providerId = selectedProviderId,
                        onProviderChange = { selectedProviderId = it },
                        baseModelId = selectedBaseModelId,
                        onBaseModelChange = { selectedBaseModelId = it },
                        instructions = instructions,
                        onInstructionsChange = { instructions = it },
                        selectedProfileId = selectedProfileId,
                        onSelectProfile = { profile ->
                            selectedProfileId = profile.id
                            traits = profile.traits
                            reasoningValue = profile.reasoningLevelValue
                            isMemoryEnabled = profile.isMemoryEnabled
                            isHistorySaved = profile.isHistorySaved
                            isAppControlEnabled = profile.isAppControlEnabled
                        }
                    )
                    1 -> TraitsAndReasoningTab(
                        traits = traits,
                        onTraitsChange = { traits = it },
                        reasoningValue = reasoningValue,
                        onReasoningChange = { reasoningValue = it },
                        reasoningModeText = compiledConfig.reasoningModeText
                    )
                    2 -> CapabilitiesTab(
                        isMemoryEnabled = isMemoryEnabled, onMemoryChange = { isMemoryEnabled = it },
                        isToolsEnabled = isToolsEnabled, onToolsChange = { isToolsEnabled = it },
                        isVisionEnabled = isVisionEnabled, onVisionChange = { isVisionEnabled = it },
                        isCameraEnabled = isCameraEnabled, onCameraChange = { isCameraEnabled = it },
                        isScreenEnabled = isScreenEnabled, onScreenChange = { isScreenEnabled = it },
                        isAudioEnabled = isAudioEnabled, onAudioChange = { isAudioEnabled = it },
                        isSearchEnabled = isSearchEnabled, onSearchChange = { isSearchEnabled = it },
                        isAppControlEnabled = isAppControlEnabled, onAppControlChange = { isAppControlEnabled = it },
                        isAutoExecuteToolsEnabled = isAutoExecuteToolsEnabled, onAutoExecuteToolsChange = { isAutoExecuteToolsEnabled = it }
                    )
                    3 -> AdvancedTab(
                        compiledConfig = compiledConfig,
                        jsonExport = repo.exportModelToJson(
                            CustomModelEntity(
                                id = modelId,
                                name = name,
                                providerId = selectedProviderId,
                                baseModelId = selectedBaseModelId,
                                instructions = instructions,
                                traitsJson = Json.encodeToString(traits),
                                reasoningLevel = reasoningValue,
                                featureFlagsJson = Json.encodeToString(
                                    mapOf(
                                        "memory" to isMemoryEnabled,
                                        "tools" to isToolsEnabled,
                                        "autoExecuteTools" to isAutoExecuteToolsEnabled
                                    )
                                ),
                                isFavorite = isFavorite,
                                isDefault = isDefault
                            )
                        )
                    )
                }
            }

            // Bottom Action Bar (Save, Test, Duplicate, Delete)
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (existingModelId != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    repo.deleteById(modelId)
                                    Toast.makeText(context, "Modelo excluído", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val copy = repo.duplicateCustomModel(modelId)
                                    if (copy != null) {
                                        Toast.makeText(context, "Modelo duplicado: ${copy.name}", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar")
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "O nome do modelo é obrigatório"
                                currentTab = 0
                                return@Button
                            }

                            val modelEntity = CustomModelEntity(
                                id = modelId,
                                name = name.trim(),
                                providerId = selectedProviderId,
                                baseModelId = selectedBaseModelId,
                                instructions = instructions.trim(),
                                traitsJson = Json.encodeToString(traits),
                                reasoningLevel = reasoningValue,
                                featureFlagsJson = Json.encodeToString(
                                    mapOf(
                                        "memory" to isMemoryEnabled,
                                        "tools" to isToolsEnabled,
                                        "vision" to isVisionEnabled,
                                        "camera" to isCameraEnabled,
                                        "screen" to isScreenEnabled,
                                        "audio" to isAudioEnabled,
                                        "search" to isSearchEnabled,
                                        "appControl" to isAppControlEnabled,
                                        "autoExecuteTools" to isAutoExecuteToolsEnabled
                                    )
                                ),
                                technicalParamsJson = Json.encodeToString(
                                    mapOf(
                                        "temperature" to compiledConfig.temperature,
                                        "topP" to compiledConfig.topP
                                    )
                                ),
                                isFavorite = isFavorite,
                                isDefault = isDefault
                            )

                            scope.launch {
                                repo.saveCustomModel(modelEntity)
                                Toast.makeText(context, "Modelo personalizado salvo!", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_save_custom_model")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Modelo")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTab(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    providerId: String,
    onProviderChange: (String) -> Unit,
    baseModelId: String,
    onBaseModelChange: (String) -> Unit,
    instructions: String,
    onInstructionsChange: (String) -> Unit,
    selectedProfileId: String?,
    onSelectProfile: (ProfilePreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome do Modelo Personalizado") },
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_custom_model_name")
        )

        // Profile Presets Selection
        Text("Perfis Pré-criados", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfilePresets.LIST.take(3).forEach { profile ->
                FilterChip(
                    selected = selectedProfileId == profile.id,
                    onClick = { onSelectProfile(profile) },
                    label = { Text(profile.name) }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfilePresets.LIST.drop(3).forEach { profile ->
                FilterChip(
                    selected = selectedProfileId == profile.id,
                    onClick = { onSelectProfile(profile) },
                    label = { Text(profile.name) }
                )
            }
        }

        // Private Profile Warning Banner
        if (selectedProfileId == "profile_private") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Perfil Privado Ativo",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Aviso: O histórico local e a memória estão desligados no modo Privado, mas a conversa ainda é transmitida aos servidores do provedor selecionado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        OutlinedTextField(
            value = providerId,
            onValueChange = onProviderChange,
            label = { Text("Provedor (gemini, openai, anthropic, groq, openrouter, xai)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = baseModelId,
            onValueChange = onBaseModelChange,
            label = { Text("Modelo Base (ex: gemini-2.5-flash, gpt-4o)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = instructions,
            onValueChange = onInstructionsChange,
            label = { Text("Instruções de Sistema Customizadas") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )
    }
}

@Composable
private fun TraitsAndReasoningTab(
    traits: TraitValues,
    onTraitsChange: (TraitValues) -> Unit,
    reasoningValue: Int,
    onReasoningChange: (Int) -> Unit,
    reasoningModeText: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Presets Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = { onTraitsChange(TraitValues.PRESET_CONSERVADOR) }, modifier = Modifier.weight(1f)) {
                Text("Conservador")
            }
            OutlinedButton(onClick = { onTraitsChange(TraitValues.PRESET_EQUILIBRADO) }, modifier = Modifier.weight(1f)) {
                Text("Equilibrado")
            }
            OutlinedButton(onClick = { onTraitsChange(TraitValues.PRESET_INTENSO) }, modifier = Modifier.weight(1f)) {
                Text("Intenso")
            }
            OutlinedButton(onClick = { onTraitsChange(TraitValues.PRESET_INSANO) }, modifier = Modifier.weight(1f)) {
                Text("Insano")
            }
        }

        // Reasoning Level Selector (5 Níveis)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nível de Raciocínio", fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = reasoningModeText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val level = ReasoningLevel.fromValue(reasoningValue)
                Text("Nível Atual: ${level.displayName} ($reasoningValue / 100)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Slider(
                    value = reasoningValue.toFloat(),
                    onValueChange = { onReasoningChange(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Instantâneo", style = MaterialTheme.typography.labelSmall)
                    Text("Leve", style = MaterialTheme.typography.labelSmall)
                    Text("Equilibrado", style = MaterialTheme.typography.labelSmall)
                    Text("Profundo", style = MaterialTheme.typography.labelSmall)
                    Text("Extremo", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Text("Traços de Personalidade", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        TraitSlider("Humor", "Sério", "Divertido", traits.humor) { onTraitsChange(traits.copy(humor = it)) }
        TraitSlider("Formalidade", "Casual", "Acadêmico", traits.formalidade) { onTraitsChange(traits.copy(formalidade = it)) }
        TraitSlider("Criatividade", "Conservador", "Experimental", traits.criatividade) { onTraitsChange(traits.copy(criatividade = it)) }
        TraitSlider("Objetividade", "Conciso", "Explicativo", traits.objetividade) { onTraitsChange(traits.copy(objetividade = it)) }
        TraitSlider("Iniciativa", "Reativo", "Proativo", traits.iniciativa) { onTraitsChange(traits.copy(iniciativa = it)) }
        TraitSlider("Questionamento", "Aceita", "Desafia", traits.questionamento) { onTraitsChange(traits.copy(questionamento = it)) }
        TraitSlider("Profundidade", "Superficial", "Especialista", traits.profundidade) { onTraitsChange(traits.copy(profundidade = it)) }
        TraitSlider("Personalidade", "Neutro", "Marcante", traits.personalidade) { onTraitsChange(traits.copy(personalidade = it)) }
    }
}

@Composable
private fun TraitSlider(
    name: String,
    leftLabel: String,
    rightLabel: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$name: $value", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("$leftLabel ↔ $rightLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CapabilitiesTab(
    isMemoryEnabled: Boolean, onMemoryChange: (Boolean) -> Unit,
    isToolsEnabled: Boolean, onToolsChange: (Boolean) -> Unit,
    isVisionEnabled: Boolean, onVisionChange: (Boolean) -> Unit,
    isCameraEnabled: Boolean, onCameraChange: (Boolean) -> Unit,
    isScreenEnabled: Boolean, onScreenChange: (Boolean) -> Unit,
    isAudioEnabled: Boolean, onAudioChange: (Boolean) -> Unit,
    isSearchEnabled: Boolean, onSearchChange: (Boolean) -> Unit,
    isAppControlEnabled: Boolean, onAppControlChange: (Boolean) -> Unit,
    isAutoExecuteToolsEnabled: Boolean, onAutoExecuteToolsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Capacidades e Ferramentas Liberadas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        CapabilitySwitch("Memória Persistente", isMemoryEnabled, onMemoryChange)
        CapabilitySwitch("Chamada de Ferramentas (Tools)", isToolsEnabled, onToolsChange)
        CapabilitySwitch("Visão Computacional", isVisionEnabled, onVisionChange)
        CapabilitySwitch("Câmera em Tempo Real", isCameraEnabled, onCameraChange)
        CapabilitySwitch("Compartilhamento de Tela", isScreenEnabled, onScreenChange)
        CapabilitySwitch("Áudio e Voz Bidirecional", isAudioEnabled, onAudioChange)
        CapabilitySwitch("Pesquisa Web", isSearchEnabled, onSearchChange)
        CapabilitySwitch("Controle de Aplicação", isAppControlEnabled, onAppControlChange)
        CapabilitySwitch("Execução Automática de Ferramentas", isAutoExecuteToolsEnabled, onAutoExecuteToolsChange)
    }
}

@Composable
private fun CapabilitySwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdvancedTab(
    compiledConfig: CompiledModelConfig,
    jsonExport: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Parâmetros Técnicos Reais", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Temperatura Clamped: ${compiledConfig.temperature}")
                Text("TopP Clamped: ${compiledConfig.topP}")
                Text("Modo de Raciocínio: ${compiledConfig.reasoningModeText}")
                if (compiledConfig.nativeReasoningEffort != null) {
                    Text("Effort Nativo: ${compiledConfig.nativeReasoningEffort}")
                }
            }
        }

        Text("Prévia do Prompt Compilado", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = compiledConfig.compiledSystemInstructions.ifBlank { "Nenhuma instrução de sistema compilada." },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Text("Exportação em JSON (Sem Chaves)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = jsonExport,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
