package com.example.robopet

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RobotApp()
        }
    }
}

@Composable
fun RobotApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var brainHolder by remember { mutableStateOf<RobotBrain?>(null) }
    var speechInputHolder by remember { mutableStateOf<SpeechInput?>(null) }
    val speechOutput = remember {
        SpeechOutput(context) { isSpeaking, text ->
            brainHolder?.send(RobotEvent.SpeakingChanged(isSpeaking, text))
            if (isSpeaking) {
                speechInputHolder?.pauseWakeWordListening()
            } else {
                speechInputHolder?.resumeWakeWordListening(delayMs = 800L)
            }
        }
    }
    val brain = remember {
        RobotBrain(context) { text ->
            speechOutput.speak(text)
        }.also { brainHolder = it }
    }
    val perception = remember { CameraPerception(context) }
    val memoryManager = remember { MemoryManager(context) }
    val geminiEngine = remember { GeminiDialogueEngine(context, memoryManager) }
    val onDeviceEngine = remember { OnDeviceDialogueEngine(context) }
    val dialogueEngine = remember {
        HybridDialogueEngine(
            onDeviceEngine = onDeviceEngine,
            geminiEngine = geminiEngine,
            memoryManager = memoryManager
        )
    }

    val state by brain.state.collectAsState()
    val currentEngineStatus by dialogueEngine.currentEngineStatus.collectAsState()
    var showMemoryDebug by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    fun processSpeechOrCommand(text: String) {
        val lower = text.lowercase(Locale.ROOT).trim()
        when {
            lower.contains("rock paper scissors") || lower.contains("play rock") -> {
                brain.send(RobotEvent.StartRockPaperScissors)
                return
            }
            lower.contains("start pomodoro") || lower.contains("start focus") || lower.contains("focus mode") -> {
                brain.send(RobotEvent.StartPomodoro(25))
                return
            }
            lower.contains("stop timer") || lower.contains("stop focus") || lower.contains("cancel timer") || lower.contains("stop pomodoro") -> {
                brain.send(RobotEvent.StopPomodoro)
                return
            }
            lower.contains("take a break") || lower.contains("start break") -> {
                brain.send(RobotEvent.StartBreak(5))
                return
            }
        }

        brain.send(RobotEvent.ThinkingChanged(true))
        coroutineScope.launch {
            val response = dialogueEngine.processInput(
                input = text,
                isSleeping = brain.state.value.isSleeping
            )
            brain.send(RobotEvent.ThinkingChanged(false))
            brain.send(RobotEvent.DialogueProcessed(response))
            val statusText = dialogueEngine.currentEngineStatus.value
            brain.send(RobotEvent.SpeechModeInfo("Using on-device speech recognition. | $statusText"))
            speechOutput.speak(response.text)
        }
    }

    val speechInput = remember {
        SpeechInput(
            context = context,
            onResult = { text ->
                brain.send(RobotEvent.SpeechRecognized(text))
                processSpeechOrCommand(text)
            },
            onListeningStateChanged = { isListening ->
                brain.send(RobotEvent.ListeningChanged(isListening))
            },
            onError = { _, errorMsg ->
                brain.send(RobotEvent.SpeechError(errorMsg))
            },
            onModeNotice = { notice ->
                val combinedNotice = "$notice | ${dialogueEngine.currentEngineStatus.value}"
                brain.send(RobotEvent.SpeechModeInfo(combinedNotice))
            },
            onRmsChanged = { rms ->
                brain.send(RobotEvent.AudioLevelChanged(rms))
            },
            onWakeWordStateChanged = { isListening ->
                brain.send(RobotEvent.WakeWordStateChanged(isListening))
            },
            onWakeWordDetected = { phrase, command ->
                brain.send(RobotEvent.WakeWordTriggered(phrase, command))
                if (!command.isNullOrBlank()) {
                    brain.send(RobotEvent.SpeechRecognized(command))
                    processSpeechOrCommand(command)
                } else {
                    speechOutput.speak("I'm listening!")
                    speechInputHolder?.startListening()
                }
            },
            canListenProvider = {
                // Prevent recognizing own TTS speech
                !brain.state.value.isSpeaking
            }
        ).also { speechInputHolder = it }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted && !brain.state.value.isSpeaking) {
            speechInput.startListening()
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            speechInput.resumeWakeWordListening(delayMs = 1200L)
        }
    }

    LaunchedEffect(Unit) {
        brain.run()
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            perception.start(lifecycleOwner) { event ->
                brain.send(event)
            }
        } else {
            perception.stop()
        }
    }

    LaunchedEffect(state.isSpeaking, state.isListening, state.isThinking, state.isSleeping, state.miniGameState.isRpsActive) {
        perception.isRpsActive = state.miniGameState.isRpsActive
        perception.isHeavyComputeActive = if (state.miniGameState.isRpsActive) false else (state.isSpeaking || state.isListening || state.isThinking)
        perception.isSleeping = state.isSleeping
    }

    DisposableEffect(Unit) {
        val testReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val speech = intent?.getStringExtra("speech")
                if (!speech.isNullOrEmpty()) {
                    speechInput.simulateSpeech(speech)
                }
            }
        }
        val filter = IntentFilter("com.example.robopet.TEST_SPEECH")
        ContextCompat.registerReceiver(
            context,
            testReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (scale > 0) (level * 100 / scale) else level
                brain.send(RobotEvent.BatteryChanged(isCharging, pct))
            }
        }
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            batteryFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(testReceiver)
            } catch (_: Exception) {}
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
            perception.close()
            speechInput.destroy()
            speechOutput.shutdown()
            onDeviceEngine.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RobotFace(
            state = state,
            onPet = {
                brain.send(RobotEvent.Petted)
            },
            onTapScreen = {
                // Interrupt TTS immediately if speaking so RoboPet stops talking and starts listening
                if (state.isSpeaking) {
                    speechOutput.stop()
                }

                if (!hasAudioPermission) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    if (state.isListening) {
                        speechInput.stopListening()
                    } else {
                        speechInput.startListening()
                    }
                }
            },
            onStartFocus = {
                if (state.pomodoroState.mode == PomodoroMode.FOCUS) {
                    brain.send(RobotEvent.StopPomodoro)
                } else {
                    brain.send(RobotEvent.StartPomodoro(25))
                }
            },
            onStartRps = {
                if (state.miniGameState.isRpsActive) {
                    brain.send(RobotEvent.StopRockPaperScissors)
                } else {
                    brain.send(RobotEvent.StartRockPaperScissors)
                }
            },
            onSelectRpsChoice = { choice ->
                brain.send(RobotEvent.SelectRpsChoice(choice))
            },
            onMemoryClick = {
                showMemoryDebug = true
            }
        )

        // Apple-style Glassmorphic Modal Sheet for Companion Studio & Memory
        if (showMemoryDebug) {
            MemoryDebugScreen(
                memoryManager = memoryManager,
                isFirebaseReady = geminiEngine.isFirebaseReady,
                modelName = geminiEngine.modelName,
                engineStatus = currentEngineStatus,
                onDeviceEngine = onDeviceEngine,
                hybridEngine = dialogueEngine,
                speechOutput = speechOutput,
                speechInput = speechInput,
                robotBrain = brain,
                cameraPerception = perception,
                onClose = { showMemoryDebug = false }
            )
        }
    }
}
