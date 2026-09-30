package com.example.providers.contracts

import com.example.providers.model.ModelInfo

interface ModelProvider {
    val defaultModelId: String
    suspend fun listModels(correlationId: String): Result<List<ModelInfo>>
}
