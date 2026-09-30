package com.example

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.GenerationState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatViewModelPrompt2Test {

    private lateinit var app: HubApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAdvancedSettingsDefaultsAndUpdates() {
        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)

        // Default temperature is 0.7f
        assertEquals(0.7f, viewModel.temperature.value, 0.01f)
        viewModel.temperature.value = 1.2f
        assertEquals(1.2f, viewModel.temperature.value, 0.01f)

        // Default max tokens is 4096
        assertEquals(4096, viewModel.maxOutputTokens.value)
        viewModel.maxOutputTokens.value = 8192
        assertEquals(8192, viewModel.maxOutputTokens.value)

        // Store interactions is false by default for strict privacy
        assertFalse(viewModel.storeInteractionsOnServer.value)
    }

    @Test
    fun testStopGenerationResetsGenerationStateToIdle() {
        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)

        viewModel.stopGeneration()
        assertEquals(GenerationState.Idle, viewModel.generationState.value)
    }

    @Test
    fun testSelectModelUpdatesActiveModelId() {
        val handle = SavedStateHandle()
        val viewModel = ChatViewModel(handle, app)

        viewModel.selectModel("gemini-2.5-pro")
        assertEquals("gemini-2.5-pro", viewModel.activeModelId.value)
    }
}
