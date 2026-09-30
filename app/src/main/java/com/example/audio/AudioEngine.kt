package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {

    companion object {
        const val RECORD_SAMPLE_RATE = 16000
        const val PLAYBACK_SAMPLE_RATE = 24000
        const val READ_CHUNK_SIZE_BYTES = 640 // 20ms at 16kHz 16-bit mono
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var audioTrack: AudioTrack? = null

    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)

    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    // Outbound Audio Queue with backpressure (drops oldest frames if WebSocket/network stalls)
    val outboundAudioQueue = Channel<ByteArray>(
        capacity = 15,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Inbound Audio Queue for playback
    private val inboundAudioQueue = Channel<ByteArray>(
        capacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Visualizer Amplitudes (0.0 to 1.0)
    private val _userAmplitude = MutableStateFlow(0.0f)
    val userAmplitude: StateFlow<Float> = _userAmplitude.asStateFlow()

    private val _modelAmplitude = MutableStateFlow(0.0f)
    val modelAmplitude: StateFlow<Float> = _modelAmplitude.asStateFlow()

    // Silence detection
    private var zeroSampleChunkCount = 0
    private var isMutedWarningActive = false

    @SuppressLint("MissingPermission")
    fun startCapture(onAudioFrame: (ByteArray) -> Unit, onError: (AppError) -> Unit) {
        if (isRecording.getAndSet(true)) return

        captureJob = scope.launch(Dispatchers.IO) {
            val minBufSize = AudioRecord.getMinBufferSize(
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufSize <= 0) {
                isRecording.set(false)
                onError(
                    AppError.Permission(
                        code = "ERR_AUDIO_RECORD_INIT",
                        userMessage = "O hardware de áudio do dispositivo não suporta a configuração 16kHz mono.",
                        technicalCause = "AudioRecord.getMinBufferSize returned $minBufSize"
                    )
                )
                return@launch
            }

            val bufferSize = (minBufSize * 2).coerceAtLeast(READ_CHUNK_SIZE_BYTES * 4)

            var recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    RECORD_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                HubLogger.log(
                    LogLevel.WARN,
                    LogCategory.AUDIO,
                    "Falha ao inicializar AudioRecord com VOICE_COMMUNICATION. Tentando MIC de fallback."
                )
                recorder = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        RECORD_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                isRecording.set(false)
                onError(
                    AppError.Permission(
                        code = "ERR_MIC_INIT_FAILED",
                        userMessage = "Não foi possível inicializar o gravador de áudio do microfone.",
                        technicalCause = "AudioRecord state is not initialized"
                    )
                )
                return@launch
            }

            audioRecord = recorder

            // Enable hardware DSP effects if available
            try {
                val audioSessionId = recorder.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = true
                        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "AcousticEchoCanceler ativado.")
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = true
                        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "NoiseSuppressor ativado.")
                    }
                }
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                        enabled = true
                        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "AutomaticGainControl ativado.")
                    }
                }
            } catch (e: Exception) {
                HubLogger.log(LogLevel.WARN, LogCategory.AUDIO, "Exceção ao configurar efeitos de áudio: ${e.message}")
            }

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                isRecording.set(false)
                onError(
                    AppError.Permission(
                        code = "ERR_MIC_START_FAILED",
                        userMessage = "Erro ao iniciar a gravação do microfone.",
                        technicalCause = e.message ?: "startRecording failed"
                    )
                )
                return@launch
            }

            HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "AudioRecord iniciado com sucesso (16kHz PCM 16-bit Mono).")

            val buffer = ByteArray(READ_CHUNK_SIZE_BYTES)
            var reinitAttempted = false

            while (isActive && isRecording.get()) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    val pcmCopy = buffer.copyOf(bytesRead)

                    // Calculate amplitude (RMS)
                    val rms = calculateRms(pcmCopy)
                    _userAmplitude.value = (rms / 10000.0f).coerceIn(0.0f, 1.0f)

                    // Silence detection (zero samples or near silence)
                    if (rms < 50) {
                        zeroSampleChunkCount++
                        if (zeroSampleChunkCount > 150 && !isMutedWarningActive) { // ~3 seconds
                            isMutedWarningActive = true
                            HubLogger.log(
                                LogLevel.WARN,
                                LogCategory.AUDIO,
                                "Microfone sem sinal por 3s consecutivos. Verifique se o microfone foi silenciado pelo sistema."
                            )
                        }
                    } else {
                        zeroSampleChunkCount = 0
                        isMutedWarningActive = false
                    }

                    onAudioFrame(pcmCopy)
                } else if (bytesRead < 0) {
                    HubLogger.log(LogLevel.WARN, LogCategory.AUDIO, "Erro no AudioRecord.read: code=$bytesRead")
                    if (!reinitAttempted) {
                        reinitAttempted = true
                        try {
                            recorder.stop()
                            recorder.startRecording()
                        } catch (e: Exception) {
                            break
                        }
                    } else {
                        onError(
                            AppError.Permission(
                                code = "ERR_MIC_READ_DEAD",
                                userMessage = "O gravador de áudio do microfone falhou repetidamente.",
                                technicalCause = "AudioRecord.read returned $bytesRead"
                            )
                        )
                        break
                    }
                }
            }
        }
    }

    fun startPlayback() {
        if (isPlaying.getAndSet(true)) return

        playbackJob = scope.launch(Dispatchers.IO) {
            val minBufSize = AudioTrack.getMinBufferSize(
                PLAYBACK_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSize = (minBufSize * 3).coerceAtLeast(4800)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(PLAYBACK_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            try {
                track.play()
            } catch (e: Exception) {
                HubLogger.log(LogLevel.ERROR, LogCategory.AUDIO, "Erro ao iniciar AudioTrack.play: ${e.message}")
            }

            HubLogger.log(
                LogLevel.INFO,
                LogCategory.AUDIO,
                "AudioTrack inicializado (USAGE_VOICE_COMMUNICATION, 24kHz PCM 16-bit Mono)."
            )

            var lastUnderrunCount = 0

            while (isActive && isPlaying.get()) {
                val chunk = inboundAudioQueue.receiveCatching().getOrNull() ?: break

                // Calculate amplitude for model response visualizer
                val rms = calculateRms(chunk)
                _modelAmplitude.value = (rms / 10000.0f).coerceIn(0.0f, 1.0f)

                var bytesWritten = 0
                while (bytesWritten < chunk.size && isActive && isPlaying.get()) {
                    val written = track.write(chunk, bytesWritten, chunk.size - bytesWritten)
                    if (written > 0) {
                        bytesWritten += written
                    } else {
                        break
                    }
                }

                // Check underruns
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val underruns = track.underrunCount
                    if (underruns > lastUnderrunCount) {
                        HubLogger.log(
                            LogLevel.WARN,
                            LogCategory.AUDIO,
                            "AudioTrack underrun detectado: total = $underruns (+${underruns - lastUnderrunCount})"
                        )
                        lastUnderrunCount = underruns
                    }
                }
            }
        }
    }

    fun enqueuePlaybackAudio(pcmData: ByteArray) {
        if (!isPlaying.get()) {
            startPlayback()
        }
        inboundAudioQueue.trySend(pcmData)
    }

    /**
     * Immediately pauses and flushes the AudioTrack buffer upon user barge-in / server interruption.
     */
    fun stopAndClearPlayback() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
            // Clear inbound queue
            while (inboundAudioQueue.tryReceive().isSuccess) {
                // Drain
            }
            _modelAmplitude.value = 0.0f
            HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Buffer de reprodução de áudio limpo imediatamente por interrupção (Barge-in).")
        } catch (e: Exception) {
            HubLogger.log(LogLevel.WARN, LogCategory.AUDIO, "Erro ao limpar buffer do AudioTrack: ${e.message}")
        }
    }

    fun stopAll() {
        isRecording.set(false)
        isPlaying.set(false)

        captureJob?.cancel()
        playbackJob?.cancel()

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // Ignore clean up errors
        } finally {
            audioRecord = null
        }

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            gainControl?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            echoCanceler = null
            noiseSuppressor = null
            gainControl = null
        }

        try {
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            audioTrack = null
        }

        _userAmplitude.value = 0.0f
        _modelAmplitude.value = 0.0f

        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Recursos de AudioEngine liberados com sucesso.")
    }

    private fun calculateRms(pcmData: ByteArray): Float {
        if (pcmData.size < 2) return 0.0f
        var sum = 0.0
        val numSamples = pcmData.size / 2

        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sum += sample * sample
        }

        val mean = sum / numSamples
        return sqrt(mean).toFloat()
    }
}
