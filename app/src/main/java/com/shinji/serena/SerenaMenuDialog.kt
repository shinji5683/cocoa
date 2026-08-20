package com.shinji.serena

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

data class serenaMenuItem(
    val icon: String,
    val title: String,
    val action: () -> Unit
)

class serenaMenuDialog(
    private val serviceContext: Context,
    private val isEditTextFocus: Boolean,
    private val items: List<serenaMenuItem>
) : Dialog(
    android.view.ContextThemeWrapper(serviceContext, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar),
    android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar
) {

    init {
        SerenaScreenReaderService.instance?.activeMenuDialog = this
        window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                win.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
            win.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
        }
    }

    private val itemViews = mutableListOf<View>()
    private var currentIndex = 0
    private var scrollMenuItems: android.widget.ScrollView? = null
    private var btnClose: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_serena_menu)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = findViewById<TextView>(R.id.tvMenuTitle)
        val container = findViewById<LinearLayout>(R.id.containerMenuItems)
        btnClose = findViewById(R.id.btnClose)
        scrollMenuItems = findViewById(R.id.scrollMenuItems)

        val titleText = if (isEditTextFocus) {
            "✏️ serena 編集アシスト"
        } else {
            "🌸 serena メニュー"
        }
        tvTitle.text = titleText
        tvTitle.contentDescription = titleText

        val inflater = LayoutInflater.from(context)
        container.removeAllViews()
        itemViews.clear()

        val service = SerenaScreenReaderService.instance
        val totalCount = items.size

        items.forEachIndexed { index, item ->
            val itemView = inflater.inflate(R.layout.item_serena_menu, container, false)
            val tvIcon = itemView.findViewById<TextView>(R.id.tvItemIcon)
            val tvItemTitle = itemView.findViewById<TextView>(R.id.tvItemTitle)

            tvIcon.text = item.icon
            tvItemTitle.text = item.title
            val accessibleText = item.title
            itemView.contentDescription = accessibleText
            itemView.isFocusable = true

            itemView.setOnClickListener {
                dismiss()
                item.action.invoke()
            }

            itemViews.add(itemView)
            container.addView(itemView)
        }

        btnClose?.setOnClickListener {
            dismiss()
        }

        if (btnClose != null) {
            itemViews.add(btnClose!!)
        }

        currentIndex = 0
        focusAndAnnounceIndex(0, initial = true)
    }

    override fun onStart() {
        super.onStart()
        SerenaScreenReaderService.instance?.activeMenuDialog = this
    }

    override fun onStop() {
        super.onStop()
        if (SerenaScreenReaderService.instance?.activeMenuDialog == this) {
            SerenaScreenReaderService.instance?.activeMenuDialog = null
        }
    }

    override fun dismiss() {
        if (SerenaScreenReaderService.instance?.activeMenuDialog == this) {
            SerenaScreenReaderService.instance?.activeMenuDialog = null
        }
        super.dismiss()
    }

    fun navigateMenuNext(): Boolean {
        if (itemViews.isEmpty()) return false
        val service = SerenaScreenReaderService.instance
        if (currentIndex >= itemViews.size - 1) {
            service?.soundHelper?.playLastItemEdgeSound()
            return false
        }
        currentIndex++
        focusAndAnnounceIndex(currentIndex)
        return true
    }

    fun navigateMenuPrev(): Boolean {
        if (itemViews.isEmpty()) return false
        val service = SerenaScreenReaderService.instance
        if (currentIndex <= 0) {
            service?.soundHelper?.playFirstItemEdgeSound()
            return false
        }
        currentIndex--
        focusAndAnnounceIndex(currentIndex)
        return true
    }

    fun performCurrentItemClick(): Boolean {
        if (currentIndex in itemViews.indices) {
            itemViews[currentIndex].performClick()
            return true
        }
        return false
    }

    private fun focusAndAnnounceIndex(index: Int, initial: Boolean = false) {
        val targetView = itemViews.getOrNull(index) ?: return
        val service = SerenaScreenReaderService.instance ?: return

        targetView.requestFocus()

        scrollMenuItems?.let { scroll ->
            val targetY = (targetView.top - 100).coerceAtLeast(0)
            scroll.smoothScrollTo(0, targetY)
        }

        if (!initial) {
            if (index == 0) {
                service.soundHelper?.playFirstItemEdgeSound()
            } else if (index == itemViews.size - 1) {
                service.soundHelper?.playLastItemEdgeSound()
            } else {
                service.soundHelper?.playFocusMove()
            }
        }

        val textToSpeak = if (index < items.size) {
            val item = items[index]
            if (initial) {
                val titlePrefix = if (isEditTextFocus) "✏️ serena 編集アシスト" else "🌸 serena メニュー"
                "$titlePrefix、全${items.size}項目、1番目、${item.title}"
            } else {
                val pos = "${index + 1}番目、"
                "${pos}${item.title}"
            }
        } else {
            "閉じる ボタン"
        }

        service.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH)
    }
}


