package com.example.robopet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PomodoroMode(val displayName: String) {
    INACTIVE("Inactive"),
    FOCUS("Focus Session"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}

data class PomodoroState(
    val mode: PomodoroMode = PomodoroMode.INACTIVE,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val isRunning: Boolean = false,
    val completedSessions: Int = 0
) {
    val formattedTime: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val progress: Float
        get() = if (totalSeconds > 0) {
            1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
        } else 0f
}

class PomodoroManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    var onSessionCompleted: ((completedMode: PomodoroMode) -> Unit)? = null
) {
    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private var timerJob: Job? = null

    fun startFocus(durationMinutes: Int = 25) {
        val totalSecs = (durationMinutes * 60).coerceAtLeast(10)
        _state.value = _state.value.copy(
            mode = PomodoroMode.FOCUS,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            isRunning = true
        )
        startTicker()
    }

    fun startBreak(durationMinutes: Int = 5, isLongBreak: Boolean = false) {
        val totalSecs = (durationMinutes * 60).coerceAtLeast(10)
        _state.value = _state.value.copy(
            mode = if (isLongBreak) PomodoroMode.LONG_BREAK else PomodoroMode.SHORT_BREAK,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            isRunning = true
        )
        startTicker()
    }

    fun pause() {
        timerJob?.cancel()
        _state.value = _state.value.copy(isRunning = false)
    }

    fun resume() {
        if (_state.value.remainingSeconds > 0 && _state.value.mode != PomodoroMode.INACTIVE) {
            _state.value = _state.value.copy(isRunning = true)
            startTicker()
        }
    }

    fun stop() {
        timerJob?.cancel()
        _state.value = _state.value.copy(
            mode = PomodoroMode.INACTIVE,
            remainingSeconds = 0,
            totalSeconds = 0,
            isRunning = false
        )
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_state.value.isRunning && _state.value.remainingSeconds > 0) {
                delay(1000L)
                val newRemaining = _state.value.remainingSeconds - 1
                if (newRemaining <= 0) {
                    val finishedMode = _state.value.mode
                    val newCompleted = if (finishedMode == PomodoroMode.FOCUS) {
                        _state.value.completedSessions + 1
                    } else {
                        _state.value.completedSessions
                    }

                    _state.value = _state.value.copy(
                        mode = PomodoroMode.INACTIVE,
                        remainingSeconds = 0,
                        isRunning = false,
                        completedSessions = newCompleted
                    )
                    onSessionCompleted?.invoke(finishedMode)
                    break
                } else {
                    _state.value = _state.value.copy(remainingSeconds = newRemaining)
                }
            }
        }
    }
}
