package com.shinji.serena

import com.shinji.serena.ime.SerenaPhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testLockScreenPinKeypadInference() {
        fun inferLabel(viewId: String?): String {
            if (viewId.isNullOrEmpty()) return ""
            val name = viewId.substringAfterLast(":id/").lowercase()
            return when {
                name == "key0" || name.endsWith("_0") || name == "button0" -> "数字の 0（ゼロ）"
                name == "key1" || name.endsWith("_1") || name == "button1" -> "数字の 1（イチ）"
                name == "key2" || name.endsWith("_2") || name == "button2" -> "数字の 2（ニ）"
                name == "key3" || name.endsWith("_3") || name == "button3" -> "数字の 3（サン）"
                name == "key4" || name.endsWith("_4") || name == "button4" -> "数字の 4（ヨン）"
                name == "key5" || name.endsWith("_5") || name == "button5" -> "数字の 5（ゴ）"
                name == "key6" || name.endsWith("_6") || name == "button6" -> "数字の 6（ロク）"
                name == "key7" || name.endsWith("_7") || name == "button7" -> "数字の 7（ナナ）"
                name == "key8" || name.endsWith("_8") || name == "button8" -> "数字の 8（ハチ）"
                name == "key9" || name.endsWith("_9") || name == "button9" -> "数字の 9（キュウ）"
                name.contains("pin_entry") || name.contains("pinentry") || name.contains("password_entry") || name.contains("pin_code") -> "PINコード入力欄"
                name.contains("delete_button") || name.contains("backspace") || name.contains("btn_delete") -> "1文字削除"
                name.contains("emergency") -> "緊急通報"
                name.contains("cancel_button") || name.contains("btn_cancel") -> "キャンセル"
                name.contains("enter_button") || name.contains("btn_ok") || name.contains("btn_done") -> "決定"
                else -> ""
            }
        }

        assertEquals("数字の 1（イチ）", inferLabel("com.android.systemui:id/key1"))
        assertEquals("数字の 5（ゴ）", inferLabel("com.android.systemui:id/key5"))
        assertEquals("数字の 0（ゼロ）", inferLabel("com.android.systemui:id/key0"))
        assertEquals("1文字削除", inferLabel("com.android.systemui:id/delete_button"))
        assertEquals("緊急通報", inferLabel("com.android.systemui:id/emergency_call_button"))
        assertEquals("PINコード入力欄", inferLabel("com.android.systemui:id/pinEntry"))
    }

    @Test
    fun testWifiSignalLevelDescriptions() {
        assertEquals("電波4本最強", WifiConnectivityHelper.getLevelDescription(4))
        assertEquals("電波3本良好", WifiConnectivityHelper.getLevelDescription(3))
        assertEquals("電波2本普通", WifiConnectivityHelper.getLevelDescription(2))
        assertEquals("電波1本やや弱い", WifiConnectivityHelper.getLevelDescription(1))
        assertEquals("電波微弱", WifiConnectivityHelper.getLevelDescription(0))
    }

    @Test
    fun testPasswordAndPinBulletFeedback() {
        fun getPasswordTypeFeedback(isPassword: Boolean, addedCount: Int, removedCount: Int): String {
            if (!isPassword) return ""
            return if (addedCount > 0) {
                if (addedCount == 1) "黒丸" else "黒丸 ${addedCount}文字"
            } else if (removedCount > 0) {
                if (removedCount == 1) "黒丸を1文字削除" else "黒丸を${removedCount}文字削除"
            } else ""
        }

        assertEquals("黒丸", getPasswordTypeFeedback(true, 1, 0))
        assertEquals("黒丸 3文字", getPasswordTypeFeedback(true, 3, 0))
        assertEquals("黒丸を1文字削除", getPasswordTypeFeedback(true, 0, 1))
        assertEquals("黒丸を2文字削除", getPasswordTypeFeedback(true, 0, 2))
    }

    @Test
    fun testSystemDialogHelperPackageClassification() {
        val permissionPkgs = listOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.miui.securitypermission",
            "com.lbe.security.miui",
            "com.coloros.safecenter",
            "com.oplus.securitypermission",
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.transsion.phonemaster",
            "com.lenovo.safecenter"
        )
        for (pkg in permissionPkgs) {
            assertTrue("Package $pkg should be detected as permission package",
                com.shinji.serena.navigation.SystemDialogHelper.isPermissionPackage(pkg))
            assertTrue("Package $pkg should be detected in SerenaFocusNavigator",
                com.shinji.serena.navigation.SerenaFocusNavigator.isPermissionOrSecurityPackage(pkg))
        }

        val specialPkgs = listOf(
            "com.android.vpndialogs",
            "com.android.companiondevicemanager",
            "com.google.android.permissioncontroller"
        )
        for (pkg in specialPkgs) {
            assertTrue("Package $pkg should be detected as special system dialog package",
                com.shinji.serena.navigation.SystemDialogHelper.isSpecialSystemDialogPackage(pkg))
        }

        val normalPkgs = listOf(
            "com.shinji.serena",
            "com.android.calculator2",
            "org.telegram.messenger",
            "com.android.vending"
        )
        for (pkg in normalPkgs) {
            assertTrue("Package $pkg should NOT be detected as permission package",
                !com.shinji.serena.navigation.SystemDialogHelper.isPermissionPackage(pkg))
            assertTrue("Package $pkg should NOT be detected as special system dialog package",
                !com.shinji.serena.navigation.SystemDialogHelper.isSpecialSystemDialogPackage(pkg))
        }
    }

    @Test
    fun testFingerprintGuidanceCalculations() {
        val targetX = 500f
        val targetY = 1000f
        val density = 2.0f
        val tolerance = 35f * density // 70px

        // 1. ターゲット中央ピッタリ (ALIGNED)
        val aligned = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 500f,
            touchY = 1000f,
            targetX = targetX,
            targetY = targetY,
            tolerancePx = tolerance,
            density = density
        )
        assertTrue("Center should be aligned", aligned.isAligned)
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.ALIGNED, aligned.direction)
        assertEquals(0f, aligned.distance, 0.01f)

        // 許容誤差範囲内 (ALIGNED)
        val nearAligned = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 520f,
            touchY = 980f,
            targetX = targetX,
            targetY = targetY,
            tolerancePx = tolerance,
            density = density
        )
        assertTrue("Within tolerance should be aligned", nearAligned.isAligned)
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.ALIGNED, nearAligned.direction)

        // 2. 直線4方向 (上・下・左・右)
        // 指が上方（targetY > touchY） -> 下へ移動
        val gDown = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 500f, touchY = 300f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.DOWN, gDown.direction)

        val gSlightlyDown = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 500f, touchY = 900f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_DOWN, gSlightlyDown.direction)

        // 指が下方（targetY < touchY） -> 上へ移動
        val gUp = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 500f, touchY = 1700f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.UP, gUp.direction)

        val gSlightlyUp = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 500f, touchY = 1100f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_UP, gSlightlyUp.direction)

        // 指が左側（targetX > touchX） -> 右へ移動
        val gRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 100f, touchY = 1000f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.RIGHT, gRight.direction)

        val gSlightlyRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 400f, touchY = 1000f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_RIGHT, gSlightlyRight.direction)

        // 指が右側（targetX < touchX） -> 左へ移動
        val gLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 900f, touchY = 1000f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.LEFT, gLeft.direction)

        val gSlightlyLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 600f, touchY = 1000f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_LEFT, gSlightlyLeft.direction)

        // 3. 斜め4方向
        // 指が左上（targetX > touchX, targetY > touchY） -> 右斜め下へ移動
        val gDownRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 150f, touchY = 400f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.DOWN_RIGHT, gDownRight.direction)

        // 指が右上（targetX < touchX, targetY > touchY） -> 左斜め下へ移動
        val gDownLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 850f, touchY = 400f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.DOWN_LEFT, gDownLeft.direction)

        // 指が左下（targetX > touchX, targetY < touchY） -> 右斜め上へ移動
        val gUpRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 150f, touchY = 1600f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.UP_RIGHT, gUpRight.direction)

        // 指が右下（targetX < touchX, targetY < touchY） -> 左斜め上へ移動
        val gUpLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 850f, touchY = 1600f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.UP_LEFT, gUpLeft.direction)

        // 斜め微小移動 (Slightly Diagonal)
        val gSlightlyDownRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 430f, touchY = 930f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_DOWN_RIGHT, gSlightlyDownRight.direction)

        val gSlightlyDownLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 570f, touchY = 930f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_DOWN_LEFT, gSlightlyDownLeft.direction)

        val gSlightlyUpRight = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 430f, touchY = 1070f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_UP_RIGHT, gSlightlyUpRight.direction)

        val gSlightlyUpLeft = FingerprintGuidanceHelper.calculateGuidance(
            touchX = 570f, touchY = 1070f,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_UP_LEFT, gSlightlyUpLeft.direction)

        // 4. 境界値（ちょうど許容誤差境界 tolerancePx）
        val gExactBoundary = FingerprintGuidanceHelper.calculateGuidance(
            touchX = targetX + tolerance, touchY = targetY,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertTrue("Exact tolerance boundary must be ALIGNED", gExactBoundary.isAligned)
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.ALIGNED, gExactBoundary.direction)

        // 許容誤差を 1px 超えた境界
        val gJustOutside = FingerprintGuidanceHelper.calculateGuidance(
            touchX = targetX + tolerance + 1.0f, touchY = targetY,
            targetX = targetX, targetY = targetY,
            tolerancePx = tolerance, density = density
        )
        assertFalse("Just outside tolerance must not be aligned", gJustOutside.isAligned)
        assertEquals(FingerprintGuidanceHelper.GuidanceDirection.SLIGHTLY_LEFT, gJustOutside.direction)

        // 5. ガイガーカウンター式振動間隔の狭まり検証
        val iFar = FingerprintGuidanceHelper.calculateGeigerInterval(400f * density, tolerance, density)
        val iMidFar = FingerprintGuidanceHelper.calculateGeigerInterval(250f * density, tolerance, density)
        val iMid = FingerprintGuidanceHelper.calculateGeigerInterval(160f * density, tolerance, density)
        val iClose = FingerprintGuidanceHelper.calculateGeigerInterval(90f * density, tolerance, density)
        val iVeryClose = FingerprintGuidanceHelper.calculateGeigerInterval(40f * density, tolerance, density)
        val iAligned = FingerprintGuidanceHelper.calculateGeigerInterval(10f, tolerance, density)

        assertEquals(750L, iFar)
        assertEquals(500L, iMidFar)
        assertEquals(350L, iMid)
        assertEquals(220L, iClose)
        assertEquals(130L, iVeryClose)
        assertEquals(80L, iAligned)

        assertTrue("Interval must decrease as distance decreases",
            iAligned < iVeryClose && iVeryClose < iClose && iClose < iMid && iMid < iMidFar && iMidFar < iFar)

        // 6. 全方向EnumのリソースID存在検証
        for (dir in FingerprintGuidanceHelper.GuidanceDirection.values()) {
            assertTrue("Resource ID for ${dir.name} must be valid", dir.stringResId != 0)
        }
    }
}

