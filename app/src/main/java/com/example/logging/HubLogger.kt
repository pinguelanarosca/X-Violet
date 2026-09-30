package com.example.logging

import android.content.Context
import android.util.Log
import com.example.storage.HubDatabase
import com.example.storage.entities.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object HubLogger {

    private const val TAG = "HubCore"
    private const val MAX_LOGS_RETENTION = 2000
    private const val BATCH_SIZE = 50
    private const val FLUSH_INTERVAL_MS = 500L

    private var database: HubDatabase? = null
    private var loggerScope: CoroutineScope? = null
    private var workerJob: Job? = null
    private val logChannel = Channel<LogEntryEntity>(capacity = 1000)

    // Rate limiter for high-frequency logs (e.g. audio streaming ticks, camera frame ticks)
    private val highFrequencyThrottleMap = ConcurrentHashMap<String, Long>()
    private val highFrequencyCounterMap = ConcurrentHashMap<String, AtomicInteger>()

    fun init(db: HubDatabase, scope: CoroutineScope) {
        this.database = db
        this.loggerScope = scope
        startBatchWorker(scope, db)
    }

    private fun startBatchWorker(scope: CoroutineScope, db: HubDatabase) {
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.IO) {
            val batch = ArrayList<LogEntryEntity>(BATCH_SIZE)
            var lastFlushTime = System.currentTimeMillis()

            while (isActive) {
                // Read from channel with timeout
                val item = logChannel.tryReceive().getOrNull()
                if (item != null) {
                    batch.add(item)
                }

                val now = System.currentTimeMillis()
                val shouldFlush = batch.size >= BATCH_SIZE || (batch.isNotEmpty() && (now - lastFlushTime >= FLUSH_INTERVAL_MS))

                if (shouldFlush) {
                    try {
                        db.logDao().insertBatch(batch)
                        db.logDao().trimToLimit(MAX_LOGS_RETENTION)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Falha ao gravar lote de logs no banco: ${e.message}")
                    }
                    batch.clear()
                    lastFlushTime = now
                }

                if (item == null) {
                    delay(50L)
                }
            }
        }
    }

    fun log(
        level: LogLevel,
        category: LogCategory,
        message: String,
        data: Map<String, Any?>? = null,
        throwable: Throwable? = null,
        correlationId: String? = null
    ) {
        // High frequency sampling for AUDIO / LIVE / VISION ticks
        if (category == LogCategory.AUDIO || category == LogCategory.LIVE || category == LogCategory.VISION) {
            if (isHighFrequencySampled(category, message)) {
                return
            }
        }

        val sanitizedMessage = LogSanitizer.sanitize(message)
        val sanitizedThrowable = LogSanitizer.sanitizeThrowable(throwable)
        val sanitizedDataJson = if (data != null) {
            val builder = StringBuilder("{")
            var first = true
            for ((k, v) in data) {
                if (!first) builder.append(", ")
                first = false
                val sanitizedValue = LogSanitizer.sanitize(v?.toString() ?: "null")
                builder.append("\"$k\": \"$sanitizedValue\"")
            }
            builder.append("}")
            builder.toString()
        } else null

        // 1. Mirror to system Logcat (sanitized)
        val logcatMsg = buildString {
            append("[$category] ")
            if (correlationId != null) append("($correlationId) ")
            append(sanitizedMessage)
            if (sanitizedDataJson != null) append(" | Data: $sanitizedDataJson")
            if (sanitizedThrowable != null) append("\n$sanitizedThrowable")
        }

        try {
            when (level) {
                LogLevel.DEBUG -> Log.d(TAG, logcatMsg)
                LogLevel.INFO -> Log.i(TAG, logcatMsg)
                LogLevel.WARN -> Log.w(TAG, logcatMsg)
                LogLevel.ERROR -> Log.e(TAG, logcatMsg)
            }
        } catch (ignored: Throwable) {
            // Running in JVM test environment where android.util.Log may not be linked
        }

        // 2. Queue for database batch writing
        val entry = LogEntryEntity(
            timestamp = System.currentTimeMillis(),
            level = level.name,
            category = category.name,
            message = sanitizedMessage,
            dataJson = sanitizedDataJson,
            throwableSanitized = sanitizedThrowable,
            correlationId = correlationId
        )

        logChannel.trySend(entry)

        // 3. Mirror breadcrumb and non-fatal exceptions to Firebase Crashlytics
        try {
            if (level == LogLevel.ERROR) {
                if (throwable != null) {
                    CrashlyticsManager.recordException(throwable, "NonFatal_[$category]_$sanitizedMessage")
                } else {
                    CrashlyticsManager.log("[ERROR][$category] $sanitizedMessage")
                }
            } else if (level == LogLevel.WARN || level == LogLevel.INFO) {
                CrashlyticsManager.log("[$level][$category] $sanitizedMessage")
            }
        } catch (_: Throwable) {}
    }

    fun d(category: LogCategory, message: String, correlationId: String? = null) {
        log(LogLevel.DEBUG, category, message, correlationId = correlationId)
    }

    fun i(category: LogCategory, message: String, correlationId: String? = null) {
        log(LogLevel.INFO, category, message, correlationId = correlationId)
    }

    fun w(category: LogCategory, message: String, throwable: Throwable? = null, correlationId: String? = null) {
        log(LogLevel.WARN, category, message, throwable = throwable, correlationId = correlationId)
    }

    fun e(category: LogCategory, message: String, throwable: Throwable? = null, correlationId: String? = null) {
        log(LogLevel.ERROR, category, message, throwable = throwable, correlationId = correlationId)
    }

    private fun isHighFrequencySampled(category: LogCategory, message: String): Boolean {
        val key = "${category.name}_${message.take(20)}"
        val now = System.currentTimeMillis()
        val lastLogged = highFrequencyThrottleMap[key] ?: 0L
        if (now - lastLogged < 500L) {
            val counter = highFrequencyCounterMap.computeIfAbsent(key) { AtomicInteger(0) }
            counter.incrementAndGet()
            return true
        }
        val skippedCount = highFrequencyCounterMap[key]?.getAndSet(0) ?: 0
        highFrequencyThrottleMap[key] = now
        if (skippedCount > 0) {
            // Note that entries were sampled
            logChannel.trySend(
                LogEntryEntity(
                    timestamp = now,
                    level = LogLevel.DEBUG.name,
                    category = category.name,
                    message = "[Amostrado: $skippedCount eventos similares suprimidos]",
                    dataJson = null,
                    throwableSanitized = null,
                    correlationId = null
                )
            )
        }
        return false
    }

    fun getLogsFlow(): Flow<List<LogEntryEntity>> {
        return database?.logDao()?.getRecentLogs() ?: emptyFlow()
    }

    suspend fun clearLogs() {
        database?.logDao()?.clearAll()
    }

    suspend fun exportLogsAsText(context: Context): File {
        val logs = database?.logDao()?.getRecentLogsList(MAX_LOGS_RETENTION) ?: emptyList()
        val exportFile = File(context.cacheDir, "hub_logs_${System.currentTimeMillis()}.txt")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        FileOutputStream(exportFile).use { fos ->
            fos.bufferedWriter().use { writer ->
                writer.write("=== HUB SISTEMA MULTIMODAL - LOGS EXPORTADOS ===\n")
                writer.write("Data de exportação: ${dateFormat.format(Date())}\n")
                writer.write("Total de entradas: ${logs.size}\n\n")

                for (entry in logs) {
                    val dateStr = dateFormat.format(Date(entry.timestamp))
                    val corr = if (entry.correlationId != null) "[Corr: ${entry.correlationId}] " else ""
                    writer.write("[$dateStr] [${entry.level}] [${entry.category}] $corr${entry.message}\n")
                    if (entry.dataJson != null) {
                        writer.write("    Dados: ${entry.dataJson}\n")
                    }
                    if (entry.throwableSanitized != null) {
                        writer.write("    Causa técnica:\n${entry.throwableSanitized}\n")
                    }
                }
            }
        }
        return exportFile
    }

    suspend fun exportLogsAsJson(context: Context): File {
        val logs = database?.logDao()?.getRecentLogsList(MAX_LOGS_RETENTION) ?: emptyList()
        val exportFile = File(context.cacheDir, "hub_logs_${System.currentTimeMillis()}.json")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        FileOutputStream(exportFile).use { fos ->
            fos.bufferedWriter().use { writer ->
                writer.write("[\n")
                logs.forEachIndexed { index, entry ->
                    writer.write("  {\n")
                    writer.write("    \"id\": ${entry.id},\n")
                    writer.write("    \"timestamp\": ${entry.timestamp},\n")
                    writer.write("    \"formattedDate\": \"${dateFormat.format(Date(entry.timestamp))}\",\n")
                    writer.write("    \"level\": \"${entry.level}\",\n")
                    writer.write("    \"category\": \"${entry.category}\",\n")
                    writer.write("    \"message\": \"${escapeJson(entry.message)}\",\n")
                    writer.write("    \"correlationId\": ${if (entry.correlationId != null) "\"${entry.correlationId}\"" else "null"},\n")
                    writer.write("    \"dataJson\": ${if (entry.dataJson != null) "\"${escapeJson(entry.dataJson)}\"" else "null"},\n")
                    writer.write("    \"throwable\": ${if (entry.throwableSanitized != null) "\"${escapeJson(entry.throwableSanitized)}\"" else "null"}\n")
                    writer.write("  }${if (index < logs.size - 1) "," else ""}\n")
                }
                writer.write("]\n")
            }
        }
        return exportFile
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
