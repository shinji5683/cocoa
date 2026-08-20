package com.shinji.serena.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * LocationAddressHelper
 * シェイク・ステータスアナウンス連動の現在地住所読み上げエンジン
 * プライバシー保護粒度（市区町村・町名まで / 番地まで詳細）＆ インターナショナル対応
 */
class LocationAddressHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationAddressHelper"
        const val PREF_KEY_LOCATION_PRECISION = "pref_location_precision_level"
        
        // 粒度設定
        const val PRECISION_TOWN = "town"              // 市区町村・町名まで (デフォルト: プライバシー保護)
        const val PRECISION_EXACT_BLOCK = "exact_block" // 番地・号まで詳細
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("serena_prefs", Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isLocationPermissionGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isExactBlockPrecision(): Boolean {
        return prefs.getString(PREF_KEY_LOCATION_PRECISION, PRECISION_TOWN) == PRECISION_EXACT_BLOCK
    }

    fun togglePrecision(): Boolean {
        val current = isExactBlockPrecision()
        val next = !current
        prefs.edit().putString(PREF_KEY_LOCATION_PRECISION, if (next) PRECISION_EXACT_BLOCK else PRECISION_TOWN).apply()
        return next
    }

    fun getPrecisionDisplayName(): String {
        return if (isExactBlockPrecision()) "番地まで詳細" else "市区町村・町名まで（プライバシー保護）"
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocationAddress(callback: (String) -> Unit) {
        if (!isLocationPermissionGranted()) {
            callback("位置情報: 権限未許可")
            return
        }

        val lm = locationManager
        if (lm == null) {
            callback("")
            return
        }

        // 1. キャッシュされた最新位置の探索 (GPS -> Network -> Passive)
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get last known location from $provider: ${e.message}")
            }
        }

        if (bestLocation != null && (System.currentTimeMillis() - bestLocation.time) < 1000 * 60 * 15) {
            // 15分以内の有効なキャッシュ位置を即時逆ジオコーディング
            resolveAddress(bestLocation, callback)
            return
        }

        // 2. シングルショットで現在位置をリクエスト
        try {
            var listenerReceived = false
            val singleShotListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (listenerReceived) return
                    listenerReceived = true
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    resolveAddress(location, callback)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val availableProvider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }

            if (availableProvider != null) {
                lm.requestSingleUpdate(availableProvider, singleShotListener, Looper.getMainLooper())
                // 3秒タイムアウトでフォールバック
                mainHandler.postDelayed({
                    if (!listenerReceived) {
                        listenerReceived = true
                        try { lm.removeUpdates(singleShotListener) } catch (_: Exception) {}
                        if (bestLocation != null) {
                            resolveAddress(bestLocation, callback)
                        } else {
                            callback("位置情報: 測位中")
                        }
                    }
                }, 3000)
            } else if (bestLocation != null) {
                resolveAddress(bestLocation, callback)
            } else {
                callback("位置情報: GPSオフ")
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestSingleUpdate failed: ${e.message}")
            if (bestLocation != null) {
                resolveAddress(bestLocation, callback)
            } else {
                callback("")
            }
        }
    }

    private fun resolveAddress(location: Location, callback: (String) -> Unit) {
        val lat = location.latitude
        val lng = location.longitude
        val locale = Locale.getDefault()

        Thread {
            try {
                val geocoder = Geocoder(context, locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addr = addresses.firstOrNull()
                        val text = formatAddressText(addr, locale)
                        mainHandler.post { callback(text) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()
                    val text = formatAddressText(addr, locale)
                    mainHandler.post { callback(text) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geocoder error: ${e.message}")
                mainHandler.post {
                    callback("現在地: 緯度${String.format(Locale.US, "%.3f", lat)} 経度${String.format(Locale.US, "%.3f", lng)}")
                }
            }
        }.start()
    }

    private fun formatAddressText(address: Address?, locale: Locale): String {
        if (address == null) return "現在地: 住所不明"

        val isJapanese = locale.language == Locale.JAPANESE.language || locale.country == "JP"
        val exact = isExactBlockPrecision()

        return if (isJapanese) {
            val admin = address.adminArea ?: "" // 都道府県 (岐阜県)
            val locality = address.locality ?: address.subAdminArea ?: "" // 市区町村 (大垣市)
            val subLocality = address.subLocality ?: "" // 町名・丁目 (郭町1丁目)
            val thoroughfare = address.thoroughfare ?: ""
            val subThoroughfare = address.subThoroughfare ?: "" // 番地・号
            val featureName = address.featureName ?: ""

            // 1. addressLine (最も完全な住所文字列) からの精密解析
            val fullLine = address.getAddressLine(0) ?: ""
            val cleanedLine = fullLine
                .replace(Regex("^日本[、,\\s]*"), "")
                .replace(Regex("^[〒\\d\\-\\s]+"), "")
                .trim()

            if (exact) {
                // 番地・号まで詳細
                if (cleanedLine.isNotEmpty()) {
                    "現在地: $cleanedLine"
                } else {
                    val townPart = when {
                        subLocality.isNotEmpty() -> subLocality
                        thoroughfare.isNotEmpty() -> thoroughfare
                        else -> ""
                    }
                    val blockPart = when {
                        subThoroughfare.isNotEmpty() -> "${townPart} ${subThoroughfare}"
                        featureName.isNotEmpty() && featureName != townPart && featureName != admin && featureName != locality -> "${townPart} ${featureName}"
                        else -> townPart
                    }
                    val full = "${admin}${locality}${blockPart}".trim()
                    if (full.isNotEmpty()) "現在地: $full" else "現在地: 住所取得中"
                }
            } else {
                // 市区町村・町名まで (プライバシー保護: 番地数字のみをカットして何町・何丁目で止める)
                if (cleanedLine.isNotEmpty()) {
                    var stripped = cleanedLine
                    // 1. 「〇〇丁目」の後の番地数字（1-2-3や12番地など）をカット
                    if (stripped.contains(Regex("[\\d\\uFF10-\\uFF19]+丁目"))) {
                        stripped = stripped.replace(Regex("(?<=[\\d\\uFF10-\\uFF19]+丁目)[\\s\\d\\uFF10-\\uFF19\\-ー番地号]+.*$"), "").trim()
                    } else if (stripped.contains(Regex("(?<=[^\\d\\s])(町|村|大字|字|通|条|番街)"))) {
                        // 2. 「〇〇町」などの直後の番地数字（1-2-3や123番地など）をカット
                        stripped = stripped.replace(Regex("(?<=(?:町|村|大字|字|通|条|番街))[\\s\\d\\uFF10-\\uFF19\\-ー番地号]+.*$"), "").trim()
                    } else {
                        // 3. 末尾の番地数字をカット
                        stripped = stripped.replace(Regex("[\\s\\d\\uFF10-\\uFF19\\-ー番地号]+$"), "").trim()
                    }
                    "現在地: $stripped"
                } else {
                    val townPart = when {
                        subLocality.isNotEmpty() -> subLocality
                        thoroughfare.isNotEmpty() -> thoroughfare
                        featureName.isNotEmpty() && featureName != admin && featureName != locality -> featureName
                        else -> ""
                    }
                    val full = "${admin}${locality}${townPart}".trim()
                    if (full.isNotEmpty()) "現在地: $full" else "現在地: 住所取得中"
                }
            }
        } else {
            // インターナショナル対応 (英語圏 / 海外)
            val city = address.locality ?: address.subAdminArea ?: ""
            val state = address.adminArea ?: ""
            val country = address.countryName ?: ""
            val street = address.thoroughfare ?: ""
            val streetNumber = address.subThoroughfare ?: ""

            if (exact) {
                val streetFull = listOfNotNull(streetNumber.ifEmpty { null }, street.ifEmpty { null }).joinToString(" ")
                val parts = listOfNotNull(streetFull.ifEmpty { null }, city.ifEmpty { null }, state.ifEmpty { null }, country.ifEmpty { null })
                val full = parts.joinToString(", ")
                if (full.isNotEmpty()) "Location: $full" else "Location: Unknown"
            } else {
                val parts = listOfNotNull(city.ifEmpty { null }, state.ifEmpty { null }, country.ifEmpty { null })
                val full = parts.joinToString(", ")
                if (full.isNotEmpty()) "Location: $full" else "Location: Unknown"
            }
        }
    }
}
