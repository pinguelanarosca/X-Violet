package com.example.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.storage.entities.AgentProfileEntity
import com.example.storage.entities.AiModelEntity
import com.example.storage.entities.AppSettingEntity
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.LogEntryEntity
import com.example.storage.entities.MemoryEntity
import com.example.storage.entities.MessageEntity
import com.example.storage.entities.VaultCredentialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(logs: List<LogEntryEntity>)

    @Query("SELECT * FROM logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 2000): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentLogsList(limit: Int = 2000): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY timestamp DESC, id DESC LIMIT :keepCount)")
    suspend fun trimToLimit(keepCount: Int = 2000)

    @Query("DELETE FROM logs")
    suspend fun clearAll()
}

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(credential: VaultCredentialEntity)

    @Query("SELECT * FROM vault_credentials WHERE providerId = :providerId LIMIT 1")
    suspend fun getCredential(providerId: String): VaultCredentialEntity?

    @Query("SELECT * FROM vault_credentials ORDER BY providerId ASC")
    fun getAllCredentialsFlow(): Flow<List<VaultCredentialEntity>>

    @Query("SELECT * FROM vault_credentials ORDER BY providerId ASC")
    suspend fun getAllCredentialsList(): List<VaultCredentialEntity>

    @Query("UPDATE vault_credentials SET status = :status, updatedAt = :updatedAt WHERE providerId = :providerId")
    suspend fun updateStatus(providerId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM vault_credentials WHERE providerId = :providerId")
    suspend fun deleteCredential(providerId: String)
}

@Dao
interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Query("SELECT * FROM app_settings")
    fun getAllSettingsFlow(): Flow<List<AppSettingEntity>>
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesListForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessageForConversation(conversationId: String): MessageEntity?

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContentAndStatus(id: String, content: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :createdAt")
    suspend fun deleteMessagesAfter(conversationId: String, createdAt: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)
}

@Dao
interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: AgentProfileEntity)

    @Query("SELECT * FROM agent_profiles ORDER BY name ASC")
    fun getAllAgents(): Flow<List<AgentProfileEntity>>

    @Query("SELECT * FROM agent_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentProfileEntity?
}

@Dao
interface AiModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(models: List<AiModelEntity>)

    @Query("SELECT * FROM ai_models WHERE providerId = :providerId ORDER BY displayName ASC")
    fun getModelsForProvider(providerId: String): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE providerId = :providerId ORDER BY displayName ASC")
    suspend fun getModelsForProviderList(providerId: String): List<AiModelEntity>

    @Query("DELETE FROM ai_models WHERE providerId = :providerId")
    suspend fun clearForProvider(providerId: String)
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isPendingApproval = 0 ORDER BY updatedAt DESC")
    fun getAllApprovedMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isPendingApproval = 0 ORDER BY updatedAt DESC")
    suspend fun getAllApprovedMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE isPendingApproval = 1 ORDER BY updatedAt DESC")
    fun getPendingSuggestionsFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isPendingApproval = 1 ORDER BY updatedAt DESC")
    suspend fun getPendingSuggestionsList(): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface WorkflowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workflow: com.example.storage.entities.WorkflowEntity)

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun getAllWorkflowsFlow(): Flow<List<com.example.storage.entities.WorkflowEntity>>

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    suspend fun getAllWorkflowsList(): List<com.example.storage.entities.WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.example.storage.entities.WorkflowEntity?

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CustomModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(model: com.example.storage.entities.CustomModelEntity)

    @Query("SELECT * FROM custom_models ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllCustomModelsFlow(): Flow<List<com.example.storage.entities.CustomModelEntity>>

    @Query("SELECT * FROM custom_models ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAllCustomModelsList(): List<com.example.storage.entities.CustomModelEntity>

    @Query("SELECT * FROM custom_models WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): com.example.storage.entities.CustomModelEntity?

    @Query("SELECT * FROM custom_models WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultModel(): com.example.storage.entities.CustomModelEntity?

    @Query("UPDATE custom_models SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("DELETE FROM custom_models WHERE id = :id")
    suspend fun deleteById(id: String)
}
