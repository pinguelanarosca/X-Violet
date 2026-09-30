package com.example.security

import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import com.example.logging.LogSanitizer
import com.example.storage.dao.VaultDao
import com.example.storage.entities.VaultCredentialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KeystoreVault(
    private val vaultDao: VaultDao,
    private val blobStorage: VaultBlobStorage,
    private val cryptoEngine: CryptoEngine = AndroidKeyStoreCryptoEngine()
) {
    val allCredentialsFlow: Flow<List<VaultCredentialEntity>> = vaultDao.getAllCredentialsFlow()

    suspend fun reconcileOrphanBlobs(): List<String> = withContext(Dispatchers.IO) {
        val onDiskProviders = blobStorage.listAllBlobProviders()
        val reconciled = mutableListOf<String>()
        for (providerId in onDiskProviders) {
            val existing = vaultDao.getCredential(providerId)
            if (existing == null) {
                val recoveredEntity = VaultCredentialEntity(
                    providerId = providerId,
                    maskedKey = "••••????",
                    status = STATUS_NEEDS_REENTRY,
                    updatedAt = System.currentTimeMillis()
                )
                vaultDao.insertOrUpdate(recoveredEntity)
                reconciled.add(providerId)
                HubLogger.log(
                    level = LogLevel.WARN,
                    category = LogCategory.SECURITY,
                    message = "Reconciliação: blob órfão encontrado no disco para '$providerId'. Metadados recriados no banco com status '$STATUS_NEEDS_REENTRY'."
                )
            }
        }
        reconciled
    }

    suspend fun saveKey(providerId: String, rawApiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val sanitizedKey = rawApiKey.trim().replace("\n", "").replace("\r", "")
        if (sanitizedKey.length < 5) {
            return@withContext Result.failure(
                AppError.Auth(
                    code = "ERR_KEY_TOO_SHORT",
                    userMessage = "A chave de API informada é muito curta ou inválida.",
                    technicalCause = "Comprimento da chave sanitizada é menor que 5 caracteres"
                )
            )
        }

        try {
            val plainTextBytes = sanitizedKey.toByteArray(Charsets.UTF_8)
            val aad = providerId.toByteArray(Charsets.UTF_8)

            // 1. Encrypt via CryptoEngine (hardware-backed AndroidKeyStore in production)
            val payload = cryptoEngine.encrypt(plainTextBytes, aad)

            // 2. Persist encrypted blob atomically to dedicated file storage (outside Room SQLite)
            blobStorage.saveBlob(providerId, payload)

            // 3. Save unclassified public metadata in Room
            val maskedPreview = LogSanitizer.maskKey(sanitizedKey)
            val entity = VaultCredentialEntity(
                providerId = providerId,
                maskedKey = maskedPreview,
                status = STATUS_ACTIVE,
                updatedAt = System.currentTimeMillis()
            )
            vaultDao.insertOrUpdate(entity)

            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.SECURITY,
                message = "Chave para o provedor '$providerId' cifrada e salva com sucesso no cofre ($maskedPreview)."
            )

            Result.success(Unit)
        } catch (e: AppError) {
            Result.failure(e)
        } catch (t: Throwable) {
            val appError = AppError.Crypto(
                code = "ERR_KEYSTORE_ENCRYPT",
                userMessage = "Falha ao cifrar a chave no Keystore.",
                technicalCause = "Erro de cifragem AES-GCM: ${t.message ?: t.javaClass.simpleName}"
            )
            HubLogger.log(
                level = LogLevel.ERROR,
                category = LogCategory.SECURITY,
                message = "Erro ao salvar credencial no cofre",
                throwable = t
            )
            Result.failure(appError)
        }
    }

    suspend fun getDecryptedKey(providerId: String): Result<String> = withContext(Dispatchers.IO) {
        // Read blob from disk (dedicated file storage)
        val payload = blobStorage.readBlob(providerId)
            ?: return@withContext Result.failure(
                AppError.Auth(
                    code = "ERR_KEY_NOT_FOUND",
                    userMessage = "Chave de API não configurada para o provedor '$providerId'.",
                    technicalCause = "Nenhum blob encontrado para providerId=$providerId"
                )
            )

        // Check if marked as needing reentry in database
        val credential = vaultDao.getCredential(providerId)
        if (credential?.status == STATUS_NEEDS_REENTRY) {
            return@withContext Result.failure(
                AppError.Crypto(
                    code = "ERR_KEY_NEEDS_REENTRY",
                    userMessage = "A chave do provedor '$providerId' precisa ser inserida novamente no cofre.",
                    technicalCause = "Credencial marcada como NEEDS_REENTRY devido a falha de decifragem anterior"
                )
            )
        }

        try {
            val aad = providerId.toByteArray(Charsets.UTF_8)
            val decryptedBytes = cryptoEngine.decrypt(payload.cipherText, payload.iv, aad)
            val decryptedString = String(decryptedBytes, Charsets.UTF_8)

            // If Room was recreated after corruption and metadata was lost, restore it automatically!
            if (credential == null) {
                val restored = VaultCredentialEntity(
                    providerId = providerId,
                    maskedKey = LogSanitizer.maskKey(decryptedString),
                    status = STATUS_ACTIVE,
                    updatedAt = System.currentTimeMillis()
                )
                vaultDao.insertOrUpdate(restored)
            }

            Result.success(decryptedString)
        } catch (e: Throwable) {
            // Decryption failure (AEADBadTagException, KeyPermanentlyInvalidatedException, etc.)
            // Rule 12: DO NOT crash and DO NOT silently delete! Mark as NEEDS_REENTRY!
            vaultDao.updateStatus(providerId, STATUS_NEEDS_REENTRY)

            HubLogger.log(
                level = LogLevel.ERROR,
                category = LogCategory.SECURITY,
                message = "Falha de decifragem para '$providerId'. Credencial marcada como 'precisa ser inserida de novo'.",
                throwable = e
            )

            val appError = AppError.Crypto(
                code = "ERR_DECRYPT_FAILED",
                userMessage = "Falha de integridade criptográfica ao decifrar a chave do provedor '$providerId'. A chave precisa ser inserida novamente.",
                technicalCause = "Erro ao decifrar com Keystore (${e.javaClass.simpleName}): ${e.message}"
            )
            Result.failure(appError)
        }
    }

    suspend fun revalidateKey(providerId: String): Result<String> = withContext(Dispatchers.IO) {
        val payload = blobStorage.readBlob(providerId)
            ?: return@withContext Result.failure(
                AppError.Auth(
                    code = "ERR_KEY_NOT_FOUND",
                    userMessage = "Chave de API não configurada para o provedor '$providerId'.",
                    technicalCause = "Nenhum blob encontrado para providerId=$providerId"
                )
            )

        try {
            val aad = providerId.toByteArray(Charsets.UTF_8)
            val decryptedBytes = cryptoEngine.decrypt(payload.cipherText, payload.iv, aad)
            val decryptedString = String(decryptedBytes, Charsets.UTF_8)

            val entity = VaultCredentialEntity(
                providerId = providerId,
                maskedKey = LogSanitizer.maskKey(decryptedString),
                status = STATUS_ACTIVE,
                updatedAt = System.currentTimeMillis()
            )
            vaultDao.insertOrUpdate(entity)
            Result.success(decryptedString)
        } catch (e: Throwable) {
            vaultDao.updateStatus(providerId, STATUS_NEEDS_REENTRY)
            val appError = AppError.Crypto(
                code = "ERR_DECRYPT_FAILED",
                userMessage = "Falha de integridade ao revalidar a chave do provedor '$providerId'.",
                technicalCause = "Erro ao decifrar (${e.javaClass.simpleName}): ${e.message}"
            )
            Result.failure(appError)
        }
    }

    suspend fun simulateDecryptionFailure(providerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!blobStorage.hasBlob(providerId)) {
            return@withContext Result.failure(
                AppError.Auth(
                    code = "ERR_KEY_NOT_FOUND",
                    userMessage = "Chave não encontrada para simular falha.",
                    technicalCause = "providerId=$providerId não possui blob gravado"
                )
            )
        }

        blobStorage.corruptBlobForTesting(providerId)
        HubLogger.log(
            level = LogLevel.WARN,
            category = LogCategory.SECURITY,
            message = "Simulação: blob criptográfico do provedor '$providerId' corrompido propositalmente para teste de resiliência."
        )
        Result.success(Unit)
    }

    suspend fun deleteKey(providerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            blobStorage.deleteBlob(providerId)
            vaultDao.deleteCredential(providerId)
            HubLogger.log(
                level = LogLevel.INFO,
                category = LogCategory.SECURITY,
                message = "Chave do provedor '$providerId' excluída do cofre por solicitação do usuário."
            )
            Result.success(Unit)
        } catch (t: Throwable) {
            val appError = AppError.Storage(
                code = "ERR_DELETE_KEY",
                userMessage = "Falha ao excluir a chave do banco local.",
                technicalCause = t.message ?: "Erro ao deletar credencial"
            )
            Result.failure(appError)
        }
    }

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_NEEDS_REENTRY = "NEEDS_REENTRY"
        const val STATUS_REVOKED = "REVOKED"
    }
}
