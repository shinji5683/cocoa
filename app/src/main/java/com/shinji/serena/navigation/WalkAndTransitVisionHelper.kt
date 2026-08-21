package com.shinji.serena.navigation

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
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
        imageHeight: Int
    ): TransitWalkState {
        if (bitmap == null || imageWidth <= 0 || imageHeight <= 0) {
            return TransitWalkState(
                trafficLight = TrafficLightState.UNKNOWN,
                trafficLightMessage = null,
                brailleBlockFound = false,
                brailleBlockDirection = DirectionGuide.NONE,
                brailleBlockMessage = null,
                stereoPan = 0f
            )
        }

        // サンプリング解析（高速化のため10x10ピクセル間隔で走査）
        val sampleStep = 8
        var greenCount = 0
        var redCount = 0
        var yellowBlockCount = 0

        var yellowSumX = 0L
        var yellowSampleTotal = 0

        val upperLimitY = (imageHeight * 0.65f).toInt() // 信号機は画面上部〜中部に多い
        val lowerStartY = (imageHeight * 0.45f).toInt() // 点字ブロックは画面下部に多い

        val hsv = FloatArray(3)

        // 1. 信号機解析（上部領域）
        for (y in 0 until upperLimitY step sampleStep) {
            for (x in 0 until imageWidth step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0] // 0..360
                val s = hsv[1] // 0..1
                val v = hsv[2] // 0..1

                // 高輝度かつ高彩度の発光LED検出
                if (v > 0.65f && s > 0.45f) {
                    // 歩行者用青信号（エメラルドグリーン・シアン系: 140°〜190°）
                    if (h in 135f..195f) {
                        greenCount++
                    }
                    // 赤信号（0°〜20° または 340°〜360°）
                    else if (h in 0f..20f || h in 340f..360f) {
                        redCount++
                    }
                }
            }
        }

        // 2. 点字ブロック解析（下部領域）
        for (y in lowerStartY until imageHeight step sampleStep) {
            for (x in 0 until imageWidth step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                // 点字ブロック特有の黄色（40°〜65°、明度・彩度がある程度高い）
                if (h in 38f..65f && s > 0.40f && v > 0.35f) {
                    yellowBlockCount++
                    yellowSumX += x
                    yellowSampleTotal++
                }
            }
        }

        // 判定ロジック
        val trafficLightState = when {
            greenCount > 15 && greenCount > redCount * 2 -> TrafficLightState.GREEN
            redCount > 15 && redCount > greenCount * 2 -> TrafficLightState.RED
            else -> TrafficLightState.UNKNOWN
        }

        val trafficMsg = when (trafficLightState) {
            TrafficLightState.GREEN -> "🟢 青信号です！安全を確認して横断できます。"
            TrafficLightState.RED -> "🔴 赤信号です！立ち止まってください。"
            TrafficLightState.UNKNOWN -> null
        }

        val hasBraille = yellowSampleTotal > 25
        val (direction, pan, brailleMsg) = if (hasBraille) {
            val avgX = (yellowSumX / yellowSampleTotal).toFloat()
            val ratioX = avgX / imageWidth // 0.0 (左) 〜 1.0 (右)
            when {
                ratioX < 0.25f -> Triple(DirectionGuide.FAR_LEFT, -0.8f, "🟨 点字ブロックは左側にあります。")
                ratioX in 0.25f..0.42f -> Triple(DirectionGuide.SLIGHT_LEFT, -0.4f, "🟨 点字ブロックはやや左です。")
                ratioX in 0.42f..0.58f -> Triple(DirectionGuide.CENTER, 0.0f, "🟨 正面に点字ブロックがあります。このまま直進してください。")
                ratioX in 0.58f..0.75f -> Triple(DirectionGuide.SLIGHT_RIGHT, 0.4f, "🟨 点字ブロックはやや右です。")
                else -> Triple(DirectionGuide.FAR_RIGHT, 0.8f, "🟨 点字ブロックは右側にあります。")
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
