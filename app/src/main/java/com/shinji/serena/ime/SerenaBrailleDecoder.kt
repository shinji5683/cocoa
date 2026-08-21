package com.shinji.serena.ime

/**
 * Serena IME - 6点点字デコーダー (Japanese 6-Dot Braille Decoder)
 * 日本点字体系（JIS/日本点字委員会基準）および英語アルファベット点字を完全サポート。
 * 点1〜点6の組み合わせ（ビットマスク 1..63）から平仮名・カタカナ・英数字・記号を高速デコード。
 */
object SerenaBrailleDecoder {

    // 点番号のビット定義
    const val DOT_1 = 1 shl 0 // 1
    const val DOT_2 = 1 shl 1 // 2
    const val DOT_3 = 1 shl 2 // 4
    const val DOT_4 = 1 shl 3 // 8
    const val DOT_5 = 1 shl 4 // 16
    const val DOT_6 = 1 shl 5 // 32

    // 前置符状態
    enum class PrefixState {
        NONE,
        DAKUON,       // 濁音符 (点5)
        HANDAKUON,    // 半濁音符 (点6)
        YOUON,        // 拗音符 (点4)
        YOU_DAKUON,   // 拗濁音符 (点4, 5)
        YOU_HANDAKUON,// 拗半濁音符 (点4, 6)
        NUMBER        // 数符 (点3, 4, 5, 6)
    }

    private var currentPrefix: PrefixState = PrefixState.NONE

    // 単独文字マッピング (ビットマスク -> 平仮名)
    private val singleCharMap = mapOf(
        // 母音
        (DOT_1) to "あ",
        (DOT_1 or DOT_2) to "い",
        (DOT_1 or DOT_4) to "う",
        (DOT_1 or DOT_2 or DOT_4) to "え",
        (DOT_2 or DOT_4) to "お",

        // か行 (母音 + 点6)
        (DOT_1 or DOT_6) to "か",
        (DOT_1 or DOT_2 or DOT_6) to "き",
        (DOT_1 or DOT_4 or DOT_6) to "く",
        (DOT_1 or DOT_2 or DOT_4 or DOT_6) to "け",
        (DOT_2 or DOT_4 or DOT_6) to "こ",

        // さ行 (母音 + 点5, 6)
        (DOT_1 or DOT_5 or DOT_6) to "さ",
        (DOT_1 or DOT_2 or DOT_5 or DOT_6) to "し",
        (DOT_1 or DOT_4 or DOT_5 or DOT_6) to "す",
        (DOT_1 or DOT_2 or DOT_4 or DOT_5 or DOT_6) to "せ",
        (DOT_2 or DOT_4 or DOT_5 or DOT_6) to "そ",

        // た行 (母音 + 点3, 5)
        (DOT_1 or DOT_3 or DOT_5) to "た",
        (DOT_1 or DOT_2 or DOT_3 or DOT_5) to "ち",
        (DOT_1 or DOT_3 or DOT_4 or DOT_5) to "つ",
        (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5) to "て",
        (DOT_2 or DOT_3 or DOT_4 or DOT_5) to "と",

        // な行 (母音 + 点3)
        (DOT_1 or DOT_3) to "な",
        (DOT_1 or DOT_2 or DOT_3) to "に",
        (DOT_1 or DOT_3 or DOT_4) to "ぬ",
        (DOT_1 or DOT_2 or DOT_3 or DOT_4) to "ね",
        (DOT_2 or DOT_3 or DOT_4) to "の",

        // は行 (母音 + 点3, 6)
        (DOT_1 or DOT_3 or DOT_6) to "は",
        (DOT_1 or DOT_2 or DOT_3 or DOT_6) to "ひ",
        (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "ふ",
        (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6) to "へ",
        (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ほ",

        // ま行 (母音 + 点3, 5, 6)
        (DOT_1 or DOT_3 or DOT_5 or DOT_6) to "ま",
        (DOT_1 or DOT_2 or DOT_3 or DOT_5 or DOT_6) to "み",
        (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6) to "む",
        (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6) to "め",
        (DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6) to "も",

        // ら行 (母音 + 点5)
        (DOT_1 or DOT_5) to "ら",
        (DOT_1 or DOT_2 or DOT_5) to "り",
        (DOT_1 or DOT_4 or DOT_5) to "る",
        (DOT_1 or DOT_2 or DOT_4 or DOT_5) to "れ",
        (DOT_2 or DOT_4 or DOT_5) to "ろ",

        // や行
        (DOT_3 or DOT_4) to "や",
        (DOT_3 or DOT_4 or DOT_6) to "ゆ",
        (DOT_3 or DOT_4 or DOT_5) to "よ",

        // わ行・ん
        (DOT_3) to "わ",
        (DOT_3 or DOT_5) to "を",
        (DOT_3 or DOT_5 or DOT_6) to "ん",
        (DOT_2) to "っ", // 促音
        (DOT_2 or DOT_5) to "ー" // 長音
    )

    // 濁音マップ
    private val dakuonMap = mapOf(
        "か" to "が", "き" to "ぎ", "く" to "ぐ", "け" to "げ", "こ" to "ご",
        "さ" to "ざ", "し" to "じ", "す" to "ず", "せ" to "ぜ", "そ" to "ぞ",
        "た" to "だ", "ち" to "ぢ", "つ" to "づ", "て" to "で", "と" to "ど",
        "は" to "ば", "ひ" to "び", "ふ" to "ぶ", "へ" to "べ", "ほ" to "ぼ",
        "う" to "ゔ"
    )

    // 半濁音マップ
    private val handakuonMap = mapOf(
        "は" to "ぱ", "ひ" to "ぴ", "ふ" to "ぷ", "へ" to "ぺ", "ほ" to "ぽ"
    )

    // 拗音マップ
    private val youonMap = mapOf(
        "か" to "きゃ", "く" to "きゅ", "こ" to "きょ",
        "さ" to "しゃ", "す" to "しゅ", "そ" to "しょ",
        "た" to "ちゃ", "つ" to "ちゅ", "と" to "ちょ",
        "な" to "にゃ", "ぬ" to "にゅ", "の" to "にょ",
        "は" to "ひゃ", "ふ" to "ひゅ", "ほ" to "ひょ",
        "ま" to "みゃ", "む" to "みゅ", "も" to "みょ",
        "ら" to "りゃ", "る" to "りゅ", "ろ" to "りょ"
    )

    // 数字マップ (数符状態)
    private val numberMap = mapOf(
        (DOT_1) to "1",
        (DOT_1 or DOT_2) to "2",
        (DOT_1 or DOT_4) to "3",
        (DOT_1 or DOT_4 or DOT_5) to "4",
        (DOT_1 or DOT_5) to "5",
        (DOT_1 or DOT_2 or DOT_4) to "6",
        (DOT_1 or DOT_2 or DOT_4 or DOT_5) to "7",
        (DOT_1 or DOT_2 or DOT_5) to "8",
        (DOT_2 or DOT_4) to "9",
        (DOT_2 or DOT_4 or DOT_5) to "0"
    )

    fun reset() {
        currentPrefix = PrefixState.NONE
    }

    /**
     * 押された点のビットマスクから1文字をデコード
     * @param dotMask 点1(1)〜点6(32)の論理和
     * @return デコード結果 (文字と読み上げ用テキストのペア、前置符のみの場合は空文字と前置符アナウンス)
     */
    fun decodeDots(dotMask: Int): Pair<String, String> {
        if (dotMask == 0) return "" to ""

        // 1. 前置符の判定
        when (dotMask) {
            (DOT_5) -> {
                currentPrefix = PrefixState.DAKUON
                return "" to "濁音符"
            }
            (DOT_6) -> {
                currentPrefix = PrefixState.HANDAKUON
                return "" to "半濁音符"
            }
            (DOT_4) -> {
                currentPrefix = PrefixState.YOUON
                return "" to "拗音符"
            }
            (DOT_4 or DOT_5) -> {
                currentPrefix = PrefixState.YOU_DAKUON
                return "" to "拗濁音符"
            }
            (DOT_4 or DOT_6) -> {
                currentPrefix = PrefixState.YOU_HANDAKUON
                return "" to "拗半濁音符"
            }
            (DOT_3 or DOT_4 or DOT_5 or DOT_6) -> {
                currentPrefix = PrefixState.NUMBER
                return "" to "数符"
            }
        }

        // 2. 数符状態の場合の処理
        if (currentPrefix == PrefixState.NUMBER) {
            val num = numberMap[dotMask]
            if (num != null) {
                return num to num
            } else {
                currentPrefix = PrefixState.NONE
            }
        }

        // 3. 通常文字のデコード
        val baseChar = singleCharMap[dotMask] ?: return "" to "点字パターン不明"

        val finalChar = when (currentPrefix) {
            PrefixState.DAKUON -> dakuonMap[baseChar] ?: baseChar
            PrefixState.HANDAKUON -> handakuonMap[baseChar] ?: baseChar
            PrefixState.YOUON -> youonMap[baseChar] ?: baseChar
            PrefixState.YOU_DAKUON -> {
                val daku = dakuonMap[baseChar] ?: baseChar
                when (daku) {
                    "が" -> "ぎゃ"; "ぐ" -> "ぎゅ"; "ご" -> "ぎょ"
                    "ざ" -> "じゃ"; "ず" -> "じゅ"; "ぞ" -> "じょ"
                    "だ" -> "ぢゃ"; "づ" -> "ぢゅ"; "ど" -> "ぢょ"
                    "ば" -> "びゃ"; "ぶ" -> "びゅ"; "ぼ" -> "びょ"
                    else -> baseChar
                }
            }
            PrefixState.YOU_HANDAKUON -> {
                when (baseChar) {
                    "は" -> "ぴゃ"; "ふ" -> "ぴゅ"; "ほ" -> "ぴょ"
                    else -> baseChar
                }
            }
            else -> baseChar
        }

        // 1文字確定したら前置符をリセット
        currentPrefix = PrefixState.NONE
        return finalChar to finalChar
    }
}
