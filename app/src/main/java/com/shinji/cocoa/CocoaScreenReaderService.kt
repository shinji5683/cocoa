package com.shinji.cocoa

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Locale

class CocoaScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CocoaScreenReader"
        const val PREFS_NAME = "cocoa_prefs"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_SPEECH_PITCH = "speech_pitch"

        var instance: CocoaScreenReaderService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0L
    private lateinit var prefs: SharedPreferences
    private var soundHelper: SoundAndHapticHelper? = null

    // 通話時間計測用
    private var isCallActive = false
    private var activeCallApp: String = ""
    private var callStartTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        soundHelper = SoundAndHapticHelper(this)
        initTts()
        Log.i(TAG, "cocoa ScreenReaderService created.")
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
            // Shinjiさん決定 カフェ風カフェアナウンス！
            speak("ほっと一息、cocoa スクリーンリーダーが起動しました。", TextToSpeech.QUEUE_FLUSH)
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
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        serviceInfo = info
        Log.i(TAG, "cocoa AccessibilityService connected.")
    }

    override fun onGesture(gestureId: Int): Boolean {
        Log.i(TAG, "onGesture detected: $gestureId")

        when (gestureId) {
            // 3本指シングルタップ (API 30 / GESTURE_3_FINGER_SINGLE_TAP = 31)
            31, GESTURE_SWIPE_UP_AND_RIGHT, GESTURE_SWIPE_DOWN_AND_RIGHT -> {
                soundHelper?.playMenuOpen()
                triggerCocoaMenu()
                return true
            }
            // 右スワイプ: 次の項目へ移動 (TalkBack風)
            GESTURE_SWIPE_RIGHT -> {
                soundHelper?.playFocusMove()
                focusNext()
                return true
            }
            // 左スワイプ: 前の項目へ移動 (TalkBack風)
            GESTURE_SWIPE_LEFT -> {
                soundHelper?.playFocusMove()
                focusPrevious()
                return true
            }
            // 2本指シングルタップ / ダブルタップ等: 読み上げ停止・再開
            GESTURE_DOUBLE_TAP, GESTURE_DOUBLE_TAP_AND_HOLD -> {
                soundHelper?.playClick()
                stopSpeech()
                return true
            }
        }
        return super.onGesture(gestureId)
    }

    private fun focusNext() {
        val currentFocus = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (currentFocus != null) {
            currentFocus.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        } else {
            rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
    }

    private fun focusPrevious() {
        val currentFocus = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (currentFocus != null) {
            currentFocus.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
        }
    }

    fun triggerCocoaMenu() {
        val focusNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val isEditable = focusNode != null && (focusNode.isEditable || focusNode.className?.contains("EditText", ignoreCase = true) == true)

        if (isEditable && focusNode != null) {
            showEditTextAssistMenu(focusNode)
        } else {
            showNormalCocoaMenu()
        }
    }

    private fun showNormalCocoaMenu() {
        soundHelper?.playMenuOpen()
        speak("cocoa メニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val items = listOf(
            CocoaMenuItem("📖", "画面の一番上から読む") {
                readFromTop()
            },
            CocoaMenuItem("⚙️", "cocoaの設定") {
                openCocoaSettings()
            },
            CocoaMenuItem("❓", "cocoaのHelp") {
                showHelp()
            }
        )
        try {
            val dialog = CocoaMenuDialog(this, false, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog: ${e.message}")
            Toast.makeText(this, "cocoaメニュー: 画面先頭読む / 設定 / ヘルプ", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEditTextAssistMenu(node: AccessibilityNodeInfo) {
        soundHelper?.playMenuOpen()
        speak("編集アシストメニューを開きました", TextToSpeech.QUEUE_FLUSH)
        val items = listOf(
            CocoaMenuItem("✂️", "全選択してコピー") {
                soundHelper?.playActionDone()
                val currentText = getNodeText(node)
                val args = Bundle()
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText.length)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
                node.performAction(AccessibilityNodeInfo.ACTION_COPY)
                speak("全選択してコピーしました", TextToSpeech.QUEUE_FLUSH)
            },
            CocoaMenuItem("📋", "貼り付け") {
                soundHelper?.playActionDone()
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                speak("貼り付けました", TextToSpeech.QUEUE_FLUSH)
            },
            CocoaMenuItem("🧹", "テキスト全消去") {
                soundHelper?.playActionDone()
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                speak("テキストを全消去しました", TextToSpeech.QUEUE_FLUSH)
            },
            CocoaMenuItem("🔊", "入力中テキストの読み上げ") {
                soundHelper?.playClick()
                val currentText = getNodeText(node)
                if (currentText.isNotEmpty()) {
                    speak("現在の入力内容: $currentText", TextToSpeech.QUEUE_FLUSH)
                } else {
                    speak("入力欄は空です", TextToSpeech.QUEUE_FLUSH)
                }
            },
            CocoaMenuItem("☕", "通常メニューを開く") {
                showNormalCocoaMenu()
            }
        )
        try {
            val dialog = CocoaMenuDialog(this, true, items)
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show edit dialog: ${e.message}")
            Toast.makeText(this, "編集アシスト: コピー / 貼り付け / クリア", Toast.LENGTH_LONG).show()
        }
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

    private fun openCocoaSettings() {
        soundHelper?.playClick()
        speak("cocoa の設定画面を開きます", TextToSpeech.QUEUE_FLUSH)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun showHelp() {
        soundHelper?.playClick()
        val helpMsg = "ほっと一息 cocoa ヘルプです。3本指タップでメニューが開きます。左右スワイプで項目を順番に移動できます。画面ダブルタップで読み上げをストップできます。"
        speak(helpMsg, TextToSpeech.QUEUE_FLUSH)
        Toast.makeText(this, helpMsg, Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isTtsReady) return

        val pkgName = event.packageName?.toString() ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notificationText = event.text.joinToString(" ").trim()
                if (notificationText.isNotEmpty()) {
                    val appName = getMessagingAppName(pkgName)
                    if (isCallRelatedPackage(pkgName) || notificationText.contains("着信") || notificationText.contains("通話")) {
                        speak("${appName}の着信: $notificationText", TextToSpeech.QUEUE_FLUSH)
                    } else {
                        speak("通知: $notificationText", TextToSpeech.QUEUE_ADD)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkCallState(pkgName)

                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val windowTitle = event.contentDescription?.toString()
                        ?: event.text.joinToString(" ").trim()

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
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                soundHelper?.playFocusMove()
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
            speak("${appLabel}の通話が終了しました。通話時間: $durationText", TextToSpeech.QUEUE_FLUSH)
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

        val currentTime = System.currentTimeMillis()
        if (announcement == lastSpokenText && (currentTime - lastSpokenTime) < 500) {
            return
        }

        lastSpokenText = announcement
        lastSpokenTime = currentTime
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
        var text = node.contentDescription?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            text = node.text?.toString()?.trim()
        }
        if (text.isNullOrEmpty() && node.childCount > 0) {
            val childTexts = mutableListOf<String>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val t = getNodeText(child)
                if (t.isNotEmpty()) childTexts.add(t)
            }
            text = childTexts.joinToString(" ")
        }
        return text ?: ""
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

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!isTtsReady || text.isBlank()) return
        tts?.speak(text, queueMode, null, "cocoaUtterance_${System.currentTimeMillis()}")
    }

    fun stopSpeech() {
        tts?.stop()
    }

    override fun onInterrupt() {
        stopSpeech()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeech()
        soundHelper?.release()
        soundHelper = null
        tts?.shutdown()
        tts = null
        isTtsReady = false
        instance = null
        Log.i(TAG, "cocoa ScreenReaderService destroyed.")
    }
}
