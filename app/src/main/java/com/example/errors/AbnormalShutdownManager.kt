package com.example.errors

import android.content.Context
import com.example.logging.CrashHandler
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import java.io.File

class AbnormalShutdownManager(private val context: Context) {

    private val flagFile = File(context.filesDir, FLAG_FILE_NAME)

    var wasAbnormalShutdown: Boolean = false
        private set

    var lastCrashReport: String? = null
        private set

    fun onAppStart() {
        if (flagFile.exists()) {
            wasAbnormalShutdown = true
            lastCrashReport = CrashHandler.getLastCrashReport(context)
            HubLogger.log(
                level = LogLevel.WARN,
                category = LogCategory.APP,
                message = "Encerramento anormal detectado da sessão anterior. Flag ativa encontrada."
            )
        } else {
            wasAbnormalShutdown = false
        }

        // Write the flag for the current session
        try {
            flagFile.writeText("RUNNING_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun onAppCleanExit() {
        try {
            if (flagFile.exists()) {
                flagFile.delete()
            }
            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.APP,
                message = "Encerramento normal e limpo da sessão."
            )
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun clearFlag() {
        try {
            if (flagFile.exists()) {
                flagFile.delete()
            }
            wasAbnormalShutdown = false
        } catch (e: Exception) {
            // Non-critical
        }
    }

    companion object {
        private const val FLAG_FILE_NAME = "session_active.flag"
    }
}
