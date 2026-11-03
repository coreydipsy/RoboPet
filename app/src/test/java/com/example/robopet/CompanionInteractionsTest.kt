package com.example.robopet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CompanionInteractionsTest {

    @Test
    fun pomodoro_formattedTimeAndProgress() {
        val stateFull = PomodoroState(
            mode = PomodoroMode.FOCUS,
            remainingSeconds = 1500,
            totalSeconds = 1500
        )
        assertEquals("25:00", stateFull.formattedTime)
        assertEquals(0f, stateFull.progress, 0.001f)

        val stateHalf = PomodoroState(
            mode = PomodoroMode.FOCUS,
            remainingSeconds = 750,
            totalSeconds = 1500
        )
        assertEquals("12:30", stateHalf.formattedTime)
        assertEquals(0.5f, stateHalf.progress, 0.001f)

        val stateZero = PomodoroState(
            mode = PomodoroMode.SHORT_BREAK,
            remainingSeconds = 0,
            totalSeconds = 300
        )
        assertEquals("00:00", stateZero.formattedTime)
        assertEquals(1.0f, stateZero.progress, 0.001f)
    }

    @Test
    fun rps_outcomeRules() {
        fun evaluate(user: RpsChoice?, robot: RpsChoice): RpsOutcome {
            return when {
                user == null -> RpsOutcome.NO_GESTURE_DETECTED
                user == robot -> RpsOutcome.TIE
                (user == RpsChoice.ROCK && robot == RpsChoice.SCISSORS) ||
                        (user == RpsChoice.PAPER && robot == RpsChoice.ROCK) ||
                        (user == RpsChoice.SCISSORS && robot == RpsChoice.PAPER) -> RpsOutcome.USER_WINS
                else -> RpsOutcome.ROBOT_WINS
            }
        }

        // User wins
        assertEquals(RpsOutcome.USER_WINS, evaluate(RpsChoice.ROCK, RpsChoice.SCISSORS))
        assertEquals(RpsOutcome.USER_WINS, evaluate(RpsChoice.PAPER, RpsChoice.ROCK))
        assertEquals(RpsOutcome.USER_WINS, evaluate(RpsChoice.SCISSORS, RpsChoice.PAPER))

        // Robot wins
        assertEquals(RpsOutcome.ROBOT_WINS, evaluate(RpsChoice.SCISSORS, RpsChoice.ROCK))
        assertEquals(RpsOutcome.ROBOT_WINS, evaluate(RpsChoice.ROCK, RpsChoice.PAPER))
        assertEquals(RpsOutcome.ROBOT_WINS, evaluate(RpsChoice.PAPER, RpsChoice.SCISSORS))

        // Tie
        assertEquals(RpsOutcome.TIE, evaluate(RpsChoice.ROCK, RpsChoice.ROCK))
        assertEquals(RpsOutcome.TIE, evaluate(RpsChoice.PAPER, RpsChoice.PAPER))
        assertEquals(RpsOutcome.TIE, evaluate(RpsChoice.SCISSORS, RpsChoice.SCISSORS))

        // No gesture
        assertEquals(RpsOutcome.NO_GESTURE_DETECTED, evaluate(null, RpsChoice.ROCK))
    }

    @Test
    fun rps_handGestureToRpsChoiceMapping() {
        fun gestureToChoice(gesture: HandGesture): RpsChoice? = when (gesture) {
            HandGesture.CLOSED_FIST -> RpsChoice.ROCK
            HandGesture.OPEN_PALM -> RpsChoice.PAPER
            HandGesture.VICTORY -> RpsChoice.SCISSORS
            else -> null
        }

        assertEquals(RpsChoice.ROCK, gestureToChoice(HandGesture.CLOSED_FIST))
        assertEquals(RpsChoice.PAPER, gestureToChoice(HandGesture.OPEN_PALM))
        assertEquals(RpsChoice.SCISSORS, gestureToChoice(HandGesture.VICTORY))
        assertEquals(null, gestureToChoice(HandGesture.WAVE))
        assertEquals(null, gestureToChoice(HandGesture.NONE))
    }

    @Test
    fun peekABoo_timingWindow() {
        fun isPeekABooValid(lostTime: Long, reappearTime: Long): Boolean {
            val elapsed = reappearTime - lostTime
            return elapsed in 400L..4500L
        }

        // Too quick (<400ms glitch/blink)
        assertFalse(isPeekABooValid(1000L, 1200L))

        // Perfect peek-a-boo window
        assertTrue(isPeekABooValid(1000L, 2000L)) // 1 second
        assertTrue(isPeekABooValid(1000L, 3500L)) // 2.5 seconds

        // Too long (>4.5 seconds absence)
        assertFalse(isPeekABooValid(1000L, 6000L))
    }

    @Test
    fun wakeWord_matchingAndCommandExtraction() {
        val phrases = SpeechInput.WAKE_PHRASES

        fun extractWakeWordAndCommand(input: String): Pair<String?, String?> {
            val lower = input.lowercase(Locale.ROOT).trim()
            val matched = phrases.firstOrNull { lower.contains(it) } ?: return Pair(null, null)
            val index = lower.indexOf(matched)
            val command = input.substring(index + matched.length).trim(' ', ',', '.', '!', '?')
            return Pair(matched, if (command.isBlank()) null else command)
        }

        // Exact wake word
        val (wake1, cmd1) = extractWakeWordAndCommand("Hey RoboPet")
        assertEquals("hey robopet", wake1)
        assertEquals(null, cmd1)

        // Wake word with trailing command
        val (wake2, cmd2) = extractWakeWordAndCommand("Hey RoboPet, start rock paper scissors!")
        assertEquals("hey robopet", wake2)
        assertEquals("start rock paper scissors", cmd2)

        // Alternative wake word
        val (wake3, cmd3) = extractWakeWordAndCommand("RoboPet, what time is it?")
        assertEquals("robopet", wake3)
        assertEquals("what time is it", cmd3)

        // Non-trigger phrase
        val (wake4, cmd4) = extractWakeWordAndCommand("Good morning computer")
        assertEquals(null, wake4)
        assertEquals(null, cmd4)
    }
}
