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
    var canListenProvider: () -> Boolean = { true }
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isOnDevice: Boolean = false

    var isListening: Boolean = false
        private set

    init {
        mainHandler.post {
            setupRecognizer()
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
                Log.d("SpeechInput", "onReadyForSpeech")
                isListening = true
                onListeningStateChanged?.invoke(true)
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechInput", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged?.invoke(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechInput", "onEndOfSpeech")
            }

            override fun onError(errorCode: Int) {
                isListening = false
                onListeningStateChanged?.invoke(false)
                val errorMessage = getErrorMessage(errorCode)
                Log.w("SpeechInput", "Speech recognition error ($errorCode): $errorMessage")
                onError?.invoke(errorCode, errorMessage)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                onListeningStateChanged?.invoke(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                Log.d("SpeechInput", "onResults: $recognizedText")
                if (!recognizedText.isNullOrEmpty()) {
                    onResult?.invoke(recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()?.trim()
                if (!partialText.isNullOrEmpty()) {
                    onPartialResult?.invoke(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        mainHandler.post {
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
                // Cancel previous session to ensure fresh state
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

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            isListening = false
            onListeningStateChanged?.invoke(false)
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
                onListeningStateChanged?.invoke(false)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w("SpeechInput", "Exception destroying speech recognizer", e)
            } finally {
                speechRecognizer = null
                isListening = false
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue"
            SpeechRecognizer.ERROR_CLIENT -> "Client application error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection issue"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            else -> "Recognition issue (code $errorCode)"
        }
    }
}
