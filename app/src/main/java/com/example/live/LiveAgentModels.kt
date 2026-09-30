package com.example.live

import kotlinx.serialization.Serializable

@Serializable
data class LiveVoiceModel(
    val id: String,
    val name: String,
    val baseVoice: String, // Puck, Charon, Kore, Fenrir, Aoede
    val pitch: Float = 1.0f, // 0.5x to 1.5x
    val speed: Float = 1.0f, // 0.5x to 2.0x
    val warmth: Float = 0.5f, // 0.0 to 1.0
    val energy: Float = 0.5f, // 0.0 to 1.0
    val accentOrStyle: String = "Neutro",
    val styleDescription: String = "",
    val isCustom: Boolean = false
)

@Serializable
data class LiveAgentPersonality(
    val humor: Float = 0.5f,        // 0.0 (Sério) -> 1.0 (Descontraído/Espirituoso)
    val formality: Float = 0.5f,    // 0.0 (Coloquial/Gírias) -> 1.0 (Acadêmico/Formal)
    val creativity: Float = 0.7f,   // 0.0 (Literal/Pragmático) -> 1.0 (Imaginativo/Poético)
    val reasoningDepth: Float = 0.6f,// 0.0 (Direto ao Ponto) -> 1.0 (Meticuloso Passo a Passo)
    val empathy: Float = 0.8f,      // 0.0 (Frio/Computacional) -> 1.0 (Altamente Acolhedor/Empático)
    val verbosity: Float = 0.4f,    // 0.0 (Telegráfico/Conciso) -> 1.0 (Elaborado/Explicativo)
    val proactivity: Float = 0.7f   // 0.0 (Reativo/Só Responde) -> 1.0 (Proativo/Faz Perguntas de Volta)
)

@Serializable
data class LiveAgent(
    val id: String,
    val name: String,
    val roleTag: String,
    val description: String,
    val avatarColorHex: String = "#00E5FF",
    val voiceModelId: String = "voice_puck",
    val modelId: String = "gemini-3.8-live",
    val personality: LiveAgentPersonality = LiveAgentPersonality(),
    val systemInstructions: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxOutputTokens: Int = 2048,
    val presencePenalty: Float = 0.0f,
    val frequencyPenalty: Float = 0.0f,
    val vadSensitivity: Float = 0.7f,
    val interruptionEnabled: Boolean = true,
    val isNarrator: Boolean = false,
    val responseModality: String = "AUDIO"
)
