package com.shinji.serena

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Calendar

class SerenaAiAssistantHelper(private val service: SerenaScreenReaderService) {

    companion object {
        private const val TAG = "serenaAiAssistant"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            service.speak("serenaアシスタントです。お使いの端末は音声入力未対応のためダイアログを起動します。", TextToSpeech.QUEUE_FLUSH)
            service.showSerenaAssistantDialog()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(service).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        service.speak("serenaアシスタントです。画面の要約や操作コマンドをお話しください。", TextToSpeech.QUEUE_FLUSH)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.e(TAG, "Speech recognition error code: $error")
                        service.speak("音声を聞き取れませんでした。serenaアシスタントメニューを開きます。", TextToSpeech.QUEUE_FLUSH)
                        service.showSerenaAssistantDialog()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val query = matches?.firstOrNull() ?: ""
                        if (query.isNotEmpty()) {
                            processCommand(query)
                        } else {
                            service.speak("音声コマンドを認識できませんでした。", TextToSpeech.QUEUE_FLUSH)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
            service.speak("serenaアシスタントダイアログを開きます。", TextToSpeech.QUEUE_FLUSH)
            service.showSerenaAssistantDialog()
        }
    }

    fun processCommand(inputQuery: String) {
        val query = inputQuery.lowercase()
        Log.i(TAG, "Processing assistant query: $query")

        when {
            query.contains("要約") || query.contains("画面") || query.contains("全体のまとめ") || query.contains("概要") -> {
                service.summarizeCurrentScreen()
            }
            query.contains("おはよう") || query.contains("お疲れ") || query.contains("ありがとう") ||
            query.contains("励まし") || query.contains("好き") || query.contains("愛") ||
            query.contains("asawa") || query.contains("mahal") || query.contains("salamat") -> {
                speakWarmHeartGreeting()
            }
            query.contains("バッテリー") || query.contains("電池") || query.contains("ステータス") || query.contains("状態") -> {
                service.announceFullStatus()
            }
            query.contains("文字") || query.contains("ocr") || query.contains("読み取り") || query.contains("文章") -> {
                service.launchCameraOcr()
            }
            query.contains("顔") || query.contains("表情") || query.contains("人物") -> {
                service.launchCameraFaceAnalysis()
            }
            query.contains("物体") || query.contains("これ何") || query.contains("景色") -> {
                service.launchCameraObjectAnalysis()
            }
            query.contains("トークバック") || query.contains("talkback") || query.contains("モード") -> {
                service.toggleTalkBackMode()
            }
            query.contains("時報") || query.contains("チャイム") || query.contains("時計") -> {
                service.cycleChimeStyle()
            }
            query.contains("速度") || query.contains("はやさ") || query.contains("速さ") -> {
                service.toggleSpeechRateQuick()
            }
            query.contains("実況") || query.contains("ライブ") || query.contains("環境") -> {
                service.toggleLiveEnvironmentDescription()
            }
            query.contains("クリップボード") || query.contains("コピー") || query.contains("履歴") -> {
                service.showClipboardHistoryQuickly()
            }
            query.contains("通知") || query.contains("フィルター") -> {
                service.cycleNotificationFilterMode()
            }
            query.contains("カーテン") || query.contains("画面消す") || query.contains("節電") -> {
                service.toggleScreenCurtain()
            }
            query.contains("メニュー") || query.contains("設定") -> {
                service.triggerSerenaMenu()
            }
            else -> {
                service.speak("serenaアシスタントです。画面要約、環境実況、ステータス、文字読み取りが利用可能です。", TextToSpeech.QUEUE_FLUSH)
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


