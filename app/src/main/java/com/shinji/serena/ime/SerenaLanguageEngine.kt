package com.shinji.serena.ime

import android.content.Context
import com.shinji.serena.ai.GeminiNanoEngine

/**
 * Serena IME - マルチリンガルAI予測変換言語エンジン
 * 日本語・英語・タガログ語（フィリピン語 - Serenaさんの故郷の言語）・グローバル言語に対応。
 */
class SerenaLanguageEngine(private val context: Context) {

    enum class LanguageMode {
        JAPANESE,
        ENGLISH,
        TAGALOG,
        BRAILLE,
        GLOBAL
    }

    var currentMode: LanguageMode = LanguageMode.JAPANESE
        private set

    var isPhoneticModeEnabled: Boolean = true
    private val nanoEngine = GeminiNanoEngine(context)

    init {
        SerenaFullKanjiDetailDictionary.init(context)
    }

    fun switchMode(): LanguageMode {
        currentMode = when (currentMode) {
            LanguageMode.JAPANESE -> LanguageMode.ENGLISH
            LanguageMode.ENGLISH -> LanguageMode.TAGALOG
            LanguageMode.TAGALOG -> LanguageMode.BRAILLE
            LanguageMode.BRAILLE -> LanguageMode.GLOBAL
            LanguageMode.GLOBAL -> LanguageMode.JAPANESE
        }
        return currentMode
    }

    fun togglePhoneticMode(): Boolean {
        isPhoneticModeEnabled = !isPhoneticModeEnabled
        return isPhoneticModeEnabled
    }

    fun getCandidatesWithDetails(input: String, contextBefore: String = ""): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val cleanInput = input.trim()

        // 1. Serena AI / Gemini Nano による文脈予測を最優先取得
        val langStr = when (currentMode) {
            LanguageMode.JAPANESE, LanguageMode.BRAILLE -> "ja"
            LanguageMode.ENGLISH -> "en"
            LanguageMode.TAGALOG -> "tl"
            LanguageMode.GLOBAL -> "en"
        }
        val aiPredictions = nanoEngine.predictNextCandidates(langStr, cleanInput, contextBefore)
        results.addAll(aiPredictions)

        if (cleanInput.isEmpty()) {
            // 入力中文字列がない場合のデフォルト文脈候補
            when (currentMode) {
                LanguageMode.JAPANESE, LanguageMode.BRAILLE -> {
                    results.add(Pair("こんにちは", "挨拶：こんにちは"))
                    results.add(Pair("ありがとうございます", "感謝：ありがとうございます"))
                    results.add(Pair("よろしくお願いします", "挨拶：よろしくお願いします"))
                    results.add(Pair("お疲れ様です", "労い：お疲れ様です"))
                    results.add(Pair("Serena", "アプリ名：Serenaスクリーンリーダー"))
                }
                LanguageMode.ENGLISH -> {
                    results.add(Pair("Hello", "Greeting: Hello"))
                    results.add(Pair("Thank you", "Expression: Thank you"))
                    results.add(Pair("Good morning", "Greeting: Good morning"))
                    results.add(Pair("Serena IME", "App Name: Serena IME"))
                }
                LanguageMode.TAGALOG -> {
                    results.add(Pair("Salamat po", "Greeting: Salamat po (ありがとうございます)"))
                    results.add(Pair("Kamusta ka po?", "Greeting: Kamusta ka po? (お元気ですか？)"))
                    results.add(Pair("Magandang araw po", "Greeting: Magandang araw po (こんにちは)"))
                    results.add(Pair("Ingat ka po", "Greeting: Ingat ka po (気をつけてね)"))
                }
                LanguageMode.GLOBAL -> {
                    results.add(Pair("✨", "絵文字：きらきら・スパークル"))
                    results.add(Pair("🌸", "絵文字：桜・さくら"))
                    results.add(Pair("💖", "絵文字：ハート・愛情"))
                }
            }
            return results.distinctBy { it.first }
        }

        when (currentMode) {
            LanguageMode.JAPANESE, LanguageMode.BRAILLE -> {
                results.add(Pair(cleanInput, "ひらがな：$cleanInput"))

                // 日本語単語・漢字変換辞書
                val japaneseDict = mapOf(
                    // 開発者・固有名詞
                    "しんじ" to listOf("晋司", "新司", "慎二", "真二"),
                    "しん" to listOf("新", "進", "晋", "真", "心", "信", "伸"),
                    "さきやま" to listOf("崎山", "先山"),
                    "せれな" to listOf("Serena", "セレナ"),
                    "あいえむいー" to listOf("IME", "アイエムイー"),
                    // 挨拶・日常
                    "あい" to listOf("愛", "相", "会", "合"),
                    "せい" to listOf("晴", "正", "清", "生", "成", "声"),
                    "とうきょう" to listOf("東京"),
                    "にほん" to listOf("日本"),
                    "あした" to listOf("明日"),
                    "きょう" to listOf("今日"),
                    "きのう" to listOf("昨日"),
                    "ありがとう" to listOf("ありがとうございます", "有難う"),
                    "おつかれ" to listOf("お疲れ様です", "お疲れ様でした", "お疲れ"),
                    "よろしく" to listOf("よろしくお願いします", "宜しく"),
                    "おはよう" to listOf("おはようございます", "お早う"),
                    "こんにち" to listOf("こんにちは"),
                    "こんばん" to listOf("こんばんは", "今晩"),
                    // 開発・技術
                    "あんどろいど" to listOf("Android"),
                    "あくせしびりてぃ" to listOf("アクセシビリティ"),
                    "すくりーんりーだー" to listOf("スクリーンリーダー"),
                    "びるど" to listOf("ビルド"),
                    "こーど" to listOf("コード"),
                    "てすと" to listOf("テスト"),
                    "かいはつ" to listOf("開発"),
                    "きのう" to listOf("機能", "昨日"),
                    "せってい" to listOf("設定"),
                    "おんせい" to listOf("音声"),
                    "じぇすちゃー" to listOf("ジェスチャー"),
                    "ふりっく" to listOf("フリック"),
                    "かーそる" to listOf("カーソル"),
                    "よみあげ" to listOf("読み上げ"),
                    "へんかん" to listOf("変換"),
                    "けっか" to listOf("結果"),
                    "じかん" to listOf("時間"),
                    "ばってりー" to listOf("バッテリー"),
                    "つうち" to listOf("通知")
                )

                japaneseDict[cleanInput]?.forEach { word ->
                    val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(word)
                    val desc = if (detail.isNotEmpty() && detail != word) "漢字：$detail" else "変換：$word"
                    results.add(Pair(word, desc))
                }

                // プレフィックス前方一致予測
                japaneseDict.keys.filter { it.startsWith(cleanInput) && it != cleanInput }.forEach { key ->
                    japaneseDict[key]?.forEach { word ->
                        val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(word)
                        val desc = if (detail.isNotEmpty() && detail != word) "予測：$detail" else "予測：$word"
                        results.add(Pair(word, desc))
                    }
                }
            }

            LanguageMode.ENGLISH -> {
                results.add(Pair(cleanInput, "English: $cleanInput"))

                val englishDict = listOf(
                    "Accessibility", "Android", "Alpha", "Application", "Audio", "Assistant",
                    "Battery", "Bluetooth", "Build", "Button",
                    "Cancel", "Clear", "Close", "Code", "Compass", "Connection",
                    "Developer", "Device", "Download",
                    "Enable", "Engine", "Enter",
                    "Focus", "Flick", "Forward",
                    "Gesture", "Google", "Gemini", "Good morning", "Good afternoon", "Good evening",
                    "Haptic", "Hello", "Help",
                    "Input", "Install", "Internet",
                    "Japanese", "Just",
                    "Keyboard", "Keypad",
                    "Language", "Light", "Live", "Log",
                    "Message", "Microphone", "Mode",
                    "Navigation", "Network", "Next", "Notification",
                    "Object", "OK", "Open", "Option",
                    "Package", "Password", "Permission", "Phone", "Phonetic", "Play", "Predictive",
                    "Reader", "Release", "Restart", "Running",
                    "Screen", "Search", "Select", "Send", "Serena", "Service", "Setting", "Sound", "Speech", "Status", "Success",
                    "Tagalog", "TalkBack", "Tap", "Thank you", "Time", "Toggle", "Touch",
                    "Update", "Upload", "User",
                    "Version", "Vibration", "View", "Vision", "Voice", "Volume",
                    "Welcome", "Wifi", "Window", "Word"
                )

                englishDict.filter { it.startsWith(cleanInput, ignoreCase = true) }.forEach { word ->
                    results.add(Pair(word, "English word: $word"))
                }
            }

            LanguageMode.TAGALOG -> {
                results.add(Pair(cleanInput, "Tagalog: $cleanInput"))

                val tagalogDict = mapOf(
                    "salamat" to "Salamat（ありがとう）",
                    "salamat po" to "Salamat po（ありがとうございます・丁寧語）",
                    "maraming salamat" to "Maraming salamat po（どうもありがとうございます）",
                    "kamusta" to "Kamusta（元気？・こんにちは）",
                    "kamusta ka po" to "Kamusta ka po?（お元気ですか？）",
                    "maganda" to "Maganda（美しい・素晴らしい）",
                    "magandang umaga" to "Magandang umaga po（おはようございます）",
                    "magandang tanghali" to "Magandang tanghali po（お昼のこんにちは）",
                    "magandang hapon" to "Magandang hapon po（夕方のこんにちは）",
                    "magandang gabi" to "Magandang gabi po（こんばんは）",
                    "magandang araw" to "Magandang araw po（良い一日を・こんにちは）",
                    "mahal" to "Mahal（愛・大切な）",
                    "mahal kita" to "Mahal kita（愛しています・大好きです）",
                    "ingat" to "Ingat ka（気をつけてね）",
                    "ingat po" to "Ingat po kayo（お気をつけて）",
                    "mabuhay" to "Mabuhay（ようこそ・万歳・乾杯）",
                    "oo" to "Oo（はい）",
                    "opo" to "Opo（はい・丁寧語）",
                    "hindi" to "Hindi（いいえ）",
                    "hindi po" to "Hindi po（いいえ・丁寧語）",
                    "paalam" to "Paalam（さようなら）",
                    "walang anuman" to "Walang anuman（どういたしまして）",
                    "masaya" to "Masaya（嬉しい・楽しい）",
                    "tulong" to "Tulong（助け・サポート）",
                    "kaibigan" to "Kaibigan（友達）",
                    "kapatid" to "Kapatid（兄弟・姉妹）",
                    "masarap" to "Masarap（美味しい）",
                    "ayos" to "Ayos（大丈夫・OK）"
                )

                tagalogDict.filter { it.key.startsWith(cleanInput.lowercase()) }.forEach { (word, desc) ->
                    results.add(Pair(word, desc))
                }

                if (cleanInput.lowercase() == "n") {
                    results.add(Pair("ñ", "小文字 エニェ (Eñe - タガログ文字)"))
                    results.add(Pair("Ñ", "大文字 エニェ (Eñe - タガログ文字)"))
                }
            }

            LanguageMode.GLOBAL -> {
                results.add(Pair(cleanInput, "Global Unicode: $cleanInput"))
            }
        }

        return results.distinctBy { it.first }
    }

    fun getSpeechForChar(char: Char): String {
        return if (isPhoneticModeEnabled) {
            SerenaPhoneticEngine.getPhoneticReading(char)
        } else {
            char.toString()
        }
    }
}
