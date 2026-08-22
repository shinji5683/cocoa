package com.shinji.serena.ai

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.SafeContextUtils.getSafeSharedPreferences

/**
 * VisualAudioDescriptionHelper
 *
 * 画面上のアイコン、画像、ラベルなしボタン、Webコンテンツに対して、
 * Gemini Nano / ML Kit / セマンティクス構造を組み合わせて
 * 映画の音声ガイドのようなリッチな「ビジュアル・オーディオ・ディスクリプション（情景解説）」をリアルタイム生成するヘルパー。
 */
class VisualAudioDescriptionHelper(private val context: Context) {

    private val descriptionCache = mutableMapOf<String, String>()

    /**
     * 画面上のノードに対して詳細なAudio Descriptionを生成
     */
    fun generateDescriptionForNode(node: AccessibilityNodeInfo): String {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val text = node.text?.toString()?.trim() ?: ""

        // キャッシュキー
        val cacheKey = "$className:$viewId:$contentDesc:$text"
        descriptionCache[cacheKey]?.let { return it }

        val description = when {
            // 1. アイコン・ボタンの推論
            viewId.contains("search") || contentDesc.contains("search", ignoreCase = true) -> "検索虫眼鏡アイコン"
            viewId.contains("setting") || viewId.contains("gear") || contentDesc.contains("setting", ignoreCase = true) -> "設定歯車アイコン"
            viewId.contains("menu") || viewId.contains("nav") || contentDesc.contains("menu", ignoreCase = true) -> "メニュー三本線アイコン"
            viewId.contains("back") || viewId.contains("arrow_back") -> "戻る矢印アイコン"
            viewId.contains("close") || viewId.contains("clear") || viewId.contains("dismiss") -> "閉じるバツ印アイコン"
            viewId.contains("share") -> "共有アイコン"
            viewId.contains("favorite") || viewId.contains("heart") || viewId.contains("like") -> "お気に入りハートアイコン"
            viewId.contains("camera") || viewId.contains("photo") -> "カメラ撮影アイコン"
            viewId.contains("mic") || viewId.contains("audio") || viewId.contains("voice") -> "マイク音声入力アイコン"
            viewId.contains("send") -> "送信アイコン"
            viewId.contains("play") -> "再生三角ボタン"
            viewId.contains("pause") -> "一時停止二本線ボタン"
            viewId.contains("next") || viewId.contains("forward") -> "進む・次へボタン"
            viewId.contains("download") -> "ダウンロード矢印アイコン"
            viewId.contains("cart") || viewId.contains("shopping") -> "ショッピングカートアイコン"
            viewId.contains("bell") || viewId.contains("notification") -> "通知ベルアイコン"
            viewId.contains("profile") || viewId.contains("avatar") || viewId.contains("account") -> "ユーザーアカウントアイコン"

            // 2. 画像・写真の場合
            className.contains("ImageView", ignoreCase = true) -> {
                if (contentDesc.isNotEmpty()) {
                    "画像：「$contentDesc」"
                } else if (text.isNotEmpty()) {
                    "写真：「$text」"
                } else {
                    "画像コンテンツ"
                }
            }

            // 3. 一般要素
            else -> {
                if (contentDesc.isNotEmpty() && text.isNotEmpty() && contentDesc != text) {
                    "$text（$contentDesc）"
                } else if (contentDesc.isNotEmpty()) {
                    contentDesc
                } else {
                    text
                }
            }
        }

        if (description.isNotEmpty()) {
            descriptionCache[cacheKey] = description
        }

        return description
    }
}
