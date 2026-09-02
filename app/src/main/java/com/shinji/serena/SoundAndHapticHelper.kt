package com.shinji.serena

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
            // Android 14/15/16/17 AudioHardening / バックグラウンドミュートを完全回避するため STREAM_ACCESSIBILITY を使用！
            toneGenerator = ToneGenerator(AudioManager.STREAM_ACCESSIBILITY, 100)
        } catch (e: Exception) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)
            } catch (ex: Exception) {
                Log.e(TAG, "ToneGenerator init failed: ${ex.message}")
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun announceTts(text: String) {
        SerenaScreenReaderService.instance?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH)
    }

    fun performHoverHaptic() {
        vibrate(10)
    }

    fun performKeyClickHaptic() {
        playClick()
    }

    fun playScroll(isForward: Boolean = true) {
        val toneType = if (isForward) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        toneGenerator?.startTone(toneType, 30)
        vibrate(12)
    }

    enum class HapticTexture {
        BUTTON,
        IMAGE,
        SLIDER,
        EDIT_TEXT,
        LINK,
        DEFAULT
    }

    fun playHapticTexture(texture: HapticTexture) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null) {
            val effect = when (texture) {
                HapticTexture.BUTTON -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticTexture.IMAGE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticTexture.SLIDER -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticTexture.EDIT_TEXT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticTexture.LINK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticTexture.DEFAULT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            vibrator?.vibrate(effect)
        } else {
            val duration = when (texture) {
                HapticTexture.BUTTON -> 25L
                HapticTexture.IMAGE -> 10L
                HapticTexture.SLIDER -> 18L
                HapticTexture.EDIT_TEXT -> 35L
                HapticTexture.LINK -> 15L
                HapticTexture.DEFAULT -> 12L
            }
            vibrate(duration)
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

    fun playSpatialTouchFeedback(normX: Float, normY: Float, isButton: Boolean) {
        val toneType = when {
            normX < 0.25f -> ToneGenerator.TONE_DTMF_1
            normX < 0.50f -> ToneGenerator.TONE_DTMF_2
            normX < 0.75f -> ToneGenerator.TONE_DTMF_3
            else -> ToneGenerator.TONE_DTMF_4
        }
        val duration = if (isButton) 40 else 20
        toneGenerator?.startTone(toneType, duration)

        val vibeTime = when {
            isButton -> 35L
            normY < 0.33f -> 10L
            normY < 0.66f -> 18L
            else -> 25L
        }
        vibrate(vibeTime)
    }

    fun playClick() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 45)
        vibrate(30)
    }

    fun playMenuOpen() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        vibrate(20)
    }

    fun playFullChargeJingle() {
        // 満充電完了の華やかな3音ジングル♪
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, 50)
            vibrate(15)
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_3, 50)
            vibrate(15)
        }, 70)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
            vibrate(30)
        }, 140)
    }

    fun playWarningSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
        vibratePattern(longArrayOf(0, 50, 40, 50))
    }

    fun playFirstItemEdgeSound() {
        // 1番目（最初）の項目：高音チャイム + 軽快なダブル振動
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 60)
        vibratePattern(longArrayOf(0, 20, 20, 20))
    }

    fun playLastItemEdgeSound() {
        // 一番下（最後）の項目：低音境界チャイム + 重厚なダブル振動
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 90)
        vibratePattern(longArrayOf(0, 35, 30, 35))
    }

    fun playEdgeReached() {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 90)
        vibratePattern(longArrayOf(0, 35, 30, 35))
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

    var isSpatialSoundstageEnabled: Boolean = true
    var isAudioDuckingEnabled: Boolean = true
    var isNightWhisperModeEnabled: Boolean = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun requestDuckAudioFocus() {
        if (!isAudioDuckingEnabled || audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ACCESSIBILITY, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestDuckAudioFocus error: ${e.message}")
        }
    }

    fun abandonDuckAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .build()
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "abandonDuckAudioFocus error: ${e.message}")
        }
    }

    fun playSpatialNodeSound(bounds: android.graphics.Rect, screenWidth: Int, screenHeight: Int) {
        if (!isSpatialSoundstageEnabled) {
            playFocusMove()
            return
        }

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // 左右パンニング (-1.0f ~ 1.0f)
        val panX = if (screenWidth > 0) ((centerX.toFloat() / screenWidth.toFloat()) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f) else 0.0f

        // 上下高低ピッチ判定 (画面上部: 高音DTMF_1/3, 画面中央: DTMF_5, 画面下部: 低音DTMF_7/9)
        val normalizedY = if (screenHeight > 0) (centerY.toFloat() / screenHeight.toFloat()).coerceIn(0.0f, 1.0f) else 0.5f

        val toneType = when {
            normalizedY < 0.25f -> ToneGenerator.TONE_DTMF_1 // 上部 (頭上・高音)
            normalizedY < 0.50f -> ToneGenerator.TONE_DTMF_4 // 上寄り
            normalizedY < 0.75f -> ToneGenerator.TONE_DTMF_7 // 下寄り
            else -> ToneGenerator.TONE_DTMF_0               // 最下部 (低音)
        }

        toneGenerator?.startTone(toneType, 30)
        vibrate(12)
    }

    fun release() {
        abandonDuckAudioFocus()
        toneGenerator?.release()
        toneGenerator = null
        try {
            chimeExecutor.shutdownNow()
        } catch (e: Exception) {
            Log.e(TAG, "chimeExecutor shutdown error: ${e.message}")
        }
    }
}


