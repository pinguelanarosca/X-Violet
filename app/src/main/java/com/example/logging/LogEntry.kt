package com.example.logging

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val dataJson: String? = null,
    val throwableSanitized: String? = null,
    val correlationId: String? = null
)
