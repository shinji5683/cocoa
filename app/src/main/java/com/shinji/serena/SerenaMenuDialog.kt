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
        window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                win.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
            win.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_serena_menu)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = findViewById<TextView>(R.id.tvMenuTitle)
        val container = findViewById<LinearLayout>(R.id.containerMenuItems)
        val btnClose = findViewById<Button>(R.id.btnClose)

        val titleText = if (isEditTextFocus) {
            "✏️ serena 編集アシスト"
        } else {
            "🌸 serena メニュー"
        }
        tvTitle.text = titleText
        tvTitle.contentDescription = titleText

        var firstItemView: View? = null
        val inflater = LayoutInflater.from(context)
        container.removeAllViews()

        val scrollMenuItems = findViewById<android.widget.ScrollView>(R.id.scrollMenuItems)
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

            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    if (index == 0) {
                        service?.soundHelper?.playFirstItemEdgeSound()
                    } else if (index == totalCount - 1) {
                        service?.soundHelper?.playLastItemEdgeSound()
                    } else {
                        service?.soundHelper?.playFocusMove()
                    }
                }
            }

            itemView.setOnClickListener {
                dismiss()
                item.action.invoke()
            }

            if (index == 0) {
                firstItemView = itemView
            }

            container.addView(itemView)
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        firstItemView?.post {
            firstItemView?.requestFocus()
            firstItemView?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            val firstTitle = items.firstOrNull()?.title ?: ""
            val fullMsg = if (firstTitle.isNotEmpty()) "$titleText、$firstTitle" else titleText
            service?.speak(fullMsg, android.speech.tts.TextToSpeech.QUEUE_FLUSH)
        }
    }
}


