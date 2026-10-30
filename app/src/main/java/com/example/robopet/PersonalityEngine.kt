package com.example.robopet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PersonalityTraits(
    val curiosity: Float = 0.70f,
    val affection: Float = 0.65f,
    val playfulness: Float = 0.75f,
    val independence: Float = 0.55f,
    val sociability: Float = 0.45f
)

data class InternalDrives(
    val energy: Float = 1.0f,            // 0.0 to 1.0
    val curiosityLevel: Float = 0.70f,   // 0.0 to 1.0
    val socialInterest: Float = 0.60f,   // 0.0 to 1.0
    val lastInteractionTime: Long = System.currentTimeMillis(),
    val lastFaceSeenTime: Long = 0L,
    val lastSpontaneousActionTime: Long = 0L,
    val spontaneousActionCount: Int = 0,
    val isQuietMode: Boolean = false,
    val isAutonomousEnabled: Boolean = true
)

class PersonalityEngine(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences("robopet_personality", Context.MODE_PRIVATE)

    private val _traits = MutableStateFlow(loadTraits())
    val traits: StateFlow<PersonalityTraits> = _traits.asStateFlow()

    private val _drives = MutableStateFlow(
        InternalDrives(
            isQuietMode = prefs?.getBoolean("quiet_mode", false) ?: false,
            isAutonomousEnabled = prefs?.getBoolean("autonomous_enabled", true) ?: true
        )
    )
    val drives: StateFlow<InternalDrives> = _drives.asStateFlow()

    private fun loadTraits(): PersonalityTraits {
        if (prefs == null) return PersonalityTraits()
        return PersonalityTraits(
            curiosity = prefs.getFloat("trait_curiosity", 0.70f),
            affection = prefs.getFloat("trait_affection", 0.65f),
            playfulness = prefs.getFloat("trait_playfulness", 0.75f),
            independence = prefs.getFloat("trait_independence", 0.55f),
            sociability = prefs.getFloat("trait_sociability", 0.45f)
        )
    }

    fun setQuietMode(enabled: Boolean) {
        _drives.update { it.copy(isQuietMode = enabled) }
        prefs?.edit()?.putBoolean("quiet_mode", enabled)?.apply()
    }

    fun setAutonomousEnabled(enabled: Boolean) {
        _drives.update { it.copy(isAutonomousEnabled = enabled) }
        prefs?.edit()?.putBoolean("autonomous_enabled", enabled)?.apply()
    }

    fun recordInteraction(now: Long = System.currentTimeMillis()) {
        _drives.update {
            it.copy(
                lastInteractionTime = now,
                // Direct interaction slightly boosts social satisfaction and energy
                energy = (it.energy + 0.05f).coerceIn(0.1f, 1.0f),
                socialInterest = (it.socialInterest + 0.1f).coerceIn(0f, 1.0f)
            )
        }
    }

    fun recordFaceSeen(now: Long = System.currentTimeMillis()) {
        _drives.update {
            it.copy(
                lastFaceSeenTime = now,
                socialInterest = (it.socialInterest + 0.02f).coerceIn(0f, 1.0f)
            )
        }
    }

    fun recordSpontaneousAction(now: Long = System.currentTimeMillis()) {
        _drives.update {
            it.copy(
                lastSpontaneousActionTime = now,
                spontaneousActionCount = it.spontaneousActionCount + 1,
                // Spontaneous actions consume a small amount of curiosity/energy
                energy = (it.energy - 0.02f).coerceIn(0.05f, 1.0f),
                curiosityLevel = (it.curiosityLevel - 0.05f).coerceIn(0.1f, 1.0f)
            )
        }
    }

    fun decayDrives(deltaSeconds: Float, isSleeping: Boolean) {
        _drives.update { current ->
            if (isSleeping) {
                // Recover energy during sleep
                val newEnergy = (current.energy + (0.01f * deltaSeconds)).coerceIn(0f, 1.0f)
                current.copy(energy = newEnergy)
            } else {
                // Slowly decay energy and increase curiosity while awake
                val energyDecay = (0.0008f * deltaSeconds) // ~1 hour to decay 50%
                val curiosityRise = (0.001f * deltaSeconds)
                current.copy(
                    energy = (current.energy - energyDecay).coerceIn(0.05f, 1.0f),
                    curiosityLevel = (current.curiosityLevel + curiosityRise).coerceIn(0f, 1.0f)
                )
            }
        }
    }

    fun resetDrives() {
        _drives.update {
            InternalDrives(
                isQuietMode = it.isQuietMode,
                isAutonomousEnabled = it.isAutonomousEnabled
            )
        }
    }
}
