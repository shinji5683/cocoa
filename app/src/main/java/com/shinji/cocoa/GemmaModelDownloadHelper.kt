package com.shinji.cocoa

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.util.Log

class GemmaModelDownloadHelper(private val context: Context) {

    companion object {
        private const val TAG = "GemmaDownloadHelper"
        const val REQUIRED_FREE_SPACE_BYTES = 1500L * 1024L * 1024L // 1.5 GB
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Storage check error: ${e.message}")
            2000L * 1024L * 1024L // 2.0GB fallback
        }
    }

    fun hasEnoughStorage(): Boolean {
        return getAvailableStorageBytes() >= REQUIRED_FREE_SPACE_BYTES
    }

    fun buildDownloadConfirmationPrompt(): String {
        val freeMb = getAvailableStorageBytes() / (1024 * 1024)
        val wifiStatus = if (isWifiConnected()) "Wi-Fi接続中" else "モバイルデータ通信中（Wi-Fi推奨）"
        return "Gemma 4 AIモデル（必要容量 約1.5GB）をダウンロードします。現在の空き容量: ${freeMb}MB、$wifiStatus。ダウンロードを開始しますか？"
    }
}
