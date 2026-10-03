package com.shinji.serena.mail.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma4MailEngine
 * 
 * Serena Mail 向け Gemma 4 オンデバイス基盤AIエンジン。
 * メール本文のスマート要約およびAI返信文作成を完全ローカル・プライバシー100%保護で実行。
 */
class Gemma4MailEngine(private val context: Context) {

    companion object {
        private const val TAG = "Gemma4MailEngine"

        @Volatile
        private var instance: Gemma4MailEngine? = null

        fun getInstance(context: Context): Gemma4MailEngine {
            return instance ?: synchronized(this) {
                instance ?: Gemma4MailEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private var llmInference: LlmInference? = null
    private var isInitializing = false

    private fun findModelFile(): File? {
        val candidateNames = listOf(
            "gemma-4-e2b.task",
            "gemma-4-e2b.bin",
            "gemma-4.task",
            "gemma.task",
            "gemma-2b-it-gpu-int4.bin"
        )

        // 1. アプリ専用内部ストレージ
        val internalDir = File(context.filesDir, "models")
        if (internalDir.exists() && internalDir.isDirectory) {
            for (name in candidateNames) {
                val f = File(internalDir, name)
                if (f.exists() && f.length() > 1024 * 1024) return f
            }
        }

        // 2. 外部ストレージ Downloads
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                for (name in candidateNames) {
                    val f = File(downloadDir, name)
                    if (f.exists() && f.length() > 1024 * 1024) return f
                }
            }
        } catch (_: Exception) {}

        return null
    }

    @Synchronized
    private fun initEngine(): Boolean {
        if (llmInference != null) return true
        if (isInitializing) return false

        val modelFile = findModelFile() ?: return false
        isInitializing = true
        return try {
            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)

            try {
                optionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)
            } catch (_: Throwable) {
                optionsBuilder.setPreferredBackend(LlmInference.Backend.CPU)
            }

            llmInference = LlmInference.createFromOptions(context, optionsBuilder.build())
            Log.i(TAG, "Gemma 4 Mail Engine initialized successfully!")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Gemma 4: ${e.message}")
            llmInference = null
            false
        } finally {
            isInitializing = false
        }
    }

    /**
     * メールのスマート要約（全盲ユーザーが長文メールを一瞬で把握できる2〜3文要約）
     */
    suspend fun summarizeEmail(
        sender: String,
        subject: String,
        body: String
    ): String = withContext(Dispatchers.Default) {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty()) return@withContext "本文が空です。"

        val isJa = java.util.Locale.getDefault().language.lowercase() == "ja"

        val prompt = if (isJa) {
            """
            全盲のユーザー向けに、以下のメールの要点を2〜3文で簡潔かつ分かりやすく要約してください。
            件名: $subject
            送信者: $sender
            本文:
            ${cleanBody.take(1500)}
            """.trimIndent()
        } else {
            """
            Summarize the key points of this email for a visually impaired user in 2-3 concise sentences:
            Subject: $subject
            Sender: $sender
            Body:
            ${cleanBody.take(1500)}
            """.trimIndent()
        }

        if (initEngine()) {
            llmInference?.let { engine ->
                try {
                    val fullInput = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
                    val response = engine.generateResponse(fullInput).trim()
                    if (response.isNotEmpty()) return@withContext response
                } catch (e: Throwable) {
                    Log.w(TAG, "Gemma inference failed: ${e.message}")
                }
            }
        }

        // フォールバック: ルールベース要約
        heuristicSummarize(cleanBody)
    }

    /**
     * AI返信文作成（承諾・お礼・丁寧な確認）
     */
    suspend fun generateReplyDraft(
        sender: String,
        subject: String,
        body: String,
        intent: String = "了解とお礼"
    ): String = withContext(Dispatchers.Default) {
        val isJa = java.util.Locale.getDefault().language.lowercase() == "ja"

        val prompt = if (isJa) {
            """
            以下のメールに対する「$intent」を伝える丁寧な返信メール本文を作成してください。
            差出人名（自分）は「Shinji」です。
            相手の件名: $subject
            相手の本文:
            ${body.take(1000)}
            """.trimIndent()
        } else {
            """
            Draft a polite and clear email reply expressing "$intent".
            My name is "Shinji".
            Original Subject: $subject
            Original Body:
            ${body.take(1000)}
            """.trimIndent()
        }

        if (initEngine()) {
            llmInference?.let { engine ->
                try {
                    val fullInput = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
                    val response = engine.generateResponse(fullInput).trim()
                    if (response.isNotEmpty()) return@withContext response
                } catch (e: Throwable) {
                    Log.w(TAG, "Gemma reply generation failed: ${e.message}")
                }
            }
        }

        // フォールバック返信文
        if (isJa) {
            "ご連絡いただきありがとうございます。\n内容を確認いたしました。\n今後ともよろしくお願いいたします。\n\nShinji"
        } else {
            "Thank you for reaching out.\nI have reviewed the details and will follow up accordingly.\n\nBest regards,\nShinji"
        }
    }

    private fun heuristicSummarize(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.length > 5 }
        if (lines.isEmpty()) return text.take(100)
        return lines.take(3).joinToString("。 ")
    }
}
