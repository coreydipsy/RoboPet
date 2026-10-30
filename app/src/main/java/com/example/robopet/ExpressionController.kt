package com.example.robopet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages high-level facial expressions and temporary expression timeouts.
 * Receives instructions (HAPPY, CURIOUS, SURPRISED, etc.) and exposes the active expression.
 */
class ExpressionController(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        const val TAG = "ExpressionController"
        const val DEFAULT_EXPRESSION_DURATION_MS = 2500L
    }

    private val _currentExpression = MutableStateFlow(FaceExpression.NEUTRAL)
    val currentExpression: StateFlow<FaceExpression> = _currentExpression.asStateFlow()

    private var timeoutJob: Job? = null

    /**
     * Sets the active facial expression.
     * If durationMs is provided, automatically returns to NEUTRAL after that time.
     * Passing null duration keeps the expression until another expression is requested.
     */
    fun setExpression(expression: FaceExpression, durationMs: Long? = null) {
        timeoutJob?.cancel()
        _currentExpression.value = expression
        try {
            Log.d(TAG, "Expression changed to: ${expression.name} (duration=${durationMs ?: "indefinite"}ms)")
        } catch (_: Exception) {}

        if (durationMs != null && durationMs > 0L && expression != FaceExpression.NEUTRAL) {
            timeoutJob = scope.launch {
                delay(durationMs)
                if (_currentExpression.value == expression) {
                    _currentExpression.value = FaceExpression.NEUTRAL
                    try {
                        Log.d(TAG, "Expression auto-reverted to NEUTRAL")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Immediately resets the facial expression to NEUTRAL.
     */
    fun resetToNeutral() {
        timeoutJob?.cancel()
        _currentExpression.value = FaceExpression.NEUTRAL
    }
}
