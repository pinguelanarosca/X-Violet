package com.example.providers.model

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchemaJson: String
)

data class ToolCall(
    val id: String,
    val toolName: String,
    val argumentsJson: String
)

data class ToolResult(
    val callId: String,
    val resultJson: String,
    val isError: Boolean = false
)

data class LiveSessionConfig(
    val modelId: String,
    val voiceName: String = "Aoede",
    val systemInstruction: String? = null,
    val enableAudioOutput: Boolean = true
)

interface LiveSession {
    val sessionId: String
    suspend fun sendAudioFrame(pcmData: ByteArray)
    suspend fun sendVideoFrame(jpegData: ByteArray)
    suspend fun sendTextMessage(text: String)
    suspend fun disconnect()
}
