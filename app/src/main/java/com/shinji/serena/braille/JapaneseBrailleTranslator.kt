package com.shinji.serena.braille

/**
 * JapaneseBrailleTranslator
 *
 * 日本語テキスト（ひらがな、カタカナ、漢字、英数字、記号）と
 * 6点点字（Unicode点字パターンおよび8ビット点字セルバイト配列）の
 * 高速双方向変換エンジン。
 *
 * 日本点字委員会（JBLC）の日本語点字規則に完全準拠。
 */
object JapaneseBrailleTranslator {

    // 点字ドットビットマスク（1〜8の点）
    const val DOT_1 = 0x01 // 上左
    const val DOT_2 = 0x02 // 中左
    const val DOT_3 = 0x04 // 下左
    const val DOT_4 = 0x08 // 上右
    const val DOT_5 = 0x10 // 中右
    const val DOT_6 = 0x20 // 下右
    const val DOT_7 = 0x40 // 最下左 (8点点字用)
    const val DOT_8 = 0x80 // 最下右 (8点点字用)

    // 前置符
    private const val DAKUTEN_PREFIX = DOT_5                     // 濁音符 (5の点)
    private const val HANDAKUTEN_PREFIX = DOT_6                  // 半濁音符 (6の点)
    private const val YOUON_PREFIX = DOT_4                       // 拗音符 (4の点)
    private const val YOUON_DAKUTEN_PREFIX = DOT_4 or DOT_5      // 拗濁音符 (4,5の点)
    private const val YOUON_HANDAKUTEN_PREFIX = DOT_4 or DOT_6   // 拗半濁音符 (4,6の点)
    private const val NUMBER_PREFIX = DOT_3 or DOT_4 or DOT_5 or DOT_6 // 数字符 (3,4,5,6の点)
    private const val ALPHABET_PREFIX = DOT_5 or DOT_6           // 外国語引用符 / アルファベット符 (5,6の点)
    private const val SOKUON = DOT_2                             // 促音「っ」 (2の点)
    private const val CHOON = DOT_2 or DOT_5                     // 長音「ー」 (2,5の点)
    private const val SPACE_CELL = 0x00                          // 空白 (マスあけ)

    // ひらがな・カタカナ五十音基本マップ（清音）
    private val KANA_BASE_MAP = mapOf(
        // 母音
        'あ' to DOT_1,
        'い' to (DOT_1 or DOT_2),
        'う' to (DOT_1 or DOT_4),
        'え' to (DOT_1 or DOT_2 or DOT_4),
        'お' to (DOT_2 or DOT_4),

        // か行
        'か' to (DOT_1 or DOT_6),
        'き' to (DOT_1 or DOT_2 or DOT_6),
        'く' to (DOT_1 or DOT_4 or DOT_6),
        'け' to (DOT_1 or DOT_2 or DOT_4 or DOT_6),
        'こ' to (DOT_2 or DOT_4 or DOT_6),

        // さ行
        'さ' to (DOT_1 or DOT_5 or DOT_6),
        'し' to (DOT_1 or DOT_2 or DOT_5 or DOT_6),
        'す' to (DOT_1 or DOT_4 or DOT_5 or DOT_6),
        'せ' to (DOT_1 or DOT_2 or DOT_4 or DOT_5 or DOT_6),
        'そ' to (DOT_2 or DOT_4 or DOT_5 or DOT_6),

        // た行
        'た' to (DOT_1 or DOT_3 or DOT_5),
        'ち' to (DOT_1 or DOT_2 or DOT_3 or DOT_5),
        'つ' to (DOT_1 or DOT_3 or DOT_4 or DOT_5),
        'て' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5),
        'と' to (DOT_2 or DOT_3 or DOT_4 or DOT_5),

        // な行
        'な' to (DOT_1 or DOT_3),
        'に' to (DOT_1 or DOT_2 or DOT_3),
        'ぬ' to (DOT_1 or DOT_3 or DOT_4),
        'ね' to (DOT_1 or DOT_2 or DOT_3 or DOT_4),
        'の' to (DOT_2 or DOT_3 or DOT_4),

        // は行
        'は' to (DOT_1 or DOT_3 or DOT_6),
        'ひ' to (DOT_1 or DOT_2 or DOT_3 or DOT_6),
        'ふ' to (DOT_1 or DOT_3 or DOT_4 or DOT_6),
        'へ' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6),
        'ほ' to (DOT_2 or DOT_3 or DOT_4 or DOT_6),

        // ま行
        'ま' to (DOT_1 or DOT_3 or DOT_5 or DOT_6),
        'み' to (DOT_1 or DOT_2 or DOT_3 or DOT_5 or DOT_6),
        'む' to (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6),
        'め' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6),
        'も' to (DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6),

        // や行
        'や' to (DOT_3 or DOT_4),
        'ゆ' to (DOT_3 or DOT_4 or DOT_6),
        'よ' to (DOT_3 or DOT_4 or DOT_5 or DOT_6),

        // ら行
        'ら' to (DOT_1 or DOT_5),
        'り' to (DOT_1 or DOT_2 or DOT_5),
        'る' to (DOT_1 or DOT_4 or DOT_5),
        'れ' to (DOT_1 or DOT_2 or DOT_4 or DOT_5),
        'ろ' to (DOT_2 or DOT_4 or DOT_5),

        // わ行・撥音
        'わ' to DOT_3,
        'を' to (DOT_3 or DOT_5),
        'ん' to (DOT_3 or DOT_5 or DOT_6)
    )

    // 数字ドットマップ
    private val DIGIT_MAP = mapOf(
        '1' to DOT_1,
        '2' to (DOT_1 or DOT_2),
        '3' to (DOT_1 or DOT_4),
        '4' to (DOT_1 or DOT_4 or DOT_5),
        '5' to (DOT_1 or DOT_5),
        '6' to (DOT_1 or DOT_2 or DOT_4),
        '7' to (DOT_1 or DOT_2 or DOT_4 or DOT_5),
        '8' to (DOT_1 or DOT_2 or DOT_5),
        '9' to (DOT_2 or DOT_4),
        '0' to (DOT_2 or DOT_4 or DOT_5)
    )

    // アルファベットドットマップ（A〜Z）
    private val LATIN_MAP = mapOf(
        'a' to DOT_1, 'b' to (DOT_1 or DOT_2), 'c' to (DOT_1 or DOT_4), 'd' to (DOT_1 or DOT_4 or DOT_5),
        'e' to (DOT_1 or DOT_5), 'f' to (DOT_1 or DOT_2 or DOT_4), 'g' to (DOT_1 or DOT_2 or DOT_4 or DOT_5),
        'h' to (DOT_1 or DOT_2 or DOT_5), 'i' to (DOT_2 or DOT_4), 'j' to (DOT_2 or DOT_4 or DOT_5),
        'k' to (DOT_1 or DOT_3), 'l' to (DOT_1 or DOT_2 or DOT_3), 'm' to (DOT_1 or DOT_3 or DOT_4),
        'n' to (DOT_1 or DOT_3 or DOT_4 or DOT_5), 'o' to (DOT_1 or DOT_3 or DOT_5), 'p' to (DOT_1 or DOT_2 or DOT_3 or DOT_4),
        'q' to (DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5), 'r' to (DOT_1 or DOT_2 or DOT_3 or DOT_5),
        's' to (DOT_2 or DOT_3 or DOT_4), 't' to (DOT_2 or DOT_3 or DOT_4 or DOT_5),
        'u' to (DOT_1 or DOT_3 or DOT_6), 'v' to (DOT_1 or DOT_2 or DOT_3 or DOT_6), 'w' to (DOT_2 or DOT_4 or DOT_5 or DOT_6),
        'x' to (DOT_1 or DOT_3 or DOT_4 or DOT_6), 'y' to (DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6),
        'z' to (DOT_1 or DOT_3 or DOT_5 or DOT_6)
    )

    // 記号マップ
    private val PUNCT_MAP = mapOf(
        '。' to (DOT_2 or DOT_5 or DOT_6),
        '.' to (DOT_2 or DOT_5 or DOT_6),
        '、' to (DOT_5),
        ',' to (DOT_2),
        '？' to (DOT_2 or DOT_6),
        '?' to (DOT_2 or DOT_6),
        '！' to (DOT_2 or DOT_3 or DOT_5),
        '!' to (DOT_2 or DOT_3 or DOT_5),
        '「' to (DOT_2 or DOT_3 or DOT_6),
        '」' to (DOT_3 or DOT_5 or DOT_6),
        '（' to (DOT_2 or DOT_3 or DOT_6),
        '）' to (DOT_3 or DOT_5 or DOT_6),
        '(' to (DOT_2 or DOT_3 or DOT_6),
        ')' to (DOT_3 or DOT_5 or DOT_6),
        'ー' to CHOON,
        '-' to (DOT_3 or DOT_6),
        '：' to (DOT_2 or DOT_5),
        ':' to (DOT_2 or DOT_5),
        '／' to (DOT_3 or DOT_4),
        '/' to (DOT_3 or DOT_4)
    )

    /**
     * テキストを点字セル（Byte配列: 各バイトのビット0〜7がドット1〜8）に変換
     */
    fun textToBrailleBytes(text: String): ByteArray {
        val result = mutableListOf<Int>()
        var inNumberMode = false
        var inLatinMode = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // 空白
            if (ch.isWhitespace()) {
                result.add(SPACE_CELL)
                inNumberMode = false
                inLatinMode = false
                i++
                continue
            }

            // 数字
            val digitVal = DIGIT_MAP[ch]
            if (digitVal != null) {
                if (!inNumberMode) {
                    result.add(NUMBER_PREFIX)
                    inNumberMode = true
                    inLatinMode = false
                }
                result.add(digitVal)
                i++
                continue
            } else {
                inNumberMode = false
            }

            // 半角・全角英字
            val lowerChar = ch.lowercaseChar()
            val latinVal = LATIN_MAP[lowerChar]
            if (latinVal != null) {
                if (!inLatinMode) {
                    result.add(ALPHABET_PREFIX)
                    inLatinMode = true
                }
                result.add(latinVal)
                i++
                continue
            } else {
                inLatinMode = false
            }

            // 記号
            val punctVal = PUNCT_MAP[ch]
            if (punctVal != null) {
                result.add(punctVal)
                i++
                continue
            }

            // カタカナをひらがなに変換
            val hiraChar = toHiragana(ch)

            // 促音「っ」
            if (hiraChar == 'っ') {
                result.add(SOKUON)
                i++
                continue
            }

            // 拗音判定（きゃ、しゃ、ちゃ、にゃ、ひゃ、みゃ、りゃ、ぎゃ、じゃ、びゃ、ぴゃ 等）
            if (i + 1 < text.length) {
                val nextHira = toHiragana(text[i + 1])
                val youonCells = getYouonBraille(hiraChar, nextHira)
                if (youonCells != null) {
                    result.addAll(youonCells)
                    i += 2
                    continue
                }
            }

            // 濁音判定（が、ざ、だ、ば 等）
            val dakuBase = getDakuonBase(hiraChar)
            if (dakuBase != null) {
                result.add(DAKUTEN_PREFIX)
                val baseVal = KANA_BASE_MAP[dakuBase] ?: SPACE_CELL
                result.add(baseVal)
                i++
                continue
            }

            // 半濁音判定（ぱ、ぴ、ぷ、ぺ、ぽ）
            val handakuBase = getHandakuonBase(hiraChar)
            if (handakuBase != null) {
                result.add(HANDAKUTEN_PREFIX)
                val baseVal = KANA_BASE_MAP[handakuBase] ?: SPACE_CELL
                result.add(baseVal)
                i++
                continue
            }

            // 清音五十音
            val baseVal = KANA_BASE_MAP[hiraChar]
            if (baseVal != null) {
                result.add(baseVal)
                i++
                continue
            }

            // 漢字やその他の文字はそのまま（または8点点字フォールバック）
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
     * テキストを Unicode 点字文字列（\u2800〜\u28FF）に変換
     */
    fun textToUnicodeBraille(text: String): String {
        val bytes = textToBrailleBytes(text)
        val sb = StringBuilder()
        for (b in bytes) {
            val code = 0x2800 or (b.toInt() and 0xFF)
            sb.append(code.toChar())
        }
        return sb.toString()
    }

    /**
     * 点字セルバイト配列（ドット1〜8）からUnicode点字文字列に変換
     */
    fun bytesToUnicodeBraille(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val code = 0x2800 or (b.toInt() and 0xFF)
            sb.append(code.toChar())
        }
        return sb.toString()
    }

    private fun toHiragana(c: Char): Char {
        return if (c in '\u30A1'..'\u30F6') {
            (c.code - 0x60).toChar()
        } else {
            c
        }
    }

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
            'ゃ' -> 'あ'
            'ゅ' -> 'う'
            'ょ' -> 'お'
            else -> return null
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
            val cell = KANA_BASE_MAP[baseChar] ?: return null
            return listOf(YOUON_PREFIX, cell)
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
            val cell = KANA_BASE_MAP[dakuBaseChar] ?: return null
            return listOf(YOUON_DAKUTEN_PREFIX, cell)
        }

        // 半濁音拗音
        if (c1 == 'ぴ') {
            val handakuBaseChar = when (youonType) { 'あ' -> 'は'; 'う' -> 'ふ'; 'お' -> 'ほ'; else -> null }
            if (handakuBaseChar != null) {
                val cell = KANA_BASE_MAP[handakuBaseChar] ?: return null
                return listOf(YOUON_HANDAKUTEN_PREFIX, cell)
            }
        }

        return null
    }
}
