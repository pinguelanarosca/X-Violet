package com.example.errors

import com.example.logging.LogCategory

sealed class AppError(
    val code: String,
    val userMessage: String,
    val technicalCause: String,
    val retryable: Boolean,
    val suggestedAction: String,
    val logCategory: LogCategory
) : Exception("$code: $userMessage ($technicalCause)") {

    class Auth(
        code: String = "ERR_AUTH",
        userMessage: String = "Chave de API inválida, ausente ou revogada. Verifique suas credenciais no cofre.",
        technicalCause: String,
        suggestedAction: String = "Inserir a chave novamente no cofre",
        logCategory: LogCategory = LogCategory.SECURITY
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Quota(
        code: String = "ERR_QUOTA",
        userMessage: String = "Limite de taxa ou cota atingido no provedor. Aguarde um momento.",
        technicalCause: String,
        val retryAfterMs: Long? = null,
        suggestedAction: String = "Aguardar o tempo de espera e tentar de novo",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class Network(
        code: String = "ERR_NETWORK",
        userMessage: String = "Sem conexão com a internet ou erro de comunicação de rede.",
        technicalCause: String,
        suggestedAction: String = "Verificar conexão de dados ou Wi-Fi",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class Timeout(
        code: String = "ERR_TIMEOUT",
        userMessage: String = "O servidor demorou muito para responder (tempo esgotado).",
        technicalCause: String,
        suggestedAction: String = "Tentar a solicitação novamente",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class ProviderUnavailable(
        code: String = "ERR_PROVIDER_UNAVAILABLE",
        userMessage: String = "O provedor de IA está temporariamente indisponível ou em manutenção (5xx).",
        technicalCause: String,
        suggestedAction: String = "Aguardar estabilização ou escolher outro provedor",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class ModelUnavailable(
        code: String = "ERR_MODEL_UNAVAILABLE",
        userMessage: String = "O modelo selecionado não está disponível para esta conta ou foi descontinuado.",
        technicalCause: String,
        suggestedAction: String = "Selecionar um modelo diferente nas configurações",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class SafetyBlocked(
        code: String = "ERR_SAFETY_BLOCKED",
        userMessage: String = "A requisição ou resposta foi bloqueada pelos filtros de moderação e segurança do modelo.",
        technicalCause: String,
        suggestedAction: String = "Reformular o prompt ou ajustar parâmetros de segurança",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class EmptyResponse(
        code: String = "ERR_EMPTY_RESPONSE",
        userMessage: String = "O modelo retornou uma resposta sem conteúdo textual.",
        technicalCause: String,
        suggestedAction: String = "Tentar novamente com um prompt mais específico",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class InvalidResponse(
        code: String = "ERR_INVALID_RESPONSE",
        userMessage: String = "Não foi possível interpretar a resposta enviada pelo provedor.",
        technicalCause: String,
        val sanitizedSnippet: String? = null,
        suggestedAction: String = "Verificar logs do sistema e tentar novamente",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class StreamInterrupted(
        code: String = "ERR_STREAM_INTERRUPTED",
        userMessage: String = "A transmissão da resposta foi interrompida no meio.",
        technicalCause: String,
        val partialText: String? = null,
        suggestedAction: String = "Tentar regenerar a resposta",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, true, suggestedAction, logCategory)

    class UnsupportedParameter(
        code: String = "ERR_UNSUPPORTED_PARAMETER",
        userMessage: String = "O provedor não suporta um dos parâmetros configurados.",
        technicalCause: String,
        val parameterName: String? = null,
        suggestedAction: String = "Ajustar os parâmetros na configuração do agente",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class InvalidRequest(
        code: String = "ERR_INVALID_REQUEST",
        userMessage: String = "A requisição continha dados inválidos e foi rejeitada (400).",
        technicalCause: String,
        suggestedAction: String = "Verificar os dados enviados e os parâmetros",
        logCategory: LogCategory = LogCategory.PROVIDER
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Permission(
        code: String = "ERR_PERMISSION",
        userMessage: String = "O aplicativo não possui a permissão necessária para executar esta ação.",
        technicalCause: String,
        val permissionName: String? = null,
        suggestedAction: String = "Conceder a permissão nas configurações do Android",
        logCategory: LogCategory = LogCategory.APP
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Storage(
        code: String = "ERR_STORAGE",
        userMessage: String = "Erro de gravação ou leitura no armazenamento interno (disco ou banco).",
        technicalCause: String,
        suggestedAction: String = "Limpar logs/cache ou liberar espaço no aparelho",
        logCategory: LogCategory = LogCategory.STORAGE
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Crypto(
        code: String = "ERR_CRYPTO",
        userMessage: String = "Falha criptográfica no Android Keystore. A chave precisa ser inserida de novo.",
        technicalCause: String,
        suggestedAction: String = "Inserir novamente a chave de API no cofre",
        logCategory: LogCategory = LogCategory.SECURITY
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Cancelled(
        code: String = "ERR_CANCELLED",
        userMessage: String = "A operação foi cancelada pelo usuário.",
        technicalCause: String = "Cancelamento cooperativo disparado",
        suggestedAction: String = "Nenhuma ação necessária",
        logCategory: LogCategory = LogCategory.APP
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)

    class Unknown(
        code: String = "ERR_UNKNOWN",
        userMessage: String = "Ocorreu um erro imprevisto.",
        technicalCause: String,
        suggestedAction: String = "Consultar os logs para mais detalhes",
        logCategory: LogCategory = LogCategory.APP
    ) : AppError(code, userMessage, technicalCause, false, suggestedAction, logCategory)
}
