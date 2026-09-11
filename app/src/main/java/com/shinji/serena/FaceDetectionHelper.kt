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
            smile >= 0.80f -> Pair(
                context.getString(R.string.face_emotion_big_smile),
                context.getString(R.string.face_emotion_big_smile_vibe)
            )
            smile in 0.50f..0.80f -> Pair(
                context.getString(R.string.face_emotion_smile),
                context.getString(R.string.face_emotion_smile_vibe)
            )
            smile in 0.20f..0.50f -> Pair(
                context.getString(R.string.face_emotion_gentle_smile),
                context.getString(R.string.face_emotion_gentle_smile_vibe)
            )
            smile in 0.07f..0.20f -> Pair(
                context.getString(R.string.face_emotion_calm),
                context.getString(R.string.face_emotion_calm_vibe)
            )
            else -> {
                if (leftEye > 0.85f && rightEye > 0.85f) {
                    Pair(
                        context.getString(R.string.face_emotion_surprised),
                        context.getString(R.string.face_emotion_surprised_vibe)
                    )
                } else if (leftEye < 0.30f && rightEye < 0.30f) {
                    Pair(
                        context.getString(R.string.face_emotion_peaceful),
                        context.getString(R.string.face_emotion_peaceful_vibe)
                    )
                } else {
                    Pair(
                        context.getString(R.string.face_emotion_serious),
                        context.getString(R.string.face_emotion_serious_vibe)
                    )
                }
            }
        }

        // 顔の状態・目・視線
        val eyesStr = when {
            leftEye < 0.20f && rightEye < 0.20f -> context.getString(R.string.face_gaze_eyes_closed)
            leftEye > 0.60f && rightEye < 0.20f -> context.getString(R.string.face_gaze_wink_right)
            rightEye > 0.60f && leftEye < 0.20f -> context.getString(R.string.face_gaze_wink_left)
            leftEye > 0.40f && rightEye > 0.40f -> context.getString(R.string.face_gaze_straight)
            else -> context.getString(R.string.face_eyes_relaxed)
        }

        val details = mutableListOf<String>()
        if (distanceStr.isNotEmpty()) {
            details.add(context.getString(R.string.face_count_direction_dist_fmt, directionStr, distanceStr, faceCount))
        } else {
            details.add(context.getString(R.string.face_count_direction_only_fmt, directionStr, faceCount))
        }
        details.add(eyesStr)
        details.add(context.getString(R.string.face_expression_vibe_summary_fmt, expressionStr, vibeStr))

        val sep = context.getString(R.string.status_separator)
        return details.joinToString(sep) + sep
    }
}
