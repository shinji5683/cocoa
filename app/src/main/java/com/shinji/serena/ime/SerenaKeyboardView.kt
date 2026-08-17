package com.shinji.serena.ime

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.shinji.serena.SoundAndHapticHelper

/**
 * Serena IME - アクセシブル・オンスクリーンキーボードビュー
 * タッチ探索 (Hover to Focus, Lift to Type)、上下フリックカーソル移動、
 * マルチリンガルAI予測変換候補表示、音響・振動フィードバックを提供。
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
        fun onFlickUp()
        fun onFlickDown()
    }

    var listener: KeyActionListener? = null
    var soundAndHapticHelper: SoundAndHapticHelper? = null
    var languageEngine: SerenaLanguageEngine? = null

    private var currentFocusedView: View? = null
    private val candidateScrollView: HorizontalScrollView
    private val candidateContainer: LinearLayout
    private val mainKeyContainer: LinearLayout
    private val statusTextView: TextView

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            val threshold = 60f
            val velocityThreshold = 100f

            if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > threshold && Math.abs(velocityY) > velocityThreshold) {
                if (diffY < 0) {
                    // 上フリック: カーソル前移動＆フォネティック読み
                    soundAndHapticHelper?.playFocusMove()
                    listener?.onFlickUp()
                    return true
                } else {
                    // 下フリック: カーソル次移動＆フォネティック読み
                    soundAndHapticHelper?.playFocusMove()
                    listener?.onFlickDown()
                    return true
                }
            }
            return false
        }
    })

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#121212"))
        setPadding(12, 12, 12, 12)

        statusTextView = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
            text = "Serena IME | 日本語 (AI予測＆フォネティックON)"
        }
        addView(statusTextView)

        candidateScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(4, 4, 4, 8)
        }
        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        candidateScrollView.addView(candidateContainer)
        addView(candidateScrollView)

        mainKeyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        addView(mainKeyContainer)

        rebuildLayout()
    }

    fun updateStatusText(modeName: String, isPhonetic: Boolean) {
        statusTextView.text = "Serena IME | $modeName (${if (isPhonetic) "AI予測＆詳細読みON" else "通常読み"})"
    }

    fun displayCandidates(candidates: List<Pair<String, String>>) {
        candidateContainer.removeAllViews()
        if (candidates.isEmpty()) {
            candidateScrollView.visibility = View.GONE
            return
        }

        candidateScrollView.visibility = View.VISIBLE
        candidates.forEachIndexed { index, (word, detail) ->
            val btn = Button(context).apply {
                text = word
                textSize = 17f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2C2C2C"))
                contentDescription = "候補${index + 1}: $word ($detail)"
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(6, 0, 6, 0)
                }
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
        val phoneticBtn = createKeyButton("詳細読切替", "詳細フォネティック読み切り替えボタン") { listener?.onPhoneticTogglePressed() }
        val spaceBtn = createKeyButton("空白", "スペース空白入力") { listener?.onSpacePressed() }
        val delBtn = createKeyButton("削除", "一文字削除") { listener?.onDeletePressed() }
        val enterBtn = createKeyButton("確定", "確定・改行") { listener?.onEnterPressed() }

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
            textSize = 19f
            setTextColor(Color.YELLOW)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            contentDescription = speechReading
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(3, 3, 3, 3)
            }
            setOnClickListener {
                soundAndHapticHelper?.performKeyClickHaptic()
                onClick()
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (gestureDetector.onTouchEvent(ev)) {
                return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)

        if (gestureDetector.onTouchEvent(event)) {
            currentFocusedView = null
            return true
        }

        val x = event.x
        val y = event.y

        when (event.action) {
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
            MotionEvent.ACTION_CANCEL -> {
                currentFocusedView = null
            }
        }
        return true
    }

    private fun findChildViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is android.view.ViewGroup) return if (parent.isClickable) parent else null

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE && x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return if (child is android.view.ViewGroup) {
                    findChildViewAt(child, x - child.left, y - child.top)
                } else {
                    child
                }
            }
        }
        return if (parent.isClickable) parent else null
    }
}
