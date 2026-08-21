package com.shinji.serena.navigation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityNodeEvaluator
 * Google TalkBack 100% 準拠のアクセシビリティノード解体・評価判定エンジン
 * 高速・軽量・Binder枯渇防止ガード付き
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
        try {
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

            // SystemUI / ロック画面の画面全体通知シェードやスクリム暗転背景を完全スキップ
            if (pkgName.contains("systemui", ignoreCase = true) || pkgName.contains("keyguard", ignoreCase = true)) {
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                if (viewId.contains("scrim") || viewId.contains("notification_stack_scroller") ||
                    viewId.contains("ambient_indication_container") || viewId.contains("keyguard_carrier_text") ||
                    viewId.contains("keyguard_status_view") || viewId.contains("keyguard_clock") ||
                    className.contains("ScrimView", ignoreCase = true) ||
                    className.contains("NotificationPanelView", ignoreCase = true) ||
                    className.contains("NotificationShade", ignoreCase = true) ||
                    className.contains("KeyguardRootView", ignoreCase = true)
                ) {
                    if (node.childCount > 0) {
                        return false
                    }
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

            val hasDirect = hasDirectTextOrLabel(node)

            if (isContainerNode(node)) {
                // ボタンや入力欄など、配下にインタラクティブな子要素を1つでも含むコンテナ（ダイアログのボタンバー等）は、
                // コンテナ自体をターゲットにせず、子要素（「完了」「開く」ボタン等）をそれぞれ個別に検出させる
                if (hasInteractiveChild(node)) {
                    return false
                }

                // 子要素にボタン等が存在しないクリック可能行（セレナメニュー項目や設定行など）は行全体を1つのターゲットとする
                if (isActionable) {
                    return true
                }

                val hasChildren = hasFocusableChildren(node)
                return !hasChildren && hasDirect
            }

            return isActionable || hasDirect
        } catch (_: Exception) {
            return false
        }
    }

    private fun hasInteractiveChild(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount.coerceAtMost(16)) {
            val child = node.getChild(i) ?: continue
            val childClass = child.className?.toString() ?: ""
            if (child.isClickable || child.isCheckable || child.isFocusable || isSwitchOrToggle(child) ||
                childClass.contains("Button", ignoreCase = true) ||
                childClass.contains("EditText", ignoreCase = true) ||
                childClass.contains("SeekBar", ignoreCase = true)
            ) {
                return true
            }
            if (hasInteractiveChild(child)) {
                return true
            }
        }
        return false
    }

    private fun hasDirectTextOrLabel(node: AccessibilityNodeInfo): Boolean {
        val cd = node.contentDescription
        if (!cd.isNullOrEmpty() && cd.toString().trim().isNotEmpty()) return true
        val txt = node.text
        if (!txt.isNullOrEmpty() && txt.toString().trim().isNotEmpty()) return true
        val hint = node.hintText
        if (!hint.isNullOrEmpty() && hint.toString().trim().isNotEmpty()) return true
        return false
    }

    private fun hasInteractiveSiblingControls(node: AccessibilityNodeInfo): Boolean {
        var interactiveCount = 0
        for (i in 0 until node.childCount.coerceAtMost(10)) {
            val child = node.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            if (child.isClickable || child.isCheckable || isSwitchOrToggle(child)) {
                interactiveCount++
            }
        }
        return interactiveCount > 1
    }

    fun hasFocusableChildren(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount.coerceAtMost(15)) {
            val child = node.getChild(i) ?: continue
            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (!child.isVisibleToUser && (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0)) continue

            val childActionable = child.isClickable || child.isCheckable || child.isFocusable ||
                    child.isLongClickable || child.safeIsHeading || isSwitchOrToggle(child) || isCheckableOrCompound(child)
            val childDirectText = hasDirectTextOrLabel(child)

            if (childActionable || childDirectText) {
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
        try {
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

            // 2. labeledBy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val labeledBy = node.labeledBy
                if (labeledBy != null) {
                    val labelText = labeledBy.contentDescription?.toString()?.trim() ?: labeledBy.text?.toString()?.trim() ?: ""
                    if (labelText.isNotEmpty()) return labelText
                }
            }

            // 3. 子要素テキストの安全な収集（直下および1階層下まで、最大5件）
            if (node.childCount > 0) {
                val childTexts = mutableListOf<String>()
                for (i in 0 until node.childCount.coerceAtMost(8)) {
                    val child = node.getChild(i) ?: continue
                    if (!child.isVisibleToUser) continue
                    val ct = child.contentDescription?.toString()?.trim() ?: child.text?.toString()?.trim()
                    if (!ct.isNullOrEmpty() && !childTexts.contains(ct)) {
                        childTexts.add(ct)
                    } else if (child.childCount > 0) {
                        for (j in 0 until child.childCount.coerceAtMost(4)) {
                            val gc = child.getChild(j) ?: continue
                            if (!gc.isVisibleToUser) continue
                            val gct = gc.contentDescription?.toString()?.trim() ?: gc.text?.toString()?.trim()
                            if (!gct.isNullOrEmpty() && !childTexts.contains(gct)) {
                                childTexts.add(gct)
                            }
                        }
                    }
                    if (childTexts.size >= 4) break
                }
                if (childTexts.isNotEmpty()) {
                    return childTexts.joinToString(" ")
                }
            }

            // 4. スイッチやチェックボックス単体でラベルがない場合、同一親行内の兄弟ノードからラベルを探索
            if (isSwitchOrToggle(node) || isCheckableOrCompound(node)) {
                val parent = node.parent
                if (parent != null && parent.childCount in 2..8) {
                    val siblingTexts = mutableListOf<String>()
                    val parentDirectText = parent.contentDescription?.toString()?.trim() ?: parent.text?.toString()?.trim()
                    if (!parentDirectText.isNullOrEmpty()) {
                        siblingTexts.add(parentDirectText)
                    }
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        if (sibling == node || isSameNode(sibling, node)) continue
                        val st = sibling.contentDescription?.toString()?.trim() ?: sibling.text?.toString()?.trim()
                        if (!st.isNullOrEmpty() && !siblingTexts.contains(st)) {
                            siblingTexts.add(st)
                        }
                    }
                    if (siblingTexts.isNotEmpty()) {
                        return siblingTexts.joinToString(" ")
                    }
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
        } catch (_: Exception) {
            return ""
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
