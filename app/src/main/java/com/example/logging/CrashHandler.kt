package com.example.logging

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashReportSync(thread, throwable)
            CrashlyticsManager.recordException(throwable, "UncaughtException_${thread.name}")
        } catch (e: Throwable) {
            // Failsafe: never crash inside the crash handler
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReportSync(thread: Thread, throwable: Throwable) {
        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val timestampStr = dateFormat.format(Date())

        val sanitizedTrace = LogSanitizer.sanitizeThrowable(throwable) ?: throwable.toString()
        val sanitizedMsg = LogSanitizer.sanitize(throwable.message ?: "Sem mensagem")

        @Suppress("DEPRECATION")
        val threadId = thread.id
        val reportContent = buildString {
            appendLine("=== HUB CRASH REPORT ===")
            appendLine("Timestamp: $timestampStr")
            appendLine("Thread: ${thread.name} (id: $threadId)")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Sanitized Message: $sanitizedMsg")
            appendLine("--- STACK TRACE ---")
            appendLine(sanitizedTrace)
            appendLine("=== END OF REPORT ===")
        }

        FileOutputStream(crashFile, false).use { fos ->
            PrintWriter(fos).use { pw ->
                pw.write(reportContent)
                pw.flush()
            }
        }
    }

    companion object {
        const val CRASH_FILE_NAME = "last_crash.txt"

        fun getLastCrashReport(context: Context): String? {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            return if (file.exists() && file.length() > 0) {
                try {
                    file.readText()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        fun clearLastCrashReport(context: Context) {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
