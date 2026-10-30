package com.example.robopet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDebugScreen(
    memoryManager: MemoryManager,
    isFirebaseReady: Boolean,
    modelName: String,
    engineStatus: String = "",
    onDeviceEngine: OnDeviceDialogueEngine? = null,
    hybridEngine: HybridDialogueEngine? = null,
    speechOutput: SpeechOutput? = null,
    robotBrain: RobotBrain? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Facts", "History", "Summaries", "Voice", "On-Device SLM", "Expressions")

    val facts by memoryManager.getAllFactsFlow().collectAsState(initial = emptyList())
    val messages by memoryManager.getAllMessagesFlow().collectAsState(initial = emptyList())
    val summaries by memoryManager.getAllSummariesFlow().collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()
    var editingFact by remember { mutableStateOf<FactEntity?>(null) }
    var showAddFactDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F121C))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RoboPet Memory Debug",
                        color = Color(0xFF86E5FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFirebaseReady) "Engine: Gemini ($modelName)" else "Engine: Local Offline (Rules + Room)",
                        color = if (isFirebaseReady) Color(0xFF9CFF9C) else Color(0xFFFFC080),
                        fontSize = 11.sp
                    )
                    if (engineStatus.isNotEmpty()) {
                        Text(
                            text = "Status: $engineStatus",
                            color = if (engineStatus.startsWith("Gemini Active") || engineStatus.startsWith("Ready")) Color(0xFF9CFF9C) else Color(0xFFFF9E80),
                            fontSize = 10.sp
                        )
                    }
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26334D))
                ) {
                    Text("Close", color = Color.White)
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF181F30),
                contentColor = Color(0xFF86E5FF)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val label = when (index) {
                                0 -> "$title (${facts.size})"
                                1 -> "$title (${messages.size})"
                                2 -> "$title (${summaries.size})"
                                else -> title
                            }
                            Text(label)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // Facts Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Long-Term Known Facts",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { showAddFactDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("+ Add Fact", fontSize = 12.sp, color = Color.Black)
                        }
                    }

                    if (facts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No facts recorded yet. Chat with RoboPet to teach it facts!", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(facts, key = { it.id }) { fact ->
                                FactItem(
                                    fact = fact,
                                    dateStr = dateFormat.format(Date(fact.timestamp)),
                                    onEdit = { editingFact = fact },
                                    onDelete = {
                                        coroutineScope.launch {
                                            memoryManager.deleteFact(fact.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // History Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conversation History",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (messages.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        memoryManager.clearHistory()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Clear All", fontSize = 12.sp, color = Color(0xFFFF8585))
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No conversation history yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                MessageItem(
                                    message = msg,
                                    dateStr = dateFormat.format(Date(msg.timestamp)),
                                    onDelete = {
                                        coroutineScope.launch {
                                            memoryManager.deleteMessage(msg.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Summaries Tab
                    Text(
                        text = "Conversation Summaries",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (summaries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No summaries created yet. Summaries are auto-generated as conversations progress.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(summaries, key = { it.id }) { summary ->
                                SummaryItem(
                                    summary = summary,
                                    dateStr = dateFormat.format(Date(summary.timestamp)),
                                    onDelete = {
                                        coroutineScope.launch {
                                            memoryManager.deleteSummary(summary.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // Voice Settings Tab
                    if (speechOutput != null) {
                        val currentStyle by speechOutput.voiceStyle.collectAsState()
                        val selectedVoiceName by speechOutput.selectedVoiceName.collectAsState()
                        val engineType by speechOutput.engineType.collectAsState()
                        val piperVoices by speechOutput.piperEngine.availableVoices.collectAsState()
                        val isPiperReady by speechOutput.piperEngine.isReady.collectAsState()

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // TTS Engine Selector
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Voice Synthesis Engine", color = Color(0xFF86E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Piper Neural runs local ONNX models for fluid, human-like voice synthesis with continuous prosody and zero robotic word-boundary pauses.",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        TtsEngineType.entries.forEach { type ->
                                            val isEngineSelected = engineType == type
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isEngineSelected) Color(0xFF1E3A5F) else Color(0xFF111726)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            type.displayName,
                                                            color = if (isEngineSelected) Color(0xFF86E5FF) else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            type.description,
                                                            color = if (type == TtsEngineType.PIPER_NEURAL) Color(0xFF81C784) else Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Button(
                                                        onClick = { speechOutput.switchEngineType(type) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isEngineSelected) Color(0xFF00ADB5) else Color(0xFF26334D)
                                                        )
                                                    ) {
                                                        Text(if (isEngineSelected) "Active ✓" else "Select")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Voice Persona & Tone
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Voice Persona & Tone", color = Color(0xFF86E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Choose how RoboPet speaks to you. Natural styles use warm, human-like pitch and conversational pacing.",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        VoiceStyle.entries.forEach { style ->
                                            val isSelected = currentStyle == style
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF111726)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            style.displayName,
                                                            color = if (isSelected) Color(0xFF86E5FF) else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(style.description, color = Color.Gray, fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = { speechOutput.applyVoiceStyle(style) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSelected) Color(0xFF00ADB5) else Color(0xFF26334D)
                                                        )
                                                    ) {
                                                        Text(if (isSelected) "Active ✓" else "Select")
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                speechOutput.speak("Hello! I am your robot companion, and I love talking with you!")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("🔊 Test Voice Output", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Voice Model Selection
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Voice Model Selection", color = Color(0xFF86E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            if (engineType == TtsEngineType.PIPER_NEURAL)
                                                "Offline neural VITS models running via Piper ONNX. Words are synthesized as continuous speech without word pauses."
                                            else
                                                "Voices from the active Google Speech Services engine.",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        val displayedVoices = if (engineType == TtsEngineType.PIPER_NEURAL) {
                                            SpeechOutput.PIPER_VOICES
                                        } else {
                                            SpeechOutput.GOOGLE_VOICES
                                        }

                                        displayedVoices.forEach { voice ->
                                            val isSelected = selectedVoiceName == voice.voiceId ||
                                                    (selectedVoiceName != null && selectedVoiceName!!.contains(voice.voiceId, ignoreCase = true)) ||
                                                    (selectedVoiceName != null && voice.label.contains(selectedVoiceName!!, ignoreCase = true))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF111726)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            voice.label,
                                                            color = if (isSelected) Color(0xFF86E5FF) else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(voice.description, color = Color.Gray, fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            speechOutput.setVoiceByName(voice.voiceId)
                                                            speechOutput.speak("Hi, I am using the ${voice.label} voice!")
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSelected) Color(0xFF00ADB5) else Color(0xFF26334D)
                                                        )
                                                    ) {
                                                        Text(if (isSelected) "Active ✓" else "Try")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Engine Status Info
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("TTS Status & Info", color = Color(0xFF86E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Engine: ${engineType.displayName}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("Active Voice: ${selectedVoiceName ?: "Default"}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("Pitch: ${currentStyle.pitch}x | Rate: ${currentStyle.rate}x", color = Color.LightGray, fontSize = 12.sp)
                                        if (engineType == TtsEngineType.PIPER_NEURAL) {
                                            Text("Piper Engine Ready: ${if (isPiperReady) "Yes ✓ (22,050Hz Neural)" else "Initializing..."}", color = if (isPiperReady) Color(0xFF81C784) else Color.Yellow, fontSize = 11.sp)
                                            Text("Piper Voices Found: ${piperVoices.size}", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Speech output not available.", color = Color.Gray)
                        }
                    }
                }
                4 -> {
                    // On-Device SLM Tab
                    if (onDeviceEngine != null) {
                        val slmReady by onDeviceEngine.isReady.collectAsState()
                        val slmStatus by onDeviceEngine.status.collectAsState()
                        val downloadState by onDeviceEngine.downloadState.collectAsState()
                        val modelFileExists = onDeviceEngine.isModelAvailable()
                        val modelSizeMb = onDeviceEngine.getModelSizeBytes() / (1024 * 1024)

                        val currentPref = hybridEngine?.enginePreference?.collectAsState()?.value ?: EnginePreference.CLOUD_GEMINI_FAST

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (hybridEngine != null) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Dialogue Engine Routing", color = Color(0xFF86E5FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Select primary intelligence for RoboPet:",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { hybridEngine.setEnginePreference(EnginePreference.CLOUD_GEMINI_FAST) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (currentPref == EnginePreference.CLOUD_GEMINI_FAST) Color(0xFF1A5A90) else Color(0xFF26334D)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Cloud Gemini", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text("Fast (~1.5s)", fontSize = 10.sp, color = Color(0xFF9CFF9C))
                                                    }
                                                }
                                                Button(
                                                    onClick = { hybridEngine.setEnginePreference(EnginePreference.ON_DEVICE_SLM_FIRST) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (currentPref == EnginePreference.ON_DEVICE_SLM_FIRST) Color(0xFF1A5A90) else Color(0xFF26334D)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("On-Device SLM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text("Offline (~15s CPU)", fontSize = 10.sp, color = Color(0xFFFFC080))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("On-Device SLM (LiteRT-LM)", color = Color(0xFF86E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (slmReady) "🟢 Engine Active & Ready" else "🟡 Engine Standby",
                                            color = if (slmReady) Color(0xFF9CFF9C) else Color(0xFFFFC080),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Status: $slmStatus", color = Color.White, fontSize = 12.sp)
                                        Text("Model Path: ${onDeviceEngine.modelFile.name} ($modelSizeMb MB)", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Model Management", color = Color(0xFF86E5FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Model: Qwen3-0.6B (LiteRT-LM format, ~600MB)",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (downloadState.isDownloading) {
                                            Text("Downloading: ${downloadState.statusText}", color = Color(0xFF86E5FF), fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { downloadState.progress },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { onDeviceEngine.startModelDownload() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF264C72))
                                                ) {
                                                    Text(if (modelFileExists) "Re-download Model" else "Download Model (~600MB)")
                                                }

                                                if (modelFileExists) {
                                                    OutlinedButton(
                                                        onClick = { onDeviceEngine.loadModel(onDeviceEngine.modelFile.absolutePath) }
                                                    ) {
                                                        Text("Reload Engine", color = Color.White)
                                                    }
                                                }
                                            }
                                            downloadState.error?.let { err ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Error: $err", color = Color(0xFFFF8080), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Fast Push via USB (ADB)", color = Color(0xFF86E5FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "To transfer instantly from your computer without downloading over mobile data:\n\n" +
                                                    "1. adb push <path>/model.litertlm /data/local/tmp/model.litertlm\n" +
                                                    "2. adb shell run-as com.example.robopet mkdir -p files/models\n" +
                                                    "3. adb shell run-as com.example.robopet cp /data/local/tmp/model.litertlm files/models/model.litertlm",
                                            color = Color(0xFFB0C4DE),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("On-device engine not configured.", color = Color.Gray)
                        }
                    }
                }
                5 -> {
                    // Expressions Preview Tab
                    val robotState = robotBrain?.state?.collectAsState()?.value
                    val currentExpr = robotState?.currentExpression ?: FaceExpression.NEUTRAL

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Rich Facial Expression Preview", color = Color(0xFF86E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Manually trigger and preview any of RoboPet's 6 emotional expressions. Eye gaze tracking and natural blinking continue smoothly during all expressions.",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Active Expression: ${currentExpr.displayName.uppercase()} ${if (robotState?.isSleeping == true) "(Sleeping)" else ""}",
                                        color = Color(0xFF9CFF9C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        items(FaceExpression.entries) { expr ->
                            val isActive = currentExpr == expr && robotState?.isSleeping != true
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Color(0xFF1E3A5F) else Color(0xFF111726)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = expr.displayName,
                                            color = if (isActive) Color(0xFF86E5FF) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = expr.description,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                robotBrain?.setExpression(expr, durationMs = 3500L)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isActive) Color(0xFF00ADB5) else Color(0xFF26334D)
                                            )
                                        ) {
                                            Text(if (isActive) "Active ✓" else "Preview (3.5s)", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                robotBrain?.setExpression(expr, durationMs = null)
                                            }
                                        ) {
                                            Text("Hold", fontSize = 12.sp, color = Color(0xFF86E5FF))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F33)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Sleep / Wake Face Controls", color = Color(0xFF86E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                robotBrain?.send(RobotEvent.DialogueProcessed(DialogueResponse(text = "", action = RobotAction.Sleep)))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26334D)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("😴 Sleep")
                                        }

                                        Button(
                                            onClick = {
                                                robotBrain?.send(RobotEvent.WakeUp)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ADB5)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("☀️ Wake Up")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Edit Fact Dialog
        editingFact?.let { factToEdit ->
            var updatedText by remember(factToEdit) { mutableStateOf(factToEdit.fact) }
            AlertDialog(
                onDismissRequest = { editingFact = null },
                title = { Text("Edit Fact", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = updatedText,
                        onValueChange = { updatedText = it },
                        label = { Text("Fact statement") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            memoryManager.updateFact(factToEdit.copy(fact = updatedText))
                            editingFact = null
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingFact = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF1E2638)
            )
        }

        // Add Fact Dialog
        if (showAddFactDialog) {
            var newFactText by remember { mutableStateOf("") }
            var categoryText by remember { mutableStateOf("preference") }
            AlertDialog(
                onDismissRequest = { showAddFactDialog = false },
                title = { Text("Add New Fact", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFactText,
                            onValueChange = { newFactText = it },
                            label = { Text("Fact (e.g. User likes sushi)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text("Category (preference, user_info, etc.)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newFactText.isNotBlank()) {
                            coroutineScope.launch {
                                memoryManager.saveFact(categoryText, newFactText, "Manually added via debug")
                                showAddFactDialog = false
                            }
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddFactDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF1E2638)
            )
        }
    }
}

@Composable
fun FactItem(
    fact: FactEntity,
    dateStr: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181F30))
            .border(1.dp, Color(0xFF26334D), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fact.category.uppercase(Locale.ROOT),
                    color = Color(0xFF00BCD4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = dateStr, color = Color.Gray, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = fact.fact, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)

            if (fact.sourceSnippet.isNotEmpty()) {
                Text(
                    text = "Source: \"${fact.sourceSnippet}\"",
                    color = Color(0xFF86E5FF).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit", fontSize = 12.sp, color = Color(0xFF86E5FF))
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp, color = Color(0xFFFF8585))
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: ConversationEntity,
    dateStr: String,
    onDelete: () -> Unit
) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isUser) Color(0xFF1C273C) else Color(0xFF142C2B))
            .border(
                1.dp,
                if (isUser) Color(0xFF2E4061) else Color(0xFF204E4B),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "USER" else "ROBOPET",
                    color = if (isUser) Color(0xFF86E5FF) else Color(0xFF9CFF9C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = dateStr, color = Color.Gray, fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("×", color = Color(0xFFFF8585), fontSize = 16.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message.text, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun SummaryItem(
    summary: SummaryEntity,
    dateStr: String,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181F30))
            .border(1.dp, Color(0xFF26334D), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dateStr, color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = summary.summary, color = Color.White, fontSize = 13.sp)
            }
            TextButton(onClick = onDelete) {
                Text("Delete", fontSize = 12.sp, color = Color(0xFFFF8585))
            }
        }
    }
}
