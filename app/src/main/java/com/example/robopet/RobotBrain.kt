package com.example.robopet

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    val currentExpression: FaceExpression = FaceExpression.NEUTRAL
)

sealed interface RobotEvent {
    data object Petted : RobotEvent
    data class FaceDetected(val x: Float, val y: Float) : RobotEvent
    data object FaceLost : RobotEvent
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
}

class RobotBrain {
    private val _state = MutableStateFlow(RobotState())
    val state: StateFlow<RobotState> = _state.asStateFlow()

    val expressionController = ExpressionController()

    private val events = Channel<RobotEvent>(Channel.BUFFERED)

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

            // Gaze wandering loop (only when awake and no face detected)
            launch {
                while (true) {
                    delay(Random.nextLong(1500, 4000))
                    if (!_state.value.faceDetected && !_state.value.isSleeping) {
                        _state.update {
                            it.copy(
                                gazeX = Random.nextInt(-12, 13).toFloat(),
                                gazeY = Random.nextInt(-10, 11).toFloat()
                            )
                        }
                    }
                    delay(1000)
                    if (!_state.value.faceDetected && !_state.value.isSleeping) {
                        _state.update { it.copy(gazeX = 0f, gazeY = 0f) }
                    }
                }
            }

            // Expression controller bridge
            launch {
                expressionController.currentExpression.collect { expr ->
                    _state.update { it.copy(currentExpression = expr) }
                }
            }

            // Event processing loop
            launch {
                var happyJob: Job? = null
                var errorClearJob: Job? = null
                var speechBubbleClearJob: Job? = null

                for (event in events) {
                    if (event !is RobotEvent.FaceDetected && event !is RobotEvent.FaceLost && event !is RobotEvent.AudioLevelChanged) {
                        android.util.Log.d("RobotBrain", "Processing event: $event")
                    }
                    when (event) {
                        is RobotEvent.SetExpression -> {
                            expressionController.setExpression(event.expression, event.durationMs)
                        }

                        RobotEvent.Petted -> {
                            if (_state.value.isSleeping) {
                                // Petting wakes the robot up!
                                _state.update { it.copy(isSleeping = false) }
                            }
                            happyJob?.cancel()
                            _state.update { it.copy(isHappy = true) }
                            expressionController.setExpression(FaceExpression.HAPPY, 1800L)
                            happyJob = launch {
                                delay(1800)
                                _state.update { it.copy(isHappy = false) }
                            }
                        }

                        RobotEvent.WakeUp -> {
                            _state.update { it.copy(isSleeping = false) }
                            happyJob?.cancel()
                            _state.update { it.copy(isHappy = true) }
                            expressionController.setExpression(FaceExpression.HAPPY, 2000L)
                            happyJob = launch {
                                delay(2000)
                                _state.update { it.copy(isHappy = false) }
                            }
                        }

                        is RobotEvent.FaceDetected -> {
                            if (!_state.value.isSleeping) {
                                val gazeX = ((event.x - 0.5f) * 24f).coerceIn(-12f, 12f)
                                val gazeY = ((event.y - 0.5f) * 20f).coerceIn(-10f, 10f)
                                _state.update {
                                    it.copy(
                                        faceDetected = true,
                                        gazeX = gazeX,
                                        gazeY = gazeY
                                    )
                                }
                            } else {
                                _state.update { it.copy(faceDetected = true) }
                            }
                        }

                        RobotEvent.FaceLost -> {
                            _state.update {
                                it.copy(
                                    faceDetected = false,
                                    gazeX = if (it.isSleeping) 0f else it.gazeX,
                                    gazeY = if (it.isSleeping) 0f else it.gazeY
                                )
                            }
                        }

                        is RobotEvent.ListeningChanged -> {
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
                                    expressionController.setExpression(FaceExpression.SLEEPY, 3000L)
                                }
                                RobotAction.WakeUp -> {
                                    _state.update { it.copy(isSleeping = false) }
                                    happyJob?.cancel()
                                    _state.update { it.copy(isHappy = true) }
                                    expressionController.setExpression(FaceExpression.HAPPY, 2000L)
                                    happyJob = launch {
                                        delay(2000)
                                        _state.update { it.copy(isHappy = false) }
                                    }
                                }
                                RobotAction.HappyBounce -> {
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
