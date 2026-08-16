package com.shinji.serena

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

/**
 * 空間電子コンパス・方角案内ヘルパー (Spatial Compass & Orientation Helper)
 * 端末の電子磁気センサーと加速度センサーから、向いている方角と角度を音声＆ハプティクス案内。
 */
class SpatialCompassHelper(private val context: Context) : SensorEventListener {

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

    private var currentAzimuth = 0f
    private var isListening = false
    private var lastNorthHapticTime = 0L

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
     * 音声読み上げ用の方角アナウンス文
     */
    fun getDirectionAnnouncement(): String {
        val dir = getDirectionName()
        val deg = currentAzimuth.toInt()
        return "現在向いている方角は「${dir}」、角度は${deg}度です。"
    }
}
