package com.shinji.serena.mail.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shinji.serena.mail.R
import com.shinji.serena.mail.data.MailMessage
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * MailAdapter
 * スクリーンリーダー（TalkBack / Serena）に完全特化したメール一覧アダプター。
 */
class MailAdapter(
    private val onItemClick: (MailMessage) -> Unit
) : RecyclerView.Adapter<MailAdapter.MailViewHolder>() {

    private val messages = mutableListOf<MailMessage>()
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    fun submitList(newMessages: List<MailMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mail, parent, false)
        return MailViewHolder(view)
    }

    override fun onBindViewHolder(holder: MailViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUnreadBadge: TextView = itemView.findViewById(R.id.tvUnreadBadge)
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvSubject: TextView = itemView.findViewById(R.id.tvSubject)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvSnippet)

        fun bind(mail: MailMessage) {
            val context = itemView.context
            val unreadText = if (mail.isUnread) {
                context.getString(R.string.mail_unread_indicator)
            } else {
                context.getString(R.string.mail_read_indicator)
            }

            tvUnreadBadge.visibility = if (mail.isUnread) View.VISIBLE else View.GONE
            tvSender.text = mail.sender
            tvSubject.text = mail.subject
            tvSnippet.text = mail.snippet

            val dateStr = if (mail.receivedDate != null) dateFormat.format(mail.receivedDate) else ""
            tvDate.text = dateStr

            // スクリーンリーダー向け完全アクセシビリティ読み上げラベル
            val a11yDesc = context.getString(
                R.string.mail_item_a11y_fmt,
                unreadText,
                mail.sender,
                mail.subject,
                dateStr
            )
            itemView.contentDescription = a11yDesc

            itemView.setOnClickListener {
                onItemClick(mail)
            }
        }
    }
}
