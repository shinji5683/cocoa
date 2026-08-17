package com.shinji.serena.ime

/**
 * Serena IME / ScreenReader - クアッド言語フォネティック読み専門エンジン
 * 🇯🇵 日本語 (漢字詳細説明 / ひらがな・カタカナ・半角カナ完全識別 / 和文通話表)
 * 🇺🇸 英語 (NATOフォネティックコード: Alpha, Bravo...)
 * 🇵🇭 タガログ語 (Tagalog / Filipino Phonetic: Araw, Bahay, Maganda, Mahal, Puso...)
 * 🏝️ ビサヤ語 (Bisaya / Cebuano Phonetic: Balay, Dako, Gwapa, Gugma, Palangga...)
 */
object SerenaPhoneticEngine {

    // NATO 欧文フォネティックコード (アルファベット A-Z)
    private val natoPhoneticMap = mapOf(
        'A' to "Alpha（アルファ）",
        'B' to "Bravo（ブラボー）",
        'C' to "Charlie（チャーリー）",
        'D' to "Delta（デルタ）",
        'E' to "Echo（エコー）",
        'F' to "Foxtrot（フォックストロット）",
        'G' to "Golf（ゴルフ）",
        'H' to "Hotel（ホテル）",
        'I' to "India（インド）",
        'J' to "Juliet（ジュリエット）",
        'K' to "Kilo（キーロ）",
        'L' to "Lima（リマ）",
        'M' to "Mike（マイク）",
        'N' to "November（ノーベンバー）",
        'O' to "Oscar（オスカー）",
        'P' to "Papa（パパ）",
        'Q' to "Quebec（ケベック）",
        'R' to "Romeo（ロメオ）",
        'S' to "Sierra（シエラ）",
        'T' to "Tango（タンゴ）",
        'U' to "Uniform（ユニフォーム）",
        'V' to "Victor（ビクター）",
        'W' to "Whiskey（ウイスキー）",
        'X' to "X-ray（エックスレイ）",
        'Y' to "Yankee（ヤンキー）",
        'Z' to "Zulu（ズールー）"
    )

    // タガログ語 (Tagalog / Filipino) フォネティック辞書
    private val tagalogPhoneticMap = mapOf(
        'A' to "Araw（太陽）のA",
        'B' to "Bahay（家）のB",
        'C' to "CaloocanのC",
        'D' to "Dagat（海）のD",
        'E' to "Eroplano（飛行機）のE",
        'F' to "FilipinoのF",
        'G' to "Gatas（ミルク）のG",
        'H' to "Halaman（植物）のH",
        'I' to "Ibon（鳥）のI",
        'J' to "JeepneyのJ",
        'K' to "Kamay（手）のK",
        'L' to "Langit（空・天国）のL",
        'M' to "Maganda / Mahal kita（愛してる）のM",
        'N' to "Nanay（お母さん）のN",
        'O' to "Oras（時間）のO",
        'P' to "Puso（心・愛）のP",
        'Q' to "QuezonのQ",
        'R' to "Rosas（バラ）のR",
        'S' to "Salamat（ありがとう）のS",
        'T' to "Tao（人間）のT",
        'U' to "Ulan（雨）のU",
        'V' to "VintaのV",
        'W' to "Watawat（国旗）のW",
        'X' to "X-rayのX",
        'Y' to "Yakap（抱きしめる）のY",
        'Z' to "ZamboangaのZ"
    )

    // ビサヤ語 / セブアノ語 (Bisaya / Cebuano) フォネティック辞書
    private val bisayaPhoneticMap = mapOf(
        'A' to "Adlaw（太陽・日）のA",
        'B' to "Balay（家）のB",
        'C' to "Cebu（セブ）のC",
        'D' to "Dako（大きい）のD",
        'E' to "Eskwela（学校）のE",
        'F' to "FiestaのF",
        'G' to "Gwapa（美しい・可愛い）のG",
        'H' to "Higugma / Gugma（愛）のH",
        'I' to "Iro（犬）のI",
        'J' to "JollibeeのJ",
        'K' to "Kasingkasing（心）のK",
        'L' to "Lami（美味しい）のL",
        'M' to "Maayo（素晴らしい・良い）のM",
        'N' to "Nindot（素敵・綺麗）のN",
        'O' to "OspitalのO",
        'P' to "Palangga（最愛の人）のP",
        'Q' to "QuickのQ",
        'R' to "RadyoのR",
        'S' to "Salamat（ありがとう）のS",
        'T' to "Tubig（水）のT",
        'U' to "Ulan（雨）のU",
        'V' to "VictoryのV",
        'W' to "WalaのW",
        'X' to "XylophoneのX",
        'Y' to "Yuta（大地）のY",
        'Z' to "ZeroのZ"
    )

    // 和文通話表ベースのカナルーツ
    private val kanaBaseDescMap = mapOf(
        'ア' to "朝のあ", 'イ' to "いろはのい", 'ウ' to "上野のう", 'エ' to "英語のえ", 'オ' to "大阪のお",
        'カ' to "為替のか", 'キ' to "切手のき", 'ク' to "クラブのく", 'ケ' to "景気のけ", 'コ' to "子供のこ",
        'サ' to "桜のさ", 'シ' to "新聞のし", 'ス' to "すずめのす", 'セ' to "世界のせ", 'ソ' to "そろばんのそ",
        'タ' to "煙草のた", 'チ' to "千鳥のち", 'ツ' to "月夜のつ", 'テ' to "手紙のて", 'ト' to "東京のと",
        'ナ' to "名古屋のな", 'ニ' to "日本のに", 'ヌ' to "沼津のぬ", 'ネ' to "ねずみのね", 'ノ' to "野原のの",
        'ハ' to "はがきのは", 'ヒ' to "飛行機のひ", 'フ' to "富士山のふ", 'ヘ' to "平和のへ", 'ホ' to "保険のほ",
        'マ' to "マッチのま", 'ミ' to "三笠のみ", 'ム' to "無線のむ", 'メ' to "明治のめ", 'モ' to "もみじのも",
        'ヤ' to "大和のや", 'ユ' to "弓矢のゆ", 'ヨ' to "吉野のよ",
        'ラ' to "ラジオのら", 'リ' to "りんごのり", 'ル' to "留守居のる", 'レ' to "れんげのれ", 'ロ' to "ローマのろ",
        'ワ' to "わらびのわ", 'ヰ' to "井戸のゐ", 'ヱ' to "かぎのゑ", 'ヲ' to "尾張のを", 'ン' to "おしまいのん",
        'ガ' to "為替のが・濁点", 'ギ' to "切手のぎ・濁点", 'グ' to "クラブのぐ・濁点", 'ゲ' to "景気のげ・濁点", 'ゴ' to "子供のご・濁点",
        'ザ' to "桜のざ・濁点", 'ジ' to "新聞のじ・濁点", 'ズ' to "すずめのず・濁点", 'ゼ' to "世界のぜ・濁点", 'ゾ' to "そろばんのぞ・濁点",
        'ダ' to "煙草のだ・濁点", 'ヂ' to "千鳥のぢ・濁点", 'ヅ' to "月夜のづ・濁点", 'デ' to "手紙ので・濁点", 'ド' to "東京のど・濁点",
        'バ' to "はがきのば・濁点", 'ビ' to "飛行機のび・濁点", 'ブ' to "富士山のぶ・濁点", 'ベ' to "平和のべ・濁点", 'ボ' to "保険のぼ・濁点",
        'パ' to "はがきのぱ・半濁点", 'ピ' to "飛行機のぴ・半濁点", 'プ' to "富士山のぷ・半濁点", 'ペ' to "平和のぺ・半濁点", 'ポ' to "保険のぽ・半濁点",
        'ぁ' to "小文字のぁ", 'ぃ' to "小文字のぃ", 'ぅ' to "小文字のぅ", 'ぇ' to "小文字のぇ", 'ぉ' to "小文字のぉ",
        'ァ' to "小文字のア", 'ィ' to "小文字のイ", 'ゥ' to "小文字のウ", 'ェ' to "小文字のエ", 'ォ' to "小文字のオ",
        'っ' to "小文字のっ", 'ッ' to "小文字のツ",
        'ゃ' to "小文字のゃ", 'ャ' to "小文字のヤ", 'ゅ' to "小文字のゅ", 'ュ' to "小文字のユ", 'ょ' to "小文字のょ", 'ョ' to "小文字のヨ",
        'ゎ' to "小文字のゎ", 'ヮ' to "小文字のワ"
    )

    /**
     * 文字のクアッド言語フォネティック詳細読みを取得
     * ひらがな・カタカナ・漢字・英字・数字を完全に聞き分けられるように識別詞を付与！
     */
    fun getPhoneticReading(char: Char): String {
        return try {
            val code = char.code

            // 1. 漢字（CJK Unified Ideographs / 常用・人名・繁体字・簡体字）の完全識別
            if (isKanji(char)) {
                val kanjiDetail = SerenaFullKanjiDetailDictionary.getKanjiDetail(char.toString())
                return if (kanjiDetail.isNotEmpty()) {
                    "漢字の $char、$kanjiDetail"
                } else {
                    "漢字の $char"
                }
            }

            // 2. ひらがな（Hiragana: \u3041 .. \u3096）の完全識別
            if (code in 0x3041..0x3096) {
                val kata = (code + 0x60).toChar()
                val desc = kanaBaseDescMap[char] ?: kanaBaseDescMap[kata] ?: "$char のひらがな"
                return "ひらがなの $char（$desc）"
            }

            // 3. 全角カタカナ（Katakana: \u30A1 .. \u30FA）の完全識別
            if (code in 0x30A1..0x30FA) {
                val desc = kanaBaseDescMap[char] ?: "$char のカタカナ"
                return "カタカナの $char（$desc）"
            }

            // 4. 半角カタカナ（Half-width Katakana: \uFF66 .. \uFF9D）
            if (code in 0xFF66..0xFF9D) {
                return "半角カタカナの $char"
            }

            // 5. アルファベット (A-Z, a-z) の場合、NATO ＋ タガログ語 ＋ ビサヤ語
            val upperChar = char.uppercaseChar()
            if (natoPhoneticMap.containsKey(upperChar)) {
                val isLower = char.isLowerCase()
                val nato = natoPhoneticMap[upperChar] ?: upperChar.toString()
                val tagalog = tagalogPhoneticMap[upperChar] ?: ""
                val bisaya = bisayaPhoneticMap[upperChar] ?: ""

                val prefix = if (isLower) "小文字 " else "大文字 "
                val tagalogBisayaDesc = if (tagalog.isNotEmpty() || bisaya.isNotEmpty()) {
                    "（タガログ: $tagalog、ビサヤ: $bisaya）"
                } else ""

                return "$prefix$upperChar、$nato $tagalogBisayaDesc"
            }

            // 6. 数字 (0-9, ０-９)
            if (char.isDigit()) {
                val isFullWidth = code in 0xFF10..0xFF19
                val prefix = if (isFullWidth) "全角数字の " else "数字の "
                val digitReading = when (char) {
                    '0', '０' -> "ゼロ"
                    '1', '１' -> "イチ"
                    '2', '２' -> "ニ"
                    '3', '３' -> "サン"
                    '4', '４' -> "ヨン"
                    '5', '５' -> "ゴ"
                    '6', '６' -> "ロク"
                    '7', '７' -> "ナナ"
                    '8', '８' -> "ハチ"
                    '9', '９' -> "キュウ"
                    else -> char.toString()
                }
                return "$prefix$char（$digitReading）"
            }

            // 7. 特殊記号・フィリピン文字 (Ñ/ñ)
            when (char) {
                'Ñ' -> "大文字 エニェ (Eñe / NiñosのÑ)"
                'ñ' -> "小文字 エニェ (Eñe / Niñosのñ)"
                '@', '＠' -> "記号 アットマーク"
                '#', '＃' -> "記号 シャープ"
                '$', '＄' -> "記号 ドル"
                '%', '％' -> "記号 パーセント"
                '&', '＆' -> "記号 アンド"
                '*', '＊' -> "記号 アスタリスク"
                '+', '＋' -> "記号 プラス"
                '-', 'ー', '―', '‐' -> "長音またはハイフン"
                '=' -> "記号 イコール"
                '/' -> "記号 スラッシュ"
                '\\' -> "記号 バックスラッシュ"
                '?', '？' -> "クエスチョンマーク"
                '!', '！' -> "ビックリマーク"
                ' ' -> "スペース"
                '　' -> "全角スペース"
                '\n' -> "改行"
                '。' -> "句点（まる）"
                '、' -> "読点（てん）"
                '「', '」' -> "かぎかっこ"
                '（', '）', '(', ')' -> "まるかっこ"
                '『', '』' -> "二重かぎかっこ"
                '【', '】' -> "すみつきかっこ"
                '・' -> "中黒（なかぐろ）"
                '…' -> "三点リーダー"
                '～', '~' -> "波ダッシュ（から）"
                else -> char.toString()
            }
        } catch (e: Exception) {
            char.toString()
        }
    }

    /**
     * 漢字判定（CJK統合漢字、拡張漢字、CJK互換漢字など）
     */
    private fun isKanji(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) ||      // CJK Unified Ideographs (常用・主要漢字)
               (code in 0x3400..0x4DBF) ||      // CJK Unified Ideographs Extension A
               (code in 0xF900..0xFAFF) ||      // CJK Compatibility Ideographs
               (code in 0x20000..0x2A6DF) ||    // CJK Unified Ideographs Extension B
               Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN
    }
}
