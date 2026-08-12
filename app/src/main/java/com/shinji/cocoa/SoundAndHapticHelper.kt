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
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
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
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 25)
        vibrate(15)
    }

    fun playFocusMovePanned(normalizedX: Float) {
        val clampedX = normalizedX.coerceIn(0.0f, 1.0f)
        val toneType = when {
            clampedX < 0.35f -> ToneGenerator.TONE_DTMF_1
            clampedX > 0.65f -> ToneGenerator.TONE_DTMF_3
            else -> ToneGenerator.TONE_PROP_BEEP2
        }
        toneGenerator?.startTone(toneType, 25)
        vibrate(15)
    }

    fun playClick() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 45)
        vibrate(30)
    }

    fun playMenuOpen() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 80)
        vibratePattern(longArrayOf(0, 30, 40, 50))
    }

    fun playActionDone() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        vibratePattern(longArrayOf(0, 25, 30, 25))
    }

    private val chimeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun playNhkRadioChime() {
        // NHKラジオ風時報: ポッ(57秒)、ポッ(58秒)、ポッ(59秒)、ポーン(00秒)！
        chimeExecutor.execute {
            try {
                for (i in 1..3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                    vibrate(20)
                    Thread.sleep(400)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
                vibratePattern(longArrayOf(0, 50, 50, 100))
            } catch (e: Exception) {
                Log.e(TAG, "NhkChime error: ${e.message}")
            }
        }
    }

    fun playCuteBeepChime() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 120)
        vibratePattern(longArrayOf(0, 30, 30, 50))
    }

    fun playJapaneseBellChime() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
        vibratePattern(longArrayOf(0, 60, 40, 80))
    }

    fun playProgressBeep(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val toneType = when {
            clamped < 20 -> ToneGenerator.TONE_DTMF_1
            clamped < 40 -> ToneGenerator.TONE_DTMF_3
            clamped < 60 -> ToneGenerator.TONE_DTMF_5
            clamped < 80 -> ToneGenerator.TONE_DTMF_7
            else -> ToneGenerator.TONE_DTMF_9
        }
        toneGenerator?.startTone(toneType, 40)
        vibrate(15)
    }

    private fun vibrate(milliseconds: Long) {
        try {
            vibrator?.let {
                if (!it.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(milliseconds)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrate error: ${e.message}")
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            vibrator?.let {
                if (!it.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibratePattern error: ${e.message}")
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
        try {
            chimeExecutor.shutdownNow()
        } catch (e: Exception) {
            Log.e(TAG, "chimeExecutor shutdown error: ${e.message}")
        }
    }
}
