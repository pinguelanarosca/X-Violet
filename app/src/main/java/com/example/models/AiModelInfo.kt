package com.example.models

data class AiModelInfo(
    val id: String,
    val providerId: String,
    val name: String,
    val displayName: String,
    val contextWindow: Int = 128000,
    val supportsVision: Boolean = true,
    val supportsAudio: Boolean = false,
    val supportsTools: Boolean = true
)
