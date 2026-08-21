package com.shinji.serena.ime

import android.content.Context
import com.shinji.serena.ai.GeminiNanoEngine

/**
 * Serena IME - マルチリンガル＆地域別QWERTY・点字AI言語エンジン
 * 日本語（かな/ローマ字QWERTY）、英語（US/UK/AU地域別）、タガログ語（フィリピン）、6点点字、グローバル対応。
 */
class SerenaLanguageEngine(private val context: Context) {

    enum class LanguageMode {
        JAPANESE_KANA,      // 日本語 50音かな
        JAPANESE_QWERTY,    // 日本語 ローマ字QWERTY
        ENGLISH_US,         // 英語 (アメリカ US: $)
        ENGLISH_UK,         // 英語 (イギリス UK: £)
        ENGLISH_AU,         // 英語 (オーストラリア AU: A$)
        TAGALOG,            // タガログ語 (フィリピン: ñ, ₱)
        BRAILLE,            // 6点点字入力 (日本点字2018年版)
        GLOBAL              // グローバル・絵文字
    }

    var currentMode: LanguageMode = LanguageMode.JAPANESE_KANA
        private set

    var isPhoneticModeEnabled: Boolean = true
    private val nanoEngine = GeminiNanoEngine(context)

    init {
        SerenaFullKanjiDetailDictionary.init(context)
    }

    fun switchMode(): LanguageMode {
        currentMode = when (currentMode) {
            LanguageMode.JAPANESE_KANA -> LanguageMode.JAPANESE_QWERTY
            LanguageMode.JAPANESE_QWERTY -> LanguageMode.ENGLISH_US
            LanguageMode.ENGLISH_US -> LanguageMode.ENGLISH_UK
            LanguageMode.ENGLISH_UK -> LanguageMode.ENGLISH_AU
            LanguageMode.ENGLISH_AU -> LanguageMode.TAGALOG
            LanguageMode.TAGALOG -> LanguageMode.BRAILLE
            LanguageMode.BRAILLE -> LanguageMode.GLOBAL
            LanguageMode.GLOBAL -> LanguageMode.JAPANESE_KANA
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
            LanguageMode.JAPANESE_KANA, LanguageMode.JAPANESE_QWERTY, LanguageMode.BRAILLE -> "ja"
            LanguageMode.ENGLISH_US, LanguageMode.ENGLISH_UK, LanguageMode.ENGLISH_AU -> "en"
            LanguageMode.TAGALOG -> "tl"
            LanguageMode.GLOBAL -> "en"
        }
        val aiPredictions = nanoEngine.predictNextCandidates(langStr, cleanInput, contextBefore)
        results.addAll(aiPredictions)

        if (cleanInput.isEmpty()) {
            // 入力中文字列がない場合のデフォルト文脈候補
            when (currentMode) {
                LanguageMode.JAPANESE_KANA, LanguageMode.JAPANESE_QWERTY, LanguageMode.BRAILLE -> {
                    results.add(Pair("こんにちは", "挨拶：こんにちは"))
                    results.add(Pair("ありがとうございます", "感謝：ありがとうございます"))
                    results.add(Pair("よろしくお願いします", "挨拶：よろしくお願いします"))
                    results.add(Pair("お疲れ様です", "労い：お疲れ様です"))
                    results.add(Pair("Serena", "アプリ名：Serenaスクリーンリーダー"))
                }
                LanguageMode.ENGLISH_US -> {
                    results.add(Pair("Hello", "US Greeting: Hello"))
                    results.add(Pair("Thank you", "US Expression: Thank you"))
                    results.add(Pair("Awesome", "US Expression: Awesome"))
                    results.add(Pair("Serena IME", "App Name: Serena IME"))
                }
                LanguageMode.ENGLISH_UK -> {
                    results.add(Pair("Good morning", "UK Greeting: Good morning"))
                    results.add(Pair("Cheers", "UK Expression: Cheers (ありがとう)"))
                    results.add(Pair("Brilliant", "UK Expression: Brilliant (素晴らしい)"))
                    results.add(Pair("Serena IME", "App Name: Serena IME"))
                }
                LanguageMode.ENGLISH_AU -> {
                    results.add(Pair("G'day", "AU Greeting: G'day mate!"))
                    results.add(Pair("No worries", "AU Expression: No worries (どういたしまして)"))
                    results.add(Pair("Cheers", "AU Expression: Cheers"))
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
            LanguageMode.JAPANESE_KANA, LanguageMode.JAPANESE_QWERTY, LanguageMode.BRAILLE -> {
                val convertedHiragana = if (currentMode == LanguageMode.JAPANESE_QWERTY) {
                    convertRomajiToHiragana(cleanInput)
                } else {
                    cleanInput
                }

                results.add(Pair(convertedHiragana, "ひらがな：$convertedHiragana"))

                // 日本語単語・漢字変換辞書
                val japaneseDict = mapOf(
                    "しんじ" to listOf("晋司", "新司", "慎二", "真二"),
                    "しん" to listOf("新", "進", "晋", "真", "心", "信", "伸"),
                    "さきやま" to listOf("崎山", "先山"),
                    "せれな" to listOf("Serena", "セレナ"),
                    "あいえむいー" to listOf("IME", "アイエムイー"),
                    "あい" to listOf("愛", "相", "会", "合"),
                    "せい" to listOf("晴", "正", "清", "生", "成", "声"),
                    "じ" to listOf("司", "治", "字", "時", "事", "次", "寺"),
                    "とうきょう" to listOf("東京"),
                    "おおがき" to listOf("大垣"),
                    "ぎふ" to listOf("岐阜"),
                    "なごや" to listOf("名古屋")
                )

                japaneseDict[convertedHiragana]?.forEach { kanji ->
                    val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(kanji)
                    results.add(Pair(kanji, detail))
                }

                // 1文字ずつの詳細漢字マッチ
                for (char in convertedHiragana) {
                    val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(char.toString())
                    if (detail.isNotEmpty() && detail != char.toString()) {
                        results.add(Pair(char.toString(), detail))
                    }
                }
            }

            LanguageMode.ENGLISH_US, LanguageMode.ENGLISH_UK, LanguageMode.ENGLISH_AU -> {
                results.add(Pair(cleanInput, "Word: $cleanInput"))
                results.add(Pair(cleanInput.lowercase(), "Lowercase: ${cleanInput.lowercase()}"))
                results.add(Pair(cleanInput.uppercase(), "Uppercase: ${cleanInput.uppercase()}"))
                results.add(Pair(cleanInput.replaceFirstChar { it.uppercase() }, "Capitalized: ${cleanInput.replaceFirstChar { it.uppercase() }}"))
            }

            LanguageMode.TAGALOG -> {
                results.add(Pair(cleanInput, "Salita: $cleanInput"))
                results.add(Pair(cleanInput.lowercase(), "Maliit: ${cleanInput.lowercase()}"))
                results.add(Pair(cleanInput.uppercase(), "Malaki: ${cleanInput.uppercase()}"))
            }

            LanguageMode.GLOBAL -> {
                results.add(Pair(cleanInput, "Raw: $cleanInput"))
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

    /**
     * ローマ字から平仮名への高速変換
     */
    fun convertRomajiToHiragana(romaji: String): String {
        var str = romaji.lowercase()
        val romajiMap = listOf(
            "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
            "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "shi" to "し",
            "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "chi" to "ち", "tsu" to "つ",
            "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
            "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
            "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
            "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
            "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
            "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "ji" to "じ",
            "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
            "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
            "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
            "sa" to "さ", "su" to "す", "se" to "せ", "so" to "そ",
            "ta" to "た", "ti" to "ち", "tu" to "つ", "te" to "て", "to" to "と",
            "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
            "ha" to "は", "hi" to "ひ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
            "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
            "ya" to "や", "yu" to "ゆ", "yo" to "よ",
            "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
            "wa" to "わ", "wo" to "を", "nn" to "ん",
            "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
            "za" to "ざ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
            "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
            "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
            "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
            "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
            "-" to "ー"
        )

        for ((r, h) in romajiMap) {
            str = str.replace(r, h)
        }
        return str
    }
}
