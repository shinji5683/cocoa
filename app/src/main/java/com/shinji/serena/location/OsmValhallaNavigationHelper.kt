package com.shinji.serena.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shinji.serena.SoundAndHapticHelper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * OsmValhallaNavigationHelper
 * OpenStreetMap & Valhalla 歩行者ナビゲーションエンジン
 * クロックポジションを一切使わず、「正面」「右斜め前」「右」などの直感的相対方向でターンバイターン案内を提供
 */
class OsmValhallaNavigationHelper(
    private val context: Context,
    private val soundAndHapticHelper: SoundAndHapticHelper?,
    private val speakCallback: (String) -> Unit
) : LocationListener {

    companion object {
        private const val TAG = "OsmValhallaNav"

        /**
         * 角度（-180 ~ +180度）を直感的相対方向用語に変換
         * クロックポジション（〜時の方向）は完全不使用！
         */
        fun getRelativeDirectionName(relativeBearingDegrees: Double): String {
            var norm = relativeBearingDegrees
            while (norm > 180.0) norm -= 360.0
            while (norm < -180.0) norm += 360.0

            return when {
                norm >= -22.5 && norm <= 22.5 -> "正面"
                norm > 22.5 && norm <= 67.5 -> "右斜め前"
                norm > 67.5 && norm <= 112.5 -> "右"
                norm > 112.5 && norm <= 157.5 -> "右斜め後ろ"
                norm > 157.5 || norm < -157.5 -> "真後ろ"
                norm >= -157.5 && norm < -112.5 -> "左斜め後ろ"
                norm >= -112.5 && norm < -67.5 -> "左"
                norm >= -67.5 && norm < -22.5 -> "左斜め前"
                else -> "正面"
            }
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isNavigating = false
    private var destinationName = ""
    private var destinationLat = 0.0
    private var destinationLon = 0.0

    private val maneuvers = mutableListOf<NavManeuver>()
    private var currentManeuverIndex = 0
    private var lastSpokenManeuverIndex = -1
    private var lastDistanceAnnouncementMs = 0L

    var currentHeadingDegrees: Float = 0.0f // コンパス方位 (0 = 北, 90 = 東, 180 = 南, 270 = 西)

    fun isNavigatingActive(): Boolean = isNavigating

    /**
     * 目的地名（施設名や住所）から緯度経度を検索し、徒歩ルート探索を開始
     */
    fun startNavigationToDestination(query: String) {
        val fineLocation = getBestCurrentLocation()
        if (fineLocation == null) {
            speakCallback("現在地を取得できませんでした。GPSが有効か確認してください。")
            return
        }

        speakCallback("${query} への徒歩ルートを探索中...")
        soundAndHapticHelper?.playFocusMove()

        thread {
            try {
                // 1. Nominatim / Photon で目的地ジオコーディング
                val destCoords = geocodeDestination(query, fineLocation.latitude, fineLocation.longitude)
                if (destCoords == null) {
                    mainHandler.post {
                        speakCallback("「$query」の場所が見つかりませんでした。別の名前でお試しください。")
                    }
                    return@thread
                }

                destinationName = destCoords.first
                destinationLat = destCoords.second
                destinationLon = destCoords.third

                // 2. Valhalla / OSM 徒歩ルート探索
                fetchPedestrianRoute(fineLocation.latitude, fineLocation.longitude, destinationLat, destinationLon)
            } catch (e: Exception) {
                Log.e(TAG, "startNavigation error: ${e.message}", e)
                mainHandler.post {
                    speakCallback("ルートの取得中にエラーが発生しました。")
                }
            }
        }
    }

    /**
     * 緯度経度直接指定での徒歩ルート開始
     */
    fun startNavigationToCoordinates(destName: String, lat: Double, lon: Double) {
        val fineLocation = getBestCurrentLocation()
        if (fineLocation == null) {
            speakCallback("現在地を取得できませんでした。")
            return
        }

        destinationName = destName
        destinationLat = lat
        destinationLon = lon
        speakCallback("${destName} への徒歩ルートを計算しています...")

        thread {
            fetchPedestrianRoute(fineLocation.latitude, fineLocation.longitude, destinationLat, destinationLon)
        }
    }

    fun stopNavigation() {
        if (!isNavigating) return
        isNavigating = false
        maneuvers.clear()
        currentManeuverIndex = 0
        lastSpokenManeuverIndex = -1
        stopLocationUpdates()
        soundAndHapticHelper?.playActionDone()
        speakCallback("徒歩ナビゲーションを終了しました。")
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 1.0f, this)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2.0f, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestLocationUpdates error: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(this)
    }

    @SuppressLint("MissingPermission")
    private fun getBestCurrentLocation(): Location? {
        val lm = locationManager ?: return null
        var best: Location? = null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best.accuracy) {
                    best = l
                }
            } catch (_: Exception) {}
        }
        return best
    }

    private fun geocodeDestination(query: String, currentLat: Double, currentLon: Double): Triple<String, Double, Double>? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // 日本語対応 Photon Geocoding API
        val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery&lat=$currentLat&lon=$currentLon&limit=1&lang=ja"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Android Accessibility)")

        if (conn.responseCode == 200) {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val root = JSONObject(response)
            val features = root.optJSONArray("features")
            if (features != null && features.length() > 0) {
                val feat = features.getJSONObject(0)
                val coords = feat.getJSONObject("geometry").getJSONArray("coordinates")
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                val props = feat.optJSONObject("properties")
                val name = props?.optString("name")?.ifEmpty { query } ?: query
                return Triple(name, lat, lon)
            }
        }
        return null
    }

    private fun fetchPedestrianRoute(startLat: Double, startLon: Double, destLat: Double, destLon: Double) {
        try {
            // OSRM / Valhalla 公開徒歩ルーティング API
            val urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$startLon,$startLat;$destLon,$destLat?overview=full&steps=true&geometries=geojson"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Android Accessibility)")

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                parseRouteJson(response)
            } else {
                mainHandler.post {
                    speakCallback("ルートサーバーから応答がありませんでした。")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPedestrianRoute error: ${e.message}", e)
            mainHandler.post {
                speakCallback("ルート計算に失敗しました。電波の良い場所でお試しください。")
            }
        }
    }

    private fun parseRouteJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        val routes = root.optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            mainHandler.post { speakCallback("歩行者ルートが見つかりませんでした。") }
            return
        }

        val route = routes.getJSONObject(0)
        val totalDistance = route.optDouble("distance", 0.0)
        val totalDurationSeconds = route.optDouble("duration", 0.0)
        val totalMinutes = ceil(totalDurationSeconds / 60.0).toInt()

        val legs = route.optJSONArray("legs") ?: return
        val parsedManeuvers = mutableListOf<NavManeuver>()

        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val steps = leg.optJSONArray("steps") ?: continue
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val distance = step.optDouble("distance", 0.0)
                val street = step.optString("name", "道路")
                val manObj = step.optJSONObject("maneuver") ?: continue
                val type = manObj.optString("type", "")
                val modifier = manObj.optString("modifier", "")
                val loc = manObj.optJSONArray("location")
                val stepLon = loc?.optDouble(0) ?: 0.0
                val stepLat = loc?.optDouble(1) ?: 0.0

                val mType = when (modifier) {
                    "right" -> ManeuverType.RIGHT
                    "slight right" -> ManeuverType.SLIGHT_RIGHT
                    "sharp right" -> ManeuverType.SHARP_RIGHT
                    "left" -> ManeuverType.LEFT
                    "slight left" -> ManeuverType.SLIGHT_LEFT
                    "sharp left" -> ManeuverType.SHARP_LEFT
                    "uturn" -> ManeuverType.UTURN
                    else -> if (type == "arrive") ManeuverType.DESTINATION else ManeuverType.STRAIGHT
                }

                val instruction = when (mType) {
                    ManeuverType.RIGHT -> "右折"
                    ManeuverType.SLIGHT_RIGHT -> "右斜め前方向へ"
                    ManeuverType.SHARP_RIGHT -> "大きく右へ曲がります"
                    ManeuverType.LEFT -> "左折"
                    ManeuverType.SLIGHT_LEFT -> "左斜め前方向へ"
                    ManeuverType.SHARP_LEFT -> "大きく左へ曲がります"
                    ManeuverType.UTURN -> "Uターンします"
                    ManeuverType.DESTINATION -> "目的地に到着します"
                    else -> "直進"
                }

                parsedManeuvers.add(NavManeuver(instruction, street, distance, mType, stepLat, stepLon))
            }
        }

        mainHandler.post {
            maneuvers.clear()
            maneuvers.addAll(parsedManeuvers)
            currentManeuverIndex = 0
            lastSpokenManeuverIndex = -1
            isNavigating = true
            startLocationUpdates()

            val distText = if (totalDistance >= 1000) String.format("%.1fキロメートル", totalDistance / 1000.0) else "${totalDistance.toInt()}メートル"
            soundAndHapticHelper?.playActionDone()
            val startMsg = "${destinationName} へのルートが見つかりました。総距離およそ ${distText}、徒歩 約${totalMinutes}分です。ナビゲーションを開始します。"
            speakCallback(startMsg)

            announceCurrentStep(true)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!isNavigating || maneuvers.isEmpty()) return

        val targetManeuver = maneuvers.getOrNull(currentManeuverIndex) ?: run {
            speakCallback("目的地付近に到着しました。ナビゲーションを終了します。")
            stopNavigation()
            return
        }

        // 次の曲がり角までの距離
        val distToStep = calculateDistanceMeters(location.latitude, location.longitude, targetManeuver.lat, targetManeuver.lon)
        val bearingToStep = calculateBearingDegrees(location.latitude, location.longitude, targetManeuver.lat, targetManeuver.lon)

        // スマホのコンパス向きに対する相対方向（正面、右斜め前、左など）
        val relativeAngle = bearingToStep - currentHeadingDegrees
        val relativeDir = getRelativeDirectionName(relativeAngle)

        // 次のステップに到達判定 (< 15メートル)
        if (distToStep < 15.0) {
            currentManeuverIndex++
            soundAndHapticHelper?.performKeyClickHaptic()
            if (currentManeuverIndex >= maneuvers.size) {
                soundAndHapticHelper?.playActionDone()
                speakCallback("目的地「${destinationName}」に到着しました！お疲れ様でした。")
                stopNavigation()
                return
            } else {
                announceCurrentStep(false)
            }
            return
        }

        // 30秒間隔または一定距離接近時の音声サポート
        val now = System.currentTimeMillis()
        if (now - lastDistanceAnnouncementMs > 25000L) {
            lastDistanceAnnouncementMs = now
            val distInt = distToStep.toInt()
            if (distInt in 20..150) {
                speakCallback("${relativeDir} ${distInt}メートル先、${targetManeuver.instruction}です。")
            }
        }
    }

    private fun announceCurrentStep(isStart: Boolean) {
        val m = maneuvers.getOrNull(currentManeuverIndex) ?: return
        val distInt = m.distanceMeters.toInt()
        val dirMsg = if (isStart) {
            "まずは正面へ、${m.streetName}を ${distInt}メートル進みます。"
        } else {
            "${m.instruction}です。その先 ${distInt}メートル進みます。"
        }
        speakCallback(dirMsg)
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球半径 (m)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lon2 - lon1))
        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
