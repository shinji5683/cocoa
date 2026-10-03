package com.shinji.serena.mail.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shinji.serena.mail.R
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.launch

/**
 * AccountSettingsActivity
 * Gmailアカウント設定 ＆ 接続テスト画面
 */
class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()

    private lateinit var etEmail: EditText
    private lateinit var etAppPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var tvVersionInfo: TextView
    private lateinit var btnCheckUpdate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        accountPrefs = AccountPreferences(this)

        etEmail = findViewById(R.id.etEmail)
        etAppPassword = findViewById(R.id.etAppPassword)
        btnSave = findViewById(R.id.btnSave)
        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        tvVersionInfo.text = "Serena Mail v${com.shinji.serena.mail.BuildConfig.VERSION_NAME} (${com.shinji.serena.mail.BuildConfig.REPO_RELEASE_TAG})"

        etEmail.setText(accountPrefs.getEmail())
        etAppPassword.setText(accountPrefs.getAppPassword())

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
                val testResult = gmailService.testConnection(email, password)
                btnSave.isEnabled = true

                testResult.onSuccess {
                    accountPrefs.saveAccount(email, password)
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_status_connected), Toast.LENGTH_LONG).show()
                    finish()
                }.onFailure { e ->
                    Toast.makeText(this@AccountSettingsActivity, getString(R.string.mail_status_error_fmt, e.localizedMessage ?: "Auth failed"), Toast.LENGTH_LONG).show()
                }
            }
        }

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

    override fun onResume() {
        super.onResume()
        com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).checkAndResumePendingInstall()
    }
}
