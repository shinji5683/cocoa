package com.shinji.serena

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager

object SafeContextUtils {

    /**
     * Direct Boot モード（ロック画面・ユーザー未ロック状態）でも安全に使用できる Context を返します。
     * API 24+ では Device Protected Storage Context を使用します。
     */
    fun getSafeContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (context.isDeviceProtectedStorage) {
                context
            } else {
                context.createDeviceProtectedStorageContext() ?: context
            }
        } else {
            context
        }
    }

    /**
     * ロック解除前（Direct Boot モード）であっても IllegalStateException を絶対に発生させない
     * 安全な SharedPreferences を取得します。
     */
    fun getSafeSharedPreferences(context: Context, name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences {
        val safeCtx = getSafeContext(context)
        return try {
            safeCtx.getSharedPreferences(name, mode)
        } catch (e: Exception) {
            try {
                context.getSharedPreferences(name, mode)
            } catch (e2: Exception) {
                // 最悪のケースでもフォールバックしてクラッシュを回避
                safeCtx.createDeviceProtectedStorageContext().getSharedPreferences(name, mode)
            }
        }
    }

    /**
     * ユーザーが画面ロックを解除済みかどうかを判定します。
     */
    fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return userManager?.isUserUnlocked ?: true
    }
}

/**
 * Context の拡張関数
 */
fun Context.getSafeContext(): Context = SafeContextUtils.getSafeContext(this)

fun Context.getSafeSharedPreferences(name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences {
    return SafeContextUtils.getSafeSharedPreferences(this, name, mode)
}
