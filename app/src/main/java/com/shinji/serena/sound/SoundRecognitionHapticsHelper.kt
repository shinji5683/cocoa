package com.shinji.serena.sound

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.shinji.serena.SafeContextUtils.getSafeSharedPreferences
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SoundRecognitionHapticsHelper
 *
 * 周囲の環境音・危険音（踏切、サイレン、クラクション、インターホン、呼びかけ声）を
 * リアルタイムに検知し、専用のハプティクス（振動パターン）でユーザーへ即座に警告するヘルパー。
 */
class SoundRecognitionHapticsHelper(private val context: Context) {

    companion object {
        private const val TAG = "SoundRecognitionHaptics"
        private const val PREFS_NAME = "serena_sound_recognition_prefs"
        private const val KEY_ENABLED = "sound_recognition_enabled"
        private const val SAMPLE_RATE = 16000
    }

    private val prefs: SharedPreferences = com.shinji.serena.SafeContextUtils.getSafeSharedPreferences(context, PREFS_NAME)
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (value) start() else stop()
        }

    fun toggleEnabled(): Boolean {
        isEnabled = !isEnabled
        return isEnabled
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!isEnabled || isRecording) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize <= 0) return

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord initialization failed.")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            workerThread = Thread({
                processAudioStream(bufferSize)
            }, "SerenaSoundRecognitionWorker").apply {
                priority = Thread.MIN_PRIORITY
                start()
            }

            Log.i(TAG, "SoundRecognitionHaptics started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sound recognition: ${e.message}")
            isRecording = false
        }
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            workerThread?.interrupt()
            workerThread = null
        } catch (_: Exception) {}
        Log.i(TAG, "SoundRecognitionHaptics stopped.")
    }

    private fun processAudioStream(bufferSize: Int) {
        val audioBuffer = ShortArray(bufferSize)
        var lastAlertTime = 0L

        while (isRecording && !Thread.currentThread().isInterrupted) {
            val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
            if (readSize > 0) {
                // RMS音量計算
                var sum = 0.0
                for (i in 0 until readSize) {
                    sum += (audioBuffer[i] * audioBuffer[i]).toDouble()
                }
                val rms = sqrt(sum / readSize)

                // ゼロ交差数 (Zero Crossing Rate: 周波数推定)
                var zeroCrossings = 0
                for (i in 1 until readSize) {
                    if ((audioBuffer[i] >= 0 && audioBuffer[i - 1] < 0) || (audioBuffer[i] < 0 && audioBuffer[i - 1] >= 0)) {
                        zeroCrossings++
                    }
                }
                val estimatedFreq = (zeroCrossings * SAMPLE_RATE) / (2.0 * readSize)

                val now = System.currentTimeMillis()
                if (now - lastAlertTime > 2500) {
                    // 1. 踏切警報音 / 高音アラーム検知 (700Hz〜1000Hz前後の高音大音量)
                    if (rms > 4000 && estimatedFreq in 650.0..1100.0) {
                        lastAlertTime = now
                        vibrateCrossingAlarm()
                    }
                    // 2. クラクション / サイレン検知 (大音量 2000Hz〜3500Hz)
                    else if (rms > 6000 && estimatedFreq in 1500.0..3800.0) {
                        lastAlertTime = now
                        vibrateSirenOrHorn()
                    }
                    // 3. 近くでの大きな呼びかけ声検知 (低〜中周波の大音量)
                    else if (rms > 7500 && estimatedFreq in 200.0..600.0) {
                        lastAlertTime = now
                        vibrateCallingVoice()
                    }
                }
            }

            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun vibrateCrossingAlarm() {
        // 踏切警報：カン、カン、カンの2連パルス振動
        val timings = longArrayOf(0, 150, 100, 150)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, -1)
        }
    }

    private fun vibrateSirenOrHorn() {
        // クラクション/サイレン：ブーッという強い長振動
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(400)
        }
    }

    private fun vibrateCallingVoice() {
        // 呼びかけ：トントンという2回ノック振動
        val timings = longArrayOf(0, 80, 80, 80)
        val amplitudes = intArrayOf(0, 200, 0, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, -1)
        }
    }
}
