package com.example.robopet

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryAndDialogueTest {

    private lateinit var db: RobotDatabase
    private lateinit var memoryManager: MemoryManager
    private lateinit var dialogueEngine: GeminiDialogueEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Use an isolated database file for testing
        context.deleteDatabase("test_robot_memory.db")
        db = Room.databaseBuilder(context, RobotDatabase::class.java, "test_robot_memory.db")
            .allowMainThreadQueries()
            .build()
        memoryManager = MemoryManager(context, db)
        dialogueEngine = GeminiDialogueEngine(context, memoryManager)
    }

    @After
    fun tearDown() {
        db.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("test_robot_memory.db")
    }

    @Test
    fun testOpenEndedQuestion() = runBlocking {
        // Requirement 1: The robot can answer an open-ended question.
        val response = dialogueEngine.processInput("Why do birds fly?", isSleeping = false)
        assertNotNull(response)
        assertTrue("Speech response should not be empty", response.text.isNotEmpty())
        assertTrue("Response should have an emotion", response.emotion == RobotEmotion.CURIOUS || response.emotion == RobotEmotion.HAPPY)
    }

    @Test
    fun testConversationTurnsMemory() = runBlocking {
        // Requirement 2: It can remember previous turns in the conversation.
        dialogueEngine.processInput("I am designing a robotic space turtle.", isSleeping = false)
        dialogueEngine.processInput("It has glowing solar shell panels.", isSleeping = false)

        val recentContext = memoryManager.getRecentMessages(10)
        assertTrue("Recent messages should contain user turn", recentContext.any { it.text.contains("space turtle") })
        assertTrue("Recent messages should contain second turn", recentContext.any { it.text.contains("solar shell panels") })

        // Ask about previous turn
        val queryResponse = dialogueEngine.processInput("What did I just say?", isSleeping = false)
        assertTrue("Robot should recall previous statement", queryResponse.text.contains("solar shell panels") || queryResponse.text.contains("space turtle"))
    }

    @Test
    fun testFactPersistenceAcrossRestart() = runBlocking {
        // Requirement 3: It remembers an explicitly stated fact after restarting the app.
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // User tells robot a fact
        dialogueEngine.processInput("My favorite color is emerald green", isSleeping = false)

        val factsBeforeRestart = memoryManager.getAllFacts()
        assertTrue("Fact should be extracted", factsBeforeRestart.any { it.fact.contains("emerald green", ignoreCase = true) })

        // Simulate app restart: close database and create new instances
        db.close()

        val restartedDb = Room.databaseBuilder(context, RobotDatabase::class.java, "test_robot_memory.db")
            .allowMainThreadQueries()
            .build()
        val restartedMemoryManager = MemoryManager(context, restartedDb)
        val restartedEngine = GeminiDialogueEngine(context, restartedMemoryManager)

        // Verify fact survived restart
        val factsAfterRestart = restartedMemoryManager.getAllFacts()
        val persistedFact = factsAfterRestart.find { it.fact.contains("emerald green", ignoreCase = true) }
        assertNotNull("Fact must persist across database restart", persistedFact)

        // Query the restarted engine about the remembered fact
        val answer = restartedEngine.processInput("What is my favorite color?", isSleeping = false)
        assertTrue("Robot should recall fact after restart: ${answer.text}", answer.text.contains("emerald green", ignoreCase = true))

        restartedDb.close()
    }

    @Test
    fun testFactDeduplicationAndSafety() = runBlocking {
        // Only save memories supported by actual user statements; avoid duplicates
        val saved1 = memoryManager.saveFact("preference", "User likes pizza", "i like pizza")
        assertTrue("First save should succeed", saved1)

        val saved2 = memoryManager.saveFact("preference", "User likes pizza", "i like pizza")
        assertFalse("Duplicate fact must not be saved", saved2)

        val facts = memoryManager.getAllFacts()
        assertEquals("Should only have 1 pizza fact", 1, facts.filter { it.fact == "User likes pizza" }.size)

        // Test rejecting hallucinated fact not in user text
        memoryManager.saveGeminiSuggestedFact("User lives in Tokyo", "I had soup for lunch")
        val tokyoFact = memoryManager.getAllFacts().find { it.fact.contains("Tokyo") }
        assertTrue("Hallucinated fact must be rejected", tokyoFact == null)
    }

    @Test
    fun testOfflineReaction() = runBlocking {
        // Requirement 4: The robot still reacts locally when internet is unavailable.
        val greeting = dialogueEngine.processInput("Hello there", isSleeping = false)
        assertEquals(RobotEmotion.HAPPY, greeting.emotion)
        assertEquals(RobotAction.HappyBounce, greeting.action)
        assertTrue(greeting.text.isNotEmpty())

        val sleep = dialogueEngine.processInput("Go to sleep", isSleeping = false)
        assertEquals(RobotEmotion.SLEEPY, sleep.emotion)
        assertEquals(RobotAction.Sleep, sleep.action)
    }
}
