package com.shinji.serena

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Calendar

class StatusAnnouncementHelper(private val context: Context) {

    companion object {
        private const val TAG = "StatusAnnouncementHelper"
        @Volatile
        var latestNetworkGeneration: String = "4G LTE"
            private set
    }

    val locationHelper = com.shinji.serena.location.LocationAddressHelper(context)
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    init {
        initTelephonyListener()
    }

    private fun initTelephonyListener() {
        if (telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 (API 31+) TelephonyCallback
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        latestNetworkGeneration = parseDisplayInfo(telephonyDisplayInfo)
                        Log.i(TAG, "TelephonyCallback: updated network generation to $latestNetworkGeneration")
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11 (API 30) PhoneStateListener
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        latestNetworkGeneration = parseDisplayInfo(telephonyDisplayInfo)
                        Log.i(TAG, "PhoneStateListener: updated network generation to $latestNetworkGeneration")
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering telephony listener: ${e.message}")
        }
    }

    @SuppressLint("NewApi")
    private fun parseDisplayInfo(displayInfo: TelephonyDisplayInfo): String {
        return when (displayInfo.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5Gミリ波"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4Gプラス"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4Gプラス"
            else -> {
                when (displayInfo.networkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    else -> "4G LTE"
                }
            }
        }
    }

    fun getGreetingPrefix(): String {
        val hour24 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour24) {
            in 5..10 -> "Magandang umaga po! Ingat lagi! 🌸"
            in 11..17 -> "Magandang araw po! Ingat lagi! ☀️"
            in 18..22 -> "Magandang gabi po! Ingat lagi! 🌙"
            else -> "Magandang gabi po! ✨ 遅くまでお疲れさまっ！"
        }
    }

    fun buildFullStatusAnnouncement(callback: ((String) -> Unit)? = null): String {
        val parts = mutableListOf<String>()

        // 0. Serena Warm Tagalog Greeting
        parts.add(getGreetingPrefix())

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

        // 5. モバイル通信・キャリア名 (超高精度 5G/4G リアルタイム判定)
        val carrierStr = getCarrierText()
        if (carrierStr.isNotEmpty()) parts.add(carrierStr)

        // 6. OSバージョン ＆ お菓子コードネーム
        val osStr = getOsVersionAndCodenameText()
        if (osStr.isNotEmpty()) parts.add(osStr)

        val baseStatus = parts.joinToString("、")

        if (callback != null) {
            callback(baseStatus)
            // 位置情報は非同期で取得でき次第、追加通知（ブロック完全排除）
            locationHelper.getCurrentLocationAddress { locStr ->
                if (locStr.isNotEmpty()) {
                    val svc = SerenaScreenReaderService.instance
                    val msg = svc?.getString(R.string.status_location_format, locStr) ?: "現在地: $locStr"
                    svc?.speak(msg, android.speech.tts.TextToSpeech.QUEUE_ADD)
                }
            }
        }

        return baseStatus
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
        return "${periodStr}${hour12}時${minute}分"
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
                4 -> "スマートドック充電中"
                else -> if (isCharging) "充電中" else "バッテリー駆動"
            }

            if (pct >= 0) {
                if (isFull && isCharging) {
                    "バッテリー100% 満充電（${chargingType}）"
                } else if (isCharging) {
                    "バッテリー残り${pct}%（${chargingType}）"
                } else {
                    "バッテリー残り${pct}%"
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery info error: ${e.message}")
            ""
        }
    }

    private fun getWifiText(): String {
        return WifiConnectivityHelper.getWifiStatusText(context)
    }

    private fun getBluetoothText(): String {
        return try {
            val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            val tm = telephonyManager ?: return ""
            val carrierName = tm.networkOperatorName?.trim()
            val carrierLabel = if (!carrierName.isNullOrEmpty()) carrierName else "携帯電波"

            // 1. 最新のリアルタイム 5G / 4G+ / 4G 判定
            var netTypeStr = latestNetworkGeneration

            // フォールバック: dataNetworkType 判定
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (netTypeStr.isEmpty() || netTypeStr == "モバイル回線")) {
                try {
                    netTypeStr = when (tm.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                        TelephonyManager.NETWORK_TYPE_HSPAP,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                        else -> "4G LTE"
                    }
                } catch (_: SecurityException) {}
            }

            // 2. リアルタイム信号強度 (0〜4本)
            val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    tm.signalStrength?.level ?: 3
                } catch (_: Exception) { 3 }
            } else {
                3
            }

            val antennaStr = when (signalLevel) {
                4 -> "電波4本最強"
                3 -> "電波3本良好"
                2 -> "電波2本普通"
                1 -> "電波1本やや弱い"
                else -> "圏外または微弱"
            }

            "携帯通信: ${carrierLabel} ${netTypeStr}、${antennaStr}"
        } catch (e: Exception) {
            Log.e(TAG, "Carrier info error: ${e.message}")
            ""
        }
    }

    /**
     * OSバージョン ＆ お菓子コードネーム (Cinnamon Bun / Baklava / Vanilla Ice Cream等) を取得
     */
    fun getOsVersionAndCodenameText(): String {
        val release = Build.VERSION.RELEASE ?: ""
        val sdk = Build.VERSION.SDK_INT
        val model = Build.MODEL ?: ""

        val codename = when {
            sdk >= 10000 -> "Android 17 (Cinnamon Bun シナモン・バン / Canary プレビュー API 10000)"
            release.startsWith("17") -> "Android 17 (Cinnamon Bun シナモン・バン)"
            sdk == 36 || release.startsWith("16") -> "Android 16 (Baklava バクラヴァ)"
            sdk == 35 || release.startsWith("15") -> "Android 15 (Vanilla Ice Cream バニラアイスクリーム)"
            sdk == 34 || release.startsWith("14") -> "Android 14 (Upside Down Cake アップサイドダウンケーキ)"
            sdk == 33 || release.startsWith("13") -> "Android 13 (Tiramisu ティラミス)"
            sdk in 31..32 || release.startsWith("12") -> "Android 12 (Snow Cone スノーコーン)"
            sdk == 30 || release.startsWith("11") -> "Android 11 (Red Velvet Cake レッドベルベットケーキ)"
            sdk == 29 || release.startsWith("10") -> "Android 10 (Quince Tart クインスタート)"
            sdk == 28 || release.startsWith("9") -> "Android 9 (Pie パイ)"
            sdk in 26..27 || release.startsWith("8") -> "Android 8 (Oreo オレオ)"
            else -> "Android $release"
        }

        return if (model.isNotEmpty()) {
            "OS: $codename (${model})"
        } else {
            "OS: $codename"
        }
    }
}
