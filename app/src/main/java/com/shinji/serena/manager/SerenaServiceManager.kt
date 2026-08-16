package com.shinji.serena.manager

import android.util.Log
import com.shinji.serena.SerenaScreenReaderService
import com.shinji.serena.gesture.SerenaGestureDispatcher
import com.shinji.serena.navigation.SerenaFocusNavigator

/**
 * Serena Screen Reader のすべてのマネージャーとサブシステムを統合管理するオーケストレーター
 */
class SerenaServiceManager(private val service: SerenaScreenReaderService) {

    companion object {
        private const val TAG = "SerenaServiceManager"
    }

    val audioController: SpeechAndAudioController = SpeechAndAudioController(service)
    val visionController: VisionAiController = VisionAiController(service)

    lateinit var focusNavigator: SerenaFocusNavigator
        private set

    lateinit var gestureDispatcher: SerenaGestureDispatcher
        private set

    lateinit var systemController: SystemUtilityController
        private set

    fun initialize() {
        Log.d(TAG, "Initializing SerenaServiceManager...")

        // 音声・オーディオの初期化
        audioController.initialize()

        // ナビゲーションとジェスチャーの初期化
        focusNavigator = SerenaFocusNavigator(service)
        gestureDispatcher = SerenaGestureDispatcher(service)

        // システムユーティリティの初期化
        systemController = SystemUtilityController(service, audioController.speechEngine) {
            // シェイク検知時の動作（例: 読み上げ停止）
            audioController.stopSpeech()
        }
        systemController.startListening()

        // Vision/AIコントローラー初期化
        visionController.initialize()

        Log.d(TAG, "SerenaServiceManager initialized successfully.")
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down SerenaServiceManager...")
        systemController.stopListening()
        audioController.shutdown()
        visionController.shutdown()
    }
}
