package com.shinji.serena.navigation

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.shinji.serena.R
import com.shinji.serena.SerenaScreenReaderService

/**
 * SystemDialogHelper
 * 権限確認ダイアログ（Google Pixel / AOSP / Samsung One UI / Xiaomi MIUI・HyperOS / OPPO ColorOS /
 * Vivo・iQOO / Huawei・Honor / Transsion 等 全OEM対応）、システムアラート（画面録画・キャスト、USBデバッグ等）、
 * アプリ設定・確認ポップアップ等の自動検知・完全モーダルトラップ・詳細情報読み上げ・主要操作ボタン自動フォーカスエンジン。
 */
object SystemDialogHelper {
    private const val TAG = "SystemDialogHelper"

    data class DialogInfo(
        val isPermission: Boolean,
        val isSpecialSystem: Boolean,
        val packageName: String,
        val title: String,
        val message: String,
        val primaryButton: AccessibilityNodeInfo?,
        val dialogRoot: AccessibilityNodeInfo?
    )

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var pendingDialogRunnable: Runnable? = null
    private var lastHandledDialogKey: String = ""

    @Volatile
    var lastHandledTimeMs: Long = 0L

    /**
     * 純粋な権限確認・システム権限マネージャーパッケージ判定
     */
    fun isPurePermissionPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val p = pkg.lowercase()
        return p == "com.google.android.permissioncontroller" ||
                p == "com.android.permissioncontroller" ||
                p == "com.android.packageinstaller" ||
                p == "com.google.android.packageinstaller" ||
                p == "com.samsung.android.permissioncontroller" ||
                p == "com.samsung.android.packageinstaller" ||
                p == "com.miui.securitypermission" ||
                p == "com.miui.guardprovider" ||
                p == "com.coloros.securitypermission" ||
                p == "com.oplus.securitypermission" ||
                p == "com.vivo.permissionmanager" ||
                p.contains("permissioncontroller") ||
                p.contains("packageinstaller")
    }

    /**
     * OEMセキュリティ・デバイス管理パッケージ判定（権限プロンプトのホスト元）
     */
    fun isOemSecurityPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val p = pkg.lowercase()
        return p.contains("securitycenter") ||
                p.contains("safecenter") ||
                p.contains("safetycenter") ||
                p.contains("systemmanager") ||
                p.contains("phonemaster") ||
                p.contains("securitycore") ||
                p == "com.iqoo.secure" ||
                p == "com.lbe.security.miui" ||
                p == "com.hihonor.systemmanager" ||
                p == "com.lenovo.safecenter"
    }

    /**
     * 権限管理・セキュリティ関連パッケージ判定（全Android OEM網羅）
     */
    fun isPermissionPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val p = pkg.lowercase()
        return isPurePermissionPackage(p) ||
                isOemSecurityPackage(p) ||
                p.contains("permission") ||
                p.contains("securitypermission")
    }

    /**
     * 特殊システムダイアログ（VPN接続確認、Bluetoothコンパニオン接続等）判定
     */
    fun isSpecialSystemDialogPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val p = pkg.lowercase()
        return isPurePermissionPackage(p) ||
                p == "com.android.vpndialogs" ||
                p == "com.android.companiondevicemanager"
    }

    /**
     * SystemUI配下のシステムダイアログ（画面キャスト/録画許可、USB接続許可等）判定
     */
    fun isSystemUiDialog(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val pkg = node.packageName?.toString()?.lowercase() ?: ""
        if (!pkg.contains("systemui") && pkg != "android") return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        // 通常の通知シェード・ステータスバー・ロック画面枠コンテナ・クイック設定は除外
        if (viewId.contains("notification_shade") || viewId.contains("keyguard") ||
            viewId.contains("notification_panel") || viewId.contains("status_bar") ||
            viewId.contains("quick_settings") || viewId.contains("qs_panel")
        ) {
            return false
        }

        return hasDialogIndicators(node, depth = 0)
    }

    /**
     * ノード内にダイアログ特有の要素（alertTitle, message, 肯定/否定ボタン等）が存在するか探索
     * 最大深さ15階層まで確実に走査（Android標準AlertDialog / PermissionControllerの深い階層に対応）
     */
    fun hasDialogIndicators(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 15) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val cls = node.className?.toString() ?: ""

        if (id.contains("alerttitle") || id.contains("alert_title") ||
            id.contains("permission_message") || id.contains("permission_grant_title") ||
            id.contains("dialog_title") || id.contains("header_title") ||
            id.contains("title_template") ||
            id.endsWith(":id/button1") || id.endsWith(":id/button2") || id.endsWith(":id/button3") ||
            id.contains("permission_allow_button") ||
            id.contains("permission_allow_foreground_only_button") ||
            id.contains("permission_allow_one_time_button") ||
            id.contains("permission_allow_always_button") ||
            id.contains("permission_deny_button") ||
            id.contains("button_start") || id.contains("btn_confirm") ||
            id.contains("parentpanel") || id.contains("contentpanel") || id.contains("buttonpanel") ||
            cls.contains("AlertDialog", ignoreCase = true) ||
            cls.contains("DialogTitle", ignoreCase = true)
        ) {
            return true
        }

        for (i in 0 until node.childCount.coerceAtMost(20)) {
            val child = node.getChild(i) ?: continue
            if (hasDialogIndicators(child, depth + 1)) return true
        }
        return false
    }

    /**
     * 汎用ダイアログ判定
     */
    fun isGeneralDialog(node: AccessibilityNodeInfo?, className: String = ""): Boolean {
        if (node == null) return false
        if (className.contains("Dialog", ignoreCase = true) ||
            className.contains("AlertDialog", ignoreCase = true) ||
            className.contains("PopupWindow", ignoreCase = true)
        ) {
            return true
        }
        val cls = node.className?.toString() ?: ""
        if (cls.contains("Dialog", ignoreCase = true) || cls.contains("AlertDialog", ignoreCase = true)) {
            return true
        }
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("parentpanel") || id.contains("contentpanel") || id.contains("alerttitle") || id.contains("dialog_root")) {
            return true
        }
        return hasDialogIndicators(node, depth = 0)
    }

    /**
     * 最前面のダイアログウィンドウを検出して取得
     */
    fun findTopDialogWindow(service: SerenaScreenReaderService): Pair<AccessibilityWindowInfo, AccessibilityNodeInfo>? {
        try {
            val wins = service.windows
            if (!wins.isNullOrEmpty()) {
                // レイヤーの高い順（前面順）にソートして精査
                val sortedWins = wins.sortedByDescending { it.layer }

                // 1. 純粋な権限確認・特殊システムダイアログのウィンドウを最優先
                for (w in sortedWins) {
                    val root = w.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (isPurePermissionPackage(pkg) || isSpecialSystemDialogPackage(pkg)) {
                        return Pair(w, root)
                    }
                }

                // 2. OEMセキュリティパッケージ（MIUI Security, ColorOS SafeCenter等）でダイアログ要素を含む場合
                for (w in sortedWins) {
                    val root = w.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (isOemSecurityPackage(pkg)) {
                        if (isGeneralDialog(root) || hasDialogIndicators(root)) {
                            return Pair(w, root)
                        }
                    }
                }

                // 3. SystemUIのシステムダイアログ（画面キャスト/録画許可、USB接続許可等）
                for (w in sortedWins) {
                    if (w.type == AccessibilityWindowInfo.TYPE_SYSTEM || w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val root = w.root ?: continue
                        if (isSystemUiDialog(root)) {
                            return Pair(w, root)
                        }
                    }
                }

                // 4. アプリ内ダイアログ（複数ウィンドウ表示時の前面モーダルウィンドウ）
                val appOrSystemWins = sortedWins.filter {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.type == AccessibilityWindowInfo.TYPE_SYSTEM
                }
                if (appOrSystemWins.size > 1) {
                    val topWin = appOrSystemWins.firstOrNull()
                    val topRoot = topWin?.root
                    if (topWin != null && topRoot != null) {
                        if (isGeneralDialog(topRoot) || hasDialogIndicators(topRoot)) {
                            return Pair(topWin, topRoot)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findTopDialogWindow error: ${e.message}")
        }

        return null
    }

    /**
     * 多言語対応のポジティブ・肯定アクションボタン文字列判定
     */
    private fun isPositiveActionText(text: String): Boolean {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return false
        return t == "許可" || t == "アプリの使用時のみ" || t == "アプリの使用中のみ許可" || t == "常に許可" ||
                t == "今回のみ" || t == "今回のみ許可" || t == "ok" || t == "はい" || t == "開始" ||
                t == "決定" || t == "今すぐ開始" || t == "続行" ||
                t == "allow" || t == "while using the app" || t == "only this time" ||
                t == "always allow" || t == "always" || t == "yes" || t == "start now" ||
                t == "confirm" || t == "continue" || t == "grant" ||
                t == "permitir" || t == "mientras la app esté en uso" || t == "solo esta vez" ||
                t == "aceptar" || t == "sí" || t == "iniciar ahora" ||
                t == "payagan" || t == "habang ginagamit ang app" || t == "sa pagkakataong ito lang" ||
                t == "oo" || t == "simulan ngayon" || t == "kumpirmahin" ||
                t == "toestaan" || t == "bij gebruik van app" || t == "alleen deze keer" ||
                t == "altijd toestaan" || t == "akkoord" || t == "ja" || t == "nu starten"
    }

    /**
     * ダイアログノード群を解析し、タイトル・本文・主要アクションボタンを精密抽出
     */
    fun inspectDialog(dialogRoot: AccessibilityNodeInfo, service: SerenaScreenReaderService): DialogInfo {
        val pkg = dialogRoot.packageName?.toString() ?: ""
        val isPerm = isPurePermissionPackage(pkg) ||
                (isOemSecurityPackage(pkg) && (isGeneralDialog(dialogRoot) || hasDialogIndicators(dialogRoot)))
        val isSpecial = isSpecialSystemDialogPackage(pkg) || isSystemUiDialog(dialogRoot)

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(dialogRoot, allNodes)

        var titleText = ""
        var messageText = ""

        // 1. タイトル探索
        // 優先度A: alertTitle, permission_message (Android 11-16で質問文が設定されるID), dialog_title, title
        val titleNode = allNodes.find { n ->
            val id = n.viewIdResourceName?.lowercase() ?: ""
            id.contains("alerttitle") || id.contains("alert_title") ||
                    id.contains("permission_message") || id.contains("permission_grant_title") ||
                    id.contains("dialog_title") || id.contains("header_title") ||
                    id.contains("title_template") ||
                    id.endsWith(":id/title")
        } ?: allNodes.find { n ->
            // 優先度B: 見出し(Heading)属性 または DialogTitle クラス
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && n.isHeading) ||
                    (!n.isClickable && n.className?.toString()?.contains("DialogTitle", ignoreCase = true) == true)
        } ?: allNodes.find { n ->
            // 優先度C: クリック・チェック不能な先頭のテキストノード
            !n.isClickable && !n.isCheckable && service.getNodeText(n).isNotEmpty()
        }

        if (titleNode != null) {
            titleText = service.getNodeText(titleNode).trim()
        }

        if (titleText.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pane = dialogRoot.paneTitle?.toString()?.trim()
            if (!pane.isNullOrEmpty()) {
                titleText = pane
            }
        }

        // 2. 本文・説明メッセージ探索
        val msgNode = allNodes.find { n ->
            val id = n.viewIdResourceName?.lowercase() ?: ""
            (id.contains("message") || id.contains("permission_grant_explanation") ||
                    id.contains("permission_rationale_message") || id.contains("permission_detail_message") ||
                    id.contains("desc") || id.contains("description") ||
                    id.contains("summary") || id.contains("body")) &&
                    n != titleNode && service.getNodeText(n).trim() != titleText
        } ?: allNodes.find { n ->
            !n.isClickable && !n.isCheckable && n != titleNode &&
                    service.getNodeText(n).trim().isNotEmpty() &&
                    service.getNodeText(n).trim() != titleText
        }

        if (msgNode != null) {
            messageText = service.getNodeText(msgNode).trim()
        }

        // 3. 主要アクションボタンの決定（ユーザー主導の許可・決定ボタンを最優先捕捉）
        // 優先度A: 権限許可（「アプリの使用中のみ」「常に許可」「許可」「今回のみ」）
        val rawPrimaryBtn = allNodes.find { n ->
            val id = n.viewIdResourceName?.lowercase() ?: ""
            (n.isClickable || hasClickableAncestor(n)) && (
                    id.contains("permission_allow_foreground_only_button") ||
                    id.contains("permission_allow_always_button") ||
                    id.contains("permission_allow_button") ||
                    id.contains("allow_foreground") ||
                    id.contains("allow_always") ||
                    id.contains("allow_button") ||
                    id.contains("permission_allow_one_time_button")
            )
        } ?: allNodes.find { n ->
            // 優先度B: 標準ダイアログ button1 (OK / Positive / 開始)
            val id = n.viewIdResourceName?.lowercase() ?: ""
            (n.isClickable || hasClickableAncestor(n)) && (
                    id.endsWith(":id/button1") ||
                    id.contains("button_start") ||
                    id.contains("btn_confirm") ||
                    id.contains("btn_ok") ||
                    id.contains("btn_positive") ||
                    id.contains("confirm_button")
            )
        } ?: allNodes.find { n ->
            // 優先度C: テキスト内容によるポジティブボタン推論（全6言語完全対応）
            val t = service.getNodeText(n)
            (n.isClickable || hasClickableAncestor(n)) && isPositiveActionText(t)
        } ?: allNodes.find { n ->
            // 優先度D: その他のクリック可能ボタン
            n.isClickable && (n.className?.toString()?.contains("Button", ignoreCase = true) == true)
        } ?: allNodes.find { n ->
            n.isClickable || n.isCheckable
        }

        // 子テキストノードが選ばれた場合、クリック可能な親ノードへと昇格
        var primaryBtn = rawPrimaryBtn
        if (primaryBtn != null && !primaryBtn.isClickable) {
            var parent = primaryBtn.parent
            while (parent != null) {
                if (parent.isClickable) {
                    primaryBtn = parent
                    break
                }
                parent = parent.parent
            }
        }

        return DialogInfo(
            isPermission = isPerm,
            isSpecialSystem = isSpecial,
            packageName = pkg,
            title = titleText,
            message = messageText,
            primaryButton = primaryBtn,
            dialogRoot = dialogRoot
        )
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var p = node.parent
        while (p != null) {
            if (p.isClickable) return true
            p = p.parent
        }
        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (node == null || depth > 15 || list.size >= 150) return
        list.add(node)
        for (i in 0 until node.childCount.coerceAtMost(30)) {
            val child = node.getChild(i) ?: continue
            collectAllNodes(child, list, depth + 1)
        }
    }

    /**
     * バックオフリトライ付きのダイアログ検知・読み上げ・フォーカス実行
     * (Attempt 1: 100ms, Attempt 2: 250ms, Attempt 3: 450ms, Attempt 4: 700ms)
     */
    fun announceDialogWithRetry(service: SerenaScreenReaderService, attempt: Int = 1) {
        // 重複発火・連続イベントのデバウンス
        pendingDialogRunnable?.let { mainHandler.removeCallbacks(it) }

        val delays = longArrayOf(100L, 250L, 450L, 700L)
        val delayMs = if (attempt - 1 in delays.indices) delays[attempt - 1] else 300L

        val runnable = Runnable {
            try {
                // 最前面ダイアログウィンドウを探索
                val topDialog = findTopDialogWindow(service)
                val dialogRoot = topDialog?.second ?: service.rootInActiveWindow

                if (dialogRoot != null) {
                    val pkg = dialogRoot.packageName?.toString() ?: ""
                    val isPerm = isPurePermissionPackage(pkg) ||
                            (isOemSecurityPackage(pkg) && (isGeneralDialog(dialogRoot) || hasDialogIndicators(dialogRoot)))
                    val isSpecial = isSpecialSystemDialogPackage(pkg) || isSystemUiDialog(dialogRoot)
                    val isGen = isGeneralDialog(dialogRoot)

                    if (isPerm || isSpecial || isGen) {
                        val info = inspectDialog(dialogRoot, service)
                        val hasContent = info.title.isNotEmpty() || info.message.isNotEmpty() || info.primaryButton != null

                        if (hasContent) {
                            val now = System.currentTimeMillis()
                            val dialogKey = "${info.packageName}:${info.title}:${info.message}"
                            if (dialogKey == lastHandledDialogKey && (now - lastHandledTimeMs) < 1200L) {
                                // フォーカスのみ確実に適用
                                info.primaryButton?.let { btn ->
                                    service.focusNavigator?.setFocusAndShowOnScreen(btn)
                                }
                                return@Runnable
                            }

                            lastHandledDialogKey = dialogKey
                            lastHandledTimeMs = now

                            // アナウンス文の組み立て
                            val tag = if (info.isPermission) {
                                service.getString(R.string.permission_dialog_tag)
                            } else {
                                service.getString(R.string.dialog_tag_confirmation)
                            }

                            val title = if (info.title.isNotEmpty()) {
                                info.title
                            } else if (info.isPermission) {
                                service.getString(R.string.permission_dialog_default_msg)
                            } else {
                                service.getString(R.string.dialog_default_title)
                            }

                            val fullAnnouncement = if (info.message.isNotEmpty() && info.message != title) {
                                service.getString(R.string.dialog_announcement_with_title_and_message_fmt, tag, title, info.message)
                            } else {
                                service.getString(R.string.dialog_announcement_fmt, tag, title)
                            }

                            service.soundHelper?.playFocusMove()

                            if (service.isTtsReady) {
                                service.speak(fullAnnouncement, TextToSpeech.QUEUE_FLUSH)
                            }

                            // 主要アクションボタンにフォーカスを設定
                            // （フォーカスイベント時に QUEUE_ADD でボタン読み上げが行われるよう lastHandledTimeMs で保護！）
                            val targetBtn = info.primaryButton
                            if (targetBtn != null) {
                                service.focusNavigator?.setFocusAndShowOnScreen(targetBtn)

                                // 安全策: 400ms経過してもアクセシビリティフォーカスイベントが届いていない場合は直接追加アナウンス
                                mainHandler.postDelayed({
                                    if (service.lastFocusTimeMs < now && service.isTtsReady) {
                                        service.announceNode(targetBtn, TextToSpeech.QUEUE_ADD)
                                    }
                                }, 400L)
                            }
                            return@Runnable
                        }
                    }
                }

                // まだノードが描画完了していない場合、リトライ
                if (attempt < 4) {
                    announceDialogWithRetry(service, attempt + 1)
                } else {
                    // 全リトライ完了後も要素が取れなかった場合のフォールバックヒント
                    if (service.isTtsReady) {
                        val activePkg = service.rootInActiveWindow?.packageName?.toString() ?: ""
                        if (isPermissionPackage(activePkg)) {
                            service.speak(service.getString(R.string.permission_dialog_hint), TextToSpeech.QUEUE_FLUSH)
                        } else {
                            service.speak(service.getString(R.string.dialog_hint), TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "announceDialogWithRetry error: ${t.message}", t)
            }
        }

        pendingDialogRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }
}
