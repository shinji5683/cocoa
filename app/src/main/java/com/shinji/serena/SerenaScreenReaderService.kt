package com.shinji.serena

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

enum class GranularityMode(val displayName: String) {
    DEFAULT("デフォルト"),
    HEADINGS("見出し"),
    CONTROLS("コントロール"),
    LINKS("リンク"),
    LINES("行"),
    PARAGRAPHS("段落"),
    WORDS("単語"),
    CHARACTERS("文字")
}

class serenaScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "serenaScreenReader"
        const val PREFS_NAME = "serena_prefs"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_SPEECH_PITCH = "speech_pitch"
        const val KEY_HOURLY_CHIME_ENABLED = "hourly_chime_enabled"
        const val KEY_TALKBACK_MODE = "key_talkback_mode"
        const val KEY_CHIME_STYLE = "key_chime_style"
        const val CHIME_STYLE_NHK = "nhk_radio"
        const val CHIME_STYLE_CUTE = "cute_beep"
        const val CHIME_STYLE_BELL = "japanese_bell"

        var instance: serenaScreenReaderService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0L
    private lateinit var prefs: SharedPreferences
    private var soundHelper: SoundAndHapticHelper? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var currentGranularity: GranularityMode = GranularityMode.DEFAULT
    private var screenCurtainHelper: ScreenCurtainHelper? = null
    private var statusHelper: StatusAnnouncementHelper? = null
    private var ocrHelper: OcrCameraHelper? = null
    private var clipboardHelper: ClipboardHistoryHelper? = null
    private var notificationFilterHelper: SmartNotificationFilterHelper? = null
    private var appProfileHelper: AppProfileHelper? = null
    private var emojiHelper: EmojiAndKaomojiHelper? = EmojiAndKaomojiHelper()
    private var faceHelper: FaceDetectionHelper? = null
    private var objectHelper: ObjectRecognitionHelper? = null
    private var gemmaDownloadHelper: GemmaModelDownloadHelper? = null
    private var assistantHelper: serenaAiAssistantHelper? = null
    private var shakeDetectorHelper: ShakeDetectorHelper? = null
    private var spatialHapticTouchMapHelper: SpatialHapticTouchMapHelper? = null
    private var isLiveEnvironmentModeActive = false
    private var liveEnvironmentHandler: android.os.Handler? = null
    private var liveEnvironmentRunnable: Runnable? = null

    // 通話時間計測用
    private var isCallActive = false
    private var activeCallApp: String = ""
    private var callStartTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createDeviceProtectedStorageContext()
        } else {
            this
        }
        prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundHelper = SoundAndHapticHelper(this)
        screenCurtainHelper = ScreenCurtainHelper(this)
        statusHelper = StatusAnnouncementHelper(this)
        ocrHelper = OcrCameraHelper(this)
        clipboardHelper = ClipboardHistoryHelper(this)
        notificationFilterHelper = SmartNotificationFilterHelper(this)
        appProfileHelper = AppProfileHelper()
        faceHelper = FaceDetectionHelper(this)
        objectHelper = ObjectRecognitionHelper(this)
        gemmaDownloadHelper = GemmaModelDownloadHelper(this)
        assistantHelper = serenaAiAssistantHelper(this)

        shakeDetectorHelper = ShakeDetectorHelper(this) {
            announceFullStatus()
        }.apply { start() }

        soundHelper?.let {
            spatialHapticTouchMapHelper = SpatialHapticTouchMapHelper(it)
        }
        initTts()
        registerTimeTickReceiver()
        Log.i(TAG, "serena ScreenReaderService created.")
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            updateTtsSettings()
            isTtsReady = true
            val isTalkBackMode = prefs.getBoolean(KEY_TALKBACK_MODE, false)
            val welcomeMsg = if (isTalkBackMode) "TalkBack互換モードで serena が起動しました。" else "ほっと一息、serena スクリーンリーダーが起動しました。"
            speak(welcomeMsg, TextToSpeech.QUEUE_FLUSH)
            soundHelper?.playMenuOpen()
            Log.i(TAG, "TTS initialized successfully.")
        } else {
            Log.e(TAG, "TTS Initialization failed.")
        }
    }

    fun updateTtsSettings() {
        val rate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        val pitch = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f)
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
        info.notificationTimeout = 100
        var flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
        }
        info.flags = flags
        serviceInfo = info
        Log.i(TAG, "serena AccessibilityService connected.")
    }

    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val gestureId = gestureEvent.gestureId
            if (handleGestureId(gestureId)) {
                return true
            }
        }
        return super.onGesture(gestureEvent)
    }

    @Deprecated("Deprecated in API 30+")
    override fun onGesture(gestureId: Int): Boolean {
        if (handleGestureId(gestureId)) {
            return true
        }
        @Suppress("DEPRECATION")
        return super.onGesture(gestureId)
    }

    private fun handleGestureId(gestureId: Int): Boolean {
        Log.i(TAG, "handleGestureId detected: $gestureId")
        AlphaTelemetryHelper.getInstance(this).incrementGestureCount()

        when (gestureId) {
            // 右スワイプ / 右フリック: 次の項目/粒度移動
            GESTURE_SWIPE_RIGHT -> {
                soundHelper?.playFocusMove()
                focusNext()
                return true
            }
            // 左スワイプ / 左フリック: 前の項目/粒度移動
            GESTURE_SWIPE_LEFT -> {
                soundHelper?.playFocusMove()
                focusPrevious()
                return true
            }
            // 上スワイプ / 上フリック: 読み上げコントロール（粒度）切り替え（前へ）
            GESTURE_SWIPE_UP -> {
                cycleGranularity(forward = false)
                return true
            }
            // 下スワイプ / 下フリック: 読み上げコントロール（粒度）切り替え（次へ）
            GESTURE_SWIPE_DOWN -> {
                cycleGranularity(forward = true)
                return true
            }
            // 2本指上スワイプ (19, 27): 下へスクロール (次ページ)
            19, 27 -> {
                scrollPageForward()
                return true
            }
            // 2本指下スワイプ (20, 28): 上へスクロール (前ページ)
            20, 28 -> {
                scrollPageBackward()
                return true
            }
            // 1本指ダブルタップ: フォーカス中要素のクリック実行
            GESTURE_DOUBLE_TAP -> {
                soundHelper?.playClick()
                performClickOnFocusedNode()
                return true
            }
            // 2本指タップ / マジックタップ (25, 26, 29): 着信応答・通話切断・メディア再生/一時停止
            25, 26, 29 -> {
                soundHelper?.playActionDone()
                handleMagicTapAction()
                return true
            }
            // 2本指トリプルタップ (30) または 3本指ダブルタップ (32): 耳元ささやきステータスチェック
            30, 32 -> {
                announceFullStatus()
                return true
            }
            // 3本指タップ (31, 33) または L字スワイプ (上→右, 下→右): serena メニュー (TalkBack標準互換)
            31, 33, GESTURE_SWIPE_UP_AND_RIGHT, GESTURE_SWIPE_DOWN_AND_RIGHT -> {
                soundHelper?.playMenuOpen()
                triggerserenaMenu()
                return true
            }
            // 下→左スワイプ: 戻るボタン
            GESTURE_SWIPE_DOWN_AND_LEFT -> {
                soundHelper?.playClick()
                speak("戻る", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
            // 上→左スワイプ: ホーム画面
            GESTURE_SWIPE_UP_AND_LEFT -> {
                soundHelper?.playClick()
                speak("ホーム画面", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
            // ダブルタップ長押し: 読み上げ停止
            GESTURE_DOUBLE_TAP_AND_HOLD -> {
                stopSpeech()
                return true
            }
        }
        return false
    }

    fun cycleGranularity(forward: Boolean) {
        val values = GranularityMode.values()
        val currentIndex = currentGranularity.ordinal
        val nextIndex = if (forward) {
            (currentIndex + 1) % values.size
        } else {
            if (currentIndex - 1 < 0) values.size - 1 else currentIndex - 1
        }
        currentGranularity = values[nextIndex]
        soundHelper?.playActionDone()
        speak("読み上げコントロール: ${currentGranularity.displayName}", TextToSpeech.QUEUE_FLUSH)
    }

    private fun getAccessibilityFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        return root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    fun scrollPageForward() {
        val focusNode = getAccessibilityFocusedNode() ?: rootInActiveWindow
        var scrollableNode: AccessibilityNodeInfo? = focusNode
        while (scrollableNode != null && !scrollableNode.isScrollable) {
            scrollableNode = scrollableNode.parent
        }

        if (scrollableNode != null && scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            soundHelper?.playFocusMove()
            speak("次のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            return
        }

        performSwipeGesture(swipeUp = true)
    }

    fun scrollPageBackward() {
        val focusNode = getAccessibilityFocusedNode() ?: rootInActiveWindow
        var scrollableNode: AccessibilityNodeInfo? = focusNode
        while (scrollableNode != null && !scrollableNode.isScrollable) {
            scrollableNode = scrollableNode.parent
        }

        if (scrollableNode != null && scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            soundHelper?.playFocusMove()
            speak("前のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            return
        }

        performSwipeGesture(swipeUp = false)
    }

    private fun performSwipeGesture(swipeUp: Boolean) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY = if (swipeUp) height * 0.75f else height * 0.25f
        val endY = if (swipeUp) height * 0.25f else height * 0.75f

        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                soundHelper?.playFocusMove()
                val dirStr = if (swipeUp) "次" else "前"
                speak("${dirStr}の画面にスクロールしました", TextToSpeech.QUEUE_FLUSH)
            }
        }, null)
    }

    private fun focusNext() {
        when (currentGranularity) {
            GranularityMode.DEFAULT -> navigateLinearFocus(forward = true)
            GranularityMode.HEADINGS -> navigateFilteredFocus(forward = true) { it.isHeading || getNodeRole(it) == "見出し" }
            GranularityMode.CONTROLS -> navigateFilteredFocus(forward = true) { it.isClickable || it.isCheckable || it.isFocusable }
            GranularityMode.LINKS -> navigateFilteredFocus(forward = true) { isLinkNode(it) }
            GranularityMode.LINES -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, forward = true)
            GranularityMode.PARAGRAPHS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH, forward = true)
            GranularityMode.WORDS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, forward = true)
            GranularityMode.CHARACTERS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, forward = true)
        }
    }

    private fun focusPrevious() {
        when (currentGranularity) {
            GranularityMode.DEFAULT -> navigateLinearFocus(forward = false)
            GranularityMode.HEADINGS -> navigateFilteredFocus(forward = false) { it.isHeading || getNodeRole(it) == "見出し" }
            GranularityMode.CONTROLS -> navigateFilteredFocus(forward = false) { it.isClickable || it.isCheckable || it.isFocusable }
            GranularityMode.LINKS -> navigateFilteredFocus(forward = false) { isLinkNode(it) }
            GranularityMode.LINES -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, forward = false)
            GranularityMode.PARAGRAPHS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH, forward = false)
            GranularityMode.WORDS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, forward = false)
            GranularityMode.CHARACTERS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, forward = false)
        }
    }

    private fun isLinkNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        if (className.contains("Link", ignoreCase = true) || className.contains("URL", ignoreCase = true)) return true
        val text = getNodeText(node)
        return text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")
    }

    private fun navigateLinearFocus(forward: Boolean) {
        val root = rootInActiveWindow ?: return
        val nodes = collectAccessibleNodes(root)
        if (nodes.isEmpty()) return

        val currentFocus = getAccessibilityFocusedNode()
        var currentIndex = -1
        if (currentFocus != null) {
            currentIndex = nodes.indexOfFirst { isSameNode(it, currentFocus) }
        }

        val targetIndex = if (forward) {
            if (currentIndex < 0 || currentIndex >= nodes.size - 1) 0 else currentIndex + 1
        } else {
            if (currentIndex <= 0) nodes.size - 1 else currentIndex - 1
        }

        val targetNode = nodes[targetIndex]
        currentFocus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (success) {
            announceNode(targetNode)
        }
    }

    private fun navigateFilteredFocus(forward: Boolean, filter: (AccessibilityNodeInfo) -> Boolean) {
        val root = rootInActiveWindow ?: return
        val allNodes = collectAccessibleNodes(root)
        val filteredNodes = allNodes.filter { filter(it) }

        if (filteredNodes.isEmpty()) {
            speak("${currentGranularity.displayName}は見つかりませんでした", TextToSpeech.QUEUE_FLUSH)
            return
        }

        val currentFocus = getAccessibilityFocusedNode()
        var currentIndex = -1
        if (currentFocus != null) {
            currentIndex = filteredNodes.indexOfFirst { isSameNode(it, currentFocus) }
        }

        val targetIndex = if (forward) {
            if (currentIndex < 0 || currentIndex >= filteredNodes.size - 1) 0 else currentIndex + 1
        } else {
            if (currentIndex <= 0) filteredNodes.size - 1 else currentIndex - 1
        }

        val targetNode = filteredNodes[targetIndex]
        currentFocus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (success) {
            speak("${currentGranularity.displayName}: ", TextToSpeech.QUEUE_FLUSH)
            announceNode(targetNode)
        }
    }

    private fun moveByGranularity(granularity: Int, forward: Boolean) {
        val focusNode = getAccessibilityFocusedNode() ?: return
        val action = if (forward) AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY else AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity)
        }

        val success = focusNode.performAction(action, args)
        if (!success) {
            navigateLinearFocus(forward)
        } else {
            announceNode(focusNode)
        }
    }

    private fun isSameNode(node1: AccessibilityNodeInfo, node2: AccessibilityNodeInfo): Boolean {
        if (node1 == node2) return true
        val b1 = android.graphics.Rect()
        val b2 = android.graphics.Rect()
        node1.getBoundsInScreen(b1)
        node2.getBoundsInScreen(b2)
        return b1 == b2 && getNodeText(node1) == getNodeText(node2)
    }

    private fun collectAccessibleNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        traverseTree(root, list)
        return list
    }

    private fun traverseTree(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return

        val hasAccessibleChildren = hasInteractiveOrTextChildren(node)
        if (isFocusableTarget(node, hasAccessibleChildren)) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseTree(child, list)
        }
    }

    private fun isFocusableTarget(node: AccessibilityNodeInfo, hasAccessibleChildren: Boolean): Boolean {
        if (!node.isVisibleToUser) return false
        // 親コンテナが配下にフォーカス可能な子要素を持つ場合、親自体はターゲットにせず子要素を順に訪問する
        if (node.childCount > 0 && hasAccessibleChildren) {
            return false
        }
        val text = getNodeText(node)
        val isActionable = node.isClickable || node.isCheckable || node.isFocusable || node.isLongClickable || node.isHeading
        return isActionable || text.isNotEmpty()
    }

    private fun hasInteractiveOrTextChildren(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            val childText = getNodeText(child)
            if (child.isClickable || child.isCheckable || child.isFocusable || childText.isNotEmpty()) {
                return true
            }
            if (hasInteractiveOrTextChildren(child)) {
                return true
            }
        }
        return false
    }

    private fun performClickOnFocusedNode() {
        val focusedNode = getAccessibilityFocusedNode()
        if (focusedNode == null) {
            Log.w(TAG, "performClickOnFocusedNode: No focused node found.")
            return
        }

        // 1. 親階層を探索して Clickable または Checkable な要素をアタック
        var target: AccessibilityNodeInfo? = focusedNode
        while (target != null) {
            if (target.isClickable || target.isCheckable) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val text = getNodeText(focusedNode)
                    if (text.isNotEmpty()) speak("$text を実行", TextToSpeech.QUEUE_FLUSH)
                    return
                }
            }
            target = target.parent
        }

        // 2. 直近ノードへの ACTION_CLICK 直撃
        if (focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val text = getNodeText(focusedNode)
            if (text.isNotEmpty()) speak("$text を実行", TextToSpeech.QUEUE_FLUSH)
            return
        }

        // 3. ACTION_SELECT の試行
        if (focusedNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)) {
            val text = getNodeText(focusedNode)
            if (text.isNotEmpty()) speak("$text 選択", TextToSpeech.QUEUE_FLUSH)
            return
        }

        // 4. 強力なフォールバック: 要素中央の画面座標へ物理タッチジェスチャーを発行
        clickNodeByGesture(focusedNode)
    }

    private fun clickNodeByGesture(node: AccessibilityNodeInfo) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) return

        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        val path = android.graphics.Path().apply {
            moveTo(centerX, centerY)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                val label = getNodeText(node)
                if (label.isNotEmpty()) speak("$label をタップ実行", TextToSpeech.QUEUE_FLUSH)
            }
        }, null)
    }

    fun triggerserenaMenu() {
        val focusNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val isEditable = focusNode != null && (focusNode.isEditable || focusNode.className?.contains("EditText", ignoreCase = true) == true)

        if (isEditable && focusNode != null) {
            showEditTextAssistMenu(focusNode)
        } else {
            showNormalserenaMenu()
        }
    }

    private fun showNormalserenaMenu() {
        soundHelper?.playMenuOpen()
        speak("serena メニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val curtainLabel = if (screenCurtainHelper?.isCurtainEnabled == true) "🌑 スクリーンカーテンを解除" else "🌑 スクリーンカーテン (画面非表示・節電)"
        val isTalkBackMode = prefs.getBoolean(KEY_TALKBACK_MODE, false)
        val modeLabel = if (isTalkBackMode) "🔄 モード切替 (現在: TalkBack互換モード)" else "🔄 モード切替 (現在: serenaオリジナルモード)"
        val filterName = notificationFilterHelper?.currentMode?.displayName ?: "自動"
        val items = listOf(
            serenaMenuItem("🎙️", "serena AI Voice Assistant (音声対話アシスタント)") {
                launchAiAssistant()
            },
            serenaMenuItem("🔄", modeLabel) {
                toggleTalkBackMode()
            },
            serenaMenuItem("📊", "スマホ状態 (バッテリー/電波/Wi-Fi/時刻)") {
                announceFullStatus()
            },
            serenaMenuItem("🎛️", "読み上げコントロール (現在: ${currentGranularity.displayName})") {
                cycleGranularity(forward = true)
            },
            serenaMenuItem("📋", "クリップボード履歴 (過去のコピー)") {
                showClipboardHistoryDialog(null)
            },
            serenaMenuItem("💌", "通知フィルター (現在: $filterName)") {
                cycleNotificationFilterMode()
            },
            serenaMenuItem("🔔", "時報チャイム音の変更 (NHKラジオ風 / ポップ / 和風)") {
                cycleChimeStyle()
            },
            serenaMenuItem(if (screenCurtainHelper?.isCurtainEnabled == true) "☀️" else "🌑", curtainLabel) {
                toggleScreenCurtain()
            },
            serenaMenuItem("📷", "カメラ・文字読み取り (On-Device OCR)") {
                launchCameraOcr()
            },
            serenaMenuItem("👤", "カメラ・表情と人物判定 (On-Device Face AI)") {
                launchCameraFaceAnalysis()
            },
            serenaMenuItem("📦", "カメラ・物体と周囲の認識 (Gemma 4 On-Device AI)") {
                launchCameraObjectAnalysis()
            },
            serenaMenuItem("⚡", "読み上げ速度の変更 (トグル切り替え)") {
                toggleSpeechRateQuick()
            },
            serenaMenuItem("📄", "次のページへ移動") {
                scrollPageForward()
            },
            serenaMenuItem("📄", "前のページへ移動") {
                scrollPageBackward()
            },
            serenaMenuItem("📖", "画面の一番上から読む") {
                readFromTop()
            },
            serenaMenuItem("📞", "開発者(${BuildConfig.DEVELOPER_NAME})へ電話をかける") {
                callDeveloper()
            },
            serenaMenuItem("✉️", "開発者(${BuildConfig.DEVELOPER_NAME})へメールを送る") {
                emailDeveloper()
            },
            serenaMenuItem("🐛", "開発者(${BuildConfig.DEVELOPER_NAME})へ動作診断・ログ送信") {
                sendTelemetryLog()
            },
            serenaMenuItem("⚙️", "serenaの設定") {
                openserenaSettings()
            },
            serenaMenuItem("❓", "serenaのHelp") {
                showHelp()
            }
        )
        try {
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog: ${e.message}")
            Toast.makeText(this, "serenaメニュー: 読み上げコントロール / クリップボード / 設定", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleTalkBackMode(): Boolean {
        val current = prefs.getBoolean(KEY_TALKBACK_MODE, false)
        val next = !current
        prefs.edit().putBoolean(KEY_TALKBACK_MODE, next).apply()
        soundHelper?.playActionDone()
        val modeName = if (next) "TalkBack互換モード" else "serenaオリジナルモード"
        speak("操作モードを $modeName に変更しました", TextToSpeech.QUEUE_FLUSH)
        return next
    }

    private fun showClipboardHistoryDialog(targetNode: AccessibilityNodeInfo?) {
        val history = clipboardHelper?.getHistory() ?: emptyList()
        if (history.isEmpty()) {
            soundHelper?.playActionDone()
            speak("クリップボード履歴は空です", TextToSpeech.QUEUE_FLUSH)
            return
        }

        soundHelper?.playMenuOpen()
        speak("クリップボード履歴を開きました", TextToSpeech.QUEUE_FLUSH)
        val items = history.mapIndexed { index, text ->
            val preview = if (text.length > 20) text.take(20) + "..." else text
            serenaMenuItem("📋", "${index + 1}. $preview") {
                soundHelper?.playActionDone()
                if (targetNode != null) {
                    val arguments = Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    speak("「$preview」を貼り付けました", TextToSpeech.QUEUE_FLUSH)
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("serenaClip", text)
                    clipboard?.setPrimaryClip(clip)
                    speak("「$preview」をコピーしました", TextToSpeech.QUEUE_FLUSH)
                }
            }
        }
        try {
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            speak("履歴: " + history.take(3).joinToString(", "), TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun cycleNotificationFilterMode() {
        val mode = notificationFilterHelper?.cycleFilterMode()
        soundHelper?.playActionDone()
        speak("通知フィルター: ${mode?.displayName}", TextToSpeech.QUEUE_FLUSH)
    }

    fun announceFullStatus() {
        soundHelper?.playActionDone()
        val statusText = statusHelper?.buildFullStatusAnnouncement() ?: "ステータス情報を取得できませんでした"
        speak(statusText, TextToSpeech.QUEUE_FLUSH)
    }

    fun toggleScreenCurtain() {
        val helper = screenCurtainHelper ?: return
        val enabled = helper.toggleCurtain()
        soundHelper?.playActionDone()
        if (enabled) {
            speak("スクリーンカーテンを有効にしました。画面が非表示になりました。", TextToSpeech.QUEUE_FLUSH)
        } else {
            speak("スクリーンカーテンを解除しました。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun launchAiAssistant() {
        soundHelper?.playMenuOpen()
        assistantHelper?.startListening()
    }

    fun launchCameraOcr() {
        soundHelper?.playClick()
        speak("文字読み取りカメラを起動します。撮影した画像から文字を即座に読み取ります。", TextToSpeech.QUEUE_FLUSH)
        ocrHelper?.launchCameraForTextRecognition()
    }

    fun launchCameraFaceAnalysis() {
        soundHelper?.playClick()
        speak("表情・人物判定カメラを起動します。撮影した人物の表情や位置を即座に解析します。", TextToSpeech.QUEUE_FLUSH)
        faceHelper?.launchCameraForFaceAnalysis()
    }

    fun launchCameraObjectAnalysis() {
        soundHelper?.playClick()
        showGemmaDownloadDialog()
    }

    private fun showGemmaDownloadDialog() {
        val helper = gemmaDownloadHelper
        val prompt = helper?.buildDownloadConfirmationPrompt() ?: "Gemma 4 AIエンジンで周囲の物体や景観を完全ローカル解析します。"
        val items = listOf(
            serenaMenuItem("🚀", "軽量ローカルAIで即座に物体を撮影・認識") {
                soundHelper?.playClick()
                speak("軽量ローカルAIエンジンで物体認識カメラを起動します。", TextToSpeech.QUEUE_FLUSH)
                objectHelper?.launchCameraForObjectRecognition()
            },
            serenaMenuItem("⬇️", "Gemma 4 AIモデル(1.5GB)の公式無料ダウンロード") {
                soundHelper?.playClick()
                val success = gemmaDownloadHelper?.startGemmaDownload() ?: false
                if (success) {
                    speak("Google公式サーバーから Gemma 4 AIモデルのバックグラウンドダウンロードを開始しました。通知領域で進行状況を確認できます。", TextToSpeech.QUEUE_FLUSH)
                    Toast.makeText(this, "Gemma 4 ダウンロード開始", Toast.LENGTH_LONG).show()
                } else {
                    speak("ダウンロードの開始に失敗しました。容量または接続を確認してください。", TextToSpeech.QUEUE_FLUSH)
                }
                objectHelper?.launchCameraForObjectRecognition()
            }
        )
        try {
            speak("カメラ解析モードの選択ダイアログを開きました。軽量ローカル認識、または Gemma 4 AI ダウンロードを選択できます。", TextToSpeech.QUEUE_FLUSH)
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Gemma dialog: ${e.message}")
            objectHelper?.launchCameraForObjectRecognition()
        }
    }

    private fun launchChromeAuthPage(url: String) {
        soundHelper?.playClick()
        speak("Google Chrome を強制起動して認証ページを開きます", TextToSpeech.QUEUE_FLUSH)
        ChromeAuthHelper.openChromeAuth(this, url)
    }

    private fun sendTelemetryLog() {
        soundHelper?.playClick()
        speak("動作診断レポートを作成し、開発者 Shinji への送信画面を開きます", TextToSpeech.QUEUE_FLUSH)
        AlphaTelemetryHelper.getInstance(this).sendReportViaEmail(this)
    }

    fun toggleSpeechRateQuick() {
        val currentRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        val newRate = when {
            currentRate < 1.25f -> 1.5f
            currentRate < 1.75f -> 2.0f
            else -> 1.0f
        }
        prefs.edit().putFloat(KEY_SPEECH_RATE, newRate).apply()
        updateTtsSettings()
        soundHelper?.playActionDone()
        speak("読み上げ速度を ${newRate} 倍に変更しました", TextToSpeech.QUEUE_FLUSH)
    }

    private fun callDeveloper() {
        soundHelper?.playClick()
        speak("開発者 ${BuildConfig.DEVELOPER_NAME} (${BuildConfig.DEVELOPER_PHONE_DISPLAY}) への電話発線画面を起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${BuildConfig.DEVELOPER_PHONE}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call developer error: ${e.message}")
            Toast.makeText(this, "電話サポート: ${BuildConfig.DEVELOPER_PHONE_DISPLAY}", Toast.LENGTH_LONG).show()
        }
    }

    private fun emailDeveloper() {
        soundHelper?.playClick()
        speak("開発者 ${BuildConfig.DEVELOPER_NAME} へのメールアプリを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${BuildConfig.DEVELOPER_EMAIL}")
                putExtra(Intent.EXTRA_SUBJECT, "serena スクリーンリーダーに関するお問い合わせ・ご要望")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Email developer error: ${e.message}")
            Toast.makeText(this, "メールサポート: ${BuildConfig.DEVELOPER_EMAIL}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showHelp() {
        soundHelper?.playMenuOpen()
        speak("serena ヘルプと開発者直通サポートメニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val items = listOf(
            serenaMenuItem("📞", "開発者(${BuildConfig.DEVELOPER_NAME})へ電話で直通相談 (${BuildConfig.DEVELOPER_PHONE_DISPLAY})") {
                callDeveloper()
            },
            serenaMenuItem("🐛", "動作診断・ログ添付でメール送信") {
                sendTelemetryLog()
            },
            serenaMenuItem("✉️", "開発者へメールでお問い合わせ") {
                emailDeveloper()
            },
            serenaMenuItem("📖", "serena の使い方音声ガイド") {
                soundHelper?.playActionDone()
                speak("使い方の基本: 上下スワイプで読み上げ単位の変更、左右スワイプで項目の移動、2本指3回タップでスマホ状態の確認、2本指ダブルタップでserenaメニューを開きます。", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("ℹ️", "アプリ情報 (v1.0.0-alpha01)") {
                soundHelper?.playActionDone()
                speak("serena スクリーンリーダー バージョン 1.0.0-alpha01、開発者 ${BuildConfig.DEVELOPER_NAME}、お問い合わせ ${BuildConfig.DEVELOPER_EMAIL}", TextToSpeech.QUEUE_FLUSH)
            }
        )
        try {
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            speak("ヘルプ: 電話 ${BuildConfig.DEVELOPER_PHONE_DISPLAY}、メール ${BuildConfig.DEVELOPER_EMAIL}", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun showEditTextAssistMenu(node: AccessibilityNodeInfo) {
        soundHelper?.playMenuOpen()
        speak("編集アシストメニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val items = listOf(
            serenaMenuItem("💬", "定型文:「今移動中です」") {
                insertPhrase(node, "今移動中です。")
            },
            serenaMenuItem("💬", "定型文:「後でかけ直します」") {
                insertPhrase(node, "後でかけ直します。")
            },
            serenaMenuItem("💬", "定型文:「了解しました」") {
                insertPhrase(node, "了解しました。")
            },
            serenaMenuItem("📋", "過去のコピー履歴から選択して貼り付け") {
                showClipboardHistoryDialog(node)
            },
            serenaMenuItem("✂️", "全選択してコピー") {
                soundHelper?.playActionDone()
                val currentText = getNodeText(node)
                if (currentText.isNotEmpty()) {
                    clipboardHelper?.addClip(currentText)
                }
                val args = Bundle()
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText.length)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
                node.performAction(AccessibilityNodeInfo.ACTION_COPY)
                speak("全選択してコピーしました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("📋", "貼り付け") {
                soundHelper?.playActionDone()
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                speak("貼り付けました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("🧹", "テキスト全消去") {
                soundHelper?.playActionDone()
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                speak("テキストを全消去しました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("🔊", "入力中テキストの読み上げ") {
                soundHelper?.playClick()
                val currentText = getNodeText(node)
                if (currentText.isNotEmpty()) {
                    speak("現在の入力内容: $currentText", TextToSpeech.QUEUE_FLUSH)
                } else {
                    speak("入力欄は空です", TextToSpeech.QUEUE_FLUSH)
                }
            },
            serenaMenuItem("☕", "通常メニューを開く") {
                showNormalserenaMenu()
            }
        )
        try {
            val dialog = serenaMenuDialog(this, true, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show edit dialog: ${e.message}")
            Toast.makeText(this, "編集アシスト: コピー / 貼り付け / クリア", Toast.LENGTH_LONG).show()
        }
    }

    private fun insertPhrase(node: AccessibilityNodeInfo, phrase: String) {
        soundHelper?.playActionDone()
        val currentText = getNodeText(node)
        val newText = if (currentText.isEmpty()) phrase else "$currentText $phrase"
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        speak("定型文「$phrase」を入力しました", TextToSpeech.QUEUE_FLUSH)
    }

    private fun readFromTop() {
        val root = rootInActiveWindow ?: return
        soundHelper?.playActionDone()
        speak("画面の一番上から読み上げを開始します", TextToSpeech.QUEUE_FLUSH)

        val textList = mutableListOf<String>()
        collectAllNodeText(root, textList)

        if (textList.isEmpty()) {
            speak("読み上げ可能なテキストが見つかりませんでした", TextToSpeech.QUEUE_ADD)
            return
        }

        for (text in textList) {
            speak(text, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun openserenaSettings() {
        soundHelper?.playClick()
        speak("serena の設定画面を開きます", TextToSpeech.QUEUE_FLUSH)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isTtsReady) return

        val pkgName = event.packageName?.toString() ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notificationText = event.text.joinToString(" ").trim()
                if (notificationText.isNotEmpty()) {
                    if (notificationFilterHelper?.shouldAnnounce(pkgName, notificationText) == true) {
                        val appName = getMessagingAppName(pkgName)
                        if (isCallRelatedPackage(pkgName) || notificationText.contains("着信") || notificationText.contains("通話")) {
                            speak("${appName}の着信: $notificationText", TextToSpeech.QUEUE_FLUSH)
                        } else {
                            speak("通知: $notificationText", TextToSpeech.QUEUE_ADD)
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkCallState(pkgName)

                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val windowTitle = event.contentDescription?.toString()
                        ?: event.text.joinToString(" ").trim()

                    val suggestedGranularity = appProfileHelper?.getSuggestedGranularity(pkgName)
                    if (suggestedGranularity != null && suggestedGranularity != currentGranularity) {
                        currentGranularity = suggestedGranularity
                        speak("アプリ切替: ${suggestedGranularity.displayName}モード", TextToSpeech.QUEUE_FLUSH)
                    }

                    if (isCallRelatedPackage(pkgName)) {
                        val callerInfo = parseCallerFromRootNode()
                        if (callerInfo.isNotEmpty() && !isCallActive) {
                            val appName = getMessagingAppName(pkgName)
                            speak("${appName}着信中: $callerInfo", TextToSpeech.QUEUE_FLUSH)
                            return
                        }
                    }

                    if (windowTitle.isNotEmpty() && !isCallActive) {
                        speak("画面: $windowTitle", TextToSpeech.QUEUE_FLUSH)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val node = event.source ?: return
                announceNode(node)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                soundHelper?.playClick()
                val node = event.source
                if (node != null) {
                    val text = getNodeText(node)
                    if (text.isNotEmpty()) {
                        speak("$text をクリック", TextToSpeech.QUEUE_FLUSH)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString(" ").trim()
                if (text.isNotEmpty()) {
                    speak(text, TextToSpeech.QUEUE_FLUSH)
                }
            }
        }
    }

    private fun isCallRelatedPackage(pkgName: String): Boolean {
        val lower = pkgName.lowercase()
        return lower.contains("dialer") ||
                lower.contains("incallui") ||
                lower.contains("phone") ||
                lower.contains("line") ||
                lower.contains("whatsapp") ||
                lower.contains("skype") ||
                lower.contains("teams") ||
                lower.contains("zoom") ||
                lower.contains("orca") ||
                lower.contains("kakao") ||
                lower.contains("telegram") ||
                lower.contains("viber") ||
                lower.contains("tencent.mm")
    }

    private fun getMessagingAppName(pkgName: String): String {
        val lower = pkgName.lowercase()
        return when {
            lower.contains("line") -> "LINE"
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("skype") -> "Skype"
            lower.contains("teams") -> "Teams"
            lower.contains("zoom") -> "Zoom"
            lower.contains("orca") -> "Messenger"
            lower.contains("kakao") -> "カカオトーク"
            lower.contains("telegram") -> "Telegram"
            lower.contains("tencent.mm") -> "WeChat"
            else -> "電話"
        }
    }

    private fun checkCallState(pkgName: String) {
        val isCallPkg = isCallRelatedPackage(pkgName)
        val root = rootInActiveWindow
        val windowText = if (root != null) parseCallerFromRootNode() else ""

        val isCurrentlyInCall = isCallPkg && (
                windowText.contains("通話中") ||
                windowText.contains("通話時間") ||
                windowText.contains("保留") ||
                windowText.contains("ミュート") ||
                windowText.matches(Regex(".*\\d{1,2}:\\d{2}.*"))
        )

        if (isCurrentlyInCall && !isCallActive) {
            isCallActive = true
            activeCallApp = getMessagingAppName(pkgName)
            callStartTimeMs = System.currentTimeMillis()
            Log.i(TAG, "Call started ($activeCallApp) at $callStartTimeMs")
        } else if (!isCurrentlyInCall && isCallActive) {
            isCallActive = false
            val elapsedMs = System.currentTimeMillis() - callStartTimeMs
            val durationText = formatDuration(elapsedMs)
            val appLabel = if (activeCallApp.isNotEmpty()) activeCallApp else "通話"
            AlphaTelemetryHelper.getInstance(this).recordCallCompleted(elapsedMs / 1000)
            speak("${appLabel}の通話が終了しました。今の通話は ${durationText} でした。", TextToSpeech.QUEUE_FLUSH)
            Log.i(TAG, "$appLabel ended. Duration: $durationText")
            activeCallApp = ""
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}時間")
        if (minutes > 0 || hours > 0) parts.add("${minutes}分")
        parts.add("${seconds}秒")

        return parts.joinToString(" ")
    }

    private fun parseCallerFromRootNode(): String {
        val root = rootInActiveWindow ?: return ""
        val collectedText = mutableListOf<String>()
        collectAllNodeText(root, collectedText)
        return collectedText.filter { it.length > 1 }.joinToString(" ")
    }

    private fun collectAllNodeText(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val t = getNodeText(node)
        if (t.isNotEmpty() && !list.contains(t)) {
            list.add(t)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodeText(child, list)
        }
    }

    private fun announceNode(node: AccessibilityNodeInfo) {
        val announcement = buildNodeAnnouncement(node)
        if (announcement.isBlank()) return

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val displayWidth = resources.displayMetrics.widthPixels
        val normalizedX = if (displayWidth > 0) rect.centerX().toFloat() / displayWidth else 0.5f

        val currentTime = System.currentTimeMillis()
        if (announcement == lastSpokenText && (currentTime - lastSpokenTime) < 500) {
            return
        }

        lastSpokenText = announcement
        lastSpokenTime = currentTime
        soundHelper?.playFocusMovePanned(normalizedX)
        speak(announcement, TextToSpeech.QUEUE_FLUSH)
    }

    private fun buildNodeAnnouncement(node: AccessibilityNodeInfo): String {
        val text = getNodeText(node)
        val role = getNodeRole(node)
        val state = getNodeState(node)

        val parts = mutableListOf<String>()
        if (text.isNotEmpty()) parts.add(text)
        if (role.isNotEmpty()) parts.add(role)
        if (state.isNotEmpty()) parts.add(state)

        return parts.joinToString("、")
    }

    private fun getNodeText(node: AccessibilityNodeInfo): String {
        // 1. ノード自体の contentDescription, text, hintText を最優先取得
        var text = node.contentDescription?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            text = node.text?.toString()?.trim()
        }
        if (text.isNullOrEmpty()) {
            text = node.hintText?.toString()?.trim()
        }
        if (!text.isNullOrEmpty()) {
            return text
        }

        // 2. リソースID名 (viewIdResourceName) からのラベル自動判定（Google検索、ホーム等）
        val inferred = inferLabelFromViewId(node.viewIdResourceName)
        if (inferred.isNotEmpty()) {
            return inferred
        }

        // 3. コンテナノードの場合、直近の子要素テキストを取得
        if (node.childCount > 0) {
            val childTexts = mutableListOf<String>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val t = child.contentDescription?.toString()?.trim()
                    ?: child.text?.toString()?.trim()
                    ?: child.hintText?.toString()?.trim()
                    ?: inferLabelFromViewId(child.viewIdResourceName)
                if (t.isNotEmpty() && !childTexts.contains(t)) {
                    childTexts.add(t)
                }
                if (childTexts.size >= 2) break
            }
            if (childTexts.isNotEmpty()) {
                return childTexts.joinToString(" ")
            }
        }

        // 4. クリック可能/操作可能要素で無標題の場合のフォールバック
        if (node.isClickable || node.isCheckable) {
            val role = getNodeRole(node)
            return if (role.isNotEmpty()) "無標題$role" else "ボタン"
        }

        return ""
    }

    private fun inferLabelFromViewId(viewId: String?): String {
        if (viewId.isNullOrEmpty()) return ""
        val name = viewId.substringAfterLast(":id/").lowercase()
        return when {
            name.contains("search") || name.contains("gsearch") -> "Google検索"
            name.contains("home") -> "ホーム"
            name.contains("menu") || name.contains("drawer") -> "メニュー"
            name.contains("setting") -> "設定"
            name.contains("back") -> "戻る"
            name.contains("close") || name.contains("cancel") -> "閉じる"
            name.contains("mic") || name.contains("voice") -> "音声検索"
            name.contains("camera") -> "カメラ"
            name.contains("phone") || name.contains("call") || name.contains("dial") -> "電話"
            name.contains("message") || name.contains("chat") || name.contains("sms") -> "メッセージ"
            name.contains("mail") || name.contains("gmail") -> "メール"
            name.contains("browser") || name.contains("chrome") || name.contains("web") -> "ブラウザ"
            name.contains("app") || name.contains("icon") -> "アプリ"
            else -> ""
        }
    }

    private fun getNodeRole(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString() ?: ""
        return when {
            className.contains("Button", ignoreCase = true) -> "ボタン"
            className.contains("EditText", ignoreCase = true) -> "テキスト入力欄"
            className.contains("CheckBox", ignoreCase = true) -> "チェックボックス"
            className.contains("RadioButton", ignoreCase = true) -> "ラジオボタン"
            className.contains("Switch", ignoreCase = true) || className.contains("ToggleButton", ignoreCase = true) -> "スイッチ"
            className.contains("ImageView", ignoreCase = true) || className.contains("Image", ignoreCase = true) -> "画像"
            className.contains("SeekBar", ignoreCase = true) -> "スライダー"
            className.contains("TextView", ignoreCase = true) -> ""
            else -> ""
        }
    }

    private fun getNodeState(node: AccessibilityNodeInfo): String {
        val states = mutableListOf<String>()
        if (node.isCheckable) {
            states.add(if (node.isChecked) "オン" else "オフ")
        }
        if (node.isSelected) {
            states.add("選択中")
        }
        if (!node.isEnabled) {
            states.add("無効")
        }
        return states.joinToString(" ")
    }

    private fun handleMagicTapAction() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRinging = audioManager?.mode == AudioManager.MODE_RINGTONE

        if (isRinging) {
            speak("電話に応答します", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
            return
        }

        if (isCallActive || audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
            speak("通話を終了します", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
            return
        }

        speak("メディアの再生または一時停止", TextToSpeech.QUEUE_FLUSH)
        simulateMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun simulateMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(downEvent)
        audioManager?.dispatchMediaKeyEvent(upEvent)
    }

    private fun detectLanguage(text: String): Locale {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Locale.JAPANESE

        if (trimmed.matches(Regex(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF].*"))) {
            return Locale.JAPANESE
        }

        val lower = trimmed.lowercase()
        val tagalogKeywords = listOf(
            "kamusta", "salamat", "magandang", "mga", "ako", "ikaw", "kayo", "po", "opo",
            "hindi", "oo", "maraming", "mabuhay", "pala", "naman", "talaga", "kasi", "ang",
            "sa", "ng", "na", "ba", "pa", "rin", "din", "walang", "may", "meron"
        )
        val isTagalog = tagalogKeywords.any { lower.contains(it) }

        if (isTagalog) {
            val tagalogLocale = Locale("fil", "PH")
            val availability = tts?.isLanguageAvailable(tagalogLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                return tagalogLocale
            }
            return Locale.ENGLISH
        }

        return Locale.ENGLISH
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!isTtsReady || text.isBlank()) return
        val processedText = emojiHelper?.translateEmojiAndKaomoji(text) ?: text
        val targetLocale = detectLanguage(processedText)
        tts?.language = targetLocale
        AlphaTelemetryHelper.getInstance(this).incrementTtsCount(targetLocale)
        tts?.speak(processedText, queueMode, null, "serenaUtterance_${System.currentTimeMillis()}")
    }

    fun stopSpeech() {
        tts?.stop()
    }

    override fun onInterrupt() {
        stopSpeech()
    }

    private fun registerTimeTickReceiver() {
        if (timeTickReceiver != null) return
        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK) {
                    checkHourlyChime()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timeTickReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(timeTickReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerTimeTickReceiver error: ${e.message}")
        }
    }

    private fun unregisterTimeTickReceiver() {
        timeTickReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Unregister receiver error: ${e.message}")
            }
        }
        timeTickReceiver = null
    }

    fun checkHourlyChime() {
        val isEnabled = prefs.getBoolean(KEY_HOURLY_CHIME_ENABLED, true)
        if (!isEnabled) return

        val calendar = Calendar.getInstance()
        val minute = calendar.get(Calendar.MINUTE)
        if (minute == 0) {
            triggerHourlyAnnouncement(calendar.get(Calendar.HOUR_OF_DAY))
        }
    }

    fun triggerHourlyAnnouncement(hour24: Int) {
        val style = prefs.getString(KEY_CHIME_STYLE, CHIME_STYLE_NHK) ?: CHIME_STYLE_NHK
        when (style) {
            CHIME_STYLE_NHK -> soundHelper?.playNhkRadioChime()
            CHIME_STYLE_CUTE -> soundHelper?.playCuteBeepChime()
            CHIME_STYLE_BELL -> soundHelper?.playJapaneseBellChime()
            else -> soundHelper?.playNhkRadioChime()
        }

        val isAm = hour24 < 12
        val displayHour = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val periodStr = if (isAm) "午前" else "午後"

        val announcement = "${periodStr}${displayHour}時をお知らせします。"
        speak(announcement, TextToSpeech.QUEUE_FLUSH)
    }

    fun cycleChimeStyle() {
        val currentStyle = prefs.getString(KEY_CHIME_STYLE, CHIME_STYLE_NHK)
        val nextStyle = when (currentStyle) {
            CHIME_STYLE_NHK -> CHIME_STYLE_CUTE
            CHIME_STYLE_CUTE -> CHIME_STYLE_BELL
            else -> CHIME_STYLE_NHK
        }
        prefs.edit().putString(KEY_CHIME_STYLE, nextStyle).apply()
        val name = when (nextStyle) {
            CHIME_STYLE_NHK -> {
                soundHelper?.playNhkRadioChime()
                "NHKラジオ風時報 (ポッ、ポッ、ポッ、ポーン！)"
            }
            CHIME_STYLE_CUTE -> {
                soundHelper?.playCuteBeepChime()
                "ポップ・ビープ音"
            }
            else -> {
                soundHelper?.playJapaneseBellChime()
                "和風・お寺の鐘風"
            }
        }
        speak("時報チャイム音を $name に変更しました", TextToSpeech.QUEUE_FLUSH)
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeDetectorHelper?.stop()
        stopLiveEnvironmentDescription()
        stopSpeech()
        unregisterTimeTickReceiver()
        soundHelper?.release()
        soundHelper = null
        tts?.shutdown()
        tts = null
        isTtsReady = false
        instance = null
        Log.i(TAG, "serena ScreenReaderService destroyed.")
    }

    fun toggleLiveEnvironmentDescription() {
        if (isLiveEnvironmentModeActive) {
            stopLiveEnvironmentDescription()
            speak("リアルタイム環境実況モードを終了しました。", TextToSpeech.QUEUE_FLUSH)
        } else {
            startLiveEnvironmentDescription()
            speak("リアルタイム環境実況モードを開始しました。カメラ前の景色や文字を自動解説します。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun startLiveEnvironmentDescription() {
        isLiveEnvironmentModeActive = true
        if (liveEnvironmentHandler == null) {
            liveEnvironmentHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        liveEnvironmentRunnable = object : Runnable {
            override fun run() {
                if (!isLiveEnvironmentModeActive) return
                ocrHelper?.captureAndRecognize { resultText ->
                    if (resultText.isNotBlank()) {
                        speak("環境実況: $resultText", TextToSpeech.QUEUE_ADD)
                    }
                }
                liveEnvironmentHandler?.postDelayed(this, 4000)
            }
        }
        liveEnvironmentHandler?.post(liveEnvironmentRunnable!!)
    }

    private fun stopLiveEnvironmentDescription() {
        isLiveEnvironmentModeActive = false
        liveEnvironmentRunnable?.let { liveEnvironmentHandler?.removeCallbacks(it) }
    }

    fun showClipboardHistoryQuickly() {
        val lastText = clipboardHelper?.getHistory()?.firstOrNull() ?: "履歴はありません"
        speak("最新のクリップボード履歴: $lastText", TextToSpeech.QUEUE_FLUSH)
    }
}


