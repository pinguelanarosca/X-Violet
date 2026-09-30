package com.example.network

import com.example.errors.AppError
import java.net.URI

object UrlSecurityValidator {

    sealed class ValidationResult {
        data class Valid(val normalizedUrl: String, val isLocalNetwork: Boolean) : ValidationResult()
        data class Invalid(val error: AppError) : ValidationResult()
    }

    /**
     * Validates a base URL according to strict security rules:
     * - HTTPS is allowed for any valid host.
     * - HTTP is allowed ONLY for local network hosts:
     *   localhost, 127.0.0.1, ::1, *.local, *.lan, *.home.arpa,
     *   10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16.
     * - HTTP to any public domain/IP is strictly rejected.
     */
    fun validateBaseUrl(url: String): ValidationResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(
                AppError.InvalidRequest(
                    code = "ERR_EMPTY_URL",
                    userMessage = "A URL base não pode ser vazia.",
                    technicalCause = "URL string is empty"
                )
            )
        }

        val uri = try {
            val candidate = if (!trimmed.contains("://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            URI(candidate)
        } catch (e: Exception) {
            return ValidationResult.Invalid(
                AppError.InvalidRequest(
                    code = "ERR_MALFORMED_URL",
                    userMessage = "Formato de URL inválido: ${e.message}",
                    technicalCause = "URI parse exception: ${e.message}"
                )
            )
        }

        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase()

        if (host.isNullOrBlank()) {
            return ValidationResult.Invalid(
                AppError.InvalidRequest(
                    code = "ERR_NO_HOST",
                    userMessage = "O endereço do servidor não contém um host válido.",
                    technicalCause = "URI host is null or empty"
                )
            )
        }

        if (scheme == "https") {
            val normalized = buildNormalizedUrl(uri)
            return ValidationResult.Valid(normalized, isLocalNetwork = isLocalAddress(host))
        }

        if (scheme == "http") {
            if (isLocalAddress(host)) {
                val normalized = buildNormalizedUrl(uri)
                return ValidationResult.Valid(normalized, isLocalNetwork = true)
            } else {
                return ValidationResult.Invalid(
                    AppError.InvalidRequest(
                        code = "ERR_INSECURE_HTTP_PUBLIC",
                        userMessage = "Conexões HTTP em texto puro são permitidas apenas em redes locais privadas (localhost, 192.168.x.x, 10.x.x.x, 172.16-31.x.x, 169.254.x.x ou .local). Para servidores externos na Internet, HTTPS é obrigatório.",
                        technicalCause = "Rejected public cleartext HTTP endpoint for host: $host"
                    )
                )
            }
        }

        return ValidationResult.Invalid(
            AppError.InvalidRequest(
                code = "ERR_UNSUPPORTED_SCHEME",
                userMessage = "Protocolo não suportado: $scheme. Utilize HTTPS (ou HTTP para rede local).",
                technicalCause = "Unsupported scheme: $scheme"
            )
        )
    }

    /**
     * Checks if the given host string is a private/local network address.
     */
    fun isLocalAddress(host: String): Boolean {
        val lowerHost = host.lowercase().trim()

        if (lowerHost == "localhost" || lowerHost == "127.0.0.1" || lowerHost == "::1" || lowerHost == "0:0:0:0:0:0:0:1") {
            return true
        }

        if (lowerHost.endsWith(".local") || lowerHost.endsWith(".lan") || lowerHost.endsWith(".home.arpa")) {
            return true
        }

        // Check if IPv4 address
        val ipv4Parts = lowerHost.split(".")
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() != null && it.toInt() in 0..255 }) {
            val octets = ipv4Parts.map { it.toInt() }
            val first = octets[0]
            val second = octets[1]

            // 127.0.0.0/8 Loopback
            if (first == 127) return true

            // 10.0.0.0/8 Private
            if (first == 10) return true

            // 172.16.0.0/12 Private (172.16.x.x to 172.31.x.x)
            if (first == 172 && second in 16..31) return true

            // 192.168.0.0/16 Private
            if (first == 192 && second == 168) return true

            // 169.254.0.0/16 Link-Local
            if (first == 169 && second == 254) return true
        }

        return false
    }

    private fun buildNormalizedUrl(uri: URI): String {
        val portStr = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return "${uri.scheme}://${uri.host}$portStr$path"
    }
}
