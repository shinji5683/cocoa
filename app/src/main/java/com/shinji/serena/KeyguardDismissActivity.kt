package com.shinji.serena

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * KeyguardDismissActivity
 *
 * ロック画面上で表示可能な透明Activityで、KeyguardManager.requestDismissKeyguard() を呼び出し、
 * OSにPIN/パスワード/パターン入力画面（バウンサー）を正式に表示させる。
 *
 * AccessibilityServiceからは dispatchGesture() でロック画面のタッチイベントを注入できないため、
 * このActivityを起動してOSのAPIを使う必要がある（TalkBackと同じパターン）。
 */
class KeyguardDismissActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KeyguardDismissActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面の上に表示されるように設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null) {
            Log.e(TAG, "KeyguardManager is null, finishing.")
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    Log.e(TAG, "requestDismissKeyguard: onDismissError")
                    finish()
                }

                override fun onDismissSucceeded() {
                    Log.i(TAG, "requestDismissKeyguard: onDismissSucceeded - Keyguard dismissed!")
                    finish()
                }

                override fun onDismissCancelled() {
                    Log.i(TAG, "requestDismissKeyguard: onDismissCancelled - User cancelled.")
                    finish()
                }
            })
        } else {
            // Android O 未満: FLAG_DISMISS_KEYGUARD で対応（上記 window.addFlags で設定済み）
            Log.i(TAG, "Pre-O device: using FLAG_DISMISS_KEYGUARD")
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // バウンサーが表示されたら、このActivityはもう不要
        if (!hasFocus) {
            Log.d(TAG, "Lost window focus (bouncer likely shown). Finishing.")
            // バウンサーにフォーカスが移ったらすぐ閉じる
        }
    }
}
