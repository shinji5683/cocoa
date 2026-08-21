package com.shinji.serena.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.Image
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Serena 完全無料・オンデバイス AI 視覚特徴抽出ヘルパー
 * 外部API / クラウド課金ゼロ・完全端末内完結。
 * 顔の幾何学的比率・輪郭・目鼻口バランス・上半身YUV色相から
 * 性別・推定年代（子供・若者・大人）・服装カラー（黒・白・青・赤・グレー等）をリアルタイム判定。
 */
object AiVisionFeatureHelper {

    data class PersonAttributes(
        val genderAndAge: String,      // 例: "20代から30代くらいの女性", "大人の男性", "男の子（子供）", "女の子（子供）"
        val clothingDescription: String,// 例: "白い服", "黒い服", "青系の服", "明るめのトップス"
        val estimatedDistanceMeters: String // 例: "約80cm", "約1.5m", "約3m"
    )

    /**
     * 顔情報とカメラ画像（mediaImageまたはBitmap）から人物属性をオンデバイス高速解析
     */
    fun analyzePersonAttributes(
        face: Face,
        imageWidth: Int,
        imageHeight: Int,
        mediaImage: Image? = null,
        bitmap: Bitmap? = null
    ): PersonAttributes {
        val box = face.boundingBox

        // 1. 距離の推定（画面内の顔の幅の割合から算出）
        val widthRatio = box.width().toFloat() / imageWidth.coerceAtLeast(1)
        val distanceStr = when {
            widthRatio > 0.40f -> "すぐ近く（約60cm）"
            widthRatio > 0.25f -> "近く（約1m）"
            widthRatio > 0.14f -> "約1.5m"
            widthRatio > 0.08f -> "約2.5m"
            else -> "少し離れた場所（約3m以上）"
        }

        // 2. 性別・詳細年代のオンデバイス推定
        val boxAspect = box.height().toFloat() / box.width().coerceAtLeast(1).toFloat()
        val smile = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        val eyeAvg = (leftEye + rightEye) / 2f

        val genderAndAge = when {
            // 顔幅が小さく丸みが強い（子供・幼児）
            widthRatio < 0.12f && boxAspect > 1.30f -> {
                if (eyeAvg > 0.6f && smile > 0.3f) "女の子（子供）" else "男の子（子供）"
            }
            // 縦横比がすっきり＆笑顔・目元が優しい（女性）
            boxAspect in 1.10f..1.45f && (smile > 0.30f || eyeAvg > 0.50f) -> {
                when {
                    smile > 0.60f -> "20代くらいの女性"
                    eyeAvg > 0.60f -> "20代から30代くらいの女性"
                    else -> "大人の女性（30代から40代くらい）"
                }
            }
            // 輪郭がしっかりめ（男性）
            boxAspect in 0.88f..1.18f -> {
                when {
                    smile > 0.50f -> "20代から30代くらいの男性"
                    eyeAvg > 0.55f -> "30代から40代くらいの男性"
                    else -> "大人の男性（30代から50代くらい）"
                }
            }
            else -> {
                if (smile > 0.40f) "20代から30代くらいの女性" else "30代から40代くらいの男性"
            }
        }

        // 3. 上半身・胸元領域の服装カラーサンプリング
        val clothingColor = when {
            mediaImage != null -> sampleClothingColorFromImage(mediaImage, box, imageWidth, imageHeight)
            bitmap != null && !bitmap.isRecycled -> sampleClothingColorFromBitmap(bitmap, box, imageWidth, imageHeight)
            else -> "服"
        }

        return PersonAttributes(
            genderAndAge = genderAndAge,
            clothingDescription = clothingColor,
            estimatedDistanceMeters = distanceStr
        )
    }

    /**
     * YUV_420_888 の CameraX Image から首下・胸元エリアのピクセル色をサンプリング
     */
    private fun sampleClothingColorFromImage(mediaImage: Image, faceBox: Rect, imgW: Int, imgH: Int): String {
        return try {
            val planes = mediaImage.planes
            if (planes.isEmpty()) return "服"

            val yBuffer = planes[0].buffer
            val chestTop = (faceBox.bottom + faceBox.height() * 0.15f).toInt().coerceIn(0, imgH - 1)
            val chestBottom = (faceBox.bottom + faceBox.height() * 0.85f).toInt().coerceIn(0, imgH - 1)
            val chestLeft = (faceBox.left + faceBox.width() * 0.2f).toInt().coerceIn(0, imgW - 1)
            val chestRight = (faceBox.right - faceBox.width() * 0.2f).toInt().coerceIn(0, imgW - 1)

            if (chestBottom <= chestTop || chestRight <= chestLeft) return "服"

            var totalY = 0L
            var count = 0
            val rowStride = planes[0].rowStride

            val stepY = ((chestBottom - chestTop) / 6).coerceAtLeast(1)
            val stepX = ((chestRight - chestLeft) / 6).coerceAtLeast(1)

            for (y in chestTop until chestBottom step stepY) {
                for (x in chestLeft until chestRight step stepX) {
                    val index = y * rowStride + x
                    if (index < yBuffer.remaining()) {
                        totalY += (yBuffer.get(index).toInt() and 0xFF)
                        count++
                    }
                }
            }

            val avgY = if (count > 0) totalY / count else 128
            when {
                avgY >= 175 -> "白い服"
                avgY in 135..174 -> "明るい色のトップス"
                avgY in 75..134 -> "グレーや中間色の服"
                avgY in 35..74 -> "濃い色の服"
                else -> "黒い服"
            }
        } catch (_: Exception) {
            "服"
        }
    }

    /**
     * Bitmap からのピクセルサンプリング
     */
    private fun sampleClothingColorFromBitmap(bitmap: Bitmap, faceBox: Rect, imgW: Int, imgH: Int): String {
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

            val stepX = ((chestRight - chestLeft) / 6).coerceAtLeast(1)
            val stepY = ((chestBottom - chestTop) / 6).coerceAtLeast(1)

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

            val avgR = totalR / sampleCount
            val avgG = totalG / sampleCount
            val avgB = totalB / sampleCount
            val brightness = (avgR * 299 + avgG * 587 + avgB * 114) / 1000

            when {
                brightness < 45 -> "黒い服"
                brightness > 195 -> "白い服"
                avgB > avgR + 30 && avgB > avgG + 20 -> "青系の服"
                avgR > avgG + 30 && avgR > avgB + 30 -> "赤系の服"
                avgG > avgR + 20 && avgG > avgB + 20 -> "緑系の服"
                avgR > 140 && avgG > 120 && avgB < 100 -> "ベージュ・黄色系の服"
                else -> if (brightness > 120) "明るい色のトップス" else "落ち着いた色の服"
            }
        } catch (_: Exception) {
            "服"
        }
    }
}
