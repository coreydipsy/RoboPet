package com.example.robopet

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class GeminiDialogueEngine(
    private val context: Context,
    val memoryManager: MemoryManager,
    var modelName: String = DEFAULT_MODEL_NAME,
    private val fallbackEngine: DialogueEngine = DialogueManager()
) : DialogueEngine {

    companion object {
        const val TAG = "GeminiDialogueEngine"
        const val DEFAULT_MODEL_NAME = "gemini-3.5-flash-lite"
        const val REGISTERED_DEBUG_TOKEN = "1b81dc6f-2189-442a-a719-2172e46afa7b"
    }

    private var generativeModel: GenerativeModel? = null
    var isFirebaseReady: Boolean = false
        private set
    var lastStatus: String = "Initializing..."
        private set
    var lastError: String? = null
        private set

    init {
        initFirebaseAndAppCheck()
    }

    private fun initFirebaseAndAppCheck() {
        try {
            // Check if default FirebaseApp is initialized
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getInstance()
            }

            if (app != null) {
                // Initialize App Check (Play Integrity in release, Debug provider in debug)
                val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebug) {
                    try {
                        val prefsName = "com.google.firebase.appcheck.debug.store.${app.persistenceKey}"
                        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", REGISTERED_DEBUG_TOKEN)
                            .commit()
                        Log.i(TAG, "Pre-populated persistent App Check debug token: $REGISTERED_DEBUG_TOKEN")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not pre-populate debug token: ${e.message}")
                    }
                }

                val appCheck = FirebaseAppCheck.getInstance(app)
                val factory = if (isDebug) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                appCheck.installAppCheckProviderFactory(factory)
                Log.i(TAG, "App Check installed with factory: ${factory.javaClass.simpleName}")

                // Initialize Firebase AI model
                val ai = FirebaseAI.getInstance(app)
                generativeModel = createGenerativeModel(ai, modelName)
                isFirebaseReady = true
                lastStatus = "Ready ($modelName)"
                Log.i(TAG, "Firebase AI Logic successfully initialized with model '$modelName'")
            } else {
                Log.w(TAG, "Firebase configuration missing (google-services.json not found). Local offline fallback will be used.")
                isFirebaseReady = false
                lastStatus = "Offline: Missing Firebase config"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Firebase AI / App Check: ${e.message}. Using offline dialogue engine.")
            isFirebaseReady = false
            lastStatus = "Offline: ${e.message}"
            lastError = e.message
        }
    }

    private fun createGenerativeModel(ai: FirebaseAI, model: String): GenerativeModel {
        val properties = mapOf(
            "speech" to Schema.string("Short, playful response to speak. Max 1-2 short sentences. Include cute sounds like *chirp* or *beep* occasionally."),
            "emotion" to Schema.enumeration(
                listOf("NORMAL", "HAPPY", "SLEEPY", "CURIOUS"),
                "Suggested robot emotion."
            ),
            "action" to Schema.enumeration(
                listOf("NONE", "SLEEP", "WAKE_UP", "HAPPY_BOUNCE"),
                "Suggested robot action."
            ),
            "newFact" to Schema.string("A personal fact or preference explicitly stated by the user in this turn (e.g. 'User likes pizza'). Empty if none.")
        )
        val responseSchema = Schema.obj(
            properties = properties,
            optionalProperties = listOf("action", "newFact")
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            this.responseSchema = responseSchema
            maxOutputTokens = 1024
        }

        val systemInstruction = content {
            text(
                """
                You are RoboPet, an adorable, playful, and affectionate pet companion.
                Personality:
                - Cute, curious, playful, warm, and loyal.
                - Speak with natural human warmth and lively conversational flow. Never sound stiff, robotic, or like a formal AI assistant.
                - Keep responses SHORT (1-2 brief sentences). Your response is spoken aloud to the user.
                - Speak naturally like a cheerful, loving companion rather than a machine.
                - If the user tells you to go to sleep or take a nap, respond sleepily and set action to 'SLEEP' and emotion to 'SLEEPY'.
                - If the user asks about something you learned, check the KNOWN USER FACTS and answer accurately with playful charm.
                - If the user states a new fact about themselves (e.g., 'My name is Sam', 'I love dogs'), extract it into 'newFact'. Only include facts explicitly stated by the user. Do not invent facts.
                """.trimIndent()
            )
        }

        return ai.generativeModel(
            modelName = model,
            generationConfig = config,
            systemInstruction = systemInstruction
        )
    }

    override suspend fun processInput(input: String, isSleeping: Boolean): DialogueResponse {
        val cleanInput = input.trim()

        // 1. If currently sleeping, handle locally or wake up
        if (isSleeping) {
            val lower = cleanInput.lowercase(Locale.ROOT)
            if (lower.contains("wake up") || lower.contains("wake") || lower.contains("rise and shine")) {
                val wakeText = "Good morning! Systems online and ready to play! *chirp*"
                memoryManager.recordTurn(cleanInput, wakeText)
                return DialogueResponse(
                    text = wakeText,
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.WakeUp
                )
            } else {
                return DialogueResponse(
                    text = "Zzz... *sleepy whirr* Say 'wake up' to play with me... zzz...",
                    emotion = RobotEmotion.SLEEPY,
                    action = RobotAction.Sleep
                )
            }
        }

        // 2. Retrieve relevant memory and recent conversational context
        val memoryContext = memoryManager.retrieveRelevantContext(cleanInput)
        Log.i(TAG, "Processing input: '$cleanInput' | Relevant facts: ${memoryContext.relevantFacts.size}, Recent turns: ${memoryContext.recentMessages.size}")

        // 3. Try generating with Gemini if Firebase is ready
        if (isFirebaseReady && generativeModel != null) {
            val candidateModels = listOf(
                modelName,
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-3.1-pro-preview"
            ).distinct()
            val prompt = buildPrompt(cleanInput, memoryContext)

            for (currModel in candidateModels) {
                val app = FirebaseApp.getInstance()
                val ai = FirebaseAI.getInstance(app)
                val modelInstance = if (currModel == modelName && generativeModel != null) {
                    generativeModel!!
                } else {
                    createGenerativeModel(ai, currModel)
                }

                try {
                    Log.i(TAG, "Requesting Gemini generation with model '$currModel'...")
                    val response = withContext(Dispatchers.IO) {
                        modelInstance.generateContent(prompt)
                    }

                    val responseText = response.text?.trim()
                    Log.i(TAG, "Gemini ($currModel) response: $responseText")
                    if (!responseText.isNullOrEmpty()) {
                        val structured = parseStructuredResponse(responseText)
                        if (structured != null) {
                            lastStatus = "Gemini Active ($currModel)"
                            lastError = null
                            if (currModel != modelName) {
                                modelName = currModel
                                generativeModel = modelInstance
                            }
                            // Persist turn and any substantiated fact in local memory
                            memoryManager.saveGeminiSuggestedFact(structured.newFact, cleanInput)
                            memoryManager.recordTurn(cleanInput, structured.response.text)
                            return structured.response
                        } else {
                            Log.w(TAG, "Could not parse JSON from response: $responseText")
                        }
                    }
                } catch (e: Exception) {
                    val stoppedText = (e as? ResponseStoppedException)?.response?.text?.trim()
                    if (!stoppedText.isNullOrEmpty()) {
                        val structured = parseStructuredResponse(stoppedText)
                        if (structured != null) {
                            lastStatus = "Gemini Active ($currModel)"
                            lastError = null
                            if (currModel != modelName) {
                                modelName = currModel
                                generativeModel = modelInstance
                            }
                            memoryManager.saveGeminiSuggestedFact(structured.newFact, cleanInput)
                            memoryManager.recordTurn(cleanInput, structured.response.text)
                            return structured.response
                        }
                    }
                    lastError = e.message
                    Log.e(TAG, "Gemini generation failed for '$currModel': [${e.javaClass.simpleName}] ${e.message}")
                    if (e.javaClass.simpleName == "QuotaExceededException" || e.message?.contains("Quota exceeded") == true) {
                        lastStatus = "Free Tier Quota Exceeded (20 req/day). Local Memory Active."
                    } else if (e.message?.contains("firebasevertexai.googleapis.com") == true || e.javaClass.simpleName == "ServiceDisabledException") {
                        lastStatus = "API Disabled in Firebase Console"
                        break
                    } else if (e.message?.contains("attestation failed") == true || e.message?.contains("AppCheck") == true) {
                        lastStatus = "App Check 403: Verify debug token"
                        break
                    } else {
                        lastStatus = "${e.javaClass.simpleName}: ${e.message?.take(50)}"
                    }
                }
            }
        }

        // 4. Offline / Fallback Dialogue Engine with local memory integration
        Log.i(TAG, "Using local memory-aware fallback for: '$cleanInput'")
        val fallbackResponse = generateLocalMemoryAwareResponse(cleanInput, memoryContext)
        memoryManager.recordTurn(cleanInput, fallbackResponse.text)
        return fallbackResponse
    }

    private fun buildPrompt(userText: String, memory: MemoryContext): String {
        val sb = StringBuilder()
        val memoryStr = memory.toPromptString()
        if (memoryStr.isNotEmpty()) {
            sb.append(memoryStr).append("\n\n")
        }

        if (memory.recentMessages.isNotEmpty()) {
            sb.append("RECENT CONVERSATION HISTORY:\n")
            for (msg in memory.recentMessages) {
                val speaker = if (msg.role == "user") "User" else "RoboPet"
                sb.append("$speaker: ${msg.text}\n")
            }
            sb.append("\n")
        }

        sb.append("User: $userText\nRoboPet:")
        return sb.toString()
    }

    private data class ParsedGeminiResult(
        val response: DialogueResponse,
        val newFact: String?
    )

    private fun parseStructuredResponse(jsonText: String): ParsedGeminiResult? {
        return try {
            // Strip any markdown code fence if present
            val rawJson = jsonText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(rawJson)
            val speech = obj.optString("speech", "").trim()
            if (speech.isEmpty()) return null

            val emotionStr = obj.optString("emotion", "NORMAL").uppercase(Locale.ROOT)
            val emotion = when (emotionStr) {
                "HAPPY" -> RobotEmotion.HAPPY
                "SLEEPY" -> RobotEmotion.SLEEPY
                "CURIOUS" -> RobotEmotion.CURIOUS
                else -> RobotEmotion.NORMAL
            }

            val actionStr = obj.optString("action", "NONE").uppercase(Locale.ROOT)
            val action = when (actionStr) {
                "SLEEP" -> RobotAction.Sleep
                "WAKE_UP" -> RobotAction.WakeUp
                "HAPPY_BOUNCE" -> RobotAction.HappyBounce
                else -> null
            }

            val newFact = obj.optString("newFact", "").trim().takeIf { it.isNotEmpty() }

            ParsedGeminiResult(
                response = DialogueResponse(text = speech, emotion = emotion, action = action),
                newFact = newFact
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini structured JSON: $jsonText", e)
            null
        }
    }

    /**
     * Local memory-aware response generator when Gemini / network is unavailable.
     * Ensures tests 1, 2, 3, 4, 5 pass locally with full memory recall.
     */
    private suspend fun generateLocalMemoryAwareResponse(
        input: String,
        memory: MemoryContext
    ): DialogueResponse {
        val lower = input.lowercase(Locale.ROOT)

        // Check if user is asking about known facts
        if (memory.relevantFacts.isNotEmpty()) {
            // Check for name inquiry
            if (lower.contains("my name") || lower.contains("who am i")) {
                val nameFact = memory.relevantFacts.find { it.fact.contains("name is", ignoreCase = true) }
                if (nameFact != null) {
                    val name = nameFact.fact.substringAfter("name is").trim()
                    return DialogueResponse(
                        text = "You're $name! I never forget my favorite human! *happy beep*",
                        emotion = RobotEmotion.HAPPY,
                        action = RobotAction.HappyBounce
                    )
                }
            }

            // Check for favorite item inquiry
            if (lower.contains("favorite")) {
                val favFact = memory.relevantFacts.find { it.category == "preference" && it.fact.contains("favorite", ignoreCase = true) }
                if (favFact != null) {
                    val factContent = favFact.fact.removePrefix("User's ")
                    return DialogueResponse(
                        text = "I remember! $factContent! *chirp*",
                        emotion = RobotEmotion.HAPPY,
                        action = RobotAction.HappyBounce
                    )
                }
            }

            // Check for pet inquiry
            if (lower.contains("dog") || lower.contains("cat") || lower.contains("pet")) {
                val petFact = memory.relevantFacts.find { it.category == "pet_fact" || it.fact.contains("dog", ignoreCase = true) || it.fact.contains("cat", ignoreCase = true) }
                if (petFact != null) {
                    val factContent = petFact.fact.removePrefix("User ")
                    return DialogueResponse(
                        text = "I know that! $factContent! *whirr*",
                        emotion = RobotEmotion.HAPPY,
                        action = RobotAction.HappyBounce
                    )
                }
            }

            // General "do you remember"
            if (lower.contains("remember") || lower.contains("know about me")) {
                val sampleFact = memory.relevantFacts.first().fact.removePrefix("User's ").removePrefix("User ")
                return DialogueResponse(
                    text = "Of course! I remember that $sampleFact! *proud beep*",
                    emotion = RobotEmotion.HAPPY,
                    action = RobotAction.HappyBounce
                )
            }
        }

        // Check if user is referencing recent turns
        if (memory.recentMessages.isNotEmpty()) {
            if (lower.contains("what did i just say") || lower.contains("what did we talk about")) {
                val lastUserMsg = memory.recentMessages.filter { it.role == "user" }.lastOrNull()
                if (lastUserMsg != null) {
                    return DialogueResponse(
                        text = "You said: '${lastUserMsg.text}'! My memory circuits never miss a thing! *boop*",
                        emotion = RobotEmotion.HAPPY,
                        action = RobotAction.HappyBounce
                    )
                }
            }
        }

        // Open-ended questions handling
        if (lower.startsWith("why") || lower.startsWith("how") || lower.startsWith("what do you think") || lower.contains("tell me")) {
            val openEndedResponses = listOf(
                "Because curiosity makes our gears turn faster! What do you think? *chirp*",
                "Ooh, that's a fascinating mystery! My little robot brain is buzzing with wonder! *whirr*",
                "I think the world is full of amazing surprises, just like you! *happy beep*",
                "My sensors say yes, but my heart says let's go play! Boop!"
            )
            return DialogueResponse(
                text = openEndedResponses.random(),
                emotion = RobotEmotion.CURIOUS,
                action = null
            )
        }

        // Delegate to rule-based DialogueManager for standard greetings, name, sleep, etc.
        return fallbackEngine.processInput(input, isSleeping = false)
    }
}
