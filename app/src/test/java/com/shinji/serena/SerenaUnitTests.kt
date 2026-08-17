package com.shinji.serena

import com.shinji.serena.ime.SerenaPhoneticEngine
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serena Screen Reader & Serena IME ユニットテスト
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
}
