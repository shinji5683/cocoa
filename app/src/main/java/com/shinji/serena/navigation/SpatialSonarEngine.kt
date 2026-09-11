package com.shinji.serena.navigation

import android.content.Context
import android.graphics.RectF
import com.shinji.serena.R
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
        val centerX = boundingBox.centerX() // 0.0 (左) 〜 0.5 (中央) 〜 1.0 (右)
        val bottomY = boundingBox.bottom

        // 左右パン計算: -1.0f (左端) 〜 0.0f (中央) 〜 +1.0f (右端)
        val pan = ((centerX - 0.5f) * 2.0f).coerceIn(-1.0f, 1.0f)

        // 相対方向判定（時計盤表現は完全禁止！）
        val direction = when {
            centerX in 0.40f..0.60f -> context.getString(R.string.dir_front)
            centerX in 0.60f..0.85f -> context.getString(R.string.dir_front_right)
            centerX > 0.85f -> context.getString(R.string.dir_right)
            centerX in 0.15f..0.40f -> context.getString(R.string.dir_front_left)
            else -> context.getString(R.string.dir_left)
        }

        // 物体種別の分類
        val lower = rawLabel.lowercase()
        val type = when {
            lower.contains("door") || lower.contains("entrance") || lower.contains("exit") -> TargetType.DOOR
            lower.contains("stair") || lower.contains("step") || lower.contains("curb") -> TargetType.STEP_DOWN
            lower.contains("person") || lower.contains("man") || lower.contains("woman") -> TargetType.PERSON
            else -> TargetType.OBSTACLE
        }

        // 各言語対応の物体名
        val targetName = when (type) {
            TargetType.DOOR -> context.getString(R.string.sonar_target_door)
            TargetType.STEP_DOWN -> context.getString(R.string.sonar_target_step)
            TargetType.PERSON -> context.getString(R.string.sonar_target_person)
            TargetType.OBSTACLE -> when {
                lower.contains("chair") -> context.getString(R.string.sonar_target_chair)
                lower.contains("table") || lower.contains("desk") -> context.getString(R.string.sonar_target_table)
                lower.contains("pole") || lower.contains("pillar") -> context.getString(R.string.sonar_target_pillar)
                else -> context.getString(R.string.sonar_target_obstacle)
            }
        }

        // 距離の言語化
        val distText = if (estimatedDistanceMeters < 1.0f) {
            val cm = (estimatedDistanceMeters * 100).toInt()
            context.getString(R.string.sonar_dist_cm_ahead, cm)
        } else {
            val m = String.format(Locale.US, "%.1f", estimatedDistanceMeters)
            context.getString(R.string.sonar_dist_m_ahead, m)
        }

        val guideMessage = context.getString(R.string.sonar_guide_fmt, direction, distText, targetName)

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
