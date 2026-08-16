package com.shinji.serena.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.shinji.serena.SoundAndHapticHelper

/**
 * Serena IME - Android InputMethodService
 * Androidシステム標準の入力法サービスとして動作。
 */
class SerenaInputMethodService : InputMethodService(), SerenaKeyboardView.KeyActionListener {

    private lateinit var keyboardView: SerenaKeyboardView
    private lateinit var languageEngine: SerenaLanguageEngine
    private lateinit var soundAndHapticHelper: SoundAndHapticHelper
    private val currentComposing = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        languageEngine = SerenaLanguageEngine(this)
        soundAndHapticHelper = SoundAndHapticHelper(this)
    }

    override fun onCreateInputView(): View {
        keyboardView = SerenaKeyboardView(this).apply {
            this.languageEngine = this@SerenaInputMethodService.languageEngine
            this.soundAndHapticHelper = this@SerenaInputMethodService.soundAndHapticHelper
            this.listener = this@SerenaInputMethodService
        }
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentComposing.clear()
    }

    override fun onKeyTyped(char: Char) {
        currentComposing.append(char)
        val connection = currentInputConnection
        connection?.setComposingText(currentComposing.toString(), 1)

        val candidates = languageEngine.getCandidatesWithDetails(currentComposing.toString())
        keyboardView.displayCandidates(candidates)

        val speech = languageEngine.getSpeechForChar(char)
        soundAndHapticHelper.announceTts(speech)
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
            soundAndHapticHelper.announceTts("削除")
        } else {
            val connection = currentInputConnection
            connection?.deleteSurroundingText(1, 0)
            soundAndHapticHelper.announceTts("一文字削除")
        }

        val candidates = languageEngine.getCandidatesWithDetails(currentComposing.toString())
        keyboardView.displayCandidates(candidates)
    }

    override fun onSpacePressed() {
        commitCurrentComposing(" ")
        soundAndHapticHelper.announceTts("スペース")
    }

    override fun onEnterPressed() {
        if (currentComposing.isNotEmpty()) {
            commitCurrentComposing("")
        } else {
            val connection = currentInputConnection
            connection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
        soundAndHapticHelper.announceTts("確定")
    }

    override fun onLanguageSwitchPressed() {
        val nextMode = languageEngine.switchMode()
        val modeName = when (nextMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE -> "日本語"
            SerenaLanguageEngine.LanguageMode.ENGLISH -> "英語"
            SerenaLanguageEngine.LanguageMode.TAGALOG -> "タガログ語 (フィリピン)"
            SerenaLanguageEngine.LanguageMode.GLOBAL -> "グローバル Unicode"
        }
        keyboardView.updateStatusText(modeName, languageEngine.isPhoneticModeEnabled)
        keyboardView.rebuildLayout()
        soundAndHapticHelper.announceTts("言語切り替え：$modeName")
    }

    override fun onPhoneticTogglePressed() {
        val isEnabled = languageEngine.togglePhoneticMode()
        val modeName = when (languageEngine.currentMode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE -> "日本語"
            SerenaLanguageEngine.LanguageMode.ENGLISH -> "英語"
            SerenaLanguageEngine.LanguageMode.TAGALOG -> "タガログ語"
            SerenaLanguageEngine.LanguageMode.GLOBAL -> "グローバル"
        }
        keyboardView.updateStatusText(modeName, isEnabled)
        keyboardView.rebuildLayout()
        val announcement = if (isEnabled) "フォネティック読み オン" else "フォネティック読み オフ"
        soundAndHapticHelper.announceTts(announcement)
    }

    override fun onCandidateSelected(candidate: String) {
        commitCurrentComposing(candidate)
        val detail = SerenaFullKanjiDetailDictionary.getKanjiDetail(candidate)
        soundAndHapticHelper.announceTts("選択：$candidate（$detail）")
    }

    private fun commitCurrentComposing(text: String) {
        val connection = currentInputConnection
        val commitVal = if (text.isEmpty()) currentComposing.toString() else text
        connection?.commitText(commitVal, 1)
        currentComposing.clear()
        keyboardView.displayCandidates(emptyList())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundAndHapticHelper.isInitialized) {
            soundAndHapticHelper.release()
        }
        currentComposing.clear()
    }
}

