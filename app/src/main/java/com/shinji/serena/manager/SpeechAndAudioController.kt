package com.shinji.serena.manager

import com.shinji.serena.SerenaScreenReaderService
import com.shinji.serena.SoundAndHapticHelper
import com.shinji.serena.speech.SerenaSpeechEngine

/**
 * 読み上げ(TTS)および音響・振動フィードバックを一括管理するコントローラー
 */
class SpeechAndAudioController(private val service: SerenaScreenReaderService) {

    val speechEngine: SerenaSpeechEngine = SerenaSpeechEngine(service)
    val soundHelper: SoundAndHapticHelper = SoundAndHapticHelper(service)

    fun initialize() {
        // 必要に応じて初期化処理
    }

    fun speakStartupAnnouncement() {
        soundHelper.playMenuOpen()
        speak("Magandang araw po! Handa na si Serena. Ingat lagi at Mabuhay!", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
    }

    fun speak(text: String, queueMode: Int = android.speech.tts.TextToSpeech.QUEUE_FLUSH) {
        if (text.isNotBlank()) {
            speechEngine.speak(text, queueMode)
        }
    }

    fun stopSpeech() {
        service.onInterrupt()
    }

    fun playClickSound() {
        soundHelper.playClick()
    }

    fun playFocusSound() {
        soundHelper.playFocusMove()
    }

    fun playBoundarySound() {
        soundHelper.playFirstItemEdgeSound()
    }

    fun shutdown() {
        soundHelper.release()
    }
}
