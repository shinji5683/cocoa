package com.shinji.serena.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.shinji.serena.SpatialCompassHelper
import java.util.Locale

/**
 * Serena 徒歩ナビゲーションエンジン (Serena Pedestrian Walking Navigator)
 * Google Maps API / キー完全不要・完全無料。
 * Android標準のGPS・Geocoderおよび電子コンパスを融合し、
 * クロックポジション（1時〜12時）と詳細方向（右前、左斜め後ろ等）による安全な音声歩行誘導を提供。
 */
class SerenaWalkingNavigator(
    private val context: Context,
    private val compassHelper: SpatialCompassHelper?
) : LocationListener {

    companion object {
        private const val TAG = "SerenaWalkingNavigator"
    }

    data class NavDestination(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val address: String = ""
    )

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    var currentLocation: Location? = null
        private set

    var activeDestination: NavDestination? = null
        private set

    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) return
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                if (locationManager?.isProviderEnabled(provider) == true) {
                    locationManager.requestLocationUpdates(
                        provider,
                        2000L, // 2秒間隔
                        1.0f,  // 1メートル移動ごと
                        this,
                        Looper.getMainLooper()
                    )
                    val lastKnown = locationManager.getLastKnownLocation(provider)
                    if (lastKnown != null && (currentLocation == null || lastKnown.time > (currentLocation?.time ?: 0))) {
                        currentLocation = lastKnown
                    }
                }
            }
            isTracking = true
            Log.i(TAG, "SerenaWalkingNavigator started tracking.")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking: ${e.message}")
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    /**
     * 現在地の町名・番地住所を日本語で取得（APIキー不要・完全無料）
     */
    fun getCurrentAddressSync(): String {
        val loc = currentLocation ?: return "現在地を取得中または測位圏外です"
        return try {
            val geocoder = Geocoder(context, Locale.JAPAN)
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(loc.latitude, loc.longitude, 3)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val admin = addr.adminArea ?: ""
                val locality = addr.locality ?: addr.subAdminArea ?: ""
                val thoroughfare = addr.thoroughfare ?: addr.subLocality ?: ""
                val feature = addr.featureName ?: ""

                val full = listOf(admin, locality, thoroughfare, feature)
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString("")

                if (full.isNotEmpty()) full else "緯度 ${String.format("%.4f", loc.latitude)}、経度 ${String.format("%.4f", loc.longitude)}"
            } else {
                "緯度 ${String.format("%.4f", loc.latitude)}、経度 ${String.format("%.4f", loc.longitude)}"
            }
        } catch (e: Exception) {
            "緯度 ${String.format("%.4f", loc.latitude)}、経度 ${String.format("%.4f", loc.longitude)}"
        }
    }

    /**
     * 施設名や住所から目的地を設定（APIキー不要）
     */
    fun setDestinationByName(name: String): Boolean {
        return try {
            val geocoder = Geocoder(context, Locale.JAPAN)
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(name, 1)
            if (!results.isNullOrEmpty()) {
                val dest = results[0]
                val fullAddress = dest.getAddressLine(0) ?: name
                activeDestination = NavDestination(name, dest.latitude, dest.longitude, fullAddress)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve destination: ${e.message}")
            false
        }
    }

    fun setDestinationCoordinates(name: String, latitude: Double, longitude: Double) {
        activeDestination = NavDestination(name, latitude, longitude)
    }

    fun clearDestination() {
        activeDestination = null
    }

    /**
     * 目的地へのリアルタイム徒歩ナビゲーション案内文を生成
     */
    fun getNavigationGuidance(): String {
        val dest = activeDestination ?: return "目的地が設定されていません。現在地は「${getCurrentAddressSync()}」です。"
        val loc = currentLocation ?: return "GPS測位中です。目的地「${dest.name}」へ案内しますので少しお待ちください。"

        // 1. 直線距離（m）と目的地方位角（bearing）を計算
        val results = FloatArray(2)
        Location.distanceBetween(loc.latitude, loc.longitude, dest.latitude, dest.longitude, results)
        val distanceMeters = results[0].toInt()
        var targetBearing = results[1]
        if (targetBearing < 0) targetBearing += 360f

        // 2. 空間コンパスから直感的な相対方向を取得
        val compass = compassHelper
        val clockGuidance = compass?.getClockPositionGuidance(targetBearing)

        // 3. 到着判定 (10m以内)
        if (distanceMeters <= 10) {
            return "目的地「${dest.name}」付近に到着しました！お疲れ様でした。"
        }

        // 4. ナビゲーション案内アナウンス
        val dirText = clockGuidance?.directionText ?: "正面"
        val advice = if (clockGuidance?.isStraightAhead == true) {
            compass.checkTargetAlignmentHaptic(targetBearing)
            "正面を向いています。そのまま直進してください。"
        } else {
            clockGuidance?.detailedDescription ?: ""
        }

        return "目的地「${dest.name}」まで約${distanceMeters}メートル、方向は${dirText}です。$advice"
    }

    /**
     * 現在地情報のサマリー読み上げ
     */
    fun getCurrentLocationSummary(): String {
        val address = getCurrentAddressSync()
        val compass = compassHelper
        val direction = compass?.getDirectionName() ?: "北"
        return "現在地: $address。向いている方角は「${direction}」です。"
    }
}
