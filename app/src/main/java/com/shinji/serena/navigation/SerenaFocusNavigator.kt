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
        private set

    fun resetIndex() {
        lastFocusedNodeIndex = -1
    }

    fun getAllRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        
        // 1. 最優先: アクティブウィンドウのルートノード
        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            list.add(activeRoot)
        }

        // 2. 現在フォーカスされているノードのルート
        try {
            val focusNode = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            var p = focusNode
            while (p?.parent != null) {
                p = p.parent
            }
            if (p != null && list.none { it == p || it.windowId == p.windowId }) {
                list.add(p)
            }
        } catch (_: Exception) {}

        // 3. windows リストからのルート探索
        try {
            val wins = service.windows
            if (!wins.isNullOrEmpty()) {
                val activeWindowId = activeRoot?.windowId ?: -1
                val sortedWins = wins.sortedWith(Comparator { w1, w2 ->
                    val p1 = getWindowPriority(w1, activeWindowId)
                    val p2 = getWindowPriority(w2, activeWindowId)
                    p1.compareTo(p2)
                })

                for (w in sortedWins) {
                    val r = w.root ?: continue
                    if (list.none { it == r || it.windowId == r.windowId }) {
                        list.add(r)
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun getWindowPriority(w: AccessibilityWindowInfo, activeWindowId: Int): Int {
        if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return 0
        }
        if (w.id == activeWindowId || w.isFocused) {
            return 1
        }
        return when (w.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> 2
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> 3
            else -> 4
        }
    }

    fun collectAccessibleNodes(root: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val roots = if (root != null) listOf(root) else getAllRoots()
        val list = mutableListOf<AccessibilityNodeInfo>()
        for (r in roots) {
            traverseTree(r, list)
        }
        return list
    }

    private fun traverseTree(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val isTarget = evaluator.isFocusableTarget(node)
        val hasFocusableChildren = evaluator.hasFocusableChildren(node)

        // 1. 子要素にフォーカス可能要素を持たない意味のあるノード（末端ノード/ボタン/テキスト等）なら登録
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

        if (forward && targetIndex >= nodes.size) {
            service.scrollPageForward {
                val newNodes = collectAccessibleNodes()
                if (newNodes.isNotEmpty()) {
                    val targetNode = newNodes[0]
                    lastFocusedNodeIndex = 0
                    setFocusAndShowOnScreen(targetNode)
                    service.announceNode(targetNode)
                } else {
                    service.soundHelper?.playLastItemEdgeSound()
                }
            }
            return
        } else if (!forward && targetIndex < 0) {
            service.scrollPageBackward {
                val newNodes = collectAccessibleNodes()
                if (newNodes.isNotEmpty()) {
                    val targetIdx = newNodes.size - 1
                    val targetNode = newNodes[targetIdx]
                    lastFocusedNodeIndex = targetIdx
                    setFocusAndShowOnScreen(targetNode)
                    service.announceNode(targetNode)
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
        val roots = getAllRoots()
        
        // 1. Pixel Launcher / Launcher3 の Workspace 直接高速探索
        for (r in roots) {
            val pkg = r.packageName?.toString() ?: ""
            val wsById = r.findAccessibilityNodeInfosByViewId("$pkg:id/workspace")
            if (wsById.isNotEmpty()) return wsById[0]
            val genericWs = r.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/workspace")
            if (genericWs.isNotEmpty()) return genericWs[0]
            val launcher3Ws = r.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/workspace")
            if (launcher3Ws.isNotEmpty()) return launcher3Ws[0]
        }

        // 2. フォーカスノードの祖先階層探索
        val focusNode = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: service.getAccessibilityFocusedNode()
        var current: AccessibilityNodeInfo? = focusNode
        while (current != null) {
            if (canScrollHorizontal(current, forward)) return current
            current = current.parent
        }

        // 3. 全ウィンドウツリー探索
        for (root in roots) {
            val directNode = findFirstHorizontalScrollableChild(root, forward)
            if (directNode != null) return directNode
            if (canScrollHorizontal(root, forward)) return root
        }

        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            if (canScrollHorizontal(activeRoot, forward)) return activeRoot
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (node.actionList.any { it.id == pageAction || it.id == scrollAction }) return true

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        if (viewId.contains("workspace") || className.contains("workspace") || className.contains("pagedview") || className.contains("viewpager") || className.contains("horizontal")) return true

        return node.isScrollable
    }

    fun performHorizontalScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
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

        if (node.actionList.any { it.id == pageAction } && node.performAction(pageAction)) return true
        if (scrollAction != -1 && node.actionList.any { it.id == scrollAction } && node.performAction(scrollAction)) return true
        if (node.performAction(pageAction)) return true
        if (scrollAction != -1 && node.performAction(scrollAction)) return true

        // 親ノードやルートへのフォールバック（水平ページアクションのみ厳密実行）
        var parent = node.parent
        while (parent != null) {
            if (parent.performAction(pageAction) || (scrollAction != -1 && parent.performAction(scrollAction))) {
                return true
            }
            parent = parent.parent
        }

        val root = service.rootInActiveWindow
        if (root != null && (root.performAction(pageAction) || (scrollAction != -1 && root.performAction(scrollAction)))) {
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
