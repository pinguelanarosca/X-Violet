package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedPayload(
    val iv: ByteArray,
    val cipherText: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + cipherText.contentHashCode()
        return result
    }
}

interface CryptoEngine {
    fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedPayload
    fun decrypt(cipherText: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray
}

class AndroidKeyStoreCryptoEngine(
    private val masterKeyAlias: String = MASTER_KEY_ALIAS
) : CryptoEngine {

    private val secureRandom = SecureRandom()

    private val keyStore: KeyStore by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (t: Throwable) {
            throw AppError.Crypto(
                code = "ERR_KEYSTORE_INIT",
                userMessage = "O provedor AndroidKeyStore não pôde ser inicializado no hardware.",
                technicalCause = "Falha ao instanciar AndroidKeyStore: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        try {
            if (!keyStore.containsAlias(masterKeyAlias)) {
                generateMasterKey()
            }
            val entry = keyStore.getEntry(masterKeyAlias, null) as? KeyStore.SecretKeyEntry
                ?: throw AppError.Crypto(
                    code = "ERR_KEYSTORE_ENTRY_NULL",
                    userMessage = "A chave mestra de segurança não foi encontrada no AndroidKeyStore.",
                    technicalCause = "KeyStore.getEntry retornou null para alias $masterKeyAlias"
                )
            return entry.secretKey
        } catch (e: AppError) {
            throw e
        } catch (t: Throwable) {
            throw AppError.Crypto(
                code = "ERR_KEYSTORE_ACCESS",
                userMessage = "Falha ao acessar o hardware de segurança (AndroidKeyStore).",
                technicalCause = "Erro ao recuperar master key: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun generateMasterKey() {
        val keyGenerator = try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        } catch (t: Throwable) {
            throw AppError.Crypto(
                code = "ERR_KEYSTORE_NO_AES",
                userMessage = "O hardware não suporta o algoritmo AES no AndroidKeyStore.",
                technicalCause = "KeyGenerator.getInstance(AES, AndroidKeyStore) falhou: ${t.message}"
            )
        }

        var isStrongBox = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val spec = KeyGenParameterSpec.Builder(
                    masterKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
                isStrongBox = true
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.SECURITY,
                    message = "Chave mestra AES-256-GCM criada com sucesso no StrongBox seguro."
                )
            } catch (e: StrongBoxUnavailableException) {
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.SECURITY,
                    message = "StrongBox não disponível neste modelo de hardware. Utilizando TEE/Keystore padrão."
                )
            } catch (e: Throwable) {
                HubLogger.log(
                    level = LogLevel.WARN,
                    category = LogCategory.SECURITY,
                    message = "Tentativa de StrongBox falhou (${e.message}). Revertendo para Keystore padrão de hardware."
                )
            }
        }

        if (!isStrongBox) {
            try {
                val standardSpec = KeyGenParameterSpec.Builder(
                    masterKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(standardSpec)
                keyGenerator.generateKey()
                HubLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.SECURITY,
                    message = "Chave mestra AES-256-GCM criada no Android Keystore de hardware."
                )
            } catch (t: Throwable) {
                throw AppError.Crypto(
                    code = "ERR_KEYSTORE_GENERATE_FAILED",
                    userMessage = "Falha ao gerar chave mestra no AndroidKeyStore.",
                    technicalCause = "Erro na geracao da master key no Keystore: ${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

    override fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedPayload {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)
        cipher.updateAAD(aad)

        val cipherText = cipher.doFinal(plainText)
        return EncryptedPayload(iv = iv, cipherText = cipherText)
    }

    override fun decrypt(cipherText: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)
        cipher.updateAAD(aad)

        return cipher.doFinal(cipherText)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "HubMasterKeyAlias"
        const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
