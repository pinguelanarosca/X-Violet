package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioRoutingManager
import com.example.live.HyperOsBatteryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveServiceAndRoutingTest {

    private lateinit var context: Context
    private lateinit var audioRoutingManager: AudioRoutingManager
    private lateinit var batteryManager: HyperOsBatteryManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioRoutingManager = AudioRoutingManager(context)
        batteryManager = HyperOsBatteryManager(context)
    }

    @Test
    fun testAudioRoutingManagerInitialization() {
        assertNotNull("AudioRoutingManager deve ser instanciado", audioRoutingManager)
        val devices = audioRoutingManager.availableDevices.value
        assertNotNull("Lista de dispositivos deve ser inicializada", devices)
    }

    @Test
    fun testHyperOsBatteryManagerInstructions() {
        val instructions = batteryManager.getXiaomiHyperOsInstructions()
        assertTrue("Instruções HyperOS devem conter passos de Autostart e economia de bateria", instructions.contains("Início Automático"))
        assertTrue("Instruções devem mencionar Xiaomi/MIUI", instructions.contains("Xiaomi HyperOS"))
    }

    @Test
    fun testClearCommunicationDeviceDoesNotCrash() {
        audioRoutingManager.clearCommunicationDevice()
        assertNull("Dispositivo de comunicação ativo deve ser nulo após reset", audioRoutingManager.activeCommunicationDevice.value)
    }
}
