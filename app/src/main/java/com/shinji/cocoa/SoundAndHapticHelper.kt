package com.shinji.cocoa

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class SoundAndHapticHelper(private val context: Context) {

    companion object {
        private const val TAG = "SoundAndHapticHelper"
    }

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator init failed: ${e.message}")
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playFocusMove() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
        vibrate(20)
    }

    fun playClick() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 40)
        vibrate(35)
    }

    fun playMenuOpen() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 80)
        vibratePattern(longArrayOf(0, 30, 40, 50))
    }

    fun playActionDone() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        vibratePattern(longArrayOf(0, 25, 30, 25))
    }

    private fun vibrate(milliseconds: Long) {
        vibrator?.let {
            if (!it.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(milliseconds)
            }
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        vibrator?.let {
            if (!it.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, -1)
            }
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
