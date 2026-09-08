package com.shinji.serena.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shinji.serena.SoundAndHapticHelper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * StreetIntersectionNavigator (ストリート名＆交差点・周辺施設 空間ナビエンジン)
 *
 * 全盲ユーザーの屋外単独歩行を強力にサポート：
 * 1. 現在歩いている「通りの名前（Street Name）」と「進行方位」を即座に言語化。
 * 2. 正面20m〜60m先にある「交差点・横断歩道」を事前検知し、車道進入前に先行アラート。
 * 3. 周辺の主要施設（コンビニ、カフェ、駅、薬局など）を「時計盤表現ゼロの直感的相対方向」で案内。
 * 4. 日本語・英語の完全マルチリンガル対応。
 */
class StreetIntersectionNavigator(
    private val context: Context,
    private val soundHelper: SoundAndHapticHelper?,
    private val locationAddressHelper: LocationAddressHelper,
    private val speakCallback: (String) -> Unit
) {

    companion object {
        private const val TAG = "StreetNav"
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val isJapanese: Boolean
        get() = Locale.getDefault().language.lowercase() == "ja"

    var currentHeadingDegrees: Float = 0.0f

    /**
     * 現在地のストリート名・交差点・周辺施設を一括スキャンして音声案内
     */
    fun announceStreetAndIntersections() {
        val fineLocation = getBestLocation()
        if (fineLocation == null) {
            val noGpsMsg = if (isJapanese) "現在地を取得できませんでした。GPSが有効か確認してください。" else "Unable to get current location. Please check your GPS."
            speakCallback(noGpsMsg)
            return
        }

        soundHelper?.playFocusMove()
        val scanningMsg = if (isJapanese) "ストリートと周辺交差点をスキャン中..." else "Scanning streets and nearby intersections..."
        speakCallback(scanningMsg)

        val lat = fineLocation.latitude
        val lon = fineLocation.longitude
        val bearing = if (fineLocation.hasBearing()) fineLocation.bearing else currentHeadingDegrees

        // 1. ストリート名と地区の取得
        locationAddressHelper.resolveStreetAndNeighborhood(fineLocation) { streetName, neighborhood ->
            // 2. OpenStreetMap Overpass API から周辺交差点と主要施設を取得
            thread {
                try {
                    val report = fetchNearbyIntersectionsAndPois(lat, lon, bearing, streetName, neighborhood)
                    mainHandler.post {
                        soundHelper?.playActionDone()
                        speakCallback(report)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Overpass query error: ${e.message}", e)
                    mainHandler.post {
                        // フォールバック: 通り名と方位のみ案内
                        val fallback = buildBasicStreetAnnouncement(streetName, neighborhood, bearing)
                        speakCallback(fallback)
                    }
                }
            }
        }
    }

    private fun buildBasicStreetAnnouncement(streetName: String, neighborhood: String, bearing: Float): String {
        val ja = isJapanese
        val cardinalDir = getCardinalDirection(bearing, ja)

        return if (ja) {
            val placePart = if (streetName.isNotEmpty()) "「$streetName」" else if (neighborhood.isNotEmpty()) "「$neighborhood」付近" else "現在の通り"
            "現在、$placePart を $cardinalDir に向かって歩行中です。"
        } else {
            val placePart = if (streetName.isNotEmpty()) "on $streetName" else if (neighborhood.isNotEmpty()) "near $neighborhood" else "on current street"
            "Currently walking $cardinalDir $placePart."
        }
    }

    private fun fetchNearbyIntersectionsAndPois(
        lat: Double,
        lon: Double,
        bearing: Float,
        streetName: String,
        neighborhood: String
    ): String {
        val ja = isJapanese
        val cardinalDir = getCardinalDirection(bearing, ja)
        val delta = 0.0008 // 約80メートル四方

        val minLat = lat - delta
        val minLon = lon - delta
        val maxLat = lat + delta
        val maxLon = lon + delta

        val query = """
            [out:json][timeout:5];
            (
              node["highway"="crossing"]($minLat,$minLon,$maxLat,$maxLon);
              node["highway"="traffic_signals"]($minLat,$minLon,$maxLat,$maxLon);
              node["amenity"~"cafe|convenience|restaurant|pharmacy|bank|post_office"]($minLat,$minLon,$maxLat,$maxLon);
              node["railway"="station"]($minLat,$minLon,$maxLat,$maxLon);
            );
            out body 8;
        """.trimIndent()

        val url = URL("https://overpass-api.de/api/interpreter")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 4500
        conn.readTimeout = 4500
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Android Spatial Navigator)")

        conn.outputStream.use { os ->
            os.write("data=${java.net.URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8))
        }

        var intersectionFound = false
        var intersectionDist = 0
        var intersectionDirection = ""

        val nearbyPois = mutableListOf<String>()

        if (conn.responseCode == 200) {
            val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
            val root = JSONObject(jsonStr)
            val elements = root.optJSONArray("elements")

            if (elements != null) {
                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val elemLat = elem.getDouble("lat")
                    val elemLon = elem.getDouble("lon")
                    val tags = elem.optJSONObject("tags") ?: JSONObject()

                    val dist = calculateDistanceMeters(lat, lon, elemLat, elemLon)
                    val targetBearing = calculateBearing(lat, lon, elemLat, elemLon)
                    val relativeBearing = (targetBearing - bearing + 360.0) % 360.0
                    val relDir = OsmValhallaNavigationHelper.getRelativeDirectionName(relativeBearing, ja)

                    val highway = tags.optString("highway", "")
                    val amenity = tags.optString("amenity", "")
                    val name = tags.optString("name", "")

                    if ((highway == "crossing" || highway == "traffic_signals") && !intersectionFound && dist in 10.0..65.0) {
                        intersectionFound = true
                        intersectionDist = dist.toInt()
                        intersectionDirection = relDir
                    } else if (dist <= 70.0) {
                        val label = when {
                            name.isNotEmpty() -> name
                            amenity == "convenience" -> if (ja) "コンビニ" else "convenience store"
                            amenity == "cafe" -> if (ja) "カフェ" else "cafe"
                            amenity == "pharmacy" -> if (ja) "薬局" else "pharmacy"
                            amenity == "bank" -> if (ja) "銀行" else "bank"
                            amenity == "restaurant" -> if (ja) "飲食店" else "restaurant"
                            tags.optString("railway") == "station" -> if (ja) "駅" else "station"
                            else -> ""
                        }
                        if (label.isNotEmpty() && nearbyPois.size < 2) {
                            val distText = if (ja) "${dist.toInt()}メートル" else "${dist.toInt()}m"
                            nearbyPois.add(if (ja) "$relDir $distText に $label" else "$label, $relDir $distText")
                        }
                    }
                }
            }
        }

        // 音声メッセージの組み立て
        val sb = StringBuilder()
        if (ja) {
            val placePart = if (streetName.isNotEmpty()) "「$streetName」" else if (neighborhood.isNotEmpty()) "「$neighborhood」付近" else "現在の通り"
            sb.append("現在、$placePart を $cardinalDir に向かって歩行中です。")

            if (intersectionFound) {
                sb.append("注意: $intersectionDirection ${intersectionDist}メートル先に交差点・横断歩道があります。")
            }
            if (nearbyPois.isNotEmpty()) {
                sb.append("周辺: ${nearbyPois.joinToString("、")}。")
            }
        } else {
            val placePart = if (streetName.isNotEmpty()) "on $streetName" else if (neighborhood.isNotEmpty()) "near $neighborhood" else "on current street"
            sb.append("Walking $cardinalDir $placePart. ")

            if (intersectionFound) {
                sb.append("Caution: $intersectionDirection $intersectionDist meters, intersection or crosswalk ahead. ")
            }
            if (nearbyPois.isNotEmpty()) {
                sb.append("Nearby: ${nearbyPois.joinToString(", ")}.")
            }
        }

        return sb.toString().trim()
    }

    private fun getCardinalDirection(bearing: Float, isJapanese: Boolean): String {
        val norm = (bearing % 360.0 + 360.0) % 360.0
        return if (isJapanese) {
            when {
                norm in 337.5..360.0 || norm in 0.0..22.5 -> "北"
                norm in 22.5..67.5 -> "北東"
                norm in 67.5..112.5 -> "東"
                norm in 112.5..157.5 -> "南東"
                norm in 157.5..202.5 -> "南"
                norm in 202.5..247.5 -> "南西"
                norm in 247.5..292.5 -> "西"
                else -> "北西"
            }
        } else {
            when {
                norm in 337.5..360.0 || norm in 0.0..22.5 -> "North"
                norm in 22.5..67.5 -> "Northeast"
                norm in 67.5..112.5 -> "East"
                norm in 112.5..157.5 -> "Southeast"
                norm in 157.5..202.5 -> "South"
                norm in 202.5..247.5 -> "Southwest"
                norm in 247.5..292.5 -> "West"
                else -> "Northwest"
            }
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球の半径 (m)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }

    @SuppressLint("MissingPermission")
    private fun getBestLocation(): Location? {
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
}
