package com.shinji.serena.navigation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityNodeEvaluator
 * Google TalkBack 100% 準拠のアクセシビリティノード解体・評価判定エンジン
 */
class AccessibilityNodeEvaluator {

    fun isContainerNode(node: AccessibilityNodeInfo): Boolean {
        if (node.childCount == 0) return false

        val className = node.className?.toString() ?: ""
        val pkgName = node.packageName?.toString() ?: ""

        // Pixel Launcher / ホーム画面アプリの背景透明枠コンテナを完全スキップ
        if (pkgName.contains("launcher", ignoreCase = true)) {
            if (className.contains("CellLayout", ignoreCase = true) ||
                className.contains("Workspace", ignoreCase = true) ||
                className.contains("DragLayer", ignoreCase = true) ||
                className.contains("PagedView", ignoreCase = true) ||
                className.contains("DropTarget", ignoreCase = true) ||
                className.contains("FolderPagedView", ignoreCase = true)
            ) {
                return false
            }
        }

        if (className.contains("TextView", ignoreCase = true) ||
            className.contains("ImageView", ignoreCase = true) ||
            className.contains("Button", ignoreCase = true) ||
            className.contains("EditText", ignoreCase = true) ||
            className.contains("CheckBox", ignoreCase = true) ||
            className.contains("RadioButton", ignoreCase = true) ||
            className.contains("Switch", ignoreCase = true) ||
            className.contains("SeekBar", ignoreCase = true) ||
            className.contains("ProgressBar", ignoreCase = true) ||
            className.contains("Spinner", ignoreCase = true)
        ) {
            return false
        }

        return true
    }

    fun isFocusableTarget(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val pkgName = node.packageName?.toString() ?: ""

        if (pkgName.contains("launcher", ignoreCase = true)) {
            if (className.contains("CellLayout", ignoreCase = true) ||
                className.contains("Workspace", ignoreCase = true) ||
                className.contains("DragLayer", ignoreCase = true) ||
                className.contains("PagedView", ignoreCase = true) ||
                className.contains("DropTarget", ignoreCase = true) ||
                className.contains("FolderPagedView", ignoreCase = true)
            ) {
                return false
            }
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 && rect.height() <= 0 && !node.isClickable && !node.isFocusable) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isScreenReaderFocusable) {
            return true
        }

        val text = getNodeText(node)
        val isActionable = node.isClickable || node.isCheckable || node.isFocusable || node.isLongClickable || node.safeIsHeading

        if (isContainerNode(node)) {
            if ((isActionable || text.isNotEmpty()) && !hasFocusableChildren(node)) {
                return true
            }
            return false
        }

        return isActionable || text.isNotEmpty()
    }

    fun hasFocusableChildren(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue

            val childText = getNodeText(child)
            val childActionable = child.isClickable || child.isCheckable || child.isFocusable || child.isLongClickable || child.safeIsHeading

            if (childActionable || childText.isNotEmpty()) {
                return true
            }

            if (hasFocusableChildren(child)) {
                return true
            }
        }
        return false
    }

    fun isSameNode(node1: AccessibilityNodeInfo?, node2: AccessibilityNodeInfo?): Boolean {
        if (node1 == null || node2 == null) return node1 == node2
        if (node1 == node2 || node1.equals(node2)) return true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val u1 = node1.uniqueId
            val u2 = node2.uniqueId
            if (!u1.isNullOrEmpty() && !u2.isNullOrEmpty()) {
                return u1 == u2
            }
        }

        if (node1.windowId != node2.windowId) return false

        val b1 = Rect()
        val b2 = Rect()
        node1.getBoundsInScreen(b1)
        node2.getBoundsInScreen(b2)
        if (b1 != b2) return false

        val t1 = getNodeText(node1)
        val t2 = getNodeText(node2)
        if (t1 != t2) return false

        val c1 = node1.className?.toString() ?: ""
        val c2 = node2.className?.toString() ?: ""
        if (c1 != c2) return false

        val id1 = node1.viewIdResourceName ?: ""
        val id2 = node2.viewIdResourceName ?: ""
        if (id1.isNotEmpty() || id2.isNotEmpty()) {
            return id1 == id2
        }

        return false
    }

    fun getNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        var text = node.contentDescription?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            text = node.text?.toString()?.trim()
        }
        if (text.isNullOrEmpty()) {
            text = node.hintText?.toString()?.trim()
        }
        if (text.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            text = node.tooltipText?.toString()?.trim()
        }
        val className = node.className?.toString() ?: ""
        val rangeInfo = node.rangeInfo
        val isSeekBar = className.contains("SeekBar", ignoreCase = true) || className.contains("Slider", ignoreCase = true) || rangeInfo != null

        if (isSeekBar) {
            val title = text ?: inferLabelFromViewId(node.viewIdResourceName)
            val cleanTitle = if (title.contains("value", ignoreCase = true) || title.contains("バリュー", ignoreCase = true)) "" else title
            val pct = if (rangeInfo != null && (rangeInfo.max - rangeInfo.min) > 0) {
                ((rangeInfo.current - rangeInfo.min) * 100 / (rangeInfo.max - rangeInfo.min)).toInt()
            } else {
                50
            }
            return if (cleanTitle.isNotEmpty()) "$cleanTitle スライダー $pct%" else "スライダー $pct%"
        }

        if (!text.isNullOrEmpty()) {
            return text
        }

        val inferred = inferLabelFromViewId(node.viewIdResourceName)
        if (inferred.isNotEmpty()) {
            return inferred
        }

        if (node.childCount > 0) {
            val childTexts = mutableListOf<String>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (!child.isVisibleToUser) continue
                val t = child.contentDescription?.toString()?.trim()
                    ?: child.text?.toString()?.trim()
                    ?: child.hintText?.toString()?.trim()
                    ?: inferLabelFromViewId(child.viewIdResourceName)
                if (t.isNotEmpty() && !childTexts.contains(t)) {
                    childTexts.add(t)
                }
                if (childTexts.size >= 3) break
            }
            if (childTexts.isNotEmpty()) {
                return childTexts.joinToString(" ")
            }
        }

        if (node.isClickable || node.isCheckable) {
            return "ボタン"
        }

        return ""
    }

    private fun inferLabelFromViewId(viewId: String?): String {
        if (viewId.isNullOrEmpty()) return ""
        val name = viewId.substringAfterLast(":id/").lowercase()
        return when {
            name.contains("search") || name.contains("gsearch") -> "Google検索"
            name.contains("home") -> "ホーム"
            name.contains("menu") || name.contains("drawer") -> "メニュー"
            name.contains("setting") -> "設定"
            name.contains("back") -> "戻る"
            name.contains("close") || name.contains("cancel") -> "閉じる"
            name.contains("mic") || name.contains("voice") -> "音声検索"
            name.contains("camera") -> "カメラ"
            name.contains("phone") || name.contains("call") || name.contains("dial") -> "電話"
            name.contains("message") || name.contains("chat") || name.contains("sms") -> "メッセージ"
            name.contains("mail") || name.contains("gmail") -> "メール"
            name.contains("browser") || name.contains("chrome") || name.contains("web") -> "ブラウザ"
            name.contains("app") || name.contains("icon") -> "アプリ"
            else -> ""
        }
    }

    private val AccessibilityNodeInfo.safeIsHeading: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isHeading else false
}
