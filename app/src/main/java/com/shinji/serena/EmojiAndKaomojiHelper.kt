package com.shinji.serena

class EmojiAndKaomojiHelper {

    companion object {
        private val EMOJI_MAP = mapOf(
            "🌸" to "桜",
            "🦯" to "白杖",
            "🪷" to "ハス",
            "🧭" to "コンパス",
            "💡" to "ひらめき",
            "👥" to "人影",
            "🚶‍♂️" to "徒歩ナビ",
            "📷" to "カメラ",
            "👤" to "人物",
            "📦" to "物体",
            "🌐" to "ワールド",
            "⚡" to "速度",
            "📄" to "ページ",
            "📖" to "読書",
            "🐛" to "レポート",
            "⚙️" to "設定",
            "❓" to "ヘルプ",
            "🗑️" to "削除",
            "ℹ️" to "情報",
            "💬" to "メッセージ",
            "✂️" to "切り取り",
            "🧹" to "消去",
            "🔊" to "音声",
            "😊" to "笑顔",
            "😃" to "大笑い",
            "😄" to "にっこり顔",
            "😁" to "歯を見せて笑う顔",
            "😆" to "目を細めて笑う顔",
            "😅" to "汗をかいた笑顔",
            "😂" to "嬉し泣き顔",
            "🤣" to "大爆笑",
            "😌" to "ホッとした顔",
            "😍" to "目がハートの笑顔",
            "🥰" to "愛に満ちた笑顔",
            "小" to "",
            "😘" to "投げキス顔",
            "😋" to "舌を出した顔",
            "😎" to "サングラス顔",
            "😭" to "号泣顔",
            "😢" to "泣き顔",
            "🥺" to "うるうる顔",
            "😱" to "驚き叫ぶ顔",
            "😡" to "怒り顔",
            "😠" to "おこり顔",
            "🤔" to "考える顔",
            "😴" to "居眠り顔",
            "👍" to "親指を立てたイイネ",
            "👎" to "バッド手サイン",
            "👏" to "拍手",
            "🙌" to "両手をあげるバンザイ",
            "🙏" to "お願い・感謝の手",
            "🎉" to "クラッカー",
            "☕" to "温かいコーヒー",
            "❤️" to "赤いハート",
            "💕" to "ふたつのハート",
            "✨" to "キラキラ",
            "🌟" to "輝く星",
            "📱" to "スマートフォン",
            "📞" to "電話",
            "✉️" to "手紙・メール",
            "☀️" to "太陽",
            "🌑" to "黒い画面・月"
        )

        private val KAOMOJI_PATTERNS = listOf(
            Regex("[(（][*＊]?´[ωω]｀[*＊]?[)）]") to "ほほえみ顔文字",
            Regex("[(（]T[__]?T[)）]") to "泣き顔文字",
            Regex("[(（]\\^[oO0]\\^[)）]") to "大笑い顔文字",
            Regex("[(（];[;]?Wait[)）]") to "汗顔文字",
            Regex("[(（]>_<[)）]") to "困り顔文字",
            Regex("[(（]・∀・[)）]") to "にっこり顔文字",
            Regex("[(（]´;ω;`[)）]") to "うるうる泣き顔文字",
            Regex("[(（]m[_ ]_m[)）]") to "ぺこりお辞儀顔文字"
        )
    }

    fun translateEmojiAndKaomoji(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // 1. 絵文字の置換
        for ((emoji, desc) in EMOJI_MAP) {
            if (desc.isNotEmpty() && result.contains(emoji)) {
                result = result.replace(emoji, " [絵文字: $desc] ")
            }
        }

        // 2. 顔文字の置換
        for ((pattern, desc) in KAOMOJI_PATTERNS) {
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, " [顔文字: $desc] ")
            }
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }
}


