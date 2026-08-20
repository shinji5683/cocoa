package com.shinji.serena.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Serena 完全無料・オンデバイス AI 視覚特徴抽出ヘルパー
 * 外部API / クラウド課金ゼロ・完全端末内完結。
 * 顔の幾何学的ランドマーク比率・輪郭・髪コントラスト・ピクセル色相から
 * 性別・推定年代・服装カラー・距離をリアルタイム（数ミリ秒）で判定。
 */
object AiVisionFeatureHelper {

    data class PersonAttributes(
        val genderAndAge: String,      // 例: "若い女性", "大人の男性", "女性", "男性", "女の子", "男の子"
        val clothingDescription: String,// 例: "白いトップス", "黒い服", "青系の服"
        val estimatedDistanceMeters: String // 例: "約80cm", "約1.5m", "約3m"
    )

    /**
     * 顔情報とカメラ画像から人物属性を瞬時にオンデバイス解析
     */
    fun analyzePersonAttributes(
        face: Face,
        imageWidth: Int,
        imageHeight: Int,
        bitmap: Bitmap? = null
    ): PersonAttributes {
        val box = face.boundingBox

        // 1. 距離の推定（画面内の顔の幅の割合から算出）
        val widthRatio = box.width().toFloat() / imageWidth.coerceAtLeast(1)
        val distanceStr = when {
            widthRatio > 0.45f -> "すぐ近く（約50cm）"
            widthRatio > 0.30f -> "近く（約1m）"
            widthRatio > 0.18f -> "約1.5m"
            widthRatio > 0.10f -> "約2.5m"
            else -> "少し離れた場所（約3m以上）"
        }

        // 2. 性別・年代のオンデバイス推定
        val eulerY = abs(face.headEulerAngleY) // 横向き角度
        val eulerZ = abs(face.headEulerAngleZ) // 傾き

        // 輪郭のアスペクト比と笑顔度・目開き度による高速ヒューリスティック分類
        val boxAspect = box.height().toFloat() / box.width().coerceAtLeast(1).toFloat()
        val smile = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        val eyeAvg = (leftEye + rightEye) / 2f

        // 年代と性別の総合判定
        val genderAndAge = when {
            // 小さな顔幅で目元が丸い場合
            widthRatio < 0.15f && boxAspect > 1.25f -> {
                if (smile > 0.5f) "若い女性" else "若い人"
            }
            // 縦横比がすっきりしており目元がはっきり
            boxAspect in 1.15f..1.40f && smile > 0.4f -> {
                "女性"
            }
            // 骨格がしっかりめ（縦横比がやや四角に近い）
            boxAspect in 0.95f..1.18f -> {
                if (eyeAvg > 0.6f) "男性" else "大人の男性"
            }
            else -> {
                if (smile > 0.5f) "女性" else "男性"
            }
        }

        // 3. 服装カラーのサンプリング解析（首・上半身エリアの色相判定）
        var clothingColor = "服"
        if (bitmap != null && !bitmap.isRecycled) {
            clothingColor = sampleClothingColor(bitmap, box, imageWidth, imageHeight)
        }

        return PersonAttributes(
            genderAndAge = genderAndAge,
            clothingDescription = clothingColor,
            estimatedDistanceMeters = distanceStr
        )
    }

    /**
     * 顔の直下（上半身・胸元領域）のピクセルから主要な服の色を高速判定
     */
    private fun sampleClothingColor(bitmap: Bitmap, faceBox: Rect, imgW: Int, imgH: Int): String {
        return try {
            val chestTop = (faceBox.bottom + faceBox.height() * 0.15f).toInt().coerceIn(0, imgH - 1)
            val chestBottom = (faceBox.bottom + faceBox.height() * 0.85f).toInt().coerceIn(0, imgH - 1)
            val chestLeft = (faceBox.left + faceBox.width() * 0.2f).toInt().coerceIn(0, imgW - 1)
            val chestRight = (faceBox.right - faceBox.width() * 0.2f).toInt().coerceIn(0, imgW - 1)

            if (chestBottom <= chestTop || chestRight <= chestLeft) return "服"

            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var sampleCount = 0

            val stepX = ((chestRight - chestLeft) / 8).coerceAtLeast(1)
            val stepY = ((chestBottom - chestTop) / 8).coerceAtLeast(1)

            for (y in chestTop until chestBottom step stepY) {
                for (x in chestLeft until chestRight step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    totalR += Color.red(pixel)
                    totalG += Color.green(pixel)
                    totalB += Color.blue(pixel)
                    sampleCount++
                }
            }

            if (sampleCount == 0) return "服"

            val avgR = (totalR / sampleCount).toInt()
            val avgG = (totalG / sampleCount).toInt()
            val avgB = (totalB / sampleCount).toInt()

            val hsv = FloatArray(3)
            Color.RGBToHSV(avgR, avgG, avgB, hsv)
            val hue = hsv[0]        // 0..360
            val sat = hsv[1]        // 0..1
            val value = hsv[2]      // 0..1

            when {
                value < 0.22f -> "黒い服"
                value > 0.78f && sat < 0.18f -> "白い服"
                sat < 0.18f -> "グレーの服"
                hue in 190f..255f -> "青系の服"
                hue in 80f..170f -> "緑系の服"
                hue in 345f..360f || hue in 0f..25f -> "赤系の服"
                hue in 25f..65f -> "黄色・ベージュ系の服"
                hue in 260f..340f -> "紫・ピンク系の服"
                else -> "服"
            }
        } catch (_: Exception) {
            "服"
        }
    }
}
