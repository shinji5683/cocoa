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

    data class TransitWalkState(
        val trafficLight: TrafficLightState,
        val trafficLightMessage: String?,
        val brailleBlockFound: Boolean,
        val brailleBlockDirection: DirectionGuide,
        val brailleBlockMessage: String?,
        val stereoPan: Float // -1.0 (全左) 〜 0.0 (中央) 〜 +1.0 (全右)
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
}
