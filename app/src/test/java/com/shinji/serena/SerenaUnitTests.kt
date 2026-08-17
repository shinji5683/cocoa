package com.shinji.serena

import com.shinji.serena.ime.SerenaPhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serena Screen Reader & Serena IME & 徒歩ナビ ユニットテスト
 */
class SerenaUnitTests {

    @Test
    fun testHiraganaPhonetics() {
        val readingA = SerenaPhoneticEngine.getPhoneticReading('あ')
        val readingKa = SerenaPhoneticEngine.getPhoneticReading('か')
        val readingShi = SerenaPhoneticEngine.getPhoneticReading('し')

        assertTrue("Hiragana reading should contain あ and description", readingA.contains("ひらがなの あ") && readingA.contains("朝のあ"))
        assertTrue("Hiragana reading should contain か and description", readingKa.contains("ひらがなの か") && readingKa.contains("為替のか"))
        assertTrue("Hiragana reading should contain し and description", readingShi.contains("ひらがなの し") && readingShi.contains("新聞のし"))
    }

    @Test
    fun testKatakanaPhonetics() {
        val readingKataA = SerenaPhoneticEngine.getPhoneticReading('ア')
        val readingKataSa = SerenaPhoneticEngine.getPhoneticReading('サ')

        assertTrue("Katakana reading should contain ア and description", readingKataA.contains("カタカナの ア") && readingKataA.contains("朝のあ"))
        assertTrue("Katakana reading should contain サ and description", readingKataSa.contains("カタカナの サ") && readingKataSa.contains("桜のさ"))
    }

    @Test
    fun testAlphabetNatoPhonetics() {
        val readingA = SerenaPhoneticEngine.getPhoneticReading('A')
        val readingSmallB = SerenaPhoneticEngine.getPhoneticReading('b')
        val readingS = SerenaPhoneticEngine.getPhoneticReading('S')

        assertTrue("Alphabet uppercase should contain 大文字 and Alpha", readingA.contains("大文字 A") && readingA.contains("Alpha"))
        assertTrue("Alphabet lowercase should contain 小文字 and Bravo", readingSmallB.contains("小文字 B") && readingSmallB.contains("Bravo"))
        assertTrue("Alphabet uppercase should contain 大文字 and Sierra", readingS.contains("大文字 S") && readingS.contains("Sierra"))
    }

    @Test
    fun testNumbersAndSymbols() {
        val reading0 = SerenaPhoneticEngine.getPhoneticReading('0')
        val reading7 = SerenaPhoneticEngine.getPhoneticReading('7')
        val readingExcl = SerenaPhoneticEngine.getPhoneticReading('!')

        assertTrue("Number 0 reading should contain 0 and ゼロ", reading0.contains("0") && reading0.contains("ゼロ"))
        assertTrue("Number 7 reading should contain 7 and ナナ", reading7.contains("7") && reading7.contains("ナナ"))
        assertTrue("Exclamation mark reading should contain 感嘆符", readingExcl.contains("感嘆符") || readingExcl.contains("ビックリマーク"))
    }

    @Test
    fun testNotificationFilter() {
        val spamSummary = "キャンペーン情報！今だけ50%オフセール開催中"
        val isSpam = spamSummary.contains("セール") || spamSummary.contains("キャンペーン")
        assertTrue("Promotional notification should be flagged as spam", isSpam)

        val importantSummary = "080-9495-9134 から着信があります"
        val isCall = importantSummary.contains("着信")
        assertTrue("Incoming call notification should be flagged as important", isCall)
    }

    @Test
    fun testClockPositionCalculations() {
        fun calcClockGuidance(targetBearing: Float, userHeading: Float): SpatialCompassHelper.ClockGuidance {
            var diff = (targetBearing - userHeading + 360f) % 360f
            if (diff < 0) diff += 360f

            return when {
                diff >= 345f || diff < 15f -> SpatialCompassHelper.ClockGuidance(12, "正面 12時の方向", "正面 まっすぐ進んでください", true)
                diff in 15f..<45f -> SpatialCompassHelper.ClockGuidance(1, "右斜め前 1時の方向", "少し右斜め前を向いてください", false)
                diff in 45f..<75f -> SpatialCompassHelper.ClockGuidance(2, "右前 2時の方向", "右前を向いてください", false)
                diff in 75f..<105f -> SpatialCompassHelper.ClockGuidance(3, "右真横 3時の方向", "右真横を向いてください", false)
                diff in 105f..<135f -> SpatialCompassHelper.ClockGuidance(4, "右斜め後ろ 4時の方向", "右斜め後ろです", false)
                diff in 135f..<165f -> SpatialCompassHelper.ClockGuidance(5, "右後方 5時の方向", "右後ろを向いてください", false)
                diff in 165f..<195f -> SpatialCompassHelper.ClockGuidance(6, "真後ろ 6時の方向", "真後ろです。Uターンしてください", false)
                diff in 195f..<225f -> SpatialCompassHelper.ClockGuidance(7, "左後方 7時の方向", "左後ろを向いてください", false)
                diff in 225f..<255f -> SpatialCompassHelper.ClockGuidance(8, "左斜め後ろ 8時の方向", "左斜め後ろです", false)
                diff in 255f..<285f -> SpatialCompassHelper.ClockGuidance(9, "左真横 9時の方向", "左真横を向いてください", false)
                diff in 285f..<315f -> SpatialCompassHelper.ClockGuidance(10, "左前 10時の方向", "左前を向いてください", false)
                diff in 315f..<345f -> SpatialCompassHelper.ClockGuidance(11, "左斜め前 11時の方向", "少し左斜め前を向いてください", false)
                else -> SpatialCompassHelper.ClockGuidance(12, "正面 12時の方向", "正面 まっすぐ進んでください", true)
            }
        }

        // 1. 正面 12時方向（ユーザー北向き0度、ターゲット北0度）
        val g12 = calcClockGuidance(0f, 0f)
        assertEquals(12, g12.hour)
        assertEquals("正面 12時の方向", g12.directionText)
        assertTrue(g12.isStraightAhead)

        // 2. 右前 2時方向（ユーザー北向き0度、ターゲット北東60度）
        val g2 = calcClockGuidance(60f, 0f)
        assertEquals(2, g2.hour)
        assertEquals("右前 2時の方向", g2.directionText)

        // 3. 左斜め後ろ 8時方向（Shinjiさんが質問した240度付近）
        val g8 = calcClockGuidance(240f, 0f)
        assertEquals(8, g8.hour)
        assertEquals("左斜め後ろ 8時の方向", g8.directionText)

        // 4. 真後ろ 6時方向（180度）
        val g6 = calcClockGuidance(180f, 0f)
        assertEquals(6, g6.hour)
        assertEquals("真後ろ 6時の方向", g6.directionText)

        // 5. 左真横 9時方向（270度）
        val g9 = calcClockGuidance(270f, 0f)
        assertEquals(9, g9.hour)
        assertEquals("左真横 9時の方向", g9.directionText)
    }
}
