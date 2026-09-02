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
        const val KEY_CALL_PERIODIC_ANNOUNCE = "call_periodic_announce"
        const val KEY_HOURLY_CHIME_ENABLED = "hourly_chime_enabled"
        const val KEY_SHAKE_THRESHOLD = "shake_threshold"
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
    var activeMenuDialog: serenaMenuDialog? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0L
    var lastFocusTimeMs: Long = 0L
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
    var spatialObstacleSonarHelper: SpatialObstacleSonarHelper? = null
    var walkingNavigator: com.shinji.serena.navigation.SerenaWalkingNavigator? = null
    var osmValhallaNavHelper: com.shinji.serena.location.OsmValhallaNavigationHelper? = null
    var spatialSurroundingRadarHelper: com.shinji.serena.location.SpatialSurroundingRadarHelper? = null
    private var shakeDetectorHelper: ShakeDetectorHelper? = null
    private var spatialHapticTouchMapHelper: SpatialHapticTouchMapHelper? = null
    private var isLiveEnvironmentModeActive = false
    private var liveEnvironmentHandler: android.os.Handler? = null
    private var liveEnvironmentRunnable: Runnable? = null

    var aiAutoLabelHelper: AiAutoLabelHelper? = null
    var morningSummaryHelper: MorningSummaryHelper? = null
    var brailleController: com.shinji.serena.braille.BrailleDisplayController? = null
    var instantTranslationHelper: com.shinji.serena.translation.InstantTranslationHelper? = null
    var soundRecognitionHelper: com.shinji.serena.sound.SoundRecognitionHapticsHelper? = null
    var visualAudioDescriptionHelper: com.shinji.serena.ai.VisualAudioDescriptionHelper? = null
    var smartScreenSummaryEngine: com.shinji.serena.ai.SmartScreenSummaryEngine? = null

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
            spatialObstacleSonarHelper = SpatialObstacleSonarHelper(safeContext)
            walkingNavigator = com.shinji.serena.navigation.SerenaWalkingNavigator(this, compassHelper).apply { try { startTracking() } catch (_: Exception) {} }
            osmValhallaNavHelper = com.shinji.serena.location.OsmValhallaNavigationHelper(this, soundHelper) { msg ->
                speak(msg, android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            }
            spatialSurroundingRadarHelper = com.shinji.serena.location.SpatialSurroundingRadarHelper(this, soundHelper) { msg ->
                speak(msg, android.speech.tts.TextToSpeech.QUEUE_FLUSH)
            }
            wifiConnectivityHelper = WifiConnectivityHelper(this).apply { try { startMonitoring() } catch (_: Exception) {} }
            batteryHelper = BatteryStateHelper(this).apply { try { start() } catch (_: Exception) {} }
            aiAutoLabelHelper = AiAutoLabelHelper(safeContext)
            morningSummaryHelper = MorningSummaryHelper(this)
            instantTranslationHelper = com.shinji.serena.translation.InstantTranslationHelper(safeContext)
            soundRecognitionHelper = com.shinji.serena.sound.SoundRecognitionHapticsHelper(safeContext).apply { try { start() } catch (_: Exception) {} }
            visualAudioDescriptionHelper = com.shinji.serena.ai.VisualAudioDescriptionHelper(safeContext)
            smartScreenSummaryEngine = com.shinji.serena.ai.SmartScreenSummaryEngine(this)

            // 点字ディスプレイ（Braille Display）コントローラー初期化
            brailleController = com.shinji.serena.braille.BrailleDisplayController(this, object : com.shinji.serena.braille.BrailleDisplayController.BrailleInteractionListener {
                override fun onBrailleCommand(command: com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand, routingIndex: Int) {
                    when (command) {
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.NAVIGATE_NEXT -> navigateLinearFocus(forward = true)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.NAVIGATE_PREVIOUS -> navigateLinearFocus(forward = false)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.NAVIGATE_UP -> cycleGranularity(forward = false)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.NAVIGATE_DOWN -> cycleGranularity(forward = true)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.PERFORM_CLICK -> performActivateCurrentFocus()
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.PERFORM_LONG_CLICK -> {
                            val node = getAccessibilityFocusedNode() ?: lastHoveredNode
                            node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)
                            soundHelper?.playClick()
                        }
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.ACTION_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.ACTION_MENU -> showNormalSerenaMenu()
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.SCROLL_FORWARD -> scrollVerticalForward()
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.SCROLL_BACKWARD -> scrollVerticalBackward()
                        com.shinji.serena.braille.BrailleProtocolHandler.BrailleCommand.ROUTING_CLICK -> performActivateCurrentFocus()
                        else -> {}
                    }
                }

                override fun onBrailleConnectionStateChanged(connected: Boolean, deviceName: String) {
                    if (connected) {
                        soundHelper?.playActionDone()
                        speak("点字ディスプレイ $deviceName に接続しました", TextToSpeech.QUEUE_FLUSH)
                    } else {
                        soundHelper?.playScroll()
                        if (deviceName.isNotEmpty()) {
                            speak("点字ディスプレイ $deviceName が切断されました", TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                }

                override fun onBrailleKeyInput(dots: Int) {
                    soundHelper?.playClick()
                }
            }).apply {
                try {
                    // ペアリング済みの点字ディスプレイがあれば自動接続を試行
                    connectToPairedBrailleDevice()
                } catch (_: Exception) {}
            }

            shakeDetectorHelper = ShakeDetectorHelper(safeContext) {
                announceFullStatus()
            }.apply { try { start() } catch (_: Exception) {} }

            focusNavigator = com.shinji.serena.navigation.SerenaFocusNavigator(this)
            soundHelper?.let {
                spatialHapticTouchMapHelper = SpatialHapticTouchMapHelper(it)
            }
            callManager = com.shinji.serena.telephony.SerenaCallManager(this, com.shinji.serena.speech.SerenaSpeechEngine(this))
            gestureDispatcher = com.shinji.serena.gesture.SerenaGestureDispatcher(this)
            com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.init(safeContext)
            
            // serenaオリジナルモードを正統デフォルトとして初期化
            if (!prefs.contains(KEY_TALKBACK_MODE)) {
                prefs.edit().putBoolean(KEY_TALKBACK_MODE, false).apply()
            }
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
            isTtsReady = false
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e(TAG, "initTts error: ${e.message}")
        }
    }

    private var hasSpokenStartupGreeting = false
    private var isStartupGreetingSpeaking = false
    private var wasKeyguardLocked = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                }
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == "serena_startup_greeting") {
                            isStartupGreetingSpeaking = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "serena_startup_greeting") {
                            isStartupGreetingSpeaking = false
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                flushPendingSpeechQueue()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId == "serena_startup_greeting") {
                            isStartupGreetingSpeaking = false
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                flushPendingSpeechQueue()
                            }
                        }
                    }
                })

                val result = tts?.setLanguage(Locale.JAPANESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                updateTtsSettings()
                isTtsReady = true
                speakStartupGreeting()
            } catch (e: Exception) {
                Log.e(TAG, "onInit configuration error: ${e.message}")
                isTtsReady = true
            }
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isTtsReady) initTts()
            }, 2000)
        }
    }

    private fun speakStartupGreeting() {
        if (!hasSpokenStartupGreeting && isTtsReady) {
            hasSpokenStartupGreeting = true
            isStartupGreetingSpeaking = true
            val welcomeMsg = "Magandang araw po, Shinji! Handa na si Serena para sa inyo! Mabuhay!"
            soundHelper?.playActionDone()
            try {
                tts?.language = detectLanguage(welcomeMsg)
            } catch (_: Exception) {}
            tts?.speak(welcomeMsg, TextToSpeech.QUEUE_FLUSH, null, "serena_startup_greeting")
            Log.i(TAG, "TTS initialized successfully. Spoke Tagalog startup greeting.")
            // フェイルセーフ（最悪の場合でも4.5秒後にフラグを解放）
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isStartupGreetingSpeaking) {
                    isStartupGreetingSpeaking = false
                    flushPendingSpeechQueue()
                }
            }, 4500)
        } else {
            flushPendingSpeechQueue()
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
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.notificationTimeout = 0
        var flags = info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
            flags = flags or AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
        }
        info.flags = flags
        serviceInfo = info
        Log.i(TAG, "serena AccessibilityService connected with full Interactive Windows, Multi-Finger & 2-Finger Passthrough flags=$flags.")

        speakStartupGreeting()

        // 再起動直後（Direct Boot）やサービス接続時の初期フォーカス自動捕捉
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked == true) {
                focusPinEntryField()
            } else {
                navigateLinearFocus(forward = true)
            }
        }, 3500)
    }

    @Volatile var isInternalGestureDispatching = false

    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (isInternalGestureDispatching) {
            Log.d(TAG, "onGesture ignored: internal gesture dispatch in progress.")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val gestureId = gestureEvent.gestureId
            Log.i(TAG, "onGesture(AccessibilityGestureEvent) received: $gestureId")
            return gestureDispatcher?.onGesture(gestureId) ?: handleGestureId(gestureId)
        }
        return false
    }

    @Deprecated("Deprecated in API 30+")
    override fun onGesture(gestureId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11/12/13/14/15/16) では onGesture(AccessibilityGestureEvent) で処理済みのため二重実行を完全防止
            return false
        }
        if (isInternalGestureDispatching) {
            Log.d(TAG, "onGesture(Int) ignored: internal gesture dispatch in progress.")
            return false
        }
        Log.i(TAG, "onGesture(Int) received: $gestureId")
        return gestureDispatcher?.onGesture(gestureId) ?: handleGestureId(gestureId)
    }

    private var lastUnlockTime = 0L

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
            // 2本指トリプルタップ (21 / GESTURE_2_FINGER_TRIPLE_TAP): 音声読み上げの消音（ミュート）切替
            21 -> {
                toggleSpeechMute()
                return true
            }
            // 2本指左フリック (27) / 上フリック (25): 次のページへ（横スクロール進む ＋ 先頭アイテム音声ガイド）
            27, 25 -> {
                if (isKeyguardLocked()) {
                    unlockKeyguardOrShowBouncer()
                    return true
                }
                return scrollHorizontalForward()
            }
            // 2本指右フリック (28) / 下フリック (26): 前のページへ（横スクロール戻る ＋ 先頭アイテム音声ガイド）
            28, 26 -> {
                return scrollHorizontalBackward()
            }

            // 3本指シングルタップ (22 / GESTURE_3_FINGER_SINGLE_TAP): Serena メニューを開く！
            22 -> {
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
            // 下→左スワイプ: 戻る (セレナメニュー表示中ならメニューを即時閉じる)
            GESTURE_SWIPE_DOWN_AND_LEFT -> {
                val menu = activeMenuDialog
                if (menu != null && menu.isShowing) {
                    activeMenuDialog = null
                    try { menu.dismiss() } catch (_: Exception) {}
                    soundHelper?.playActionDone()
                    speak("セレナメニューを閉じました", TextToSpeech.QUEUE_FLUSH)
                    return true
                }
                soundHelper?.playClick()
                speak("戻る", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
            // 上→左スワイプ: ホーム画面 (セレナメニュー表示中ならメニューも閉じる)
            GESTURE_SWIPE_UP_AND_LEFT -> {
                val menu = activeMenuDialog
                if (menu != null && menu.isShowing) {
                    activeMenuDialog = null
                    try { menu.dismiss() } catch (_: Exception) {}
                }
                soundHelper?.playClick()
                speak("ホーム画面", TextToSpeech.QUEUE_FLUSH)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
            // 左→上スワイプ (43): 最近使ったアプリ
            GESTURE_SWIPE_LEFT_AND_UP, 43 -> {
                val menu = activeMenuDialog
                if (menu != null && menu.isShowing) {
                    activeMenuDialog = null
                    try { menu.dismiss() } catch (_: Exception) {}
                }
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
        val text = lastSpokenText ?: ""
        if (text.isEmpty()) {
            speak("コピーする読み上げ履歴がありません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Serena", text)
        cm?.setPrimaryClip(clip)
        soundHelper?.playActionDone()
        val analysis = clipboardHelper?.addClip(text) ?: "クリップボードにコピーしました"
        speak(analysis, TextToSpeech.QUEUE_FLUSH)
    }

    fun scrollPageForward(onComplete: ((Boolean) -> Unit)? = null) {
        val horizontalNode = focusNavigator?.findHorizontalScrollableNode(forward = true)
        if (horizontalNode != null && focusNavigator?.performHorizontalScroll(horizontalNode, forward = true) == true) {
            soundHelper?.playScroll(isForward = true)
            speak("次のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ it(true) }, 350) }
            return
        }

        val verticalNode = focusNavigator?.findScrollableNode(forward = true)
        if (verticalNode != null && focusNavigator?.performScroll(verticalNode, forward = true) == true) {
            soundHelper?.playScroll(isForward = true)
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ it(true) }, 300) }
            return
        }

        // スクロール可能な要素が存在しない・スクロール上限に達した場合は端音を鳴らして停止
        soundHelper?.playLastItemEdgeSound()
        onComplete?.let { it(false) }
    }

    fun scrollPageBackward(onComplete: ((Boolean) -> Unit)? = null) {
        val horizontalNode = focusNavigator?.findHorizontalScrollableNode(forward = false)
        if (horizontalNode != null && focusNavigator?.performHorizontalScroll(horizontalNode, forward = false) == true) {
            soundHelper?.playScroll(isForward = false)
            speak("前のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ it(true) }, 350) }
            return
        }

        val verticalNode = focusNavigator?.findScrollableNode(forward = false)
        if (verticalNode != null && focusNavigator?.performScroll(verticalNode, forward = false) == true) {
            soundHelper?.playScroll(isForward = false)
            onComplete?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ it(true) }, 300) }
            return
        }

        // スクロール可能な要素が存在しない・スクロール下限に達した場合は端音を鳴らして停止
        soundHelper?.playFirstItemEdgeSound()
        onComplete?.let { it(false) }
    }

    private fun performSwipeGesture(swipeUp: Boolean, onComplete: (() -> Unit)? = null) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX1 = width * 0.40f
        val startX2 = width * 0.60f
        val startY = if (swipeUp) height * 0.60f else height * 0.40f
        val endY = if (swipeUp) height * 0.40f else height * 0.60f

        val path1 = android.graphics.Path().apply {
            moveTo(startX1, startY)
            lineTo(startX1, endY)
        }
        val path2 = android.graphics.Path().apply {
            moveTo(startX2, startY)
            lineTo(startX2, endY)
        }
        val stroke1 = android.accessibilityservice.GestureDescription.StrokeDescription(path1, 0, 200)
        val stroke2 = android.accessibilityservice.GestureDescription.StrokeDescription(path2, 0, 200)
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

        val startX = if (swipeLeft) width * 0.88f else width * 0.12f
        val endX = if (swipeLeft) width * 0.12f else width * 0.88f
        val startY = height * 0.45f

        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(endX, startY)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        Log.i(TAG, "performHorizontalSwipeGesture: swipeLeft=$swipeLeft from ($startX, $startY) to ($endX, $startY)")
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "performHorizontalSwipeGesture: gesture completed successfully.")
                soundHelper?.playScroll(isForward = swipeLeft)
                if (onComplete != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(onComplete, 350)
                } else {
                    val pageStr = if (swipeLeft) "次" else "前"
                    speak("${pageStr}のページへ移動しました", TextToSpeech.QUEUE_FLUSH)
                }
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "performHorizontalSwipeGesture: gesture was CANCELLED by system.")
                onComplete?.invoke()
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

    fun getCurrentGranularity(): GranularityMode = currentGranularity

    fun executeActiveCustomAction(): Boolean {
        val node = getAccessibilityFocusedNode() ?: return false
        val isLockscreen = isKeyguardLocked()
        val customActions = getAvailableCustomActions(node)

        // 0 は「デフォルト（通常のアクティベート / クリック）」
        if (selectedCustomActionIndex <= 0) {
            performClickOnFocusedNode()
            return true
        }

        val actionIdx = selectedCustomActionIndex - 1
        if (actionIdx in customActions.indices) {
            val actionItem = customActions[actionIdx]
            if (actionItem.isKeyguardUnlock) {
                return unlockKeyguardOrShowBouncer()
            }
            val success = node.performAction(actionItem.action.id)
            if (success) {
                soundHelper?.playActionDone()
                speak("${actionItem.action.label} を実行しました", TextToSpeech.QUEUE_FLUSH)
                return true
            }
        }
        return false
    }

    data class SerenaCustomAction(val action: AccessibilityNodeInfo.AccessibilityAction, val isKeyguardUnlock: Boolean = false)

    private fun getAvailableCustomActions(node: AccessibilityNodeInfo): List<SerenaCustomAction> {
        val list = mutableListOf<SerenaCustomAction>()
        if (isKeyguardLocked()) {
            val unlockAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                AccessibilityNodeInfo.AccessibilityAction(0x7F0A0001, "画面ロック解除")
            } else null
            if (unlockAction != null) {
                list.add(SerenaCustomAction(unlockAction, isKeyguardUnlock = true))
            }
        }
        val nodeActions = node.actionList.filter { 
            it.id != AccessibilityNodeInfo.ACTION_CLICK && 
            it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
            it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
            !it.label.isNullOrEmpty()
        }
        for (a in nodeActions) {
            list.add(SerenaCustomAction(a, isKeyguardUnlock = false))
        }
        return list
    }
    private var selectedCustomActionIndex = 0
    private var charOffsetInFocusedNode = -1
    private var lastCharNavText = ""

    fun focusNext() {
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

    fun focusPrevious() {
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
            // 0. Serenaメニュー表示中の場合: 現在選択中メニュー項目のテキストを確実に文字送り！
            val menu = activeMenuDialog
            if (menu != null && menu.isShowing) {
                val menuText = menu.getCurrentItemText()
                if (menuText.isNotEmpty()) {
                    if (menuText != lastCharNavText) {
                        lastCharNavText = menuText
                        charOffsetInFocusedNode = -1
                    }
                    charOffsetInFocusedNode++
                    if (charOffsetInFocusedNode < menuText.length) {
                        val targetChar = menuText[charOffsetInFocusedNode]
                        val phonetic = com.shinji.serena.ime.SerenaPhoneticEngine.getPhoneticReading(targetChar)
                        soundHelper?.playFocusMove()
                        speak(phonetic, TextToSpeech.QUEUE_FLUSH)
                        return
                    } else {
                        charOffsetInFocusedNode = -1
                        lastCharNavText = ""
                        menu.navigateMenuNext()
                        return
                    }
                }
            }

            val focused = getAccessibilityFocusedNode() ?: lastHoveredNode
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
                if (text != lastCharNavText) {
                    lastCharNavText = text
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
                    lastCharNavText = ""
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
            // 0. Serenaメニュー表示中の場合: 現在選択中メニュー項目のテキストを確実に文字戻し！
            val menu = activeMenuDialog
            if (menu != null && menu.isShowing) {
                val menuText = menu.getCurrentItemText()
                if (menuText.isNotEmpty()) {
                    if (menuText != lastCharNavText) {
                        lastCharNavText = menuText
                        charOffsetInFocusedNode = menuText.length
                    }
                    charOffsetInFocusedNode--
                    if (charOffsetInFocusedNode >= 0) {
                        val targetChar = menuText[charOffsetInFocusedNode]
                        val phonetic = com.shinji.serena.ime.SerenaPhoneticEngine.getPhoneticReading(targetChar)
                        soundHelper?.playFocusMove()
                        speak(phonetic, TextToSpeech.QUEUE_FLUSH)
                        return
                    } else {
                        charOffsetInFocusedNode = -1
                        lastCharNavText = ""
                        menu.navigateMenuPrev()
                        return
                    }
                }
            }

            val focused = getAccessibilityFocusedNode() ?: lastHoveredNode
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
                if (text != lastCharNavText) {
                    lastCharNavText = text
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
                    lastCharNavText = ""
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

    fun findBestVisibleNodeAfterScroll(nodes: List<AccessibilityNodeInfo>, forward: Boolean, horizontal: Boolean): AccessibilityNodeInfo? {
        if (nodes.isEmpty()) return null
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val visibleNodes = nodes.filter { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val text = getNodeText(node)
            rect.width() > 0 && rect.height() > 0 &&
            rect.left >= 0 && rect.right <= screenW &&
            rect.top >= (screenH * 0.04f).toInt() && rect.bottom <= (screenH * 0.96f).toInt() &&
            !text.contains("最近の項目はありません", ignoreCase = true)
        }

        if (visibleNodes.isEmpty()) return null

        if (horizontal) {
            // ホーム画面/ランチャー等のページめくり時:
            // 画面上部〜中央のメインワークスペース内のアプリアイコン（ドック領域・ステータスバー等を除外）を最優先！
            val workspaceNodes = visibleNodes.filter { node ->
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                r.top >= (screenH * 0.08f).toInt() && r.bottom <= (screenH * 0.82f).toInt() &&
                (node.isClickable || node.isFocusable || !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty())
            }

            val candidates = if (workspaceNodes.isNotEmpty()) workspaceNodes else visibleNodes

            val comparator = Comparator<AccessibilityNodeInfo> { n1, n2 ->
                val r1 = android.graphics.Rect()
                val r2 = android.graphics.Rect()
                n1.getBoundsInScreen(r1)
                n2.getBoundsInScreen(r2)
                if (kotlin.math.abs(r1.top - r2.top) > 60) {
                    r1.top.compareTo(r2.top)
                } else {
                    r1.left.compareTo(r2.left)
                }
            }

            // 左右ページスクロール時: 遷移先ページの先頭項目（一番左上）を常に捕捉してフォーカス＆音声ガイド！
            return candidates.minWithOrNull(comparator)
        } else {
            // 縦スクロール時:
            // forward=true (下へスクロール/次へ進む): 画面上部の最初の項目へ！
            // forward=false (上へスクロール/前へ戻る): 画面下部の最後の項目へ！
            val comparator = Comparator<AccessibilityNodeInfo> { n1, n2 ->
                val r1 = android.graphics.Rect()
                val r2 = android.graphics.Rect()
                n1.getBoundsInScreen(r1)
                n2.getBoundsInScreen(r2)
                if (kotlin.math.abs(r1.top - r2.top) > 30) {
                    r1.top.compareTo(r2.top)
                } else {
                    r1.left.compareTo(r2.left)
                }
            }

            return if (forward) {
                visibleNodes.minWithOrNull(comparator)
            } else {
                visibleNodes.maxWithOrNull(comparator)
            }
        }
    }

    fun scrollHorizontalForward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 300) return true
        lastScrollTime = now

        soundHelper?.playScroll(isForward = true)
        speak("次のページへ移動しました", TextToSpeech.QUEUE_FLUSH)

        val scrollNode = focusNavigator?.findHorizontalScrollableNode(forward = true)
        Log.i(TAG, "scrollHorizontalForward: scrollNode=${scrollNode?.viewIdResourceName} class=${scrollNode?.className}")

        val success = if (scrollNode != null) {
            focusNavigator?.performHorizontalScroll(scrollNode, forward = true) == true
        } else {
            val root = rootInActiveWindow
            root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        }

        if (success) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val newNodes = collectAccessibleNodes()
                val target = findBestVisibleNodeAfterScroll(newNodes, forward = true, horizontal = true)
                if (target != null) {
                    focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                    focusNavigator?.setFocusAndShowOnScreen(target)
                    announceNode(target, TextToSpeech.QUEUE_ADD)
                }
            }, 350)
            return true
        }

        // 物理2本指スワイプフォールバック (TalkBack完全互換: 指が離れた直後にメインスレッドで物理ドラッグ実行)
        performPhysical2FingerScroll(forward = true, horizontal = true)
        return true
    }

    fun scrollHorizontalBackward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 300) return true
        lastScrollTime = now

        soundHelper?.playScroll(isForward = false)
        speak("前のページへ移動しました", TextToSpeech.QUEUE_FLUSH)

        val scrollNode = focusNavigator?.findHorizontalScrollableNode(forward = false)
        Log.i(TAG, "scrollHorizontalBackward: scrollNode=${scrollNode?.viewIdResourceName} class=${scrollNode?.className}")

        val success = if (scrollNode != null) {
            focusNavigator?.performHorizontalScroll(scrollNode, forward = false) == true
        } else {
            val root = rootInActiveWindow
            root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        }

        if (success) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val newNodes = collectAccessibleNodes()
                val target = findBestVisibleNodeAfterScroll(newNodes, forward = false, horizontal = true)
                if (target != null) {
                    focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                    focusNavigator?.setFocusAndShowOnScreen(target)
                    announceNode(target, TextToSpeech.QUEUE_ADD)
                }
            }, 350)
            return true
        }

        // 物理2本指スワイプフォールバック (TalkBack完全互換)
        performPhysical2FingerScroll(forward = false, horizontal = true)
        return true
    }

    fun scrollVerticalForward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 300) return true
        lastScrollTime = now

        soundHelper?.playScroll(isForward = true)

        val activeRoot = rootInActiveWindow
        val pkg = activeRoot?.packageName?.toString()?.lowercase() ?: ""
        val isLauncher = pkg.contains("launcher")

        val scrollNode = focusNavigator?.findScrollableNode(forward = true)
        if (scrollNode != null && focusNavigator?.performScroll(scrollNode, forward = true) == true) {
            speak("下へスクロールしました", TextToSpeech.QUEUE_FLUSH)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val newNodes = collectAccessibleNodes()
                val target = findBestVisibleNodeAfterScroll(newNodes, forward = true, horizontal = false)
                if (target != null) {
                    focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                    focusNavigator?.setFocusAndShowOnScreen(target)
                    announceNode(target, TextToSpeech.QUEUE_ADD)
                }
            }, 300)
            return true
        }

        // ホーム画面で下スクロール（2本指上スワイプ）された場合はアプリ一覧ドロワーを展開
        if (isLauncher && !isAllAppsOpen()) {
            speak("アプリ一覧を開きます", TextToSpeech.QUEUE_FLUSH)
            performOpenAllAppsGesture()
            return true
        }

        speak("下へスクロールしました", TextToSpeech.QUEUE_FLUSH)
        performPhysical2FingerScroll(forward = true, horizontal = false)
        return true
    }

    fun scrollVerticalBackward(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 300) return true
        lastScrollTime = now

        soundHelper?.playScroll(isForward = false)

        val activeRoot = rootInActiveWindow
        val pkg = activeRoot?.packageName?.toString()?.lowercase() ?: ""
        val isLauncher = pkg.contains("launcher")

        val scrollNode = focusNavigator?.findScrollableNode(forward = false)
        if (scrollNode != null && focusNavigator?.performScroll(scrollNode, forward = false) == true) {
            speak("上へスクロールしました", TextToSpeech.QUEUE_FLUSH)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val newNodes = collectAccessibleNodes()
                val target = findBestVisibleNodeAfterScroll(newNodes, forward = false, horizontal = false)
                if (target != null) {
                    focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                    focusNavigator?.setFocusAndShowOnScreen(target)
                    announceNode(target, TextToSpeech.QUEUE_ADD)
                }
            }, 300)
            return true
        }

        // ホーム画面で上スクロール（2本指下スワイプ）された場合は通知シェードを展開
        if (isLauncher && !isAllAppsOpen()) {
            speak("通知を開きます", TextToSpeech.QUEUE_FLUSH)
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            return true
        }

        speak("上へスクロールしました", TextToSpeech.QUEUE_FLUSH)
        performPhysical2FingerScroll(forward = false, horizontal = false)
        return true
    }

    private fun isAllAppsOpen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val allApps = root.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/apps_list_view")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/apps_list_view") }
        return allApps.isNotEmpty()
    }

    private fun performOpenAllAppsGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (performGlobalAction(14 /* AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS */)) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newNodes = collectAccessibleNodes()
                    val target = newNodes.firstOrNull { it.isClickable || !it.text.isNullOrEmpty() }
                    if (target != null) {
                        focusNavigator?.setFocusAndShowOnScreen(target)
                        announceNode(target, TextToSpeech.QUEUE_ADD)
                    }
                }, 350)
                return
            }
        }

        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = android.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.90f)
            lineTo(w * 0.5f, h * 0.20f)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        isInternalGestureDispatching = true
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isInternalGestureDispatching = false
                    val newNodes = collectAccessibleNodes()
                    val target = newNodes.firstOrNull { it.isClickable || !it.text.isNullOrEmpty() }
                    if (target != null) {
                        focusNavigator?.setFocusAndShowOnScreen(target)
                        announceNode(target, TextToSpeech.QUEUE_ADD)
                    }
                }, 350)
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                isInternalGestureDispatching = false
            }
        }, null)
    }

    fun isKeyguardLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km != null) {
            return km.isKeyguardLocked || km.isDeviceLocked
        }

        val currentWindows = try { windows } catch (_: Exception) { null }
        if (!currentWindows.isNullOrEmpty()) {
            val hasKeyguardWindow = currentWindows.any { it.type == 4 /* TYPE_KEYGUARD */ }
            if (hasKeyguardWindow) return true
        }

        val root = rootInActiveWindow
        val rootPkg = root?.packageName?.toString()?.lowercase() ?: ""
        if (rootPkg.contains("keyguard")) {
            return true
        }
        return false
    }

    private var lastPinFocusTimeMs = 0L

    fun autoFocusPinKeypadIfPresent(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastPinFocusTimeMs < 1000L) return false

        val currentFocus = getAccessibilityFocusedNode()
        if (currentFocus != null && !force) {
            return false
        }

        val roots = focusNavigator?.getAllRoots() ?: listOfNotNull(rootInActiveWindow)
        if (roots.isEmpty()) return false

        // Google TalkBack AccessibilityFocusMonitor 準拠: PIN入力欄・PINエリアそのものを最優先捕捉
        var pinInputField: AccessibilityNodeInfo? = null
        var pinMessageArea: AccessibilityNodeInfo? = null

        fun scanKeyguardNodes(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""

            // 1. 最優先: PIN/パスワード入力欄そのもの (PasswordTextView / pinEntry / passwordEntry / lockPassword / element:pin_code_field / EditText / isPassword)
            if (pinInputField == null) {
                val isPinOrPass = node.isPassword ||
                        className.contains("passwordtextview") ||
                        viewId.contains("pinentry") || viewId.contains("passwordentry") ||
                        viewId.contains("lockpassword") || viewId.contains("pin_entry") ||
                        viewId.contains("password_entry") || viewId.contains("pin_field") ||
                        viewId.contains("pin_code") || viewId.contains("pin_view") ||
                        viewId.contains("element:pin") ||
                        (desc.contains("PIN", ignoreCase = true) && (node.isFocusable || node.isClickable || node.isPassword)) ||
                        (className.contains("edittext") && (viewId.contains("pin") || viewId.contains("password") || viewId.contains("keyguard") || node.isPassword)) ||
                        (node.isEditable && (viewId.contains("pin") || viewId.contains("password") || viewId.contains("keyguard")))

                if (isPinOrPass) {
                    pinInputField = node
                    return
                }
            }

            // 2. 次点: PINメッセージエリア (keyguard_message_area / bouncer_message / element:bouncer)
            if (pinMessageArea == null) {
                if (viewId.contains("keyguard_message_area") || viewId.contains("bouncer_message") ||
                    viewId.contains("message_area") || viewId.contains("element:bouncer_message") ||
                    desc.contains("PINを入力") || desc.contains("再起動後") || text.contains("PINを入力") || text.contains("再起動後")
                ) {
                    pinMessageArea = node
                }
            }

            for (i in 0 until node.childCount) {
                scanKeyguardNodes(node.getChild(i))
                if (pinInputField != null) return
            }
        }

        for (r in roots) {
            scanKeyguardNodes(r)
            if (pinInputField != null) break
        }

        val target = pinInputField ?: pinMessageArea
        if (target != null) {
            lastPinFocusTimeMs = now
            val focused = target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            if (!focused) {
                try {
                    target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                } catch (_: Exception) {}
            }
            lastHoveredNode = target
            soundHelper?.playFocusMove()
            announceNode(target)
            Log.i(TAG, "autoFocusPinKeypadIfPresent: successfully focused PIN target ${target.viewIdResourceName}")
            return true
        }

        return false
    }

    fun isKeyboardOrPinKeyNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val pkg = node.packageName?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        // 1. PIN keypad buttons on Lock Screen (Compose element:pin_key_*, standard keyguard digit buttons)
        val isPinDigitId = viewId.contains("digit") || viewId.contains("pin_key") ||
                viewId.endsWith("key1") || viewId.endsWith("key2") || viewId.endsWith("key3") ||
                viewId.endsWith("key4") || viewId.endsWith("key5") || viewId.endsWith("key6") ||
                viewId.endsWith("key7") || viewId.endsWith("key8") || viewId.endsWith("key9") ||
                viewId.endsWith("key0") || viewId.contains("delete") || viewId.contains("cancel") ||
                viewId.contains("enter") || viewId.contains("ok") || viewId.contains("numpad") ||
                viewId.contains("klav")

        val isSingleCharOrDigit = (text.length == 1 && !text.all { it.isWhitespace() }) ||
                (desc.length == 1 && !desc.all { it.isWhitespace() })

        val isKeyguardKey = (isKeyguardLocked() || pkg.contains("systemui") || pkg.contains("keyguard")) &&
                (isPinDigitId || (node.isClickable && (isSingleCharOrDigit || desc.contains("削除") || desc.contains("決定") || desc.contains("確定"))))

        // 2. Soft Keyboard / IME keys (Gboard, Serena Keyboard, etc.)
        val isImeKey = (pkg.contains("inputmethod") || pkg.contains("latin") || pkg.contains("gboard") ||
                pkg.contains("keyboard") || className.contains("Key", ignoreCase = true) || className.contains("Keyboard", ignoreCase = true)) &&
                node.isClickable && (isSingleCharOrDigit || isPinDigitId || viewId.contains("key") ||
                desc.contains("削除") || desc.contains("スペース") || desc.contains("確定") || desc.contains("改行"))

        return isKeyguardKey || isImeKey
    }

    fun unlockKeyguardOrShowBouncer(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUnlockTime < 500L) return false
        lastUnlockTime = now

        soundHelper?.playActionDone()
        speak("ロックを解除しています", TextToSpeech.QUEUE_FLUSH)

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // 1. Google TalkBack 完全準拠: OS標準 Global Action による通知シェード消去
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Global action dismiss notification shade: ${e.message}")
        }

        // 2. ロック画面ルートノードおよびCompose element:lockscreenへの ACTION_DISMISS / ACTION_CLICK 発行
        val roots = focusNavigator?.getAllRoots() ?: listOfNotNull(rootInActiveWindow)
        fun dismissNodeRecursively(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id }) {
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
                }
            }
            if (viewId.contains("lockscreen") || viewId.contains("lock_icon") || viewId.contains("keyguard") || viewId.contains("scene_container") || viewId.contains("scene_window")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            for (i in 0 until node.childCount) {
                dismissNodeRecursively(node.getChild(i))
            }
        }

        for (r in roots) {
            dismissNodeRecursively(r)
        }

        // 3. Google TalkBack 完全準拠の縦スワイプドラッグ（中央 50%, 85% -> 中央 50%, 12%, 240ms）
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val p = android.graphics.Path().apply {
            moveTo(width * 0.50f, height * 0.85f)
            lineTo(width * 0.50f, height * 0.12f)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 240)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        isInternalGestureDispatching = true

        val dispatched = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                isInternalGestureDispatching = false
                autoFocusPinKeypadIfPresent(force = true)
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                isInternalGestureDispatching = false
            }
        }, mainHandler)

        // 4. バウンサー展開後のPIN入力欄へのフォーカス
        mainHandler.postDelayed({ autoFocusPinKeypadIfPresent(force = false) }, 350)
        return dispatched
    }

    private fun performPhysical2FingerScroll(forward: Boolean, horizontal: Boolean) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.postDelayed({
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels.toFloat()
            val height = displayMetrics.heightPixels.toFloat()

            val startX1: Float; val endX1: Float; val startY1: Float; val endY1: Float
            val startX2: Float; val endX2: Float; val startY2: Float; val endY2: Float

            if (horizontal) {
                if (forward) {
                    startX1 = width * 0.80f; endX1 = width * 0.20f; startY1 = height * 0.48f; endY1 = height * 0.48f
                    startX2 = width * 0.80f; endX2 = width * 0.20f; startY2 = height * 0.52f; endY2 = height * 0.52f
                } else {
                    startX1 = width * 0.20f; endX1 = width * 0.80f; startY1 = height * 0.48f; endY1 = height * 0.48f
                    startX2 = width * 0.20f; endX2 = width * 0.80f; startY2 = height * 0.52f; endY2 = height * 0.52f
                }
            } else {
                if (forward) {
                    startX1 = width * 0.45f; endX1 = width * 0.45f; startY1 = height * 0.65f; endY1 = height * 0.30f
                    startX2 = width * 0.55f; endX2 = width * 0.55f; startY2 = height * 0.65f; endY2 = height * 0.30f
                } else {
                    startX1 = width * 0.45f; endX1 = width * 0.45f; startY1 = height * 0.30f; endY1 = height * 0.65f
                    startX2 = width * 0.55f; endX2 = width * 0.55f; startY2 = height * 0.30f; endY2 = height * 0.65f
                }
            }

            val p1 = android.graphics.Path().apply { moveTo(startX1, startY1); lineTo(endX1, endY1) }
            val p2 = android.graphics.Path().apply { moveTo(startX2, startY2); lineTo(endX2, endY2) }
            val s1 = android.accessibilityservice.GestureDescription.StrokeDescription(p1, 0, 200)
            val s2 = android.accessibilityservice.GestureDescription.StrokeDescription(p2, 0, 200)
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(s1)
                .addStroke(s2)
                .build()

            isInternalGestureDispatching = true
            dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    mainHandler.postDelayed({ isInternalGestureDispatching = false }, 350)
                    mainHandler.postDelayed({
                        val newNodes = collectAccessibleNodes()
                        val target = findBestVisibleNodeAfterScroll(newNodes, forward = forward, horizontal = horizontal)
                        if (target != null) {
                            focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                            focusNavigator?.setFocusAndShowOnScreen(target)
                            announceNode(target, TextToSpeech.QUEUE_ADD)
                        }
                    }, 250)
                }
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    mainHandler.postDelayed({ isInternalGestureDispatching = false }, 350)
                    mainHandler.postDelayed({
                        val newNodes = collectAccessibleNodes()
                        val target = findBestVisibleNodeAfterScroll(newNodes, forward = forward, horizontal = horizontal)
                        if (target != null) {
                            focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(newNodes, target) ?: 0
                            focusNavigator?.setFocusAndShowOnScreen(target)
                            announceNode(target, TextToSpeech.QUEUE_ADD)
                        }
                    }, 250)
                }
            }, mainHandler)
        }, 40)
    }

    private fun navigateCustomActions(forward: Boolean) {
        val node = getAccessibilityFocusedNode()
        if (node == null) {
            speak("フォーカスされている項目がありません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        val customActions = getAvailableCustomActions(node)
        val totalCount = 1 + customActions.size
        if (totalCount <= 1) {
            selectedCustomActionIndex = 0
            speak("デフォルト。ダブルタップで有効化します", TextToSpeech.QUEUE_FLUSH)
            return
        }
        selectedCustomActionIndex = if (forward) {
            (selectedCustomActionIndex + 1) % totalCount
        } else {
            if (selectedCustomActionIndex - 1 < 0) totalCount - 1 else selectedCustomActionIndex - 1
        }
        soundHelper?.playFocusMove()
        if (selectedCustomActionIndex == 0) {
            speak("デフォルト。ダブルタップで有効化します", TextToSpeech.QUEUE_FLUSH)
        } else {
            val actionItem = customActions[selectedCustomActionIndex - 1]
            speak("アクション: ${actionItem.action.label}。実行するにはダブルタップします", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun isLinkNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        if (className.contains("Link", ignoreCase = true) || className.contains("URL", ignoreCase = true)) return true
        val text = getNodeText(node)
        return text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")
    }

    fun navigateLinearFocus(forward: Boolean) {
        val menu = activeMenuDialog
        if (menu != null && menu.isShowing) {
            if (forward) {
                menu.navigateMenuNext()
            } else {
                menu.navigateMenuPrev()
            }
            return
        }

        if (focusNavigator != null) {
            focusNavigator?.navigateLinearFocus(forward)
            return
        }

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
                soundHelper?.playScroll(isForward = true)
            } else if (verticalScrollNode != null && focusNavigator?.performScroll(verticalScrollNode, forward = true) == true) {
                didScroll = true
                soundHelper?.playScroll(isForward = true)
            }

            if (didScroll) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newRoot = rootInActiveWindow ?: return@postDelayed
                    val newNodes = collectAccessibleNodes(newRoot)
                    if (newNodes.isNotEmpty()) {
                        val firstNode = newNodes.first()
                        firstNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        announceNode(firstNode)
                    }
                }, 250)
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
                soundHelper?.playScroll(isForward = false)
            } else if (verticalScrollNode != null && focusNavigator?.performScroll(verticalScrollNode, forward = false) == true) {
                didScroll = true
                soundHelper?.playScroll(isForward = false)
            }

            if (didScroll) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val newRoot = rootInActiveWindow ?: return@postDelayed
                    val newNodes = collectAccessibleNodes(newRoot)
                    if (newNodes.isNotEmpty()) {
                        val prevNode = newNodes.last()
                        prevNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        announceNode(prevNode)
                    }
                }, 250)
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
        if (b1 != b2) return false
        val c1 = node1.className?.toString() ?: ""
        val c2 = node2.className?.toString() ?: ""
        val id1 = node1.viewIdResourceName ?: ""
        val id2 = node2.viewIdResourceName ?: ""
        return c1 == c2 && id1 == id2
    }

    private fun collectAccessibleNodes(root: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val navNodes = focusNavigator?.collectAccessibleNodes(root)
        if (!navNodes.isNullOrEmpty()) return navNodes
        val targetRoot = root ?: rootInActiveWindow ?: return emptyList()
        return focusNavigator?.collectAccessibleNodes(targetRoot) ?: emptyList()
    }

    private fun performClickOnFocusedNode() {
        val menu = activeMenuDialog
        if (menu != null && menu.isShowing) {
            if (menu.performCurrentItemClick()) return
        }

        val focusedNode = getAccessibilityFocusedNode()
        if (focusedNode == null) {
            Log.w(TAG, "performClickOnFocusedNode: No focused node found.")
            return
        }

        // 0. カスタムアクションモード時の選択アクション実行（インデックス0はデフォルトクリック）
        if (currentGranularity == GranularityMode.ACTIONS && selectedCustomActionIndex > 0) {
            val customActions = focusedNode.actionList.filter { 
                it.id != AccessibilityNodeInfo.ACTION_CLICK && 
                it.id != AccessibilityNodeInfo.ACTION_FOCUS && 
                it.id != AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS &&
                !it.label.isNullOrEmpty()
            }
            val actionIdx = selectedCustomActionIndex - 1
            if (actionIdx in customActions.indices) {
                val action = customActions[actionIdx]
                if (focusedNode.performAction(action.id)) {
                    soundHelper?.playActionDone()
                    speak("${action.label} を実行しました", TextToSpeech.QUEUE_FLUSH)
                    return
                }
            }
        }

        val isKeyguardElement = focusedNode.packageName?.contains("systemui") == true ||
                focusedNode.viewIdResourceName?.contains("key", ignoreCase = true) == true ||
                focusedNode.viewIdResourceName?.contains("pin", ignoreCase = true) == true ||
                focusedNode.viewIdResourceName?.contains("digit", ignoreCase = true) == true ||
                focusedNode.viewIdResourceName?.contains("delete", ignoreCase = true) == true ||
                focusedNode.viewIdResourceName?.contains("enter", ignoreCase = true) == true

        // 1. 直近ノード（特にPINキーやSystemUI要素）へのダイレクトクリック試行
        if (focusedNode.isClickable || focusedNode.isCheckable || isKeyguardElement) {
            val text = getNodeText(focusedNode)
            val logicalSuccess = focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // PINキーやSystemUI要素、あるいは物理タッチが必要な要素には同時に物理ジェスチャータップを発射
            if (isKeyguardElement) {
                clickNodeByGesture(focusedNode)
                if (text.isNotEmpty()) speak(text, TextToSpeech.QUEUE_FLUSH)
                return
            }

            if (logicalSuccess) {
                if (text.isNotEmpty()) speak("$text を実行", TextToSpeech.QUEUE_FLUSH)
                return
            }
        }

        // 2. 直近ノードでクリック不可の場合のみ、親階層を探索
        var target: AccessibilityNodeInfo? = focusedNode.parent
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

        // 3. ACTION_SELECT の試行
        if (focusedNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)) {
            val text = getNodeText(focusedNode)
            if (text.isNotEmpty()) speak("$text 選択", TextToSpeech.QUEUE_FLUSH)
            return
        }

        // 4. 強力なフォールバック: 要素中央の画面座標へ物理タッチジェスチャーを発行
        clickNodeByGesture(focusedNode)
    }

    fun performActivateCurrentFocus(): Boolean {
        val menu = activeMenuDialog
        if (menu != null && menu.isShowing) {
            if (menu.performCurrentItemClick()) {
                soundHelper?.playClick()
                return true
            }
        }

        val focusNode = getAccessibilityFocusedNode() ?: lastHoveredNode ?: return false
        val rawText = getNodeText(focusNode)
        val cleanText = rawText.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Extended_Pictographic}\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u26FF\u2700-\u27BF]"), "").replace(Regex("\\s+"), " ").trim()
        val announceText = if (cleanText.isNotEmpty()) "$cleanText を実行" else "実行"

        // 1. 直近ノードの ACTION_CLICK
        if (focusNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
            soundHelper?.playClick()
            speak(announceText, TextToSpeech.QUEUE_FLUSH)
            return true
        }

        // 2. 親ノードの ACTION_CLICK
        var parent = focusNode.parent
        while (parent != null) {
            if (parent.isClickable || parent.isCheckable) {
                if (parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    soundHelper?.playClick()
                    speak(announceText, TextToSpeech.QUEUE_FLUSH)
                    return true
                }
            }
            parent = parent.parent
        }

        // 3. 物理座標ジェスチャークリック
        clickNodeByGesture(focusNode)
        soundHelper?.playClick()
        speak(announceText, TextToSpeech.QUEUE_FLUSH)
        return true
    }

    fun clickNodeByGesture(node: AccessibilityNodeInfo) {
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

        isInternalGestureDispatching = true
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isInternalGestureDispatching = false
                }, 150)
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isInternalGestureDispatching = false
                }, 150)
            }
        }, null)
    }

    fun dismissActiveMenu(): Boolean {
        val menu = activeMenuDialog
        if (menu != null && menu.isShowing) {
            activeMenuDialog = null
            try {
                menu.dismiss()
            } catch (_: Exception) {}
            soundHelper?.playActionDone()
            speak("セレナメニューを閉じました", TextToSpeech.QUEUE_FLUSH)
            return true
        }
        return false
    }

    fun triggerSerenaMenu() {
        showNormalSerenaMenu()
    }

    fun showNormalSerenaMenu(node: AccessibilityNodeInfo? = null) {
        if (dismissActiveMenu()) {
            return
        }
        val snapshotRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        val snapshotFocused = node ?: getAccessibilityFocusedNode()
        soundHelper?.playMenuOpen()
        val curtainLabel = if (screenCurtainHelper?.isCurtainEnabled == true) "🌑 スクリーンカーテンを解除" else "🌑 スクリーンカーテン (画面非表示・節電)"
        val isTalkBackMode = prefs.getBoolean(KEY_TALKBACK_MODE, false)
        val modeLabel = if (isTalkBackMode) "🔄 モード切替 (現在: TalkBack互換モード)" else "🔄 モード切替 (現在: serenaオリジナルモード)"
        val filterName = notificationFilterHelper?.detailLevel?.displayName ?: "すべて読み上げ"
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
            serenaMenuItem("📊", "スマホ状態 & 現在地 (バッテリー/電波/Wi-Fi/時刻/現在地)") {
                announceFullStatus()
            },
            serenaMenuItem("📍", "現在地読み上げ精度 (現在: ${statusHelper?.locationHelper?.getPrecisionDisplayName() ?: "市区町村・町名まで"})") {
                val exact = statusHelper?.locationHelper?.togglePrecision() ?: false
                soundHelper?.playActionDone()
                val name = if (exact) "番地まで詳細" else "市区町村・町名まで（プライバシー保護）"
                speak("現在地読み上げ精度を $name に変更しました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("🌍", "国名読み上げ設定 (現在: ${statusHelper?.locationHelper?.getCountrySettingDisplayName() ?: "スマート"})") {
                val always = statusHelper?.locationHelper?.toggleAlwaysIncludeCountry() ?: false
                soundHelper?.playActionDone()
                val name = if (always) "常時国名付き（例: 日本、岐阜県...）" else "スマート（国内は省略、海外は国名付き）"
                speak("国名読み上げ設定を $name に変更しました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("🎛️", "読み上げコントロール (現在: ${currentGranularity.displayName})") {
                cycleGranularity(forward = true)
            },
            serenaMenuItem("📋", "クリップボード履歴 (過去のコピー)") {
                showClipboardHistoryDialog(null)
            },
            serenaMenuItem("💌", "通知・着信読み上げ設定 (現在: ${notificationFilterHelper?.detailLevel?.displayName ?: "すべて読み上げ"})") {
                cycleNotificationFilterMode()
            },
            serenaMenuItem("⌨️", "キー入力方式 (現在: ${if (isKeyboardLiftToType) "指を離して入力" else "ダブルタップ入力"})") {
                cycleKeyboardTypingMode()
            },
            serenaMenuItem("🎙️", "Serena 音声入力 (入力欄へ直接音声入力)") {
                launchVoiceInput()
            },
            serenaMenuItem("🌏", "Serena 音声翻訳 (指定言語へ翻訳して入力/読み上げ)") {
                launchVoiceTranslation()
            },
            serenaMenuItem("🌐", "リアルタイム翻訳のデフォルト言語 (現在: ${instantTranslationHelper?.defaultTargetLanguageCode?.uppercase() ?: "EN"})") {
                cycleTranslationDefaultLanguage()
            },
            serenaMenuItem("🖼️", "画像・写真 AIディープエクスプレイヤー (詳細な情景解説)") {
                explainCurrentImageOrScreen()
            },
            serenaMenuItem("📳", "環境音・危険音・呼びかけ検知 (現在: ${soundRecognitionHelper?.detailLevel?.spokenLabel ?: "オフ"})") {
                val nextLevel = soundRecognitionHelper?.cycleDetailLevel() ?: com.shinji.serena.sound.SoundAlertDetailLevel.DISABLED
                soundHelper?.playActionDone()
                speak("環境音・危険音検知を ${nextLevel.displayName} に変更しました", TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("👀", "Serena Eyes (リアルタイムAI視覚＆実況カメラ)") {
                launchSerenaEyes()
            },
            serenaMenuItem("🧠", "AI画面要約 (クイックブリーフィング)") {
                summarizeCurrentScreen()
            },
            serenaMenuItem("📬", "スマート通知ダイジェスト (未読・重要通知の要約)") {
                announceNotificationDigest()
            },
            serenaMenuItem("📡", "3D空間オーディオ・周辺マップ＆紛失防止レーダー") {
                toggleSurroundingRadar()
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
            serenaMenuItem("🧭", "3D空間オーディオ・コンパス案内 (左右立体音響 & 方角アナウンス)") {
                toggleSpatialCompassAudio()
            },
            serenaMenuItem("🦇", "3D空間オーディオ・障害物＆段差検知ソナー (ステレオ立体音響)") {
                toggleSpatialObstacleSonar()
            },
            serenaMenuItem("🏠", "インドア空間ナビ ＆ 屋内リアルタイム実況 (正面・左右・足元案内)") {
                launchIndoorNavigation()
            },
            serenaMenuItem("🚶‍♂️", "徒歩ナビ・現在地と目的地案内") {
                announceCurrentLocationAndNav()
            },
            serenaMenuItem("🥫", "食品＆賞味期限スキャナー (缶詰・調味料・飲料・日付の自動判別)") {
                launchFoodExpirationScanner()
            },
            serenaMenuItem("🚦", "歩行・信号＆点字ブロックナビ (青信号/赤信号・誘導ブロック案内)") {
                launchWalkTransitNav()
            },
            serenaMenuItem("📄", "バーコード＆書類・レシート読み取り (合計金額・期日・商品コード)") {
                launchBarcodeDocScanner()
            },
            serenaMenuItem("📖", "漢字詳細読み上げ (現在の文字を「信じるの信」等で解説)") {
                explainCurrentFocusedKanji()
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
        val totalCount = items.size
        val firstTitle = items.firstOrNull()?.title?.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Extended_Pictographic}\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u26FF\u2700-\u27BF]"), "")?.replace(Regex("\\s+"), " ")?.trim() ?: ""
        soundHelper?.playMenuOpen()
        
        // メニューオープン音とほぼ同時に超高速レスポンスでナレーション開始！
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            speak("セレナメニュー、全${totalCount}項目。1番目、${firstTitle}", TextToSpeech.QUEUE_FLUSH)
        }, 40)

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val dialog = serenaMenuDialog(this, false, items)
                activeMenuDialog = dialog
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
            activeMenuDialog = dialog
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
        val level = notificationFilterHelper?.cycleDetailLevel()
        soundHelper?.playActionDone()
        speak("通知・着信読み上げ: ${level?.displayName}", TextToSpeech.QUEUE_FLUSH)
    }

    fun announceFullStatus() {
        soundHelper?.playActionDone()
        val helper = statusHelper
        if (helper != null) {
            helper.buildFullStatusAnnouncement { fullText ->
                speak(fullText, TextToSpeech.QUEUE_FLUSH)
            }
        } else {
            speak("ステータス情報を取得できませんでした", TextToSpeech.QUEUE_FLUSH)
        }
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
        unlockKeyguardOrShowBouncer()
    }

    private fun focusPinEntryField(): Boolean {
        return autoFocusPinKeypadIfPresent(force = true)
    }

    private fun findNodesByClass(node: AccessibilityNodeInfo, targetClass: String, outList: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.contains(targetClass, ignoreCase = true) == true) {
            outList.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClass(child, targetClass, outList)
        }
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

    fun summarizeCurrentScreen() {
        soundHelper?.playActionDone()
        val summary = smartScreenSummaryEngine?.generateDetailedSummary() ?: "画面の情報を解析できませんでした。"
        speak(summary, TextToSpeech.QUEUE_FLUSH)
    }

    fun announceNotificationDigest() {
        soundHelper?.playActionDone()
        val digest = notificationFilterHelper?.buildNotificationDigest() ?: "現在、未読の重要通知はありません。"
        speak(digest, TextToSpeech.QUEUE_FLUSH)
    }

    fun launchSerenaEyes(mode: String = "EYES") {
        soundHelper?.playClick()
        speak("Serena Eyes（リアルタイムAI視覚）を起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", mode)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Serena Eyes: ${e.message}")
        }
    }

    var isKeyboardLiftToType: Boolean
        get() = prefs.getBoolean("serena_keyboard_lift_to_type", true)
        set(value) {
            prefs.edit().putBoolean("serena_keyboard_lift_to_type", value).apply()
        }

    fun cycleKeyboardTypingMode() {
        isKeyboardLiftToType = !isKeyboardLiftToType
        soundHelper?.playActionDone()
        val modeName = if (isKeyboardLiftToType) "指を離して入力（Lift to Type）" else "ダブルタップして入力（Double Tap）"
        speak("キー入力方式を $modeName に変更しました", TextToSpeech.QUEUE_FLUSH)
    }

    fun launchVoiceInput() {
        soundHelper?.playActionDone()
        assistantHelper?.startVoiceInput()
    }

    fun launchVoiceTranslation() {
        soundHelper?.playActionDone()
        assistantHelper?.startVoiceTranslation()
    }

    fun cycleTranslationDefaultLanguage() {
        val nextPair = instantTranslationHelper?.cycleDefaultTargetLanguage() ?: ("en" to "英語")
        soundHelper?.playActionDone()
        speak("デフォルト翻訳先言語を ${nextPair.second} に変更しました", TextToSpeech.QUEUE_FLUSH)
    }

    fun explainCurrentImageOrScreen() {
        soundHelper?.playActionDone()
        speak("AIディープエクスプレイヤーで画像を解析中...", TextToSpeech.QUEUE_FLUSH)
        val focused = getAccessibilityFocusedNode()
        val text = focused?.contentDescription?.toString() ?: focused?.text?.toString() ?: ""
        val summary = smartScreenSummaryEngine?.generateDetailedSummary() ?: "画像情報を検出できませんでした。"
        speak("情景解説: $summary", TextToSpeech.QUEUE_FLUSH)
    }

    fun launchAiAssistant() {
        soundHelper?.playActionDone()
        assistantHelper?.startListening()
    }

    fun launchCameraOcr() {
        launchSerenaEyes("OCR")
    }

    fun launchCameraFaceAnalysis() {
        launchSerenaEyes("FACE")
    }

    fun launchCameraObjectAnalysis() {
        launchSerenaEyes("OBJECT")
    }

    fun launchIndoorNavigation() {
        soundHelper?.playClick()
        speak("インドア空間ナビを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "INDOOR")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Indoor Navigation: ${e.message}")
        }
    }

    fun launchFoodExpirationScanner() {
        soundHelper?.playClick()
        speak("食品および賞味期限スキャナーを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "FOOD_EXPIRATION")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Food Expiration: ${e.message}")
        }
    }

    fun launchWalkTransitNav() {
        soundHelper?.playClick()
        speak("歩行・信号および点字ブロックナビを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "WALK_TRANSIT")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Walk Transit: ${e.message}")
        }
    }

    fun launchBarcodeDocScanner() {
        soundHelper?.playClick()
        speak("バーコードおよび書類スキャナーを起動します", TextToSpeech.QUEUE_FLUSH)
        try {
            val intent = Intent(this, LiveVisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MODE", "BARCODE_DOC")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Barcode Doc: ${e.message}")
        }
    }

    fun explainCurrentFocusedKanji() {
        soundHelper?.playClick()
        val text = lastHoveredNode?.text?.toString()
            ?: lastHoveredNode?.contentDescription?.toString()
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.text?.toString()
            ?: ""
        if (text.isNotBlank()) {
            val explanation = com.shinji.serena.ai.KanjiExplainerHelper.explainWord(text)
            speak("漢字詳細読み: $explanation", TextToSpeech.QUEUE_FLUSH)
        } else {
            speak("フォーカス中の文字が見つかりません。", TextToSpeech.QUEUE_FLUSH)
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
            if (nav.activeDestination != null) {
                val text = nav.getNavigationGuidance()
                speak(text, TextToSpeech.QUEUE_FLUSH)
            } else {
                showWalkingNavMenu(nav)
            }
        } else {
            speak("位置情報機能が利用できません", TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun startOsmValhallaNavigation(destName: String) {
        soundHelper?.playMenuOpen()
        osmValhallaNavHelper?.startNavigationToDestination(destName)
    }

    fun stopOsmValhallaNavigation() {
        osmValhallaNavHelper?.stopNavigation()
    }

    fun toggleSurroundingRadar() {
        val radar = spatialSurroundingRadarHelper ?: return
        if (radar.isRadarActive()) {
            radar.stopSurroundingRadar()
        } else {
            radar.startSurroundingRadar()
        }
    }

    private fun showWalkingNavMenu(nav: com.shinji.serena.navigation.SerenaWalkingNavigator) {
        val currentSummary = nav.getCurrentLocationSummary()
        val items = listOf(
            serenaMenuItem("📡", "3D音響・周辺マップレーダー (開始/停止)") {
                toggleSurroundingRadar()
            },
            serenaMenuItem("🚶‍♂️", "目的地へのValhalla徒歩ルート案内 (音声検索)") {
                speak("目的地（施設名や駅名など）を音声で検索します。どうぞ！", TextToSpeech.QUEUE_FLUSH)
                assistantHelper?.startListening()
            },
            serenaMenuItem("📍", "現在地と方角の確認") {
                speak(nav.getCurrentLocationSummary(), TextToSpeech.QUEUE_FLUSH)
            },
            serenaMenuItem("🏪", "周辺のコンビニを探す") {
                searchAndShowPlaces("コンビニ", "🏪")
            },
            serenaMenuItem("🚉", "周辺の駅を探す") {
                searchAndShowPlaces("駅", "🚉")
            },
            serenaMenuItem("☕", "周辺の喫茶店・カフェを探す") {
                searchAndShowPlaces("喫茶店", "☕")
            },
            serenaMenuItem("🍔", "周辺のファストフード店を探す") {
                searchAndShowPlaces("ファストフード", "🍔")
            },
            serenaMenuItem("🍽️", "周辺のレストラン・飲食店を探す") {
                searchAndShowPlaces("レストラン", "🍽️")
            },
            serenaMenuItem("🛍️", "周辺のスーパー・商業施設を探す") {
                searchAndShowPlaces("スーパー", "🛍️")
            },
            serenaMenuItem("🏥", "周辺の病院・薬局を探す") {
                searchAndShowPlaces("病院", "🏥")
            },
            serenaMenuItem("📮", "周辺の郵便局・銀行を探す") {
                searchAndShowPlaces("郵便局", "📮")
            },
            serenaMenuItem("🧭", "3D空間オーディオ・コンパス案内を開始") {
                toggleSpatialCompassAudio()
            },
            serenaMenuItem("❌", "目的地の案内を終了・解除") {
                stopOsmValhallaNavigation()
                nav.clearDestination()
                soundHelper?.playActionDone()
                speak("目的地の案内を終了しました", TextToSpeech.QUEUE_FLUSH)
            }
        )

        speak("${currentSummary}。徒歩ナビ・周辺施設メニューを開きました。全${items.size}項目。1番目、${items[0].title}", TextToSpeech.QUEUE_FLUSH)

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val dialog = serenaMenuDialog(this, false, items)
                activeMenuDialog = dialog
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Walking nav dialog error: ${e.message}")
            }
        }
    }

    fun searchAndShowPlaces(category: String, icon: String) {
        val nav = walkingNavigator ?: run {
            speak("徒歩ナビゲーション機能が利用できません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        soundHelper?.playActionDone()
        speak("現在地周辺の${category}を検索中...", TextToSpeech.QUEUE_FLUSH)
        Thread {
            val places = nav.searchNearbyPlaces(category)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (places.isNotEmpty()) {
                    showNearbyPlacesListDialog(category, icon, places, nav)
                } else {
                    speak("現在地周辺の${category}が見つかりませんでした", TextToSpeech.QUEUE_FLUSH)
                }
            }
        }.start()
    }

    private fun showNearbyPlacesListDialog(
        categoryTitle: String,
        categoryIcon: String,
        places: List<com.shinji.serena.navigation.SerenaWalkingNavigator.NavPlace>,
        nav: com.shinji.serena.navigation.SerenaWalkingNavigator
    ) {
        val items = places.map { place ->
            serenaMenuItem(categoryIcon, "${place.name}（約${place.distanceMeters}m）") {
                showPlaceDetailsDialog(place, nav)
            }
        }

        speak("周辺の${categoryTitle}、${items.size}件見つかりました。1番目、${items[0].title}", TextToSpeech.QUEUE_FLUSH)

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val dialog = serenaMenuDialog(this, false, items)
                activeMenuDialog = dialog
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Places list dialog error: ${e.message}")
            }
        }
    }

    private fun showPlaceDetailsDialog(
        place: com.shinji.serena.navigation.SerenaWalkingNavigator.NavPlace,
        nav: com.shinji.serena.navigation.SerenaWalkingNavigator
    ) {
        val items = mutableListOf<serenaMenuItem>()

        // 1. 目的地に設定して徒歩ナビ開始
        items.add(serenaMenuItem("🎯", "ここへ徒歩ナビを開始（約${place.distanceMeters}m）") {
            nav.setDestinationCoordinates(place.name, place.latitude, place.longitude)
            soundHelper?.playActionDone()
            val guidance = nav.getNavigationGuidance()
            speak("目的地を「${place.name}」に設定しました。$guidance", TextToSpeech.QUEUE_FLUSH)
        })

        // 2. 電話をかける
        items.add(serenaMenuItem("📞", "電話をかける") {
            soundHelper?.playClick()
            if (place.phone.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    speak("電話アプリを起動できませんでした", TextToSpeech.QUEUE_FLUSH)
                }
            } else {
                // 電話番号検索
                speak("「${place.name}」の電話番号検索を開きます", TextToSpeech.QUEUE_FLUSH)
                try {
                    val searchUri = Uri.parse("https://www.google.com/search?q=${java.net.URLEncoder.encode("${place.name} 電話番号", "UTF-8")}")
                    val browserIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (_: Exception) {}
            }
        })

        // 3. ホームページ・詳細情報を開く
        items.add(serenaMenuItem("🌐", "ホームページ・施設情報を開く") {
            soundHelper?.playClick()
            speak("「${place.name}」のWeb詳細情報を開きます", TextToSpeech.QUEUE_FLUSH)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(place.website)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(browserIntent)
            } catch (_: Exception) {
                speak("ブラウザを開けませんでした", TextToSpeech.QUEUE_FLUSH)
            }
        })

        // 4. 詳しい住所の読み上げ
        items.add(serenaMenuItem("📍", "詳しい住所の確認") {
            speak("${place.name}の住所は「${place.address}」です。現在地から約${place.distanceMeters}メートルです。", TextToSpeech.QUEUE_FLUSH)
        })

        speak("「${place.name}」の詳細メニューを開きました。全${items.size}項目。1番目、${items[0].title}", TextToSpeech.QUEUE_FLUSH)

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val dialog = serenaMenuDialog(this, false, items)
                activeMenuDialog = dialog
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Place details dialog error: ${e.message}")
            }
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
            activeMenuDialog = dialog
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

    fun toggleSpatialCompassAudio() {
        soundHelper?.playActionDone()
        val helper = compassHelper ?: run {
            speak("コンパス機能が利用できません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        val isEnabled = helper.toggleSpatialAudio()
        if (isEnabled) {
            val heading = helper.getDirectionAnnouncement()
            speak("${heading}。3D空間オーディオコンパスを開始しました。スマホを真北に向けると両耳の中央で澄んだ音が鳴ります。終了するにはもう一度メニューからタップしてください。", TextToSpeech.QUEUE_FLUSH)
        } else {
            speak("3D空間オーディオコンパスを停止しました。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun toggleSpatialObstacleSonar() {
        soundHelper?.playActionDone()
        val helper = spatialObstacleSonarHelper ?: run {
            speak("障害物ソナー機能が利用できません", TextToSpeech.QUEUE_FLUSH)
            return
        }
        val isRunning = helper.toggleSonar()
        if (isRunning) {
            speak("3D空間障害物ソナーを開始しました。前方の壁や段差に近づくとステレオ立体音響と振動で距離を案内します。終了するにはもう一度メニューからタップするか、音声でソナー停止と言ってください。", TextToSpeech.QUEUE_FLUSH)
        } else {
            speak("3D空間障害物ソナーを停止しました。", TextToSpeech.QUEUE_FLUSH)
        }
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
        callManager?.checkCallState(pkgName, rootInActiveWindow)

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val result = notificationFilterHelper?.analyzeEvent(event)
                if (result != null && result.shouldAnnounce) {
                    if (result.isIncomingCall) {
                        soundHelper?.playFocusMove()
                        speak("【着信】" + result.formattedAnnouncement, TextToSpeech.QUEUE_FLUSH)
                    } else {
                        soundHelper?.playScroll()
                        speak(result.formattedAnnouncement, TextToSpeech.QUEUE_ADD)
                    }

                    // 点字ディスプレイにも通知/着信テキストをリアルタイム出力
                    brailleController?.displayAnnouncement(result.formattedAnnouncement)
                }
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val currentlyLocked = isKeyguardLocked()
                if (wasKeyguardLocked && !currentlyLocked) {
                    wasKeyguardLocked = false
                    soundHelper?.playActionDone()
                    speak("ロックを解除しました", TextToSpeech.QUEUE_FLUSH)
                } else if (currentlyLocked) {
                    wasKeyguardLocked = true
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val currentlyLocked = isKeyguardLocked()
                if (wasKeyguardLocked && !currentlyLocked) {
                    wasKeyguardLocked = false
                    soundHelper?.playActionDone()
                    speak("ロックを解除しました", TextToSpeech.QUEUE_FLUSH)
                } else if (currentlyLocked) {
                    wasKeyguardLocked = true
                }

                // セレナメニューダイアログ表示中の場合、OSがウィンドウ内の全項目テキストを結合して送ってくるため全読みを抑制！
                if (activeMenuDialog != null) {
                    return
                }

                // 朝の初回ロック解除時のモーニングサマリー挨拶
                morningSummaryHelper?.checkAndAnnounceMorningSummary()

                checkCallState(pkgName)

                val windowTitle = event.contentDescription?.toString()
                    ?: event.text.joinToString(" ").trim()
                val className = event.className?.toString() ?: ""

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

                val isDialog = className.contains("Dialog", ignoreCase = true) || className.contains("AlertDialog", ignoreCase = true)
                if (isKeyguardLocked() || pkgName.contains("systemui") || pkgName.contains("keyguard")) {
                    // SystemUI / ロック画面 / Bouncer のウィンドウ検知時は、未フォーカス時のみPIN入力欄を捕捉
                    autoFocusPinKeypadIfPresent(force = false)
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val nodes = collectAccessibleNodes()
                        if (nodes.isNotEmpty()) {
                            val currentFocus = getAccessibilityFocusedNode()
                            if (currentFocus == null || !nodes.any { isSameNode(it, currentFocus) }) {
                                val first = nodes[0]
                                focusNavigator?.setFocusAndShowOnScreen(first)
                                if (isTtsReady) {
                                    if (windowTitle.isNotEmpty() && !windowTitle.contains("画面") && isDialog) {
                                        speak(windowTitle, TextToSpeech.QUEUE_FLUSH)
                                        announceNode(first, TextToSpeech.QUEUE_ADD)
                                    } else {
                                        announceNode(first, TextToSpeech.QUEUE_FLUSH)
                                    }
                                }
                            }
                        }
                    }, 200)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // コンテンツ動的更新時はフォーカス奪取を行わない（誤爆・ループ防止）
            }

            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                val node = event.source ?: return
                val pkg = node.packageName?.toString() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val className = node.className?.toString() ?: ""

                // 画面全体の背景暗転枠（ScrimView等）で子要素を持つコンテナのみスキップ
                if (isKeyguardLocked() || pkg.contains("systemui") || pkg.contains("keyguard")) {
                    if (viewId.contains("scrim") || className.contains("ScrimView", ignoreCase = true) ||
                        className.contains("NotificationPanelView", ignoreCase = true) ||
                        className.contains("NotificationShade", ignoreCase = true) ||
                        className.contains("KeyguardRootView", ignoreCase = true)
                    ) {
                        if (node.childCount > 0) return
                    }
                }

                val now = System.currentTimeMillis()
                lastFocusTimeMs = now
                lastHoveredNode = node
                soundHelper?.playFocusMove()
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                if (isTtsReady) {
                    val queueMode = if (now - lastScrollTime < 900) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
                    announceNode(node, queueMode)
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val node = event.source ?: return
                selectedCustomActionIndex = 0
                val now = System.currentTimeMillis()
                lastFocusTimeMs = now
                lastHoveredNode = node
                soundHelper?.playFocusMove()
                if (isTtsReady) {
                    val queueMode = if (now - lastScrollTime < 900) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
                    announceNode(node, queueMode)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                soundHelper?.playClick()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = System.currentTimeMillis()
                if (now - lastScrollEventTime < 400) return
                lastScrollEventTime = now

                // フォーカス移動直後（800ms以内）のスクロール調整はフォーカスアナウンスを妨げないよう読み上げを抑制
                if (now - lastFocusTimeMs < 800) {
                    return
                }

                soundHelper?.playScroll()

                val itemCount = event.itemCount
                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                val text = event.text.joinToString(" ").trim()
                val contentDesc = event.contentDescription?.toString()?.trim() ?: ""

                if (contentDesc.isNotEmpty() && (contentDesc.contains("ページ") || contentDesc.contains("page") || contentDesc.contains("行") || contentDesc.contains("項目"))) {
                    speak(contentDesc, TextToSpeech.QUEUE_ADD)
                } else if (text.isNotEmpty() && (text.contains("ページ") || text.contains("page") || text.contains("行") || text.contains("項目"))) {
                    speak(text, TextToSpeech.QUEUE_ADD)
                } else if (itemCount > 0 && fromIndex >= 0) {
                    val pageIndex = if (toIndex > fromIndex) "${fromIndex + 1}〜${toIndex + 1} / 全${itemCount}項目" else "${fromIndex + 1} / 全${itemCount}項目"
                    speak(pageIndex, TextToSpeech.QUEUE_ADD)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (event.scrollDeltaY != 0 || event.scrollDeltaX != 0)) {
                    val dx = event.scrollDeltaX
                    val dy = event.scrollDeltaY
                    val dirText = if (Math.abs(dx) >= Math.abs(dy)) {
                        // 横スクロール（ページめくり）
                        if (dx > 0) "次のページへ移動しました" else "前のページへ移動しました"
                    } else {
                        // 縦スクロール
                        if (dy > 0) "下へスクロールしました" else "上へスクロールしました"
                    }
                    speak(dirText, TextToSpeech.QUEUE_ADD)
                }

                // スクロール後に画面内の最初の要素に自動フォーカス＆音声ガイド！
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val nodes = collectAccessibleNodes()
                    val target = findBestVisibleNodeAfterScroll(nodes, forward = true, horizontal = true)
                    if (target != null && target != getAccessibilityFocusedNode()) {
                        focusNavigator?.lastFocusedNodeIndex = focusNavigator?.findCurrentNodeIndex(nodes, target) ?: 0
                        focusNavigator?.setFocusAndShowOnScreen(target)
                        announceNode(target, TextToSpeech.QUEUE_ADD)
                    }
                }, 300)
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
                    if (currentGranularity == GranularityMode.CHARACTERS) {
                        val detail = com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.getKanjiDetail(traversed)
                        if (detail.isNotEmpty() && detail != traversed) {
                            speak("$traversed ($detail)", TextToSpeech.QUEUE_FLUSH)
                        } else {
                            speak(traversed, TextToSpeech.QUEUE_FLUSH)
                        }
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
                        if (addedText.length == 1 && currentGranularity == GranularityMode.CHARACTERS) {
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

    private var lastFocusedWindowId: Int = -1

    fun announceNode(node: AccessibilityNodeInfo, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val announcement = buildNodeAnnouncement(node)
        if (announcement.isBlank()) return

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val displayWidth = resources.displayMetrics.widthPixels
        val normalizedX = if (displayWidth > 0) rect.centerX().toFloat() / displayWidth else 0.5f

        val currentTime = System.currentTimeMillis()
        if (announcement == lastSpokenText && (currentTime - lastSpokenTime) < 200) {
            return
        }

        lastSpokenText = announcement
        lastSpokenTime = currentTime
        soundHelper?.playFocusMovePanned(normalizedX)
        speak(announcement, queueMode)

        // 外国語テキストのリアルタイム日本語翻訳読み上げ（原文の直後にキュー追加）
        try {
            instantTranslationHelper?.processTranslationIfNeeded(announcement) { translatedMsg ->
                speak(translatedMsg, TextToSpeech.QUEUE_ADD)
                brailleController?.displayAnnouncement(translatedMsg)
            }
        } catch (_: Exception) {}

        // 点字ディスプレイ（Braille Display）へリアルタイム出力
        try {
            brailleController?.displayNodeInfo(announcement)
        } catch (_: Exception) {}
    }

    private fun buildNodeAnnouncement(node: AccessibilityNodeInfo): String {
        var text = getNodeText(node)
        val role = getNodeRole(node)
        val state = getNodeState(node)
        val pkg = node.packageName?.toString() ?: ""
        val currentWinId = node.windowId

        val parts = mutableListOf<String>()

        // 0. ウィンドウ遷移検知 (ウィンドウが切り替わった時にウィンドウ名やSystemUIを先頭でアナウンス)
        if (currentWinId != lastFocusedWindowId && currentWinId != -1) {
            lastFocusedWindowId = currentWinId
            val win = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    node.window ?: windows?.find { it.id == currentWinId }
                } else null
            } catch (_: Exception) { null }

            val winTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                win?.title?.toString()?.trim() ?: ""
            } else ""

            val winType = win?.type ?: -1
            val winName = when {
                winTitle.isNotEmpty() -> winTitle
                pkg.contains("systemui", ignoreCase = true) || winType == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM -> "SystemUI"
                winType == 4 /* TYPE_KEYGUARD */ || isKeyguardLocked() -> "画面ロック"
                else -> ""
            }
            if (winName.isNotEmpty()) {
                parts.add("ウィンドウ $winName")
            }
        }

        // 1. テキストが空または画像・アイコンの場合、ビジュアル・オーディオ・ディスクリプション（AI情景解説）を生成
        if (text.isEmpty() || node.className?.toString()?.contains("ImageView", ignoreCase = true) == true || role == "ボタン") {
            val visualDesc = visualAudioDescriptionHelper?.generateDescriptionForNode(node) ?: ""
            if (visualDesc.isNotEmpty()) {
                text = if (text.isEmpty()) visualDesc else "$visualDesc ($text)"
            }
        }

        // 2. あやめキーボード (jp.yama3nomori.ayame) または IME候補バーの漢字詳細読み上げ拡張
        if (pkg.contains("ayame", ignoreCase = true) || pkg.contains("markdownhelperkeyboard", ignoreCase = true)) {
            if (text.isNotEmpty() && text.length in 1..4) {
                val detail = com.shinji.serena.ime.SerenaFullKanjiDetailDictionary.getKanjiDetail(text)
                if (detail.isNotEmpty() && detail != text && !text.contains("(")) {
                    text = "$text ($detail)"
                }
            }
        }

        if (text.isNotEmpty()) parts.add(text)
        if (role.isNotEmpty()) parts.add(role)
        if (state.isNotEmpty()) parts.add(state)

        // コンテナやルートノード（text, role, state がすべて空）の場合、無意味な単体読み上げを抑止しPIN入力欄へ即時誘導！
        if (text.isEmpty() && role.isEmpty() && state.isEmpty()) {
            if (isKeyguardLocked() || pkg.contains("keyguard") || pkg.contains("systemui")) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    autoFocusPinKeypadIfPresent(force = true)
                }
            }
            return ""
        }

        return parts.joinToString("、")
    }

    private fun findCheckableOrSwitchNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isCheckable) return node
        val className = node.className?.toString() ?: ""
        if (className.contains("Switch", ignoreCase = true) ||
            className.contains("CheckBox", ignoreCase = true) ||
            className.contains("RadioButton", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true) ||
            className.contains("CompoundButton", ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findCheckableOrSwitchNode(child)
            if (found != null) return found
        }
        return null
    }

    fun getNodeText(node: AccessibilityNodeInfo): String {
        try {
            // 1. ノード自体の direct text
            var text = node.contentDescription?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                text = node.text?.toString()?.trim()
            }
            if (text.isNullOrEmpty()) {
                text = node.hintText?.toString()?.trim()
            }
            if (text.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                text = node.tooltipText?.toString()?.trim()
            }
            if (text.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                text = node.paneTitle?.toString()?.trim()
            }
            if (!text.isNullOrEmpty()) {
                return text
            }

            // 2. labeledBy があればそれを参照
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val labeledBy = node.labeledBy
                if (labeledBy != null) {
                    val labelText = labeledBy.contentDescription?.toString()?.trim() ?: labeledBy.text?.toString()?.trim() ?: ""
                    if (labelText.isNotEmpty()) return labelText
                }
            }

            // 3. 子要素テキストの安全な収集（直下および1階層下まで、最大4件）
            if (node.childCount > 0) {
                val childTexts = mutableListOf<String>()
                for (i in 0 until node.childCount.coerceAtMost(8)) {
                    val child = node.getChild(i) ?: continue
                    if (!child.isVisibleToUser) continue
                    val ct = child.contentDescription?.toString()?.trim() ?: child.text?.toString()?.trim()
                    if (!ct.isNullOrEmpty() && !childTexts.contains(ct)) {
                        childTexts.add(ct)
                    } else if (child.childCount > 0) {
                        for (j in 0 until child.childCount.coerceAtMost(4)) {
                            val gc = child.getChild(j) ?: continue
                            if (!gc.isVisibleToUser) continue
                            val gct = gc.contentDescription?.toString()?.trim() ?: gc.text?.toString()?.trim()
                            if (!gct.isNullOrEmpty() && !childTexts.contains(gct)) {
                                childTexts.add(gct)
                            }
                        }
                    }
                    if (childTexts.size >= 4) break
                }
                if (childTexts.isNotEmpty()) {
                    return childTexts.joinToString(" ")
                }
            }

            // 4. スイッチやチェックボックス単体でラベルがない場合、同一親行内の兄弟ノードからラベルを探索
            val target = findCheckableOrSwitchNode(node)
            if (target != null || node.isClickable || node.isCheckable) {
                val parent = node.parent
                if (parent != null && parent.childCount in 2..8) {
                    val siblingTexts = mutableListOf<String>()
                    val parentDirectText = parent.contentDescription?.toString()?.trim() ?: parent.text?.toString()?.trim()
                    if (!parentDirectText.isNullOrEmpty()) {
                        siblingTexts.add(parentDirectText)
                    }
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        if (sibling == node || isSameNode(sibling, node)) continue
                        val st = sibling.contentDescription?.toString()?.trim() ?: sibling.text?.toString()?.trim()
                        if (!st.isNullOrEmpty() && !siblingTexts.contains(st)) {
                            siblingTexts.add(st)
                        }
                    }
                    if (siblingTexts.isNotEmpty()) {
                        return siblingTexts.joinToString(" ")
                    }
                }
            }

            // 5. AIスマート自動ラベリング（無名ボタンへのAI推論名付与）
            val aiInferred = aiAutoLabelHelper?.inferLabelForUnlabeledNode(node)
            if (!aiInferred.isNullOrEmpty()) {
                return aiInferred
            }

            // 6. 特定のキー・ボタンIDからの推定
            val inferred = inferLabelFromViewId(node.viewIdResourceName)
            if (inferred.isNotEmpty()) {
                return inferred
            }

            if (node.isClickable || node.isCheckable || target != null) {
                val role = getNodeRole(node)
                return if (role.isNotEmpty()) role else "ボタン"
            }

            return ""
        } catch (_: Exception) {
            return ""
        }
    }

    private fun inferLabelFromViewId(viewId: String?): String {
        if (viewId.isNullOrEmpty()) return ""
        val name = viewId.substringAfterLast(":id/").lowercase()
        return when {
            name.contains("expand_button") || name.contains("chevron") || name == "expand" -> "展開"
            name.contains("collapse_button") || name == "collapse" -> "折りたたみ"
            name.contains("clear_all") || name.contains("btn_clear_all") || name.contains("dismiss_all") -> "すべて消去"
            name.contains("settings_button") || name == "settings_gear" || name == "quick_settings" -> "クイック設定"
            name.contains("power_button") || name == "power" -> "電源メニュー"
            name.contains("edit_button") || name == "btn_edit" -> "タイル編集"
            name.contains("media_play") || name == "action_play" -> "再生"
            name.contains("media_pause") || name == "action_pause" -> "一時停止"
            name.contains("media_prev") || name == "action_prev" -> "前の曲"
            name.contains("media_next") || name == "action_next" -> "次の曲"
            name.contains("pinentry") || name.contains("pin_entry") || name.contains("pin_field") || name.contains("keyguard_pin") || name.contains("pin_view") -> "PIN入力欄"
            name.contains("passwordentry") || name.contains("password_entry") || name.contains("lockpassword") || name.contains("keyguard_password") -> "パスワード入力欄"
            name == "key0" || name == "button0" -> "0"
            name == "key1" || name == "button1" -> "1"
            name == "key2" || name == "button2" -> "2"
            name == "key3" || name == "button3" -> "3"
            name == "key4" || name == "button4" -> "4"
            name == "key5" || name == "button5" -> "5"
            name == "key6" || name == "button6" -> "6"
            name == "key7" || name == "button7" -> "7"
            name == "key8" || name == "button8" -> "8"
            name == "key9" || name == "button9" -> "9"
            name.contains("delete_button") || name.contains("backspace") || name.contains("btn_delete") -> "1文字削除"
            name.contains("emergency_call_button") || name.contains("emergency") -> "緊急通報"
            name.contains("cancel_button") || name.contains("btn_cancel") -> "キャンセル"
            name.contains("enter_button") || name.contains("btn_ok") || name.contains("btn_done") -> "決定"
            name.contains("search_button") || name == "search" -> "検索"
            else -> ""
        }
    }

    private fun getNodeRole(node: AccessibilityNodeInfo): String {
        val target = findCheckableOrSwitchNode(node) ?: node
        val className = target.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val hasExpand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id }
        } else false
        val hasCollapse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id }
        } else false

        val isPin = viewId.contains("pin") || className.contains("pin", ignoreCase = true) ||
                className.contains("PasswordTextView", ignoreCase = true)
        val isPass = isPin || node.isPassword || className.contains("PasswordTextView", ignoreCase = true) ||
                viewId.contains("password") || viewId.contains("lockpassword")

        return when {
            hasExpand || viewId.contains("expand_button") || viewId.contains("chevron") -> "展開ボタン"
            hasCollapse || viewId.contains("collapse_button") -> "折りたたみボタン"
            className.contains("Switch", ignoreCase = true) || className.contains("ToggleButton", ignoreCase = true) -> "スイッチ"
            className.contains("CheckBox", ignoreCase = true) -> "チェックボックス"
            className.contains("RadioButton", ignoreCase = true) -> "ラジオボタン"
            className.contains("Button", ignoreCase = true) -> "ボタン"
            node.isPassword || isPass || className.contains("PasswordTextView", ignoreCase = true) ||
            className.contains("EditText", ignoreCase = true) || node.isEditable -> {
                val inputType = node.inputType
                when {
                    isPin -> "PIN入力欄"
                    isPass -> "パスワード入力欄"
                    (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER -> "数字入力欄"
                    (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_PHONE -> "電話番号入力欄"
                    (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "メールアドレス入力欄"
                    (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_URI -> "URL入力欄"
                    else -> "テキスト入力欄"
                }
            }
            className.contains("ImageView", ignoreCase = true) || className.contains("Image", ignoreCase = true) -> "画像"
            className.contains("SeekBar", ignoreCase = true) -> "スライダー"
            target.isCheckable -> "スイッチ"
            className.contains("TextView", ignoreCase = true) -> ""
            else -> ""
        }
    }

    @Suppress("DEPRECATION")
    private fun getNodeState(node: AccessibilityNodeInfo): String {
        val target = findCheckableOrSwitchNode(node) ?: node
        val states = mutableListOf<String>()
        val className = target.className?.toString() ?: ""
        val isSwitch = className.contains("Switch", ignoreCase = true) || className.contains("ToggleButton", ignoreCase = true)
        val isCheckBox = className.contains("CheckBox", ignoreCase = true)
        val isRadio = className.contains("RadioButton", ignoreCase = true)

        // 1. 開閉・展開状態の判定（通知シェード、クイック設定、アコーディオンなど）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val hasExpandAction = node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id }
            val hasCollapseAction = node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id }
            if (hasExpandAction) {
                states.add("折りたたまれています")
            } else if (hasCollapseAction) {
                states.add("展開されています")
            }
        }

        // 2. チェック・スイッチ状態判定
        if (target.isCheckable || isSwitch || isCheckBox || isRadio) {
            if (isSwitch) {
                states.add(if (target.isChecked) "オン" else "オフ")
            } else if (isCheckBox) {
                states.add(if (target.isChecked) "チェック済み" else "チェックなし")
            } else if (isRadio) {
                states.add(if (target.isChecked) "選択中" else "未選択")
            } else {
                states.add(if (target.isChecked) "オン" else "オフ")
            }
        }
        if (node.isSelected && !states.contains("選択中")) {
            states.add("選択中")
        }

        // 3. 有効/無効の判定（クリック可能・展開可能な要素に対して誤って「無効」と言わないよう防御）
        val isInteractive = node.isClickable || node.isCheckable || target.isClickable || target.isCheckable
        if (!node.isEnabled && !target.isEnabled && !isInteractive) {
            states.add("無効")
        }

        return states.joinToString(" ")
    }

    fun handleMagicTapAction() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRinging = audioManager?.mode == AudioManager.MODE_RINGTONE

        if (isRinging) {
            soundHelper?.playActionDone()
            speak("通話に応答しました", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_HEADSETHOOK)
            return
        }

        if (isCallActive || audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
            soundHelper?.playActionDone()
            speak("通話を終了しました", TextToSpeech.QUEUE_FLUSH)
            endCallDirectly()
            return
        }

        // メディアの再生・一時停止状態を厳密に判定
        val isPlaying = audioManager?.isMusicActive == true
        if (isPlaying) {
            soundHelper?.playActionDone()
            speak("メディアを一時停止しました", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        } else {
            soundHelper?.playActionDone()
            speak("メディアを再生しました", TextToSpeech.QUEUE_FLUSH)
            simulateMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
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

        // 1. 日本語（ひらがな・カタカナ・漢字・全角記号）が1文字でも含まれていれば絶対に日本語！
        if (trimmed.matches(Regex(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF\\u3000-\\u303F].*"))) {
            return Locale.JAPANESE
        }

        // 2. タガログ語判定（単語単位・決まり文句で厳密マッチ）
        val lower = trimmed.lowercase()
        val tagalogDistinctPhrases = listOf(
            "magandang araw", "handa na si serena", "mabuhay", "maraming salamat", "kumusta", "kamusta"
        )
        val tagalogWords = setOf(
            "kamusta", "kumusta", "salamat", "magandang", "mabuhay", "asawa", "handa", "walang", "opo"
        )
        val tokens = lower.split(Regex("[^a-zA-Z]+")).filter { it.isNotEmpty() }
        val isTagalog = tagalogDistinctPhrases.any { lower.contains(it) } || tokens.any { tagalogWords.contains(it) }

        if (isTagalog) {
            val tagalogLocale = Locale.Builder().setLanguage("fil").setRegion("PH").build()
            val availability = tts?.isLanguageAvailable(tagalogLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                return tagalogLocale
            }
            return Locale.JAPANESE
        }

        // 3. 英語判定（日本語を含まず、アルファベットの英単語で構成されている場合）
        if (trimmed.matches(Regex("^[a-zA-Z0-9\\s\\p{Punct}]+$")) && trimmed.any { it.isLetter() }) {
            val englishLocale = Locale.ENGLISH
            val availability = tts?.isLanguageAvailable(englishLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                return englishLocale
            }
        }

        return Locale.JAPANESE
    }

    private var isSpeechPaused = false
    private var currentSpeakingUtterance: String? = null

    private var isMuted = false

    fun toggleSpeechMute() {
        if (!isMuted) {
            // ミュート（消音）へ移行
            soundHelper?.playActionDone()
            speak("音声をミュートしました", TextToSpeech.QUEUE_FLUSH)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isMuted = true
                stopSpeech()
                Toast.makeText(this, "🔇 音声ミュート中（2本指トリプルタップで解除）", Toast.LENGTH_SHORT).show()
            }, 1200)
        } else {
            // ミュート解除へ移行
            isMuted = false
            soundHelper?.playActionDone()
            speak("音声のミュートを解除しました", TextToSpeech.QUEUE_FLUSH)
            Toast.makeText(this, "🔊 音声ミュート解除", Toast.LENGTH_SHORT).show()
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

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (text.isBlank() || isMuted) return
        val effectiveQueueMode = if (isStartupGreetingSpeaking && !text.startsWith("Magandang araw")) {
            TextToSpeech.QUEUE_ADD
        } else {
            queueMode
        }

        if (!isTtsReady || tts == null) {
            synchronized(pendingSpeechQueue) {
                if (effectiveQueueMode == TextToSpeech.QUEUE_FLUSH) {
                    pendingSpeechQueue.clear()
                }
                pendingSpeechQueue.add(Pair(text, effectiveQueueMode))
            }
            return
        }

        val processedText = emojiHelper?.translateEmojiAndKaomoji(text) ?: text
        lastSpokenText = processedText
        currentSpeakingUtterance = processedText
        isSpeechPaused = false

        try {
            val targetLocale = detectLanguage(processedText)
            if (tts?.language?.language != targetLocale.language) {
                tts?.language = targetLocale
            }
            AlphaTelemetryHelper.getInstance(this).incrementTtsCount(targetLocale)
        } catch (_: Exception) {}

        val utteranceId = "serenaUtterance_${System.currentTimeMillis()}"
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = tts?.speak(processedText, effectiveQueueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak returned ERROR for text: $processedText")
            tts?.speak(processedText, effectiveQueueMode, null, utteranceId)
        }
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

    fun dismissKeyguardViaOs() {
        unlockKeyguardOrShowBouncer()
    }

    private fun registerTimeTickReceiver() {
        if (timeTickReceiver != null) return
        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK -> checkHourlyChime()
                    Intent.ACTION_SCREEN_OFF -> {
                        soundHelper?.playClick()
                        speak("画面がロックされました", TextToSpeech.QUEUE_FLUSH)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (isKeyguardLocked()) {
                            soundHelper?.playFocusMove()
                            speak("ロック画面です。2本指で上にスワイプして解除してください。", TextToSpeech.QUEUE_FLUSH)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                autoFocusPinKeypadIfPresent(force = true)
                            }, 250)
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        soundHelper?.playActionDone()
                        speak("ロックを解除しました", TextToSpeech.QUEUE_FLUSH)
                    }
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
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
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
        try {
            val farewell = "Paalam po, Shinji! Maraming salamat at mag-ingat ka palagi!"
            try {
                tts?.language = detectLanguage(farewell)
            } catch (_: Exception) {}
            tts?.speak(farewell, TextToSpeech.QUEUE_FLUSH, null, "serena_shutdown_greeting")
            Thread.sleep(2600)
        } catch (_: Exception) {}
        super.onDestroy()
        callManager?.release()
        callManager = null
        wifiConnectivityHelper?.stopMonitoring()
        wifiConnectivityHelper = null
        batteryHelper?.stop()
        batteryHelper = null
        soundRecognitionHelper?.stop()
        soundRecognitionHelper = null
        instantTranslationHelper?.close()
        instantTranslationHelper = null
        visualAudioDescriptionHelper = null
        brailleController?.disconnect()
        brailleController = null
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


}

private val AccessibilityNodeInfo.safeIsHeading: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isHeading else false



