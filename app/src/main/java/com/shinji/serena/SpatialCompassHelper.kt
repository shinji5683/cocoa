package com.shinji.serena

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * 空間電子コンパス・方角＆クロックポジション案内ヘルパー (Spatial Compass & Orientation Helper)
 * 端末の電子磁気センサーと加速度センサーから、向いている方角と角度、
 * および目的地へのクロックポジション（1時〜12時）と詳細方向を音声＆ハプティクス＆3Dステレオ音響で案内。
 */
class SpatialCompassHelper(private val context: Context) : SensorEventListener {

    data class ClockGuidance(
        val hour: Int,
        val directionText: String,
        val detailedDescription: String,
        val isStraightAhead: Boolean
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private val rMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    var currentAzimuth = 0f
        private set

    var isSpatialAudioEnabled = false
        private set

    private var isListening = false
    private var lastNorthHapticTime = 0L
    private var lastTargetHapticTime = 0L
    private var lastAudioPingTime = 0L

    private var targetBearing: Float? = null // null の場合は「真北 (0度)」が目標

    fun setTargetBearing(bearing: Float?) {
        targetBearing = bearing
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        if (rotationSensor != null) {
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rMatrix, event.values)
            SensorManager.getOrientation(rMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            currentAzimuth = azimuth
            checkNorthHaptic(azimuth)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet && rotationSensor == null) {
            val success = SensorManager.getRotationMatrix(rMatrix, null, lastAccelerometer, lastMagnetometer)
            if (success) {
                SensorManager.getOrientation(rMatrix, orientation)
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                currentAzimuth = azimuth
                checkNorthHaptic(azimuth)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun toggleSpatialAudio(): Boolean {
        isSpatialAudioEnabled = !isSpatialAudioEnabled
        if (isSpatialAudioEnabled) {
            startListening()
        } else if (!isListening) {
            stopListening()
        }
        return isSpatialAudioEnabled
    }

    private fun checkSpatialAudioPulse(azimuth: Float) {
        if (!isSpatialAudioEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastAudioPingTime < 600) return
        lastAudioPingTime = now

        val target = targetBearing ?: 0f // デフォルトは北(0度)
        var diff = (target - azimuth + 360f) % 360f
        if (diff > 180f) diff -= 360f // -180 (左) 〜 +180 (右)

        val isAligned = kotlin.math.abs(diff) <= 12f
        val freq = if (isAligned) 880.0 else (440.0 - kotlin.math.abs(diff) * 0.8).coerceAtLeast(280.0)

        // ステレオパンニング音量 (左/右)
        val leftVol: Float
        val rightVol: Float

        if (isAligned) {
            leftVol = 0.9f
            rightVol = 0.9f
        } else if (diff > 0) {
            // 目標が右側にある -> 右耳を強く
            val norm = (diff / 180f).coerceIn(0f, 1f)
            leftVol = (1f - norm).coerceAtLeast(0.15f) * 0.7f
            rightVol = 0.85f
        } else {
            // 目標が左側にある -> 左耳を強く
            val norm = (-diff / 180f).coerceIn(0f, 1f)
            leftVol = 0.85f
            rightVol = (1f - norm).coerceAtLeast(0.15f) * 0.7f
        }

        playStereoTone(freq, 70, leftVol, rightVol)
    }

    private fun playStereoTone(frequencyHz: Double, durationMs: Int, leftVolume: Float, rightVolume: Float) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples * 2) // ステレオ (L, R, L, R...)

                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / frequencyHz)
                    val rawSample = (sin(angle) * Short.MAX_VALUE).toInt().toShort()

                    // フェードアウトエンベロープ
                    val envelope = 1.0 - (i.toDouble() / numSamples.toDouble())
                    val sampleLeft = (rawSample * leftVolume * envelope).toInt().toShort()
                    val sampleRight = (rawSample * rightVolume * envelope).toInt().toShort()

                    buffer[i * 2] = sampleLeft
                    buffer[i * 2 + 1] = sampleRight
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                Thread.sleep(durationMs.toLong() + 120)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }.start()
    }

    private fun checkNorthHaptic(azimuth: Float) {
        // 北（0度/360度）の前後5度以内の時にカチッと触覚フィードバック
        if (azimuth <= 5f || azimuth >= 355f) {
            val now = System.currentTimeMillis()
            if (now - lastNorthHapticTime > 1500) {
                lastNorthHapticTime = now
                triggerHaptic()
            }
        }
        checkSpatialAudioPulse(azimuth)
    }

    fun checkTargetAlignmentHaptic(targetBearing: Float): Boolean {
        val guidance = getClockPositionGuidance(targetBearing)
        if (guidance.isStraightAhead) {
            val now = System.currentTimeMillis()
            if (now - lastTargetHapticTime > 1200) {
                lastTargetHapticTime = now
                triggerHaptic()
            }
            return true
        }
        return false
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(30)
                }
            }
        } catch (_: Exception) {}
    }

    fun getDirectionNameResId(azimuth: Float = currentAzimuth): Int {
        return when {
            azimuth >= 348.75f || azimuth < 11.25f -> R.string.cardinal_n
            azimuth in 11.25f..33.75f -> R.string.cardinal_nne
            azimuth in 33.75f..56.25f -> R.string.cardinal_ne
            azimuth in 56.25f..78.75f -> R.string.cardinal_ene
            azimuth in 78.75f..101.25f -> R.string.cardinal_e
            azimuth in 101.25f..123.75f -> R.string.cardinal_ese
            azimuth in 123.75f..146.25f -> R.string.cardinal_se
            azimuth in 146.25f..168.75f -> R.string.cardinal_sse
            azimuth in 168.75f..191.25f -> R.string.cardinal_s
            azimuth in 191.25f..213.75f -> R.string.cardinal_ssw
            azimuth in 213.75f..236.25f -> R.string.cardinal_sw
            azimuth in 236.25f..258.75f -> R.string.cardinal_wsw
            azimuth in 258.75f..281.25f -> R.string.cardinal_w
            azimuth in 281.25f..303.75f -> R.string.cardinal_wnw
            azimuth in 303.75f..326.25f -> R.string.cardinal_nw
            azimuth in 326.25f..348.75f -> R.string.cardinal_nnw
            else -> R.string.cardinal_n
        }
    }

    /**
     * 16方位の方角名を算出
     */
    fun getDirectionName(azimuth: Float = currentAzimuth): String {
        return context.getString(getDirectionNameResId(azimuth))
    }

    /**
     * 目的地方位角 (targetBearing) と端末方位 (userHeading) から直感的な相対方向（正面・右斜め前・右・後ろ等）を算出
     */
    fun getClockPositionGuidance(targetBearing: Float, userHeading: Float = currentAzimuth): ClockGuidance {
        var diff = (targetBearing - userHeading + 360f) % 360f
        if (diff < 0) diff += 360f

        return when {
            diff >= 345f || diff < 15f -> ClockGuidance(12, context.getString(R.string.dir_front), context.getString(R.string.compass_guidance_front), true)
            diff in 15f..<45f -> ClockGuidance(1, context.getString(R.string.dir_slight_right), context.getString(R.string.compass_guidance_slight_right), false)
            diff in 45f..<75f -> ClockGuidance(2, context.getString(R.string.dir_front_right), context.getString(R.string.compass_guidance_front_right), false)
            diff in 75f..<105f -> ClockGuidance(3, context.getString(R.string.dir_right_side), context.getString(R.string.compass_guidance_right), false)
            diff in 105f..<135f -> ClockGuidance(4, context.getString(R.string.dir_back_right), context.getString(R.string.compass_guidance_back_right), false)
            diff in 135f..<165f -> ClockGuidance(5, context.getString(R.string.dir_back_right), context.getString(R.string.compass_guidance_behind_right), false)
            diff in 165f..<195f -> ClockGuidance(6, context.getString(R.string.dir_directly_behind), context.getString(R.string.compass_guidance_directly_behind), false)
            diff in 195f..<225f -> ClockGuidance(7, context.getString(R.string.dir_back_left), context.getString(R.string.compass_guidance_behind_left), false)
            diff in 225f..<255f -> ClockGuidance(8, context.getString(R.string.dir_back_left), context.getString(R.string.compass_guidance_back_left), false)
            diff in 255f..<285f -> ClockGuidance(9, context.getString(R.string.dir_left_side), context.getString(R.string.compass_guidance_left), false)
            diff in 285f..<315f -> ClockGuidance(10, context.getString(R.string.dir_front_left), context.getString(R.string.compass_guidance_front_left), false)
            diff in 315f..<345f -> ClockGuidance(11, context.getString(R.string.dir_slight_left), context.getString(R.string.compass_guidance_slight_left), false)
            else -> ClockGuidance(12, context.getString(R.string.dir_front), context.getString(R.string.compass_guidance_front), true)
        }
    }

    /**
     * 音声読み上げ用の方角アナウンス文
     */
    fun getDirectionAnnouncement(): String {
        val dir = getDirectionName()
        val deg = currentAzimuth.toInt()
        return context.getString(R.string.compass_direction_announcement_fmt, dir, deg)
    }
}
