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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * リアルタイムAIカメラ実況アクティビティ
 * CameraX + ML Kit (日本語OCR & 顔・表情認識) を用いて、
 * カメラに映った世界をリアルタイムに音声実況します。
 */
class LiveVisionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveVisionActivity"
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnClose: Button
    private lateinit var cameraExecutor: ExecutorService

    private val textRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var mode = "LIVE"
    private var lastSpokenText = ""
    private var lastSpokenTime = 0L
    private var lastFaceDescription = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_vision)

        mode = intent.getStringExtra("MODE") ?: "LIVE"
        viewFinder = findViewById(R.id.viewFinder)
        tvStatus = findViewById(R.id.tvStatus)
        btnClose = findViewById(R.id.btnClose)

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnClose.setOnClickListener {
            finish()
        }

        val modeTitle = when (mode) {
            "OCR" -> "文字読み取りカメラ"
            "FACE" -> "表情・人物認識カメラ"
            else -> "リアルタイムAI環境実況"
        }
        tvStatus.text = "🌸 $modeTitle 起動中…"
        speak("カメラが起動しました。周囲をゆっくり映してください。")

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

    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        SerenaScreenReaderService.instance?.speak(text, queueMode)
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
        // 頻繁すぎる読み上げ防止 (最低2秒間隔)
        if (currentTime - lastSpokenTime < 2000) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

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
                            val imgWidth = image.width
                            val imgHeight = image.height
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
                                val leftEye = face.leftEyeOpenProbability ?: 0.5f
                                val rightEye = face.rightEyeOpenProbability ?: 0.5f
                                val avgEye = (leftEye + rightEye) / 2f
                                val isLooking = avgEye > 0.4f

                                val (expressionDesc, meaningDesc) = when {
                                    smileProb > 0.65f -> Pair("満面の明るい笑顔", "とても楽しそうに喜んでいる様子")
                                    smileProb in 0.30f..0.65f -> Pair("穏やかな微笑み", "リラックスして安心している雰囲気")
                                    avgEye > 0.6f && smileProb < 0.15f -> Pair("真剣な表情", "集中して深く考え事をしている様子")
                                    avgEye < 0.35f && smileProb < 0.15f -> Pair("少し暗めの落ち着いた表情", "物思いにふけっている様子")
                                    else -> Pair("自然体な表情", "落ち着いた普段の様子")
                                }

                                com.shinji.serena.ai.GeminiNanoEngine.PersonAnalysisDetail(
                                    position = pos,
                                    distanceMeters = attrs.estimatedDistanceMeters,
                                    genderAndAge = attrs.genderAndAge,
                                    clothingColor = "服",
                                    pantsColor = "ボトムス",
                                    expression = expressionDesc,
                                    emotionalMeaning = meaningDesc,
                                    isLookingAtCamera = isLooking
                                )
                            }

                            val desc = geminiNanoEngine.describeSceneComprehensive("", persons, emptyList(), emptyList())
                            if (desc != lastFaceDescription && desc.isNotEmpty()) {
                                lastFaceDescription = desc
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
            else -> {
                // LIVE 実況モード (Gemini Nano 統合解析: 照度 + 表情・位置 + OCR)
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
                        avg >= 150 -> "明るい室内"
                        avg in 60..149 -> "落ち着いた明るさの場所"
                        avg in 20..59 -> "薄暗い場所"
                        else -> "真っ暗な場所"
                    }
                } else {
                    ""
                }

                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        val imgWidth = image.width
                        val imgHeight = image.height
                        val persons = faces.map { face ->
                            val box = face.boundingBox
                            val centerX = box.centerX().toFloat() / imgWidth.coerceAtLeast(1)

                            val pos = when {
                                centerX < 0.35f -> "左側"
                                centerX > 0.65f -> "右側"
                                else -> "正面"
                            }

                            // 完全無料・オンデバイス属性解析（性別・推定年代・服の色・精密距離）
                            val attrs = com.shinji.serena.ai.AiVisionFeatureHelper.analyzePersonAttributes(
                                face = face,
                                imageWidth = imgWidth,
                                imageHeight = imgHeight,
                                bitmap = null
                            )

                            val smileProb = face.smilingProbability ?: 0f
                            val leftEye = face.leftEyeOpenProbability ?: 0.5f
                            val rightEye = face.rightEyeOpenProbability ?: 0.5f
                            val avgEye = (leftEye + rightEye) / 2f
                            val isLooking = avgEye > 0.4f

                            val (expressionDesc, meaningDesc) = when {
                                smileProb > 0.65f -> Pair("満面の明るい笑顔", "とても楽しそうに喜んでいる様子")
                                smileProb in 0.30f..0.65f -> Pair("穏やかな微笑み", "リラックスして安心している雰囲気")
                                avgEye > 0.6f && smileProb < 0.15f -> Pair("真剣な表情", "集中して深く考え事をしている様子")
                                avgEye < 0.35f && smileProb < 0.15f -> Pair("少し暗めの落ち着いた表情", "物思いにふけっている様子")
                                else -> Pair("自然体な表情", "落ち着いた普段の様子")
                            }

                            val clothingColorDesc = if (brightnessLevel.contains("明るい")) "明るめの服" else "落ち着いた色の服"

                            com.shinji.serena.ai.GeminiNanoEngine.PersonAnalysisDetail(
                                position = pos,
                                distanceMeters = attrs.estimatedDistanceMeters,
                                genderAndAge = attrs.genderAndAge,
                                clothingColor = clothingColorDesc,
                                pantsColor = "ボトムス",
                                expression = expressionDesc,
                                emotionalMeaning = meaningDesc,
                                isLookingAtCamera = isLooking
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

                                if (sceneSummary.isNotEmpty() && sceneSummary != lastSpokenText) {
                                    lastSpokenText = sceneSummary
                                    lastSpokenTime = currentTime
                                    runOnUiThread {
                                        tvStatus.text = "🌐 実況: $sceneSummary"
                                    }
                                    speak(sceneSummary, TextToSpeech.QUEUE_FLUSH)
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
        cameraExecutor.shutdown()
        speak("実況カメラを終了しました。")
    }
}
