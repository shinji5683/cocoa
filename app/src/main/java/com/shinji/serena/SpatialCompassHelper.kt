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

/**
 * 空間電子コンパス・方角＆クロックポジション案内ヘルパー (Spatial Compass & Orientation Helper)
 * 端末の電子磁気センサーと加速度センサーから、向いている方角と角度、
 * および目的地へのクロックポジション（1時〜12時）と詳細方向（右前・左斜め後ろ等）を音声＆ハプティクス案内。
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

    private var isListening = false
    private var lastNorthHapticTime = 0L
    private var lastTargetHapticTime = 0L

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

    private fun checkNorthHaptic(azimuth: Float) {
        // 北（0度/360度）の前後5度以内の時にカチッと触覚フィードバック
        if (azimuth <= 5f || azimuth >= 355f) {
            val now = System.currentTimeMillis()
            if (now - lastNorthHapticTime > 1500) {
                lastNorthHapticTime = now
                triggerHaptic()
            }
        }
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

    /**
     * 16方位の日本語方角名を算出
     */
    fun getDirectionName(azimuth: Float = currentAzimuth): String {
        return when {
            azimuth >= 348.75f || azimuth < 11.25f -> "北"
            azimuth in 11.25f..33.75f -> "北北東"
            azimuth in 33.75f..56.25f -> "北東"
            azimuth in 56.25f..78.75f -> "東北東"
            azimuth in 78.75f..101.25f -> "東"
            azimuth in 101.25f..123.75f -> "東南東"
            azimuth in 123.75f..146.25f -> "南東"
            azimuth in 146.25f..168.75f -> "南南東"
            azimuth in 168.75f..191.25f -> "南"
            azimuth in 191.25f..213.75f -> "南南西"
            azimuth in 213.75f..236.25f -> "南西"
            azimuth in 236.25f..258.75f -> "西南西"
            azimuth in 258.75f..281.25f -> "西"
            azimuth in 281.25f..303.75f -> "西北西"
            azimuth in 303.75f..326.25f -> "北西"
            azimuth in 326.25f..348.75f -> "北北西"
            else -> "北"
        }
    }

    /**
     * 目的地方位角 (targetBearing) と端末方位 (userHeading) からクロックポジションと詳細相対方向を算出
     */
    fun getClockPositionGuidance(targetBearing: Float, userHeading: Float = currentAzimuth): ClockGuidance {
        var diff = (targetBearing - userHeading + 360f) % 360f
        if (diff < 0) diff += 360f

        return when {
            diff >= 345f || diff < 15f -> ClockGuidance(12, "正面 12時の方向", "正面 まっすぐ進んでください", true)
            diff in 15f..<45f -> ClockGuidance(1, "右斜め前 1時の方向", "少し右斜め前を向いてください", false)
            diff in 45f..<75f -> ClockGuidance(2, "右前 2時の方向", "右前を向いてください", false)
            diff in 75f..<105f -> ClockGuidance(3, "右真横 3時の方向", "右真横を向いてください", false)
            diff in 105f..<135f -> ClockGuidance(4, "右斜め後ろ 4時の方向", "右斜め後ろです", false)
            diff in 135f..<165f -> ClockGuidance(5, "右後方 5時の方向", "右後ろを向いてください", false)
            diff in 165f..<195f -> ClockGuidance(6, "真後ろ 6時の方向", "真後ろです。Uターンしてください", false)
            diff in 195f..<225f -> ClockGuidance(7, "左後方 7時の方向", "左後ろを向いてください", false)
            diff in 225f..<255f -> ClockGuidance(8, "左斜め後ろ 8時の方向", "左斜め後ろです", false)
            diff in 255f..<285f -> ClockGuidance(9, "左真横 9時の方向", "左真横を向いてください", false)
            diff in 285f..<315f -> ClockGuidance(10, "左前 10時の方向", "左前を向いてください", false)
            diff in 315f..<345f -> ClockGuidance(11, "左斜め前 11時の方向", "少し左斜め前を向いてください", false)
            else -> ClockGuidance(12, "正面 12時の方向", "正面 まっすぐ進んでください", true)
        }
    }

    /**
     * 音声読み上げ用の方角アナウンス文
     */
    fun getDirectionAnnouncement(): String {
        val dir = getDirectionName()
        val deg = currentAzimuth.toInt()
        return "現在向いている方角は「${dir}」、角度は${deg}度です。"
    }
}
