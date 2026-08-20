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

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    service.soundHelper?.playActionDone()
                    val chargingDetails = getChargingDetailsDesc(intent, plugged)
                    service.speak("${chargingDetails}を開始しました。バッテリー残量は${pct}パーセントです。", TextToSpeech.QUEUE_FLUSH)
                    lastChargingState = true
                    lastPluggedType = plugged
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    service.soundHelper?.playFocusMove()
                    service.speak("充電器が外れました。バッテリー駆動に切り替わりました。バッテリー残量は${pct}パーセントです。", TextToSpeech.QUEUE_FLUSH)
                    lastChargingState = false
                    lastPluggedType = -1
                    hasAnnouncedFull = false
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // 充電タイプの途中変更（例: USBからAC急速充電に切り替わった場合など）
                    if (isCharging && !lastChargingState) {
                        lastChargingState = true
                        lastPluggedType = plugged
                        val chargingDetails = getChargingDetailsDesc(intent, plugged)
                        service.soundHelper?.playActionDone()
                        service.speak("${chargingDetails}を開始しました。バッテリー残量は${pct}パーセントです。", TextToSpeech.QUEUE_ADD)
                    } else if (!isCharging && lastChargingState) {
                        lastChargingState = false
                        lastPluggedType = -1
                        hasAnnouncedFull = false
                    }

                    // 100% 満充電の初検知（プレミアム完了ジングル♪）
                    if (isFull && isCharging && !hasAnnouncedFull) {
                        hasAnnouncedFull = true
                        service.soundHelper?.playFullChargeJingle()
                        service.speak("バッテリーが100パーセント満充電になりました。充電器を取り外せます。", TextToSpeech.QUEUE_ADD)
                    }

                    // バッテリー発熱警告（45℃以上）
                    val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                    if (tempTenths > 0) {
                        val tempCelsius = tempTenths / 10
                        if (tempCelsius >= 45 && !hasAnnouncedOverheat) {
                            hasAnnouncedOverheat = true
                            service.soundHelper?.playWarningSound()
                            service.speak("警告。バッテリー温度が${tempCelsius}度と高温になっています。充電器を外すか、端末を休ませてください。", TextToSpeech.QUEUE_ADD)
                        } else if (tempCelsius < 40) {
                            hasAnnouncedOverheat = false
                        }
                    }
                }
            }
        }
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
            BatteryManager.BATTERY_PLUGGED_AC -> "AC電源"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "ワイヤレス"
            4 -> "専用ドック"
            else -> "充電器"
        }

        // 充電速度判定 (急速 / 普通 / 低速)
        var speed = "普通充電"
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
                    watts >= 15.0 -> "急速充電"
                    watts >= 7.0 -> "普通充電"
                    else -> "低速充電"
                }
            } else if (currentMicroAmps > 0) {
                val milliamps = kotlin.math.abs(currentMicroAmps) / 1000
                speed = when {
                    milliamps >= 2000 -> "急速充電"
                    milliamps >= 1000 -> "普通充電"
                    else -> "低速充電"
                }
            } else {
                // 電流取得不可時のフォールバック
                speed = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "急速充電"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "普通充電"
                    BatteryManager.BATTERY_PLUGGED_USB -> "低速充電"
                    else -> "充電"
                }
            }
        } catch (_: Exception) {
            speed = if (plugged == BatteryManager.BATTERY_PLUGGED_AC) "急速充電" else "充電"
        }

        return "${source}から${speed}"
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
