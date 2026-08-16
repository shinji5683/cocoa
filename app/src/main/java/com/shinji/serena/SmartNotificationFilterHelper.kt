package com.shinji.serena

import android.content.Context
import android.content.SharedPreferences

enum class NotificationFilterMode(val displayName: String) {
    ALL("すべての通知を読み上げ"),
    IMPORTANT_ONLY("重要通知のみ (電話・メッセージ優先)"),
    SILENT_SUMMARY("通知読み上げオフ (静音要約)")
}

class SmartNotificationFilterHelper(context: Context) {

    companion object {
        private const val PREFS_NAME = "serena_notification_filter_prefs"
        private const val KEY_FILTER_MODE = "notification_filter_mode"
    }

    private val prefs: SharedPreferences = context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentMode: NotificationFilterMode
        get() {
            val name = prefs.getString(KEY_FILTER_MODE, null) ?: return NotificationFilterMode.IMPORTANT_ONLY
            return try {
                NotificationFilterMode.valueOf(name)
            } catch (e: Exception) {
                NotificationFilterMode.IMPORTANT_ONLY
            }
        }
        set(value) {
            prefs.edit().putString(KEY_FILTER_MODE, value.name).apply()
        }

    fun cycleFilterMode(): NotificationFilterMode {
        val modes = NotificationFilterMode.values()
        val nextIndex = (currentMode.ordinal + 1) % modes.size
        currentMode = modes[nextIndex]
        return currentMode
    }

    fun shouldAnnounce(packageName: String, text: String): Boolean {
        val mode = currentMode
        if (mode == NotificationFilterMode.SILENT_SUMMARY) return false
        if (mode == NotificationFilterMode.ALL) return true

        val pkgLower = packageName.lowercase()
        val textLower = text.lowercase()

        val isMessagingOrCallApp = pkgLower.contains("dialer") ||
                pkgLower.contains("incallui") ||
                pkgLower.contains("phone") ||
                pkgLower.contains("line") ||
                pkgLower.contains("whatsapp") ||
                pkgLower.contains("skype") ||
                pkgLower.contains("teams") ||
                pkgLower.contains("zoom") ||
                pkgLower.contains("orca") ||
                pkgLower.contains("kakao") ||
                pkgLower.contains("telegram") ||
                pkgLower.contains("sms") ||
                pkgLower.contains("message") ||
                pkgLower.contains("gmail") ||
                pkgLower.contains("mail")

        val isImportantKeyword = textLower.contains("着信") ||
                textLower.contains("通話") ||
                textLower.contains("電話") ||
                textLower.contains("緊急") ||
                textLower.contains("警報") ||
                textLower.contains("コード") ||
                textLower.contains("認証")

        val isAdOrGameNoise = textLower.contains("イベント開催") ||
                textLower.contains("ガチャ") ||
                textLower.contains("ログインボーナス") ||
                textLower.contains("セール") ||
                textLower.contains("クーポン") ||
                textLower.contains("お得な情報")

        if (isAdOrGameNoise && !isImportantKeyword) return false

        return isMessagingOrCallApp || isImportantKeyword
    }

    /**
     * 長文通知・メッセージを要約して自然な日本語でアナウンス
     */
    fun formatSmartNotificationSummary(packageName: String, title: String, text: String): String {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        val pkgLower = packageName.lowercase()

        val appName = when {
            pkgLower.contains("line") -> "LINE"
            pkgLower.contains("whatsapp") -> "WhatsApp"
            pkgLower.contains("gmail") || pkgLower.contains("mail") -> "メール"
            pkgLower.contains("sms") || pkgLower.contains("message") -> "SMSメッセージ"
            pkgLower.contains("discord") -> "Discord"
            pkgLower.contains("twitter") || pkgLower.contains("x.android") -> "X"
            pkgLower.contains("teams") -> "Teams"
            pkgLower.contains("slack") -> "Slack"
            pkgLower.contains("dialer") || pkgLower.contains("phone") -> "お電話"
            else -> "通知"
        }

        if (cleanTitle.isEmpty() && cleanText.isEmpty()) {
            return "${appName}の新しい通知があります。"
        }

        // 短いメッセージならそのまま、長いメッセージは先頭要約
        val bodySummary = if (cleanText.length > 50) {
            cleanText.substring(0, 48) + "、以下省略"
        } else {
            cleanText
        }

        return if (cleanTitle.isNotEmpty() && bodySummary.isNotEmpty()) {
            "${cleanTitle}さんから${appName}：「${bodySummary}」"
        } else if (cleanTitle.isNotEmpty()) {
            "${appName}：${cleanTitle}"
        } else {
            "${appName}：${bodySummary}"
        }
    }
}


