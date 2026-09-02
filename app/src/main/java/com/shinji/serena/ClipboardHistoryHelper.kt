package com.shinji.serena

import android.content.Context
import android.content.SharedPreferences

class ClipboardHistoryHelper(context: Context) {

    companion object {
        private const val PREFS_NAME = "serena_clipboard_prefs"
        private const val KEY_HISTORY = "clipboard_history_list"
        private const val MAX_HISTORY_SIZE = 10
    }

    private val prefs: SharedPreferences = context.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addClip(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val currentList = getHistory().toMutableList()
        currentList.remove(trimmed)
        currentList.add(0, trimmed)

        while (currentList.size > MAX_HISTORY_SIZE) {
            currentList.removeAt(currentList.size - 1)
        }

        saveList(currentList)
        return analyzeClipType(trimmed)
    }

    fun analyzeClipType(text: String): String {
        return when {
            text.startsWith("http://") || text.startsWith("https://") -> {
                val host = try {
                    java.net.URI(text).host ?: "ウェブ"
                } catch (_: Exception) {
                    "ウェブ"
                }
                "クリップボードにURLをコピーしました。「$host」のリンクです。"
            }
            text.matches(Regex(".*(東京都|大阪府|京都府|北海道|.{2,3}県).*(市|区|町|村).*")) -> {
                "クリップボードに住所をコピーしました。「$text」。ナビゲーションを開始できます。"
            }
            text.matches(Regex(".*0[789]0-?[0-9]{4}-?[0-9]{4}.*")) || text.matches(Regex(".*0[0-9]{1,4}-?[0-9]{1,4}-?[0-9]{4}.*")) -> {
                "クリップボードに電話番号をコピーしました。「$text」。直接発信が可能です。"
            }
            else -> {
                "クリップボードにコピーしました: $text"
            }
        }
    }

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|||KEY_SEP|||").filter { it.isNotEmpty() }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveList(list: List<String>) {
        val joined = list.joinToString("|||KEY_SEP|||")
        prefs.edit().putString(KEY_HISTORY, joined).apply()
    }
}


