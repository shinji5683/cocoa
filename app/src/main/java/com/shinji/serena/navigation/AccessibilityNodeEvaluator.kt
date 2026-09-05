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

    fun isSelfContainedControl(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val isActionable = node.isClickable || node.isCheckable || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isScreenReaderFocusable)
        if (!isActionable) return false

        // コンテナ（CardView, Layout, ViewGroup, Dialog, RecyclerView等）で、配下にインタラクティブな子要素がある場合は
        // 自己充足コントロールとみなさず、子要素（ボタン、スイッチ等）を必ず個別に探索させる！
        val className = node.className?.toString() ?: ""
        if (className.contains("Layout", ignoreCase = true) ||
            className.contains("ViewGroup", ignoreCase = true) ||
            className.contains("CardView", ignoreCase = true) ||
            className.contains("ScrollView", ignoreCase = true) ||
            className.contains("ViewPager", ignoreCase = true) ||
            className.contains("RecyclerView", ignoreCase = true) ||
            className.contains("ListView", ignoreCase = true) ||
            className.contains("Dialog", ignoreCase = true)
        ) {
            if (hasInteractiveChild(node)) {
                return false
            }
        }

        // 有効な contentDescription または text を自身が直接持っているか判定
        val hasDirectLabel = (!node.contentDescription.isNullOrEmpty() && node.contentDescription.toString().trim().isNotEmpty()) ||
                (!node.text.isNullOrEmpty() && node.text.toString().trim().isNotEmpty())

        var hasLabel = hasDirectLabel
        if (!hasLabel && node.childCount in 1..2) {
            // 電話アプリの通話ボタンのように、親がclickableで子に非clickableのラベル（desc='通話'等）がある場合
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                val cDesc = c.contentDescription?.toString()?.trim() ?: ""
                val cText = c.text?.toString()?.trim() ?: ""
                if (!c.isClickable && (cDesc.isNotEmpty() || cText.isNotEmpty())) {
                    hasLabel = true
                    break
                }
            }
        }

        if (hasLabel) {
            // スクロールビューやページャー等の大枠コンテナ自体は除外
            if (className.contains("ScrollView", ignoreCase = true) ||
                className.contains("ViewPager", ignoreCase = true) ||
                className.contains("RecyclerView", ignoreCase = true) ||
                className.contains("ListView", ignoreCase = true)
            ) {
                return false
            }
            // 配下に別のアクション可能な子要素（別のアクションボタン等）を持つなら自己完結コントロールにしない
            if (hasInteractiveChild(node)) {
                return false
            }
            return true
        }
        return false
    }

    fun isContainerNode(node: AccessibilityNodeInfo): Boolean {
        if (node.childCount == 0) return false

        // 自己充足コントロール（自身がクリック可能でラベルを持つアカウントボタン・各種アクション等）は分解せず単一ノードとして保持
        if (isSelfContainedControl(node)) {
            return false
        }

        val className = node.className?.toString() ?: ""
        val pkgName = node.packageName?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        // PIN / パスワード入力欄 (PasswordTextView, EditText, pinEntry, lockPassword等) はコンテナ判定から除外
        if (node.isPassword || node.isEditable ||
            className.contains("PasswordTextView", ignoreCase = true) ||
            className.contains("EditText", ignoreCase = true) ||
            viewId.contains("pinentry") || viewId.contains("passwordentry") ||
            viewId.contains("pin_entry") || viewId.contains("password_entry") ||
            viewId.contains("lockpassword")
        ) {
            return false
        }

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
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // 0. 最重要: PIN / パスワード入力欄・PINキーパッドは 100% 確実にフォーカス対象として即時許可！
            val isPinOrPassField = node.isPassword || node.isEditable ||
                    className.contains("PasswordTextView", ignoreCase = true) ||
                    className.contains("EditText", ignoreCase = true) ||
                    viewId.contains("pinentry") || viewId.contains("passwordentry") ||
                    viewId.contains("pin_entry") || viewId.contains("password_entry") ||
                    viewId.contains("lockpassword") || viewId.contains("pin_code") ||
                    viewId.contains("element:pin") || viewId.contains("element:bouncer") ||
                    node.contentDescription?.contains("PIN", ignoreCase = true) == true ||
                    ((viewId.contains("pin") || viewId.contains("password")) && (pkgName.contains("systemui") || pkgName.contains("keyguard")))

            if (isPinOrPassField) {
                return true
            }

            // ロック画面全体枠 (element:lockscreen / lock_icon) はダブルタップでPIN入力画面を開くための重要ターゲットとして許可
            if (viewId.contains("element:lockscreen") || viewId.contains("lock_icon")) {
                return true
            }

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

            // SystemUI / ロック画面 / Bouncer の枠コンテナは完全スキップ（中身のPIN入力欄・PIN数字キー・時計等にのみフォーカスを許可）
            if (pkgName.contains("systemui", ignoreCase = true) || pkgName.contains("keyguard", ignoreCase = true)) {
                if (node.childCount > 0 && !isPinOrPassField && !viewId.contains("element:lockscreen")) {
                    if (viewId.contains("scrim") || className.contains("ScrimView", ignoreCase = true) ||
                        className.contains("NotificationPanelView", ignoreCase = true) ||
                        className.contains("NotificationShade", ignoreCase = true) ||
                        className.contains("KeyguardRootView", ignoreCase = true) ||
                        className.contains("KeyguardSecurityContainer", ignoreCase = true) ||
                        className.contains("KeyguardBouncerView", ignoreCase = true) ||
                        className.contains("KeyguardHostView", ignoreCase = true) ||
                        viewId.contains("scene_window_root") ||
                        viewId.contains("scene_container_root") ||
                        viewId.contains("keyguard_security_container") ||
                        viewId.contains("keyguard_bouncer") ||
                        viewId.contains("pin_pad") ||
                        viewId.contains("container") ||
                        className.contains("FrameLayout", ignoreCase = true) ||
                        className.contains("ViewGroup", ignoreCase = true)
                    ) {
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

    fun hasInteractiveChild(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 6) return false
        for (i in 0 until node.childCount.coerceAtMost(25)) {
            val child = node.getChild(i) ?: continue
            val childClass = child.className?.toString() ?: ""
            if (child.isClickable || child.isCheckable || child.isFocusable || isSwitchOrToggle(child) ||
                childClass.contains("Button", ignoreCase = true) ||
                childClass.contains("EditText", ignoreCase = true) ||
                childClass.contains("SeekBar", ignoreCase = true) ||
                childClass.contains("QuickContactBadge", ignoreCase = true) ||
                childClass.contains("Switch", ignoreCase = true)
            ) {
                return true
            }
            if (child.childCount > 0 && hasInteractiveChild(child, depth + 1)) {
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

    fun hasFocusableChildren(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 6) return false
        for (i in 0 until node.childCount.coerceAtMost(25)) {
            val child = node.getChild(i) ?: continue
            val rect = Rect()
            child.getBoundsInScreen(rect)
            if (!child.isVisibleToUser && (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0)) continue

            val childClass = child.className?.toString() ?: ""
            val childActionable = child.isClickable || child.isCheckable || child.isFocusable ||
                    child.isLongClickable || child.safeIsHeading || isSwitchOrToggle(child) || isCheckableOrCompound(child) ||
                    childClass.contains("Button", ignoreCase = true) || childClass.contains("QuickContactBadge", ignoreCase = true)
            val childDirectText = hasDirectTextOrLabel(child)

            if (childActionable || childDirectText) {
                return true
            }
            if (child.childCount > 0 && hasFocusableChildren(child, depth + 1)) {
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

        // viewIdが無い場合でも、同一ウィンドウ・同一座標・同一クラス名なら同一ノードとして判定！
        val t1 = node1.text?.toString() ?: ""
        val t2 = node2.text?.toString() ?: ""
        val d1 = node1.contentDescription?.toString() ?: ""
        val d2 = node2.contentDescription?.toString() ?: ""
        if (t1 == t2 && d1 == d2) return true

        return true
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

            // 2. PIN / パスワード入力欄の徹底した安全性保護（平文PIN/パスワードの漏洩を完全防止）
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val isPassOrPin = node.isPassword || className.contains("PasswordTextView", ignoreCase = true) ||
                    viewId.contains("pinentry") || viewId.contains("passwordentry") ||
                    viewId.contains("pin_entry") || viewId.contains("password_entry") ||
                    viewId.contains("lockpassword") || viewId.contains("pin_code") ||
                    viewId.contains("element:pin") ||
                    (node.isEditable && (viewId.contains("pin") || viewId.contains("password")))

            if (isPassOrPin) {
                val len = text?.length ?: 0
                return if (len > 0) "黒丸 ${len}文字" else "未入力"
            }

            if (!text.isNullOrEmpty()) {
                return text
            }

            // 3. labeledBy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val labeledBy = node.labeledBy
                if (labeledBy != null) {
                    val labelText = labeledBy.contentDescription?.toString()?.trim() ?: labeledBy.text?.toString()?.trim() ?: ""
                    if (labelText.isNotEmpty()) return labelText
                }
            }

            // 4. Jetpack Compose & 階層UI: 子要素テキストの再帰的収集（直下および2階層下まで）
            if (node.childCount > 0) {
                val childTexts = mutableListOf<String>()
                for (i in 0 until node.childCount.coerceAtMost(10)) {
                    val child = node.getChild(i) ?: continue
                    val ct = child.contentDescription?.toString()?.trim() ?: child.text?.toString()?.trim()
                    if (!ct.isNullOrEmpty() && !childTexts.contains(ct)) {
                        childTexts.add(ct)
                    }
                    if (child.childCount > 0) {
                        for (j in 0 until child.childCount.coerceAtMost(6)) {
                            val gc = child.getChild(j) ?: continue
                            val gct = gc.contentDescription?.toString()?.trim() ?: gc.text?.toString()?.trim()
                            if (!gct.isNullOrEmpty() && !childTexts.contains(gct)) {
                                childTexts.add(gct)
                            }
                        }
                    }
                    if (childTexts.size >= 6) break
                }
                if (childTexts.isNotEmpty()) {
                    return childTexts.joinToString(" ")
                }
            }

            // 5. Jetpack Compose 親コンテナからのラベル継承（自身が空のComposeサブ要素の場合）
            val parent = node.parent
            if (parent != null) {
                val parentText = parent.contentDescription?.toString()?.trim() ?: parent.text?.toString()?.trim()
                if (!parentText.isNullOrEmpty() && !parentText.contains("android", ignoreCase = true)) {
                    return parentText
                }
            }

            // 6. スイッチやチェックボックス単体でラベルがない場合、同一親行内の兄弟ノードからラベルを探索
            if (isSwitchOrToggle(node) || isCheckableOrCompound(node)) {
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

            // 7. 特定のキー・ボタンIDからの推定
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
