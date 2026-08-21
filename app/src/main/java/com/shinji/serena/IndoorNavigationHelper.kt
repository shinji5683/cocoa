package com.shinji.serena

import android.content.Context
import android.util.Log

/**
 * Serena Indoor Navigation & Spatial Vision Helper
 * 屋内インドア空間ナビゲーション ＆ リアルタイム実況エンジン (TensorFlow Lite & ML Kit Vision)
 * 日常の直感的な方向表現（正面・右斜め前・左斜め前・右側・左側・足元・前方）で
 * 家具・扉・人物の服装・年代・性別・表情・足元クリアランスを音声実況します。
 */
class IndoorNavigationHelper(private val context: Context) {

    companion object {
        private const val TAG = "IndoorNavigationHelper"
    }

    enum class Direction(val label: String) {
        FRONT("正面"),
        FRONT_RIGHT("右斜め前"),
        FRONT_LEFT("左斜め前"),
        RIGHT("右側"),
        LEFT("左側"),
        FOOT("足元"),
        AHEAD("前方")
    }

    data class IndoorObject(
        val name: String,
        val direction: Direction,
        val distanceMeter: Float,
        val detail: String = ""
    )

    data class PersonState(
        val direction: Direction,
        val distanceMeter: Float,
        val genderAndAge: String,
        val clothingColor: String,
        val expression: String,
        val isLookingAtCamera: Boolean,
        val poseDescription: String = "人"
    )

    /**
     * 画面座標 (0.0〜1.0) とサイズから方向と距離を推定
     */
    fun calculateDirectionAndDistance(
        centerX: Float,
        centerY: Float,
        boxWidth: Float,
        boxHeight: Float
    ): Pair<Direction, Float> {
        val direction = when {
            centerY >= 0.72f -> Direction.FOOT
            centerX in 0.38f..0.62f -> Direction.FRONT
            centerX > 0.62f && centerY in 0.20f..0.72f -> Direction.FRONT_RIGHT
            centerX < 0.38f && centerY in 0.20f..0.72f -> Direction.FRONT_LEFT
            centerX > 0.62f -> Direction.RIGHT
            else -> Direction.LEFT
        }

        // バウンディングボックスの面積比率からおおよその距離(0.5m〜4.0m)を逆算推定
        val area = (boxWidth * boxHeight).coerceIn(0.01f, 1.0f)
        val distance = when {
            area > 0.45f -> 0.6f
            area > 0.25f -> 1.0f
            area > 0.12f -> 1.8f
            area > 0.05f -> 2.5f
            else -> 3.5f
        }

        return Pair(direction, distance)
    }

    /**
     * 家具やオブジェクト・人物（服装・年代・表情）の屋内実況文を生成
     */
    fun buildIndoorAnnouncement(
        roomName: String = "",
        objects: List<IndoorObject>,
        people: List<PersonState>,
        isPathClear: Boolean
    ): String {
        val parts = mutableListOf<String>()

        if (roomName.isNotEmpty()) {
            parts.add("${roomName}にいます")
        }

        // 人物（服装・年代・性別・表情・視線）の詳細実況
        for (p in people) {
            val distStr = if (p.distanceMeter <= 1.0f) "1メートル付近" else "${p.distanceMeter.toInt()}メートル先"
            val clothingStr = if (p.clothingColor.isNotEmpty() && !p.clothingColor.contains("不明")) "${p.clothingColor}を着た" else ""
            val personLabel = if (p.genderAndAge.isNotEmpty()) p.genderAndAge else p.poseDescription
            val lookingStr = if (p.isLookingAtCamera) "こちらを見ています" else ""
            val exprDesc = listOf(p.expression, lookingStr).filter { it.isNotEmpty() }.joinToString("で")

            parts.add("${p.direction.label} ${distStr}に ${clothingStr}${personLabel}がいます。${exprDesc}")
        }

        // 重要家具・扉・足元障害物の実況
        for (obj in objects.take(3)) {
            val distStr = if (obj.direction == Direction.FOOT) "" else "${obj.distanceMeter.toInt()}メートル先に"
            val detailStr = if (obj.detail.isNotEmpty()) "（${obj.detail}）" else ""
            parts.add("${obj.direction.label}に ${distStr}${obj.name}${detailStr}があります")
        }

        // 通路クリアランス判定
        if (isPathClear && objects.none { it.direction == Direction.FOOT || (it.direction == Direction.FRONT && it.distanceMeter < 1.0f) }) {
            parts.add("前方クリアです。まっすぐ進めます")
        }

        return if (parts.isNotEmpty()) parts.joinToString("。") else "周囲を確認中…ゆっくり周囲を映してください。"
    }

    /**
     * 検出ラベルから家具・屋内設備名への変換
     */
    fun translateIndoorLabel(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.contains("door") -> "ドア"
            lower.contains("stairs") || lower.contains("staircase") -> "階段"
            lower.contains("table") || lower.contains("desk") -> "机"
            lower.contains("chair") || lower.contains("seat") -> "椅子"
            lower.contains("sofa") || lower.contains("couch") -> "ソファ"
            lower.contains("bed") -> "ベッド"
            lower.contains("refrigerator") || lower.contains("fridge") -> "冷蔵庫"
            lower.contains("sink") -> "洗面台・シンク"
            lower.contains("toilet") -> "トイレ"
            lower.contains("slipper") || lower.contains("shoe") || lower.contains("footwear") -> "スリッパ・靴"
            lower.contains("cup") || lower.contains("bottle") -> "コップ・飲み物"
            lower.contains("laptop") || lower.contains("computer") -> "パソコン"
            lower.contains("television") || lower.contains("tv") || lower.contains("screen") -> "テレビ"
            lower.contains("box") || lower.contains("trash") -> "ゴミ箱・箱"
            lower.contains("shelf") || lower.contains("cabinet") -> "棚・キャビネット"
            lower.contains("fashion goods") || lower.contains("clothing") -> "洋服"
            lower.contains("plant") -> "観葉植物"
            else -> label
        }
    }
}
