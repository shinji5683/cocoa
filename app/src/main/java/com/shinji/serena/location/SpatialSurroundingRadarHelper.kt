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
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * SpatialSurroundingRadarHelper
 * OpenStreetMap 周辺POI ＆ 3D音響・触知マップレーダーエンジン
 * スマホを向けた方角の施設や横断歩道を「正面」「右斜め前」などの直感的相対方向と立体音響で案内
 */
class SpatialSurroundingRadarHelper(
    private val context: Context,
    private val soundAndHapticHelper: SoundAndHapticHelper?,
    private val speakCallback: (String) -> Unit
) {

    companion object {
        private const val TAG = "SpatialRadarHelper"
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val nearbyPois = mutableListOf<OsmPoiModel>()
    private var isRadarActive = false
    private var lastAnnouncedPoiId: Long = -1L
    private var lastAnnouncementTimeMs = 0L

    var currentHeadingDegrees: Float = 0.0f

    fun isRadarActive(): Boolean = isRadarActive

    /**
     * 周辺マップレーダーの開始
     */
    fun startSurroundingRadar() {
        val fineLoc = getBestCurrentLocation()
        if (fineLoc == null) {
            speakCallback("現在地を取得できませんでした。GPSをONにしてください。")
            return
        }

        isRadarActive = true
        soundAndHapticHelper?.playActionDone()
        speakCallback("周辺マップレーダーを開始しました。スマホを周囲に向けると、その方向にある施設や横断歩道を音と音声で案内します。")

        refreshNearbyPois(fineLoc.latitude, fineLoc.longitude)
    }

    fun stopSurroundingRadar() {
        if (!isRadarActive) return
        isRadarActive = false
        nearbyPois.clear()
        lastAnnouncedPoiId = -1L
        soundAndHapticHelper?.playActionDone()
        speakCallback("周辺マップレーダーを停止しました。")
    }

    /**
     * 周辺POIリストの手動再読み込み
     */
    fun refreshNearbyPois(lat: Double, lon: Double) {
        thread {
            try {
                // OpenStreetMap Overpass API (半径150m以内の歩行関連POI & 施設を高速取得)
                val overpassQuery = """
                    [out:json][timeout:8];
                    (
                      node["highway"="crossing"](around:150, $lat, $lon);
                      node["highway"="traffic_signals"](around:150, $lat, $lon);
                      node["shop"="convenience"](around:200, $lat, $lon);
                      node["railway"="station"](around:350, $lat, $lon);
                      node["highway"="bus_stop"](around:200, $lat, $lon);
                      node["amenity"="pharmacy"](around:200, $lat, $lon);
                      node["amenity"="hospital"](around:300, $lat, $lon);
                      node["amenity"="post_office"](around:250, $lat, $lon);
                      node["amenity"="bank"](around:200, $lat, $lon);
                      node["amenity"="atm"](around:150, $lat, $lon);
                    );
                    out body 25;
                """.trimIndent()

                val urlStr = "https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(overpassQuery, "UTF-8")
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Accessibility)")

                if (conn.responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                    parseOverpassJson(response, lat, lon)
                } else {
                    // フォールバック: Photon API 周辺施設検索
                    fetchPhotonNearby(lat, lon)
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshNearbyPois error: ${e.message}")
                fetchPhotonNearby(lat, lon)
            }
        }
    }

    private fun fetchPhotonNearby(lat: Double, lon: Double) {
        try {
            val urlStr = "https://photon.komoot.io/api/?lat=$lat&lon=$lon&limit=15&lang=ja"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "SerenaScreenReader/1.0 (Accessibility)")

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val root = JSONObject(response)
                val features = root.optJSONArray("features") ?: return
                val list = mutableListOf<OsmPoiModel>()

                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val coords = feat.getJSONObject("geometry").getJSONArray("coordinates")
                    val pLon = coords.getDouble(0)
                    val pLat = coords.getDouble(1)
                    val props = feat.optJSONObject("properties") ?: continue
                    val name = props.optString("name", "")
                    if (name.isEmpty()) continue

                    val osmId = props.optLong("osm_id", i.toLong())
                    val dist = calculateDistanceMeters(lat, lon, pLat, pLon)
                    val bearing = calculateBearingDegrees(lat, lon, pLat, pLon)
                    val relAngle = bearing - currentHeadingDegrees
                    val dirName = OsmValhallaNavigationHelper.getRelativeDirectionName(relAngle)

                    list.add(OsmPoiModel(osmId, name, "poi", "施設", pLat, pLon, dist, relAngle, dirName))
                }

                mainHandler.post {
                    nearbyPois.clear()
                    nearbyPois.addAll(list)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPhotonNearby error: ${e.message}")
        }
    }

    private fun parseOverpassJson(jsonStr: String, currentLat: Double, currentLon: Double) {
        val root = JSONObject(jsonStr)
        val elements = root.optJSONArray("elements") ?: return
        val list = mutableListOf<OsmPoiModel>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val id = el.optLong("id")
            val pLat = el.optDouble("lat")
            val pLon = el.optDouble("lon")
            val tags = el.optJSONObject("tags") ?: JSONObject()

            val name = tags.optString("name", "").ifEmpty {
                when {
                    tags.optString("highway") == "crossing" -> "横断歩道"
                    tags.optString("highway") == "traffic_signals" -> "信号機"
                    tags.optString("shop") == "convenience" -> tags.optString("brand", "コンビニ")
                    tags.optString("railway") == "station" -> "駅"
                    tags.optString("highway") == "bus_stop" -> "バス停"
                    tags.optString("amenity") == "pharmacy" -> "薬局"
                    tags.optString("amenity") == "hospital" -> "病院"
                    tags.optString("amenity") == "post_office" -> "郵便局"
                    tags.optString("amenity") == "bank" -> "銀行"
                    tags.optString("amenity") == "atm" -> "ATM"
                    else -> "周辺施設"
                }
            }

            val category = when {
                tags.optString("highway") == "crossing" -> "横断歩道"
                tags.optString("highway") == "traffic_signals" -> "信号機"
                tags.optString("shop") == "convenience" -> "コンビニ"
                tags.optString("railway") == "station" -> "駅"
                tags.optString("highway") == "bus_stop" -> "バス停"
                else -> "施設"
            }

            val dist = calculateDistanceMeters(currentLat, currentLon, pLat, pLon)
            val bearing = calculateBearingDegrees(currentLat, currentLon, pLat, pLon)
            val relAngle = bearing - currentHeadingDegrees
            val dirName = OsmValhallaNavigationHelper.getRelativeDirectionName(relAngle)

            list.add(OsmPoiModel(id, name, category, category, pLat, pLon, dist, relAngle, dirName))
        }

        mainHandler.post {
            nearbyPois.clear()
            nearbyPois.addAll(list)
            if (nearbyPois.isNotEmpty()) {
                val count = nearbyPois.size
                speakCallback("周囲 ${count}件の施設や横断歩道を取得しました。")
            }
        }
    }

    /**
     * コンパスの向きが変化した時に呼び出し（360度音響レーダー）
     */
    fun onCompassHeadingUpdated(heading: Float) {
        currentHeadingDegrees = heading
        if (!isRadarActive || nearbyPois.isEmpty()) return

        val fineLoc = getBestCurrentLocation() ?: return

        // 正面方向（±25度以内）にある最も近いPOIを特定
        var bestPoi: OsmPoiModel? = null
        var minDistance = Double.MAX_VALUE

        for (p in nearbyPois) {
            val dist = calculateDistanceMeters(fineLoc.latitude, fineLoc.longitude, p.lat, p.lon)
            val bearing = calculateBearingDegrees(fineLoc.latitude, fineLoc.longitude, p.lat, p.lon)
            var relAngle = bearing - heading
            while (relAngle > 180.0) relAngle -= 360.0
            while (relAngle < -180.0) relAngle += 360.0

            if (abs(relAngle) <= 25.0) {
                if (dist < minDistance) {
                    minDistance = dist
                    bestPoi = p.copy(distanceMeters = dist, relativeBearingDegrees = relAngle, relativeDirectionName = "正面")
                }
            }
        }

        if (bestPoi != null) {
            val now = System.currentTimeMillis()
            if (bestPoi.id != lastAnnouncedPoiId || (now - lastAnnouncementTimeMs > 6000L)) {
                lastAnnouncedPoiId = bestPoi.id
                lastAnnouncementTimeMs = now

                soundAndHapticHelper?.playFocusMove()
                val distInt = bestPoi.distanceMeters.toInt()
                speakCallback("正面 ${distInt}メートル先に ${bestPoi.name}")
            }
        }
    }

    /**
     * 画面タッチ触知マップのタッチ処理
     * 画面中央 = 現在地, 上 = 正面, 右 = 右, 下 = 真後ろ, 左 = 左
     */
    fun onTouchTactileMap(normalizedX: Float, normalizedY: Float) {
        val fineLoc = getBestCurrentLocation() ?: return
        if (nearbyPois.isEmpty()) {
            refreshNearbyPois(fineLoc.latitude, fineLoc.longitude)
            return
        }

        // タッチ座標 (-1.0 ~ +1.0)
        val relX = (normalizedX - 0.5f) * 2.0f
        val relY = -(normalizedY - 0.5f) * 2.0f // 上がプラス

        val touchAngle = Math.toDegrees(atan2(relX.toDouble(), relY.toDouble()))
        val touchDistNorm = sqrt(relX * relX + relY * relY).coerceIn(0.0f, 1.0f)
        val touchDistMeters = touchDistNorm * 150.0 // 半径150mにスケーリング

        val targetDir = OsmValhallaNavigationHelper.getRelativeDirectionName(touchAngle)

        // タッチした方向と距離に最も近いPOIを探索
        val closest = nearbyPois.minByOrNull {
            val dDiff = abs(it.distanceMeters - touchDistMeters)
            val b = calculateBearingDegrees(fineLoc.latitude, fineLoc.longitude, it.lat, it.lon)
            var aDiff = abs((b - currentHeadingDegrees) - touchAngle)
            while (aDiff > 180.0) aDiff -= 360.0
            abs(aDiff) * 2.0 + dDiff
        }

        if (closest != null) {
            soundAndHapticHelper?.playSpatialTouchFeedback(normalizedX, normalizedY, true)
            val distInt = closest.distanceMeters.toInt()
            speakCallback("${targetDir} ${distInt}メートル、${closest.name}")
        }
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

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
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
}
