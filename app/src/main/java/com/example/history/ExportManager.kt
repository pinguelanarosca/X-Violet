package com.example.history

import android.content.Context
import android.net.Uri
import com.example.errors.AppError
import com.example.storage.entities.ConversationEntity
import com.example.storage.entities.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(private val context: Context) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())

    /**
     * Builds plain text transcript representation.
     */
    fun buildTranscript(
        conversation: ConversationEntity,
        messages: List<MessageEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("=== HUB MULTIMODAL - TRANSCRIÇÃO DA CONVERSA ===\n")
        sb.append("Título: ${conversation.title}\n")
        sb.append("Provedor: ${conversation.providerId} | Modelo: ${conversation.modelId}\n")
        sb.append("Criado em: ${isoFormat.format(Date(conversation.createdAt))}\n")
        sb.append("Total de Mensagens: ${messages.size}\n")
        sb.append("================================================\n\n")

        messages.forEach { msg ->
            val time = timeFormat.format(Date(msg.createdAt))
            val roleLabel = when (msg.role) {
                "user" -> "Você"
                "assistant" -> "Modelo (${conversation.modelId})"
                "system" -> "Sistema"
                else -> msg.role.replaceFirstChar { it.uppercase() }
            }
            sb.append("[$time] $roleLabel:\n")
            sb.append("${msg.content}\n\n")
        }

        return sb.toString()
    }

    /**
     * Builds JSON export string.
     */
    fun buildJsonExport(
        conversation: ConversationEntity,
        messages: List<MessageEntity>
    ): String {
        val root = buildJsonObject {
            put("version", "1.0")
            put("hubApp", "Hub Multimodal")
            putJsonObject("conversation") {
                put("id", conversation.id)
                put("title", conversation.title)
                put("providerId", conversation.providerId)
                put("modelId", conversation.modelId)
                put("createdAtIso", isoFormat.format(Date(conversation.createdAt)))
                put("updatedAtIso", isoFormat.format(Date(conversation.updatedAt)))
                put("totalMessages", messages.size)
            }
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("id", msg.id)
                        put("role", msg.role)
                        put("content", msg.content)
                        put("status", msg.status)
                        put("timestampIso", isoFormat.format(Date(msg.createdAt)))
                    }
                }
            }
        }
        return root.toString()
    }

    /**
     * Streams content to a temporary file first, then writes atomically to the destination Uri.
     */
    suspend fun exportToFileUri(
        destinationUri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.tmp")
        try {
            // 1. Write to temporary file first
            FileOutputStream(tempFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }

            // 2. Stream copy temp file to destination Uri via ContentResolver
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(
                    AppError.Storage(
                        code = "ERR_EXPORT_URI_OPEN",
                        userMessage = "Não foi possível abrir o destino para salvar a conversa.",
                        technicalCause = "openOutputStream returned null for uri: $destinationUri"
                    )
                )

            outputStream.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                AppError.Storage(
                    code = "ERR_EXPORT_WRITE_FAILED",
                    userMessage = "Falha ao gravar arquivo de exportação: ${e.message ?: "Erro de E/S"}",
                    technicalCause = "Exception during export: ${e.javaClass.simpleName}: ${e.message}"
                )
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
