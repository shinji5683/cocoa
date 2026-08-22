package com.shinji.serena.braille

/**
 * JapaneseBrailleTranslator
 *
 * 日本語点字（JBLC 日本点字委員会基準）の完全双方向エンジン。
 *
 * 1. 【点訳 (Ten-yaku)】: 墨字（漢字かな混じり文・ひらがな・英数字・記号） -> 点字セルバイト列 / Unicode点字文字列
 * 2. 【墨訳 (Sumi-yaku)】: 点字セルバイト列 / Unicode点字文字列 -> ひらがな・カナ・英数字・漢字かな混じりテキスト
 *
 * 濁音符(点5)、半濁音符(点6)、拗音符(点4)、拗濁音符(点4,5)、拗半濁音符(点4,6)、
 * 数符(点3,4,5,6)、外字符(点5,6)、促音(点2)、長音(点2,5)、句読点、マスあけに完全対応。
 */
object JapaneseBrailleTranslator {

    // 点番号のビット定義（1〜8の点）
    const val DOT_1 = 1 shl 0 // 0x01
    const val DOT_2 = 1 shl 1 // 0x02
    const val DOT_3 = 1 shl 2 // 0x04
    const val DOT_4 = 1 shl 3 // 0x08
    const val DOT_5 = 1 shl 4 // 0x10
    const val DOT_6 = 1 shl 5 // 0x20
    const val DOT_7 = 1 shl 6 // 0x40 (8点点字用)
    const val DOT_8 = 1 shl 7 // 0x80 (8点点字用)

    // 前置符
    const val PREFIX_DAKUTEN = DOT_5                     // 濁音符 (点5)
    const val PREFIX_HANDAKUTEN = DOT_6                  // 半濁音符 (点6)
    const val PREFIX_YOUON = DOT_4                       // 拗音符 (点4)
    const val PREFIX_YOU_DAKUTEN = DOT_4 or DOT_5        // 拗濁音符 (点4,5)
    const val PREFIX_YOU_HANDAKUTEN = DOT_4 or DOT_6     // 拗半濁音符 (点4,6)
    const val PREFIX_NUMBER = DOT_3 or DOT_4 or DOT_5 or DOT_6 // 数符 (点3,4,5,6)
    const val PREFIX_GAIJI = DOT_5 or DOT_6             // 外字符 / アルファベット符 (点5,6)
    const val SOKUON = DOT_2                             // 促音「っ」 (点2)
    const val CHOON = DOT_2 or DOT_5                     // 長音「ー」 (点2,5)
    const val SPACE_CELL = 0x00                          // 空白 (マスあけ)

    // 清音五十音 (ひらがな -> ドット)
    private val KANA_TO_DOT = mapOf(
        'あ' to DOT_1, 'い' to (DOT_1 or DOT_2), 'う' to (DOT_1 or DOT_4), 'え' to (DOT_1 or DOT_2 or DOT_4), 'お' to (DOT_2 or DOT_4),
        'か' to (DOT_1 or DOT_6), 'き' to (DOT_1 or DOT_2 or DOT_6), 'く' to (DOT_1 or DOT_4 or DOT_6), 'け' to (DOT_1 or DOT_2 or DOT_4 or DOT_6), 'こ' to (DOT_2 or DOT_4 or DOT_6),
        'さ' to (DOT_1 or DOT_5 or DOT_6), 'し' to (DOT_1 or DOT_2 or DOT_5 or DOT_6), 'す' to (DOT_1 or DOT_4 or DOT_5 or DOT_6), 'せ' to (DOT_1 or DOT_2 or DOT_4 or DOT_5 or DOT_6), 'そ' to (DOT_2 or DOT_4 or DOT_5 or DOT_6),
        'た' to (DOT_1 or DOT_3 or DOT_5), 'ち' to (DOT_1 or DOT_2 or DOT_3 or DOT_5), 'つ' to (DOT_1 or DOT_3 or DOT_4 or DOT_5), 'て' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5), 'と' to (DOT_2 or DOT_3 or DOT_4 or DOT_5),
        'な' to (DOT_1 or DOT_3), 'に' to (DOT_1 or DOT_2 or DOT_3), 'ぬ' to (DOT_1 or DOT_3 or DOT_4), 'ね' to (DOT_1 or DOT_2 or DOT_3 or DOT_4), 'の' to (DOT_2 or DOT_3 or DOT_4),
        'は' to (DOT_1 or DOT_3 or DOT_6), 'ひ' to (DOT_1 or DOT_2 or DOT_3 or DOT_6), 'ふ' to (DOT_1 or DOT_3 or DOT_4 or DOT_6), 'へ' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6), 'ほ' to (DOT_2 or DOT_3 or DOT_4 or DOT_6),
        'ま' to (DOT_1 or DOT_3 or DOT_5 or DOT_6), 'み' to (DOT_1 or DOT_2 or DOT_3 or DOT_5 or DOT_6), 'む' to (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6), 'め' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6), 'も' to (DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6),
        'や' to (DOT_3 or DOT_4), 'ゆ' to (DOT_3 or DOT_4 or DOT_6), 'よ' to (DOT_3 or DOT_4 or DOT_5 or DOT_6),
        'ら' to (DOT_1 or DOT_5), 'り' to (DOT_1 or DOT_2 or DOT_5), 'る' to (DOT_1 or DOT_4 or DOT_5), 'れ' to (DOT_1 or DOT_2 or DOT_4 or DOT_5), 'ろ' to (DOT_2 or DOT_4 or DOT_5),
        'わ' to DOT_3, 'を' to (DOT_3 or DOT_5), 'ん' to (DOT_3 or DOT_5 or DOT_6)
    )

    // ドット -> 清音五十音 (逆引き)
    private val DOT_TO_KANA = KANA_TO_DOT.entries.associate { (k, v) -> v to k }

    // 濁音マップ (ドット -> 濁音かな)
    private val DOT_TO_DAKUTEN = mapOf(
        (DOT_1 or DOT_6) to "が", (DOT_1 or DOT_2 or DOT_6) to "ぎ", (DOT_1 or DOT_4 or DOT_6) to "ぐ", (DOT_1 or DOT_2 or DOT_4 or DOT_6) to "げ", (DOT_2 or DOT_4 or DOT_6) to "ご",
        (DOT_1 or DOT_5 or DOT_6) to "ざ", (DOT_1 or DOT_2 or DOT_5 or DOT_6) to "じ", (DOT_1 or DOT_4 or DOT_5 or DOT_6) to "ず", (DOT_1 or DOT_2 or DOT_4 or DOT_5 or DOT_6) to "ぜ", (DOT_2 or DOT_4 or DOT_5 or DOT_6) to "ぞ",
        (DOT_1 or DOT_3 or DOT_5) to "だ", (DOT_1 or DOT_2 or DOT_3 or DOT_5) to "ぢ", (DOT_1 or DOT_3 or DOT_4 or DOT_5) to "づ", (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5) to "で", (DOT_2 or DOT_3 or DOT_4 or DOT_5) to "ど",
        (DOT_1 or DOT_3 or DOT_6) to "ば", (DOT_1 or DOT_2 or DOT_3 or DOT_6) to "び", (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "ぶ", (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6) to "べ", (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ぼ",
        (DOT_1 or DOT_4) to "ゔ"
    )

    // 半濁音マップ (ドット -> 半濁音かな)
    private val DOT_TO_HANDAKUTEN = mapOf(
        (DOT_1 or DOT_3 or DOT_6) to "ぱ", (DOT_1 or DOT_2 or DOT_3 or DOT_6) to "ぴ", (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "ぷ", (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ぺ", (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ぽ"
    )

    // 拗音マップ (ドット -> 拗音かな)
    private val DOT_TO_YOUON = mapOf(
        (DOT_1 or DOT_6) to "きゃ", (DOT_1 or DOT_4 or DOT_6) to "きゅ", (DOT_2 or DOT_4 or DOT_6) to "きょ",
        (DOT_1 or DOT_5 or DOT_6) to "しゃ", (DOT_1 or DOT_4 or DOT_5 or DOT_6) to "しゅ", (DOT_2 or DOT_4 or DOT_5 or DOT_6) to "しょ",
        (DOT_1 or DOT_3 or DOT_5) to "ちゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_5) to "ちゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_5) to "ちょ",
        (DOT_1 or DOT_3) to "にゃ", (DOT_1 or DOT_3 or DOT_4) to "にゅ", (DOT_2 or DOT_3 or DOT_4) to "にょ",
        (DOT_1 or DOT_3 or DOT_6) to "ひゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "ひゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ひょ",
        (DOT_1 or DOT_3 or DOT_5 or DOT_6) to "みゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6) to "みゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6) to "みょ",
        (DOT_1 or DOT_5) to "りゃ", (DOT_1 or DOT_4 or DOT_5) to "りゅ", (DOT_2 or DOT_4 or DOT_5) to "りょ"
    )

    // 拗濁音マップ (ドット -> 拗濁音かな)
    private val DOT_TO_YOU_DAKUTEN = mapOf(
        (DOT_1 or DOT_6) to "ぎゃ", (DOT_1 or DOT_4 or DOT_6) to "ぎゅ", (DOT_2 or DOT_4 or DOT_6) to "ぎょ",
        (DOT_1 or DOT_5 or DOT_6) to "じゃ", (DOT_1 or DOT_4 or DOT_5 or DOT_6) to "じゅ", (DOT_2 or DOT_4 or DOT_5 or DOT_6) to "じょ",
        (DOT_1 or DOT_3 or DOT_5) to "ぢゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_5) to "ぢゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_5) to "ぢょ",
        (DOT_1 or DOT_3 or DOT_6) to "びゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "びゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "びょ"
    )

    // 拗半濁音マップ (ドット -> 拗半濁音かな)
    private val DOT_TO_YOU_HANDAKUTEN = mapOf(
        (DOT_1 or DOT_3 or DOT_6) to "ぴゃ", (DOT_1 or DOT_3 or DOT_4 or DOT_6) to "ぴゅ", (DOT_2 or DOT_3 or DOT_4 or DOT_6) to "ぴょ"
    )

    // 数字マップ
    private val DIGIT_TO_DOT = mapOf(
        '1' to DOT_1, '2' to (DOT_1 or DOT_2), '3' to (DOT_1 or DOT_4), '4' to (DOT_1 or DOT_4 or DOT_5), '5' to (DOT_1 or DOT_5),
        '6' to (DOT_1 or DOT_2 or DOT_4), '7' to (DOT_1 or DOT_2 or DOT_4 or DOT_5), '8' to (DOT_1 or DOT_2 or DOT_5), '9' to (DOT_2 or DOT_4), '0' to (DOT_2 or DOT_4 or DOT_5)
    )
    private val DOT_TO_DIGIT = DIGIT_TO_DOT.entries.associate { (k, v) -> v to k }

    // アルファベットマップ
    private val LATIN_TO_DOT = mapOf(
        'a' to DOT_1, 'b' to (DOT_1 or DOT_2), 'c' to (DOT_1 or DOT_4), 'd' to (DOT_1 or DOT_4 or DOT_5), 'e' to (DOT_1 or DOT_5),
        'f' to (DOT_1 or DOT_2 or DOT_4), 'g' to (DOT_1 or DOT_2 or DOT_4 or DOT_5), 'h' to (DOT_1 or DOT_2 or DOT_5), 'i' to (DOT_2 or DOT_4), 'j' to (DOT_2 or DOT_4 or DOT_5),
        'k' to (DOT_1 or DOT_3), 'l' to (DOT_1 or DOT_2 or DOT_3), 'm' to (DOT_1 or DOT_3 or DOT_4), 'n' to (DOT_1 or DOT_3 or DOT_4 or DOT_5), 'o' to (DOT_1 or DOT_3 or DOT_5),
        'p' to (DOT_1 or DOT_2 or DOT_3 or DOT_4), 'q' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5), 'r' to (DOT_1 or DOT_2 or DOT_3 or DOT_5), 's' to (DOT_2 or DOT_3 or DOT_4), 't' to (DOT_2 or DOT_3 or DOT_4 or DOT_5),
        'u' to (DOT_1 or DOT_3 or DOT_6), 'v' to (DOT_1 or DOT_2 or DOT_3 or DOT_6), 'w' to (DOT_2 or DOT_4 or DOT_5 or DOT_6), 'x' to (DOT_1 or DOT_3 or DOT_4 or DOT_6), 'y' to (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6), 'z' to (DOT_1 or DOT_3 or DOT_5 or DOT_6)
    )
    private val DOT_TO_LATIN = LATIN_TO_DOT.entries.associate { (k, v) -> v to k }

    // 記号マップ
    private val PUNCT_TO_DOT = mapOf(
        '。' to (DOT_2 or DOT_5 or DOT_6), '.' to (DOT_2 or DOT_5 or DOT_6),
        '、' to DOT_5, ',' to DOT_2,
        '？' to (DOT_2 or DOT_6), '?' to (DOT_2 or DOT_6),
        '！' to (DOT_2 or DOT_3 or DOT_5), '!' to (DOT_2 or DOT_3 or DOT_5),
        '「' to (DOT_2 or DOT_3 or DOT_6), '」' to (DOT_3 or DOT_5 or DOT_6),
        '（' to (DOT_2 or DOT_3 or DOT_6), '）' to (DOT_3 or DOT_5 or DOT_6),
        '(' to (DOT_2 or DOT_3 or DOT_6), ')' to (DOT_3 or DOT_5 or DOT_6),
        'ー' to CHOON, '-' to (DOT_3 or DOT_6),
        '：' to (DOT_2 or DOT_5), ':' to (DOT_2 or DOT_5),
        '／' to (DOT_3 or DOT_4), '/' to (DOT_3 or DOT_4)
    )
    private val DOT_TO_PUNCT = PUNCT_TO_DOT.entries.associate { (k, v) -> v to k }

    // ==========================================
    // 1. 【点訳 (Ten-yaku)】: 墨字 -> 点字
    // ==========================================

    /**
     * テキストを点字セルバイト列（各バイトがドット1〜8）に点訳
     */
    fun translateToBraille(text: String): ByteArray {
        val result = mutableListOf<Int>()
        var inNumberMode = false
        var inLatinMode = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // 空白 (マスあけ)
            if (ch.isWhitespace()) {
                result.add(SPACE_CELL)
                inNumberMode = false
                inLatinMode = false
                i++
                continue
            }

            // 数字
            val digitVal = DIGIT_TO_DOT[ch]
            if (digitVal != null) {
                if (!inNumberMode) {
                    result.add(PREFIX_NUMBER)
                    inNumberMode = true
                    inLatinMode = false
                }
                result.add(digitVal)
                i++
                continue
            } else {
                inNumberMode = false
            }

            // 英字 (A〜Z, a〜z)
            val lowerChar = ch.lowercaseChar()
            val latinVal = LATIN_TO_DOT[lowerChar]
            if (latinVal != null) {
                if (!inLatinMode) {
                    result.add(PREFIX_GAIJI)
                    inLatinMode = true
                }
                result.add(latinVal)
                i++
                continue
            } else {
                inLatinMode = false
            }

            // 記号
            val punctVal = PUNCT_TO_DOT[ch]
            if (punctVal != null) {
                result.add(punctVal)
                i++
                continue
            }

            // カタカナをひらがなに統一
            val hiraChar = toHiragana(ch)

            // 促音「っ」
            if (hiraChar == 'っ') {
                result.add(SOKUON)
                i++
                continue
            }

            // 拗音判定 (きゃ, しゃ, ちゃ, にゃ, ひゃ, みゃ, りゃ, ぎゃ, じゃ, びゃ, ぴゃ 等)
            if (i + 1 < text.length) {
                val nextHira = toHiragana(text[i + 1])
                val youonCells = getYouonBraille(hiraChar, nextHira)
                if (youonCells != null) {
                    result.addAll(youonCells)
                    i += 2
                    continue
                }
            }

            // 濁音判定 (が, ざ, だ, ば 等)
            val dakuBase = getDakuonBase(hiraChar)
            if (dakuBase != null) {
                result.add(PREFIX_DAKUTEN)
                val baseVal = KANA_TO_DOT[dakuBase] ?: SPACE_CELL
                result.add(baseVal)
                i++
                continue
            }

            // 半濁音判定 (ぱ, ぴ, ぷ, ぺ, ぽ)
            val handakuBase = getHandakuonBase(hiraChar)
            if (handakuBase != null) {
                result.add(PREFIX_HANDAKUTEN)
                val baseVal = KANA_TO_DOT[handakuBase] ?: SPACE_CELL
                result.add(baseVal)
                i++
                continue
            }

            // 清音五十音
            val baseVal = KANA_TO_DOT[hiraChar]
            if (baseVal != null) {
                result.add(baseVal)
                i++
                continue
            }

            // 漢字・その他文字フォールバック (上位・下位6ビットに分割)
            val code = ch.code
            val b1 = code and 0x3F
            val b2 = (code shr 6) and 0x3F
            result.add(b1)
            result.add(b2)
            i++
        }

        return result.map { it.toByte() }.toByteArray()
    }

    /**
     * テキストを Unicode点字文字列 (⠁⠂⠃等) に点訳
     */
    fun translateToUnicodeBraille(text: String): String {
        val bytes = translateToBraille(text)
        return bytesToUnicode(bytes)
    }

    // ==========================================
    // 2. 【墨訳 (Sumi-yaku)】: 点字 -> 墨字
    // ==========================================

    /**
     * 点字セルバイト列を墨字（かな・英数字・記号テキスト）に墨訳
     */
    fun translateToText(brailleBytes: ByteArray): String {
        val sb = StringBuilder()
        var currentPrefix = 0
        var isNumberMode = false
        var isLatinMode = false

        for (byteVal in brailleBytes) {
            val cell = byteVal.toInt() and 0xFF

            // 空白 (マスあけ)
            if (cell == SPACE_CELL) {
                sb.append(" ")
                currentPrefix = 0
                isNumberMode = false
                isLatinMode = false
                continue
            }

            // 前置符の検出
            when (cell) {
                PREFIX_DAKUTEN, PREFIX_HANDAKUTEN, PREFIX_YOUON,
                PREFIX_YOU_DAKUTEN, PREFIX_YOU_HANDAKUTEN -> {
                    currentPrefix = cell
                    continue
                }
                PREFIX_NUMBER -> {
                    isNumberMode = true
                    isLatinMode = false
                    currentPrefix = 0
                    continue
                }
                PREFIX_GAIJI -> {
                    isLatinMode = true
                    isNumberMode = false
                    currentPrefix = 0
                    continue
                }
                SOKUON -> {
                    sb.append("っ")
                    currentPrefix = 0
                    continue
                }
                CHOON -> {
                    sb.append("ー")
                    currentPrefix = 0
                    continue
                }
            }

            // 数符モード中
            if (isNumberMode) {
                val digit = DOT_TO_DIGIT[cell]
                if (digit != null) {
                    sb.append(digit)
                    continue
                } else {
                    isNumberMode = false
                }
            }

            // 外字符モード中
            if (isLatinMode) {
                val latin = DOT_TO_LATIN[cell]
                if (latin != null) {
                    sb.append(latin)
                    continue
                } else {
                    isLatinMode = false
                }
            }

            // 前置符付き文字のデコード
            when (currentPrefix) {
                PREFIX_DAKUTEN -> {
                    val kana = DOT_TO_DAKUTEN[cell]
                    if (kana != null) {
                        sb.append(kana)
                    } else {
                        val base = DOT_TO_KANA[cell]
                        if (base != null) sb.append(base)
                    }
                    currentPrefix = 0
                    continue
                }
                PREFIX_HANDAKUTEN -> {
                    val kana = DOT_TO_HANDAKUTEN[cell]
                    if (kana != null) {
                        sb.append(kana)
                    } else {
                        val base = DOT_TO_KANA[cell]
                        if (base != null) sb.append(base)
                    }
                    currentPrefix = 0
                    continue
                }
                PREFIX_YOUON -> {
                    val kana = DOT_TO_YOUON[cell]
                    if (kana != null) {
                        sb.append(kana)
                    } else {
                        val base = DOT_TO_KANA[cell]
                        if (base != null) sb.append(base)
                    }
                    currentPrefix = 0
                    continue
                }
                PREFIX_YOU_DAKUTEN -> {
                    val kana = DOT_TO_YOU_DAKUTEN[cell]
                    if (kana != null) {
                        sb.append(kana)
                    } else {
                        val base = DOT_TO_KANA[cell]
                        if (base != null) sb.append(base)
                    }
                    currentPrefix = 0
                    continue
                }
                PREFIX_YOU_HANDAKUTEN -> {
                    val kana = DOT_TO_YOU_HANDAKUTEN[cell]
                    if (kana != null) {
                        sb.append(kana)
                    } else {
                        val base = DOT_TO_KANA[cell]
                        if (base != null) sb.append(base)
                    }
                    currentPrefix = 0
                    continue
                }
            }

            // 記号
            val punct = DOT_TO_PUNCT[cell]
            if (punct != null) {
                sb.append(punct)
                continue
            }

            // 単独清音五十音
            val kana = DOT_TO_KANA[cell]
            if (kana != null) {
                sb.append(kana)
                continue
            }
        }

        return sb.toString()
    }

    /**
     * Unicode点字文字列 (⠁⠂⠃等) を墨字に墨訳
     */
    fun translateUnicodeToText(unicodeBraille: String): String {
        val bytes = unicodeToBytes(unicodeBraille)
        return translateToText(bytes)
    }

    // ==========================================
    // 3. ユーティリティ
    // ==========================================

    fun bytesToUnicode(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val code = 0x2800 or (b.toInt() and 0xFF)
            sb.append(code.toChar())
        }
        return sb.toString()
    }

    fun unicodeToBytes(unicodeBraille: String): ByteArray {
        val list = mutableListOf<Byte>()
        for (ch in unicodeBraille) {
            val code = ch.code
            if (code in 0x2800..0x28FF) {
                list.add((code - 0x2800).toByte())
            } else if (ch.isWhitespace()) {
                list.add(SPACE_CELL.toByte())
            }
        }
        return list.toByteArray()
    }

    // 互換性エイリアス
    fun textToBrailleBytes(text: String): ByteArray = translateToBraille(text)
    fun textToUnicodeBraille(text: String): String = translateToUnicodeBraille(text)
    fun bytesToUnicodeBraille(bytes: ByteArray): String = bytesToUnicode(bytes)

    private fun toHiragana(c: Char): Char = if (c in '\u30A1'..'\u30F6') (c.code - 0x60).toChar() else c

    private fun getDakuonBase(c: Char): Char? = when (c) {
        'が' -> 'か'; 'ぎ' -> 'き'; 'ぐ' -> 'く'; 'げ' -> 'け'; 'ご' -> 'こ'
        'ざ' -> 'さ'; 'じ' -> 'し'; 'ず' -> 'す'; 'ぜ' -> 'せ'; 'ぞ' -> 'そ'
        'だ' -> 'た'; 'ぢ' -> 'ち'; 'づ' -> 'つ'; 'で' -> 'て'; 'ど' -> 'と'
        'ば' -> 'は'; 'び' -> 'ひ'; 'ぶ' -> 'ふ'; 'べ' -> 'へ'; 'ぼ' -> 'ほ'
        'ゔ' -> 'う'
        else -> null
    }

    private fun getHandakuonBase(c: Char): Char? = when (c) {
        'ぱ' -> 'は'; 'ぴ' -> 'ひ'; 'ぷ' -> 'ふ'; 'ぺ' -> 'へ'; 'ぽ' -> 'ほ'
        else -> null
    }

    private fun getYouonBraille(c1: Char, c2: Char): List<Int>? {
        val youonType = when (c2) {
            'ゃ' -> 'あ'; 'ゅ' -> 'う'; 'ょ' -> 'お'; else -> return null
        }

        // 清音拗音
        val baseChar = when (c1) {
            'き' -> when (youonType) { 'あ' -> 'か'; 'う' -> 'く'; 'お' -> 'こ'; else -> null }
            'し' -> when (youonType) { 'あ' -> 'さ'; 'う' -> 'す'; 'お' -> 'そ'; else -> null }
            'ち' -> when (youonType) { 'あ' -> 'た'; 'う' -> 'つ'; 'お' -> 'と'; else -> null }
            'に' -> when (youonType) { 'あ' -> 'な'; 'う' -> 'ぬ'; 'お' -> 'の'; else -> null }
            'ひ' -> when (youonType) { 'あ' -> 'は'; 'う' -> 'ふ'; 'お' -> 'ほ'; else -> null }
            'み' -> when (youonType) { 'あ' -> 'ま'; 'う' -> 'む'; 'お' -> 'も'; else -> null }
            'り' -> when (youonType) { 'あ' -> 'ら'; 'う' -> 'る'; 'お' -> 'ろ'; else -> null }
            else -> null
        }
        if (baseChar != null) {
            val cell = KANA_TO_DOT[baseChar] ?: return null
            return listOf(PREFIX_YOUON, cell)
        }

        // 濁音拗音
        val dakuBaseChar = when (c1) {
            'ぎ' -> when (youonType) { 'あ' -> 'か'; 'う' -> 'く'; 'お' -> 'こ'; else -> null }
            'じ' -> when (youonType) { 'あ' -> 'さ'; 'う' -> 'す'; 'お' -> 'そ'; else -> null }
            'ぢ' -> when (youonType) { 'あ' -> 'た'; 'う' -> 'つ'; 'お' -> 'と'; else -> null }
            'び' -> when (youonType) { 'あ' -> 'は'; 'う' -> 'ふ'; 'お' -> 'ほ'; else -> null }
            else -> null
        }
        if (dakuBaseChar != null) {
            val cell = KANA_TO_DOT[dakuBaseChar] ?: return null
            return listOf(PREFIX_YOU_DAKUTEN, cell)
        }

        // 半濁音拗音
        if (c1 == 'ぴ') {
            val handakuBaseChar = when (youonType) { 'あ' -> 'は'; 'う' -> 'ふ'; 'お' -> 'ほ'; else -> null }
            if (handakuBaseChar != null) {
                val cell = KANA_TO_DOT[handakuBaseChar] ?: return null
                return listOf(PREFIX_YOU_HANDAKUTEN, cell)
            }
        }

        return null
    }
}
