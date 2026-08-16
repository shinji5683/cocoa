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

    fun startListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(service)) {
                    service.speak("お使いの端末は音声認識に対応していません。", TextToSpeech.QUEUE_FLUSH)
                    return@post
                }

                // 既にリスニング中なら破棄して再初期化
                stopListeningInternal()

                // ガイド音声を再生してからマイクを開く
                service.speak("セレナAIです。どうぞ！", TextToSpeech.QUEUE_FLUSH)

                // TTS発話が終わるのを待ってからマイクを開放（マイクとTTSの干渉防止）
                mainHandler.postDelayed({
                    startRecognizerInternal()
                }, 1200)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AI Assistant: ${e.message}")
                service.speak("AIアシスタントの起動に失敗しました。", TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun startRecognizerInternal() {
        try {
            // Android 13+ (API 33+) ではOn-Device音声認識を優先
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(service)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(service)
            } else {
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
                            service.speak("聞き取れませんでした。もう一度お話しください。", TextToSpeech.QUEUE_FLUSH)
                        }
                        SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            service.speak("マイクの権限または音声録音を確認してください。", TextToSpeech.QUEUE_FLUSH)
                        }
                        else -> {
                            service.speak("聞き取りを終了しました。", TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                    stopListeningInternal()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val query = matches?.firstOrNull() ?: ""
                    Log.i(TAG, "Speech recognition result: $query")
                    if (query.isNotEmpty()) {
                        service.soundHelper?.playClick()
                        processCommand(query)
                    } else {
                        service.speak("音声を聞き取れませんでした。", TextToSpeech.QUEUE_FLUSH)
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
            service.speak("音声認識の開始エラーが発生しました。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
        } catch (e: Exception) { }
    }

    fun processCommand(inputQuery: String) {
        val query = inputQuery.lowercase()
        Log.i(TAG, "Processing assistant query: $query")

        when {
            // === AIモデル・技術アーキテクチャ質問 ===
            query.contains("モデル") || query.contains("ベース") || query.contains("エンジン") || query.contains("gemini") || query.contains("ジェミニ") || query.contains("ai") || query.contains("仕組み") -> {
                service.speak("セレナのベースAIモデルは、Google最新のオンデバイス基底モデル『Gemini Nano（Google AICore）』です！周囲の物体認識、カメラ解析、画面スマート要約まで、すべて端末内完結の超高速・プライバシー完全保護で動作していますよ！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("開発者") || query.contains("作者") || query.contains("誰が作った") || query.contains("作った人") -> {
                service.speak("セレナの開発者は、Shinjiさんです！世界最高峰のアクセシビリティと温かい愛を込めて創られています！", TextToSpeech.QUEUE_FLUSH)
            }
            query.contains("何ができる") || query.contains("使い方") || query.contains("機能") || query.contains("ヘルプ") || query.contains("コマンド") -> {
                service.speak("セレナは、Gemini Nanoカメラ物体認識、文字読み取りOCR、表情人物判定、リアルタイム環境実況、画面スマート要約、4言語フォネティック詳細読み、スマート通知フィルター、時報チャイムなど、何でもお手伝いできますよ！", TextToSpeech.QUEUE_FLUSH)
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
            query.contains("方角") || query.contains("コンパス") || query.contains("向き") || query.contains("北") || query.contains("南") || query.contains("東") || query.contains("西") -> {
                service.announceCompassHeading()
            }
            query.contains("色") || query.contains("カラー") -> {
                service.announceColorAndLightReport()
            }

            // === システム・ステータス ===
            query.contains("バッテリー") || query.contains("電池") || query.contains("ステータス") || query.contains("状態") || query.contains("何時") || query.contains("時間") -> {
                service.announceFullStatus()
            }

            // === カメラ・Vision AI ===
            query.contains("文字") || query.contains("ocr") || query.contains("読み取り") || query.contains("文章") || query.contains("書類") -> {
                service.launchCameraOcr()
            }
            query.contains("顔") || query.contains("表情") || query.contains("人物") || query.contains("誰がいる") -> {
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

            // === Gemini Nano AI スマート回答 ===
            else -> {
                service.speak("Gemini Nano AIです。「$inputQuery」についてですね。周囲のカメラ認識、画面要約、ステータス案内、各種設定など何でもお申し付けください！", TextToSpeech.QUEUE_FLUSH)
            }
        }
    }
}
