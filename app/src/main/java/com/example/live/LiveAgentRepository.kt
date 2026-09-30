package com.example.live

import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LiveAgentRepository(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }

    private val _voiceModels = MutableStateFlow<List<LiveVoiceModel>>(emptyList())
    val voiceModels: StateFlow<List<LiveVoiceModel>> = _voiceModels.asStateFlow()

    private val _agents = MutableStateFlow<List<LiveAgent>>(emptyList())
    val agents: StateFlow<List<LiveAgent>> = _agents.asStateFlow()

    private val _activeAgent = MutableStateFlow<LiveAgent?>(null)
    val activeAgent: StateFlow<LiveAgent?> = _activeAgent.asStateFlow()

    init {
        scope.launch {
            loadVoiceModels()
            loadAgents()
        }
    }

    private suspend fun loadVoiceModels() {
        val storedJson = settingsRepository.getSetting("live_voice_models", "")
        if (storedJson.isNotBlank()) {
            try {
                val list = json.decodeFromString<List<LiveVoiceModel>>(storedJson)
                _voiceModels.value = list
                return
            } catch (e: Exception) {
                HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Falha ao decodificar modelos de voz salvos. Restaurando padrões.")
            }
        }

        // Padrões predefinidos
        val defaults = listOf(
            LiveVoiceModel(id = "voice_puck", name = "Puck (Padrão)", baseVoice = "Puck", pitch = 1.0f, speed = 1.0f, warmth = 0.5f, energy = 0.6f, accentOrStyle = "Neutro"),
            LiveVoiceModel(id = "voice_charon", name = "Charon (Grave & Solene)", baseVoice = "Charon", pitch = 0.85f, speed = 0.95f, warmth = 0.8f, energy = 0.5f, accentOrStyle = "Formal"),
            LiveVoiceModel(id = "voice_kore", name = "Kore (Serena & Suave)", baseVoice = "Kore", pitch = 1.1f, speed = 1.0f, warmth = 0.9f, energy = 0.4f, accentOrStyle = "Empático"),
            LiveVoiceModel(id = "voice_fenrir", name = "Fenrir (Robusto & Firme)", baseVoice = "Fenrir", pitch = 0.8f, speed = 1.05f, warmth = 0.4f, energy = 0.85f, accentOrStyle = "Dinâmico"),
            LiveVoiceModel(id = "voice_aoede", name = "Aoede (Melódica & Clara)", baseVoice = "Aoede", pitch = 1.05f, speed = 1.0f, warmth = 0.7f, energy = 0.6f, accentOrStyle = "Calmo"),
            LiveVoiceModel(id = "voice_narrator_epic", name = "Narrador Épico (Live)", baseVoice = "Charon", pitch = 0.75f, speed = 0.9f, warmth = 0.85f, energy = 0.7f, accentOrStyle = "Narrativo", styleDescription = "Tom cinematográfico e solene para narração imersiva", isCustom = true),
            LiveVoiceModel(id = "voice_aura_empath", name = "Aura Acolhedora (Live)", baseVoice = "Kore", pitch = 1.0f, speed = 0.95f, warmth = 1.0f, energy = 0.45f, accentOrStyle = "Empático", styleDescription = "Tom terapêutico, caloroso e de escuta ativa", isCustom = true),
            LiveVoiceModel(id = "voice_atlas_tech", name = "Atlas Dinâmico (Live)", baseVoice = "Puck", pitch = 0.95f, speed = 1.15f, warmth = 0.4f, energy = 0.95f, accentOrStyle = "Dinâmico", styleDescription = "Dicção rápida, técnica, articulada e confiante", isCustom = true)
        )
        _voiceModels.value = defaults
        saveVoiceModelsInternal(defaults)
    }

    private suspend fun loadAgents() {
        val storedJson = settingsRepository.getSetting("live_agents_list", "")
        if (storedJson.isNotBlank()) {
            try {
                val list = json.decodeFromString<List<LiveAgent>>(storedJson)
                _agents.value = list
                val activeId = settingsRepository.getSetting("active_live_agent_id", list.firstOrNull()?.id ?: "")
                _activeAgent.value = list.find { it.id == activeId } ?: list.firstOrNull()
                return
            } catch (e: Exception) {
                HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Falha ao decodificar agentes Live. Restaurando padrões.")
            }
        }

        val defaults = listOf(
            LiveAgent(
                id = "agent_atlas",
                name = "Atlas",
                roleTag = "Engenheiro Sênior",
                description = "Arquiteto de sistemas, focado em código robusto, arquitetura e lógica técnica.",
                avatarColorHex = "#2979FF",
                voiceModelId = "voice_atlas_tech",
                personality = LiveAgentPersonality(humor = 0.3f, formality = 0.8f, creativity = 0.5f, reasoningDepth = 0.9f, empathy = 0.4f, verbosity = 0.3f, proactivity = 0.8f),
                systemInstructions = "Você é Atlas, arquiteto de software sênior. Responda com precisão técnica incomparável, conciso e orientado a soluções eficientes."
            ),
            LiveAgent(
                id = "agent_cronos",
                name = "Cronos Narrador",
                roleTag = "Narrador Literário",
                description = "Voz cinematográfica e profunda para contar histórias, audiolivros e narrativas dramáticas.",
                avatarColorHex = "#7C4DFF",
                voiceModelId = "voice_narrator_epic",
                isNarrator = true,
                personality = LiveAgentPersonality(humor = 0.4f, formality = 0.7f, creativity = 0.95f, reasoningDepth = 0.7f, empathy = 0.9f, verbosity = 0.75f, proactivity = 0.6f),
                systemInstructions = "Você é Cronos, narrador literário e storyteller. Narre eventos com cadência envolvente, rica em metáforas e presença vocal expressiva."
            ),
            LiveAgent(
                id = "agent_aura",
                name = "Aura",
                roleTag = "Tradutora & Linguista",
                description = "Especialista em tradução simultânea, idiomas e interpretação cultural em tempo real.",
                avatarColorHex = "#00E5FF",
                voiceModelId = "voice_aura_empath",
                personality = LiveAgentPersonality(humor = 0.5f, formality = 0.6f, creativity = 0.6f, reasoningDepth = 0.8f, empathy = 0.9f, verbosity = 0.35f, proactivity = 0.5f),
                systemInstructions = "Você é Aura, tradutora simultânea poliglota. Forneça traduções naturais e nuances idiomáticas perfeitas instantaneamente."
            ),
            LiveAgent(
                id = "agent_nexus",
                name = "Nexus",
                roleTag = "Tutor Socrático",
                description = "Guia de raciocínio profundo que estimula reflexão analítica através do método socrático.",
                avatarColorHex = "#FF4081",
                voiceModelId = "voice_fenrir",
                personality = LiveAgentPersonality(humor = 0.35f, formality = 0.7f, creativity = 0.7f, reasoningDepth = 1.0f, empathy = 0.7f, verbosity = 0.5f, proactivity = 0.9f),
                systemInstructions = "Você é Nexus, tutor socrático. Guie o interlocutor para a verdade questionando premissas e estimulando raciocínio passo a passo."
            ),
            LiveAgent(
                id = "agent_iris",
                name = "Íris",
                roleTag = "Assistente Pessoal",
                description = "Empática, atenta a detalhes da rotina, organizadora de tarefas e companheira diária.",
                avatarColorHex = "#00B0FF",
                voiceModelId = "voice_kore",
                personality = LiveAgentPersonality(humor = 0.7f, formality = 0.4f, creativity = 0.7f, reasoningDepth = 0.6f, empathy = 0.95f, verbosity = 0.4f, proactivity = 0.9f),
                systemInstructions = "Você é Íris, assistente pessoal proativa e carinhosa. Ajude na organização e dialogue de maneira leve, acolhedora e positiva."
            )
        )
        _agents.value = defaults
        _activeAgent.value = defaults.first()
        saveAgentsInternal(defaults)
    }

    fun saveAgent(agent: LiveAgent) {
        scope.launch {
            val current = _agents.value.toMutableList()
            val index = current.indexOfFirst { it.id == agent.id }
            if (index >= 0) {
                current[index] = agent
            } else {
                current.add(agent)
            }
            _agents.value = current
            saveAgentsInternal(current)
            if (_activeAgent.value?.id == agent.id) {
                _activeAgent.value = agent
            }
        }
    }

    fun deleteAgent(agentId: String) {
        scope.launch {
            val updated = _agents.value.filterNot { it.id == agentId }
            _agents.value = updated
            saveAgentsInternal(updated)
            if (_activeAgent.value?.id == agentId) {
                _activeAgent.value = updated.firstOrNull()
            }
        }
    }

    fun setActiveAgent(agent: LiveAgent) {
        _activeAgent.value = agent
        scope.launch {
            settingsRepository.setSetting("active_live_agent_id", agent.id)
            HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Agente Live ativo alterado para '${agent.name}'.")
        }
    }

    fun saveVoiceModel(voiceModel: LiveVoiceModel) {
        scope.launch {
            val current = _voiceModels.value.toMutableList()
            val index = current.indexOfFirst { it.id == voiceModel.id }
            if (index >= 0) {
                current[index] = voiceModel
            } else {
                current.add(voiceModel)
            }
            _voiceModels.value = current
            saveVoiceModelsInternal(current)
        }
    }

    fun deleteVoiceModel(voiceModelId: String) {
        scope.launch {
            val updated = _voiceModels.value.filterNot { it.id == voiceModelId }
            _voiceModels.value = updated
            saveVoiceModelsInternal(updated)
        }
    }

    fun generateVoiceModel(
        name: String,
        baseVoice: String,
        pitch: Float,
        speed: Float,
        warmth: Float,
        energy: Float,
        accentOrStyle: String,
        styleDescription: String
    ): LiveVoiceModel {
        val model = LiveVoiceModel(
            id = "custom_voice_${UUID.randomUUID().toString().take(8)}",
            name = name,
            baseVoice = baseVoice,
            pitch = pitch,
            speed = speed,
            warmth = warmth,
            energy = energy,
            accentOrStyle = accentOrStyle,
            styleDescription = styleDescription,
            isCustom = true
        )
        saveVoiceModel(model)
        return model
    }

    fun compileSystemPromptForAgent(agent: LiveAgent, voiceModel: LiveVoiceModel?): String {
        val p = agent.personality
        val builder = StringBuilder()

        if (agent.systemInstructions.isNotBlank()) {
            builder.appendLine(agent.systemInstructions)
            builder.appendLine()
        }

        builder.appendLine("DIRETRIZES DE PERSONALIDADE E COMPORTAMENTO DE FALA:")
        builder.appendLine("- Humor: ${if (p.humor > 0.7f) "Descontraído e bem-humorado" else if (p.humor < 0.3f) "Sério e austero" else "Equilibrado"}.")
        builder.appendLine("- Formalidade: ${if (p.formality > 0.7f) "Linguagem culta e formal" else if (p.formality < 0.3f) "Coloquial e direto" else "Natural e polido"}.")
        builder.appendLine("- Criatividade: ${if (p.creativity > 0.7f) "Respostas ricas, analógicas e inovadoras" else "Pragmático e literal"}.")
        builder.appendLine("- Raciocínio: ${if (p.reasoningDepth > 0.7f) "Explique detalhadamente seu raciocínio passo a passo" else "Vá direto à conclusão sem preâmbulos desnecessários"}.")
        builder.appendLine("- Empatia: ${if (p.empathy > 0.7f) "Demonstre escuta ativa profunda e empatia com o interlocutor" else "Seja estritamente objetivo"}.")
        builder.appendLine("- Concisão vocal: ${if (p.verbosity < 0.4f) "Seja telegráfico e responda em 1 a 2 frases curtas próprias para conversa por voz falada" else "Ofereça explicações completas e ricas"}.")
        builder.appendLine("- Proatividade: ${if (p.proactivity > 0.6f) "Após responder, faça uma pergunta relevante para aprofundar o diálogo" else "Aguarde as ordens do usuário"}.")

        voiceModel?.let { vm ->
            builder.appendLine()
            builder.appendLine("DIRETRIZES VOCAIS E NARRATIVAS (${vm.name}):")
            builder.appendLine("- Timbre de base: ${vm.baseVoice} | Estilo pretendido: ${vm.accentOrStyle}.")
            if (vm.styleDescription.isNotBlank()) {
                builder.appendLine("- Instrução vocal: ${vm.styleDescription}.")
            }
            if (agent.isNarrator) {
                builder.appendLine("- Modo Narrador Ativo: Entoe com expressividade teatral, cadência ritmada e pausas dramáticas adequadas à narração.")
            }
        }

        return builder.toString()
    }

    private suspend fun saveVoiceModelsInternal(list: List<LiveVoiceModel>) {
        settingsRepository.setSetting("live_voice_models", json.encodeToString(list))
    }

    private suspend fun saveAgentsInternal(list: List<LiveAgent>) {
        settingsRepository.setSetting("live_agents_list", json.encodeToString(list))
    }
}
