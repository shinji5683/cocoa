package com.shinji.serena.navigation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityNodeEvaluator
 * Google TalkBack 100% 準拠のアクセシビリティノード解体・評価判定エンジン
 */
class AccessibilityNodeEvaluator {

    fun isSwitchOrToggle(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val className = node.className?.toString() ?: ""
        return className.contains("Switch", ignoreCase = true) ||
                className.contains("ToggleButton", ignoreCase = true) ||
                className.contains("Toggle", ignoreCase = true)
    }

    fun isCheckableOrCompound(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isCheckable) return true
        val className = node.className?.toString() ?: ""
        return className.contains("CheckBox", ignoreCase = true) ||
                className.contains("RadioButton", ignoreCase = true) ||
                className.contains("Switch", ignoreCase = true) ||
                className.contains("CompoundButton", ignoreCase = true)
    }

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
            className.contains("CompoundButton", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true) ||
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
        if (rect.width() <= 0 && rect.height() <= 0 && !node.isClickable && !node.isFocusable && !node.isCheckable) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isScreenReaderFocusable) {
            return true
        }

        val isActionable = node.isClickable || node.isCheckable || node.isFocusable || node.isLongClickable ||
                node.safeIsHeading || isSwitchOrToggle(node) || isCheckableOrCompound(node)

        val text = getNodeText(node)

        if (isContainerNode(node)) {
            // スイッチやチェックボックスを子に持つコンテナの場合、または子要素にフォーカス可能要素がない場合はターゲット
            val hasChildren = hasFocusableChildren(node)
            if (!hasChildren && (isActionable || text.isNotEmpty())) {
                return true
            }
            // コンテナ自身がクリック可能で行として機能する場合も対象
            if (node.isClickable && text.isNotEmpty() && !hasInteractiveSiblingControls(node)) {
                return true
            }
            return false
        }

        return isActionable || text.isNotEmpty()
    }

    private fun hasInteractiveSiblingControls(node: AccessibilityNodeInfo): Boolean {
        // コンテナ内に複数の独立したボタンやスイッチがあるか（その場合は子要素を個別にフォーカスさせる）
        var interactiveCount = 0
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            if (child.isClickable || child.isCheckable || isSwitchOrToggle(child)) {
                interactiveCount++
            }
        }
        return interactiveCount > 1
    }

    fun hasFocusableChildren(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (!child.isVisibleToUser && (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0)) continue

            val childActionable = child.isClickable || child.isCheckable || child.isFocusable ||
                    child.isLongClickable || child.safeIsHeading || isSwitchOrToggle(child) || isCheckableOrCompound(child)
            val childText = getNodeText(child)

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

        // 1. ノード自体の direct text
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
        if (text.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            text = node.paneTitle?.toString()?.trim()
        }

        val className = node.className?.toString() ?: ""
        val rangeInfo = node.rangeInfo
        val isSeekBar = className.contains("SeekBar", ignoreCase = true) || className.contains("Slider", ignoreCase = true) || rangeInfo != null

        if (isSeekBar) {
            val title = text ?: ""
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

        // 2. labeledBy があればそれを参照
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val labeledBy = node.labeledBy
            if (labeledBy != null) {
                val labelText = getNodeText(labeledBy)
                if (labelText.isNotEmpty()) return labelText
            }
        }

        // 3. 子孫要素テキストの再帰収集（深さ制限付きDFS）
        if (node.childCount > 0) {
            val childTexts = mutableListOf<String>()
            collectAllDescendantText(node, childTexts)
            if (childTexts.isNotEmpty()) {
                return childTexts.joinToString(" ")
            }
        }

        // 4. スイッチやチェックボックス単体でラベルがない場合、親や兄弟ノードからラベルを探索
        val parent = node.parent
        if (parent != null) {
            val siblingTexts = mutableListOf<String>()
            val parentDirectText = parent.contentDescription?.toString()?.trim() ?: parent.text?.toString()?.trim()
            if (!parentDirectText.isNullOrEmpty()) {
                siblingTexts.add(parentDirectText)
            }
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling == node || isSameNode(sibling, node)) continue
                collectAllDescendantText(sibling, siblingTexts)
            }
            if (siblingTexts.isNotEmpty()) {
                return siblingTexts.distinct().joinToString(" ")
            }
        }

        // 5. 特定のキー・ボタンIDからの推定
        val inferred = inferLabelFromViewId(node.viewIdResourceName)
        if (inferred.isNotEmpty()) {
            return inferred
        }

        if (isSwitchOrToggle(node)) {
            return "スイッチ"
        }
        if (node.isCheckable) {
            return "チェックボックス"
        }
        if (node.isClickable) {
            return "ボタン"
        }

        return ""
    }

    private fun collectAllDescendantText(node: AccessibilityNodeInfo, result: MutableList<String>, maxCount: Int = 10) {
        if (result.size >= maxCount) return
        val directText = node.contentDescription?.toString()?.trim()
            ?: node.text?.toString()?.trim()
            ?: node.hintText?.toString()?.trim()
        if (!directText.isNullOrEmpty() && !result.contains(directText)) {
            result.add(directText)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            collectAllDescendantText(child, result, maxCount)
        }
    }

    private fun inferLabelFromViewId(viewId: String?): String {
        if (viewId.isNullOrEmpty()) return ""
        val name = viewId.substringAfterLast(":id/").lowercase()
        return when (name) {
            "key0", "button0" -> "0"
            "key1", "button1" -> "1"
            "key2", "button2" -> "2"
            "key3", "button3" -> "3"
            "key4", "button4" -> "4"
            "key5", "button5" -> "5"
            "key6", "button6" -> "6"
            "key7", "button7" -> "7"
            "key8", "button8" -> "8"
            "key9", "button9" -> "9"
            "delete_button", "backspace", "btn_delete" -> "1文字削除"
            "emergency_call_button", "emergency" -> "緊急通報"
            "cancel_button", "btn_cancel" -> "キャンセル"
            "enter_button", "btn_ok", "btn_done" -> "決定"
            "search_button" -> "検索"
            else -> ""
        }
    }

    private val AccessibilityNodeInfo.safeIsHeading: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isHeading else false
}
