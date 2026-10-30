package com.example.robopet

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RobotState(
    val isBlinking: Boolean = false,
    val isHappy: Boolean = false,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val faceDetected: Boolean = false,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSleeping: Boolean = false,
    val spokenText: String? = null,
    val recognizedText: String? = null,
    val speechModeNotice: String? = null,
    val speechError: String? = null,
    val audioLevel: Float = 0f,
    val currentExpression: FaceExpression = FaceExpression.NEUTRAL,
    val lastGesture: HandGesture = HandGesture.NONE,
    val gestureConfidence: Float = 0f,
    val attentionTarget: AttentionTargetType = AttentionTargetType.NONE,
    val energy: Float = 1.0f,
    val curiosityLevel: Float = 0.70f,
    val isQuietMode: Boolean = false,
    val isAutonomousEnabled: Boolean = true
)

sealed interface RobotEvent {
    data object Petted : RobotEvent
    data class FaceDetected(val x: Float, val y: Float) : RobotEvent
    data object FaceLost : RobotEvent
    data class GestureDetected(val gesture: RecognizedGesture) : RobotEvent
    data class ListeningChanged(val isListening: Boolean) : RobotEvent
    data class ThinkingChanged(val isThinking: Boolean) : RobotEvent
    data class SpeakingChanged(val isSpeaking: Boolean, val text: String? = null) : RobotEvent
    data class SpeechRecognized(val text: String) : RobotEvent
    data class DialogueProcessed(val response: DialogueResponse) : RobotEvent
    data class SpeechError(val error: String) : RobotEvent
    data class SpeechModeInfo(val notice: String) : RobotEvent
    data class AudioLevelChanged(val level: Float) : RobotEvent
    data object WakeUp : RobotEvent
    data class SetExpression(val expression: FaceExpression, val durationMs: Long? = null) : RobotEvent
    data class SetQuietMode(val enabled: Boolean) : RobotEvent
    data class SetAutonomousEnabled(val enabled: Boolean) : RobotEvent
}

class RobotBrain(context: Context? = null) {
    private val _state = MutableStateFlow(RobotState())
    val state: StateFlow<RobotState> = _state.asStateFlow()

    val expressionController = ExpressionController()
    val soundEffects = SoundEffects()
    val personalityEngine = PersonalityEngine(context)
    val attentionManager = AttentionManager()

    var behaviorScheduler: BehaviorScheduler

    private val events = Channel<RobotEvent>(Channel.BUFFERED)

    init {
        soundEffects.isQuietMode = personalityEngine.drives.value.isQuietMode
        _state.update {
            it.copy(
                isQuietMode = personalityEngine.drives.value.isQuietMode,
                isAutonomousEnabled = personalityEngine.drives.value.isAutonomousEnabled,
                energy = personalityEngine.drives.value.energy,
                curiosityLevel = personalityEngine.drives.value.curiosityLevel
            )
        }

        behaviorScheduler = BehaviorScheduler(
            personalityEngine = personalityEngine,
            attentionManager = attentionManager,
            soundEffects = soundEffects,
            onTriggerExpression = { expr, duration ->
                setExpression(expr, duration)
            },
            onTriggerSleep = {
                _state.update { it.copy(isSleeping = true, isHappy = false, gazeX = 0f, gazeY = 0f) }
            },
            onGazeShift = { x, y ->
                if (!_state.value.faceDetected && !_state.value.isSleeping && _state.value.attentionTarget == AttentionTargetType.NONE) {
                    _state.update { it.copy(gazeX = x, gazeY = y) }
                }
            }
        )
    }

    fun send(event: RobotEvent) {
        events.trySend(event)
    }

    fun setExpression(expression: FaceExpression, durationMs: Long? = null) {
        send(RobotEvent.SetExpression(expression, durationMs))
    }

    suspend fun run() {
        coroutineScope {
            // Blinking loop (only when awake)
            launch {
                while (true) {
                    delay(3000)
                    if (!_state.value.isSleeping) {
                        _state.update { it.copy(isBlinking = true) }
                        delay(150)
                        _state.update { it.copy(isBlinking = false) }
                    }
                }
            }

            // Expression controller bridge
            launch {
                expressionController.currentExpression.collect { expr ->
                    _state.update { it.copy(currentExpression = expr) }
                }
            }

            // Personality drives bridge
            launch {
                personalityEngine.drives.collect { drives ->
                    soundEffects.isQuietMode = drives.isQuietMode
                    _state.update {
                        it.copy(
                            energy = drives.energy,
                            curiosityLevel = drives.curiosityLevel,
                            isQuietMode = drives.isQuietMode,
                            isAutonomousEnabled = drives.isAutonomousEnabled
                        )
                    }
                }
            }

            // Attention manager state bridge
            launch {
                attentionManager.state.collect { attention ->
                    _state.update {
                        it.copy(
                            attentionTarget = attention.targetType,
                            faceDetected = attention.isUserPresent,
                            gazeX = if (it.isSleeping) 0f else attention.targetGazeX,
                            gazeY = if (it.isSleeping) 0f else attention.targetGazeY
                        )
                    }
                }
            }

            // Start autonomous behavior scheduler
            behaviorScheduler.start(
                scope = this,
                isSleepingProvider = { _state.value.isSleeping },
                isBusyProvider = { _state.value.isSpeaking || _state.value.isListening || _state.value.isThinking }
            )

            // Event processing loop
            launch {
                var happyJob: Job? = null
                var errorClearJob: Job? = null
                var speechBubbleClearJob: Job? = null

                for (event in events) {
                    if (event !is RobotEvent.FaceDetected && event !is RobotEvent.FaceLost && event !is RobotEvent.AudioLevelChanged) {
                        try {
                            android.util.Log.d("RobotBrain", "Processing event: $event")
                        } catch (_: Exception) {}
                    }
                    when (event) {
                        is RobotEvent.SetExpression -> {
                            expressionController.setExpression(event.expression, event.durationMs)
                        }

                        is RobotEvent.SetQuietMode -> {
                            personalityEngine.setQuietMode(event.enabled)
                        }

                        is RobotEvent.SetAutonomousEnabled -> {
                            personalityEngine.setAutonomousEnabled(event.enabled)
                        }

                        RobotEvent.Petted -> {
                            personalityEngine.recordInteraction()
                            if (_state.value.isSleeping) {
                                _state.update { it.copy(isSleeping = false) }
                                soundEffects.play(SoundEffectType.WAKE_UP)
                            } else {
                                soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                            }
                            happyJob?.cancel()
                            _state.update { it.copy(isHappy = true) }
                            expressionController.setExpression(FaceExpression.HAPPY, 2200L)
                            happyJob = launch {
                                delay(2200)
                                _state.update { it.copy(isHappy = false) }
                            }
                        }

                        RobotEvent.WakeUp -> {
                            personalityEngine.recordInteraction()
                            _state.update { it.copy(isSleeping = false) }
                            soundEffects.play(SoundEffectType.WAKE_UP)
                            happyJob?.cancel()
                            _state.update { it.copy(isHappy = true) }
                            expressionController.setExpression(FaceExpression.HAPPY, 2000L)
                            happyJob = launch {
                                delay(2000)
                                _state.update { it.copy(isHappy = false) }
                            }
                        }

                        is RobotEvent.FaceDetected -> {
                            personalityEngine.recordFaceSeen()
                            if (!_state.value.isSleeping) {
                                val shouldGreet = attentionManager.onFaceDetected(event.x, event.y)
                                if (shouldGreet && !_state.value.isSpeaking && !_state.value.isListening) {
                                    // Spontaneous greeting upon user returning after absence
                                    soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                                    expressionController.setExpression(FaceExpression.HAPPY, 2200L)
                                }
                            }
                        }

                        RobotEvent.FaceLost -> {
                            attentionManager.onFaceLost()
                        }

                        is RobotEvent.GestureDetected -> {
                            _state.update {
                                it.copy(
                                    lastGesture = event.gesture.gesture,
                                    gestureConfidence = event.gesture.confidence
                                )
                            }

                            // Only react if awake and not actively speaking/listening/thinking
                            if (!_state.value.isSleeping && !_state.value.isSpeaking && !_state.value.isListening && !_state.value.isThinking) {
                                personalityEngine.recordInteraction()
                                attentionManager.onHandGesture(event.gesture.handX, event.gesture.handY, 2200L)

                                when (event.gesture.gesture) {
                                    HandGesture.WAVE -> {
                                        soundEffects.play(SoundEffectType.WAVE_GREETING)
                                        expressionController.setExpression(FaceExpression.HAPPY, 3000L)
                                    }
                                    HandGesture.THUMB_UP -> {
                                        soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                                        expressionController.setExpression(FaceExpression.HAPPY, 2500L)
                                    }
                                    HandGesture.THUMB_DOWN -> {
                                        soundEffects.play(SoundEffectType.CONFUSED_BURP)
                                        expressionController.setExpression(FaceExpression.CONFUSED, 2500L)
                                    }
                                    HandGesture.VICTORY -> {
                                        soundEffects.play(SoundEffectType.PLAYFUL_TRILL)
                                        expressionController.setExpression(FaceExpression.PLAYFUL, 2500L)
                                    }
                                    HandGesture.OPEN_PALM -> {
                                        soundEffects.play(SoundEffectType.CURIOUS_SQUEAK)
                                        expressionController.setExpression(FaceExpression.CURIOUS, 2500L)
                                    }
                                    HandGesture.APPROACHING -> {
                                        expressionController.setExpression(FaceExpression.CURIOUS, 2000L)
                                    }
                                    HandGesture.NONE -> {}
                                }
                            }
                        }

                        is RobotEvent.ListeningChanged -> {
                            personalityEngine.recordInteraction()
                            attentionManager.setInteractionTarget(
                                if (event.isListening) AttentionTargetType.USER_INTERACTION else AttentionTargetType.NONE
                            )
                            _state.update {
                                it.copy(
                                    isListening = event.isListening,
                                    speechError = if (event.isListening) null else it.speechError
                                )
                            }
                        }

                        is RobotEvent.ThinkingChanged -> {
                            _state.update {
                                it.copy(isThinking = event.isThinking)
                            }
                        }

                        is RobotEvent.SpeakingChanged -> {
                            if (event.isSpeaking) {
                                speechBubbleClearJob?.cancel()
                                _state.update {
                                    it.copy(
                                        isSpeaking = true,
                                        spokenText = event.text ?: it.spokenText
                                    )
                                }
                            } else {
                                _state.update { it.copy(isSpeaking = false) }
                                speechBubbleClearJob?.cancel()
                                speechBubbleClearJob = launch {
                                    delay(2500)
                                    _state.update { it.copy(spokenText = null) }
                                }
                            }
                        }

                        is RobotEvent.SpeechRecognized -> {
                            personalityEngine.recordInteraction()
                            _state.update {
                                it.copy(
                                    recognizedText = event.text,
                                    speechError = null
                                )
                            }
                        }

                        is RobotEvent.DialogueProcessed -> {
                            when (event.response.action) {
                                RobotAction.Sleep -> {
                                    _state.update {
                                        it.copy(
                                            isSleeping = true,
                                            isHappy = false,
                                            gazeX = 0f,
                                            gazeY = 0f
                                        )
                                    }
                                    soundEffects.play(SoundEffectType.SLEEP_SIGH)
                                    expressionController.setExpression(FaceExpression.SLEEPY, 3000L)
                                }
                                RobotAction.WakeUp -> {
                                    _state.update { it.copy(isSleeping = false) }
                                    soundEffects.play(SoundEffectType.WAKE_UP)
                                    happyJob?.cancel()
                                    _state.update { it.copy(isHappy = true) }
                                    expressionController.setExpression(FaceExpression.HAPPY, 2000L)
                                    happyJob = launch {
                                        delay(2000)
                                        _state.update { it.copy(isHappy = false) }
                                    }
                                }
                                RobotAction.HappyBounce -> {
                                    soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                                    happyJob?.cancel()
                                    _state.update { it.copy(isHappy = true) }
                                    expressionController.setExpression(FaceExpression.HAPPY, 2000L)
                                    happyJob = launch {
                                        delay(2000)
                                        _state.update { it.copy(isHappy = false) }
                                    }
                                }
                                null -> {
                                    when (event.response.emotion) {
                                        RobotEmotion.HAPPY -> {
                                            happyJob?.cancel()
                                            _state.update { it.copy(isHappy = true) }
                                            expressionController.setExpression(FaceExpression.HAPPY, 2500L)
                                            happyJob = launch {
                                                delay(2500)
                                                _state.update { it.copy(isHappy = false) }
                                            }
                                        }
                                        RobotEmotion.CURIOUS -> {
                                            expressionController.setExpression(FaceExpression.CURIOUS, 3000L)
                                        }
                                        RobotEmotion.SLEEPY -> {
                                            expressionController.setExpression(FaceExpression.SLEEPY, 3500L)
                                        }
                                        RobotEmotion.NORMAL -> {
                                            expressionController.resetToNeutral()
                                        }
                                    }
                                }
                            }
                        }

                        is RobotEvent.SpeechError -> {
                            _state.update { it.copy(speechError = event.error) }
                            errorClearJob?.cancel()
                            errorClearJob = launch {
                                delay(3000)
                                _state.update { it.copy(speechError = null) }
                            }
                        }

                        is RobotEvent.SpeechModeInfo -> {
                            _state.update { it.copy(speechModeNotice = event.notice) }
                        }

                        is RobotEvent.AudioLevelChanged -> {
                            _state.update { it.copy(audioLevel = event.level) }
                        }
                    }
                }
            }
        }
    }
}
