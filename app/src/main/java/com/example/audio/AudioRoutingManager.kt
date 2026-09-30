package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioDeviceItem(
    val id: Int,
    val name: String,
    val type: Int,
    val isOutput: Boolean,
    val nativeDeviceInfo: AudioDeviceInfo?
)

class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableDevices = MutableStateFlow<List<AudioDeviceItem>>(emptyList())
    val availableDevices: StateFlow<List<AudioDeviceItem>> = _availableDevices.asStateFlow()

    private val _activeCommunicationDevice = MutableStateFlow<AudioDeviceItem?>(null)
    val activeCommunicationDevice: StateFlow<AudioDeviceItem?> = _activeCommunicationDevice.asStateFlow()

    private val _lastRoutingEventMessage = MutableStateFlow("")
    val lastRoutingEventMessage: StateFlow<String> = _lastRoutingEventMessage.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableDevices()
            HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Novo dispositivo de áudio conectado ao sistema.")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableDevices()
            checkActiveDeviceDisconnect(removedDevices)
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateAvailableDevices()
    }

    fun updateAvailableDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        val items = mutableListOf<AudioDeviceItem>()

        outputs.forEach { dev ->
            items.add(
                AudioDeviceItem(
                    id = dev.id,
                    name = getDeviceTypeName(dev.type, isOutput = true),
                    type = dev.type,
                    isOutput = true,
                    nativeDeviceInfo = dev
                )
            )
        }

        inputs.forEach { dev ->
            items.add(
                AudioDeviceItem(
                    id = dev.id,
                    name = getDeviceTypeName(dev.type, isOutput = false),
                    type = dev.type,
                    isOutput = false,
                    nativeDeviceInfo = dev
                )
            )
        }

        _availableDevices.value = items.distinctBy { "${it.type}_${it.isOutput}" }
    }

    fun selectCommunicationDevice(deviceItem: AudioDeviceItem): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nativeDev = deviceItem.nativeDeviceInfo
            if (nativeDev != null) {
                val success = audioManager.setCommunicationDevice(nativeDev)
                if (success) {
                    _activeCommunicationDevice.value = deviceItem
                    val msg = "Rota de áudio alterada para: ${deviceItem.name}"
                    _lastRoutingEventMessage.value = msg
                    HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, msg)

                    if (deviceItem.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        HubLogger.log(
                            LogLevel.WARN,
                            LogCategory.AUDIO,
                            "Fones Bluetooth em modo de chamada (SCO) utilizam amostragem de áudio reduzida pela HAL do Bluetooth."
                        )
                    }
                    return true
                }
            }
        } else {
            // Legacy audio routing fallback
            if (deviceItem.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            } else if (deviceItem.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
            _activeCommunicationDevice.value = deviceItem
            return true
        }
        return false
    }

    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        _activeCommunicationDevice.value = null
        _lastRoutingEventMessage.value = "Rota de comunicação redefinida para o padrão do sistema."
        HubLogger.log(LogLevel.INFO, LogCategory.AUDIO, "Rota de áudio redefinida para o padrão do sistema.")
    }

    private fun checkActiveDeviceDisconnect(removedDevices: Array<out AudioDeviceInfo>?) {
        val currentActive = _activeCommunicationDevice.value ?: return
        val removed = removedDevices?.any { it.id == currentActive.id || it.type == currentActive.type } ?: false

        if (removed) {
            clearCommunicationDevice()
            val warn = "O dispositivo de áudio ativo '${currentActive.name}' foi desconectado. Retornando ao padrão de fábrica."
            _lastRoutingEventMessage.value = warn
            HubLogger.log(LogLevel.WARN, LogCategory.AUDIO, warn)
        }
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun getDeviceTypeName(type: Int, isOutput: Boolean): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular Interno"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-Falante Interno"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com Fio (com Mic)"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Fone com Fio"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Fone Bluetooth (Modo Chamada)"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Fone Bluetooth (A2DP)"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfone do Dispositivo"
            else -> if (isOutput) "Dispositivo de Saída ($type)" else "Dispositivo de Entrada ($type)"
        }
    }
}
