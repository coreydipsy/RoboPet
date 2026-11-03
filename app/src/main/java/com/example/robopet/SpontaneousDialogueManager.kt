package com.example.robopet

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class SpontaneousSpeechState(
    val isEnabled: Boolean = true,
    val maxDailyBudget: Int = 4,
    val usedToday: Int = 0,
    val lastSpokenTimeMs: Long = 0L
) {
    val remainingToday: Int
        get() = (maxDailyBudget - usedToday).coerceAtLeast(0)
}

class SpontaneousDialogueManager(
    context: Context,
    private val onSpeak: (String) -> Unit
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("robopet_spontaneous_dialogue", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SpontaneousSpeechState> = _state.asStateFlow()

    private var lastAttemptTime = 0L

    companion object {
        private const val MIN_COOLDOWN_MS = 2700000L // 45 minutes between spontaneous utterances
        private const val ABSENCE_THRESHOLD_MS = 900000L // 15 minutes absence
    }

    private fun loadState(): SpontaneousSpeechState {
        val todayStr = dateFormat.format(Date())
        val savedDate = prefs.getString("saved_date", "")
        val isEnabled = prefs.getBoolean("is_enabled", true)
        val maxBudget = prefs.getInt("max_budget", 4)

        val usedToday = if (savedDate == todayStr) {
            prefs.getInt("used_today", 0)
        } else {
            // New day, reset budget!
            prefs.edit()
                .putString("saved_date", todayStr)
                .putInt("used_today", 0)
                .apply()
            0
        }

        return SpontaneousSpeechState(
            isEnabled = isEnabled,
            maxDailyBudget = maxBudget,
            usedToday = usedToday,
            lastSpokenTimeMs = prefs.getLong("last_spoken_time", 0L)
        )
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        prefs.edit().putBoolean("is_enabled", enabled).apply()
    }

    fun checkNewDay() {
        val todayStr = dateFormat.format(Date())
        val savedDate = prefs.getString("saved_date", "")
        if (savedDate != todayStr) {
            prefs.edit()
                .putString("saved_date", todayStr)
                .putInt("used_today", 0)
                .apply()
            _state.value = _state.value.copy(usedToday = 0)
        }
    }

    /**
     * Called when user arrives after being absent.
     */
    fun onUserReturnAfterAbsence(absenceDurationMs: Long, isQuietMode: Boolean, isSleeping: Boolean): Boolean {
        checkNewDay()
        if (absenceDurationMs < ABSENCE_THRESHOLD_MS) return false
        if (!canSpeak(isQuietMode, isSleeping)) return false

        val greetings = listOf(
            "Welcome back! Good to see you.",
            "You're back! Hope your day is going well.",
            "Hey! Glad you're back at your desk.",
            "Welcome back! What are we working on now?"
        )
        return triggerSpeech(greetings.random())
    }

    /**
     * Evaluates periodic spontaneous remark when idle.
     */
    fun evaluateIdleRemark(isQuietMode: Boolean, isSleeping: Boolean, curiosity: Float): Boolean {
        checkNewDay()
        if (!canSpeak(isQuietMode, isSleeping)) return false

        // Random roll weighted by curiosity
        if (Random.nextFloat() > (curiosity * 0.4f)) return false

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val candidatePhrases = when {
            hour in 6..11 -> listOf(
                "Good morning! Ready for a great day?",
                "Morning! Don't forget to take breaks today.",
                "Sun is up and I'm ready to hang out with you!"
            )
            hour in 12..17 -> listOf(
                "Good afternoon! Don't forget to drink some water.",
                "Hope your afternoon is going smoothly!",
                "Just hanging out with you on your desk."
            )
            hour in 18..22 -> listOf(
                "Good evening! Time to start winding down soon.",
                "Getting later in the day. Hope you had a productive one!",
                "Quiet evenings are great for thinking."
            )
            else -> listOf(
                "It's nice and quiet here on your desk.",
                "I feel pretty happy keeping you company.",
                "Just thinking about all the fun things we learned."
            )
        }

        return triggerSpeech(candidatePhrases.random())
    }

    fun canSpeak(isQuietMode: Boolean, isSleeping: Boolean): Boolean {
        if (!_state.value.isEnabled) return false
        if (isQuietMode || isSleeping) return false
        if (_state.value.remainingToday <= 0) return false

        val now = SystemClock.uptimeMillis()
        if (lastAttemptTime != 0L && now - lastAttemptTime < MIN_COOLDOWN_MS) return false

        return true
    }

    fun triggerSpeech(text: String): Boolean {
        val now = SystemClock.uptimeMillis()
        lastAttemptTime = now

        val newUsed = _state.value.usedToday + 1
        _state.value = _state.value.copy(
            usedToday = newUsed,
            lastSpokenTimeMs = System.currentTimeMillis()
        )

        prefs.edit()
            .putInt("used_today", newUsed)
            .putLong("last_spoken_time", System.currentTimeMillis())
            .apply()

        onSpeak(text)
        return true
    }

    fun testSpeak() {
        val testPhrases = listOf(
            "Hello! I'm your RoboPet companion, happy to hang out with you!",
            "I'm keeping your desk safe and cozy today!",
            "Did you know that taking mini-breaks helps you stay focused?"
        )
        onSpeak(testPhrases.random())
    }

    fun resetDailyBudget() {
        prefs.edit().putInt("used_today", 0).apply()
        _state.value = _state.value.copy(usedToday = 0)
    }
}
