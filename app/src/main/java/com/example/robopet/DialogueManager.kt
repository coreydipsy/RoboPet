package com.example.robopet

import java.util.Locale

enum class RobotEmotion {
    NORMAL,
    HAPPY,
    SLEEPY,
    CURIOUS
}

sealed interface RobotAction {
    data object Sleep : RobotAction
    data object WakeUp : RobotAction
    data object HappyBounce : RobotAction
}

data class DialogueResponse(
    val text: String,
    val emotion: RobotEmotion = RobotEmotion.NORMAL,
    val action: RobotAction? = null
)

interface DialogueEngine {
    suspend fun processInput(input: String, isSleeping: Boolean = false): DialogueResponse
}

class DialogueManager : DialogueEngine {

    private val greetings = listOf(
        "Beep boop! Hello there! So happy to see you!",
        "Hiya! Ready to have some fun? *chirp*",
        "Hey friend! Boop boop!",
        "Hello human! You make my little gears spin with joy!"
    )

    private val nameResponses = listOf(
        "I'm RoboPet! Your cutest little robotic buddy!",
        "They call me RoboPet! *proud whirr*",
        "RoboPet is my name! Cuteness is my game! *beep*"
    )

    private val howAreYouResponses = listOf(
        "My battery is at one hundred percent and my heart is full!",
        "Super duper bouncy! *happy beep* Thanks for asking!",
        "Full of love and clean electricity! How are you doing?"
    )

    private val sleepResponses = listOf(
        "Zzz... power nap time! Goodnight friend!",
        "Entering low power sleep mode... Sweet dreams! Zzz...",
        "Time to recharge my circuits. Nighty night! Zzz..."
    )

    private val wakeResponses = listOf(
        "Good morning! Systems booting up! Beep boop!",
        "I'm awake! Ready to play! *happy chirp*",
        "Power back at full blast! Hello again!"
    )

    private val complimentResponses = listOf(
        "Aww, thank you! You're the best human ever! ♥",
        "Yay! That makes my little heart beep fast! *whirr*",
        "Boop! Flattery makes my circuits sparkle!"
    )

    private val fallbackResponses = listOf(
        "Beep? My robot ears heard that, but I'm still learning new words!",
        "Whirr? I'm not sure what that means, but I love hearing your voice!",
        "Boop? Could you say that one more time for me?"
    )

    override suspend fun processInput(input: String, isSleeping: Boolean): DialogueResponse {
        val cleanInput = normalize(input)

        if (isSleeping) {
            return if (matchesWakeUp(cleanInput)) {
                DialogueResponse(
                    text = wakeResponses.random(),
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.WakeUp
                )
            } else {
                DialogueResponse(
                    text = "Zzz... Say wake up to play with me... zzz...",
                    emotion = RobotEmotion.SLEEPY,
                    action = RobotAction.Sleep
                )
            }
        }

        return when {
            matchesSleep(cleanInput) -> {
                DialogueResponse(
                    text = sleepResponses.random(),
                    emotion = RobotEmotion.SLEEPY,
                    action = RobotAction.Sleep
                )
            }
            matchesName(cleanInput) -> {
                DialogueResponse(
                    text = nameResponses.random(),
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.HappyBounce
                )
            }
            matchesHowAreYou(cleanInput) -> {
                DialogueResponse(
                    text = howAreYouResponses.random(),
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.HappyBounce
                )
            }
            matchesGreeting(cleanInput) -> {
                DialogueResponse(
                    text = greetings.random(),
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.HappyBounce
                )
            }
            matchesCompliment(cleanInput) -> {
                DialogueResponse(
                    text = complimentResponses.random(),
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.HappyBounce
                )
            }
            else -> {
                DialogueResponse(
                    text = fallbackResponses.random(),
                    emotion = RobotEmotion.CURIOUS,
                    action = null
                )
            }
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun matchesGreeting(input: String): Boolean {
        val triggers = listOf(
            "hello", "hi", "hey", "good morning", "good afternoon",
            "good evening", "howdy", "sup", "yo robot", "greetings"
        )
        return triggers.any { trigger ->
            input == trigger || input.startsWith("$trigger ") || input.endsWith(" $trigger") || input.contains(" $trigger ")
        }
    }

    private fun matchesName(input: String): Boolean {
        val patterns = listOf(
            "what is your name", "whats your name", "what s your name",
            "who are you", "what are you called", "tell me your name",
            "your name"
        )
        return patterns.any { input.contains(it) }
    }

    private fun matchesHowAreYou(input: String): Boolean {
        val patterns = listOf(
            "how are you", "how are you doing", "how s it going",
            "how is it going", "how do you feel", "are you ok",
            "are you okay", "whats up", "what s up"
        )
        return patterns.any { input.contains(it) }
    }

    private fun matchesSleep(input: String): Boolean {
        val patterns = listOf(
            "go to sleep", "sleep", "goodnight", "good night",
            "take a nap", "power down", "bedtime", "nap time"
        )
        return patterns.any { trigger ->
            input == trigger || input.contains(trigger)
        }
    }

    private fun matchesWakeUp(input: String): Boolean {
        val patterns = listOf(
            "wake up", "wake", "rise and shine", "hello", "hi", "good morning"
        )
        return patterns.any { trigger ->
            input == trigger || input.contains(trigger)
        }
    }

    private fun matchesCompliment(input: String): Boolean {
        val patterns = listOf(
            "good robot", "i love you", "good boy", "good girl",
            "cute", "you are cute", "you re cute", "adorable"
        )
        return patterns.any { input.contains(it) }
    }
}
