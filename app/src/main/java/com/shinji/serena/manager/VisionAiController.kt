package com.shinji.serena.manager

import android.content.Context
import com.shinji.serena.FaceDetectionHelper
import com.shinji.serena.ObjectRecognitionHelper
import com.shinji.serena.OcrCameraHelper

/**
 * 画像認識・カメラOCR・Gemini Nano AI機能を取り仕切るコントローラー
 */
class VisionAiController(private val context: Context) {
    val ocrHelper: OcrCameraHelper = OcrCameraHelper(context)
    val faceHelper: FaceDetectionHelper = FaceDetectionHelper(context)
    val objectHelper: ObjectRecognitionHelper = ObjectRecognitionHelper(context)

    fun launchFaceAnalysis() {
        faceHelper.launchCameraForFaceAnalysis()
    }

    fun launchOcrCamera() {
        ocrHelper.launchCameraForTextRecognition()
    }

    fun launchObjectRecognition() {
        objectHelper.launchCameraForObjectRecognition()
    }

    fun initialize() {
        // 必要に応じて初期化処理を記述
    }

    fun shutdown() {
        // リソース解放処理
    }
}
