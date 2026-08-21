package com.shinji.serena.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.shinji.serena.SoundAndHapticHelper

/**
 * Serena IME - Android InputMethodService
 * 日本語・英語・タガログ語のマルチリンガルAI予測変換、
 * 上下フリックによる直感的カーソル移動とフォネティック詳細読み上げを提供。
 * 音量キーはOS標準のボリューム操作を維持。
 */
class SerenaInputMethodService : InputMethodService(), SerenaKeyboardView.KeyActionListener {

    private var keyboardView: SerenaKeyboardView? = null
    private var languageEngine: SerenaLanguageEngine? = null
    private var soundAndHapticHelper: SoundAndHapticHelper? = null
    private val currentComposing = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        try {
            val safeCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && !isDeviceProtectedStorage) {
                createDeviceProtectedStorageContext()
            } else {
                this
            }
            languageEngine = SerenaLanguageEngine(safeCtx)
            soundAndHapticHelper = SoundAndHapticHelper(safeCtx)
        } catch (e: Exception) {
            android.util.Log.e("SerenaIME", "Error in onCreate: ${e.message}")
        }
    }

    override fun onCreateInputView(): View {
        val kv = SerenaKeyboardView(this).apply {
            this.languageEngine = this@SerenaInputMethodService.languageEngine
            this.soundAndHapticHelper = this@SerenaInputMethodService.soundAndHapticHelper
            this.listener = this@SerenaInputMethodService
        }
        keyboardView = kv
        kv.rebuildLayout()
        updateCandidateList()
        return kv
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentComposing.clear()
        updateCandidateList()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateCandidateList()
    }

    override fun onKeyTyped(char: Char) {
        currentComposing.append(char)
        val connection = currentInputConnection
        connection?.setComposingText(currentComposing.toString(), 1)

        updateCandidateList()

        val speech = languageEngine?.getSpeechForChar(char) ?: char.toString()
        soundAndHapticHelper?.announceTts(speech)
    }

    override fun onDeletePressed() {
        if (currentComposing.isNotEmpty()) {
            currentComposing.deleteCharAt(currentComposing.length - 1)
            val connection = currentInputConnection
            if (currentComposing.isNotEmpty()) {
                connection?.setComposingText(currentComposing.toString(), 1)
            } else {
                connection?.commitText("", 1)
            }
            soundAndHapticHelper?.announceTts("削除")
        } else {
            val connection = currentInputConnection
            val beforeTwo = connection?.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (beforeTwo.length >= 2 && Character.isSurrogatePair(beforeTwo[0], beforeTwo[1])) {
                connection?.deleteSurroundingText(2, 0)
                soundAndHapticHelper?.announceTts("$beforeTwo を削除")
            } else {
                val before = connection?.getTextBeforeCursor(1, 0)?.toString() ?: ""
                connection?.deleteSurroundingText(1, 0)
                if (before.isNotEmpty()) {
                    val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(before)
                    if (detail.isNotEmpty() && detail != before) {
                        soundAndHapticHelper?.announceTts("$before ($detail) を削除")
                    } else {
                        soundAndHapticHelper?.announceTts("$before を削除")
                    }
                } else {
                    soundAndHapticHelper?.announceTts("一文字削除")
                }
            }
        }

        updateCandidateList()
    }

    override fun onSpacePressed() {
        commitCurrentComposing(" ")
        soundAndHapticHelper?.announceTts("スペース")
    }

    override fun onEnterPressed() {
        if (currentComposing.isNotEmpty()) {
            commitCurrentComposing("")
        } else {
            val connection = currentInputConnection
            connection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
        soundAndHapticHelper?.announceTts("確定")
    }

    override fun onLanguageSwitchPressed() {
        val nextMode = languageEngine?.switchMode() ?: SerenaLanguageEngine.LanguageMode.JAPANESE_KANA
        val modeName = when (nextMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE_KANA -> "日本語かな (50音)"
            SerenaLanguageEngine.LanguageMode.JAPANESE_QWERTY -> "日本語ローマ字 (QWERTY)"
            SerenaLanguageEngine.LanguageMode.ENGLISH_US -> "英語 US (アメリカ)"
            SerenaLanguageEngine.LanguageMode.ENGLISH_UK -> "英語 UK (イギリス)"
            SerenaLanguageEngine.LanguageMode.ENGLISH_AU -> "英語 AU (オーストラリア)"
            SerenaLanguageEngine.LanguageMode.TAGALOG -> "タガログ語 (フィリピン)"
            SerenaLanguageEngine.LanguageMode.BRAILLE -> "6点点字入力"
            SerenaLanguageEngine.LanguageMode.GLOBAL -> "グローバル 絵文字・記号"
        }
        val isPhonetic = languageEngine?.isPhoneticModeEnabled ?: true
        keyboardView?.updateStatusText(modeName, isPhonetic)
        keyboardView?.rebuildLayout()
        updateCandidateList()
        soundAndHapticHelper?.announceTts("入力モード切替：$modeName")
    }

    override fun onPhoneticTogglePressed() {
        val isEnabled = languageEngine?.togglePhoneticMode() ?: true
        val modeName = when (languageEngine?.currentMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE_KANA -> "日本語かな"
            SerenaLanguageEngine.LanguageMode.JAPANESE_QWERTY -> "日本語ローマ字"
            SerenaLanguageEngine.LanguageMode.ENGLISH_US -> "英語 US"
            SerenaLanguageEngine.LanguageMode.ENGLISH_UK -> "英語 UK"
            SerenaLanguageEngine.LanguageMode.ENGLISH_AU -> "英語 AU"
            SerenaLanguageEngine.LanguageMode.TAGALOG -> "タガログ語"
            SerenaLanguageEngine.LanguageMode.BRAILLE -> "6点点字"
            SerenaLanguageEngine.LanguageMode.GLOBAL -> "グローバル"
            null -> "日本語"
        }
        keyboardView?.updateStatusText(modeName, isEnabled)
        keyboardView?.rebuildLayout()
        val announcement = if (isEnabled) "AI予測＆詳細読み オン" else "通常読み"
        soundAndHapticHelper?.announceTts(announcement)
    }

    override fun onCandidateSelected(candidate: String) {
        commitCurrentComposing(candidate)
        val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(candidate)
        val speech = if (detail.isNotEmpty() && detail != candidate) {
            "選択：$candidate（$detail）"
        } else {
            "選択：$candidate"
        }
        soundAndHapticHelper?.announceTts(speech)
    }

    /**
     * 上フリック: カーソルを1文字前へ移動 ＆ 詳細フォネティック読み上げ
     */
    override fun onFlickUp() {
        val connection = currentInputConnection ?: return
        if (currentComposing.isNotEmpty()) {
            commitCurrentComposing("")
        }

        // 左矢印キーイベントを発行してカーソルを左へ移動
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))

        // カーソル直後の文字（現在位置の文字）を取得して読み上げ
        val charAfter = connection.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (charAfter.isNotEmpty()) {
            val char = charAfter[0]
            val phonetic = SerenaPhoneticEngine.getPhoneticReading(char)
            soundAndHapticHelper?.announceTts(phonetic)
        } else {
            soundAndHapticHelper?.announceTts("行頭")
        }
        updateCandidateList()
    }

    /**
     * 下フリック: カーソルを1文字次へ移動 ＆ 詳細フォネティック読み上げ
     */
    override fun onFlickDown() {
        val connection = currentInputConnection ?: return
        if (currentComposing.isNotEmpty()) {
            commitCurrentComposing("")
        }

        // 右矢印キーイベントを発行してカーソルを右へ移動
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))

        // カーソル直前の文字（通過した文字）を取得して読み上げ
        val charBefore = connection.getTextBeforeCursor(1, 0)?.toString() ?: ""
        if (charBefore.isNotEmpty()) {
            val char = charBefore.last()
            val phonetic = SerenaPhoneticEngine.getPhoneticReading(char)
            soundAndHapticHelper?.announceTts(phonetic)
        } else {
            soundAndHapticHelper?.announceTts("行末")
        }
        updateCandidateList()
    }

    private fun updateCandidateList() {
        val connection = currentInputConnection
        val contextBefore = connection?.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val candidates = languageEngine?.getCandidatesWithDetails(currentComposing.toString(), contextBefore) ?: emptyList()
        keyboardView?.displayCandidates(candidates)
    }

    private fun commitCurrentComposing(text: String) {
        val connection = currentInputConnection
        val commitVal = if (text.isEmpty()) currentComposing.toString() else text
        connection?.commitText(commitVal, 1)
        currentComposing.clear()
        updateCandidateList()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundAndHapticHelper?.release()
        currentComposing.clear()
    }
}
