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
import android.speech.tts.TextToSpeech
import android.util.Log
import com.shinji.serena.SerenaScreenReaderService
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 環境音・危険音・呼びかけ検知の5段階詳細度
 */
enum class SoundAlertDetailLevel(val displayName: String, val spokenLabel: String) {
    ULTRA_DETAILED("1. 超詳細（音響・材質・人物声解析＋バイブ）", "超詳細実況モード"),
    STANDARD("2. 標準（危険音＋呼びかけ音声＋バイブ）", "標準モード"),
    DANGER_VOICE_ONLY("3. 危険音音声＋バイブ（踏切・サイレン・クラクション）", "危険音音声モード"),
    HAPTIC_ONLY("4. バイブレーションのみ（音声なし）", "バイブのみモード"),
    DISABLED("5. 無効（オフ）", "オフ")
}

/**
 * SoundRecognitionHapticsHelper
 *
 * 周囲の環境音・危険音・呼びかけ声・衝撃音・材質をリアルタイムに音響解析し、
 * 専用のハプティクス（振動パターン）と自然な音声アナウンスを提供するヘルパー。
 */
class SoundRecognitionHapticsHelper(private val context: Context) {

    companion object {
        private const val TAG = "SoundRecognitionHaptics"
        private const val PREFS_NAME = "serena_sound_recognition_prefs"
        private const val KEY_DETAIL_LEVEL = "sound_alert_detail_level"
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

    var detailLevel: SoundAlertDetailLevel
        get() {
            val name = prefs.getString(KEY_DETAIL_LEVEL, SoundAlertDetailLevel.DISABLED.name)
            return try {
                SoundAlertDetailLevel.valueOf(name ?: SoundAlertDetailLevel.DISABLED.name)
            } catch (_: Exception) {
                SoundAlertDetailLevel.DISABLED
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DETAIL_LEVEL, value.name).apply()
            if (value != SoundAlertDetailLevel.DISABLED) start() else stop()
        }

    val isEnabled: Boolean
        get() = detailLevel != SoundAlertDetailLevel.DISABLED

    fun cycleDetailLevel(): SoundAlertDetailLevel {
        val levels = SoundAlertDetailLevel.values()
        val nextIndex = (detailLevel.ordinal + 1) % levels.size
        detailLevel = levels[nextIndex]
        return detailLevel
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

            Log.i(TAG, "SoundRecognitionHaptics started with level: ${detailLevel.name}")
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
                var peak = 0
                for (i in 0 until readSize) {
                    val absVal = abs(audioBuffer[i].toInt())
                    if (absVal > peak) peak = absVal
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
                if (now - lastAlertTime > 3000) {
                    // 1. 踏切警報音 / 高音アラーム検知 (700Hz〜1100Hz前後の高音大音量)
                    if (rms > 5000 && estimatedFreq in 680.0..1150.0) {
                        lastAlertTime = now
                        handleDetectedSound(
                            vibrate = { vibrateCrossingAlarm() },
                            spokenMsg = "踏切の警報音を検知しました。周囲にご注意ください。",
                            isDanger = true
                        )
                    }
                    // 2. クラクション / 緊急サイレン検知 (大音量 1500Hz〜3800Hz)
                    else if (rms > 7000 && estimatedFreq in 1500.0..3800.0) {
                        lastAlertTime = now
                        handleDetectedSound(
                            vibrate = { vibrateSirenOrHorn() },
                            spokenMsg = "緊急サイレンまたはクラクションを検知しました。",
                            isDanger = true
                        )
                    }
                    // 3. 衝撃音・衝突音（壁、ドア、ガラス、金属、電柱など）
                    else if (peak > 18000 && rms < 5000) {
                        lastAlertTime = now
                        val materialDesc = when {
                            estimatedFreq > 2500.0 -> "金属またはガラスのような硬い衝撃音"
                            estimatedFreq in 800.0..2500.0 -> "木製ドアや家具、壁への衝突音"
                            else -> "鈍い打撃音または床への衝撃"
                        }
                        if (detailLevel == SoundAlertDetailLevel.ULTRA_DETAILED) {
                            handleDetectedSound(
                                vibrate = { vibrateCollision() },
                                spokenMsg = "${materialDesc}を検知しました。",
                                isDanger = false
                            )
                        }
                    }
                    // 4. 人の声・呼びかけ検知 (低〜中周波の大音量)
                    else if (rms > 8500 && estimatedFreq in 150.0..700.0) {
                        lastAlertTime = now
                        val voiceGender = if (estimatedFreq < 280.0) "男性の声" else "女性または高い声"
                        val voiceMsg = if (detailLevel == SoundAlertDetailLevel.ULTRA_DETAILED) {
                            "${voiceGender}で呼びかけがありました。"
                        } else {
                            "近くで人の呼びかけ声を検知しました。"
                        }

                        if (detailLevel == SoundAlertDetailLevel.ULTRA_DETAILED || detailLevel == SoundAlertDetailLevel.STANDARD) {
                            handleDetectedSound(
                                vibrate = { vibrateCallingVoice() },
                                spokenMsg = voiceMsg,
                                isDanger = false
                            )
                        }
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

    private fun handleDetectedSound(vibrate: () -> Unit, spokenMsg: String, isDanger: Boolean) {
        when (detailLevel) {
            SoundAlertDetailLevel.DISABLED -> return
            SoundAlertDetailLevel.HAPTIC_ONLY -> {
                vibrate.invoke()
            }
            SoundAlertDetailLevel.DANGER_VOICE_ONLY -> {
                if (isDanger) {
                    vibrate.invoke()
                    speakAnnouncement(spokenMsg)
                }
            }
            SoundAlertDetailLevel.STANDARD -> {
                vibrate.invoke()
                speakAnnouncement(spokenMsg)
            }
            SoundAlertDetailLevel.ULTRA_DETAILED -> {
                vibrate.invoke()
                speakAnnouncement(spokenMsg)
            }
        }
    }

    private fun speakAnnouncement(msg: String) {
        SerenaScreenReaderService.instance?.speak(msg, TextToSpeech.QUEUE_ADD)
    }

    private fun vibrateCrossingAlarm() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(400)
        }
    }

    private fun vibrateCallingVoice() {
        val timings = longArrayOf(0, 80, 80, 80)
        val amplitudes = intArrayOf(0, 200, 0, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, -1)
        }
    }

    private fun vibrateCollision() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(180)
        }
    }
}
