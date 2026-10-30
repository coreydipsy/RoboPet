package com.example.robopet

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AttentionTargetType {
    NONE,
    USER_INTERACTION, // Listening / Speaking / Petting
    GESTURE_HAND,     // Looking at user's hand/gesture
    USER_FACE,        // Tracking face
    CURIOSITY_WANDER, // Autonomous glancing
    RESTING           // Sleeping / resting
}

data class AttentionState(
    val targetType: AttentionTargetType = AttentionTargetType.NONE,
    val targetGazeX: Float = 0f,
    val targetGazeY: Float = 0f,
    val isFaceConfirmed: Boolean = false,
    val isUserPresent: Boolean = false
)

class AttentionManager {
    private val _state = MutableStateFlow(AttentionState())
    val state: StateFlow<AttentionState> = _state.asStateFlow()

    // Hysteresis timestamps
    private var faceFirstSeenTime = 0L
    private var faceLastSeenTime = 0L
    private var faceConfirmedTime = 0L
    private var lastGreetingTriggerTime = -GREETING_COOLDOWN_MS

    // Hand tracking hold
    private var handFocusUntil = 0L

    companion object {
        private const val FACE_ACQUISITION_MS = 380L      // Must see face for 380ms to confirm
        private const val FACE_LOSS_TOLERANCE_MS = 1400L  // Tolerate 1.4s temporary loss
        private const val SIGNIFICANT_ABSENCE_MS = 25000L // 25s absence resets greeting
        private const val GREETING_COOLDOWN_MS = 300000L   // 5 minutes between spontaneous greetings
    }

    /**
     * Updates face detection coordinates and evaluates presence hysteresis.
     * Returns true if a spontaneous greeting should be triggered (user returned after absence).
     */
    fun onFaceDetected(x: Float, y: Float, now: Long = SystemClock.uptimeMillis()): Boolean {
        faceLastSeenTime = now

        if (faceFirstSeenTime == 0L) {
            faceFirstSeenTime = now
        }

        val stableDuration = now - faceFirstSeenTime
        val isConfirmed = stableDuration >= FACE_ACQUISITION_MS

        val wasConfirmed = _state.value.isFaceConfirmed
        val absenceDuration = if (faceConfirmedTime > 0L) now - faceConfirmedTime else Long.MAX_VALUE

        // Gaze range: -12 to 12 on X, -10 to 10 on Y
        val gazeX = ((x - 0.5f) * 24f).coerceIn(-12f, 12f)
        val gazeY = ((y - 0.5f) * 20f).coerceIn(-10f, 10f)

        _state.update { current ->
            if (current.targetType <= AttentionTargetType.USER_FACE) {
                current.copy(
                    targetType = AttentionTargetType.USER_FACE,
                    targetGazeX = gazeX,
                    targetGazeY = gazeY,
                    isFaceConfirmed = isConfirmed,
                    isUserPresent = true
                )
            } else {
                current.copy(
                    isFaceConfirmed = isConfirmed,
                    isUserPresent = true
                )
            }
        }

        // Check if this is a newly confirmed arrival after a significant absence
        if (isConfirmed && !wasConfirmed) {
            faceConfirmedTime = now
            val canGreet = (now - lastGreetingTriggerTime >= GREETING_COOLDOWN_MS)
            val hadSignificantAbsence = absenceDuration >= SIGNIFICANT_ABSENCE_MS
            if (canGreet && hadSignificantAbsence) {
                lastGreetingTriggerTime = now
                return true
            }
        }

        return false
    }

    /**
     * Called when camera frame produces no face. Applies hysteresis tolerance.
     */
    fun onFaceLost(now: Long = SystemClock.uptimeMillis()) {
        val timeSinceSeen = now - faceLastSeenTime
        if (timeSinceSeen < FACE_LOSS_TOLERANCE_MS) {
            // Within grace period, maintain user present state
            return
        }

        faceFirstSeenTime = 0L

        _state.update { current ->
            val newTarget = if (current.targetType == AttentionTargetType.USER_FACE) {
                AttentionTargetType.NONE
            } else current.targetType

            current.copy(
                targetType = newTarget,
                isFaceConfirmed = false,
                isUserPresent = false
            )
        }
    }

    /**
     * Focuses on a recognized gesture/hand for a specified duration.
     */
    fun onHandGesture(handX: Float, handY: Float, holdMs: Long = 2000L, now: Long = SystemClock.uptimeMillis()) {
        handFocusUntil = now + holdMs
        val gazeX = ((handX - 0.5f) * 24f).coerceIn(-12f, 12f)
        val gazeY = ((handY - 0.5f) * 20f).coerceIn(-10f, 10f)

        _state.update {
            it.copy(
                targetType = AttentionTargetType.GESTURE_HAND,
                targetGazeX = gazeX,
                targetGazeY = gazeY
            )
        }
    }

    /**
     * Updates attention when autonomous wandering or user interaction is active.
     */
    fun setInteractionTarget(targetType: AttentionTargetType, gazeX: Float = 0f, gazeY: Float = 0f) {
        _state.update {
            it.copy(
                targetType = targetType,
                targetGazeX = gazeX,
                targetGazeY = gazeY
            )
        }
    }

    /**
     * Periodic check to release hand focus or idle targets if expired.
     */
    fun tick(now: Long = SystemClock.uptimeMillis()) {
        if (_state.value.targetType == AttentionTargetType.GESTURE_HAND && now > handFocusUntil) {
            // Hand focus expired, return to face tracking or none
            _state.update {
                if (it.isFaceConfirmed) {
                    it.copy(targetType = AttentionTargetType.USER_FACE)
                } else {
                    it.copy(targetType = AttentionTargetType.NONE, targetGazeX = 0f, targetGazeY = 0f)
                }
            }
        }
    }

    fun reset() {
        faceFirstSeenTime = 0L
        faceLastSeenTime = 0L
        faceConfirmedTime = 0L
        _state.value = AttentionState()
    }
}
