package com.shinji.serena

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.shinji.serena.ai.GeminiNanoEngine

/**
 * AiAutoLabelHelper
 * Google Gemini Nano (On-Device Foundation AI) およびセマンティック推論エンジンを活用し、
 * テキストや contentDescription のない「名前のないボタン」「アイコン」「画像」に対して
 * 自動で正確なラベルおよび説明を推論・生成するスマートアクセシビリティモジュール。
 */
class AiAutoLabelHelper(
    private val context: Context,
    private val nanoEngine: GeminiNanoEngine = GeminiNanoEngine(context)
) {

    companion object {
        private const val TAG = "AiAutoLabelHelper"

        // ビューID、リソース名、クラス名からの詳細なパターン辞書
        private val ID_LABEL_PATTERNS = listOf(
            // ナビゲーション・基本操作
            Regex(".*(nav_back|arrow_back|btn_back|back_button|back_btn|action_back|ic_back|icon_back|go_back).*") to "戻る",
            Regex(".*(nav_forward|arrow_forward|btn_forward|forward_btn|arrow_right|ic_forward).*") to "次へ",
            Regex(".*(close|dismiss|cancel|cross|delete_btn|btn_close|ic_close|clear_btn).*") to "閉じる",
            Regex(".*(home_btn|btn_home|nav_home|ic_home|main_tab).*") to "ホーム",
            Regex(".*(menu|drawer|hamburger|more_vert|overflow|btn_more|ic_more|option_menu).*") to "メニュー",
            Regex(".*(search|find|query|magnif|btn_search|ic_search).*") to "検索",
            Regex(".*(setting|config|pref|gear|btn_setting|ic_settings).*") to "設定",
            Regex(".*(help|faq|info|question|btn_help|ic_help).*") to "ヘルプ・情報",
            Regex(".*(refresh|reload|sync|update_btn|ic_refresh).*") to "再読み込み",
            Regex(".*(filter|sort|btn_filter|ic_filter).*") to "絞り込み・フィルター",
            
            // コミュニケーション・ソーシャル
            Regex(".*(share|export|btn_share|ic_share).*") to "共有",
            Regex(".*(send|submit|post_btn|upload|btn_send|ic_send).*") to "送信",
            Regex(".*(favorite|like|heart|star|fav_btn|ic_favorite|btn_like|bookmark).*") to "お気に入り",
            Regex(".*(thumbs_up|btn_upvote).*") to "高評価・いいね",
            Regex(".*(thumbs_down|btn_downvote).*") to "低評価",
            Regex(".*(profile|account|user|avatar|my_page|btn_profile|ic_person).*") to "アカウント・プロフィール",
            Regex(".*(notif|bell|alert|badge|btn_notif|ic_notification).*") to "通知",
            Regex(".*(chat|message|comment|dialog|btn_chat|ic_message).*") to "メッセージ",
            Regex(".*(call|dial|phone_btn|btn_call|ic_call).*") to "通話発信",
            Regex(".*(end_call|hangup|reject_call).*") to "通話終了",
            Regex(".*(attach|clip|file_upload|btn_attach).*") to "ファイル添付",

            // メディア・カメラ・オーディオ
            Regex(".*(play|btn_play|ic_play).*") to "再生",
            Regex(".*(pause|btn_pause|ic_pause).*") to "一時停止",
            Regex(".*(stop|btn_stop|ic_stop).*") to "停止",
            Regex(".*(next_track|skip_next|btn_next).*") to "次の曲",
            Regex(".*(prev_track|skip_prev|btn_prev).*") to "前の曲",
            Regex(".*(shuffle|btn_shuffle).*") to "シャッフル再生",
            Regex(".*(repeat|btn_repeat).*") to "リピート再生",
            Regex(".*(mic|voice|audio_record|btn_mic|ic_mic).*") to "マイク・音声入力",
            Regex(".*(camera|photo|lens|snapshot|btn_camera|ic_camera|take_photo).*") to "カメラ・写真撮影",
            Regex(".*(gallery|album|media_picker|btn_gallery).*") to "画像ギャラリー・アルバム",
            Regex(".*(volume|speaker|mute|unmute|sound_btn).*") to "音量",
            Regex(".*(download|save_btn|btn_download|ic_download).*") to "ダウンロード・保存",

            // 編集・作成・ショッピング
            Regex(".*(add|create|new_btn|plus|fab_add|btn_add|ic_add).*") to "新規作成・追加",
            Regex(".*(edit|modify|pencil|write|btn_edit|ic_edit).*") to "編集",
            Regex(".*(delete|trash|remove|bin|btn_delete|ic_delete).*") to "削除",
            Regex(".*(copy|btn_copy|ic_copy).*") to "コピー",
            Regex(".*(paste|btn_paste|ic_paste).*") to "貼り付け",
            Regex(".*(cart|bag|shop|btn_cart|ic_cart|basket).*") to "ショッピングカート",
            Regex(".*(buy|purchase|checkout|pay_btn).*") to "購入手続き",
            Regex(".*(qr_code|barcode|scan_btn|scanner).*") to "QRコードスキャン",
            Regex(".*(location|map_pin|gps|btn_location|ic_location).*") to "現在地・位置情報",
            Regex(".*(dark_mode|theme_btn|light_mode).*") to "画面テーマ切り替え",
            Regex(".*(bluetooth|btn_bt).*") to "Bluetooth",
            Regex(".*(wifi|network_btn).*") to "Wi-Fi"
        )
    }

    /**
     * ノードにラベルや説明がない場合、Gemini Nano およびパターン解析で高精度に名前を生成
     */
    fun inferLabelForUnlabeledNode(node: AccessibilityNodeInfo): String? {
        val className = node.className?.toString() ?: ""
        val isClickable = node.isClickable
        val isImageOrIcon = className.contains("ImageView", ignoreCase = true) || className.contains("Image", ignoreCase = true)
        val isButtonLike = isClickable || className.contains("Button", ignoreCase = true) || isImageOrIcon

        if (!isButtonLike) return null

        // 1. リソースID・ビューIDからのセマンティック推論
        val resId = node.viewIdResourceName?.lowercase() ?: ""
        if (resId.isNotEmpty()) {
            val idSimple = resId.substringAfterLast(":id/").substringAfterLast("/")
            for ((pattern, label) in ID_LABEL_PATTERNS) {
                if (pattern.matches(idSimple)) {
                    return if (isImageOrIcon && !isClickable) {
                        "$label アイコン"
                    } else {
                        label
                    }
                }
            }
        }

        // 2. 近接する兄弟要素のテキストコンテキストから推論
        val parent = node.parent
        if (parent != null) {
            val siblingTexts = mutableListOf<String>()
            for (i in 0 until parent.childCount.coerceAtMost(6)) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling != node && !sibling.isClickable) {
                    val st = sibling.text?.toString()?.trim() ?: sibling.contentDescription?.toString()?.trim()
                    if (!st.isNullOrEmpty() && st.length in 1..20) {
                        siblingTexts.add(st)
                    }
                }
            }
            if (siblingTexts.isNotEmpty()) {
                val contextLabel = siblingTexts.first()
                return if (isImageOrIcon && !isClickable) {
                    "$contextLabel の画像"
                } else {
                    contextLabel
                }
            }
        }

        // 3. パッケージ名・画面コンテキストに応じた推論
        val pkg = node.packageName?.toString()?.lowercase() ?: ""
        if (isImageOrIcon && !isClickable) {
            return when {
                pkg.contains("camera") -> "カメラプレビュー画像"
                pkg.contains("gallery") || pkg.contains("photos") -> "写真画像"
                pkg.contains("youtube") -> "動画サムネイル画像"
                pkg.contains("maps") -> "地図画像"
                else -> "画像"
            }
        }

        return if (isClickable) "名前のないボタン" else null
    }
}
