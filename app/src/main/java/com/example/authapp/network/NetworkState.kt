package com.example.authapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time network state monitoring.
 * Detects online/offline status and connection type.
 */
data class NetworkStatus(
    val isConnected: Boolean = false,
    val isMetered: Boolean = false,
    val connectionType: String = "UNKNOWN" // WIFI, CELLULAR, VPN, UNKNOWN
)

class NetworkStateManager(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    init {
        registerNetworkCallback()
        updateNetworkStatus()
    }

    private fun registerNetworkCallback() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus()
            }

            override fun onLost(network: Network) {
                updateNetworkStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateNetworkStatus()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun updateNetworkStatus() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        if (capabilities == null) {
            _networkStatus.value = NetworkStatus(isConnected = false)
            return
        }

        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        
        val connectionType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "UNKNOWN"
        }

        _networkStatus.value = NetworkStatus(
            isConnected = isConnected,
            isMetered = isMetered,
            connectionType = connectionType
        )
    }
}
