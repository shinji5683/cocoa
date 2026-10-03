package com.shinji.serena.mail.data

import java.io.Serializable
import java.util.Date

/**
 * Serena Mail メールメッセージモデル
 */
data class MailMessage(
    val messageId: String,
    val sender: String,
    val senderAddress: String,
    val recipients: List<String>,
    val subject: String,
    val body: String,
    val receivedDate: Date?,
    val isUnread: Boolean,
    val snippet: String = body.take(120).replace("\n", " ").trim()
) : Serializable
