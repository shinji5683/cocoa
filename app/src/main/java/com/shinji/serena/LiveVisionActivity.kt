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
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.shinji.serena.ai.FoodAndExpirationScannerHelper
import com.shinji.serena.navigation.WalkAndTransitVisionHelper
import com.shinji.serena.navigation.SpatialSonarEngine
import android.graphics.RectF
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * リアルタイムAIカメラ実況アクティビティ
 * CameraX + ML Kit (日本語OCR & 顔・服装・年代認識 & バーコード & 物体検出) + Gemini Nano を用いて、
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
            .enableTracking()
            .build()
    )
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
    )

    private var mode = "LIVE"
    private var lastSpokenText = ""
    private var lastSpokenTime = 0L
    private val soundHelper by lazy { SoundAndHapticHelper(this) }
    private val spatialSonarEngine by lazy { SpatialSonarEngine(this, soundHelper) }

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
            "OCR" -> getString(R.string.eyes_mode_ocr)
            "FACE" -> getString(R.string.eyes_mode_face)
            "INDOOR" -> getString(R.string.eyes_mode_indoor)
            "FOOD_EXPIRATION" -> getString(R.string.eyes_mode_food)
            "WALK_TRANSIT" -> getString(R.string.eyes_mode_walk)
            "BARCODE_DOC" -> getString(R.string.eyes_mode_barcode_doc)
            "FASHION" -> getString(R.string.eyes_mode_fashion)
            "SONAR" -> getString(R.string.eyes_mode_sonar)
            else -> getString(R.string.eyes_mode_live)
        }
        tvStatus.text = getString(R.string.eyes_starting_status_fmt, modeTitle)
        val startAnnounce = when (mode) {
            "INDOOR" -> getString(R.string.eyes_start_indoor)
            "FOOD_EXPIRATION" -> getString(R.string.eyes_start_food)
            "WALK_TRANSIT" -> getString(R.string.eyes_start_walk)
            "BARCODE_DOC" -> getString(R.string.eyes_start_barcode_doc)
            "FASHION" -> getString(R.string.eyes_start_fashion)
            "SONAR" -> getString(R.string.eyes_start_sonar)
            "OCR" -> getString(R.string.eyes_start_ocr)
            "FACE" -> getString(R.string.eyes_start_face)
            else -> getString(R.string.eyes_start_live)
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
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.05f)
            isTtsReady = true
        }
    }

    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (text.isEmpty()) return
        Log.i(TAG, "LiveVision speak: $text")

        // サービス稼働中はサービス側TTSで1本化、非稼働時のみActivityローカルTTSで発声（2重発声を完全防止）
        val service = SerenaScreenReaderService.instance
        if (service != null) {
            service.speak(text, queueMode)
        } else if (isTtsReady && tts != null) {
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
                speak(getString(R.string.eyes_permission_camera_required))
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
                speak(getString(R.string.eyes_camera_start_failed))
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
                avg >= 150 -> getString(R.string.eyes_brightness_bright)
                avg in 60..149 -> getString(R.string.eyes_brightness_cozy)
                avg in 20..59 -> getString(R.string.eyes_brightness_dim)
                else -> getString(R.string.eyes_brightness_dark)
            }
        } else {
            getString(R.string.eyes_brightness_bright)
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
                                tvStatus.text = "📝 $text"
                            }
                            speak(getString(R.string.eyes_text_detected_fmt, text), TextToSpeech.QUEUE_FLUSH)
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
                            val centerXRatio = face.boundingBox.centerX().toFloat() / imgWidth
                            val dirStr = when {
                                centerXRatio < 0.25f -> getString(R.string.dir_left)
                                centerXRatio in 0.25f..0.40f -> getString(R.string.dir_front_left)
                                centerXRatio in 0.40f..0.60f -> getString(R.string.dir_front)
                                centerXRatio in 0.60f..0.75f -> getString(R.string.dir_front_right)
                                else -> getString(R.string.dir_right)
                            }
                            val attrs = com.shinji.serena.ai.AiVisionFeatureHelper.analyzePersonAttributes(
                                face = face,
                                imageWidth = imgWidth,
                                imageHeight = imgHeight,
                                mediaImage = mediaImage,
                                bitmap = null
                            )
                            val desc = getString(R.string.eyes_person_desc_fmt, dirStr, attrs.estimatedDistanceMeters, attrs.clothingDescription, attrs.genderAndAge, attrs.emotion.fullDescription)
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
                                    getString(R.string.food_detected_with_expiry_fmt, foodResult.category, foodResult.estimatedItemName, foodResult.expiration.spokenMessage)
                                }
                                foodResult != null -> {
                                    getString(R.string.food_detected_fmt, foodResult.category, foodResult.estimatedItemName)
                                }
                                expirationResult != null -> {
                                    expirationResult.spokenMessage
                                }
                                else -> {
                                    // 一般OCR文字
                                    val topText = visionText.textBlocks.firstOrNull()?.text?.trim() ?: ""
                                    if (topText.isNotEmpty()) getString(R.string.eyes_text_detected_fmt, topText) else ""
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
                        imageHeight = imgHeight,
                        context = this
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
                                    getString(R.string.eyes_front_clear_checking, brightnessLevel)
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
            "FASHION" -> {
                try {
                    val bitmap = imageProxy.toBitmap()
                    val centerX = imgWidth / 2
                    val centerY = imgHeight / 2
                    val sampleRadius = (imgWidth * 0.15f).toInt().coerceAtLeast(10)

                    var totalR = 0L
                    var totalG = 0L
                    var totalB = 0L
                    var count = 0

                    val step = (sampleRadius / 4).coerceAtLeast(1)
                    for (y in (centerY - sampleRadius).coerceAtLeast(0) until (centerY + sampleRadius).coerceAtMost(imgHeight - 1) step step) {
                        for (x in (centerX - sampleRadius).coerceAtLeast(0) until (centerX + sampleRadius).coerceAtMost(imgWidth - 1) step step) {
                            val pixel = bitmap.getPixel(x, y)
                            totalR += android.graphics.Color.red(pixel)
                            totalG += android.graphics.Color.green(pixel)
                            totalB += android.graphics.Color.blue(pixel)
                            count++
                        }
                    }

                    if (count > 0) {
                        val avgR = (totalR / count).toInt()
                        val avgG = (totalG / count).toInt()
                        val avgB = (totalB / count).toInt()

                        val mood = com.shinji.serena.ai.FashionMoodHelper.describeColorFromRgb(avgR, avgG, avgB)
                        val announcement = "👗 ${mood.fullSpokenDescription}"

                        if (announcement != lastSpokenText || currentTime - lastSpokenTime > 3500) {
                            lastSpokenText = announcement
                            lastSpokenTime = currentTime
                            runOnUiThread {
                                tvStatus.text = announcement
                            }
                            speak(mood.fullSpokenDescription, TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fashion analysis error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }
            "SONAR" -> {
                objectDetector.process(image)
                    .addOnSuccessListener { detectedObjects ->
                        if (detectedObjects.isNotEmpty()) {
                            val primary = detectedObjects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (primary != null) {
                                val box = primary.boundingBox
                                val rectF = RectF(
                                    box.left.toFloat() / imgWidth.coerceAtLeast(1),
                                    box.top.toFloat() / imgHeight.coerceAtLeast(1),
                                    box.right.toFloat() / imgWidth.coerceAtLeast(1),
                                    box.bottom.toFloat() / imgHeight.coerceAtLeast(1)
                                )
                                val label = primary.labels.firstOrNull()?.text ?: "障害物"
                                val areaRatio = (rectF.width() * rectF.height()).coerceIn(0.01f, 1.0f)
                                val dist = ((1.0f - kotlin.math.sqrt(areaRatio)) * 3.0f + 0.3f).coerceIn(0.3f, 3.5f)

                                val sonarRes = spatialSonarEngine.processDetectedTarget(rectF, label, dist)
                                val announcement = sonarRes.guideMessage

                                if (announcement != lastSpokenText || currentTime - lastSpokenTime > 3000) {
                                    lastSpokenText = announcement
                                    lastSpokenTime = currentTime
                                    runOnUiThread {
                                        tvStatus.text = "🦇 $announcement"
                                    }
                                    speak(announcement, TextToSpeech.QUEUE_FLUSH)
                                }
                            }
                        } else {
                            if (currentTime - lastSpokenTime > 4000) {
                                lastSpokenTime = currentTime
                                val clearMsg = getString(R.string.eyes_front_clear)
                                runOnUiThread {
                                    tvStatus.text = "🦇 $clearMsg"
                                }
                                speak(clearMsg, TextToSpeech.QUEUE_FLUSH)
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            else -> {
                // LIVE リアルタイムAI環境実況モード (Gemini Nano + Face Detection + Object/Labeling + Japanese OCR)
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        val persons = faces.map { face ->
                            val box = face.boundingBox
                            val centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1)
                            val pos = when {
                                centerX < 0.25f -> getString(R.string.dir_left)
                                centerX in 0.25f..0.40f -> getString(R.string.dir_front_left)
                                centerX in 0.40f..0.60f -> getString(R.string.dir_front)
                                centerX in 0.60f..0.75f -> getString(R.string.dir_front_right)
                                else -> getString(R.string.dir_right)
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
                                gazeAndPose = attrs.emotion.gazeAndHeadPose,
                                isLookingAtCamera = attrs.emotion.gazeAndHeadPose.contains("こちらを見ています")
                            )
                        }

                        objectDetector.process(image)
                            .addOnSuccessListener { detectedObjects ->
                                val objectDetails = detectedObjects.mapNotNull { obj ->
                                    val box = obj.boundingBox
                                    val centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1)
                                    val pos = when {
                                        centerX < 0.25f -> getString(R.string.dir_left)
                                        centerX in 0.25f..0.40f -> getString(R.string.dir_front_left)
                                        centerX in 0.40f..0.60f -> getString(R.string.dir_front)
                                        centerX in 0.60f..0.75f -> getString(R.string.dir_front_right)
                                        else -> getString(R.string.dir_right)
                                    }
                                    val widthRatio = box.width().toFloat() / imgWidth.coerceAtLeast(1)
                                    val dist = when {
                                        widthRatio > 0.45f -> "すぐ近く（約40cm）"
                                        widthRatio > 0.25f -> "近く（約80cm）"
                                        widthRatio > 0.12f -> "約1.5m"
                                        else -> "約2.5m"
                                    }
                                    val labelText = obj.labels.firstOrNull()?.text ?: ""
                                    val name = translateObjectLabel(labelText)
                                    if (name.isNotEmpty()) {
                                        "${pos} ${dist}に${name}"
                                    } else null
                                }

                                textRecognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        val recognizedTexts = visionText.textBlocks.mapNotNull { it.text.trim().takeIf { t -> t.isNotEmpty() } }
                                        val sceneSummary = geminiNanoEngine.describeSceneComprehensive(
                                            lightingLevel = brightnessLevel,
                                            persons = persons,
                                            objects = objectDetails,
                                            texts = recognizedTexts
                                        )

                                        val finalAnnouncement = if (sceneSummary.isNotEmpty()) {
                                            sceneSummary
                                        } else if (persons.isNotEmpty()) {
                                            val p = persons[0]
                                            val clothesPart = if (p.clothingColor.isNotEmpty() && p.clothingColor != "服") "${p.clothingColor}を着た" else ""
                                            val vibePart = if (p.emotionalMeaning.endsWith("です") || p.emotionalMeaning.endsWith("ます")) p.emotionalMeaning else "${p.emotionalMeaning}です"
                                            "${p.position}（${p.distanceMeters}）に、${clothesPart}${p.genderAndAge}が1人います。${p.gazeAndPose}。表情は${p.expression}で、${vibePart}。"
                                        } else if (objectDetails.isNotEmpty()) {
                                            getString(R.string.eyes_surrounding_objects_fmt, objectDetails.take(2).joinToString(getString(R.string.status_separator)))
                                        } else if (recognizedTexts.isNotEmpty()) {
                                            getString(R.string.eyes_text_detected_fmt, recognizedTexts.take(2).joinToString(getString(R.string.status_separator)))
                                        } else {
                                            getString(R.string.eyes_front_clear_checking, brightnessLevel)
                                        }

                                        if (finalAnnouncement.isNotEmpty() && (finalAnnouncement != lastSpokenText || currentTime - lastSpokenTime > 3500)) {
                                            lastSpokenText = finalAnnouncement
                                            lastSpokenTime = currentTime
                                            runOnUiThread {
                                                tvStatus.text = "🌐 リアルタイム実況: $finalAnnouncement"
                                            }
                                            speak(finalAnnouncement, TextToSpeech.QUEUE_FLUSH)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                            .addOnFailureListener {
                                imageProxy.close()
                            }
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
            }
        }
    }

    private fun translateObjectLabel(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.contains("food") -> getString(R.string.eyes_obj_food)
            lower.contains("beverage") || lower.contains("drink") || lower.contains("bottle") -> getString(R.string.eyes_obj_drink)
            lower.contains("cup") || lower.contains("mug") -> getString(R.string.eyes_obj_cup)
            lower.contains("home good") || lower.contains("furniture") -> getString(R.string.eyes_obj_furniture)
            lower.contains("chair") || lower.contains("seat") -> getString(R.string.eyes_obj_chair)
            lower.contains("table") || lower.contains("desk") -> getString(R.string.eyes_obj_table)
            lower.contains("couch") || lower.contains("sofa") -> getString(R.string.eyes_obj_sofa)
            lower.contains("door") -> getString(R.string.eyes_obj_door)
            lower.contains("plant") || lower.contains("flower") -> getString(R.string.eyes_obj_plant)
            lower.contains("electronic") || lower.contains("gadget") -> getString(R.string.eyes_obj_electronics)
            lower.contains("laptop") || lower.contains("computer") -> getString(R.string.eyes_obj_laptop)
            lower.contains("phone") || lower.contains("mobile") -> getString(R.string.eyes_obj_phone)
            lower.contains("book") || lower.contains("magazine") -> getString(R.string.eyes_obj_book)
            lower.contains("bag") || lower.contains("backpack") -> getString(R.string.eyes_obj_bag)
            lower.contains("shoe") || lower.contains("footwear") -> getString(R.string.eyes_obj_shoe)
            lower.contains("clock") || lower.contains("watch") -> getString(R.string.eyes_obj_clock)
            lower.contains("glasses") -> getString(R.string.eyes_obj_glasses)
            lower.contains("key") -> getString(R.string.eyes_obj_key)
            lower.contains("pen") || lower.contains("pencil") -> getString(R.string.eyes_obj_pen)
            lower.isNotEmpty() -> label
            else -> getString(R.string.eyes_obj_item)
        }
    }

    override fun onDestroy() {
        try {
            val endMsg = when (mode) {
                "OCR" -> getString(R.string.eyes_end_ocr)
                "FACE" -> getString(R.string.eyes_end_face)
                "INDOOR" -> getString(R.string.eyes_end_indoor)
                "FOOD_EXPIRATION" -> getString(R.string.eyes_end_food)
                "WALK_TRANSIT" -> getString(R.string.eyes_end_walk)
                "BARCODE_DOC" -> getString(R.string.eyes_end_barcode_doc)
                else -> getString(R.string.eyes_end_live)
            }
            SerenaScreenReaderService.instance?.speak("🌸 $endMsg", TextToSpeech.QUEUE_FLUSH)
        } catch (_: Exception) {}

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
        objectDetector.close()
    }
}
