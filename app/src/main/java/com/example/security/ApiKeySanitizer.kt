package com.example.security

object ApiKeySanitizer {

    // Common prefixes from env variables or copy-pasting
    private val ENV_PREFIX_REGEX = Regex("^(?:export\\s+)?[A-Za-z0-9_]*(?:API_KEY|KEY|TOKEN|SECRET)\\s*=\\s*", RegexOption.IGNORE_CASE)
    private val BEARER_PREFIX_REGEX = Regex("^bearer\\s+", RegexOption.IGNORE_CASE)

    // Invisible / zero-width characters and unusual whitespace
    // \u200B (zero-width space), \u200C (ZWNJ), \u200D (ZWJ), \uFEFF (BOM/ZWNBSP),
    // \u200E (LRM), \u200F (RLM), \u202A-\u202E (directional formatting),
    // \u00A0 (NBSP), \u3000 (ideographic space), \u0000-\u001F (control characters), \u007F (DEL)
    private val INVISIBLE_CHARS_REGEX = Regex("[\\u200B-\\u200F\\uFEFF\\u202A-\\u202E\\u0000-\\u001F\\u007F]")

    /**
     * Cleans and sanitizes raw API keys pasted by users.
     * Handles:
     * - Leading and trailing whitespace / newlines
     * - Surrounding quotes ("...", '...', `...`)
     * - Export or ENV prefixes (e.g., "GEMINI_API_KEY=...", "export OPENAI_API_KEY=...")
     * - HTTP "Bearer " prefixes
     * - Invisible Unicode zero-width characters, BOM, control characters
     */
    fun sanitizeKey(rawKey: String, providerId: String = ""): String {
        if (rawKey.isBlank()) return ""

        var cleaned = rawKey.trim()

        // 1. Strip environment prefix if copied from .env file or terminal export
        cleaned = cleaned.replace(ENV_PREFIX_REGEX, "").trim()

        // 2. Strip Bearer prefix if copied from HTTP Authorization header
        cleaned = cleaned.replace(BEARER_PREFIX_REGEX, "").trim()

        // 3. Strip surrounding quotes (double, single, backticks)
        cleaned = stripSurroundingQuotes(cleaned).trim()

        // 4. Remove all invisible zero-width characters and control characters
        cleaned = cleaned.replace(INVISIBLE_CHARS_REGEX, "")

        // 5. Replace non-breaking spaces with standard space and trim again
        cleaned = cleaned.replace("\u00A0", "").replace("\u3000", "").trim()

        // 6. If provider is a standard token (not custom_openai with spaces), remove internal accidental line breaks
        cleaned = cleaned.replace("\n", "").replace("\r", "").replace("\t", "")

        return cleaned
    }

    private fun stripSurroundingQuotes(input: String): String {
        var str = input
        while (str.length >= 2) {
            val first = str.first()
            val last = str.last()
            if ((first == '"' && last == '"') ||
                (first == '\'' && last == '\'') ||
                (first == '`' && last == '`')
            ) {
                str = str.substring(1, str.length - 1).trim()
            } else {
                break
            }
        }
        return str
    }

    /**
     * Checks if sanitization changed the raw key significantly (e.g. removed quotes or prefix).
     */
    fun hasTransformations(rawKey: String, sanitizedKey: String): Boolean {
        if (rawKey.isEmpty() && sanitizedKey.isEmpty()) return false
        return rawKey != sanitizedKey
    }
}
