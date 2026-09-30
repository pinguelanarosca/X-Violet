package com.example

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.errors.AppError
import com.example.providers.model.ChatMessage
import com.example.providers.model.ChatRole
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.HubDatabase
import com.example.ui.chat.AssistantStateLabel
import com.example.ui.chat.ChatViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    private lateinit var app: HubApplication

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext<HubApplication>()
        app.historyRepository.deleteAllConversations()
    }

    @Test
    fun testDraftTextPreservedInSavedStateHandleAcrossRecreation() {
        val handle1 = SavedStateHandle()
        val viewModel1 = ChatViewModel(handle1, app)

        // User types a draft
        viewModel1.onDraftChange("Prompt importante que não pode ser perdido após rotação")
        assertEquals("Prompt importante que não pode ser perdido após rotação", viewModel1.draftText.value)

        // Simulate activity destruction and recreation with same SavedStateHandle state
        val savedDraft = handle1.get<String>("chat_draft")
        assertEquals("Prompt importante que não pode ser perdido após rotação", savedDraft)

        val handle2 = SavedStateHandle(mapOf("chat_draft" to savedDraft))
        val viewModel2 = ChatViewModel(handle2, app)

        // Restored draft is intact
        assertEquals("Prompt importante que não pode ser perdido após rotação", viewModel2.draftText.value)
    }

    @Test
    fun testAssistantStatusDerivesPurelyFromFacts() {
        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)

        // 1. Initial StateFlow value without configured credentials
        assertEquals(AssistantStateLabel.NO_CREDENTIAL, viewModel.assistantStatus.value)

        // 2. Unit testing fact combinations
        // Case A: No key configured -> Always NO_CREDENTIAL
        val statusNoKey = ChatViewModel.deriveStatus(
            isOnline = true,
            hasKey = false,
            circuitState = com.example.resilience.CircuitBreakerState.Closed
        )
        assertEquals(AssistantStateLabel.NO_CREDENTIAL, statusNoKey)

        // Case B: Has key, but no internet connection -> OFFLINE
        val statusOffline = ChatViewModel.deriveStatus(
            isOnline = false,
            hasKey = true,
            circuitState = com.example.resilience.CircuitBreakerState.Closed
        )
        assertEquals(AssistantStateLabel.OFFLINE, statusOffline)

        // Case C: Has key, online, but CircuitBreaker is tripped -> UNSTABLE
        val statusUnstable = ChatViewModel.deriveStatus(
            isOnline = true,
            hasKey = true,
            circuitState = com.example.resilience.CircuitBreakerState.Open(System.currentTimeMillis(), 10000L)
        )
        assertEquals(AssistantStateLabel.UNSTABLE, statusUnstable)

        // Case D: Has key, online, and CircuitBreaker is Closed -> ONLINE
        val statusOnline = ChatViewModel.deriveStatus(
            isOnline = true,
            hasKey = true,
            circuitState = com.example.resilience.CircuitBreakerState.Closed
        )
        assertEquals(AssistantStateLabel.ONLINE, statusOnline)
    }

    @Test
    fun testSendMessageClearsDraftAndAppendsUserMessage() = runBlocking {
        app.providerRegistry.setActiveProviderId("gemini")
        app.providerRegistry.setActiveModelId("gemini-2.5-flash")
        app.keystoreVault.saveKey("gemini", "FAKE_KEY_AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q")

        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        var waitConv = 0
        while (viewModel.activeConversation.value == null && waitConv < 30) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            kotlinx.coroutines.delay(50L)
            waitConv++
        }

        viewModel.onDraftChange("Olá Assistente!")
        assertEquals("Olá Assistente!", viewModel.draftText.value)

        viewModel.sendMessage()
        
        // Draft is cleared immediately inside sendMessage on the main thread
        assertEquals("", viewModel.draftText.value)

        // Wait for Dispatchers.IO to persist the conversation in database
        var conversations: List<com.example.storage.entities.ConversationEntity> = emptyList()
        var elapsed = 0
        while (conversations.isEmpty() && elapsed < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            conversations = app.historyRepository.conversationsFlow.first()
            elapsed++
        }
        assertTrue("Conversa deveria ter sido criada", conversations.isNotEmpty())

        // Wait for Dispatchers.IO to persist the message in database
        var msgs: List<com.example.storage.entities.MessageEntity> = emptyList()
        var msgElapsed = 0
        while (msgs.isEmpty() && msgElapsed < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            msgs = app.historyRepository.getMessagesList(conversations.first().id)
            msgElapsed++
        }
        assertTrue("Deveria conter a mensagem de texto digitada", msgs.any { it.content == "Olá Assistente!" })
    }

    @Test
    fun testEmptyDraftDoesNotSendMessage() {
        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)

        viewModel.onDraftChange("   ")
        viewModel.sendMessage()

        assertEquals(0, viewModel.messages.value.size)
    }
}
