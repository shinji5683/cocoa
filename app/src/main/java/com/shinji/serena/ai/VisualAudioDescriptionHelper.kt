package com.shinji.serena.ai

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * VisualAudioDescriptionHelper
 *
 * 画面上のアイコン、画像、ラベルなしボタン、ウィジェットに対して、
 * セマンティクス構造とキーワード推論を組み合わせて
 * 映画の音声ガイドのようなリッチな「ビジュアル・オーディオ・ディスクリプション（形状・情景解説）」をリアルタイム生成するヘルパー。
 */
class VisualAudioDescriptionHelper(private val context: Context) {

    private val descriptionCache = mutableMapOf<String, String>()

    /**
     * ノードの「視覚的な形状・デザイン（絵柄）」のみを生成する。
     * 例: Watch -> "腕時計アイコン", 設定 -> "歯車アイコン", 検索 -> "虫眼鏡アイコン"
     * 該当する形状が特定できない場合は空文字を返す（無意味なオウム返しを防止）。
     */
    fun getVisualShapeDescription(node: AccessibilityNodeInfo, nodeText: String = ""): String {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val text = (if (nodeText.isNotEmpty()) nodeText else node.text?.toString()?.trim() ?: "").lowercase()
        val combined = "$viewId $contentDesc $text"

        return when {
            // 1. 時計・ウォッチ
            combined.contains("watch") || combined.contains("clock") || combined.contains("alarm") || combined.contains("timer") -> "腕時計アイコン"

            // 2. 設定・システム
            combined.contains("setting") || combined.contains("gear") || combined.contains("config") || combined.contains("preference") -> "歯車アイコン"

            // 3. 電話・通話
            combined.contains("phone") || combined.contains("dialer") || combined.contains("call") -> "受話器アイコン"

            // 4. メッセージ・チャット
            combined.contains("message") || combined.contains("sms") || combined.contains("chat") || combined.contains("talk") -> "吹き出しアイコン"

            // 5. メール・Gmail
            combined.contains("gmail") || combined.contains("mail") || combined.contains("inbox") -> "封筒アイコン"

            // 6. ブラウザ・Chrome
            combined.contains("chrome") -> "4色の球体アイコン"
            combined.contains("browser") || combined.contains("web") || combined.contains("globe") || combined.contains("internet") -> "地球儀アイコン"

            // 7. カメラ・撮影
            combined.contains("camera") || combined.contains("shutter") || combined.contains("lens") -> "レンズ付きカメラアイコン"

            // 8. 写真・フォト・ギャラリー
            combined.contains("photo") || combined.contains("gallery") || combined.contains("picture") -> "写真アイコン"

            // 9. YouTube・動画
            combined.contains("youtube") -> "赤い再生ボタンアイコン"
            combined.contains("video") || combined.contains("movie") || combined.contains("film") -> "動画フィルムアイコン"

            // 10. Gemini・AIアシスタント
            combined.contains("gemini") || combined.contains("bard") || combined.contains("spark") -> "四芒星アイコン"

            // 11. 検索
            combined.contains("search") || combined.contains("find") || combined.contains("magnif") -> "虫眼鏡アイコン"

            // 12. メニュー・ドロワー
            combined.contains("menu") || combined.contains("nav") || combined.contains("drawer") || combined.contains("hamburger") -> "三本線メニューアイコン"
            combined.contains("overflow") || combined.contains("more") || combined.contains("kebab") || combined.contains("dots") -> "三点リーダーメニューアイコン"

            // 13. 戻る・進む
            combined.contains("back") || combined.contains("arrow_back") || combined.contains("prev") -> "左向き矢印アイコン"
            combined.contains("next") || combined.contains("arrow_forward") || combined.contains("forward") -> "右向き矢印アイコン"

            // 14. 閉じる・削除・クリア
            combined.contains("close") || combined.contains("clear") || combined.contains("dismiss") || combined.contains("cancel") -> "バツ印アイコン"
            combined.contains("delete") || combined.contains("trash") || combined.contains("remove") || combined.contains("bin") -> "ゴミ箱アイコン"

            // 15. 追加・作成
            combined.contains("add") || combined.contains("plus") || combined.contains("create") || combined.contains("new") -> "プラス記号アイコン"

            // 16. 共有
            combined.contains("share") || combined.contains("export") -> "共有アイコン"

            // 17. お気に入り・ハート・星
            combined.contains("favorite") || combined.contains("heart") || combined.contains("like") -> "ハートアイコン"
            combined.contains("star") || combined.contains("bookmark") -> "星マークアイコン"

            // 18. マイク・音声
            combined.contains("mic") || combined.contains("audio") || combined.contains("voice") || combined.contains("speech") -> "マイクアイコン"

            // 19. 再生・一時停止・音量
            combined.contains("play") -> "再生三角アイコン"
            combined.contains("pause") -> "一時停止二本線アイコン"
            combined.contains("stop") -> "停止四角アイコン"
            combined.contains("volume") || combined.contains("speaker") -> "スピーカーアイコン"
            combined.contains("mute") -> "消音アイコン"

            // 20. ダウンロード・アップロード
            combined.contains("download") -> "下向き矢印アイコン"
            combined.contains("upload") -> "上向き矢印アイコン"

            // 21. カート・買い物
            combined.contains("cart") || combined.contains("shopping") || combined.contains("basket") || combined.contains("bag") -> "買い物かごアイコン"

            // 22. 通知・ベル
            combined.contains("bell") || combined.contains("notification") || combined.contains("alert") -> "通知ベルアイコン"

            // 23. ユーザー・プロフィール
            combined.contains("profile") || combined.contains("avatar") || combined.contains("account") || combined.contains("user") || combined.contains("person") -> "人型アカウントアイコン"

            // 24. マップ・位置情報
            combined.contains("map") || combined.contains("location") || combined.contains("pin") || combined.contains("place") || combined.contains("navigate") -> "地図とピンアイコン"

            // 25. カレンダー
            combined.contains("calendar") || combined.contains("schedule") || combined.contains("event") -> "カレンダーアイコン"

            // 26. 天気
            combined.contains("weather") || combined.contains("sun") || combined.contains("cloud") || combined.contains("rain") -> "お天気マーク"

            // 27. 電卓
            combined.contains("calc") || combined.contains("calculator") -> "電卓アイコン"

            // 28. フォルダー
            combined.contains("folder") || combined.contains("dir") -> "フォルダーアイコン"

            else -> ""
        }
    }

    /**
     * ラベルのないボタン・画像（text, contentDescription が空）に対して、
     * viewIdや周辺情報から推定した意味ある名前と言語表現を生成。
     * 「名前のない画像」「ラベルなし」を完全撲滅！
     */
    fun inferLabelForUnlabelledNode(node: AccessibilityNodeInfo): String {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        val isClickable = node.isClickable

        // 1. 形状解説から推定（虫眼鏡なら「検索ボタン」など）
        val shape = getVisualShapeDescription(node)
        if (shape.isNotEmpty()) {
            val baseName = shape.replace("アイコン", "").replace("マーク", "")
            return if (isClickable) "${baseName}ボタン" else shape
        }

        // 2. viewId のキーワードから推定
        val idInferred = when {
            viewId.contains("search") -> "検索"
            viewId.contains("setting") -> "設定"
            viewId.contains("menu") -> "メニュー"
            viewId.contains("back") -> "戻る"
            viewId.contains("close") || viewId.contains("dismiss") -> "閉じる"
            viewId.contains("send") || viewId.contains("submit") -> "送信"
            viewId.contains("confirm") || viewId.contains("ok") -> "確認"
            viewId.contains("cancel") -> "キャンセル"
            viewId.contains("next") -> "次へ"
            viewId.contains("prev") -> "前へ"
            viewId.contains("home") -> "ホーム"
            viewId.contains("login") || viewId.contains("signin") -> "ログイン"
            viewId.contains("logout") || viewId.contains("signout") -> "ログアウト"
            viewId.contains("share") -> "共有"
            viewId.contains("filter") -> "絞り込みフィルター"
            viewId.contains("sort") -> "並び替え"
            viewId.contains("refresh") || viewId.contains("reload") -> "更新"
            viewId.contains("info") || viewId.contains("help") -> "ヘルプ情報"
            viewId.contains("notification") -> "お知らせ通知"
            viewId.contains("cart") -> "買い物かご"
            viewId.contains("play") -> "再生"
            viewId.contains("pause") -> "一時停止"
            viewId.contains("edit") -> "編集"
            viewId.contains("save") -> "保存"
            viewId.contains("delete") -> "削除"
            viewId.contains("add") -> "追加"
            viewId.contains("mic") -> "音声入力"
            viewId.contains("camera") -> "カメラ撮影"
            else -> ""
        }
        if (idInferred.isNotEmpty()) {
            return if (isClickable) "${idInferred}ボタン" else "${idInferred}項目"
        }

        // 3. 親ノードや兄弟ノードから推定
        val parent = node.parent
        if (parent != null) {
            val parentDesc = parent.contentDescription?.toString()?.trim() ?: ""
            val parentText = parent.text?.toString()?.trim() ?: ""
            if (parentDesc.isNotEmpty()) return parentDesc
            if (parentText.isNotEmpty()) return parentText
        }

        // 4. フォールバック（絶対に「名前のない画像」「ラベルなし」とは言わせない！）
        return when {
            isClickable -> "操作ボタン"
            className.contains("ImageView", ignoreCase = true) || className.contains("Image", ignoreCase = true) -> "グラフィック画像"
            else -> ""
        }
    }

    /**
     * 互換性維持のための総合解説生成メソッド
     */
    fun generateDescriptionForNode(node: AccessibilityNodeInfo): String {
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val text = node.text?.toString()?.trim() ?: ""

        // テキストが既にある場合は形状解説を補完
        if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
            val shape = getVisualShapeDescription(node, if (text.isNotEmpty()) text else contentDesc)
            if (shape.isNotEmpty()) return shape
            return if (contentDesc.isNotEmpty()) contentDesc else text
        }

        // ラベルがない場合は自動推論
        return inferLabelForUnlabelledNode(node)
    }
}
