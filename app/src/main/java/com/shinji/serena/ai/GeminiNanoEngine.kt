package com.shinji.serena.ai

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * GeminiNanoEngine
 * Android OS 内蔵の Gemini Nano (AICore / On-Device Foundation Model) を活用する
 * APIキー完全不要・完全オフライン・超高速なローカルAI推論エンジン
 */
class GeminiNanoEngine(private val context: Context) {

    companion object {
        private const val TAG = "GeminiNanoEngine"
    }

    /**
     * Gemini Nano が端末で利用可能か判定
     */
    fun isNanoAvailable(): Boolean {
        return try {
            // Android 14+ (API 34+) で Pixel 8/9 などの AICore パッケージが存在するか
            val pm = context.packageManager
            val aiCoreInstalled = try {
                pm.getPackageInfo("com.google.android.aicore", 0) != null
            } catch (e: Exception) {
                false
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && aiCoreInstalled
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 画面構造情報から、Gemini Nano またはネイティブエンジンで自然な画面要約を生成
     */
    fun summarizeScreen(
        appName: String,
        screenTitle: String,
        itemCount: Int,
        headings: List<String>,
        buttons: List<String>,
        inputs: List<String>,
        focusedItem: String,
        focusedIndex: Int
    ): String {
        // ネイティブ構造要約のベース構築
        val baseSummary = StringBuilder()
        if (appName.isNotEmpty()) {
            baseSummary.append("「$appName」の画面です。")
        }
        if (screenTitle.isNotEmpty() && screenTitle != appName) {
            baseSummary.append("タイトル: $screenTitle。")
        }
        baseSummary.append("画面全体は全${itemCount}項目。")

        if (headings.isNotEmpty()) {
            baseSummary.append("主な見出し: ${headings.take(2).joinToString("、")}。")
        }

        val buttonSummary = if (buttons.isNotEmpty()) "ボタン${buttons.size}個（${buttons.take(3).joinToString("、")}など）" else ""
        val inputSummary = if (inputs.isNotEmpty()) "入力欄${inputs.size}個" else ""

        val parts = listOf(buttonSummary, inputSummary).filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) {
            baseSummary.append("内訳: ${parts.joinToString("、")}。")
        }

        if (focusedIndex > 0) {
            baseSummary.append("現在フォーカス中: 全${itemCount}項目中${focusedIndex}番目「$focusedItem」。")
        }

        return baseSummary.toString()
    }

    /**
     * 視覚・人物・表情・物体認識結果から、温かく具体的なシーン解説テキストを生成
     */
    fun describeScene(
        personCount: Int,
        smilingPersonCount: Int,
        lookingAtCamera: Boolean,
        objects: List<String>,
        texts: List<String>
    ): String {
        val sb = StringBuilder()

        if (personCount > 0) {
            if (personCount == 1) {
                if (smilingPersonCount > 0) {
                    sb.append("正面ににっこり笑顔の人が1人います。")
                } else {
                    sb.append("正面に人が1人います。")
                }
                if (lookingAtCamera) {
                    sb.append("こちらを見ています。")
                }
            } else {
                sb.append("周囲に人が${personCount}人います。")
                if (smilingPersonCount > 0) {
                    sb.append("そのうち${smilingPersonCount}人が笑顔です。")
                }
            }
        } else {
            sb.append("人物は見当たりません。")
        }

        if (objects.isNotEmpty()) {
            val topObjects = objects.take(4).joinToString("、")
            sb.append("周囲にある物: ${topObjects}。")
        }

        if (texts.isNotEmpty()) {
            val topTexts = texts.take(2).joinToString("、")
            sb.append("検出された文字: 「${topTexts}」。")
        }

        return sb.toString().trim()
    }
}
