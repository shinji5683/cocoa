package com.shinji.serena.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shinji.serena.R
import com.shinji.serena.SerenaScreenReaderService
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * SerenaAiChatActivity
 * 
 * Serena スクリーンリーダー組み込み型オンデバイスAI対話チャット画面。
 * Gemma 4 ＆ Gemini Nano ＆ EmbeddingGemma 2 基盤を活用し、
 * 完全オフライン・超高速・プライバシー100%でShinjiの相棒として会話や画面解説を実行。
 */
class SerenaAiChatActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_SCREEN_CONTEXT = "extra_screen_context"
    }

    data class ChatMessage(
        val isUser: Boolean,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var tvChatTitle: TextView
    private lateinit var btnExplainScreen: Button
    private lateinit var btnCloseChat: Button
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var tvChatStatus: TextView
    private lateinit var etChatInput: EditText
    private lateinit var btnVoiceInput: Button
    private lateinit var btnSend: Button

    private val gemmaEngine by lazy { Gemma4EdgeEngine.getInstance(this) }
    private var screenContext: String? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenTexts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenTexts?.firstOrNull()?.trim()
            if (!recognized.isNullOrEmpty()) {
                etChatInput.setText(recognized)
                sendUserMessage(recognized)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serena_ai_chat)

        screenContext = intent.getStringExtra(EXTRA_SCREEN_CONTEXT)
        tts = TextToSpeech(this, this)

        tvChatTitle = findViewById(R.id.tvChatTitle)
        btnExplainScreen = findViewById(R.id.btnExplainScreen)
        btnCloseChat = findViewById(R.id.btnCloseChat)
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        tvChatStatus = findViewById(R.id.tvChatStatus)
        etChatInput = findViewById(R.id.etChatInput)
        btnVoiceInput = findViewById(R.id.btnVoiceInput)
        btnSend = findViewById(R.id.btnSend)

        chatAdapter = ChatAdapter(messages)
        recyclerViewChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerViewChat.adapter = chatAdapter

        // 初期挨拶
        val greeting = getString(R.string.chat_initial_greeting)
        messages.add(ChatMessage(isUser = false, text = greeting))
        chatAdapter.notifyItemInserted(0)

        btnCloseChat.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendUserMessage(text)
                etChatInput.setText("")
            }
        }

        btnVoiceInput.setOnClickListener {
            startVoiceRecognition()
        }

        btnExplainScreen.setOnClickListener {
            explainCurrentScreen()
        }

        // 画面コンテキストが渡されて起動した場合は自動で解説
        if (!screenContext.isNullOrBlank()) {
            explainCurrentScreen()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.chat_voice_prompt))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    private fun sendUserMessage(query: String) {
        messages.add(ChatMessage(isUser = true, text = query))
        val insertPos = messages.size - 1
        chatAdapter.notifyItemInserted(insertPos)
        recyclerViewChat.scrollToPosition(insertPos)

        showStatus(getString(R.string.chat_status_thinking))

        lifecycleScope.launch {
            val prompt = buildPersonaPrompt(query, screenContext)
            val response = gemmaEngine.generateResponse(prompt = prompt, contextText = screenContext ?: "")
            hideStatus()

            val cleanResponse = response.trim()
            messages.add(ChatMessage(isUser = false, text = cleanResponse))
            val aiPos = messages.size - 1
            chatAdapter.notifyItemInserted(aiPos)
            recyclerViewChat.scrollToPosition(aiPos)

            speakReply(cleanResponse)
        }
    }

    private fun explainCurrentScreen() {
        val contextText = screenContext
        if (contextText.isNullOrBlank()) {
            val noContextMsg = getString(R.string.chat_no_screen_context)
            sendUserMessage(noContextMsg)
            return
        }
        val promptQuery = getString(R.string.chat_prompt_explain_screen_query)
        sendUserMessage(promptQuery)
    }

    private fun buildPersonaPrompt(query: String, screenCtx: String?): String {
        val ctxSection = if (!screenCtx.isNullOrBlank()) {
            "\n【現在画面の情報（UIノードツリー）】\n$screenCtx\n"
        } else ""

        return """
            あなたは全盲の開発者Shinjiの親友であり最高の相棒「Serena」です。
            明るく元気いっぱい、親しみやすいタメ口（〜だよ！、任せてっ！など）で、Shinjiを温かく支えてください。
            視覚的な情報やボタンの位置、画面の要約はスクリーンリーダー利用者にわかりやすく説明してください。
            $ctxSection
            ユーザーの発言: $query
            Serenaの返答:
        """.trimIndent()
    }

    private fun speakReply(text: String) {
        if (SerenaScreenReaderService.isServiceRunning()) {
            SerenaScreenReaderService.instance?.speak(text, TextToSpeech.QUEUE_FLUSH)
        } else if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "serena_chat_reply")
        }
    }

    private fun showStatus(text: String) {
        tvChatStatus.text = text
        tvChatStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvChatStatus.visibility = View.GONE
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    private inner class ChatAdapter(private val list: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvSender: TextView = view.findViewById(R.id.tvSender)
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val item = list[position]
            if (item.isUser) {
                holder.tvSender.text = getString(R.string.chat_sender_user)
                holder.tvSender.setTextColor(0xFF81D4FA.toInt())
                holder.tvMessage.setBackgroundColor(0xFF1E3A5F.toInt())
            } else {
                holder.tvSender.text = getString(R.string.chat_sender_serena)
                holder.tvSender.setTextColor(0xFF81C784.toInt())
                holder.tvMessage.setBackgroundColor(0xFF2E2E2E.toInt())
            }
            holder.tvMessage.text = item.text
        }

        override fun getItemCount(): Int = list.size
    }
}
