package com.shinji.serena.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Android 標準 PackageInstaller セッション API のコールバックを受信し、
 * ユーザー確認ダイアログ（「このアプリを更新しますか？」）を自動起動するレシーバー
 */
class AutoUpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutoUpdateReceiver"
        const val ACTION_INSTALL_STATUS = "com.shinji.serena.action.INSTALL_STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        Log.i(TAG, "PackageInstaller session status: $status ($message)")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android 10+ / 14 / 17: システム標準の更新確認ダイアログ Activity を起動
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                } else {
                    Log.w(TAG, "EXTRA_INTENT was null in STATUS_PENDING_USER_ACTION")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update package installed successfully!")
            }
            else -> {
                Log.e(TAG, "Installation session completed with non-success status: $status ($message)")
            }
        }
    }
}
