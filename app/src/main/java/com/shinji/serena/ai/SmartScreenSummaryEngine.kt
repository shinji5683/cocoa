package com.shinji.serena.ai

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.shinji.serena.SerenaScreenReaderService

/**
 * SmartScreenSummaryEngine
 * 全盲ユーザー向け究極の具体性：
 * 全項目数、現在フォーカス位置（何番目/全何項目）、全項目の種類と詳細リスト、
 * Gemini Nano による自然言語シーン解説を完備した最高峰画面要約エンジン。
 */
class SmartScreenSummaryEngine(private val service: SerenaScreenReaderService) {

    companion object {
        private const val TAG = "SmartScreenSummaryEngine"
    }

    private val nanoEngine = GeminiNanoEngine(service)

    data class DetailedNodeItem(
        val index: Int,
        val type: String,
        val label: String,
        val state: String,
        val isFocused: Boolean
    )

    fun generateDetailedSummary(snapshotRoot: AccessibilityNodeInfo? = null, snapshotFocusedNode: AccessibilityNodeInfo? = null): String {
        var root: AccessibilityNodeInfo? = snapshotRoot

        if (root == null) {
            val windows = try { service.windows } catch (e: Exception) { null }
            if (!windows.isNullOrEmpty()) {
                val appWindow = windows.firstOrNull { 
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused 
                } ?: windows.firstOrNull { 
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION 
                }
                root = appWindow?.root
            }
        }

        if (root == null) {
            root = try { service.rootInActiveWindow } catch (e: Exception) { null }
        }

        if (root == null) {
            val focused = snapshotFocusedNode ?: service.getAccessibilityFocusedNode()
            var current = focused
            while (current?.parent != null) {
                current = current.parent
            }
            root = current
        }

        if (root == null) {
            return "画面の情報を取得できませんでした。もう一度お試しください。"
        }

        val focusedNode = snapshotFocusedNode ?: service.getAccessibilityFocusedNode()

        val packageName = root.packageName?.toString() ?: ""
        val appName = getAppName(packageName)

        val allItems = mutableListOf<DetailedNodeItem>()
        val headings = mutableListOf<String>()
        val buttons = mutableListOf<String>()
        val inputs = mutableListOf<String>()
        var screenTitle = ""
        var focusedIndex = -1
        var focusedItemLabel = ""

        fun collectInteractiveNodes(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim() ?: ""
            val className = node.className?.toString() ?: ""

            if (text.isNotEmpty()) {
                val isClickable = node.isClickable || className.contains("Button")
                val isEditable = node.isEditable || className.contains("EditText")
                val isCheckable = node.isCheckable || className.contains("CheckBox") || className.contains("Switch")
                val isHeading = node.isHeading || (className.contains("TextView") && text.length < 25 && (node.parent?.childCount ?: 0) < 3)

                val typeStr = when {
                    isHeading -> {
                        if (screenTitle.isEmpty() && text.length < 30) screenTitle = text
                        headings.add(text)
                        "見出し"
                    }
                    isCheckable -> "スイッチ"
                    isEditable -> {
                        inputs.add(text)
                        "入力欄"
                    }
                    isClickable -> {
                        buttons.add(text)
                        "ボタン"
                    }
                    else -> "テキスト"
                }

                val stateStr = when {
                    isCheckable -> if (node.isChecked) "（オン）" else "（オフ）"
                    isEditable -> if (text.isEmpty() || text == node.hintText?.toString()) "（未入力）" else "（入力済み）"
                    node.isSelected -> "（選択中）"
                    else -> ""
                }

                val isCurrentFocused = (focusedNode != null && (node == focusedNode || node.isAccessibilityFocused))
                val itemIndex = allItems.size + 1

                val item = DetailedNodeItem(
                    index = itemIndex,
                    type = typeStr,
                    label = text,
                    state = stateStr,
                    isFocused = isCurrentFocused
                )

                allItems.add(item)

                if (isCurrentFocused) {
                    focusedIndex = itemIndex
                    focusedItemLabel = text
                }
            }

            for (i in 0 until node.childCount) {
                collectInteractiveNodes(node.getChild(i))
            }
        }

        collectInteractiveNodes(root)

        if (allItems.isEmpty()) {
            return "「${appName}」の画面です。操作可能な項目は見つかりませんでした。"
        }

        return nanoEngine.summarizeScreen(
            appName = appName,
            screenTitle = screenTitle,
            itemCount = allItems.size,
            headings = headings,
            buttons = buttons,
            inputs = inputs,
            focusedItem = focusedItemLabel,
            focusedIndex = focusedIndex
        )
    }

    private fun getAppName(packageName: String): String {
        if (packageName.isEmpty()) return "現在のアプリ"
        return try {
            val pm = service.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
