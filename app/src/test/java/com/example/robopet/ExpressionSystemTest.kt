package com.example.robopet

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionSystemTest {

    @Test
    fun testExpressionControllerDefaultAndSet() {
        val controller = ExpressionController()

        assertEquals(FaceExpression.NEUTRAL, controller.currentExpression.value)

        controller.setExpression(FaceExpression.CURIOUS)
        assertEquals(FaceExpression.CURIOUS, controller.currentExpression.value)

        controller.resetToNeutral()
        assertEquals(FaceExpression.NEUTRAL, controller.currentExpression.value)
    }

    @Test
    fun testExpressionControllerAutoTimeout() = runBlocking {
        val controller = ExpressionController()

        controller.setExpression(FaceExpression.HAPPY, durationMs = 100L)
        assertEquals(FaceExpression.HAPPY, controller.currentExpression.value)

        delay(180L)
        assertEquals(FaceExpression.NEUTRAL, controller.currentExpression.value)
    }

    @Test
    fun testResolveTargetAllExpressionsDistinct() {
        val allExpressions = FaceExpression.entries
        val targets = allExpressions.associateWith { expr ->
            FaceAnimationDefaults.resolveTarget(
                expression = expr,
                isSleeping = false,
                isSpeaking = false,
                isThinking = false,
                isListening = false
            )
        }

        // Verify each expression has unique visual characteristics
        val happy = targets[FaceExpression.HAPPY]!!
        assertTrue("Happy expression should have happy squint arc", happy.leftEye.isHappyArc)
        assertTrue("Happy expression should show blush", happy.showBlush)
        assertEquals(MouthType.HEART, happy.mouth.type)

        val curious = targets[FaceExpression.CURIOUS]!!
        assertTrue("Curious left eye should tilt slightly", curious.leftEye.rotationDeg < 0f)
        assertTrue("Curious right eye should tilt slightly", curious.rightEye.rotationDeg > 0f)
        assertEquals(MouthType.CURIOUS_DOT, curious.mouth.type)

        val surprised = targets[FaceExpression.SURPRISED]!!
        assertTrue("Surprised eyes should be taller than neutral", surprised.leftEye.heightDp > 130f)
        assertTrue("Surprised pupils should contract", surprised.leftEye.pupilSizeDp < 20f)
        assertEquals(MouthType.ROUND_O, surprised.mouth.type)

        val confused = targets[FaceExpression.CONFUSED]!!
        // Verify asymmetry
        assertNotEquals("Confused eye heights should be asymmetric", confused.leftEye.heightDp, confused.rightEye.heightDp)
        assertEquals(MouthType.WAVY, confused.mouth.type)

        val playful = targets[FaceExpression.PLAYFUL]!!
        assertTrue("Playful should show cheek blush", playful.showBlush)
        assertTrue("Playful left pupil should be offset sideways", playful.leftEye.pupilOffsetX > 5f)
        assertTrue("Playful right eye should wink", playful.rightEye.isHappyArc)
        assertEquals(MouthType.PLAYFUL_SMIRK, playful.mouth.type)

        val sleepy = targets[FaceExpression.SLEEPY]!!
        assertTrue("Sleepy eyes should have droopy scaleY", sleepy.leftEye.scaleYMultiplier < 0.5f)
        assertEquals(MouthType.SLEEP_CURVE, sleepy.mouth.type)
    }

    @Test
    fun testSleepingOverridesExpression() {
        val target = FaceAnimationDefaults.resolveTarget(
            expression = FaceExpression.HAPPY,
            isSleeping = true,
            isSpeaking = false,
            isThinking = false,
            isListening = false
        )
        assertTrue("Sleeping target should have closed arc", target.leftEye.isClosedArc)
        assertFalse("Sleeping target should hide pupils", target.leftEye.pupilVisible)
        assertEquals(MouthType.SLEEP_CURVE, target.mouth.type)
    }

    @Test
    fun testSpeakingOverridesMouthWhilePreservingEyeExpression() {
        val target = FaceAnimationDefaults.resolveTarget(
            expression = FaceExpression.CURIOUS,
            isSleeping = false,
            isSpeaking = true,
            isThinking = false,
            isListening = false
        )
        assertEquals("Speaking should animate mouth", MouthType.SPEAKING, target.mouth.type)
        assertEquals("Eyes should remain curious while speaking", 22f, target.leftEye.pupilSizeDp)
    }
}
