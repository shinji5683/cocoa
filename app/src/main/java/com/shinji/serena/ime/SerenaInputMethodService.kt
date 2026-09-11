package com.shinji.serena.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.shinji.serena.R
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
            soundAndHapticHelper?.announceTts(getString(R.string.ime_deleted))
        } else {
            val connection = currentInputConnection
            val beforeTwo = connection?.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (beforeTwo.length >= 2 && Character.isSurrogatePair(beforeTwo[0], beforeTwo[1])) {
                connection?.deleteSurroundingText(2, 0)
                soundAndHapticHelper?.announceTts(getString(R.string.ime_deleted_char, beforeTwo))
            } else {
                val before = connection?.getTextBeforeCursor(1, 0)?.toString() ?: ""
                connection?.deleteSurroundingText(1, 0)
                if (before.isNotEmpty()) {
                    val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(before)
                    if (detail.isNotEmpty() && detail != before) {
                        soundAndHapticHelper?.announceTts(getString(R.string.ime_deleted_char_with_detail, before, detail))
                    } else {
                        soundAndHapticHelper?.announceTts(getString(R.string.ime_deleted_char, before))
                    }
                } else {
                    soundAndHapticHelper?.announceTts(getString(R.string.ime_deleted_single_char))
                }
            }
        }

        updateCandidateList()
    }

    override fun onSpacePressed() {
        commitCurrentComposing(" ")
        soundAndHapticHelper?.announceTts(getString(R.string.ime_space))
    }

    override fun onEnterPressed() {
        if (currentComposing.isNotEmpty()) {
            commitCurrentComposing("")
        } else {
            val connection = currentInputConnection
            connection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
        soundAndHapticHelper?.announceTts(getString(R.string.ime_commit))
    }

    override fun onLanguageSwitchPressed() {
        val nextMode = languageEngine?.switchMode() ?: SerenaLanguageEngine.LanguageMode.JAPANESE_KANA
        val modeName = when (nextMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE_KANA -> getString(R.string.ime_mode_kana)
            SerenaLanguageEngine.LanguageMode.JAPANESE_QWERTY -> getString(R.string.ime_mode_qwerty)
            SerenaLanguageEngine.LanguageMode.ENGLISH_US -> getString(R.string.ime_mode_en_us)
            SerenaLanguageEngine.LanguageMode.ENGLISH_UK -> getString(R.string.ime_mode_en_uk)
            SerenaLanguageEngine.LanguageMode.ENGLISH_AU -> getString(R.string.ime_mode_en_au)
            SerenaLanguageEngine.LanguageMode.TAGALOG -> getString(R.string.ime_mode_tagalog)
            SerenaLanguageEngine.LanguageMode.BRAILLE -> getString(R.string.ime_mode_braille)
            SerenaLanguageEngine.LanguageMode.GLOBAL -> getString(R.string.ime_mode_global)
        }
        val isPhonetic = languageEngine?.isPhoneticModeEnabled ?: true
        keyboardView?.updateStatusText(modeName, isPhonetic)
        keyboardView?.rebuildLayout()
        updateCandidateList()
        soundAndHapticHelper?.announceTts(getString(R.string.ime_mode_switched, modeName))
    }

    override fun onPhoneticTogglePressed() {
        val isEnabled = languageEngine?.togglePhoneticMode() ?: true
        val modeName = when (languageEngine?.currentMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE_KANA -> getString(R.string.ime_short_mode_kana)
            SerenaLanguageEngine.LanguageMode.JAPANESE_QWERTY -> getString(R.string.ime_short_mode_qwerty)
            SerenaLanguageEngine.LanguageMode.ENGLISH_US -> getString(R.string.ime_short_mode_en_us)
            SerenaLanguageEngine.LanguageMode.ENGLISH_UK -> getString(R.string.ime_short_mode_en_uk)
            SerenaLanguageEngine.LanguageMode.ENGLISH_AU -> getString(R.string.ime_short_mode_en_au)
            SerenaLanguageEngine.LanguageMode.TAGALOG -> getString(R.string.ime_short_mode_tagalog)
            SerenaLanguageEngine.LanguageMode.BRAILLE -> getString(R.string.ime_short_mode_braille)
            SerenaLanguageEngine.LanguageMode.GLOBAL -> getString(R.string.ime_short_mode_global)
            null -> getString(R.string.ime_short_mode_japanese)
        }
        keyboardView?.updateStatusText(modeName, isEnabled)
        keyboardView?.rebuildLayout()
        val announcement = if (isEnabled) getString(R.string.ime_phonetic_on) else getString(R.string.ime_phonetic_off)
        soundAndHapticHelper?.announceTts(announcement)
    }

    override fun onCandidateSelected(candidate: String) {
        commitCurrentComposing(candidate)
        val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(candidate)
        val speech = if (detail.isNotEmpty() && detail != candidate) {
            getString(R.string.ime_candidate_selected_with_detail, candidate, detail)
        } else {
            getString(R.string.ime_candidate_selected, candidate)
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
            soundAndHapticHelper?.announceTts(getString(R.string.ime_line_start))
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
            soundAndHapticHelper?.announceTts(getString(R.string.ime_line_end))
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
