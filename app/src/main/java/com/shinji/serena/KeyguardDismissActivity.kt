package com.shinji.serena

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * KeyguardDismissActivity
 *
 * ロック画面上で表示可能な透明Activityで、KeyguardManager.requestDismissKeyguard() を呼び出し、
 * OSにPIN/パスワード/パターン入力画面（バウンサー）を正式に表示させる。
 *
 * 重要:
 * 1. 素の Activity を継承（AppCompatActivity のテーマ非互換クラッシュを防止）
 * 2. FLAG_NOT_TOUCHABLE / FLAG_NOT_FOCUSABLE を設定し、タッチやアクセシビリティフォーカスが
 *    この透明Activityに吸われず、背後のSystemUI PINキーパッドに直接届くようにする。
 * 3. requestDismissKeyguard() 実行後、速やかに finish() して前面から退場し、
 *    SystemUI / PIN入力画面が完全に最前面のアクティブウィンドウになるようにする。
 */
class KeyguardDismissActivity : Activity() {

    companion object {
        private const val TAG = "KeyguardDismissActivity"
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面の上に表示 + 画面点灯 + タッチ・フォーカスを通過（パススルー）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null) {
            Log.e(TAG, "KeyguardManager is null, finishing immediately.")
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    Log.e(TAG, "requestDismissKeyguard: onDismissError")
                    finishAndRemoveTaskSafe()
                }

                override fun onDismissSucceeded() {
                    Log.i(TAG, "requestDismissKeyguard: onDismissSucceeded - Keyguard dismissed!")
                    finishAndRemoveTaskSafe()
                }

                override fun onDismissCancelled() {
                    Log.i(TAG, "requestDismissKeyguard: onDismissCancelled")
                    finishAndRemoveTaskSafe()
                }
            })

            // OSへリクエスト送出後、前面を塞がないよう短時間(200ms)で確実にActivityを終了してSystemUIに主権を渡す
            handler.postDelayed({
                finishAndRemoveTaskSafe()
            }, 200)
        } else {
            // Android O 未満: FLAG_DISMISS_KEYGUARD で対応
            handler.postDelayed({
                finishAndRemoveTaskSafe()
            }, 100)
        }
    }

    private fun finishAndRemoveTaskSafe() {
        try {
            if (!isFinishing && !isDestroyed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
