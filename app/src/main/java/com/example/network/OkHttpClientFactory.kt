package com.example.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class NetworkTimeouts(
    val connectTimeoutMs: Long = 15000L,
    val readTimeoutMs: Long = 30000L,
    val writeTimeoutMs: Long = 15000L,
    val callTimeoutMs: Long = 60000L,
    val streamInactivityTimeoutMs: Long = 20000L
)

object OkHttpClientFactory {

    private val defaultPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    fun create(timeouts: NetworkTimeouts = NetworkTimeouts()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionPool(defaultPool)
            .connectTimeout(timeouts.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeouts.callTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false) // Retry is orchestrated cleanly by RetryPolicy with jitter

        // Note: Rule 5 forbids any HTTP logging interceptor that logs URLs, headers or bodies
        // No permissive TrustManager or HostnameVerifier is allowed; default standard system TLS is strictly used.

        return builder.build()
    }
}
