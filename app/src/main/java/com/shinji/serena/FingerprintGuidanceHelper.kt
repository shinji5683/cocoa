package com.shinji.serena

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * FingerprintGuidanceHelper (画面内指紋センサー位置案内 ＆ リアルタイム指ナビゲーション)
 *
 * ロック画面や生体認証ダイアログ（BiometricPrompt / Bouncer）において、
 * 画面内指紋センサー（UDFPS: Under-Display Fingerprint Sensor）の位置を検出し、
 * ユーザーの指の位置から「上」「下」「左」「右」「右斜め上」「ピッタリ！」などの
 * 直感的な相対方向案内と、距離に応じたガイガーカウンター式ハプティクス（接近するほど振動間隔が狭まる）
 * でセンサー中央へ完全誘導する支援エンジン。
 *
 * 【設計方針 & 鉄則遵守】
 * 1. 方向表現の鉄則: 時計盤表現（"12時の方向"等）は一切使わず、直感的な相対方向（上・下・左・右・斜め）を徹底。
 * 2. センサー位置特定: UDFPSノード（lock_icon, udfps_icon, BiometricPrompt 等）の検出 + Pixel/Galaxy/一般端末のヒューリスティック(72%位置)。
 * 3. ガイガーカウンター式ハプティクス: 遠いとゆったり(750ms)、近づくにつれて(500ms -> 350ms -> 220ms -> 130ms -> 90ms)、ピッタリで確実な決定振動(Snap)。
 * 4. 多言語対応 & ゼロハードコーディング: strings.xml (6言語) 経由で取得。
 */
class FingerprintGuidanceHelper(
    private val service: SerenaScreenReaderService,
    private val soundHelper: SoundAndHapticHelper?
) {

    companion object {
        private const val TAG = "FingerprintGuidance"
        const val PREFS_KEY_ENABLED = "fingerprint_guidance_enabled"

        /**
         * 純粋計算ロジック（単体テスト可能）
         */
        fun calculateGuidance(
            touchX: Float,
            touchY: Float,
            targetX: Float,
            targetY: Float,
            tolerancePx: Float,
            density: Float
        ): GuidanceResult {
            val dx = targetX - touchX
            val dy = targetY - touchY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance <= tolerancePx) {
                return GuidanceResult(GuidanceDirection.ALIGNED, distance, isAligned = true)
            }

            val closeThresholdPx = tolerancePx * 2.2f
            val isClose = distance <= closeThresholdPx
            val absX = abs(dx)
            val absY = abs(dy)

            val direction: GuidanceDirection = when {
                // 水平方向が圧倒的 (absX > 2.0 * absY)
                absX > 2.0f * absY -> {
                    if (dx > 0) {
                        if (isClose) GuidanceDirection.SLIGHTLY_RIGHT else GuidanceDirection.RIGHT
                    } else {
                        if (isClose) GuidanceDirection.SLIGHTLY_LEFT else GuidanceDirection.LEFT
                    }
                }
                // 垂直方向が圧倒的 (absY > 2.0 * absX)
                absY > 2.0f * absX -> {
                    if (dy > 0) {
                        if (isClose) GuidanceDirection.SLIGHTLY_DOWN else GuidanceDirection.DOWN
                    } else {
                        if (isClose) GuidanceDirection.SLIGHTLY_UP else GuidanceDirection.UP
                    }
                }
                // 斜め方向
                else -> {
                    if (dx > 0 && dy < 0) {
                        if (isClose) GuidanceDirection.SLIGHTLY_UP_RIGHT else GuidanceDirection.UP_RIGHT
                    } else if (dx > 0 && dy > 0) {
                        if (isClose) GuidanceDirection.SLIGHTLY_DOWN_RIGHT else GuidanceDirection.DOWN_RIGHT
                    } else if (dx < 0 && dy < 0) {
                        if (isClose) GuidanceDirection.SLIGHTLY_UP_LEFT else GuidanceDirection.UP_LEFT
                    } else {
                        if (isClose) GuidanceDirection.SLIGHTLY_DOWN_LEFT else GuidanceDirection.DOWN_LEFT
                    }
                }
            }

            return GuidanceResult(direction, distance, isAligned = false)
        }

        /**
         * ガイガーカウンター式振動間隔の計算 (ミリ秒)
         */
        fun calculateGeigerInterval(distance: Float, tolerancePx: Float, density: Float): Long {
            return when {
                distance <= tolerancePx -> 80L
                distance < 60f * density -> 130L
                distance < 120f * density -> 220L
                distance < 200f * density -> 350L
                distance < 300f * density -> 500L
                else -> 750L
            }
        }
    }

    enum class GuidanceDirection(val stringResId: Int) {
        ALIGNED(R.string.fingerprint_guidance_aligned),
        UP(R.string.fingerprint_dir_up),
        SLIGHTLY_UP(R.string.fingerprint_dir_slightly_up),
        DOWN(R.string.fingerprint_dir_down),
        SLIGHTLY_DOWN(R.string.fingerprint_dir_slightly_down),
        LEFT(R.string.fingerprint_dir_left),
        SLIGHTLY_LEFT(R.string.fingerprint_dir_slightly_left),
        RIGHT(R.string.fingerprint_dir_right),
        SLIGHTLY_RIGHT(R.string.fingerprint_dir_slightly_right),
        UP_RIGHT(R.string.fingerprint_dir_up_right),
        SLIGHTLY_UP_RIGHT(R.string.fingerprint_dir_slightly_up_right),
        DOWN_RIGHT(R.string.fingerprint_dir_down_right),
        SLIGHTLY_DOWN_RIGHT(R.string.fingerprint_dir_slightly_down_right),
        UP_LEFT(R.string.fingerprint_dir_up_left),
        SLIGHTLY_UP_LEFT(R.string.fingerprint_dir_slightly_up_left),
        DOWN_LEFT(R.string.fingerprint_dir_down_left),
        SLIGHTLY_DOWN_LEFT(R.string.fingerprint_dir_slightly_down_left);
    }

    data class GuidanceResult(
        val direction: GuidanceDirection,
        val distance: Float,
        val isAligned: Boolean
    )

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                service.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator access failed: ${e.message}")
            null
        }
    }

    var sensorX: Float = 0f
        private set
    var sensorY: Float = 0f
        private set
    var sensorRadiusPx: Float = 0f
        private set
    var isSensorLocated: Boolean = false
        private set
    var isExactNodeLocated: Boolean = false
        private set

    val isEnabled: Boolean
        get() {
            val prefs = service.getSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREFS_KEY_ENABLED, true)
        }

    fun setEnabled(enabled: Boolean) {
        val prefs = service.getSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_KEY_ENABLED, enabled).apply()
        if (!enabled) {
            onTouchEnd()
        }
    }

    @Volatile
    private var isTouching = false
    @Volatile
    private var currentDistance = Float.MAX_VALUE
    private var isCurrentlyAligned = false
    private var lastAnnouncedDirection: GuidanceDirection? = null
    private var lastAnnounceTimeMs = 0L
    private var isPulseRunning = false
    private var touchControllerCallback: Any? = null

    init {
        initTouchInteractionController()
    }

    /**
     * Android 13+ (API 33+) TouchInteractionController の安全なリスナー登録
     */
    private fun initTouchInteractionController() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val controller = service.getTouchInteractionController(android.view.Display.DEFAULT_DISPLAY)
                val callback = object : android.accessibilityservice.TouchInteractionController.Callback {
                    override fun onMotionEvent(event: android.view.MotionEvent) {
                        if (isGuidanceActive()) {
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN,
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    onTouchCoordinates(event.x, event.y)
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    onTouchEnd()
                                }
                            }
                        }
                    }

                    override fun onStateChanged(state: Int) {}
                }
                controller.registerCallback(
                    service.mainExecutor,
                    callback
                )
                touchControllerCallback = callback
            } catch (t: Throwable) {
                Log.d(TAG, "TouchInteractionController registration optional: ${t.message}")
            }
        }
    }

    /**
     * ガイダンスが現在有効かつ発動対象画面（ロック画面／生体認証プロンプト）か
     */
    fun isGuidanceActive(): Boolean {
        if (!isEnabled) return false

        if (service.isKeyguardLocked()) return true
        if (isBiometricPromptShowing()) return true

        // フォールバック: アクティブウィンドウまたはノード構造に lockscreen / bouncer / scene_container が存在するか
        try {
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                val viewId = root.viewIdResourceName?.lowercase() ?: ""
                if (pkg.contains("keyguard") || viewId.contains("lockscreen") || viewId.contains("scene_container")) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun isBiometricPromptShowing(): Boolean {
        try {
            val root = service.rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString()?.lowercase() ?: ""
            if (pkg.contains("biometric") || pkg.contains("systemui.biometrics")) return true

            val roots = service.focusNavigator?.getAllRoots() ?: listOf(root)
            for (r in roots) {
                if (findBiometricNodeRecursively(r)) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun findBiometricNodeRecursively(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (viewId.contains("biometric") || viewId.contains("fingerprint") || viewId.contains("udfps") ||
            desc.contains("fingerprint") || desc.contains("指紋") || text.contains("fingerprint") || text.contains("指紋")) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (findBiometricNodeRecursively(node.getChild(i))) return true
        }
        return false
    }

    /**
     * 画面内指紋センサー位置の検出＆キャリブレーション
     */
    fun updateSensorLocation(): Boolean {
        try {
            val dm = service.resources.displayMetrics
            val screenWidth = dm.widthPixels.toFloat()
            val screenHeight = dm.heightPixels.toFloat()
            val density = dm.density

            // デフォルト・ヒューリスティック設定（Pixel 6〜9, Galaxy S21〜S24, 一般Android端末: 下部72%位置・中央）
            var locX = screenWidth * 0.5f
            var locY = screenHeight * 0.72f
            var radius = 36f * density
            var foundNode = false

            val roots = service.focusNavigator?.getAllRoots() ?: listOfNotNull(service.rootInActiveWindow)
            for (root in roots) {
                val node = findUdfpsNodeRecursively(root, screenWidth, screenHeight)
                if (node != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        locX = rect.centerX().toFloat()
                        locY = rect.centerY().toFloat()
                        radius = (max(rect.width(), rect.height()) / 2f).coerceIn(28f * density, 60f * density)
                        foundNode = true
                        break
                    }
                }
            }

            sensorX = locX
            sensorY = locY
            sensorRadiusPx = radius
            isSensorLocated = true
            isExactNodeLocated = foundNode
            return foundNode
        } catch (e: Exception) {
            Log.w(TAG, "updateSensorLocation error: ${e.message}")
            return false
        }
    }

    private fun findUdfpsNodeRecursively(node: AccessibilityNodeInfo?, screenWidth: Float, screenHeight: Float): AccessibilityNodeInfo? {
        if (node == null) return null
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        val isUdfpsId = viewId.contains("udfps") || viewId.contains("lock_icon") ||
                viewId.contains("fingerprint") || viewId.contains("biometric_icon") ||
                viewId.contains("fp_sensor")
        val isUdfpsText = desc.contains("fingerprint") || desc.contains("指紋") ||
                desc.contains("biometric") || desc.contains("生体認証") ||
                text.contains("fingerprint") || text.contains("指紋")

        if (isUdfpsId || isUdfpsText) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // ステータスバー誤検知防止（画面中央から下部35%〜95%の範囲）
            // かつ親コンテナ誤認防止（画面幅の70%未満、画面高の40%未満のアイコンサイズ）
            val isReasonableSize = rect.width() > 0 && rect.height() > 0 &&
                    rect.width() < screenWidth * 0.7f && rect.height() < screenHeight * 0.4f
            if (isReasonableSize && rect.centerY() > screenHeight * 0.35f && rect.centerY() < screenHeight * 0.95f) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val match = findUdfpsNodeRecursively(child, screenWidth, screenHeight)
            if (match != null) return match
        }
        return null
    }

    /**
     * タッチ座標更新 & 方向案内 & ハプティクスパルス
     */
    fun onTouchCoordinates(touchX: Float, touchY: Float) {
        if (!isGuidanceActive()) return
        if (!isSensorLocated || !isExactNodeLocated) {
            updateSensorLocation()
        }

        isTouching = true
        val density = service.resources.displayMetrics.density
        val tolerance = sensorRadiusPx.coerceAtLeast(34f * density)

        val result = calculateGuidance(
            touchX = touchX,
            touchY = touchY,
            targetX = sensorX,
            targetY = sensorY,
            tolerancePx = tolerance,
            density = density
        )

        currentDistance = result.distance
        startGeigerPulse()

        if (result.isAligned) {
            if (!isCurrentlyAligned) {
                isCurrentlyAligned = true
                isPulseRunning = false
                handler.removeCallbacks(pulseRunnable)
                lastAnnouncedDirection = GuidanceDirection.ALIGNED
                lastAnnounceTimeMs = System.currentTimeMillis()
                vibrateSnap()
                soundHelper?.playActionDone()
                service.speak(service.getString(R.string.fingerprint_guidance_aligned), TextToSpeech.QUEUE_FLUSH)
            }
            return
        }

        // センサーから指が離れた場合
        val wasAligned = isCurrentlyAligned
        isCurrentlyAligned = false
        if (wasAligned) {
            startGeigerPulse()
        }

        val now = System.currentTimeMillis()
        val dirChanged = (result.direction != lastAnnouncedDirection)
        val timeSinceLast = now - lastAnnounceTimeMs

        // 方向変更時は450ms以上のクールダウン、同一方向継続時は1100ms周期で読み上げ
        if ((dirChanged && timeSinceLast >= 450L) || timeSinceLast >= 1100L) {
            lastAnnounceTimeMs = now
            lastAnnouncedDirection = result.direction
            service.speak(service.getString(result.direction.stringResId), TextToSpeech.QUEUE_FLUSH)
        }
    }

    /**
     * 座標のみ更新（ガイガー振動用、音声読み上げはスキップ）
     */
    fun updateCoordinatesOnly(touchX: Float, touchY: Float) {
        if (!isGuidanceActive()) return
        if (!isSensorLocated || !isExactNodeLocated) {
            updateSensorLocation()
        }
        isTouching = true
        val density = service.resources.displayMetrics.density
        val tolerance = sensorRadiusPx.coerceAtLeast(34f * density)
        val result = calculateGuidance(
            touchX = touchX,
            touchY = touchY,
            targetX = sensorX,
            targetY = sensorY,
            tolerancePx = tolerance,
            density = density
        )
        currentDistance = result.distance
        if (!result.isAligned) {
            isCurrentlyAligned = false
            startGeigerPulse()
        }
    }

    /**
     * ホバーイベント（AccessibilityEvent.TYPE_VIEW_HOVER_ENTER）からのノード受付
     */
    fun onHoverNode(node: AccessibilityNodeInfo?): Boolean {
        if (!isGuidanceActive() || node == null) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        // ステータスバー領域は除外
        val statusBarHeight = try {
            val resId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) service.resources.getDimensionPixelSize(resId) else 72
        } catch (_: Exception) {
            72
        }
        if (rect.top <= 0 && rect.bottom <= statusBarHeight * 1.5) return false

        // PINキーボード数字ボタン等はユーザーのキー入力を最優先
        if (service.isKeyboardOrPinKeyNode(node)) {
            return false
        }

        // 背景コンテナや指紋アイコン以外の情報提供要素（時計、通知、緊急通報、カメラ等のボタン）の場合：
        // 音声読み上げを邪魔せず画面リーダー本来の通知・時刻読み上げを許可しつつ、
        // ハプティクス用の距離のみ更新する。
        if (!isBackgroundOrFingerprintNode(node)) {
            updateCoordinatesOnly(rect.centerX().toFloat(), rect.centerY().toFloat())
            return false
        }

        onTouchCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
        return true
    }

    private fun isBackgroundOrFingerprintNode(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val isClickable = node.isClickable

        // 指紋センサー関連ノード
        if (viewId.contains("udfps") || viewId.contains("lock_icon") ||
            viewId.contains("fingerprint") || viewId.contains("biometric_icon") ||
            viewId.contains("fp_sensor") || desc.contains("fingerprint") || desc.contains("指紋")) {
            return true
        }

        // テキストや説明がなく、かつクリック可能なボタンではないノードは背景として扱う
        if (text.isEmpty() && desc.isEmpty() && !isClickable) {
            return true
        }

        // ロック画面の背景・コンテナ
        val isContainer = viewId.contains("lockscreen") || viewId.contains("scene_container") ||
                viewId.contains("container") || viewId.contains("wallpaper") ||
                viewId.contains("ambient_indication") || viewId.contains("root") ||
                viewId.contains("background") || viewId.isEmpty()

        return isContainer && !isClickable
    }

    fun onTouchStart() {
        if (!isGuidanceActive()) return
        if (!isSensorLocated || !isExactNodeLocated) {
            updateSensorLocation()
        }
        isTouching = true
    }

    fun onTouchEnd() {
        isTouching = false
        isPulseRunning = false
        handler.removeCallbacks(pulseRunnable)
        isCurrentlyAligned = false
        lastAnnouncedDirection = null
    }

    fun onKeyguardDismissed() {
        onTouchEnd()
        isSensorLocated = false
        isExactNodeLocated = false
    }

    private val pulseRunnable = object : Runnable {
        override fun run() {
            // ピッタリ重なっている間は指紋認証読取に専念できるよう振動パルスを一時停止
            if (!isTouching || !isGuidanceActive() || isCurrentlyAligned) {
                isPulseRunning = false
                return
            }

            vibrateTick()

            val density = service.resources.displayMetrics.density
            val tolerance = sensorRadiusPx.coerceAtLeast(34f * density)
            val nextDelay = calculateGeigerInterval(currentDistance, tolerance, density)

            handler.postDelayed(this, nextDelay)
        }
    }

    private fun startGeigerPulse() {
        if (!isPulseRunning) {
            isPulseRunning = true
            handler.post(pulseRunnable)
        }
    }

    private fun vibrateTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(8L)
            }
        } catch (_: Throwable) {}
    }

    private fun vibrateSnap() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35L)
            }
        } catch (_: Throwable) {}
    }

    fun destroy() {
        onTouchEnd()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && touchControllerCallback != null) {
            try {
                val controller = service.getTouchInteractionController(android.view.Display.DEFAULT_DISPLAY)
                (touchControllerCallback as? android.accessibilityservice.TouchInteractionController.Callback)?.let {
                    controller.unregisterCallback(it)
                }
            } catch (_: Throwable) {}
            touchControllerCallback = null
        }
    }
}
