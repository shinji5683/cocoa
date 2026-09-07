package com.shinji.serena.ai

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * VisualAudioDescriptionHelper
 *
 * 画面上のアイコン、画像、ラベルなしボタン、ウィジェット、そして世界中で使われる主要アプリに対して、
 * セマンティクス構造とキーワード推論を組み合わせて
 * 映画の音声ガイドのようなリッチな「ビジュアル・オーディオ・ディスクリプション（形状・情景解説）」をリアルタイム生成するヘルパー。
 */
class VisualAudioDescriptionHelper(private val context: Context) {

    private val descriptionCache = mutableMapOf<String, String>()

    /**
     * ノードの「視覚的な形状・デザイン（絵柄）」のみを生成する。
     * 例: Watch -> "腕時計アイコン", Spotify -> "緑色の音波アイコン", Meet -> "4色のビデオ通話カメラアイコン"
     * 該当する形状が特定できない場合は空文字を返す（無意味なオウム返しを防止）。
     */
    fun getVisualShapeDescription(node: AccessibilityNodeInfo, nodeText: String = ""): String {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val text = (if (nodeText.isNotEmpty()) nodeText else node.text?.toString()?.trim() ?: "").lowercase()
        val combined = "$viewId $contentDesc $text"

        return when {
            // ==========================================
            // 1. 音楽・オーディオ・クリエイター
            // ==========================================
            combined.contains("spotify for artists") || combined == "artists" || combined.contains("spotify_artists") -> "黒と緑のアーティスト向け音波アイコン"
            combined.contains("spotify") -> "緑色の丸に3本の音波マーク"
            combined.contains("ytmusic") || combined.contains("youtube music") -> "赤い二重丸の再生アイコン"
            combined.contains("apple music") || combined.contains("applemusic") -> "ピンクと白の音符アイコン"
            combined.contains("amazon music") -> "青地に水色の笑顔矢印と音符アイコン"
            combined.contains("soundcloud") -> "オレンジ色の雲と波形アイコン"
            combined.contains("shazam") -> "青地に白いSの渦巻きマーク"
            combined.contains("audible") -> "オレンジ色の開いた本とヘッドホンアイコン"
            combined.contains("podcast") -> "紫色の電波塔と人物アイコン"
            combined.contains("radiko") -> "青いラジオ電波アイコン"
            combined.contains("tunein") -> "黒地に青いTマーク"
            combined.contains("tidal") -> "黒地に4つの白いひし形アイコン"
            combined.contains("deezer") -> "カラフルな音量イコライザーバーアイコン"
            combined.contains("music") || combined.contains("audio") || combined.contains("sound") -> "音符アイコン"

            // ==========================================
            // 2. Google サービス ＆ システムツール
            // ==========================================
            combined.contains("meet") || combined.contains("google meet") -> "4色のビデオ通話カメラアイコン"
            combined.contains("gemini") || combined.contains("bard") || combined.contains("spark") -> "青と紫に光る四芒星アイコン"
            combined.contains("chatgpt") || combined.contains("openai") -> "白黒の渦巻き六角形マーク"
            combined.contains("claude") || combined.contains("anthropic") -> "オレンジ色の星型放射マーク"
            combined.contains("copilot") -> "カラフルなリボンが重なるループアイコン"
            combined.contains("gmail") -> "赤いMマークの封筒アイコン"
            combined.contains("mail") || combined.contains("email") || combined.contains("inbox") -> "封筒アイコン"
            combined.contains("chrome") -> "赤黄緑青の丸いアイコン"
            combined.contains("firefox") -> "青い地球に巻き付く炎のキツネアイコン"
            combined.contains("edge") -> "青と緑の渦巻く波のアイコン"
            combined.contains("safari") -> "青い丸に赤い方位磁針の羅針盤アイコン"
            combined.contains("brave") -> "オレンジ色のライオンの顔アイコン"
            combined.contains("opera") -> "赤い立体的なOのリングアイコン"
            combined.contains("browser") || combined.contains("web") || combined.contains("globe") || combined.contains("internet") -> "地球儀アイコン"
            combined.contains("google maps") || combined.contains("maps") || combined.contains("map") -> "地図とピンアイコン"
            combined.contains("google drive") || combined.contains("drive") -> "黄緑青の三角形ドライブアイコン"
            combined.contains("google photos") || combined.contains("photos") || combined.contains("フォト") -> "4色の風車模様の写真アイコン"
            combined.contains("photo") || combined.contains("gallery") || combined.contains("picture") -> "写真アイコン"
            combined.contains("youtube") -> "赤い四角に白い再生三角アイコン"
            combined.contains("video") || combined.contains("movie") || combined.contains("film") -> "動画フィルムアイコン"
            combined.contains("google calendar") || combined.contains("calendar") || combined.contains("カレンダー") -> "日めくりカレンダーアイコン"
            combined.contains("keep") -> "黄色い電球のメモアイコン"
            combined.contains("files") || combined.contains("folder") || combined.contains("dir") -> "フォルダーアイコン"
            combined.contains("calculator") || combined.contains("電卓") || combined.contains("calc") -> "四則演算マークアイコン"
            combined.contains("recorder") || combined.contains("ボイスレコーダー") -> "赤い録音丸ボタンアイコン"
            combined.contains("contacts") || combined.contains("連絡先") || combined.contains("アドレス帳") -> "人型アドレス帳アイコン"
            combined.contains("watch") || combined.contains("clock") || combined.contains("alarm") || combined.contains("timer") || combined.contains("時計") -> "腕時計アイコン"
            combined.contains("weather") || combined.contains("sun") || combined.contains("cloud") || combined.contains("rain") || combined.contains("天気") -> "お天気マーク"
            combined.contains("setting") || combined.contains("gear") || combined.contains("config") || combined.contains("preference") || combined.contains("設定") -> "歯車アイコン"
            combined.contains("camera") || combined.contains("shutter") || combined.contains("lens") || combined.contains("カメラ") -> "レンズ付きカメラアイコン"

            // ==========================================
            // 3. SNS・メッセージ・コミュニケーション
            // ==========================================
            combined.contains("whatsapp") -> "緑色の吹き出しに白い受話器アイコン"
            combined.contains("telegram") -> "青い丸に白い紙飛行機アイコン"
            combined.contains("signal") -> "青い四角に白い吹き出しアイコン"
            combined.contains("line") -> "緑色の吹き出しにLINEのロゴ"
            combined == "x" || combined.contains("twitter") -> "黒地に白いXマーク"
            combined.contains("instagram") -> "紫からピンクのグラデーションカメラアイコン"
            combined.contains("facebook") -> "青い四角に白いfのマーク"
            combined.contains("messenger") -> "青から紫のグラデーション雷吹き出しアイコン"
            combined.contains("tiktok") -> "黒地に水色と赤の立体音符マーク"
            combined.contains("snapchat") -> "黄色い四角に白いお化けマーク"
            combined.contains("reddit") -> "オレンジ色の丸に白い宇宙人マーク"
            combined.contains("discord") -> "青紫色のゲームコントローラー風の顔アイコン"
            combined.contains("slack") -> "4色の格子ハッシュマークアイコン"
            combined.contains("zoom") -> "青いビデオカメラアイコン"
            combined.contains("teams") -> "紫色のTと人物アイコン"
            combined.contains("skype") -> "青い丸に白いSマーク"
            combined.contains("threads") -> "黒地に白いアットマーク風アイコン"
            combined.contains("bluesky") -> "青い蝶々のアイコン"
            combined.contains("pinterest") -> "赤い丸に白いPのピンマーク"
            combined.contains("linkedin") -> "青い四角に白いinの文字"
            combined.contains("phone") || combined.contains("dialer") || combined.contains("call") || combined.contains("電話") -> "受話器アイコン"
            combined.contains("message") || combined.contains("sms") || combined.contains("chat") || combined.contains("talk") || combined.contains("メッセージ") -> "吹き出しアイコン"

            // ==========================================
            // 4. ショッピング・決済・デリバリー
            // ==========================================
            combined.contains("amazon") -> "黄色い笑顔矢印アイコン"
            combined.contains("paypay") -> "赤いPの四角いロゴアイコン"
            combined.contains("paypal") -> "濃い青と水色の重なる2つのPマーク"
            combined.contains("uber eats") || combined.contains("ubereats") -> "緑と黒のUberEatsロゴ"
            combined.contains("uber") -> "黒地に白いUber文字"
            combined.contains("demaecan") || combined.contains("出前館") -> "赤いバイクと笑顔マーク"
            combined.contains("mercari") || combined.contains("メルカリ") -> "青赤黄のカラフルなメルカリマーク"
            combined.contains("rakuten") || combined.contains("楽天市場") || combined.contains("楽天ペイ") -> "赤い丸に白いRの文字"
            combined.contains("suica") -> "緑と黒のペンギンマーク"
            combined.contains("pasmo") -> "ピンク色のロボットマーク"
            combined.contains("wallet") || combined.contains("ウォレット") -> "カードとお財布のアイコン"
            combined.contains("cart") || combined.contains("shopping") || combined.contains("basket") || combined.contains("bag") || combined.contains("買い物") -> "買い物かごアイコン"

            // ==========================================
            // 5. 開発・ドキュメント・ノート
            // ==========================================
            combined.contains("github") -> "黒い丸に白いオクトキャットアイコン"
            combined.contains("notion") -> "黒い四角に白いNの立方体アイコン"
            combined.contains("obsidian") -> "紫色の結晶クリスタルアイコン"
            combined.contains("evernote") -> "緑色の四角に白いゾウの横顔アイコン"
            combined.contains("dropbox") -> "青い開いた段ボール箱アイコン"
            combined.contains("1password") -> "青い丸に白い鍵穴アイコン"
            combined.contains("bitwarden") -> "青い盾と二重線のアイコン"

            // ==========================================
            // 6. UI標準ボタン・ナビゲーション
            // ==========================================
            combined.contains("search") || combined.contains("find") || combined.contains("magnif") || combined.contains("検索") -> "虫眼鏡アイコン"
            combined.contains("menu") || combined.contains("nav") || combined.contains("drawer") || combined.contains("hamburger") || combined.contains("メニュー") -> "三本線メニューアイコン"
            combined.contains("overflow") || combined.contains("more") || combined.contains("kebab") || combined.contains("dots") -> "三点リーダーメニューアイコン"
            combined.contains("back") || combined.contains("arrow_back") || combined.contains("prev") || combined.contains("戻る") -> "左向き矢印アイコン"
            combined.contains("next") || combined.contains("arrow_forward") || combined.contains("forward") || combined.contains("進む") || combined.contains("次へ") -> "右向き矢印アイコン"
            combined.contains("close") || combined.contains("clear") || combined.contains("dismiss") || combined.contains("cancel") || combined.contains("閉じる") -> "バツ印アイコン"
            combined.contains("delete") || combined.contains("trash") || combined.contains("remove") || combined.contains("bin") || combined.contains("削除") -> "ゴミ箱アイコン"
            combined.contains("add") || combined.contains("plus") || combined.contains("create") || combined.contains("new") || combined.contains("追加") -> "プラス記号アイコン"
            combined.contains("share") || combined.contains("export") || combined.contains("共有") -> "共有アイコン"
            combined.contains("favorite") || combined.contains("heart") || combined.contains("like") || combined.contains("お気に入り") -> "ハートアイコン"
            combined.contains("star") || combined.contains("bookmark") -> "星マークアイコン"
            combined.contains("mic") || combined.contains("speech") || combined.contains("マイク") -> "マイクアイコン"
            combined.contains("play") -> "再生三角アイコン"
            combined.contains("pause") -> "一時停止二本線アイコン"
            combined.contains("stop") -> "停止四角アイコン"
            combined.contains("volume") || combined.contains("speaker") || combined.contains("音量") -> "スピーカーアイコン"
            combined.contains("mute") || combined.contains("消音") -> "消音アイコン"
            combined.contains("download") || combined.contains("ダウンロード") -> "下向き矢印アイコン"
            combined.contains("upload") || combined.contains("アップロード") -> "上向き矢印アイコン"
            combined.contains("bell") || combined.contains("notification") || combined.contains("alert") || combined.contains("通知") -> "通知ベルアイコン"
            combined.contains("profile") || combined.contains("avatar") || combined.contains("account") || combined.contains("user") || combined.contains("person") || combined.contains("アカウント") -> "人型アカウントアイコン"

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
            val baseName = shape.replace("アイコン", "").replace("マーク", "").replace("ロゴ", "")
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

        if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
            val shape = getVisualShapeDescription(node, if (text.isNotEmpty()) text else contentDesc)
            if (shape.isNotEmpty()) return shape
            return if (contentDesc.isNotEmpty()) contentDesc else text
        }

        return inferLabelForUnlabelledNode(node)
    }
}
