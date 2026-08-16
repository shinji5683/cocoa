package com.shinji.serena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * カラー・照明・明るさ・太陽チェッカー (Color, Light, Room Brightness & Solar Guide)
 * 物の色、部屋の照明がついているか、周囲の明るさ（Lux）、現在のお日様の状態を完全案内。
 */
class ColorAndLightHelper(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var currentLux: Float = -1f

    init {
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            currentLux = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun release() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * 照度センサーとお部屋の照明状態を判定
     */
    fun getRoomLightStatus(): String {
        val lux = currentLux
        return when {
            lux < 0f -> "照度センサー取得中"
            lux < 5f -> "周囲は真っ暗です。お部屋の照明は消えています（照度: ${lux.toInt()}ルクス）。"
            lux < 40f -> "周囲は薄暗いです。常夜灯または間接照明の明るさです（照度: ${lux.toInt()}ルクス）。"
            lux < 250f -> "お部屋の照明が点いています。落ち着いた明るさです（照度: ${lux.toInt()}ルクス）。"
            lux < 800f -> "お部屋の照明がしっかり明るく点いています（照度: ${lux.toInt()}ルクス）。"
            else -> "周囲は非常に明るく、直射日光や強い昼光が入っています（照度: ${lux.toInt()}ルクス）。"
        }
    }

    /**
     * 現在時刻と太陽（お日様）の位置・高度・日照状態を算出
     */
    fun getSunAndDaylightStatus(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeStr = SimpleDateFormat("a K時m分", Locale.JAPAN).format(Date())

        val sunDesc = when (hour) {
            in 4..5 -> "東の空からお日様が昇り始めています（夜明け・日の出前）。"
            in 6..9 -> "お日様は東から南東の空に昇り、朝の光が差し込んでいます。"
            in 10..13 -> "お日様は南の空の一番高い位置（南中）にあり、日中で最も明るい時間帯です。"
            in 14..16 -> "お日様は南西から西の空へ傾き始めています（午後の日差し）。"
            in 17..18 -> "西の空にお日様が沈みかけています（夕暮れ・日没時）。"
            in 19..23 -> "お日様は完全に沈んでおり、夜間です。"
            else -> "お日様は沈んでおり、深夜です。"
        }

        return "現在${timeStr}。${sunDesc}"
    }

    /**
     * 画像・カメラ中央ピクセルから色名（日本語の自然な日常色）を判定
     */
    fun detectColorFromBitmap(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2
        val centerY = height / 2

        // 中央エリア周辺をサンプリング
        val sampleRadius = (width.coerceAtMost(height) * 0.08f).toInt().coerceAtLeast(1)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        for (x in (centerX - sampleRadius)..(centerX + sampleRadius) step 2) {
            for (y in (centerY - sampleRadius)..(centerY + sampleRadius) step 2) {
                if (x in 0 until width && y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    totalR += Color.red(pixel)
                    totalG += Color.green(pixel)
                    totalB += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0) return "色を判定できませんでした"

        val avgR = (totalR / count).toInt()
        val avgG = (totalG / count).toInt()
        val avgB = (totalB / count).toInt()

        return getColorName(avgR, avgG, avgB)
    }

    /**
     * RGBからHSV/明度/彩度を分析して日常的な日本語色名に変換
     */
    fun getColorName(r: Int, g: Int, b: Int): String {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]        // 0..360
        val sat = hsv[1]        // 0..1
        val value = hsv[2]      // 0..1

        // 無彩色（黒・白・グレー）の判定
        if (value < 0.15f) return "漆黒・黒"
        if (value < 0.30f && sat < 0.25f) return "チャコールグレー・黒に近い灰色"
        if (sat < 0.12f) {
            return when {
                value > 0.88f -> "真っ白・ホワイト"
                value > 0.65f -> "明るい灰色・オフホワイト"
                else -> "灰色・グレー"
            }
        }

        // 有彩色の判定 (色相 Hue 基準)
        return when {
            // 赤〜ピンク系
            hue < 12 || hue >= 345 -> {
                when {
                    value < 0.40f -> "深紅・ワインレッド"
                    sat < 0.45f -> "薄いピンク・桜色"
                    sat > 0.70f && value > 0.75f -> "鮮やかな赤"
                    else -> "赤"
                }
            }
            // オレンジ・茶色系
            hue in 12.0..42.0 -> {
                when {
                    value < 0.45f -> if (sat > 0.5f) "焦げ茶色" else "茶色"
                    sat < 0.35f -> "ベージュ・肌色"
                    sat > 0.65f && value > 0.70f -> "明るいオレンジ色"
                    else -> "キャメル・薄茶色"
                }
            }
            // 黄色系
            hue in 42.0..68.0 -> {
                when {
                    sat < 0.35f -> "アイボリー・クリーム色"
                    value < 0.50f -> "マスタード・黄土色"
                    else -> "黄色・レモンイエロー"
                }
            }
            // 黄緑〜緑系
            hue in 68.0..155.0 -> {
                when {
                    hue < 95 -> "黄緑・ライムグリーン"
                    value < 0.35f -> "深緑・ダークグリーン"
                    sat < 0.40f -> "オリーブ・くすみグリーン"
                    else -> "緑・エメラルドグリーン"
                }
            }
            // シアン〜水色〜青系
            hue in 155.0..255.0 -> {
                when {
                    hue < 190 -> "青緑・ターコイズ"
                    value < 0.35f -> "濃紺・ダークネイビー"
                    value < 0.55f -> "紺色・ネイビー"
                    sat < 0.45f -> "水色・スカイブルー"
                    else -> "青・ブルー"
                }
            }
            // 紫〜マゼンタ系
            hue in 255.0..320.0 -> {
                when {
                    value < 0.40f -> "濃い紫・ダークパープル"
                    sat < 0.45f -> "薄紫・ラベンダー"
                    else -> "紫・パープル"
                }
            }
            // ピンク・ローズ系
            else -> {
                when {
                    sat < 0.50f -> "薄いピンク"
                    value > 0.75f -> "鮮やかなピンク・マゼンタ"
                    else -> "ローズ・ピンク"
                }
            }
        }
    }

    /**
     * お部屋・照明・お日様・現在色の総合フルレポート
     */
    fun buildFullSensoryReport(detectedColor: String? = null): String {
        val light = getRoomLightStatus()
        val sun = getSunAndDaylightStatus()
        val colorPart = if (!detectedColor.isNullOrBlank()) "目の前の物の色は「$detectedColor」です。" else ""
        return "【環境・照明・太陽レポート】\n$sun\n$light\n$colorPart".trim()
    }
}
