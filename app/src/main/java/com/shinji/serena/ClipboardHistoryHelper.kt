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

    fun addClip(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val currentList = getHistory().toMutableList()
        currentList.remove(trimmed)
        currentList.add(0, trimmed)

        while (currentList.size > MAX_HISTORY_SIZE) {
            currentList.removeAt(currentList.size - 1)
        }

        saveList(currentList)
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


