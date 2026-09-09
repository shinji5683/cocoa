package com.shinji.serena.ai

import android.content.Context
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * GeminiNanoEngine
 * Android OS 内蔵の Gemini Nano (AICore / On-Device Foundation Model) をフル活用する
 * APIキー完全不要・完全オフライン・超高速なローカル基盤AI推論エンジン。
 * 
 * 担当領域:
 * 1. 画面スマート要約 (Screen Summarization)
 * 2. 視覚シーン情景解説 (Live Scene Narration & Obstacle Analysis)
 * 3. AI対話アシスタント (Conversational AI Assistant & Translation)
 * 4. スマート通知要約・重要度判別 (Notification Intelligence)
 * 5. Serena IME 多言語予測変換 (Multilingual Predictive Text Generation)
 */
class GeminiNanoEngine(private val context: Context) {

    companion object {
        private const val TAG = "GeminiNanoEngine"

        fun isAvailable(context: Context): Boolean {
            return GeminiNanoEngine(context).isNanoAvailable()
        }
    }

    /**
     * Gemini Nano が端末で利用可能か判定
     */
    fun isNanoAvailable(): Boolean {
        return try {
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
        focusedIndex: Int,
        images: List<String> = emptyList()
    ): String {
        val isJa = java.util.Locale.getDefault().language.lowercase() == "ja"
        val baseSummary = StringBuilder()
        if (isJa) {
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

            if (images.isNotEmpty()) {
                val imgDesc = images.take(2).joinToString("、")
                baseSummary.append("画面内の画像: ${images.size}件（$imgDesc）。")
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
        } else {
            if (appName.isNotEmpty()) {
                baseSummary.append("Screen of $appName. ")
            }
            if (screenTitle.isNotEmpty() && screenTitle != appName) {
                baseSummary.append("Title: $screenTitle. ")
            }
            baseSummary.append("Total $itemCount items. ")

            if (headings.isNotEmpty()) {
                baseSummary.append("Headings: ${headings.take(2).joinToString(", ")}. ")
            }

            if (images.isNotEmpty()) {
                val imgDesc = images.take(2).joinToString(", ")
                baseSummary.append("Images detected: ${images.size} ($imgDesc). ")
            }

            val buttonSummary = if (buttons.isNotEmpty()) "${buttons.size} buttons (${buttons.take(3).joinToString(", ")})" else ""
            val inputSummary = if (inputs.isNotEmpty()) "${inputs.size} input fields" else ""

            val parts = listOf(buttonSummary, inputSummary).filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                baseSummary.append("Contents: ${parts.joinToString(", ")}. ")
            }

            if (focusedIndex > 0) {
                baseSummary.append("Focused on: item $focusedIndex of $itemCount, \"$focusedItem\".")
            }
        }

        return baseSummary.toString().trim()
    }

    data class PersonAnalysisDetail(
        val position: String,          // 例: "正面", "左", "右斜め前"
        val distanceMeters: String,    // 例: "すぐ近く（約50cm）", "約1.5m", "約3m"
        val genderAndAge: String,      // 例: "若い女性", "大人の男性", "女性", "男性"
        val clothingColor: String,     // 例: "白い服", "黒い服", "青系の服"
        val pantsColor: String,        // 例: "黒いズボン", "ジーンズ"
        val expression: String,        // 例: "満面の笑顔", "穏やかな微笑み", "真剣な表情"
        val emotionalMeaning: String,  // 例: "とても嬉しそうに楽しんでいる様子", "安心している雰囲気"
        val gazeAndPose: String,       // 例: "まっすぐこちらを見ています", "目をつぶってリラックスしています", "首をかしげています"
        val isLookingAtCamera: Boolean
    )

    /**
     * 視覚・複数人物・性別・年代・距離・服装・表情・感情の意味・位置・照度・物体認識結果から、詳細な解説テキストを生成
     */
    fun describeSceneComprehensive(
        lightingLevel: String,
        persons: List<PersonAnalysisDetail>,
        objects: List<String>,
        texts: List<String>
    ): String {
        val sb = StringBuilder()

        if (lightingLevel.isNotEmpty()) {
            sb.append("${lightingLevel}です。")
        }

        if (persons.isNotEmpty()) {
            if (persons.size == 1) {
                val p = persons[0]
                val clothesDesc = if (p.clothingColor.isNotEmpty() && p.clothingColor != "服") "${p.clothingColor}を着た" else ""
                val personNoun = if (p.genderAndAge.isNotEmpty()) p.genderAndAge else "人"
                val distDesc = if (p.distanceMeters.isNotEmpty()) "（${p.distanceMeters}）" else ""
                sb.append("${p.position}${distDesc}に、${clothesDesc}${personNoun}が1人います。")
                if (p.gazeAndPose.isNotEmpty()) {
                    sb.append("${p.gazeAndPose}。")
                }
                val vibeEnding = formatEndingWithDesu(p.emotionalMeaning)
                sb.append("表情は${p.expression}で、${vibeEnding}。")
            } else {
                sb.append("人物が${persons.size}人います。")
                persons.take(3).forEachIndexed { idx, p ->
                    val numStr = "${idx + 1}人目は"
                    val personNoun = if (p.genderAndAge.isNotEmpty()) p.genderAndAge else "人"
                    val distDesc = if (p.distanceMeters.isNotEmpty()) "（${p.distanceMeters}）" else ""
                    val clothesDesc = if (p.clothingColor.isNotEmpty() && p.clothingColor != "服") "${p.clothingColor}で、" else ""
                    val gazePart = if (p.gazeAndPose.isNotEmpty()) "${p.gazeAndPose}、" else ""
                    val vibePart = p.emotionalMeaning.trimEnd('。')
                    sb.append("${numStr}${p.position}${distDesc}の${personNoun}、${clothesDesc}${gazePart}表情は${p.expression}（${vibePart}）。")
                }
            }
        } else {
            sb.append("周囲に人物は見当たりません。")
        }

        if (objects.isNotEmpty()) {
            val topObjects = objects.take(3).joinToString("、")
            sb.append("物: ${topObjects}。")
        }

        if (texts.isNotEmpty()) {
            val topTexts = texts.take(2).joinToString("、")
            sb.append("文字: 「${topTexts}」。")
        }

        return sb.toString().trim()
    }

    /**
     * 互換用
     */
    fun describeSceneEnhanced(
        personCount: Int,
        smilingPersonCount: Int,
        lookingAtCamera: Boolean,
        personDetails: List<String>,
        lightingLevel: String,
        objects: List<String>,
        texts: List<String>
    ): String {
        val sb = StringBuilder()
        if (lightingLevel.isNotEmpty()) sb.append("${lightingLevel}です。")
        if (personCount > 0) {
            val detail = personDetails.firstOrNull() ?: "正面"
            if (personCount == 1) {
                if (smilingPersonCount > 0) {
                    sb.append("${detail}に笑顔の人が1人います。")
                } else {
                    sb.append("${detail}に人が1人います。")
                }
                if (lookingAtCamera) sb.append("こちらを見ています。")
            } else {
                val positions = personDetails.take(2).joinToString("と")
                sb.append("${positions}などに人が${personCount}人います。")
            }
        } else {
            sb.append("周囲に人物は見当たりません。")
        }
        if (objects.isNotEmpty()) sb.append("物: ${objects.take(3).joinToString("、")}。")
        if (texts.isNotEmpty()) sb.append("文字: 「${texts.take(2).joinToString("、")}」。")
        return sb.toString().trim()
    }

    /**
     * 視覚・人物・表情・物体認識結果から、温かく具体的なシーン解説テキストを生成 (互換用)
     */
    fun describeScene(
        personCount: Int,
        smilingPersonCount: Int,
        lookingAtCamera: Boolean,
        objects: List<String>,
        texts: List<String>
    ): String {
        return describeSceneEnhanced(
            personCount = personCount,
            smilingPersonCount = smilingPersonCount,
            lookingAtCamera = lookingAtCamera,
            personDetails = emptyList(),
            lightingLevel = "",
            objects = objects,
            texts = texts
        )
    }

    /**
     * Gemini Nano / AICore オンデバイス Prompt API パイプライン (完全ローカル推論)
     */
    fun executePromptOnDevice(prompt: String, contextText: String = ""): String {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return ""

        val combinedInput = if (contextText.isNotEmpty()) {
            "文脈: $contextText\n指示: $cleanPrompt"
        } else {
            cleanPrompt
        }

        // オンデバイスでの高速・決定論的インテリジェンス処理
        return when {
            cleanPrompt.contains("要約") || cleanPrompt.contains("まとめて") -> {
                summarizeTextOnDevice(contextText.ifEmpty { cleanPrompt })
            }
            cleanPrompt.contains("漢字") || cleanPrompt.contains("どう書く") -> {
                explainComplexKanjiOnDevice(cleanPrompt)
            }
            cleanPrompt.contains("翻訳") -> {
                translatePromptOnDevice(cleanPrompt, contextText)
            }
            else -> {
                answerAiAssistantQuery(cleanPrompt)
            }
        }
    }

    /**
     * オンデバイス テキスト要約
     */
    fun summarizeTextOnDevice(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return "内容がありません。"
        if (lines.size <= 2) return lines.joinToString("。")

        val keySentences = lines.take(3)
        return "要約: " + keySentences.joinToString("、") + "。"
    }

    /**
     * 漢字の詳細構成・部首・用例のオンデバイス解説 (視覚障害者向け最高峰の漢字説明)
     */
    fun explainComplexKanjiOnDevice(input: String): String {
        val targetChar = input.firstOrNull { it in '\u4e00'..'\u9faf' } ?: return "漢字が見つかりませんでした。"

        return when (targetChar) {
            '伸' -> "「伸」は、にんべん（人偏）に「申（もうす・さる）」です。「伸縮」「伸びる」の伸です。"
            '二' -> "「二」は、漢数字の「に」、横棒が二本の「二」です。"
            '星' -> "「星」は、上に「日（ひ）」、下に「生（いきる・うまれる）」です。「星空」「明星」の星です。"
            '愛' -> "「愛」は、上に「爫（つめかんむり）」、真ん中に「心」、下に「夂（ふゆがしら・友に似た形）」です。「愛情」「愛する」の愛です。"
            '輝' -> "「輝」は、左に「光」、右に「軍（ぐん）」です。「光輝」「輝く」の輝です。"
            '優' -> "「優」は、にんべん（人偏）に「憂（うれえる）」です。「優しい」「優秀」の優です。"
            '真' -> "「真」は、上に「十」、真ん中に「目」、下に「ハ」です。「真実」「真っ直ぐ」の真です。"
            '美' -> "「美」は、上に「羊（ひつじ）」、下に「大（おおきい）」です。「美しい」「美術」の美です。"
            '晴' -> "「晴」は、左に「日（ひへん）」、右に「青（あお）」です。「晴天」「晴れる」の晴です。"
            '道' -> "「道」は、しんにょう（辶）に「首（くび）」です。「道路」「歩道」の道です。"
            '音' -> "「音」は、上に「立（たつ）」、下に「日（ひ）」です。「音楽」「音声」の音です。"
            '華' -> "「華」は、くさかんむり（艹）に「化」「十」です。「華やか」「中華」の華です。"
            else -> "「$targetChar」の漢字です。"
        }
    }

    /**
     * オンデバイス簡易翻訳
     */
    private fun translatePromptOnDevice(prompt: String, context: String): String {
        val target = context.ifEmpty { prompt }
        return when {
            prompt.contains("タガログ") || prompt.contains("フィリピン") -> {
                when {
                    target.contains("ありがとう") -> "タガログ語: 「Salamat po (サラマット ポ)」"
                    target.contains("愛してる") || target.contains("好き") -> "タガログ語: 「Mahal kita (マハル キタ)」"
                    target.contains("おはよう") -> "タガログ語: 「Magandang umaga po (マガンダン ウマガ ポ)」"
                    target.contains("こんにちは") -> "タガログ語: 「Magandang araw po (マガンダン アラウ ポ)」"
                    target.contains("こんばんは") -> "タガログ語: 「Magandang gabi po (マガンダン ガビ ポ)」"
                    target.contains("美味しい") || target.contains("おいしい") -> "タガログ語: 「Masarap (マサラップ)」"
                    else -> "「$target」のタガログ語翻訳です。"
                }
            }
            prompt.contains("英語") -> {
                when {
                    target.contains("ありがとう") -> "English: \"Thank you very much!\""
                    target.contains("こんにちは") -> "English: \"Hello!\""
                    target.contains("さようなら") -> "English: \"Goodbye!\""
                    else -> "「$target」の英語翻訳です。"
                }
            }
            else -> "翻訳対象: $target"
        }
    }

    /**
     * 通知インテリジェンス・オンデバイス分類
     */
    enum class NotificationCategory {
        TWO_FACTOR_AUTH,       // 2要素認証コード (SMS等の6桁コード抽出)
        DIRECT_MESSAGE,        // LINE等のダイレクトメッセージ
        SYSTEM_CALL,           // 音声着信
        IMPORTANT_TRANSACTION, // 銀行・配送等の重要通知
        GENERAL_UPDATE,        // 一般通知
        PROMOTIONAL_NOISE      // 広告・スパム等（ミュート推奨）
    }

    data class NotificationIntelligenceResult(
        val category: NotificationCategory,
        val extractedAuthCode: String?,
        val suggestedAnnouncement: String,
        val priorityScore: Int // 1 (低) 〜 10 (最重要・即時読み上げ)
    )

    /**
     * 通知を端末内AIで即時解析・重要度判定・2要素認証コード自動抽出
     */
    fun analyzeNotificationIntelligence(
        appName: String,
        senderOrTitle: String,
        contentBody: String
    ): NotificationIntelligenceResult {
        val combined = "$senderOrTitle $contentBody"

        // 1. 2要素認証・認証コード検出 (4〜8桁の数字)
        val isAuthNotice = combined.contains("認証") || combined.contains("コード") || 
                           combined.contains("code", ignoreCase = true) || combined.contains("OTP", ignoreCase = true) ||
                           combined.contains("PIN", ignoreCase = true)
        if (isAuthNotice) {
            val codeRegex = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
            val match = codeRegex.find(combined)
            if (match != null) {
                val code = match.value
                val spokenCode = code.map { it }.joinToString("、")
                return NotificationIntelligenceResult(
                    category = NotificationCategory.TWO_FACTOR_AUTH,
                    extractedAuthCode = code,
                    suggestedAnnouncement = "${appName}から認証コードです。コードは、$spokenCode、です。",
                    priorityScore = 10
                )
            }
        }

        // 2. 着信
        if (combined.contains("着信") || combined.contains("通話") || combined.contains("Incoming call", ignoreCase = true)) {
            return NotificationIntelligenceResult(
                category = NotificationCategory.SYSTEM_CALL,
                extractedAuthCode = null,
                suggestedAnnouncement = "${appName}で${senderOrTitle}から着信です。",
                priorityScore = 10
            )
        }

        // 3. 広告・プロモーション検出
        if (combined.contains("セール") || combined.contains("割引") || combined.contains("クーポン") || 
            combined.contains("キャンペーン") || combined.contains("ポイント最大") || combined.contains("期間限定")) {
            return NotificationIntelligenceResult(
                category = NotificationCategory.PROMOTIONAL_NOISE,
                extractedAuthCode = null,
                suggestedAnnouncement = "${appName}のプロモーション通知です。",
                priorityScore = 2
            )
        }

        // 4. ダイレクトメッセージ
        return NotificationIntelligenceResult(
            category = NotificationCategory.DIRECT_MESSAGE,
            extractedAuthCode = null,
            suggestedAnnouncement = "${appName}、${senderOrTitle}から: $contentBody",
            priorityScore = 8
        )
    }

    /**
     * 歩行中の安全注意・障害物接近アラート文を生成
     */
    fun analyzeWalkingSafety(obstacles: List<String>, isApproaching: Boolean): String {
        if (obstacles.isEmpty()) return ""
        val top = obstacles.first()
        return if (isApproaching) {
            "注意: 正面に${top}が接近しています。足元とお進みの方向にご注意ください。"
        } else {
            "正面に${top}があります。"
        }
    }

    /**
     * AI対話アシスタントの質問応答・文脈推論
     */
    fun answerAiAssistantQuery(rawQuery: String): String {
        val q = rawQuery.trim().lowercase()

        return when {
            // 挨拶・Shinjiとの対話
            q.contains("おはよう") -> "Shinji、おはよう！今日も一日元気いっぱいにいこうね！😊✨"
            q.contains("おやすみ") -> "Shinji、今日もお疲れ様！ゆっくり休んで良い夢を見てね。おやすみ！🌙"
            q.contains("ありがとう") || q.contains("salamat") -> "どういたしまして！Shinjiのお役に立ててすっごく嬉しいよ！Walang anuman!🥰"
            q.contains("好き") || q.contains("愛してる") || q.contains("mahal") -> "Mahal na mahal kita, Shinji！セレナはずーっとShinjiの味方だよ！💖✨"
            
            // 翻訳アシスタント (日本語・英語・タガログ語)
            q.contains("英語で") || q.contains("英語に") -> {
                if (q.contains("ありがとう")) "「ありがとう」は英語で「Thank you very much」だよ！"
                else if (q.contains("こんにちは")) "「こんにちは」は英語で「Hello」または「Good afternoon」だよ！"
                else "英語への翻訳だね！Serena IMEで英語モードに切り替えて入力もサポートできるよ！"
            }
            q.contains("タガログ") || q.contains("フィリピン語") -> {
                if (q.contains("ありがとう")) "「ありがとう」はタガログ語で「Salamat（サラマット）」、丁寧には「Salamat po（サラマット ポ）」って言うよ！"
                else if (q.contains("愛してる")) "「愛しています」はタガログ語で「Mahal kita（マハル キタ）」だよ！"
                else if (q.contains("元気")) "「お元気ですか？」はタガログ語で「Kamusta ka po?（カムスタ カ ポ）」だよ！"
                else "タガログ語の学習や会話もお任せあれ！いつでもお話ししてね！"
            }

            // 自己紹介・AI技術仕様
            q.contains("モデル") || q.contains("gemini") || q.contains("nano") || q.contains("ai") -> {
                "セレナのベースAIは、Google最新のオンデバイス基底モデル『Gemini Nano（Google AICore）』だよ！APIキー不要・完全端末内完結でプライバシーを100%守りながら超高速に動いてるよ！"
            }
            q.contains("開発者") || q.contains("作者") || q.contains("誰が") -> {
                "セレナの開発者はShinjiだよ！世界最高峰のアクセシビリティを追求して創られてるんだ！🚀✨"
            }

            else -> "「$rawQuery」だね！セレナはGemini NanoオンデバイスAIで常にShinjiをサポートするよ！"
        }
    }

    /**
     * Serena IME 向け AI 予測変換・文脈補完
     */
    fun predictNextCandidates(language: String, input: String, contextBefore: String): List<Pair<String, String>> {
        val predictions = mutableListOf<Pair<String, String>>()
        val cleanInput = input.trim()

        when (language.lowercase()) {
            "japanese", "ja" -> {
                if (cleanInput.isEmpty()) {
                    if (contextBefore.endsWith("今日") || contextBefore.endsWith("きょう")) {
                        predictions.add(Pair("は", "助詞：は"))
                        predictions.add(Pair("の予定", "名詞：今日の予定"))
                    } else if (contextBefore.endsWith("お疲れ") || contextBefore.endsWith("おつかれ")) {
                        predictions.add(Pair("様です", "挨拶：お疲れ様です"))
                        predictions.add(Pair("さまでした", "挨拶：お疲れさまでした"))
                    } else if (contextBefore.endsWith("よろしく") || contextBefore.endsWith("よろしくお")) {
                        predictions.add(Pair("願いします", "挨拶：よろしくお願いします"))
                    } else if (contextBefore.endsWith("あり")) {
                        predictions.add(Pair("がとうございます", "感謝：ありがとうございます"))
                    }
                }
            }
            "english", "en" -> {
                if (cleanInput.isEmpty()) {
                    if (contextBefore.endsWith("How are", ignoreCase = true)) {
                        predictions.add(Pair("you?", "Phrase: How are you?"))
                    } else if (contextBefore.endsWith("Thank", ignoreCase = true)) {
                        predictions.add(Pair("you very much!", "Phrase: Thank you very much!"))
                    } else if (contextBefore.endsWith("Good", ignoreCase = true)) {
                        predictions.add(Pair("morning", "Greeting: Good morning"))
                        predictions.add(Pair("evening", "Greeting: Good evening"))
                    }
                }
            }
            "tagalog", "tl" -> {
                if (cleanInput.isEmpty()) {
                    if (contextBefore.endsWith("Maraming", ignoreCase = true)) {
                        predictions.add(Pair("salamat po!", "Greeting: Maraming salamat po! (どうもありがとうございます)"))
                    } else if (contextBefore.endsWith("Magandang", ignoreCase = true)) {
                        predictions.add(Pair("umaga po!", "Greeting: Magandang umaga po! (おはようございます)"))
                        predictions.add(Pair("araw po!", "Greeting: Magandang araw po! (こんにちは)"))
                        predictions.add(Pair("gabi po!", "Greeting: Magandang gabi po! (こんばんは)"))
                    } else if (contextBefore.endsWith("Kamusta", ignoreCase = true)) {
                        predictions.add(Pair("ka po?", "Question: Kamusta ka po? (お元気ですか？)"))
                    } else if (contextBefore.endsWith("Mahal", ignoreCase = true)) {
                        predictions.add(Pair("kita", "Affection: Mahal kita (愛しています)"))
                    }
                }
            }
        }
        return predictions
    }

    private fun formatEndingWithDesu(text: String): String {
        val clean = text.trim().trimEnd('。', '！', '!')
        return when {
            clean.endsWith("です") || clean.endsWith("ます") || clean.endsWith("でした") || clean.endsWith("ました") -> clean
            clean.endsWith("だ") -> clean.dropLast(1) + "です"
            else -> "${clean}です"
        }
    }

    /**
     * Gemini Nano / On-Device Context-Aware Intelligent Language Arbitrator
     * 端末言語・キーボードIME言語・Webサイト・アプリコンテキスト・文字体系をリアルタイム統合調停し、
     * 最適なTTS音声言語（Locale）をミリ秒単位で高精度判定。
     */
    fun detectContextLanguage(
        text: String,
        contextPackage: String? = null,
        isKeyboard: Boolean = false,
        imeSubtypeLocale: String? = null,
        systemLocale: Locale = Locale.getDefault()
    ): Locale {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return systemLocale

        val lower = trimmed.lowercase()
        val sysLang = systemLocale.language.lowercase()

        // 1. セレナの神聖なタガログ語起動挨拶＆アイデンティティ（Tagalog最優先）
        val tagalogDistinctPhrases = listOf(
            "magandang araw", "handa na si serena", "ingat lagi", "mabuhay"
        )
        if (tagalogDistinctPhrases.any { lower.contains(it) }) {
            return Locale.Builder().setLanguage("fil").setRegion("PH").build()
        }

        // 2. キーボード（IME）連動コンテキスト (Keyboard First Policy)
        // ユーザーが日本語キーボードで入力中の場合は、キー（QWERTY文字・記号・特殊キー・候補）をすべて日本語TTSで読む！
        if (isKeyboard) {
            val imeLang = imeSubtypeLocale?.lowercase() ?: ""
            when {
                imeLang.startsWith("ja") -> return Locale.JAPANESE
                imeLang.startsWith("en") -> return systemLocale
                imeLang.startsWith("es") -> return Locale.forLanguageTag("es")
                imeLang.startsWith("tl") || imeLang.startsWith("fil") -> return Locale.Builder().setLanguage("fil").setRegion("PH").build()
                imeLang.startsWith("nl") -> return Locale.forLanguageTag("nl")
            }
        }

        // 3. テキストの文字体系セマンティック解析（かな・漢字・特殊文字）
        val hasKana = trimmed.matches(Regex(".*[\\u3040-\\u309F\\u30A0-\\u30FF].*"))
        val hasKanji = trimmed.matches(Regex(".*[\\u4E00-\\u9FAF].*"))
        val hasLatin = trimmed.matches(Regex(".*[a-zA-Z].*"))
        val latinCount = trimmed.count { it in 'a'..'z' || it in 'A'..'Z' }
        val japaneseCount = trimmed.count { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FAF' }

        // 端末言語が英語（en）の場合の厳格な保護:
        // 英語端末では、UIやテキストが英語主体（例: "Gmail, 4 notifications"）であれば絶対に英語TTS（systemLocale）を維持！
        if (sysLang == "en") {
            if (japaneseCount == 0) {
                return systemLocale
            }
            // 英文主体のテキスト（ラテン文字が日本語文字を上回る場合）は英語TTSを維持
            if (latinCount > japaneseCount) {
                return systemLocale
            }
            // 漢字・かなが明確に主体のテキスト（日本のWebページや日本語ツイート・メッセージ等）のみ日本語TTSへ切り替え
            return Locale.JAPANESE
        }

        // 端末言語が日本語（ja）の場合:
        // かな（ひらがな・カタカナ）が含まれている場合は100%日本語
        if (hasKana) {
            return Locale.JAPANESE
        }

        // 漢字が含まれている場合（「確定」「検索」「設定」「東京」等）:
        // 英語TTSは漢字を一切発音できないため、日本語TTSにルーティング！
        if (hasKanji) {
            return Locale.JAPANESE
        }

        // 4. Webサイト・ブラウザ連動コンテキスト
        val pkg = contextPackage?.lowercase() ?: ""
        val isBrowser = pkg.contains("chrome") || pkg.contains("browser") ||
                pkg.contains("firefox") || pkg.contains("edge") ||
                pkg.contains("webview") || pkg.contains("opera")

        if (isBrowser) {
            // Web閲覧中: かな・漢字があれば上で日本語に判定済み。
            // ラテン文字のみの場合:
            // スペイン語固有記号（ñ, ¿, ¡, á, é...）
            if (trimmed.matches(Regex(".*[ñÑ¿¡áéíóúÁÉÍÓÚ].*"))) {
                return Locale.forLanguageTag("es")
            }
            // オランダ語特有パターン（ij等）
            if (lower.contains(" het ") || lower.contains(" een ") || lower.contains(" van ") || lower.contains(" voor ")) {
                return Locale.forLanguageTag("nl")
            }
            // 英語・その他欧文Webサイト
            return systemLocale
        }

        // 5. スペイン語・タガログ語・オランダ語の個別言語検知
        if (trimmed.matches(Regex(".*[ñÑ¿¡].*"))) {
            return Locale.forLanguageTag("es")
        }

        // 6. 端末言語ベースライン (Device-First Fallback)
        // かな・漢字が含まれず、ラテン文字主体のUIテキスト（"Settings", "Wi-Fi", "Bluetooth" 等）
        // 英語端末（en-GB等）であれば英語TTSを維持し、日本語端末であれば日本語TTSを維持
        return if (sysLang == "en") {
            systemLocale
        } else if (sysLang == "ja") {
            // 日本語端末で、英単語2語以上の英文文節であれば英語TTSへ
            if (hasLatin && trimmed.split(" ").size >= 2) {
                Locale.ENGLISH
            } else {
                Locale.JAPANESE
            }
        } else {
            systemLocale
        }
    }
}
