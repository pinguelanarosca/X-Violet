package com.example.security

enum class ValidationLevel {
    VALID,
    CLEANED_OK,
    WARNING,
    ERROR
}

data class KeyValidationReport(
    val isValid: Boolean,
    val level: ValidationLevel,
    val sanitizedKey: String,
    val message: String,
    val detectedProviderId: String? = null,
    val detectedProviderName: String? = null,
    val wasTransformed: Boolean = false,
    val isMismatch: Boolean = false,
    val suggestions: List<String> = emptyList()
)

object ApiKeyValidator {

    fun validate(providerId: String, rawInput: String): KeyValidationReport {
        val sanitized = ApiKeySanitizer.sanitizeKey(rawInput, providerId)
        val wasTransformed = ApiKeySanitizer.hasTransformations(rawInput, sanitized)

        if (sanitized.isEmpty()) {
            if (providerId == "custom_openai") {
                return KeyValidationReport(
                    isValid = true,
                    level = ValidationLevel.VALID,
                    sanitizedKey = "",
                    message = "Chave em branco permitida para servidores locais (Ollama / LM Studio sem autenticação)."
                )
            }
            return KeyValidationReport(
                isValid = false,
                level = ValidationLevel.ERROR,
                sanitizedKey = "",
                message = "Insira ou cole a chave de API fornecida pelo provedor."
            )
        }

        // Check for internal whitespace
        if (sanitized.contains(" ") && providerId != "custom_openai") {
            return KeyValidationReport(
                isValid = false,
                level = ValidationLevel.ERROR,
                sanitizedKey = sanitized,
                message = "A chave de API contém espaços internos. Verifique se copiou múltiplos fragmentos.",
                suggestions = listOf("Remova espaços internos da chave.")
            )
        }

        // Detect provider signature
        val detected = detectProviderSignature(sanitized)
        val isMismatch = detected != null && detected.first != providerId

        // Length validation
        if (sanitized.length < 5 && providerId != "custom_openai") {
            return KeyValidationReport(
                isValid = false,
                level = ValidationLevel.ERROR,
                sanitizedKey = sanitized,
                message = "Chave muito curta (menos de 5 caracteres).",
                suggestions = listOf("Copie a chave completa a partir do painel oficial.")
            )
        }

        // Provider-specific rules
        when (providerId) {
            "gemini" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves do Google Gemini geralmente começam com 'AIzaSy'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed,
                        suggestions = listOf("Verifique se selecionou o item correto no Cofre.")
                    )
                }
                if (sanitized.startsWith("AIzaSy")) {
                    val msg = if (wasTransformed) "Chave do Google Gemini formatada e válida (39 caracteres)." else "Formato padrão do Google Gemini reconhecido."
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = msg,
                        detectedProviderId = "gemini",
                        detectedProviderName = "Google Gemini",
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.length >= 20) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Chave de tamanho válido para Google Gemini.",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "openai" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves da OpenAI geralmente começam com 'sk-' ou 'sk-proj-'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.startsWith("sk-")) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Formato padrão da OpenAI reconhecido.",
                        detectedProviderId = "openai",
                        detectedProviderName = "OpenAI",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "anthropic" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves da Anthropic geralmente começam com 'sk-ant-'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.startsWith("sk-ant-")) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Formato padrão da Anthropic Claude reconhecido.",
                        detectedProviderId = "anthropic",
                        detectedProviderName = "Anthropic (Claude)",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "groq" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves da Groq geralmente começam com 'gsk_'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.startsWith("gsk_")) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Formato padrão da Groq reconhecido.",
                        detectedProviderId = "groq",
                        detectedProviderName = "Groq",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "openrouter" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves do OpenRouter começam com 'sk-or-'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.startsWith("sk-or-")) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Formato padrão do OpenRouter reconhecido.",
                        detectedProviderId = "openrouter",
                        detectedProviderName = "OpenRouter",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "xai" -> {
                if (isMismatch) {
                    return KeyValidationReport(
                        isValid = true,
                        level = ValidationLevel.WARNING,
                        sanitizedKey = sanitized,
                        message = "Atenção: Esta chave parece pertencer a ${detected?.second}. Chaves da xAI começam com 'xai-'.",
                        detectedProviderId = detected?.first,
                        detectedProviderName = detected?.second,
                        isMismatch = true,
                        wasTransformed = wasTransformed
                    )
                }
                if (sanitized.startsWith("xai-")) {
                    return KeyValidationReport(
                        isValid = true,
                        level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                        sanitizedKey = sanitized,
                        message = "Formato padrão da xAI (Grok) reconhecido.",
                        detectedProviderId = "xai",
                        detectedProviderName = "xAI (Grok)",
                        wasTransformed = wasTransformed
                    )
                }
            }

            "custom_openai" -> {
                return KeyValidationReport(
                    isValid = true,
                    level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
                    sanitizedKey = sanitized,
                    message = "Chave configurada para servidor compatível com OpenAI.",
                    wasTransformed = wasTransformed
                )
            }
        }

        val defaultMsg = if (wasTransformed) {
            "Chave higienizada com sucesso (removidas aspas/espaços/prefixos)."
        } else {
            "Formato aceito."
        }

        return KeyValidationReport(
            isValid = true,
            level = if (wasTransformed) ValidationLevel.CLEANED_OK else ValidationLevel.VALID,
            sanitizedKey = sanitized,
            message = defaultMsg,
            wasTransformed = wasTransformed
        )
    }

    private fun detectProviderSignature(key: String): Pair<String, String>? {
        return when {
            key.startsWith("AIzaSy") -> "gemini" to "Google Gemini"
            key.startsWith("sk-ant-") -> "anthropic" to "Anthropic (Claude)"
            key.startsWith("sk-or-") || key.startsWith("sk-or-v1-") -> "openrouter" to "OpenRouter"
            key.startsWith("gsk_") -> "groq" to "Groq"
            key.startsWith("xai-") -> "xai" to "xAI (Grok)"
            key.startsWith("sk-proj-") || (key.startsWith("sk-") && !key.startsWith("sk-ant-") && !key.startsWith("sk-or-")) -> "openai" to "OpenAI"
            else -> null
        }
    }
}
