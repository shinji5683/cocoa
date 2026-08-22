package com.shinji.serena.ime

import com.shinji.serena.braille.JapaneseBrailleTranslator

/**
 * Serena IME - 6点点字デコーダー (Japanese 6-Dot Braille Decoder)
 *
 * オンスクリーン6点点字キーボードおよび外付け点字キーボードからの
 * 点字ドット入力（点1〜点6の同時押し・累積）をリアルタイムにデコードし、
 * 平仮名・カタカナ・英数字・記号へと墨訳する。
 */
object SerenaBrailleDecoder {

    const val DOT_1 = JapaneseBrailleTranslator.DOT_1
    const val DOT_2 = JapaneseBrailleTranslator.DOT_2
    const val DOT_3 = JapaneseBrailleTranslator.DOT_3
    const val DOT_4 = JapaneseBrailleTranslator.DOT_4
    const val DOT_5 = JapaneseBrailleTranslator.DOT_5
    const val DOT_6 = JapaneseBrailleTranslator.DOT_6

    private var activePrefix = 0
    private var isNumberMode = false
    private var isLatinMode = false

    fun reset() {
        activePrefix = 0
        isNumberMode = false
        isLatinMode = false
    }

    /**
     * 点字ドットマスク（1..63）を入力し、デコードされた文字列と読み上げ用テキストを返す
     */
    fun decodeDots(mask: Int): Pair<String, String> {
        val cell = mask and 0x3F

        // 前置符の検出
        when (cell) {
            JapaneseBrailleTranslator.PREFIX_DAKUTEN -> {
                activePrefix = cell
                return Pair("", "濁音符")
            }
            JapaneseBrailleTranslator.PREFIX_HANDAKUTEN -> {
                activePrefix = cell
                return Pair("", "半濁音符")
            }
            JapaneseBrailleTranslator.PREFIX_YOUON -> {
                activePrefix = cell
                return Pair("", "拗音符")
            }
            JapaneseBrailleTranslator.PREFIX_YOU_DAKUTEN -> {
                activePrefix = cell
                return Pair("", "拗濁音符")
            }
            JapaneseBrailleTranslator.PREFIX_YOU_HANDAKUTEN -> {
                activePrefix = cell
                return Pair("", "拗半濁音符")
            }
            JapaneseBrailleTranslator.PREFIX_NUMBER -> {
                isNumberMode = true
                isLatinMode = false
                activePrefix = 0
                return Pair("", "数符")
            }
            JapaneseBrailleTranslator.PREFIX_GAIJI -> {
                isLatinMode = true
                isNumberMode = false
                activePrefix = 0
                return Pair("", "外字符")
            }
            JapaneseBrailleTranslator.SOKUON -> {
                activePrefix = 0
                return Pair("っ", "促音 っ")
            }
            JapaneseBrailleTranslator.CHOON -> {
                activePrefix = 0
                return Pair("ー", "長音")
            }
        }

        // 単一または前置符付き文字の墨訳を実行
        val byteArray = if (activePrefix != 0) {
            byteArrayOf(activePrefix.toByte(), cell.toByte())
        } else {
            byteArrayOf(cell.toByte())
        }

        val decoded = JapaneseBrailleTranslator.translateToText(byteArray)
        activePrefix = 0

        if (decoded.isNotEmpty()) {
            return Pair(decoded, decoded)
        }

        return Pair("", "不明な点字")
    }

    /**
     * ドットマスクからUnicode点字文字を取得 (⠁, ⠃, ⠇ 等)
     */
    fun getBrailleGlyph(mask: Int): String {
        val code = 0x2800 or (mask and 0xFF)
        return code.toChar().toString()
    }
}
