package com.shinji.serena.gesture

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
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
        private const val GESTURE_DEBOUNCE_MS = 50L
    }

    private var lastGestureId = -1
    private var lastGestureTime = 0L

    fun onGesture(gestureId: Int): Boolean {
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
                navigator?.navigateLinearFocus(forward = true)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_LEFT -> {
                navigator?.navigateLinearFocus(forward = false)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_UP -> {
                if (service.handleVerticalSwipe(up = true)) return true
                navigator?.navigateLinearFocus(forward = false)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_DOWN -> {
                if (service.handleVerticalSwipe(up = false)) return true
                navigator?.navigateLinearFocus(forward = true)
                return true
            }

            // 1本指ダブルタップ & 長押し (クリック決定)
            AccessibilityService.GESTURE_DOUBLE_TAP -> {
                val menu = service.activeMenuDialog
                if (menu != null && menu.isShowing) {
                    if (menu.performCurrentItemClick()) {
                        service.soundHelper?.playClick()
                        return true
                    }
                }
                if (service.executeActiveCustomAction()) return true
                val focusNode = service.getAccessibilityFocusedNode() ?: service.lastHoveredNode
                if (focusNode != null) {
                    // 1. ノード直接のクリック試行
                    if (focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                        service.soundHelper?.playClick()
                        return true
                    }

                    // 2. 親祖先ノードを遡ってクリック試行
                    var parent = focusNode.parent
                    while (parent != null) {
                        if (parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                            service.soundHelper?.playClick()
                            return true
                        }
                        parent = parent.parent
                    }

                    // 3. 画面上の中心座標を取得して物理タップジェスチャーをエミュレート
                    val rect = android.graphics.Rect()
                    focusNode.getBoundsInScreen(rect)
                    val x = rect.centerX().toFloat()
                    val y = rect.centerY().toFloat()

                    if (rect.width() > 0 && rect.height() > 0) {
                        val path = android.graphics.Path().apply {
                            moveTo(x, y)
                        }
                        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
                        val gesture = android.accessibilityservice.GestureDescription.Builder()
                            .addStroke(stroke)
                            .build()
                        service.dispatchGesture(gesture, null, null)
                        service.soundHelper?.playClick()
                        return true
                    }
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
                service.speak("通知領域を開きました", TextToSpeech.QUEUE_FLUSH)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                service.speak("ホーム画面", TextToSpeech.QUEUE_FLUSH)
                return true
            }
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                service.speak("戻る", TextToSpeech.QUEUE_FLUSH)
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
                if (callMgr?.handleAnswerCallGesture() == true ||
                    callMgr?.handleEndCallGesture() == true) {
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
            AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT -> {
                // 2本指右フリック: 前のページへ
                service.scrollHorizontalBackward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT -> {
                // 2本指左フリック: 次のページへ
                service.scrollHorizontalForward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN -> {
                // 2本指下フリック: 前へ縦スクロール
                service.scrollVerticalBackward()
                return true
            }
            AccessibilityService.GESTURE_2_FINGER_SWIPE_UP -> {
                // 2本指上フリック: 次へ縦スクロール
                service.scrollVerticalForward()
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
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT,
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> {
                return false
            }

            else -> {
                Log.d(TAG, "Unhandled gesture ID: $gestureId")
                return false
            }
        }
    }
}
