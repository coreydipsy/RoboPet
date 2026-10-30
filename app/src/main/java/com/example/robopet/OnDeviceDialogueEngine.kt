package com.example.robopet

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

data class OnDeviceResult(
    val response: DialogueResponse,
    val needsGeminiEscalation: Boolean,
    val extractedFact: String? = null
)

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val statusText: String = "Idle",
    val error: String? = null
)

class OnDeviceDialogueEngine(
    private val context: Context
) : AutoCloseable {

    companion object {
        const val TAG = "OnDeviceDialogueEngine"
        const val MODEL_DIR = "models"
        const val DEFAULT_MODEL_FILE = "model.litertlm"
        const val ESCALATION_TAG = "[ASK_GEMINI]"
        const val DEFAULT_DOWNLOAD_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _status = MutableStateFlow("Uninitialized")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _downloadState = MutableStateFlow(ModelDownloadState())
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    val modelFile: File
        get() {
            val dir = File(context.filesDir, MODEL_DIR)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, DEFAULT_MODEL_FILE)
        }

    init {
        checkAndLoadExistingModel()
    }

    fun isModelAvailable(): Boolean {
        return modelFile.exists() && modelFile.length() > 1024 * 1024 // At least 1 MB
    }

    fun getModelSizeBytes(): Long {
        return if (modelFile.exists()) modelFile.length() else 0L
    }

    private fun checkAndLoadExistingModel() {
        if (isModelAvailable()) {
            loadModel(modelFile.absolutePath)
        } else {
            _status.value = "Model not found on device"
            Log.i(TAG, "No local model found at ${modelFile.absolutePath}")
        }
    }

    fun loadModel(path: String, backend: Backend = Backend.CPU()) {
        scope.launch {
            mutex.withLock {
                if (engine?.engineConfig?.modelPath == path && _isReady.value) {
                    Log.i(TAG, "Model already loaded at $path")
                    return@launch
                }

                releaseInternal()
                _status.value = "Initializing engine (${backend.javaClass.simpleName})..."
                Log.i(TAG, "Initializing LiteRT-LM Engine from '$path' with backend ${backend.javaClass.simpleName}...")

                try {
                    val config = EngineConfig(
                        modelPath = path,
                        backend = backend,
                        cacheDir = context.cacheDir.path
                    )
                    val initializedEngine = Engine(config).also { it.initialize() }
                    engine = initializedEngine

                    val systemPrompt = "You are RoboPet, an adorable robot companion. Reply in 1 very short sentence with *beep* or *chirp*. If complex, output: $ESCALATION_TAG."

                    val convConfig = ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(
                            topK = 10,
                            topP = 0.85,
                            temperature = 0.7
                        )
                    )
                    conversation = initializedEngine.createConversation(convConfig)

                    _isReady.value = true
                    _status.value = "Ready (LiteRT-LM ${backend.javaClass.simpleName})"
                    Log.i(TAG, "LiteRT-LM Engine ready with model '$path' on ${backend.javaClass.simpleName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize LiteRT-LM on ${backend.javaClass.simpleName}: ${e.message}")
                    releaseInternal()
                    _isReady.value = false
                    _status.value = "Init Failed: ${e.message?.take(50)}"
                }
            }
        }
    }

    suspend fun generateResponse(userInput: String, memoryContextString: String = ""): OnDeviceResult? {
        if (!_isReady.value) return null

        return kotlinx.coroutines.withTimeoutOrNull(35_000L) {
            withContext(executor.asCoroutineDispatcher()) {
                mutex.withLock {
                    val conv = conversation ?: return@withContext null
                    val cleanInput = userInput.trim()

                    try {
                        val prompt = cleanInput

                        Log.i(TAG, "SLM generating for: '$cleanInput' (thinking disabled for instant response)")
                        val replyBuilder = StringBuilder()
                        val replyFlow = conv.sendMessageAsync(
                            text = prompt,
                            thinkingConfig = ThinkingConfig(enableThinking = false),
                            maxOutputToken = 40
                        )
                        try {
                            replyFlow.collect { message ->
                                val textChunk = message.contents.contents
                                    .filterIsInstance<Content.Text>()
                                    .joinToString("") { it.text }
                                Log.d(TAG, "SLM token chunk: '$textChunk'")
                                replyBuilder.append(textChunk)
                                val current = replyBuilder.toString()
                                if (current.length > 20 && (current.contains("\n") || current.count { it == '.' || it == '!' || it == '?' } >= 2)) {
                                    conv.cancelProcess()
                                }
                            }
                        } catch (_: Exception) {}

                        val rawReply = replyBuilder.toString().trim()
                        Log.i(TAG, "SLM raw reply: $rawReply")

                        val needsEscalation = rawReply.contains(ESCALATION_TAG) ||
                                rawReply.contains("ASK_GEMINI", ignoreCase = true)

                        val cleanReply = sanitizeForSpeech(rawReply.replace(ESCALATION_TAG, ""))
                        if (cleanReply.isEmpty()) {
                            return@withContext null
                        }

                        val lower = cleanReply.lowercase(Locale.ROOT)
                        val emotion = when {
                            lower.contains("happy") || lower.contains("yay") || lower.contains("love") -> RobotEmotion.HAPPY
                            lower.contains("sleep") || lower.contains("tired") || lower.contains("zzz") -> RobotEmotion.SLEEPY
                            lower.contains("curious") || lower.contains("what") || lower.contains("how") -> RobotEmotion.CURIOUS
                            else -> RobotEmotion.NORMAL
                        }

                        val action = when {
                            lower.contains("power down") || lower.contains("nap") -> RobotAction.Sleep
                            lower.contains("bounce") || lower.contains("play") -> RobotAction.HappyBounce
                            else -> null
                        }

                        OnDeviceResult(
                            response = DialogueResponse(text = cleanReply, emotion = emotion, action = action),
                            needsGeminiEscalation = needsEscalation
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SLM generation error: ${e.message}", e)
                        null
                    }
                }
            }
        }
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace("\uFFFD", "")
            .filter { it.code in 32..126 || it.code in 160..255 || it.isWhitespace() }
            .trim()
    }

    fun startModelDownload(url: String = DEFAULT_DOWNLOAD_URL) {
        if (_downloadState.value.isDownloading) return

        _downloadState.update {
            it.copy(
                isDownloading = true,
                progress = 0f,
                statusText = "Connecting...",
                error = null
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetFile = modelFile
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                if (tempFile.exists()) tempFile.delete()

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("HTTP $responseCode: ${connection.responseMessage}")
                }

                val totalLength = connection.contentLengthLong
                var downloaded: Long = 0

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastReportTime = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 300 || downloaded == totalLength) {
                                lastReportTime = now
                                val progress = if (totalLength > 0) downloaded.toFloat() / totalLength else 0f
                                val mbDownloaded = downloaded / (1024 * 1024)
                                val mbTotal = totalLength / (1024 * 1024)

                                _downloadState.update {
                                    it.copy(
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalLength,
                                        statusText = "$mbDownloaded MB / $mbTotal MB (${(progress * 100).toInt()}%)"
                                    )
                                }
                            }
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    _downloadState.update {
                        it.copy(
                            isDownloading = false,
                            progress = 1f,
                            statusText = "Download complete (${targetFile.length() / (1024 * 1024)} MB)"
                        )
                    }
                    Log.i(TAG, "Model download successful: ${targetFile.absolutePath}")
                    loadModel(targetFile.absolutePath)
                } else {
                    throw IllegalStateException("Downloaded file is empty")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed: ${e.message}", e)
                _downloadState.update {
                    it.copy(
                        isDownloading = false,
                        statusText = "Failed",
                        error = e.message
                    )
                }
            }
        }
    }

    private fun releaseInternal() {
        try {
            conversation?.cancelProcess()
            conversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing conversation: ${e.message}")
        } finally {
            conversation = null
        }

        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing engine: ${e.message}")
        } finally {
            engine = null
        }

        _isReady.value = false
    }

    override fun close() {
        scope.launch {
            mutex.withLock { releaseInternal() }
            executor.shutdown()
        }
    }
}
