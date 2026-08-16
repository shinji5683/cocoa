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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL || pct == 100

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    service.soundHelper?.playActionDone()
                    val chargingSource = getPluggedSourceDesc(plugged)
                    service.speak("${chargingSource}を開始しました。バッテリー残量は${pct}パーセントです。", TextToSpeech.QUEUE_FLUSH)
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
                        val chargingSource = getPluggedSourceDesc(plugged)
                        service.soundHelper?.playActionDone()
                        service.speak("${chargingSource}を開始しました。バッテリー残量は${pct}パーセントです。", TextToSpeech.QUEUE_ADD)
                    } else if (!isCharging && lastChargingState) {
                        lastChargingState = false
                        lastPluggedType = -1
                        hasAnnouncedFull = false
                    }

                    // 100% 満充電の初検知
                    if (isFull && isCharging && !hasAnnouncedFull) {
                        hasAnnouncedFull = true
                        service.soundHelper?.playActionDone()
                        service.speak("バッテリーが100パーセント満充電になりました。充電器を取り外せます。", TextToSpeech.QUEUE_ADD)
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

    private fun getPluggedSourceDesc(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "コンセントから急速充電"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB充電"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "ワイヤレス充電"
            4 -> "スマートドック充電"
            else -> "充電"
        }
    }
}
