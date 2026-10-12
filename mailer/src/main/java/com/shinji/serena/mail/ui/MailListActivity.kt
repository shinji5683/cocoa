package com.shinji.serena.mail.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shinji.serena.mail.R
import com.shinji.serena.mail.ai.EmbeddingGemma2Engine
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.data.MailMessage
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MailListActivity
 * 受信トレイ画面（メイン画面）
 * 
 * Google EmbeddingGemma 2 オンデバイス意味検索（セマンティック検索）搭載
 */
class MailListActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()
    private lateinit var mailAdapter: MailAdapter

    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCompose: Button
    private lateinit var btnSettings: Button
    private lateinit var etSearchQuery: EditText
    private lateinit var btnClearSearch: Button
    private lateinit var recyclerViewMails: RecyclerView

    private var allMails: List<MailMessage> = emptyList()
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mail_list)

        accountPrefs = AccountPreferences(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCompose = findViewById(R.id.btnCompose)
        btnSettings = findViewById(R.id.btnSettings)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnClearSearch = findViewById(R.id.btnClearSearch)
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

        // EmbeddingGemma 2 によるリアルタイム意味検索
        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSemanticSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearchQuery.setText("")
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
        val credential = accountPrefs.getAuthCredential()
        val isOAuth = accountPrefs.isOAuth()

        if (email.isEmpty() || credential.isEmpty()) {
            showStatus(getString(R.string.mail_status_no_account))
            return
        }

        showStatus(getString(R.string.mail_status_fetching))

        lifecycleScope.launch {
            val result = gmailService.fetchInbox(email, credential, isOAuth = isOAuth, maxCount = 30)
            result.onSuccess { list ->
                allMails = list
                val currentQuery = etSearchQuery.text.toString().trim()
                if (currentQuery.isNotEmpty()) {
                    performSemanticSearch(currentQuery)
                } else {
                    mailAdapter.submitList(list)
                    showStatus(getString(R.string.mail_status_fetched_fmt, list.size))
                }
            }.onFailure { e ->
                showStatus(getString(R.string.mail_status_error_fmt, e.localizedMessage ?: "Network error"))
            }
        }
    }

    private fun performSemanticSearch(query: String) {
        searchJob?.cancel()
        if (query.isEmpty()) {
            mailAdapter.submitList(allMails)
            showStatus(getString(R.string.mail_status_fetched_fmt, allMails.size))
            return
        }

        searchJob = lifecycleScope.launch {
            delay(150) // デバウンス
            val scored = EmbeddingGemma2Engine.getInstance(this@MailListActivity).searchMails(query, allMails)
            val filtered = scored.filter { it.similarityScore > 0.05f }.map { it.mail }
            mailAdapter.submitList(filtered)
            showStatus(getString(R.string.mail_search_result_fmt, filtered.size))
        }
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
    }
}
