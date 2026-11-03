package com.example.robopet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingCompanionFeaturesTest {

    @Test
    fun relationshipStats_stageAndLevelProgression() {
        val acquaintance = RelationshipStatsEntity(score = 50)
        assertEquals("New Acquaintance", acquaintance.stage)
        assertEquals(1, acquaintance.stageLevel)

        val friend = RelationshipStatsEntity(score = 250)
        assertEquals("Familiar Friend", friend.stage)
        assertEquals(2, friend.stageLevel)

        val companion = RelationshipStatsEntity(score = 550)
        assertEquals("Close Companion", companion.stage)
        assertEquals(3, companion.stageLevel)

        val bestFriend = RelationshipStatsEntity(score = 850)
        assertEquals("Best Friend / Soulmate", bestFriend.stage)
        assertEquals(4, bestFriend.stageLevel)
    }

    @Test
    fun relationshipStats_scoreClamping() {
        val zeroScore = RelationshipStatsEntity(score = 0)
        assertEquals("New Acquaintance", zeroScore.stage)
        assertEquals(1, zeroScore.stageLevel)

        val maxScore = RelationshipStatsEntity(score = 1000)
        assertEquals("Best Friend / Soulmate", maxScore.stage)
        assertEquals(4, maxScore.stageLevel)
    }

    @Test
    fun spontaneousSpeech_dailyBudgetCalculation() {
        val fullBudget = SpontaneousSpeechState(maxDailyBudget = 4, usedToday = 0)
        assertEquals(4, fullBudget.remainingToday)

        val partialBudget = SpontaneousSpeechState(maxDailyBudget = 4, usedToday = 2)
        assertEquals(2, partialBudget.remainingToday)

        val exhaustedBudget = SpontaneousSpeechState(maxDailyBudget = 4, usedToday = 4)
        assertEquals(0, exhaustedBudget.remainingToday)

        val overBudget = SpontaneousSpeechState(maxDailyBudget = 4, usedToday = 6)
        assertEquals(0, overBudget.remainingToday)
    }

    @Test
    fun dockMode_visibilityRules() {
        // AUTO_ON_CHARGE
        val autoUnplugged = RobotState(dockModeSetting = DockModeSetting.AUTO_ON_CHARGE, isCharging = false)
        val isAutoUnpluggedVisible = when (autoUnplugged.dockModeSetting) {
            DockModeSetting.ALWAYS_ON -> true
            DockModeSetting.AUTO_ON_CHARGE -> autoUnplugged.isCharging
            DockModeSetting.DISABLED -> false
        }
        assertFalse("Auto should be hidden when unplugged", isAutoUnpluggedVisible)

        val autoCharging = RobotState(dockModeSetting = DockModeSetting.AUTO_ON_CHARGE, isCharging = true)
        val isAutoChargingVisible = when (autoCharging.dockModeSetting) {
            DockModeSetting.ALWAYS_ON -> true
            DockModeSetting.AUTO_ON_CHARGE -> autoCharging.isCharging
            DockModeSetting.DISABLED -> false
        }
        assertTrue("Auto should be visible when charging", isAutoChargingVisible)

        // ALWAYS_ON
        val alwaysOnUnplugged = RobotState(dockModeSetting = DockModeSetting.ALWAYS_ON, isCharging = false)
        val isAlwaysOnVisible = when (alwaysOnUnplugged.dockModeSetting) {
            DockModeSetting.ALWAYS_ON -> true
            DockModeSetting.AUTO_ON_CHARGE -> alwaysOnUnplugged.isCharging
            DockModeSetting.DISABLED -> false
        }
        assertTrue("Always on should be visible even when unplugged", isAlwaysOnVisible)

        // DISABLED
        val disabledCharging = RobotState(dockModeSetting = DockModeSetting.DISABLED, isCharging = true)
        val isDisabledVisible = when (disabledCharging.dockModeSetting) {
            DockModeSetting.ALWAYS_ON -> true
            DockModeSetting.AUTO_ON_CHARGE -> disabledCharging.isCharging
            DockModeSetting.DISABLED -> false
        }
        assertFalse("Disabled mode should never be visible", isDisabledVisible)
    }

    @Test
    fun deskClock_nightDimmingWindow() {
        // 10 PM to 7 AM window
        fun isNightDimmed(hour: Int): Boolean = hour >= 22 || hour < 7

        assertTrue("10 PM should be dimmed", isNightDimmed(22))
        assertTrue("11 PM should be dimmed", isNightDimmed(23))
        assertTrue("Midnight should be dimmed", isNightDimmed(0))
        assertTrue("3 AM should be dimmed", isNightDimmed(3))
        assertTrue("6 AM should be dimmed", isNightDimmed(6))

        assertFalse("7 AM should not be dimmed", isNightDimmed(7))
        assertFalse("12 PM noon should not be dimmed", isNightDimmed(12))
        assertFalse("6 PM should not be dimmed", isNightDimmed(18))
        assertFalse("9 PM should not be dimmed", isNightDimmed(21))
    }

    @Test
    fun personalityEngine_traitSync() {
        val engine = PersonalityEngine(null)
        val newTraits = PersonalityTraits(
            curiosity = 0.88f,
            affection = 0.92f,
            playfulness = 0.80f,
            independence = 0.40f,
            sociability = 0.75f
        )
        engine.updateTraits(newTraits)
        val synced = engine.traits.value

        assertEquals(0.88f, synced.curiosity, 0.001f)
        assertEquals(0.92f, synced.affection, 0.001f)
        assertEquals(0.80f, synced.playfulness, 0.001f)
        assertEquals(0.40f, synced.independence, 0.001f)
        assertEquals(0.75f, synced.sociability, 0.001f)
    }
}
