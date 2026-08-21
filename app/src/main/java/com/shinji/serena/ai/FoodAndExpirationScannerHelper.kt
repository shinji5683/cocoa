package com.shinji.serena.ai

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * FoodAndExpirationScannerHelper
 * 完全オンデバイス・オフラインで動作する
 * 食品・賞味期限・消費期限・お買い物バーコード・書類スキャナーエンジン。
 */
object FoodAndExpirationScannerHelper {

    data class ExpirationResult(
        val dateString: String,       // 例: "2026年9月15日"
        val labelType: String,        // "賞味期限" または "消費期限"
        val remainingDays: Int?,      // 残り日数（負の値は期限切れ）
        val spokenMessage: String     // 読み上げ用メッセージ
    )

    data class FoodAnalysisResult(
        val category: String,         // 例: "調味料", "飲料", "レトルト食品", "お菓子・スナック", "缶詰"
        val estimatedItemName: String,// 例: "しょうゆ", "緑茶ペットボトル", "カレー", "ポテトチップス"
        val expiration: ExpirationResult?,
        val detectedTexts: List<String>
    )

    data class DocumentSummaryResult(
        val docType: String,          // 例: "レシート", "請求書・納付書", "郵便物・書類"
        val totalAmount: String?,     // 例: "1,280円"
        val dueDate: String?,         // 例: "2026年8月31日"
        val summarySpokenText: String
    )

    // 日付正規表現パターン
    private val DATE_PATTERNS = listOf(
        // パターン1: 2026年9月15日 / 2026.09.15 / 2026/09/15 / 2026-09-15
        Pattern.compile("(?i)(賞味期限|消費期限|賞味|消費|期限)?[:：\\s]*([2-3][0-9]{3})[年./\\-](0?[1-9]|1[0-2])[月./\\-](0?[1-9]|[12][0-9]|3[01])[日]?"),
        // パターン2: 26.09.15 / 26/09/15 (西暦下2桁)
        Pattern.compile("(?i)(賞味期限|消費期限|賞味|消費|期限)?[:：\\s]*([2-3][0-9])[./\\-](0?[1-9]|1[0-2])[./\\-](0?[1-9]|[12][0-9]|3[01])"),
        // パターン3: 9月15日
        Pattern.compile("(?i)(賞味期限|消費期限|賞味|消費|期限)?[:：\\s]*(0?[1-9]|1[0-2])月(0?[1-9]|[12][0-9]|3[01])日")
    )

    // 食品カテゴリとキーワード定義
    private val FOOD_KEYWORDS = mapOf(
        "調味料" to listOf(
            "しょうゆ", "醤油", "みそ", "味噌", "マヨネーズ", "ケチャップ", "ソース", "ドレッシング",
            "油", "オリーブオイル", "ごま油", "みりん", "料理酒", "酢", "ポン酢", "塩", "コショウ", "砂糖", "つゆ", "めんつゆ"
        ),
        "飲料" to listOf(
            "お茶", "緑茶", "麦茶", "烏龍茶", "ほうじ茶", "紅茶", "コーヒー", "珈琲", "水", "天然水",
            "牛乳", "豆乳", "ジュース", "炭酸", "コーラ", "サイダー", "スポーツドリンク", "ポカリスエット", "アクエリアス"
        ),
        "レトルト・主食" to listOf(
            "カレー", "カリー", "シチュー", "パスタ", "スパゲッティ", "ラーメン", "うどん", "そば",
            "ごはん", "白米", "パックご飯", "食パン", "パン", "カップ麺", "やきそば", "中華丼", "牛丼"
        ),
        "お菓子・スナック" to listOf(
            "ポテトチップス", "チョコ", "チョコレート", "クッキー", "ビスケット", "せんべい", "煎餅",
            "あめ", "キャンディ", "グミ", "アイス", "プリン", "ゼリー", "ケーキ", "スナック"
        ),
        "缶詰・保存食" to listOf(
            "ツナ", "シーチキン", "サバ", "さば水煮", "さば味噌煮", "いわし", "さんま", "トマト缶",
            "コーン", "フルーツ缶", "みかん缶", "パイン缶", "焼鳥"
        ),
        "生鮮・日配品" to listOf(
            "納豆", "豆腐", "たまご", "卵", "ヨーグルト", "チーズ", "バター", "ハム", "ソーセージ",
            "ウインナー", "ベーコン", "ちくわ", "かまぼこ"
        )
    )

    /**
     * OCRテキストから賞味期限・消費期限を解析抽出
     */
    fun extractExpirationDate(ocrText: String): ExpirationResult? {
        val cleanText = ocrText.replace(" ", "").replace("　", "")
        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)

        for (pattern in DATE_PATTERNS) {
            val matcher = pattern.matcher(cleanText)
            if (matcher.find()) {
                val prefix = matcher.group(1) ?: ""
                val label = if (prefix.contains("消費")) "消費期限" else "賞味期限"

                val year: Int
                val month: Int
                val day: Int

                if (matcher.groupCount() >= 4 && matcher.group(4) != null) {
                    val rawYear = matcher.group(2)?.toIntOrNull() ?: currentYear
                    year = if (rawYear < 100) 2000 + rawYear else rawYear
                    month = matcher.group(3)?.toIntOrNull() ?: 1
                    day = matcher.group(4)?.toIntOrNull() ?: 1
                } else if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                    // 月日のみの場合は今年
                    year = currentYear
                    month = matcher.group(2)?.toIntOrNull() ?: 1
                    day = matcher.group(3)?.toIntOrNull() ?: 1
                } else {
                    continue
                }

                // 日付の妥当性チェック
                if (month in 1..12 && day in 1..31) {
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month - 1)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val todayCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
                    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

                    val dateStr = "${year}年${month}月${day}日"
                    val spoken = when {
                        diffDays > 0 -> "$label は $dateStr です。残り、あと${diffDays}日です。"
                        diffDays == 0 -> "$label は本日、${dateStr}です！お早めにお召し上がりください。"
                        else -> "注意！ $label の $dateStr を ${-diffDays}日 過ぎています！"
                    }

                    return ExpirationResult(
                        dateString = dateStr,
                        labelType = label,
                        remainingDays = diffDays,
                        spokenMessage = spoken
                    )
                }
            }
        }
        return null
    }

    /**
     * OCRテキストから食品の種類・品名をオンデバイス高速推定
     */
    fun analyzeFoodItem(ocrText: String): FoodAnalysisResult? {
        if (ocrText.isBlank()) return null
        val expiration = extractExpirationDate(ocrText)

        var matchedCategory = "食品"
        var matchedItem = ""

        for ((category, keywords) in FOOD_KEYWORDS) {
            for (kw in keywords) {
                if (ocrText.contains(kw, ignoreCase = true)) {
                    matchedCategory = category
                    matchedItem = kw
                    break
                }
            }
            if (matchedItem.isNotEmpty()) break
        }

        if (matchedItem.isEmpty() && expiration == null) {
            return null
        }

        val itemName = if (matchedItem.isNotEmpty()) matchedItem else "食品パッケージ"
        return FoodAnalysisResult(
            category = matchedCategory,
            estimatedItemName = itemName,
            expiration = expiration,
            detectedTexts = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        )
    }

    /**
     * レシートや請求書・郵便物のテキストから合計金額や期日を要約
     */
    fun summarizeDocument(ocrText: String): DocumentSummaryResult {
        val cleanText = ocrText.replace(" ", "").replace("　", "")
        
        // 1. 金額の抽出（合計、請求額、お買上額など）
        val amountPattern = Pattern.compile("(?i)(合計|小計|お買上|ご請求|請求金額|領収金額)[\\s:：¥￥]*([0-9,]{3,9})円?")
        val amountMatcher = amountPattern.matcher(cleanText)
        val totalAmount = if (amountMatcher.find()) {
            val amt = amountMatcher.group(2)
            if (amt != null) amt + "円" else null
        } else {
            val generalYen = Pattern.compile("([0-9,]{3,9})円").matcher(cleanText)
            if (generalYen.find()) {
                val amt = generalYen.group(1)
                if (amt != null) amt + "円" else null
            } else null
        }

        // 2. 期限・期日の抽出
        val expiration = extractExpirationDate(ocrText)
        val dueDate = expiration?.dateString

        val isReceipt = cleanText.contains("レシート") || cleanText.contains("領収書") || cleanText.contains("買上") || cleanText.contains("お釣り")
        val isBill = cleanText.contains("請求") || cleanText.contains("納付") || cleanText.contains("期日") || cleanText.contains("払込")

        val docType = when {
            isReceipt -> "レシート"
            isBill -> "請求書または納付書"
            else -> "書類または郵便物"
        }

        val sb = StringBuilder("$docType を検出しました。")
        if (totalAmount != null) {
            sb.append("金額は $totalAmount です。")
        }
        if (dueDate != null) {
            sb.append("期限は $dueDate です。")
        }

        return DocumentSummaryResult(
            docType = docType,
            totalAmount = totalAmount,
            dueDate = dueDate,
            summarySpokenText = sb.toString()
        )
    }
}
