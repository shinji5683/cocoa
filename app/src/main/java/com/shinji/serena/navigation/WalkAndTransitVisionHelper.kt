package com.shinji.serena.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import com.shinji.serena.R
import kotlin.math.max
import kotlin.math.min

/**
 * WalkAndTransitVisionHelper
 * 完全オンデバイス・ミリ秒処理で動作する
 * 歩行者信号（青/赤）・点字ブロック・障害物リアルタイム視覚ナビゲーション支援エンジン。
 */
object WalkAndTransitVisionHelper {

    enum class TrafficLightState {
        GREEN,     // 青信号（進んでOK）
        RED,       // 赤信号（止まれ）
        UNKNOWN    // 検知なし / 不明
    }

    enum class DirectionGuide {
        CENTER,    // 正面まっすぐ
        SLIGHT_LEFT,// やや左寄り
        SLIGHT_RIGHT,// やや右寄り
        FAR_LEFT,  // 左側
        FAR_RIGHT, // 右側
        NONE       // 検知なし
    }

    data class PedestrianGuide(
        val direction: String,
        val distance: String,
        val isVeryClose: Boolean,
        val message: String
    )

    data class TransitWalkState(
        val trafficLight: TrafficLightState,
        val trafficLightMessage: String?,
        val brailleBlockFound: Boolean,
        val brailleBlockDirection: DirectionGuide,
        val brailleBlockMessage: String?,
        val stereoPan: Float, // -1.0 (全左) 〜 0.0 (中央) 〜 +1.0 (全右)
        val pedestrianGuide: PedestrianGuide? = null
    )

    /**
     * カメラ画像（BitmapまたはYUVプレーン）から信号機（青/赤）および点字ブロックを高速解析
     */
    fun analyzeWalkingScene(
        bitmap: Bitmap?,
        imageWidth: Int,
        imageHeight: Int,
        context: Context? = null
    ): TransitWalkState {
        if (bitmap == null || imageWidth <= 0 || imageHeight <= 0) {
            return TransitWalkState(
                trafficLight = TrafficLightState.UNKNOWN,
                trafficLightMessage = null,
                brailleBlockFound = false,
                brailleBlockDirection = DirectionGuide.NONE,
                brailleBlockMessage = null,
                stereoPan = 0.0f
            )
        }

        val stepX = max(1, imageWidth / 40)
        val stepY = max(1, imageHeight / 40)

        var redCount = 0
        var greenCount = 0
        var yellowBlockCount = 0
        var yellowSumX = 0L
        var yellowSampleTotal = 0

        val hsv = FloatArray(3)

        val upperLimitY = (imageHeight * 0.65f).toInt()
        val lowerStartY = (imageHeight * 0.45f).toInt()

        for (y in 0 until imageHeight step stepY) {
            for (x in 0 until imageWidth step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (y < upperLimitY) {
                    if (r > 150 && g < 80 && b < 80) {
                        redCount++
                    } else if (g > 150 && r < 90 && b > 80 && b < 200) {
                        greenCount++
                    }
                }

                if (y >= lowerStartY) {
                    Color.colorToHSV(pixel, hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]

                    if (h in 38f..65f && s > 0.40f && v > 0.35f) {
                        yellowBlockCount++
                        yellowSumX += x
                        yellowSampleTotal++
                    }
                }
            }
        }

        val trafficLightState = when {
            greenCount > 15 && greenCount > redCount * 2 -> TrafficLightState.GREEN
            redCount > 15 && redCount > greenCount * 2 -> TrafficLightState.RED
            else -> TrafficLightState.UNKNOWN
        }

        val trafficMsg = when (trafficLightState) {
            TrafficLightState.GREEN -> context?.getString(R.string.transit_green_light)
            TrafficLightState.RED -> context?.getString(R.string.transit_red_light)
            TrafficLightState.UNKNOWN -> null
        }

        val hasBraille = yellowSampleTotal > 25
        val (direction, pan, brailleMsg) = if (hasBraille) {
            val avgX = (yellowSumX / yellowSampleTotal).toFloat()
            val ratioX = avgX / imageWidth
            when {
                ratioX < 0.25f -> Triple(DirectionGuide.FAR_LEFT, -0.8f, context?.getString(R.string.transit_braille_far_left))
                ratioX in 0.25f..0.42f -> Triple(DirectionGuide.SLIGHT_LEFT, -0.4f, context?.getString(R.string.transit_braille_slight_left))
                ratioX in 0.42f..0.58f -> Triple(DirectionGuide.CENTER, 0.0f, context?.getString(R.string.transit_braille_center))
                ratioX in 0.58f..0.75f -> Triple(DirectionGuide.SLIGHT_RIGHT, 0.4f, context?.getString(R.string.transit_braille_slight_right))
                else -> Triple(DirectionGuide.FAR_RIGHT, 0.8f, context?.getString(R.string.transit_braille_far_right))
            }
        } else {
            Triple(DirectionGuide.NONE, 0.0f, null)
        }

        return TransitWalkState(
            trafficLight = trafficLightState,
            trafficLightMessage = trafficMsg,
            brailleBlockFound = hasBraille,
            brailleBlockDirection = direction,
            brailleBlockMessage = brailleMsg,
            stereoPan = pan
        )
    }

    /**
     * 検出された顔のバウンディングボックス群から、注視すべき歩行者を特定し、相対方向と距離の案内を生成
     */
    fun calculatePedestrianGuideFromFaces(
        faceRects: List<android.graphics.Rect>,
        imageWidth: Int,
        imageHeight: Int,
        context: Context
    ): PedestrianGuide? {
        if (faceRects.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null
        val primary = faceRects.maxByOrNull { it.width() * it.height() } ?: return null
        val centerX = primary.centerX().toFloat() / imageWidth.coerceAtLeast(1)
        val direction = when {
            centerX < 0.25f -> context.getString(R.string.dir_left)
            centerX in 0.25f..0.40f -> context.getString(R.string.dir_front_left)
            centerX in 0.40f..0.60f -> context.getString(R.string.dir_front)
            centerX in 0.60f..0.75f -> context.getString(R.string.dir_front_right)
            else -> context.getString(R.string.dir_right)
        }
        val widthRatio = primary.width().toFloat() / imageWidth.coerceAtLeast(1)
        val isVeryClose = widthRatio > 0.28f
        val distance = when {
            widthRatio > 0.28f -> context.getString(R.string.face_distance_close_60cm)
            widthRatio > 0.18f -> context.getString(R.string.face_distance_1m_short)
            widthRatio > 0.10f -> context.getString(R.string.face_distance_1_5m)
            widthRatio > 0.05f -> context.getString(R.string.face_distance_2_5m)
            else -> context.getString(R.string.face_distance_far_3m)
        }
        val message = if (isVeryClose) {
            context.getString(R.string.transit_pedestrian_close_warning, direction)
        } else {
            context.getString(R.string.transit_pedestrian_detected, direction, distance)
        }
        return PedestrianGuide(direction, distance, isVeryClose, message)
    }

    /**
     * 検出された人物（全身・半身）のバウンディングボックス群から、注視すべき歩行者を特定し、相対方向と距離の案内を生成
     */
    fun calculatePedestrianGuideFromObjects(
        personRects: List<android.graphics.Rect>,
        imageWidth: Int,
        imageHeight: Int,
        context: Context
    ): PedestrianGuide? {
        if (personRects.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null
        val primary = personRects.maxByOrNull { it.width() * it.height() } ?: return null
        val centerX = primary.centerX().toFloat() / imageWidth.coerceAtLeast(1)
        val direction = when {
            centerX < 0.25f -> context.getString(R.string.dir_left)
            centerX in 0.25f..0.40f -> context.getString(R.string.dir_front_left)
            centerX in 0.40f..0.60f -> context.getString(R.string.dir_front)
            centerX in 0.60f..0.75f -> context.getString(R.string.dir_front_right)
            else -> context.getString(R.string.dir_right)
        }
        val heightRatio = primary.height().toFloat() / imageHeight.coerceAtLeast(1)
        val isVeryClose = heightRatio > 0.65f
        val distance = when {
            heightRatio > 0.65f -> context.getString(R.string.face_distance_close_60cm)
            heightRatio > 0.45f -> context.getString(R.string.face_distance_1m_short)
            heightRatio > 0.28f -> context.getString(R.string.face_distance_1_5m)
            heightRatio > 0.15f -> context.getString(R.string.face_distance_2_5m)
            else -> context.getString(R.string.face_distance_far_3m)
        }
        val message = if (isVeryClose) {
            context.getString(R.string.transit_pedestrian_close_warning, direction)
        } else {
            context.getString(R.string.transit_pedestrian_detected, direction, distance)
        }
        return PedestrianGuide(direction, distance, isVeryClose, message)
    }

    /**
     * 顔検出と物体検出の結果を統合し、最も重要・近接している歩行者案内を選択
     */
    fun calculateBestPedestrianGuide(
        faceRects: List<android.graphics.Rect>,
        objectPersonRects: List<android.graphics.Rect>,
        imageWidth: Int,
        imageHeight: Int,
        context: Context
    ): PedestrianGuide? {
        val faceGuide = calculatePedestrianGuideFromFaces(faceRects, imageWidth, imageHeight, context)
        val objectGuide = calculatePedestrianGuideFromObjects(objectPersonRects, imageWidth, imageHeight, context)

        return when {
            faceGuide != null && objectGuide != null -> {
                if (faceGuide.isVeryClose || !objectGuide.isVeryClose) faceGuide else objectGuide
            }
            faceGuide != null -> faceGuide
            objectGuide != null -> objectGuide
            else -> null
        }
    }
}
