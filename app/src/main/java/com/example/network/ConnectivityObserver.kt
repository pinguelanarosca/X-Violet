package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.errors.AppError
import com.example.logging.HubLogger
import com.example.logging.LogCategory
import com.example.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityObserver(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        registerCallback()
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_isConnected.value
                    _isConnected.value = true
                    if (wasOffline) {
                        HubLogger.log(
                            level = LogLevel.INFO,
                            category = LogCategory.APP,
                            message = "Conectividade de rede restabelecida."
                        )
                    }
                }

                override fun onLost(network: Network) {
                    _isConnected.value = false
                    HubLogger.log(
                        level = LogLevel.WARN,
                        category = LogCategory.APP,
                        message = "Conectividade de rede perdida."
                    )
                }
            })
        } catch (e: SecurityException) {
            HubLogger.log(
                level = LogLevel.ERROR,
                category = LogCategory.APP,
                message = "Permissão ACCESS_NETWORK_STATE não concedida para NetworkCallback: ${e.message}"
            )
        }
    }

    fun ensureConnectedOrThrow() {
        if (!_isConnected.value) {
            throw AppError.Network(
                code = "ERR_NO_NETWORK",
                userMessage = "Sem conexão de rede disponível no momento. Verifique seus dados móveis ou Wi-Fi.",
                technicalCause = "ConnectivityObserver detectou ausência de conexão ativa com internet"
            )
        }
    }
}
