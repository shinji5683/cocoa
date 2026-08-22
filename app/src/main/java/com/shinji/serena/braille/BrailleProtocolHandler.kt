package com.shinji.serena.braille

/**
 * BrailleProtocolHandler
 *
 * 点字ディスプレイとの送受信パケットフォーマット、
 * セルデータのエンコード/デコード、キー入力コマンドの解析を行う。
 */
class BrailleProtocolHandler(var displayCellCount: Int = 40) {

    enum class BrailleCommand {
        NAVIGATE_NEXT,          // 次の項目へ（パン右 / ジョイスティック右）
        NAVIGATE_PREVIOUS,      // 前の項目へ（パン左 / ジョイスティック左）
        NAVIGATE_UP,            // 上へ
        NAVIGATE_DOWN,          // 下へ
        PERFORM_CLICK,          // 実行（ジョイスティック中央 / Enter / ルーティングキー）
        PERFORM_LONG_CLICK,     // 長押し
        ACTION_BACK,            // 戻る
        ACTION_HOME,            // ホーム
        ACTION_MENU,            // Serenaメニュー
        ACTION_NOTIFICATIONS,   // 通知シェード展開
        SCROLL_FORWARD,         // 次のページへスクロール
        SCROLL_BACKWARD,        // 前のページへスクロール
        ROUTING_CLICK,          // 特定セルのルーティングキークリック
        BRAILLE_KEY_INPUT,      // 点字キー文字入力（ドット1〜6 + Space）
        UNKNOWN
    }

    data class KeyInputEvent(
        val command: BrailleCommand,
        val routingCellIndex: Int = -1,
        val rawDots: Int = 0,
        val text: String = ""
    )

    /**
     * 表示用点字セル配列（byte配列）をディスプレイのセル幅に合わせてフォーマット・パディング
     */
    fun formatDisplayBuffer(brailleBytes: ByteArray, cursorPosition: Int = -1): ByteArray {
        val buffer = ByteArray(displayCellCount) { 0x00 }
        val copyLen = brailleBytes.size.coerceAtMost(displayCellCount)
        System.arraycopy(brailleBytes, 0, buffer, 0, copyLen)

        // カーソル位置のドット7+8（最下段ピン）を点滅/常時点灯で表現
        if (cursorPosition in 0 until displayCellCount) {
            buffer[cursorPosition] = (buffer[cursorPosition].toInt() or 0xC0).toByte()
        }

        return buffer
    }

    /**
     * 受信バイト列から点字コマンドをパース
     */
    fun parseIncomingPacket(data: ByteArray): KeyInputEvent? {
        if (data.isEmpty()) return null

        val firstByte = data[0].toInt() and 0xFF

        // 1. ルーティングキー（0x80 〜 0xBF: 各セルのタッチボタン）
        if (firstByte in 0x80..0xBF) {
            val cellIndex = firstByte - 0x80
            return KeyInputEvent(BrailleCommand.ROUTING_CLICK, routingCellIndex = cellIndex)
        }

        // 2. ナビゲーションキー・ジョイスティック
        return when (firstByte) {
            0x21, 0x06 -> KeyInputEvent(BrailleCommand.NAVIGATE_NEXT)      // Pan Right / Next
            0x20, 0x02 -> KeyInputEvent(BrailleCommand.NAVIGATE_PREVIOUS)  // Pan Left / Prev
            0x22, 0x04 -> KeyInputEvent(BrailleCommand.NAVIGATE_UP)        // Up
            0x23, 0x05 -> KeyInputEvent(BrailleCommand.NAVIGATE_DOWN)      // Down
            0x24, 0x0A -> KeyInputEvent(BrailleCommand.PERFORM_CLICK)      // Enter / Center Key
            0x1B -> KeyInputEvent(BrailleCommand.ACTION_BACK)              // Back (Esc)
            0x1C -> KeyInputEvent(BrailleCommand.ACTION_HOME)              // Home
            0x1D -> KeyInputEvent(BrailleCommand.ACTION_MENU)              // Menu
            0x1E -> KeyInputEvent(BrailleCommand.ACTION_NOTIFICATIONS)     // Notifications

            // 3. 点字キー入力 (Perkins: ドット1〜6)
            in 0x01..0x3F -> {
                KeyInputEvent(
                    command = BrailleCommand.BRAILLE_KEY_INPUT,
                    rawDots = firstByte
                )
            }

            else -> KeyInputEvent(BrailleCommand.UNKNOWN)
        }
    }
}
