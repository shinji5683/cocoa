package com.shinji.serena.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.shinji.serena.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SerenaSentinelEyesManager (常駐AI見守りアイズ)
 *
 * 全盲のユーザーのために、Serena Eyesを手動で開いていなくても、
 * バックグラウンドでカメラが静かに見守り、
 * 人が目の前に現れたり、近づいたり、通り過ぎたりした瞬間に
 * 「正面 約1.5mに、白い服を着た笑顔の女性がいます」
 * と、性別・表情・感情・服装・相対方向・距離をリアルタイム実況する安全見守りエンジン。
 */
class SerenaSentinelEyesManager(
    private val context: Context,
    private val speakCallback: (String) -> Unit
) : LifecycleOwner {

    companion object {
        private const val TAG = "SerenaSentinelEyes"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isSentinelActive = false

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAnnouncement = ""
    private var lastAnnounceTimeMs = 0L
    private var lastFaceCount = 0

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun isSentinelActive(): Boolean = isSentinelActive

    /**
     * 常駐見守りアイズを開始
     */
    fun startSentinelEyes() {
        if (isSentinelActive) return
        isSentinelActive = true
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        cameraExecutor = Executors.newSingleThreadExecutor()
        speakCallback(context.getString(R.string.sentinel_started))

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraAnalysis()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider: ${e.message}")
                speakCallback(context.getString(R.string.sentinel_camera_error))
                stopSentinelEyes()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 常駐見守りアイズを停止
     */
    fun stopSentinelEyes() {
        if (!isSentinelActive) return
        isSentinelActive = false
        try {
            cameraProvider?.unbindAll()
            cameraExecutor?.shutdown()
            cameraExecutor = null
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sentinel eyes: ${e.message}")
        }
        speakCallback(context.getString(R.string.sentinel_stopped))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraAnalysis() {
        val provider = cameraProvider ?: return
        val executor = cameraExecutor ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && isSentinelActive) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    val width = imageProxy.width
                    val height = imageProxy.height

                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            processFaces(faces, width, height, mediaImage)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Face detection error: ${e.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            provider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            Log.i(TAG, "Sentinel Eyes camera bound successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Binding camera error: ${e.message}")
            speakCallback(context.getString(R.string.sentinel_bind_error))
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processFaces(faces: List<Face>, imgWidth: Int, imgHeight: Int, mediaImage: android.media.Image) {
        val now = System.currentTimeMillis()
        val currentCount = faces.size

        if (currentCount == 0) {
            if (lastFaceCount > 0 && now - lastAnnounceTimeMs > 3000) {
                lastFaceCount = 0
                mainHandler.post {
                    speakCallback(context.getString(R.string.sentinel_person_left_view))
                }
            }
            return
        }

        val primaryFace = faces[0]
        val centerXRatio = primaryFace.boundingBox.centerX().toFloat() / imgWidth.coerceAtLeast(1)

        // 相対方向（時計盤表現は完全禁止！）
        val directionStr = when {
            centerXRatio < 0.28f -> context.getString(R.string.dir_left)
            centerXRatio in 0.28f..0.42f -> context.getString(R.string.dir_front_left)
            centerXRatio in 0.42f..0.58f -> context.getString(R.string.dir_front)
            centerXRatio in 0.58f..0.72f -> context.getString(R.string.dir_front_right)
            else -> context.getString(R.string.dir_right)
        }

        // 性別・年代・服装・表情・感情・距離解析
        val attrs = AiVisionFeatureHelper.analyzePersonAttributes(
            face = primaryFace,
            imageWidth = imgWidth,
            imageHeight = imgHeight,
            mediaImage = mediaImage,
            bitmap = null
        )

        val announcement = if (currentCount == 1) {
            context.getString(
                R.string.sentinel_announce_single_fmt,
                directionStr,
                attrs.estimatedDistanceMeters,
                attrs.clothingDescription,
                attrs.genderAndAge,
                attrs.emotion.category,
                attrs.emotion.emotionalVibe
            )
        } else {
            context.getString(
                R.string.sentinel_announce_multi_fmt,
                directionStr,
                currentCount,
                attrs.estimatedDistanceMeters,
                attrs.genderAndAge,
                attrs.emotion.category
            )
        }

        // アナウンス間隔制御: 人数が変わったか、前回の実況と異なるか、または4秒以上経過
        if (currentCount != lastFaceCount || announcement != lastAnnouncement || now - lastAnnounceTimeMs > 4500) {
            lastAnnouncement = announcement
            lastAnnounceTimeMs = now
            lastFaceCount = currentCount

            mainHandler.post {
                speakCallback(announcement)
            }
        }
    }
}
