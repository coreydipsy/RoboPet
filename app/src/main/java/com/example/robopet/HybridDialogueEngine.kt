package com.example.robopet

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class EnginePreference {
    CLOUD_GEMINI_FAST,    // Cloud Gemini primary (~1-2s response), On-Device / Room fallback
    ON_DEVICE_SLM_FIRST   // Local LiteRT-LM primary, escalate to Gemini if needed
}

class HybridDialogueEngine(
    val onDeviceEngine: OnDeviceDialogueEngine,
    val geminiEngine: GeminiDialogueEngine,
    val memoryManager: MemoryManager,
    private val fallbackEngine: DialogueEngine = DialogueManager()
) : DialogueEngine {

    companion object {
        const val TAG = "HybridDialogueEngine"
    }

    private val _enginePreference = MutableStateFlow(EnginePreference.CLOUD_GEMINI_FAST)
    val enginePreference: StateFlow<EnginePreference> = _enginePreference.asStateFlow()

    private val _currentEngineStatus = MutableStateFlow(resolveInitialStatus())
    val currentEngineStatus: StateFlow<String> = _currentEngineStatus.asStateFlow()

    fun setEnginePreference(pref: EnginePreference) {
        _enginePreference.value = pref
        _currentEngineStatus.value = resolveInitialStatus()
    }

    private fun resolveInitialStatus(): String {
        return when (_enginePreference.value) {
            EnginePreference.CLOUD_GEMINI_FAST -> {
                if (geminiEngine.isFirebaseReady) {
                    "Cloud Gemini (${geminiEngine.modelName})"
                } else if (onDeviceEngine.isReady.value) {
                    "On-Device SLM Active"
                } else {
                    "Local Memory Active"
                }
            }
            EnginePreference.ON_DEVICE_SLM_FIRST -> {
                if (onDeviceEngine.isReady.value) {
                    "On-Device SLM Ready"
                } else if (geminiEngine.isFirebaseReady) {
                    "Cloud Gemini (${geminiEngine.modelName})"
                } else {
                    "Local Memory Active"
                }
            }
        }
    }

    override suspend fun processInput(input: String, isSleeping: Boolean): DialogueResponse {
        val cleanInput = input.trim()

        // 1. Handle sleep state locally
        if (isSleeping) {
            val lower = cleanInput.lowercase(Locale.ROOT)
            if (lower.contains("wake up") || lower.contains("wake") || lower.contains("rise and shine")) {
                val wakeText = "Good morning! Systems online and ready to play! *chirp*"
                memoryManager.recordTurn(cleanInput, wakeText)
                _currentEngineStatus.value = "Awake"
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

        // 2. Retrieve local memory context from Room SQLite database
        val memoryContext = memoryManager.retrieveRelevantContext(cleanInput)
        val memoryStr = memoryContext.toPromptString()

        // 3. Execution based on engine preference
        if (_enginePreference.value == EnginePreference.ON_DEVICE_SLM_FIRST && onDeviceEngine.isReady.value) {
            Log.i(TAG, "Querying On-Device SLM first for: '$cleanInput'")
            _currentEngineStatus.value = "Thinking on phone CPU... ⏳"
            val slmResult = onDeviceEngine.generateResponse(cleanInput, memoryStr)

            if (slmResult != null) {
                if (slmResult.needsGeminiEscalation) {
                    Log.i(TAG, "On-Device SLM requested Gemini escalation: '${slmResult.response.text}'")
                    _currentEngineStatus.value = "Escalating to Gemini..."

                    val geminiResponse = tryGemini(cleanInput, isSleeping)
                    if (geminiResponse != null) {
                        _currentEngineStatus.value = "Escalated to Gemini (${geminiEngine.modelName})"
                        return geminiResponse
                    } else {
                        _currentEngineStatus.value = "On-Device SLM Active (Gemini offline)"
                        memoryManager.recordTurn(cleanInput, slmResult.response.text)
                        return slmResult.response
                    }
                } else {
                    Log.i(TAG, "On-Device SLM answered: '${slmResult.response.text}'")
                    _currentEngineStatus.value = "On-Device SLM Active"
                    memoryManager.recordTurn(cleanInput, slmResult.response.text)
                    return slmResult.response
                }
            } else {
                Log.w(TAG, "On-device SLM timed out (>35s); falling back to Gemini")
            }
        }

        // 4. Cloud Gemini (Primary for CLOUD_GEMINI_FAST, or fallback for SLM)
        Log.i(TAG, "Querying Cloud Gemini for: '$cleanInput'")
        val geminiResponse = tryGemini(cleanInput, isSleeping)
        if (geminiResponse != null) {
            _currentEngineStatus.value = geminiEngine.lastStatus
            return geminiResponse
        }

        // 5. If Cloud Gemini failed / offline, try On-Device SLM as offline fallback
        if (onDeviceEngine.isReady.value) {
            Log.i(TAG, "Gemini unavailable. Trying On-Device SLM fallback for: '$cleanInput'")
            _currentEngineStatus.value = "On-Device SLM (Offline Fallback)"
            val slmResult = onDeviceEngine.generateResponse(cleanInput, memoryStr)
            if (slmResult != null) {
                memoryManager.recordTurn(cleanInput, slmResult.response.text)
                return slmResult.response
            }
        }

        // 6. Final safety fallback to local Room memory & rule manager
        Log.i(TAG, "Using local memory-aware fallback for: '$cleanInput'")
        _currentEngineStatus.value = "Local Memory Active"
        return geminiEngine.processInput(cleanInput, isSleeping)
    }

    private suspend fun tryGemini(input: String, isSleeping: Boolean): DialogueResponse? {
        return try {
            val response = geminiEngine.processInput(input, isSleeping)
            if (geminiEngine.lastError != null) {
                null
            } else {
                response
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini attempt failed: ${e.message}")
            null
        }
    }
}
