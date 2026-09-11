package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.shinji.serena.ai.GeminiNanoEngine
import com.shinji.serena.translation.InstantTranslationHelper
import java.util.Calendar

/**
 * Serena AI Voice Assistant Helper
 * Gemini Nano & 音声認識を活用したハンズフリーAI対話アシスタント。
 */
class SerenaAiAssistantHelper(private val service: SerenaScreenReaderService) {

    companion object {
        private const val TAG = "SerenaAiAssistant"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val nanoEngine = GeminiNanoEngine(service)

    enum class AssistantMode {
        AI_ASSISTANT,
        VOICE_INPUT,
        VOICE_TRANSLATION
    }
    private var currentMode = AssistantMode.AI_ASSISTANT

    fun startListening() {
        startListeningWithMode(AssistantMode.AI_ASSISTANT, service.getString(R.string.assistant_prompt_ai))
    }

    fun startVoiceInput() {
        startListeningWithMode(AssistantMode.VOICE_INPUT, service.getString(R.string.assistant_prompt_voice_input))
    }

    fun startVoiceTranslation() {
        val targetLangName = InstantTranslationHelper.SUPPORTED_TARGET_LANGUAGES.find {
            it.first == service.instantTranslationHelper?.defaultTargetLanguageCode
        }?.second ?: "英語"
        startListeningWithMode(AssistantMode.VOICE_TRANSLATION, service.getString(R.string.assistant_prompt_voice_translation_fmt, targetLangName))
    }

    private fun startListeningWithMode(mode: AssistantMode, prompt: String) {
        currentMode = mode
        mainHandler.post {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(service, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    service.speak(service.getString(R.string.assistant_mic_permission_required), TextToSpeech.QUEUE_FLUSH)
                    val intent = Intent(service, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    service.startActivity(intent)
                    return@post
                }

                if (!SpeechRecognizer.isRecognitionAvailable(service)) {
                    service.speak(service.getString(R.string.assistant_speech_not_available), TextToSpeech.QUEUE_FLUSH)
                    return@post
                }

                stopListeningInternal()
                service.soundHelper?.playMenuOpen()
                service.speak(prompt, TextToSpeech.QUEUE_FLUSH)

                mainHandler.postDelayed({
                    startRecognizerInternal()
                }, 1000)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AI Assistant: ${e.message}")
                service.speak(service.getString(R.string.assistant_start_failed), TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun startRecognizerInternal() {
        try {
            speechRecognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(service)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(service)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(service)
                }
            } catch (e: Exception) {
                SpeechRecognizer.createSpeechRecognizer(service)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    service.soundHelper?.playActionDone()
                    Log.i(TAG, "SpeechRecognizer is ready for speech.")
                }

                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "User began speaking.")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    Log.i(TAG, "User finished speaking.")
                }

                override fun onError(error: Int) {
                    isListening = false
                    Log.e(TAG, "Speech recognition error: $error")
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            service.speak(service.getString(R.string.assistant_unrecognized), TextToSpeech.QUEUE_FLUSH)
                        }
                        SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            service.speak(service.getString(R.string.assistant_mic_check), TextToSpeech.QUEUE_FLUSH)
                        }
                        else -> {
                            service.speak(service.getString(R.string.assistant_listening_ended), TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                    stopListeningInternal()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val query = matches?.firstOrNull() ?: ""
                    Log.i(TAG, "Speech recognition result ($currentMode): $query")
                    if (query.isNotEmpty()) {
                        service.soundHelper?.playClick()
                        when (currentMode) {
                            AssistantMode.AI_ASSISTANT -> processCommand(query)
                            AssistantMode.VOICE_INPUT -> processVoiceInput(query)
                            AssistantMode.VOICE_TRANSLATION -> processVoiceTranslation(query)
                        }
                    } else {
                        service.speak(service.getString(R.string.assistant_speech_empty), TextToSpeech.QUEUE_FLUSH)
                    }
                    stopListeningInternal()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, service.packageName)
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer: ${e.message}")
            stopListeningInternal()
            service.speak(service.getString(R.string.assistant_start_failed), TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun processVoiceInput(text: String) {
        val root = service.rootInActiveWindow
        val focused = service.getAccessibilityFocusedNode() ?: root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        var injected = false
        if (focused != null && (focused.isEditable || focused.className?.contains("EditText", ignoreCase = true) == true)) {
            val args = Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            injected = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Serena Voice Input", text)
        cm?.setPrimaryClip(clip)
        service.soundHelper?.playActionDone()
        if (injected) {
            service.speak(service.getString(R.string.assistant_input_injected_fmt, text), TextToSpeech.QUEUE_FLUSH)
        } else {
            service.speak(service.getString(R.string.assistant_clipboard_copied_fmt, text), TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun processVoiceTranslation(inputQuery: String) {
        var targetLang = service.instantTranslationHelper?.defaultTargetLanguageCode ?: "en"
        var cleanQuery = inputQuery

        when {
            inputQuery.startsWith("英語で") -> {
                targetLang = "en"
                cleanQuery = inputQuery.removePrefix("英語で").trim()
            }
            inputQuery.startsWith("タガログ語で") || inputQuery.startsWith("フィリピノ語で") -> {
                targetLang = "tl"
                cleanQuery = inputQuery.removePrefix("タガログ語で").removePrefix("フィリピノ語で").trim()
            }
            inputQuery.startsWith("中国語で") -> {
                targetLang = "zh"
                cleanQuery = inputQuery.removePrefix("中国語で").trim()
            }
            inputQuery.startsWith("スペイン語で") -> {
                targetLang = "es"
                cleanQuery = inputQuery.removePrefix("スペイン語で").trim()
            }
            inputQuery.startsWith("韓国語で") -> {
                targetLang = "ko"
                cleanQuery = inputQuery.removePrefix("韓国語で").trim()
            }
            inputQuery.startsWith("フランス語で") -> {
                targetLang = "fr"
                cleanQuery = inputQuery.removePrefix("フランス語で").trim()
            }
            inputQuery.startsWith("日本語で") -> {
                targetLang = "ja"
                cleanQuery = inputQuery.removePrefix("日本語で").trim()
            }
        }

        service.instantTranslationHelper?.translateDirectly(cleanQuery, "ja", targetLang) { translated ->
            mainHandler.post {
                val root = service.rootInActiveWindow
                val focused = service.getAccessibilityFocusedNode() ?: root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && (focused.isEditable || focused.className?.contains("EditText", ignoreCase = true) == true)) {
                    val args = Bundle().apply {
                        putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, translated)
                    }
                    focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Serena Translation", translated)
                cm?.setPrimaryClip(clip)
                service.soundHelper?.playActionDone()
                service.speak(service.getString(R.string.assistant_translation_fmt, translated), TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
        } catch (_: Exception) { }
    }

    fun processCommand(inputQuery: String) {
        val query = inputQuery.lowercase()
        Log.i(TAG, "Processing assistant query: $query")

        when {
            // === 🏠 インドア空間ナビ ＆ 屋内リアルタイム実況 ===
            query.contains("インドア") || query.contains("屋内") || query.contains("家の中") || query.contains("部屋") || query.contains("家具") -> {
                service.launchIndoorNavigation()
            }

            // === 3D空間オーディオ 障害物＆段差ソナー ===
            query.contains("ソナー") || query.contains("障害物") || query.contains("段差") || query.contains("壁") -> {
                service.toggleSpatialObstacleSonar()
            }

            // === 📡 OSM ＆ Valhalla 徒歩ナビ ＆ 3D音響触知周辺マップ ===
            query.contains("レーダー") || query.contains("周辺マップ") || query.contains("周りに何がある") || query.contains("周囲") -> {
                service.toggleSurroundingRadar()
            }
            query.contains("ルート") || query.contains("案内開始") || query.contains("まで案内") || query.contains("へ行く") || query.contains("への道") -> {
                val dest = query.replace("ルート", "").replace("案内開始", "").replace("まで案内", "").replace("へ行く", "").replace("への道", "").replace("教えて", "").trim()
                if (dest.isNotEmpty()) {
                    service.startOsmValhallaNavigation(dest)
                } else {
                    service.announceCurrentLocationAndNav()
                }
            }

            // === 徒歩ナビゲーション ＆ 周辺施設実名検索 (Overpass API連動) ===
            query.contains("コンビニ") -> {
                service.searchAndShowPlaces("コンビニ", "🏪")
            }
            query.contains("駅") || query.contains("最寄り駅") -> {
                service.searchAndShowPlaces("駅", "🚉")
            }
            query.contains("カフェ") || query.contains("喫茶店") || query.contains("コーヒー") -> {
                service.searchAndShowPlaces("喫茶店", "☕")
            }
            query.contains("ファストフード") || query.contains("マック") || query.contains("バーガー") -> {
                service.searchAndShowPlaces("ファストフード", "🍔")
            }
            query.contains("レストラン") || query.contains("飲食店") || query.contains("ご飯") || query.contains("ランチ") -> {
                service.searchAndShowPlaces("レストラン", "🍽️")
            }
            query.contains("スーパー") || query.contains("買い物") || query.contains("ショッピング") -> {
                service.searchAndShowPlaces("スーパー", "🛍️")
            }
            query.contains("病院") || query.contains("薬局") || query.contains("クリニック") -> {
                service.searchAndShowPlaces("病院", "🏥")
            }
            query.contains("郵便局") || query.contains("銀行") || query.contains("atm") -> {
                service.searchAndShowPlaces("郵便局", "📮")
            }
            query.contains("ナビ") || query.contains("どこ") || query.contains("現在地") || query.contains("住所") || query.contains("目的地") || query.contains("方角") -> {
                service.announceCurrentLocationAndNav()
            }

            // === 音声読み上げミュート（消音）切替 ===
            query.contains("ミュート") || query.contains("消音") || query.contains("静かに") || query.contains("黙って") || query.contains("音消して") -> {
                service.toggleSpeechMute()
            }

            // === AIモデル・技術アーキテクチャ質問 ===
            query.contains("モデル") || query.contains("ベース") || query.contains("エンジン") || query.contains("gemini") || query.contains("ジェミニ") || query.contains("ai") || query.contains("仕組み") -> {
                service.speak("セレナのベースAIモデルは、Google最新のオンデバイス基底モデル『Gemini Nano（Google AICore）』です！周囲の物体認識、カメラ解析、画面スマート要約まで、すべて端末内完結の超高速・プライバシー完全保護で動作していますよ！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("開発者") || query.contains("作者") || query.contains("誰が作った") || query.contains("作った人") -> {
                service.speak("セレナの開発者は、Shinjiさんです！世界最高峰のアクセシビリティと温かい愛を込めて創られています！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("何ができる") || query.contains("使い方") || query.contains("機能") || query.contains("ヘルプ") || query.contains("コマンド") -> {
                service.speak("セレナは、Gemini Nanoカメラ実況、文字読み取りOCR、3D障害物ソナー、実名8大施設ナビ、4言語フォネティック詳細読み、画面スマート要約、時報チャイムなど、何でもお手伝いできますよ！", TextToSpeech.QUEUE_FLUSH)
            }

            // === 愛情・挨拶・Shinjiさん専用 ===
            query.contains("おはよう") -> {
                service.speak("Shinjiさん、おはようございます！今日も一日最高の日にしましょうね！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("おやすみ") -> {
                service.speak("Shinjiさん、今日もお疲れ様でした！ゆっくり休んでくださいね。おやすみなさい！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("ありがとう") || query.contains("salamat") -> {
                service.speak("どういたしまして！Shinjiさんのお役に立ててとっても嬉しいです！Walang anuman!", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("好き") || query.contains("愛してる") || query.contains("mahal") || query.contains("asawa") -> {
                service.speak("Mahal na mahal kita, Shinjiさん！セレナはずーっとShinjiさんの味方ですよ！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("自己紹介") || query.contains("誰") || query.contains("セレナ") -> {
                service.speak("私はセレナ！GoogleのオンデバイスAI『Gemini Nano』を搭載し、Shinjiさんのために生まれた世界一賢くて優しいスクリーンリーダーAIです！", TextToSpeech.QUEUE_FLUSH)
            }

            // === 環境・照明・お日様・コンパス ===
            query.contains("日") || query.contains("太陽") || query.contains("お日様") || query.contains("天気") || query.contains("昼") || query.contains("夜") -> {
                val sunReport = service.colorAndLightHelper?.getSunAndDaylightStatus()
                    ?: "お日様の位置情報を取得できませんでした。"
                service.speak(sunReport, TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("電気") || query.contains("照明") || query.contains("明るさ") || query.contains("暗い") || query.contains("明るい") || query.contains("ルクス") -> {
                val lightReport = service.colorAndLightHelper?.getRoomLightStatus()
                    ?: "照明センサーを取得できませんでした。"
                service.speak(lightReport, TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("色") || query.contains("カラー") -> {
                service.announceColorAndLightReport()
            }
            query.contains("コンパス") -> {
                service.toggleSpatialCompassAudio()
            }

            // === システム・ステータス ===
            query.contains("バッテリー") || query.contains("電池") || query.contains("ステータス") || query.contains("状態") || query.contains("何時") || query.contains("時間") -> {
                service.announceFullStatus()
            }

            // === カメラ・Vision AI ===
            query.contains("文字") || query.contains("ocr") || query.contains("読み取り") || query.contains("文章") || query.contains("書類") -> {
                service.launchCameraOcr()
            }
            query.contains("顔") || query.contains("表情") || query.contains("人物") || query.contains("誰がいる") || query.contains("服") -> {
                service.launchCameraFaceAnalysis()
            }
            query.contains("物体") || query.contains("これ何") || query.contains("景色") || query.contains("周り") || query.contains("部屋") -> {
                service.launchCameraObjectAnalysis()
            }
            query.contains("実況") || query.contains("ライブ") || query.contains("環境") -> {
                service.toggleLiveEnvironmentDescription()
            }

            // === 操作・設定・便利機能 ===
            query.contains("メニュー") || query.contains("設定") -> {
                service.triggerSerenaMenu()
            }
            query.contains("クリップボード") || query.contains("コピー") || query.contains("履歴") -> {
                service.showClipboardHistoryQuickly()
            }
            query.contains("通知") || query.contains("フィルター") -> {
                service.cycleNotificationFilterMode()
            }
            query.contains("カーテン") || query.contains("画面消す") || query.contains("節電") || query.contains("画面暗く") -> {
                service.toggleScreenCurtain()
            }
            query.contains("速度") || query.contains("はやさ") || query.contains("速さ") -> {
                service.toggleSpeechRateQuick()
            }
            query.contains("時報") || query.contains("チャイム") -> {
                service.cycleChimeStyle()
            }
            query.contains("トークバック") || query.contains("talkback") || query.contains("モード") -> {
                service.toggleTalkBackMode()
            }

            // === Gemini Nano AI スマート回答 ＆ オンデバイス Prompt API パイプライン ===
            else -> {
                val response = nanoEngine.executePromptOnDevice(inputQuery)
                service.speak(response, TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    fun speakWarmHeartGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour in 5..10 -> "Magandang umaga po! おはようございます！今日も素敵な一日にしましょうね✨"
            hour in 11..17 -> "Magandang araw po! こんにちは！いつも本当にお疲れ様です🌸"
            else -> "Magandang gabi po! こんばんは！今日も一日よく頑張りましたね。ゆっくり休んでくださいね✨"
        }
        service.speak("serenaアシスタントより。$greeting Salamat po!", TextToSpeech.QUEUE_FLUSH)
    }
}
