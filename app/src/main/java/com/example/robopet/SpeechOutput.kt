package com.example.robopet

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceStyle(
    val displayName: String,
    val pitch: Float,
    val rate: Float,
    val description: String
) {
    CHEERFUL_UPBEAT("Cheerful & Upbeat", 1.1f, 1.15f, "Bright, enthusiastic, happy companion"),
    NATURAL_WARM("Natural Warm", 1.0f, 1.12f, "Friendly, conversational flow"),
    PLAYFUL("Playful & Bouncy", 1.15f, 1.18f, "Energetic, youthful excitement"),
    CALM("Calm & Gentle", 0.95f, 1.0f, "Relaxed, soothing pace"),
    ROBOTIC("Classic Robotic", 1.35f, 1.1f, "Synthetic retro robot")
}

enum class TtsEngineType(val displayName: String, val description: String) {
    PIPER_NEURAL(
        "Piper Neural (On-Device ONNX)",
        "Zero robotic pauses. Fluid, continuous neural prosody (HFC Female, Bryce, Amy & Ryan)."
    ),
    GOOGLE_SYSTEM(
        "Google System TTS",
        "Built-in Android Google Speech Services engine."
    )
}

data class CuratedVoice(
    val voiceId: String,
    val label: String,
    val description: String,
    val engineType: TtsEngineType
)

class SpeechOutput(
    private val context: Context,
    private val onSpeakingStateChanged: ((Boolean, String?) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        const val TAG = "SpeechOutput"
        private const val PREFS_NAME = "robopet_voice_prefs"
        private const val KEY_ENGINE_TYPE = "tts_engine_type"
        private const val KEY_VOICE = "selected_voice"
        private const val KEY_STYLE = "selected_style"

        val PIPER_VOICES = listOf(
            CuratedVoice(
                voiceId = "vits-piper-en_US-hfc_female-medium",
                label = "HFC Female (Cheerful & Animated)",
                description = "Bright, energetic, upbeat female tone. Smiling and expressive. 100% offline.",
                engineType = TtsEngineType.PIPER_NEURAL
            ),
            CuratedVoice(
                voiceId = "vits-piper-en_US-bryce-medium",
                label = "Bryce (Upbeat & Energetic Male)",
                description = "Positive, enthusiastic male companion tone. 100% offline.",
                engineType = TtsEngineType.PIPER_NEURAL
            ),
            CuratedVoice(
                voiceId = "vits-piper-en_US-amy-medium",
                label = "Amy (Gentle Warm Female)",
                description = "Soft, calm conversational female voice. 100% offline.",
                engineType = TtsEngineType.PIPER_NEURAL
            ),
            CuratedVoice(
                voiceId = "vits-piper-en_US-ryan-medium",
                label = "Ryan (Clear Neutral Male)",
                description = "Clear, grounded natural male tone. 100% offline.",
                engineType = TtsEngineType.PIPER_NEURAL
            )
        )

        val GOOGLE_VOICES = listOf(
            CuratedVoice("en-us-x-sfg-network", "Natural Female (Google Assistant)", "Online neural stream, studio quality", TtsEngineType.GOOGLE_SYSTEM),
            CuratedVoice("en-us-x-iob-network", "Natural Male (Warm)", "Smooth connected words, rich male tone", TtsEngineType.GOOGLE_SYSTEM),
            CuratedVoice("en-us-x-iol-network", "Natural Casual (Female)", "Bright conversational flow", TtsEngineType.GOOGLE_SYSTEM),
            CuratedVoice("en-us-x-iom-network", "Natural Deep (Male)", "Clear, resonant speech", TtsEngineType.GOOGLE_SYSTEM),
            CuratedVoice("en-us-x-sfg-local", "Offline Standard Female", "Runs offline without Wi-Fi", TtsEngineType.GOOGLE_SYSTEM),
            CuratedVoice("en-us-x-iob-local", "Offline Standard Male", "Runs offline without Wi-Fi", TtsEngineType.GOOGLE_SYSTEM)
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    val piperEngine = PiperTtsEngine(context, onSpeakingStateChanged)

    private val _engineType = MutableStateFlow(loadSavedEngineType())
    val engineType: StateFlow<TtsEngineType> = _engineType.asStateFlow()

    private var googleTts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isGoogleTtsInitialized = false
    private var pendingSpeech: String? = null
    private var currentText: String? = null

    private val _voiceStyle = MutableStateFlow(loadSavedStyle())
    val voiceStyle: StateFlow<VoiceStyle> = _voiceStyle.asStateFlow()

    private val _googleVoices = MutableStateFlow<List<Voice>>(emptyList())
    val googleVoices: StateFlow<List<Voice>> = _googleVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(loadInitialVoiceName())
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    init {
        // Upgrade any default/sad Amy selection to cheerful HFC Female
        val savedVoice = prefs.getString(KEY_VOICE, null)
        val isOldOrSadVoice = savedVoice == null || savedVoice.contains("amy", ignoreCase = true)
        if (_engineType.value == TtsEngineType.PIPER_NEURAL && isOldOrSadVoice) {
            val cheerful = piperEngine.availableVoices.value.firstOrNull { it.id.contains("hfc", ignoreCase = true) }
            if (cheerful != null) {
                piperEngine.loadVoice(cheerful)
                _selectedVoiceName.value = cheerful.name
                prefs.edit().putString(KEY_VOICE, cheerful.id).apply()
            }
        }
    }

    private fun loadInitialVoiceName(): String? {
        val saved = prefs.getString(KEY_VOICE, null)
        if (saved == null || saved.contains("amy", ignoreCase = true)) {
            val cheerful = piperEngine.availableVoices.value.firstOrNull { it.id.contains("hfc", ignoreCase = true) }
            if (cheerful != null) return cheerful.name
        }
        return saved
    }

    val isSpeaking: Boolean
        get() = piperEngine.isSpeaking || (googleTts != null && _isGoogleSpeaking)

    private var _isGoogleSpeaking = false

    private fun loadSavedEngineType(): TtsEngineType {
        val saved = prefs.getString(KEY_ENGINE_TYPE, null)
        if (saved != null) {
            try {
                return TtsEngineType.valueOf(saved)
            } catch (_: Exception) {}
        }
        return TtsEngineType.PIPER_NEURAL
    }

    private fun loadSavedStyle(): VoiceStyle {
        val styleName = prefs.getString(KEY_STYLE, null) ?: return VoiceStyle.CHEERFUL_UPBEAT
        return try {
            VoiceStyle.valueOf(styleName)
        } catch (_: Exception) {
            VoiceStyle.CHEERFUL_UPBEAT
        }
    }

    fun switchEngineType(type: TtsEngineType) {
        if (_engineType.value == type) return
        stop()
        _engineType.value = type
        prefs.edit().putString(KEY_ENGINE_TYPE, type.name).apply()
        Log.i(TAG, "Switched TTS engine type to: ${type.displayName}")

        if (type == TtsEngineType.PIPER_NEURAL) {
            val piperVoice = piperEngine.selectedVoice.value
            _selectedVoiceName.value = piperVoice?.name ?: "HFC Female (Cheerful & Animated)"
        } else {
            findAndApplyBestGoogleVoice()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = googleTts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                googleTts?.setLanguage(Locale.getDefault())
            }

            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .build()
                googleTts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set audio attributes", e)
            }

            findAndApplyBestGoogleVoice()
            applyVoiceStyle(_voiceStyle.value)

            googleTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        _isGoogleSpeaking = true
                        onSpeakingStateChanged?.invoke(true, currentText)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        _isGoogleSpeaking = false
                        val finishedText = currentText
                        currentText = null
                        onSpeakingStateChanged?.invoke(false, finishedText)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        _isGoogleSpeaking = false
                        currentText = null
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        Log.e(TAG, "TTS Utterance error code: $errorCode")
                        _isGoogleSpeaking = false
                        currentText = null
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                }
            })

            isGoogleTtsInitialized = true
            Log.d(TAG, "Google TextToSpeech successfully initialized")

            pendingSpeech?.let { text ->
                pendingSpeech = null
                speak(text)
            }
        } else {
            Log.e(TAG, "Failed to initialize Google TextToSpeech, status: $status")
        }
    }

    private fun findAndApplyBestGoogleVoice() {
        try {
            val voices = googleTts?.voices ?: emptySet()
            val englishVoices = voices
                .filter { it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) }
                .sortedByDescending { it.quality }

            _googleVoices.value = englishVoices

            val savedVoice = _selectedVoiceName.value
            val preferredVoice = englishVoices.firstOrNull { it.name == savedVoice }
                ?: englishVoices.firstOrNull { it.name == "en-us-x-sfg-network" }
                ?: englishVoices.firstOrNull { it.name == "en-us-x-iob-network" }
                ?: englishVoices.firstOrNull { it.name.contains("en-us-x-", ignoreCase = true) && it.isNetworkConnectionRequired }
                ?: englishVoices.firstOrNull { it.quality >= Voice.QUALITY_HIGH }
                ?: googleTts?.defaultVoice

            if (preferredVoice != null) {
                googleTts?.voice = preferredVoice
                if (_engineType.value == TtsEngineType.GOOGLE_SYSTEM) {
                    _selectedVoiceName.value = preferredVoice.name
                }
                Log.i(TAG, "Selected Google TTS voice: ${preferredVoice.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring Google TTS voices", e)
        }
    }

    fun applyVoiceStyle(style: VoiceStyle) {
        _voiceStyle.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
        googleTts?.setPitch(style.pitch)
        googleTts?.setSpeechRate(style.rate)
        Log.i(TAG, "Applied voice style: ${style.displayName} (pitch=${style.pitch}, rate=${style.rate})")
    }

    fun setVoiceByName(voiceIdOrName: String) {
        // Check Piper voices first
        val piperMatch = piperEngine.availableVoices.value.firstOrNull {
            it.id == voiceIdOrName || it.name.contains(voiceIdOrName, ignoreCase = true) || voiceIdOrName.contains(it.id, ignoreCase = true)
        }
        if (piperMatch != null) {
            switchEngineType(TtsEngineType.PIPER_NEURAL)
            piperEngine.loadVoice(piperMatch)
            _selectedVoiceName.value = piperMatch.name
            prefs.edit().putString(KEY_VOICE, piperMatch.id).apply()
            Log.i(TAG, "Switched to Piper voice: ${piperMatch.name}")
            return
        }

        // Fallback to Google voice
        val targetGoogleVoice = _googleVoices.value.firstOrNull { it.name == voiceIdOrName }
        if (targetGoogleVoice != null) {
            switchEngineType(TtsEngineType.GOOGLE_SYSTEM)
            googleTts?.voice = targetGoogleVoice
            _selectedVoiceName.value = targetGoogleVoice.name
            prefs.edit().putString(KEY_VOICE, targetGoogleVoice.name).apply()
            Log.i(TAG, "Switched to Google voice: ${targetGoogleVoice.name}")
        }
    }

    private var speechWatchdogRunnable: Runnable? = null

    fun speak(text: String) {
        val sanitized = text
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace("\uFFFD", "")
            .filter { it.code in 32..126 || it.code in 160..255 || it.isWhitespace() }
            .trim()

        speechWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }

        if (sanitized.isEmpty()) {
            stop()
            return
        }

        currentText = sanitized

        // 1. Clean sound effect tags like *whirr*, *chirp*, *beep*, *gasp*, *yawn*
        var cleanedSpeech = sanitized.replace(Regex("\\*[^*]+\\*"), "").trim()

        // 2. Smooth out unnatural punctuation pauses
        cleanedSpeech = cleanedSpeech
            .replace(Regex("\\.{2,}"), ", ")
            .replace(Regex("!{2,}"), "!")
            .replace(Regex("\\?{2,}"), "?")
            .replace(Regex("\\s+"), " ")
            .trim()

        val speechToUtter = if (cleanedSpeech.isNotEmpty()) {
            cleanedSpeech
        } else {
            sanitized.replace("*", "").trim()
        }

        val rate = _voiceStyle.value.rate

        if (_engineType.value == TtsEngineType.PIPER_NEURAL && piperEngine.isReady.value) {
            Log.i(TAG, "Routing speech to Piper Neural engine: '$speechToUtter'")
            piperEngine.speak(speechToUtter, rate = rate)
            return
        }

        // Fallback to Google TTS
        if (!isGoogleTtsInitialized) {
            Log.d(TAG, "Google TTS not ready yet. Queuing text: $speechToUtter")
            pendingSpeech = speechToUtter
            return
        }

        _isGoogleSpeaking = true
        mainHandler.post { onSpeakingStateChanged?.invoke(true, sanitized) }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = googleTts?.speak(speechToUtter, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "googleTts.speak returned non-success code: $result")
            _isGoogleSpeaking = false
            currentText = null
            mainHandler.post { onSpeakingStateChanged?.invoke(false, null) }
            return
        }

        val wordCount = speechToUtter.split("\\s+".toRegex()).size
        val timeoutMs = ((wordCount / 2.0f) * 1000L).toLong() + 4000L
        val watchdog = Runnable {
            if (_isGoogleSpeaking) {
                Log.w(TAG, "TTS speech watchdog triggered. Resetting isSpeaking.")
                _isGoogleSpeaking = false
                currentText = null
                onSpeakingStateChanged?.invoke(false, null)
            }
        }
        speechWatchdogRunnable = watchdog
        mainHandler.postDelayed(watchdog, timeoutMs.coerceAtLeast(3000L))
    }

    fun stop() {
        speechWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        speechWatchdogRunnable = null
        piperEngine.stop()
        try {
            googleTts?.stop()
        } catch (_: Exception) {}
        _isGoogleSpeaking = false
        currentText = null
        mainHandler.post {
            onSpeakingStateChanged?.invoke(false, null)
        }
    }

    fun shutdown() {
        speechWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        speechWatchdogRunnable = null
        piperEngine.release()
        try {
            googleTts?.stop()
            googleTts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down Google TTS", e)
        } finally {
            googleTts = null
            isGoogleTtsInitialized = false
            _isGoogleSpeaking = false
        }
    }
}
