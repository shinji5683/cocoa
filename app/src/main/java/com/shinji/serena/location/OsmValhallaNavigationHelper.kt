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

        fun getRelativeDirectionResId(relativeBearingDegrees: Double): Int {
            var norm = relativeBearingDegrees
            while (norm > 180.0) norm -= 360.0
            while (norm < -180.0) norm += 360.0
            return when {
                norm >= -22.5 && norm <= 22.5 -> com.shinji.serena.R.string.dir_front
                norm > 22.5 && norm <= 67.5 -> com.shinji.serena.R.string.dir_front_right
                norm > 67.5 && norm <= 112.5 -> com.shinji.serena.R.string.dir_right
                norm > 112.5 && norm <= 157.5 -> com.shinji.serena.R.string.dir_back_right
                norm > 157.5 || norm < -157.5 -> com.shinji.serena.R.string.dir_directly_behind
                norm >= -157.5 && norm < -112.5 -> com.shinji.serena.R.string.dir_back_left
                norm >= -112.5 && norm < -67.5 -> com.shinji.serena.R.string.dir_left
                norm >= -67.5 && norm < -22.5 -> com.shinji.serena.R.string.dir_front_left
                else -> com.shinji.serena.R.string.dir_front
            }
        }

        fun getRelativeDirectionName(context: Context, relativeBearingDegrees: Double): String {
            return context.getString(getRelativeDirectionResId(relativeBearingDegrees))
        }

        /**
         * 角度（-180 ~ +180度）を直感的相対方向用語に変換
         * クロックポジション（〜時の方向）は完全不使用！
         */
        fun getRelativeDirectionName(relativeBearingDegrees: Double, isJapanese: Boolean = java.util.Locale.getDefault().language.lowercase() == "ja"): String {
            var norm = relativeBearingDegrees
            while (norm > 180.0) norm -= 360.0
            while (norm < -180.0) norm += 360.0

            return if (isJapanese) {
                when {
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
            } else {
                when {
                    norm >= -22.5 && norm <= 22.5 -> "Straight ahead"
                    norm > 22.5 && norm <= 67.5 -> "Front-right"
                    norm > 67.5 && norm <= 112.5 -> "Right"
                    norm > 112.5 && norm <= 157.5 -> "Back-right"
                    norm > 157.5 || norm < -157.5 -> "Directly behind"
                    norm >= -157.5 && norm < -112.5 -> "Back-left"
                    norm >= -112.5 && norm < -67.5 -> "Left"
                    norm >= -67.5 && norm < -22.5 -> "Front-left"
                    else -> "Straight ahead"
                }
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
            speakCallback(context.getString(com.shinji.serena.R.string.radar_gps_error))
            return
        }

        speakCallback(context.getString(com.shinji.serena.R.string.valhalla_searching_route_fmt, query))
        soundAndHapticHelper?.playFocusMove()

        thread {
            try {
                // 1. Nominatim / Photon で目的地ジオコーディング
                val destCoords = geocodeDestination(query, fineLocation.latitude, fineLocation.longitude)
                if (destCoords == null) {
                    mainHandler.post {
                        speakCallback(context.getString(com.shinji.serena.R.string.valhalla_not_found_fmt, query))
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
                    speakCallback(context.getString(com.shinji.serena.R.string.valhalla_error_route))
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
            speakCallback(context.getString(com.shinji.serena.R.string.radar_gps_error))
            return
        }

        destinationName = destName
        destinationLat = lat
        destinationLon = lon
        speakCallback(context.getString(com.shinji.serena.R.string.valhalla_calculating_fmt, destName))

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
        speakCallback(context.getString(com.shinji.serena.R.string.valhalla_stopped))
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
        val photonLang = if (java.util.Locale.getDefault().language.lowercase() == "ja") "ja" else "en"
        val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery&lat=$currentLat&lon=$currentLon&limit=1&lang=$photonLang"
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
            // 1. 最新 Valhalla v3.4 Pedestrian Routing API (歩行者・段差・安全歩道最適化)
            val valhallaJson = JSONObject().apply {
                put("locations", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("lat", startLat); put("lon", startLon) })
                    put(JSONObject().apply { put("lat", destLat); put("lon", destLon) })
                })
                put("costing", "pedestrian")
                put("costing_options", JSONObject().apply {
                    put("pedestrian", JSONObject().apply {
                        put("use_hills", 0.1)
                        put("use_ferry", 0.0)
                        put("use_living_streets", 1.0)
                        put("service_penalty", 0.0)
                        put("max_hiking_difficulty", 1)
                        put("step_penalty", 0.0)
                    })
                })
                val isJa = java.util.Locale.getDefault().language.lowercase() == "ja"
                val langCode = if (isJa) "ja-JP" else "en-US"
                val country = java.util.Locale.getDefault().country.uppercase()
                val units = if (country in listOf("US", "GB", "LR", "MM")) "miles" else "kilometers"

                put("directions_options", JSONObject().apply {
                    put("language", langCode)
                    put("units", units)
                })
            }

            var routeFetched = false

            // Primary: Valhalla v3.4 API
            try {
                val valhallaUrl = URL("https://valhalla1.openstreetmap.de/route")
                val conn = valhallaUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Android Accessibility; Valhalla-v3.4)")

                val os = conn.outputStream
                os.write(valhallaJson.toString().toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                if (conn.responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                    parseValhallaRouteJson(response)
                    routeFetched = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Valhalla primary API failed, falling back to OSRM: ${e.message}")
            }

            if (routeFetched) return

            // Fallback: OSRM / OpenStreetMap Foot Routing API
            val fallbackUrl = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$startLon,$startLat;$destLon,$destLat?overview=full&steps=true&geometries=geojson"
            val connFallback = URL(fallbackUrl).openConnection() as HttpURLConnection
            connFallback.connectTimeout = 6000
            connFallback.readTimeout = 6000
            connFallback.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Android Accessibility)")

            if (connFallback.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connFallback.inputStream, Charsets.UTF_8)).readText()
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

    private fun parseValhallaRouteJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        val trip = root.optJSONObject("trip") ?: run {
            mainHandler.post { speakCallback("歩行者ルートが見つかりませんでした。") }
            return
        }

        val summary = trip.optJSONObject("summary")
        val totalLengthKm = summary?.optDouble("length", 0.0) ?: 0.0
        val totalDistanceMeters = totalLengthKm * 1000.0
        val totalTimeSeconds = summary?.optDouble("time", 0.0) ?: 0.0
        val totalMinutes = ceil(totalTimeSeconds / 60.0).toInt().coerceAtLeast(1)

        val legs = trip.optJSONArray("legs") ?: return
        val parsedManeuvers = mutableListOf<NavManeuver>()

        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val maneuversArr = leg.optJSONArray("maneuvers") ?: continue
            for (m in 0 until maneuversArr.length()) {
                val manObj = maneuversArr.getJSONObject(m)
                val instruction = manObj.optString("instruction", "").trim()
                val streetNames = manObj.optJSONArray("street_names")
                val streetName = if (streetNames != null && streetNames.length() > 0) streetNames.getString(0) else "道なり"
                val lengthKm = manObj.optDouble("length", 0.0)
                val distM = lengthKm * 1000.0
                val lat = manObj.optDouble("lat", destinationLat)
                val lon = manObj.optDouble("lon", destinationLon)

                val manTypeInt = manObj.optInt("type", 0)
                val mType = when (manTypeInt) {
                    1, 2, 3 -> ManeuverType.START
                    4, 5 -> ManeuverType.DESTINATION
                    6, 7, 8 -> ManeuverType.SLIGHT_RIGHT
                    9, 10, 11 -> ManeuverType.RIGHT
                    12, 13 -> ManeuverType.SHARP_RIGHT
                    14 -> ManeuverType.UTURN
                    15, 16 -> ManeuverType.SHARP_LEFT
                    17, 18, 19 -> ManeuverType.LEFT
                    20, 21, 22 -> ManeuverType.SLIGHT_LEFT
                    else -> ManeuverType.STRAIGHT
                }

                parsedManeuvers.add(NavManeuver(instruction, streetName, distM, mType, lat, lon))
            }
        }

        if (parsedManeuvers.isEmpty()) {
            mainHandler.post { speakCallback(context.getString(com.shinji.serena.R.string.valhalla_empty_data)) }
            return
        }

        maneuvers.clear()
        maneuvers.addAll(parsedManeuvers)
        currentManeuverIndex = 0
        lastSpokenManeuverIndex = -1
        isNavigating = true

        startLocationUpdates()

        mainHandler.post {
            val distText = if (totalDistanceMeters >= 1000) {
                String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
            } else {
                "${totalDistanceMeters.toInt()} m"
            }
            soundAndHapticHelper?.playActionDone()
            val startMsg = context.getString(com.shinji.serena.R.string.valhalla_route_found_fmt, destinationName, distText, totalMinutes)
            speakCallback(startMsg)
            announceCurrentStep(true)
        }
    }

    private fun parseRouteJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        val routes = root.optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            mainHandler.post { speakCallback(context.getString(com.shinji.serena.R.string.valhalla_no_route)) }
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
                val street = step.optString("name", "")
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
                    ManeuverType.RIGHT -> context.getString(com.shinji.serena.R.string.maneuver_right)
                    ManeuverType.SLIGHT_RIGHT -> context.getString(com.shinji.serena.R.string.maneuver_slight_right)
                    ManeuverType.SHARP_RIGHT -> context.getString(com.shinji.serena.R.string.maneuver_sharp_right)
                    ManeuverType.LEFT -> context.getString(com.shinji.serena.R.string.maneuver_left)
                    ManeuverType.SLIGHT_LEFT -> context.getString(com.shinji.serena.R.string.maneuver_slight_left)
                    ManeuverType.SHARP_LEFT -> context.getString(com.shinji.serena.R.string.maneuver_sharp_left)
                    ManeuverType.UTURN -> context.getString(com.shinji.serena.R.string.maneuver_uturn)
                    ManeuverType.DESTINATION -> context.getString(com.shinji.serena.R.string.maneuver_destination)
                    else -> context.getString(com.shinji.serena.R.string.maneuver_straight)
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

            val distText = if (totalDistance >= 1000) String.format(java.util.Locale.US, "%.1f km", totalDistance / 1000.0) else "${totalDistance.toInt()} m"
            soundAndHapticHelper?.playActionDone()
            val startMsg = context.getString(com.shinji.serena.R.string.valhalla_route_found_fmt, destinationName, distText, totalMinutes)
            speakCallback(startMsg)

            announceCurrentStep(true)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!isNavigating || maneuvers.isEmpty()) return

        val targetManeuver = maneuvers.getOrNull(currentManeuverIndex) ?: run {
            speakCallback(context.getString(com.shinji.serena.R.string.valhalla_near_destination))
            stopNavigation()
            return
        }

        // 次の曲がり角までの距離
        val distToStep = calculateDistanceMeters(location.latitude, location.longitude, targetManeuver.lat, targetManeuver.lon)
        val bearingToStep = calculateBearingDegrees(location.latitude, location.longitude, targetManeuver.lat, targetManeuver.lon)

        // スマホのコンパス向きに対する相対方向（正面、右斜め前、左など）
        val relativeAngle = bearingToStep - currentHeadingDegrees
        val relativeDir = getRelativeDirectionName(context, relativeAngle)

        // 次のステップに到達判定 (< 15メートル)
        if (distToStep < 15.0) {
            currentManeuverIndex++
            soundAndHapticHelper?.performKeyClickHaptic()
            if (currentManeuverIndex >= maneuvers.size) {
                soundAndHapticHelper?.playActionDone()
                speakCallback(context.getString(com.shinji.serena.R.string.valhalla_arrived_fmt, destinationName))
                stopNavigation()
                return
            } else {
                announceCurrentStep(false)
            }
            return
        }

        // 25秒間隔または一定距離接近時の音声サポート
        val now = System.currentTimeMillis()
        if (now - lastDistanceAnnouncementMs > 25000L) {
            lastDistanceAnnouncementMs = now
            val distInt = distToStep.toInt()
            if (distInt in 20..150) {
                speakCallback(context.getString(com.shinji.serena.R.string.valhalla_step_ahead_fmt, relativeDir, distInt, targetManeuver.instruction))
            }
        }
    }

    private fun announceCurrentStep(isStart: Boolean) {
        val m = maneuvers.getOrNull(currentManeuverIndex) ?: return
        val distInt = m.distanceMeters.toInt()
        val dirMsg = if (isStart) {
            context.getString(com.shinji.serena.R.string.valhalla_start_step_fmt, m.streetName, distInt)
        } else {
            context.getString(com.shinji.serena.R.string.valhalla_next_step_fmt, m.instruction, distInt)
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
