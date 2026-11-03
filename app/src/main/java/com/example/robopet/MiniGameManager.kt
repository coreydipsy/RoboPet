package com.example.robopet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RpsChoice(val symbol: String, val displayName: String) {
    ROCK("✊", "Rock"),
    PAPER("✋", "Paper"),
    SCISSORS("✌️", "Scissors")
}

enum class RpsOutcome {
    NONE,
    USER_WINS,
    ROBOT_WINS,
    TIE,
    NO_GESTURE_DETECTED
}

enum class RpsPhase {
    INACTIVE,
    COUNTDOWN_3,
    COUNTDOWN_2,
    COUNTDOWN_1,
    REVEAL,
    FINISHED
}

data class MiniGameState(
    val isRpsActive: Boolean = false,
    val rpsPhase: RpsPhase = RpsPhase.INACTIVE,
    val robotChoice: RpsChoice? = null,
    val userChoice: RpsChoice? = null,
    val rpsOutcome: RpsOutcome = RpsOutcome.NONE,
    val isPeekABooReady: Boolean = false,
    val isFollowingFinger: Boolean = false,
    val targetFingerX: Float = 0.5f,
    val targetFingerY: Float = 0.5f
)

class MiniGameManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    var onSpeak: ((String) -> Unit)? = null,
    var onPlaySound: ((SoundEffectType) -> Unit)? = null,
    var onSetExpression: ((FaceExpression, Long?) -> Unit)? = null,
    var onAwardRelationship: ((Int) -> Unit)? = null
) {
    private val _state = MutableStateFlow(MiniGameState())
    val state: StateFlow<MiniGameState> = _state.asStateFlow()

    private var rpsJob: Job? = null
    private var peekABooFaceLostTime: Long = 0L
    private var peekABooArmed: Boolean = false

    fun startRockPaperScissors() {
        rpsJob?.cancel()
        _state.value = _state.value.copy(
            isRpsActive = true,
            rpsPhase = RpsPhase.COUNTDOWN_3,
            robotChoice = null,
            userChoice = null,
            rpsOutcome = RpsOutcome.NONE
        )

        rpsJob = scope.launch {
            onSetExpression?.invoke(FaceExpression.PLAYFUL, 4000L)
            onPlaySound?.invoke(SoundEffectType.PLAYFUL_TRILL)
            onSpeak?.invoke("Rock, paper, scissors! Ready? Three...")
            delay(1200L)

            _state.value = _state.value.copy(rpsPhase = RpsPhase.COUNTDOWN_2)
            onSpeak?.invoke("Two...")
            delay(1000L)

            _state.value = _state.value.copy(rpsPhase = RpsPhase.COUNTDOWN_1)
            onSpeak?.invoke("One... Shoot!")
            delay(1000L)

            val chosen = RpsChoice.entries[Random.nextInt(RpsChoice.entries.size)]
            _state.value = _state.value.copy(
                rpsPhase = RpsPhase.REVEAL,
                robotChoice = chosen
            )

            // If user already captured gesture during COUNTDOWN_1, evaluate immediately!
            if (_state.value.userChoice != null) {
                evaluateRpsResult()
            } else {
                // Wait up to 3.5s window to capture user gesture or tap
                delay(3500L)
                if (_state.value.rpsPhase == RpsPhase.REVEAL) {
                    evaluateRpsResult()
                }
            }
        }
    }

    fun onGestureSeenDuringRps(gesture: HandGesture) {
        if (!_state.value.isRpsActive) return
        if (_state.value.rpsPhase != RpsPhase.COUNTDOWN_1 && _state.value.rpsPhase != RpsPhase.REVEAL) return
        if (_state.value.userChoice != null) return // Already captured

        val choice = when (gesture) {
            HandGesture.CLOSED_FIST -> RpsChoice.ROCK
            HandGesture.OPEN_PALM -> RpsChoice.PAPER
            HandGesture.VICTORY -> RpsChoice.SCISSORS
            else -> null
        }

        if (choice != null) {
            _state.value = _state.value.copy(userChoice = choice)
            if (_state.value.rpsPhase == RpsPhase.REVEAL) {
                evaluateRpsResult()
            }
        }
    }

    fun onUserChoiceSelected(choice: RpsChoice) {
        if (!_state.value.isRpsActive) return
        if (_state.value.rpsPhase == RpsPhase.FINISHED) return
        _state.value = _state.value.copy(userChoice = choice)
        if (_state.value.robotChoice == null) {
            _state.value = _state.value.copy(robotChoice = RpsChoice.entries[Random.nextInt(RpsChoice.entries.size)])
        }
        evaluateRpsResult()
    }

    private fun evaluateRpsResult() {
        val robot = _state.value.robotChoice ?: return
        val user = _state.value.userChoice

        val outcome = when {
            user == null -> RpsOutcome.NO_GESTURE_DETECTED
            user == robot -> RpsOutcome.TIE
            (user == RpsChoice.ROCK && robot == RpsChoice.SCISSORS) ||
                    (user == RpsChoice.PAPER && robot == RpsChoice.ROCK) ||
                    (user == RpsChoice.SCISSORS && robot == RpsChoice.PAPER) -> RpsOutcome.USER_WINS
            else -> RpsOutcome.ROBOT_WINS
        }

        _state.value = _state.value.copy(
            rpsPhase = RpsPhase.FINISHED,
            rpsOutcome = outcome
        )

        when (outcome) {
            RpsOutcome.USER_WINS -> {
                onSetExpression?.invoke(FaceExpression.SURPRISED, 3500L)
                onPlaySound?.invoke(SoundEffectType.HAPPY_CHIRP)
                onSpeak?.invoke("You chose ${user?.displayName} and I chose ${robot.displayName}! You win! Well played!")
                onAwardRelationship?.invoke(10)
            }
            RpsOutcome.ROBOT_WINS -> {
                onSetExpression?.invoke(FaceExpression.PLAYFUL, 3500L)
                onPlaySound?.invoke(SoundEffectType.PLAYFUL_TRILL)
                onSpeak?.invoke("I chose ${robot.displayName} and you chose ${user?.displayName}! I win! Good try!")
                onAwardRelationship?.invoke(4)
            }
            RpsOutcome.TIE -> {
                onSetExpression?.invoke(FaceExpression.CURIOUS, 3500L)
                onPlaySound?.invoke(SoundEffectType.CURIOUS_SQUEAK)
                onSpeak?.invoke("We both picked ${robot.displayName}! It's a tie!")
                onAwardRelationship?.invoke(5)
            }
            RpsOutcome.NO_GESTURE_DETECTED -> {
                onSetExpression?.invoke(FaceExpression.CURIOUS, 2500L)
                onSpeak?.invoke("I chose ${robot.displayName}! I couldn't quite see your hand. Let's play again soon!")
            }
            RpsOutcome.NONE -> {}
        }

        scope.launch {
            delay(5000L)
            if (_state.value.rpsPhase == RpsPhase.FINISHED) {
                _state.value = _state.value.copy(
                    isRpsActive = false,
                    rpsPhase = RpsPhase.INACTIVE,
                    robotChoice = null,
                    userChoice = null,
                    rpsOutcome = RpsOutcome.NONE
                )
            }
        }
    }

    fun stopRps() {
        rpsJob?.cancel()
        _state.value = _state.value.copy(
            isRpsActive = false,
            rpsPhase = RpsPhase.INACTIVE,
            robotChoice = null,
            userChoice = null,
            rpsOutcome = RpsOutcome.NONE
        )
    }

    // Peek-a-boo logic
    fun onFaceLostWithHandCovering(now: Long = System.currentTimeMillis()) {
        peekABooArmed = true
        peekABooFaceLostTime = now
        onSetExpression?.invoke(FaceExpression.CURIOUS, 3000L)
    }

    fun onFaceReappearedAfterCovering(now: Long = System.currentTimeMillis()): Boolean {
        if (!peekABooArmed) return false
        val elapsed = now - peekABooFaceLostTime
        peekABooArmed = false

        if (elapsed in 400L..4500L) {
            onSetExpression?.invoke(FaceExpression.HAPPY, 3500L)
            onPlaySound?.invoke(SoundEffectType.HAPPY_CHIRP)
            onSpeak?.invoke("Peek-a-boo! I see you!")
            onAwardRelationship?.invoke(6)
            return true
        }
        return false
    }

    // Follow My Finger logic
    fun onFingerDetected(handX: Float, handY: Float) {
        _state.value = _state.value.copy(
            isFollowingFinger = true,
            targetFingerX = handX,
            targetFingerY = handY
        )
    }

    fun onFingerLost() {
        if (_state.value.isFollowingFinger) {
            _state.value = _state.value.copy(isFollowingFinger = false)
        }
    }
}
