package com.shinji.serena.speech

import android.speech.tts.TextToSpeech
import com.shinji.serena.GranularityMode
import com.shinji.serena.SerenaScreenReaderService

/**
 * SerenaSpeechEngine
 * TTS 音声合成・読み上げキュー・音声効果音・粒度切り替えを統一管理するモジュール
 */
class SerenaSpeechEngine(
    private val service: SerenaScreenReaderService
) {
    companion object {
        private const val TAG = "SerenaSpeechEngine"
    }

    var currentGranularity: GranularityMode = GranularityMode.DEFAULT
        private set

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        service.speak(text, queueMode)
    }

    fun speak(text: String, interrupt: Boolean) {
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        service.speak(text, mode)
    }

    fun cycleGranularity(forward: Boolean) {
        val values = GranularityMode.values()
        val currentIndex = currentGranularity.ordinal
        val nextIndex = if (forward) {
            (currentIndex + 1) % values.size
        } else {
            if (currentIndex - 1 < 0) values.size - 1 else currentIndex - 1
        }
        currentGranularity = values[nextIndex]
        service.soundHelper?.playActionDone()
        service.speak(service.getString(com.shinji.serena.R.string.granularity_spoken_header, currentGranularity.displayName), TextToSpeech.QUEUE_FLUSH)
    }

    fun setGranularity(mode: GranularityMode) {
        currentGranularity = mode
    }
}
