package com.shinji.serena.ime

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.shinji.serena.SoundAndHapticHelper

/**
 * Serena IME - アクセシブル・オンスクリーンキーボードビュー
 * タッチ探索 (Hover to Focus, Lift to Type) と高コントラスト表示、音響・振動フィードバックを提供。
 */
class SerenaKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface KeyActionListener {
        fun onKeyTyped(char: Char)
        fun onDeletePressed()
        fun onSpacePressed()
        fun onEnterPressed()
        fun onLanguageSwitchPressed()
        fun onPhoneticTogglePressed()
        fun onCandidateSelected(candidate: String)
    }

    var listener: KeyActionListener? = null
    var soundAndHapticHelper: SoundAndHapticHelper? = null
    var languageEngine: SerenaLanguageEngine? = null

    private var currentFocusedView: View? = null
    private val candidateContainer: LinearLayout
    private val mainKeyContainer: LinearLayout
    private val statusTextView: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#121212"))
        setPadding(16, 16, 16, 16)

        statusTextView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
            text = "Serena IME | 日本語 (フォネティックON)"
        }
        addView(statusTextView)

        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(8, 8, 8, 16)
        }
        addView(candidateContainer)

        mainKeyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        addView(mainKeyContainer)

        rebuildLayout()
    }

    fun updateStatusText(modeName: String, isPhonetic: Boolean) {
        statusTextView.text = "Serena IME | $modeName (${if (isPhonetic) "フォネティックON" else "通常読み"})"
    }

    fun displayCandidates(candidates: List<Pair<String, String>>) {
        candidateContainer.removeAllViews()
        candidates.forEach { (word, detail) ->
            val btn = Button(context).apply {
                text = word
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2C2C2C"))
                contentDescription = "$word ($detail)"
                setOnClickListener {
                    soundAndHapticHelper?.performKeyClickHaptic()
                    listener?.onCandidateSelected(word)
                }
            }
            candidateContainer.addView(btn)
        }
    }

    fun rebuildLayout() {
        mainKeyContainer.removeAllViews()

        val mode = languageEngine?.currentMode ?: SerenaLanguageEngine.LanguageMode.JAPANESE

        when (mode) {
            SerenaLanguageEngine.LanguageMode.JAPANESE -> buildJapaneseKanaLayout()
            SerenaLanguageEngine.LanguageMode.ENGLISH -> buildQwertyLayout(isTagalog = false)
            SerenaLanguageEngine.LanguageMode.TAGALOG -> buildQwertyLayout(isTagalog = true)
            SerenaLanguageEngine.LanguageMode.GLOBAL -> buildQwertyLayout(isTagalog = false)
        }

        val controlRow = LinearLayout(context).apply { orientation = HORIZONTAL }

        val langBtn = createKeyButton("言語切替", "言語切り替えボタン") { listener?.onLanguageSwitchPressed() }
        val phoneticBtn = createKeyButton("読み切替", "フォネティック読み切り替えボタン") { listener?.onPhoneticTogglePressed() }
        val spaceBtn = createKeyButton("スペース", "空白入力") { listener?.onSpacePressed() }
        val delBtn = createKeyButton("削除", "一文字削除") { listener?.onDeletePressed() }
        val enterBtn = createKeyButton("確定", "改行・確定") { listener?.onEnterPressed() }

        controlRow.addView(langBtn)
        controlRow.addView(phoneticBtn)
        controlRow.addView(spaceBtn)
        controlRow.addView(delBtn)
        controlRow.addView(enterBtn)

        mainKeyContainer.addView(controlRow)
    }

    private fun buildJapaneseKanaLayout() {
        val kanaRows = listOf(
            listOf('あ', 'い', 'う', 'え', 'お'),
            listOf('か', 'き', 'く', 'け', 'こ'),
            listOf('さ', 'し', 'す', 'せ', 'そ'),
            listOf('た', 'ち', 'つ', 'て', 'と'),
            listOf('な', 'に', 'ぬ', 'ね', 'の'),
            listOf('は', 'ひ', 'ふ', 'へ', 'ほ'),
            listOf('ま', 'み', 'む', 'め', 'も'),
            listOf('や', 'ゆ', 'よ', 'ら', 'り'),
            listOf('る', 'れ', 'ろ', 'わ', 'ん')
        )

        for (row in kanaRows) {
            val rowLayout = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (char in row) {
                val speech = languageEngine?.getSpeechForChar(char) ?: char.toString()
                val btn = createKeyButton(char.toString(), speech) {
                    listener?.onKeyTyped(char)
                }
                rowLayout.addView(btn)
            }
            mainKeyContainer.addView(rowLayout)
        }
    }

    private fun buildQwertyLayout(isTagalog: Boolean) {
        val rows = mutableListOf(
            listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
            listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
            listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
        )

        if (isTagalog) {
            val secondRowWithEne = rows[1].toMutableList()
            secondRowWithEne.add('ñ')
            rows[1] = secondRowWithEne
        }

        for (row in rows) {
            val rowLayout = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (char in row) {
                val speech = languageEngine?.getSpeechForChar(char) ?: char.toString()
                val btn = createKeyButton(char.toString(), speech) {
                    listener?.onKeyTyped(char)
                }
                rowLayout.addView(btn)
            }
            mainKeyContainer.addView(rowLayout)
        }
    }

    private fun createKeyButton(label: String, speechReading: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 20f
            setTextColor(Color.YELLOW)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            contentDescription = speechReading
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                soundAndHapticHelper?.performKeyClickHaptic()
                onClick()
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.onInterceptTouchEvent(ev)

        val x = ev.x
        val y = ev.y

        when (ev.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val targetView = findChildViewAt(this, x, y)
                if (targetView != null && targetView != currentFocusedView) {
                    currentFocusedView = targetView
                    soundAndHapticHelper?.performHoverHaptic()
                    val desc = targetView.contentDescription
                    if (!desc.isNullOrEmpty()) {
                        soundAndHapticHelper?.announceTts(desc.toString())
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                currentFocusedView?.performClick()
                currentFocusedView = null
            }
        }
        return false
    }

    private fun findChildViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is LinearLayout) return if (parent.isClickable) parent else null

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return if (child is LinearLayout) {
                    findChildViewAt(child, x - child.left, y - child.top)
                } else {
                    child
                }
            }
        }
        return null
    }
}
