package com.example.robopet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueManagerTest {

    private val dialogueManager = DialogueManager()

    @Test
    fun testGreetings() = runBlocking {
        val greetings = listOf("hello", "hi", "hey", "good morning", "howdy")
        for (greeting in greetings) {
            val response = dialogueManager.processInput(greeting)
            assertEquals("Greeting $greeting should have HAPPY emotion", RobotEmotion.HAPPY, response.emotion)
            assertEquals("Greeting $greeting should have HappyBounce action", RobotAction.HappyBounce, response.action)
            assertTrue("Greeting response should not be empty", response.text.isNotEmpty())
        }
    }

    @Test
    fun testNameQuery() = runBlocking {
        val queries = listOf("what's your name?", "What is your name", "who are you", "tell me your name")
        for (query in queries) {
            val response = dialogueManager.processInput(query)
            assertEquals(RobotEmotion.HAPPY, response.emotion)
            assertTrue("Response should mention RoboPet: ${response.text}", response.text.contains("RoboPet"))
        }
    }

    @Test
    fun testHowAreYou() = runBlocking {
        val queries = listOf("how are you?", "how are you doing", "how's it going", "how do you feel")
        for (query in queries) {
            val response = dialogueManager.processInput(query)
            assertEquals(RobotEmotion.HAPPY, response.emotion)
            assertEquals(RobotAction.HappyBounce, response.action)
            assertTrue("Response should not be empty", response.text.isNotEmpty())
        }
    }

    @Test
    fun testGoToSleep() = runBlocking {
        val queries = listOf("go to sleep", "sleep", "goodnight", "take a nap")
        for (query in queries) {
            val response = dialogueManager.processInput(query)
            assertEquals(RobotEmotion.SLEEPY, response.emotion)
            assertEquals(RobotAction.Sleep, response.action)
            assertTrue("Response should contain sleep keyword: ${response.text}", response.text.contains("Zzz") || response.text.contains("sleep") || response.text.contains("night"))
        }
    }

    @Test
    fun testSleepStateWakeUp() = runBlocking {
        // While sleeping, regular query returns sleepy reminder
        val sleepingResponse = dialogueManager.processInput("how are you", isSleeping = true)
        assertEquals(RobotEmotion.SLEEPY, sleepingResponse.emotion)
        assertEquals(RobotAction.Sleep, sleepingResponse.action)

        // Wake up query wakes the robot up
        val wakeResponse = dialogueManager.processInput("wake up", isSleeping = true)
        assertEquals(RobotEmotion.HAPPY, wakeResponse.emotion)
        assertEquals(RobotAction.WakeUp, wakeResponse.action)
    }

    @Test
    fun testFallback() = runBlocking {
        val response = dialogueManager.processInput("quantum mechanical astrophysics")
        assertEquals(RobotEmotion.CURIOUS, response.emotion)
        assertTrue(response.text.isNotEmpty())
    }
}
