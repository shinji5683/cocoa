package com.shinji.serena

import com.shinji.serena.navigation.ContextualActionHintHelper
import com.shinji.serena.navigation.ContextualActionHintHelper.ActionHintType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ContextualActionHintHelper ユニットテスト
 *
 * ダブルタップ操作ヒント判定エンジンの全パターン・境界値・フォールバックを網羅検証
 */
class ContextualActionHintHelperTest {

    @Test
    fun testLauncherAppLaunchDetection() {
        val type = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.apps.nexuslauncher",
            className = "com.android.launcher3.BubbleTextView",
            viewId = "com.google.android.apps.nexuslauncher:id/icon",
            text = "YouTube",
            contentDesc = "YouTube",
            actionLabels = emptyList(),
            role = ""
        )
        assertEquals(ActionHintType.LAUNCH_APP, type)
    }

    @Test
    fun testLauncherWidgetNotAppLaunch() {
        val type = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.launcher3",
            className = "android.appwidget.AppWidgetHostView",
            viewId = "com.android.launcher3:id/widget_container",
            text = "天気",
            contentDesc = "東京 22度",
            actionLabels = emptyList(),
            role = "ウィジェット",
            roleWidget = "ウィジェット"
        )
        // ウィジェットはアプリ起動ヒントではなく汎用アクティベート
        assertEquals(ActionHintType.ACTIVATE, type)
    }

    @Test
    fun testPowerOffAndShutdownDetection() {
        val powerOff = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.systemui",
            className = "android.widget.Button",
            viewId = "com.android.systemui:id/global_actions_power",
            text = "電源を切る",
            contentDesc = "電源を切る",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.POWER_OFF, powerOff)

        val shutdown = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.systemui",
            className = "android.widget.Button",
            viewId = "com.android.systemui:id/shutdown_btn",
            text = "シャットダウン",
            contentDesc = "シャットダウン",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.SHUTDOWN, shutdown)
    }

    @Test
    fun testRestartDetection() {
        val restart = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.systemui",
            className = "android.widget.Button",
            viewId = "com.android.systemui:id/global_actions_restart",
            text = "再起動",
            contentDesc = "再起動",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.RESTART, restart)
    }

    @Test
    fun testSendDetection() {
        val send = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "jp.naver.line.android",
            className = "android.widget.ImageButton",
            viewId = "jp.naver.line.android:id/btn_send",
            text = "",
            contentDesc = "送信",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.SEND, send)
    }

    @Test
    fun testDeleteDetection() {
        val delete = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.gm",
            className = "android.widget.ImageView",
            viewId = "com.google.android.gm:id/delete",
            text = "",
            contentDesc = "削除",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.DELETE, delete)
    }

    @Test
    fun testSearchDetection() {
        val search = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.apps.maps",
            className = "android.widget.Button",
            viewId = "com.google.android.apps.maps:id/search_button",
            text = "検索",
            contentDesc = "周辺を検索",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.SEARCH, search)
    }

    @Test
    fun testCloseAndBackDetection() {
        val close = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.shinji.serena",
            className = "android.widget.Button",
            viewId = "com.shinji.serena:id/btnClose",
            text = "閉じる",
            contentDesc = "閉じる",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.CLOSE, close)

        val back = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.settings",
            className = "android.widget.ImageButton",
            viewId = "com.android.settings:id/action_bar_up",
            text = "戻る",
            contentDesc = "前の画面へ戻る",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.BACK, back)
    }

    @Test
    fun testCallAndEndCallDetection() {
        val call = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.dialer",
            className = "android.widget.Button",
            viewId = "com.google.android.dialer:id/dialpad_call_button",
            text = "発信",
            contentDesc = "発信",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.CALL, call)

        val endCall = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.dialer",
            className = "android.widget.Button",
            viewId = "com.google.android.dialer:id/incall_end_call",
            text = "通話を終了",
            contentDesc = "通話を終了",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.END_CALL, endCall)
    }

    @Test
    fun testSwitchAndCheckDetection() {
        val switchItem = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = true,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.settings",
            className = "android.widget.Switch",
            viewId = "com.android.settings:id/switch_widget",
            text = "Wi-Fi",
            contentDesc = "Wi-Fi",
            actionLabels = emptyList(),
            role = "スイッチ",
            roleSwitch = "スイッチ"
        )
        assertEquals(ActionHintType.TOGGLE, switchItem)

        val checkboxItem = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = true,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.settings",
            className = "android.widget.CheckBox",
            viewId = "com.android.settings:id/checkbox_agree",
            text = "利用規約に同意する",
            contentDesc = "利用規約に同意する",
            actionLabels = emptyList(),
            role = "チェックボックス",
            roleCheckbox = "チェックボックス",
            roleSwitch = "スイッチ"
        )
        assertEquals(ActionHintType.CHECK, checkboxItem)
    }

    @Test
    fun testEditTextDetection() {
        val editText = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = true,
            isLongClickable = true,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.apps.messaging",
            className = "android.widget.EditText",
            viewId = "com.google.android.apps.messaging:id/compose_message_text",
            text = "",
            contentDesc = "メッセージを入力",
            actionLabels = emptyList(),
            role = "テキスト入力",
            roleEditText = "テキスト入力"
        )
        assertEquals(ActionHintType.EDIT_TEXT, editText)
    }

    @Test
    fun testPlayAndPauseDetection() {
        val play = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.youtube",
            className = "android.widget.ImageButton",
            viewId = "com.google.android.youtube:id/player_play_button",
            text = "",
            contentDesc = "再生",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.PLAY, play)

        val pause = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.google.android.youtube",
            className = "android.widget.ImageButton",
            viewId = "com.google.android.youtube:id/player_pause_button",
            text = "",
            contentDesc = "一時停止",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.PAUSE, pause)
    }

    @Test
    fun testLinkDetection() {
        val link = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.chrome",
            className = "android.widget.TextView",
            viewId = "",
            text = "https://github.com/shinji5683/cocoa",
            contentDesc = "",
            actionLabels = emptyList(),
            role = "リンク"
        )
        assertEquals(ActionHintType.OPEN_LINK, link)
    }

    @Test
    fun testExpandAndCollapseDetection() {
        val expand = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = false,
            hasExpandAction = true,
            hasCollapseAction = false,
            pkg = "com.android.systemui",
            className = "android.widget.FrameLayout",
            viewId = "com.android.systemui:id/expand_button",
            text = "",
            contentDesc = "",
            actionLabels = emptyList(),
            role = ""
        )
        assertEquals(ActionHintType.EXPAND, expand)

        val collapse = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = false,
            hasExpandAction = false,
            hasCollapseAction = true,
            pkg = "com.android.systemui",
            className = "android.widget.FrameLayout",
            viewId = "com.android.systemui:id/collapse_button",
            text = "",
            contentDesc = "",
            actionLabels = emptyList(),
            role = ""
        )
        assertEquals(ActionHintType.COLLAPSE, collapse)
    }

    @Test
    fun testNonActionableNodeReturnsNone() {
        val staticText = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = false,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = false,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.android.settings",
            className = "android.widget.TextView",
            viewId = "com.android.settings:id/section_header",
            text = "ネットワークとインターネット",
            contentDesc = "",
            actionLabels = emptyList(),
            role = ""
        )
        assertEquals(ActionHintType.NONE, staticText)
    }

    @Test
    fun testFallbackActionableReturnsActivate() {
        val unknownButton = ContextualActionHintHelper.evaluateActionHintType(
            isClickable = true,
            isCheckable = false,
            isEditable = false,
            isLongClickable = false,
            hasClickAction = true,
            hasExpandAction = false,
            hasCollapseAction = false,
            pkg = "com.example.customapp",
            className = "android.widget.Button",
            viewId = "com.example.customapp:id/btn_mystery",
            text = "OK",
            contentDesc = "OK",
            actionLabels = emptyList(),
            role = "ボタン"
        )
        assertEquals(ActionHintType.ACTIVATE, unknownButton)
    }

    @Test
    fun testAllActionHintTypeResourceIdsAreValid() {
        for (type in ActionHintType.values()) {
            if (type == ActionHintType.NONE) {
                assertEquals(0, type.stringResId)
            } else {
                assertTrue("Resource ID for ${type.name} must be greater than 0", type.stringResId > 0)
            }
        }
    }
}
