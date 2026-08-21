package com.shinji.serena

import android.content.Context
import android.content.Intent
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

        val smile = smileProbability ?: 0f
        val leftEye = leftEyeOpenProb ?: 0.5f
        val rightEye = rightEyeOpenProb ?: 0.5f

        val positionStr = when {
            centerXRatio < 0.35f -> "左側"
            centerXRatio > 0.65f -> "右側"
            else -> "正面"
        }

        val (expressionStr, vibeStr) = when {
            smile >= 0.80f -> Pair("パッと明るい満面の笑み", "とても嬉しそうにしています")
            smile in 0.50f..0.80f -> Pair("ニッコリ笑顔", "親しみやすく明るい雰囲気です")
            smile in 0.20f..0.50f -> Pair("優しい微笑み（ほほえみ）", "穏やかで安心している様子です")
            smile in 0.07f..0.20f -> Pair("穏やかでリラックスした表情", "落ち着いた雰囲気です")
            else -> {
                if (leftEye > 0.85f && rightEye > 0.85f) Pair("目を丸くした驚きの表情", "興味深そうにこちらを見ています")
                else if (leftEye < 0.30f && rightEye < 0.30f) Pair("目を細めた安らぎの表情", "落ち着いてリラックスしています")
                else Pair("真剣で落ち着いた表情", "真面目にこちらに注目しています")
            }
        }

        val eyesStr = when {
            leftEye < 0.20f && rightEye < 0.20f -> "目を閉じています"
            leftEye > 0.60f && rightEye < 0.20f -> "右目でウインクしています😉"
            rightEye > 0.60f && leftEye < 0.20f -> "左目でウインクしています😉"
            leftEye > 0.40f && rightEye > 0.40f -> "まっすぐこちらを見ています"
            else -> ""
        }

        val details = listOf(
            "${positionStr}に人が${faceCount}人います",
            "表情は${expressionStr}で、${vibeStr}",
            eyesStr
        ).filter { it.isNotEmpty() }

        return details.joinToString("。") + "。"
    }
}
