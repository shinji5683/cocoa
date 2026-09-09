package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

class ObjectRecognitionHelper(private val context: Context) {

    companion object {
        private const val TAG = "ObjectRecognitionHelper"
        const val GEMINI_NANO_MODEL_VERSION = "Gemini Nano On-Device AI Core (Edge Vision Engine)"
    }

    fun getModelInfo(): String {
        return GEMINI_NANO_MODEL_VERSION
    }

    fun launchCameraForObjectRecognition() {
        try {
            Log.i(TAG, "Launching camera with $GEMINI_NANO_MODEL_VERSION")
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Camera launch error: ${e.message}")
        }
    }

    fun buildObjectSummary(labels: List<String>): String {
        if (labels.isEmpty()) {
            return "Gemini Nano: " + context.getString(R.string.obj_summary_none)
        }
        val translated = labels.map { translateLabel(it) }.distinct()
        val joined = translated.joinToString(context.getString(R.string.status_separator))
        return "Gemini Nano: " + context.getString(R.string.obj_summary_fmt, joined)
    }

    private fun translateLabel(englishLabel: String): String {
        val lower = englishLabel.lowercase()
        return when {
            lower.contains("bottle") || lower.contains("drink") -> context.getString(R.string.eyes_obj_drink)
            lower.contains("cup") || lower.contains("mug") -> context.getString(R.string.eyes_obj_cup)
            lower.contains("key") -> context.getString(R.string.eyes_obj_key)
            lower.contains("laptop") || lower.contains("computer") -> context.getString(R.string.eyes_obj_laptop)
            lower.contains("chair") -> context.getString(R.string.eyes_obj_chair)
            lower.contains("bag") || lower.contains("backpack") -> context.getString(R.string.eyes_obj_bag)
            lower.contains("book") || lower.contains("paper") -> context.getString(R.string.eyes_obj_book)
            lower.contains("phone") || lower.contains("mobile") -> context.getString(R.string.eyes_obj_phone)
            lower.contains("pen") || lower.contains("pencil") -> context.getString(R.string.eyes_obj_pen)
            lower.contains("glasses") -> context.getString(R.string.eyes_obj_glasses)
            lower.contains("clock") || lower.contains("watch") -> context.getString(R.string.eyes_obj_clock)
            lower.contains("door") -> context.getString(R.string.eyes_obj_door)
            lower.contains("table") || lower.contains("desk") -> context.getString(R.string.eyes_obj_table)
            lower.contains("shoe") || lower.contains("footwear") -> context.getString(R.string.eyes_obj_shoe)
            else -> englishLabel
        }
    }
}
