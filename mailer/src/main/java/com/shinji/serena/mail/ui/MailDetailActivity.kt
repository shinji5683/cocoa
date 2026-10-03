package com.shinji.serena.mail.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shinji.serena.mail.R
import com.shinji.serena.mail.ai.Gemma4MailEngine
import com.shinji.serena.mail.data.MailMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * MailDetailActivity
 * メール詳細閲覧 ＆ Gemma 4 オンデバイス要約 ＆ AI返信画面
 */
class MailDetailActivity : AppCompatActivity() {

    private var currentMail: MailMessage? = null
    private val gemma4Engine by lazy { Gemma4MailEngine.getInstance(this) }
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    private lateinit var btnSummarizeGemma4: Button
    private lateinit var btnAiReply: Button
    private lateinit var layoutSummaryCard: LinearLayout
    private lateinit var tvSummaryContent: TextView
    private lateinit var tvSubject: TextView
    private lateinit var tvSender: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvBody: TextView
    private lateinit var btnReply: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mail_detail)

        currentMail = intent.getSerializableExtra("MAIL_ITEM") as? MailMessage

        btnSummarizeGemma4 = findViewById(R.id.btnSummarizeGemma4)
        btnAiReply = findViewById(R.id.btnAiReply)
        layoutSummaryCard = findViewById(R.id.layoutSummaryCard)
        tvSummaryContent = findViewById(R.id.tvSummaryContent)
        tvSubject = findViewById(R.id.tvSubject)
        tvSender = findViewById(R.id.tvSender)
        tvDate = findViewById(R.id.tvDate)
        tvBody = findViewById(R.id.tvBody)
        btnReply = findViewById(R.id.btnReply)

        currentMail?.let { mail ->
            tvSubject.text = mail.subject
            tvSender.text = getString(R.string.mail_label_from, mail.sender)
            val dateStr = if (mail.receivedDate != null) dateFormat.format(mail.receivedDate) else ""
            tvDate.text = getString(R.string.mail_label_date, dateStr)
            tvBody.text = mail.body

            // アクセシビリティヘッダー設定
            tvSubject.contentDescription = getString(R.string.mail_label_subject, mail.subject)
            tvSender.contentDescription = getString(R.string.mail_label_from, mail.sender)
        }

        // Gemma 4 で要約ボタン
        btnSummarizeGemma4.setOnClickListener {
            val mail = currentMail ?: return@setOnClickListener
            layoutSummaryCard.visibility = View.VISIBLE
            tvSummaryContent.text = getString(R.string.mail_status_summarizing)

            lifecycleScope.launch {
                val summary = gemma4Engine.summarizeEmail(mail.sender, mail.subject, mail.body)
                tvSummaryContent.text = summary
                layoutSummaryCard.contentDescription = getString(R.string.mail_summary_result_fmt, summary)
            }
        }

        // AI返信文作成ボタン
        btnAiReply.setOnClickListener {
            val mail = currentMail ?: return@setOnClickListener
            val intent = Intent(this, ComposeMailActivity::class.java).apply {
                putExtra("REPLY_TO", mail.senderAddress.ifEmpty { mail.sender })
                putExtra("REPLY_SUBJECT", if (mail.subject.startsWith("Re:", ignoreCase = true)) mail.subject else "Re: ${mail.subject}")
                putExtra("ORIGINAL_BODY", mail.body)
                putExtra("AUTO_GENERATE_AI", true)
            }
            startActivity(intent)
        }

        // 通常返信ボタン
        btnReply.setOnClickListener {
            val mail = currentMail ?: return@setOnClickListener
            val intent = Intent(this, ComposeMailActivity::class.java).apply {
                putExtra("REPLY_TO", mail.senderAddress.ifEmpty { mail.sender })
                putExtra("REPLY_SUBJECT", if (mail.subject.startsWith("Re:", ignoreCase = true)) mail.subject else "Re: ${mail.subject}")
                putExtra("ORIGINAL_BODY", mail.body)
            }
            startActivity(intent)
        }
    }
}
