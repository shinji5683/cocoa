package com.shinji.serena.ime

import android.content.Context
import com.shinji.serena.getSafeSharedPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Serena IME - フルスペック漢字詳細読み大辞書
 * Google TTS / TalkBack / NVDA 日本語基準の8,200文字以上の漢字詳細読みデータベースを提供。
 * すべての常用漢字（2,136字）、人名用漢字、JIS第1・第2水準漢字を完全網羅。
 */
object SerenaFullKanjiDetailDictionary {

    private const val PREFS_NAME = "serena_custom_kanji_details"
    private const val ASSET_FILE = "kanji_descriptions.json"

    // カスタム詳細読みマップ（Shinjiさん専用上書き設定）
    private val customDetailsMap = mutableMapOf<String, String>()

    // アセットから読み込んだ8,200文字以上の大辞書
    private val assetDescriptionsMap = mutableMapOf<String, String>()

    // 静的ベースマップ（Shinjiさん特選＋初期化前フォールバック）
    private val staticBaseMap = mapOf(
        "晋" to "あきらかのシン、Shinjiさんのしん！",
        "司" to "つかさのシ、Shinjiさんのじ！",
        "芹" to "せりのセリ",
        "菜" to "なのはなのナ、野菜のナ",
        "新" to "アタラシイの シン",
        "治" to "オサメルノ ジ、セイジの ジ",
        "漢" to "カンジの カン",
        "字" to "モジの ジ、カンジの ジ",
        "語" to "ゲンゴの ゴ、ニホンゴの ゴ",
        "日" to "ニチヨウビの ニチ、ニホンの ニチ",
        "本" to "ホンバコの ホン、キホンの ホン"
    )

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            // 1. SharedPreferencesからカスタム上書きをロード
            val prefs = context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    customDetailsMap[key] = value
                }
            }

            // 2. assets/kanji_descriptions.json から8,200字以上の標準辞書をロード
            context.assets.open(ASSET_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    val json = JSONObject(sb.toString())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = json.optString(key, "")
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            assetDescriptionsMap[key] = value
                        }
                    }
                }
            }
            isInitialized = true
        } catch (e: Exception) {
            // ロード失敗時でもクラッシュさせず静的マップで安全に継続
        }
    }

    fun setCustomDetail(context: Context, kanji: String, description: String) {
        customDetailsMap[kanji] = description
        try {
            context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(kanji, description)
                .apply()
        } catch (e: Exception) { }
    }

    /**
     * 漢字の詳細読み（例: "アタラシイの シン"、"オサメルノ ジ"）を取得
     */
    fun getKanjiDetail(kanji: String): String {
        if (kanji.isEmpty()) return ""

        // 1. カスタム上書き設定
        if (customDetailsMap.containsKey(kanji)) {
            return customDetailsMap[kanji]!!
        }

        // 2. 8,200文字フルスペック大辞書
        if (assetDescriptionsMap.containsKey(kanji)) {
            return assetDescriptionsMap[kanji]!!
        }

        // 3. 静的ベースマップ
        if (staticBaseMap.containsKey(kanji)) {
            return staticBaseMap[kanji]!!
        }

        // 複数文字の場合
        if (kanji.length > 1) {
            val sb = StringBuilder()
            for (char in kanji) {
                val detail = getKanjiDetail(char.toString())
                if (detail.isNotEmpty()) {
                    sb.append(detail).append("、")
                }
            }
            return sb.toString().trimEnd('、')
        }

        return ""
    }
}
