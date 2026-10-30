package com.example.robopet

import androidx.compose.ui.graphics.Color

/**
 * High-level facial expressions for RoboPet.
 */
enum class FaceExpression(val displayName: String, val description: String) {
    NEUTRAL("Neutral", "Relaxed resting face with natural blinking"),
    HAPPY("Happy", "Squinting curved eyes, cute blush, and smiling heart"),
    CURIOUS("Curious", "Wide inquisitive eyes with focused pupils and tilted gaze"),
    SURPRISED("Surprised", "Wide-open eyes with contracted pupils and rounded mouth"),
    CONFUSED("Confused", "Asymmetric tilted eyes with mismatched pupils and wavy mouth"),
    PLAYFUL("Playful", "Cheeky side-eye glance with winking expression and smirk"),
    SLEEPY("Sleepy", "Half-closed droopy eyelids, soft breathing pace, and lazy mouth")
}

/**
 * Geometric and visual parameters for one eye.
 */
data class EyeConfig(
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float,
    val rotationDeg: Float,
    val scaleYMultiplier: Float,
    val pupilSizeDp: Float,
    val pupilOffsetX: Float,
    val pupilOffsetY: Float,
    val pupilVisible: Boolean,
    val isHappyArc: Boolean = false,
    val isClosedArc: Boolean = false,
    val eyeColor: Color = Color(0xFF86E5FF),
    val pupilColor: Color = Color(0xFF101018)
)

/**
 * Types of mouth rendering supported by RobotFace.
 */
enum class MouthType {
    NONE,
    HEART,
    ROUND_O,
    WAVY,
    PLAYFUL_SMIRK,
    SLEEP_CURVE,
    CURIOUS_DOT,
    SPEAKING,
    THINKING,
    LISTENING
}

/**
 * Visual parameters for mouth rendering.
 */
data class MouthConfig(
    val type: MouthType,
    val widthDp: Float = 38f,
    val heightDp: Float = 14f,
    val rotationDeg: Float = 0f,
    val color: Color = Color(0xFF86E5FF)
)

/**
 * Complete instantaneous visual target state for the face.
 */
data class FaceVisualTarget(
    val leftEye: EyeConfig,
    val rightEye: EyeConfig,
    val mouth: MouthConfig,
    val showBlush: Boolean = false,
    val blushAlpha: Float = 0f
)

object FaceAnimationDefaults {

    val CYAN_NORMAL = Color(0xFF86E5FF)
    val CYAN_SLEEPY = Color(0xFF4A7B9D)
    val CYAN_WARM = Color(0xFF90F0FF)
    val PUPIL_DARK = Color(0xFF101018)

    /**
     * Resolves the visual target parameters for a given state combination.
     */
    fun resolveTarget(
        expression: FaceExpression,
        isSleeping: Boolean,
        isSpeaking: Boolean,
        isThinking: Boolean,
        isListening: Boolean,
        isHappyLegacy: Boolean = false
    ): FaceVisualTarget {
        // Priority 1: Sleeping overrides everything
        if (isSleeping) {
            val sleepEye = EyeConfig(
                widthDp = 95f,
                heightDp = 125f,
                cornerRadiusDp = 48f,
                rotationDeg = 0f,
                scaleYMultiplier = 0.08f,
                pupilSizeDp = 0f,
                pupilOffsetX = 0f,
                pupilOffsetY = 0f,
                pupilVisible = false,
                isClosedArc = true,
                eyeColor = CYAN_SLEEPY
            )
            val sleepMouth = MouthConfig(
                type = MouthType.SLEEP_CURVE,
                widthDp = 30f,
                heightDp = 16f,
                color = CYAN_SLEEPY.copy(alpha = 0.7f)
            )
            return FaceVisualTarget(
                leftEye = sleepEye,
                rightEye = sleepEye,
                mouth = sleepMouth,
                showBlush = false,
                blushAlpha = 0f
            )
        }

        val effectiveExpression = if (isHappyLegacy && expression == FaceExpression.NEUTRAL) {
            FaceExpression.HAPPY
        } else {
            expression
        }

        // Compute eye configurations based on expression
        val (leftEye, rightEye) = when (effectiveExpression) {
            FaceExpression.NEUTRAL -> {
                val eye = EyeConfig(
                    widthDp = 95f,
                    heightDp = 125f,
                    cornerRadiusDp = 48f,
                    rotationDeg = 0f,
                    scaleYMultiplier = 1.0f,
                    pupilSizeDp = 28f,
                    pupilOffsetX = 0f,
                    pupilOffsetY = 0f,
                    pupilVisible = true,
                    eyeColor = CYAN_NORMAL
                )
                Pair(eye, eye)
            }

            FaceExpression.HAPPY -> {
                // Curved squinting happy eyes with cheerful upward curvature
                val eye = EyeConfig(
                    widthDp = 95f,
                    heightDp = 115f,
                    cornerRadiusDp = 48f,
                    rotationDeg = 0f,
                    scaleYMultiplier = 0.28f,
                    pupilSizeDp = 0f,
                    pupilOffsetX = 0f,
                    pupilOffsetY = 0f,
                    pupilVisible = false,
                    isHappyArc = true,
                    eyeColor = CYAN_WARM
                )
                Pair(eye, eye)
            }

            FaceExpression.CURIOUS -> {
                // Slightly widened eyes with subtle inquisitive head-tilt asymmetry & focused pupils
                val left = EyeConfig(
                    widthDp = 98f,
                    heightDp = 132f,
                    cornerRadiusDp = 50f,
                    rotationDeg = -4f,
                    scaleYMultiplier = 1.05f,
                    pupilSizeDp = 22f,
                    pupilOffsetX = 1f,
                    pupilOffsetY = -4f,
                    pupilVisible = true,
                    eyeColor = CYAN_NORMAL
                )
                val right = EyeConfig(
                    widthDp = 104f,
                    heightDp = 136f,
                    cornerRadiusDp = 52f,
                    rotationDeg = 5f,
                    scaleYMultiplier = 1.08f,
                    pupilSizeDp = 22f,
                    pupilOffsetX = -1f,
                    pupilOffsetY = -6f,
                    pupilVisible = true,
                    eyeColor = CYAN_NORMAL
                )
                Pair(left, right)
            }

            FaceExpression.SURPRISED -> {
                // Wide open tall eyes with contracted pupils
                val eye = EyeConfig(
                    widthDp = 102f,
                    heightDp = 138f,
                    cornerRadiusDp = 50f,
                    rotationDeg = 0f,
                    scaleYMultiplier = 1.12f,
                    pupilSizeDp = 18f,
                    pupilOffsetX = 0f,
                    pupilOffsetY = -2f,
                    pupilVisible = true,
                    eyeColor = Color(0xFFA5F4FF)
                )
                Pair(eye, eye)
            }

            FaceExpression.CONFUSED -> {
                // Asymmetric eyes: Left eye squinted & angled down, right eye raised & inquisitive
                val left = EyeConfig(
                    widthDp = 86f,
                    heightDp = 100f,
                    cornerRadiusDp = 42f,
                    rotationDeg = -9f,
                    scaleYMultiplier = 0.72f,
                    pupilSizeDp = 24f,
                    pupilOffsetX = -4f,
                    pupilOffsetY = 3f,
                    pupilVisible = true,
                    eyeColor = CYAN_NORMAL
                )
                val right = EyeConfig(
                    widthDp = 102f,
                    heightDp = 134f,
                    cornerRadiusDp = 50f,
                    rotationDeg = 8f,
                    scaleYMultiplier = 1.10f,
                    pupilSizeDp = 26f,
                    pupilOffsetX = 5f,
                    pupilOffsetY = -6f,
                    pupilVisible = true,
                    eyeColor = CYAN_NORMAL
                )
                Pair(left, right)
            }

            FaceExpression.PLAYFUL -> {
                // Left eye open with playful side-look, right eye cheeky wink!
                val left = EyeConfig(
                    widthDp = 98f,
                    heightDp = 126f,
                    cornerRadiusDp = 48f,
                    rotationDeg = -3f,
                    scaleYMultiplier = 1.02f,
                    pupilSizeDp = 26f,
                    pupilOffsetX = 10f,
                    pupilOffsetY = -3f,
                    pupilVisible = true,
                    eyeColor = CYAN_WARM
                )
                val right = EyeConfig(
                    widthDp = 92f,
                    heightDp = 115f,
                    cornerRadiusDp = 46f,
                    rotationDeg = 6f,
                    scaleYMultiplier = 0.18f,
                    pupilSizeDp = 0f,
                    pupilOffsetX = 0f,
                    pupilOffsetY = 0f,
                    pupilVisible = false,
                    isHappyArc = true,
                    eyeColor = CYAN_WARM
                )
                Pair(left, right)
            }

            FaceExpression.SLEEPY -> {
                // Droopy half-closed eyelids and softened dimmer blue
                val eye = EyeConfig(
                    widthDp = 95f,
                    heightDp = 120f,
                    cornerRadiusDp = 46f,
                    rotationDeg = 0f,
                    scaleYMultiplier = 0.42f,
                    pupilSizeDp = 24f,
                    pupilOffsetX = 0f,
                    pupilOffsetY = 8f,
                    pupilVisible = true,
                    eyeColor = CYAN_SLEEPY
                )
                Pair(eye, eye)
            }
        }

        // Compute mouth based on active states and expressions
        val mouth = when {
            isSpeaking -> MouthConfig(
                type = MouthType.SPEAKING,
                widthDp = 38f,
                heightDp = 20f,
                color = CYAN_NORMAL
            )

            isThinking -> MouthConfig(
                type = MouthType.THINKING,
                widthDp = 45f,
                heightDp = 20f,
                color = CYAN_NORMAL
            )

            isListening -> MouthConfig(
                type = MouthType.LISTENING,
                widthDp = 14f,
                heightDp = 14f,
                color = CYAN_NORMAL.copy(alpha = 0.85f)
            )

            effectiveExpression == FaceExpression.HAPPY -> MouthConfig(
                type = MouthType.HEART,
                widthDp = 38f,
                heightDp = 38f,
                color = Color(0xFFFF85B3)
            )

            effectiveExpression == FaceExpression.SURPRISED -> MouthConfig(
                type = MouthType.ROUND_O,
                widthDp = 26f,
                heightDp = 34f,
                color = CYAN_NORMAL
            )

            effectiveExpression == FaceExpression.CONFUSED -> MouthConfig(
                type = MouthType.WAVY,
                widthDp = 36f,
                heightDp = 12f,
                rotationDeg = -6f,
                color = CYAN_NORMAL
            )

            effectiveExpression == FaceExpression.PLAYFUL -> MouthConfig(
                type = MouthType.PLAYFUL_SMIRK,
                widthDp = 34f,
                heightDp = 18f,
                rotationDeg = 8f,
                color = Color(0xFFFF94B8)
            )

            effectiveExpression == FaceExpression.CURIOUS -> MouthConfig(
                type = MouthType.CURIOUS_DOT,
                widthDp = 16f,
                heightDp = 16f,
                color = CYAN_NORMAL
            )

            effectiveExpression == FaceExpression.SLEEPY -> MouthConfig(
                type = MouthType.SLEEP_CURVE,
                widthDp = 24f,
                heightDp = 10f,
                color = CYAN_SLEEPY.copy(alpha = 0.6f)
            )

            else -> MouthConfig(type = MouthType.NONE)
        }

        val showBlush = effectiveExpression == FaceExpression.HAPPY || effectiveExpression == FaceExpression.PLAYFUL
        val blushAlpha = when (effectiveExpression) {
            FaceExpression.HAPPY -> 0.75f
            FaceExpression.PLAYFUL -> 0.60f
            else -> 0f
        }

        return FaceVisualTarget(
            leftEye = leftEye,
            rightEye = rightEye,
            mouth = mouth,
            showBlush = showBlush,
            blushAlpha = blushAlpha
        )
    }
}
