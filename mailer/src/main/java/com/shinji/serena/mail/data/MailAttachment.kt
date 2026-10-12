package com.shinji.serena.mail.data

import android.net.Uri
import java.io.Serializable

/**
 * 添付ファイル情報モデル
 */
data class MailAttachment(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val size: Long
) : Serializable
