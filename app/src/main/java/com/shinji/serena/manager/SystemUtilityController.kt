package com.shinji.serena.manager

import com.shinji.serena.ClipboardHistoryHelper
import com.shinji.serena.DeviceSecurityHelper
import com.shinji.serena.ScreenCurtainHelper
import com.shinji.serena.SerenaScreenReaderService
import com.shinji.serena.ShakeDetectorHelper
import com.shinji.serena.SmartNotificationFilterHelper
import com.shinji.serena.StatusAnnouncementHelper
import com.shinji.serena.telephony.SerenaCallManager

/**
 * システム関連ユーティリティ（画面カーテン、シェイク、通知、クリップボード、通話管理等）を一括制御するコントローラー
 */
class SystemUtilityController(
    private val service: SerenaScreenReaderService,
    private val speechEngine: com.shinji.serena.speech.SerenaSpeechEngine,
    private val onShakeDetected: () -> Unit
) {
    val screenCurtainHelper: ScreenCurtainHelper = ScreenCurtainHelper(service)
    val notificationFilterHelper: SmartNotificationFilterHelper = SmartNotificationFilterHelper(service)
    val clipboardHelper: ClipboardHistoryHelper = ClipboardHistoryHelper(service)
    val statusAnnouncementHelper: StatusAnnouncementHelper = StatusAnnouncementHelper(service)
    val securityHelper: DeviceSecurityHelper = DeviceSecurityHelper(service)
    val callManager: SerenaCallManager = SerenaCallManager(service, speechEngine)

    private val shakeDetectorHelper: ShakeDetectorHelper = ShakeDetectorHelper(service) {
        onShakeDetected()
    }

    fun startListening() {
        shakeDetectorHelper.start()
    }

    fun stopListening() {
        shakeDetectorHelper.stop()
    }

    fun toggleScreenCurtain(): Boolean {
        return screenCurtainHelper.toggleCurtain()
    }

    fun isScreenCurtainActive(): Boolean {
        return screenCurtainHelper.isCurtainEnabled
    }
}
