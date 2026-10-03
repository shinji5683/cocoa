package com.shinji.serena.mail.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.shinji.serena.mail.R
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AccountSettingsActivity
 * Serena Mail アカウント設定 ＆ 接続テスト画面
 * 
 * Google公式の1タップ「Googleでログイン」を最優先に配置し、
 * アプリパスワードの手動入力を不要化。
 */
class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var tvSettingsDesc: TextView
    private lateinit var layoutLoggedIn: LinearLayout
    private lateinit var tvLoggedInStatus: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnGoogleSignIn: Button

    private lateinit var btnToggleManual: Button
    private lateinit var layoutManualSettings: LinearLayout
    private lateinit var etEmail: EditText
    private lateinit var etAppPassword: EditText
    private lateinit var btnOpenAppPasswordPage: Button
    private lateinit var btnSave: Button

    private lateinit var tvVersionInfo: TextView
    private lateinit var btnCheckUpdate: Button

    // Google Sign-In ランチャー
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val googleAccount = task.getResult(ApiException::class.java)
                if (googleAccount != null && !googleAccount.email.isNullOrEmpty()) {
                    handleGoogleSignInAccount(googleAccount)
                } else {
                    fallbackToAccountManager()
                }
            } catch (e: Exception) {
                // Play Console OAuth クライアント未登録などの例外時は AccountManager にフォールバック
                fallbackToAccountManager()
            }
        }
    }

    // Android 標準 AccountManager ランチャー（フォールバック用）
    private val chooseAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                fetchOAuthTokenForEmail(accountName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        accountPrefs = AccountPreferences(this)

        // Google Sign-In クライアント初期化
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope("https://mail.google.com/"),
                Scope("https://www.googleapis.com/auth/gmail.modify")
            )
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        initViews()
        updateUiState()
    }

    private fun initViews() {
        tvSettingsDesc = findViewById(R.id.tvSettingsDesc)
        layoutLoggedIn = findViewById(R.id.layoutLoggedIn)
        tvLoggedInStatus = findViewById(R.id.tvLoggedInStatus)
        btnLogout = findViewById(R.id.btnLogout)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        btnToggleManual = findViewById(R.id.btnToggleManual)
        layoutManualSettings = findViewById(R.id.layoutManualSettings)
        etEmail = findViewById(R.id.etEmail)
        etAppPassword = findViewById(R.id.etAppPassword)
        btnOpenAppPasswordPage = findViewById(R.id.btnOpenAppPasswordPage)
        btnSave = findViewById(R.id.btnSave)

        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        tvVersionInfo.text = "Serena Mail v${com.shinji.serena.mail.BuildConfig.VERSION_NAME} (${com.shinji.serena.mail.BuildConfig.REPO_RELEASE_TAG})"

        // 「Googleでログイン」ボタンクリック
        btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            try {
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                fallbackToAccountManager()
            }
        }

        // ログアウトボタン
        btnLogout.setOnClickListener {
            accountPrefs.clearAccount()
            googleSignInClient.signOut().addOnCompleteListener {
                Toast.makeText(this, getString(R.string.mail_logged_out), Toast.LENGTH_SHORT).show()
                updateUiState()
            }
        }

        // 手動設定の開閉トグル
        btnToggleManual.setOnClickListener {
            if (layoutManualSettings.visibility == View.VISIBLE) {
                layoutManualSettings.visibility = View.GONE
            } else {
                layoutManualSettings.visibility = View.VISIBLE
            }
        }

        // Google アプリパスワード生成ページをブラウザで開く
        btnOpenAppPasswordPage.setOnClickListener {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://myaccount.google.com/apppasswords")
                )
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "https://myaccount.google.com/apppasswords", Toast.LENGTH_LONG).show()
            }
        }

        // 手動保存ボタン（アプリパスワード方式）
        btnSave.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etAppPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.mail_status_no_account), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            Toast.makeText(this, getString(R.string.mail_status_connecting), Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val testResult = gmailService.testConnection(email, password, isOAuth = false)
                btnSave.isEnabled = true

                testResult.onSuccess {
                    accountPrefs.savePasswordAccount(email, password)
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_status_connected), Toast.LENGTH_LONG).show()
                    updateUiState()
                }.onFailure { e ->
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_status_error_fmt, e.localizedMessage ?: "Auth failed"), Toast.LENGTH_LONG).show()
                }
            }
        }

        // アップデート確認
        btnCheckUpdate.setOnClickListener {
            btnCheckUpdate.isEnabled = false
            com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).checkForUpdate(silent = false) { info ->
                btnCheckUpdate.isEnabled = true
                if (info != null) {
                    if (info.isUpdateAvailable) {
                        com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).showUpdateDialog(this, info)
                    } else {
                        Toast.makeText(this, getString(R.string.update_already_latest, info.currentVersion), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.update_download_failed, "Network error"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * UIの表示状態（ログイン中／未ログイン）を更新
     */
    private fun updateUiState() {
        if (accountPrefs.hasAccount()) {
            val email = accountPrefs.getEmail()
            layoutLoggedIn.visibility = View.VISIBLE
            tvLoggedInStatus.text = getString(R.string.mail_status_logged_in_as, email)
            btnGoogleSignIn.visibility = View.GONE
            btnToggleManual.visibility = View.GONE
            layoutManualSettings.visibility = View.GONE
        } else {
            layoutLoggedIn.visibility = View.GONE
            btnGoogleSignIn.visibility = View.VISIBLE
            btnToggleManual.visibility = View.VISIBLE
            etEmail.setText("")
            etAppPassword.setText("")
        }
    }

    /**
     * Google Sign-In 成功時のアカウント処理
     */
    private fun handleGoogleSignInAccount(googleAccount: GoogleSignInAccount) {
        val email = googleAccount.email ?: return
        fetchOAuthTokenForEmail(email, googleAccount.account)
    }

    /**
     * AccountManager によるフォールバック
     */
    private fun fallbackToAccountManager() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            chooseAccountLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.mail_oauth_fallback_hint), Toast.LENGTH_LONG).show()
            layoutManualSettings.visibility = View.VISIBLE
        }
    }

    /**
     * OAuthトークンを取得してGmail接続テスト＆保存
     */
    private fun fetchOAuthTokenForEmail(email: String, account: Account? = null) {
        Toast.makeText(this, getString(R.string.mail_oauth_connecting), Toast.LENGTH_SHORT).show()
        btnGoogleSignIn.isEnabled = false

        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) {
                try {
                    val targetAccount = account ?: run {
                        val am = AccountManager.get(this@AccountSettingsActivity)
                        val accounts = am.getAccountsByType("com.google")
                        accounts.find { it.name.equals(email, ignoreCase = true) }
                            ?: Account(email, "com.google")
                    }

                    // Gmail IMAP / API スコープでトークン取得
                    val scope = "oauth2:https://mail.google.com/"
                    GoogleAuthUtil.getToken(this@AccountSettingsActivity, targetAccount, scope)
                } catch (e: Exception) {
                    null
                }
            }

            btnGoogleSignIn.isEnabled = true

            if (!token.isNullOrEmpty()) {
                val testResult = gmailService.testConnection(email, token, isOAuth = true)
                testResult.onSuccess {
                    accountPrefs.saveOAuthAccount(email, token)
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_status_connected), Toast.LENGTH_LONG).show()
                    updateUiState()
                }.onFailure {
                    // トークンでのIMAP接続失敗時
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_oauth_fallback_hint), Toast.LENGTH_LONG).show()
                    etEmail.setText(email)
                    layoutManualSettings.visibility = View.VISIBLE
                }
            } else {
                // OAuth トークン取得不可時
                Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_oauth_fallback_hint), Toast.LENGTH_LONG).show()
                etEmail.setText(email)
                layoutManualSettings.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).checkAndResumePendingInstall()
    }
}
