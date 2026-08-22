package com.shinji.serena.translation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.shinji.serena.SafeContextUtils.getSafeSharedPreferences

/**
 * 翻訳読み上げモード
 */
enum class TranslationMode(val displayName: String) {
    ORIGINAL_THEN_TRANSLATION("原文を読んだ後に日本語訳を連続読み上げ"),
    TRANSLATION_ONLY("日本語訳のみ読み上げ"),
    OFF("翻訳オフ (原文のみ)")
}

/**
 * InstantTranslationHelper
 *
 * 外国語（英語等）のテキストを検出した際に、オンデバイスML Kitで即座に翻訳し、
 * 「原文 ➔ 日本語訳」の順序でスムーズに連続読み上げを提供するヘルパー。
 */
class InstantTranslationHelper(private val context: Context) {

    companion object {
        private const val TAG = "InstantTranslation"
        private const val PREFS_NAME = "serena_translation_prefs"
        private const val KEY_TRANSLATION_MODE = "translation_mode"
    }

    private val prefs: SharedPreferences = com.shinji.serena.SafeContextUtils.getSafeSharedPreferences(context, PREFS_NAME)
    private val languageIdentifier = LanguageIdentification.getClient()
    private var englishJapaneseTranslator: Translator? = null
    private var isTranslatorReady = false

    private val translationCache = mutableMapOf<String, String>()

    var mode: TranslationMode
        get() {
            val name = prefs.getString(KEY_TRANSLATION_MODE, null) ?: return TranslationMode.ORIGINAL_THEN_TRANSLATION
            return try {
                TranslationMode.valueOf(name)
            } catch (_: Exception) {
                TranslationMode.ORIGINAL_THEN_TRANSLATION
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TRANSLATION_MODE, value.name).apply()
        }

    init {
        initTranslator()
    }

    private fun initTranslator() {
        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.JAPANESE)
                .build()
            val translator = Translation.getClient(options)
            englishJapaneseTranslator = translator

            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    isTranslatorReady = true
                    Log.i(TAG, "On-device English-Japanese translation model is ready.")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to download translation model: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing translator: ${e.message}")
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
        if (mode == TranslationMode.OFF || originalText.length < 3) {
            return
        }

        // 日本語のみで構成されている場合は翻訳不要
        if (isMostlyJapanese(originalText)) {
            return
        }

        // キャッシュチェック
        translationCache[originalText]?.let { cached ->
            callback(formatTranslatedAnnouncement(originalText, cached))
            return
        }

        // 言語判定
        languageIdentifier.identifyLanguage(originalText)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und" && languageCode != "ja") {
                    translateText(originalText, languageCode, callback)
                }
            }
            .addOnFailureListener {
                // フォールバック: 英字比率が高い場合は英語として翻訳試行
                if (isMostlyLatin(originalText)) {
                    translateText(originalText, "en", callback)
                }
            }
    }

    private fun translateText(originalText: String, langCode: String, callback: (String) -> Unit) {
        val translator = englishJapaneseTranslator
        if (translator == null || !isTranslatorReady) {
            // オフライン基本辞書フォールバック
            val fallback = getDictionaryFallback(originalText)
            if (fallback != null) {
                translationCache[originalText] = fallback
                callback(formatTranslatedAnnouncement(originalText, fallback))
            }
            return
        }

        translator.translate(originalText)
            .addOnSuccessListener { translatedText ->
                if (translatedText.isNotEmpty() && translatedText != originalText) {
                    translationCache[originalText] = translatedText
                    callback(formatTranslatedAnnouncement(originalText, translatedText))
                }
            }
            .addOnFailureListener {
                val fallback = getDictionaryFallback(originalText)
                if (fallback != null) {
                    callback(formatTranslatedAnnouncement(originalText, fallback))
                }
            }
    }

    private fun formatTranslatedAnnouncement(original: String, translated: String): String {
        return when (mode) {
            TranslationMode.ORIGINAL_THEN_TRANSLATION -> "日本語訳：「$translated」"
            TranslationMode.TRANSLATION_ONLY -> translated
            TranslationMode.OFF -> ""
        }
    }

    private fun isMostlyJapanese(text: String): Boolean {
        var japaneseChars = 0
        for (c in text) {
            if (c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c in '\u4E00'..'\u9FFF') {
                japaneseChars++
            }
        }
        return japaneseChars.toFloat() / text.length > 0.4f
    }

    private fun isMostlyLatin(text: String): Boolean {
        var latinChars = 0
        for (c in text) {
            if (c in 'a'..'z' || c in 'A'..'Z') {
                latinChars++
            }
        }
        return latinChars.toFloat() / text.length > 0.6f
    }

    private fun getDictionaryFallback(text: String): String? {
        val lower = text.trim().lowercase()
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
            "previous" -> "前へ"
            else -> null
        }
    }

    fun close() {
        try {
            englishJapaneseTranslator?.close()
            languageIdentifier.close()
        } catch (_: Exception) {}
    }
}
