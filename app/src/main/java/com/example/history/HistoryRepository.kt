package com.example.history

import com.example.storage.dao.ConversationDao
import com.example.storage.dao.MessageDao
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class HistoryRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val conversationsFlow: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return conversationDao.searchConversations(query)
    }

    fun getMessagesFlow(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageDao.getMessagesListForConversation(conversationId)
    }

    suspend fun getConversationById(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
        conversationDao.getById(id)
    }

    suspend fun createNewConversation(
        providerId: String = "gemini",
        modelId: String = "gemini-2.5-flash",
        title: String = "Nova Conversa"
    ): ConversationEntity = withContext(Dispatchers.IO) {
        val entity = ConversationEntity(
            id = "conv_${UUID.randomUUID()}",
            title = title,
            providerId = providerId,
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insert(entity)
        entity
    }

    suspend fun autoGenerateTitleIfFirstMessage(conversationId: String, userText: String) = withContext(Dispatchers.IO) {
        val conv = conversationDao.getById(conversationId) ?: return@withContext
        if (conv.title == "Nova Conversa" || conv.title.isEmpty()) {
            val words = userText.trim().split("\\s+".toRegex()).take(6).joinToString(" ")
            val derivedTitle = if (words.length > 35) words.take(32) + "..." else words
            if (derivedTitle.isNotEmpty()) {
                conversationDao.updateTitle(conversationId, derivedTitle)
            }
        }
    }

    suspend fun saveUserMessage(
        conversationId: String,
        content: String,
        status: String = "SENT"
    ): MessageEntity = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            id = "msg_${UUID.randomUUID()}",
            conversationId = conversationId,
            role = "user",
            content = content,
            status = status,
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(message)
        conversationDao.updateTimestamp(conversationId)
        message
    }

    suspend fun saveAssistantMessage(
        conversationId: String,
        content: String = "",
        status: String = "GENERATING"
    ): MessageEntity = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            id = "msg_${UUID.randomUUID()}",
            conversationId = conversationId,
            role = "assistant",
            content = content,
            status = status,
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(message)
        conversationDao.updateTimestamp(conversationId)
        message
    }

    suspend fun updateMessage(
        messageId: String,
        content: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        messageDao.updateContentAndStatus(messageId, content, status)
    }

    suspend fun markInterruptedGenerations(conversationId: String) = withContext(Dispatchers.IO) {
        val lastMsg = messageDao.getLastMessageForConversation(conversationId)
        if (lastMsg != null && lastMsg.status == "GENERATING") {
            messageDao.updateContentAndStatus(lastMsg.id, lastMsg.content, "INTERRUPTED")
        }
    }

    suspend fun editUserMessageAndTruncate(
        conversationId: String,
        messageId: String,
        newContent: String
    ) = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesListForConversation(conversationId)
        val target = messages.find { it.id == messageId }
        if (target != null) {
            // Truncate subsequent messages
            messageDao.deleteMessagesAfter(conversationId, target.createdAt)
            // Update the target message
            messageDao.updateContentAndStatus(messageId, newContent, "SENT")
            conversationDao.updateTimestamp(conversationId)
        }
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteById(messageId)
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteForConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        conversationDao.deleteAll()
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.updateTitle(conversationId, newTitle.trim())
    }
}
