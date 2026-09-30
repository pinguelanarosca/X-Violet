package com.example.providers.contracts

import com.example.providers.model.ChatRequest
import com.example.providers.model.ChatResponse
import com.example.providers.model.ProviderCapabilities
import com.example.providers.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface AIProvider {
    val id: String
    val displayName: String
    val defaultBaseUrl: String
    val capabilities: ProviderCapabilities

    suspend fun validateCredentials(): Result<Unit>
    fun isConfiguredFlow(): Flow<Boolean>

    suspend fun generateText(
        request: ChatRequest,
        correlationId: String
    ): Result<ChatResponse>

    fun streamText(
        request: ChatRequest,
        correlationId: String
    ): Flow<StreamEvent>
}
