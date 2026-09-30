package com.example.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

object LogSanitizer {

    private val OPENAI_KEY_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9_\\-]{6,})")
    private val GEMINI_KEY_PATTERN = Pattern.compile("(AIza[0-9A-Za-z\\-_]{20,})")
    private val GROQ_KEY_PATTERN = Pattern.compile("(gsk_[a-zA-Z0-9_\\-]{10,})")
    private val XAI_KEY_PATTERN = Pattern.compile("(xai-[a-zA-Z0-9_\\-]{10,})")
    private val BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)([a-zA-Z0-9_\\.\\-]{10,})")
    private val HEADER_KEY_PATTERN = Pattern.compile("(?i)(x-api-key|x-goog-api-key|api-key)\\s*[:=]\\s*([^\\s,;\"']+)")
    private val URL_PARAM_PATTERN = Pattern.compile("(?i)([?&](?:key|access_token|api_key)=)([^&\\s\"']+)")
    private val BASE64_LONG_PATTERN = Pattern.compile("(?:[A-Za-z0-9+/]{4}){25,}(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")

    fun maskKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        val trimmed = key.trim()
        return if (trimmed.length > 4) {
            "••••" + trimmed.takeLast(4)
        } else {
            "••••"
        }
    }

    fun sanitize(input: String?): String {
        if (input == null) return ""
        var result = input

        // 1. OpenAI / Anthropic format sk-...
        result = replaceWithMask(result, OPENAI_KEY_PATTERN) { match ->
            maskKey(match)
        }

        // 2. Google Gemini format AIza...
        result = replaceWithMask(result, GEMINI_KEY_PATTERN) { match ->
            maskKey(match)
        }

        // 3. Groq format gsk_...
        result = replaceWithMask(result, GROQ_KEY_PATTERN) { match ->
            maskKey(match)
        }

        // 4. xAI format xai-...
        result = replaceWithMask(result, XAI_KEY_PATTERN) { match ->
            maskKey(match)
        }

        // 5. Bearer tokens
        result = BEARER_PATTERN.matcher(result).replaceAll { matchResult ->
            val prefix = matchResult.group(1) ?: "Bearer "
            val token = matchResult.group(2) ?: ""
            prefix + maskKey(token)
        }

        // 6. Header patterns
        result = HEADER_KEY_PATTERN.matcher(result).replaceAll { matchResult ->
            val headerName = matchResult.group(1) ?: "x-api-key"
            val token = matchResult.group(2) ?: ""
            "$headerName: " + maskKey(token)
        }

        // 7. URL query params (key=..., access_token=...)
        result = URL_PARAM_PATTERN.matcher(result).replaceAll { matchResult ->
            val prefix = matchResult.group(1) ?: ""
            val secret = matchResult.group(2) ?: ""
            prefix + maskKey(secret)
        }

        // 8. Base64 payload truncation
        result = BASE64_LONG_PATTERN.matcher(result).replaceAll { matchResult ->
            val len = matchResult.group().length
            "[DADO_BASE64_OMITIDO: $len caracteres]"
        }

        return result
    }

    fun sanitizeThrowable(throwable: Throwable?): String? {
        if (throwable == null) return null
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sanitize(sw.toString())
    }

    private fun replaceWithMask(source: String, pattern: Pattern, maskFn: (String) -> String): String {
        val matcher = pattern.matcher(source)
        val sb = StringBuffer()
        while (matcher.find()) {
            val original = matcher.group(1) ?: ""
            val replacement = maskFn(original)
            matcher.appendReplacement(sb, MatcherCompat.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private object MatcherCompat {
        fun quoteReplacement(s: String): String {
            return java.util.regex.Matcher.quoteReplacement(s)
        }
    }
}
