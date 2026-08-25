package com.shinji.serena.navigation

import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.shinji.serena.SerenaScreenReaderService

/**
 * SerenaFocusNavigator
 * Google TalkBack 100% 準拠のアクセシビリティフォーカスナビゲーション＆探索エンジン
 */
class SerenaFocusNavigator(
    private val service: SerenaScreenReaderService,
    val evaluator: AccessibilityNodeEvaluator = AccessibilityNodeEvaluator()
) {
    companion object {
        private const val TAG = "SerenaFocusNavigator"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    var lastFocusedNodeIndex: Int = -1

    fun resetIndex() {
        lastFocusedNodeIndex = -1
    }

    fun getAllRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()

        // 1. ロック画面中ならキーガード/Bouncerウィンドウを取得
        if (service.isKeyguardLocked()) {
            try {
                val wins = service.windows
                val keyguardWin = wins?.find { it.type == 4 /* TYPE_KEYGUARD */ || it.root?.packageName?.toString()?.contains("keyguard") == true }
                val r = keyguardWin?.root ?: service.rootInActiveWindow
                if (r != null) return listOf(r)
            } catch (_: Exception) {}
            val root = service.rootInActiveWindow
            return if (root != null) listOf(root) else emptyList()
        }

        // 2. 通常時: service.windows から最前面のフォーカスウィンドウを優先取得！
        try {
            val wins = service.windows
            if (!wins.isNullOrEmpty()) {
                val activeRoot = service.rootInActiveWindow
                val activePkg = activeRoot?.packageName?.toString()?.lowercase() ?: ""
                val isSystemUiActive = activePkg.contains("systemui")

                // ダイアログ・モーダルウィンドウ（最前面のフォーカス中/アクティブなAPPLICATIONウィンドウ）を特定
                val appWins = wins.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val focusedAppWin = appWins.firstOrNull { it.isFocused }
                    ?: appWins.firstOrNull { it.isActive }
                    ?: appWins.maxByOrNull { it.layer }

                val targetWins = wins.filter { w ->
                    when (w.type) {
                        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> true
                        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> true
                        AccessibilityWindowInfo.TYPE_APPLICATION -> {
                            // モーダルダイアログ等で複数のAPPLICATIONウィンドウが存在する場合、最前面のフォーカス中ウィンドウのみを対象にする！
                            if (appWins.size > 1 && focusedAppWin != null) {
                                w.id == focusedAppWin.id
                            } else {
                                true
                            }
                        }
                        AccessibilityWindowInfo.TYPE_SYSTEM -> isSystemUiActive
                        else -> false
                    }
                }.sortedBy { getWindowPriority(it) }

                for (w in targetWins) {
                    val r = w.root ?: continue
                    if (list.none { it == r || it.windowId == r.windowId }) {
                        list.add(r)
                    }
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            val activeRoot = service.rootInActiveWindow
            if (activeRoot != null) {
                list.add(activeRoot)
            }
        }

        return list
    }

    private fun getWindowPriority(w: AccessibilityWindowInfo): Int {
        // 最前面オーバーレイ（Serena UI等）
        if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return 0
        }
        // フォーカス中ウィンドウまたはアクティブウィンドウ（現在操作中のメインアプリ画面）
        if (w.isFocused || w.isActive) {
            return 1
        }
        // IME（ソフトウェアキーボード）
        if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return 2
        }
        // 通常アプリケーション
        if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return 3
        }
        // システムウィンドウ（ステータスバー・ナビゲーションバー）
        if (w.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return 4
        }
        return 5
    }

    fun collectAccessibleNodes(root: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val roots = if (root != null) listOf(root) else getAllRoots()
        val list = mutableListOf<AccessibilityNodeInfo>()
        for (r in roots) {
            traverseTree(r, list)
        }

        val dm = service.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // 画面上に実際に表示されている（可視領域内にある）要素のみに厳格フィルタリング！
        // （Pixel Launcher等のPagedViewで画面外にある前後のページの要素が混入してフォーカスが戻るのを完全防止）
        val visibleList = list.filter { node ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (!node.isVisibleToUser) return@filter false
            }
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) return@filter false
            // 画面境界外（オフスクリーン要素）を完全排除
            if (rect.right <= 0 || rect.left >= screenW) return@filter false
            if (rect.bottom <= 0 || rect.top >= screenH) return@filter false
            true
        }

        if (service.isKeyguardLocked()) {
            // ロック画面時はPIN/パスワード入力欄を最優先(1050)、メッセージ(1040)、続けてPINキーパッド（1〜9, 削除, 0, 決定）を自然な順序に整列！通知領域は末尾へ隔離
            return visibleList.sortedByDescending { node ->
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val className = node.className?.toString() ?: ""
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                fun isDigit(d: String): Boolean =
                    viewId.endsWith("key$d") || viewId.endsWith("digit$d") || desc == d || text == d ||
                    desc.startsWith(d) || text.startsWith(d) || viewId.contains("element:pin_key_$d")

                val isPinOrPass = node.isPassword || className.contains("PasswordTextView", ignoreCase = true) ||
                        viewId.contains("pinentry") || viewId.contains("passwordentry") ||
                        viewId.contains("pin_entry") || viewId.contains("password_entry") ||
                        viewId.contains("lockpassword") || viewId.contains("pin_code") ||
                        viewId.contains("element:pin") || viewId.contains("element:bouncer") ||
                        desc.contains("PIN", ignoreCase = true) || desc.contains("パスワード") ||
                        text.contains("PIN", ignoreCase = true) ||
                        (node.isEditable && (viewId.contains("pin") || viewId.contains("password") || viewId.contains("keyguard")))

                when {
                    isPinOrPass -> 1050
                    viewId.contains("message_area") || viewId.contains("bouncer_message") || viewId.contains("keyguard_message") || viewId.contains("element:bouncer_message") -> 1040
                    isDigit("1") -> 1000
                    isDigit("2") -> 990
                    isDigit("3") -> 980
                    isDigit("4") -> 970
                    isDigit("5") -> 960
                    isDigit("6") -> 950
                    isDigit("7") -> 940
                    isDigit("8") -> 930
                    isDigit("9") -> 920
                    viewId.contains("delete") || desc.contains("削除") || text.contains("削除") -> 915
                    isDigit("0") -> 910
                    viewId.contains("enter") || viewId.contains("ok") || desc.contains("決定") || text.contains("決定") || desc.contains("確定") -> 900
                    viewId.contains("emergency") || desc.contains("緊急") || text.contains("緊急") -> 880
                    node.isEditable -> 870
                    viewId.contains("lockscreen") || viewId.contains("lock_icon") || desc.contains("ロック") || text.contains("ロック") || desc.contains("解除") -> 700
                    viewId.contains("notification") || node.className?.contains("Notification", ignoreCase = true) == true -> -1000
                    else -> 0
                }
            }
        }

        // 通常画面: TalkBack標準 読書順序ソート (上から下、左から右)
        return visibleList.sortedWith(Comparator { n1, n2 ->
            val r1 = android.graphics.Rect()
            val r2 = android.graphics.Rect()
            n1.getBoundsInScreen(r1)
            n2.getBoundsInScreen(r2)
            val rowOverlap = kotlin.math.min(r1.bottom, r2.bottom) - kotlin.math.max(r1.top, r2.top)
            if (rowOverlap > (kotlin.math.min(r1.height(), r2.height()) * 0.4f) || kotlin.math.abs(r1.top - r2.top) < 30) {
                r1.left.compareTo(r2.left)
            } else {
                r1.top.compareTo(r2.top)
            }
        })
    }

    private fun traverseTree(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        val isPinOrPassField = node.isPassword || className.contains("PasswordTextView", ignoreCase = true) ||
                               viewId.contains("pinentry") || viewId.contains("passwordentry") ||
                               viewId.contains("pin_entry") || viewId.contains("password_entry") ||
                               viewId.contains("lockpassword") || viewId.contains("pin_view") ||
                               viewId.contains("pin_code") || viewId.contains("element:pin") ||
                               node.contentDescription?.contains("PIN", ignoreCase = true) == true

        val isPinKeyNode = isPinOrPassField ||
                           viewId.contains("systemui:id/key") || 
                           viewId.contains("systemui:id/delete_button") ||
                           viewId.contains("systemui:id/emergency_call_button") ||
                           viewId.contains("element:pin_key") ||
                           viewId.contains("pin_pad") ||
                           viewId.contains("numpad")

        val isTarget = evaluator.isFocusableTarget(node)
        val hasFocusableChildren = if (isPinKeyNode) false else evaluator.hasFocusableChildren(node)

        // 1. 子要素にフォーカス可能要素を持たない意味のあるノード（末端ノード/ボタン/テキスト/PIN入力欄等）なら登録
        if (isTarget && !hasFocusableChildren) {
            if (list.none { it == node || (it.windowId == node.windowId && evaluator.isSameNode(it, node)) }) {
                list.add(node)
            }
            return
        }

        // 2. 子要素が存在する場合、全子要素を必ず再帰探索！（スクロールビュー下部の画面外要素も確実に取得）
        var addedAnyChild = false
        val initialSize = list.size
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseTree(child, list)
        }
        if (list.size > initialSize) {
            addedAnyChild = true
        }

        // 3. 子要素から1つもターゲットが取れなかったが、このノード自体がターゲット（クリック可能行など）ならフォールバック登録
        if (!addedAnyChild && isTarget) {
            if (list.none { it == node || (it.windowId == node.windowId && evaluator.isSameNode(it, node)) }) {
                list.add(node)
            }
        }
    }

    fun findCurrentNodeIndex(nodes: List<AccessibilityNodeInfo>, currentFocus: AccessibilityNodeInfo?): Int {
        if (currentFocus == null || nodes.isEmpty()) return -1

        val directIndex = nodes.indexOfFirst { evaluator.isSameNode(it, currentFocus) }
        if (directIndex >= 0) return directIndex

        var parent = currentFocus.parent
        while (parent != null) {
            val parentIndex = nodes.indexOfFirst { evaluator.isSameNode(it, parent) }
            if (parentIndex >= 0) return parentIndex
            parent = parent.parent
        }

        val focusRect = Rect()
        currentFocus.getBoundsInScreen(focusRect)
        val focusCenterX = focusRect.centerX()
        val focusCenterY = focusRect.centerY()

        var minDistance = Double.MAX_VALUE
        var bestIndex = -1

        for (i in nodes.indices) {
            val n = nodes[i]
            val r = Rect()
            n.getBoundsInScreen(r)
            val dx = (r.centerX() - focusCenterX).toDouble()
            val dy = (r.centerY() - focusCenterY).toDouble()
            val dist = Math.sqrt(dx * dx + dy * dy)
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }

        return bestIndex
    }

    fun syncTouchExplorationIndex(targetNode: AccessibilityNodeInfo) {
        val nodes = collectAccessibleNodes()
        val idx = findCurrentNodeIndex(nodes, targetNode)
        if (idx >= 0) {
            lastFocusedNodeIndex = idx
        }
    }

    private fun isHotseatNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var p: AccessibilityNodeInfo? = node
        var depth = 0
        while (p != null && depth < 6) {
            val viewId = p.viewIdResourceName?.lowercase() ?: ""
            val cls = p.className?.toString()?.lowercase() ?: ""
            if (viewId.contains("hotseat") || cls.contains("hotseat") || viewId.contains("search_container_hotseat") || viewId.contains("qsb") || viewId.contains("dock")) {
                return true
            }
            p = p.parent
            depth++
        }
        return false
    }

    private fun isWorkspaceNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val pkg = node.packageName?.toString()?.lowercase() ?: ""
        if (!pkg.contains("launcher")) return false
        return !isHotseatNode(node)
    }

    fun navigateLinearFocus(forward: Boolean) {
        val currentFocus = service.getAccessibilityFocusedNode()
        val nodes = collectAccessibleNodes()

        Log.i(TAG, "navigateLinearFocus: forward=$forward, totalNodes=${nodes.size}")
        if (nodes.isEmpty()) {
            service.soundHelper?.playLastItemEdgeSound()
            return
        }

        val currentIndex = if (currentFocus != null) {
            findCurrentNodeIndex(nodes, currentFocus)
        } else if (lastFocusedNodeIndex in nodes.indices) {
            lastFocusedNodeIndex
        } else {
            -1
        }

        val targetIndex = if (forward) {
            if (currentIndex < 0) 0 else currentIndex + 1
        } else {
            if (currentIndex < 0) nodes.size - 1 else currentIndex - 1
        }

        // ホーム画面のページ境界判定: ワークスペースの末尾アイコン（YouTube等）から右フリックした際、ドック（電話）に行かずに次ページへ進む！
        val isCurrentInWorkspace = currentFocus != null && isWorkspaceNode(currentFocus)
        val isTargetInHotseat = targetIndex in nodes.indices && isHotseatNode(nodes[targetIndex])

        if (forward && (targetIndex >= nodes.size || (isCurrentInWorkspace && isTargetInHotseat))) {
            val horiz = findHorizontalScrollableNode(forward = true)
            val vert = findScrollableNode(forward = true)
            if (horiz == null && vert == null) {
                // スクロール可能な要素が存在しない（画面末尾）: 端音を鳴らして最後の要素にとどまる！
                service.soundHelper?.playLastItemEdgeSound()
                return
            }

            val isHorizontal = horiz != null && (isCurrentInWorkspace || vert == null)
            val oldFocus = currentFocus
            service.scrollPageForward { success ->
                if (success) {
                    val newNodes = collectAccessibleNodes()
                    if (newNodes.isNotEmpty()) {
                        // 次のページ: 新しいページの最初のワークスペース項目または新規要素へ！
                        val targetNode = service.findBestVisibleNodeAfterScroll(newNodes, forward = true, horizontal = isHorizontal)
                            ?: newNodes.firstOrNull { n -> isWorkspaceNode(n) && (oldFocus == null || !evaluator.isSameNode(n, oldFocus)) }
                            ?: newNodes.firstOrNull { n -> isWorkspaceNode(n) }
                            ?: newNodes.firstOrNull { n -> oldFocus == null || !evaluator.isSameNode(n, oldFocus) }
                            ?: newNodes[0]
                        lastFocusedNodeIndex = findCurrentNodeIndex(newNodes, targetNode).coerceAtLeast(0)
                        setFocusAndShowOnScreen(targetNode)
                        service.announceNode(targetNode, android.speech.tts.TextToSpeech.QUEUE_ADD)
                    }
                } else {
                    service.soundHelper?.playLastItemEdgeSound()
                }
            }
            return
        } else if (!forward && targetIndex < 0) {
            val horiz = findHorizontalScrollableNode(forward = false)
            val vert = findScrollableNode(forward = false)
            if (horiz == null && vert == null) {
                // スクロール可能な要素が存在しない（画面先頭）: 端音を鳴らして先頭要素にとどまる！
                service.soundHelper?.playFirstItemEdgeSound()
                return
            }

            val isHorizontal = horiz != null && (isCurrentInWorkspace || vert == null)
            val oldFocus = currentFocus
            service.scrollPageBackward { success ->
                if (success) {
                    val newNodes = collectAccessibleNodes()
                    if (newNodes.isNotEmpty()) {
                        // 前のページ: 前のページの最後のワークスペース項目または新規要素へ！
                        val targetNode = service.findBestVisibleNodeAfterScroll(newNodes, forward = false, horizontal = isHorizontal)
                            ?: newNodes.lastOrNull { n -> isWorkspaceNode(n) && (oldFocus == null || !evaluator.isSameNode(n, oldFocus)) }
                            ?: newNodes.lastOrNull { n -> isWorkspaceNode(n) }
                            ?: newNodes.lastOrNull { n -> oldFocus == null || !evaluator.isSameNode(n, oldFocus) }
                            ?: newNodes[newNodes.size - 1]
                        lastFocusedNodeIndex = findCurrentNodeIndex(newNodes, targetNode).coerceAtLeast(0)
                        setFocusAndShowOnScreen(targetNode)
                        service.announceNode(targetNode, android.speech.tts.TextToSpeech.QUEUE_ADD)
                    }
                } else {
                    service.soundHelper?.playFirstItemEdgeSound()
                }
            }
            return
        }

        val safeTargetIndex = targetIndex.coerceIn(0, nodes.size - 1)
        val targetNode = nodes[safeTargetIndex]

        lastFocusedNodeIndex = safeTargetIndex
        Log.i(TAG, "navigateLinearFocus: moving to index $safeTargetIndex / ${nodes.size - 1} (${evaluator.getNodeText(targetNode)})")

        currentFocus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        setFocusAndShowOnScreen(targetNode)

        if (safeTargetIndex == 0) {
            service.soundHelper?.playFirstItemEdgeSound()
        } else if (safeTargetIndex == nodes.size - 1) {
            service.soundHelper?.playLastItemEdgeSound()
        } else {
            service.soundHelper?.playFocusMove()
        }

        service.announceNode(targetNode)
    }

    fun setFocusAndShowOnScreen(targetNode: AccessibilityNodeInfo) {
        try {
            targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            var p = targetNode.parent
            while (p != null) {
                p.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                p = p.parent
            }
        } catch (_: Exception) {}

        val focused = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (!focused) {
            var handled = false
            for (i in 0 until targetNode.childCount) {
                val c = targetNode.getChild(i) ?: continue
                if (c.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {
                    handled = true
                    break
                }
            }
            if (!handled) {
                var p = targetNode.parent
                while (p != null) {
                    if (p.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {
                        break
                    }
                    p = p.parent
                }
            }
        }
    }

    fun findScrollableNode(forward: Boolean): AccessibilityNodeInfo? {
        val focusNode = service.getAccessibilityFocusedNode() ?: service.rootInActiveWindow ?: return null
        var current: AccessibilityNodeInfo? = focusNode
        while (current != null) {
            if (canScroll(current, forward)) return current
            current = current.parent
        }
        val roots = getAllRoots()
        for (r in roots) {
            val res = findFirstScrollableChild(r, forward)
            if (res != null) return res
        }
        return null
    }

    fun findHorizontalScrollableNode(forward: Boolean): AccessibilityNodeInfo? {
        // 1. フォーカスノードの祖先階層探索 (現在操作中のアプリ・コンテナを最優先！)
        val focusNode = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: service.getAccessibilityFocusedNode()
        var current: AccessibilityNodeInfo? = focusNode
        while (current != null) {
            if (canScrollHorizontal(current, forward)) return current
            current = current.parent
        }

        // 2. アクティブウィンドウの直接探索 (Pixel Launcher / アプリ等)
        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            val pkg = activeRoot.packageName?.toString() ?: ""
            if (pkg.contains("launcher")) {
                val wsById = activeRoot.findAccessibilityNodeInfosByViewId("$pkg:id/workspace")
                if (wsById.isNotEmpty()) return wsById[0]
                val genericWs = activeRoot.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/workspace")
                if (genericWs.isNotEmpty()) return genericWs[0]
                val launcher3Ws = activeRoot.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/workspace")
                if (launcher3Ws.isNotEmpty()) return launcher3Ws[0]
            }
            val directNode = findFirstHorizontalScrollableChild(activeRoot, forward)
            if (directNode != null) return directNode
            if (canScrollHorizontal(activeRoot, forward)) return activeRoot
        }

        // 3. 全ウィンドウツリー探索
        val roots = getAllRoots()
        for (root in roots) {
            val directNode = findFirstHorizontalScrollableChild(root, forward)
            if (directNode != null) return directNode
            if (canScrollHorizontal(root, forward)) return root
        }

        return null
    }

    fun canScrollHorizontal(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val pageAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id
        }
        val scrollAction = if (forward) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id else -1
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id else -1
        }
        if (node.actionList.any { it.id == pageAction || (scrollAction != -1 && it.id == scrollAction) }) return true

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        if (viewId.contains("workspace") || className.contains("workspace") || className.contains("pagedview") || className.contains("viewpager") || className.contains("horizontal") || className.contains("tabrow")) return true

        return false
    }

    fun performHorizontalScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        // Pixel Launcher / ViewPager / Android View システムにおける水平スクロール:
        // forward=true (次ページ/右のコンテンツへ): ACTION_SCROLL_FORWARD または ACTION_PAGE_LEFT / ACTION_SCROLL_LEFT
        // forward=false (前ページ/左のコンテンツへ): ACTION_SCROLL_BACKWARD または ACTION_PAGE_RIGHT / ACTION_SCROLL_RIGHT
        val stdScroll = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val pageAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id
        }
        val fallbackPageAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id
        }
        val scrollAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        } else -1

        // 1. 標準スクロール
        if (node.actionList.any { it.id == stdScroll } && node.performAction(stdScroll)) return true
        if (node.actionList.any { it.id == pageAction } && node.performAction(pageAction)) return true
        if (scrollAction != -1 && node.actionList.any { it.id == scrollAction } && node.performAction(scrollAction)) return true
        if (node.actionList.any { it.id == fallbackPageAction } && node.performAction(fallbackPageAction)) return true

        if (node.performAction(stdScroll)) return true
        if (node.performAction(pageAction)) return true
        if (scrollAction != -1 && node.performAction(scrollAction)) return true
        if (node.performAction(fallbackPageAction)) return true

        // 親ノードやルートへのフォールバック
        var parent = node.parent
        while (parent != null) {
            if (parent.performAction(stdScroll) || parent.performAction(pageAction) || (scrollAction != -1 && parent.performAction(scrollAction)) || parent.performAction(fallbackPageAction)) {
                return true
            }
            parent = parent.parent
        }

        val root = service.rootInActiveWindow
        if (root != null && (root.performAction(stdScroll) || root.performAction(pageAction) || (scrollAction != -1 && root.performAction(scrollAction)) || root.performAction(fallbackPageAction))) {
            return true
        }

        return false
    }

    private fun findFirstHorizontalScrollableChild(node: AccessibilityNodeInfo, forward: Boolean): AccessibilityNodeInfo? {
        if (canScrollHorizontal(node, forward)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findFirstHorizontalScrollableChild(child, forward)
            if (res != null) return res
        }
        return null
    }

    private fun canScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val actionId = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val altActionId = if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val pageActionId = if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id
        if (node.actionList.any { it.id == actionId || it.id == altActionId || it.id == pageActionId }) return true

        if (node.isScrollable) return true
        val className = node.className?.toString()?.lowercase() ?: ""
        return className.contains("scroll") || className.contains("recycler") || className.contains("list") || className.contains("grid") || className.contains("web")
    }

    fun performScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val altAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        }
        val standardAction = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val pageAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id
        }

        if (node.actionList.any { it.id == altAction } && node.performAction(altAction)) return true
        if (node.actionList.any { it.id == standardAction } && node.performAction(standardAction)) return true
        if (node.actionList.any { it.id == pageAction } && node.performAction(pageAction)) return true
        if (node.performAction(standardAction)) return true
        if (node.performAction(altAction)) return true

        // 親ノードへのフォールバック
        var parent = node.parent
        while (parent != null) {
            if (parent.performAction(altAction) || parent.performAction(standardAction) || parent.performAction(pageAction)) {
                return true
            }
            parent = parent.parent
        }

        // ルートノードへのフォールバック
        val root = service.rootInActiveWindow
        if (root != null && (root.performAction(altAction) || root.performAction(standardAction) || root.performAction(pageAction))) {
            return true
        }

        return false
    }

    private fun findFirstScrollableChild(node: AccessibilityNodeInfo, forward: Boolean): AccessibilityNodeInfo? {
        if (canScroll(node, forward)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findFirstScrollableChild(child, forward)
            if (res != null) return res
        }
        return null
    }
}
