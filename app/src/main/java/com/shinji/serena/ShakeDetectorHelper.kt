package com.shinji.serena

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetectorHelper(
    context: Context,
    private val onShakeListener: () -> Unit
) : SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val SHAKE_SLOP_TIME_MS = 500
    }

    private val prefs = context.getSafeSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)
    private var shakeThreshold = 2.3f

    init {
        shakeThreshold = prefs.getFloat(SerenaScreenReaderService.KEY_SHAKE_THRESHOLD, 2.3f)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var shakeTimestamp: Long = 0

    fun start() {
        shakeThreshold = prefs.getFloat(SerenaScreenReaderService.KEY_SHAKE_THRESHOLD, 2.3f)
        prefs.registerOnSharedPreferenceChangeListener(this)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        sensorManager?.unregisterListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == SerenaScreenReaderService.KEY_SHAKE_THRESHOLD) {
            shakeThreshold = prefs.getFloat(SerenaScreenReaderService.KEY_SHAKE_THRESHOLD, 2.3f)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > shakeThreshold) {
            val now = System.currentTimeMillis()
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            shakeTimestamp = now
            onShakeListener()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}


