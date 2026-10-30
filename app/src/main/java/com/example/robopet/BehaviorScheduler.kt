package com.example.robopet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class BehaviorScheduler(
    private val personalityEngine: PersonalityEngine,
    private val attentionManager: AttentionManager,
    private val soundEffects: SoundEffects,
    private val onTriggerExpression: (FaceExpression, Long) -> Unit,
    private val onTriggerSleep: () -> Unit,
    private val onGazeShift: (Float, Float) -> Unit
) {
    private var schedulerJob: Job? = null

    companion object {
        private const val INACTIVITY_SLEEP_TIMEOUT_MS = 300000L // 5 minutes
    }

    fun start(scope: CoroutineScope, isSleepingProvider: () -> Boolean, isBusyProvider: () -> Boolean) {
        schedulerJob?.cancel()
        schedulerJob = scope.launch {
            while (isActive) {
                delay(3000)

                val isSleeping = isSleepingProvider()
                val isBusy = isBusyProvider() // speaking, listening, or thinking

                // 1. Decay/recover personality drives
                personalityEngine.decayDrives(3.0f, isSleeping)
                attentionManager.tick()

                if (isSleeping || isBusy) {
                    continue
                }

                val drives = personalityEngine.drives.value
                val traits = personalityEngine.traits.value

                if (!drives.isAutonomousEnabled) {
                    continue
                }

                val now = System.currentTimeMillis()
                val timeSinceInteraction = now - drives.lastInteractionTime
                val timeSinceFaceSeen = now - drives.lastFaceSeenTime

                // 2. Inactivity sleep check
                val hasBeenInactive = (timeSinceInteraction > INACTIVITY_SLEEP_TIMEOUT_MS) &&
                        (timeSinceFaceSeen > INACTIVITY_SLEEP_TIMEOUT_MS)

                if (hasBeenInactive) {
                    onTriggerExpression(FaceExpression.SLEEPY, 3500L)
                    soundEffects.play(SoundEffectType.SLEEP_SIGH)
                    delay(3500)
                    onTriggerSleep()
                    continue
                }

                // 3. Spontaneous curiosity/playfulness evaluation
                val timeSinceSpontaneous = now - drives.lastSpontaneousActionTime
                val minIntervalMs = if (drives.isQuietMode) 60000L else 28000L

                val attention = attentionManager.state.value

                if (timeSinceSpontaneous >= minIntervalMs) {
                    // Decide if we should do a spontaneous action based on playfulness & energy
                    val triggerChance = (traits.playfulness * 0.5f) + (drives.curiosityLevel * 0.5f)
                    if (Random.nextFloat() < triggerChance) {
                        personalityEngine.recordSpontaneousAction(now)

                        if (!drives.isQuietMode && Random.nextFloat() < 0.45f) {
                            // Playful or curious reaction
                            if (Random.nextBoolean()) {
                                onTriggerExpression(FaceExpression.PLAYFUL, 2500L)
                                soundEffects.play(SoundEffectType.PLAYFUL_TRILL)
                            } else {
                                onTriggerExpression(FaceExpression.CURIOUS, 2500L)
                                soundEffects.play(SoundEffectType.CURIOUS_SQUEAK)
                            }
                        }
                    }
                }

                // 4. Autonomous gaze wandering (when no face or hand is being tracked)
                if (attention.targetType == AttentionTargetType.NONE && !drives.isQuietMode) {
                    if (Random.nextFloat() < 0.40f) {
                        val wanderX = Random.nextInt(-11, 12).toFloat()
                        val wanderY = Random.nextInt(-9, 10).toFloat()
                        onGazeShift(wanderX, wanderY)
                        delay(1200)
                        if (attentionManager.state.value.targetType == AttentionTargetType.NONE) {
                            onGazeShift(0f, 0f)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
    }
}
