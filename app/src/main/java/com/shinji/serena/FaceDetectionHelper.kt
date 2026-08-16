package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import com.shinji.serena.ai.GeminiNanoEngine

class FaceDetectionHelper(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectionHelper"
    }

    private val nanoEngine = GeminiNanoEngine(context)

    fun launchCameraForFaceAnalysis() {
        try {
            val intent = Intent(context, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "FACE")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Camera launch error: ${e.message}")
        }
    }

    fun buildFaceAnalysisSummary(
        faceCount: Int,
        smileProbability: Float?,
        leftEyeOpenProb: Float?,
        rightEyeOpenProb: Float?,
        centerXRatio: Float
    ): String {
        if (faceCount <= 0) {
            return "人物は見当たりません。"
        }

        val smilingCount = if ((smileProbability ?: 0f) > 0.4f) 1 else 0
        val isLooking = (leftEyeOpenProb ?: 0f) > 0.5f && (rightEyeOpenProb ?: 0f) > 0.5f

        val positionStr = when {
            centerXRatio < 0.35f -> "左側"
            centerXRatio > 0.65f -> "右側"
            else -> "正面"
        }

        val expressionStr = when {
            (smileProbability ?: 0f) > 0.7f -> "にっこり満面の笑顔"
            (smileProbability ?: 0f) > 0.35f -> "優しく微笑んでいる表情"
            else -> "穏やかな表情"
        }

        val eyesStr = when {
            isLooking -> "こちらを見ています"
            (leftEyeOpenProb ?: 1f) < 0.2f && (rightEyeOpenProb ?: 1f) < 0.2f -> "目を閉じています"
            else -> ""
        }

        val details = listOf(
            "${positionStr}に人が${faceCount}人います",
            expressionStr,
            eyesStr
        ).filter { it.isNotEmpty() }

        return details.joinToString("。") + "。"
    }
}
