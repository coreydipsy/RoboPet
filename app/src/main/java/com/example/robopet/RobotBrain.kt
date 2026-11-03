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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DockModeSetting {
    AUTO_ON_CHARGE,
    ALWAYS_ON,
    DISABLED
}

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
    val isAutonomousEnabled: Boolean = true,
    val isCharging: Boolean = false,
    val batteryLevel: Int = 100,
    val dockModeSetting: DockModeSetting = DockModeSetting.AUTO_ON_CHARGE,
    val isDockClockVisible: Boolean = false,
    val isNightDimmed: Boolean = false,
    val currentTimeString: String = "",
    val currentDateString: String = "",
    val relationshipStage: String = "New Acquaintance",
    val relationshipScore: Int = 120,
    val relationshipLevel: Int = 1,
    val spontaneousSpeechEnabled: Boolean = true,
    val spontaneousSpeechRemainingToday: Int = 4,
    val isWakeWordListening: Boolean = false,
    val isWakeWordEnabled: Boolean = true,
    val pomodoroState: PomodoroState = PomodoroState(),
    val miniGameState: MiniGameState = MiniGameState()
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
    data class BatteryChanged(val isCharging: Boolean, val level: Int) : RobotEvent
    data class SetDockModeSetting(val setting: DockModeSetting) : RobotEvent
    data class SetSpontaneousSpeechEnabled(val enabled: Boolean) : RobotEvent
    data object ResetDailySpontaneousBudget : RobotEvent
    data object ResetRelationship : RobotEvent
    data object TestSpontaneousSpeech : RobotEvent
    data class WakeWordStateChanged(val isListening: Boolean) : RobotEvent
    data class SetWakeWordEnabled(val enabled: Boolean) : RobotEvent
    data class WakeWordTriggered(val phrase: String, val command: String?) : RobotEvent
    data class StartPomodoro(val minutes: Int = 25) : RobotEvent
    data class StartBreak(val minutes: Int = 5) : RobotEvent
    data object PausePomodoro : RobotEvent
    data object ResumePomodoro : RobotEvent
    data object StopPomodoro : RobotEvent
    data object StartRockPaperScissors : RobotEvent
    data object StopRockPaperScissors : RobotEvent
    data class SelectRpsChoice(val choice: RpsChoice) : RobotEvent
}

class RobotBrain(
    context: Context? = null,
    private val onSpontaneousSpeak: ((String) -> Unit)? = null
) {
    private val _state = MutableStateFlow(RobotState())
    val state: StateFlow<RobotState> = _state.asStateFlow()

    val expressionController = ExpressionController()
    val soundEffects = SoundEffects()
    val personalityEngine = PersonalityEngine(context)
    val attentionManager = AttentionManager()

    val relationshipManager: RelationshipManager? = context?.let { RelationshipManager(it, personalityEngine) }
    val spontaneousDialogueManager: SpontaneousDialogueManager? = context?.let {
        SpontaneousDialogueManager(it) { text ->
            onSpontaneousSpeak?.invoke(text)
        }
    }

    val pomodoroManager = PomodoroManager(
        onSessionCompleted = { finishedMode ->
            if (finishedMode == PomodoroMode.FOCUS) {
                soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                expressionController.setExpression(FaceExpression.HAPPY, 4000L)
                onSpontaneousSpeak?.invoke("Focus block completed! Great work! Time for a short break.")
                relationshipManager?.recordPoints(15)
            } else {
                soundEffects.play(SoundEffectType.PLAYFUL_TRILL)
                expressionController.setExpression(FaceExpression.PLAYFUL, 3500L)
                onSpontaneousSpeak?.invoke("Break time is up! Ready to get back into focus mode?")
            }
        }
    )

    val miniGameManager = MiniGameManager(
        onSpeak = { text -> onSpontaneousSpeak?.invoke(text) },
        onPlaySound = { sound -> soundEffects.play(sound) },
        onSetExpression = { expr, dur -> expressionController.setExpression(expr, dur) },
        onAwardRelationship = { points -> relationshipManager?.recordPoints(points) }
    )

    var behaviorScheduler: BehaviorScheduler

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    private val events = Channel<RobotEvent>(Channel.BUFFERED)

    init {
        soundEffects.isQuietMode = personalityEngine.drives.value.isQuietMode
        val initialDrives = personalityEngine.drives.value
        _state.update {
            it.copy(
                isQuietMode = initialDrives.isQuietMode,
                isAutonomousEnabled = initialDrives.isAutonomousEnabled,
                energy = initialDrives.energy,
                curiosityLevel = initialDrives.curiosityLevel,
                currentTimeString = timeFormat.format(Date()),
                currentDateString = dateFormat.format(Date())
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
            // Clock & Dock mode ticker loop (every second)
            launch {
                while (true) {
                    delay(1000)
                    val now = Date()
                    val timeStr = timeFormat.format(now)
                    val dateStr = dateFormat.format(now)

                    val cal = Calendar.getInstance()
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val isNight = hour >= 22 || hour < 7

                    _state.update { current ->
                        val isDockVisible = when (current.dockModeSetting) {
                            DockModeSetting.ALWAYS_ON -> true
                            DockModeSetting.AUTO_ON_CHARGE -> current.isCharging
                            DockModeSetting.DISABLED -> false
                        }
                        current.copy(
                            currentTimeString = timeStr,
                            currentDateString = dateStr,
                            isDockClockVisible = isDockVisible,
                            isNightDimmed = isDockVisible && isNight
                        )
                    }
                }
            }

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

            // Relationship stats bridge
            launch {
                relationshipManager?.stats?.collect { rel ->
                    _state.update {
                        it.copy(
                            relationshipScore = rel.score,
                            relationshipStage = rel.stage,
                            relationshipLevel = rel.stageLevel
                        )
                    }
                }
            }

            // Spontaneous speech budget bridge
            launch {
                spontaneousDialogueManager?.state?.collect { sp ->
                    _state.update {
                        it.copy(
                            spontaneousSpeechEnabled = sp.isEnabled,
                            spontaneousSpeechRemainingToday = sp.remainingToday
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

            // Pomodoro state bridge
            launch {
                pomodoroManager.state.collect { pomo ->
                    _state.update { it.copy(pomodoroState = pomo) }
                }
            }

            // Mini-game state bridge
            launch {
                miniGameManager.state.collect { mg ->
                    _state.update { it.copy(miniGameState = mg) }
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
                    if (event !is RobotEvent.FaceDetected && event !is RobotEvent.FaceLost && event !is RobotEvent.AudioLevelChanged && event !is RobotEvent.BatteryChanged) {
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

                        is RobotEvent.BatteryChanged -> {
                            _state.update {
                                it.copy(
                                    isCharging = event.isCharging,
                                    batteryLevel = event.level
                                )
                            }
                        }

                        is RobotEvent.SetDockModeSetting -> {
                            _state.update { it.copy(dockModeSetting = event.setting) }
                        }

                        is RobotEvent.SetSpontaneousSpeechEnabled -> {
                            spontaneousDialogueManager?.setEnabled(event.enabled)
                        }

                        RobotEvent.ResetDailySpontaneousBudget -> {
                            spontaneousDialogueManager?.resetDailyBudget()
                        }

                        RobotEvent.ResetRelationship -> {
                            relationshipManager?.resetRelationship()
                        }

                        RobotEvent.TestSpontaneousSpeech -> {
                            spontaneousDialogueManager?.testSpeak()
                        }

                        RobotEvent.Petted -> {
                            personalityEngine.recordInteraction()
                            relationshipManager?.recordPet()
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
                            miniGameManager.onFaceReappearedAfterCovering()
                            personalityEngine.recordFaceSeen()
                            if (!_state.value.isSleeping) {
                                val shouldGreet = attentionManager.onFaceDetected(event.x, event.y)
                                if (shouldGreet && !_state.value.isSpeaking && !_state.value.isListening && _state.value.pomodoroState.mode != PomodoroMode.FOCUS) {
                                    // Spontaneous greeting upon user returning after absence (only when not in Focus)
                                    soundEffects.play(SoundEffectType.HAPPY_CHIRP)
                                    expressionController.setExpression(FaceExpression.HAPPY, 2200L)

                                    // Spontaneous speech check on return
                                    spontaneousDialogueManager?.onUserReturnAfterAbsence(
                                        absenceDurationMs = 1200000L,
                                        isQuietMode = _state.value.isQuietMode,
                                        isSleeping = _state.value.isSleeping
                                    )
                                }
                            }
                        }

                        RobotEvent.FaceLost -> {
                            if (_state.value.lastGesture == HandGesture.APPROACHING || _state.value.lastGesture == HandGesture.OPEN_PALM) {
                                miniGameManager.onFaceLostWithHandCovering()
                            }
                            attentionManager.onFaceLost()
                        }

                        is RobotEvent.GestureDetected -> {
                            relationshipManager?.recordGesture()
                            _state.update {
                                it.copy(
                                    lastGesture = event.gesture.gesture,
                                    gestureConfidence = event.gesture.confidence
                                )
                            }

                            // Route to RPS if game is active
                            if (miniGameManager.state.value.isRpsActive) {
                                miniGameManager.onGestureSeenDuringRps(event.gesture.gesture)
                            }

                            // Follow my finger
                            if (event.gesture.gesture == HandGesture.POINTING_UP) {
                                miniGameManager.onFingerDetected(event.gesture.handX, event.gesture.handY)
                            } else {
                                miniGameManager.onFingerLost()
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
                                    HandGesture.CLOSED_FIST -> {
                                        soundEffects.play(SoundEffectType.CURIOUS_SQUEAK)
                                        expressionController.setExpression(FaceExpression.CURIOUS, 2000L)
                                    }
                                    HandGesture.POINTING_UP -> {
                                        expressionController.setExpression(FaceExpression.CURIOUS, 2000L)
                                    }
                                    HandGesture.APPROACHING -> {
                                        expressionController.setExpression(FaceExpression.CURIOUS, 2000L)
                                    }
                                    HandGesture.NONE -> {}
                                }
                            }
                        }

                        is RobotEvent.WakeWordStateChanged -> {
                            _state.update { it.copy(isWakeWordListening = event.isListening) }
                        }

                        is RobotEvent.SetWakeWordEnabled -> {
                            _state.update { it.copy(isWakeWordEnabled = event.enabled) }
                        }

                        is RobotEvent.WakeWordTriggered -> {
                            if (_state.value.isSleeping) {
                                _state.update { it.copy(isSleeping = false) }
                                soundEffects.play(SoundEffectType.WAKE_UP)
                            } else {
                                soundEffects.play(SoundEffectType.CURIOUS_SQUEAK)
                            }
                            expressionController.setExpression(FaceExpression.PLAYFUL, 2000L)
                        }

                        is RobotEvent.StartPomodoro -> {
                            pomodoroManager.startFocus(event.minutes)
                            expressionController.setExpression(FaceExpression.CURIOUS, 2500L)
                            onSpontaneousSpeak?.invoke("Focus timer started for ${event.minutes} minutes! Let's get to work.")
                        }

                        is RobotEvent.StartBreak -> {
                            pomodoroManager.startBreak(event.minutes)
                            expressionController.setExpression(FaceExpression.HAPPY, 2500L)
                            onSpontaneousSpeak?.invoke("Break timer set for ${event.minutes} minutes. Relax and stretch!")
                        }

                        RobotEvent.PausePomodoro -> pomodoroManager.pause()
                        RobotEvent.ResumePomodoro -> pomodoroManager.resume()
                        RobotEvent.StopPomodoro -> {
                            pomodoroManager.stop()
                            onSpontaneousSpeak?.invoke("Focus session stopped.")
                        }

                        RobotEvent.StartRockPaperScissors -> {
                            miniGameManager.startRockPaperScissors()
                        }

                        RobotEvent.StopRockPaperScissors -> {
                            miniGameManager.stopRps()
                        }

                        is RobotEvent.SelectRpsChoice -> {
                            miniGameManager.onUserChoiceSelected(event.choice)
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
                            relationshipManager?.recordChat()
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
