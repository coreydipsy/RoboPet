package com.example.robopet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

class SpeechInput(
    context: Context,
    var onResult: ((String) -> Unit)? = null,
    var onPartialResult: ((String) -> Unit)? = null,
    var onListeningStateChanged: ((Boolean) -> Unit)? = null,
    var onError: ((Int, String) -> Unit)? = null,
    var onModeNotice: ((String) -> Unit)? = null,
    var onRmsChanged: ((Float) -> Unit)? = null,
    var onWakeWordDetected: ((wakePhrase: String, trailingCommand: String?) -> Unit)? = null,
    var onWakeWordStateChanged: ((Boolean) -> Unit)? = null,
    var canListenProvider: () -> Boolean = { true }
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isOnDevice: Boolean = false

    var isListening: Boolean = false
        private set

    var isWakeWordEnabled: Boolean = true
        private set

    var isWakeWordListening: Boolean = false
        private set

    private var restartRunnable: Runnable? = null

    companion object {
        val WAKE_PHRASES = listOf(
            "hey robopet",
            "hey robo pet",
            "robopet",
            "robo pet",
            "hey robot",
            "wake up robopet",
            "wake up robo pet",
            "hey buddy"
        )
    }

    init {
        mainHandler.post {
            setupRecognizer()
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        isWakeWordEnabled = enabled
        if (!enabled) {
            pauseWakeWordListening()
        } else if (!isListening) {
            schedulePassiveRestart(delayMs = 600L)
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            val msg = "Speech recognition is not available on this device."
            Log.e("SpeechInput", msg)
            onModeNotice?.invoke(msg)
            return
        }

        val canUseOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

        if (canUseOnDevice) {
            try {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                isOnDevice = true
                val notice = "Using on-device speech recognition."
                Log.i("SpeechInput", notice)
                onModeNotice?.invoke(notice)
            } catch (e: Exception) {
                Log.w("SpeechInput", "Failed to create on-device recognizer; falling back to standard recognizer.", e)
                fallbackToOnlineRecognizer()
            }
        } else {
            fallbackToOnlineRecognizer()
        }

        attachListener()
    }

    private fun fallbackToOnlineRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        isOnDevice = false
        val notice = "On-device speech recognition is unavailable. Speech may be processed online."
        Log.i("SpeechInput", notice)
        onModeNotice?.invoke(notice)
    }

    private fun attachListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechInput", "onReadyForSpeech (wakeWord=$isWakeWordListening)")
                if (!isWakeWordListening) {
                    isListening = true
                    onListeningStateChanged?.invoke(true)
                }
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechInput", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isWakeWordListening) {
                    onRmsChanged?.invoke(rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechInput", "onEndOfSpeech")
            }

            override fun onError(errorCode: Int) {
                if (isWakeWordListening) {
                    // Normal timeout/silence in passive mode; restart loop quietly
                    isWakeWordListening = false
                    onWakeWordStateChanged?.invoke(false)
                    schedulePassiveRestart(delayMs = 400L)
                    return
                }

                isListening = false
                onListeningStateChanged?.invoke(false)
                val errorMessage = getErrorMessage(errorCode)
                Log.w("SpeechInput", "Speech recognition error ($errorCode): $errorMessage")
                onError?.invoke(errorCode, errorMessage)

                if (isWakeWordEnabled) {
                    schedulePassiveRestart(delayMs = 1200L)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                Log.d("SpeechInput", "onResults: $recognizedText (wakeWordMode=$isWakeWordListening)")

                if (isWakeWordListening) {
                    isWakeWordListening = false
                    onWakeWordStateChanged?.invoke(false)

                    if (!recognizedText.isNullOrEmpty()) {
                        val lower = recognizedText.lowercase(Locale.ROOT)
                        val matched = WAKE_PHRASES.firstOrNull { lower.contains(it) }
                        if (matched != null) {
                            val triggerIndex = lower.indexOf(matched)
                            val command = recognizedText.substring(triggerIndex + matched.length).trim(' ', ',', '.', '!', '?')
                            handleWakeWord(matched, if (command.isBlank()) null else command)
                            return
                        }
                    }
                    schedulePassiveRestart(delayMs = 400L)
                    return
                }

                isListening = false
                onListeningStateChanged?.invoke(false)
                if (!recognizedText.isNullOrEmpty()) {
                    onResult?.invoke(recognizedText)
                }

                if (isWakeWordEnabled) {
                    schedulePassiveRestart(delayMs = 1200L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()?.trim()

                if (isWakeWordListening && !partialText.isNullOrEmpty()) {
                    val lower = partialText.lowercase(Locale.ROOT)
                    val matched = WAKE_PHRASES.firstOrNull { lower.contains(it) }
                    if (matched != null) {
                        val triggerIndex = lower.indexOf(matched)
                        val command = partialText.substring(triggerIndex + matched.length).trim(' ', ',', '.', '!', '?')
                        handleWakeWord(matched, if (command.isBlank()) null else command)
                        return
                    }
                }

                if (!isWakeWordListening && !partialText.isNullOrEmpty()) {
                    onPartialResult?.invoke(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun handleWakeWord(wakePhrase: String, trailingCommand: String?) {
        mainHandler.post {
            isWakeWordListening = false
            onWakeWordStateChanged?.invoke(false)
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}

            Log.i("SpeechInput", "Wake word detected: '$wakePhrase' command: '$trailingCommand'")
            onWakeWordDetected?.invoke(wakePhrase, trailingCommand)
        }
    }

    private fun schedulePassiveRestart(delayMs: Long = 450L) {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (isWakeWordEnabled && !isListening && !isWakeWordListening && canListenProvider()) {
                startWakeWordListeningInternal()
            }
        }
        restartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun startWakeWordListeningInternal() {
        if (!isWakeWordEnabled || isListening || !canListenProvider()) return
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        if (speechRecognizer == null) {
            setupRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(intent)
            isWakeWordListening = true
            onWakeWordStateChanged?.invoke(true)
            Log.d("SpeechInput", "Passive wake word listening started (onDevice=$isOnDevice)")
        } catch (e: Exception) {
            Log.w("SpeechInput", "Exception starting passive wake word listening", e)
            isWakeWordListening = false
            onWakeWordStateChanged?.invoke(false)
            schedulePassiveRestart(delayMs = 2000L)
        }
    }

    fun startListening() {
        mainHandler.post {
            // Cancel passive listening
            pauseWakeWordListening()

            // Guard: check permission
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val msg = "RECORD_AUDIO permission not granted."
                Log.w("SpeechInput", msg)
                onError?.invoke(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, msg)
                return@post
            }

            // Guard: prevent recognizing own speech / listening while speaking
            if (!canListenProvider()) {
                Log.w("SpeechInput", "Cannot start listening while robot is speaking.")
                return@post
            }

            if (speechRecognizer == null) {
                setupRecognizer()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            try {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged?.invoke(true)
                Log.d("SpeechInput", "startListening called (onDevice=$isOnDevice)")
            } catch (e: Exception) {
                Log.e("SpeechInput", "Exception starting speech recognizer", e)
                isListening = false
                onListeningStateChanged?.invoke(false)
                onError?.invoke(SpeechRecognizer.ERROR_CLIENT, e.localizedMessage ?: "Failed to start listening")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("SpeechInput", "Exception stopping speech recognizer", e)
            }
        }
    }

    fun pauseWakeWordListening() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        if (isWakeWordListening) {
            isWakeWordListening = false
            onWakeWordStateChanged?.invoke(false)
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun resumeWakeWordListening(delayMs: Long = 600L) {
        if (isWakeWordEnabled) {
            schedulePassiveRestart(delayMs)
        }
    }

    fun simulateWakeWord(phrase: String = "Hey RoboPet", command: String? = null) {
        mainHandler.post {
            handleWakeWord(phrase, command)
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening = false
            onListeningStateChanged?.invoke(false)
            if (isWakeWordEnabled) {
                schedulePassiveRestart(delayMs = 1000L)
            }
        }
    }

    /**
     * Simulates speech recognition output (useful for testing dialogue without voice input).
     */
    fun simulateSpeech(text: String) {
        mainHandler.post {
            Log.d("SpeechInput", "Simulated speech result: $text")
            isListening = false
            onListeningStateChanged?.invoke(false)
            onResult?.invoke(text)
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("SpeechInput", "Exception canceling speech recognizer", e)
            } finally {
                isListening = false
                isWakeWordListening = false
                onListeningStateChanged?.invoke(false)
                onWakeWordStateChanged?.invoke(false)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            restartRunnable?.let { mainHandler.removeCallbacks(it) }
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w("SpeechInput", "Exception destroying speech recognizer", e)
            } finally {
                speechRecognizer = null
                isListening = false
                isWakeWordListening = false
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech recognition error ($errorCode)"
        }
    }
}
