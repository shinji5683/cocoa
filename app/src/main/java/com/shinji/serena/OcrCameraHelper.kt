package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

class OcrCameraHelper(private val context: Context) {

    companion object {
        private const val TAG = "OcrCameraHelper"
    }

    fun launchCameraForTextRecognition() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Camera launch error: ${e.message}")
        }
    }

    fun captureAndRecognize(onResult: (String) -> Unit) {
        // テキスト/環境読み取りシミュレーション・リアルタイムフレームフィードバック
        onResult("前方クリア、テキスト未検知")
    }
}

