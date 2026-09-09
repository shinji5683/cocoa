package com.shinji.serena

import java.util.Locale

class EmojiAndKaomojiHelper {

    companion object {
        private val EMOJI_MAP_JA = mapOf(
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
            "🌑" to "黒い画面・月",
            "🚀" to "ロケット",
            "✏️" to "編集",
            "⏰" to "時報・時計",
            "🗺️" to "地図",
            "👁️" to "AIビジョン"
        )

        private val EMOJI_MAP_EN = mapOf(
            "🌸" to "cherry blossom",
            "🦯" to "white cane",
            "🪷" to "lotus",
            "🧭" to "compass",
            "💡" to "light bulb",
            "👥" to "people",
            "🚶‍♂️" to "walking navigation",
            "📷" to "camera",
            "👤" to "profile",
            "📦" to "package",
            "🌐" to "globe",
            "⚡" to "speed",
            "📄" to "page",
            "📖" to "reading",
            "🐛" to "report bug",
            "⚙️" to "settings",
            "❓" to "help",
            "🗑️" to "delete",
            "ℹ️" to "info",
            "💬" to "message",
            "✂️" to "cut",
            "🧹" to "clear",
            "🔊" to "audio",
            "😊" to "smiling face",
            "😃" to "grinning face",
            "😄" to "happy face",
            "😁" to "beaming face",
            "😆" to "laughing face",
            "😅" to "sweat smile",
            "😂" to "tears of joy",
            "🤣" to "rolling laughing",
            "😌" to "relieved face",
            "😍" to "heart eyes",
            "🥰" to "smiling face with hearts",
            "😘" to "blowing kiss",
            "😋" to "delicious face",
            "😎" to "sunglasses face",
            "😭" to "loudly crying face",
            "😢" to "crying face",
            "🥺" to "pleading face",
            "😱" to "screaming face",
            "😡" to "pouting face",
            "😠" to "angry face",
            "🤔" to "thinking face",
            "😴" to "sleeping face",
            "👍" to "thumbs up",
            "👎" to "thumbs down",
            "👏" to "clapping hands",
            "🙌" to "raising hands",
            "🙏" to "folded hands",
            "🎉" to "party popper",
            "☕" to "coffee",
            "❤️" to "red heart",
            "💕" to "two hearts",
            "✨" to "sparkles",
            "🌟" to "glowing star",
            "📱" to "mobile phone",
            "📞" to "phone call",
            "✉️" to "envelope",
            "☀️" to "sun",
            "🌑" to "dark screen",
            "🚀" to "rocket",
            "✏️" to "edit",
            "⏰" to "alarm clock",
            "🗺️" to "map",
            "👁️" to "vision AI"
        )

        private val KAOMOJI_PATTERNS_JA = listOf(
            Regex("[(（][*＊]?´[ωω]｀[*＊]?[)）]") to "ほほえみ顔文字",
            Regex("[(（]T[__]?T[)）]") to "泣き顔文字",
            Regex("[(（]\\^[oO0]\\^[)）]") to "大笑い顔文字",
            Regex("[(（];[;]?Wait[)）]") to "汗顔文字",
            Regex("[(（]>_<[)）]") to "困り顔文字",
            Regex("[(（]・∀・[)）]") to "にっこり顔文字",
            Regex("[(（]´;ω;`[)）]") to "うるうる泣き顔文字",
            Regex("[(（]m[_ ]_m[)）]") to "ぺこりお辞儀顔文字"
        )

        private val KAOMOJI_PATTERNS_EN = listOf(
            Regex("[(（][*＊]?´[ωω]｀[*＊]?[)）]") to "smile emoticon",
            Regex("[(（]T[__]?T[)）]") to "crying emoticon",
            Regex("[(（]\\^[oO0]\\^[)）]") to "laughing emoticon",
            Regex("[(（];[;]?Wait[)）]") to "sweat emoticon",
            Regex("[(（]>_<[)）]") to "troubled emoticon",
            Regex("[(（]・∀・[)）]") to "happy emoticon",
            Regex("[(（]´;ω;`[)）]") to "sniffling emoticon",
            Regex("[(（]m[_ ]_m[)）]") to "bowing emoticon"
        )
    }

    fun translateEmojiAndKaomoji(text: String, locale: Locale = Locale.getDefault()): String {
        if (text.isBlank()) return text

        val isJapanese = locale.language.lowercase() == "ja"
        val emojiMap = if (isJapanese) EMOJI_MAP_JA else EMOJI_MAP_EN
        val kaomojiList = if (isJapanese) KAOMOJI_PATTERNS_JA else KAOMOJI_PATTERNS_EN
        val emojiTag = if (isJapanese) "絵文字" else "emoji"
        val kaomojiTag = if (isJapanese) "顔文字" else "emoticon"

        var result = text

        // 1. 絵文字の置換
        for ((emoji, desc) in emojiMap) {
            if (desc.isNotEmpty() && result.contains(emoji)) {
                result = result.replace(emoji, " [$emojiTag: $desc] ")
            }
        }

        // 2. 顔文字の置換
        for ((pattern, desc) in kaomojiList) {
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, " [$kaomojiTag: $desc] ")
            }
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }
}
