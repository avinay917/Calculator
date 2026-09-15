package com.example.authapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.NetworkHistoryItem

class NetworkHistoryMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var currentSsid: String = ""
    private var currentType: String = ""
    private var connectedTimestamp: Long = 0L

    fun startMonitoring() {
        if (networkCallback != null || connectivityManager == null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val childId = AppHealthTelemetry.getEffectiveUserId(context)
                if (childId.isEmpty()) return

                val caps = connectivityManager.getNetworkCapabilities(network)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                var ssid = "Unknown"
                val netType = when {
                    isWifi -> "WIFI"
                    isCellular -> "MOBILE_DATA"
                    else -> "OTHER"
                }

                if (isWifi) {
                    try {
                        val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
                        val rawSsid = wifiInfo?.ssid?.replace("\"", "") ?: ""
                        if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>") {
                            ssid = rawSsid
                        } else {
                            ssid = "Connected Wi-Fi"
                        }
                    } catch (e: Exception) {
                        ssid = "Wi-Fi Network"
                    }
                } else if (isCellular) {
                    ssid = "Cellular Internet"
                }

                currentSsid = ssid
                currentType = netType
                connectedTimestamp = System.currentTimeMillis()

                val item = NetworkHistoryItem(
                    ssid = ssid,
                    networkType = netType,
                    connectedAt = connectedTimestamp,
                    disconnectedAt = 0L
                )
                FirebaseRepository.recordNetworkHistory(childId, item)
            }

            override fun onLost(network: Network) {
                val childId = AppHealthTelemetry.getEffectiveUserId(context)
                if (childId.isEmpty()) return

                if (connectedTimestamp > 0L) {
                    val item = NetworkHistoryItem(
                        ssid = currentSsid,
                        networkType = currentType,
                        connectedAt = connectedTimestamp,
                        disconnectedAt = System.currentTimeMillis()
                    )
                    FirebaseRepository.recordNetworkHistory(childId, item)
                    connectedTimestamp = 0L
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            // Ignore permission or OS level errors
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {}
            networkCallback = null
        }
    }
}
