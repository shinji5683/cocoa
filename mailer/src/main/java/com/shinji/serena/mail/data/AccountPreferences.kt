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
        private const val KEY_OAUTH_TOKEN = "oauth_token"
        private const val KEY_AUTH_TYPE = "auth_type"

        const val AUTH_TYPE_PASSWORD = "password"
        const val AUTH_TYPE_OAUTH2 = "oauth2"
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
        savePasswordAccount(email, appPassword)
    }

    fun savePasswordAccount(email: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_APP_PASSWORD, appPassword.replace(" ", "").trim())
            .putString(KEY_AUTH_TYPE, AUTH_TYPE_PASSWORD)
            .apply()
    }

    fun saveOAuthAccount(email: String, oauthToken: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_OAUTH_TOKEN, oauthToken.trim())
            .putString(KEY_AUTH_TYPE, AUTH_TYPE_OAUTH2)
            .apply()
    }

    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

    fun getAppPassword(): String = getAuthCredential()

    fun getAuthCredential(): String {
        return if (isOAuth()) {
            prefs.getString(KEY_OAUTH_TOKEN, "") ?: ""
        } else {
            prefs.getString(KEY_APP_PASSWORD, "") ?: ""
        }
    }

    fun getAuthType(): String = prefs.getString(KEY_AUTH_TYPE, AUTH_TYPE_PASSWORD) ?: AUTH_TYPE_PASSWORD

    fun isOAuth(): Boolean = getAuthType() == AUTH_TYPE_OAUTH2

    fun hasAccount(): Boolean = getEmail().isNotEmpty() && getAuthCredential().isNotEmpty()

    fun clearAccount() {
        prefs.edit().clear().apply()
    }
}
