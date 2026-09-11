package com.shinji.serena

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * MorningSummaryHelper
 * 朝一番（5:00〜11:00）の画面ロック解除時に、Shinjiさんへ爽やかな笑顔のモーニングサマリーを届ける！
 * 「おはよう、Shinjiさん！✨ バッテリーは〇パーセント、未読通知は〇件あるよ！」
 */
class MorningSummaryHelper(
    private val service: SerenaScreenReaderService
) {

    companion object {
        private const val PREFS_NAME = "serena_morning_summary_prefs"
        private const val KEY_LAST_GREETING_DATE = "last_greeting_date"
    }

    private val prefs: SharedPreferences = service.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 画面ロック解除時または画面点灯時に呼び出し、朝の初回であれば元気に挨拶！
     */
    fun checkAndAnnounceMorningSummary() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // 朝の時間帯（5:00〜10:59）に限定
        if (hour !in 5..10) return

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date())
        val lastGreetingDate = prefs.getString(KEY_LAST_GREETING_DATE, "")

        if (lastGreetingDate == todayStr) {
            // 本日すでに朝の挨拶済み
            return
        }

        // 挨拶記録を保存
        prefs.edit().putString(KEY_LAST_GREETING_DATE, todayStr).apply()

        // バッテリー残量
        var batteryPct = 100
        try {
            val bm = service.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            }
        } catch (_: Exception) {}

        service.soundHelper?.playActionDone()

        val greetingText = service.getString(R.string.morning_greeting_fmt, batteryPct)
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            service.speak(greetingText, TextToSpeech.QUEUE_ADD)
        }, 500)
    }
}
