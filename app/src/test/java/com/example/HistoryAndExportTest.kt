package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.history.ExportManager
import com.example.history.HistoryRepository
import com.example.storage.HubDatabase
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryAndExportTest {

    private lateinit var database: HubDatabase
    private lateinit var historyRepository: HistoryRepository
    private lateinit var exportManager: ExportManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyRepository = HistoryRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao()
        )
        exportManager = ExportManager(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testConversationCreationAndAutoTitleGeneration() = runBlocking {
        val conv = historyRepository.createNewConversation(providerId = "gemini", modelId = "gemini-2.5-flash")
        assertNotNull(conv)
        assertEquals("Nova Conversa", conv.title)

        // Save first user message
        historyRepository.saveUserMessage(conv.id, "Como funciona o algoritmo de criptografia AES-GCM?")
        historyRepository.autoGenerateTitleIfFirstMessage(conv.id, "Como funciona o algoritmo de criptografia AES-GCM?")

        val updated = historyRepository.getConversationById(conv.id)
        assertNotNull(updated)
        assertTrue("Título deve derivar das primeiras palavras", updated!!.title.startsWith("Como funciona o algoritmo"))
    }

    @Test
    fun testMessageStatusTransitionsAndTruncationOnEdit() = runBlocking {
        val conv = historyRepository.createNewConversation()

        // 1. User message 1
        val u1 = historyRepository.saveUserMessage(conv.id, "Mensagem 1", "SENT")
        Thread.sleep(10)

        // 2. Assistant message 1
        val a1 = historyRepository.saveAssistantMessage(conv.id, "Resposta 1", "COMPLETED")
        Thread.sleep(10)

        // 3. User message 2
        val u2 = historyRepository.saveUserMessage(conv.id, "Mensagem 2", "SENT")
        Thread.sleep(10)

        // 4. Assistant message 2
        val a2 = historyRepository.saveAssistantMessage(conv.id, "Resposta 2", "COMPLETED")

        val messagesBefore = historyRepository.getMessagesList(conv.id)
        assertEquals(4, messagesBefore.size)

        // Edit message 1 -> messages after u1 (a1, u2, a2) must be deleted
        historyRepository.editUserMessageAndTruncate(conv.id, u1.id, "Mensagem 1 Editada")

        val messagesAfter = historyRepository.getMessagesList(conv.id)
        assertEquals(1, messagesAfter.size)
        assertEquals("Mensagem 1 Editada", messagesAfter[0].content)
        assertEquals(u1.id, messagesAfter[0].id)
    }

    @Test
    fun testExportManagerGeneratesValidTranscriptAndJson() {
        val conv = ConversationEntity(
            id = "conv_test_1",
            title = "Discussão sobre Kotlin",
            createdAt = 1700000000000L,
            updatedAt = 1700000060000L,
            providerId = "gemini",
            modelId = "gemini-2.5-flash"
        )
        val msgs = listOf(
            MessageEntity(id = "m1", conversationId = conv.id, role = "user", content = "Qual a vantagem do Jetpack Compose?", createdAt = 1700000000000L),
            MessageEntity(id = "m2", conversationId = conv.id, role = "assistant", content = "Declaração reativa e moderna.", createdAt = 1700000010000L)
        )

        val transcript = exportManager.buildTranscript(conv, msgs)
        assertTrue(transcript.contains("=== HUB MULTIMODAL - TRANSCRIÇÃO DA CONVERSA ==="))
        assertTrue(transcript.contains("Qual a vantagem do Jetpack Compose?"))
        assertTrue(transcript.contains("Declaração reativa e moderna."))

        val jsonExport = exportManager.buildJsonExport(conv, msgs)
        assertTrue(jsonExport.contains("\"hubApp\":\"Hub Multimodal\""))
        assertTrue(jsonExport.contains("\"totalMessages\":2"))
        assertTrue(jsonExport.contains("Jetpack Compose"))
    }

    @Test
    fun testSearchConversationsByTitleKeyword() = runBlocking {
        historyRepository.createNewConversation(title = "Projeto Android")
        historyRepository.createNewConversation(title = "Receita de Bolo")
        historyRepository.createNewConversation(title = "Configuração do Servidor")

        val searchResult = historyRepository.searchConversations("Android").first()
        assertEquals(1, searchResult.size)
        assertEquals("Projeto Android", searchResult[0].title)
    }
}
