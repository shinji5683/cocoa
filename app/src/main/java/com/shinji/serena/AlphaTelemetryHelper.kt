package com.shinji.serena

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.util.Locale

class AlphaTelemetryHelper(private val context: Context) {

    companion object {
        const val PREFS_NAME = "serena_telemetry_prefs"
        const val KEY_CONSENT_GRANTED = "telemetry_consent_granted"
        const val KEY_CONSENT_ASKED = "telemetry_consent_asked"

        const val KEY_COUNT_GESTURES = "count_gestures"
        const val KEY_COUNT_TTS_JA = "count_tts_ja"
        const val KEY_COUNT_TTS_EN = "count_tts_en"
        const val KEY_COUNT_TTS_FIL = "count_tts_fil"
        const val KEY_COUNT_CALLS = "count_calls"
        const val KEY_TOTAL_CALL_SEC = "total_call_sec"

        val TARGET_EMAIL = BuildConfig.DEVELOPER_EMAIL

        @Volatile
        private var instance: AlphaTelemetryHelper? = null

        fun getInstance(context: Context): AlphaTelemetryHelper {
            return instance ?: synchronized(this) {
                instance ?: AlphaTelemetryHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConsentAsked(): Boolean = prefs.getBoolean(KEY_CONSENT_ASKED, false)

    fun isConsentGranted(): Boolean = prefs.getBoolean(KEY_CONSENT_GRANTED, false)

    fun setConsentStatus(granted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_ASKED, true)
            .putBoolean(KEY_CONSENT_GRANTED, granted)
            .apply()
    }

    fun incrementGestureCount() {
        if (!isConsentGranted()) return
        val current = prefs.getInt(KEY_COUNT_GESTURES, 0)
        prefs.edit().putInt(KEY_COUNT_GESTURES, current + 1).apply()
    }

    fun incrementTtsCount(locale: Locale) {
        if (!isConsentGranted()) return
        val key = when (locale.language) {
            "ja" -> KEY_COUNT_TTS_JA
            "en" -> KEY_COUNT_TTS_EN
            "fil", "tl" -> KEY_COUNT_TTS_FIL
            else -> KEY_COUNT_TTS_JA
        }
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    fun recordCallCompleted(durationSec: Long) {
        if (!isConsentGranted()) return
        val currentCalls = prefs.getInt(KEY_COUNT_CALLS, 0)
        val currentSec = prefs.getLong(KEY_TOTAL_CALL_SEC, 0L)
        prefs.edit()
            .putInt(KEY_COUNT_CALLS, currentCalls + 1)
            .putLong(KEY_TOTAL_CALL_SEC, currentSec + durationSec)
            .apply()
    }

    fun generateReportText(): String {
        val gestureCount = prefs.getInt(KEY_COUNT_GESTURES, 0)
        val ttsJa = prefs.getInt(KEY_COUNT_TTS_JA, 0)
        val ttsEn = prefs.getInt(KEY_COUNT_TTS_EN, 0)
        val ttsFil = prefs.getInt(KEY_COUNT_TTS_FIL, 0)
        val callsCount = prefs.getInt(KEY_COUNT_CALLS, 0)
        val callSec = prefs.getLong(KEY_TOTAL_CALL_SEC, 0L)

        val channelTag = "Canary/QPR-Beta/Stable Multi-Channel"

        return """
            === serena アルファ版 動作診断・利用統計レポート ===
            [基本情報]
            ・アプリ名: serena (スクリーンリーダー)
            ・バージョン: 1.0.0-alpha01
            ・OSバージョン: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            ・テストチャンネル: $channelTag
            ・端末モデル: ${Build.MANUFACTURER} ${Build.MODEL}
            ・同意ステータス: 許可済み

            [利用メトリクス (完全匿名)]
            ・ジェスチャー操作回数: $gestureCount 回
            ・TTS発声回数 (日本語): $ttsJa 回
            ・TTS発声回数 (英語): $ttsEn 回
            ・TTS発声回数 (タガログ語): $ttsFil 回
            ・計測通話回数: $callsCount 回
            ・累計通話計測時間: ${callSec / 60}分 ${callSec % 60}秒

            ※このレポートには個人の連絡先・メッセージ本文・音声データ等の個人情報は含まれません。
            ==================================================
        """.trimIndent()
    }

    fun sendReportViaEmail(context: Context) {
        val reportText = generateReportText()
        val subject = context.getString(R.string.telemetry_report_subject)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TARGET_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, reportText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.telemetry_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.telemetry_launch_failed_copied), Toast.LENGTH_LONG).show()
            copyReportToClipboard(context)
        }
    }

    fun copyReportToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("serena Telemetry Report", generateReportText())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.telemetry_copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
}


