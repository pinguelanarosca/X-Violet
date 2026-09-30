package com.example

import com.example.security.CryptoEngine
import com.example.security.EncryptedPayload
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TestCryptoEngine : CryptoEngine {
    private val secureRandom = SecureRandom()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedPayload {
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val cipherText = cipher.doFinal(plainText)
        return EncryptedPayload(iv = iv, cipherText = cipherText)
    }

    override fun decrypt(cipherText: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }
}
