package com.shinji.serena

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
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
    CHARACTERS("文字"),
    WORDS("単語"),
    LINES("行"),
    PARAGRAPHS("段落"),
    HEADINGS("見出し"),
    CONTROLS("コントロール"),
    LINKS("リンク"),
    ACTIONS("アクション"),
    DEFAULT("デフォルト")
}

class SerenaScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SerenaScreenReader"
        const val PREFS_NAME = "serena_prefs"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_SPEECH_PITCH = "speech_pitch"
        const val KEY_HOURLY_CHIME_ENABLED = "hourly_chime_enabled"
        const val KEY_TALKBACK_MODE = "key_talkback_mode"
        const val KEY_CHIME_STYLE = "key_chime_style"
        const val CHIME_STYLE_NHK = "nhk_radio"
        const val CHIME_STYLE_CUTE = "cute_beep"
        const val CHIME_STYLE_BELL = "japanese_bell"

        var instance: SerenaScreenReaderService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null
    }

    var focusNavigator: com.shinji.serena.navigation.SerenaFocusNavigator? = null
    var callManager: com.shinji.serena.telephony.SerenaCallManager? = null
    var lastHoveredNode: AccessibilityNodeInfo? = null
    var gestureDispatcher: com.shinji.serena.gesture.SerenaGestureDispatcher? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0L
    private lateinit var prefs: SharedPreferences
    var soundHelper: SoundAndHapticHelper? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var currentGranularity: GranularityMode = GranularityMode.DEFAULT
    var screenCurtainHelper: ScreenCurtainHelper? = null
    var statusHelper: StatusAnnouncementHelper? = null
    var ocrHelper: OcrCameraHelper? = null
    var clipboardHelper: ClipboardHistoryHelper? = null
    var notificationFilterHelper: SmartNotificationFilterHelper? = null
    var appProfileHelper: AppProfileHelper? = null
    var emojiHelper: EmojiAndKaomojiHelper? = EmojiAndKaomojiHelper()
    var faceHelper: FaceDetectionHelper? = null
    var objectHelper: ObjectRecognitionHelper? = null
    var assistantHelper: SerenaAiAssistantHelper? = null
    var wifiConnectivityHelper: WifiConnectivityHelper? = null
    var batteryHelper: BatteryStateHelper? = null
    var colorAndLightHelper: ColorAndLightHelper? = null
    var compassHelper: SpatialCompassHelper? = null
    var walkingNavigator: com.shinji.serena.navigation.SerenaWalkingNavigator? = null
    private var shakeDetectorHelper: ShakeDetectorHelper? = null
    private var spatialHapticTouchMapHelper: SpatialHapticTouchMapHelper? = null
    private var isLiveEnvironmentModeActive = false
    private var liveEnvironmentHandler: android.os.Handler? = null
    private var liveEnvironmentRunnable: Runnable? = null

    // 通話時間計測用
    var isCallActive = false
    private var activeCallApp: String = ""
    private var callStartTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        val safeContext = getSafeContext()
        try {
            prefs = safeContext.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            soundHelper = SoundAndHapticHelper(safeContext)
            screenCurtainHelper = ScreenCurtainHelper(safeContext)
            statusHelper = StatusAnnouncementHelper(safeContext)
            ocrHelper = OcrCameraHelper(this)
            clipboardHelper = ClipboardHistoryHelper(safeContext)
            notificationFilterHelper = SmartNotificationFilterHelper(safeContext)
            appProfileHelper = AppProfileHelper()
            faceHelper = FaceDetectionHelper(this)
            objectHelper = ObjectRecognitionHelper(this)
            assistantHelper = SerenaAiAssistantHelper(this)
            colorAndLightHelper = ColorAndLightHelper(safeContext)
            compassHelper = SpatialCompassHelper(safeContext).apply { try { startListening() } catch (_: Exception) {} }
            walkingNavigator = com.shinji.serena.navigation.SerenaWalkingNavigator(this, compassHelper).apply { try { startTracking() } catch (_: Exception) {} }
            wifiConnectivityHelper = WifiConnectivityHelper(this).apply { try { startMonitoring() } catch (_: Exception) {} }
            batteryHelper = BatteryStateHelper(this).apply { try { start() } catch (_: Exception) {} }

            shakeDetectorHelper = ShakeDetectorHelper(safeContext) {
                announceFullStatus()
            }.apply { try { start() } catch (_: Exception) {} }

            soundHelper?.let {
                spatialHapticTouchMapHelper = SpatialHapticTouchMapHelper(it)
            }
            com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.init(safeContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing non-core helpers: ${e.message}")
        }

        initTts(this)
        registerTimeTickReceiver()
        Log.i(TAG, "serena ScreenReaderService created with fail-safe direct boot resilience.")
    }

    private val pendingSpeechQueue = mutableListOf<Pair<String, Int>>()

    private fun initTts(context: Context = this) {
        try {
            tts?.shutdown()
            tts = TextToSpeech(this, this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initTts error: ${e.message}")
        }
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
            soundHelper?.playActionDone()
            flushPendingSpeechQueue()
            Log.i(TAG, "TTS initialized successfully.")
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isTtsReady) initTts()
            }, 2000)
        }
    }

    private fun flushPendingSpeechQueue() {
        synchronized(pendingSpeechQueue) {
            for (item in pendingSpeechQueue) {
                speak(item.first, item.second)
            }
            pendingSpeechQueue.clear()
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
        info.notificationTimeout = 0
        var flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
        }
        info.flags = flags
        serviceInfo = info
        Log.i(TAG, "serena AccessibilityService connected with Multi-Finger & Touch Exploration enabled.")
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
            // 1本指右スワイプ: 次の項目へフォーカス移動 (リニア)
            GESTURE_SWIPE_RIGHT -> {
                soundHelper?.playFocusMove()
                navigateLinearFocus(forward = true)
                return true
            }
            // 1本指左スワイプ: 前の項目へフォーカス移動 (リニア)
            GESTURE_SWIPE_LEFT -> {
                soundHelper?.playFocusMove()
                navigateLinearFocus(forward = false)
                return true
            }
            // 1本指上スワイプ: 選択中の読み上げコントロール（行/単語/見出し等）に従って前へ移動
            GESTURE_SWIPE_UP -> {
                soundHelper?.playFocusMove()
                focusPrevious()
                return true
            }
            // 1本指下スワイプ: 選択中の読み上げコントロール（行/単語/見出し等）に従って次へ移動
            GESTURE_SWIPE_DOWN -> {
                soundHelper?.playFocusMove()
                focusNext()
                return true
            }
            // 1本指ダブルタップ: フォーカス中要素のクリック実行
            GESTURE_DOUBLE_TAP -> {
                soundHelper?.playClick()
                performClickOnFocusedNode()
                return true
            }
            // 1本指ダブルタップ＆ホールド (長押し / TalkBack互換 18): アクション・ショートカットメニュー表示
            18 -> {
                soundHelper?.playActionDone()
                showActionsMenu(getAccessibilityFocusedNode())
                return true
            }

            // 2本指シングルタップ (19): 読み上げの一時停止と再開
            19 -> {
                toggleSpeechPauseResume()
                return true
            }
            // 2本指ダブルタップ (20): 通話の応答と終了 / メディアの再生と一時停止
            20 -> {
                soundHelper?.playActionDone()
                handleMagicTapAction()
                return true
            }
            // 2本指右フリック (28): 前のページへ（横スクロール戻る）
            28 -> {
                scrollHorizontalBackward()
                return true
            }
            // 2本指左フリック (27): 次のページへ（横スクロール進む）
            27 -> {
                scrollHorizontalForward()
                return true
            }
            // 2本指下フリック (26): 前へ縦スクロール（上にスクロールして前の内容を表示）
            26 -> {
                scrollVerticalBackward()
                return true
            }
            // 2本指上フリック (25): ロック画面時はロック解除 / アプリ内は次へ縦スクロール
            25 -> {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isKeyguard = km?.isKeyguardLocked == true || rootInActiveWindow?.packageName?.toString()?.contains("systemui") == true
                if (isKeyguard) {
                    unlockKeyguardSwipe()
                } else {
                    scrollVerticalForward()
                }
                return true
            }

            // 3本指シングルタップ (21, 22): Serena メニューを開く！
            21, 22 -> {
                soundHelper?.playMenuOpen()
                showNormalSerenaMenu()
                return true
            }
            // 3本指ダブルタップ (23): 全ステータスアナウンス
            23 -> {
                announceFullStatus()
                return true
            }
            // 3本指トリプルタップ (24): クリップボードにコピー
            24 -> {
                copyLastSpokenTextToClipboard()
                return true
            }
            // 3本指上フリック (29): 読み上げ粒度（コントロール）を前へ
            29 -> {
                soundHelper?.playActionDone()
                cycleGranularity(forward = false)
                return true
            }
            // 3本指下フリック (30): 読み上げ粒度（コントロール）を次へ
            30 -> {
                soundHelper?.playActionDone()
                cycleGranularity(forward = true)
                return true
            }
            // 3本指左フリック (31): Serena AI Voice アシスタントを即時起動！
            31 -> {
                soundHelper?.playActionDone()
                speak("AIボイスアシスタントを起動します", TextToSpeech.QUEUE_FLUSH)
                launchAiAssistant()
                return true
            }
            // 3本指右フリック (32): リアルタイムカメラAI実況を即時起動！
            32 -> {
                launchRealtimeLiveSceneCommentary()
                return true
            }

            // TalkBack伝統 L字ジェスチャー: セレナメニュー
            GESTURE_SWIPE_DOWN_AND_RIGHT, GESTURE_SWIPE_UP_AND_RIGHT -> {
                soundHelper?.playMenuOpen()
                showNormalSerenaMenu()
                return true
            }
            // 下→左スワイプ: 戻る
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
            // 左→上スワイプ (43): 最近使ったアプリ
            GESTURE_SWIPE_LEFT_AND_UP, 43 -> {
                soundHelper?.playClick()
                speak("最近使ったアプリ", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                return true
            }
            // 右→下スワイプ (44): クイック設定
            GESTURE_SWIPE_RIGHT_AND_DOWN, 44 -> {
                soundHelper?.playClick()
                speak("クイック設定パネルを開きます", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
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
        val dirLabel = if (forward) "次のコントロール" else "前のコントロール"
        speak("${dirLabel}: ${currentGranularity.displayName}", TextToSpeech.QUEUE_FLUSH)
    }

    fun copyLastSpokenTextToClipboard() {
        val text = lastSpokenText ?: return
        clipboardHelper?.addClip(text)
        speak("直前の読み上げをクリップボードにコピーしました", TextToSpeech.QUEUE_FLUSH)
    }

    fun scrollPageForward(onComplete: (() -> Unit)? = null) {
        val scrollNode = focusNavigator?.findScrollableNode(forward = true)
        if (scrollNode != null && focusNavigator?.performScroll(scrollNode, forward = true) == true) {
            soundHelper?.playFocusMove()
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(it, 250) }
            return
        }

        performSwipeGesture(swipeUp = true, onComplete = onComplete)
    }

    fun scrollPageBackward(onComplete: (() -> Unit)? = null) {
        val scrollNode = focusNavigator?.findScrollableNode(forward = false)
        if (scrollNode != null && focusNavigator?.performScroll(scrollNode, forward = false) == true) {
            soundHelper?.playFocusMove()
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(it, 250) }
            return
        }

        performSwipeGesture(swipeUp = false, onComplete = onComplete)
    }

    private fun performSwipeGesture(swipeUp: Boolean, onComplete: (() -> Unit)? = null) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX1 = width * 0.35f
        val startX2 = width * 0.65f
        val startY = if (swipeUp) height * 0.75f else height * 0.25f
        val endY = if (swipeUp) height * 0.20f else height * 0.80f

        val path1 = android.graphics.Path().apply {
            moveTo(startX1, startY)
            lineTo(startX1, endY)
        }
        val path2 = android.graphics.Path().apply {
            moveTo(startX2, startY)
            lineTo(startX2, endY)
        }
        val stroke1 = android.accessibilityservice.GestureDescription.StrokeDescription(path1, 0, 250)
        val stroke2 = android.accessibilityservice.GestureDescription.StrokeDescription(path2, 0, 250)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                soundHelper?.playFocusMove()
                if (onComplete != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(onComplete, 200)
                } else {
                    val dirStr = if (swipeUp) "次" else "前"
                    speak("${dirStr}へ縦スクロールしました", TextToSpeech.QUEUE_FLUSH)
                }
            }
        }, null)
    }

    private fun performHorizontalSwipeGesture(swipeLeft: Boolean, onComplete: (() -> Unit)? = null) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        // 戻るジェスチャー領域(左右12%以内)を完全に避け、20%〜80%の安全画面中央帯をスワイプ
        val startX = if (swipeLeft) width * 0.80f else width * 0.20f
        val endX = if (swipeLeft) width * 0.20f else width * 0.80f
        val startY = height * 0.50f

        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(endX, startY)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 150)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        Log.i(TAG, "performHorizontalSwipeGesture: swipeLeft=$swipeLeft from ($startX, $startY) to ($endX, $startY)")
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "performHorizontalSwipeGesture: gesture completed successfully.")
                soundHelper?.playFocusMove()
                if (onComplete != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(onComplete, 200)
                } else {
                    val pageStr = if (swipeLeft) "次" else "前"
                    speak("${pageStr}のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
                }
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "performHorizontalSwipeGesture: gesture was CANCELLED by system.")
            }
        }, null)
    }

    fun getAccessibilityFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        return root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    fun handleVerticalSwipe(up: Boolean): Boolean {
        cycleGranularity(forward = !up)
        return true
    }

    fun executeActiveCustomAction(): Boolean {
        val node = getAccessibilityFocusedNode() ?: return false
        val actions = node.actionList.filter { it.id != AccessibilityNodeInfo.ACTION_CLICK && it.id != AccessibilityNodeInfo.ACTION_FOCUS }
        if (actions.isNotEmpty()) {
            return node.performAction(actions.first().id)
        }
        return false
    }
    private var selectedCustomActionIndex = -1
    private var charOffsetInFocusedNode = -1
    private var lastFocusedNodeHash = 0

    private fun focusNext() {
        when (currentGranularity) {
            GranularityMode.ACTIONS -> navigateCustomActions(forward = true)
            GranularityMode.HEADINGS -> navigateFilteredFocus(forward = true) { it.isHeading || getNodeRole(it) == "見出し" }
            GranularityMode.CONTROLS -> navigateFilteredFocus(forward = true) { it.isClickable || it.isCheckable || it.isFocusable }
            GranularityMode.LINKS -> navigateFilteredFocus(forward = true) { isLinkNode(it) }
            GranularityMode.LINES -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, forward = true)
            GranularityMode.PARAGRAPHS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH, forward = true)
            GranularityMode.WORDS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, forward = true)
            GranularityMode.CHARACTERS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, forward = true)
            GranularityMode.DEFAULT -> handleDefaultGranularityNext()
        }
    }

    private fun focusPrevious() {
        when (currentGranularity) {
            GranularityMode.ACTIONS -> navigateCustomActions(forward = false)
            GranularityMode.HEADINGS -> navigateFilteredFocus(forward = false) { it.isHeading || getNodeRole(it) == "見出し" }
            GranularityMode.CONTROLS -> navigateFilteredFocus(forward = false) { it.isClickable || it.isCheckable || it.isFocusable }
            GranularityMode.LINKS -> navigateFilteredFocus(forward = false) { isLinkNode(it) }
            GranularityMode.LINES -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE, forward = false)
            GranularityMode.PARAGRAPHS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH, forward = false)
            GranularityMode.WORDS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD, forward = false)
            GranularityMode.CHARACTERS -> moveByGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER, forward = false)
            GranularityMode.DEFAULT -> handleDefaultGranularityPrevious()
        }
    }

    private fun handleDefaultGranularityNext() {
        try {
            val focused = getAccessibilityFocusedNode()
            if (focused == null) {
                navigateLinearFocus(forward = true)
                return
            }

            val className = focused.className?.toString() ?: ""
            // 1. スライダー (SeekBar / Slider / ProgressBar)
            if (className.contains("SeekBar", ignoreCase = true) || className.contains("Slider", ignoreCase = true) || className.contains("ProgressBar", ignoreCase = true)) {
                val success = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id) ||
                              focused.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                if (success) {
                    soundHelper?.playActionDone()
                    return
                }
            }

            // 2. カスタムアクションを持つ要素（ホーム画面アイコンなど）
            val customActions = focused.actionList.filter { 
                it.id != AccessibilityNodeInfo.ACTION_CLICK && 
                it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
                it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
                !it.label.isNullOrEmpty()
            }
            if (customActions.isNotEmpty()) {
                navigateCustomActions(forward = true)
                return
            }

            // 3. 通常の場所: 1文字ずつフォネティック詳細送り
            val text = getNodeText(focused)
            if (text.isNotEmpty()) {
                val currentHash = focused.hashCode()
                if (currentHash != lastFocusedNodeHash) {
                    lastFocusedNodeHash = currentHash
                    charOffsetInFocusedNode = -1
                }

                charOffsetInFocusedNode++
                if (charOffsetInFocusedNode < text.length) {
                    val targetChar = text[charOffsetInFocusedNode]
                    val phonetic = com.shinji.serena.ime.SerenaPhoneticEngine.getPhoneticReading(targetChar)
                    soundHelper?.playFocusMove()
                    speak(phonetic, TextToSpeech.QUEUE_FLUSH)
                    return
                } else {
                    charOffsetInFocusedNode = -1
                    navigateLinearFocus(forward = true)
                    return
                }
            }

            navigateLinearFocus(forward = true)
        } catch (e: Exception) {
            navigateLinearFocus(forward = true)
        }
    }

    private fun handleDefaultGranularityPrevious() {
        try {
            val focused = getAccessibilityFocusedNode()
            if (focused == null) {
                navigateLinearFocus(forward = false)
                return
            }

            val className = focused.className?.toString() ?: ""
            // 1. スライダー (SeekBar / Slider / ProgressBar)
            if (className.contains("SeekBar", ignoreCase = true) || className.contains("Slider", ignoreCase = true) || className.contains("ProgressBar", ignoreCase = true)) {
                val success = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id) ||
                              focused.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (success) {
                    soundHelper?.playActionDone()
                    return
                }
            }

            // 2. カスタムアクションを持つ要素
            val customActions = focused.actionList.filter { 
                it.id != AccessibilityNodeInfo.ACTION_CLICK && 
                it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
                it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
                !it.label.isNullOrEmpty()
            }
            if (customActions.isNotEmpty()) {
                navigateCustomActions(forward = false)
                return
            }

            // 3. 通常の場所: 1文字ずつフォネティック詳細戻り
            val text = getNodeText(focused)
            if (text.isNotEmpty()) {
                val currentHash = focused.hashCode()
                if (currentHash != lastFocusedNodeHash) {
                    lastFocusedNodeHash = currentHash
                    charOffsetInFocusedNode = text.length
                }

                charOffsetInFocusedNode--
                if (charOffsetInFocusedNode >= 0) {
                    val targetChar = text[charOffsetInFocusedNode]
                    val phonetic = com.shinji.serena.ime.SerenaPhoneticEngine.getPhoneticReading(targetChar)
                    soundHelper?.playFocusMove()
                    speak(phonetic, TextToSpeech.QUEUE_FLUSH)
                    return
                } else {
                    charOffsetInFocusedNode = -1
                    navigateLinearFocus(forward = false)
                    return
                }
            }

            navigateLinearFocus(forward = false)
        } catch (e: Exception) {
            navigateLinearFocus(forward = false)
        }
    }

    private var lastScrollTime = 0L
    private var lastScrollEventTime = 0L

    fun scrollHorizontalForward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 450) return true
        lastScrollTime = now

        val scrollNode = focusNavigator?.findHorizontalScrollableNode(forward = true)
        Log.i(TAG, "scrollHorizontalForward: scrollNode=${scrollNode?.viewIdResourceName} class=${scrollNode?.className}")

        if (scrollNode != null) {
            val success = focusNavigator?.performHorizontalScroll(scrollNode, forward = true) == true
            Log.i(TAG, "scrollHorizontalForward: performHorizontalScroll result=$success")
            if (success) {
                soundHelper?.playFocusMove()
                speak("次のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
                return true
            }
        }

        val root = rootInActiveWindow
        val fallbackSuccess = root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        if (fallbackSuccess) {
            soundHelper?.playFocusMove()
            speak("次のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            return true
        }

        soundHelper?.playLastItemEdgeSound()
        return false
    }

    fun scrollHorizontalBackward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 450) return true
        lastScrollTime = now

        val scrollNode = focusNavigator?.findHorizontalScrollableNode(forward = false)
        Log.i(TAG, "scrollHorizontalBackward: scrollNode=${scrollNode?.viewIdResourceName} class=${scrollNode?.className}")

        if (scrollNode != null) {
            val success = focusNavigator?.performHorizontalScroll(scrollNode, forward = false) == true
            Log.i(TAG, "scrollHorizontalBackward: performHorizontalScroll result=$success")
            if (success) {
                soundHelper?.playFocusMove()
                speak("前のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
                return true
            }
        }

        val root = rootInActiveWindow
        val fallbackSuccess = root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        if (fallbackSuccess) {
            soundHelper?.playFocusMove()
            speak("前のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            return true
        }

        soundHelper?.playFirstItemEdgeSound()
        return false
    }

    fun scrollVerticalForward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 450) return true
        lastScrollTime = now

        val scrollNode = focusNavigator?.findScrollableNode(forward = true)
        val success = if (scrollNode != null) {
            focusNavigator?.performScroll(scrollNode, forward = true) == true
        } else {
            val root = rootInActiveWindow
            val focused = getAccessibilityFocusedNode() ?: root
            val actionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            focused?.performAction(actionId) == true || focused?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        }

        if (success) {
            soundHelper?.playFocusMove()
            speak("次へ縦スクロールしました", TextToSpeech.QUEUE_FLUSH)
            return true
        } else {
            soundHelper?.playLastItemEdgeSound()
            return false
        }
    }

    fun scrollVerticalBackward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 450) return true
        lastScrollTime = now

        val scrollNode = focusNavigator?.findScrollableNode(forward = false)
        val success = if (scrollNode != null) {
            focusNavigator?.performScroll(scrollNode, forward = false) == true
        } else {
            val root = rootInActiveWindow
            val focused = getAccessibilityFocusedNode() ?: root
            val actionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            focused?.performAction(actionId) == true || focused?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        }

        if (success) {
            soundHelper?.playFocusMove()
            speak("前へ縦スクロールしました", TextToSpeech.QUEUE_FLUSH)
            return true
        } else {
            soundHelper?.playFirstItemEdgeSound()
            return false
        }
    }

    private fun navigateCustomActions(forward: Boolean) {
        val node = getAccessibilityFocusedNode()
        if (node == null) {
            speak("フォーカスされている項目がありません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        val customActions = node.actionList.filter { 
            it.id != AccessibilityNodeInfo.ACTION_CLICK && 
            it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
            it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
            !it.label.isNullOrEmpty()
        }
        if (customActions.isEmpty()) {
            speak("利用可能なカスタムアクションはありません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        selectedCustomActionIndex = if (forward) {
            (selectedCustomActionIndex + 1) % customActions.size
        } else {
            if (selectedCustomActionIndex - 1 < 0) customActions.size - 1 else selectedCustomActionIndex - 1
        }
        val action = customActions[selectedCustomActionIndex]
        soundHelper?.playFocusMove()
        speak("アクション: ${action.label}。実行するにはダブルタップします", TextToSpeech.QUEUE_FLUSH)
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

        // 次へ進む場合で、現在のページの末尾に達した時
        if (forward && (currentIndex >= nodes.size - 1)) {
            val horizontalScrollNode = focusNavigator?.findHorizontalScrollableNode(forward = true)
            val verticalScrollNode = focusNavigator?.findScrollableNode(forward = true)
            
            var didScroll = false
            if (horizontalScrollNode != null && focusNavigator?.performHorizontalScroll(horizontalScrollNode, forward = true) == true) {
                didScroll = true
                soundHelper?.playFocusMove()
            } else if (verticalScrollNode != null && focusNavigator?.performScroll(verticalScrollNode, forward = true) == true) {
                didScroll = true
                soundHelper?.playFocusMove()
            }

            if (didScroll) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newRoot = rootInActiveWindow ?: return@postDelayed
                    val newNodes = collectAccessibleNodes(newRoot)
                    if (newNodes.isNotEmpty()) {
                        val firstNode = newNodes.first()
                        firstNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        soundHelper?.playFocusMove()
                        announceNode(firstNode)
                    }
                }, 350)
                return
            } else {
                soundHelper?.playLastItemEdgeSound()
                return
            }
        } else if (!forward && (currentIndex <= 0)) {
            val horizontalScrollNode = focusNavigator?.findHorizontalScrollableNode(forward = false)
            val verticalScrollNode = focusNavigator?.findScrollableNode(forward = false)
            
            var didScroll = false
            if (horizontalScrollNode != null && focusNavigator?.performHorizontalScroll(horizontalScrollNode, forward = false) == true) {
                didScroll = true
                soundHelper?.playFocusMove()
            } else if (verticalScrollNode != null && focusNavigator?.performScroll(verticalScrollNode, forward = false) == true) {
                didScroll = true
                soundHelper?.playFocusMove()
            }

            if (didScroll) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newRoot = rootInActiveWindow ?: return@postDelayed
                    val newNodes = collectAccessibleNodes(newRoot)
                    if (newNodes.isNotEmpty()) {
                        val prevNode = newNodes.first()
                        prevNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        soundHelper?.playFocusMove()
                        announceNode(prevNode)
                    }
                }, 350)
                return
            } else {
                soundHelper?.playFirstItemEdgeSound()
                return
            }
        }

        val targetIndex = if (forward) {
            (currentIndex + 1).coerceIn(0, nodes.size - 1)
        } else {
            (currentIndex - 1).coerceIn(0, nodes.size - 1)
        }

        val targetNode = nodes[targetIndex]
        currentFocus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (success) {
            if (targetIndex == 0) {
                soundHelper?.playFirstItemEdgeSound()
            } else if (targetIndex == nodes.size - 1) {
                soundHelper?.playLastItemEdgeSound()
            } else {
                soundHelper?.playFocusMove()
            }
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

    private fun collectAccessibleNodes(root: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val navNodes = focusNavigator?.collectAccessibleNodes(root)
        if (!navNodes.isNullOrEmpty()) return navNodes
        val list = mutableListOf<AccessibilityNodeInfo>()
        val targetRoot = root ?: rootInActiveWindow ?: return emptyList()
        traverseTree(targetRoot, list)
        return list
    }

    private fun traverseTree(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return

        val text = getNodeText(node)
        val isClickableRow = (node.isClickable || node.isCheckable) && node.childCount > 0 && text.isNotEmpty()
        if (isClickableRow) {
            list.add(node)
            return // クリック可能な行コンテナ（セレナメニュー項目等）を1つの統合フォーカス項目として扱う
        }

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
        val isActionable = node.isClickable || node.isCheckable || node.isFocusable || node.isLongClickable || node.safeIsHeading
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

        // 0. カスタムアクションモード時の選択アクション実行
        if (currentGranularity == GranularityMode.ACTIONS && selectedCustomActionIndex >= 0) {
            val customActions = focusedNode.actionList.filter { 
                it.id != AccessibilityNodeInfo.ACTION_CLICK && 
                it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
                it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
                !it.label.isNullOrEmpty()
            }
            if (selectedCustomActionIndex < customActions.size) {
                val action = customActions[selectedCustomActionIndex]
                if (focusedNode.performAction(action.id)) {
                    speak("${action.label} を実行しました", TextToSpeech.QUEUE_FLUSH)
                    return
                }
            }
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

    fun triggerSerenaMenu() {
        showNormalSerenaMenu()
    }

    fun showNormalSerenaMenu(node: AccessibilityNodeInfo? = null) {
        val snapshotRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        val snapshotFocused = node ?: getAccessibilityFocusedNode()
        soundHelper?.playMenuOpen()
        val curtainLabel = if (screenCurtainHelper?.isCurtainEnabled == true) "🌑 スクリーンカーテンを解除" else "🌑 スクリーンカーテン (画面非表示・節電)"
        val isTalkBackMode = prefs.getBoolean(KEY_TALKBACK_MODE, false)
        val modeLabel = if (isTalkBackMode) "🔄 モード切替 (現在: TalkBack互換モード)" else "🔄 モード切替 (現在: serenaオリジナルモード)"
        val filterName = notificationFilterHelper?.currentMode?.displayName ?: "自動"
        val items = listOf(
            serenaMenuItem("💡", "画面スマート要約 (画面の全体構造・項目数・詳細解説)") {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val summary = com.shinji.serena.ai.SmartScreenSummaryEngine(this).generateDetailedSummary(snapshotRoot, snapshotFocused)
                    Log.i(TAG, "Generated Smart Screen Summary: $summary")
                    speak(summary, TextToSpeech.QUEUE_FLUSH)
                }, 300)
            },
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
            serenaMenuItem("🎨", "カラー・照明・お日様チェッカー (部屋の明るさ・太陽・色の判定)") {
                announceColorAndLightReport()
            },
            serenaMenuItem("🧭", "空間電子コンパス (現在向いている方角と角度)") {
                announceCompassHeading()
            },
            serenaMenuItem("🚶‍♂️", "徒歩ナビ・現在地と目的地クロックポジション案内") {
                announceCurrentLocationAndNav()
            },
            serenaMenuItem("📷", "カメラ・文字読み取り (On-Device OCR)") {
                launchCameraOcr()
            },
            serenaMenuItem("👤", "カメラ・表情と人物判定 (On-Device Face AI)") {
                launchCameraFaceAnalysis()
            },
            serenaMenuItem("📦", "カメラ・物体と周囲の認識 (Gemini Nano On-Device AI)") {
                launchCameraObjectAnalysis()
            },
            serenaMenuItem("🌐", "リアルタイム環境実況モード切替 (カメラ自動解説)") {
                toggleLiveEnvironmentDescription()
            },
            serenaMenuItem("⚡", "読み上げ速度の変更 (トグル切り替え)") {
                toggleSpeechRateQuick()
            },
            serenaMenuItem("📄", "次のページへ移動 (本めくり)") {
                scrollHorizontalForward()
            },
            serenaMenuItem("📄", "前のページへ移動 (本めくり)") {
                scrollHorizontalBackward()
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
        speak("セレナメニューが開きました。全${items.size}項目。", TextToSpeech.QUEUE_FLUSH)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val dialog = serenaMenuDialog(this, false, items)
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show dialog: ${e.message}")
                Toast.makeText(this, "セレナメニュー: 全${items.size}項目", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showActionsMenu(targetNode: AccessibilityNodeInfo?) {
        val node = targetNode ?: getAccessibilityFocusedNode()
        val items = mutableListOf<serenaMenuItem>()

        items.add(serenaMenuItem("👆", "要素を長押し (ロングタップ)") {
            node?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        })

        val targetPkg = resolveTargetPackageName(node)
        if (!targetPkg.isNullOrEmpty()) {
            val appLabel = try {
                val pm = packageManager
                val info = pm.getApplicationInfo(targetPkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                targetPkg
            }

            items.add(serenaMenuItem("🗑️", "「$appLabel」をアンインストール") {
                launchUninstallApp(targetPkg)
            })
            items.add(serenaMenuItem("ℹ️", "「$appLabel」のアプリ情報を開く") {
                launchAppDetailsSettings(targetPkg)
            })
        } else {
            items.add(serenaMenuItem("🗑️", "アプリのアンインストール (設定から選択)") {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    speak("アプリ一覧設定を開きました。アンインストールするアプリを選択してください", TextToSpeech.QUEUE_FLUSH)
                } catch (e: Exception) {
                    speak("アプリ設定を開けませんでした", TextToSpeech.QUEUE_FLUSH)
                }
            })
        }

        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val customActions = node.actionList
            for (action in customActions) {
                val label = action.label?.toString()
                if (!label.isNullOrBlank()) {
                    items.add(serenaMenuItem("⚡", label) {
                        node.performAction(action.id)
                    })
                }
            }
        }

        soundHelper?.playMenuOpen()
        speak("アクションメニューを開きました。全${items.size}項目。", TextToSpeech.QUEUE_FLUSH)
        try {
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show actions menu: ${e.message}")
        }
    }

    private fun resolveTargetPackageName(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val directPkg = node.packageName?.toString()
        val isLauncher = directPkg == "com.android.launcher" || 
                         directPkg == "com.google.android.apps.nexuslauncher" || 
                         directPkg?.contains("launcher", ignoreCase = true) == true ||
                         directPkg?.contains("home", ignoreCase = true) == true

        if (!isLauncher && !directPkg.isNullOrEmpty() && directPkg != packageName) {
            return directPkg
        }

        val label = node.text?.toString() ?: node.contentDescription?.toString()
        if (!label.isNullOrBlank()) {
            try {
                val pm = packageManager
                val apps = pm.getInstalledApplications(0)
                for (app in apps) {
                    val appLabel = pm.getApplicationLabel(app).toString()
                    if (appLabel.equals(label, ignoreCase = true) || label.contains(appLabel, ignoreCase = true)) {
                        if (app.packageName != packageName) {
                            return app.packageName
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Package resolve error: ${e.message}")
            }
        }

        return if (!directPkg.isNullOrEmpty() && directPkg != packageName) directPkg else null
    }

    private fun launchUninstallApp(pkgName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$pkgName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            speak("アンインストール確認画面を開きました", TextToSpeech.QUEUE_FLUSH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch uninstall: ${e.message}")
            try {
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$pkgName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                speak("アンインストール画面を開けませんでした", TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun launchAppDetailsSettings(pkgName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkgName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app info: ${e.message}")
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

    fun unlockKeyguardSwipe() {
        soundHelper?.playActionDone()
        speak("ロック画面を上にスワイプしてロック解除します", TextToSpeech.QUEUE_FLUSH)
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val path = android.graphics.Path().apply {
            moveTo(width * 0.50f, height * 0.85f)
            lineTo(width * 0.50f, height * 0.15f)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 150)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        }, null)
    }

    fun launchRealtimeLiveSceneCommentary() {
        soundHelper?.playActionDone()
        speak("リアルタイム実況AIカメラを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "OBJECT")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LiveVision: ${e.message}")
        }
    }

    fun launchAiAssistant() {
        soundHelper?.playActionDone()
        assistantHelper?.startListening()
    }

    fun launchCameraOcr() {
        soundHelper?.playClick()
        speak("文字読み取りカメラを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "OCR")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch OCR: ${e.message}")
        }
    }

    fun launchCameraFaceAnalysis() {
        soundHelper?.playClick()
        speak("表情・人物判定カメラを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "FACE")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Face: ${e.message}")
        }
    }

    fun launchCameraObjectAnalysis() {
        soundHelper?.playClick()
        speak("Gemini Nano AIカメラを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "OBJECT")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Object: ${e.message}")
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

    fun announceCurrentLocationAndNav() {
        soundHelper?.playActionDone()
        val nav = walkingNavigator
        if (nav != null) {
            val text = if (nav.activeDestination != null) {
                nav.getNavigationGuidance()
            } else {
                nav.getCurrentLocationSummary()
            }
            speak(text, TextToSpeech.QUEUE_FLUSH)
        } else {
            speak("位置情報機能が利用できません", TextToSpeech.QUEUE_FLUSH)
        }
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
                showNormalSerenaMenu()
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

    fun announceColorAndLightReport() {
        soundHelper?.playActionDone()
        val report = colorAndLightHelper?.buildFullSensoryReport()
            ?: "センサー情報を取得できませんでした。"
        speak(report, TextToSpeech.QUEUE_FLUSH)
    }

    fun announceCompassHeading() {
        soundHelper?.playFocusMove()
        val heading = compassHelper?.getDirectionAnnouncement()
            ?: "コンパス情報を取得できませんでした。"
        speak(heading, TextToSpeech.QUEUE_FLUSH)
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
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notificationText = event.text.joinToString(" ").trim()
                if (notificationText.isNotEmpty()) {
                    if (notificationFilterHelper?.shouldAnnounce(pkgName, notificationText) == true) {
                        val summary = notificationFilterHelper?.formatSmartNotificationSummary(pkgName, "", notificationText)
                            ?: "通知: $notificationText"
                        speak(summary, TextToSpeech.QUEUE_ADD)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkCallState(pkgName)

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

            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                val node = event.source ?: return
                lastHoveredNode = node
                soundHelper?.playFocusMove()
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                if (isTtsReady) {
                    announceNode(node)
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val node = event.source ?: return
                lastHoveredNode = node
                soundHelper?.playFocusMove()
                if (isTtsReady) {
                    announceNode(node)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                soundHelper?.playClick()
                val node = event.source
                if (node != null && isTtsReady) {
                    val text = getNodeText(node)
                    if (text.isNotEmpty()) {
                        speak("$text をタップ", TextToSpeech.QUEUE_FLUSH)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = System.currentTimeMillis()
                if (now - lastScrollEventTime < 800) return
                lastScrollEventTime = now

                val itemCount = event.itemCount
                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                val text = event.text.joinToString(" ").trim()
                val contentDesc = event.contentDescription?.toString()?.trim() ?: ""

                if (contentDesc.isNotEmpty() && (contentDesc.contains("ページ") || contentDesc.contains("page"))) {
                    speak(contentDesc, TextToSpeech.QUEUE_FLUSH)
                } else if (text.isNotEmpty() && (text.contains("ページ") || text.contains("page"))) {
                    speak(text, TextToSpeech.QUEUE_FLUSH)
                } else if (itemCount > 0 && fromIndex >= 0) {
                    val pageIndex = if (toIndex > fromIndex) "${fromIndex + 1}〜${toIndex + 1} / $itemCount 項目" else "${fromIndex + 1} / $itemCount 項目"
                    speak(pageIndex, TextToSpeech.QUEUE_FLUSH)
                }
            }

            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                val text = event.text.joinToString(" ").trim()
                if (text.isNotEmpty() && isTtsReady) {
                    speak(text, TextToSpeech.QUEUE_FLUSH)
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> {
                val text = event.text.joinToString(" ").trim()
                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                if (text.isNotEmpty() && fromIndex >= 0 && fromIndex < text.length && isTtsReady) {
                    val end = if (toIndex in (fromIndex + 1)..text.length) toIndex else fromIndex + 1
                    val traversed = text.substring(fromIndex, end)
                    val detail = com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.getKanjiDetail(traversed)
                    if (detail.isNotEmpty() && detail != traversed) {
                        speak("$traversed ($detail)", TextToSpeech.QUEUE_FLUSH)
                    } else {
                        speak(traversed, TextToSpeech.QUEUE_FLUSH)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString(" ").trim()
                val addedCount = event.addedCount
                val removedCount = event.removedCount
                val beforeText = event.beforeText?.toString() ?: ""
                val sourceNode = try { event.source } catch (_: Exception) { null }
                val viewId = sourceNode?.viewIdResourceName?.lowercase() ?: ""
                val isPasswordField = event.isPassword || 
                        sourceNode?.isPassword == true ||
                        viewId.contains("pin") ||
                        viewId.contains("password") ||
                        viewId.contains("keyguard")

                if (isTtsReady) {
                    if (isPasswordField) {
                        if (addedCount > 0) {
                            soundHelper?.playClick()
                            val bulletText = if (addedCount == 1) "黒丸" else "黒丸 ${addedCount}文字"
                            speak(bulletText, TextToSpeech.QUEUE_FLUSH)
                        } else if (removedCount > 0) {
                            soundHelper?.playActionDone()
                            val delText = if (removedCount == 1) "黒丸を1文字削除" else "黒丸を${removedCount}文字削除"
                            speak(delText, TextToSpeech.QUEUE_FLUSH)
                        }
                    } else if (addedCount > 0 && text.isNotEmpty()) {
                        val fromIndex = event.fromIndex
                        val addedText = if (fromIndex >= 0 && fromIndex + addedCount <= text.length) {
                            text.substring(fromIndex, fromIndex + addedCount)
                        } else {
                            text
                        }
                        if (addedText.length == 1) {
                            val detail = com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.getKanjiDetail(addedText)
                            if (detail.isNotEmpty() && detail != addedText) {
                                speak("$addedText ($detail)", TextToSpeech.QUEUE_FLUSH)
                            } else {
                                speak(addedText, TextToSpeech.QUEUE_FLUSH)
                            }
                        } else {
                            speak(addedText, TextToSpeech.QUEUE_FLUSH)
                        }
                    } else if (removedCount > 0) {
                        val fromIndex = event.fromIndex
                        val deletedText = if (beforeText.isNotEmpty() && fromIndex >= 0 && fromIndex + removedCount <= beforeText.length) {
                            beforeText.substring(fromIndex, fromIndex + removedCount)
                        } else {
                            ""
                        }
                        if (deletedText.isNotEmpty()) {
                            speak("$deletedText を削除", TextToSpeech.QUEUE_FLUSH)
                        } else {
                            speak("削除", TextToSpeech.QUEUE_FLUSH)
                        }
                    } else if (text.isNotEmpty()) {
                        speak(text, TextToSpeech.QUEUE_FLUSH)
                    }
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

    private var lastCallCheckTimeMs = 0L

    private fun checkCallState(pkgName: String) {
        val now = System.currentTimeMillis()
        if (now - lastCallCheckTimeMs < 1500) return
        lastCallCheckTimeMs = now

        val isCallPkg = isCallRelatedPackage(pkgName)
        if (!isCallPkg) return

        val root = rootInActiveWindow
        val windowText = if (root != null) parseCallerFromRootNode() else ""

        val isCurrentlyInCall = (
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
            val calendar = Calendar.getInstance()
            val timeStr = "${calendar.get(Calendar.HOUR_OF_DAY)}時${calendar.get(Calendar.MINUTE)}分"
            speak("${activeCallApp}の通話を開始しました。開始時刻は ${timeStr} です。", TextToSpeech.QUEUE_FLUSH)
            Log.i(TAG, "Call started ($activeCallApp) at $callStartTimeMs")
        } else if (!isCurrentlyInCall && isCallActive) {
            isCallActive = false
            val elapsedMs = (System.currentTimeMillis() - callStartTimeMs).coerceAtLeast(1000L)
            val durationText = formatDuration(elapsedMs)
            val appLabel = if (activeCallApp.isNotEmpty()) activeCallApp else "電話"
            val calendar = Calendar.getInstance()
            val timeStr = "${calendar.get(Calendar.HOUR_OF_DAY)}時${calendar.get(Calendar.MINUTE)}分"
            AlphaTelemetryHelper.getInstance(this).recordCallCompleted(elapsedMs / 1000)
            speak("${appLabel}の通話が終了しました。通話時間は ${durationText} です。終了時刻は ${timeStr} です。", TextToSpeech.QUEUE_FLUSH)
            Log.i(TAG, "$appLabel ended. Duration: $durationText, EndTime: $timeStr")
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
        if (announcement == lastSpokenText && (currentTime - lastSpokenTime) < 1500) {
            return
        }

        lastSpokenText = announcement
        lastSpokenTime = currentTime
        soundHelper?.playFocusMovePanned(normalizedX)
        speak(announcement, TextToSpeech.QUEUE_FLUSH)
    }

    private fun buildNodeAnnouncement(node: AccessibilityNodeInfo): String {
        var text = getNodeText(node)
        val role = getNodeRole(node)
        val state = getNodeState(node)
        val pkg = node.packageName?.toString() ?: ""

        // あやめキーボード (jp.yama3nomori.ayame) または IME候補バーの漢字詳細読み上げ拡張
        if (pkg.contains("ayame", ignoreCase = true) || pkg.contains("markdownhelperkeyboard", ignoreCase = true)) {
            if (text.isNotEmpty() && text.length in 1..4) {
                val detail = com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.getKanjiDetail(text)
                if (detail.isNotEmpty() && detail != text && !text.contains("(")) {
                    text = "$text ($detail)"
                }
            }
        }

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
            // === 画面ロック・PINコード入力テンキー & 特殊キーボード ===
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
            name.contains("dialpad") || name.contains("digits") -> "ダイヤルキー"

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

    @Suppress("DEPRECATION")
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

    fun handleMagicTapAction() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRinging = audioManager?.mode == AudioManager.MODE_RINGTONE

        if (isRinging) {
            speak("電話に応答します", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
            return
        }

        if (isCallActive || audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
            speak("通話を終了します", TextToSpeech.QUEUE_FLUSH)
            endCallDirectly()
            return
        }

        speak("メディアの再生または一時停止", TextToSpeech.QUEUE_FLUSH)
        simulateMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun endCallDirectly(): Boolean {
        var terminated = false

        // 1. TelecomManager による直接切断 (Android 9.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            try {
                if (telecomManager != null && checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    terminated = telecomManager.endCall()
                    Log.i(TAG, "TelecomManager.endCall executed: $terminated")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TelecomManager endCall error: ${e.message}")
            }
        }

        // 2. 画面上の「通話終了」「切断」ノード探索＆クリック
        val root = rootInActiveWindow
        if (root != null) {
            val endNodes = root.findAccessibilityNodeInfosByText("終了") +
                    root.findAccessibilityNodeInfosByText("切断") +
                    root.findAccessibilityNodeInfosByText("通話を終了") +
                    root.findAccessibilityNodeInfosByText("End call") +
                    root.findAccessibilityNodeInfosByText("Decline")
            for (node in endNodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    terminated = true
                    Log.i(TAG, "End call button clicked via accessibility node.")
                    break
                }
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        terminated = true
                        Log.i(TAG, "End call parent button clicked.")
                        break
                    }
                    parent = parent.parent
                }
                if (terminated) break
            }
        }

        // 3. メディアキー & ヘッドセットフックシミュレーション
        simulateMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
        simulateMediaKey(KeyEvent.KEYCODE_ENDCALL)

        return terminated
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
            val tagalogLocale = Locale.Builder().setLanguage("fil").setRegion("PH").build()
            val availability = tts?.isLanguageAvailable(tagalogLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                return tagalogLocale
            }
            return Locale.ENGLISH
        }

        return Locale.ENGLISH
    }

    private var isSpeechPaused = false
    private var currentSpeakingUtterance: String? = null

    private var isMuted = false

    fun toggleSpeechMute() {
        isMuted = !isMuted
        if (isMuted) {
            stopSpeech()
            Toast.makeText(this, "消音モード", Toast.LENGTH_SHORT).show()
        } else {
            speak("消音を解除しました", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun focusFirstElement() {
        val root = rootInActiveWindow ?: return
        val nodes = collectAccessibleNodes(root)
        if (nodes.isNotEmpty()) {
            val firstNode = nodes.first()
            firstNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            soundHelper?.playFirstItemEdgeSound()
            announceNode(firstNode)
        }
    }

    private fun focusLastElement() {
        val root = rootInActiveWindow ?: return
        val nodes = collectAccessibleNodes(root)
        if (nodes.isNotEmpty()) {
            val lastNode = nodes.last()
            lastNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            soundHelper?.playLastItemEdgeSound()
            announceNode(lastNode)
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (text.isBlank() || isMuted) return
        if (!isTtsReady || tts == null) {
            synchronized(pendingSpeechQueue) {
                if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                    pendingSpeechQueue.clear()
                }
                pendingSpeechQueue.add(Pair(text, queueMode))
            }
            return
        }

        val processedText = emojiHelper?.translateEmojiAndKaomoji(text) ?: text
        lastSpokenText = processedText
        currentSpeakingUtterance = processedText
        isSpeechPaused = false

        try {
            val targetLocale = detectLanguage(processedText)
            tts?.language = targetLocale
            AlphaTelemetryHelper.getInstance(this).incrementTtsCount(targetLocale)
        } catch (_: Exception) {}

        tts?.speak(processedText, queueMode, null, "serenaUtterance_${System.currentTimeMillis()}")
    }

    fun toggleSpeechPauseResume() {
        if (tts?.isSpeaking == true) {
            isSpeechPaused = true
            soundHelper?.playActionDone()
            tts?.stop()
        } else if (isSpeechPaused && !currentSpeakingUtterance.isNullOrBlank()) {
            isSpeechPaused = false
            soundHelper?.playActionDone()
            speak(currentSpeakingUtterance!!, TextToSpeech.QUEUE_FLUSH)
        } else {
            val focusNode = getAccessibilityFocusedNode()
            if (focusNode != null) {
                soundHelper?.playActionDone()
                announceNode(focusNode)
            }
        }
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
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK -> checkHourlyChime()
                    Intent.ACTION_USER_UNLOCKED -> {
                        Log.i(TAG, "Device unlocked from Direct Boot. Reloading storage & TTS.")
                        try {
                            val safeCtx = getSafeContext()
                            prefs = safeCtx.getSafeSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            updateTtsSettings()
                            if (tts == null || !isTtsReady) {
                                initTts(safeCtx)
                            }
                            soundHelper?.playActionDone()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling USER_UNLOCKED: ${e.message}")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
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
        wifiConnectivityHelper?.stopMonitoring()
        wifiConnectivityHelper = null
        batteryHelper?.stop()
        batteryHelper = null
        shakeDetectorHelper?.stop()
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
        try {
            soundHelper?.playMenuOpen()
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "LIVE")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LiveVisionActivity: ${e.message}")
            speak("リアルタイム実況カメラの起動に失敗しました。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun showClipboardHistoryQuickly() {
        val lastText = clipboardHelper?.getHistory()?.firstOrNull() ?: "履歴はありません"
        speak("最新のクリップボード履歴: $lastText", TextToSpeech.QUEUE_FLUSH)
    }

    fun showSerenaAssistantDialog() {
        soundHelper?.playMenuOpen()
        speak("serena アシスタントメニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val items = listOf(
            serenaMenuItem("🧠", "画面のスマートAI要約 (オンデバイス解析)") {
                summarizeCurrentScreen()
            },
            serenaMenuItem("🌸", "あたたかいハートフルメッセージ ＆ 挨拶") {
                assistantHelper?.speakWarmHeartGreeting()
            },
            serenaMenuItem("🎙️", "音声コマンド入力") {
                assistantHelper?.startListening()
            },
            serenaMenuItem("📷", "カメラ文字読み取り (OCR)") {
                launchCameraOcr()
            },
            serenaMenuItem("🖼️", "AIカメラ物体・あたたか情景認識") {
                launchCameraObjectAnalysis()
            },
            serenaMenuItem("🔋", "端末ステータス・電池アナウンス") {
                announceFullStatus()
            }
        )
        try {
            val dialog = serenaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "showSerenaAssistantDialog error: ${e.message}")
        }
    }

    private var isSummarizing = false

    @Suppress("DEPRECATION")
    fun summarizeCurrentScreen() {
        if (isSummarizing) return
        isSummarizing = true
        try {
            val root = rootInActiveWindow
            if (root == null) {
                speak("画面情報を取得できませんでした。", TextToSpeech.QUEUE_FLUSH)
                return
            }
            val collectedText = mutableListOf<String>()
            collectScreenTexts(root, collectedText)

            if (collectedText.isEmpty()) {
                speak("画面上に読み取れるテキストが見つかりませんでした。", TextToSpeech.QUEUE_FLUSH)
                return
            }

            val topTexts = collectedText.take(6).joinToString("、")
            val summary = "画面の要点要約です。現在表示されている主な要素は、${topTexts} など合計 ${collectedText.size} 件の項目があります。"
            speak(summary, TextToSpeech.QUEUE_FLUSH)
        } finally {
            isSummarizing = false
        }
    }

    @Suppress("DEPRECATION")
    private fun collectScreenTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val text = getNodeText(node).trim()
        if (text.isNotEmpty() && text.length > 1 && !list.contains(text)) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScreenTexts(child, list)
            child.recycle()
        }
    }
}

private val AccessibilityNodeInfo.safeIsHeading: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isHeading else false



