package com.shinji.serena.navigation

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.R

/**
 * ContextualActionHintHelper
 *
 * ノードの各種プロパティ（viewId, class, text, contentDescription, action labels, parent/app context, role）
 * を多角的に総合評価し、単調な「ダブルタップでアクティベート」ではなく、対象の操作内容に応じた
 * インテリジェントで文脈に即した操作ヒント（Contextual Action / Usage Hint）を動的に生成するエンジン。
 *
 * 全文字列は strings.xml に完全外部化されており、全6言語（日本語, 英語, スペイン語, タガログ語, オランダ語, デフォルト）に対応。
 */
class ContextualActionHintHelper(private val context: Context) {

    enum class ActionHintType(val stringResId: Int) {
        NONE(0),
        LAUNCH_APP(R.string.hint_action_launch_app),
        POWER_OFF(R.string.hint_action_power_off),
        SHUTDOWN(R.string.hint_action_shutdown),
        RESTART(R.string.hint_action_restart),
        SEND(R.string.hint_action_send),
        DELETE(R.string.hint_action_delete),
        SEARCH(R.string.hint_action_search),
        CLOSE(R.string.hint_action_close),
        BACK(R.string.hint_action_back),
        TOGGLE(R.string.hint_action_toggle),
        CHECK(R.string.hint_action_check),
        CALL(R.string.hint_action_call),
        END_CALL(R.string.hint_action_end_call),
        EDIT_TEXT(R.string.hint_action_edit_text),
        PLAY(R.string.hint_action_play),
        PAUSE(R.string.hint_action_pause),
        OPEN_LINK(R.string.hint_action_open_link),
        EXPAND(R.string.hint_action_expand),
        COLLAPSE(R.string.hint_action_collapse),
        LONG_CLICK(R.string.hint_action_long_click),
        CUSTOM(R.string.hint_action_custom_fmt),
        ACTIVATE(R.string.hint_action_activate)
    }

    /**
     * 指定されたノードに対する最適な操作ヒント文字列を生成して返します。
     * ノードが操作可能（アクション可能）でない場合は空文字列を返します。
     */
    fun getActionHint(
        node: AccessibilityNodeInfo?,
        text: String = "",
        role: String = ""
    ): String {
        if (node == null) return ""

        val actions = node.actionList
        val hasClick = actions.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id }
        val hasLongClick = actions.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.id }
        val hasExpand = actions.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id }
        val hasCollapse = actions.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id }

        var isParentClickable = false
        try {
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.childCount in 1..3) {
                isParentClickable = true
            }
        } catch (_: Exception) {}

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        val pkg = node.packageName?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val directText = node.text?.toString() ?: ""

        val actionLabels = mutableListOf<String>()
        var customClickLabel: String? = null
        for (action in actions) {
            val label = action.label?.toString()?.trim()
            if (!label.isNullOrEmpty()) {
                actionLabels.add(label)
                if (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id) {
                    customClickLabel = label
                }
            }
        }

        val hintType = evaluateActionHintType(
            isClickable = node.isClickable || isParentClickable,
            isCheckable = node.isCheckable,
            isEditable = node.isEditable,
            isLongClickable = node.isLongClickable,
            hasClickAction = hasClick,
            hasExpandAction = hasExpand,
            hasCollapseAction = hasCollapse,
            pkg = pkg,
            className = className,
            viewId = viewId,
            text = if (text.isNotEmpty()) text else directText,
            contentDesc = contentDesc,
            actionLabels = actionLabels,
            role = role,
            roleWidget = context.getString(R.string.role_widget),
            roleSwitch = context.getString(R.string.role_switch),
            roleCheckbox = context.getString(R.string.role_checkbox),
            roleRadio = context.getString(R.string.role_radio_button),
            roleEditText = context.getString(R.string.role_edit_text)
        )

        return when (hintType) {
            ActionHintType.NONE -> ""
            ActionHintType.CUSTOM -> {
                if (!customClickLabel.isNullOrBlank()) {
                    context.getString(R.string.hint_action_custom_fmt, customClickLabel)
                } else {
                    context.getString(R.string.hint_action_activate)
                }
            }
            else -> context.getString(hintType.stringResId)
        }
    }

    companion object {

        /**
         * 純粋関数ロジック：ノードの各属性から ActionHintType を決定
         * Android Context 不要のため、単体テスト・網羅検証が安全に行えます。
         */
        fun evaluateActionHintType(
            isClickable: Boolean,
            isCheckable: Boolean,
            isEditable: Boolean,
            isLongClickable: Boolean,
            hasClickAction: Boolean,
            hasExpandAction: Boolean,
            hasCollapseAction: Boolean,
            pkg: String,
            className: String,
            viewId: String,
            text: String,
            contentDesc: String,
            actionLabels: List<String>,
            role: String,
            roleWidget: String = "",
            roleSwitch: String = "",
            roleCheckbox: String = "",
            roleRadio: String = "",
            roleEditText: String = ""
        ): ActionHintType {
            val isActionable = isClickable || isCheckable || isEditable || isLongClickable ||
                    hasClickAction || hasExpandAction || hasCollapseAction

            if (!isActionable) {
                return ActionHintType.NONE
            }

            val lowViewId = viewId.lowercase()
            val lowPkg = pkg.lowercase()
            val lowText = text.lowercase()
            val lowDesc = contentDesc.lowercase()
            val lowRole = role.lowercase()

            val combinedTarget = (
                "$lowText $lowDesc $lowViewId ${className.lowercase()} " +
                actionLabels.joinToString(" ") { it.lowercase() }
            ).trim()

            // 1. アプリ起動判定（ホーム画面・ランチャー）
            if (isLauncherApp(lowPkg, className, lowViewId, lowRole, roleWidget, text, isClickable)) {
                return ActionHintType.LAUNCH_APP
            }

            // 2. シャットダウン / 電源OFF判定
            if (isShutdownText(combinedTarget)) {
                return ActionHintType.SHUTDOWN
            }
            if (isPowerOffText(combinedTarget)) {
                return ActionHintType.POWER_OFF
            }

            // 3. 再起動判定
            if (isRestartText(combinedTarget)) {
                return ActionHintType.RESTART
            }

            // 4. 通話終了判定（通常の発信より優先）
            if (isEndCallText(combinedTarget)) {
                return ActionHintType.END_CALL
            }

            // 5. 電話発信 / ダイヤル判定
            if (isCallText(combinedTarget)) {
                return ActionHintType.CALL
            }

            // 6. 送信判定
            if (isSendText(combinedTarget)) {
                return ActionHintType.SEND
            }

            // 7. 削除 / ごみ箱判定
            if (isDeleteText(combinedTarget)) {
                return ActionHintType.DELETE
            }

            // 8. 検索判定
            if (isSearchTarget(combinedTarget, className, lowViewId)) {
                return ActionHintType.SEARCH
            }

            // 9. 閉じる / 破棄判定
            if (isCloseTarget(combinedTarget, lowViewId)) {
                return ActionHintType.CLOSE
            }

            // 10. 戻る判定
            if (isBackTarget(combinedTarget, lowViewId)) {
                return ActionHintType.BACK
            }

            // 11. 一時停止 / 再生判定（player_pause誤検知防止のため一時停止を優先）
            if (isPauseTarget(combinedTarget)) {
                return ActionHintType.PAUSE
            }
            if (isPlayTarget(combinedTarget)) {
                return ActionHintType.PLAY
            }

            // 12. テキスト入力フィールド判定
            if (isEditTextTarget(isEditable, className, lowRole, roleEditText)) {
                return ActionHintType.EDIT_TEXT
            }

            // 13. スイッチ / トグル判定
            if (isSwitchTarget(className, lowRole, roleSwitch)) {
                return ActionHintType.TOGGLE
            }

            // 14. チェックボックス / ラジオボタン判定
            if (isCheckTarget(isCheckable, className, lowRole, roleCheckbox, roleRadio, roleSwitch)) {
                return ActionHintType.CHECK
            }

            // 15. 展開 / 折りたたみ判定
            if (hasExpandAction) {
                return ActionHintType.EXPAND
            }
            if (hasCollapseAction) {
                return ActionHintType.COLLAPSE
            }

            // 16. リンク判定
            if (isLinkTarget(className, lowRole, text)) {
                return ActionHintType.OPEN_LINK
            }

            // 17. カスタムクリックラベル判定
            if (actionLabels.isNotEmpty()) {
                val candidate = actionLabels.firstOrNull { it.isNotBlank() }
                if (candidate != null) {
                    return ActionHintType.CUSTOM
                }
            }

            // 18. 長押しのみ可能なコントロール
            if (!isClickable && !hasClickAction && isLongClickable) {
                return ActionHintType.LONG_CLICK
            }

            // 19. フォールバック
            return ActionHintType.ACTIVATE
        }

        private fun isLauncherApp(
            pkg: String,
            className: String,
            viewId: String,
            role: String,
            roleWidget: String,
            text: String,
            isClickable: Boolean
        ): Boolean {
            val isLauncherPkg = pkg == "com.android.launcher" ||
                    pkg == "com.google.android.apps.nexuslauncher" ||
                    pkg == "com.sec.android.app.launcher" ||
                    pkg == "com.mi.android.globallauncher" ||
                    pkg.contains("launcher") ||
                    pkg.contains("home")

            if (!isLauncherPkg) return false

            if (roleWidget.isNotEmpty() && role.contains(roleWidget, ignoreCase = true)) return false
            if (viewId.contains("widget") || className.contains("Widget", ignoreCase = true)) return false
            if (viewId.contains("qsb") || viewId.contains("search")) return false

            val isAppIconClass = className.contains("BubbleTextView", ignoreCase = true) ||
                    className.contains("LauncherIcon", ignoreCase = true) ||
                    className.contains("FolderIcon", ignoreCase = true) ||
                    className.contains("AppItem", ignoreCase = true)

            val isAppViewId = viewId.contains("icon") ||
                    viewId.contains("app") ||
                    viewId.contains("workspace") ||
                    viewId.contains("application_item")

            return (isAppIconClass || isAppViewId || (isClickable && text.isNotBlank()))
        }

        private fun isShutdownText(target: String): Boolean {
            return target.contains("シャットダウン") ||
                    target.contains("shutdown") ||
                    target.contains("i-shutdown")
        }

        private fun isPowerOffText(target: String): Boolean {
            return target.contains("電源を切る") ||
                    target.contains("電源オフ") ||
                    target.contains("power off") ||
                    target.contains("turn off") ||
                    target.contains("apagar") ||
                    target.contains("patayin") ||
                    target.contains("uitschakelen") ||
                    target.contains("global_actions_power") ||
                    target.contains("power_off")
        }

        private fun isRestartText(target: String): Boolean {
            return target.contains("再起動") ||
                    target.contains("リスタート") ||
                    target.contains("restart") ||
                    target.contains("reboot") ||
                    target.contains("reiniciar") ||
                    target.contains("muling simulan") ||
                    target.contains("herstarten") ||
                    target.contains("global_actions_restart")
        }

        private fun isEndCallText(target: String): Boolean {
            return target.contains("通話を終了") ||
                    target.contains("電話を切る") ||
                    target.contains("終話") ||
                    target.contains("切断") ||
                    target.contains("end call") ||
                    target.contains("hang up") ||
                    target.contains("hangup") ||
                    target.contains("colgar") ||
                    target.contains("tapusin ang tawag") ||
                    target.contains("ibaba ang tawag") ||
                    target.contains("gesprek beëindigen") ||
                    target.contains("disconnect_call") ||
                    target.contains("btn_end") ||
                    target.contains("end_call")
        }

        private fun isCallText(target: String): Boolean {
            return target.contains("発信") ||
                    target.contains("電話をかける") ||
                    target.contains("通話開始") ||
                    target.contains("ダイヤル") ||
                    target.contains("dial") ||
                    target.contains("make call") ||
                    target.contains("llamar") ||
                    target.contains("tumawag") ||
                    target.contains("bellen") ||
                    target.contains("call_button") ||
                    target.contains("btn_call") ||
                    (target.contains("call") && !target.contains("caller") && !target.contains("callback"))
        }

        private fun isSendText(target: String): Boolean {
            return target.contains("送信") ||
                    target.contains("send") ||
                    target.contains("enviar") ||
                    target.contains("ipadala") ||
                    target.contains("verzenden") ||
                    target.contains("btn_send") ||
                    target.contains("send_button") ||
                    target.contains("composer_send")
        }

        private fun isDeleteText(target: String): Boolean {
            return target.contains("削除") ||
                    target.contains("ごみ箱") ||
                    target.contains("ゴミ箱") ||
                    target.contains("消去") ||
                    target.contains("delete") ||
                    target.contains("remove") ||
                    target.contains("trash") ||
                    target.contains("eliminar") ||
                    target.contains("borrar") ||
                    target.contains("burahin") ||
                    target.contains("verwijderen") ||
                    target.contains("btn_delete") ||
                    target.contains("action_delete")
        }

        private fun isSearchTarget(target: String, className: String, viewId: String): Boolean {
            if (className.contains("SearchView", ignoreCase = true) || viewId.contains("search")) {
                return true
            }
            return target.contains("検索") ||
                    target.contains("さがす") ||
                    target.contains("search") ||
                    target.contains("buscar") ||
                    target.contains("maghanap") ||
                    target.contains("zoeken") ||
                    target.contains("btn_search")
        }

        private fun isCloseTarget(target: String, viewId: String): Boolean {
            if (viewId.contains("close") || viewId.contains("dismiss") || viewId.contains("btn_close")) {
                return true
            }
            return target.contains("閉じる") ||
                    target.contains("破棄") ||
                    target.contains("dismiss") ||
                    target.contains("close") ||
                    target.contains("cerrar") ||
                    target.contains("isara") ||
                    target.contains("sluiten")
        }

        private fun isBackTarget(target: String, viewId: String): Boolean {
            if (viewId.contains("back") || viewId.contains("up_button") || viewId.contains("action_bar_up")) {
                return true
            }
            return target.contains("戻る") ||
                    target.contains("前の画面") ||
                    target.contains("back") ||
                    target.contains("navigate up") ||
                    target.contains("regresar") ||
                    target.contains("bumalik") ||
                    target.contains("terug")
        }

        private fun isPlayTarget(target: String): Boolean {
            if (isPauseTarget(target)) return false
            return target.contains("再生") ||
                    target.contains("reproducir") ||
                    target.contains("i-play") ||
                    target.contains("afspelen") ||
                    target.contains("btn_play") ||
                    target.contains("play_button") ||
                    Regex("\\bplay\\b").containsMatchIn(target)
        }

        private fun isPauseTarget(target: String): Boolean {
            return target.contains("一時停止") ||
                    target.contains("pause") ||
                    target.contains("pausar") ||
                    target.contains("i-pause") ||
                    target.contains("pauzeren") ||
                    target.contains("btn_pause")
        }

        private fun isEditTextTarget(
            isEditable: Boolean,
            className: String,
            role: String,
            roleEditText: String
        ): Boolean {
            if (isEditable || className.contains("EditText", ignoreCase = true)) {
                return true
            }
            return (roleEditText.isNotEmpty() && role.contains(roleEditText, ignoreCase = true)) ||
                    role.contains("entry", ignoreCase = true) ||
                    role.contains("edit", ignoreCase = true) ||
                    role.contains("入力", ignoreCase = true)
        }

        private fun isSwitchTarget(
            className: String,
            role: String,
            roleSwitch: String
        ): Boolean {
            if (className.contains("Switch", ignoreCase = true) ||
                className.contains("ToggleButton", ignoreCase = true) ||
                className.contains("Toggle", ignoreCase = true)
            ) {
                return true
            }
            return roleSwitch.isNotEmpty() && role.contains(roleSwitch, ignoreCase = true)
        }

        private fun isCheckTarget(
            isCheckable: Boolean,
            className: String,
            role: String,
            roleCheckbox: String,
            roleRadio: String,
            roleSwitch: String
        ): Boolean {
            if (className.contains("CheckBox", ignoreCase = true) ||
                className.contains("RadioButton", ignoreCase = true)
            ) {
                return true
            }
            if ((roleCheckbox.isNotEmpty() && role.contains(roleCheckbox, ignoreCase = true)) ||
                (roleRadio.isNotEmpty() && role.contains(roleRadio, ignoreCase = true))
            ) {
                return true
            }
            return isCheckable && !isSwitchTarget(className, role, roleSwitch)
        }

        private fun isLinkTarget(className: String, role: String, text: String): Boolean {
            if (className.contains("Link", ignoreCase = true) || className.contains("URL", ignoreCase = true)) {
                return true
            }
            if (role.contains("link", ignoreCase = true) || role.contains("リンク", ignoreCase = true)) {
                return true
            }
            val trimmed = text.trim()
            return trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("www.")
        }
    }
}
