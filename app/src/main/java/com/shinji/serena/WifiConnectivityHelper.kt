package com.shinji.serena

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * WifiConnectivityHelper
 * Wi-Fi の接続・切断・電波強度の変動をリアルタイムに検知してアナウンスするエンジン
 */
class WifiConnectivityHelper(
    private val service: SerenaScreenReaderService
) {

    companion object {
        private const val TAG = "WifiConnectivityHelper"

        fun getWifiStatusText(context: Context): String {
            return try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

                var isConnected = false
                val activeNet = cm?.activeNetwork
                if (activeNet != null) {
                    val caps = cm.getNetworkCapabilities(activeNet)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        isConnected = true
                    }
                }

                if (!isConnected && cm != null) {
                    for (net in cm.allNetworks) {
                        val caps = cm.getNetworkCapabilities(net)
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            isConnected = true
                            break
                        }
                    }
                }

                val info = wm?.connectionInfo
                if (!isConnected && info != null) {
                    if (info.networkId != -1 && info.supplicantState == android.net.wifi.SupplicantState.COMPLETED) {
                        isConnected = true
                    }
                }

                if (isConnected) {
                    val rawSsid = info?.ssid?.replace("\"", "")?.trim()
                    if (!rawSsid.isNullOrEmpty() && rawSsid != "<unknown ssid>" && rawSsid != "0x") {
                        "Wi-Fi接続中 SS ID ${rawSsid}"
                    } else {
                        "Wi-Fi接続中"
                    }
                } else {
                    "Wi-Fi未接続"
                }
            } catch (e: Exception) {
                "Wi-Fi未接続"
            }
        }
    }

    private val connectivityManager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager = service.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var isRegistered = false
    private var lastWasConnected = false
    private var lastSignalLevel = -1

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i(TAG, "Wi-Fi network onAvailable")
            service.soundHelper?.playActionDone()
            val wifiInfo = getRealtimeWifiStatus()
            lastWasConnected = true
            service.speak("Wi-Fiに接続しました。$wifiInfo", TextToSpeech.QUEUE_ADD)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.i(TAG, "Wi-Fi network onLost")
            if (lastWasConnected) {
                lastWasConnected = false
                service.soundHelper?.playFocusMove()
                service.speak("Wi-Fiが切断されました。モバイル通信に切り替わりました。", TextToSpeech.QUEUE_ADD)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, capabilities)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    capabilities.signalStrength
                } else {
                    -100
                }
                val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager != null) {
                    wifiManager.calculateSignalLevel(signalStrength)
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.calculateSignalLevel(signalStrength, 5)
                }

                if (lastSignalLevel >= 0 && Math.abs(level - lastSignalLevel) >= 2) {
                    val levelDesc = getLevelDescription(level)
                    Log.i(TAG, "Wi-Fi signal changed to: $levelDesc")
                }
                lastSignalLevel = level
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered || connectivityManager == null) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            Log.i(TAG, "Wi-Fi realtime monitoring started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Wi-Fi network callback: ${e.message}")
        }
    }

    fun stopMonitoring() {
        if (!isRegistered || connectivityManager == null) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
            Log.i(TAG, "Wi-Fi realtime monitoring stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Wi-Fi callback: ${e.message}")
        }
    }

    fun getRealtimeWifiStatus(): String {
        return try {
            var isWifiConnected = false
            val activeNet = connectivityManager?.activeNetwork
            if (activeNet != null) {
                val caps = connectivityManager?.getNetworkCapabilities(activeNet)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isWifiConnected = true
                }
            }

            if (!isWifiConnected && connectivityManager != null) {
                for (net in connectivityManager.allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(net)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        isWifiConnected = true
                        break
                    }
                }
            }

            val info = wifiManager?.connectionInfo
            if (!isWifiConnected && info != null) {
                if (info.networkId != -1 && info.supplicantState == android.net.wifi.SupplicantState.COMPLETED) {
                    isWifiConnected = true
                }
            }

            if (isWifiConnected) {
                val ssid = info?.ssid?.replace("\"", "")?.trim()

                val rssi = info?.rssi ?: -100
                val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager != null) {
                    wifiManager.calculateSignalLevel(rssi)
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.calculateSignalLevel(rssi, 5)
                }

                val levelDesc = getLevelDescription(level)
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>" && ssid != "0x") {
                    "ネットワーク名: ${ssid}、${levelDesc}"
                } else {
                    levelDesc
                }
            } else {
                "Wi-Fi未接続"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting realtime wifi status: ${e.message}")
            "Wi-Fi未接続"
        }
    }

    private fun getLevelDescription(level: Int): String {
        return when (level) {
            4 -> "電波最強、アンテナ4本"
            3 -> "電波良好、アンテナ3本"
            2 -> "電波普通、アンテナ2本"
            1 -> "電波やや弱い、アンテナ1本"
            else -> "電波微弱"
        }
    }
}
