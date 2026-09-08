package com.shinji.serena.ai

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * FashionMoodHelper
 *
 * 生まれつき視覚を持たない全盲ユーザーのために、
 * 単なる「赤」「青」といった記号的な色名ではなく、
 * 「温度感（あたたかさ・涼しさ）」「光のトーン」「肌ざわりの質感」「雰囲気・印象」
 * に翻訳して心に情景を届ける、Serena独自のファッション＆色彩情景エンジン。
 */
object FashionMoodHelper {

    data class FashionMoodDescription(
        val simpleColorName: String,     // 例: "ライトブルー"
        val sensoryTone: String,         // 例: "夏の空や水のように涼しげで澄んだトーン"
        val emotionalVibe: String,       // 例: "爽やかで清潔感あふれる明るい印象"
        val fullSpokenDescription: String // 例: "水のように涼しげで澄んだライトブルー。爽やかで清潔感あふれる印象です。"
    )

    /**
     * RGB値から、全盲の感覚に直感的に届く色彩ムード表現を生成
     */
    fun describeColorFromRgb(r: Int, g: Int, b: Int): FashionMoodDescription {
        val brightness = (r * 299 + g * 587 + b * 114) / 1000
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal
        val saturation = if (maxVal == 0) 0f else delta.toFloat() / maxVal

        // 1. 白系（クリアホワイト、クリーム）
        if (brightness >= 210) {
            return if (saturation < 0.12f) {
                FashionMoodDescription(
                    simpleColorName = "クリアホワイト",
                    sensoryTone = "曇りのないパリッとした清潔感のあるトーン",
                    emotionalVibe = "清潔感にあふれた明るく清らかな印象",
                    fullSpokenDescription = "パリッと清潔感のあるクリアホワイト。明るく清らかな印象です。"
                )
            } else {
                FashionMoodDescription(
                    simpleColorName = "クリームホワイト",
                    sensoryTone = "ミルクのようにほんのり温かみを感じる柔らかなトーン",
                    emotionalVibe = "優しく穏やかで親しみやすい雰囲気",
                    fullSpokenDescription = "ミルクのようにほんのり温かみのあるクリームホワイト。優しく穏やかな雰囲気です。"
                )
            }
        }

        // 2. 黒系（漆黒、チャコール）
        if (brightness <= 40) {
            return FashionMoodDescription(
                simpleColorName = "漆黒ブラック",
                sensoryTone = "キリッと全体が美しく引き締まる深みのあるトーン",
                emotionalVibe = "上品でフォーマルなかっこいい印象",
                fullSpokenDescription = "キリッと全体が引き締まる漆黒ブラック。上品でシックなかっこいい印象です。"
            )
        }

        // 3. 低彩度（グレー、ベージュ、ブラウン）
        if (saturation < 0.18f) {
            return if (brightness > 130) {
                FashionMoodDescription(
                    simpleColorName = "ライトグレー",
                    sensoryTone = "ふんわりと柔らかく風通しのよい軽快なトーン",
                    emotionalVibe = "洗練されていて肩の力が抜けた自然体の印象",
                    fullSpokenDescription = "柔らかく軽快なライトグレー。洗練された自然体の印象です。"
                )
            } else {
                FashionMoodDescription(
                    simpleColorName = "チャコールグレー",
                    sensoryTone = "落ち着きのあるシックで上品な深みトーン",
                    emotionalVibe = "大人っぽく落ち着いた知的な雰囲気",
                    fullSpokenDescription = "落ち着きのあるチャコールグレー。大人っぽく知的な雰囲気です。"
                )
            }
        }

        // 4. 有彩色（色相ごとの情緒的・感性的表現）
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0] // 0..360

        return when (hue) {
            // 赤〜ピンク (345..360, 0..15)
            in 345f..360f, in 0f..15f -> {
                if (brightness < 100) {
                    FashionMoodDescription(
                        simpleColorName = "ワインレッド",
                        sensoryTone = "暖炉の火のようにじんわりとあたたかい深みトーン",
                        emotionalVibe = "落ち着いた大人の温もりと上品な華やかさ",
                        fullSpokenDescription = "暖炉の火のようにじんわり温かいワインレッド。落ち着いた大人の上品な華やかさです。"
                    )
                } else if (saturation > 0.6f) {
                    FashionMoodDescription(
                        simpleColorName = "チェリーレッド",
                        sensoryTone = "太陽の光のようにパッと目を引く情熱的なトーン",
                        emotionalVibe = "明るく元気いっぱいでエネルギッシュな印象",
                        fullSpokenDescription = "パッと目を引く鮮やかなチェリーレッド。明るく元気いっぱいのエネルギッシュな印象です。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "パステルローズ",
                        sensoryTone = "咲き始めの花びらのようにふんわり優しいトーン",
                        emotionalVibe = "愛らしく華やかで心和らぐ雰囲気",
                        fullSpokenDescription = "ふんわり優しいパステルローズ。愛らしく華やかな雰囲気です。"
                    )
                }
            }
            // オレンジ〜テラコッタ (16..45)
            in 16f..45f -> {
                if (brightness < 110) {
                    FashionMoodDescription(
                        simpleColorName = "テラコッタ・ブラウン",
                        sensoryTone = "太陽を浴びた大地の土のようにあたたかく豊かなトーン",
                        emotionalVibe = "頼りがいのある落ち着いたぬくもり",
                        fullSpokenDescription = "あたたかい大地のようなテラコッタブラウン。落ち着いたぬくもりを感じる装いです。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "アプリコットオレンジ",
                        sensoryTone = "果実のようにジューシーで暖かなトーン",
                        emotionalVibe = "親しみやすく笑顔がこぼれる明るい雰囲気",
                        fullSpokenDescription = "暖かみあふれるアプリコットオレンジ。親しみやすく明るい雰囲気です。"
                    )
                }
            }
            // イエロー〜ベージュ (46..70)
            in 46f..70f -> {
                if (saturation < 0.35f) {
                    FashionMoodDescription(
                        simpleColorName = "カフェラテ・ベージュ",
                        sensoryTone = "木肌や砂のように肌なじみがよく包み込むようなトーン",
                        emotionalVibe = "優しくて安心感のある穏やかな印象",
                        fullSpokenDescription = "肌なじみのいいカフェラテベージュ。優しく安心感のある穏やかな装いです。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "陽だまりイエロー",
                        sensoryTone = "春のぽかぽかした陽射しを浴びているようなトーン",
                        emotionalVibe = "見ているだけで楽しくなる元気でフレンドリーな印象",
                        fullSpokenDescription = "ぽかぽか陽だまりのようなイエロー。見ているだけで楽しくなるフレンドリーな印象です。"
                    )
                }
            }
            // グリーン〜オリーブ (71..165)
            in 71f..165f -> {
                if (brightness < 90) {
                    FashionMoodDescription(
                        simpleColorName = "ディープフォレストグリーン",
                        sensoryTone = "深い森の木立に包まれたように静かで澄んだトーン",
                        emotionalVibe = "落ち着きと信頼感のある洗練された雰囲気",
                        fullSpokenDescription = "深い森のように静かなフォレストグリーン。落ち着きと信頼感のある洗練された雰囲気です。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "ナチュラルミントグリーン",
                        sensoryTone = "若葉や草原を吹き抜ける風のようにみずみずしく爽快なトーン",
                        emotionalVibe = "リフレッシュ感のある優しく穏やかな印象",
                        fullSpokenDescription = "草原の風のようにみずみずしいミントグリーン。爽やかで心安らぐ印象です。"
                    )
                }
            }
            // シアン〜スカイブルー (166..205)
            in 166f..205f -> {
                FashionMoodDescription(
                    simpleColorName = "クリアスカイブルー",
                    sensoryTone = "澄み切った夏の青空や水のように涼やかで透明感のあるトーン",
                    emotionalVibe = "爽快で涼しげな、清潔感あふれる素敵な印象",
                    fullSpokenDescription = "澄んだ青空のように涼やかなスカイブルー。清潔感あふれる爽快な印象です。"
                )
            }
            // ブルー〜ネイビー (206..260)
            in 206f..260f -> {
                if (brightness < 80) {
                    FashionMoodDescription(
                        simpleColorName = "シックなダークネイビー",
                        sensoryTone = "夜空のように静寂で深みのあるトーン",
                        emotionalVibe = "上品できちんとした、知性と品格を感じる装い",
                        fullSpokenDescription = "夜空のように深くて上品なダークネイビー。知性と品格を感じるきちんとした装いです。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "ロイヤルブルー",
                        sensoryTone = "澄んだ海のように力強く鮮やかなトーン",
                        emotionalVibe = "凛としていて華やかでスマートな印象",
                        fullSpokenDescription = "鮮やかで力強いロイヤルブルー。凛とした華やかさのあるスマートな印象です。"
                    )
                }
            }
            // パープル〜ラベンダー (261..315)
            in 261f..315f -> {
                if (brightness > 120) {
                    FashionMoodDescription(
                        simpleColorName = "優しいラベンダーパープル",
                        sensoryTone = "そよ風に揺れる花のように甘やかで優美なトーン",
                        emotionalVibe = "気品があり、とても優雅で穏やかな雰囲気",
                        fullSpokenDescription = "そよ風に揺れる花のようなラベンダーパープル。優雅で心安らぐ雰囲気です。"
                    )
                } else {
                    FashionMoodDescription(
                        simpleColorName = "ノーブルパープル",
                        sensoryTone = "深みと神秘的な美しさを湛えたトーン",
                        emotionalVibe = "大人の落ち着きと特別な華やかさ",
                        fullSpokenDescription = "深みのあるノーブルパープル。大人の落ち着きと華やかさを感じる装いです。"
                    )
                }
            }
            // マゼンタ〜ピンク (316..344)
            else -> {
                FashionMoodDescription(
                    simpleColorName = "華やかピンク",
                    sensoryTone = "春の陽気のように暖かく華やぐトーン",
                    emotionalVibe = "周りをパッと明るくする愛らしく魅力的な印象",
                    fullSpokenDescription = "パッと華やぐ温かいピンク。周りを明るくする愛らしく魅力的な印象です。"
                )
            }
        }
    }
}
