package com.example.providers.contracts

import com.example.providers.model.LiveSession
import com.example.providers.model.LiveSessionConfig
import com.example.providers.model.ToolCall
import com.example.providers.model.ToolDefinition
import com.example.providers.model.ToolResult

interface LiveProvider {
    suspend fun connectLive(
        sessionConfig: LiveSessionConfig,
        correlationId: String
    ): Result<LiveSession>
}

interface ToolProvider {
    fun availableTools(): List<ToolDefinition>
    suspend fun executeTool(call: ToolCall, correlationId: String): Result<ToolResult>
}
