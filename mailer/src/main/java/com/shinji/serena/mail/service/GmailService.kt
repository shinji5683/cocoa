package com.shinji.serena.mail.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shinji.serena.mail.data.MailAttachment
import com.shinji.serena.mail.data.MailMessage
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

/**
 * GmailService
 * 
 * Gmail (IMAP over SSL: port 993, SMTP over SSL: port 465) による
 * 完全リアルタイム・セキュアなメール送受信＆添付ファイル対応エンジン。
 */
class GmailService {

    companion object {
        private const val TAG = "GmailService"
        private const val IMAP_HOST = "imap.gmail.com"
        private const val IMAP_PORT = "993"
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "465"
    }

    /**
     * アカウント接続テスト (アプリパスワード / OAuth2 トークン両対応)
     */
    suspend fun testConnection(
        email: String,
        tokenOrPassword: String,
        isOAuth: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", IMAP_HOST)
                put("mail.imaps.port", IMAP_PORT)
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.timeout", "10000")
                put("mail.imaps.connectiontimeout", "10000")
                if (isOAuth) {
                    put("mail.imaps.sasl.enable", "true")
                    put("mail.imaps.sasl.mechanisms", "XOAUTH2")
                    put("mail.imaps.auth.login.disable", "true")
                    put("mail.imaps.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props)
            val store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, tokenOrPassword)
            store.close()
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 受信トレイから最新メッセージを取得 (アプリパスワード / OAuth2 トークン両対応)
     */
    suspend fun fetchInbox(
        email: String,
        tokenOrPassword: String,
        isOAuth: Boolean = false,
        maxCount: Int = 30
    ): Result<List<MailMessage>> = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        var inbox: Folder? = null
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", IMAP_HOST)
                put("mail.imaps.port", IMAP_PORT)
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.timeout", "15000")
                put("mail.imaps.connectiontimeout", "15000")
                if (isOAuth) {
                    put("mail.imaps.sasl.enable", "true")
                    put("mail.imaps.sasl.mechanisms", "XOAUTH2")
                    put("mail.imaps.auth.login.disable", "true")
                    put("mail.imaps.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, tokenOrPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

            val imapFolder = inbox as? IMAPFolder

            val total = inbox.messageCount
            if (total == 0) {
                return@withContext Result.success(emptyList())
            }

            val startIndex = (total - maxCount + 1).coerceAtLeast(1)
            val rawMessages = inbox.getMessages(startIndex, total)

            val resultList = mutableListOf<MailMessage>()
            // 新しい順（降順）に処理
            for (i in rawMessages.indices.reversed()) {
                val msg = rawMessages[i]
                try {
                    val uid = if (imapFolder != null) imapFolder.getUID(msg) else msg.messageNumber.toLong()
                    val fromArray = msg.from
                    val senderStr = if (!fromArray.isNullOrEmpty()) {
                        val addr = fromArray[0] as? InternetAddress
                        addr?.personal ?: addr?.address ?: fromArray[0].toString()
                    } else "Unknown"

                    val senderAddress = if (!fromArray.isNullOrEmpty()) {
                        val addr = fromArray[0] as? InternetAddress
                        addr?.address ?: ""
                    } else ""

                    val toList = msg.getRecipients(Message.RecipientType.TO)?.map { it.toString() } ?: emptyList()
                    val subject = msg.subject ?: "(No Subject)"
                    val body = extractBodyText(msg)
                    val isUnread = !msg.isSet(Flags.Flag.SEEN)
                    val date = msg.sentDate ?: msg.receivedDate

                    resultList.add(
                        MailMessage(
                            messageId = msg.messageNumber.toString(),
                            sender = senderStr,
                            senderAddress = senderAddress,
                            recipients = toList,
                            subject = subject,
                            body = body,
                            receivedDate = date,
                            isUnread = isUnread,
                            uid = uid
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message $i: ${e.message}")
                }
            }

            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch inbox: ${e.message}", e)
            Result.failure(e)
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * メールを既読化（Gmailサーバー上の SEEN フラグを更新）
     */
    suspend fun markAsRead(
        email: String,
        tokenOrPassword: String,
        uid: Long,
        isOAuth: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        var inbox: Folder? = null
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", IMAP_HOST)
                put("mail.imaps.port", IMAP_PORT)
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.timeout", "10000")
                put("mail.imaps.connectiontimeout", "10000")
                if (isOAuth) {
                    put("mail.imaps.sasl.enable", "true")
                    put("mail.imaps.sasl.mechanisms", "XOAUTH2")
                    put("mail.imaps.auth.login.disable", "true")
                    put("mail.imaps.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, tokenOrPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            val imapFolder = inbox as? IMAPFolder
            val msg = if (imapFolder != null && uid > 0) {
                imapFolder.getMessageByUID(uid)
            } else if (uid.toInt() in 1..inbox.messageCount) {
                inbox.getMessage(uid.toInt())
            } else {
                null
            }

            if (msg != null) {
                msg.setFlag(Flags.Flag.SEEN, true)
                Log.i(TAG, "Successfully marked message (UID=$uid) as SEEN on Gmail server")
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark as read (UID=$uid): ${e.message}")
            Result.failure(e)
        } finally {
            try { inbox?.close(true) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * メール送信 ＆ 音声/画像/ファイル添付対応 ＆ 送信トレイ自動同期
     * (.flac, .wav, .mp3, 写真, ドキュメント, ドライブ等あらゆる添付に対応)
     */
    suspend fun sendEmail(
        context: Context,
        fromEmail: String,
        tokenOrPassword: String,
        toEmail: String,
        subject: String,
        bodyText: String,
        attachments: List<MailAttachment> = emptyList(),
        isOAuth: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        try {
            val props = Properties().apply {
                put("mail.smtp.host", SMTP_HOST)
                put("mail.smtp.port", SMTP_PORT)
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.timeout", "30000")
                put("mail.smtp.connectiontimeout", "30000")
                if (isOAuth) {
                    put("mail.smtp.sasl.enable", "true")
                    put("mail.smtp.sasl.mechanisms", "XOAUTH2")
                    put("mail.smtp.auth.login.disable", "true")
                    put("mail.smtp.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(fromEmail, tokenOrPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail, "Shinji"))
                setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                setSubject(subject, "UTF-8")
                sentDate = java.util.Date()
            }

            if (attachments.isEmpty()) {
                message.setText(bodyText, "UTF-8")
            } else {
                val multipart = MimeMultipart("mixed")

                // テキスト本文パート
                val textPart = MimeBodyPart().apply {
                    setText(bodyText, "UTF-8")
                }
                multipart.addBodyPart(textPart)

                // 各添付ファイルパート (.flac, .wav, .mp3, 写真, ドキュメント等)
                for (att in attachments) {
                    try {
                        val attachPart = MimeBodyPart()
                        val dataSource = UriDataSource(context, att.uri, att.fileName, att.mimeType)
                        attachPart.dataHandler = DataHandler(dataSource)
                        attachPart.fileName = MimeUtility.encodeText(att.fileName, "UTF-8", null)
                        multipart.addBodyPart(attachPart)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to attach file ${att.fileName}: ${e.message}", e)
                    }
                }
                message.setContent(multipart)
            }

            // SMTP送信
            if (isOAuth) {
                val transport = session.getTransport("smtps")
                transport.connect(SMTP_HOST, fromEmail, tokenOrPassword)
                transport.sendMessage(message, message.allRecipients)
                transport.close()
            } else {
                Transport.send(message)
            }
            Log.i(TAG, "Email successfully sent to $toEmail")

            // 送信済みトレイ（[Gmail]/Sent Mail または Sent）にコピー保存して公式Gmailと完全同期
            try {
                val imapProps = Properties().apply {
                    put("mail.store.protocol", "imaps")
                    put("mail.imaps.host", IMAP_HOST)
                    put("mail.imaps.port", IMAP_PORT)
                    put("mail.imaps.ssl.enable", "true")
                    if (isOAuth) {
                        put("mail.imaps.sasl.enable", "true")
                        put("mail.imaps.sasl.mechanisms", "XOAUTH2")
                        put("mail.imaps.auth.login.disable", "true")
                        put("mail.imaps.auth.plain.disable", "true")
                    }
                }
                val imapSession = Session.getInstance(imapProps)
                store = imapSession.getStore("imaps")
                store.connect(IMAP_HOST, fromEmail, tokenOrPassword)

                val sentFolderNames = listOf("[Gmail]/Sent Mail", "[Gmail]/送信済みトレイ", "Sent", "Sent Messages")
                var sentFolder: Folder? = null
                for (name in sentFolderNames) {
                    try {
                        val folder = store.getFolder(name)
                        if (folder.exists()) {
                            sentFolder = folder
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (sentFolder != null) {
                    sentFolder.open(Folder.READ_WRITE)
                    message.setFlag(Flags.Flag.SEEN, true)
                    sentFolder.appendMessages(arrayOf(message))
                    sentFolder.close(false)
                    Log.i(TAG, "Successfully synced sent email to Gmail sent folder: ${sentFolder.fullName}")
                }
            } catch (sentEx: Exception) {
                Log.w(TAG, "Warning: Could not sync to sent folder: ${sentEx.message}")
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email: ${e.message}", e)
            Result.failure(e)
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    /**
     * MimeMessageからプレーンテキスト本文を抽出
     */
    private fun extractBodyText(message: Message): String {
        return try {
            val content = message.content
            when (content) {
                is String -> content
                is MimeMultipart -> extractTextFromMultipart(content)
                else -> content?.toString() ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract body text: ${e.message}")
            ""
        }
    }

    private fun extractTextFromMultipart(multipart: MimeMultipart): String {
        val count = multipart.count
        for (i in 0 until count) {
            val bodyPart = multipart.getBodyPart(i)
            if (bodyPart.isMimeType("text/plain")) {
                return bodyPart.content.toString()
            } else if (bodyPart.isMimeType("text/html")) {
                val html = bodyPart.content.toString()
                return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } else if (bodyPart.content is MimeMultipart) {
                return extractTextFromMultipart(bodyPart.content as MimeMultipart)
            }
        }
        return ""
    }

    /**
     * Android Content Uri 用の DataSource 実装
     * オーディオ(.flac/.wav/.mp3)、画像、ドキュメント、Googleドライブ等を安全にストリーミング
     */
    private class UriDataSource(
        private val context: Context,
        private val uri: Uri,
        private val name: String,
        private val mime: String
    ) : DataSource {
        override fun getInputStream(): InputStream {
            return context.contentResolver.openInputStream(uri)
                ?: throw java.io.FileNotFoundException("Cannot open stream for URI: $uri")
        }

        override fun getOutputStream(): OutputStream {
            throw UnsupportedOperationException("Writing to attachment URI is not supported")
        }

        override fun getContentType(): String {
            return if (mime.isNotEmpty()) mime else "application/octet-stream"
        }

        override fun getName(): String {
            return name
        }
    }
}

