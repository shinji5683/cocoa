package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import com.shinji.serena.ai.GeminiNanoEngine

/**
 * FaceDetectionHelper
 *
 * ML Kit Face Detection と Gemini Nano を活用し、
 * カメラに映る人物の位置、推定距離、人数、表情（満面の笑顔、真剣、驚き、ウインク等）、
 * 雰囲気を高精度にリアルタイム実況するヘルパー。
 */
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
        centerXRatio: Float,
        faceBoundingBox: Rect? = null,
        previewWidth: Int = 1080,
        previewHeight: Int = 1920
    ): String {
        if (faceCount <= 0) {
            return context.getString(R.string.face_summary_none)
        }

        val smile = smileProbability ?: 0f
        val leftEye = leftEyeOpenProb ?: 0.5f
        val rightEye = rightEyeOpenProb ?: 0.5f

        // 相対方向の判定 (時計盤表現は完全禁止、規約に準拠)
        val directionStr = when {
            centerXRatio < 0.25f -> context.getString(R.string.dir_left)
            centerXRatio in 0.25f..0.40f -> context.getString(R.string.dir_front_left)
            centerXRatio in 0.40f..0.60f -> context.getString(R.string.dir_front)
            centerXRatio in 0.60f..0.75f -> context.getString(R.string.dir_front_right)
            else -> context.getString(R.string.dir_right)
        }

        // 推定距離の計算 (顔の枠の高さ・面積比から算出)
        val distanceStr = if (faceBoundingBox != null && previewHeight > 0) {
            val faceHeightRatio = faceBoundingBox.height().toFloat() / previewHeight
            when {
                faceHeightRatio > 0.45f -> context.getString(R.string.face_distance_close_50cm)
                faceHeightRatio in 0.30f..0.45f -> context.getString(R.string.face_distance_1m)
                faceHeightRatio in 0.18f..0.30f -> context.getString(R.string.face_distance_2m)
                faceHeightRatio in 0.10f..0.18f -> context.getString(R.string.face_distance_3m)
                else -> context.getString(R.string.face_distance_far)
            }
        } else {
            ""
        }

        // 表情と雰囲気の判定
        val (expressionStr, vibeStr) = when {
            smile >= 0.80f -> Pair("満面の笑顔", "とても嬉しそうにしています")
            smile in 0.50f..0.80f -> Pair("ニッコリ笑顔", "親しみやすく明るい雰囲気です")
            smile in 0.20f..0.50f -> Pair("優しい微笑み", "穏やかで安心している様子です")
            smile in 0.07f..0.20f -> Pair("穏やかでリラックスした表情", "落ち着いた雰囲気です")
            else -> {
                if (leftEye > 0.85f && rightEye > 0.85f) Pair("目を丸くした表情", "興味深そうにこちらを見ています")
                else if (leftEye < 0.30f && rightEye < 0.30f) Pair("安らぎの表情", "落ち着いてリラックスしています")
                else Pair("真剣で落ち着いた表情", "真面目にこちらに注目しています")
            }
        }

        // 顔の状態・目・視線
        val eyesStr = when {
            leftEye < 0.20f && rightEye < 0.20f -> "目をつぶってリラックスしています"
            leftEye > 0.60f && rightEye < 0.20f -> "右目でウインクしています😉"
            rightEye > 0.60f && leftEye < 0.20f -> "左目でウインクしています😉"
            leftEye > 0.40f && rightEye > 0.40f -> "まっすぐこちらを見ています"
            else -> "リラックスした目元です"
        }

        val details = mutableListOf<String>()
        if (distanceStr.isNotEmpty()) {
            details.add("${directionStr} ${distanceStr}に人が${faceCount}人います")
        } else {
            details.add("${directionStr}に人が${faceCount}人います")
        }
        details.add(eyesStr)
        details.add("表情は${expressionStr}で、${vibeStr}です")

        return details.joinToString("。") + "。"
    }
}
