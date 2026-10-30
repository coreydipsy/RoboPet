package com.example.robopet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutonomousBehaviorTest {
    private lateinit var attentionManager: AttentionManager
    private lateinit var personalityEngine: PersonalityEngine

    @Before
    fun setup() {
        attentionManager = AttentionManager()
        personalityEngine = PersonalityEngine(null)
    }

    @Test
    fun attentionManager_faceAcquisitionRequiresStability() {
        // First detection at t=1000
        val greet1 = attentionManager.onFaceDetected(0.5f, 0.5f, now = 1000L)
        assertFalse("Single frame under 380ms should not confirm face yet", attentionManager.state.value.isFaceConfirmed)
        assertFalse(greet1)

        // Second detection at t=1200 (200ms elapsed < 380ms)
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1200L)
        assertFalse("Still under 380ms", attentionManager.state.value.isFaceConfirmed)

        // Third detection at t=1400 (400ms elapsed >= 380ms)
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1400L)
        assertTrue("Face confirmed after >= 380ms stable detection", attentionManager.state.value.isFaceConfirmed)
        assertTrue("Target type should be USER_FACE", attentionManager.state.value.targetType == AttentionTargetType.USER_FACE)
    }

    @Test
    fun attentionManager_temporaryFaceLossTolerated() {
        // Acquire face
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1000L)
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1400L)
        assertTrue(attentionManager.state.value.isFaceConfirmed)

        // Lost face for 500ms (< 1400ms tolerance)
        attentionManager.onFaceLost(now = 1900L)
        assertTrue("User presence should be maintained within grace period", attentionManager.state.value.isUserPresent)

        // Lost face for 2000ms (> 1400ms tolerance)
        attentionManager.onFaceLost(now = 3500L)
        assertFalse("User presence should be lost after exceeding grace period", attentionManager.state.value.isUserPresent)
        assertFalse("Face should no longer be confirmed", attentionManager.state.value.isFaceConfirmed)
    }

    @Test
    fun attentionManager_handGestureTakesPriorityOverFaceTracking() {
        // Acquire face
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1000L)
        attentionManager.onFaceDetected(0.5f, 0.5f, now = 1400L)
        assertEquals(AttentionTargetType.USER_FACE, attentionManager.state.value.targetType)

        // Hand gesture detected
        attentionManager.onHandGesture(handX = 0.2f, handY = 0.8f, holdMs = 2000L, now = 1500L)
        assertEquals(AttentionTargetType.GESTURE_HAND, attentionManager.state.value.targetType)

        // Tick before expiration (t=2500 < 3500)
        attentionManager.tick(now = 2500L)
        assertEquals(AttentionTargetType.GESTURE_HAND, attentionManager.state.value.targetType)

        // Tick after expiration (t=3600 > 3500)
        attentionManager.tick(now = 3600L)
        assertEquals("Should return to USER_FACE after hand focus expires", AttentionTargetType.USER_FACE, attentionManager.state.value.targetType)
    }

    @Test
    fun personalityEngine_drivesDecayAndSleepRecovery() {
        val initialEnergy = personalityEngine.drives.value.energy
        assertEquals(1.0f, initialEnergy, 0.001f)

        // Decay drives while awake for 60 seconds
        personalityEngine.decayDrives(60f, isSleeping = false)
        val awakeEnergy = personalityEngine.drives.value.energy
        assertTrue("Energy must decay while awake", awakeEnergy < initialEnergy)

        // Recover drives while sleeping for 30 seconds
        personalityEngine.decayDrives(30f, isSleeping = true)
        val sleptEnergy = personalityEngine.drives.value.energy
        assertTrue("Energy must recover during sleep", sleptEnergy > awakeEnergy)
    }

    @Test
    fun personalityEngine_interactionBoostsSocialInterest() {
        val initialSocial = personalityEngine.drives.value.socialInterest
        personalityEngine.recordInteraction(now = 1000L)
        val postInteractionSocial = personalityEngine.drives.value.socialInterest
        assertTrue("Interaction must increase social interest", postInteractionSocial > initialSocial)
    }
}
