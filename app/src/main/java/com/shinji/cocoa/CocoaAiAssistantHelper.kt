package com.shinji.cocoa

import android.content.Context
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast

class CocoaAiAssistantHelper(private val service: CocoaScreenReaderService) {

    companion object {
        private const val TAG = "CocoaAiAssistant"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            service.speak("お使いの端末は音声認識に対応していません。", TextToSpeech.QUEUE_FLUSH)
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(service).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        service.speak("音声アシスタントです。お話しください。", TextToSpeech.QUEUE_FLUSH)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.e(TAG, "Speech recognition error code: $error")
                        service.speak("音声を聞き取れませんでした。もう一度お試しください。", TextToSpeech.QUEUE_FLUSH)
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
            service.speak("音声アシスタントの起動に失敗しました。", TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun processCommand(inputQuery: String) {
        val query = inputQuery.lowercase()
        Log.i(TAG, "Processing assistant query: $query")

        when {
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
            query.contains("シェイク") || query.contains("振り振り") -> {
                service.announceFullStatus()
            }
            query.contains("カーテン") || query.contains("画面消す") || query.contains("節電") -> {
                service.toggleScreenCurtain()
            }
            query.contains("メニュー") || query.contains("設定") -> {
                service.triggerCocoaMenu()
            }
            else -> {
                service.speak("音声コマンド「$inputQuery」を受け付けました。環境実況、ステータス、文字読み取り、通知フィルター、クリップボードに対応しています。", TextToSpeech.QUEUE_FLUSH)
            }
        }
    }
}
