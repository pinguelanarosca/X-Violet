package com.example.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs",
    indices = [
        Index("timestamp"),
        Index("level"),
        Index("category"),
        Index("correlationId")
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val category: String,
    val message: String,
    val dataJson: String? = null,
    val throwableSanitized: String? = null,
    val correlationId: String? = null
)

@Entity(tableName = "vault_credentials")
data class VaultCredentialEntity(
    @PrimaryKey
    val providerId: String,
    val maskedKey: String,
    val status: String, // ACTIVE, NEEDS_REENTRY, REVOKED
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val providerId: String = "gemini",
    val modelId: String = "default"
)

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("createdAt")
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // user, assistant, system, tool
    val content: String,
    val status: String = "SENT", // PENDING, SENT, STREAMING, ERROR
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_profiles")
data class AgentProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val providerId: String,
    val modelId: String,
    val temperature: Float = 0.7f,
    val iconName: String = "assistant"
)

@Entity(
    tableName = "ai_models",
    indices = [Index("providerId")]
)
data class AiModelEntity(
    @PrimaryKey
    val id: String,
    val providerId: String,
    val modelName: String,
    val displayName: String,
    val capabilitiesJson: String = "[]",
    val isDiscovered: Boolean = false
)

@kotlinx.serialization.Serializable
@Entity(
    tableName = "memories",
    indices = [
        Index("category"),
        Index("key")
    ]
)
data class MemoryEntity(
    @PrimaryKey
    val id: String,
    val key: String,
    val content: String,
    val category: String = "general",
    val tagsJson: String = "[]",
    val origin: String = "user",
    val scope: String = "global",
    val scopeId: String? = null,
    val isPendingApproval: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
@Entity(
    tableName = "workflows"
)
data class WorkflowEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val stepsJson: String = "[]",
    val isActive: Boolean = true,
    val maxSteps: Int = 20,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
@Entity(
    tableName = "custom_models",
    indices = [
        Index("providerId"),
        Index("isFavorite"),
        Index("isDefault")
    ]
)
data class CustomModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String = "psychology",
    val providerId: String,
    val baseModelId: String,
    val instructions: String = "",
    val traitsJson: String = "{}",
    val reasoningLevel: Int = 50,
    val featureFlagsJson: String = "{}",
    val technicalParamsJson: String = "{}",
    val routingMode: String = "MANUAL",
    val fallbackChainJson: String = "[]",
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
