package com.shinji.serena.navigation

import android.content.Context
import android.graphics.RectF
import com.shinji.serena.SoundAndHapticHelper
import java.util.Locale

/**
 * SpatialSonarEngine (空間障害物＆ドア・段差ソナーエンジン)
 *
 * 全盲ユーザーの安全な歩行・空間把握のため、
 * カメラ画像やセンサーから検知された障害物・ドア・段差までの距離と位置を解析し、
 * 立体音響ソナーパルス（近接するほど高頻度化）と直感的な相対方向表現で伝達する。
 *
 * 【鉄則】
 * - 時計盤表現（12時の方向、3時の方向）は完全排除！
 * - 相対方向（正面、右斜め前、左斜め前、右、左、足元）のみを使用。
 */
class SpatialSonarEngine(
    private val context: Context,
    private val soundHelper: SoundAndHapticHelper?
) {

    private val isJapanese: Boolean
        get() = Locale.getDefault().language.lowercase() == "ja"

    enum class TargetType {
        DOOR,       // ドア・出入口・自動ドア
        STEP_DOWN,  // 下り段差・階段
        OBSTACLE,   // 一般障害物（柱、看板、机、椅子等）
        PERSON      // 人物
    }

    data class SonarDetection(
        val type: TargetType,
        val label: String,
        val distanceMeters: Float,
        val directionText: String,
        val stereoPan: Float, // -1.0f (左) 〜 0.0f (正面) 〜 +1.0f (右)
        val guideMessage: String
    )

    /**
     * カメラ視野角と物体バウンディングボックスからソナー検知情報を生成
     * @param boundingBox 正規化座標 (0.0f .. 1.0f)
     * @param rawLabel ML Kit または AI が検出したラベル (例: "door", "stairs", "chair", "person")
     */
    fun processDetectedTarget(
        boundingBox: RectF,
        rawLabel: String,
        estimatedDistanceMeters: Float
    ): SonarDetection {
        val ja = isJapanese
        val centerX = boundingBox.centerX() // 0.0 (左) 〜 0.5 (中央) 〜 1.0 (右)
        val bottomY = boundingBox.bottom

        // 左右パン計算: -1.0f (左端) 〜 0.0f (中央) 〜 +1.0f (右端)
        val pan = ((centerX - 0.5f) * 2.0f).coerceIn(-1.0f, 1.0f)

        // 相対方向判定（時計盤表現は完全禁止！）
        val direction = when {
            centerX in 0.40f..0.60f -> if (ja) "正面" else "Straight ahead"
            centerX in 0.60f..0.85f -> if (ja) "右斜め前" else "Front-right"
            centerX > 0.85f -> if (ja) "右" else "Right"
            centerX in 0.15f..0.40f -> if (ja) "左斜め前" else "Front-left"
            else -> if (ja) "左" else "Left"
        }

        // 物体種別の分類
        val lower = rawLabel.lowercase()
        val type = when {
            lower.contains("door") || lower.contains("entrance") || lower.contains("exit") -> TargetType.DOOR
            lower.contains("stair") || lower.contains("step") || lower.contains("curb") -> TargetType.STEP_DOWN
            lower.contains("person") || lower.contains("man") || lower.contains("woman") -> TargetType.PERSON
            else -> TargetType.OBSTACLE
        }

        // 日本語・英語の物体名
        val targetName = when (type) {
            TargetType.DOOR -> if (ja) "ドア" else "door"
            TargetType.STEP_DOWN -> if (ja) "段差" else "step"
            TargetType.PERSON -> if (ja) "人" else "person"
            TargetType.OBSTACLE -> when {
                lower.contains("chair") -> if (ja) "椅子" else "chair"
                lower.contains("table") || lower.contains("desk") -> if (ja) "机" else "table"
                lower.contains("pole") || lower.contains("pillar") -> if (ja) "柱" else "pillar"
                else -> if (ja) "障害物" else "obstacle"
            }
        }

        // 距離の言語化
        val distText = if (estimatedDistanceMeters < 1.0f) {
            val cm = (estimatedDistanceMeters * 100).toInt()
            if (ja) "${cm}センチ先" else "$cm cm ahead"
        } else {
            val m = String.format(Locale.US, "%.1f", estimatedDistanceMeters)
            if (ja) "${m}メートル先" else "$m meters ahead"
        }

        val guideMessage = if (ja) {
            "$direction $distText に $targetName"
        } else {
            "$targetName $direction, $distText"
        }

        // ソナーパルス音の鳴動
        soundHelper?.playSonarPulse(estimatedDistanceMeters, pan)

        return SonarDetection(
            type = type,
            label = targetName,
            distanceMeters = estimatedDistanceMeters,
            directionText = direction,
            stereoPan = pan,
            guideMessage = guideMessage
        )
    }
}
