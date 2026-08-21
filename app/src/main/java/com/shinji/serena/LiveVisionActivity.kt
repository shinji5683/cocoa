package com.shinji.serena

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * リアルタイムAIカメラ実況アクティビティ
 * CameraX + TensorFlow Lite & ML Kit (日本語OCR & 顔・服装・年代認識 & TFLite物体検知) を用いて、
 * カメラに映った世界をリアルタイムに音声実況します。
 */
class LiveVisionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "LiveVisionActivity"
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnClose: Button
    private lateinit var cameraExecutor: ExecutorService
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val textRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private var mode = "LIVE"
    private var lastSpokenText = ""
    private var lastSpokenTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_vision)

        mode = intent.getStringExtra("MODE") ?: "LIVE"
        viewFinder = findViewById(R.id.viewFinder)
        tvStatus = findViewById(R.id.tvStatus)
        btnClose = findViewById(R.id.btnClose)

        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)

        btnClose.setOnClickListener {
            finish()
        }

        val modeTitle = when (mode) {
            "OCR" -> "文字読み取りカメラ"
            "FACE" -> "表情・人物認識カメラ"
            "INDOOR" -> "🏠 インドア空間ナビ ＆ 屋内実況"
            else -> "リアルタイムAI環境実況"
        }
        tvStatus.text = "🌸 $modeTitle 起動中…"
        val startAnnounce = if (mode == "INDOOR") {
            "インドア空間ナビを起動しました。部屋の家具や扉、周囲の人を正面や左右の方向で実況します。"
        } else {
            "リアルタイムカメラ実況を起動しました。周囲をゆっくり映してください。"
        }
        speak(startAnnounce)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.JAPANESE
            tts?.setSpeechRate(1.1f)
            isTtsReady = true
            val initialPrompt = if (mode == "INDOOR") {
                "インドア空間ナビを起動しました。部屋の家具や扉、周囲の人を正面や左右の方向で実況します。"
            } else {
                "リアルタイムカメラ実況を起動しました。周囲をゆっくり映してください。"
            }
            speak(initialPrompt)
        }
    }

    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (text.isEmpty()) return
        Log.i(TAG, "LiveVision speak: $text")

        // 1. AccessibilityService TTS
        SerenaScreenReaderService.instance?.speak(text, queueMode)

        // 2. Activity 専用直接 TTS フォールバック
        if (isTtsReady && tts != null) {
            tts?.speak(text, queueMode, null, "live_vision_${System.currentTimeMillis()}")
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                speak("カメラの権限が必要です。")
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "CameraX binding failed: ${e.message}")
                speak("カメラの起動に失敗しました。")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private val geminiNanoEngine by lazy { com.shinji.serena.ai.GeminiNanoEngine(this) }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        // 頻繁すぎる読み上げ防止 (最低1.8秒間隔)
        if (currentTime - lastSpokenTime < 1800) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val imgWidth = image.width
        val imgHeight = image.height

        // 明るさの計算
        val planes = mediaImage.planes
        val brightnessLevel = if (planes.isNotEmpty()) {
            val buffer = planes[0].buffer
            var sum = 0L
            val step = (buffer.remaining() / 200).coerceAtLeast(1)
            var count = 0
            for (i in 0 until buffer.remaining() step step) {
                sum += (buffer.get(i).toInt() and 0xFF)
                count++
            }
            val avg = if (count > 0) sum / count else 128
            when {
                avg >= 150 -> "明るい場所"
                avg in 60..149 -> "落ち着いた明るさ"
                avg in 20..59 -> "薄暗い場所"
                else -> "暗い場所"
            }
        } else {
            "明るい場所"
        }

        when (mode) {
            "OCR" -> {
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim().replace("\n", " ")
                        if (text.isNotEmpty() && text != lastSpokenText) {
                            lastSpokenText = text
                            lastSpokenTime = currentTime
                            runOnUiThread {
                                tvStatus.text = "📝 読み取り: $text"
                            }
                            speak("文字を検出: $text", TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            "FACE" -> {
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val attrs = com.shinji.serena.ai.AiVisionFeatureHelper.analyzePersonAttributes(
                                face = face,
                                imageWidth = imgWidth,
                                imageHeight = imgHeight,
                                bitmap = null
                            )
                            val smileProb = face.smilingProbability ?: 0f
                            val expr = if (smileProb > 0.4f) "笑顔" else "真剣な表情"
                            val desc = "正面に ${attrs.clothingDescription}を着た${attrs.genderAndAge}がいます。${expr}で${attrs.estimatedDistanceMeters}です"
                            if (desc != lastSpokenText) {
                                lastSpokenText = desc
                                lastSpokenTime = currentTime
                                runOnUiThread {
                                    tvStatus.text = "👤 $desc"
                                }
                                speak(desc, TextToSpeech.QUEUE_FLUSH)
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            "INDOOR" -> {
                val indoorHelper = IndoorNavigationHelper(this)

                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        val peopleList = faces.map { face ->
                            val box = face.boundingBox
                            val (dir, dist) = indoorHelper.calculateDirectionAndDistance(
                                centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1),
                                centerY = box.centerY().toFloat() / imgHeight.coerceAtLeast(1),
                                boxWidth = box.width().toFloat() / imgWidth.coerceAtLeast(1),
                                boxHeight = box.height().toFloat() / imgHeight.coerceAtLeast(1)
                            )
                            val attrs = com.shinji.serena.ai.AiVisionFeatureHelper.analyzePersonAttributes(
                                face = face,
                                imageWidth = imgWidth,
                                imageHeight = imgHeight,
                                bitmap = null
                            )
                            val smileProb = face.smilingProbability ?: 0f
                            val isLooking = ((face.leftEyeOpenProbability ?: 0.5f) + (face.rightEyeOpenProbability ?: 0.5f)) / 2f > 0.4f
                            val expr = if (smileProb > 0.5f) "満面の笑顔" else if (smileProb > 0.25f) "微笑み" else "落ち着いた表情"
                            val cloth = if (attrs.clothingDescription.isNotEmpty() && !attrs.clothingDescription.contains("不明")) attrs.clothingDescription else "服"

                            IndoorNavigationHelper.PersonState(
                                direction = dir,
                                distanceMeter = dist,
                                genderAndAge = attrs.genderAndAge,
                                clothingColor = cloth,
                                expression = expr,
                                isLookingAtCamera = isLooking,
                                poseDescription = "人"
                            )
                        }

                        objectDetector.process(image)
                            .addOnSuccessListener { detectedObjects ->
                                val indoorObjects = detectedObjects.mapNotNull { obj ->
                                    val primaryLabel = obj.labels.firstOrNull()?.text ?: "家具・設備"
                                    val translated = indoorHelper.translateIndoorLabel(primaryLabel)
                                    val box = obj.boundingBox
                                    val (dir, dist) = indoorHelper.calculateDirectionAndDistance(
                                        centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1),
                                        centerY = box.centerY().toFloat() / imgHeight.coerceAtLeast(1),
                                        boxWidth = box.width().toFloat() / imgWidth.coerceAtLeast(1),
                                        boxHeight = box.height().toFloat() / imgHeight.coerceAtLeast(1)
                                    )
                                    IndoorNavigationHelper.IndoorObject(
                                        name = translated,
                                        direction = dir,
                                        distanceMeter = dist
                                    )
                                }

                                val announcement = if (peopleList.isEmpty() && indoorObjects.isEmpty()) {
                                    "${brightnessLevel}。前方クリアです。周囲を確認中…"
                                } else {
                                    indoorHelper.buildIndoorAnnouncement(
                                        roomName = "",
                                        objects = indoorObjects,
                                        people = peopleList,
                                        isPathClear = true
                                    )
                                }

                                if (announcement.isNotEmpty() && (announcement != lastSpokenText || currentTime - lastSpokenTime > 4500)) {
                                    lastSpokenText = announcement
                                    lastSpokenTime = currentTime
                                    runOnUiThread {
                                        tvStatus.text = "🏠 インドア実況: $announcement"
                                    }
                                    speak(announcement, TextToSpeech.QUEUE_FLUSH)
                                }
                            }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            else -> {
                // LIVE 実況モード (Gemini Nano 統合解析: 照度 + 表情・服装・年代 + TFLite物体 + OCR)
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        val persons = faces.map { face ->
                            val box = face.boundingBox
                            val centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1)
                            val pos = when {
                                centerX < 0.35f -> "左側"
                                centerX > 0.65f -> "右側"
                                else -> "正面"
                            }
                            val attrs = com.shinji.serena.ai.AiVisionFeatureHelper.analyzePersonAttributes(
                                face = face,
                                imageWidth = imgWidth,
                                imageHeight = imgHeight,
                                bitmap = null
                            )
                            val smileProb = face.smilingProbability ?: 0f
                            val expr = if (smileProb > 0.5f) "満面の笑顔" else if (smileProb > 0.25f) "穏やかな微笑み" else "自然な表情"

                            com.shinji.serena.ai.GeminiNanoEngine.PersonAnalysisDetail(
                                position = pos,
                                distanceMeters = attrs.estimatedDistanceMeters,
                                genderAndAge = attrs.genderAndAge,
                                clothingColor = attrs.clothingDescription,
                                pantsColor = "ボトムス",
                                expression = expr,
                                emotionalMeaning = "安心している様子",
                                isLookingAtCamera = true
                            )
                        }

                        objectDetector.process(image)
                            .addOnSuccessListener { detectedObjs ->
                                val objectLabels = detectedObjs.mapNotNull { it.labels.firstOrNull()?.text }
                                textRecognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        val recognizedTexts = visionText.textBlocks.mapNotNull { it.text.trim().takeIf { t -> t.isNotEmpty() } }
                                        val sceneSummary = geminiNanoEngine.describeSceneComprehensive(
                                            lightingLevel = brightnessLevel,
                                            persons = persons,
                                            objects = objectLabels,
                                            texts = recognizedTexts
                                        )

                                        val finalAnnouncement = if (sceneSummary.isNotEmpty()) {
                                            sceneSummary
                                        } else {
                                            "${brightnessLevel}。周囲を確認中…"
                                        }

                                        if (finalAnnouncement.isNotEmpty() && (finalAnnouncement != lastSpokenText || currentTime - lastSpokenTime > 4500)) {
                                            lastSpokenText = finalAnnouncement
                                            lastSpokenTime = currentTime
                                            runOnUiThread {
                                                tvStatus.text = "🌐 実況: $finalAnnouncement"
                                            }
                                            speak(finalAnnouncement, TextToSpeech.QUEUE_FLUSH)
                                        }
                                    }
                            }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
        cameraExecutor.shutdown()
        textRecognizer.close()
        faceDetector.close()
        objectDetector.close()
    }
}
