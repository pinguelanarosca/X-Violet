package com.example.providers.model

data class ProviderCapabilities(
    val supportsText: Boolean = true,
    val supportsImageInput: Boolean = false,
    val supportsAudioInput: Boolean = false,
    val supportsAudioOutput: Boolean = false,
    val supportsVideoInput: Boolean = false,
    val supportsLiveStreaming: Boolean = false,
    val supportsToolCalling: Boolean = false,
    val supportsReasoningEffort: Boolean = false,
    val temperatureRange: ClosedFloatingPointRange<Float> = 0.0f..2.0f,
    val defaultTemperature: Float = 0.7f,
    val maxContextTokens: Int = 128000,
    val maxOutputTokens: Int = 4096,
    val returnsTokenUsage: Boolean = true,
    val supportedImageMimeTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
    val supportedAudioMimeTypes: List<String> = listOf("audio/mp3", "audio/wav", "audio/aac")
)
