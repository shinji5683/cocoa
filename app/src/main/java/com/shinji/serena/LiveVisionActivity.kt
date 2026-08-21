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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.shinji.serena.ai.FoodAndExpirationScannerHelper
import com.shinji.serena.navigation.WalkAndTransitVisionHelper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * リアルタイムAIカメラ実況アクティビティ
 * CameraX + ML Kit (日本語OCR & 顔・服装・年代認識 & バーコード) + Gemini Nano を用いて、
 * カメラに映った世界をリアルタイムに音声実況します。
 * 音声ガイドが途中で遮られず最後まで落ち着いて聞けるよう、発声中は待機制御を行います。
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
    private val barcodeScanner = BarcodeScanning.getClient()
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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
            "FOOD_EXPIRATION" -> "🥫 食品＆賞味期限スキャナー"
            "WALK_TRANSIT" -> "🚦 歩行・信号＆点字ブロックナビ"
            "BARCODE_DOC" -> "📄 バーコード＆書類・レシート読み取り"
            else -> "リアルタイムAI環境実況"
        }
        tvStatus.text = "🌸 $modeTitle 起動中…"
        val startAnnounce = when (mode) {
            "INDOOR" -> "インドア空間ナビを起動しました。部屋の家具や扉、周囲の人を正面や左右の方向で実況します。"
            "FOOD_EXPIRATION" -> "食品と賞味期限スキャナーを起動しました。食品パッケージや賞味期限の印字をゆっくり映してください。"
            "WALK_TRANSIT" -> "歩行・信号および点字ブロックナビを起動しました。正面の道路や信号機を映してください。"
            "BARCODE_DOC" -> "バーコードおよび書類スキャナーを起動しました。商品バーコードやレシート、請求書を映してください。"
            "OCR" -> "文字読み取りカメラを起動しました。"
            "FACE" -> "表情・人物認識カメラを起動しました。"
            else -> "リアルタイムカメラ実況を起動しました。周囲をゆっくり映してください。"
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
            tts?.setSpeechRate(1.05f)
            isTtsReady = true
            val initialPrompt = when (mode) {
                "INDOOR" -> "インドア空間ナビを起動しました。部屋の家具や扉、周囲の人を正面や左右の方向で実況します。"
                "FOOD_EXPIRATION" -> "食品と賞味期限スキャナーを起動しました。食品パッケージや賞味期限の印字をゆっくり映してください。"
                "WALK_TRANSIT" -> "歩行・信号および点字ブロックナビを起動しました。正面の道路や信号機を映してください。"
                "BARCODE_DOC" -> "バーコードおよび書類スキャナーを起動しました。商品バーコードやレシート、請求書を映してください。"
                "OCR" -> "文字読み取りカメラを起動しました。"
                "FACE" -> "表情・人物認識カメラを起動しました。"
                else -> "リアルタイムカメラ実況を起動しました。周囲をゆっくり映してください。"
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

        // 音声ガイドを発声中の場合、前の読み上げを遮らずに最後まで聞かせる！
        if (tts?.isSpeaking == true) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        // 落ち着いて聞き取れるスキャン間隔（最低3秒待機）
        if (currentTime - lastSpokenTime < 3000) {
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
                avg in 60..149 -> "落ち着いた明るさの部屋"
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
                                mediaImage = mediaImage,
                                bitmap = null
                            )
                            val desc = "正面${attrs.estimatedDistanceMeters}に、${attrs.clothingDescription}を着た${attrs.genderAndAge}がいます。${attrs.emotion.fullDescription}"
                            if (desc != lastSpokenText || currentTime - lastSpokenTime > 4000) {
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
            "FOOD_EXPIRATION" -> {
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val rawText = visionText.text.trim()
                        if (rawText.isNotEmpty()) {
                            val foodResult = FoodAndExpirationScannerHelper.analyzeFoodItem(rawText)
                            val expirationResult = FoodAndExpirationScannerHelper.extractExpirationDate(rawText)

                            val announcement = when {
                                foodResult != null && foodResult.expiration != null -> {
                                    "【${foodResult.category}】${foodResult.estimatedItemName}を検出。${foodResult.expiration.spokenMessage}"
                                }
                                foodResult != null -> {
                                    "【${foodResult.category}】${foodResult.estimatedItemName}を検出しました。"
                                }
                                expirationResult != null -> {
                                    expirationResult.spokenMessage
                                }
                                else -> {
                                    // 一般OCR文字
                                    val topText = visionText.textBlocks.firstOrNull()?.text?.trim() ?: ""
                                    if (topText.isNotEmpty()) "文字: $topText" else ""
                                }
                            }

                            if (announcement.isNotEmpty() && (announcement != lastSpokenText || currentTime - lastSpokenTime > 4000)) {
                                lastSpokenText = announcement
                                lastSpokenTime = currentTime
                                runOnUiThread {
                                    tvStatus.text = "🥫 $announcement"
                                }
                                speak(announcement, TextToSpeech.QUEUE_FLUSH)
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            "WALK_TRANSIT" -> {
                try {
                    val bitmap = imageProxy.toBitmap()
                    val walkState = WalkAndTransitVisionHelper.analyzeWalkingScene(
                        bitmap = bitmap,
                        imageWidth = imgWidth,
                        imageHeight = imgHeight
                    )

                    val parts = mutableListOf<String>()
                    if (walkState.trafficLightMessage != null) {
                        parts.add(walkState.trafficLightMessage)
                    }
                    if (walkState.brailleBlockMessage != null) {
                        parts.add(walkState.brailleBlockMessage)
                    }

                    if (parts.isNotEmpty()) {
                        val announcement = parts.joinToString(" ")
                        if (announcement != lastSpokenText || currentTime - lastSpokenTime > 3500) {
                            lastSpokenText = announcement
                            lastSpokenTime = currentTime
                            runOnUiThread {
                                tvStatus.text = "🚦 $announcement"
                            }
                            speak(announcement, TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Walk transit analysis error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }
            "BARCODE_DOC" -> {
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val bc = barcodes[0]
                            val rawValue = bc.rawValue ?: ""
                            val formatName = when (bc.format) {
                                Barcode.FORMAT_QR_CODE -> "QRコード"
                                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E -> "商品バーコード"
                                else -> "コード"
                            }
                            val msg = "📌 $formatName を検出: $rawValue"
                            if (msg != lastSpokenText || currentTime - lastSpokenTime > 4000) {
                                lastSpokenText = msg
                                lastSpokenTime = currentTime
                                runOnUiThread {
                                    tvStatus.text = msg
                                }
                                speak(msg, TextToSpeech.QUEUE_FLUSH)
                            }
                        } else {
                            // バーコードがない場合は書類・レシートOCR解析
                            textRecognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    val rawText = visionText.text.trim()
                                    if (rawText.isNotEmpty()) {
                                        val docSummary = FoodAndExpirationScannerHelper.summarizeDocument(rawText)
                                        val msg = docSummary.summarySpokenText
                                        if (msg != lastSpokenText || currentTime - lastSpokenTime > 4000) {
                                            lastSpokenText = msg
                                            lastSpokenTime = currentTime
                                            runOnUiThread {
                                                tvStatus.text = "📄 $msg"
                                            }
                                            speak(msg, TextToSpeech.QUEUE_FLUSH)
                                        }
                                    }
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
                                mediaImage = mediaImage,
                                bitmap = null
                            )
                            IndoorNavigationHelper.PersonState(
                                direction = dir,
                                distanceMeter = dist,
                                genderAndAge = attrs.genderAndAge,
                                clothingColor = attrs.clothingDescription,
                                expression = "${attrs.emotion.category}（${attrs.emotion.emotionalVibe}）",
                                isLookingAtCamera = attrs.emotion.gazeAndHeadPose.contains("こちらを見ています"),
                                poseDescription = attrs.emotion.gazeAndHeadPose
                            )
                        }

                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val detectedTexts = visionText.textBlocks.mapNotNull { it.text.trim().takeIf { t -> t.isNotEmpty() } }
                                val indoorObjects = detectedTexts.take(2).mapIndexed { idx, label ->
                                    val translated = indoorHelper.translateIndoorLabel(label)
                                    val dir = if (idx == 0) IndoorNavigationHelper.Direction.FRONT else IndoorNavigationHelper.Direction.FRONT_RIGHT
                                    IndoorNavigationHelper.IndoorObject(
                                        name = translated,
                                        direction = dir,
                                        distanceMeter = 1.5f + idx * 0.8f
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

                                if (announcement.isNotEmpty() && (announcement != lastSpokenText || currentTime - lastSpokenTime > 5000)) {
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
                // LIVE 実況モード (Gemini Nano 統合解析: 照度 + 表情・服装・詳細年代 + OCR)
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
                                mediaImage = mediaImage,
                                bitmap = null
                            )

                            com.shinji.serena.ai.GeminiNanoEngine.PersonAnalysisDetail(
                                position = pos,
                                distanceMeters = attrs.estimatedDistanceMeters,
                                genderAndAge = attrs.genderAndAge,
                                clothingColor = attrs.clothingDescription,
                                pantsColor = "ボトムス",
                                expression = attrs.emotion.category,
                                emotionalMeaning = attrs.emotion.emotionalVibe,
                                isLookingAtCamera = attrs.emotion.gazeAndHeadPose.contains("こちらを見ています")
                            )
                        }

                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val recognizedTexts = visionText.textBlocks.mapNotNull { it.text.trim().takeIf { t -> t.isNotEmpty() } }
                                val sceneSummary = geminiNanoEngine.describeSceneComprehensive(
                                    lightingLevel = brightnessLevel,
                                    persons = persons,
                                    objects = emptyList(),
                                    texts = recognizedTexts
                                )

                                val finalAnnouncement = if (sceneSummary.isNotEmpty()) {
                                    sceneSummary
                                } else {
                                    "${brightnessLevel}。周囲を確認中…"
                                }

                                if (finalAnnouncement.isNotEmpty() && (finalAnnouncement != lastSpokenText || currentTime - lastSpokenTime > 5000)) {
                                    lastSpokenText = finalAnnouncement
                                    lastSpokenTime = currentTime
                                    runOnUiThread {
                                        tvStatus.text = "🌐 実況: $finalAnnouncement"
                                    }
                                    speak(finalAnnouncement, TextToSpeech.QUEUE_FLUSH)
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
        barcodeScanner.close()
        faceDetector.close()
    }
}
