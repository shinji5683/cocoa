package com.shinji.serena

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * BatteryStateHelper
 * 充電器の接続（コンセント/AC、USB、ワイヤレス、ドック）、充電切断、満充電を
 * リアルタイムに検知して即座にアナウンス・効果音を発生させる世界最高峰のバッテリーモニター
 */
class BatteryStateHelper(
    private val service: SerenaScreenReaderService
) {

    companion object {
        private const val TAG = "BatteryStateHelper"
    }

    private var isRegistered = false
    private var lastChargingState = false
    private var lastPluggedType = -1
    private var hasAnnouncedFull = false
    private var hasAnnounced80 = false
    private var hasAnnounced50 = false
    private var lastLowAnnouncedPct = -1
    private var hasAnnouncedOverheat = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action

            val bm = service.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).takeIf { it != -1 }
                ?: if (bm?.isCharging == true) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1).takeIf { it != -1 }
                ?: if (bm?.isCharging == true) BatteryManager.BATTERY_PLUGGED_AC else 0

            val pct = getBatteryPercentage(intent)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL || pct == 100

            val prefs = service.getSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    service.soundHelper?.playActionDone()
                    val chargingDetails = getChargingDetailsDesc(intent, plugged)
                    service.speak(service.getString(R.string.battery_charging_started, chargingDetails, pct), TextToSpeech.QUEUE_FLUSH)
                    lastChargingState = true
                    lastPluggedType = plugged
                    lastLowAnnouncedPct = -1

                    // 満充電までの予測残り時間案内
                    val isEstimateEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_SMART_BATTERY_ESTIMATE, true)
                    if (isEstimateEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bm != null) {
                        try {
                            val remainingMs = bm.computeChargeTimeRemaining()
                            if (remainingMs > 60_000L) {
                                val totalMin = (remainingMs / 1000 / 60).toInt()
                                val estimateText = if (totalMin >= 60) {
                                    val h = totalMin / 60
                                    val m = totalMin % 60
                                    service.getString(R.string.battery_estimate_time_hours_fmt, h, m)
                                } else {
                                    service.getString(R.string.battery_estimate_time_fmt, totalMin)
                                }
                                service.speak(estimateText, TextToSpeech.QUEUE_ADD)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "computeChargeTimeRemaining error: ${e.message}")
                        }
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    service.soundHelper?.playFocusMove()
                    service.speak(service.getString(R.string.battery_discharging_started, pct), TextToSpeech.QUEUE_FLUSH)
                    lastChargingState = false
                    lastPluggedType = -1
                    hasAnnouncedFull = false
                    hasAnnounced80 = false
                    hasAnnounced50 = false
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // 充電タイプの途中変更（例: USBからAC急速充電に切り替わった場合など）
                    if (isCharging && !lastChargingState) {
                        lastChargingState = true
                        lastPluggedType = plugged
                        lastLowAnnouncedPct = -1
                        val chargingDetails = getChargingDetailsDesc(intent, plugged)
                        service.soundHelper?.playActionDone()
                        service.speak(service.getString(R.string.battery_charging_started, chargingDetails, pct), TextToSpeech.QUEUE_ADD)
                    } else if (!isCharging && lastChargingState) {
                        lastChargingState = false
                        lastPluggedType = -1
                        hasAnnouncedFull = false
                        hasAnnounced80 = false
                        hasAnnounced50 = false
                    }

                    if (isCharging) {
                        lastLowAnnouncedPct = -1

                        // 50% 充電進行ステップ通知
                        val isStepsEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_SMART_BATTERY_STEPS, true)
                        if (isStepsEnabled && pct in 50..79 && !hasAnnounced50) {
                            hasAnnounced50 = true
                            service.soundHelper?.playActionDone()
                            service.speak(service.getString(R.string.battery_step_reached_fmt, 50), TextToSpeech.QUEUE_ADD)
                        }

                        // 80% バッテリー保護通知（またはステップ通知）
                        val is80Enabled = prefs.getBoolean(SerenaScreenReaderService.KEY_SMART_BATTERY_80, true)
                        if (pct in 80..99 && !hasAnnounced80) {
                            hasAnnounced80 = true
                            if (is80Enabled) {
                                service.soundHelper?.playFullChargeJingle()
                                service.speak(service.getString(R.string.battery_protect_80_reached), TextToSpeech.QUEUE_ADD)
                            } else if (isStepsEnabled) {
                                service.soundHelper?.playActionDone()
                                service.speak(service.getString(R.string.battery_step_reached_fmt, 80), TextToSpeech.QUEUE_ADD)
                            }
                        }

                        // 100% 満充電の初検知（プレミアム完了ジングル♪）
                        if (isFull && !hasAnnouncedFull) {
                            hasAnnouncedFull = true
                            service.soundHelper?.playFullChargeJingle()
                            service.speak(service.getString(R.string.battery_fully_charged), TextToSpeech.QUEUE_ADD)
                        }
                    } else {
                        // 放電中: 低残量（20%以下）充電リマインダー
                        val isLowEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_SMART_BATTERY_LOW, true)
                        if (isLowEnabled && pct in 1..20) {
                            val shouldAlert = (lastLowAnnouncedPct == -1 || pct <= lastLowAnnouncedPct - 5)
                            if (shouldAlert) {
                                lastLowAnnouncedPct = pct
                                service.soundHelper?.playWarningSound()
                                service.speak(service.getString(R.string.battery_low_warning_fmt, pct), TextToSpeech.QUEUE_ADD)
                            }
                        }
                    }

                    // バッテリー発熱警告（45℃以上）
                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                    if (tempTenths > 0) {
                        val tempCelsius = tempTenths / 10
                        if (tempCelsius >= 45 && !hasAnnouncedOverheat) {
                            hasAnnouncedOverheat = true
                            service.soundHelper?.playWarningSound()
                            service.speak(service.getString(R.string.battery_overheat_warning, tempCelsius), TextToSpeech.QUEUE_ADD)
                        } else if (tempCelsius < 40) {
                            hasAnnouncedOverheat = false
                        }
                    }
                }
            }
        }
    }

    /**
     * スマート充電通知のテスト再生
     */
    fun testSmartBatteryAnnouncement() {
        service.soundHelper?.playFullChargeJingle()
        val sample80 = service.getString(R.string.battery_protect_80_reached)
        val sampleEst = service.getString(R.string.battery_estimate_time_fmt, 45)
        service.speak("$sample80 $sampleEst", TextToSpeech.QUEUE_FLUSH)
    }

    fun start() {
        if (isRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                service.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                service.registerReceiver(batteryReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "BatteryStateHelper monitoring started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BatteryStateHelper: ${e.message}")
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            service.unregisterReceiver(batteryReceiver)
            isRegistered = false
            Log.i(TAG, "BatteryStateHelper monitoring stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BatteryStateHelper: ${e.message}")
        }
    }

    private fun getChargingDetailsDesc(intent: Intent, plugged: Int): String {
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> service.getString(R.string.battery_source_ac)
            BatteryManager.BATTERY_PLUGGED_USB -> service.getString(R.string.battery_source_usb)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> service.getString(R.string.battery_source_wireless)
            4 -> service.getString(R.string.battery_source_dock)
            else -> service.getString(R.string.battery_source_generic)
        }

        // 充電速度判定 (急速 / 普通 / 低速)
        var speed = service.getString(R.string.battery_speed_standard)
        try {
            val bm = service.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            var currentMicroAmps = intent.getIntExtra("max_charging_current", -1)
            var voltageMicroVolts = intent.getIntExtra("max_charging_voltage", -1)

            if (currentMicroAmps <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && bm != null) {
                currentMicroAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }

            if (currentMicroAmps > 0 && voltageMicroVolts > 0) {
                val watts = (currentMicroAmps / 1_000_000.0) * (voltageMicroVolts / 1_000_000.0)
                speed = when {
                    watts >= 15.0 -> service.getString(R.string.battery_speed_fast)
                    watts >= 7.0 -> service.getString(R.string.battery_speed_standard)
                    else -> service.getString(R.string.battery_speed_slow)
                }
            } else if (currentMicroAmps > 0) {
                val milliamps = kotlin.math.abs(currentMicroAmps) / 1000
                speed = when {
                    milliamps >= 2000 -> service.getString(R.string.battery_speed_fast)
                    milliamps >= 1000 -> service.getString(R.string.battery_speed_standard)
                    else -> service.getString(R.string.battery_speed_slow)
                }
            } else {
                // 電流取得不可時のフォールバック
                speed = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> service.getString(R.string.battery_speed_fast)
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> service.getString(R.string.battery_speed_standard)
                    BatteryManager.BATTERY_PLUGGED_USB -> service.getString(R.string.battery_speed_slow)
                    else -> service.getString(R.string.battery_speed_generic)
                }
            }
        } catch (_: Exception) {
            speed = if (plugged == BatteryManager.BATTERY_PLUGGED_AC) service.getString(R.string.battery_speed_fast) else service.getString(R.string.battery_speed_generic)
        }

        return service.getString(R.string.battery_details_format, source, speed)
    }

    private fun getBatteryPercentage(intent: Intent): Int {
        // 1. BatteryManager から直接リアルタイム残量プロパティを取得（最優先）
        try {
            val bm = service.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (cap in 0..100) return cap
            }
        } catch (_: Exception) {}

        // 2. Intent から level と scale を取得
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            val pct = (level * 100 / scale.toFloat()).toInt()
            if (pct in 0..100) return pct
        }

        // 3. Sticky Intent の ACTION_BATTERY_CHANGED から再取得
        try {
            val sticky = service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                val sLevel = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sScale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (sLevel >= 0 && sScale > 0) {
                    val pct = (sLevel * 100 / sScale.toFloat()).toInt()
                    if (pct in 0..100) return pct
                }
            }
        } catch (_: Exception) {}

        return 50 // フォールバック
    }
}
