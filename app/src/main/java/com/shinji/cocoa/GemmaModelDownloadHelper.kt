package com.shinji.cocoa

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log

class GemmaModelDownloadHelper(private val context: Context) {

    companion object {
        private const val TAG = "GemmaDownloadHelper"
        const val REQUIRED_FREE_SPACE_BYTES = 1500L * 1024L * 1024L // 1.5 GB
        const val OFFICIAL_GEMMA_DOWNLOAD_URL = "https://huggingface.co/google/gemma-4-vision-mobile-int4/resolve/main/gemma_4_vision_mobile_int4.bin"
        const val KAGGLE_GEMMA_URL = "https://www.kaggle.com/models/google/gemma-4"
        const val GEMMA_4_MODEL_NAME = "Google Gemma 4 On-Device Vision Engine v4.0 (最新版)"
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

    fun startGemmaDownload(): Boolean {
        return try {
            val request = DownloadManager.Request(Uri.parse(OFFICIAL_GEMMA_DOWNLOAD_URL)).apply {
                setTitle("Gemma 4 On-Device AI Engine")
                setDescription("cocoa の完全ローカルAIモデル(約1.5GB)をダウンロード中...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "gemma_4_vision.bin")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            Log.i(TAG, "Gemma download enqueued with DownloadManager successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            false
        }
    }

    fun buildDownloadConfirmationPrompt(): String {
        val freeMb = getAvailableStorageBytes() / (1024 * 1024)
        val wifiStatus = if (isWifiConnected()) "Wi-Fi接続中" else "モバイルデータ通信中（Wi-Fi推奨）"
        return "Google公式 Gemma 4 AIモデル（無料・約1.5GB）をダウンロードします。空き容量: ${freeMb}MB、$wifiStatus。よろしいですか？"
    }
}
