package com.shinji.serena.navigation

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.R

/**
 * ContextualActionHintHelper
 *
 * ノードの各種プロパティ（viewId, class, text, contentDescription, action labels, parent/app context, role, enabled state）
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
     * 端末にインストールされているランチャーパッケージのキャッシュ
     */
    private val launcherPackages: Set<String> by lazy {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfos = context.packageManager.queryIntentActivities(homeIntent, 0)
            val pkgs = resolveInfos.mapNotNull { it.activityInfo?.packageName?.lowercase() }.toSet()
            if (pkgs.isNotEmpty()) pkgs else KNOWN_LAUNCHER_PACKAGES
        } catch (_: Exception) {
            KNOWN_LAUNCHER_PACKAGES
        }
    }

    /**
     * 指定されたパッケージがホームランチャーかどうか判定
     */
    fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lowPkg = pkg.lowercase()
        return lowPkg in launcherPackages ||
                lowPkg in KNOWN_LAUNCHER_PACKAGES ||
                lowPkg.endsWith(".launcher") ||
                lowPkg.contains("launcher3") ||
                (lowPkg.contains("launcher") && !lowPkg.contains("shortcut") && !lowPkg.contains("settings") && !lowPkg.contains("permission"))
    }

    /**
     * 指定されたノードに対する最適な操作ヒント文字列を生成して返します。
     * ノードが無効（disabled）または操作可能でない場合は空文字列を返します。
     */
    fun getActionHint(
        node: AccessibilityNodeInfo?,
        text: String = "",
        role: String = ""
    ): String {
        if (node == null) return ""
        // 無効状態の要素には操作案内を付与しない（視覚障害ユーザーが操作可能と誤認するのを防ぐ）
        if (!node.isEnabled) return ""

        val actions = node.actionList ?: emptyList()
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
            isLongClickable = node.isLongClickable || hasLongClick,
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
            roleEditText = context.getString(R.string.role_edit_text),
            isEnabled = node.isEnabled,
            customClickLabel = customClickLabel,
            roleTab = context.getString(R.string.role_tab),
            isKnownLauncher = isLauncherPackage(pkg)
        )

        return when (hintType) {
            ActionHintType.NONE -> ""
            ActionHintType.CUSTOM -> {
                val label = customClickLabel ?: actionLabels.firstOrNull { it.isNotBlank() }
                if (!label.isNullOrBlank()) {
                    context.getString(R.string.hint_action_custom_fmt, label)
                } else {
                    context.getString(R.string.hint_action_activate)
                }
            }
            else -> context.getString(hintType.stringResId)
        }
    }

    companion object {

        val KNOWN_LAUNCHER_PACKAGES = setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.mi.android.globallauncher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.oneplus.launcher",
            "com.teslacoilsw.launcher",
            "bitpit.launcher",
            "com.microsoft.launcher",
            "com.smartlauncher",
            "ch.deletescape.lawnchair",
            "app.lawnchair"
        )

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
            roleEditText: String = "",
            isEnabled: Boolean = true,
            customClickLabel: String? = null,
            roleTab: String = "",
            isKnownLauncher: Boolean? = null
        ): ActionHintType {
            if (!isEnabled) {
                return ActionHintType.NONE
            }

            val isActionable = isClickable || isCheckable || isEditable || isLongClickable ||
                    hasClickAction || hasExpandAction || hasCollapseAction

            if (!isActionable) {
                return ActionHintType.NONE
            }

            // 1. アプリ開発者が明示的に提供した ACTION_CLICK のカスタムアクセシビリティアクションラベル
            if (!customClickLabel.isNullOrBlank()) {
                val lowCustom = customClickLabel.lowercase().trim()
                val isGeneric = lowCustom in setOf("click", "tap", "activate", "タップ", "クリック", "アクティベート")
                if (!isGeneric) {
                    return ActionHintType.CUSTOM
                }
            }

            val lowViewId = viewId.lowercase()
            val lowPkg = pkg.lowercase()
            val lowText = text.lowercase()
            val lowDesc = contentDesc.lowercase()
            val lowRole = role.lowercase()

            // 2. テキスト入力フィールド判定（検索バーやメッセージ入力欄は、ヒントに関わらず入力操作が主）
            if (isEditTextTarget(isEditable, className, lowRole, roleEditText)) {
                return ActionHintType.EDIT_TEXT
            }

            // 3. スイッチ / トグル判定（「通話の自動録音」「削除の確認」などの項目名でも切り替え操作が主）
            if (isSwitchTarget(className, lowRole, roleSwitch)) {
                return ActionHintType.TOGGLE
            }

            // 4. チェックボックス / ラジオボタン判定
            if (isCheckTarget(isCheckable, className, lowRole, roleCheckbox, roleRadio, roleSwitch)) {
                return ActionHintType.CHECK
            }

            // 5. 展開 / 折りたたみ判定
            if (hasExpandAction) {
                return ActionHintType.EXPAND
            }
            if (hasCollapseAction) {
                return ActionHintType.COLLAPSE
            }

            // 6. アプリ起動判定（ホーム画面・ランチャー）
            if (isLauncherApp(lowPkg, className, lowViewId, lowRole, roleWidget, text, isClickable, isKnownLauncher)) {
                return ActionHintType.LAUNCH_APP
            }

            val combinedTarget = (
                "$lowText $lowDesc $lowViewId ${className.lowercase()} " +
                actionLabels.joinToString(" ") { it.lowercase() }
            ).trim()

            // 7. シャットダウン / 電源OFF判定
            if (isShutdownText(combinedTarget)) {
                return ActionHintType.SHUTDOWN
            }
            if (isPowerOffText(combinedTarget)) {
                return ActionHintType.POWER_OFF
            }

            // 8. 再起動判定
            if (isRestartText(combinedTarget)) {
                return ActionHintType.RESTART
            }

            // 9. 通話終了判定（通常の発信より優先）
            if (isEndCallText(combinedTarget)) {
                return ActionHintType.END_CALL
            }

            // 10. 電話発信 / ダイヤル判定
            if (isCallText(combinedTarget, lowRole, roleTab)) {
                return ActionHintType.CALL
            }

            // 11. 送信判定
            if (isSendText(combinedTarget)) {
                return ActionHintType.SEND
            }

            // 12. 削除 / ごみ箱判定
            if (isDeleteText(combinedTarget)) {
                return ActionHintType.DELETE
            }

            // 13. 閉じる / 破棄判定
            if (isCloseTarget(combinedTarget, lowViewId)) {
                return ActionHintType.CLOSE
            }

            // 14. 戻る判定
            if (isBackTarget(combinedTarget, lowViewId)) {
                return ActionHintType.BACK
            }

            // 15. 一時停止 / 再生判定
            if (isPauseTarget(combinedTarget)) {
                return ActionHintType.PAUSE
            }
            if (isPlayTarget(combinedTarget)) {
                return ActionHintType.PLAY
            }

            // 16. 検索判定
            if (isSearchTarget(combinedTarget, className, lowViewId)) {
                return ActionHintType.SEARCH
            }

            // 17. リンク判定
            if (isLinkTarget(className, lowRole, text)) {
                return ActionHintType.OPEN_LINK
            }

            // 18. その他のカスタムアクションラベル
            if (actionLabels.isNotEmpty()) {
                val candidate = actionLabels.firstOrNull { it.isNotBlank() }
                if (candidate != null) {
                    return ActionHintType.CUSTOM
                }
            }

            // 19. 長押しのみ可能なコントロール
            if (!isClickable && !hasClickAction && isLongClickable) {
                return ActionHintType.LONG_CLICK
            }

            // 20. フォールバック
            return ActionHintType.ACTIVATE
        }

        private fun isLauncherApp(
            pkg: String,
            className: String,
            viewId: String,
            role: String,
            roleWidget: String,
            text: String,
            isClickable: Boolean,
            isKnownLauncher: Boolean?
        ): Boolean {
            val isLauncher = isKnownLauncher ?: (
                pkg in KNOWN_LAUNCHER_PACKAGES ||
                pkg.endsWith(".launcher") ||
                pkg.contains("launcher3") ||
                (pkg.contains("launcher") && !pkg.contains("shortcut") && !pkg.contains("settings") && !pkg.contains("permission"))
            )

            if (!isLauncher) return false

            if (roleWidget.isNotEmpty() && role.contains(roleWidget, ignoreCase = true)) return false
            if (viewId.contains("widget") || className.contains("Widget", ignoreCase = true)) return false
            if (viewId.contains("qsb") || viewId.contains("search")) return false

            // ランチャー内のアクションボタン（Overviewのすべてクリア、スクリーンショット、設定等）はアプリ起動アイコンではない
            if (className.equals("android.widget.Button", ignoreCase = true) ||
                className.equals("android.widget.ImageButton", ignoreCase = true)
            ) {
                return false
            }

            val lowViewId = viewId.lowercase()
            val isSystemControlId = lowViewId.contains("clear_all") ||
                    lowViewId.contains("screenshot") ||
                    lowViewId.contains("overview") ||
                    lowViewId.contains("task") ||
                    lowViewId.contains("snapshot") ||
                    lowViewId.contains("page_indicator") ||
                    lowViewId.contains("drag_target") ||
                    lowViewId.contains("delete_target") ||
                    lowViewId.contains("action_button") ||
                    lowViewId.contains("wallpaper")

            if (isSystemControlId) return false

            val lowText = text.lowercase()
            val isSystemControlText = lowText.contains("すべてクリア") ||
                    lowText.contains("クリア") ||
                    lowText.contains("clear all") ||
                    lowText.contains("スクリーンショット") ||
                    lowText.contains("screenshot") ||
                    lowText.contains("壁紙") ||
                    lowText.contains("wallpaper") ||
                    lowText.contains("ホームの設定") ||
                    lowText.contains("home settings") ||
                    lowText.contains("アプリ情報") ||
                    lowText.contains("app info") ||
                    lowText.contains("アンインストール") ||
                    lowText.contains("uninstall")

            if (isSystemControlText) return false

            val isAppIconClass = className.contains("BubbleTextView", ignoreCase = true) ||
                    className.contains("LauncherIcon", ignoreCase = true) ||
                    className.contains("FolderIcon", ignoreCase = true) ||
                    className.contains("HomeItem", ignoreCase = true) ||
                    className.contains("AppsItem", ignoreCase = true) ||
                    className.contains("ShortcutIcon", ignoreCase = true) ||
                    className.contains("ItemIcon", ignoreCase = true) ||
                    className.contains("AppItem", ignoreCase = true)

            val isAppViewId = lowViewId.contains("icon") ||
                    lowViewId.contains("application_item") ||
                    lowViewId.contains("app_item") ||
                    lowViewId.contains("workspace_cell") ||
                    lowViewId.contains("cell_layout")

            if (isAppIconClass || isAppViewId) return true

            // ランチャー内のクリック可能なTextView（アイコン＋ラベル構成）
            return isClickable && text.isNotBlank() && (className.contains("TextView", ignoreCase = true) || className.isEmpty())
        }

        private fun isShutdownText(target: String): Boolean {
            return target.contains("シャットダウン") ||
                    target.contains("shutdown") ||
                    target.contains("i-shutdown") ||
                    target.contains("afsluiten")
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

        private fun isCallText(target: String, role: String, roleTab: String): Boolean {
            // タブナビゲーション、通話履歴、コールバック、発信者番号等は除外
            if (roleTab.isNotEmpty() && role.contains(roleTab, ignoreCase = true)) {
                return false
            }
            if (target.contains("通話履歴") ||
                target.contains("call log") ||
                target.contains("call history") ||
                target.contains("callback") ||
                target.contains("caller") ||
                target.contains("recall")
            ) {
                return false
            }

            return target.contains("発信") ||
                    target.contains("電話をかける") ||
                    target.contains("通話開始") ||
                    target.contains("ダイヤル") ||
                    target.contains("make call") ||
                    target.contains("llamar") ||
                    target.contains("tumawag") ||
                    target.contains("bellen") ||
                    target.contains("call_button") ||
                    target.contains("btn_call") ||
                    Regex("\\b(call|dial)\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isSendText(target: String): Boolean {
            // 送信者プロフィールや送信済みメールフォルダー等は除外
            if (target.contains("送信者") ||
                target.contains("送信済み") ||
                target.contains("sender") ||
                target.contains("sent")
            ) {
                return false
            }

            return target.contains("送信") ||
                    target.contains("enviar") ||
                    target.contains("ipadala") ||
                    target.contains("verzenden") ||
                    target.contains("btn_send") ||
                    target.contains("send_button") ||
                    target.contains("composer_send") ||
                    Regex("\\bsend\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isDeleteText(target: String): Boolean {
            // 最近削除した項目フォルダーやごみ箱ナビゲーション等は除外
            if (target.contains("最近削除") ||
                target.contains("recently deleted") ||
                target.contains("trash folder") ||
                target.contains("trash bin")
            ) {
                return false
            }

            return target.contains("削除") ||
                    target.contains("ごみ箱") ||
                    target.contains("ゴミ箱") ||
                    target.contains("消去") ||
                    target.contains("trash") ||
                    target.contains("eliminar") ||
                    target.contains("borrar") ||
                    target.contains("burahin") ||
                    target.contains("verwijderen") ||
                    target.contains("btn_delete") ||
                    target.contains("action_delete") ||
                    Regex("\\b(delete|remove)\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isSearchTarget(target: String, className: String, viewId: String): Boolean {
            if (className.contains("SearchView", ignoreCase = true)) {
                return true
            }
            // 検索結果アイテムや結果一覧コンテナは除外
            if (target.contains("検索結果") ||
                target.contains("search result") ||
                viewId.contains("search_result") ||
                viewId.contains("search_item")
            ) {
                return false
            }

            val isSearchId = viewId.contains("btn_search") ||
                    viewId.contains("search_button") ||
                    viewId.endsWith("_search") ||
                    viewId.startsWith("search_") ||
                    viewId == "search"

            if (isSearchId) return true

            return target.contains("検索") ||
                    target.contains("さがす") ||
                    target.contains("buscar") ||
                    target.contains("maghanap") ||
                    target.contains("zoeken") ||
                    Regex("\\bsearch\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isCloseTarget(target: String, viewId: String): Boolean {
            val isCloseId = viewId.contains("btn_close") ||
                    viewId.contains("close_button") ||
                    viewId.endsWith("_close") ||
                    viewId.startsWith("close_") ||
                    viewId.contains("dismiss")

            if (isCloseId) return true

            // closet, closed などの部分一致誤判定を排除
            if (target.contains("closet") ||
                target.contains("closed") ||
                target.contains("closely") ||
                target.contains("disclosure")
            ) {
                return false
            }

            return target.contains("閉じる") ||
                    target.contains("破棄") ||
                    target.contains("dismiss") ||
                    target.contains("cerrar") ||
                    target.contains("isara") ||
                    target.contains("sluiten") ||
                    Regex("\\bclose\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isBackTarget(target: String, viewId: String): Boolean {
            val isBackId = viewId.contains("action_bar_up") ||
                    viewId.contains("up_button") ||
                    viewId.contains("btn_back") ||
                    viewId.contains("back_button") ||
                    viewId.endsWith("_back") ||
                    viewId.startsWith("back_") ||
                    viewId == "back"

            if (isBackId) return true

            // feedback, playback, background, cashback などの部分一致誤判定を排除
            if (target.contains("feedback") ||
                target.contains("playback") ||
                target.contains("background") ||
                target.contains("cashback") ||
                target.contains("paperback") ||
                target.contains("fallback")
            ) {
                return false
            }

            return target.contains("戻る") ||
                    target.contains("前の画面") ||
                    target.contains("navigate up") ||
                    target.contains("regresar") ||
                    target.contains("bumalik") ||
                    target.contains("terug") ||
                    Regex("\\bback\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
        }

        private fun isPlayTarget(target: String): Boolean {
            if (isPauseTarget(target)) return false
            // 再生リストや自動再生スイッチは除外
            if (target.contains("再生リスト") ||
                target.contains("playlist") ||
                target.contains("自動再生") ||
                target.contains("autoplay")
            ) {
                return false
            }

            return target.contains("再生") ||
                    target.contains("reproducir") ||
                    target.contains("i-play") ||
                    target.contains("afspelen") ||
                    target.contains("btn_play") ||
                    target.contains("play_button") ||
                    Regex("\\bplay\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)
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
