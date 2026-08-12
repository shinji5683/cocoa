package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

class ObjectRecognitionHelper(private val context: Context) {

    companion object {
        private const val TAG = "ObjectRecognitionHelper"
        const val GEMMA_MODEL_VERSION = "Gemma 4 On-Device Vision Engine v4.0 (User-First Edition)"
    }

    fun getModelInfo(): String {
        return GEMMA_MODEL_VERSION
    }

    fun launchCameraForObjectRecognition() {
        try {
            Log.i(TAG, "Launching camera with $GEMMA_MODEL_VERSION")
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
            return "Gemma 4 AI: 特定の物体は検出されませんでした。"
        }
        val translated = labels.map { translateLabel(it) }.distinct()
        return "Gemma 4 AI解析: 画面内に ${translated.joinToString("、")} を検出しました。"
    }

    private fun translateLabel(englishLabel: String): String {
        val lower = englishLabel.lowercase()
        return when {
            lower.contains("bottle") -> "ペットボトル"
            lower.contains("cup") || lower.contains("mug") -> "コップ"
            lower.contains("key") -> "鍵"
            lower.contains("laptop") || lower.contains("computer") -> "ノートパソコン"
            lower.contains("chair") -> "椅子"
            lower.contains("bag") || lower.contains("backpack") -> "カバン"
            lower.contains("book") || lower.contains("paper") -> "書籍・書類"
            lower.contains("phone") || lower.contains("mobile") -> "スマートフォン"
            lower.contains("pen") || lower.contains("pencil") -> "筆記具"
            lower.contains("glasses") -> "メガネ"
            lower.contains("clock") || lower.contains("watch") -> "時計"
            lower.contains("door") -> "ドア"
            lower.contains("table") || lower.contains("desk") -> "机"
            lower.contains("shoe") || lower.contains("footwear") -> "靴"
            else -> englishLabel
        }
    }
}

