package com.shinji.serena

import android.view.accessibility.AccessibilityNodeInfo

/**
 * AiAutoLabelHelper
 * 名前のないボタン（テキストやcontentDescriptionが未設定のボタン/画像）に対して、
 * ビューリソースIDや周辺コンテキストから自動で高精度な名前を推論・自動ラベリング！
 */
class AiAutoLabelHelper {

    companion object {
        private val ID_LABEL_PATTERNS = listOf(
            Regex(".*(search|find|query|magnif).*") to "検索",
            Regex(".*(setting|config|pref|gear).*") to "設定",
            Regex(".*(close|dismiss|cancel|cross|delete|clear).*") to "閉じる",
            Regex(".*(back|nav_back|prev|arrow_back|arrow_left).*") to "戻る",
            Regex(".*(forward|next|arrow_forward|arrow_right).*") to "次へ",
            Regex(".*(menu|drawer|hamburger|more_vert|overflow).*") to "メニュー",
            Regex(".*(send|submit|post|upload).*") to "送信",
            Regex(".*(share|export).*") to "共有",
            Regex(".*(notif|bell|alert).*") to "通知",
            Regex(".*(mic|voice|audio_record).*") to "マイク・音声入力",
            Regex(".*(camera|photo|lens|snapshot).*") to "カメラ・写真撮影",
            Regex(".*(play|resume).*") to "再生",
            Regex(".*(pause|stop).*") to "一時停止",
            Regex(".*(favorite|like|heart|star).*") to "お気に入り",
            Regex(".*(profile|account|user|avatar).*") to "アカウント・プロフィール",
            Regex(".*(home|main_tab).*") to "ホーム",
            Regex(".*(cart|bag|shop).*") to "ショッピングカート",
            Regex(".*(refresh|reload|sync|update).*") to "更新・再読み込み",
            Regex(".*(add|create|new|plus).*") to "新規追加",
            Regex(".*(edit|modify|pencil).*") to "編集",
            Regex(".*(filter|sort).*") to "絞り込み・フィルター",
            Regex(".*(download|save).*") to "ダウンロード・保存",
            Regex(".*(help|info|question).*") to "ヘルプ・情報"
        )
    }

    /**
     * ノードにラベルがない場合、AI/ヒューリスティック推論で自然な名前を生成
     */
    fun inferLabelForUnlabeledNode(node: AccessibilityNodeInfo): String? {
        val className = node.className?.toString() ?: ""
        val isClickable = node.isClickable
        val isButtonLike = isClickable ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("ImageView", ignoreCase = true) ||
                className.contains("Image", ignoreCase = true)

        if (!isButtonLike) return null

        val resId = node.viewIdResourceName?.lowercase() ?: ""
        if (resId.isNotEmpty()) {
            val idSimple = resId.substringAfterLast(":id/").substringAfterLast("/")
            for ((pattern, label) in ID_LABEL_PATTERNS) {
                if (pattern.matches(idSimple)) {
                    return "ボタン（AI自動判定: $label）"
                }
            }
        }

        // 親や子ノードの近接テキストから推論
        val parent = node.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling != node && !sibling.isClickable) {
                    val siblingText = sibling.text?.toString()?.trim()
                    if (!siblingText.isNullOrEmpty() && siblingText.length in 1..15) {
                        return "ボタン（AI自動判定: $siblingText）"
                    }
                }
            }
        }

        return "名前のないボタン"
    }
}
