package com.example.robopet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GesturePerceptionTest {
    private lateinit var detector: WaveDetector

    @Before
    fun setup() {
        detector = WaveDetector()
    }

    @Test
    fun nonPalmGesture_doesNotTriggerWave() {
        val triggered = detector.processHand(
            isPalm = false,
            handX = 0.5f,
            confidence = 0.9f,
            nowMs = 1000L
        )
        assertFalse(triggered)
    }

    @Test
    fun staticPalm_doesNotTriggerWave() {
        var triggered = false
        // Palm stays stationary at x = 0.5f for 600ms
        for (i in 0..6) {
            val res = detector.processHand(
                isPalm = true,
                handX = 0.5f,
                confidence = 0.85f,
                nowMs = 1000L + (i * 100L)
            )
            if (res) triggered = true
        }
        assertFalse("Stationary hand should not trigger wave", triggered)
    }

    @Test
    fun unidirectionalMovement_doesNotTriggerWave() {
        var triggered = false
        // Hand moves monotonically from left to right (0.2 -> 0.7) without reversal
        val steps = listOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
        steps.forEachIndexed { i, x ->
            val res = detector.processHand(
                isPalm = true,
                handX = x,
                confidence = 0.85f,
                nowMs = 1000L + (i * 80L)
            )
            if (res) triggered = true
        }
        assertFalse("Monotonic movement without reversal should not trigger wave", triggered)
    }

    @Test
    fun oscillatingHand_triggersWave() {
        var triggered = false
        // Hand moves left -> right (0.35 -> 0.65) then right -> left (0.65 -> 0.40)
        val movement = listOf(
            0.35f, 0.42f, 0.52f, 0.62f, 0.65f, // moving right
            0.55f, 0.45f, 0.38f                // reversed to left
        )

        movement.forEachIndexed { i, x ->
            val res = detector.processHand(
                isPalm = true,
                handX = x,
                confidence = 0.88f,
                nowMs = 1000L + (i * 80L)
            )
            if (res) triggered = true
        }
        assertTrue("Oscillating hand with direction reversal must trigger wave", triggered)
    }

    @Test
    fun waveCooldown_suppressesSecondWaveWithinCooldown() {
        val movement = listOf(
            0.35f, 0.45f, 0.55f, 0.65f,
            0.55f, 0.45f, 0.35f
        )

        var firstWave = false
        movement.forEachIndexed { i, x ->
            if (detector.processHand(true, x, 0.9f, 1000L + (i * 80L))) {
                firstWave = true
            }
        }
        assertTrue("First wave should succeed", firstWave)

        // Try waving again after 2 seconds (well within 8-second cooldown)
        var secondWave = false
        movement.forEachIndexed { i, x ->
            if (detector.processHand(true, x, 0.9f, 3000L + (i * 80L))) {
                secondWave = true
            }
        }
        assertFalse("Second wave within cooldown must be suppressed", secondWave)

        // Try waving after cooldown expires (8.5 seconds later)
        var thirdWave = false
        movement.forEachIndexed { i, x ->
            if (detector.processHand(true, x, 0.9f, 10000L + (i * 80L))) {
                thirdWave = true
            }
        }
        assertTrue("Third wave after cooldown must succeed", thirdWave)
    }
}
