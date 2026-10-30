package com.example.robopet

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PiperVoiceInfo(
    val id: String,
    val name: String,
    val description: String,
    val modelDir: File,
    val onnxFile: File,
    val tokensFile: File,
    val dataDir: File
)

class PiperTtsEngine(
    private val context: Context,
    private val onSpeakingStateChanged: ((Boolean, String?) -> Unit)? = null
) {
    companion object {
        const val TAG = "PiperTtsEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _availableVoices = MutableStateFlow<List<PiperVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<PiperVoiceInfo>> = _availableVoices.asStateFlow()

    private val _selectedVoice = MutableStateFlow<PiperVoiceInfo?>(null)
    val selectedVoice: StateFlow<PiperVoiceInfo?> = _selectedVoice.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var offlineTts: OfflineTts? = null
    private var activeTrack: AudioTrack? = null
    private var currentSpeechJob: Job? = null
    var isSpeaking: Boolean = false
        private set

    init {
        scanAndLoadVoices()
    }

    fun scanAndLoadVoices() {
        val candidateDirs = listOfNotNull(
            context.filesDir.resolve("piper"),
            context.getExternalFilesDir("piper")
        )

        val foundVoices = mutableListOf<PiperVoiceInfo>()
        for (piperDir in candidateDirs) {
            if (!piperDir.exists()) continue
            piperDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val onnx = dir.listFiles { _, name -> name.endsWith(".onnx") }?.firstOrNull()
                    val tokens = dir.resolve("tokens.txt")
                    val dataDir = dir.resolve("espeak-ng-data")

                    if (onnx != null && tokens.exists() && dataDir.exists() && foundVoices.none { it.id == dir.name }) {
                    val isHfc = dir.name.contains("hfc", ignoreCase = true)
                    val isBryce = dir.name.contains("bryce", ignoreCase = true)
                    val isAmy = dir.name.contains("amy", ignoreCase = true)
                    val isRyan = dir.name.contains("ryan", ignoreCase = true)
                    val name = when {
                        isHfc -> "HFC Female (Cheerful & Animated)"
                        isBryce -> "Bryce (Upbeat & Energetic Male)"
                        isAmy -> "Amy (Gentle Warm Female)"
                        isRyan -> "Ryan (Clear Neutral Male)"
                        else -> dir.name
                    }
                    val desc = when {
                        isHfc -> "Bright, energetic, upbeat female tone. Smiling, friendly and expressive. 100% offline."
                        isBryce -> "Positive, enthusiastic male companion tone. 100% offline."
                        isAmy -> "Soft, warm, calm conversational female voice. 100% offline."
                        isRyan -> "Clear, grounded natural male tone. 100% offline."
                        else -> "Local Piper neural voice model."
                    }
                        foundVoices.add(
                            PiperVoiceInfo(
                                id = dir.name,
                                name = name,
                                description = desc,
                                modelDir = dir,
                                onnxFile = onnx,
                                tokensFile = tokens,
                                dataDir = dataDir
                            )
                        )
                    }
                }
            }
        }

        _availableVoices.value = foundVoices
        Log.i(TAG, "Found ${foundVoices.size} Piper voices across candidate directories")

        val defaultVoice = foundVoices.firstOrNull { it.id.contains("hfc", ignoreCase = true) }
            ?: foundVoices.firstOrNull { it.id.contains("bryce", ignoreCase = true) }
            ?: foundVoices.firstOrNull { it.id.contains("amy", ignoreCase = true) }
            ?: foundVoices.firstOrNull()

        if (defaultVoice != null) {
            loadVoice(defaultVoice)
        }
    }

    private var voiceLoadJob: Job? = null

    fun loadVoice(voice: PiperVoiceInfo) {
        if (_selectedVoice.value?.id == voice.id && _isReady.value) {
            Log.d(TAG, "Piper voice ${voice.name} is already loaded")
            return
        }

        voiceLoadJob?.cancel()
        voiceLoadJob = scope.launch {
            try {
                Log.i(TAG, "Loading Piper voice model: ${voice.name}")
                // noiseScale: 0.85f (higher pitch dynamic variation for lively, smiling tone)
                // noiseScaleW: 0.85f (rhythmic duration variation)
                // lengthScale: 0.88f (perkier, snappier phonemes, eliminates somber/sluggish drag)
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = voice.onnxFile.absolutePath,
                    tokens = voice.tokensFile.absolutePath,
                    dataDir = voice.dataDir.absolutePath,
                    noiseScale = 0.85f,
                    noiseScaleW = 0.85f,
                    lengthScale = 0.88f
                )
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                val ttsConfig = OfflineTtsConfig(model = modelConfig)

                val newTts = OfflineTts(config = ttsConfig)

                synchronized(this@PiperTtsEngine) {
                    try {
                        offlineTts?.free()
                    } catch (_: Exception) {}
                    offlineTts = newTts
                    _selectedVoice.value = voice
                    _isReady.value = true
                }
                Log.i(TAG, "Piper voice ${voice.name} loaded and ready!")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error loading Piper voice model", e)
                }
            }
        }
    }

    fun speak(text: String, rate: Float = 1.0f, onComplete: (() -> Unit)? = null) {
        stop()

        val tts = offlineTts
        if (tts == null) {
            Log.w(TAG, "Piper TTS not ready yet")
            return
        }

        currentSpeechJob = scope.launch {
            try {
                mainHandler.post {
                    isSpeaking = true
                    onSpeakingStateChanged?.invoke(true, text)
                }

                val speed = rate.coerceIn(0.5f, 2.0f)
                val startTime = System.currentTimeMillis()
                val audio = tts.generate(text = text, sid = 0, speed = speed)
                val synthTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Synthesized ${audio.samples.size} samples at ${audio.sampleRate}Hz in ${synthTime}ms")

                if (audio.samples.isEmpty()) {
                    finishSpeaking(onComplete)
                    return@launch
                }

                playSamples(audio.samples, audio.sampleRate)
                finishSpeaking(onComplete)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error in Piper speech generation/playback", e)
                }
                finishSpeaking(onComplete)
            }
        }
    }

    private suspend fun playSamples(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 4)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            synchronized(this@PiperTtsEngine) {
                activeTrack = audioTrack
            }

            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            audioTrack.play()

            // Wait for playback duration
            val durationMs = ((samples.size.toDouble() / sampleRate) * 1000).toLong()
            kotlinx.coroutines.delay(durationMs + 100)

            audioTrack.stop()
            audioTrack.release()
            synchronized(this@PiperTtsEngine) {
                if (activeTrack == audioTrack) activeTrack = null
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "Error during AudioTrack playback", e)
            }
        }
    }

    private fun finishSpeaking(onComplete: (() -> Unit)?) {
        mainHandler.post {
            isSpeaking = false
            onSpeakingStateChanged?.invoke(false, null)
            onComplete?.invoke()
        }
    }

    fun stop() {
        currentSpeechJob?.cancel()
        currentSpeechJob = null
        synchronized(this) {
            try {
                activeTrack?.pause()
                activeTrack?.flush()
                activeTrack?.stop()
                activeTrack?.release()
            } catch (_: Exception) {}
            activeTrack = null
        }
        if (isSpeaking) {
            mainHandler.post {
                isSpeaking = false
                onSpeakingStateChanged?.invoke(false, null)
            }
        }
    }

    fun release() {
        stop()
        synchronized(this) {
            try {
                offlineTts?.free()
            } catch (_: Exception) {}
            offlineTts = null
            _isReady.value = false
        }
    }
}
