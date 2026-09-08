package com.shinji.serena.ai

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * SelfHealingA11yEngine (AI自動ラベル付け＆UI自己修復エンジン)
 * 
 * アクセシビリティ非対応アプリの「説明なしボタン」「ラベルなし画像」を、
 * 幾何学的配置（左上/右上/右下/入力欄隣）、viewIdリソース名、階層構造から
 * 瞬時に推論して自然な日本語/英語ラベルを付与する自己修復エンジン。
 */
class SelfHealingA11yEngine(private val context: Context) {

    private val isJapanese: Boolean
        get() = Locale.getDefault().language.lowercase() == "ja"

    /**
     * 未ラベルまたは不完全なノードを解析し、自己修復されたラベルを返す。
     * ラベルが存在する場合は空文字を返す。
     */
    fun repairUnlabeledNode(node: AccessibilityNodeInfo?, screenWidth: Int = 1080, screenHeight: Int = 2400): String {
        if (node == null) return ""

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
            return "" // 既にラベルがある場合はスキップ
        }

        val className = node.className?.toString() ?: ""
        val isActionable = node.isClickable || node.isLongClickable || className.contains("Button", ignoreCase = true)
        val isImage = className.contains("ImageView", ignoreCase = true) || className.contains("Image", ignoreCase = true)

        if (!isActionable && !isImage) return ""

        // 1. viewIdリソース名からの精密自然言語推論
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val fromId = inferFromViewId(viewId, isActionable)
        if (fromId.isNotEmpty()) return fromId

        // 2. 幾何学的配置（スクリーン上の物理座標）による推論
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val fromGeometry = inferFromScreenGeometry(bounds, screenWidth, screenHeight, isActionable)
        if (fromGeometry.isNotEmpty()) return fromGeometry

        // 3. 親ノードおよび兄弟ノードのコンテキスト結合
        val fromContext = inferFromSurroundingContext(node, isActionable)
        if (fromContext.isNotEmpty()) return fromContext

        // 4. 最終フォールバック（絶対に「ボタン（説明なし）」「ラベルなし」とは言わせない！）
        return when {
            isActionable -> if (isJapanese) "操作ボタン" else "Action button"
            isImage -> if (isJapanese) "画像アイコン" else "Image icon"
            else -> ""
        }
    }

    private fun inferFromViewId(viewId: String, isActionable: Boolean): String {
        if (viewId.isEmpty()) return ""

        val ja = isJapanese
        val inferred = when {
            viewId.contains("search") || viewId.contains("find") -> if (ja) "検索" else "Search"
            viewId.contains("setting") || viewId.contains("gear") || viewId.contains("config") -> if (ja) "設定" else "Settings"
            viewId.contains("menu") || viewId.contains("nav_drawer") || viewId.contains("hamburger") -> if (ja) "メニュー" else "Menu"
            viewId.contains("back") || viewId.contains("up") || viewId.contains("arrow_left") -> if (ja) "戻る" else "Back"
            viewId.contains("close") || viewId.contains("dismiss") || viewId.contains("cancel") || viewId.contains("clear") -> if (ja) "閉じる" else "Close"
            viewId.contains("send") || viewId.contains("submit") || viewId.contains("post") -> if (ja) "送信" else "Send"
            viewId.contains("confirm") || viewId.contains("ok") || viewId.contains("apply") -> if (ja) "決定" else "Confirm"
            viewId.contains("next") || viewId.contains("forward") -> if (ja) "次へ" else "Next"
            viewId.contains("prev") || viewId.contains("previous") -> if (ja) "前へ" else "Previous"
            viewId.contains("home") -> if (ja) "ホーム" else "Home"
            viewId.contains("refresh") || viewId.contains("reload") || viewId.contains("sync") -> if (ja) "更新" else "Refresh"
            viewId.contains("share") -> if (ja) "共有" else "Share"
            viewId.contains("filter") -> if (ja) "フィルター" else "Filter"
            viewId.contains("sort") -> if (ja) "並び替え" else "Sort"
            viewId.contains("favorite") || viewId.contains("like") || viewId.contains("star") || viewId.contains("heart") -> if (ja) "お気に入り" else "Favorite"
            viewId.contains("bookmark") -> if (ja) "ブックマーク" else "Bookmark"
            viewId.contains("cart") || viewId.contains("bag") || viewId.contains("basket") -> if (ja) "カート" else "Cart"
            viewId.contains("notification") || viewId.contains("bell") || viewId.contains("alert") -> if (ja) "お知らせ" else "Notification"
            viewId.contains("profile") || viewId.contains("avatar") || viewId.contains("account") || viewId.contains("user") -> if (ja) "プロフィール" else "Profile"
            viewId.contains("edit") || viewId.contains("pencil") -> if (ja) "編集" else "Edit"
            viewId.contains("delete") || viewId.contains("trash") || viewId.contains("remove") -> if (ja) "削除" else "Delete"
            viewId.contains("add") || viewId.contains("plus") || viewId.contains("create") || viewId.contains("fab") -> if (ja) "追加" else "Add"
            viewId.contains("mic") || viewId.contains("voice") || viewId.contains("audio_input") -> if (ja) "音声入力" else "Voice input"
            viewId.contains("camera") || viewId.contains("shutter") -> if (ja) "カメラ撮影" else "Camera"
            viewId.contains("play") -> if (ja) "再生" else "Play"
            viewId.contains("pause") -> if (ja) "一時停止" else "Pause"
            viewId.contains("download") -> if (ja) "ダウンロード" else "Download"
            viewId.contains("upload") -> if (ja) "アップロード" else "Upload"
            viewId.contains("help") || viewId.contains("info") || viewId.contains("faq") -> if (ja) "ヘルプ" else "Help"
            viewId.contains("copy") -> if (ja) "コピー" else "Copy"
            viewId.contains("paste") -> if (ja) "貼り付け" else "Paste"
            viewId.contains("overflow") || viewId.contains("more") || viewId.contains("dots") -> if (ja) "その他オプション" else "More options"
            else -> ""
        }

        return if (inferred.isNotEmpty()) {
            if (isActionable) {
                if (ja) "${inferred}ボタン" else "$inferred button"
            } else {
                if (ja) "${inferred}アイコン" else "$inferred icon"
            }
        } else ""
    }

    private fun inferFromScreenGeometry(bounds: Rect, screenWidth: Int, screenHeight: Int, isActionable: Boolean): String {
        if (!isActionable || bounds.isEmpty) return ""

        val ja = isJapanese
        val topZone = screenHeight * 0.12f // 画面上部 12%
        val bottomZone = screenHeight * 0.88f // 画面下部 12%
        val leftZone = screenWidth * 0.20f // 画面左端 20%
        val rightZone = screenWidth * 0.80f // 画面右端 20%

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // 1. 画面左上端のクリック可能要素 ➔ 「戻るボタン」または「メニュー」
        if (centerY < topZone && centerX < leftZone) {
            return if (ja) "戻るボタン" else "Back button"
        }

        // 2. 画面右上端のクリック可能要素 ➔ 「その他オプション・メニュー」
        if (centerY < topZone && centerX > rightZone) {
            return if (ja) "その他オプションボタン" else "More options button"
        }

        // 3. 画面右下のフローティングボタン (FAB) ➔ 「新規作成・追加ボタン」
        if (centerY > bottomZone && centerX > rightZone && bounds.width() in 80..220 && bounds.height() in 80..220) {
            return if (ja) "新規追加ボタン" else "Add button"
        }

        return ""
    }

    private fun inferFromSurroundingContext(node: AccessibilityNodeInfo, isActionable: Boolean): String {
        val parent = node.parent ?: return ""
        val ja = isJapanese

        // 親ノードが説明を持っている場合
        val parentDesc = parent.contentDescription?.toString()?.trim() ?: ""
        if (parentDesc.isNotEmpty()) {
            return if (isActionable) {
                if (ja) "${parentDesc}の操作ボタン" else "$parentDesc action button"
            } else parentDesc
        }

        // 兄弟ノード（同じ親の下にある隣接テキスト）を探す
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling != node) {
                val sibText = sibling.text?.toString()?.trim() ?: sibling.contentDescription?.toString()?.trim() ?: ""
                if (sibText.isNotEmpty() && sibText.length < 30) {
                    return if (isActionable) {
                        if (ja) "${sibText}の操作ボタン" else "$sibText action button"
                    } else sibText
                }
            }
        }

        return ""
    }
}
