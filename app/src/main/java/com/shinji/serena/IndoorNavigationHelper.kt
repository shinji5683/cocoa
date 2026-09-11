package com.shinji.serena

import android.content.Context
import android.util.Log

import androidx.annotation.StringRes

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

    enum class Direction(@StringRes val resId: Int) {
        FRONT(R.string.dir_front),
        FRONT_RIGHT(R.string.dir_front_right),
        FRONT_LEFT(R.string.dir_front_left),
        RIGHT(R.string.dir_right),
        LEFT(R.string.dir_left),
        FOOT(R.string.dir_foot),
        AHEAD(R.string.dir_ahead);

        fun getLabel(context: Context): String = context.getString(resId)
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
        val poseDescription: String = ""
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
            parts.add(context.getString(R.string.indoor_in_room_fmt, roomName))
        }

        // 人物（服装・年代・性別・表情・視線）の詳細実況
        for (p in people) {
            val distStr = if (p.distanceMeter <= 1.0f) "1m" else "${p.distanceMeter.toInt()}m"
            val clothingStr = if (p.clothingColor.isNotEmpty() && !p.clothingColor.equals("unknown", ignoreCase = true)) p.clothingColor else ""
            val personLabel = if (p.genderAndAge.isNotEmpty()) p.genderAndAge else (if (p.poseDescription.isNotEmpty()) p.poseDescription else context.getString(R.string.person_generic))
            val lookingStr = if (p.isLookingAtCamera) "looking" else ""
            val exprDesc = listOf(p.expression, lookingStr).filter { it.isNotEmpty() }.joinToString(", ")

            parts.add(context.getString(R.string.eyes_person_desc_fmt, p.direction.getLabel(context), distStr, clothingStr, personLabel, exprDesc))
        }

        // 重要家具・扉・足元障害物の実況
        for (obj in objects.take(3)) {
            val distStr = if (obj.direction == Direction.FOOT) "" else "${obj.distanceMeter.toInt()}m"
            val detailStr = if (obj.detail.isNotEmpty()) " (${obj.detail})" else ""
            parts.add("${obj.direction.getLabel(context)}: ${distStr} ${obj.name}${detailStr}")
        }

        // 通路クリアランス判定
        if (isPathClear && objects.none { it.direction == Direction.FOOT || (it.direction == Direction.FRONT && it.distanceMeter < 1.0f) }) {
            parts.add(context.getString(R.string.eyes_front_clear))
        }

        return if (parts.isNotEmpty()) parts.joinToString("。") else context.getString(R.string.face_summary_none)
    }

    /**
     * 検出ラベルから家具・屋内設備名への変換
     */
    fun translateIndoorLabel(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.contains("door") -> context.getString(R.string.eyes_obj_door)
            lower.contains("table") || lower.contains("desk") -> context.getString(R.string.eyes_obj_table)
            lower.contains("chair") || lower.contains("seat") -> context.getString(R.string.eyes_obj_chair)
            lower.contains("sofa") || lower.contains("couch") -> context.getString(R.string.eyes_obj_sofa)
            lower.contains("cup") || lower.contains("bottle") -> context.getString(R.string.eyes_obj_cup)
            lower.contains("laptop") || lower.contains("computer") -> context.getString(R.string.eyes_obj_laptop)
            lower.contains("shoe") || lower.contains("footwear") || lower.contains("slipper") -> context.getString(R.string.eyes_obj_shoe)
            lower.contains("plant") -> context.getString(R.string.eyes_obj_plant)
            lower.contains("bag") || lower.contains("backpack") -> context.getString(R.string.eyes_obj_bag)
            lower.contains("phone") -> context.getString(R.string.eyes_obj_phone)
            lower.contains("book") -> context.getString(R.string.eyes_obj_book)
            else -> label
        }
    }
}
