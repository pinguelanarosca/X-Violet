package com.example.models

import com.example.providers.model.ProviderCapabilities
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TraitValues(
    val humor: Int = 50,
    val formalidade: Int = 50,
    val criatividade: Int = 50,
    val objetividade: Int = 50,
    val iniciativa: Int = 50,
    val questionamento: Int = 50,
    val profundidade: Int = 50,
    val personalidade: Int = 50
) {
    companion object {
        val PRESET_CONSERVADOR = TraitValues(
            humor = 20, formalidade = 80, criatividade = 15,
            objetividade = 85, iniciativa = 30, questionamento = 40,
            profundidade = 80, personalidade = 30
        )
        val PRESET_EQUILIBRADO = TraitValues(
            humor = 50, formalidade = 50, criatividade = 50,
            objetividade = 50, iniciativa = 50, questionamento = 50,
            profundidade = 50, personalidade = 50
        )
        val PRESET_INTENSO = TraitValues(
            humor = 70, formalidade = 30, criatividade = 85,
            objetividade = 40, iniciativa = 80, questionamento = 75,
            profundidade = 70, personalidade = 85
        )
        val PRESET_INSANO = TraitValues(
            humor = 90, formalidade = 10, criatividade = 100,
            objetividade = 20, iniciativa = 100, questionamento = 95,
            profundidade = 60, personalidade = 100
        )
    }
}

enum class ReasoningLevel(val minVal: Int, val maxVal: Int, val displayName: String) {
    INSTANTANEO(0, 20, "Instantâneo"),
    LEVE(21, 40, "Leve"),
    EQUILIBRADO(41, 60, "Equilibrado"),
    PROFUNDO(61, 80, "Profundo"),
    EXTREMO(81, 100, "Extremo");

    companion object {
        fun fromValue(value: Int): ReasoningLevel {
            val clamped = value.coerceIn(0, 100)
            return values().firstOrNull { clamped in it.minVal..it.maxVal } ?: EQUILIBRADO
        }
    }
}

data class CompiledModelConfig(
    val temperature: Float,
    val topP: Float,
    val compiledSystemInstructions: String,
    val reasoningModeText: String, // "Nativo (Provedor)" or "Simulado (Prompt)"
    val nativeReasoningEffort: String? = null,
    val nativeThinkingBudget: Int? = null
)

object TraitTranslator {

    fun compile(
        traits: TraitValues,
        reasoningValue: Int,
        userInstructions: String,
        providerId: String,
        baseModelId: String,
        capabilities: ProviderCapabilities
    ): CompiledModelConfig {

        // 1. Convert Criatividade to Temperature and TopP, clamping strictly to ProviderCapabilities
        val minTemp = capabilities.temperatureRange.start
        val maxTemp = capabilities.temperatureRange.endInclusive
        val rawTemp = minTemp + (traits.criatividade / 100.0f) * (maxTemp - minTemp)
        val clampedTemp = rawTemp.coerceIn(minTemp, maxTemp)

        val rawTopP = 0.1f + (traits.criatividade / 100.0f) * 0.9f
        val clampedTopP = rawTopP.coerceIn(0.0f, 1.0f)

        // 2. Translate behavioral traits into system prompt instructions
        val promptBuilder = StringBuilder()

        if (userInstructions.isNotBlank()) {
            promptBuilder.append(userInstructions.trim()).append("\n\n")
        }

        promptBuilder.append("[DIRETRIZES DE COMPORTAMENTO TRADUZIDAS]\n")

        // Humor (0: Sério ↔ 100: Divertido)
        when {
            traits.humor <= 25 -> promptBuilder.append("- Mantenha um tom estritamente sério e sóbrio. Evite piadas, ironias ou descontrações.\n")
            traits.humor >= 75 -> promptBuilder.append("- Adote uma postura bem-humorada, leve e divertida, usando analogias criativas e tom descontraído.\n")
        }

        // Formalidade (0: Casual ↔ 100: Acadêmico)
        when {
            traits.formalidade <= 25 -> promptBuilder.append("- Use linguagem totalmente casual, direta, com gírias comuns e sem jargões acadêmicos.\n")
            traits.formalidade >= 75 -> promptBuilder.append("- Use linguagem altamente formal, elegante e precisa, adequada a um contexto acadêmico ou corporativo sênior.\n")
        }

        // Objetividade (0: Conciso ↔ 100: Detalhado/Explicativo)
        when {
            traits.objetividade <= 25 -> promptBuilder.append("- Seja o mais conciso e direto possível. Responda em poucas frases ou tópicos sem floreios.\n")
            traits.objetividade >= 75 -> promptBuilder.append("- Forneça explicações profundas, detalhadas e contextualizadas, cobrindo todos os ângulos relevantes.\n")
        }

        // Iniciativa (0: Reativo ↔ 100: Proativo)
        when {
            traits.iniciativa >= 75 -> promptBuilder.append("- Seja altamente proativo: além de responder ao pedido, sugira próximos passos, melhorias ou tópicos relacionados.\n")
        }

        // Questionamento (0: Aceita ↔ 100: Desafia)
        when {
            traits.questionamento >= 75 -> promptBuilder.append("- Atue de forma criticamente reflexiva: questione premissas potencialmente equivocadas do usuário e apresente contrapontos fundamentados.\n")
        }

        // Profundidade (0: Superficial ↔ 100: Especialista)
        when {
            traits.profundidade >= 75 -> promptBuilder.append("- Responda no nível de um especialista sênior no assunto, utilizando termos técnicos precisos e fundamentação rigorosa.\n")
        }

        // 3. Reasoning Translation (5 Levels)
        val reasoningLevel = ReasoningLevel.fromValue(reasoningValue)
        val isNativeReasoningSupported = capabilities.supportsReasoningEffort

        var reasoningModeText = "Simulado (Prompt/Chain)"
        var nativeEffort: String? = null
        var nativeBudget: Int? = null

        if (isNativeReasoningSupported) {
            reasoningModeText = "Nativo (Provedor)"
            nativeEffort = when (reasoningLevel) {
                ReasoningLevel.INSTANTANEO, ReasoningLevel.LEVE -> "low"
                ReasoningLevel.EQUILIBRADO -> "medium"
                ReasoningLevel.PROFUNDO, ReasoningLevel.EXTREMO -> "high"
            }
            nativeBudget = when (reasoningLevel) {
                ReasoningLevel.INSTANTANEO -> 1024
                ReasoningLevel.LEVE -> 2048
                ReasoningLevel.EQUILIBRADO -> 4096
                ReasoningLevel.PROFUNDO -> 8192
                ReasoningLevel.EXTREMO -> 16384
            }
        } else {
            // Simulated reasoning via verification step
            when (reasoningLevel) {
                ReasoningLevel.PROFUNDO, ReasoningLevel.EXTREMO -> {
                    promptBuilder.append("\n[ETAPA DE RACIOCÍNIO E VERIFICAÇÃO INTERNA]\n")
                    promptBuilder.append("- Antes de fornecer sua resposta final, execute mentalmente uma etapa de reflexão passo a passo. Verifique se há erros lógicos, inconsistências ou lacunas na resposta e corrija-os antes de responder.\n")
                }
                ReasoningLevel.EQUILIBRADO, ReasoningLevel.LEVE -> {
                    promptBuilder.append("\n[ETAPA DE RACIOCÍNIO LEVE]\n")
                    promptBuilder.append("- Estruture suas ideias logicamente antes de responder.\n")
                }
                ReasoningLevel.INSTANTANEO -> {
                    // No reasoning overhead
                }
            }
        }

        return CompiledModelConfig(
            temperature = clampedTemp,
            topP = clampedTopP,
            compiledSystemInstructions = promptBuilder.toString().trim(),
            reasoningModeText = reasoningModeText,
            nativeReasoningEffort = nativeEffort,
            nativeThinkingBudget = nativeBudget
        )
    }
}
