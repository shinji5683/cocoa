package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class OcrCameraHelper(private val context: Context) {

    companion object {
        private const val TAG = "OcrCameraHelper"
    }

    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private var lastRecognizedText = ""

    fun launchCameraForTextRecognition() {
        try {
            val intent = Intent(context, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "OCR")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LiveVisionActivity for OCR: ${e.message}")
        }
    }

    fun recognizeTextFromBitmap(bitmap: Bitmap, onResult: (String) -> Unit) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isNotEmpty()) {
                        lastRecognizedText = text
                        onResult(text)
                    } else {
                        onResult("文字は見つかりませんでした。")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR recognition error: ${e.message}")
                    onResult("文字の読み取りに失敗しました。")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during OCR: ${e.message}")
            onResult("文字認識エラーが発生しました。")
        }
    }

    fun captureAndRecognize(onResult: (String) -> Unit) {
        // 静的なダミーの連呼を廃止。直近の認識結果または変化時のみコールバック
        if (lastRecognizedText.isNotEmpty()) {
            onResult(lastRecognizedText)
        }
    }
}
