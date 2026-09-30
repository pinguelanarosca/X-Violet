package com.example.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppUiState(
    val activeProviderId: String = "gemini",
    val activeModelId: String = "gemini-2.5-flash",
    val isNetworkAvailable: Boolean = true,
    val isStreaming: Boolean = false,
    val isAdvancedMode: Boolean = false
)

class AppStateManager {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun updateNetworkStatus(isConnected: Boolean) {
        _uiState.value = _uiState.value.copy(isNetworkAvailable = isConnected)
    }

    fun setAdvancedMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAdvancedMode = enabled)
    }
}
