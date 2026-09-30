package com.example.providers.model

data class ModelInfo(
    val id: String,
    val displayName: String,
    val providerId: String,
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val description: String = ""
)
