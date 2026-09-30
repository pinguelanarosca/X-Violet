package com.example

import com.example.errors.AppError
import com.example.network.UrlSecurityValidator
import org.junit.Assert.*
import org.junit.Test

class UrlSecurityValidatorTest {

    @Test
    fun testValidHttpsUrlsAccepted() {
        val urls = listOf(
            "https://api.openai.com/v1",
            "https://api.anthropic.com/v1",
            "https://api.x.ai/v1",
            "https://openrouter.ai/api/v1",
            "https://api.groq.com/openai/v1",
            "https://custom-server.example.com:8443/v1"
        )

        for (url in urls) {
            val result = UrlSecurityValidator.validateBaseUrl(url)
            assertTrue("Deveria aceitar HTTPS: $url", result is UrlSecurityValidator.ValidationResult.Valid)
        }
    }

    @Test
    fun testLocalHttpUrlsAcceptedWithLocalFlag() {
        val localUrls = listOf(
            "http://localhost:11434/v1",
            "http://127.0.0.1:8000/v1",
            "http://192.168.1.50:11434/v1",
            "http://10.0.0.15:5000/v1",
            "http://172.20.10.2:11434/v1",
            "http://169.254.10.5:8080/v1",
            "http://ollama-server.local:11434/v1",
            "http://home-assistant.lan:8123/api"
        )

        for (url in localUrls) {
            val result = UrlSecurityValidator.validateBaseUrl(url)
            assertTrue("Deveria aceitar HTTP local: $url", result is UrlSecurityValidator.ValidationResult.Valid)
            val valid = result as UrlSecurityValidator.ValidationResult.Valid
            assertTrue("isLocalNetwork deve ser true para $url", valid.isLocalNetwork)
        }
    }

    @Test
    fun testPublicCleartextHttpUrlsStrictlyRejected() {
        val publicHttpUrls = listOf(
            "http://api.openai.com/v1",
            "http://api.anthropic.com/v1",
            "http://openrouter.ai/api/v1",
            "http://8.8.8.8:8080/v1",
            "http://1.1.1.1/v1",
            "http://public-ai-server.com/v1"
        )

        for (url in publicHttpUrls) {
            val result = UrlSecurityValidator.validateBaseUrl(url)
            assertTrue("Deveria REJEITAR HTTP público: $url", result is UrlSecurityValidator.ValidationResult.Invalid)
            val invalid = result as UrlSecurityValidator.ValidationResult.Invalid
            assertTrue("Erro deve ser do tipo AppError.InvalidRequest", invalid.error is AppError.InvalidRequest)
            assertEquals("ERR_INSECURE_HTTP_PUBLIC", (invalid.error as AppError.InvalidRequest).code)
        }
    }

    @Test
    fun testInvalidUrlsAndSchemesRejected() {
        val invalid = listOf(
            "",
            "   ",
            "ftp://files.example.com",
            "ws://socket.example.com"
        )

        for (url in invalid) {
            val result = UrlSecurityValidator.validateBaseUrl(url)
            assertTrue("Deveria rejeitar entrada inválida: '$url'", result is UrlSecurityValidator.ValidationResult.Invalid)
        }
    }
}
