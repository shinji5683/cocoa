package com.shinji.serena

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager

class ScreenCurtainHelper(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCurtainHelper"
    }

    private var windowManager: WindowManager? = null
    private var curtainView: View? = null
    var isCurtainEnabled: Boolean = false
        private set

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    fun toggleCurtain(): Boolean {
        return if (isCurtainEnabled) {
            disableCurtain()
            false
        } else {
            enableCurtain()
            true
        }
    }

    fun enableCurtain(): Boolean {
        if (isCurtainEnabled) return true
        try {
            val view = View(context).apply {
                setBackgroundColor(Color.BLACK)
            }

            val overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(view, params)
            curtainView = view
            isCurtainEnabled = true
            Log.i(TAG, "Screen curtain enabled successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable screen curtain: ${e.message}")
            isCurtainEnabled = false
            return false
        }
    }

    fun disableCurtain() {
        if (!isCurtainEnabled) return
        try {
            curtainView?.let {
                windowManager?.removeView(it)
            }
            curtainView = null
            isCurtainEnabled = false
            Log.i(TAG, "Screen curtain disabled.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable screen curtain: ${e.message}")
        }
    }
}


