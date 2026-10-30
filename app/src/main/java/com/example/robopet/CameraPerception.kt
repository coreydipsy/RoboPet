package com.example.robopet

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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

    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    val gesturePerception = GesturePerception(appContext)

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisInFlight = AtomicBoolean(false)

    private var lastFaceAnalyzedMs = 0L
    private var lastGestureAnalyzedMs = 0L

    // Telemetry & metrics
    var faceLatencyMs: Long = 0L
        private set
    var gestureLatencyMs: Long = 0L
        private set
    var isGestureEnabled: Boolean
        get() = gesturePerception.isEnabled
        set(value) {
            gesturePerception.isEnabled = value
        }

    // High performance throttle mode (e.g. while speech or SLM thinking is active)
    var isHeavyComputeActive: Boolean = false
    var isSleeping: Boolean = false

    companion object {
        private const val TAG = "CameraPerception"
        // Optimizing for Moto G 2025: ~9 FPS face detection, ~3.3 FPS gesture recognition
        private const val FACE_INTERVAL_MS = 110L
        private const val GESTURE_INTERVAL_MS = 300L
        private val TARGET_RESOLUTION = Size(480, 640) // Drastically cuts processing time vs 1080p
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
                .setTargetResolution(TARGET_RESOLUTION)
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
                Log.d(TAG, "CameraX bound with target resolution $TARGET_RESOLUTION")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX: ${e.message}")
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
        faceDetector.close()
        gesturePerception.close()
        analysisExecutor.shutdown()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy, onEvent: (RobotEvent) -> Unit) {
        val now = SystemClock.uptimeMillis()

        // If sleeping, skip processing to conserve battery and CPU
        if (isSleeping) {
            imageProxy.close()
            return
        }

        val runFace = (now - lastFaceAnalyzedMs >= FACE_INTERVAL_MS)
        // Skip gestures during active speech/thinking or if gestures disabled
        val runGesture = isGestureEnabled && !isHeavyComputeActive && (now - lastGestureAnalyzedMs >= GESTURE_INTERVAL_MS)

        if (!runFace && !runGesture) {
            imageProxy.close()
            return
        }

        if (!analysisInFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            analysisInFlight.set(false)
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees

        // 1. Synchronously process gesture if scheduled
        if (runGesture) {
            lastGestureAnalyzedMs = now
            val gestureStart = SystemClock.uptimeMillis()
            gesturePerception.processImage(imageProxy) { recognized ->
                onEvent(RobotEvent.GestureDetected(recognized))
            }
            gestureLatencyMs = SystemClock.uptimeMillis() - gestureStart
        }

        // 2. Process face if scheduled
        if (runFace) {
            lastFaceAnalyzedMs = now
            val faceStart = SystemClock.uptimeMillis()
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    faceLatencyMs = SystemClock.uptimeMillis() - faceStart
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
                .addOnFailureListener {
                    onEvent(RobotEvent.FaceLost)
                }
                .addOnCompleteListener {
                    analysisInFlight.set(false)
                    imageProxy.close()
                }
        } else {
            analysisInFlight.set(false)
            imageProxy.close()
        }
    }
}
