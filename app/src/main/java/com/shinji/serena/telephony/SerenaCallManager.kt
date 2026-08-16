package com.shinji.serena.telephony

import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.SerenaScreenReaderService
import com.shinji.serena.speech.SerenaSpeechEngine

/**
 * SerenaCallManager
 * 電話アプリ（標準電話）および全メッセージングアプリ（LINE, Discord, Skype, WhatsApp, Messenger, Rakuten Link等）対応
 * 着信分離・リアルタイム画面タイマー読み取り・通話時間計測・合計通話時間レポート・2本指ダブルタップ応答/切断マネージャー
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

    private var isCallActive = false
    private var activeCallApp: String = ""
    private var callerName: String = ""
    private var callStartTimeMs = 0L
    private var lastCallCheckTimeMs = 0L
    private var lastAnnouncedMinute = 0L

    fun checkCallState(pkgName: String, root: AccessibilityNodeInfo?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallCheckTimeMs < 1000) return
        lastCallCheckTimeMs = now

        val isCallPkg = isCallRelatedPackage(pkgName)
        if (!isCallPkg && audioManager?.mode != AudioManager.MODE_IN_CALL && audioManager?.mode != AudioManager.MODE_RINGTONE) return

        val windowText = if (root != null) parseCallerFromNode(root) else ""

        // 着信中（プルルル…と鳴っている状態）の判定
        val isRinging = windowText.contains("着信") || windowText.contains("からの通話") || audioManager?.mode == AudioManager.MODE_RINGTONE

        // 画面ノードから本物の通話タイマーテキスト（「01:23」や「01:15:20」など）を抽出
        val uiTimerText = extractCallTimerFromText(windowText)

        // 実際の通話接続中の判定（着信中を除く！）
        val isCurrentlyInCall = !isRinging && (
            uiTimerText.isNotEmpty() ||
            windowText.contains("通話中") ||
            windowText.contains("通話時間") ||
            audioManager?.mode == AudioManager.MODE_IN_CALL
        )

        // 1. 着信アナウンス（着信中のみ）
        if (isRinging && callerName.isEmpty() && windowText.isNotEmpty()) {
            callerName = extractNameFromText(windowText)
            val appLabel = getMessagingAppName(pkgName)
            if (callerName.isNotEmpty()) {
                speechEngine.speak("${appLabel}で${callerName}さんから着信です", interrupt = true)
            } else {
                speechEngine.speak("${appLabel}から着信です", interrupt = true)
            }
        }

        // 2. 通話開始判定（実際に相手と接続された瞬間からタイマースタート！）
        if (isCurrentlyInCall && !isCallActive) {
            isCallActive = true
            activeCallApp = getMessagingAppName(pkgName)
            callStartTimeMs = SystemClock.elapsedRealtime()
            lastAnnouncedMinute = 0L
            val targetName = if (callerName.isNotEmpty()) "（${callerName}さん）" else ""
            speechEngine.speak("${activeCallApp}の通話を開始しました${targetName}", interrupt = true)
            Log.i(TAG, "Call connected ($activeCallApp) at $callStartTimeMs")
        } 
        // 3. 通話中の経過時間案内（1分ごとのオート経過アナウンス）
        else if (isCurrentlyInCall && isCallActive) {
            val elapsedMs = SystemClock.elapsedRealtime() - callStartTimeMs
            val currentMinutes = (elapsedMs / 1000) / 60
            if (currentMinutes > 0 && currentMinutes > lastAnnouncedMinute) {
                lastAnnouncedMinute = currentMinutes
                val timeToAnnounce = if (uiTimerText.isNotEmpty()) uiTimerText else "${currentMinutes}分"
                speechEngine.speak("現在、通話時間 ${timeToAnnounce} 経過しています", interrupt = false)
            }
        }
        // 4. 通話終了判定 ＆ 正確な合計通話時間レポート
        else if (!isCurrentlyInCall && isCallActive) {
            isCallActive = false
            val elapsedMs = SystemClock.elapsedRealtime() - callStartTimeMs
            val durationText = if (uiTimerText.isNotEmpty()) uiTimerText else formatDuration(elapsedMs)
            val appLabel = if (activeCallApp.isNotEmpty()) activeCallApp else "通話"
            
            speechEngine.speak("${appLabel}の通話が終了しました。合計通話時間は ${durationText} でした。", interrupt = true)
            Log.i(TAG, "$appLabel ended. Duration: $durationText")
            
            // ピカピカに初期化
            activeCallApp = ""
            callerName = ""
            callStartTimeMs = 0L
            lastAnnouncedMinute = 0L
        }
    }

    fun announceCurrentCallDuration() {
        if (!isCallActive) {
            speechEngine.speak("現在、アクティブな通話はありません", interrupt = true)
            return
        }
        val root = service.rootInActiveWindow
        val windowText = if (root != null) parseCallerFromNode(root) else ""
        val uiTimerText = extractCallTimerFromText(windowText)

        val durationText = if (uiTimerText.isNotEmpty()) {
            uiTimerText
        } else {
            val elapsedMs = SystemClock.elapsedRealtime() - callStartTimeMs
            formatDuration(elapsedMs)
        }
        speechEngine.speak("現在、${activeCallApp}の通話時間は ${durationText} 経過しています", interrupt = true)
    }

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
        // 1. TelecomManager による直接応答
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                if (audioManager?.mode == AudioManager.MODE_RINGTONE || telecomManager.isInCall) {
                    telecomManager.acceptRingingCall()
                    speechEngine.speak("通話を応答しました", interrupt = true)
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "acceptRingingCall permission error: ${e.message}")
        }

        // 2. UIノード検索 ＆ 親要素トラバース ＆ 物理タッチエミュレーション
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

            speechEngine.speak("通話を応答しました", interrupt = true)
            return true
        }

        speechEngine.speak("応答ボタンが見つかりませんでした", interrupt = true)
        return false
    }

    fun handleEndCallGesture(): Boolean {
        // 1. TelecomManager による直接切断
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager != null) {
                if (isCallActive || audioManager?.mode == AudioManager.MODE_IN_CALL) {
                    if (telecomManager.endCall()) {
                        speechEngine.speak("通話を終了します", interrupt = true)
                        return true
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "endCall permission error: ${e.message}")
        }

        // 2. UIノード検索 ＆ 親要素トラバース ＆ 物理タッチエミュレーション
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

            speechEngine.speak("通話を終了します", interrupt = true)
            return true
        }

        speechEngine.speak("切断ボタンが見つかりませんでした", interrupt = true)
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
