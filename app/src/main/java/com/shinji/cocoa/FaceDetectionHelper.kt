package com.shinji.cocoa

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

class FaceDetectionHelper(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectionHelper"
    }

    fun launchCameraForFaceAnalysis() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
            return "人物は検出されませんでした。"
        }

        val parts = mutableListOf<String>()

        // 1. 人数
        parts.add("${faceCount}人の人物を検出しました")

        // 2. 画面上の位置
        val positionStr = when {
            centerXRatio < 0.35f -> "画面の左側"
            centerXRatio > 0.65f -> "画面の右側"
            else -> "画面の中央正面"
        }
        parts.add(positionStr)

        // 3. 表情（笑顔度）
        if (smileProbability != null) {
            val expression = when {
                smileProbability > 0.7f -> "満面の笑顔です"
                smileProbability > 0.3f -> "優しく微笑んでいます"
                else -> "穏やかで真剣な表情です"
            }
            parts.add(expression)
        }

        // 4. 目の状態
        if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
            val eyeState = when {
                leftEyeOpenProb > 0.5f && rightEyeOpenProb > 0.5f -> "両目をしっかり開けています"
                leftEyeOpenProb <= 0.3f && rightEyeOpenProb <= 0.3f -> "目を閉じています"
                else -> "ウインクしています"
            }
            parts.add(eyeState)
        }

        return parts.joinToString("、")
    }
}
