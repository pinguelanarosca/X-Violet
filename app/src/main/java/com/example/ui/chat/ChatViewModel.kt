package com.example.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.HubApplication
import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.providers.contracts.AIProvider
import com.example.providers.model.*
import com.example.resilience.CircuitBreakerState
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.MessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

sealed class GenerationState {
    data object Idle : GenerationState()
    data class Connecting(val attempt: Int = 1) : GenerationState()
    data object Connected : GenerationState()
    data object Thinking : GenerationState()
    data class Receiving(val tokensCount: Int = 0) : GenerationState()
    data object Completed : GenerationState()
    data class Error(val error: AppError) : GenerationState()
}

enum class AssistantStateLabel(val text: String) {
    NO_CREDENTIAL("Sem credencial"),
    OFFLINE("Offline"),
    UNSTABLE("Instável"),
    ONLINE("Online")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val app: HubApplication = HubApplication.instance
) : ViewModel() {

    private val KEY_DRAFT = "chat_draft"
    val draftText: StateFlow<String> = savedStateHandle.getStateFlow(KEY_DRAFT, "")

    private val _activeConversation = MutableStateFlow<ConversationEntity?>(null)
    val activeConversation: StateFlow<ConversationEntity?> = _activeConversation.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _activeAttachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val activeAttachments: StateFlow<List<ChatAttachment>> = _activeAttachments.asStateFlow()

    // Advanced settings
    val temperature = MutableStateFlow(0.7f)
    val maxOutputTokens = MutableStateFlow(4096)
    val storeInteractionsOnServer = MutableStateFlow(false)

    val activeProviderId: StateFlow<String> = app.providerRegistry.activeProviderId
    val activeModelId: StateFlow<String> = app.providerRegistry.activeModelId

    val isNetworkConnected: StateFlow<Boolean> = app.connectivityObserver.isConnected

    val isKeyConfigured: StateFlow<Boolean> = combine(
        app.keystoreVault.allCredentialsFlow,
        activeProviderId
    ) { credentials, activeId ->
        credentials.any { it.providerId == activeId && it.status == com.example.security.KeystoreVault.STATUS_ACTIVE }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val activeCircuitBreakerState: StateFlow<CircuitBreakerState> = activeProviderId.flatMapLatest { id ->
        when (id) {
            "gemini" -> app.geminiCircuitBreaker.state
            "openai" -> app.openAiCircuitBreaker.state
            "anthropic" -> app.anthropicCircuitBreaker.state
            "groq" -> app.groqCircuitBreaker.state
            "openrouter" -> app.openRouterCircuitBreaker.state
            "xai" -> app.xAiCircuitBreaker.state
            else -> app.geminiCircuitBreaker.state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CircuitBreakerState.Closed)

    val assistantStatus: StateFlow<AssistantStateLabel> = combine(
        isNetworkConnected,
        isKeyConfigured,
        activeCircuitBreakerState
    ) { online, hasKey, circuitState ->
        deriveStatus(online, hasKey, circuitState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssistantStateLabel.NO_CREDENTIAL)

    // Room DB messages for active conversation
    val messages: StateFlow<List<MessageEntity>> = _activeConversation.flatMapLatest { conv ->
        if (conv == null) flowOf(emptyList())
        else app.historyRepository.getMessagesFlow(conv.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // All available models for active provider
    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            // Load or initialize default active conversation
            val existing = app.historyRepository.conversationsFlow.first()
            val conv = if (existing.isNotEmpty()) {
                existing.first()
            } else {
                app.historyRepository.createNewConversation(
                    providerId = activeProviderId.value,
                    modelId = activeModelId.value
                )
            }
            _activeConversation.value = conv
            app.historyRepository.markInterruptedGenerations(conv.id)

            // Discover models
            refreshAvailableModels()
        }
    }

    fun onDraftChange(newText: String) {
        savedStateHandle[KEY_DRAFT] = newText
    }

    fun addAttachmentFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    // Downscale if larger than 1024px max dimension
                    val maxDim = 1024
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val scaledBitmap = if (width > maxDim || height > maxDim) {
                        val ratio = width.toFloat() / height.toFloat()
                        val newWidth = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                        val newHeight = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                    } else {
                        originalBitmap
                    }

                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val byteArray = stream.toByteArray()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    val attachment = ChatAttachment(
                        mimeType = "image/jpeg",
                        dataBase64 = base64,
                        fileUri = uri.toString()
                    )
                    _activeAttachments.value = _activeAttachments.value + attachment
                }
            } catch (e: Exception) {
                HubLogger.log(
                    level = LogLevel.WARN,
                    category = LogCategory.UI,
                    message = "Falha ao processar imagem para anexo: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    fun removeAttachment(index: Int) {
        val current = _activeAttachments.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _activeAttachments.value = current
        }
    }

    fun refreshAvailableModels() {
        viewModelScope.launch {
            val provider = app.providerRegistry.getActiveProvider()
            if (provider is com.example.providers.contracts.ModelProvider) {
                val correlationId = "MODELS_${System.currentTimeMillis()}"
                val result = provider.listModels(correlationId)
                if (result.isSuccess) {
                    _availableModels.value = result.getOrThrow()
                }
            }
        }
    }

    fun selectModel(modelId: String) {
        app.providerRegistry.setActiveModelId(modelId)
    }

    fun selectProvider(providerId: String) {
        app.providerRegistry.setActiveProviderId(providerId)
        refreshAvailableModels()
    }

    fun sendMessage() {
        val text = draftText.value.trim()
        val attachments = _activeAttachments.value
        if (text.isEmpty() && attachments.isEmpty()) return
        if (_generationState.value !is GenerationState.Idle && _generationState.value !is GenerationState.Completed && _generationState.value !is GenerationState.Error) {
            return // Double tap protection
        }

        // 1. Clear draft immediately to avoid duplicate send
        savedStateHandle[KEY_DRAFT] = ""
        _activeAttachments.value = emptyList()

        viewModelScope.launch {
            val conv = _activeConversation.value ?: run {
                val newConv = app.historyRepository.createNewConversation(
                    providerId = activeProviderId.value,
                    modelId = activeModelId.value
                )
                _activeConversation.value = newConv
                newConv
            }

            val provider = app.providerRegistry.getActiveProvider()
            val modelId = activeModelId.value
            val correlationId = "CHAT_GEN_${System.currentTimeMillis()}"

            val userMsgEntity = app.historyRepository.saveUserMessage(
                conversationId = conv.id,
                content = text,
                status = "SENT"
            )
            app.historyRepository.autoGenerateTitleIfFirstMessage(conv.id, text)

            // 2. Check prerequisites
            if (!isKeyConfigured.value) {
                _generationState.value = GenerationState.Error(
                    AppError.Auth(
                        code = "ERR_NO_API_KEY",
                        userMessage = "Nenhuma chave ativa encontrada para o provedor ${provider.displayName}.",
                        technicalCause = "Configure a chave na aba Personalizar > Provedores"
                    )
                )
                return@launch
            }

            if (!isNetworkConnected.value) {
                _generationState.value = GenerationState.Error(
                    AppError.Network(
                        code = "ERR_NO_NETWORK",
                        technicalCause = "Dispositivo desconectado de Wi-Fi e Dados Móveis"
                    )
                )
                return@launch
            }

            // 3. Prepare ChatRequest with conversation history
            val allDbMessages = app.historyRepository.getMessagesList(conv.id)
            val chatMessages = allDbMessages.map { m ->
                ChatMessage(
                    role = if (m.role == "user") ChatRole.USER else ChatRole.ASSISTANT,
                    content = m.content,
                    attachments = if (m.id == userMsgEntity.id) attachments else emptyList()
                )
            }

            val request = ChatRequest(
                modelId = modelId,
                messages = chatMessages,
                temperature = temperature.value,
                maxTokens = maxOutputTokens.value
            )

            // 4. Create assistant placeholder message in Room
            val assistantMsg = app.historyRepository.saveAssistantMessage(
                conversationId = conv.id,
                content = "",
                status = "GENERATING"
            )

            // 5. Start streaming generation
            startStreaming(provider, request, assistantMsg.id, correlationId)
        }
    }

    private fun startStreaming(
        provider: AIProvider,
        request: ChatRequest,
        assistantMessageId: String,
        correlationId: String
    ) {
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _generationState.value = GenerationState.Connecting(1)

            val accumulatedContent = StringBuilder()
            var lastDbPersistTime = System.currentTimeMillis()
            var firstTokenReceived = false

            try {
                // Execute streaming with cancellation support
                provider.streamText(request, correlationId).collect { event ->
                    when (event) {
                        is StreamEvent.Started -> {
                            _generationState.value = GenerationState.Connected
                        }
                        is StreamEvent.ReasoningDelta -> {
                            _generationState.value = GenerationState.Thinking
                        }
                        is StreamEvent.TextDelta -> {
                            firstTokenReceived = true
                            accumulatedContent.append(event.text)
                            _generationState.value = GenerationState.Receiving(accumulatedContent.length)

                            // Throttle Room updates to ~1s
                            val now = System.currentTimeMillis()
                            if (now - lastDbPersistTime > 1000L) {
                                lastDbPersistTime = now
                                app.historyRepository.updateMessage(
                                    messageId = assistantMessageId,
                                    content = accumulatedContent.toString(),
                                    status = "GENERATING"
                                )
                            }
                        }
                        is StreamEvent.Completed -> {
                            app.historyRepository.updateMessage(
                                messageId = assistantMessageId,
                                content = accumulatedContent.toString(),
                                status = "COMPLETED"
                            )
                            _generationState.value = GenerationState.Completed
                        }
                        is StreamEvent.Failed -> {
                            val status = if (firstTokenReceived) "INTERRUPTED" else "FAILED"
                            app.historyRepository.updateMessage(
                                messageId = assistantMessageId,
                                content = accumulatedContent.toString(),
                                status = status
                            )
                            _generationState.value = GenerationState.Error(event.error)
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                // Cooperative user cancellation (Stop button)
                app.historyRepository.updateMessage(
                    messageId = assistantMessageId,
                    content = accumulatedContent.toString(),
                    status = "INTERRUPTED"
                )
                _generationState.value = GenerationState.Idle
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.UI,
                    message = "Geração interrompida pelo usuário ($correlationId)."
                )
            } catch (e: Exception) {
                val appError = ErrorMapper.fromThrowable(e)
                app.historyRepository.updateMessage(
                    messageId = assistantMessageId,
                    content = accumulatedContent.toString(),
                    status = if (firstTokenReceived) "INTERRUPTED" else "FAILED"
                )
                _generationState.value = GenerationState.Error(appError)
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _generationState.value = GenerationState.Idle
    }

    fun regenerateLast() {
        val conv = _activeConversation.value ?: return
        viewModelScope.launch {
            val allMessages = app.historyRepository.getMessagesList(conv.id)
            val lastAssistant = allMessages.lastOrNull { it.role == "assistant" }
            if (lastAssistant != null) {
                app.historyRepository.deleteMessage(lastAssistant.id)
            }
            sendMessage()
        }
    }

    fun continueGeneration(messageId: String) {
        val conv = _activeConversation.value ?: return
        val provider = app.providerRegistry.getActiveProvider()
        val correlationId = "CONT_${System.currentTimeMillis()}"

        viewModelScope.launch {
            val allMessages = app.historyRepository.getMessagesList(conv.id)
            val targetMsg = allMessages.find { it.id == messageId } ?: return@launch

            val promptToContinue = "Por favor, continue a partir de exatamente onde parou:\n${targetMsg.content}"
            val request = ChatRequest(
                modelId = activeModelId.value,
                messages = listOf(ChatMessage(role = ChatRole.USER, content = promptToContinue)),
                temperature = temperature.value,
                maxTokens = maxOutputTokens.value
            )

            val newAssistantMsg = app.historyRepository.saveAssistantMessage(
                conversationId = conv.id,
                content = targetMsg.content,
                status = "GENERATING"
            )

            startStreaming(provider, request, newAssistantMsg.id, correlationId)
        }
    }

    fun editUserMessage(messageId: String, newContent: String) {
        val conv = _activeConversation.value ?: return
        viewModelScope.launch {
            app.historyRepository.editUserMessageAndTruncate(conv.id, messageId, newContent)
            sendMessage()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            app.historyRepository.deleteMessage(messageId)
        }
    }

    fun clearConversation() {
        val conv = _activeConversation.value ?: return
        viewModelScope.launch {
            app.historyRepository.deleteConversation(conv.id)
            val newConv = app.historyRepository.createNewConversation(
                providerId = activeProviderId.value,
                modelId = activeModelId.value
            )
            _activeConversation.value = newConv
        }
    }

    fun switchConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = app.historyRepository.getConversationById(conversationId)
            if (conv != null) {
                _activeConversation.value = conv
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newConv = app.historyRepository.createNewConversation(
                providerId = activeProviderId.value,
                modelId = activeModelId.value
            )
            _activeConversation.value = newConv
        }
    }

    fun clearError() {
        _generationState.value = GenerationState.Idle
    }

    companion object {
        fun deriveStatus(
            isOnline: Boolean,
            hasKey: Boolean,
            circuitState: CircuitBreakerState
        ): AssistantStateLabel {
            return when {
                !hasKey -> AssistantStateLabel.NO_CREDENTIAL
                !isOnline -> AssistantStateLabel.OFFLINE
                circuitState is CircuitBreakerState.Open -> AssistantStateLabel.UNSTABLE
                else -> AssistantStateLabel.ONLINE
            }
        }
    }
}
