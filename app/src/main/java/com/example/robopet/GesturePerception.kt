package com.example.robopet

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class HandGesture {
    NONE,
    CLOSED_FIST,
    OPEN_PALM,
    THUMB_UP,
    THUMB_DOWN,
    VICTORY,
    POINTING_UP,
    WAVE,
    APPROACHING
}

data class RecognizedGesture(
    val gesture: HandGesture,
    val confidence: Float,
    val handX: Float,
    val handY: Float,
    val handSize: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class WaveDetector {
    private val history = mutableListOf<Pair<Long, Float>>() // timestamp to handX
    private var lastWaveTriggerTime = -WAVE_COOLDOWN_MS

    companion object {
        private const val WINDOW_MS = 1200L
        private const val MIN_WINDOW_MS = 350L
        private const val WAVE_COOLDOWN_MS = 8000L
        private const val MIN_SWING_DELTA = 0.07f
        private const val MIN_TOTAL_SPAN = 0.12f
    }

    fun processHand(
        isPalm: Boolean,
        handX: Float,
        confidence: Float,
        nowMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        if (nowMs - lastWaveTriggerTime < WAVE_COOLDOWN_MS) {
            history.clear()
            return false
        }

        if (!isPalm || confidence < 0.65f) {
            // Palm lost, purge old entries
            history.removeAll { nowMs - it.first > 300 }
            return false
        }

        // Add to history and purge old entries
        history.add(nowMs to handX)
        history.removeAll { nowMs - it.first > WINDOW_MS }

        if (history.size < 4) return false

        val duration = history.last().first - history.first().first
        if (duration < MIN_WINDOW_MS) return false

        // Check horizontal span
        val xs = history.map { it.second }
        val minX = xs.minOrNull() ?: return false
        val maxX = xs.maxOrNull() ?: return false
        val totalSpan = maxX - minX
        if (totalSpan < MIN_TOTAL_SPAN) return false

        // Check for direction reversal (reversal: left->right then right->left or vice versa)
        var reversals = 0
        var lastDirection = 0 // -1 = moving left, +1 = moving right
        var lastPeakX = xs.first()

        for (i in 1 until xs.size) {
            val delta = xs[i] - xs[i - 1]
            if (kotlin.math.abs(delta) < 0.015f) continue

            val currentDirection = if (delta > 0) 1 else -1
            if (lastDirection != 0 && currentDirection != lastDirection) {
                val swing = kotlin.math.abs(xs[i - 1] - lastPeakX)
                if (swing >= MIN_SWING_DELTA) {
                    reversals++
                    lastPeakX = xs[i - 1]
                }
            }
            lastDirection = currentDirection
        }

        if (reversals >= 1) {
            lastWaveTriggerTime = nowMs
            history.clear()
            return true
        }

        return false
    }

    fun reset() {
        history.clear()
    }
}

class GesturePerception(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: GestureRecognizer? = null
    private val isInitialized = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)

    private val waveDetector = WaveDetector()

    // Stability tracking
    private var candidateGesture: HandGesture = HandGesture.NONE
    private var candidateStartTime: Long = 0L
    private var lastTriggeredGesture: HandGesture = HandGesture.NONE
    private var lastTriggerTime: Long = 0L

    var isEnabled: Boolean = true
    var isRpsActive: Boolean = false
    var lastInferenceTimeMs: Long = 0L
        private set
    var lastConfidence: Float = 0f
        private set
    var currentDetectedGesture: HandGesture = HandGesture.NONE
        private set

    companion object {
        private const val TAG = "GesturePerception"
        private const val STABILITY_DURATION_MS = 280L
        private const val DEFAULT_COOLDOWN_MS = 5000L
        private const val CONFIDENCE_THRESHOLD = 0.70f
        private const val APPROACHING_HAND_SIZE_THRESHOLD = 0.38f
    }

    fun initRecognizer(): Boolean {
        if (recognizer != null) return true
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.65f)
                .setMinHandPresenceConfidence(0.65f)
                .setMinTrackingConfidence(0.65f)
                .build()

            recognizer = GestureRecognizer.createFromOptions(appContext, options)
            isInitialized.set(true)
            Log.d(TAG, "MediaPipe GestureRecognizer initialized successfully from assets")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GestureRecognizer: ${e.message}", e)
            return false
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun processImage(
        imageProxy: ImageProxy,
        onGestureEvent: (RecognizedGesture) -> Unit
    ) {
        if (!isEnabled) {
            return
        }

        if (recognizer == null && !initRecognizer()) {
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            return
        }

        val startTime = SystemClock.uptimeMillis()

        try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            val mpImage = BitmapImageBuilder(bitmap).build()
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotation)
                .build()

            val result: GestureRecognizerResult = recognizer?.recognize(mpImage, processingOptions)
                ?: return

            lastInferenceTimeMs = SystemClock.uptimeMillis() - startTime

            handleResult(result, onGestureEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error during gesture recognition: ${e.message}")
        } finally {
            isProcessing.set(false)
        }
    }

    private fun handleResult(
        result: GestureRecognizerResult,
        onGestureEvent: (RecognizedGesture) -> Unit
    ) {
        val now = SystemClock.uptimeMillis()

        val gestureCategory = result.gestures().firstOrNull()?.firstOrNull()
        val rawLandmarks = result.landmarks().firstOrNull()

        if (gestureCategory == null || rawLandmarks.isNullOrEmpty()) {
            // Hand lost or no gesture
            waveDetector.processHand(false, 0f, 0f, now)
            candidateGesture = HandGesture.NONE
            candidateStartTime = 0L
            currentDetectedGesture = HandGesture.NONE
            return
        }

        val confidence = gestureCategory.score()
        lastConfidence = confidence
        val categoryName = gestureCategory.categoryName()

        // Calculate hand center and span
        val xs = rawLandmarks.map { it.x() }
        val ys = rawLandmarks.map { it.y() }
        val rawCenterX = xs.average().toFloat()
        val centerY = ys.average().toFloat()
        // Mirror X for front camera
        val handX = (1f - rawCenterX).coerceIn(0f, 1f)

        val widthSpan = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        val heightSpan = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        val handSize = kotlin.math.max(widthSpan, heightSpan)

        // Check for wave first
        val isPalm = categoryName.equals("Open_Palm", ignoreCase = true)
        val isWaving = waveDetector.processHand(isPalm, handX, confidence, now)

        if (isWaving) {
            currentDetectedGesture = HandGesture.WAVE
            lastTriggerTime = now
            lastTriggeredGesture = HandGesture.WAVE
            candidateGesture = HandGesture.NONE
            candidateStartTime = 0L
            Log.d(TAG, "Triggered gesture: WAVE (confidence: $confidence)")
            onGestureEvent(
                RecognizedGesture(
                    gesture = HandGesture.WAVE,
                    confidence = confidence,
                    handX = handX,
                    handY = centerY,
                    handSize = handSize
                )
            )
            return
        }

        // Map MediaPipe static categories
        val detected = when {
            handSize >= APPROACHING_HAND_SIZE_THRESHOLD -> HandGesture.APPROACHING
            categoryName.equals("Closed_Fist", ignoreCase = true) -> HandGesture.CLOSED_FIST
            categoryName.equals("Open_Palm", ignoreCase = true) -> HandGesture.OPEN_PALM
            categoryName.equals("Thumb_Up", ignoreCase = true) -> HandGesture.THUMB_UP
            categoryName.equals("Thumb_Down", ignoreCase = true) -> HandGesture.THUMB_DOWN
            categoryName.equals("Victory", ignoreCase = true) -> HandGesture.VICTORY
            categoryName.equals("Pointing_Up", ignoreCase = true) -> HandGesture.POINTING_UP
            else -> HandGesture.NONE
        }

        currentDetectedGesture = detected

        val minConfidence = if (isRpsActive) 0.52f else CONFIDENCE_THRESHOLD
        val requiredStability = if (isRpsActive) 60L else STABILITY_DURATION_MS

        if (detected == HandGesture.NONE || confidence < minConfidence) {
            candidateGesture = HandGesture.NONE
            candidateStartTime = 0L
            return
        }

        // Stability filtering
        if (detected != candidateGesture) {
            candidateGesture = detected
            candidateStartTime = now
            return
        }

        val heldDuration = now - candidateStartTime
        if (heldDuration < requiredStability) {
            return
        }

        // Cooldown check: prevent triggering the same gesture continuously (bypassed during active RPS game!)
        val elapsedSinceTrigger = now - lastTriggerTime
        if (!isRpsActive && detected == lastTriggeredGesture && elapsedSinceTrigger < DEFAULT_COOLDOWN_MS) {
            return
        }

        // Confirmed stable gesture outside cooldown!
        lastTriggerTime = now
        lastTriggeredGesture = detected
        Log.d(TAG, "Triggered gesture: $detected (confidence: $confidence, handSize: $handSize, rpsActive=$isRpsActive)")

        onGestureEvent(
            RecognizedGesture(
                gesture = detected,
                confidence = confidence,
                handX = handX,
                handY = centerY,
                handSize = handSize
            )
        )
    }

    fun close() {
        try {
            recognizer?.close()
        } catch (_: Exception) {}
        recognizer = null
        isInitialized.set(false)
    }
}
