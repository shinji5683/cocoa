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
    context: Context,
    private val isEditTextFocus: Boolean,
    private val items: List<serenaMenuItem>
) : Dialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar) {

    init {
        if (context !is Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
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

        val inflater = LayoutInflater.from(context)
        container.removeAllViews()

        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_serena_menu, container, false)
            val tvIcon = itemView.findViewById<TextView>(R.id.tvItemIcon)
            val tvItemTitle = itemView.findViewById<TextView>(R.id.tvItemTitle)

            tvIcon.text = item.icon
            tvItemTitle.text = item.title
            itemView.contentDescription = item.title

            itemView.setOnClickListener {
                dismiss()
                item.action.invoke()
            }

            container.addView(itemView)
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        tvTitle.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }
}


