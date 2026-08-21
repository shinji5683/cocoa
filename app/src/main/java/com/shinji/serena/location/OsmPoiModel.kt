package com.shinji.serena.location

/**
 * OpenStreetMap POI & 地図要素モデル
 */
data class OsmPoiModel(
    val id: Long,
    val name: String,
    val category: String, // "crosswalk", "traffic_signals", "convenience", "station", "bus_stop", "restaurant", "hospital", etc.
    val categoryDisplayName: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Double = 0.0,
    val relativeBearingDegrees: Double = 0.0, // 進行方向/向いている方角からの相対角度 (-180 ~ +180)
    val relativeDirectionName: String = "正面" // 「正面」「右斜め前」「右」「右斜め後ろ」「真後ろ」「左斜め後ろ」「左」「左斜め前」
)

/**
 * ターンバイターン・ナビゲーション指示モデル
 */
data class NavManeuver(
    val instruction: String,
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: ManeuverType,
    val lat: Double,
    val lon: Double
)

enum class ManeuverType {
    START,
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN,
    SHARP_LEFT,
    LEFT,
    SLIGHT_LEFT,
    DESTINATION
}
