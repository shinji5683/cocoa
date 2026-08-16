package com.shinji.serena

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL || pct == 100

            val chargingType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "コンセントから急速充電中"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB充電中"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "ワイヤレス充電中"
                4 -> "スマートドック充電中" // BATTERY_PLUGGED_DOCK
                else -> if (isCharging) "充電中" else "バッテリー駆動"
            }

            if (pct >= 0) {
                if (isFull && isCharging) {
                    "バッテリー100% 満充電（${chargingType}）"
                } else if (isCharging) {
                    "バッテリー残り${pct}%（${chargingType}）"
                } else {
                    "バッテリー残り${pct}%（バッテリー駆動）"
                }
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "Battery info error: ${e.message}")
            ""
        }
    }

    private fun getWifiText(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            var isWifiConnected = false
            var wifiCapabilities: NetworkCapabilities? = null

            // 1. activeNetwork の判定
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isWifiConnected = true
                    wifiCapabilities = caps
                }
            }

            // 2. allNetworks を走査して Wi-Fi ネットワークを探索
            if (!isWifiConnected && cm != null) {
                for (net in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(net)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        isWifiConnected = true
                        wifiCapabilities = caps
                        break
                    }
                }
            }

            // 3. WifiManager の connectionInfo をチェック (フォールバック)
            val info = wifiManager?.connectionInfo
            if (!isWifiConnected && info != null) {
                if (info.networkId != -1 && info.supplicantState == android.net.wifi.SupplicantState.COMPLETED) {
                    isWifiConnected = true
                }
            }

            if (isWifiConnected) {
                val ssid = info?.ssid?.replace("\"", "")?.trim()

                // 信号レベル (0〜4本)
                val rssi = info?.rssi ?: -100
                val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager != null) {
                    wifiManager.calculateSignalLevel(rssi)
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.calculateSignalLevel(rssi, 5)
                }

                val levelDesc = when (level) {
                    4 -> "電波最強、アンテナ4本"
                    3 -> "電波良好、アンテナ3本"
                    2 -> "電波普通、アンテナ2本"
                    1 -> "電波やや弱い、アンテナ1本"
                    else -> "電波微弱"
                }

                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>" && ssid != "0x") {
                    "Wi-Fi接続中、ネットワーク名: ${ssid}、${levelDesc}"
                } else {
                    "Wi-Fi接続中、${levelDesc}"
                }
            } else {
                "Wi-Fi未接続"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wifi info error: ${e.message}")
            "Wi-Fi未接続"
        }
    }

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
    private fun getCarrierText(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrierName = tm?.networkOperatorName?.trim()
            val carrierLabel = if (!carrierName.isNullOrEmpty()) carrierName else "携帯電波"

            // 4G / 5G / LTE 判定
            val netTypeStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    when (tm?.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                        TelephonyManager.NETWORK_TYPE_HSPAP,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                        else -> "モバイル回線"
                    }
                } catch (e: SecurityException) {
                    "モバイル回線"
                }
            } else {
                "モバイル回線"
            }

            // 信号レベル (0〜4本)
            val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tm?.signalStrength?.level ?: 3
            } else {
                3
            }

            val antennaStr = when (signalLevel) {
                4 -> "アンテナ4本（電波最強）"
                3 -> "アンテナ3本（電波良好）"
                2 -> "アンテナ2本（電波普通）"
                1 -> "アンテナ1本（電波弱い）"
                else -> "圏外または微弱"
            }

            "携帯通信: ${carrierLabel} ${netTypeStr}、${antennaStr}"
        } catch (e: Exception) {
            Log.e(TAG, "Carrier info error: ${e.message}")
            ""
        }
    }
}


