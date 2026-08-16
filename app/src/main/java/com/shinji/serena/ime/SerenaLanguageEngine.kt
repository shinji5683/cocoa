package com.shinji.serena.ime

import android.content.Context

/**
 * Serena IME - マルチリンガル言語エンジン
 * 日本語・英語・タガログ語（フィリピン語 - Serenaさんの故郷の言語）・グローバル言語に対応。
 */
class SerenaLanguageEngine(private val context: Context) {

    enum class LanguageMode {
        JAPANESE,
        ENGLISH,
        TAGALOG,
        GLOBAL
    }

    var currentMode: LanguageMode = LanguageMode.JAPANESE
        private set

    var isPhoneticModeEnabled: Boolean = true

    init {
        SerenaFullKanjiDetailDictionary.init(context)
    }

    fun switchMode(): LanguageMode {
        currentMode = when (currentMode) {
            LanguageMode.JAPANESE -> LanguageMode.ENGLISH
            LanguageMode.ENGLISH -> LanguageMode.TAGALOG
            LanguageMode.TAGALOG -> LanguageMode.GLOBAL
            LanguageMode.GLOBAL -> LanguageMode.JAPANESE
        }
        return currentMode
    }

    fun togglePhoneticMode(): Boolean {
        isPhoneticModeEnabled = !isPhoneticModeEnabled
        return isPhoneticModeEnabled
    }

    fun getCandidatesWithDetails(input: String): List<Pair<String, String>> {
        if (input.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, String>>()

        when (currentMode) {
            LanguageMode.JAPANESE -> {
                results.add(Pair(input, "ひらがな：$input"))

                when (input) {
                    "しんじ" -> {
                        results.add(Pair("晋司", SerenaFullKanjiDetailDictionary.getKanjiDetail("晋司")))
                        results.add(Pair("新司", SerenaFullKanjiDetailDictionary.getKanjiDetail("新司")))
                        results.add(Pair("慎二", "つつしむのシン、二のジ"))
                    }
                    "しん" -> {
                        results.add(Pair("新", SerenaFullKanjiDetailDictionary.getKanjiDetail("新")))
                        results.add(Pair("進", SerenaFullKanjiDetailDictionary.getKanjiDetail("進")))
                        results.add(Pair("晋", SerenaFullKanjiDetailDictionary.getKanjiDetail("晋")))
                        results.add(Pair("真", SerenaFullKanjiDetailDictionary.getKanjiDetail("真")))
                    }
                    "あい" -> {
                        results.add(Pair("愛", SerenaFullKanjiDetailDictionary.getKanjiDetail("愛")))
                        results.add(Pair("相", SerenaFullKanjiDetailDictionary.getKanjiDetail("相")))
                    }
                    "せい" -> {
                        results.add(Pair("晴", SerenaFullKanjiDetailDictionary.getKanjiDetail("晴")))
                        results.add(Pair("正", SerenaFullKanjiDetailDictionary.getKanjiDetail("正")))
                        results.add(Pair("清", SerenaFullKanjiDetailDictionary.getKanjiDetail("清")))
                    }
                    else -> {
                        results.add(Pair(input, "入力：$input"))
                    }
                }
            }

            LanguageMode.ENGLISH -> {
                results.add(Pair(input, "English: $input"))
                val commonEnglish = listOf("Hello", "Thank you", "Good morning", "Accessibility", "Serena", "Serena IME")
                commonEnglish.filter { it.startsWith(input, ignoreCase = true) }.forEach {
                    results.add(Pair(it, "English word: $it"))
                }
            }

            LanguageMode.TAGALOG -> {
                results.add(Pair(input, "Tagalog: $input"))

                val tagalogPhrases = mapOf(
                    "salamat" to "Salamat（ありがとう）",
                    "kamusta" to "Kamusta（元気ですか・こんにちは）",
                    "magandang" to "Magandang umaga / gabi（素晴らしい）",
                    "mahal" to "Mahal kita（愛しています）",
                    "ingat" to "Ingat ka（気をつけてね）",
                    "mabuhay" to "Mabuhay（ようこそ・万歳）",
                    "salamat po" to "Salamat po（ありがとうございます・丁寧語）"
                )

                tagalogPhrases.filter { it.key.startsWith(input.lowercase()) }.forEach { (word, desc) ->
                    results.add(Pair(word, desc))
                }

                if (input.lowercase() == "n") {
                    results.add(Pair("ñ", "小文字 エニェ (Eñe - タガログ文字)"))
                    results.add(Pair("Ñ", "大文字 エニェ (Eñe - タガログ文字)"))
                }
            }

            LanguageMode.GLOBAL -> {
                results.add(Pair(input, "Global Unicode: $input"))
            }
        }

        return results
    }

    fun getSpeechForChar(char: Char): String {
        return if (isPhoneticModeEnabled) {
            SerenaPhoneticEngine.getPhoneticReading(char)
        } else {
            char.toString()
        }
    }
}
