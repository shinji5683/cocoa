package com.shinji.serena.gesture

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import com.shinji.serena.GranularityMode
import com.shinji.serena.R
import com.shinji.serena.SerenaScreenReaderService

/**
 * SerenaGestureDispatcher
 * Google TalkBack 最新仕様 (Android 14/15/16+) 100% 準拠のジェスチャーディスパッチエンジン
 */
class SerenaGestureDispatcher(
    private val service: SerenaScreenReaderService
) {
    companion object {
        private const val TAG = "SerenaGestureDispatcher"
        private const val GESTURE_DEBOUNCE_MS = 180L
        private const val CLICK_DEBOUNCE_MS = 250L
    }

    private var lastGestureId = -1
    private var lastGestureTime = 0L
    private var lastClickTime = 0L

    fun onGesture(gestureId: Int): Boolean {
        if (service.isInternalGestureDispatching) {
            Log.d(TAG, "Gesture $gestureId ignored: internal gesture dispatch in progress.")
            return false
        }
        val now = SystemClock.uptimeMillis()
        if (gestureId == lastGestureId && (now - lastGestureTime) < GESTURE_DEBOUNCE_MS) {
            Log.d(TAG, "Gesture $gestureId debounced.")
            return false
        }

        lastGestureId = gestureId
        lastGestureTime = now

        Log.i(TAG, "onGesture detected: $gestureId")
        return dispatchGestureId(gestureId)
    }

    private fun dispatchGestureId(gestureId: Int): Boolean {
        val navigator = service.focusNavigator
        val callMgr = service.callManager

        when (gestureId) {
            // 1本指直線スワイプ
            AccessibilityService.GESTURE_SWIPE_RIGHT -> {
                service.navigateLinearFocus(forward = true)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_LEFT -> {
                service.navigateLinearFocus(forward = false)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_UP -> {
                // 1本指上フリック: 読み上げコントロール（粒度: 文字/単語/行/見出し等）に合わせて前へ移動
                service.focusPrevious()
                return true
            }
            AccessibilityService.GESTURE_SWIPE_DOWN -> {
                // 1本指下フリック: 読み上げコントロール（粒度: 文字/単語/行/見出し等）に合わせて次へ移動
                service.focusNext()
                return true
            }

            // 1本指ダブルタップ & 長押し (クリック決定)
            AccessibilityService.GESTURE_DOUBLE_TAP -> {
                val clickNow = SystemClock.uptimeMillis()
                if (clickNow - lastClickTime < CLICK_DEBOUNCE_MS) {
                    Log.d(TAG, "Double tap click debounced.")
                    return true
                }
                lastClickTime = clickNow
                val menu = service.activeMenuDialog
                if (menu != null && menu.isShowing) {
                    if (menu.performCurrentItemClick()) {
                        service.soundHelper?.playClick()
                        return true
                    }
                }
                
                // カスタムアクション粒度モード時のみカスタムアクションを実行
                if (service.getCurrentGranularity() == GranularityMode.ACTIONS) {
                    if (service.executeActiveCustomAction()) return true
                }

                val focusNode = service.getAccessibilityFocusedNode() ?: service.lastHoveredNode
                if (focusNode != null) {
                    val isPinOrKeyboard = service.isKeyboardOrPinKeyNode(focusNode)
                    val rawText = service.getNodeText(focusNode)
                    val viewId = focusNode.viewIdResourceName?.lowercase() ?: ""
                    val isLockElement = !isPinOrKeyboard && (
                            viewId.contains("lock_icon") || viewId.contains("element:lockscreen") ||
                            rawText.contains("ロック解除") || rawText.contains("スワイプしてロック解除")
                    )
                    if (service.isKeyguardLocked() && isLockElement) {
                        service.unlockKeyguardOrShowBouncer()
                        return true
                    }

                    if (isPinOrKeyboard) {
                        // PIN数字キー・キーボードキーの場合: 直近クリックまたは物理座標クリックを発行
                        val clicked = focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        if (!clicked) {
                            service.clickNodeByGesture(focusNode)
                        }
                        service.soundHelper?.playClick()
                        return true
                    }

                    val cleanText = rawText.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Extended_Pictographic}\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u26FF\u2700-\u27BF]"), "").replace(Regex("\\s+"), " ").trim()
                    val announceText = if (cleanText.isNotEmpty()) "$cleanText を実行" else "実行"

                    // 1. 直近ノードの ACTION_CLICK
                    val directSuccess = focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    if (directSuccess) {
                        service.soundHelper?.playClick()
                        if (SerenaScreenReaderService.isOperationActionsAnnounceEnabled(service)) {
                            service.speak(announceText, TextToSpeech.QUEUE_FLUSH)
                        }
                        return true
                    }

                    // 2. 親・祖先ノードの ACTION_CLICK 探索
                    var parent = focusNode.parent
                    while (parent != null) {
                        if (parent.isClickable || parent.isCheckable) {
                            if (parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                                service.soundHelper?.playClick()
                                if (SerenaScreenReaderService.isOperationActionsAnnounceEnabled(service)) {
                                    service.speak(announceText, TextToSpeech.QUEUE_FLUSH)
                                }
                                return true
                            }
                        }
                        parent = parent.parent
                    }

                    // 3. ACTION_SELECT
                    if (focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SELECT)) {
                        service.soundHelper?.playClick()
                        service.speak(announceText, TextToSpeech.QUEUE_FLUSH)
                        return true
                    }

                    // 4. 物理座標タップジェスチャーの発行（Compose/YouTube Music/カスタム描画/ゲーム/Webビュー等でも1発で確実に反応！）
                    service.clickNodeByGesture(focusNode)
                    service.soundHelper?.playClick()
                    service.speak(announceText, TextToSpeech.QUEUE_FLUSH)
                    return true
                }

                return false
            }
            AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD -> {
                val focusNode = service.getAccessibilityFocusedNode()
                if (focusNode != null) {
                    val handled = focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    service.soundHelper?.playClick()
                    return handled
                }
                return false
            }

            // 1本指 L字角形ジェスチャー
            AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                service.speak(service.getString(R.string.gesture_notifications), TextToSpeech.QUEUE_FLUSH)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> {
                service.dismissActiveMenu()
                service.soundHelper?.playClick()
                service.speak(service.getString(R.string.gesture_home), TextToSpeech.QUEUE_FLUSH)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> {
                if (service.dismissActiveMenu()) {
                    return true
                }
                service.soundHelper?.playClick()
                service.speak(service.getString(R.string.gesture_back), TextToSpeech.QUEUE_FLUSH)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT,
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT -> {
                service.showNormalSerenaMenu()
                return true
            }

            // 2本指ジェスチャー
            AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP -> {
                // 2本指シングルタップ: 読み上げの一時停止と再開
                service.toggleSpeechPauseResume()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP -> {
                // 2本指ダブルタップ: 通話の応答・切断 / メディアの再生・一時停止
                if (callMgr?.handleSmartCallGesture() == true) {
                    return true
                }
                service.handleMagicTapAction()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP -> {
                // 2本指トリプルタップ: 音声読み上げのミュート（消音）とミュート解除
                service.toggleSpeechMute()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT, 27 -> {
                if (service.isKeyguardLocked()) {
                    service.unlockKeyguardOrShowBouncer()
                    return true
                }
                // 2本指左スワイプ → 右スクロール（次のページへ）
                Log.i(TAG, "2-Finger swipe LEFT ($gestureId) -> scrollHorizontalForward (次のページへ)")
                service.scrollHorizontalForward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT, 28 -> {
                if (service.isKeyguardLocked()) {
                    service.unlockKeyguardOrShowBouncer()
                    return true
                }
                // 2本指右スワイプ → 左スクロール（前のページへ）
                Log.i(TAG, "2-Finger swipe RIGHT ($gestureId) -> scrollHorizontalBackward (前のページへ)")
                service.scrollHorizontalBackward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_UP, 25 -> {
                // 2本指上スワイプ → 下スクロール（次の行/コンテンツへ）
                Log.i(TAG, "2-Finger swipe UP ($gestureId) -> scrollVerticalForward (下スクロール)")
                service.scrollVerticalForward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN, 26 -> {
                // 2本指下スワイプ → 上スクロール（前の行/コンテンツへ）
                Log.i(TAG, "2-Finger swipe DOWN ($gestureId) -> scrollVerticalBackward (上スクロール)")
                service.scrollVerticalBackward()
                return true
            }

            // 3本指ジェスチャー
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP -> {
                // 3本指シングルタップ: Serena メニュー起動！
                service.showNormalSerenaMenu()
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP -> {
                // 3本指ダブルタップ: 現在状態アナウンス
                service.announceFullStatus()
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP -> {
                // 3本指トリプルタップ: クリップボードコピー
                service.copyLastSpokenTextToClipboard()
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> {
                // 3本指上フリック: 読み上げコントロール（粒度）を前に移動！
                service.cycleGranularity(forward = false)
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN -> {
                // 3本指下フリック: 読み上げコントロール（粒度）を次に移動！
                service.cycleGranularity(forward = true)
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> {
                // 3本指左フリック: オンデバイスAI画面要約（クイックブリーフィング）を即時実行！
                service.summarizeCurrentScreen()
                return true
            }
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> {
                // 3本指右フリック: Serena Eyes（リアルタイムAI視覚＆実況カメラ）を即時起動！
                service.launchSerenaEyes()
                return true
            }

            else -> {
                Log.d(TAG, "Unhandled gesture ID: $gestureId")
                return false
            }
        }
    }
}
