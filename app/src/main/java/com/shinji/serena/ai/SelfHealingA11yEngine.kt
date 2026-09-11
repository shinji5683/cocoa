package com.shinji.serena.ai

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.R

/**
 * SelfHealingA11yEngine (AI自動ラベル付け＆UI自己修復エンジン)
 * 
 * アクセシビリティ非対応アプリの「説明なしボタン」「ラベルなし画像」を、
 * 幾何学的配置（左上/右上/右下/入力欄隣）、viewIdリソース名、階層構造から
 * 瞬時に推論して自然なローカライズラベルを付与する自己修復エンジン。
 */
class SelfHealingA11yEngine(private val context: Context) {

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
            isActionable -> context.getString(R.string.self_healing_action_button)
            isImage -> context.getString(R.string.self_healing_image_icon)
            else -> ""
        }
    }

    private fun inferFromViewId(viewId: String, isActionable: Boolean): String {
        if (viewId.isEmpty()) return ""

        val resId = when {
            viewId.contains("search") || viewId.contains("find") -> R.string.self_healing_search
            viewId.contains("setting") || viewId.contains("gear") || viewId.contains("config") -> R.string.self_healing_settings
            viewId.contains("menu") || viewId.contains("nav_drawer") || viewId.contains("hamburger") -> R.string.self_healing_menu
            viewId.contains("back") || viewId.contains("up") || viewId.contains("arrow_left") -> R.string.self_healing_back
            viewId.contains("close") || viewId.contains("dismiss") || viewId.contains("cancel") || viewId.contains("clear") -> R.string.self_healing_close
            viewId.contains("send") || viewId.contains("submit") || viewId.contains("post") -> R.string.self_healing_send
            viewId.contains("confirm") || viewId.contains("ok") || viewId.contains("apply") -> R.string.self_healing_confirm
            viewId.contains("next") || viewId.contains("forward") -> R.string.self_healing_next
            viewId.contains("prev") || viewId.contains("previous") -> R.string.self_healing_prev
            viewId.contains("home") -> R.string.self_healing_home
            viewId.contains("refresh") || viewId.contains("reload") || viewId.contains("sync") -> R.string.self_healing_refresh
            viewId.contains("share") -> R.string.self_healing_share
            viewId.contains("filter") -> R.string.self_healing_filter
            viewId.contains("sort") -> R.string.self_healing_sort
            viewId.contains("favorite") || viewId.contains("like") || viewId.contains("star") || viewId.contains("heart") -> R.string.self_healing_favorite
            viewId.contains("bookmark") -> R.string.self_healing_bookmark
            viewId.contains("cart") || viewId.contains("bag") || viewId.contains("basket") -> R.string.self_healing_cart
            viewId.contains("notification") || viewId.contains("bell") || viewId.contains("alert") -> R.string.self_healing_notification
            viewId.contains("profile") || viewId.contains("avatar") || viewId.contains("account") || viewId.contains("user") -> R.string.self_healing_profile
            viewId.contains("edit") || viewId.contains("pencil") -> R.string.self_healing_edit
            viewId.contains("delete") || viewId.contains("trash") || viewId.contains("remove") -> R.string.self_healing_delete
            viewId.contains("add") || viewId.contains("plus") || viewId.contains("create") || viewId.contains("fab") -> R.string.self_healing_add
            viewId.contains("mic") || viewId.contains("voice") || viewId.contains("audio_input") -> R.string.self_healing_voice_input
            viewId.contains("camera") || viewId.contains("shutter") -> R.string.self_healing_camera
            viewId.contains("play") -> R.string.self_healing_play
            viewId.contains("pause") -> R.string.self_healing_pause
            viewId.contains("download") -> R.string.self_healing_download
            viewId.contains("upload") -> R.string.self_healing_upload
            viewId.contains("help") || viewId.contains("info") || viewId.contains("faq") -> R.string.self_healing_help
            viewId.contains("copy") -> R.string.self_healing_copy
            viewId.contains("paste") -> R.string.self_healing_paste
            viewId.contains("overflow") || viewId.contains("more") || viewId.contains("dots") -> R.string.self_healing_more_options
            else -> 0
        }

        if (resId == 0) return ""

        val inferred = context.getString(resId)
        return if (isActionable) {
            context.getString(R.string.self_healing_button_suffix_fmt, inferred)
        } else {
            context.getString(R.string.self_healing_icon_suffix_fmt, inferred)
        }
    }

    private fun inferFromScreenGeometry(bounds: Rect, screenWidth: Int, screenHeight: Int, isActionable: Boolean): String {
        if (!isActionable || bounds.isEmpty) return ""

        val topZone = screenHeight * 0.12f // 画面上部 12%
        val bottomZone = screenHeight * 0.88f // 画面下部 12%
        val leftZone = screenWidth * 0.20f // 画面左端 20%
        val rightZone = screenWidth * 0.80f // 画面右端 20%

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // 1. 画面左上端のクリック可能要素 ➔ 「戻るボタン」
        if (centerY < topZone && centerX < leftZone) {
            return context.getString(R.string.self_healing_button_suffix_fmt, context.getString(R.string.self_healing_back))
        }

        // 2. 画面右上端のクリック可能要素 ➔ 「その他オプション・メニュー」
        if (centerY < topZone && centerX > rightZone) {
            return context.getString(R.string.self_healing_more_options_btn)
        }

        // 3. 画面右下のフローティングボタン (FAB) ➔ 「新規作成・追加ボタン」
        if (centerY > bottomZone && centerX > rightZone && bounds.width() in 80..220 && bounds.height() in 80..220) {
            return context.getString(R.string.self_healing_new_add_btn)
        }

        return ""
    }

    private fun inferFromSurroundingContext(node: AccessibilityNodeInfo, isActionable: Boolean): String {
        val parent = node.parent ?: return ""

        // 親ノードが説明を持っている場合
        val parentDesc = parent.contentDescription?.toString()?.trim() ?: ""
        if (parentDesc.isNotEmpty()) {
            return if (isActionable) {
                context.getString(R.string.self_healing_action_btn_suffix_fmt, parentDesc)
            } else parentDesc
        }

        // 兄弟ノード（同じ親の下にある隣接テキスト）を探す
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling != node) {
                val sibText = sibling.text?.toString()?.trim() ?: sibling.contentDescription?.toString()?.trim() ?: ""
                if (sibText.isNotEmpty() && sibText.length < 30) {
                    return if (isActionable) {
                        context.getString(R.string.self_healing_action_btn_suffix_fmt, sibText)
                    } else sibText
                }
            }
        }

        return ""
    }
}
