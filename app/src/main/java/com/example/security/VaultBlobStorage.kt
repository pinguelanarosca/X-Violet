package com.example.security

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VaultBlobStorage(
    private val baseDir: File
) {
    constructor(context: Context) : this(File(context.filesDir, "vault_blobs"))

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    private val validProviderIdRegex = Regex("^[a-zA-Z0-9_-]{1,64}$")

    fun validateProviderId(providerId: String) {
        if (providerId.isEmpty()) {
            throw IllegalArgumentException("providerId não pode ser vazio")
        }
        if (providerId.length > 64) {
            throw IllegalArgumentException("providerId excede o tamanho máximo de 64 caracteres: ${providerId.length}")
        }
        if (!validProviderIdRegex.matches(providerId)) {
            throw IllegalArgumentException("providerId contém caracteres inválidos. Permitidos apenas [a-zA-Z0-9_-]: '$providerId'")
        }
    }

    private fun getBlobFile(providerId: String): File {
        validateProviderId(providerId)
        val file = File(baseDir, "$providerId.vault").canonicalFile
        val canonicalBase = baseDir.canonicalFile
        if (file.parentFile != canonicalBase) {
            throw SecurityException("Tentativa de path traversal detectada para providerId: '$providerId'")
        }
        return file
    }

    @Synchronized
    fun saveBlob(providerId: String, payload: EncryptedPayload) {
        val targetFile = getBlobFile(providerId)
        val tempFile = File(baseDir, "$providerId.vault.tmp")

        FileOutputStream(tempFile).use { fos ->
            // Write 12 bytes IV followed by CipherText
            fos.write(payload.iv)
            fos.write(payload.cipherText)
            fos.fd.sync() // Ensure flushed to disk
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            // Fallback copy if rename fails on some file systems
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    @Synchronized
    fun readBlob(providerId: String): EncryptedPayload? {
        val file = getBlobFile(providerId)
        if (!file.exists() || file.length() < GCM_IV_LENGTH_BYTES) {
            return null
        }

        val totalBytes = file.readBytes()
        if (totalBytes.size <= GCM_IV_LENGTH_BYTES) {
            return null
        }

        val iv = totalBytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = totalBytes.copyOfRange(GCM_IV_LENGTH_BYTES, totalBytes.size)

        return EncryptedPayload(iv = iv, cipherText = cipherText)
    }

    @Synchronized
    fun deleteBlob(providerId: String) {
        val file = getBlobFile(providerId)
        if (file.exists()) {
            file.delete()
        }
        val tempFile = File(baseDir, "$providerId.vault.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Synchronized
    fun hasBlob(providerId: String): Boolean {
        val file = getBlobFile(providerId)
        return file.exists() && file.length() > GCM_IV_LENGTH_BYTES
    }

    @Synchronized
    fun corruptBlobForTesting(providerId: String): Boolean {
        val file = getBlobFile(providerId)
        if (!file.exists()) return false
        val corruptedBytes = "CORRUPTED_CIPHERTEXT_FOR_TESTING".toByteArray(Charsets.UTF_8)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES) { 0 }
        saveBlob(providerId, EncryptedPayload(iv = iv, cipherText = corruptedBytes))
        return true
    }

    @Synchronized
    fun listAllBlobProviders(): List<String> {
        return baseDir.listFiles { file -> file.name.endsWith(".vault") }
            ?.map { it.name.removeSuffix(".vault") }
            ?: emptyList()
    }

    companion object {
        const val GCM_IV_LENGTH_BYTES = 12
    }
}
