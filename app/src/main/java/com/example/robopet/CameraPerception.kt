package com.example.robopet

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraPerception(context: Context) {
    private val appContext = context.applicationContext
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisInFlight = AtomicBoolean(false)
    private var lastAnalyzedTimeMs = 0L

    companion object {
        private const val ANALYSIS_INTERVAL_MS = 120L // ~8 FPS preserves CPU for on-device SLM
    }

    @SuppressLint("MissingPermission")
    fun start(lifecycleOwner: LifecycleOwner, onEvent: (RobotEvent) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(analysisExecutor) { imageProxy ->
                        analyze(imageProxy, onEvent)
                    }
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (_: Exception) {
                analysis.clearAnalyzer()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    fun close() {
        stop()
        detector.close()
        analysisExecutor.shutdown()
    }

    private fun analyze(imageProxy: ImageProxy, onEvent: (RobotEvent) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimeMs < ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        if (!analysisInFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimeMs = now
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            analysisInFlight.set(false)
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                if (face == null) {
                    onEvent(RobotEvent.FaceLost)
                } else {
                    val rotatedWidth =
                        if (rotation == 0 || rotation == 180) imageProxy.width else imageProxy.height
                    val rotatedHeight =
                        if (rotation == 0 || rotation == 180) imageProxy.height else imageProxy.width
                    val centerX = face.boundingBox.centerX() / rotatedWidth.toFloat()
                    val centerY = face.boundingBox.centerY() / rotatedHeight.toFloat()
                    onEvent(
                        RobotEvent.FaceDetected(
                            x = (1f - centerX).coerceIn(0f, 1f),
                            y = centerY.coerceIn(0f, 1f)
                        )
                    )
                }
            }
            .addOnCompleteListener {
                analysisInFlight.set(false)
                imageProxy.close()
            }
    }
}
