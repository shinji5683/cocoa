package com.shinji.serena.mail.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shinji.serena.mail.R
import com.shinji.serena.mail.ai.Gemma4MailEngine
import com.shinji.serena.mail.data.AccountPreferences
import com.shinji.serena.mail.data.MailAttachment
import com.shinji.serena.mail.service.GmailService
import kotlinx.coroutines.launch

/**
 * ComposeMailActivity
 * メール新規作成 ＆ オーディオ(.flac/.wav/.mp3)・写真・ファイル・ドライブフル添付 ＆ Gemma 4 AI下書き生成 ＆ 送信画面
 */
class ComposeMailActivity : AppCompatActivity() {

    private lateinit var accountPrefs: AccountPreferences
    private val gmailService = GmailService()
    private val gemma4Engine by lazy { Gemma4MailEngine.getInstance(this) }

    private lateinit var etTo: EditText
    private lateinit var etSubject: EditText
    private lateinit var etBody: EditText
    private lateinit var btnAiAssist: Button
    private lateinit var btnAttachAudio: Button
    private lateinit var btnAttachFile: Button
    private lateinit var tvAttachmentHeader: TextView
    private lateinit var layoutAttachments: LinearLayout
    private lateinit var btnSend: Button

    private val attachmentList = mutableListOf<MailAttachment>()

    // 音声ファイル用ピッカー (FLAC, WAV, MP3, M4A, OGG, OPUS等)
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.forEach { uri -> handleSelectedUri(uri) }
    }

    // 全ファイル用ピッカー (写真、ドキュメント、Googleドライブ、ZIP等)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.forEach { uri -> handleSelectedUri(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_mail)

        accountPrefs = AccountPreferences(this)

        etTo = findViewById(R.id.etTo)
        etSubject = findViewById(R.id.etSubject)
        etBody = findViewById(R.id.etBody)
        btnAiAssist = findViewById(R.id.btnAiAssist)
        btnAttachAudio = findViewById(R.id.btnAttachAudio)
        btnAttachFile = findViewById(R.id.btnAttachFile)
        tvAttachmentHeader = findViewById(R.id.tvAttachmentHeader)
        layoutAttachments = findViewById(R.id.layoutAttachments)
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

        // 音声ファイル添付ボタン (FLAC, WAV, MP3, AAC, OGG等)
        btnAttachAudio.setOnClickListener {
            val audioMimeTypes = arrayOf(
                "audio/*",
                "audio/flac",
                "audio/x-flac",
                "audio/wav",
                "audio/x-wav",
                "audio/mpeg",
                "audio/mp3",
                "audio/mp4",
                "audio/ogg",
                "audio/opus"
            )
            audioPickerLauncher.launch(audioMimeTypes)
        }

        // あらゆるファイル添付ボタン (画像・カメラ写真・ドキュメント・Googleドライブ)
        btnAttachFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        btnSend.setOnClickListener {
            sendEmail()
        }
    }

    private fun handleSelectedUri(uri: Uri) {
        try {
            var fileName = "attachment"
            var fileSize = 0L
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            // Gmailの添付上限: 25MB (25 * 1024 * 1024 = 26,214,400 bytes)
            if (fileSize > 25 * 1024 * 1024) {
                Toast.makeText(this, getString(R.string.mail_attachment_size_too_large, fileName), Toast.LENGTH_LONG).show()
                return
            }

            // 重複チェック
            if (attachmentList.none { it.uri == uri }) {
                val attachment = MailAttachment(
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    size = fileSize
                )
                attachmentList.add(attachment)
                updateAttachmentViews()
                Toast.makeText(this, getString(R.string.mail_attachment_added_fmt, fileName), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.mail_attachment_read_error, e.localizedMessage ?: "Unknown"), Toast.LENGTH_LONG).show()
        }
    }

    private fun updateAttachmentViews() {
        layoutAttachments.removeAllViews()

        if (attachmentList.isEmpty()) {
            tvAttachmentHeader.visibility = View.GONE
            return
        }

        tvAttachmentHeader.visibility = View.VISIBLE
        tvAttachmentHeader.text = getString(R.string.mail_attachment_count_fmt, attachmentList.size)

        val inflater = LayoutInflater.from(this)
        for (att in attachmentList) {
            val itemView = inflater.inflate(R.layout.item_attachment, layoutAttachments, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvAttachmentName)
            val tvSize = itemView.findViewById<TextView>(R.id.tvAttachmentSize)
            val btnRemove = itemView.findViewById<Button>(R.id.btnRemoveAttachment)

            val sizeStr = formatFileSize(att.size)
            tvName.text = att.fileName
            tvSize.text = sizeStr

            // スクリーンリーダー向けの明瞭な読み上げラベル
            val a11yDesc = getString(R.string.mail_attachment_item_a11y_fmt, att.fileName, sizeStr)
            itemView.contentDescription = a11yDesc
            btnRemove.contentDescription = "${att.fileName} を${getString(R.string.mail_attachment_btn_remove)}"

            btnRemove.setOnClickListener {
                attachmentList.remove(att)
                updateAttachmentViews()
                Toast.makeText(this, getString(R.string.mail_attachment_removed_fmt, att.fileName), Toast.LENGTH_SHORT).show()
            }

            layoutAttachments.addView(itemView)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            getString(R.string.mail_attachment_unit_mb, bytes.toDouble() / (1024 * 1024))
        } else {
            getString(R.string.mail_attachment_unit_kb, bytes.toDouble() / 1024)
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
        val myCredential = accountPrefs.getAuthCredential()
        val isOAuth = accountPrefs.isOAuth()

        if (myEmail.isEmpty() || myCredential.isEmpty()) {
            Toast.makeText(this, getString(R.string.mail_status_no_account), Toast.LENGTH_LONG).show()
            return
        }

        btnSend.isEnabled = false
        Toast.makeText(this, getString(R.string.mail_status_sending), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = gmailService.sendEmail(
                context = applicationContext,
                fromEmail = myEmail,
                tokenOrPassword = myCredential,
                toEmail = to,
                subject = subject,
                bodyText = body,
                attachments = attachmentList,
                isOAuth = isOAuth
            )
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
