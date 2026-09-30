package com.example.live

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel

class HyperOsBatteryManager(private val context: Context) {

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            HubLogger.log(LogLevel.INFO, LogCategory.APP, "Abrindo lista de otimização de bateria do sistema.")
        } catch (e: Exception) {
            HubLogger.log(LogLevel.WARN, LogCategory.APP, "Não foi possível abrir a tela de otimização de bateria: ${e.message}")
        }
    }

    fun getXiaomiHyperOsInstructions(): String {
        return """
            Instruções para Xiaomi HyperOS / MIUI:
            1. Abra as Configurações do seu aparelho -> Apps -> Gerenciar Apps.
            2. Localize este aplicativo ('Hub Multimodal').
            3. Ative a opção 'Início Automático' (Autostart).
            4. Em 'Economia de bateria', selecione 'Sem restrições'.
            5. Nas tarefas recentes do HyperOS, toque e segure no aplicativo e clique no ícone de Cadeado para mantê-lo fixo na memória.
        """.trimIndent()
    }
}
