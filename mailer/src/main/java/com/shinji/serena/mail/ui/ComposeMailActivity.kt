package com.shinji.serena.mail.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shinji.serena.mail.R
import com.shinji.serena.mail.ai.Gemma4MailEngine
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.launch

/**
 * ComposeMailActivity
 * メール新規作成 ＆ Gemma 4 AI下書き生成 ＆ 送信画面
 */
class ComposeMailActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()
    private val gemma4Engine by lazy { Gemma4MailEngine.getInstance(this) }

    private lateinit var etTo: EditText
    private lateinit var etSubject: EditText
    private lateinit var etBody: EditText
    private lateinit var btnAiAssist: Button
    private lateinit var btnSend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_mail)

        accountPrefs = AccountPreferences(this)

        etTo = findViewById(R.id.etTo)
        etSubject = findViewById(R.id.etSubject)
        etBody = findViewById(R.id.etBody)
        btnAiAssist = findViewById(R.id.btnAiAssist)
        btnSend = findViewById(R.id.btnSend)

        val replyTo = intent.getStringExtra("REPLY_TO") ?: ""
        val replySubject = intent.getStringExtra("REPLY_SUBJECT") ?: ""
        val originalBody = intent.getStringExtra("ORIGINAL_BODY") ?: ""
        val autoGenerate = intent.getBooleanExtra("AUTO_GENERATE_AI", false)

        if (replyTo.isNotEmpty()) etTo.setText(replyTo)
        if (replySubject.isNotEmpty()) etSubject.setText(replySubject)

        if (autoGenerate && originalBody.isNotEmpty()) {
            generateAiDraft(replyTo, replySubject, originalBody)
        }

        btnAiAssist.setOnClickListener {
            val to = etTo.text.toString().trim()
            val subj = etSubject.text.toString().trim()
            val currentBody = etBody.text.toString().trim().ifEmpty { originalBody }
            generateAiDraft(to, subj, currentBody)
        }

        btnSend.setOnClickListener {
            sendEmail()
        }
    }

    private fun generateAiDraft(sender: String, subject: String, body: String) {
        Toast.makeText(this, getString(R.string.mail_status_generating_reply), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val draft = gemma4Engine.generateReplyDraft(sender, subject, body)
            etBody.setText(draft)
            etBody.setSelection(draft.length)
        }
    }

    private fun sendEmail() {
        val to = etTo.text.toString().trim()
        val subject = etSubject.text.toString().trim()
        val body = etBody.text.toString().trim()

        if (to.isEmpty()) {
            Toast.makeText(this, getString(R.string.mail_hint_to), Toast.LENGTH_SHORT).show()
            return
        }

        val myEmail = accountPrefs.getEmail()
        val myPassword = accountPrefs.getAppPassword()

        if (myEmail.isEmpty() || myPassword.isEmpty()) {
            Toast.makeText(this, getString(R.string.mail_status_no_account), Toast.LENGTH_LONG).show()
            return
        }

        btnSend.isEnabled = false
        Toast.makeText(this, getString(R.string.mail_status_sending), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = gmailService.sendEmail(myEmail, myPassword, to, subject, body)
            btnSend.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@ComposeMailActivity, getString(R.string.mail_status_sent), Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { e ->
                Toast.makeText(this@ComposeMailActivity, getString(R.string.mail_status_send_failed_fmt, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
