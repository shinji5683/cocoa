package com.shinji.serena.mail.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * AccountPreferences
 * Gmailアカウント情報（メールアドレスおよびアプリパスワード）を
 * 端末内の暗号化ストレージ（EncryptedSharedPreferences）で安全に保管。
 */
class AccountPreferences(context: Context) {

    companion object {
        private const val PREFS_FILE = "serena_mail_secure_prefs"
        private const val KEY_EMAIL = "email_address"
        private const val KEY_APP_PASSWORD = "app_password"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // 暗号化初期化に失敗した場合の標準Prefsフォールバック
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveAccount(email: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_APP_PASSWORD, appPassword.replace(" ", "").trim())
            .apply()
    }

    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

    fun getAppPassword(): String = prefs.getString(KEY_APP_PASSWORD, "") ?: ""

    fun hasAccount(): Boolean = getEmail().isNotEmpty() && getAppPassword().isNotEmpty()

    fun clearAccount() {
        prefs.edit().clear().apply()
    }
}
