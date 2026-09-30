package com.example.live

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Base64
import com.example.audio.AudioEngine
import com.example.errors.AppError
import com.example.errors.ErrorMapper
import com.example.history.HistoryRepository
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.logging.LogSanitizer
import com.example.network.ConnectivityObserver
import com.example.security.KeystoreVault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class LiveChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LiveSessionManager(
    private val context: Context,
    private val keystoreVault: KeystoreVault,
    private val historyRepository: HistoryRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Unlimited for WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {

    sealed class SessionState {
        object Idle : SessionState()
        object Connecting : SessionState()
        object Connected : SessionState()
        data class Reconnecting(val attempt: Int, val maxAttempts: Int, val reason: String) : SessionState()
        object Disconnecting : SessionState()
        data class Disconnected(val reason: String) : SessionState()
        data class Failed(val error: AppError) : SessionState()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State Flows
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _isAudioActive = MutableStateFlow(false)
    val isAudioActive: StateFlow<Boolean> = _isAudioActive.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isScreenShareActive = MutableStateFlow(false)
    val isScreenShareActive: StateFlow<Boolean> = _isScreenShareActive.asStateFlow()

    private val _isModelThinking = MutableStateFlow(false)
    val isModelThinking: StateFlow<Boolean> = _isModelThinking.asStateFlow()

    private val _isProactiveAudioMuted = MutableStateFlow(false)
    val isProactiveAudioMuted: StateFlow<Boolean> = _isProactiveAudioMuted.asStateFlow()

    // Live Transcripts
    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _modelTranscript = MutableStateFlow("")
    val modelTranscript: StateFlow<String> = _modelTranscript.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _liveMessages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val liveMessages: StateFlow<List<LiveChatMessage>> = _liveMessages.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private var durationTimerJob: Job? = null

    // Configurable parameters
    var selectedModelId: String = "gemini-3.8-live"
    var selectedVoice: String = "Puck" // Puck, Charon, Aoede, Fenrir, Kore
    var systemInstructions: String = "Você é um assistente de voz nativo e empático. Responda de forma concisa e natural."
    var temperature: Float = 0.7f
    var topP: Float = 0.95f
    var topK: Int = 40
    var maxOutputTokens: Int = 2048
    var presencePenalty: Float = 0.0f
    var frequencyPenalty: Float = 0.0f
    var responseModality: String = "AUDIO" // AUDIO, TEXT
    var exaOnlySearchPolicy: Boolean = true

    var liveAgentRepository: LiveAgentRepository? = null

    fun syncFromActiveAgent() {
        val repo = liveAgentRepository ?: return
        val agent = repo.activeAgent.value ?: return
        val voiceModel = repo.voiceModels.value.find { it.id == agent.voiceModelId }
        applyAgent(agent, voiceModel)
    }

    fun applyAgent(agent: LiveAgent, voiceModel: LiveVoiceModel? = null) {
        selectedModelId = agent.modelId
        selectedVoice = voiceModel?.baseVoice ?: "Puck"
        temperature = agent.temperature
        topP = agent.topP
        topK = agent.topK
        maxOutputTokens = agent.maxOutputTokens
        presencePenalty = agent.presencePenalty
        frequencyPenalty = agent.frequencyPenalty
        responseModality = agent.responseModality

        val compiledInstructions = liveAgentRepository?.compileSystemPromptForAgent(agent, voiceModel)
            ?: agent.systemInstructions
        systemInstructions = compiledInstructions

        HubLogger.log(
            LogLevel.INFO,
            LogCategory.LIVE,
            "Configurações do Live atualizadas para Agente '${agent.name}' (Voz: $selectedVoice, Mod: $selectedModelId)."
        )
    }

    // Engine and Socket references
    val audioEngine = AudioEngine(context)
    private var webSocket: WebSocket? = null

    // Session handles
    private var activeConversationId: String? = null
    private var lastResumptionHandle: String? = null

    // Reconnection parameters
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5
    private var isIntentionallyClosed = AtomicBoolean(false)
    private var stableConnectionJob: Job? = null

    // Latency metrics
    private var userSpeechEndTime = 0L
    private var isFirstAudioTokenReceived = false

    // Audio Focus
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        // Observe network recovery for instant reconnection
        scope.launch {
            connectivityObserver.isConnected.collect { isConnected ->
                if (isConnected && _sessionState.value is SessionState.Reconnecting) {
                    HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Conexão de rede restabelecida. Retomando sessão Live imediatamente.")
                    reconnectSession("Rede restabelecida")
                }
            }
        }
    }

    fun startLiveSession(conversationId: String? = null) {
        if (_sessionState.value is SessionState.Connecting || _sessionState.value is SessionState.Connected) {
            return
        }

        // Sincroniza parâmetros granulares do agente e voz ativos antes de conectar
        syncFromActiveAgent()

        isIntentionallyClosed.set(false)
        activeConversationId = conversationId
        _sessionState.value = SessionState.Connecting

        scope.launch(Dispatchers.IO) {
            val keyResult = keystoreVault.getDecryptedKey("gemini")
            if (keyResult.isFailure || keyResult.getOrThrow().isBlank()) {
                val error = AppError.Auth(
                    code = "ERR_GEMINI_KEY_MISSING",
                    userMessage = "Chave da API do Gemini ausente no cofre. Configure em Personalizar > Provedores.",
                    technicalCause = "Keystore has no key for 'gemini'"
                )
                _sessionState.value = SessionState.Failed(error)
                return@launch
            }

            val apiKey = keyResult.getOrThrow()
            requestAudioFocus()
            connectWebSocket(apiKey)
        }
    }

    private fun connectWebSocket(apiKey: String) {
        val modelPath = if (selectedModelId.startsWith("models/")) selectedModelId else "models/$selectedModelId"
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$encodedKey"

        // Log sanitized URL without key
        HubLogger.log(
            LogLevel.INFO,
            LogCategory.LIVE,
            "Iniciando conexão WebSocket Live para o modelo $modelPath..."
        )

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "WebSocket Live conectado. Enviando mensagem de setup...")
                sendSetupMessage(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val sanitizedMsg = LogSanitizer.sanitize(t.message ?: "Erro de conexão no WebSocket")
                HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Falha na conexão WebSocket Live: $sanitizedMsg")

                val appError = ErrorMapper.fromThrowable(t)
                if (appError is AppError.Auth || !appError.retryable || isIntentionallyClosed.get()) {
                    _sessionState.value = SessionState.Failed(appError)
                    cleanup()
                } else {
                    scheduleReconnect(appError.userMessage)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "WebSocket Live fechado (code=$code, reason=$reason)")
                if (!isIntentionallyClosed.get()) {
                    scheduleReconnect("Conexão encerrada pelo servidor ($reason)")
                } else {
                    _sessionState.value = SessionState.Disconnected("Sessão encerrada pelo usuário")
                    cleanup()
                }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val modelPath = if (selectedModelId.startsWith("models/")) selectedModelId else "models/$selectedModelId"

        val setupObj = buildJsonObject {
            putJsonObject("setup") {
                put("model", modelPath)
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add(responseModality)
                    }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", selectedVoice)
                            }
                        }
                    }
                    put("temperature", temperature)
                    put("topP", topP)
                    put("topK", topK)
                    put("maxOutputTokens", maxOutputTokens)
                    if (presencePenalty != 0.0f) {
                        put("presencePenalty", presencePenalty)
                    }
                    if (frequencyPenalty != 0.0f) {
                        put("frequencyPenalty", frequencyPenalty)
                    }
                }
                if (systemInstructions.isNotBlank()) {
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", systemInstructions)
                            }
                        }
                    }
                }
                // Request session resumption handle
                lastResumptionHandle?.let { handle ->
                    putJsonObject("sessionResumption") {
                        put("handle", handle)
                    }
                }
            }
        }

        val sent = ws.send(setupObj.toString())
        if (!sent) {
            HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Falha ao enviar mensagem de setup no WebSocket.")
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject

            // 1. Setup Complete
            if (root.containsKey("setupComplete")) {
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Sessão Live confirmada pelo servidor (setupComplete).")
                _sessionState.value = SessionState.Connected
                reconnectAttempt = 0
                markConnectionStable()
                startCallDurationTimer()
                startAudioEngineCapture()
                try {
                    LiveService.startService(context)
                } catch (e: Exception) {
                    HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Não foi possível iniciar o LiveService em segundo plano: ${e.message}")
                }
                return
            }

            // 2. GoAway (Pre-emptive migration warning)
            if (root.containsKey("goAway")) {
                val ttl = root["goAway"]?.jsonObject?.get("timeToLive")?.jsonPrimitive?.contentOrNull ?: "10s"
                HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Aviso GoAway recebido (TTL: $ttl). Preparando migração com handle...")
                return
            }

            // 3. Server Content (Audio/Text/Turn/Interruption)
            if (root.containsKey("serverContent")) {
                val serverContent = root["serverContent"]?.jsonObject ?: return
                val modelTurn = serverContent["modelTurn"]?.jsonObject
                val parts = modelTurn?.get("parts")?.jsonArray

                val interrupted = serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
                if (interrupted) {
                    HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Sinal de interrupção do modelo recebido. Limpando áudio...")
                    audioEngine.stopAndClearPlayback()
                    _isModelThinking.value = false
                    return
                }

                parts?.forEach { part ->
                    val partObj = part.jsonObject

                    // Audio PCM 24kHz chunk
                    val inlineData = partObj["inlineData"]?.jsonObject
                    if (inlineData != null) {
                        val base64Data = inlineData["data"]?.jsonPrimitive?.contentOrNull
                        if (!base64Data.isNullOrEmpty() && !_isProactiveAudioMuted.value) {
                            if (!isFirstAudioTokenReceived && userSpeechEndTime > 0) {
                                isFirstAudioTokenReceived = true
                                val latency = System.currentTimeMillis() - userSpeechEndTime
                                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Latência Live: $latency ms entre fim da fala e primeiro áudio do modelo.")
                            }
                            _isModelThinking.value = false
                            val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                            audioEngine.enqueuePlaybackAudio(pcmBytes)
                        }
                    }

                    // Text Transcript
                    val textPart = partObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!textPart.isNullOrEmpty()) {
                        _modelTranscript.value += textPart
                        _liveMessages.update { current ->
                            val last = current.lastOrNull()
                            if (last != null && last.role == "model") {
                                current.dropLast(1) + last.copy(text = last.text + textPart)
                            } else {
                                current + LiveChatMessage(role = "model", text = textPart)
                            }
                        }
                        saveLiveMessageToHistory("assistant", textPart)
                    }
                }

                val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                if (turnComplete) {
                    _isModelThinking.value = false
                }
            }

            // 4. Session Resumption Handle
            if (root.containsKey("sessionResumption")) {
                val handle = root["sessionResumption"]?.jsonObject?.get("handle")?.jsonPrimitive?.contentOrNull
                if (!handle.isNullOrEmpty()) {
                    lastResumptionHandle = handle
                }
            }

        } catch (e: Exception) {
            HubLogger.log(LogLevel.WARN, LogCategory.LIVE, "Erro ao processar mensagem do WebSocket Live: ${e.message}")
        }
    }

    private fun startCallDurationTimer() {
        durationTimerJob?.cancel()
        _callDurationSeconds.value = 0L
        durationTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (_sessionState.value is SessionState.Connected) {
                    _callDurationSeconds.value += 1
                }
            }
        }
    }

    private fun startAudioEngineCapture() {
        audioEngine.startPlayback()
        audioEngine.startCapture(
            onAudioFrame = { pcmChunk ->
                if (_sessionState.value is SessionState.Connected) {
                    if (!_isAudioActive.value) {
                        _isAudioActive.value = true
                    }
                    if (!_isMicMuted.value) {
                        sendAudioFrameToWebSocket(pcmChunk)
                    }
                }
            },
            onError = { appError ->
                HubLogger.log(LogLevel.ERROR, LogCategory.AUDIO, "Erro no AudioEngine: ${appError.userMessage}")
                _sessionState.value = SessionState.Failed(appError)
            }
        )
    }

    private fun sendAudioFrameToWebSocket(pcmChunk: ByteArray) {
        val ws = webSocket ?: return
        val base64 = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)

        val realtimeInput = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    addJsonObject {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64)
                    }
                }
            }
        }

        userSpeechEndTime = System.currentTimeMillis()
        isFirstAudioTokenReceived = false
        _isModelThinking.value = true

        val sent = ws.send(realtimeInput.toString())
        if (!sent) {
            // Backpressure: drop oldest frame in bounded channel
            audioEngine.outboundAudioQueue.trySend(pcmChunk)
        }
    }

    fun sendTextMessage(text: String) {
        val ws = webSocket ?: return
        if (text.isBlank()) return

        val clientContent = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", text)
                            }
                        }
                    }
                }
                put("turnComplete", true)
            }
        }

        ws.send(clientContent.toString())
        _userTranscript.value = text
        _liveMessages.update { it + LiveChatMessage(role = "user", text = text) }
        saveLiveMessageToHistory("user", text)
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Microfone no Live ${if (_isMicMuted.value) "silenciado" else "reativado"}.")
    }

    fun sendVideoFrameToWebSocket(jpegBytes: ByteArray) {
        val ws = webSocket ?: return
        if (_sessionState.value !is SessionState.Connected) return

        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val realtimeInput = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    addJsonObject {
                        put("mimeType", "image/jpeg")
                        put("data", base64)
                    }
                }
            }
        }

        ws.send(realtimeInput.toString())
    }

    fun setCameraActive(active: Boolean) {
        _isCameraActive.value = active
        HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Estado da Câmera no Live atualizado: $active")
    }

    fun setScreenShareActive(active: Boolean) {
        _isScreenShareActive.value = active
        HubLogger.log(LogLevel.INFO, LogCategory.VISION, "Estado do Compartilhamento de Tela no Live atualizado: $active")
    }

    fun toggleProactiveAudioMute() {
        _isProactiveAudioMuted.value = !_isProactiveAudioMuted.value
        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Áudio do modelo ${if (_isProactiveAudioMuted.value) "silenciado" else "ativado"}.")
    }

    private fun scheduleReconnect(reason: String) {
        if (isIntentionallyClosed.get()) return

        reconnectAttempt++
        if (reconnectAttempt > maxReconnectAttempts) {
            val err = AppError.Network(
                code = "ERR_LIVE_MAX_RECONNECT",
                userMessage = "Sessão Live encerrada após $maxReconnectAttempts tentativas de reconexão.",
                technicalCause = reason
            )
            _sessionState.value = SessionState.Failed(err)
            cleanup()
            return
        }

        // Exponential backoff with jitter: 1s, 2s, 4s, 8s, 16s (max 30s)
        val backoffMs = (1000L * (1 shl (reconnectAttempt - 1))).coerceAtMost(30000L)
        _sessionState.value = SessionState.Reconnecting(reconnectAttempt, maxReconnectAttempts, reason)

        HubLogger.log(
            LogLevel.WARN,
            LogCategory.LIVE,
            "Reconectando sessão Live (tentativa $reconnectAttempt/$maxReconnectAttempts) em ${backoffMs}ms. Motivo: $reason"
        )

        scope.launch {
            delay(backoffMs)
            if (_sessionState.value is SessionState.Reconnecting && !isIntentionallyClosed.get()) {
                reconnectSession(reason)
            }
        }
    }

    private fun reconnectSession(reason: String) {
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        scope.launch(Dispatchers.IO) {
            val keyResult = keystoreVault.getDecryptedKey("gemini")
            if (keyResult.isSuccess) {
                connectWebSocket(keyResult.getOrThrow())
            }
        }
    }

    private fun markConnectionStable() {
        stableConnectionJob?.cancel()
        stableConnectionJob = scope.launch {
            delay(30000L) // 30s stable
            if (_sessionState.value is SessionState.Connected) {
                reconnectAttempt = 0
                HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Conexão Live estabilizada por 30s+. Contador de reconexões zerado.")
            }
        }
    }

    private fun saveLiveMessageToHistory(role: String, content: String) {
        val convId = activeConversationId ?: return
        scope.launch(Dispatchers.IO) {
            try {
                if (role == "user") {
                    historyRepository.saveUserMessage(convId, content)
                } else {
                    historyRepository.saveAssistantMessage(convId, content, status = "COMPLETED")
                }
            } catch (e: Exception) {
                // Ignore history insert errors
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            stopLiveSession()
                        }
                    }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stopLiveSession() },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }

    fun stopLiveSession() {
        isIntentionallyClosed.set(true)
        _sessionState.value = SessionState.Disconnecting
        cleanup()
        _sessionState.value = SessionState.Disconnected("Encerrado pelo usuário")
    }

    /**
     * Single idempotent cleanup path for socket, audio engine, coroutines, and audio focus.
     */
    fun cleanup() {
        try {
            webSocket?.close(1000, "Clean shutdown")
        } catch (e: Exception) {
            // Ignore
        } finally {
            webSocket = null
        }

        audioEngine.stopAll()
        _isAudioActive.value = false
        _isModelThinking.value = false

        durationTimerJob?.cancel()
        durationTimerJob = null
        _callDurationSeconds.value = 0L

        stableConnectionJob?.cancel()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            // Ignore
        }

        try {
            LiveService.stopService(context)
        } catch (e: Exception) {
            // Ignore
        }

        HubLogger.log(LogLevel.INFO, LogCategory.LIVE, "Limpeza da sessão Live concluída (idempotente).")
    }
}
