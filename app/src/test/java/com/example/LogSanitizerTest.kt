package com.example

import com.example.logging.LogSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun testMaskKey() {
        assertEquals("••••a1b2", LogSanitizer.maskKey("sk-live-1234a1b2"))
        assertEquals("••••", LogSanitizer.maskKey("123"))
        assertEquals("", LogSanitizer.maskKey(null))
        assertEquals("", LogSanitizer.maskKey(""))
    }

    @Test
    fun testSanitizeOpenAiAndAnthropicKeys() {
        val raw = "Chave sk-proj-1234567890abcdef1234 enviada na requisicao"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("sk-proj-1234567890abcdef1234"))
        assertTrue(sanitized.contains("••••1234"))
    }

    @Test
    fun testSanitizeGeminiKeys() {
        val raw = "Endpoint https://generativelanguage.googleapis.com/v1beta?key=AIzaSyA1234567890BCDEF1234567890"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("AIzaSyA1234567890BCDEF1234567890"))
        assertTrue(sanitized.contains("key=••••7890"))
    }

    @Test
    fun testSanitizeGroqAndXaiKeys() {
        val rawGroq = "Authorization gsk_abcdef1234567890xyz"
        val sanitizedGroq = LogSanitizer.sanitize(rawGroq)
        assertFalse(sanitizedGroq.contains("gsk_abcdef1234567890xyz"))
        assertTrue(sanitizedGroq.contains("••••0xyz"))

        val rawXai = "Token xai-1234567890abcdef12"
        val sanitizedXai = LogSanitizer.sanitize(rawXai)
        assertFalse(sanitizedXai.contains("xai-1234567890abcdef12"))
        assertTrue(sanitizedXai.contains("••••ef12"))
    }

    @Test
    fun testSanitizeBearerAndHeaders() {
        val raw = "Header x-api-key: mySecretSecretTokenValue123 and Bearer mySecretSecretTokenValue123"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("mySecretSecretTokenValue123"))
        assertTrue(sanitized.contains("••••e123"))
    }

    @Test
    fun testSanitizeUrlQueryParams() {
        val url = "https://api.example.com/v1/chat?access_token=secret1234567890&model=gemini"
        val sanitized = LogSanitizer.sanitize(url)
        assertFalse(sanitized.contains("secret1234567890"))
        assertTrue(sanitized.contains("access_token=••••7890"))
        assertTrue(sanitized.contains("model=gemini"))
    }

    @Test
    fun testSanitizeBase64LongPayload() {
        val base64 = "A".repeat(160)
        val raw = "Recebido payload: $base64 no frame"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains(base64))
        assertTrue(sanitized.contains("[DADO_BASE64_OMITIDO:"))
    }

    @Test
    fun testSanitizeThrowable() {
        val exception = RuntimeException("Erro ao conectar em https://api.com?key=AIzaSyA9876543210ABCDEF9876543210")
        val sanitized = LogSanitizer.sanitizeThrowable(exception)
        assertFalse(sanitized!!.contains("AIzaSyA9876543210ABCDEF9876543210"))
        assertTrue(sanitized.contains("••••3210"))
    }

    @Test
    fun testSanitizeXGoogApiKeyHeader() {
        val raw = "Outgoing Request Headers: x-goog-api-key: AIzaSySecretKey9876543210 and Host: google.com"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse("Não deve conter a chave do Google em texto claro", sanitized.contains("AIzaSySecretKey9876543210"))
        assertTrue("Deve conter a máscara", sanitized.contains("••••3210"))
    }

    @Test
    fun testSanitizeAccessTokenQueryParam() {
        val raw = "GET /v1/user?access_token=secretOAuthTokenValue12345&scope=read"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse("Não deve conter access_token em texto claro", sanitized.contains("secretOAuthTokenValue12345"))
        assertTrue("Deve preservar o parâmetro mascarado", sanitized.contains("access_token=••••2345"))
        assertTrue("Deve preservar outros parâmetros de query", sanitized.contains("scope=read"))
    }

    @Test
    fun testSanitizeAuthorizationBearer() {
        val raw = "Request Header Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secretToken12345"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse("Não deve conter o token Bearer em texto claro", sanitized.contains("secretToken12345"))
        assertTrue("Deve conter o prefixo Bearer mascarado", sanitized.contains("Bearer ••••2345"))
    }

    @Test
    fun testSanitizeExceptionMessageWithUrlContainingKey() {
        val exception = java.io.IOException("Unable to resolve host: https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSySecretKeyXYZ1234567890")
        val sanitized = LogSanitizer.sanitizeThrowable(exception)
        assertNotNull(sanitized)
        assertFalse("Exceção com URL não deve conter o parâmetro key em texto claro", sanitized!!.contains("AIzaSySecretKeyXYZ1234567890"))
        assertTrue("Deve conter a chave mascarada na URL", sanitized.contains("key=••••7890"))
    }
}
