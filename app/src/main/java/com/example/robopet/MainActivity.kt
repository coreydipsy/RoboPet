package com.example.robopet

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

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

    val brain = remember { RobotBrain() }
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

    val speechOutput = remember {
        SpeechOutput(context) { isSpeaking, text ->
            brain.send(RobotEvent.SpeakingChanged(isSpeaking, text))
        }
    }

    val speechInput = remember {
        SpeechInput(
            context = context,
            onResult = { text ->
                brain.send(RobotEvent.SpeechRecognized(text))
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
                    // Ensure recognizer is stopped before robot speaks to avoid self-recognition
                    speechOutput.speak(response.text)
                }
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
            canListenProvider = {
                // Prevent recognizing own TTS speech
                !brain.state.value.isSpeaking
            }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted && !brain.state.value.isSpeaking) {
            speechInput.startListening()
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

        onDispose {
            try {
                context.unregisterReceiver(testReceiver)
            } catch (_: Exception) {}
            perception.close()
            speechInput.destroy()
            speechOutput.shutdown()
            onDeviceEngine.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showMemoryDebug) {
            MemoryDebugScreen(
                memoryManager = memoryManager,
                isFirebaseReady = geminiEngine.isFirebaseReady,
                modelName = geminiEngine.modelName,
                engineStatus = currentEngineStatus,
                onDeviceEngine = onDeviceEngine,
                hybridEngine = dialogueEngine,
                speechOutput = speechOutput,
                robotBrain = brain,
                onClose = { showMemoryDebug = false }
            )
        } else {
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
                onMemoryClick = {
                    showMemoryDebug = true
                }
            )

            FaceDebugIndicator(
                detected = state.faceDetected,
                speechNotice = state.speechModeNotice,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
