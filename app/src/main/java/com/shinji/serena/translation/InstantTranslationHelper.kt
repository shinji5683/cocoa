package com.shinji.serena.translation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 翻訳読み上げモード
 */
enum class TranslationMode(val displayName: String, val shortLabel: String) {
    ORIGINAL_THEN_TRANSLATION("原文を読んだ後に翻訳を連続読み上げ（推奨）", "原文＋翻訳"),
    TRANSLATION_ONLY("翻訳のみ読み上げ", "翻訳のみ"),
    OFF("翻訳オフ (原文のみ)", "オフ")
}

/**
 * InstantTranslationHelper
 *
 * 外国語（英語・タガログ語・中国語・韓国語・スペイン語等）のテキストやUIアイコンを検知し、
 * ユーザーの端末設定言語（日本語または他言語）へオンデバイスで即座に翻訳し、
 * 「[何語から翻訳] 翻訳：〇〇」としてスムーズに読み上げる多言語対応リアルタイム翻訳エンジン。
 */
class InstantTranslationHelper(private val context: Context) {

    companion object {
        private const val TAG = "InstantTranslation"
        private const val PREFS_NAME = "serena_translation_prefs"
        private const val KEY_TRANSLATION_MODE = "translation_mode"
        private const val KEY_DEFAULT_TARGET_LANG = "default_target_language"

        val SUPPORTED_TARGET_LANGUAGES = listOf(
            "en" to "英語 (English)",
            "nl" to "オランダ語 (Dutch / Nederlands)",
            "de" to "ドイツ語 (German / Deutsch)",
            "tl" to "タガログ語 (Tagalog / Filipino)",
            "zh" to "中国語 (Chinese)",
            "es" to "スペイン語 (Spanish)",
            "ko" to "韓国語 (Korean)",
            "fr" to "フランス語 (French)",
            "ja" to "日本語 (Japanese)"
        )
    }

    private val prefs: SharedPreferences = com.shinji.serena.SafeContextUtils.getSafeSharedPreferences(context, PREFS_NAME)
    private val languageIdentifier = LanguageIdentification.getClient()
    private val translatorMap = ConcurrentHashMap<String, Translator>()
    private val translationCache = ConcurrentHashMap<String, String>()

    var mode: TranslationMode
        get() {
            val name = prefs.getString(KEY_TRANSLATION_MODE, null) ?: return TranslationMode.OFF
            return try {
                TranslationMode.valueOf(name)
            } catch (_: Exception) {
                TranslationMode.OFF
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TRANSLATION_MODE, value.name).apply()
        }

    var defaultTargetLanguageCode: String
        get() = prefs.getString(KEY_DEFAULT_TARGET_LANG, "en") ?: "en"
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_TARGET_LANG, value).apply()
        }

    val targetLanguageCode: String
        get() = Locale.getDefault().language.lowercase()

    init {
        // デフォルトで英語-日本語モデルをウォームアップ
        getOrCreateTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.JAPANESE)
        getOrCreateTranslator(TranslateLanguage.JAPANESE, TranslateLanguage.ENGLISH)
    }

    fun cycleDefaultTargetLanguage(): Pair<String, String> {
        val currentIndex = SUPPORTED_TARGET_LANGUAGES.indexOfFirst { it.first == defaultTargetLanguageCode }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % SUPPORTED_TARGET_LANGUAGES.size
        val nextPair = SUPPORTED_TARGET_LANGUAGES[nextIndex]
        defaultTargetLanguageCode = nextPair.first
        return nextPair
    }

    fun translateDirectly(text: String, sourceLang: String = "ja", targetLang: String = defaultTargetLanguageCode, callback: (String) -> Unit) {
        val translator = getOrCreateTranslator(sourceLang, targetLang)
        if (translator == null) {
            callback(text)
            return
        }
        translator.translate(text)
            .addOnSuccessListener { translated ->
                callback(translated)
            }
            .addOnFailureListener {
                callback(text)
            }
    }

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator? {
        val key = "${sourceLang}_$targetLang"
        return translatorMap.getOrPut(key) {
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                val client = Translation.getClient(options)
                client.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        Log.i(TAG, "Translator model $key downloaded/ready.")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Model download failed for $key: ${e.message}")
                    }
                client
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create translator for $key: ${e.message}")
                null
            }
        }
    }

    fun cycleMode(): TranslationMode {
        val modes = TranslationMode.values()
        val nextIndex = (mode.ordinal + 1) % modes.size
        mode = modes[nextIndex]
        return mode
    }

    /**
     * テキストの翻訳が必要かを判定し、翻訳コールバックを実行
     */
    fun processTranslationIfNeeded(originalText: String, callback: (String) -> Unit) {
        if (mode == TranslationMode.OFF || originalText.trim().length < 2) {
            return
        }

        val targetLang = targetLanguageCode
        val cleanText = originalText.trim()

        // 端末言語が日本語で、対象テキストがほぼ日本語なら翻訳不要
        if (targetLang == "ja" && isMostlyJapanese(cleanText)) {
            return
        }
        // 端末言語が英語で、対象テキストがほぼラテン英字なら翻訳不要
        if (targetLang == "en" && isMostlyLatin(cleanText)) {
            return
        }

        // キャッシュチェック
        translationCache["${targetLang}_$cleanText"]?.let { cached ->
            callback(cached)
            return
        }

        // 言語判定
        languageIdentifier.identifyLanguage(cleanText)
            .addOnSuccessListener { detectedLang ->
                val sourceLang = if (detectedLang != "und") detectedLang else if (isMostlyLatin(cleanText)) "en" else "und"
                if (sourceLang != "und" && sourceLang != targetLang) {
                    translateText(cleanText, sourceLang, targetLang, callback)
                }
            }
            .addOnFailureListener {
                if (targetLang == "ja" && isMostlyLatin(cleanText)) {
                    translateText(cleanText, "en", "ja", callback)
                }
            }
    }

    private fun translateText(originalText: String, sourceLang: String, targetLang: String, callback: (String) -> Unit) {
        val mlkitSource = TranslateLanguage.fromLanguageTag(sourceLang) ?: TranslateLanguage.ENGLISH
        val mlkitTarget = TranslateLanguage.fromLanguageTag(targetLang) ?: TranslateLanguage.JAPANESE

        val translator = getOrCreateTranslator(mlkitSource, mlkitTarget)
        if (translator == null) {
            // オフライン基本辞書フォールバック
            val fallback = getDictionaryFallback(originalText, targetLang)
            if (fallback != null) {
                val formatted = formatAnnouncement(originalText, fallback, sourceLang, targetLang)
                translationCache["${targetLang}_$originalText"] = formatted
                callback(formatted)
            }
            return
        }

        translator.translate(originalText)
            .addOnSuccessListener { translatedText ->
                if (translatedText.isNotEmpty() && !translatedText.equals(originalText, ignoreCase = true)) {
                    val formatted = formatAnnouncement(originalText, translatedText, sourceLang, targetLang)
                    translationCache["${targetLang}_$originalText"] = formatted
                    callback(formatted)
                }
            }
            .addOnFailureListener {
                val fallback = getDictionaryFallback(originalText, targetLang)
                if (fallback != null) {
                    val formatted = formatAnnouncement(originalText, fallback, sourceLang, targetLang)
                    translationCache["${targetLang}_$originalText"] = formatted
                    callback(formatted)
                }
            }
    }

    private fun formatAnnouncement(original: String, translated: String, sourceLang: String, targetLang: String): String {
        val sourceName = getLanguageName(sourceLang, targetLang)
        return when (mode) {
            TranslationMode.ORIGINAL_THEN_TRANSLATION -> {
                if (targetLang == "ja") {
                    "（${sourceName}から翻訳）日本語訳：「$translated」"
                } else {
                    "(Translated from $sourceName) \"$translated\""
                }
            }
            TranslationMode.TRANSLATION_ONLY -> translated
            TranslationMode.OFF -> ""
        }
    }

    private fun getLanguageName(langCode: String, targetLang: String): String {
        return if (targetLang == "ja") {
            when (langCode.lowercase()) {
                "en" -> "英語"
                "tl", "fil" -> "タガログ語"
                "zh" -> "中国語"
                "ko" -> "韓国語"
                "es" -> "スペイン語"
                "fr" -> "フランス語"
                "de" -> "ドイツ語"
                "it" -> "イタリア語"
                "pt" -> "ポルトガル語"
                "ru" -> "ロシア語"
                "vi" -> "ベトナム語"
                "th" -> "タイ語"
                "id" -> "インドネシア語"
                "ar" -> "アラビア語"
                else -> "外国語"
            }
        } else {
            Locale(langCode).getDisplayLanguage(Locale(targetLang))
        }
    }

    private fun isMostlyJapanese(text: String): Boolean {
        var japaneseChars = 0
        for (c in text) {
            if (c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c in '\u4E00'..'\u9FFF') {
                japaneseChars++
            }
        }
        return japaneseChars.toFloat() / text.length.coerceAtLeast(1) > 0.35f
    }

    private fun isMostlyLatin(text: String): Boolean {
        var latinChars = 0
        for (c in text) {
            if (c in 'a'..'z' || c in 'A'..'Z') {
                latinChars++
            }
        }
        return latinChars.toFloat() / text.length.coerceAtLeast(1) > 0.6f
    }

    private fun getDictionaryFallback(text: String, targetLang: String): String? {
        val lower = text.trim().lowercase()
        if (targetLang == "ja") {
            return when (lower) {
                "hello", "hi" -> "こんにちは"
                "welcome" -> "ようこそ"
                "settings" -> "設定"
                "profile" -> "プロフィール"
                "home" -> "ホーム"
                "notifications" -> "通知"
                "messages" -> "メッセージ"
                "search" -> "検索"
                "cancel" -> "キャンセル"
                "save" -> "保存"
                "delete" -> "削除"
                "edit" -> "編集"
                "share" -> "共有"
                "subscribe" -> "チャンネル登録"
                "subscribed" -> "登録済み"
                "download" -> "ダウンロード"
                "play" -> "再生"
                "pause" -> "一時停止"
                "next" -> "次へ"
                "previous", "prev" -> "前へ"
                "add to cart" -> "カートに追加"
                "buy now" -> "今すぐ購入"
                "sign in", "login", "log in" -> "ログイン"
                "sign out", "logout", "log out" -> "ログアウト"
                "close" -> "閉じる"
                "back" -> "戻る"
                "menu" -> "メニュー"
                "like" -> "高評価"
                "dislike" -> "低評価"
                "comment", "comments" -> "コメント"
                "camera" -> "カメラ"
                "battery" -> "バッテリー"
                "wifi" -> "Wi-Fi"
                "bluetooth" -> "Bluetooth"
                else -> null
            }
        }
        return null
    }

    fun close() {
        try {
            translatorMap.values.forEach { it.close() }
            translatorMap.clear()
            languageIdentifier.close()
        } catch (_: Exception) {}
    }
}
