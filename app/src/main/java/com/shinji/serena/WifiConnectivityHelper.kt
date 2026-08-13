package com.shinji.serena

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log

object WifiConnectivityHelper {

    private const val TAG = "WifiConnectivityHelper"

    /**
     * Wi-Fiが現在接続中（データ通信可能状態）であるか判定します。
     * アクティブネットワーク、全ネットワーク走査、およびWifiManagerのフォールバックを組み合わせます。
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                // 1. アクティブネットワークのチェック
                val activeNet = cm.activeNetwork
                if (activeNet != null) {
                    val caps = cm.getNetworkCapabilities(activeNet)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return true
                    }
                }

                // 2. VPN接続時やマルチネットワーク対応：全ネットワークインターフェースを検索
                val allNetworks = cm.allNetworks
                for (network in allNetworks) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return true
                    }
                }
            }

            // 3. WifiManager 経由の防衛的フォールバック判定
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiManager.isWifiEnabled) {
                val info = wifiManager.connectionInfo
                if (info != null && info.networkId != -1) {
                    val bssid = info.bssid
                    if (!bssid.isNullOrEmpty() && bssid != "00:00:00:00:00:00") {
                        return true
                    }
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "isWifiConnected error: ${e.message}")
            false
        }
    }

    /**
     * 読み上げや画面表示用のWi-Fiステータス文字列を取得します。
     * 例: "Wi-Fi接続中 SS ID Home_Network"、"Wi-Fi接続中"、"Wi-Fi未接続"
     */
    @SuppressLint("MissingPermission")
    fun getWifiStatusText(context: Context): String {
        if (!isWifiConnected(context)) {
            return "Wi-Fi未接続"
        }

        val rawSsid = getWifiSsid(context)
        return if (!rawSsid.isNullOrEmpty()) {
            "Wi-Fi接続中 SS ID ${rawSsid}"
        } else {
            "Wi-Fi接続中"
        }
    }

    /**
     * 接続中Wi-FiのSSIDを取得してクレンジングします。
     * 取得不能時や <unknown ssid> 等の場合は null を返します。
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun getWifiSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val info = wifiManager.connectionInfo ?: return null
            val rawSsid = info.ssid ?: return null

            cleanSsid(rawSsid)
        } catch (e: Exception) {
            Log.e(TAG, "getWifiSsid error: ${e.message}")
            null
        }
    }

    /**
     * ダブルクォーテーションの削除および不明SSID文字列の防衛的除外
     */
    private fun cleanSsid(ssid: String): String? {
        val trimmed = ssid.replace("\"", "").trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("<unknown ssid>", ignoreCase = true) ||
            trimmed.equals("0x", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true)) {
            return null
        }
        return trimmed
    }
}
