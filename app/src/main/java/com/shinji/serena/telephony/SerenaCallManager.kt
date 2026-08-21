package com.shinji.serena.telephony

import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.SerenaScreenReaderService
import com.shinji.serena.getSafeSharedPreferences
import com.shinji.serena.speech.SerenaSpeechEngine

/**
 * SerenaCallManager
 * 電話アプリ（標準キャリア電話）および全VoIP・メッセージングアプリ（LINE, Discord, Skype, WhatsApp, Messenger, Rakuten Link, Zoom等）対応
 * 通話開始時の音声ガイダンス・通話終了時の合計通話時間レポート・着信案内・2本指ダブルタップ応答/切断マネージャー
 */
class SerenaCallManager(
    private val service: SerenaScreenReaderService,
    private val speechEngine: SerenaSpeechEngine
) {

    companion object {
        private const val TAG = "SerenaCallManager"
    }

    private val telecomManager = service.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val telephonyManager = service.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val prefs = service.getSafeSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

    private var isCallActive = false
    private var activeCallApp: String = ""
    private var callerName: String = ""
    private var callStartTimeMs = 0L
    private var lastCallCheckTimeMs = 0L
    private var lastAnnouncedMinute = 0L

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    init {
        initTelephonyListener()
    }

    private fun initTelephonyListener() {
        try {
            if (telephonyManager == null) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleTelephonyCallState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(service.mainExecutor, callback)
                Log.i(TAG, "TelephonyCallback registered for API 31+.")
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleTelephonyCallState(state)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.i(TAG, "PhoneStateListener registered for legacy API.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "initTelephonyListener error (permission or device restriction): ${e.message}")
        }
    }

    private fun handleTelephonyCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.i(TAG, "Telephony: CALL_STATE_RINGING")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.i(TAG, "Telephony: CALL_STATE_OFFHOOK -> Call connected")
                onCallStarted("電話")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.i(TAG, "Telephony: CALL_STATE_IDLE -> Call ended")
                if (isCallActive && (activeCallApp == "電話" || activeCallApp.isEmpty())) {
                    onCallEnded("電話")
                }
            }
        }
    }

    fun onCallStarted(appName: String, extraInfo: String = "") {
        if (isCallActive) return
        isCallActive = true
        activeCallApp = if (appName.isNotEmpty()) appName else "電話"
        callStartTimeMs = SystemClock.elapsedRealtime()
        lastAnnouncedMinute = 0L
        service.soundHelper?.playActionDone()

        val extra = if (extraInfo.isNotEmpty()) "（${extraInfo}）" else ""
        service.speak("${activeCallApp}の通話を開始しました${extra}。", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
        Log.i(TAG, "onCallStarted: $activeCallApp at $callStartTimeMs")
    }

    fun onCallEnded(appName: String = activeCallApp) {
        if (!isCallActive) return
        isCallActive = false
        val elapsedMs = (SystemClock.elapsedRealtime() - callStartTimeMs).coerceAtLeast(1000L)
        val durationText = formatDuration(elapsedMs)
        val appLabel = if (appName.isNotEmpty()) appName else if (activeCallApp.isNotEmpty()) activeCallApp else "電話"
        
        service.soundHelper?.playActionDone()
        service.speak("${appLabel}の通話が終了しました。合計通話時間は ${durationText} でした。", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
        Log.i(TAG, "onCallEnded: $appLabel. Duration: $durationText")

        activeCallApp = ""
        callerName = ""
        callStartTimeMs = 0L
        lastAnnouncedMinute = 0L
    }

    fun checkCallState(pkgName: String, root: AccessibilityNodeInfo?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallCheckTimeMs < 800) return
        lastCallCheckTimeMs = now

        val isCallPkg = isCallRelatedPackage(pkgName)
        val currentAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        val isAudioInCall = currentAudioMode == AudioManager.MODE_IN_CALL || currentAudioMode == AudioManager.MODE_IN_COMMUNICATION

        if (!isCallPkg && !isAudioInCall && currentAudioMode != AudioManager.MODE_RINGTONE && !isCallActive) return

        val windowText = if (root != null) parseCallerFromNode(root) else ""

        // 着信中（プルルル…と鳴っている状態）の判定
        val isRinging = windowText.contains("着信") || windowText.contains("からの通話") || currentAudioMode == AudioManager.MODE_RINGTONE

        // 実際の通話接続中の判定（着信中を除く）
        val isCurrentlyInCall = !isRinging && (
            isAudioInCall ||
            windowText.contains("通話中") ||
            windowText.contains("通話時間") ||
            (isCallPkg && (windowText.contains("ミュート") || windowText.contains("スピーカー") || windowText.contains("切断") || windowText.contains("終話")))
        )

        // 1. 着信アナウンス（着信中のみ）
        if (isRinging && callerName.isEmpty() && windowText.isNotEmpty()) {
            callerName = extractNameFromText(windowText)
            val appLabel = getMessagingAppName(pkgName)
            if (callerName.isNotEmpty()) {
                service.speak("${appLabel}で${callerName}さんから着信です", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            } else {
                service.speak("${appLabel}から着信です", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            }
        }

        // 2. 通話開始判定（実際に相手と接続された瞬間からタイマースタート！）
        if (isCurrentlyInCall && !isCallActive) {
            val appLabel = getMessagingAppName(pkgName)
            val name = if (callerName.isNotEmpty()) "${callerName}さん" else ""
            onCallStarted(appLabel, name)
        } 
        // 3. 通話中の経過時間案内（設定でONになっている場合のみ、実測時間で1分ごとにアナウンス）
        else if (isCurrentlyInCall && isCallActive && callStartTimeMs > 0L) {
            val periodicEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_CALL_PERIODIC_ANNOUNCE, false)
            if (periodicEnabled) {
                val elapsedMs = SystemClock.elapsedRealtime() - callStartTimeMs
                val currentMinutes = (elapsedMs / 1000) / 60
                if (currentMinutes > 0 && currentMinutes > lastAnnouncedMinute) {
                    lastAnnouncedMinute = currentMinutes
                    val timeToAnnounce = formatDuration(elapsedMs)
                    service.speak("現在、通話時間 ${timeToAnnounce} 経過しています", android.speech.tts.TextToSpeech.QUEUE_ADD)
                }
            }
        }
        // 4. 通話終了判定 ＆ 正確な合計通話時間レポート
        else if (!isCurrentlyInCall && isCallActive && !isCallPkg && !isAudioInCall) {
            onCallEnded()
        }
    }

    fun announceCurrentCallDuration() {
        if (!isCallActive || callStartTimeMs <= 0L) {
            service.speak("現在、アクティブな通話はありません", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            return
        }
        val elapsedMs = SystemClock.elapsedRealtime() - callStartTimeMs
        val durationText = formatDuration(elapsedMs)
        service.speak("現在、${activeCallApp}の通話時間は ${durationText} 経過しています", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
    }

    fun isCallActive(): Boolean = isCallActive

    /**
     * 2本指ダブルタップ時に呼ばれるスマート判定通話ハンドラ
     * 通話中なら100%切断、着信中なら100%応答を実行する
     */
    fun handleSmartCallGesture(): Boolean {
        val root = service.rootInActiveWindow
        val windowText = if (root != null) parseCallerFromNode(root) else ""

        val isRinging = windowText.contains("着信") || windowText.contains("からの通話") || audioManager?.mode == AudioManager.MODE_RINGTONE
        val isCurrentlyInCall = isCallActive ||
            windowText.contains("通話中") ||
            windowText.contains("通話時間") ||
            audioManager?.mode == AudioManager.MODE_IN_CALL ||
            audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

        // 通話中であれば確実に切断を実行
        if (isCurrentlyInCall && !isRinging) {
            Log.i(TAG, "In call detected. Executing end call gesture.")
            return handleEndCallGesture()
        }

        // 着信中であれば確実に応答を実行
        if (isRinging) {
            Log.i(TAG, "Ringing call detected. Executing answer call gesture.")
            return handleAnswerCallGesture()
        }

        return false
    }

    fun handleAnswerCallGesture(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                if (audioManager?.mode == AudioManager.MODE_RINGTONE || telecomManager.isInCall) {
                    telecomManager.acceptRingingCall()
                    service.speak("通話を応答しました", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "acceptRingingCall permission error: ${e.message}")
        }

        val root = service.rootInActiveWindow ?: return false
        val answerNode = findCallAnswerNode(root)
        if (answerNode != null) {
            val clickableTarget = findClickableAncestor(answerNode) ?: answerNode
            val clicked = clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (!clicked) {
                val rect = Rect()
                clickableTarget.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    dispatchTapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
            }

            service.speak("通話を応答しました", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            return true
        }

        service.speak("応答ボタンが見つかりませんでした", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
        return false
    }

    fun handleEndCallGesture(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager != null) {
                if (isCallActive || audioManager?.mode == AudioManager.MODE_IN_CALL) {
                    if (telecomManager.endCall()) {
                        service.speak("通話を終了します", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
                        return true
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "endCall permission error: ${e.message}")
        }

        val root = service.rootInActiveWindow ?: return false
        val endNode = findCallEndNode(root)
        if (endNode != null) {
            val clickableTarget = findClickableAncestor(endNode) ?: endNode
            val clicked = clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (!clicked) {
                val rect = Rect()
                clickableTarget.getBoundsInScreen(rect)
                if (!rect.isEmpty) {
                    dispatchTapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
            }

            service.speak("通話を終了します", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            return true
        }

        service.speak("切断ボタンが見つかりませんでした", android.speech.tts.TextToSpeech.QUEUE_FLUSH)
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var count = 0
        while (current != null && count < 5) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
            count++
        }
        return null
    }

    private fun dispatchTapAt(x: Float, y: Float) {
        val path = android.graphics.Path().apply {
            moveTo(x, y)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}時間")
        if (minutes > 0 || hours > 0) parts.add("${minutes}分")
        parts.add("${seconds}秒")

        return parts.joinToString(" ")
    }

    private fun extractCallTimerFromText(fullText: String): String {
        val regex = Regex("(\\d{1,2}:\\d{2}(:\\d{2})?)")
        val match = regex.find(fullText) ?: return ""
        val timerStr = match.value
        val parts = timerStr.split(":")
        return when (parts.size) {
            3 -> "${parts[0]}時間${parts[1]}分${parts[2]}秒"
            2 -> "${parts[0]}分${parts[1]}秒"
            else -> timerStr
        }
    }

    private fun isCallRelatedPackage(pkgName: String): Boolean {
        val p = pkgName.lowercase()
        return p.contains("dialer") ||
               p.contains("phone") ||
               p.contains("incallui") ||
               p.contains("line") ||
               p.contains("skype") ||
               p.contains("whatsapp") ||
               p.contains("discord") ||
               p.contains("messenger") ||
               p.contains("rakuten") ||
               p.contains("zoom")
    }

    private fun getMessagingAppName(pkgName: String): String {
        val p = pkgName.lowercase()
        return when {
            p.contains("line") -> "LINE"
            p.contains("discord") -> "Discord"
            p.contains("skype") -> "Skype"
            p.contains("whatsapp") -> "WhatsApp"
            p.contains("messenger") -> "Messenger"
            p.contains("rakuten") -> "楽天リンク"
            p.contains("zoom") -> "Zoom"
            else -> "電話"
        }
    }

    private fun parseCallerFromNode(root: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val txt = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim() ?: ""
            if (txt.isNotEmpty() && node.isVisibleToUser) {
                texts.add(txt)
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return texts.joinToString(" ")
    }

    private fun extractNameFromText(fullText: String): String {
        val cleaned = fullText.replace("着信", "").replace("からの通話", "").replace("音声通話", "").trim()
        return if (cleaned.length in 1..20) cleaned else ""
    }

    fun release() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (phoneStateListener != null) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {}
        telephonyCallback = null
        phoneStateListener = null
    }

    private fun findCallAnswerNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isAnswerText = txt.contains("応答") || txt.contains("受話") || txt.contains("Answer") || txt.contains("Accept")
        val isAnswerId = viewId.contains("answer") || viewId.contains("accept") || viewId.contains("call_btn") || viewId.contains("incoming_call")

        if (isAnswerText || isAnswerId) {
            return node
        }

        for (i in 0 until node.childCount) {
            val res = findCallAnswerNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    private fun findCallEndNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isEndText = txt.contains("切断") || txt.contains("終話") || txt.contains("拒否") || txt.contains("End") || txt.contains("Decline") || txt.contains("Hang up")
        val isEndId = viewId.contains("decline") || viewId.contains("reject") || viewId.contains("hangup") || viewId.contains("end_call")

        if (isEndText || isEndId) {
            return node
        }

        for (i in 0 until node.childCount) {
            val res = findCallEndNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }
}
