package com.shinji.serena.mail.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shinji.serena.mail.R
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.launch

/**
 * MailListActivity
 * 受信トレイ画面（メイン画面）
 */
class MailListActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()
    private lateinit var mailAdapter: MailAdapter

    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCompose: Button
    private lateinit var btnSettings: Button
    private lateinit var recyclerViewMails: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mail_list)

        accountPrefs = AccountPreferences(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCompose = findViewById(R.id.btnCompose)
        btnSettings = findViewById(R.id.btnSettings)
        recyclerViewMails = findViewById(R.id.recyclerViewMails)

        mailAdapter = MailAdapter { mail ->
            val intent = Intent(this, MailDetailActivity::class.java).apply {
                putExtra("MAIL_ITEM", mail)
            }
            startActivity(intent)
        }

        recyclerViewMails.layoutManager = LinearLayoutManager(this)
        recyclerViewMails.adapter = mailAdapter

        btnRefresh.setOnClickListener {
            refreshInbox()
        }

        btnCompose.setOnClickListener {
            startActivity(Intent(this, ComposeMailActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, AccountSettingsActivity::class.java))
        }

        // 起動時にサイレントでアップデートを確認し、最新版があれば案内ダイアログを表示
        com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).checkForUpdate(silent = true) { info ->
            if (!isFinishing && !isDestroyed && info != null && info.isUpdateAvailable) {
                com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).showUpdateDialog(this, info)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.shinji.serena.mail.update.AutoUpdateManager.getInstance(this).checkAndResumePendingInstall()
        if (!accountPrefs.hasAccount()) {
            showStatus(getString(R.string.mail_status_no_account))
            startActivity(Intent(this, AccountSettingsActivity::class.java))
        } else {
            refreshInbox()
        }
    }

    private fun refreshInbox() {
        val email = accountPrefs.getEmail()
        val password = accountPrefs.getAppPassword()

        if (email.isEmpty() || password.isEmpty()) {
            showStatus(getString(R.string.mail_status_no_account))
            return
        }

        showStatus(getString(R.string.mail_status_fetching))

        lifecycleScope.launch {
            val result = gmailService.fetchInbox(email, password, maxCount = 30)
            result.onSuccess { list ->
                mailAdapter.submitList(list)
                showStatus(getString(R.string.mail_status_fetched_fmt, list.size))
            }.onFailure { e ->
                showStatus(getString(R.string.mail_status_error_fmt, e.localizedMessage ?: "Network error"))
            }
        }
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
    }
}
