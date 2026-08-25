package com.shinji.serena

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SerenaApp
 *
 * アプリケーション全体のライフサイクル管理と、不意のクラッシュ（Uncaught Exception）を
 * 100%捕捉して端末内に詳細診断ログとして自動保存するクラッシュセーフティハブ。
 */
class SerenaApp : Application() {

    companion object {
        private const val TAG = "SerenaApp"
        const val CRASH_REPORT_FILE = "serena_last_crash.txt"
        const val PREFS_CRASH = "serena_crash_prefs"
        const val KEY_HAS_CRASH = "has_crash_report"

        fun getLastCrashReport(context: Context): String? {
            return try {
                val file = File(context.filesDir, CRASH_REPORT_FILE)
                if (file.exists() && file.length() > 0) {
                    file.readText()
                } else null
            } catch (_: Exception) {
                null
            }
        }

        fun clearCrashReport(context: Context) {
            try {
                val file = File(context.filesDir, CRASH_REPORT_FILE)
                if (file.exists()) file.delete()
                val prefs = context.getSafeSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_HAS_CRASH, false).apply()
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())
                val report = """
                    === Serena 自動クラッシュ診断ログ ===
                    日時: $timeStr
                    端末: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
                    OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, Codename: ${Build.VERSION.CODENAME})
                    スレッド: ${thread.name} (ID: ${thread.id})
                    例外種別: ${throwable.javaClass.name}
                    メッセージ: ${throwable.message ?: "なし"}
                    
                    --- スタックトレース ---
                    $stackTrace
                    =======================================
                """.trimIndent()

                Log.e(TAG, "FATAL CRASH INTERCEPTED:\n$report")

                // クラッシュログをファイルに永続化
                val crashFile = File(filesDir, CRASH_REPORT_FILE)
                crashFile.writeText(report)

                val prefs = getSafeSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_HAS_CRASH, true).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error writing crash log: ${e.message}")
            } finally {
                // デフォルトのハンドラーへ委譲
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
