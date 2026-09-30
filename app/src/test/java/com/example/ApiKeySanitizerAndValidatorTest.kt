package com.example

import com.example.security.ApiKeySanitizer
import com.example.security.ApiKeyValidator
import com.example.security.ValidationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeySanitizerAndValidatorTest {

    @Test
    fun testSanitizerStripsWhitespaceAndNewlines() {
        val raw = "   \n\t AIzaSyTestKey1234567890 \r\n  "
        val cleaned = ApiKeySanitizer.sanitizeKey(raw)
        assertEquals("AIzaSyTestKey1234567890", cleaned)
    }

    @Test
    fun testSanitizerStripsSurroundingQuotes() {
        val rawDouble = "\"AIzaSyTestKeyDoubleQuotes12345\""
        assertEquals("AIzaSyTestKeyDoubleQuotes12345", ApiKeySanitizer.sanitizeKey(rawDouble))

        val rawSingle = "'sk-proj-SingleQuotesSecretKey12345'"
        assertEquals("sk-proj-SingleQuotesSecretKey12345", ApiKeySanitizer.sanitizeKey(rawSingle))

        val rawBacktick = "`gsk_BacktickSecretKey12345`"
        assertEquals("gsk_BacktickSecretKey12345", ApiKeySanitizer.sanitizeKey(rawBacktick))
    }

    @Test
    fun testSanitizerStripsBearerPrefix() {
        val rawBearer = "Bearer sk-proj-1234567890abcdef"
        assertEquals("sk-proj-1234567890abcdef", ApiKeySanitizer.sanitizeKey(rawBearer))

        val rawBearerLower = "bearer AIzaSyGeminiKey12345"
        assertEquals("AIzaSyGeminiKey12345", ApiKeySanitizer.sanitizeKey(rawBearerLower))
    }

    @Test
    fun testSanitizerStripsEnvVariablePrefixes() {
        val rawEnv1 = "GEMINI_API_KEY=AIzaSyGeminiApiKeyTest123"
        assertEquals("AIzaSyGeminiApiKeyTest123", ApiKeySanitizer.sanitizeKey(rawEnv1))

        val rawEnv2 = "export OPENAI_API_KEY=\"sk-proj-1234567890\""
        assertEquals("sk-proj-1234567890", ApiKeySanitizer.sanitizeKey(rawEnv2))

        val rawEnv3 = "API_KEY = 'sk-ant-anthropic-key-123'"
        assertEquals("sk-ant-anthropic-key-123", ApiKeySanitizer.sanitizeKey(rawEnv3))
    }

    @Test
    fun testSanitizerRemovesInvisibleZeroWidthCharacters() {
        // \u200B zero-width space, \uFEFF BOM, \u00A0 NBSP
        val rawInvisible = "AIzaSy\u200BZero\uFEFFWidth\u00A0Key12345"
        val cleaned = ApiKeySanitizer.sanitizeKey(rawInvisible)
        assertEquals("AIzaSyZeroWidthKey12345", cleaned)
    }

    @Test
    fun testValidatorDetectsGeminiKeyCorrectly() {
        val report = ApiKeyValidator.validate("gemini", "AIzaSyValidGeminiKey1234567890abcdef123")
        assertTrue(report.isValid)
        assertEquals(ValidationLevel.VALID, report.level)
        assertEquals("gemini", report.detectedProviderId)
        assertFalse(report.isMismatch)
    }

    @Test
    fun testValidatorDetectsOpenAiKeyCorrectly() {
        val report = ApiKeyValidator.validate("openai", "sk-proj-ValidOpenAiSecretKey1234567890")
        assertTrue(report.isValid)
        assertEquals(ValidationLevel.VALID, report.level)
        assertEquals("openai", report.detectedProviderId)
        assertFalse(report.isMismatch)
    }

    @Test
    fun testValidatorDetectsProviderMismatchWarning() {
        // Pasting OpenAI key into Gemini
        val report = ApiKeyValidator.validate("gemini", "sk-proj-OpenAiKeyInGeminiSlot12345")
        assertTrue(report.isValid) // Still allows saving if user insists
        assertEquals(ValidationLevel.WARNING, report.level)
        assertTrue(report.isMismatch)
        assertEquals("openai", report.detectedProviderId)
        assertTrue(report.message.contains("OpenAI"))
    }

    @Test
    fun testValidatorRejectsKeysWithInternalWhitespace() {
        val report = ApiKeyValidator.validate("gemini", "AIzaSy Fragment1 Fragment2")
        assertFalse(report.isValid)
        assertEquals(ValidationLevel.ERROR, report.level)
        assertTrue(report.message.contains("espaços internos"))
    }

    @Test
    fun testValidatorAllowsEmptyKeyForCustomOpenAi() {
        val report = ApiKeyValidator.validate("custom_openai", "")
        assertTrue(report.isValid)
        assertEquals(ValidationLevel.VALID, report.level)
    }
}
