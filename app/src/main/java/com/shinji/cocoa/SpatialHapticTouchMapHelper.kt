package com.shinji.cocoa

import android.graphics.Rect

class SpatialHapticTouchMapHelper(private val soundAndHapticHelper: SoundAndHapticHelper) {

    private var lastAnnouncedXCategory = -1
    private var lastAnnouncedYCategory = -1

    fun processTouchLocation(x: Float, y: Float, screenWidth: Int, screenHeight: Int, isButton: Boolean = false) {
        if (screenWidth <= 0 || screenHeight <= 0) return

        val normX = (x / screenWidth).coerceIn(0.0f, 1.0f)
        val normY = (y / screenHeight).coerceIn(0.0f, 1.0f)

        val xCategory = (normX * 5).toInt().coerceIn(0, 4)
        val yCategory = (normY * 5).toInt().coerceIn(0, 4)

        if (xCategory != lastAnnouncedXCategory || yCategory != lastAnnouncedYCategory) {
            lastAnnouncedXCategory = xCategory
            lastAnnouncedYCategory = yCategory

            soundAndHapticHelper.playSpatialTouchFeedback(normX, normY, isButton)
        }
    }

    fun processNodeBounds(bounds: Rect, screenWidth: Int, screenHeight: Int, isButton: Boolean) {
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        processTouchLocation(centerX, centerY, screenWidth, screenHeight, isButton)
    }

    fun reset() {
        lastAnnouncedXCategory = -1
        lastAnnouncedYCategory = -1
    }
}
