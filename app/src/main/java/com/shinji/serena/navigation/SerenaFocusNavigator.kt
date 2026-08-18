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
        val activeRoot = service.rootInActiveWindow
        val activeWindowId = activeRoot?.windowId ?: -1

        val wins = service.windows
        if (!wins.isNullOrEmpty()) {
            val sortedWins = wins.sortedWith(Comparator { w1, w2 ->
                val p1 = getWindowPriority(w1, activeWindowId)
                val p2 = getWindowPriority(w2, activeWindowId)
                p1.compareTo(p2)
            })

            for (w in sortedWins) {
                val r = w.root ?: continue
                if (list.none { it.windowId == r.windowId || it == r }) {
                    list.add(r)
                }
            }
        }

        if (list.isEmpty() && activeRoot != null) {
            list.add(activeRoot)
        }
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
        if (evaluator.isFocusableTarget(node)) {
            if (list.none { it == node || (it.windowId == node.windowId && evaluator.isSameNode(it, node)) }) {
                list.add(node)
            }
            if (!evaluator.isContainerNode(node)) {
                return
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseTree(child, list)
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

        val currentIndex = findCurrentNodeIndex(nodes, currentFocus)

        val targetIndex = if (forward) {
            if (currentIndex < 0) 0 else currentIndex + 1
        } else {
            if (currentIndex < 0) nodes.size - 1 else currentIndex - 1
        }

        if (forward && targetIndex >= nodes.size) {
            service.scrollPageForward {
                val newNodes = collectAccessibleNodes()
                if (newNodes.isNotEmpty()) {
                    val targetIdx = (nodes.size - 1).coerceIn(0, newNodes.size - 1)
                    val targetNode = newNodes[targetIdx]
                    lastFocusedNodeIndex = targetIdx
                    setFocusAndShowOnScreen(targetNode)
                } else {
                    service.speak("下へスクロールしました", TextToSpeech.QUEUE_FLUSH)
                }
            }
            return
        } else if (!forward && targetIndex < 0) {
            service.scrollPageBackward {
                val newNodes = collectAccessibleNodes()
                if (newNodes.isNotEmpty()) {
                    val targetNode = newNodes[0]
                    lastFocusedNodeIndex = 0
                    setFocusAndShowOnScreen(targetNode)
                } else {
                    service.speak("上へスクロールしました", TextToSpeech.QUEUE_FLUSH)
                }
            }
            return
        }

        val safeTargetIndex = targetIndex.coerceIn(0, nodes.size - 1)
        val targetNode = nodes[safeTargetIndex]

        val rect = Rect()
        targetNode.getBoundsInScreen(rect)
        val dm = service.resources.displayMetrics
        val isOffScreen = (rect.bottom > dm.heightPixels - 60) || (rect.top < 60) || rect.isEmpty

        lastFocusedNodeIndex = safeTargetIndex
        Log.i(TAG, "navigateLinearFocus: moving to index $safeTargetIndex / ${nodes.size - 1} (${evaluator.getNodeText(targetNode)}) isOffScreen=$isOffScreen")

        if (isOffScreen) {
            if (forward) {
                service.scrollPageForward {
                    setFocusAndShowOnScreen(targetNode)
                }
            } else {
                service.scrollPageBackward {
                    setFocusAndShowOnScreen(targetNode)
                }
            }
            return
        }

        setFocusAndShowOnScreen(targetNode)
    }

    fun setFocusAndShowOnScreen(targetNode: AccessibilityNodeInfo) {
        try {
            targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            targetNode.parent?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
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
        val root = service.rootInActiveWindow ?: return null
        
        // 1. 全ツリーから workspace / pagedview / viewpager / horizontal scrollable を完全探索
        val directNode = findFirstHorizontalScrollableChild(root, forward)
        if (directNode != null) {
            return directNode
        }

        val focusNode = service.getAccessibilityFocusedNode() ?: root
        var current: AccessibilityNodeInfo? = focusNode
        while (current != null) {
            if (canScrollHorizontal(current, forward)) return current
            current = current.parent
        }
        return if (canScrollHorizontal(root, forward)) root else null
    }

    fun canScrollHorizontal(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        if (viewId.contains("workspace") || className.contains("workspace") || className.contains("pagedview") || className.contains("viewpager")) return true

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
        
        return node.actionList.any { it.id == pageAction || it.id == scrollAction } || (node.isScrollable && (className.contains("viewpager") || className.contains("horizontal") || className.contains("scroll")))
    }

    fun performHorizontalScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val pageAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id
        }
        if (node.actionList.any { it.id == pageAction }) {
            if (node.performAction(pageAction)) return true
        }

        val scrollAction = if (forward) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (node.actionList.any { it.id == scrollAction }) {
            if (node.performAction(scrollAction)) return true
        }

        val fallback = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node.performAction(fallback)) return true

        // 親ノードやルートへのフォールバック
        var parent = node.parent
        while (parent != null) {
            if (parent.performAction(pageAction) || parent.performAction(scrollAction) || parent.performAction(fallback)) {
                return true
            }
            parent = parent.parent
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
        if (node.isScrollable) return true
        val className = node.className?.toString()?.lowercase() ?: ""
        if (className.contains("scroll") || className.contains("recycler") || className.contains("list") || className.contains("grid") || className.contains("web")) return true

        val actionId = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val altActionId = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        }
        return node.actionList.any { it.id == actionId || it.id == altActionId }
    }

    fun performScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val altAction = if (forward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        }
        if (node.actionList.any { it.id == altAction }) {
            if (node.performAction(altAction)) return true
        }
        val standardAction = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (node.performAction(standardAction)) return true

        // 親ノードへのフォールバック
        var parent = node.parent
        while (parent != null) {
            if (parent.performAction(altAction) || parent.performAction(standardAction)) {
                return true
            }
            parent = parent.parent
        }

        // ルートノードへのフォールバック
        val root = service.rootInActiveWindow
        if (root != null && (root.performAction(altAction) || root.performAction(standardAction))) {
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
