package com.shinji.serena.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.shinji.serena.BuildConfig
import com.shinji.serena.R
import com.shinji.serena.SerenaScreenReaderService
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * AutoUpdateManager
 *
 * 全盲のユーザーがブラウザやファイルマネージャーで迷うことなく、
 * アプリ内から1タップで最新バージョンの検出・自動ダウンロード・インストールを行える
 * インテリジェント・アクセシブル・アップデートエンジン。
 */
class AutoUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoUpdateManager"
        private const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/shinji5683/cocoa/releases/latest"
        const val PREFS_NAME = "serena_update_prefs"
        const val KEY_LAST_CHECK_TIME = "last_update_check_time"
        const val KEY_IGNORED_VERSION = "ignored_version"

        @Volatile
        private var instance: AutoUpdateManager? = null

        fun getInstance(context: Context): AutoUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AutoUpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val releaseDate: String
    )

    /**
     * GitHub Releases API を非同期で照会し、最新リリースの有無を判定します。
     */
    fun checkForUpdate(onResult: (UpdateInfo?) -> Unit) {
        thread(name = "SerenaUpdateChecker") {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_API)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Serena-ScreenReader/${BuildConfig.VERSION_NAME}")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned HTTP ${connection.responseCode}")
                    Handler(Looper.getMainLooper()).post { onResult(null) }
                    return@thread
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.optString("tag_name", "")
                val releaseNotes = json.optString("body", "")
                val releaseDate = json.optString("published_at", "")

                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.contains("serena") && name.endsWith(".apk") && !name.contains("debug")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = "https://github.com/shinji5683/cocoa/releases/latest/download/app-serena-release.apk"
                }

                val latestClean = tagName.removePrefix("v").trim()
                val currentClean = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                val hasUpdate = isNewerVersion(latestClean, currentClean)

                val info = UpdateInfo(
                    isUpdateAvailable = hasUpdate,
                    latestVersion = tagName,
                    currentVersion = "v${BuildConfig.VERSION_NAME}",
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    releaseDate = releaseDate
                )

                Handler(Looper.getMainLooper()).post { onResult(info) }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update: ${e.message}")
                Handler(Looper.getMainLooper()).post { onResult(null) }
            }
        }
    }

    /**
     * セマンティックバージョニング比較 (例: 2.0.1 > 2.0.0)
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * 最新の APK をバックグラウンドで高速ダウンロードし、完了時に自動でインストール画面へ遷移します。
     */
    fun startDownloadAndInstall(downloadUrl: String, onStatus: ((String) -> Unit)? = null) {
        val appContext = context.applicationContext
        val fileName = "app-serena-release.apk"
        val destinationFile = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.cacheDir, fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val startMsg = appContext.getString(R.string.update_download_started)
        onStatus?.invoke(startMsg)
        Toast.makeText(appContext, startMsg, Toast.LENGTH_SHORT).show()
        if (SerenaScreenReaderService.isServiceRunning()) {
            SerenaScreenReaderService.instance?.speak(startMsg, TextToSpeech.QUEUE_FLUSH)
        }

        thread(name = "SerenaApkDownloader") {
            try {
                val url = URL(downloadUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                // GitHub Releases から AWS S3 へのリダイレクト追従
                var status = conn.responseCode
                var redirects = 0
                while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) && redirects < 5) {
                    val location = conn.getHeaderField("Location")
                    conn = URL(location).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    status = conn.responseCode
                    redirects++
                }

                conn.inputStream.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                Log.i(TAG, "APK downloaded successfully: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")

                Handler(Looper.getMainLooper()).post {
                    val finishMsg = appContext.getString(R.string.update_download_finished)
                    onStatus?.invoke(finishMsg)
                    Toast.makeText(appContext, finishMsg, Toast.LENGTH_SHORT).show()
                    if (SerenaScreenReaderService.isServiceRunning()) {
                        SerenaScreenReaderService.instance?.speak(finishMsg, TextToSpeech.QUEUE_FLUSH)
                    }
                    installApk(destinationFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download update APK: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    val errorMsg = appContext.getString(R.string.update_download_failed, e.message ?: "")
                    onStatus?.invoke(errorMsg)
                    Toast.makeText(appContext, errorMsg, Toast.LENGTH_LONG).show()
                    if (SerenaScreenReaderService.isServiceRunning()) {
                        SerenaScreenReaderService.instance?.speak(errorMsg, TextToSpeech.QUEUE_FLUSH)
                    }
                }
            }
        }
    }

    /**
     * FileProvider 経由で Android 標準パッケージインストーラーを起動します。
     */
    fun installApk(file: File) {
        val appContext = context.applicationContext
        try {
            // Android 8.0+ 未知のアプリ提供元の許可チェック
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val permMsg = appContext.getString(R.string.update_permission_required)
                    Toast.makeText(appContext, permMsg, Toast.LENGTH_LONG).show()
                    if (SerenaScreenReaderService.isServiceRunning()) {
                        SerenaScreenReaderService.instance?.speak(permMsg, TextToSpeech.QUEUE_FLUSH)
                    }
                    appContext.startActivity(manageIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            appContext.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}")
            val failMsg = appContext.getString(R.string.update_install_failed, e.message ?: "")
            Toast.makeText(appContext, failMsg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * アプリ内更新案内ダイアログの表示
     */
    fun showUpdateDialog(activityContext: Context, info: UpdateInfo) {
        val title = activityContext.getString(R.string.update_dialog_title, info.latestVersion)
        val message = activityContext.getString(
            R.string.update_dialog_message,
            info.currentVersion,
            info.latestVersion,
            info.releaseNotes.take(300) + if (info.releaseNotes.length > 300) "..." else ""
        )

        AlertDialog.Builder(activityContext)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.update_btn_install_now) { _, _ ->
                startDownloadAndInstall(info.downloadUrl)
            }
            .setNeutralButton(R.string.update_btn_open_github) { _, _ ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shinji5683/cocoa/releases"))
                    activityContext.startActivity(browserIntent)
                } catch (_: Exception) {}
            }
            .setNegativeButton(R.string.update_btn_later, null)
            .show()
    }
}
