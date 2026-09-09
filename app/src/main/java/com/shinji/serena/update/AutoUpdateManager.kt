package com.shinji.serena.update

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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

        @Volatile
        var pendingInstallApkFile: File? = null

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
     * 設定画面等から権限付与後に復帰した際、保留中のアップデートインストールを即座に自動再開します。
     */
    fun checkAndResumePendingInstall(): Boolean {
        val pending = pendingInstallApkFile ?: return false
        if (!pending.exists() || pending.length() == 0L) {
            pendingInstallApkFile = null
            return false
        }
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appContext.packageManager.canRequestPackageInstalls()) {
                Log.i(TAG, "Permission granted! Resuming pending install: ${pending.absolutePath}")
                pendingInstallApkFile = null
                installApk(pending)
                return true
            }
        }
        return false
    }

    /**
     * 最新の APK をバックグラウンドで高速ダウンロードし、完了時に自動でインストール画面へ遷移します。
     */
    fun startDownloadAndInstall(downloadUrl: String, onStatus: ((String) -> Unit)? = null) {
        val appContext = context.applicationContext
        val fileName = "app-serena-release.apk"
        val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val destinationFile = File(updateDir, fileName)
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
                destinationFile.setReadable(true, false)

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
     * PackageInstaller セッション API (第1優先) または FileProvider (フォールバック) で
     * Android 標準パッケージインストーラーを確実に起動します。
     */
    fun installApk(file: File) {
        val appContext = context.applicationContext
        try {
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty: ${file.absolutePath}")
                return
            }
            file.setReadable(true, false)

            // Android 8.0+ 未知のアプリ提供元の許可チェック
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    pendingInstallApkFile = file
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

            pendingInstallApkFile = null
            val launchMsg = appContext.getString(R.string.update_launching_installer)
            Toast.makeText(appContext, launchMsg, Toast.LENGTH_SHORT).show()
            if (SerenaScreenReaderService.isServiceRunning()) {
                SerenaScreenReaderService.instance?.speak(launchMsg, TextToSpeech.QUEUE_FLUSH)
            }

            // 1. Android 標準 PackageInstaller Session API の試行 (Android 10+ / 14 / 17 で最も堅牢)
            val sessionSuccess = tryInstallViaPackageInstallerSession(appContext, file)
            if (sessionSuccess) {
                Log.i(TAG, "Successfully launched PackageInstaller session")
                return
            }

            // 2. フォールバック: FileProvider 経由の ACTION_VIEW
            installViaFileProvider(appContext, file)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}", e)
            val failMsg = appContext.getString(R.string.update_install_failed, e.message ?: "")
            Toast.makeText(appContext, failMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryInstallViaPackageInstallerSession(appContext: Context, file: File): Boolean {
        try {
            val packageInstaller = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(file.length())
                setAppPackageName(appContext.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            file.inputStream().use { input ->
                session.openWrite("package", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(appContext, AutoUpdateInstallReceiver::class.java).apply {
                action = AutoUpdateInstallReceiver.ACTION_INSTALL_STATUS
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
            session.close()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller session failed: ${e.message}, falling back to FileProvider")
            return false
        }
    }

    private fun installViaFileProvider(appContext: Context, file: File) {
        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resInfoList = appContext.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val pkg = resolveInfo.activityInfo.packageName
            appContext.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        appContext.startActivity(installIntent)
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
