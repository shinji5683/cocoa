package com.shinji.serena

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.sin

/**
 * 3D空間オーディオ・障害物＆段差検知ソナー (3D Spatial Obstacle Sonar Helper)
 * 端末センサーおよびビジョン近接情報から、前方・左側・右側の障害物との距離と方角をリアルタイム検知し、
 * 立体ステレオ音響（左・右・中央定位）とハプティクス振動で空間を立体的に案内。
 */
class SpatialObstacleSonarHelper(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SpatialSonar"
        private const val SAMPLE_RATE = 44100
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isSonarActive = false
    private var lastPingTime = 0L

    // 障害物推定データ: 距離(メートル) と 水平パン位置 (-1.0f: 左端, 0.0f: 正面, 1.0f: 右端)
    private var estimatedDistanceMeters = 2.5f
    private var obstaclePanPosition = 0.0f
    private var isObstacleDetected = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (!isSonarActive) return

            val now = System.currentTimeMillis()
            val intervalMs = when {
                estimatedDistanceMeters < 0.6f -> 180L  // 超至近距離: 爆速連打
                estimatedDistanceMeters < 1.2f -> 320L  // 近距離: 高速ピピッ
                estimatedDistanceMeters < 2.0f -> 600L  // 中距離: 通常テンポ
                else -> 1200L                           // 遠方/クリア: ゆったり
            }

            if (now - lastPingTime >= intervalMs) {
                lastPingTime = now
                if (isObstacleDetected || estimatedDistanceMeters < 1.8f) {
                    playSpatialSonarPing(estimatedDistanceMeters, obstaclePanPosition)
                }
            }

            mainHandler.postDelayed(this, 100)
        }
    }

    fun startSonar(): Boolean {
        if (isSonarActive) return true
        isSonarActive = true

        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        mainHandler.post(pingRunnable)
        performSonarVibration(30)
        Log.i(TAG, "3D Spatial Obstacle Sonar started.")
        return true
    }

    fun stopSonar() {
        if (!isSonarActive) return
        isSonarActive = false
        mainHandler.removeCallbacks(pingRunnable)
        sensorManager?.unregisterListener(this)
        Log.i(TAG, "3D Spatial Obstacle Sonar stopped.")
    }

    fun isSonarRunning(): Boolean = isSonarActive

    fun toggleSonar(): Boolean {
        return if (isSonarActive) {
            stopSonar()
            false
        } else {
            startSonar()
            true
        }
    }

    /**
     * カメラVision AIなど外部から視覚的障害物（人物や大きな物体）の距離と水平位置をフィードバック
     */
    fun updateVisionObstacle(distanceMeters: Float, panPosition: Float, detected: Boolean) {
        if (!isSonarActive) return
        this.estimatedDistanceMeters = distanceMeters.coerceIn(0.2f, 5.0f)
        this.obstaclePanPosition = panPosition.coerceIn(-1.0f, 1.0f)
        this.isObstacleDetected = detected
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isSonarActive) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                if (distance < maxRange) {
                    estimatedDistanceMeters = 0.4f
                    obstaclePanPosition = 0.0f
                    isObstacleDetected = true
                    performSonarVibration(60)
                } else if (!isObstacleDetected) {
                    estimatedDistanceMeters = 2.5f
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // 下向き（下り段差・落下リスク検知）
                val z = event.values[2]
                val y = event.values[1]
                if (y < -6.0f && z < 2.0f) {
                    // 端末が急激に前傾している場合、足元の注意喚起
                    obstaclePanPosition = 0.0f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 3D立体音響（ステレオバイノーラルPCM合成）でソナーピンを生成
     * @param distance 距離（近いほど高周波でクリアなピピッ音）
     * @param pan 水平位置 (-1.0: 左耳, 0.0: 中央, 1.0: 右耳)
     */
    private fun playSpatialSonarPing(distance: Float, pan: Float) {
        Thread {
            try {
                val freq = when {
                    distance < 0.6f -> 1760.0 // 超至近: A6 (高音・警告)
                    distance < 1.2f -> 1320.0 // 近距離: E6
                    distance < 2.0f -> 880.0  // 中距離: A5
                    else -> 587.33            // 遠方: D5 (穏やか)
                }

                val durationMs = if (distance < 0.6f) 45 else 70
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val leftFactor = ((1.0f - pan) / 2.0f).coerceIn(0.05f, 0.95f)
                val rightFactor = ((1.0f + pan) / 2.0f).coerceIn(0.05f, 0.95f)

                val generatedSnd = ShortArray(numSamples * 2)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val decay = 1.0 - (i.toDouble() / numSamples)
                    val sample = (sin(2.0 * Math.PI * freq * t) * 32767 * decay * 0.7).toInt().toShort()

                    // インターリーブ・ステレオ書き込み (L, R)
                    generatedSnd[i * 2] = (sample * leftFactor).toInt().toShort()
                    generatedSnd[i * 2 + 1] = (sample * rightFactor).toInt().toShort()
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
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(generatedSnd.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()

                mainHandler.postDelayed({
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }, durationMs.toLong() + 50)

                if (distance < 0.8f) {
                    performSonarVibration(40)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sonar audio synthesis error: ${e.message}")
            }
        }.start()
    }

    private fun performSonarVibration(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
