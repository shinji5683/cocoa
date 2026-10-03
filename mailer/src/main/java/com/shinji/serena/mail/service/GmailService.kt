package com.shinji.serena.mail.service

import android.util.Log
import com.shinji.serena.mail.data.MailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * GmailService
 * 
 * Gmail (IMAP over SSL: port 993, SMTP over SSL: port 465) による
 * 完全非同期・セキュアなメール送受信エンジン。
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
     * アカウント接続テスト
     */
    suspend fun testConnection(email: String, appPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", IMAP_HOST)
                put("mail.imaps.port", IMAP_PORT)
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.timeout", "10000")
                put("mail.imaps.connectiontimeout", "10000")
            }

            val session = Session.getInstance(props)
            val store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, appPassword)
            store.close()
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 受信トレイから最新メッセージを取得
     */
    suspend fun fetchInbox(
        email: String,
        appPassword: String,
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
            }

            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, appPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

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
                            isUnread = isUnread
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
     * メール送信 (SMTP over SSL)
     */
    suspend fun sendEmail(
        fromEmail: String,
        appPassword: String,
        toEmail: String,
        subject: String,
        bodyText: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.host", SMTP_HOST)
                put("mail.smtp.port", SMTP_PORT)
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.connectiontimeout", "15000")
            }

            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(fromEmail, appPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail, "Shinji"))
                setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                setSubject(subject, "UTF-8")
                setText(bodyText, "UTF-8")
            }

            Transport.send(message)
            Log.i(TAG, "Email successfully sent to $toEmail")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email: ${e.message}", e)
            Result.failure(e)
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
}
