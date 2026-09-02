package com.shinji.serena

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent

/**
 * 読み上げ詳細レベル
 */
enum class NotificationReadDetailLevel(val displayName: String) {
    FULL("すべて読み上げ（アプリ名・送信者・内容）"),
    SENDER_ONLY("送信者まで（内容非表示・プライバシー保護）"),
    APP_NAME_ONLY("アプリ名のみ"),
    CALL_ONLY("着信のみ読み上げ（通知はミュート）"),
    MUTED("通知・着信すべてミュート")
}

/**
 * 通知・着信の解析結果
 */
data class NotificationAnalysisResult(
    val isIncomingCall: Boolean,       // 着信かどうか
    val appDisplayName: String,        // アプリ名（LINE、電話、Gmail等）
    val senderOrTitle: String,         // 発信者名・送信者名・タイトル
    val contentBody: String,           // 通知本文・メッセージ内容
    val formattedAnnouncement: String, // 最終読み上げテキスト
    val shouldAnnounce: Boolean        // 読み上げるべきか
)

/**
 * SmartNotificationFilterHelper
 *
 * 通知と着信を完全に区別し、「何の通知/着信か」「誰からか」「内容」を高精度に解析して読み上げるヘルパー。
 * ユーザーが読み上げ詳細レベル（FULL, SENDER_ONLY, APP_NAME_ONLY, CALL_ONLY, MUTED）を自由に切り替え可能。
 */
class SmartNotificationFilterHelper(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "serena_notification_filter_prefs"
        private const val KEY_DETAIL_LEVEL = "notification_read_detail_level"
        private const val KEY_ANNOUNCE_CALLS = "notification_announce_calls"
        private const val KEY_FILTER_NOISE = "notification_filter_noise"
    }

    private val prefs: SharedPreferences = context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var detailLevel: NotificationReadDetailLevel
        get() {
            val name = prefs.getString(KEY_DETAIL_LEVEL, null) ?: return NotificationReadDetailLevel.FULL
            return try {
                NotificationReadDetailLevel.valueOf(name)
            } catch (_: Exception) {
                NotificationReadDetailLevel.FULL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DETAIL_LEVEL, value.name).apply()
        }

    var isAnnounceCallsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_CALLS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ANNOUNCE_CALLS, value).apply()
        }

    var isNoiseFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_NOISE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FILTER_NOISE, value).apply()
        }

    fun cycleDetailLevel(): NotificationReadDetailLevel {
        val levels = NotificationReadDetailLevel.values()
        val nextIndex = (detailLevel.ordinal + 1) % levels.size
        detailLevel = levels[nextIndex]
        return detailLevel
    }

    /**
     * AccessibilityEvent (TYPE_NOTIFICATION_STATE_CHANGED) から通知/着信を詳細解析
     */
    fun analyzeEvent(event: AccessibilityEvent): NotificationAnalysisResult? {
        val pkgName = event.packageName?.toString() ?: ""
        val parcelable = event.parcelableData
        var title = ""
        var text = ""
        var category = ""
        var isCall = false

        if (parcelable is Notification) {
            category = parcelable.category ?: ""
            val extras = parcelable.extras
            if (extras != null) {
                title = extractCharSequence(extras, Notification.EXTRA_TITLE)
                text = extractCharSequence(extras, Notification.EXTRA_TEXT)

                val bigText = extractCharSequence(extras, Notification.EXTRA_BIG_TEXT)
                if (bigText.isNotEmpty()) {
                    text = bigText
                }

                val subText = extractCharSequence(extras, Notification.EXTRA_SUB_TEXT)
                if (title.isEmpty() && subText.isNotEmpty()) {
                    title = subText
                }
            }

            // カテゴリ判定 (CATEGORY_CALL)
            if (category == Notification.CATEGORY_CALL) {
                isCall = true
            }
        }

        // フォールバック: event.text
        if (text.isEmpty() && event.text.isNotEmpty()) {
            val combined = event.text.joinToString(" ").trim()
            if (title.isEmpty()) {
                text = combined
            } else {
                text = combined
            }
        }

        val pkgLower = pkgName.lowercase()
        val textLower = text.lowercase()
        val titleLower = title.lowercase()

        // アプリ名・キーワードから着信判定
        val isCallKeyword = textLower.contains("着信") || titleLower.contains("着信") ||
                textLower.contains("通話中") || titleLower.contains("通話中") ||
                textLower.contains("呼出") || titleLower.contains("呼出") ||
                textLower.contains("incoming call") || titleLower.contains("incoming call")

        val isPhoneApp = pkgLower.contains("dialer") || pkgLower.contains("incallui") || pkgLower.contains("phone") || pkgLower.contains("telecom")

        if (isCallKeyword || (isPhoneApp && (category == Notification.CATEGORY_CALL || textLower.contains("電話") || isCall))) {
            isCall = true
        }

        // ノイズ通知（OS充電通知、宣伝など）のフィルタリング
        if (isNoiseFilterEnabled && isSystemOrAdNoise(pkgLower, titleLower, textLower)) {
            return null
        }

        val appDisplayName = getAppFriendlyName(pkgName)

        // 読み上げ要否の判定
        val currentLevel = detailLevel
        if (currentLevel == NotificationReadDetailLevel.MUTED) {
            return NotificationAnalysisResult(isCall, appDisplayName, title, text, "", false)
        }

        if (currentLevel == NotificationReadDetailLevel.CALL_ONLY && !isCall) {
            return NotificationAnalysisResult(false, appDisplayName, title, text, "", false)
        }

        // 読み上げテキストの構築
        val announcement = buildAnnouncementText(isCall, appDisplayName, title, text, currentLevel)

        return NotificationAnalysisResult(
            isIncomingCall = isCall,
            appDisplayName = appDisplayName,
            senderOrTitle = title,
            contentBody = text,
            formattedAnnouncement = announcement,
            shouldAnnounce = announcement.isNotEmpty()
        )
    }

    private fun extractCharSequence(bundle: Bundle, key: String): String {
        return try {
            bundle.getCharSequence(key)?.toString()?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun isSystemOrAdNoise(pkgLower: String, titleLower: String, textLower: String): Boolean {
        // システム充電・USB通知の除外
        val isSystemCharging = (pkgLower == "android" || pkgLower.contains("systemui")) && (
                textLower.contains("usbで充電") ||
                textLower.contains("充電しています") ||
                textLower.contains("充電中") ||
                textLower.contains("充電器") ||
                textLower.contains("usb を接続") ||
                textLower.contains("usb 接続")
        )
        if (isSystemCharging) return true

        // 宣伝・ガチャ・クーポン通知の除外
        val isAdNoise = textLower.contains("イベント開催") ||
                textLower.contains("ガチャ") ||
                textLower.contains("ログインボーナス") ||
                textLower.contains("セール") ||
                textLower.contains("クーポン") ||
                textLower.contains("お得な情報") ||
                titleLower.contains("セール")

        val isImportant = textLower.contains("着信") || textLower.contains("緊急") ||
                textLower.contains("認証") || textLower.contains("コード")

        return isAdNoise && !isImportant
    }

    private val appLabelCache = mutableMapOf<String, String>()

    private fun getAppFriendlyName(packageName: String): String {
        if (packageName.isEmpty()) return "通知"

        // キャッシュチェック
        appLabelCache[packageName]?.let { return it }

        val pkgLower = packageName.lowercase()

        // 1. 主要アプリの即時マッピング辞書
        val knownName = when {
            pkgLower.contains("youtube.music") -> "YouTube Music"
            pkgLower.contains("youtube") -> "YouTube"
            pkgLower.contains("gmail") || pkgLower == "com.google.android.gm" || (pkgLower.contains("android") && pkgLower.contains(".gm")) -> "Gmail"
            pkgLower.contains("line") -> "LINE"
            pkgLower.contains("whatsapp") -> "WhatsApp"
            pkgLower.contains("discord") -> "Discord"
            pkgLower.contains("twitter") || pkgLower.contains("x.android") -> "X"
            pkgLower.contains("instagram") -> "Instagram"
            pkgLower.contains("facebook.orca") || pkgLower.contains("messenger") -> "Messenger"
            pkgLower.contains("facebook") -> "Facebook"
            pkgLower.contains("tiktok") -> "TikTok"
            pkgLower.contains("teams") -> "Teams"
            pkgLower.contains("slack") -> "Slack"
            pkgLower.contains("zoom") -> "Zoom"
            pkgLower.contains("skype") -> "Skype"
            pkgLower.contains("chrome") -> "Chrome"
            pkgLower.contains("vending") || pkgLower.contains("play.store") -> "Google Playストア"
            pkgLower.contains("maps") -> "Googleマップ"
            pkgLower.contains("photos") -> "Googleフォト"
            pkgLower.contains("calendar") -> "カレンダー"
            pkgLower.contains("clock") || pkgLower.contains("deskclock") -> "時計・アラーム"
            pkgLower.contains("dialer") || pkgLower.contains("phone") || pkgLower.contains("incallui") || pkgLower.contains("telecom") -> "電話"
            pkgLower.contains("sms") || pkgLower.contains("messaging") || pkgLower.contains("mms") -> "メッセージ"
            pkgLower.contains("spotify") -> "Spotify"
            pkgLower.contains("paypay") -> "PayPay"
            pkgLower.contains("mercari") -> "メルカリ"
            pkgLower.contains("amazon") -> "Amazon"
            pkgLower.contains("rakuten") -> "楽天"
            pkgLower.contains("yahoo") -> "Yahoo"
            pkgLower.contains("googlequicksearchbox") -> "Google"
            pkgLower.contains("settings") -> "設定"
            pkgLower.contains("systemui") -> "システム"
            else -> null
        }

        if (knownName != null) {
            appLabelCache[packageName] = knownName
            return knownName
        }

        // 2. PackageManager からアプリの正確な表示名を取得
        try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString().trim()
            if (label.isNotEmpty() && !label.equals("null", ignoreCase = true)) {
                appLabelCache[packageName] = label
                return label
            }
        } catch (_: Exception) {}

        // 3. パッケージ名から末尾の識別名を美しく抽出（例: com.example.myawesomeapp -> Myawesomeapp）
        val lastSegment = packageName.split(".").lastOrNull { it.isNotEmpty() }
        val fallbackName = if (!lastSegment.isNullOrBlank() && lastSegment.length >= 2) {
            lastSegment.replaceFirstChar { it.uppercase() }
        } else {
            "通知"
        }

        appLabelCache[packageName] = fallbackName
        return fallbackName
    }

    private fun buildAnnouncementText(
        isCall: Boolean,
        appDisplayName: String,
        title: String,
        text: String,
        level: NotificationReadDetailLevel
    ): String {
        val cleanTitle = title.trim()
        val cleanText = text.trim()

        // 1. 着信の場合
        if (isCall) {
            val callerName = if (cleanTitle.isNotEmpty()) cleanTitle else if (cleanText.isNotEmpty()) cleanText else "不明な発信者"
            return when (level) {
                NotificationReadDetailLevel.APP_NAME_ONLY -> "${appDisplayName}の着信です"
                else -> "${appDisplayName}着信、${callerName}さんから"
            }
        }

        // 2. 一般通知の場合
        // Gemini Nano オンデバイスインテリジェンスによる認証コード・重要度解析
        val nanoEngine = com.shinji.serena.ai.GeminiNanoEngine(context)
        val aiResult = nanoEngine.analyzeNotificationIntelligence(appDisplayName, cleanTitle, cleanText)
        if (aiResult.category == com.shinji.serena.ai.GeminiNanoEngine.NotificationCategory.TWO_FACTOR_AUTH) {
            return when (level) {
                NotificationReadDetailLevel.APP_NAME_ONLY -> "${appDisplayName}の認証コード通知"
                else -> aiResult.suggestedAnnouncement
            }
        }

        val bodySummary = if (cleanText.length > 60) {
            cleanText.substring(0, 58) + "、以下省略"
        } else {
            cleanText
        }

        return when (level) {
            NotificationReadDetailLevel.APP_NAME_ONLY -> {
                "${appDisplayName}の通知"
            }
            NotificationReadDetailLevel.SENDER_ONLY -> {
                if (cleanTitle.isNotEmpty()) {
                    "${appDisplayName}、${cleanTitle}さんから"
                } else {
                    "${appDisplayName}の新しい通知"
                }
            }
            NotificationReadDetailLevel.FULL -> {
                if (cleanTitle.isNotEmpty() && bodySummary.isNotEmpty()) {
                    "${appDisplayName}、${cleanTitle}さんから「${bodySummary}」"
                } else if (cleanTitle.isNotEmpty()) {
                    "${appDisplayName}、${cleanTitle}"
                } else if (bodySummary.isNotEmpty()) {
                    "${appDisplayName}、「${bodySummary}」"
                } else {
                    "${appDisplayName}の通知があります"
                }
            }
            else -> ""
        }
    }

    private val recentNotifications = mutableListOf<NotificationAnalysisResult>()

    fun recordNotification(result: NotificationAnalysisResult) {
        recentNotifications.add(0, result)
        while (recentNotifications.size > 15) {
            recentNotifications.removeAt(recentNotifications.size - 1)
        }
    }

    fun buildNotificationDigest(): String {
        if (recentNotifications.isEmpty()) {
            return "現在、未読の重要通知はありません。"
        }
        val count = recentNotifications.size
        val topNotifs = recentNotifications.take(3).map {
            val senderPart = if (it.senderOrTitle.isNotEmpty()) it.senderOrTitle else "通知"
            "${it.appDisplayName}（$senderPart）"
        }.joinToString("、")
        return "通知ダイジェスト全${count}件。直近の通知: ${topNotifs}などがあります。"
    }
}
