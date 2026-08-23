package com.shinji.serena

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * ロック画面（Keyguard）からBouncer（PIN/パスワード/パターン入力画面）を
 * 100%確実に召喚するための透過専用Activity。
 * 
 * Android OSの仕様上、KeyguardManager.requestDismissKeyguard() は
 * Activityコンテキストから呼び出さなければOSのWindowManagerが無視するため、
 * 本Activityを経由して正規にOSへ解除要求を直撃させる。
 */
class SerenaUnlockActivity : Activity() {

    companion object {
        private const val TAG = "SerenaUnlockActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面上に表示し、画面を点灯させるフラグを適用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        requestBouncer()
    }

    private fun requestBouncer() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    super.onDismissError()
                    Log.w(TAG, "Keyguard dismiss error")
                    SerenaScreenReaderService.instance?.autoFocusPinKeypadIfPresent(force = true)
                    finish()
                }

                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    Log.i(TAG, "Keyguard dismiss succeeded")
                    finish()
                }

                override fun onDismissCancelled() {
                    super.onDismissCancelled()
                    Log.d(TAG, "Keyguard dismiss cancelled")
                    SerenaScreenReaderService.instance?.autoFocusPinKeypadIfPresent(force = true)
                    finish()
                }
            })
        } else {
            finish()
        }

        // 即座にPINフィールドへのフォーカスを要求
        window.decorView.postDelayed({
            SerenaScreenReaderService.instance?.autoFocusPinKeypadIfPresent(force = true)
        }, 80)
    }

    override fun onResume() {
        super.onResume()
        // Bouncer展開後にActivityが残り続けないよう速やかに閉じる
        window.decorView.postDelayed({
            if (!isFinishing) {
                finish()
            }
        }, 1500)
    }
}
