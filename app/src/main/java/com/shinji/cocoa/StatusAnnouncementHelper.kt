package com.shinji.cocoa

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Calendar

class StatusAnnouncementHelper(private val context: Context) {

    companion object {
        private const val TAG = "StatusAnnouncementHelper"
    }

    fun buildFullStatusAnnouncement(): String {
        val parts = mutableListOf<String>()

        // 1. 現在時刻
        val timeStr = getCurrentTimeText()
        if (timeStr.isNotEmpty()) parts.add(timeStr)

        // 2. バッテリー情報
        val batteryStr = getBatteryText()
        if (batteryStr.isNotEmpty()) parts.add(batteryStr)

        // 3. Wi-Fi 接続状態
        val wifiStr = getWifiText()
        if (wifiStr.isNotEmpty()) parts.add(wifiStr)

        // 4. Bluetooth 状態
        val btStr = getBluetoothText()
        if (btStr.isNotEmpty()) parts.add(btStr)

        // 5. モバイル通信・キャリア名
        val carrierStr = getCarrierText()
        if (carrierStr.isNotEmpty()) parts.add(carrierStr)

        return parts.joinToString("、")
    }

    private fun getCurrentTimeText(): String {
        val calendar = Calendar.getInstance()
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val periodStr = if (hour24 < 12) "午前" else "午後"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "ただいま ${periodStr}${hour12}時${minute}分"
    }

    private fun getBatteryText(): String {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            if (pct >= 0) {
                if (isCharging) "バッテリー残り${pct}% 充電中" else "バッテリー残り${pct}%"
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "Battery info error: ${e.message}")
            ""
        }
    }

    private fun getWifiText(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)

            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wifiManager?.connectionInfo
                val ssid = info?.ssid?.replace("\"", "")?.trim()
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    "Wi-Fi接続中 SS ID ${ssid}"
                } else {
                    "Wi-Fi接続中"
                }
            } else {
                "Wi-Fi未接続"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wifi info error: ${e.message}")
            "Wi-Fi未接続"
        }
    }

    private fun getBluetoothText(): String {
        return try {
            val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                bm?.adapter?.isEnabled == true
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
            }

            if (isEnabled) "Bluetoothオン" else "Bluetoothオフ"
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth info error: ${e.message}")
            ""
        }
    }

    private fun getCarrierText(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrierName = tm?.networkOperatorName?.trim()
            if (!carrierName.isNullOrEmpty()) {
                "モバイル通信 ${carrierName}"
            } else {
                "モバイル通信"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Carrier info error: ${e.message}")
            ""
        }
    }
}
