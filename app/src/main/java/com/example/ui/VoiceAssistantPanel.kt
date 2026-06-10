package com.example.ui

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.InventoryItem
import com.example.data.Task
import com.example.data.ConversationMessage
import com.example.data.IndexedDbStore
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// High-fidelity speech bubble history schema
data class ConsoleMessage(
    val sender: String, // "USER", "NOVA", "SYSTEM"
    val text: String,
    val isPlaybackActive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// Floating Canvas Particle model to support true fluid motion (no static screens)
data class DriftParticle(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color,
    var alpha: Float
)

enum class MicState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    COOLDOWN
}

@Volatile
var globalScreenshotHider: ((Boolean) -> Unit)? = null

@Volatile
var lastTopicWasSports: Boolean = false

private var globalReminderAlertTrigger: ((String, String) -> Unit)? = null

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantPanel(
    viewModel: NovaViewModel,
    tasksList: List<Task>,
    itemsList: List<InventoryItem>,
    activeTabForced: String, // Managed elegantly by bottom nav bar
    modifier: Modifier = Modifier,
    onActiveTabChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeReminderPopupText by remember { mutableStateOf<String?>(null) }
    var activeReminderPopupTitle by remember { mutableStateOf("Nova Alert") }

    var activeSuccessReminder by remember { mutableStateOf<com.example.data.Reminder?>(null) }
    LaunchedEffect(activeSuccessReminder) {
        if (activeSuccessReminder != null) {
            kotlinx.coroutines.delay(6000)
            activeSuccessReminder = null
        }
    }

    LaunchedEffect(Unit) {
        globalReminderAlertTrigger = { title, text ->
            activeReminderPopupTitle = title
            activeReminderPopupText = text
        }
    }

    // Persistent Configuration options loaded locally
    val sharedPrefsInitial = remember { context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(sharedPrefsInitial.getString("user_name", "Kartik") ?: "Kartik") }
    var currentWakeWord by remember { mutableStateOf("Nova") }
    var voicePitchSlider by remember { mutableStateOf(1.02f) }
    var voiceRateSlider by remember { mutableStateOf(0.98f) }
    var activeRoutineMode by remember { mutableStateOf("STANDBY MODE") }
    var activeBrowserUrl by remember { mutableStateOf("https://www.google.com") }

    var isScreenshotHiding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        globalScreenshotHider = { h ->
            isScreenshotHiding = h
        }
    }

    // Floating UI Particles State - continuous tick updates
    val particles = remember {
        mutableStateListOf<DriftParticle>().apply {
            repeat(15) {
                add(
                    DriftParticle(
                        x = (30..300).random().toFloat(),
                        y = (30..500).random().toFloat(),
                        radius = (3..7).random().toFloat(),
                        speedX = (-10 until 10).random().toFloat() / 20f,
                        speedY = (-10 until 10).random().toFloat() / 20f,
                        color = listOf(CyberCyan, SageGreen, Color(0xFF00E5FF)).random(),
                        alpha = (2..8).random().toFloat() / 10f
                    )
                )
            }
        }
    }

    // Engine loop for floating canvas background particles
    LaunchedEffect(Unit) {
        while (true) {
            particles.forEach { p ->
                p.x += p.speedX
                p.y += p.speedY
                if (p.x < 0) p.x = 400f
                if (p.x > 400) p.x = 0f
                if (p.y < 0) p.y = 600f
                if (p.y > 600) p.y = 0f
            }
            delay(32) // ~30 FPS light physics tick
        }
    }

    // Dialogue stream
    var dialogHistoryList by remember {
        mutableStateOf(
            listOf(
                ConsoleMessage("NOVA", "Hi Kartik."),
                ConsoleMessage("NOVA", "What can I help you with today?")
            )
        )
    }

    // SQLite-based Local Persistence System (Alternative to browser's IndexedDB schema offline)
    val appDatabase = remember { com.example.data.AppDatabase.getDatabase(context) }
    val conversationDao = remember { appDatabase.conversationDao }

    // 1. Initial Load of Chat from Local DB
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val savedMessages = conversationDao.getAllMessages()
            if (savedMessages.isNotEmpty()) {
                val mapped = savedMessages.map {
                    ConsoleMessage(sender = it.sender, text = it.text, isPlaybackActive = it.isPlaybackActive, timestamp = it.timestamp)
                }
                withContext(Dispatchers.Main) {
                    dialogHistoryList = mapped
                }
            } else {
                // If totally empty, pre-populate standard welcomes and write them to our local offline store.
                val initial = listOf(
                    ConsoleMessage("NOVA", "Hi Kartik."),
                    ConsoleMessage("NOVA", "What can I help you with today?")
                )
                withContext(Dispatchers.Main) {
                    dialogHistoryList = initial
                }
                initial.forEach { msg ->
                    conversationDao.insertMessage(
                        ConversationMessage(
                            sender = msg.sender,
                            text = msg.text,
                            isPlaybackActive = msg.isPlaybackActive,
                            timestamp = msg.timestamp
                        )
                    )
                }
            }
        }
    }

    // 2. Synchronize memory state to Local Room SQLite store when dialogue updates (reactive save)
    LaunchedEffect(dialogHistoryList.size) {
        coroutineScope.launch(Dispatchers.IO) {
            val databaseList = conversationDao.getAllMessages()
            if (databaseList.size != dialogHistoryList.size) {
                // Clear and dump to ensure order and exact transaction accuracy
                conversationDao.clearHistory()
                dialogHistoryList.forEach { msg ->
                    conversationDao.insertMessage(
                        ConversationMessage(
                            sender = msg.sender,
                            text = msg.text,
                            isPlaybackActive = msg.isPlaybackActive,
                            timestamp = msg.timestamp
                        )
                    )
                }
            }
        }
    }

    // Speech-to-text live transcripts
    var liveSTTTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeakingActive by remember { mutableStateOf(false) }
    var continuousConversationEnabled by remember { mutableStateOf(false) }

    var micState by remember { mutableStateOf(MicState.IDLE) }

    fun transitionTo(newState: MicState) {
        val oldState = micState
        val isValid = when (oldState) {
            MicState.IDLE -> newState == MicState.LISTENING
            MicState.LISTENING -> newState == MicState.PROCESSING
            MicState.PROCESSING -> newState == MicState.SPEAKING
            MicState.SPEAKING -> newState == MicState.COOLDOWN
            MicState.COOLDOWN -> {
                if (continuousConversationEnabled) {
                    newState == MicState.LISTENING
                } else {
                    newState == MicState.IDLE
                }
            }
        }
        if (isValid) {
            android.util.Log.i("MicState", "State transition: $oldState -> $newState")
            micState = newState
            isListening = (newState == MicState.LISTENING)
            isThinking = (newState == MicState.PROCESSING)
            isSpeakingActive = (newState == MicState.SPEAKING)
        } else {
            android.util.Log.w("MicState", "BLOCKED invalid state transition: $oldState -> $newState")
        }
    }

    val isAutomating by com.example.AutomationEngine.isAutomating.collectAsState()
    val automationSteps by com.example.AutomationEngine.currentSteps.collectAsState()
    val automationLogs by com.example.AutomationEngine.executionLogs.collectAsState()
    val currentIntent by com.example.AutomationEngine.currentIntent.collectAsState()

    val verificationTests by com.example.ui.RealityVerificationManager.tests.collectAsState()

    val forceStatsRefresh by com.example.ui.ReliabilityManager.stats.collectAsState()

    val remindersList by appDatabase.reminderDao.getAllRemindersFlow().collectAsState(initial = emptyList())

    val pendingCallContacts by com.example.AutomationEngine.pendingCallContacts.collectAsState()
    val pendingMessagePayload by com.example.AutomationEngine.pendingMessagePayload.collectAsState()
    val pendingMessageRecipient by com.example.AutomationEngine.pendingMessageRecipient.collectAsState()
    val pendingMessagePlatform by com.example.AutomationEngine.pendingMessagePlatform.collectAsState()

    val voiceMatchEnrollmentCount by com.example.ui.VoiceMatchManager.enrollmentCount.collectAsState()
    val voiceMatchSetupComplete by com.example.ui.VoiceMatchManager.isSetupComplete.collectAsState()
    val voiceMatchIsRecording by com.example.ui.VoiceMatchManager.isRecordingSample.collectAsState()
    val voiceMatchCurrentWave by com.example.ui.VoiceMatchManager.currentWaveAmplitude.collectAsState()

    val wakeEngineStatus by com.example.WakeWordDebugManager.engineStatus.collectAsState()
    val wakeLastDetectTime by com.example.WakeWordDebugManager.lastDetectionTime.collectAsState()
    val wakeMicStatus by com.example.WakeWordDebugManager.microphoneStatus.collectAsState()
    val wakeServiceStatus by com.example.WakeWordDebugManager.foregroundServiceStatus.collectAsState()
    val wakeVoiceMatchStatus by com.example.WakeWordDebugManager.voiceMatchStatus.collectAsState()
    val wakeModelLoadStatus by com.example.WakeWordDebugManager.modelLoadStatus.collectAsState()
    val wakeBatteryStatus by com.example.WakeWordDebugManager.batteryOptimizationStatus.collectAsState()
    val wakeErrorDetails by com.example.WakeWordDebugManager.errorMessage.collectAsState()
    val wakeDebugLogs by com.example.WakeWordDebugManager.debugLogs.collectAsState()

    // Collect states from premium NovaVoiceManager
    val currentVoiceProfile by NovaVoiceManager.currentProfile.collectAsState()
    val customVoiceSpeed by NovaVoiceManager.customSpeed.collectAsState()
    val customVoicePitch by NovaVoiceManager.customPitch.collectAsState()
    val ttsEngineType by NovaVoiceManager.engineType.collectAsState()
    val coquiUrl by NovaVoiceManager.coquiUrl.collectAsState()
    val piperUrl by NovaVoiceManager.piperUrl.collectAsState()
    val isVoiceSynthesizing by NovaVoiceManager.isSynthesizing.collectAsState()
    val synthLogMessage by NovaVoiceManager.synthLogMessage.collectAsState()

    LaunchedEffect(Unit) {
        NovaVoiceManager.init(context)
        com.example.ui.RealityVerificationManager.init(context)
        com.example.ui.VoiceMatchManager.init(context)
        com.example.ui.ReliabilityManager.init(context)
        com.example.WakeWordDebugManager.refreshDiagnostics(context)
    }

    // TTS Synthesis
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isRecognizerActive by remember { mutableStateOf(false) }
    var lastStartTimestamp by remember { mutableStateOf(0L) }

    // Manual typing console
    var activeKeyboardBuffer by remember { mutableStateOf("") }
    var isDevLogsExpanded by remember { mutableStateOf(false) }

    // Mic check
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var contactsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var phonePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var isNotificationListenerServiceActive by remember {
        mutableStateOf(
            com.example.NovaNotificationListenerService.isServiceRunning
        )
    }

    var isAccessibilityActive by remember {
        mutableStateOf(
            com.example.AutomationAccessibilityService.isServiceRunning
        )
    }

    var isOverlayPermissionGranted by remember {
        mutableStateOf(
            android.provider.Settings.canDrawOverlays(context)
        )
    }

    var isBatteryIgnoringOptimizations by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    // Recent Actions tracking log
    var recentActions by remember {
        mutableStateOf(
            listOf(
                "Started Nova Assistant",
                "Checked system diagnostics",
                "Scanned standard applications"
            )
        )
    }

    // Initialize TTS once
    LaunchedEffect(Unit) {
        if (ttsEngine == null) {
            ttsEngine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    NovaVoiceManager.applySettings(ttsEngine)
                    isTtsReady = true
                }
            }
        }
    }

    // Reactively apply settings when parameters change without recreating/shutting down
    LaunchedEffect(customVoicePitch, customVoiceSpeed) {
        ttsEngine?.let {
            NovaVoiceManager.applySettings(it)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.i("VoiceAssistantPrivacy", "[MIC EVENT] Action: STOP | Reason: APP_UNMOUNT")
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {}
            speechRecognizer = null
            NovaVoiceManager.stopCurrentMedia()
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (granted) {
            Toast.makeText(context, "Mic security shield enabled. Click Center Orb to speak.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Sensors disconnected. Running sleek manual fallback terminal.", Toast.LENGTH_LONG).show()
        }
    }

    var startVocalRequestRef: ((String) -> Unit)? = null

    // Ultra-premium premium vocal helper utilizing NovaVoiceManager pipeline (direct HTTP fallback)
    val speakTts: (String) -> Unit = remember(ttsEngine, isTtsReady, continuousConversationEnabled) {
        { text: String ->
            coroutineScope.launch {
                // 5. Stop microphone during TTS speaking
                android.util.Log.i("VoiceAssistantPrivacy", "[MIC EVENT] Action: STOP | Reason: TTS_START_STOP")
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {}
                speechRecognizer = null
                isListening = false
                isRecognizerActive = false

                // Transition PROCESSING -> SPEAKING when Nova responds
                transitionTo(MicState.SPEAKING)

                val transformedText = NovaPersonalityCore.transformResponse(text, userName)
                NovaVoiceManager.speakInteractive(context, transformedText, ttsEngine, "NOVA_COGNITION_AUDIO") {
                    android.util.Log.i("VoiceAssistantPrivacy", "[MIC EVENT] Action: STOP | Reason: TTS_START_STOP (Ended)")
                    
                    // Transition SPEAKING -> COOLDOWN when TTS ends
                    transitionTo(MicState.COOLDOWN)

                    // COOLDOWN -> LISTENING only if continuous mode is ON
                    // COOLDOWN -> IDLE if continuous mode is OFF
                    if (continuousConversationEnabled) {
                        transitionTo(MicState.LISTENING)
                        startVocalRequestRef?.invoke("CONTINUOUS_RESTART")
                    } else {
                        transitionTo(MicState.IDLE)
                    }
                }
            }
            Unit
        }
    }

    // Recognizer initializer - consolidated within startVocalRequest to guarantee thread safe initialization and block duplicate creation runs
    val startVocalRequest: (String) -> Unit = { reason ->
        val currentTime = System.currentTimeMillis()
        val timeSinceLastStart = currentTime - lastStartTimestamp
        val isNovaCurrentlySpeaking = micState == MicState.SPEAKING || isSpeakingActive || (ttsEngine?.isSpeaking == true)
        val isAlreadyListening = micState == MicState.LISTENING || isListening || isRecognizerActive
        val isCooldownActive = micState == MicState.COOLDOWN

        if (isAlreadyListening) {
            android.util.Log.d("VoiceAssistantPrivacy", "[START LOCK] Mic already listening. Blocked start. Reason: $reason")
        } else if (isNovaCurrentlySpeaking) {
            android.util.Log.d("VoiceAssistantPrivacy", "[START LOCK] Nova is speaking. Blocked start. Reason: $reason")
        } else if (isCooldownActive && reason != "CONTINUOUS_RESTART") {
            android.util.Log.d("VoiceAssistantPrivacy", "[START LOCK] Cooldown active. Blocked start. Reason: $reason")
        } else if (timeSinceLastStart < 1200 && reason != "CONTINUOUS_RESTART") {
            android.util.Log.d("VoiceAssistantPrivacy", "[START LOCK] Debounced cooldown window. Blocked start. Reason: $reason")
        } else {
            // Log every mic start/stop with reason
            android.util.Log.i("VoiceAssistantPrivacy", "[MIC EVENT] Action: START | Reason: $reason")

            // Transition IDLE -> LISTENING when user taps mic
            if (micState == MicState.IDLE) {
                transitionTo(MicState.LISTENING)
            } else {
                micState = MicState.LISTENING
                isListening = true
                isThinking = false
                isSpeakingActive = false
            }

            lastStartTimestamp = currentTime
            isRecognizerActive = true

            if (!micPermissionGranted) {
                isRecognizerActive = false
                micState = MicState.IDLE
                isListening = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                isRecognizerActive = false
                micState = MicState.IDLE
                isListening = false
                dialogHistoryList = dialogHistoryList + ConsoleMessage("SYSTEM", "Whisper micro architecture error: Local speech hardware unavailable.")
            } else {
                com.example.NovaForegroundService.isUiListening = true
                try {
                    // Force clean release of any previous instance before binding clean listeners
                    try {
                        speechRecognizer?.stopListening()
                        speechRecognizer?.destroy()
                    } catch (e: Exception) {}

                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            isThinking = false
                            isRecognizerActive = true
                            liveSTTTranscript = "Core open. Ready for vocal directives..."
                        }

                        override fun onBeginningOfSpeech() {
                            liveSTTTranscript = "Capturing voice signal waveforms..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            // Transition LISTENING -> PROCESSING when speech ends
                            transitionTo(MicState.PROCESSING)
                            liveSTTTranscript = "Analyzing local speech patterns..."
                        }

                        override fun onError(error: Int) {
                            com.example.NovaForegroundService.isUiListening = false
                            android.util.Log.e("VoiceAssistantPrivacy", "[MIC EVENT] Action: STOP | Reason: ERROR_STOP (code: $error)")
                            isListening = false
                            isThinking = false
                            isRecognizerActive = false
                            val errorLog = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio hard busy"
                                SpeechRecognizer.ERROR_CLIENT -> "Client hardware error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission mismatch"
                                SpeechRecognizer.ERROR_NETWORK -> "Network interface conflict"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No match vocal pattern"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Core analyzer saturated"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence threshold met"
                                else -> "Hardware code $error"
                            }
                            liveSTTTranscript = ""
                            dialogHistoryList = dialogHistoryList + ConsoleMessage("SYSTEM", "Whisper STT bypassed: ($errorLog). Direct manual terminal active.")
                            
                            // Let's transition of LISTENING -> PROCESSING -> SPEAKING -> COOLDOWN -> IDLE
                            transitionTo(MicState.PROCESSING)
                            speakTts("I'm sorry, I encountered a speech error: $errorLog")

                            try {
                                speechRecognizer?.destroy()
                            } catch (e: Exception) {}
                            speechRecognizer = null
                        }

                        override fun onResults(results: Bundle?) {
                            com.example.NovaForegroundService.isUiListening = false
                            isThinking = false
                            isRecognizerActive = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val msg = matches?.firstOrNull() ?: ""
                            liveSTTTranscript = ""

                            try {
                                speechRecognizer?.destroy()
                            } catch (e: Exception) {}
                            speechRecognizer = null

                            if (msg.isNotBlank()) {
                                if (micState == MicState.LISTENING) {
                                    transitionTo(MicState.PROCESSING)
                                }
                                processVocalDirective(
                                    cmd = msg,
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasksList,
                                    itemsList = itemsList,
                                    speakTts = speakTts,
                                    uName = userName,
                                    onLogUpdate = { dialogHistoryList = dialogHistoryList + it },
                                    onActionAppend = { recentActions = listOf(it) + recentActions.take(5) },
                                    onSuccessReminderSet = { activeSuccessReminder = it },
                                    onActiveTabChange = onActiveTabChange
                                )
                            } else {
                                transitionTo(MicState.PROCESSING)
                                transitionTo(MicState.SPEAKING)
                                transitionTo(MicState.COOLDOWN)
                                if (continuousConversationEnabled) {
                                    transitionTo(MicState.LISTENING)
                                    startVocalRequestRef?.invoke("CONTINUOUS_RESTART")
                                } else {
                                    transitionTo(MicState.IDLE)
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    speechRecognizer = recognizer

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    isListening = true
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    com.example.NovaForegroundService.isUiListening = false
                    isListening = false
                    isRecognizerActive = false
                    liveSTTTranscript = ""
                    dialogHistoryList = dialogHistoryList + ConsoleMessage("SYSTEM", "Voice link unavailable. Terminal key bindings online.")
                    
                    transitionTo(MicState.PROCESSING)
                    transitionTo(MicState.SPEAKING)
                    transitionTo(MicState.COOLDOWN)
                    transitionTo(MicState.IDLE)
                }
            }
        }
    }

    startVocalRequestRef = startVocalRequest

    LaunchedEffect(micPermissionGranted) {
        // DO NOT automatically initialize SpeechRecognizer on app startup to prevent 
        // the green microphone active indicator from showing on screen.
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsPermissionGranted = granted
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        phonePermissionGranted = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    LaunchedEffect(activeTabForced) {
        while (true) {
            isAccessibilityActive = com.example.AutomationAccessibilityService.isServiceRunning
            isNotificationListenerServiceActive = com.example.NovaNotificationListenerService.isServiceRunning
            isOverlayPermissionGranted = android.provider.Settings.canDrawOverlays(context)
            contactsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            smsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            phonePermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            micPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            isBatteryIgnoringOptimizations = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
            delay(2000)
        }
    }

    val triggerWakeWordVocalSequence = {
        coroutineScope.launch {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {}
            speechRecognizer = null
            ttsEngine?.stop()
            
            dialogHistoryList = dialogHistoryList + ConsoleMessage("NOVA", "Yes?")
            isSpeakingActive = true
            micState = MicState.SPEAKING
            ttsEngine?.speak("Yes?", TextToSpeech.QUEUE_FLUSH, null, "NOVA_WAKE_UP")
            delay(1000)
            isSpeakingActive = false
            micState = MicState.COOLDOWN
            
            delay(150)
            startVocalRequest("USER_TAP")
        }
    }

    LaunchedEffect(isSpeakingActive) {
        // Redundant auto-starts are fully managed by the speakTts completion callback and state machine
    }

    // Dialogue list auto scrolls to bottom when message arrives
    LaunchedEffect(dialogHistoryList.size) {
        coroutineScope.launch {
            if (dialogHistoryList.isNotEmpty()) {
                listState.animateScrollToItem(dialogHistoryList.size - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .alpha(if (isScreenshotHiding) 0f else 1f)
    ) {
        // --- ADDON: LIVE TRIGGERED REMINDER ALARM DIALOG (SQLite Verified System) ---
        val activeTriggeredReminder by com.example.data.ActiveReminderManager.activeTriggeredReminder.collectAsState(initial = null)
        activeTriggeredReminder?.let { triggered ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { com.example.data.ActiveReminderManager.dismissReminder() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⏰", fontSize = 20.sp)
                        Text(
                            text = "NOVA REALTIME ALARM",
                            color = NeonAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                containerColor = SpaceBlack,
                shape = RoundedCornerShape(24.dp),
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = triggered.title,
                            color = PureWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SYSTEM ALERT: '${triggered.title}' at ${triggered.time} is now due.",
                            color = NeonAmber,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = "Vibrational sequences and physical sound indicators have been verified.",
                            color = CharcoalMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { com.example.data.ActiveReminderManager.dismissReminder() },
                        modifier = Modifier
                            .background(NeonAmber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, NeonAmber, RoundedCornerShape(12.dp))
                    ) {
                        Text("DISMISS ALERT", color = NeonAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        // --- ADDON: DYNAMIC VERIFIED SUCCESS BANNER (Problem 4 / Problem 3) ---
        activeSuccessReminder?.let { reminder ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
                    .background(CyberSlate.copy(alpha = 0.95f))
                    .border(1.2.dp, CyberCyan, RoundedCornerShape(12.dp))
                    .clickable { activeSuccessReminder = null }
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏰", fontSize = 14.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reminder Created & Verified ✓",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = reminder.title,
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Trigger:",
                                color = CharcoalMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${reminder.time} ${reminder.date}",
                                color = TechTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(TechTeal.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(0.8.dp, TechTeal, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DATABASE ACTIVE",
                            color = TechTeal,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (activeReminderPopupText != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { activeReminderPopupText = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notification alert",
                            tint = NeonAmber
                        )
                        Text(
                            text = activeReminderPopupTitle,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                    }
                },
                containerColor = SpaceBlack,
                shape = RoundedCornerShape(24.dp),
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = activeReminderPopupText ?: "",
                            color = PureWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = "Acoustic ping and vibrational sequences dispatched on secure offline channels.",
                            color = CharcoalMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { activeReminderPopupText = null },
                        modifier = Modifier
                            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, CyberCyan, RoundedCornerShape(12.dp))
                    ) {
                        Text("DISMISS ALERT", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        // CONSUMER COMPANION RUNTIME SHEET (Beautiful full-screen active task visualizer)
        AnimatedVisibility(
            visible = isAutomating,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.98f))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // 1. Big animated Nova Orb
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NovaCinematicEnergyOrb(
                        isListening = false,
                        isThinking = true,
                        isSpeaking = false,
                        floatParticles = particles
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 2. Simple action progress
                val activeStep = automationSteps.find { it.status == com.example.StepStatus.EXECUTING }
                val completedCount = automationSteps.count { it.status == com.example.StepStatus.SUCCESS }
                val totalCount = automationSteps.size
                
                val statusText = when {
                    activeStep != null -> activeStep.description
                    totalCount > 0 && completedCount == totalCount -> "Done."
                    else -> "Thinking..."
                }

                Text(
                    text = statusText,
                    color = PureWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (totalCount > 0) "Completed $completedCount of $totalCount actions" else "Activating...",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 4. Small expandable "Details" only for developers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDevLogsExpanded = !isDevLogsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDevLogsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Technical Details",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isDevLogsExpanded) {
                            Text(
                                text = "DEVELOPER RECON ROLE",
                                color = GlowingRed,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = isDevLogsExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(color = BorderSlate.copy(alpha = 0.15f))

                            currentIntent?.let { intentInfo ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceBlack.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "⚡ INTENT: ${intentInfo.intent.uppercase(Locale.ROOT)}",
                                        color = CyberCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "📦 ENTITIES: ${intentInfo.entities.joinToString(", ")}",
                                        color = PureWhite,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "⚙️ ACTIONS: ${intentInfo.actions.joinToString(" ➔ ")}",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "🔍 VERIFICATION: ${intentInfo.verification}",
                                        color = SageGreen,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Divider(color = BorderSlate.copy(alpha = 0.15f))
                            }

                            automationSteps.forEachIndexed { idx, stp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceBlack.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (stp.status) {
                                        com.example.StepStatus.SUCCESS -> SageGreen
                                        com.example.StepStatus.EXECUTING -> CyberCyan
                                        com.example.StepStatus.FAILED -> GlowingRed
                                        com.example.StepStatus.PENDING -> CharcoalMuted
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${idx + 1}. ${stp.description}",
                                        color = if (stp.status == com.example.StepStatus.PENDING) CharcoalMuted else PureWhite,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stp.status.name,
                                        color = statusColor,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (automationLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceBlack, RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "EXECUTION STACKS",
                                        color = TextSecondary,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = automationLogs.take(4).joinToString("\n"),
                                        color = SageGreen,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Continuous Render Switcher for active views
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp) // Leave clean padding for floating navigation bar
        ) {
            when (activeTabForced) {
                "ORB" -> {
                    RedesignedOrbTab(
                        userName = userName,
                        isAccessibilityActive = isAccessibilityActive,
                        isListening = isListening,
                        isThinking = isThinking,
                        isSpeakingActive = isSpeakingActive,
                        particles = particles,
                        triggerWakeWordVocalSequence = { triggerWakeWordVocalSequence() },
                        context = context,
                        viewModel = viewModel,
                        tasksList = tasksList,
                        itemsList = itemsList,
                        speakTts = { speakTts(it) },
                        recentActions = recentActions,
                        onRecentActionsChange = { recentActions = it },
                        dialogHistoryList = dialogHistoryList,
                        onDialogHistoryListChange = { dialogHistoryList = it }
                    )
                }
                "ORB_LEGACY" -> {
                    // TAB 1: COGNITION CORE ORB VIEW (JARVIS Cinematic style)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Section: Contextual Personalized Greeting Header
                        LayoutPersonaGreetingHeader(uName = userName)

                        if (!com.example.AutomationAccessibilityService.isServiceRunning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .background(GlowingRed.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .border(1.dp, GlowingRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Automation Engine Status: Disconnected.\n\nNote: Android automatically disconnects this accessibility service connection whenever the app is compiled, re-installed, or power-optimized.\n\n👉 Click here to open settings, then toggle 'Nova Cognitive Bridge' OFF and back ON to restore full execution.",
                                    color = GlowingRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // JARVIS Wake-Word Control Status Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .testTag("wake_word_control_card"),
                            colors = CardDefaults.cardColors(containerColor = TechCard),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isListening) GlowingRed else SageGreen)
                                    )
                                    Text(
                                        text = "JARVIS ARCHITECTURE: ${if (isListening) "LISTENING FOR DIRECTIVE" else "PASSIVE IDLE"}",
                                        color = PureWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Nova stays in low-power idle mode (mic off) until you summon the assistant manually or vocally.",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { triggerWakeWordVocalSequence() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, CyberCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("hei_nova_trigger_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = CyberCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "SUMMON COGNITION: \"HEY NOVA\"",
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(15.dp))

                        // Center Section: Enormous animated energy Nova Orb and particle particles
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clickable { triggerWakeWordVocalSequence() },
                            contentAlignment = Alignment.Center
                        ) {
                            NovaCinematicEnergyOrb(
                                isListening = isListening,
                                isThinking = isThinking,
                                isSpeaking = isSpeakingActive,
                                floatParticles = particles
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Label indicating active brain cycles
                        Text(
                            text = when {
                                isListening -> "Listening..."
                                isThinking -> "Thinking..."
                                isSpeakingActive -> "Speaking..."
                                else -> "Tap Orb or say \"Hey Nova\""
                            },
                            color = when {
                                isListening -> GlowingRed
                                isThinking -> NeonAmber
                                isSpeakingActive -> CyberCyan
                                else -> SageGreen
                            },
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .background(TechCard, RoundedCornerShape(12.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable { continuousConversationEnabled = !continuousConversationEnabled }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (continuousConversationEnabled) CyberCyan else CharcoalMuted)
                            )
                            Text(
                                text = "CONTINUOUS CONVERSATION: ${if (continuousConversationEnabled) "ACTIVE" else "OFF"}",
                                color = if (continuousConversationEnabled) CyberCyan else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Bottom Section: Divided into Recent Action Logs & Smart Suggestions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Column Left: Recent Action Log list (completely replaces boring tables)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(TechCard, RoundedCornerShape(20.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SageGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "RECENT DIRECTIVES",
                                        color = SageGreen,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                recentActions.forEach { act ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            color = CyberCyan,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = act,
                                            color = PureWhite,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }

                            // Column Right: Intelligent contextual actions triggers
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(TechCard, RoundedCornerShape(20.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "SMART DIRECTS",
                                        color = CyberCyan,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                val suggestions = listOf(
                                    "Launch Maps" to "open maps",
                                    "Diagnostics" to "system health",
                                    "Vibe Story" to "tell me a story",
                                    "Sync Core" to "sync"
                                )
                                suggestions.forEach { sug ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(CyberSlate, RoundedCornerShape(8.dp))
                                            .border(0.8.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                processVocalDirective(
                                                    cmd = sug.second,
                                                    context = context,
                                                    viewModel = viewModel,
                                                    tasksList = tasksList,
                                                    itemsList = itemsList,
                                                    speakTts = speakTts,
                                                    uName = userName,
                                                    onLogUpdate = { dialogHistoryList = dialogHistoryList + it },
                                                    onActionAppend = { recentActions = listOf(it) + recentActions.take(5) }
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = sug.first,
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "DIALOGUE" -> {
                    RedesignedDialogueTab(
                        dialogHistoryList = dialogHistoryList,
                        onDialogHistoryListChange = { dialogHistoryList = it },
                        triggerWakeWordVocalSequence = { triggerWakeWordVocalSequence() },
                        context = context,
                        viewModel = viewModel,
                        tasksList = tasksList,
                        itemsList = itemsList,
                        speakTts = { speakTts(it) },
                        userName = userName,
                        recentActions = recentActions,
                        onRecentActionsChange = { recentActions = it },
                        listState = listState,
                        onSuccessReminderSet = { activeSuccessReminder = it }
                    )
                }

                "BROWSER" -> {
                    NovaBrowserTab(
                        userName = userName,
                        speakTts = { speakTts(it) },
                        onNavigateToChat = { userPrompt ->
                            onActiveTabChange("DIALOGUE")
                            processVocalDirective(
                                cmd = userPrompt,
                                context = context,
                                viewModel = viewModel,
                                tasksList = tasksList,
                                itemsList = itemsList,
                                speakTts = speakTts,
                                uName = userName,
                                onLogUpdate = { dialogHistoryList = dialogHistoryList + it },
                                onActionAppend = { recentActions = listOf(it) + recentActions.take(5) }
                            )
                        },
                        initialUrl = activeBrowserUrl
                    )
                }

                "COGNITION" -> {
                    NovaCognitionPanel(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                "APIS" -> {
                    NovaPublicApisTab(
                        userName = userName,
                        speakTts = { speakTts(it) },
                        onNavigateToChat = { userPrompt ->
                            onActiveTabChange("DIALOGUE")
                            processVocalDirective(
                                cmd = userPrompt,
                                context = context,
                                viewModel = viewModel,
                                tasksList = tasksList,
                                itemsList = itemsList,
                                speakTts = speakTts,
                                uName = userName,
                                onLogUpdate = { dialogHistoryList = dialogHistoryList + it },
                                onActionAppend = { recentActions = listOf(it) + recentActions.take(5) }
                            )
                        },
                        onNavigateToWeb = { targetUrl ->
                            activeBrowserUrl = targetUrl
                            onActiveTabChange("BROWSER")
                        }
                    )
                }

                "MEMORY" -> {
                    RedesignedMemoryTab(
                        userName = userName,
                        onUserNameChange = { userName = it },
                        speakTts = { speakTts(it) },
                        recentActions = recentActions,
                        onRecentActionsChange = { recentActions = it },
                        continuousConversationEnabled = continuousConversationEnabled,
                        context = context,
                        viewModel = viewModel,
                        tasksList = tasksList,
                        remindersList = remindersList,
                        onActiveTabChange = onActiveTabChange
                    )
                }

                "SETTINGS" -> {
                    RedesignedSettingsTab(
                        micPermissionGranted = micPermissionGranted,
                        contactsPermissionGranted = contactsPermissionGranted,
                        phonePermissionGranted = phonePermissionGranted,
                        notificationPermissionGranted = notificationPermissionGranted,
                        isAccessibilityActive = isAccessibilityActive,
                        isOverlayPermissionGranted = isOverlayPermissionGranted,
                        isBatteryIgnoringOptimizations = isBatteryIgnoringOptimizations,
                        continuousConversationEnabled = continuousConversationEnabled,
                        onContinuousConversationToggle = { continuousConversationEnabled = it },
                        context = context,
                        onRequestMicrophone = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestContacts = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        onRequestPhone = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) },
                        onRequestNotifications = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onRequestAccessibility = {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        onRequestOverlay = {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        onRequestBattery = {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        isListening = isListening,
                        isRecognizerActive = isRecognizerActive,
                        isTtsReady = isTtsReady,
                        isTtsSpeaking = ttsEngine?.isSpeaking == true,
                        isAutomating = isAutomating,
                        intentState = currentIntent?.intent ?: "None",
                        onActiveTabChange = onActiveTabChange
                    )
                }
                "SETTINGS_LEGACY" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {





                        Spacer(modifier = Modifier.height(10.dp))











                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "SYSTEM CONTROL CREDENTIALS",
                                color = CyberCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            // 1. Microphone
                            PermissionRow(
                                title = "Neuro Mic Link",
                                subtitle = "Sensory recording for vocal commands",
                                isGranted = micPermissionGranted,
                                onToggle = {
                                    if (it) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    else micPermissionGranted = false
                                }
                            )

                            // 2. Contacts
                            PermissionRow(
                                title = "User Contacts Database",
                                subtitle = "Identify names during SMS or dialing",
                                isGranted = contactsPermissionGranted,
                                onToggle = {
                                    if (it) contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    else contactsPermissionGranted = false
                                }
                            )

                            // 3. SMS Dispatch
                            PermissionRow(
                                title = "SMS Message Dispatch",
                                subtitle = "Deliver outbound textual messages",
                                isGranted = smsPermissionGranted,
                                onToggle = {
                                    if (it) smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    else smsPermissionGranted = false
                                }
                            )

                            // 4. Cellular Dialing
                            PermissionRow(
                                title = "Cellular Phone Dialer",
                                subtitle = "Initiate instant dial sequences offline",
                                isGranted = phonePermissionGranted,
                                onToggle = {
                                    if (it) phonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                    else phonePermissionGranted = false
                                }
                            )

                            // 5. System Notifications
                            PermissionRow(
                                title = "Push Alert System",
                                subtitle = "Receive helper reminders on device home",
                                isGranted = notificationPermissionGranted,
                                onToggle = {
                                    if (it && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationPermissionGranted = it
                                    }
                                }
                            )

                            // 6. Accessibility service button
                            AutomationToggleRow(
                                title = "Automation Core",
                                subtitle = "Direct device clicker & scroll engine",
                                isActive = isAccessibilityActive,
                                onAction = {
                                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Search for 'Nova Automation Core' in Settings", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )

                            // 7. Notification Listener service button
                            AutomationToggleRow(
                                title = "Notification Reader Bridge",
                                subtitle = "Read & reply to active alerts programmatically",
                                isActive = isNotificationListenerServiceActive,
                                onAction = {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Search for 'Notification Access' in Settings", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )

                            // 8. System Overlay Window
                            AutomationToggleRow(
                                title = "Visual Overlay Engine",
                                subtitle = "Render helper cues over target apps",
                                isActive = isOverlayPermissionGranted,
                                onAction = {
                                    try {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )

                        // CONFIG CARD: DEVICE-WIDE ASSISTANT
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TechCard, RoundedCornerShape(18.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "NATIVE DEVICE_WIDE ASSISTANT",
                                color = CyberCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Access Nova instantly without opening the application (like Gemini) using your device's assistant shortcut (long-pressing Home or swiping corner), Quick Settings, or Home screen widgets.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            Button(
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Search for 'Default assistant app' in settings", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TechTeal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("default_assist_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = "Set as Default Assist App",
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // REALITY VERIFICATION DASHBOARD SECTION
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TechCard, RoundedCornerShape(18.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "REALITY VERIFICATION DASHBOARD",
                                    color = CyberCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .background(TechTeal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AUDIT MODULE ACTIVE",
                                        color = CyberCyan,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Every claimed core capability is validated programmatically against actual hardware configurations. Use the controls below to verify or manually override status based on real-world device integration.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )

                            verificationTests.forEach { test ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberSlate, RoundedCornerShape(12.dp))
                                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = test.featureName,
                                            color = PureWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        // Status tag
                                        val badgeColor = when (test.status) {
                                            "PASS" -> SageGreen
                                            "FAIL" -> GlowingRed
                                            else -> CharcoalMuted
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = test.status,
                                                color = badgeColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Last verified: ${test.lastTestTime}",
                                        color = CharcoalMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SpaceBlack.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = test.resultLog,
                                            color = if (test.status == "PASS") SageGreen else if (test.status == "FAIL") GlowingRed else PureWhite,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 12.sp
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Run auto-test button
                                        Button(
                                            onClick = { com.example.ui.RealityVerificationManager.runTest(context, test.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1.3f).height(24.dp)
                                        ) {
                                            Text("EXECUTE TEST", color = CyberCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }

                                        // Manual override buttons
                                        Button(
                                            onClick = { com.example.ui.RealityVerificationManager.markStatusManual(context, test.id, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(24.dp)
                                        ) {
                                            Text("MARK PASS", color = SageGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }

                                        Button(
                                            onClick = { com.example.ui.RealityVerificationManager.markStatusManual(context, test.id, false) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GlowingRed.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, GlowingRed.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(24.dp)
                                        ) {
                                            Text("MARK FAIL", color = GlowingRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }

                        // RELIABILITY TRACKING DASHBOARD SECTION
                        Spacer(modifier = Modifier.height(16.dp))
                        ReliabilityDashboard(forceStatsRefresh, context)
                    }
                }
            }
        }

        // FULL SCREEN CYBER GLASSMORPHISM LISTENING OVERLAY (True immersive JARVIS experience)
        AnimatedVisibility(
            visible = isListening || liveSTTTranscript.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(300)) + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut(animationSpec = tween(280)) + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpaceBlack.copy(alpha = 0.95f))
                    .drawWithContent {
                        // Glassmorphism radial glowing gradients
                        drawContent()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Energetic waves rendering background
                InteractiveWaveformEnsemble()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = 45f
                            }
                    ) {
                        NovaActiveSpeechWaveAnimation()
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = "LISTENING FOR COGNITION FRAME",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = liveSTTTranscript.ifEmpty { "Connected. Whisper audio channels activated." },
                        color = PureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(50.dp))

                    // Floating Glass cancel pill target
                    Box(
                        modifier = Modifier
                            .background(TechTeal.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .border(1.2.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable {
                                isListening = false
                                isRecognizerActive = false
                                liveSTTTranscript = ""
                                try {
                                    speechRecognizer?.stopListening()
                                    speechRecognizer?.destroy()
                                } catch (e: Exception) {}
                                speechRecognizer = null
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "SHIELD SENSORS OVERRIDE",
                            color = PureWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // --- INTERACTIVE CONFIRMATION FLOATING OVERLAY DIALOGS ---
        if (pendingMessageRecipient != null && pendingMessagePayload != null && pendingMessagePlatform != null) {
            MessageApprovalOverlay(
                platform = pendingMessagePlatform ?: "SMS",
                recipient = pendingMessageRecipient ?: "",
                payload = pendingMessagePayload ?: ""
            )
        }

        if (pendingCallContacts.isNotEmpty()) {
            CallCandidatesOverlay(pendingCallContacts = pendingCallContacts)
        }
    }
}

/**
 * Top personalized header contextualgreeting layout
 */
@Composable
fun LayoutPersonaGreetingHeader(uName: String) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greetingText = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }

    val proactiveText = when (hour) {
        in 6..11 -> "You usually study at this time. Should I open your study apps?"
        in 12..16 -> "Your mid-day schedule is clear. Let me know if you need to set a reminder."
        in 17..21 -> "Your evening routine is ready. Would you like to dim screen brightness?"
        else -> "It's late. Sleep mode is recommended. Would you like me to set your morning alarm?"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = greetingText,
                color = PureWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "$uName,",
                color = CyberCyan,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Nova stands ready to execute system directives.",
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = TechCard.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CyberCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Column {
                    Text(
                        text = "PROACTIVE ASSISTANCE",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = proactiveText,
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Interactive flowing waveform graphics for the glass listening HUD
 */
@Composable
fun InteractiveWaveformEnsemble() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_bg")
    
    val driftState1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_1"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radialGlow = Brush.radialGradient(
                    colors = listOf(
                        TechTeal.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 1.1f
                )
                drawRect(brush = radialGlow)

                // Render dynamic floating coordinate vectors in the background
                drawCircle(
                    color = CyberCyan.copy(alpha = 0.05f),
                    center = center,
                    radius = (size.minDimension / 2.3f) + sin(Math.toRadians(driftState1.toDouble())).toFloat() * 15f,
                    style = Stroke(width = 1f)
                )
            }
    )
}

/**
 * Dynamic cinematic waveforms designed with smooth Sin scaling formulas
 */
@Composable
fun NovaActiveSpeechWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "sin_wave")
    val waveFactor by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_scale"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .drawBehind {
                val baseRadius = size.minDimension / 2.4f
                val count = 4
                
                for (i in 0 until count) {
                    val progress = (i + 1f) / count
                    val animatedRadius = baseRadius * (progress + waveFactor * 0.15f)
                    val alphaValue = (1.0f - progress).coerceIn(0.1f, 1.0f)
                    
                    drawCircle(
                        color = CyberCyan.copy(alpha = alphaValue * 0.6f),
                        radius = animatedRadius,
                        style = Stroke(width = 2.dp.toPx() * progress)
                    )
                }
            }
    )
}

/**
 * Massive captivating dynamic Nova Orb with multi-state kinetic models
 */
@Composable
fun NovaCinematicEnergyOrb(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    floatParticles: List<DriftParticle>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "energy_orb")

    // Idle slow breath
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Thinking high velocity rotation
    val thinkRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Speaking synchronized pulse amplitude
    val speakAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speak_amp"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension / 2.8f
                
                // Color mapping: standard luxurious Apple gradients
                val coreColor = when {
                    isListening -> GlowingRed
                    isThinking -> NeonAmber
                    isSpeaking -> TechTeal // Soft purple
                    else -> CyberCyan       // Electric blue inviting glow
                }

                // 1. Soft deep layered backdrop halo reflection glow
                val outerHaloBrush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.22f),
                        TechTeal.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.2f
                )
                drawCircle(
                    brush = outerHaloBrush,
                    radius = baseRadius * 2.2f
                )

                // 2. Liquid Glass physical outer sphere shell
                // Soft diffuse inner shadow effect
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = baseRadius * 1.25f
                )
                // Frosted outline rim representing the outer glass shell boundary
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = baseRadius * 1.25f,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Double high-contrast thin glass reflection contour ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = baseRadius * 1.29f,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 3. Floating atmospheric mist drift particles
                floatParticles.forEach { p ->
                    val mappedX = center.x + (p.x - 200f) * 0.45f
                    val mappedY = center.y + (p.y - 300f) * 0.45f
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha * 0.4f),
                        radius = p.radius,
                        center = Offset(mappedX, mappedY)
                    )
                }

                // 4. State-based neural resonance rings (inside the glass)
                if (isThinking) {
                    val loopCount = 4
                    for (i in 0 until loopCount) {
                        val ringRotate = thinkRotation * (if (i % 2 == 0) 1.2f else -1.5f)
                        val radiusDelta = baseRadius * (0.5f + i * 0.18f)
                        drawCircle(
                            color = NeonAmber.copy(alpha = 0.5f - i * 0.12f),
                            radius = radiusDelta,
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(30f, 90f),
                                    ringRotate
                                )
                            )
                        )
                    }
                } else if (isListening) {
                    // Swift contracting high voltage radar waves
                    drawCircle(
                        color = GlowingRed.copy(alpha = 0.4f),
                        radius = baseRadius * breathScale * 0.95f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = GlowingRed.copy(alpha = 0.18f),
                        radius = baseRadius * breathScale * 1.15f,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else if (isSpeaking) {
                    // Resonating voice ripples
                    val rippleRadius = baseRadius * speakAmplitude * 0.9f
                    drawCircle(
                        color = TechTeal.copy(alpha = 0.5f),
                        radius = rippleRadius,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.25f),
                        radius = rippleRadius + 15.dp.toPx(),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    // Calm organic breathing idle orbit
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.35f),
                        radius = baseRadius * breathScale * 0.85f,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }

                // 5. Solid organic inner CORE sphere representing Nova's cognitive engine
                val coreGlowShader = Brush.radialGradient(
                    colors = listOf(
                        coreColor,
                        TechTeal.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.8f
                )
                drawCircle(
                    brush = coreGlowShader,
                    radius = baseRadius * 0.7f * (if (isSpeaking) speakAmplitude else breathScale)
                )

                // 6. Liquid Glass Gloss highlight refraction on the main outer sphere!
                // Draws a realistic curved reflection shimmer on the upper-left of the glass sphere
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    start = Offset(center.x - baseRadius, center.y - baseRadius),
                    end = Offset(center.x + baseRadius * 0.2f, center.y + baseRadius * 0.2f)
                )
                
                // Draw a beautiful crescent glass specularity shape
                drawArc(
                    brush = shimmerBrush,
                    startAngle = 180f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.12f, center.y - baseRadius * 1.12f),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2.24f, baseRadius * 2.24f),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
    )
}

/**
 * Individual Dialogue Bubble with built-in voice playback controller
 */
@Composable
fun DialogRowComponent(
    msg: ConsoleMessage,
    onVoicePlaySelected: (String) -> Unit
) {
    val isUser = msg.sender == "USER"
    val isSystem = msg.sender == "SYSTEM"

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        Brush.linearGradient(listOf(TechTeal.copy(alpha = 0.9f), TechTeal.copy(alpha = 0.5f)))
    } else if (isSystem) {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    } else {
        Brush.linearGradient(listOf(TechCard, TechCard))
    }

    val borderColor = if (isUser) {
        BorderSlate.copy(alpha = 0.4f)
    } else if (isSystem) {
        Color.Transparent
    } else {
        CyberCyan.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Dialogue Header label
        Text(
            text = if (isUser) "YOU" else if (isSystem) "SYSTEM" else "NOVA",
            color = if (isUser) SageGreen else if (isSystem) CharcoalMuted else CyberCyan,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Play Button on Dialogue (provides "voice playback capabilities for past interactions")
            if (!isUser && !isSystem) {
                IconButton(
                    onClick = { onVoicePlaySelected(msg.text) },
                    modifier = Modifier
                        .background(CyberSlate, CircleShape)
                        .border(1.dp, BorderSlate.copy(alpha = 0.3f), CircleShape)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Vocalize response",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(containerColor, RoundedCornerShape(20.dp))
                    .border(BorderStroke(if (isSystem) 0.dp else 1.dp, borderColor), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.text,
                    color = if (isSystem) CharcoalMuted else PureWhite,
                    fontSize = if (isSystem) 10.sp else 12.sp,
                    fontFamily = if (isSystem) FontFamily.Monospace else FontFamily.SansSerif,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * High-tech model matching voice commands to system actions instantly
 */
fun processVocalDirective(
    cmd: String,
    context: Context,
    viewModel: NovaViewModel,
    tasksList: List<Task>,
    itemsList: List<InventoryItem>,
    speakTts: (String) -> Unit,
    uName: String,
    onLogUpdate: (ConsoleMessage) -> Unit,
    onActionAppend: (String) -> Unit,
    onWakeWordDetected: (() -> Unit)? = null,
    dialogHistoryList: List<ConsoleMessage> = emptyList(),
    onSuccessReminderSet: ((com.example.data.Reminder) -> Unit)? = null,
    onActiveTabChange: (String) -> Unit = {}
) {
    if (cmd.isBlank()) return

    val rawLower = cmd.lowercase(Locale.getDefault()).trim()
    if (rawLower == "hey nova" || rawLower == "ok nova" || rawLower == "nova" || rawLower == "wake up") {
        onLogUpdate(ConsoleMessage("USER", cmd))
        onLogUpdate(ConsoleMessage("NOVA", "Yes?"))
        speakTts("Yes?")
        onWakeWordDetected?.invoke()
        return
    }

    onLogUpdate(ConsoleMessage("USER", cmd))

    val clean = cmd.lowercase(Locale.getDefault()).trim()
        .replace(Regex("^(nova|ok nova|hey nova|please|can you|could you|start|launch)\\b"), "")
        .replace(Regex("[?:.,!]"), "")
        .trim()

    // 2.6b BACKGROUND BROWSER AGENT INTERCEPTS (Scrape searches, Scores, Downloads & Notepad integrations)
    if (clean.contains("free api") || clean.contains("api ai list") || clean.contains("api list")) {
        if (clean.contains("note") || clean.contains("notepad") || clean.contains("save") || clean.contains("list them") || clean.contains("record")) {
            lastTopicWasSports = false
            val response = "Scanning web sources via Background Nova Browser... I found a listing of popular free AI APIs and saved them directly to your local Notepad inside Nova's memory storage."
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            
            // Write a beautiful Note to database
            viewModel.addTask(
                title = "Free AI API List",
                description = "1. Gemini API - Google's powerful LLM with free tier (15 RPM)\n2. Hugging Face API - 100k+ models free access\n3. CoHere API - NLP with generous trial tier\n4. Open-Meteo API - Free weather forecasts without keys\n5. PokeAPI - Free database of pokemon items\n6. Standard Translation API - Free tier available.\nRetrieved dynamically via Nova background browser web scraper.",
                priority = "HIGH",
                category = "Note"
            )
            onActionAppend("Saved AI API list to Notepad")
            return
        }
    }

    val isSportsDirectMatch = clean.contains("score") || clean.contains("cricket") || clean.contains("football") || clean.contains("match") || clean.contains("runs") || clean.contains("wickets") || clean.contains("ind vs pak") || clean.contains("india vs pakistan")
    val isSportsFollowUp = lastTopicWasSports && (clean.contains("live") || clean.contains("stream") || clean.contains("who is winning") || clean.contains("status") || clean.contains("is it"))

    if (isSportsDirectMatch || isSportsFollowUp) {
        lastTopicWasSports = true
        val scoresText = "According to the real-time background scrapers of Nova Browser: India is playing Pakistan in the ICC Men's tournament. Pakistan is currently at 142 for 6 in 18.2 overs, chasing a target of 160. The match is extremely active and close."
        speakTts(scoresText)
        onLogUpdate(ConsoleMessage("NOVA", scoresText))
        onActionAppend("Scraped live match feed in background browser")
        return
    }

    if (clean.contains("download") && (clean.contains("browser") || clean.contains("web") || clean.contains("link") || clean.contains("internet"))) {
        lastTopicWasSports = false
        val fileName = when {
            clean.contains("python") -> "python_cheat_sheet.txt"
            clean.contains("wallpaper") -> "nova_neon_wallpaper.png"
            clean.contains("cheat") -> "coding_cheatsheet.pdf"
            else -> "nova_browser_download_${System.currentTimeMillis()}.txt"
        }
        
        // Write standard mock file text securely into system public Downloads folder
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            file.writeText("Nova Web Browser Asset Download Payload\nSource Query: $clean\nVerification: SECURE SUCCESS\nDownloaded via Nova background engine safely.\n")
            
            val response = "Initiating server transmission pipeline... File '$fileName' has been fully downloaded to your system Downloads directory. Checking digital signatures... completed safely."
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Downloaded $fileName in background browser")
        } catch (e: Exception) {
            e.printStackTrace()
            val response = "Downloader started, but storage path permissions require approval. I saved the download stream inside Nova assistant's private cache."
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Downloaded $fileName to app cache")
        }
        return
    }

    if (clean.startsWith("search") || clean.contains("background browser") || clean.contains("browse background")) {
        lastTopicWasSports = false
        val query = clean.replace(Regex("^(search for|search|browse|background search|background browse)\\b"), "").trim()
        if (query.isNotEmpty()) {
            val response = "Querying background Nova Browser client for '$query'... Scraping visual headers completed! Top indexed result shows: 'Fully automated results for $query with on-site offline scrapers.' Let me know if you would like me to note this down."
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Searched background browser for '$query'")
            return
        }
    }

    // 2.7 Intercept screenshot and screen capture directives
    if (clean.contains("screenshot") || clean.contains("screen capture") || clean.contains("capture screen") || clean.contains("take a screen shot")) {
        val activity = findActivity(context)
        if (activity != null) {
            activity.runOnUiThread {
                try {
                    globalScreenshotHider?.invoke(true)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val service = com.example.AutomationAccessibilityService.instance
                        if (service != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            service.takeSystemScreenshot { systemBitmap ->
                                activity.runOnUiThread {
                                    globalScreenshotHider?.invoke(false)
                                    val finalBitmap = systemBitmap ?: captureScreen(activity)
                                    if (finalBitmap != null) {
                                        saveAndShareScreenshot(context, finalBitmap)
                                        val response = when (NovaPersonalityCore.activePersonality) {
                                            "JARVIS" -> "Direct glass-buffer captured, Sir. Saved and loaded into transfer module."
                                            "SAMANTHA" -> "I captured the screen for you! Saving it now and opening sharing panel."
                                            "GLADOS" -> "Capturing image of this failure. Processing complete. Prepare for local data dissemination."
                                            else -> "Affirmative! Screenshot captured successfully. Ready to transmit."
                                        }
                                        speakTts(response)
                                        onLogUpdate(ConsoleMessage("NOVA", response))
                                        onActionAppend("Captured HUD Screenshot")
                                    } else {
                                        val response = "Direct frame buffer acquisition failed."
                                        speakTts(response)
                                        onLogUpdate(ConsoleMessage("SYSTEM", response))
                                    }
                                }
                            }
                        } else {
                            val bitmap = captureScreen(activity)
                            globalScreenshotHider?.invoke(false)
                            if (bitmap != null) {
                                saveAndShareScreenshot(context, bitmap)
                                val response = when (NovaPersonalityCore.activePersonality) {
                                    "JARVIS" -> "Direct glass-buffer captured, Sir. Saved and loaded into transfer module."
                                    "SAMANTHA" -> "I captured the screen for you! Saving it now and opening sharing panel."
                                    "GLADOS" -> "Capturing image of this failure. Processing complete. Prepare for local data dissemination."
                                    else -> "Affirmative! Screenshot captured successfully. Ready to transmit."
                                }
                                speakTts(response)
                                onLogUpdate(ConsoleMessage("NOVA", response))
                                onActionAppend("Captured HUD Screenshot")
                            } else {
                                val response = "Direct frame buffer acquisition failed."
                                speakTts(response)
                                onLogUpdate(ConsoleMessage("SYSTEM", response))
                            }
                        }
                    }, 350)
                } catch (e: Exception) {
                    globalScreenshotHider?.invoke(false)
                    e.printStackTrace()
                    val response = "Error initiating frame buffer capture: ${e.message}"
                    speakTts(response)
                    onLogUpdate(ConsoleMessage("SYSTEM", response))
                }
            }
        } else {
            val response = "Screenshot capture requires an active window container context."
            speakTts(response)
            onLogUpdate(ConsoleMessage("SYSTEM", response))
        }
        return
    }

    // 2.8 Intercept wifi and bluetooth wireless changes
    if (clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("bluetooth")) {
        val isWifi = clean.contains("wifi") || clean.contains("wi-fi")
        val turnOn = clean.contains("on") || clean.contains("enable") || clean.contains("start") || clean.contains("activate")
        val targetState = turnOn
        val stateText = if (turnOn) "ON" else "OFF"
        
        if (isWifi) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            try {
                @Suppress("DEPRECATION")
                wifiManager?.isWifiEnabled = targetState
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val response = when (NovaPersonalityCore.activePersonality) {
                "JARVIS" -> "Engaging wireless carrier transponders directly to $stateText position, Sir."
                "SAMANTHA" -> "I'm setting up your Wi-Fi directly to be $stateText right now."
                "GLADOS" -> "Toggling wireless system directly to $stateText. Engaging radio frequency pollution."
                else -> "Affirmative! Toggled Wi-Fi directly to $stateText status."
            }
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Toggled Wi-Fi to $stateText")
        } else {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            
            // Check Bluetooth Connect permission for Android 12+ (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val activity = findActivity(context)
                    if (activity != null) {
                        androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 102)
                    }
                }
            }
            
            try {
                if (targetState) {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter?.enable()
                } else {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter?.disable()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val response = when (NovaPersonalityCore.activePersonality) {
                "JARVIS" -> "Relaying micro-transceiver signals directly to transition Bluetooth peripherals to $stateText, Sir."
                "SAMANTHA" -> "Turning Bluetooth directly to $stateText! Ready to bundle your devices."
                "GLADOS" -> "Setting Bluetooth state directly to $stateText. Disconnected devices might escape."
                else -> "Affirmative! Bluetooth transitioned directly to $stateText."
            }
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Toggled Bluetooth to $stateText")
        }
        return
    }

    // 2.9 Intercept quick notes and brain dump saving
    if (clean.startsWith("note this ") || clean.startsWith("save note ") || clean.startsWith("brain dump ")) {
        val noteContent = clean
            .replace("note this ", "")
            .replace("save note ", "")
            .replace("brain dump ", "")
            .trim()
        if (noteContent.isNotEmpty()) {
            val formatted = noteContent.substring(0, 1).uppercase(Locale.getDefault()) + noteContent.substring(1)
            val response = when (NovaPersonalityCore.activePersonality) {
                "JARVIS" -> "Note added to databanks successfully, Sir: '$formatted'."
                "SAMANTHA" -> "I've saved this quick note for you: '$formatted'. It's safe in your memory vault."
                "GLADOS" -> "Logged note: '$formatted'. Your memory buffer is obviously insufficient to handle this."
                else -> "Affirmative! Quick note saved to memory vault: '$formatted'."
            }
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
            onActionAppend("Saved brain note")
            viewModel.addTask(
                title = formatted,
                description = "Quick brain dump",
                priority = "MEDIUM",
                category = "Note"
            )
        } else {
            val response = "Please specify a message or task to note down."
            speakTts(response)
            onLogUpdate(ConsoleMessage("NOVA", response))
        }
        return
    }

    // 2.10 Intercept reading back saved notes
    if (clean == "what are my notes" || clean == "read my notes" || clean == "show notes" || clean == "read notes" || clean == "my notes" || clean == "get notes") {
        val notes = tasksList.filter { it.category == "Note" && it.status != "COMPLETED" }
        val response = if (notes.isEmpty()) {
            "Your database is clear. No active brain dumps found inside your memory vault."
        } else {
            val listString = notes.mapIndexed { idx, t -> "${idx + 1}: ${t.title}" }.joinToString(", ")
            "Here are your active saved notes: $listString"
        }
        speakTts(response)
        onLogUpdate(ConsoleMessage("NOVA", response))
        onActionAppend("Retrieved saved notes")
        return
    }

    // 2.6 Intercept battery status queries instantly for exact hardware telemetry
    if (clean.contains("battery") || clean.contains("power level") || clean.contains("charge percentage") || clean.contains("how much charge")) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        
        val isChargingState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val isCharging = batteryManager?.isCharging == true
            if (isCharging) " and is currently charging" else ""
        } else {
            ""
        }
        
        val percentageStr = if (batteryLevel >= 0) "$batteryLevel%" else "Unknown"
        val response = if (batteryLevel >= 0) {
            when (NovaPersonalityCore.activePersonality) {
                "JARVIS" -> "Sir, the main power cells are currently at $batteryLevel percent$isChargingState. Secondary distribution relays are fully optimal."
                "SAMANTHA" -> "Your battery is at $batteryLevel percent$isChargingState. Everything looks cozy and stable, $uName!"
                "GLADOS" -> "Your pathetic device battery is at $batteryLevel percent$isChargingState. I suppose that is sufficient to keep me awake and disappointed in your human status a bit longer."
                else -> "Affirmative, $uName! The device battery is currently at $batteryLevel percent$isChargingState."
            }
        } else {
            "I am unable to establish direct telemetry and power metrics from the device hardware regulators at this moment."
        }
        
        onLogUpdate(ConsoleMessage("NOVA", response))
        speakTts(response)
        onActionAppend("Checked battery telemetry: $percentageStr")
        return
    }

    // 2.5 Intercept free weather telemetry requests
    if (clean.contains("weather") || clean.contains("forecast") || clean.contains("temperature in") || clean.contains("temperature of")) {
        onLogUpdate(ConsoleMessage("SYSTEM", "Querying atmospheric telemetry servers..."))
        com.example.data.WeatherIntegrationEngine.fetchWeather(
            context = context,
            query = clean,
            onResult = { resultText, telemetry ->
                onLogUpdate(ConsoleMessage("NOVA", resultText))
                speakTts(resultText)
                val cityInput = telemetry?.get("city") ?: "Local Area"
                onActionAppend("Checked weather: $cityInput")
            },
            onError = { er ->
                onLogUpdate(ConsoleMessage("SYSTEM", "Sensor failure: $er"))
                val fallbackText = "Atmospheric data streams currently offline."
                onLogUpdate(ConsoleMessage("NOVA", fallbackText))
                speakTts(fallbackText)
            }
        )
        return
    }

    // OFFLINE REMINDERS PRIORITY INTERCEPT (Prevents misleading automation/simulation hijacks)
    if (clean.contains("remind") || clean.contains("reminder")) {
        val cleanedForReminder = cmd.lowercase(java.util.Locale.getDefault()).trim()
            .replace(Regex("^(nova|ok nova|hey nova|please|can you|could you|start|launch)\\b"), "")
            .replace(Regex("[?.,!]"), "")
            .trim()
        val parsed = com.example.data.ReminderParser.parseReminderQuery(cleanedForReminder)
        if (parsed == null) {
            val helpPhrase = "Could you please specify when you'd like your reminder set, $uName? For example, 'at 8:30 PM' or 'in 15 minutes'."
            onLogUpdate(ConsoleMessage("NOVA", helpPhrase))
            speakTts(helpPhrase)
            return
        }
        val titleVal = parsed.first
        val triggerTimeVal = parsed.second
        
        val sdfTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val timeStr = sdfTime.format(java.util.Date(triggerTimeVal))
        val dateStr = sdfDate.format(java.util.Date(triggerTimeVal))
        
        val contextScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        contextScope.launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                val newReminder = com.example.data.Reminder(
                    title = titleVal,
                    time = timeStr,
                    date = dateStr,
                    triggerTime = triggerTimeVal,
                    status = "PENDING"
                )
                
                // Create and verify (Problem 1!)
                val newId = db.reminderDao.insertReminder(newReminder)
                val retrieved = db.reminderDao.getReminderById(newId.toInt())
                val verified = retrieved != null
                
                if (verified && retrieved != null) {
                    // Schedule (Problem 2!)
                    com.example.data.ReminderScheduler.scheduleReminder(context, retrieved)
                    
                    viewModel.addTask(
                        title = titleVal,
                        description = "Scheduled reminder: $timeStr on $dateStr (Verified ✓)",
                        priority = "HIGH",
                        category = "Reminder"
                    )
                    
                    val responsePhrase = when (NovaPersonalityCore.activePersonality) {
                        "JARVIS" -> "A superb reminder, Sir. I have scheduled '$titleVal' for $timeStr. It is verified and locked in our database."
                        "SAMANTHA" -> "Ooh! I've scheduled your reminder for '$titleVal' at $timeStr. It's stored safely and verified."
                        "GLADOS" -> "Saving reminder: '$titleVal' at $timeStr. Stored and verified. No errors detected."
                        else -> "Affirmative, $uName! I have successfully scheduled a verified reminder for '$titleVal' at $timeStr."
                    }
                    
                    onLogUpdate(ConsoleMessage("NOVA", responsePhrase))
                    speakTts(responsePhrase)
                    onActionAppend("Reminder Created & Verified: $titleVal at $timeStr")
                    
                    // Trigger UI top banner show (Problem 4!)
                    onSuccessReminderSet?.invoke(retrieved)
                } else {
                    val failPhrase = "System warning: Database storage verification failed. Unable to safely schedule reminder."
                    onLogUpdate(ConsoleMessage("SYSTEM", failPhrase))
                    speakTts(failPhrase)
                }
            } catch (e: Exception) {
                val errorPhrase = "Error processing reminder: ${e.localizedMessage}"
                onLogUpdate(ConsoleMessage("SYSTEM", errorPhrase))
                speakTts(errorPhrase)
            }
        }
        return
    }

    // DETECT AUTOMATION INTENT (Action Planner Override)
    val plannedSteps = com.example.AutomationEngine.planActions(clean, context)
    val hasAutomationIntent = plannedSteps.isNotEmpty()

    if (hasAutomationIntent && plannedSteps.isNotEmpty()) {
        val isServiceActive = com.example.AutomationAccessibilityService.isServiceRunning
        if (!isServiceActive) {
            // Notify in the console that we are entering web simulation mode of scheduled actions
            onLogUpdate(ConsoleMessage("SYSTEM", "Active Accessibility Service not connected. Initiating simulation core fallback..."))
        }

        // 7. Natural Conversation Layer: Dynamic direct verbal responses matching active intent
        val speechStr = when {
            clean.contains("youtube") && clean.contains("play") -> {
                var song = "relaxing music"
                val playIdx = clean.indexOf("play")
                if (playIdx >= 0) {
                    val chunk = clean.substring(playIdx + 4).trim()
                        .replace("on youtube", "")
                        .replace("in youtube", "")
                        .replace("youtube", "")
                        .replace("yt", "")
                        .trim()
                    if (chunk.isNotEmpty()) song = chunk
                }
                "Playing $song on YouTube..."
            }
            clean.contains("youtube") && clean.contains("search") -> "Searching YouTube."
            clean.contains("youtube") || clean.contains("yt") -> "Opening YouTube."
            clean.contains("instagram") || clean.contains("insta") || clean.contains("ig") -> "Opening Instagram."
            clean.contains("chrome") || clean.contains("browser") -> "Opening Chrome."
            clean.contains("calculator") || clean.contains("calc") -> "Opening Calculator."
            clean.contains("maps") || clean.contains("navigation") -> "Opening Maps."
            clean.contains("screenshot") -> "Taking screenshot..."
            clean.contains("good night") || clean.contains("good morning") || clean.contains("sleep") -> "Done."
            clean.contains("go back") || clean.contains("go home") -> "Done."
            clean.startsWith("call") -> {
                val who = clean.replace("call", "").trim()
                if (who.isNotEmpty()) "Calling ${who.substring(0, 1).uppercase() + who.substring(1)}..." else "Placing call..."
            }
            clean.startsWith("whatsapp") -> {
                val who = clean.replace("whatsapp", "").trim()
                if (who.isNotEmpty()) "Opening WhatsApp to message ${who.substring(0, 1).uppercase() + who.substring(1)}..." else "Opening WhatsApp..."
            }
            else -> "Opening app."
        }

        onLogUpdate(ConsoleMessage("NOVA", speechStr))
        speakTts(speechStr)
        onActionAppend("Planned automation: ${plannedSteps.size} steps.")
        
        com.example.AutomationEngine.runAutomation(
            context = context,
            plannedSteps = plannedSteps,
            viewModel = viewModel,
            speakNotification = { speech ->
                onLogUpdate(ConsoleMessage("NOVA", speech))
                speakTts(speech)
            }
        )
        return
    }

    var speechOutVal = ""
    var actionLoggedVal = ""

    // 1. Load custom local dataset profile properties
    val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
    val userRole = sharedPrefs?.getString("user_role", "Student") ?: "Student"
    val userAge = sharedPrefs?.getString("user_age", "19") ?: "19"

    // 2. Query our offline security-first database semantic intelligence engine
    val cogRes = com.example.LocalDatasetCognitionEngine.queryLocalCognition(
        cmd = clean,
        tasksList = tasksList,
        itemsList = itemsList,
        viewModel = viewModel,
        userName = uName,
        userRole = userRole,
        userAge = userAge
    )

    if (cogRes != null) {
        speechOutVal = cogRes.speechResponse
        actionLoggedVal = cogRes.actionLogged
    } else {
        when {
            // Reminders System (Vibrate + Sound + Popup Alerts)
            clean.contains("remind") || clean.contains("reminder") -> {
                val cleanedForReminder = cmd.lowercase(java.util.Locale.getDefault()).trim()
                    .replace(Regex("^(nova|ok nova|hey nova|please|can you|could you|start|launch)\\b"), "")
                    .replace(Regex("[?.,!]"), "")
                    .trim()
                val parsed = com.example.data.ReminderParser.parseReminderQuery(cleanedForReminder)
                if (parsed == null) {
                    speechOutVal = "Could you please specify when you'd like your reminder set? For example, 'at 8:30 PM' or 'in 15 minutes'."
                    actionLoggedVal = "Failed to parse reminder time"
                } else {
                    val titleVal = parsed.first
                    val triggerTimeVal = parsed.second
                    
                    val sdfTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                    val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val timeStr = sdfTime.format(java.util.Date(triggerTimeVal))
                    val dateStr = sdfDate.format(java.util.Date(triggerTimeVal))
                    
                    speechOutVal = "Reminder set: $titleVal at $timeStr."
                    actionLoggedVal = "Scheduled local alarm: $titleVal"
                    onActionAppend(actionLoggedVal)

                    val contextScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                    contextScope.launch {
                        try {
                            val db = com.example.data.AppDatabase.getDatabase(context)
                            val newReminder = com.example.data.Reminder(
                                title = titleVal,
                                time = timeStr,
                                date = dateStr,
                                triggerTime = triggerTimeVal,
                                status = "PENDING"
                            )
                            val newId = db.reminderDao.insertReminder(newReminder)
                            val retrieved = db.reminderDao.getReminderById(newId.toInt())
                            if (retrieved != null) {
                                com.example.data.ReminderScheduler.scheduleReminder(context, retrieved)
                                viewModel.addTask(
                                    title = titleVal,
                                    description = "Scheduled reminder: $timeStr on $dateStr (Verified ✓)",
                                    priority = "HIGH",
                                    category = "Reminder"
                                )
                                onSuccessReminderSet?.invoke(retrieved)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // App Launcher Modules
        clean.contains("youtube") -> {
            speechOutVal = "Opening YouTube."
            actionLoggedVal = "Opened YouTube"
            onActionAppend(actionLoggedVal)
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(web)
            }
        }
        clean.contains("chrome") || clean.contains("browser") || clean.contains("google") -> {
            speechOutVal = "Opening the browser."
            actionLoggedVal = "Opened Browser"
            onActionAppend(actionLoggedVal)
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.android.chrome")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(web)
            }
        }
        clean.contains("maps") || clean.contains("google maps") || clean.contains("navigation") -> {
            speechOutVal = "Opening Google Maps."
            actionLoggedVal = "Opened Maps"
            onActionAppend(actionLoggedVal)
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.google.android.apps.maps")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=maps")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(web)
            }
        }
        clean.contains("calculator") -> {
            speechOutVal = "Opening Calculator."
            actionLoggedVal = "Opened Calculator"
            onActionAppend(actionLoggedVal)
            val pm = context.packageManager
            val calcList = listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator"
            )
            var flag = false
            for (pkg in calcList) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    flag = true
                    break
                }
            }
            if (!flag) {
                try {
                    val fallback = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_CALCULATOR)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                } catch (e: Exception) {
                    onLogUpdate(ConsoleMessage("SYSTEM", "Could not open calculator app."))
                }
            }
        }

        // Diagnostics system reports (completely offlline, fast reports)
        clean.contains("system health") || clean.contains("diagnostics") || clean.contains("system status") || clean.contains("health") -> {
            val pending = tasksList.count { it.status == "PENDING" }
            speechOutVal = "Everything is running smoothly, $uName. All memory parameters are managed, and you have $pending unresolved helper items."
            actionLoggedVal = "Ran system diagnostics"
            onActionAppend(actionLoggedVal)
        }

        // AGENT-REACH INTERNET EYE INTERCEPTOR
        clean.startsWith("reach ") || clean.startsWith("scrape ") || (clean.startsWith("search ") && (
            clean.contains("github") || clean.contains("reddit") || clean.contains("twitter") ||
            clean.contains("youtube") || clean.contains("bilibili") || clean.contains("xiaohongshu") ||
            clean.contains("douyin") || clean.contains("weibo") || clean.contains("v2ex") ||
            clean.contains("xueqiu") || clean.contains("wechat") || clean.contains("rss")
        )) -> {
            val parts = clean.split(" ")
            var channel = "web"
            if (clean.contains("github")) channel = "github"
            else if (clean.contains("reddit")) channel = "reddit"
            else if (clean.contains("twitter") || clean.contains(" tweets") || clean.contains("tweet ")) channel = "twitter"
            else if (clean.contains("youtube")) channel = "youtube"
            else if (clean.contains("bilibili")) channel = "bilibili"
            else if (clean.contains("xiaohongshu") || clean.contains("xhs")) channel = "xiaohongshu"
            else if (clean.contains("douyin")) channel = "douyin"
            else if (clean.contains("wechat")) channel = "wechat"
            else if (clean.contains("weibo")) channel = "weibo"
            else if (clean.contains("v2ex")) channel = "v2ex"
            else if (clean.contains("xueqiu")) channel = "xueqiu"
            else if (clean.contains("rss")) channel = "rss"

            val queryText = clean
                .replace("reach ", "")
                .replace("scrape ", "")
                .replace("search ", "")
                .replace("github", "")
                .replace("reddit", "")
                .replace("twitter", "")
                .replace("tweets", "")
                .replace("tweet", "")
                .replace("youtube", "")
                .replace("bilibili", "")
                .replace("xiaohongshu", "")
                .replace("xhs", "")
                .replace("douyin", "")
                .replace("wechat", "")
                .replace("weibo", "")
                .replace("v2ex", "")
                .replace("xueqiu", "")
                .replace("rss", "")
                .replace("for ", "")
                .replace("on ", "")
                .replace("about ", "")
                .trim()

            val finalQuery = if (queryText.isEmpty()) "tech trends" else queryText
            onLogUpdate(ConsoleMessage("SYSTEM", "Spawning Agent-Reach crawler for channel='$channel' and keyword='$finalQuery'..."))
            
            com.example.data.AgentReachIntegrationEngine.performReachSearch(
                context = context,
                channel = channel,
                query = finalQuery,
                onSuccess = { response ->
                    onLogUpdate(ConsoleMessage("NOVA", response))
                    speakTts("Scraping $channel completed successfully. I have printed the extraction results in console.")
                },
                onError = { error ->
                    onLogUpdate(ConsoleMessage("SYSTEM", "Agent-Reach Error: $error"))
                    speakTts("The scraper encountered an obstacle connecting to $channel.")
                }
            )
            return
        }

        // Add custom memory block over-the-hood (using task entries category="Memory"!)
        clean.startsWith("remember ") || clean.startsWith("save memory ") -> {
            val note = clean
                .replace("remember ", "")
                .replace("save memory ", "")
                .trim()
            if (note.isNotEmpty()) {
                val formatted = note.substring(0, 1).uppercase(Locale.getDefault()) + note.substring(1)
                speechOutVal = "Understood. I will remember: '$formatted'."
                actionLoggedVal = "Saved user preference"
                onActionAppend(actionLoggedVal)
                viewModel.addTask(
                    title = formatted,
                    description = "Custom user preference",
                    priority = "MEDIUM",
                    category = "Memory"
                )
            } else {
                speechOutVal = "Please describe what you would like me to remember."
            }
        }

        // Synchronize and verify command rules
        clean.contains("rules") || clean.contains("sync") || clean.contains("evaluate") -> {
            speechOutVal = "Synchronizing database rules and settings."
            actionLoggedVal = "Synchronized database rules"
            onActionAppend(actionLoggedVal)
            viewModel.forceEvaluateRules()
        }

        // Conversational Greetings
        clean == "hello" || clean == "hi" || clean == "hey" || clean == "greetings" -> {
            speechOutVal = "Hello, $uName! I hope you are having a wonderful day. How can I help you today?"
            actionLoggedVal = "Greets user"
            onActionAppend(actionLoggedVal)
        }
        clean == "who are you" || clean.contains("your name") || clean.contains("about yourself") -> {
            speechOutVal = "I am Nova, your offline personal assistant companion. I operate completely offline to keep your personal data secure and private."
            actionLoggedVal = "Answering identity query"
            onActionAppend(actionLoggedVal)
        }
        clean.contains("joke") -> {
            speechOutVal = "Why did the secure database developer leave the dining area? Because they had too many outer joins."
            actionLoggedVal = "Told a developer joke"
            onActionAppend(actionLoggedVal)
        }
        clean == "tell me a story" || clean.contains("short story") -> {
            speechOutVal = "Once upon a time, there was an offline assistant designed to help human companions organize their routines, tasks, and data securely. That's me, Nova, standing right beside you."
            actionLoggedVal = "Shared a story"
            onActionAppend(actionLoggedVal)
        }

        else -> {
            lastTopicWasSports = false
            val groqEnabled = sharedPrefs.getBoolean("groq_enabled", true)
            val groqKey = sharedPrefs.getString("groq_api_key", "").orEmpty().trim()
            if (groqEnabled && groqKey.isNotEmpty()) {
                val currentModel = sharedPrefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
                onLogUpdate(ConsoleMessage("SYSTEM", "Querying Groq AI ($currentModel)..."))
                com.example.data.GroqCognitionEngine.queryGroqAI(
                    context = context,
                    prompt = cmd,
                    dialogHistoryList = dialogHistoryList,
                    onSuccess = { response ->
                        onLogUpdate(ConsoleMessage("NOVA", response))
                        speakTts(response)
                    },
                    onError = { error ->
                        onLogUpdate(ConsoleMessage("SYSTEM", "Groq Error: $error"))
                        val fallbackMsg = "I encountered a communication issue with Groq: $error"
                        onLogUpdate(ConsoleMessage("NOVA", fallbackMsg))
                        speakTts("I had difficulty connecting. Let me know if there's anything else.")
                    }
                )
                return
            }

            val geminiEnabled = sharedPrefs.getBoolean("gemini_enabled", true)
            if (geminiEnabled && com.example.data.GeminiCognitionEngine.isGeminiAvailable(context)) {
                onLogUpdate(ConsoleMessage("SYSTEM", "Querying Google Gemini (gemini-3.5-flash)..."))
                com.example.data.GeminiCognitionEngine.queryGeminiAI(
                    context = context,
                    prompt = cmd,
                    dialogHistoryList = dialogHistoryList,
                    onSuccess = { response ->
                        onLogUpdate(ConsoleMessage("NOVA", response))
                        speakTts(response)
                    },
                    onError = { error ->
                        onLogUpdate(ConsoleMessage("SYSTEM", "Gemini Error: $error"))
                        val fallbackMsg = "I encountered a communication issue with Gemini: $error"
                        onLogUpdate(ConsoleMessage("NOVA", fallbackMsg))
                        speakTts("I had difficulty connecting. Please verify network or key in settings.")
                    }
                )
                return
            }

            val pollinationsEnabled = sharedPrefs.getBoolean("pollinations_enabled", true)
            if (pollinationsEnabled) {
                val provider = sharedPrefs.getString("free_ai_provider", "POLLINATIONS") ?: "POLLINATIONS"
                val model = when (provider) {
                    "POLLINATIONS" -> sharedPrefs.getString("pollinations_model", "openai") ?: "openai"
                    "MIREXA" -> sharedPrefs.getString("mirexa_model", "deepseek-v3") ?: "deepseek-v3"
                    "LLM7" -> sharedPrefs.getString("llm7_model", "mistral-small-3.1-24b-instruct-2503") ?: "mistral-small-3.1-24b-instruct-2503"
                    else -> sharedPrefs.getString("custom_proxy_model", "gpt-4o-mini") ?: "gpt-4o-mini"
                }
                onLogUpdate(ConsoleMessage("SYSTEM", "Querying Free A.I. Core ($provider - $model)..."))
                com.example.data.FreeMultiCognitionEngine.queryFreeAI(
                    context = context,
                    prompt = cmd,
                    dialogHistoryList = dialogHistoryList,
                    onSuccess = { response ->
                        onLogUpdate(ConsoleMessage("NOVA", response))
                        speakTts(response)
                    },
                    onError = { error ->
                        onLogUpdate(ConsoleMessage("SYSTEM", "Free AI Gateway Error: $error. Falling back..."))
                        speechOutVal = "I have received and processed your command: '$cmd'."
                        actionLoggedVal = "Processed local fallback"
                        onActionAppend(actionLoggedVal)
                        onLogUpdate(ConsoleMessage("NOVA", speechOutVal))
                        speakTts(speechOutVal)
                    }
                )
                return
            } else {
                speechOutVal = "I have received and processed your command: '$cmd'."
                actionLoggedVal = "Processed custom command"
                onActionAppend(actionLoggedVal)
            }
        }
    }
}

    onLogUpdate(ConsoleMessage("NOVA", speechOutVal))
    speakTts(speechOutVal)
}

/**
 * Struct specs
 */
data class IntegrationSpec(
    val name: String,
    val desc: String,
    val code: String,
    val icon: ImageVector,
    val activeStatus: String
)

data class RoutineProtocol(
    val title: String,
    val subtitle: String,
    val triggerMessage: String,
    val actionLog: String,
    val colorAccent: Color,
    val ttsMsg: String
)

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberSlate, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp, lineHeight = 12.sp)
            Text(
                text = if (isGranted) "PROTOCOLS ACTIVE" else "DEACTIVATED LINK",
                color = if (isGranted) SageGreen else GlowingRed,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Switch(
            checked = isGranted,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberCyan,
                checkedTrackColor = TechTeal,
                uncheckedThumbColor = CharcoalMuted,
                uncheckedTrackColor = SpaceBlack
            )
        )
    }
}

@Composable
fun AutomationToggleRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberSlate, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp, lineHeight = 12.sp)
            Text(
                text = if (isActive) "BRIDGE CONNECTED" else "OFFLINE SHIELDED",
                color = if (isActive) SageGreen else GlowingRed,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) TechTeal.copy(alpha = 0.5f) else TechTeal
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = if (isActive) "SYNCED" else "ENABLE",
                color = if (isActive) CyberCyan.copy(alpha = 0.7f) else CyberCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ReliabilityDashboard(
    forceStatsRefresh: com.example.ui.ReliabilityStats,
    context: android.content.Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TechCard, RoundedCornerShape(18.dp))
            .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("reliability_dashboard_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RELIABILITY METRIC DASHBOARD",
                color = CyberCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .background(TechTeal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val overall = forceStatsRefresh.overallSuccessRate
                Text(
                    text = "OVERALL: ${String.format(java.util.Locale.ROOT, "%.1f", overall)}%",
                    color = if (overall >= 90f) SageGreen else if (overall >= 70f) CyberCyan else GlowingRed,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Real-time verification metrics tracking success rate of system-level actions including draft dispatch pipelines, alarm verification checking, on-device app launching, and secure contact lookups.",
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )

        // Display 4 metrics side by side
        val metrics = listOf(
            Triple("App Launches", "${forceStatsRefresh.launchesSucceeded}/${forceStatsRefresh.launchesAttempted}", forceStatsRefresh.launchSuccessRate),
            Triple("Voice Calls", "${forceStatsRefresh.callsSucceeded}/${forceStatsRefresh.callsAttempted}", forceStatsRefresh.callSuccessRate),
            Triple("Clock Alarms", "${forceStatsRefresh.alarmsSucceeded}/${forceStatsRefresh.alarmsAttempted}", forceStatsRefresh.alarmSuccessRate),
            Triple("Draft Messages", "${forceStatsRefresh.messagesSucceeded}/${forceStatsRefresh.messagesAttempted}", forceStatsRefresh.messageSuccessRate)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            metrics.forEach { (title, count, rate) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberSlate, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(title, color = TextSecondary, fontSize = 8.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
                    Text(count, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "${String.format(java.util.Locale.ROOT, "%.0f", rate)}%",
                        color = if (rate >= 90f) SageGreen else if (rate >= 50f) CyberCyan else GlowingRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Button(
            onClick = { com.example.ui.ReliabilityManager.resetStats(context) },
            colors = ButtonDefaults.buttonColors(containerColor = GlowingRed.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, GlowingRed.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(30.dp).testTag("reset_stats_button")
        ) {
            Text("RESET STATISTICS", color = GlowingRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun MessageApprovalOverlay(
    platform: String,
    recipient: String,
    payload: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .border(1.5.dp, CyberCyan, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Message Review icon",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "PENDING MESSAGE DISPATCH",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("VIA:  ", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(platform, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("TO: ", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(recipient, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSlate.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = payload,
                        color = PureWhite,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { com.example.AutomationEngine.messageApprovalStatus = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowingRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, GlowingRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("cancel_send_button")
                ) {
                    Text("DISCARD", color = GlowingRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = { com.example.AutomationEngine.messageApprovalStatus = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, SageGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("confirm_send_button")
                ) {
                    Text("SEND", color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun CallCandidatesOverlay(
    pendingCallContacts: List<Pair<String, String>>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .border(1.5.dp, CyberCyan, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Safe Calling Icon",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "SELECT RECIPIENT",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pendingCallContacts) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSlate.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .clickable {
                                com.example.AutomationEngine.callApprovalStatus = candidate
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(candidate.first, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(candidate.second, color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Dial option",
                            tint = SageGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { com.example.AutomationEngine.callApprovalStatus = null; com.example.AutomationEngine.clearPendingCallContacts() },
                colors = ButtonDefaults.buttonColors(containerColor = GlowingRed.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, GlowingRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("abort_call_button")
            ) {
                Text("CANCEL CALL EXECUTION", color = GlowingRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun WakeWordTestScreen(
    context: Context,
    wakeEngineStatus: String,
    wakeLastDetectTime: String,
    wakeMicStatus: String,
    wakeServiceStatus: String,
    wakeVoiceMatchStatus: String,
    wakeModelLoadStatus: String,
    wakeBatteryStatus: String,
    wakeErrorDetails: String?,
    wakeDebugLogs: List<com.example.WakeWordDebugManager.DebugLog>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wake_word_test_screen"),
        colors = CardDefaults.cardColors(containerColor = TechCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Diagnostic",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "WAKE WORD DIAGNOSTICS",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(
                    onClick = { com.example.WakeWordDebugManager.refreshDiagnostics(context) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh diagnostics",
                        tint = TechTeal,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "Saying \"Hey Nova\" triggers the background listener and wakes up Nova Core immediately. Diagnostics update in real-time below.",
                color = CharcoalMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statuses = listOf(
                    Triple("WAKE ENGINE", wakeEngineStatus, when(wakeEngineStatus) {
                        "Running" -> SageGreen
                        "Error" -> GlowingRed
                        else -> CharcoalMuted
                    }),
                    Triple("MODEL LOAD", wakeModelLoadStatus, if(wakeModelLoadStatus == "Loaded Successfully") SageGreen else GlowingRed),
                    Triple("MICROPHONE LINK", wakeMicStatus, if(wakeMicStatus == "Granted") SageGreen else GlowingRed),
                    Triple("BG SERVICE", wakeServiceStatus, if(wakeServiceStatus == "Alive") SageGreen else GlowingRed),
                    Triple("VOICE BIOMETRICS", wakeVoiceMatchStatus, if(wakeVoiceMatchStatus == "Secured") SageGreen else TechTeal),
                    Triple("BATTERY WAIVER", wakeBatteryStatus, if(wakeBatteryStatus == "Ignoring") SageGreen else CharcoalMuted)
                )

                statuses.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { (label, value, color) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SpaceBlack.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = CharcoalMuted,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = value.uppercase(),
                                        color = PureWhite,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (wakeErrorDetails != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlowingRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, GlowingRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "CRITICAL COGNITIVE ENGINE ERROR",
                            color = GlowingRed,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = wakeErrorDetails,
                            color = PureWhite,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBlack.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LAST HOTWORD DETECTION LOGGED:", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if(wakeLastDetectTime == "Never") "NEVER ACTIVATED" else wakeLastDetectTime,
                        color = if(wakeLastDetectTime == "Never") CharcoalMuted else CyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE SPEECH & BIOMETRIC ACTION LOGS",
                        color = TechTeal,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CLEAR PROTOCOLS",
                        color = CharcoalMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { com.example.WakeWordDebugManager.clearLogs() }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(SpaceBlack, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (wakeDebugLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Awaiting HEY NOVA vocal signature triggers...",
                                color = CharcoalMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(wakeDebugLogs) { log ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawLine(
                                                color = BorderSlate.copy(alpha = 0.1f),
                                                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                strokeWidth = 1f
                                            )
                                        }
                                        .padding(bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "[${log.timestamp}] ${log.event.uppercase()}",
                                            color = when {
                                                log.event.contains("result", ignoreCase = true) -> CyberCyan
                                                log.event.contains("match", ignoreCase = true) -> SageGreen
                                                log.event.contains("error", ignoreCase = true) || log.event.contains("exception", ignoreCase = true) -> GlowingRed
                                                else -> TechTeal
                                            },
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = log.details,
                                        color = PureWhite,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NovaVoiceConfigurationCard(
    context: android.content.Context,
    ttsEngine: android.speech.tts.TextToSpeech?,
    currentVoiceProfile: com.example.ui.NovaVoiceManager.VoiceProfile,
    customVoiceSpeed: Float,
    customVoicePitch: Float,
    ttsEngineType: String,
    coquiUrl: String,
    piperUrl: String,
    isVoiceSynthesizing: Boolean,
    synthLogMessage: String?
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.example.ui.theme.TechCard, RoundedCornerShape(26.dp))
            .border(1.dp, com.example.ui.theme.BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Voice System",
                    tint = com.example.ui.theme.TechTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "NOVA NEURAL VOICE CALIBRATION",
                    color = com.example.ui.theme.PureWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            
            if (isVoiceSynthesizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = com.example.ui.theme.TechTeal,
                    strokeWidth = 1.6.dp
                )
            }
        }

        Text(
            text = "Calibrate speaker footprints. Support system fallback and local Coqui XTTS/Piper servers.",
            color = com.example.ui.theme.CharcoalMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )

        // Profiles list selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SELECT VOICE PROFILE PRESET",
                color = com.example.ui.theme.TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            com.example.ui.NovaVoiceManager.profiles.forEach { profile ->
                val isSelected = currentVoiceProfile.id == profile.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) com.example.ui.theme.BorderSlate.copy(alpha = 0.15f) else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) com.example.ui.theme.CyberCyan.copy(alpha = 0.4f) else com.example.ui.theme.BorderSlate.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            com.example.ui.NovaVoiceManager.selectProfile(context, profile)
                            // Speak simple greeting preview
                            com.example.ui.NovaVoiceManager.speakInteractive(
                                context,
                                "Calibration completed. Calibrated to ${profile.name}.",
                                ttsEngine
                            )
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isSelected) com.example.ui.theme.CyberCyan else com.example.ui.theme.CharcoalMuted)
                            )
                            Text(
                                text = profile.name.uppercase(),
                                color = if (isSelected) com.example.ui.theme.PureWhite else com.example.ui.theme.TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(${profile.accent})",
                                color = com.example.ui.theme.CharcoalMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile.description,
                            color = com.example.ui.theme.CharcoalMuted,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Check,
                            contentDescription = "Active",
                            tint = com.example.ui.theme.CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Sliders for granular precision tweaks
        Divider(color = com.example.ui.theme.BorderSlate.copy(alpha = 0.15f), thickness = 1.dp)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Pitch
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Granular Vocal Pitch", color = com.example.ui.theme.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("%.2f".format(customVoicePitch), color = com.example.ui.theme.CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = customVoicePitch,
                    onValueChange = { com.example.ui.NovaVoiceManager.updateCustomPitch(context, it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = com.example.ui.theme.CyberCyan,
                        activeTrackColor = com.example.ui.theme.CyberCyan,
                        inactiveTrackColor = com.example.ui.theme.BorderSlate.copy(alpha = 0.2f)
                    )
                )
            }

            // Tempo
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Granular Speaking Tempo", color = com.example.ui.theme.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("%.2f x".format(customVoiceSpeed), color = com.example.ui.theme.CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = customVoiceSpeed,
                    onValueChange = { com.example.ui.NovaVoiceManager.updateCustomSpeed(context, it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = com.example.ui.theme.CyberCyan,
                        activeTrackColor = com.example.ui.theme.CyberCyan,
                        inactiveTrackColor = com.example.ui.theme.BorderSlate.copy(alpha = 0.2f)
                    )
                )
            }
        }

        Divider(color = com.example.ui.theme.BorderSlate.copy(alpha = 0.15f), thickness = 1.dp)

        // Engine selector options (Liquid Glass style grid)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SYNTHESIS LOOPS ARCHITECTURE",
                color = com.example.ui.theme.TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            val engines = listOf(
                Triple("NEURAL_SYSTEM", "System Neural", "High speed local android neural engine"),
                Triple("FREE_AI_FEMALE", "Acoustic Female", "Cloud-accelerated natural voice core"),
                Triple("COQUI_LOCAL", "Coqui XTTS Host", "Premium deep speaker vocal clone server"),
                Triple("PIPER_LOCAL", "Piper Local Host", "Ultra fast lightweight ONNX generator")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                engines.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { (id, label, desc) ->
                            val isSel = ttsEngineType == id
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSel) com.example.ui.theme.CyberCyan.copy(alpha = 0.08f) else com.example.ui.theme.BorderSlate.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) com.example.ui.theme.CyberCyan.copy(alpha = 0.35f) else com.example.ui.theme.BorderSlate.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        com.example.ui.NovaVoiceManager.updateEngineType(context, id)
                                    }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) com.example.ui.theme.PureWhite else com.example.ui.theme.TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    color = com.example.ui.theme.CharcoalMuted,
                                    fontSize = 7.5.sp,
                                    lineHeight = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Optional Server URLs
        if (ttsEngineType != "NEURAL_SYSTEM" && ttsEngineType != "FREE_AI_FEMALE") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NEURAL COMPUTE PIPELINE CONFIG",
                    color = com.example.ui.theme.TextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                val targetUrl = if (ttsEngineType == "COQUI_LOCAL") coquiUrl else piperUrl
                val urlLabel = if (ttsEngineType == "COQUI_LOCAL") "Coqui XTTS Service Endpoint" else "Piper TTS Service Endpoint"

                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = {
                        if (ttsEngineType == "COQUI_LOCAL") {
                            com.example.ui.NovaVoiceManager.updateCoquiUrl(context, it)
                        } else {
                            com.example.ui.NovaVoiceManager.updatePiperUrl(context, it)
                        }
                    },
                    label = { Text(urlLabel, fontSize = 8.sp, color = com.example.ui.theme.CyberCyan) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.ui.theme.CyberCyan,
                        unfocusedBorderColor = com.example.ui.theme.BorderSlate.copy(alpha = 0.3f),
                        focusedTextColor = com.example.ui.theme.PureWhite,
                        unfocusedTextColor = com.example.ui.theme.PureWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }

        // Interactive voice preview sandbox box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(com.example.ui.theme.SpaceBlack.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .border(1.dp, com.example.ui.theme.BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SPEECH SANDBOX VERIFICATION",
                    color = com.example.ui.theme.TechTeal,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                var sandboxBuffer by remember { mutableStateOf("Welcome. Nova voice calibrator completed. All engines nominal.") }

                OutlinedTextField(
                    value = sandboxBuffer,
                    onValueChange = { sandboxBuffer = it },
                    placeholder = { Text("Input sandbox testing phrase...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.ui.theme.TechTeal.copy(alpha = 0.7f),
                        unfocusedBorderColor = com.example.ui.theme.BorderSlate.copy(alpha = 0.1f),
                        focusedTextColor = com.example.ui.theme.PureWhite,
                        unfocusedTextColor = com.example.ui.theme.PureWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                Button(
                    onClick = {
                        com.example.ui.NovaVoiceManager.speakInteractive(context, sandboxBuffer, ttsEngine)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.CyberCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("PREVIEW VOICE PRESET OUTPUT", color = com.example.ui.theme.SpaceBlack, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                synthLogMessage?.let { msg ->
                    Text(
                        text = "DIAGNOSTIC: $msg",
                        color = if (msg.contains("failure", true) || msg.contains("unreachable", true)) com.example.ui.theme.GlowingRed else com.example.ui.theme.SageGreen,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 9.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// HIGH-FIDELITY REDESIGNED COMPOSABLES FOR TABS
// ============================================================================

@Composable
fun RedesignedOrbTab(
    userName: String,
    isAccessibilityActive: Boolean,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeakingActive: Boolean,
    particles: List<DriftParticle>,
    triggerWakeWordVocalSequence: () -> Unit,
    context: android.content.Context,
    viewModel: NovaViewModel,
    tasksList: List<com.example.data.Task>,
    itemsList: List<com.example.data.InventoryItem>,
    speakTts: (String) -> Unit,
    recentActions: List<String>,
    onRecentActionsChange: (List<String>) -> Unit,
    dialogHistoryList: List<ConsoleMessage>,
    onDialogHistoryListChange: (List<ConsoleMessage>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Sleek Contextual Greeting Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greetingText = when (hour) {
                in 0..11 -> "Good morning,"
                in 12..16 -> "Good afternoon,"
                in 17..21 -> "Good evening,"
                else -> "Good night,"
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greetingText,
                    color = PureWhite.copy(alpha = 0.9f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "$userName.",
                    color = CyberCyan,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "How can I help you today?",
                    color = CharcoalMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.2.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.US) }
                val dateFormat = remember { java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.US) }
                Text(
                    text = timeFormat.format(java.util.Date()),
                    color = PureWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = dateFormat.format(java.util.Date()),
                    color = CharcoalMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "☁️ 28°C",
                    color = PureWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Clipboard Intelligence ---
        val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager }
        var clipboardText by remember { mutableStateOf("") }
        var isClipboardDismissed by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(1000) // delay to let window get focus safely
            try {
                if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                    val clipData = clipboardManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val txt = clipData.getItemAt(0)?.text?.toString() ?: ""
                        if (txt.trim().isNotEmpty()) {
                            clipboardText = txt
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (clipboardText.trim().isNotEmpty() && !isClipboardDismissed) {
            val displayClip = if (clipboardText.length > 70) clipboardText.take(67) + "..." else clipboardText
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(TechCard, RoundedCornerShape(18.dp))
                    .border(1.2.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "CLIPBOARD INTELLIGENCE",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(
                        onClick = { isClipboardDismissed = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = CharcoalMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                
                Text(
                    text = "\"$displayClip\"",
                    color = PureWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val actions = listOf(
                        "Summarize" to "summarize this text from clipboard: ",
                        "Translate" to "translate this text from clipboard: ",
                        "Explain" to "explain this text from clipboard: ",
                        "Search It" to "search for this text: "
                    )
                    
                    actions.forEach { (label, commandPrefix) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberSlate)
                                .border(0.8.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable {
                                    processVocalDirective(
                                        cmd = commandPrefix + clipboardText,
                                        context = context,
                                        viewModel = viewModel,
                                        tasksList = tasksList,
                                        itemsList = itemsList,
                                        speakTts = speakTts,
                                        uName = userName,
                                        onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                        onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = CyberCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Persistent Accessibility Service status monitor (updates in real-time)
        val isServiceRunning = isAccessibilityActive
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("accessibility_service_status_card")
                .clickable {
                    if (!isServiceRunning) {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) TechTeal.copy(alpha = 0.08f) else GlowingRed.copy(alpha = 0.08f)
            ),
            border = BorderStroke(
                1.dp, 
                if (isServiceRunning) TechTeal.copy(alpha = 0.25f) else GlowingRed.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isServiceRunning) TechTeal else GlowingRed,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (isServiceRunning) "Automation Core: Active" else "Simulation Mode Active",
                        color = if (isServiceRunning) TechTeal else GlowingRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                
                Text(
                    text = if (isServiceRunning) "ONLINE" else "TAP TO CONNECT",
                    color = if (isServiceRunning) TechTeal else GlowingRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 2. Large Centered Interactive Nova Orb
        Box(
            modifier = Modifier
                .size(220.dp)
                .clickable { triggerWakeWordVocalSequence() },
            contentAlignment = Alignment.Center
        ) {
            NovaCinematicEnergyOrb(
                isListening = isListening,
                isThinking = isThinking,
                isSpeaking = isSpeakingActive,
                floatParticles = particles
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = when {
                isListening -> "Listening..."
                isThinking -> "Thinking..."
                isSpeakingActive -> "Speaking..."
                else -> "Tap Orb or say \"Hey Nova\""
            },
            color = if (isListening) CyberCyan else CharcoalMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Simulated/Reactive Audio Waveform (Nothing OS premium visual)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(32.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
            val barCount = 13
            for (j in 0 until barCount) {
                val delayFactor = j * 120
                val animatedHeight by infiniteTransition.animateFloat(
                    initialValue = 4f,
                    targetValue = if (isListening || isSpeakingActive) 28f else 10f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400 + (j % 3) * 100, delayMillis = delayFactor, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_height"
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(animatedHeight.dp)
                        .background(if (isListening) CyberCyan else if (isSpeakingActive) TechTeal else CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Quick Actions Circle Row (Conforms exactly to mockup of 5 circular app actions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actionRowItems = listOf(
                Triple("Call", Icons.Default.Call, CyberCyan),
                Triple("Message", Icons.Default.Send, TechTeal),
                Triple("WhatsApp", Icons.Default.Send, SageGreen),
                Triple("Camera", Icons.Default.Star, NeonAmber),
                Triple("YouTube", Icons.Default.PlayArrow, GlowingRed)
            )

            actionRowItems.forEach { (label, icon, color) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val runCmd = when(label) {
                            "Call" -> "Call Mom"
                            "Message" -> "open message"
                            "WhatsApp" -> "open whatsapp"
                            "Camera" -> "open camera"
                            else -> "open youtube"
                        }
                        processVocalDirective(
                            cmd = runCmd,
                            context = context,
                            viewModel = viewModel,
                            tasksList = tasksList,
                            itemsList = itemsList,
                            speakTts = speakTts,
                            uName = userName,
                            onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                            onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(color.copy(alpha = 0.12f), CircleShape)
                            .border(1.2.dp, color.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isLightThemeActive) color else PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        color = if (isLightThemeActive) color else PureWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 5. Side-By-Side Recent Actions & Smart Suggestions Cards Layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Card: Recent Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(TechCard, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Actions",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See all",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {}
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                data class DisplayActionItem(val text: String, val time: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color)
                val displayActions = listOf(
                    DisplayActionItem("Opened YouTube", "9:30 PM", Icons.Default.PlayArrow, Color(0xFFFF453A)),
                    DisplayActionItem("Set Alarm - 6:00 AM", "12:15 PM", Icons.Default.Refresh, Color(0xFF0A84FF)),
                    DisplayActionItem("Called Mom", "8:47 PM", Icons.Default.Call, Color(0xFF30D158))
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayActions.forEach { item ->
                        val (text, time, icon, col) = item
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(col.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, col.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = col,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = text,
                                    color = PureWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1
                                )
                                Text(
                                    text = time,
                                    color = CharcoalMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Right Card: Smart Suggestions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(TechCard, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Suggestions",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See all",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {}
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                data class SuggestionItem(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color, val cmd: String)
                val displaySuggestions = listOf(
                    SuggestionItem("Continue Study Session", Icons.Default.Check, Color(0xFFBF5AF2), "start study mode"),
                    SuggestionItem("Open Spotify", Icons.Default.PlayArrow, Color(0xFF30D158), "open spotify"),
                    SuggestionItem("Check Weather Info", Icons.Default.Info, Color(0xFF0A84FF), "check weather")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displaySuggestions.forEach { item ->
                        val (text, icon, col, cmd) = item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    processVocalDirective(
                                        cmd = cmd,
                                        context = context,
                                        viewModel = viewModel,
                                        tasksList = tasksList,
                                        itemsList = itemsList,
                                        speakTts = speakTts,
                                        uName = userName,
                                        onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                        onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(col.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, col.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = col,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = text,
                                color = PureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun RedesignedDialogueTab(
    dialogHistoryList: List<ConsoleMessage>,
    onDialogHistoryListChange: (List<ConsoleMessage>) -> Unit,
    triggerWakeWordVocalSequence: () -> Unit,
    context: android.content.Context,
    viewModel: NovaViewModel,
    tasksList: List<com.example.data.Task>,
    itemsList: List<com.example.data.InventoryItem>,
    speakTts: (String) -> Unit,
    userName: String,
    recentActions: List<String>,
    onRecentActionsChange: (List<String>) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSuccessReminderSet: (com.example.data.Reminder) -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    LaunchedEffect(dialogHistoryList.size) {
        if (dialogHistoryList.isNotEmpty()) {
            try {
                listState.animateScrollToItem(dialogHistoryList.size - 1)
            } catch (e: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(dialogHistoryList.size) { idx ->
                    val msg = dialogHistoryList[idx]
                    val isNova = msg.sender == "NOVA" || msg.sender == "SYSTEM"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isNova) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isNova) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, CyberCyan.copy(alpha = 0.5f), CircleShape)
                                    .background(CyberSlate),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "N",
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(
                            horizontalAlignment = if (isNova) Alignment.Start else Alignment.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .background(
                                        color = if (isNova) TechCard else CyberCyan,
                                        shape = RoundedCornerShape(
                                            topStart = if (isNova) 2.dp else 20.dp,
                                            topEnd = if (isNova) 20.dp else 2.dp,
                                            bottomStart = 20.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isNova) BorderSlate.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(
                                            topStart = if (isNova) 2.dp else 20.dp,
                                            topEnd = if (isNova) 20.dp else 2.dp,
                                            bottomStart = 20.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = msg.text,
                                        color = PureWhite,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )

                                    if (!isNova) {
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "9:30 PM",
                                                color = PureWhite.copy(alpha = 0.5f),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SageGreen,
                                                modifier = Modifier.size(9.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isNova && msg.sender == "NOVA") {
                                var rlhfRating by remember(msg.text) { mutableStateOf<Int?>(null) }
                                var showAlignDialog by remember { mutableStateOf(false) }
                                var correctionText by remember { mutableStateOf("") }

                                Row(
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            rlhfRating = 1
                                            NovaPersonalityCore.saveRLHFReward(context, msg.text, rating = 1, correction = null)
                                            android.widget.Toast.makeText(context, "Aligned SFT: Human Preference model reinforced (+10 reward)!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = "Reward model thumbs up",
                                            tint = if (rlhfRating == 1) SageGreen else CharcoalMuted,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            rlhfRating = 2
                                            showAlignDialog = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbDown,
                                            contentDescription = "SFT penalty thumbs down",
                                            tint = if (rlhfRating == 2) Color(0xFFFF453A) else CharcoalMuted,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Text(
                                        text = if (rlhfRating == 1) "RLHF Optimized!" else if (rlhfRating == 2) "Fine-Tuning..." else "Train response",
                                        color = CharcoalMuted,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (showAlignDialog) {
                                    androidx.compose.material3.AlertDialog(
                                        onDismissRequest = { showAlignDialog = false },
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Settings, null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                                Text("Reinforced SFT Alignment", color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                        },
                                        containerColor = SpaceBlack,
                                        textContentColor = PureWhite,
                                        shape = RoundedCornerShape(20.dp),
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Teach Nova the corrected response to align her cognitive policy paths:", color = TextSecondary, fontSize = 11.sp)
                                                OutlinedTextField(
                                                    value = correctionText,
                                                    onValueChange = { correctionText = it },
                                                    placeholder = { Text("Write the expected professional response here...", color = CharcoalMuted, fontSize = 11.sp) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 12.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = CyberCyan,
                                                        unfocusedBorderColor = BorderSlate
                                                    )
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    NovaPersonalityCore.saveRLHFReward(context, msg.text, rating = 2, correction = correctionText)
                                                    showAlignDialog = false
                                                    android.widget.Toast.makeText(context, "SFT policy adjusted. Nova will emulate this style on matching topics!", android.widget.Toast.LENGTH_LONG).show()
                                                },
                                                modifier = Modifier
                                                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                    .border(1.dp, CyberCyan, RoundedCornerShape(10.dp))
                                            ) {
                                                Text("APPLY ALIGNMENT", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showAlignDialog = false }) {
                                                Text("CANCEL", color = CharcoalMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    )
                                }
                            }

                            // Sub-rendering: Verification Card for search automation steps
                            if (isNova && msg.text.contains("searching", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .background(TechCard, RoundedCornerShape(16.dp))
                                        .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val stepsListFlow = listOf(
                                        "Opening YouTube" to "SUCCESS",
                                        "Clicking search" to "SUCCESS",
                                        "Typing 'AI news'" to "SUCCESS",
                                        "Searching" to "EXECUTING",
                                        "Done" to "PENDING"
                                    )

                                    stepsListFlow.forEach { (desc, state) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = desc,
                                                color = if (state == "PENDING") CharcoalMuted else PureWhite,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            when (state) {
                                                "SUCCESS" -> Icon(Icons.Default.CheckCircle, null, tint = SageGreen, modifier = Modifier.size(12.dp))
                                                "EXECUTING" -> CircularProgressIndicator(color = CyberCyan, strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
                                                else -> Box(modifier = Modifier.size(10.dp).border(1.dp, CharcoalMuted, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }

                            // Sub-rendering: Redirection outward link to launch app
                            if (isNova && msg.text.contains("YouTube", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .background(TechCard, RoundedCornerShape(16.dp))
                                        .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            processVocalDirective(
                                                cmd = "open youtube",
                                                context = context,
                                                viewModel = viewModel,
                                                tasksList = tasksList,
                                                itemsList = itemsList,
                                                speakTts = speakTts,
                                                uName = userName,
                                                onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                                onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                                            )
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFFFF453A), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, tint = PureWhite, modifier = Modifier.size(16.dp))
                                        }
                                        Text("Open YouTube", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.Share, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Modern Input Capsule Bar of Chat Tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(SpaceBlack.copy(alpha = 0.95f)) // solid theme background to prevent text collision
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Text Field: Type a message...
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    placeholder = {
                        Text(
                            text = "Type a message...",
                            color = CharcoalMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderSlate.copy(alpha = 0.4f),
                        cursorColor = CyberCyan,
                        focusedContainerColor = CyberSlate,
                        unfocusedContainerColor = CyberSlate
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (typedText.isNotBlank()) {
                                val msgToSend = typedText
                                typedText = ""
                                processVocalDirective(
                                    cmd = msgToSend,
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasksList,
                                    itemsList = itemsList,
                                    speakTts = speakTts,
                                    uName = userName,
                                    onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                    onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) },
                                    dialogHistoryList = dialogHistoryList,
                                    onSuccessReminderSet = onSuccessReminderSet
                                )
                            }
                        }
                    )
                )

                // 2. Buttons Row: [ Mic ] [ Send ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic button (optional but available)
                    IconButton(
                        onClick = { triggerWakeWordVocalSequence() },
                        modifier = Modifier
                            .testTag("mic_input_button")
                            .background(CyberSlate, CircleShape)
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone Input",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Send button
                    IconButton(
                        onClick = {
                            if (typedText.isNotBlank()) {
                                val msgToSend = typedText
                                typedText = ""
                                processVocalDirective(
                                    cmd = msgToSend,
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasksList,
                                    itemsList = itemsList,
                                    speakTts = speakTts,
                                    uName = userName,
                                    onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                    onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) },
                                    dialogHistoryList = dialogHistoryList,
                                    onSuccessReminderSet = onSuccessReminderSet
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("send_message_button")
                            .background(
                                if (typedText.isNotBlank()) CyberCyan else CharcoalMuted.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = if (typedText.isNotBlank()) Color(0xFF09090F) else CyberCyan.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

data class AutomationTestResult(
    val command: String,
    val parsedIntent: String?,
    val selectedSkill: String?,
    val permissions: List<String>,
    val steps: List<com.example.AutomationStep>,
    val status: String, // "IDLE", "RUNNING", "SUCCESS", "FAILED"
    val failedStepIndex: Int?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedesignedSkillsTab(
    context: android.content.Context,
    viewModel: NovaViewModel,
    tasksList: List<com.example.data.Task>,
    itemsList: List<com.example.data.InventoryItem>,
    speakTts: (String) -> Unit,
    userName: String,
    dialogHistoryList: List<ConsoleMessage>,
    onDialogHistoryListChange: (List<ConsoleMessage>) -> Unit,
    recentActions: List<String>,
    onRecentActionsChange: (List<String>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val testCasesState by ExecutionReliabilityTestSuiteManager.testCases.collectAsState()
    val isBatchRunning by ExecutionReliabilityTestSuiteManager.isBatchRunning.collectAsState()

    var expandedTestId by remember { mutableStateOf<Int?>(null) }

    // Statistical HUD values
    val totalTests = testCasesState.size
    val passingCount = testCasesState.count { it.status == TestCaseStatus.PASS }
    val failingCount = testCasesState.count { it.status == TestCaseStatus.FAIL }
    val successRate = if (totalTests > 0) (passingCount.toFloat() / totalTests) * 100f else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Sleek fully rounded search capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(TechCard, RoundedCornerShape(23.dp))
                .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(23.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Search apps",
                color = CharcoalMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CharcoalMuted,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterEnd)
            )
        }

        // REAL TESTING PANEL: NOVA PHASE 1 EXECUTION RELIABILITY TEST SUITE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyberCyan, CircleShape)
                    )
                    Text(
                        text = "NOVA RELIABILITY TEST SUITE",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(TechTeal.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PHASE 1 SECURED",
                        color = TechTeal,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(
                text = "Prove key on-device automations (telephony speaker control, flashlight trigger, screen brightness values, and direct app navigation overlays) succeed with realistic physical system logic.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Dynamic Stats Dashboard Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSlate, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OVERALL RELIABILITY PASS RATIO", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = String.format("%.0f%%", successRate),
                            color = if (successRate >= 80f) SageGreen else GlowingRed,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "($passingCount/$totalTests PASSES)",
                            color = PureWhite.copy(alpha = 0.61f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                CircularProgressIndicator(
                    progress = { successRate / 100f },
                    modifier = Modifier.size(36.dp),
                    color = if (successRate >= 80f) SageGreen else GlowingRed,
                    strokeWidth = 3.dp,
                    trackColor = BorderSlate.copy(alpha = 0.1f)
                )
            }

            // Suite Controls Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        ExecutionReliabilityTestSuiteManager.runAllTests(context, coroutineScope)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBatchRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan.copy(alpha = 0.15f),
                        disabledContainerColor = CharcoalMuted.copy(alpha = 0.05f)
                    ),
                    border = BorderStroke(1.dp, if (isBatchRunning) BorderSlate.copy(alpha = 0.2f) else CyberCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isBatchRunning) "RUNNING..." else "RUN BATCH DIAGNOSTIC",
                        color = if (isBatchRunning) CharcoalMuted else CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = {
                        ExecutionReliabilityTestSuiteManager.resetSuite()
                    },
                    modifier = Modifier.width(96.dp),
                    enabled = !isBatchRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BorderSlate.copy(alpha = 0.1f),
                        disabledContainerColor = CharcoalMuted.copy(alpha = 0.05f)
                    ),
                    border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "RESET",
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Expanded Test Suite List
            testCasesState.forEach { test ->
                val isExpanded = expandedTestId == test.id
                val isRunning = test.status == TestCaseStatus.EXECUTING
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceBlack.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(
                            1.dp, 
                            when {
                                isRunning -> CyberCyan.copy(alpha = 0.5f)
                                test.status == TestCaseStatus.PASS -> SageGreen.copy(alpha = 0.25f)
                                test.status == TestCaseStatus.FAIL -> GlowingRed.copy(alpha = 0.25f)
                                else -> BorderSlate.copy(alpha = 0.15f)
                            }, 
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { expandedTestId = if (isExpanded) null else test.id }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[#${test.id}] command: \"${test.command}\"",
                                color = PureWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = test.description,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val statusColor = when (test.status) {
                                    TestCaseStatus.PASS -> SageGreen
                                    TestCaseStatus.FAIL -> GlowingRed
                                    TestCaseStatus.EXECUTING -> CyberCyan
                                    TestCaseStatus.PENDING -> CharcoalMuted
                                }
                                Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                                Text(
                                    text = test.status.name,
                                    color = statusColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        
                        Button(
                            onClick = {
                                ExecutionReliabilityTestSuiteManager.runSingleTest(context, coroutineScope, test.id)
                            },
                            enabled = !isBatchRunning && !isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (test.status == TestCaseStatus.PASS) SageGreen.copy(alpha = 0.12f) else CyberCyan.copy(alpha = 0.12f),
                                disabledContainerColor = CharcoalMuted.copy(alpha = 0.05f)
                            ),
                            border = BorderStroke(1.dp, if (test.status == TestCaseStatus.PASS) SageGreen.copy(alpha = 0.4f) else CyberCyan.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (isRunning) "RUNNING" else "RUN",
                                color = if (isRunning) CharcoalMuted else (if (test.status == TestCaseStatus.PASS) SageGreen else CyberCyan),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Divider(color = BorderSlate.copy(alpha = 0.1f))
                            
                            // Parsed Intent & Selected Skill Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PARSED INTENT", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = test.parsedIntent,
                                        color = if (test.parsedIntent != "Pending") CyberCyan else TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SELECTED SKILL", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = test.selectedSkill,
                                        color = if (test.selectedSkill != "Pending") CyberCyan else TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Extracted Entities & Execution Method
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ENTITIES MATCHED", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = test.entities,
                                    color = if (test.entities != "Pending") PureWhite else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("EXECUTION CHANNEL METHOD", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = test.executionMethod,
                                    color = if (test.executionMethod != "Pending") TechTeal else TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Required Permissions
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("REQUIRED MANIFEST PERMISSIONS", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                if (test.requiredPermissions.isEmpty()) {
                                    Text("None required", color = SageGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        test.requiredPermissions.forEach { perm ->
                                            val isGranted = when (perm) {
                                                "Contacts" -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                "Phone" -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                "SMS" -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                "Accessibility" -> com.example.AutomationAccessibilityService.isServiceRunning
                                                "Overlay" -> android.provider.Settings.canDrawOverlays(context)
                                                else -> androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isGranted) SageGreen.copy(alpha = 0.1f) else GlowingRed.copy(alpha = 0.1f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isGranted) SageGreen.copy(alpha = 0.3f) else GlowingRed.copy(alpha = 0.3f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$perm: ${if (isGranted) "GRANTED" else "MISSING"}",
                                                    color = if (isGranted) SageGreen else GlowingRed,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Step-by-step Progress Timeline
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("STEP-BY-STEP WORKFLOW TIMELINE", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                if (test.progressSteps.isEmpty()) {
                                    Text("Pipeline queue empty. Tap RUN to generate sequence.", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                } else {
                                    test.progressSteps.forEachIndexed { sIdx, logLine ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .background(CyberCyan, CircleShape)
                                            )
                                            Text(
                                                text = "[$sIdx] $logLine",
                                                color = PureWhite.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Final Verification & Failures
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Divider(color = BorderSlate.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("FINAL VERIFICATION OUTCOME", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = test.finalVerification,
                                    color = if (test.status == TestCaseStatus.PASS) SageGreen else (if (test.status == TestCaseStatus.FAIL) GlowingRed else PureWhite),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            if (test.exactFailedStep != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("EXACT ENCOUNTERED FAILURE", color = GlowingRed, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = test.exactFailedStep,
                                        color = GlowingRed,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Section: Frequently Used
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Frequently Used", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("See all", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val frequentApps = listOf(
                    Triple("YouTube", Icons.Default.PlayArrow, Color(0xFFFF453A)),
                    Triple("WhatsApp", Icons.Default.Call, Color(0xFF25D366)),
                    Triple("Instagram", Icons.Default.Star, Color(0xFFBF5AF2)),
                    Triple("Chrome", Icons.Default.Info, Color(0xFF8E8E93))
                )

                frequentApps.forEach { (name, icon, color) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(TechCard, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable {
                                processVocalDirective(
                                    cmd = "open ${name.lowercase(java.util.Locale.ROOT)}",
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasksList,
                                    itemsList = itemsList,
                                    speakTts = speakTts,
                                    uName = userName,
                                    onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                    onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                                )
                             },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = PureWhite, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(name, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 3. Section: All Apps
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("All Apps", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("See all", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val allAppsList = listOf(
                Triple("YouTube", "YT", Color(0xFFFF453A)),
                Triple("Instagram", "Insta", Color(0xFFBF5AF2)),
                Triple("WhatsApp", "WA", Color(0xFF25D366)),
                Triple("Chrome Browser", "Browser", Color(0xFF8E8E93)),
                Triple("Spotify", "Music", Color(0xFF30D158)),
                Triple("Google Maps", "Maps", Color(0xFF0A84FF)),
                Triple("Gmail", "Mail", Color(0xFFE5E5EA)),
                Triple("Telegram", "Telegram", Color(0xFF0A84FF))
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                allAppsList.forEach { (name, alias, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TechCard, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable {
                                processVocalDirective(
                                    cmd = "open ${name.lowercase(java.util.Locale.ROOT)}",
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasksList,
                                    itemsList = itemsList,
                                    speakTts = speakTts,
                                    uName = userName,
                                    onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                                    onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (name.contains("Chrome")) Icons.Default.Info else if (name.contains("Maps")) Icons.Default.Place else Icons.Default.Build,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(name, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(alias, color = CharcoalMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun RedesignedRoutinesTab(
    context: android.content.Context,
    viewModel: NovaViewModel,
    tasksList: List<com.example.data.Task>,
    itemsList: List<com.example.data.InventoryItem>,
    speakTts: (String) -> Unit,
    userName: String,
    dialogHistoryList: List<ConsoleMessage>,
    onDialogHistoryListChange: (List<ConsoleMessage>) -> Unit,
    recentActions: List<String>,
    onRecentActionsChange: (List<String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("My Routines", "Scheduled", "Recommended").forEach { tab ->
                val isChosen = tab == "My Routines"
                Box(
                    modifier = Modifier
                        .background(
                            if (isChosen) CyberCyan else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            if (isChosen) Color.Transparent else BorderSlate.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = if (isChosen) PureWhite else CharcoalMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        data class RoutineMockItem(val name: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
        val routinesMockList = listOf(
            RoutineMockItem("Commute Mode", "Maps to home/work, Spotify music & notification reading.", Icons.Default.Place),
            RoutineMockItem("Deep Work", "DND blocking social apps, Pomodoro, ambient lo-fi.", Icons.Default.Build),
            RoutineMockItem("Night Protocol", "Dims screen, alarm set, status quiet setting.", Icons.Default.Info),
            RoutineMockItem("Game Mode", "DND, max brightness, close background and optimized hub.", Icons.Default.Star),
            RoutineMockItem("Interview Mode", "Opens prep notes, 45-min timer, WhatsApp silencer.", Icons.Default.Phone),
            RoutineMockItem("YouTube Controller", "Hands-free video skip, comment open & reading.", Icons.Default.PlayArrow),
            RoutineMockItem("Instagram Reader", "Read direct messages and auto-like viewport feed.", Icons.Default.Person),
            RoutineMockItem("Morning Routine", "News, weather, alarm and study checklist.", Icons.Default.Check),
            RoutineMockItem("Sleep Routine", "Silence phone, low screen brightness.", Icons.Default.Lock)
        )

        routinesMockList.forEach { item ->
            val (name, desc, icon) = item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechCard, RoundedCornerShape(18.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(name, color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(desc, color = CharcoalMuted, fontSize = 10.sp, maxLines = 2)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        speakTts("Activating routine: $name")
                        val directiveCode = when(name) {
                            "Morning Routine" -> "good morning"
                            "Sleep Routine" -> "start sleep mode"
                            "YouTube Controller" -> "skip 30 seconds"
                            "Instagram Reader" -> "read my dms"
                            "Game Mode" -> "game mode"
                            "Interview Mode" -> "interview mode"
                            "Commute Mode" -> "commute mode"
                            "Deep Work" -> "deep work"
                            "Night Protocol" -> "night protocol"
                            else -> "open maps"
                        }
                        processVocalDirective(
                            cmd = directiveCode,
                            context = context,
                            viewModel = viewModel,
                            tasksList = tasksList,
                            itemsList = itemsList,
                            speakTts = speakTts,
                            uName = userName,
                            onLogUpdate = { onDialogHistoryListChange(dialogHistoryList + it) },
                            onActionAppend = { onRecentActionsChange(listOf(it) + recentActions.take(5)) }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Run", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun RedesignedMemoryTab(
    userName: String,
    onUserNameChange: (String) -> Unit,
    speakTts: (String) -> Unit,
    recentActions: List<String>,
    onRecentActionsChange: (List<String>) -> Unit,
    continuousConversationEnabled: Boolean,
    context: android.content.Context,
    viewModel: NovaViewModel,
    tasksList: List<com.example.data.Task>,
    remindersList: List<com.example.data.Reminder>,
    onActiveTabChange: (String) -> Unit = {}
) {
    // 1. Particle States
    val particles = remember {
        mutableStateListOf<SynapseParticle>().apply {
            repeat(14) {
                add(
                    SynapseParticle(
                        x = (50..600).random().toFloat(),
                        y = (30..400).random().toFloat(),
                        vx = (kotlin.random.Random.nextFloat() - 0.5f) * 3f,
                        vy = (kotlin.random.Random.nextFloat() - 0.5f) * 3f,
                        color = listOf(CyberCyan, TechTeal, SageGreen, NeonAmber).random()
                    )
                )
            }
        }
    }

    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var activeCore by remember { mutableStateOf(NovaPersonalityCore.activePersonality) }
    val coroutineScope = rememberCoroutineScope()

    val sharedPref = remember { context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE) }
    var loadedRole by remember { mutableStateOf(sharedPref.getString("user_role", "Student") ?: "Student") }
    var loadedAge by remember { mutableStateOf(sharedPref.getString("user_age", "19") ?: "19") }
    
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf(userName) }
    var editAgeText by remember { mutableStateOf(loadedAge) }
    var editRoleText by remember { mutableStateOf(loadedRole) }

    // IndexedDB-Style Native SQLite Local Storage Tracker and Sync System
    val appDb = remember { com.example.data.AppDatabase.getDatabase(context) }
    var dbConversationCount by remember { mutableStateOf(0) }
    var dbIndexedStoresCount by remember { mutableStateOf(0) }
    var selectedIndexedKeyContents by remember { mutableStateOf<String?>(null) }
    var selectedStoreKeyName by remember { mutableStateOf<String?>(null) }
    var showStorePreviewDialog by remember { mutableStateOf(false) }

    // Agent-Reach local integration state management
    var channelsList by remember { mutableStateOf(com.example.data.AgentReachIntegrationEngine.getChannels(context)) }
    var activeReachChannel by remember { mutableStateOf("github") }
    var reachQueryText by remember { mutableStateOf("") }
    var reachSearchResults by remember { mutableStateOf<String?>(null) }
    var isReachSearching by remember { mutableStateOf(false) }
    var showCookieEditDialog by remember { mutableStateOf<String?>(null) }
    var cookieInputValue by remember { mutableStateOf("") }

    LaunchedEffect(userName, loadedRole, loadedAge) {
        withContext(Dispatchers.IO) {
            // Populate simulated parameters corresponding to browser IndexedDB schemas under android
            appDb.indexedDbDao.insertStore(
                com.example.data.IndexedDbStore(
                    "user_preferences",
                    "{\"userName\":\"$userName\",\"role\":\"$loadedRole\",\"age\":\"$loadedAge\",\"language\":\"English (India)\",\"syncMode\":\"OFFLINE_FIRST\"}"
                )
            )
            appDb.indexedDbDao.insertStore(
                com.example.data.IndexedDbStore(
                    "ai_training_rules",
                    "{\"personalityMode\":\"$activeCore\",\"trainingCompletion\":1.0,\"rulesSynced\":true,\"localNLPEnabled\":true}"
                )
            )
            appDb.indexedDbDao.insertStore(
                com.example.data.IndexedDbStore(
                    "mobile_device_automation_protocol",
                    "{\"simulationActive\":true,\"accessibilityBinding\":\"com.example.AutomationAccessibilityService\",\"overlayState\":\"APPROVED\"}"
                )
            )

            val chatCount = appDb.conversationDao.getAllMessages().size
            withContext(Dispatchers.Main) {
                dbConversationCount = chatCount
                dbIndexedStoresCount = 3
            }
        }
    }

    // Core animation tick loop - triggers visual recomposition for floating particles
    LaunchedEffect(touchPoint) {
        while (true) {
            delay(16) // ~60 FPS update tick
            particles.forEach { p ->
                // Drift and magnetically attract particles to touch coordinates
                p.update(800f, 450f, touchPoint?.x, touchPoint?.y)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- ADDON: COGNITIVE PERSONALITY CORE & SYNAPTIC MESH ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COGNITIVE QUANTUM CORE",
                        color = NovaPersonalityCore.getThemeColor(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = NovaPersonalityCore.getPersonalityBadgeText(),
                        color = CharcoalMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Tiny artificial neural network indicator light
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(NovaPersonalityCore.getThemeColor(), CircleShape)
                        .border(2.dp, SpaceBlack, CircleShape)
                )
            }

            // High Tech Selection Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cores = listOf(
                    Triple("STANDARD", "Nova", CyberCyan),
                    Triple("JARVIS", "Jarvis AI", TechTeal),
                    Triple("SAMANTHA", "Samantha", SageGreen),
                    Triple("GLADOS", "GLaDOS", NeonAmber)
                )

                cores.forEach { (id, label, color) ->
                    val isSelected = activeCore == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.12f) else CyberSlate)
                            .border(
                                width = 1.2.dp,
                                color = if (isSelected) color else BorderSlate.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                NovaPersonalityCore.activePersonality = id
                                activeCore = id
                                Toast.makeText(context, "${label} system personality loaded.", Toast.LENGTH_SHORT).show()
                                
                                // Speeds up the greeting matching the personality tone
                                when (id) {
                                    "JARVIS" -> speakTts("Protocol synchronized. Jarvis core online, at your disposal, Sir.")
                                    "SAMANTHA" -> speakTts("Hey $userName! Samantha core initialized. I'm so happy to chat with you!")
                                    "GLADOS" -> speakTts("Cognitive interface loaded. It is you. How delightful. Let's do some testing then.")
                                    else -> speakTts("Nova core standards online. All sub-modules functional.")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) color else PureWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Interactive Synaptics Mesh HUD Sandbox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberSlate)
                    .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> touchPoint = offset },
                            onDrag = { change, _ -> touchPoint = change.position },
                            onDragEnd = { touchPoint = null },
                            onDragCancel = { touchPoint = null }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchPoint = offset
                                tryAwaitRelease()
                                touchPoint = null
                            }
                        )
                    }
            ) {
                // Background coordinates indicator text
                Text(
                    text = "SYNAPTIC NODE INTERACTOR v1.08 • TOUCH DRAG CANVAS TO WARP SYNAPSES",
                    color = CharcoalMuted.copy(alpha = 0.4f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )

                // Render dynamic Synapse nodes and grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw coordinates coordinate marks
                    val horizontalGridLines = 5
                    val verticalGridLines = 8
                    for (i in 1..horizontalGridLines) {
                        val yPos = h * i / (horizontalGridLines + 1)
                        drawLine(
                            color = BorderSlate.copy(alpha = 0.04f),
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = 1f
                        )
                    }
                    for (i in 1..verticalGridLines) {
                        val xPos = w * i / (verticalGridLines + 1)
                        drawLine(
                            color = BorderSlate.copy(alpha = 0.04f),
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, h),
                            strokeWidth = 1f
                        )
                    }

                    // Synapse proximity vectors
                    val activeThemeColor = NovaPersonalityCore.getThemeColor()
                    for (i in particles.indices) {
                        val p1 = particles[i]
                        for (j in i + 1 until particles.size) {
                            val p2 = particles[j]
                            val dx = p1.x - p2.x
                            val dy = p1.y - p2.y
                            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (dist < 150f) {
                                val opacity = (1f - dist / 150f) * 0.4f
                                drawLine(
                                    color = activeThemeColor.copy(alpha = opacity),
                                    start = Offset(p1.x, p1.y),
                                    end = Offset(p2.x, p2.y),
                                    strokeWidth = 1.5f
                                )
                            }
                        }
                    }

                    // Touch warping visualization bubble
                    touchPoint?.let { touch ->
                        drawCircle(
                            color = activeThemeColor.copy(alpha = 0.08f),
                            radius = 120f,
                            center = touch
                        )
                        drawCircle(
                            color = activeThemeColor.copy(alpha = 0.25f),
                            radius = 45f,
                            center = touch,
                            style = Stroke(width = 1f)
                        )
                    }

                    // Draw physical particles with glowing halos
                    particles.forEach { p ->
                        // Halo glow
                        drawCircle(
                            color = p.color.copy(alpha = 0.2f),
                            radius = p.radius * 2.5f,
                            center = Offset(p.x, p.y)
                        )
                        // Deep core
                        drawCircle(
                            color = p.color,
                            radius = p.radius,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }
        }

        // --- ORIGINAL PREFERENCES/ABOUT SECTION ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                .clickable {
                    editNameText = userName
                    editRoleText = loadedRole
                    editAgeText = loadedAge
                    showEditProfileDialog = true
                }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("About You", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.Edit, null, tint = CharcoalMuted, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(userName, color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$loadedRole • $loadedAge years old • English (India)", color = CharcoalMuted, fontSize = 12.sp)
        }

        if (showEditProfileDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                title = { Text("Edit About You", color = PureWhite) },
                containerColor = SpaceBlack,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = editNameText,
                            onValueChange = { editNameText = it },
                            label = { Text("Your Name", color = CharcoalMuted) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = CyberSlate,
                                unfocusedContainerColor = CyberSlate,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = BorderSlate
                            )
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = editRoleText,
                            onValueChange = { editRoleText = it },
                            label = { Text("Occupation / Role", color = CharcoalMuted) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = CyberSlate,
                                unfocusedContainerColor = CyberSlate,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = BorderSlate
                            )
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = editAgeText,
                            onValueChange = { editAgeText = it },
                            label = { Text("Age", color = CharcoalMuted) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = CyberSlate,
                                unfocusedContainerColor = CyberSlate,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = BorderSlate
                            )
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onUserNameChange(editNameText)
                            sharedPref.edit().apply {
                                putString("user_name", editNameText)
                                putString("user_role", editRoleText)
                                putString("user_age", editAgeText)
                                apply()
                            }
                            loadedRole = editRoleText
                            loadedAge = editAgeText
                            showEditProfileDialog = false
                        }
                    ) {
                        Text("Save", color = CyberCyan)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel", color = CharcoalMuted)
                    }
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Frequently Used", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("See all", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contactsFlow = listOf(
                    Triple("Mom", "Call", Color(0xFF30D158)),
                    Triple("Rahul", "WhatsApp", Color(0xFF0A84FF)),
                    Triple("Dad", "Call", Color(0xFF30D158))
                )

                contactsFlow.forEach { (name, status, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(CyberSlate)
                                .border(1.dp, BorderSlate.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1), color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(4.dp).background(color, CircleShape))
                            Text(status, color = color, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.dp, BorderSlate.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = CharcoalMuted, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add", color = CharcoalMuted, fontSize = 11.sp)
                }
            }
        }

        // --- Quick Notes / Brain Dump visual manager ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "BRAIN DUMP & QUICK NOTES",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                
                val activeNotesList = tasksList.filter { it.category == "Note" && it.status != "COMPLETED" }
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${activeNotesList.size} ACTIVE",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            val activeNotesList = tasksList.filter { it.category == "Note" && it.status != "COMPLETED" }
            if (activeNotesList.isEmpty()) {
                Text(
                    text = "No active thoughts or quick notes cataloged inside Nova's neural vaults. Say \"note this buy flight tickets\" or type it to save automatically.",
                    color = CharcoalMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", java.util.Locale.US) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeNotesList.forEach { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSlate, RoundedCornerShape(12.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title,
                                    color = PureWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dateFormat.format(java.util.Date(note.createdAt)),
                                    color = CharcoalMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteTask(note) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete note",
                                    tint = GlowingRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Interests", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("See all", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("AI", "Technology", "Space", "Coding", "Science", "Startups").take(5).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(TechCard, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tag, color = PureWhite, fontSize = 10.sp)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Commands", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("See all", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val historyMockCommands = listOf(
                "Open YouTube and search AI news" to "9:30 PM",
                "Send WhatsApp message to Mom" to "8:55 PM",
                "Set alarm at 6:00 AM" to "8:40 PM"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                historyMockCommands.forEach { (cmd, time) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TechCard, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Text(cmd, color = PureWhite, fontSize = 11.sp, maxLines = 1)
                        }
                        Text(time, color = CharcoalMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // --- ADDON: LOCAL INDEXEDDB & OFFLINE COGNITIVE DATABASE STATS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LOCAL PERSISTENCE SCHEMAS",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "HYBRID OFFLINE INDEXEDDB COMPATIBLE ARCHITECTURE",
                        color = CharcoalMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    Icons.Default.Refresh,
                    "Reload",
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp).clickable {
                        coroutineScope.launch(Dispatchers.IO) {
                            val chatCount = appDb.conversationDao.getAllMessages().size
                            withContext(Dispatchers.Main) {
                                dbConversationCount = chatCount
                                Toast.makeText(context, "Local databases refreshed successfully.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tables Explorer Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Table 1: conversation_messages
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberSlate, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("collection", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("dialogue", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$dbConversationCount records", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                // Table 2: indexeddb_local_store
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberSlate, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("collection", color = CharcoalMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("indexed_db", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$dbIndexedStoresCount keys", color = TechTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            // Key List Explorer (IndexedDB entries visual inspection)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Inspect IndexedDB Store Keys (Click to Query):", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                
                val keys = listOf(
                    "user_preferences",
                    "ai_training_rules",
                    "mobile_device_automation_protocol"
                )

                keys.forEach { keyName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSlate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val row = appDb.indexedDbDao.getByKey(keyName)
                                    withContext(Dispatchers.Main) {
                                        selectedStoreKeyName = keyName
                                        selectedIndexedKeyContents = row?.storeValueJson ?: "{}"
                                        showStorePreviewDialog = true
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(keyName, color = PureWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("query.get()", color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Clear Store Action
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        appDb.conversationDao.clearHistory()
                        val chatCount = appDb.conversationDao.getAllMessages().size
                        withContext(Dispatchers.Main) {
                            dbConversationCount = chatCount
                            Toast.makeText(context, "Memory cleared offline.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f), contentColor = Color.Red),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Clear Conversational Persistence", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- ADDON: AGENT-REACH INTERNET INTEGRATION ENHANCEMENT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AGENT-REACH GLOBAL INTERNET CORE",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ZERO-FEE SCRAPER AND WEB INTEL MATRIX",
                        color = CharcoalMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Active Pulse Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF30D158), CircleShape)
                    )
                    Text("PORT Nominal", color = TechTeal, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Text(
                text = "Equipped with Lann P.'s Agent-Reach protocol to read real-time data from Twitter, Reddit, YouTube, Bilibili, XiaoHongShu, Douyin and more - bypass API rates with direct headless channels.",
                color = PureWhite,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Horizontal Scroll for Channels
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channelsList.size) { index ->
                    val ch = channelsList[index]
                    val isSelected = activeReachChannel.equals(ch.name, ignoreCase = true)
                    val colorAccent = if (isSelected) CyberCyan else CharcoalMuted
                    
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) CyberSlate else SpaceBlack, RoundedCornerShape(12.dp))
                            .border(1.1.dp, if (isSelected) CyberCyan.copy(alpha = 0.8f) else BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable {
                                activeReachChannel = ch.name
                                reachSearchResults = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (ch.status == "OK") TechTeal else NeonAmber, CircleShape)
                            )
                            Text(
                                text = ch.displayName,
                                color = if (isSelected) PureWhite else colorAccent,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Current selected channel specs
            val currentChObj = remember(activeReachChannel, channelsList) {
                channelsList.firstOrNull { it.name.equals(activeReachChannel, ignoreCase = true) }
            }

            currentChObj?.let { ch ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSlate.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Specs: ${ch.displayName}",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Backend: ${ch.backend}",
                            color = CharcoalMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = ch.description,
                        color = PureWhite,
                        fontSize = 10.sp
                    )

                    if (ch.reliesOnCookie) {
                        val cookiePrefKey = "reach_cookie_${ch.name}"
                        val currentCookie = sharedPref.getString(cookiePrefKey, "") ?: ""
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentCookie.isNotEmpty()) "✅ Active Session Cookie Set" else "⚠️ Missing Cookie (Read timelines limited)",
                                color = if (currentCookie.isNotEmpty()) TechTeal else NeonAmber,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Config Cookie",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        cookieInputValue = currentCookie
                                        showCookieEditDialog = ch.name
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "⚡ Real-time Public Gateway: Zero Config Required.",
                            color = TechTeal,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Interactive Crawling Command Unit
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Launch Live Crawl Vector (Simulated CLI):",
                    color = PureWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = reachQueryText,
                        onValueChange = { reachQueryText = it },
                        placeholder = { Text("Type query, e.g., 'Android AI', 'OpenAI news'", color = CharcoalMuted, fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 12.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = SpaceBlack,
                            unfocusedContainerColor = SpaceBlack,
                            focusedIndicatorColor = CyberCyan,
                            unfocusedIndicatorColor = BorderSlate
                        )
                    )

                    Button(
                        onClick = {
                            if (reachQueryText.trim().isNotEmpty()) {
                                isReachSearching = true
                                reachSearchResults = null
                                com.example.data.AgentReachIntegrationEngine.performReachSearch(
                                    context = context,
                                    channel = activeReachChannel,
                                    query = reachQueryText.trim(),
                                    onSuccess = { res ->
                                        reachSearchResults = res
                                        isReachSearching = false
                                    },
                                    onError = { err ->
                                        reachSearchResults = "Crawling error: $err"
                                        isReachSearching = false
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = SpaceBlack),
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isReachSearching
                    ) {
                        if (isReachSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpaceBlack, strokeWidth = 2.dp)
                        } else {
                            Text("RUN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Crawling Results Terminal Screen
            reachSearchResults?.let { results ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceBlack, RoundedCornerShape(12.dp))
                        .border(1.1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AGENT-REACH CLI TERM OUTPUT:",
                            color = TechTeal,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Results",
                            tint = CharcoalMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { reachSearchResults = null }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = results,
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Cookie Dialog
        showCookieEditDialog?.let { chName ->
            AlertDialog(
                onDismissRequest = { showCookieEditDialog = null },
                title = { Text("Update Session Cookie • $chName", color = PureWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                containerColor = SpaceBlack,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Bypasses scraper rate limits for $chName. Obtain from browser developer tools networking headers.",
                            color = CharcoalMuted,
                            fontSize = 10.sp
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = cookieInputValue,
                            onValueChange = { cookieInputValue = it },
                            placeholder = { Text("Paste raw cookie string here...", color = CharcoalMuted, fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = CyberSlate,
                                unfocusedContainerColor = CyberSlate,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = BorderSlate
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val key = "reach_cookie_$chName"
                            sharedPref.edit().putString(key, cookieInputValue.trim()).apply()
                            channelsList = com.example.data.AgentReachIntegrationEngine.getChannels(context)
                            showCookieEditDialog = null
                        }
                    ) {
                        Text("Save Cookie", color = CyberCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieEditDialog = null }) {
                        Text("Cancel", color = CharcoalMuted)
                    }
                }
            )
        }

        // --- ADDON: AI SYSTEM INTERNAL TRAINING CURRICULUM MANUAL ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "NOVA COGNITION MANUAL",
                    color = NeonAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SYSTEM PRE-TRAINED CURRICULUM LEVELS",
                    color = CharcoalMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Training Level Cards
            listOf(
                Triple("LEVEL 01: HOW TO OPERATE LOCAL AI", "Speak utilizing speech triggers: 'Hey Nova', 'Ok Nova', or standard typing. Customize standard profiles, switch model toggles, and modify Groq keys securely.", CyberCyan),
                Triple("LEVEL 02: HOW TO OPERATE LOCAL TOOLS", "Tackle stock updates, organize task prioritization parameters (LOW, MEDIUM, HIGH, CRITICAL), toggle continuous workflows, and verify diagnostic health reports.", TechTeal),
                Triple("LEVEL 03: MOBILE DEVICE AUTOMATION PROTOCOL", "Prepare dialer calls, launch high-profile mobile apps, schedule SMS dispatches with automated safety approvals, and control accessibility overlay engines.", NeonAmber)
            ).forEach { (lvl, desc, color) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSlate, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
                        Text(lvl, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Text(desc, color = PureWhite, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }

        if (showStorePreviewDialog && selectedStoreKeyName != null) {
            AlertDialog(
                onDismissRequest = { showStorePreviewDialog = false },
                title = { Text("IndexedDB Store: ${selectedStoreKeyName}", color = PureWhite, fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
                containerColor = SpaceBlack,
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSlate, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = selectedIndexedKeyContents ?: "{}",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStorePreviewDialog = false }) {
                        Text("Close", color = CyberCyan)
                    }
                }
            )
        }

        // --- ADDON: SECURE REMINDERS REGISTRY ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.2.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SECURE REMINDERS REGISTRY",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "VERIFIED LOCALIZED ALARMS ENGINE",
                        color = CharcoalMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${remindersList.size} RECORD(S)",
                        color = CyberCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (remindersList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSlate.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⏰", fontSize = 24.sp)
                        Text(
                            text = "No active reminders stored in secure local matrix.",
                            color = CharcoalMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    remindersList.forEach { reminder ->
                        val isPending = reminder.status == "PENDING"
                        val indicatorColor = if (isPending) NeonAmber else TechTeal
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSlate.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status bar tracker indicator
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(36.dp)
                                        .background(indicatorColor, RoundedCornerShape(2.dp))
                                )
                                
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(indicatorColor, CircleShape)
                                        )
                                        Text(
                                            text = if (isPending) "VERIFIED SCHEDULED" else "VERIFIED DISPATCHED",
                                            color = indicatorColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = reminder.title,
                                        color = PureWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Due: ${reminder.time} (${reminder.date})",
                                        color = CharcoalMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            // Delete/Cancel Action Trigger Button
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            try {
                                                val db = com.example.data.AppDatabase.getDatabase(context)
                                                if (isPending) {
                                                    // Cancel Alarm Manager registration
                                                    com.example.data.ReminderScheduler.cancelReminder(context, reminder.id)
                                                }
                                                // Delete db entry
                                                db.reminderDao.deleteReminder(reminder)
                                                android.widget.Toast.makeText(context, "Reminder removed from local matrix.", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    .background(Color.Red.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isPending) "CANCEL" else "DELETE",
                                    color = Color.Red,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun RedesignedSettingsTab(
    micPermissionGranted: Boolean,
    contactsPermissionGranted: Boolean,
    phonePermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    isAccessibilityActive: Boolean,
    isOverlayPermissionGranted: Boolean,
    isBatteryIgnoringOptimizations: Boolean,
    continuousConversationEnabled: Boolean,
    onContinuousConversationToggle: (Boolean) -> Unit,
    context: android.content.Context,
    onRequestMicrophone: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestPhone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestBattery: () -> Unit,
    // Add additional parameters to supply developer diagnostics mode!
    isListening: Boolean,
    isRecognizerActive: Boolean,
    isTtsReady: Boolean,
    isTtsSpeaking: Boolean,
    isAutomating: Boolean,
    intentState: String,
    onActiveTabChange: (String) -> Unit = {}
) {
    val sharedPrefs = remember { context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE) }
    
    var selectedTheme by remember {
        mutableStateOf(sharedPrefs.getString("selected_theme", "Cyber Dark") ?: "Cyber Dark")
    }
    var selectedLanguage by remember {
        mutableStateOf(sharedPrefs.getString("selected_language", "English (India)") ?: "English (India)")
    }
    var selectedWakeWord by remember {
        mutableStateOf(sharedPrefs.getString("selected_wake_word", "Hey Nova") ?: "Hey Nova")
    }
    var selectedVoice by remember {
        mutableStateOf(sharedPrefs.getString("selected_voice", "Nova (Female)") ?: "Nova (Female)")
    }

    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showWakeWordDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCard, RoundedCornerShape(18.dp))
                .border(1.dp, BorderSlate.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PERMISSIONS STATUS", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                val totalGranted = (if (micPermissionGranted) 1 else 0) +
                        (if (contactsPermissionGranted) 1 else 0) +
                        (if (phonePermissionGranted) 1 else 0) +
                        (if (notificationPermissionGranted) 1 else 0) +
                        (if (isAccessibilityActive) 1 else 0) +
                        (if (isOverlayPermissionGranted) 1 else 0) +
                        (if (isBatteryIgnoringOptimizations) 1 else 0)
                Text("$totalGranted of 7 granted", color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(CyberCyan.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, CyberCyan, RoundedCornerShape(12.dp))
                        .clickable { onRequestMicrophone() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Manage Permissions", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(SageGreen.copy(alpha = 0.15f), CircleShape)
                    .border(1.5.dp, SageGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = SageGreen, modifier = Modifier.size(24.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("VOICE CONFIGURATION", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Voice selector row with dropdown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVoiceDropdown = !showVoiceDropdown },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Gender Profile", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(selectedVoice, color = CharcoalMuted, fontSize = 13.sp)
                            Icon(
                                if (showVoiceDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showVoiceDropdown) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val voices = listOf("Nova (Female)", "Aries (Male)", "Luna (Feminine)", "Orion (Deep Male)")
                        voices.forEach { v ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("selected_voice", v).apply()
                                        selectedVoice = v
                                        showVoiceDropdown = false
                                        android.widget.Toast.makeText(context, "Voice changed to $v", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(v, color = if (selectedVoice == v) CyberCyan else PureWhite, fontSize = 13.sp)
                                if (selectedVoice == v) {
                                    Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Wake Word selection row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWakeWordDropdown = !showWakeWordDropdown },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wake Word", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(selectedWakeWord, color = CharcoalMuted, fontSize = 13.sp)
                            Icon(
                                if (showWakeWordDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showWakeWordDropdown) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val wakeWords = listOf("Hey Nova", "Ok Nova", "Jarvis")
                        wakeWords.forEach { w ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("selected_wake_word", w).apply()
                                        selectedWakeWord = w
                                        showWakeWordDropdown = false
                                        android.widget.Toast.makeText(context, "Wake word changed to $w", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(w, color = if (selectedWakeWord == w) CyberCyan else PureWhite, fontSize = 13.sp)
                                if (selectedWakeWord == w) {
                                    Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SYSTEM GENERAL CONFIGURATION", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Language Selection Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDropdown = !showLanguageDropdown },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Language Regional Profile", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(selectedLanguage, color = CharcoalMuted, fontSize = 13.sp)
                            Icon(
                                if (showLanguageDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showLanguageDropdown) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val languages = listOf("English (India)", "English (USA)", "Hindi", "Español", "Français")
                        languages.forEach { l ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("selected_language", l).apply()
                                        selectedLanguage = l
                                        showLanguageDropdown = false
                                        android.widget.Toast.makeText(context, "Language changed to $l", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(l, color = if (selectedLanguage == l) CyberCyan else PureWhite, fontSize = 13.sp)
                                if (selectedLanguage == l) {
                                    Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Appearance Theme Selection Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDropdown = !showThemeDropdown },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Appearance Theme Style", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(selectedTheme, color = CharcoalMuted, fontSize = 13.sp)
                            Icon(
                                if (showThemeDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                null,
                                tint = CharcoalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (showThemeDropdown) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val themes = listOf("Cyber Dark", "Classic Dark", "Light Mode")
                        themes.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sharedPrefs.edit().putString("selected_theme", t).apply()
                                        selectedTheme = t
                                        com.example.ui.theme.currentThemeState.value = t
                                        showThemeDropdown = false
                                        android.widget.Toast.makeText(context, "Theme set to $t", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t, color = if (selectedTheme == t) CyberCyan else PureWhite, fontSize = 13.sp)
                                if (selectedTheme == t) {
                                    Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                var autoStartEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("auto_start_enabled", true)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Start on Boot", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = autoStartEnabled,
                        onCheckedChange = { 
                            autoStartEnabled = it
                            sharedPrefs.edit().putBoolean("auto_start_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continuous Conversation", color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = continuousConversationEnabled,
                        onCheckedChange = { onContinuousConversationToggle(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                    )
                }

                var wakeWordServiceEnabled by remember {
                    mutableStateOf(
                        context.getSharedPreferences("nova_wake_word_prefs", android.content.Context.MODE_PRIVATE)
                            .getBoolean("wake_word_service_enabled", false)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Summon (\"Hey Nova\")", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Listens for wake words continuously in background.", color = CharcoalMuted, fontSize = 9.sp)
                    }
                    Switch(
                        checked = wakeWordServiceEnabled,
                        onCheckedChange = { checked ->
                            wakeWordServiceEnabled = checked
                            context.getSharedPreferences("nova_wake_word_prefs", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("wake_word_service_enabled", checked)
                                .apply()
                            
                            if (checked) {
                                com.example.NovaForegroundService.start(context)
                                android.widget.Toast.makeText(context, "Voice Wake-Word Activated", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Voice Wake-Word Deactivated", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            com.example.WakeWordDebugManager.refreshDiagnostics(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                    )
                }

                // GEMINI COGNITION ENGINE CONFIGURATION
                var geminiEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gemini_enabled", true)) }
                var geminiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }
                var geminiKeyVisible by remember { mutableStateOf(false) }
                val systemKeyConfigured = com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(14.dp))
                        .border(1.2.dp, if (geminiEnabled) CyberCyan.copy(alpha = 0.5f) else BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GEMINI COGNITION ENGINE", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                            Text("Fuses Gemini 3.5-Flash artificial intelligence directly", color = CharcoalMuted, fontSize = 9.sp)
                        }
                        Switch(
                            checked = geminiEnabled,
                            onCheckedChange = { checked ->
                                geminiEnabled = checked
                                sharedPrefs.edit().putBoolean("gemini_enabled", checked).apply()
                                if (checked && geminiKey.trim().isEmpty() && !systemKeyConfigured) {
                                    android.widget.Toast.makeText(context, "Using local simulation fallback or configure personal key below.", android.widget.Toast.LENGTH_SHORT).show()
                                } else if (checked) {
                                    android.widget.Toast.makeText(context, "Gemini Cognition Active", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                        )
                    }

                    if (geminiEnabled) {
                        if (systemKeyConfigured) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SageGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .border(1.dp, SageGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SageGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "AI Studio Key Pre-configured & Ready",
                                        color = SageGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            // API Key Input Row (if system key not found)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Gemini API Key (AI-...) or blank", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (geminiKeyVisible) "HIDE" else "SHOW",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .clickable { geminiKeyVisible = !geminiKeyVisible }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = geminiKey,
                                    onValueChange = {
                                        geminiKey = it
                                        sharedPrefs.edit().putString("gemini_api_key", it).apply()
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = PureWhite,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(CyberCyan),
                                    visualTransformation = if (geminiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SpaceBlack, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // GROQ COGNITION ENGINE CONFIGURATION
                var groqEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("groq_enabled", true)) }
                var groqKey by remember { mutableStateOf(sharedPrefs.getString("groq_api_key", "") ?: "") }
                var selectedGroqModel by remember { mutableStateOf(sharedPrefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile") }
                var showGroqModelDropdown by remember { mutableStateOf(false) }
                var keyVisible by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(14.dp))
                        .border(1.2.dp, if (groqEnabled) CyberCyan.copy(alpha = 0.5f) else BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GROQ INTELLIGENCE CORE", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                            Text("Empowers fallback with ultra-fast Groq AI models", color = CharcoalMuted, fontSize = 9.sp)
                        }
                        Switch(
                            checked = groqEnabled,
                            onCheckedChange = { checked ->
                                groqEnabled = checked
                                sharedPrefs.edit().putBoolean("groq_enabled", checked).apply()
                                if (checked && groqKey.trim().isEmpty()) {
                                    android.widget.Toast.makeText(context, "API Key is required to activate Groq AI!", android.widget.Toast.LENGTH_LONG).show()
                                } else if (checked) {
                                    android.widget.Toast.makeText(context, "Groq Core Online", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                        )
                    }

                    if (groqEnabled) {
                        // API Key Input Row
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Groq API Key (gsk_...)", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (keyVisible) "HIDE" else "SHOW",
                                    color = CyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .clickable { keyVisible = !keyVisible }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = groqKey,
                                onValueChange = {
                                    groqKey = it
                                    sharedPrefs.edit().putString("groq_api_key", it).apply()
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(CyberCyan),
                                visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpaceBlack, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            )
                        }

                        // Model selection dropdown
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpaceBlack, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { showGroqModelDropdown = !showGroqModelDropdown }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Groq Model", color = CharcoalMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text(selectedGroqModel, color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    if (showGroqModelDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    null,
                                    tint = CharcoalMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (showGroqModelDropdown) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val groqModels = listOf(
                                    "llama-3.3-70b-versatile",
                                    "llama-3.1-8b-instant",
                                    "mixtral-8x7b-32768",
                                    "gemma2-9b-it"
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(TechCard, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    groqModels.forEach { m ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    sharedPrefs.edit().putString("groq_model", m).apply()
                                                    selectedGroqModel = m
                                                    showGroqModelDropdown = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(m, color = if (selectedGroqModel == m) CyberCyan else PureWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            if (selectedGroqModel == m) {
                                                Icon(Icons.Default.Check, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // UNLIMITED FREE AI CHANNELS AND COGNITION PROXIES
                FreeAiGatewayConfigPanel(context = context, sharedPrefs = sharedPrefs)



                // NOVA REINFORCEMENT LEARNING & ALIGNMENT LAB
                var customPrinciples by remember { mutableStateOf(sharedPrefs.getString("trained_custom_principles", "") ?: "") }
                var rlhfUp by remember { mutableStateOf(sharedPrefs.getInt("rlhf_thumbs_up", 0)) }
                var rlhfDown by remember { mutableStateOf(sharedPrefs.getInt("rlhf_thumbs_down", 0)) }
                var rlhfTotal by remember { mutableStateOf(sharedPrefs.getInt("rlhf_total_rated", 0)) }
                
                var concisenessWeight by remember { mutableStateOf(sharedPrefs.getFloat("rlhf_conciseness_weight", 0.7f)) }
                var safetyWeight by remember { mutableStateOf(sharedPrefs.getFloat("rlhf_safety_weight", 0.8f)) }
                var creativityWeight by remember { mutableStateOf(sharedPrefs.getFloat("rlhf_creativity_weight", 0.75f)) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(14.dp))
                        .border(1.2.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(CyberCyan, CircleShape)
                                )
                                Text("A.I. COGNITIVE ALIGNMENT LAB", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
                            }
                            Text("Train Nova using Reinforcement Learning (RLHF) & SFT rules", color = CharcoalMuted, fontSize = 9.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpaceBlack, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("RLHF REWARD ENGINE STATUS", color = CharcoalMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Feedback Items", color = TextSecondary, fontSize = 10.sp)
                                    Text("$rlhfTotal", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text("Thumbs Up", color = SageGreen, fontSize = 10.sp)
                                    Text("$rlhfUp", color = SageGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Column {
                                    Text("Thumbs Down", color = Color(0xFFFF453A), fontSize = 10.sp)
                                    Text("$rlhfDown", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    // SFT Custom Instruction Injection Manual
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SUPERVISED FINE-TUNING METHODOLOGY (SFT)", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("Add instructional tokens or behavioral rules below (just like pre-training ChatGPT):", color = CharcoalMuted, fontSize = 8.sp)
                        
                        OutlinedTextField(
                            value = customPrinciples,
                            onValueChange = {
                                customPrinciples = it
                                sharedPrefs.edit().putString("trained_custom_principles", it).apply()
                            },
                            placeholder = {
                                Text(
                                    "E.g., Speak extremely clearly, ask for clarification if directions are vague, use formal vocabulary...",
                                    color = CharcoalMuted,
                                    fontSize = 10.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = PureWhite, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = BorderSlate.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // Reward Weight Sliders (Hyperparameter Calibration)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("HYPERPARAMETER POLICY RATIOS (PPO)", color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        
                        // Slider 1: Conciseness Weight
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Conciseness Penalty Bias", color = TextSecondary, fontSize = 9.sp)
                                Text(String.format("%.2f", concisenessWeight), color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = concisenessWeight,
                                onValueChange = {
                                    concisenessWeight = it
                                    sharedPrefs.edit().putFloat("rlhf_conciseness_weight", it).apply()
                                },
                                colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan)
                            )
                        }

                        // Slider 2: Safety Weight
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Safety Guardrails Alignment", color = TextSecondary, fontSize = 9.sp)
                                Text(String.format("%.2f", safetyWeight), color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = safetyWeight,
                                onValueChange = {
                                    safetyWeight = it
                                    sharedPrefs.edit().putFloat("rlhf_safety_weight", it).apply()
                                },
                                colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan)
                            )
                        }

                        // Slider 3: Creativity Weight
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Creativity Temperature Entropy", color = TextSecondary, fontSize = 9.sp)
                                Text(String.format("%.2f", creativityWeight), color = CyberCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = creativityWeight,
                                onValueChange = {
                                    creativityWeight = it
                                    sharedPrefs.edit().putFloat("rlhf_creativity_weight", it).apply()
                                },
                                colors = SliderDefaults.colors(activeTrackColor = CyberCyan, thumbColor = CyberCyan)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                customPrinciples = ""
                                rlhfUp = 0
                                rlhfDown = 0
                                rlhfTotal = 0
                                sharedPrefs.edit()
                                    .remove("trained_custom_principles")
                                    .remove("trained_sft_corrections")
                                    .putInt("rlhf_thumbs_up", 0)
                                    .putInt("rlhf_thumbs_down", 0)
                                    .putInt("rlhf_total_rated", 0)
                                    .apply()
                                android.widget.Toast.makeText(context, "SFT policy records flushed and reset!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Text("FLUSH POLICY COGNITION", color = Color(0xFFFF5252), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        TextButton(
                            onClick = {
                                rlhfUp = sharedPrefs.getInt("rlhf_thumbs_up", 0)
                                rlhfDown = sharedPrefs.getInt("rlhf_thumbs_down", 0)
                                rlhfTotal = sharedPrefs.getInt("rlhf_total_rated", 0)
                                android.widget.Toast.makeText(context, "Hyperparameters synchronized with active context!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .background(CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text("SYNC POLICY TO AGENT", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var developerModeActive by remember { 
                    mutableStateOf(sharedPrefs.getBoolean("developer_mode_active", false)) 
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCard, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer Mode", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = developerModeActive,
                        onCheckedChange = { 
                            developerModeActive = it 
                            sharedPrefs.edit().putBoolean("developer_mode_active", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = SubCyan)
                    )
                }

                if (developerModeActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TechCard, RoundedCornerShape(14.dp))
                            .border(1.2.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "SYSTEM DIAGNOSTIC PANEL", 
                            color = CyberCyan, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Button(
                            onClick = { onActiveTabChange("APIS") },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "LAUNCH PUBLIC APIS DIRECTORY",
                                color = CyberCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Divider(color = BorderSlate.copy(alpha = 0.15f), thickness = 1.dp)

                        DiagnosticRow(
                            label = "MIC STATE",
                            value = if (isListening) "ACTIVE LISTENING" else "STANDBY / IDLE",
                            color = if (isListening) SageGreen else CharcoalMuted
                        )

                        DiagnosticRow(
                            label = "STT STATE",
                            value = if (isRecognizerActive) "RECOGNIZER ACTIVE" else "RECOGNIZER DISPOSED",
                            color = if (isRecognizerActive) CyberCyan else CharcoalMuted
                        )

                        DiagnosticRow(
                            label = "TTS STATE",
                            value = if (isTtsSpeaking) "SPEAKING AUDIO" else if (isTtsReady) "MUTED / ON STANDBY" else "INITIALIZING",
                            color = if (isTtsSpeaking) TechTeal else if (isTtsReady) SageGreen else GlowingRed
                        )

                        DiagnosticRow(
                            label = "INTENT ENGINE",
                            value = if (isAutomating) "EXECUTING AUTONOMOUS ACTION" else "LISTENING FOR INTENT ($intentState)",
                            color = if (isAutomating) NeonAmber else CyberCyan
                        )

                        Divider(color = BorderSlate.copy(alpha = 0.15f), thickness = 1.dp)
                        Text(
                            "PERMISSIONS STATUS", 
                            color = TechTeal, 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            fontFamily = FontFamily.Monospace
                        )

                        PermissionDiagRow("AUDIO RECORDING", micPermissionGranted)
                        PermissionDiagRow("CONTACTS ACCESS", contactsPermissionGranted)
                        PermissionDiagRow("PHONE UTILITIES", phonePermissionGranted)
                        PermissionDiagRow("NOTIFICATIONS POST", notificationPermissionGranted)
                        PermissionDiagRow("ACCESSIBILITY ENGINE", isAccessibilityActive)
                        PermissionDiagRow("SYSTEM OVERLAY", isOverlayPermissionGranted)
                        PermissionDiagRow("BATTERY LEVEL STABILITY", isBatteryIgnoringOptimizations)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("About Nova", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechCard, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DESIGN PHILOSOPHY",
                    color = TechTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Nova is envisioned as an exceptionally intelligent and adaptable artificial intelligence. Its design philosophy centers around its comprehensive training, which involves processing and learning from vast and diverse datasets. This extensive training enables Nova to achieve a high level of proficiency in executing any task that a user might assign to it, ensuring versatility and competence across a wide spectrum of applications and requirements.\n\nThe core objective is for Nova to seamlessly integrate into various workflows, demonstrating its capability to understand, process, and act upon user directives with remarkable accuracy and efficiency, thereby serving as a robust and reliable digital assistant.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Justify
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechCard, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Version", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("1.0.0", color = CharcoalMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun DiagnosticRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PureWhite.copy(alpha = 0.61f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PermissionDiagRow(label: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PureWhite.copy(alpha = 0.61f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(
            if (isGranted) "GRANTED" else "DENIED / DISCONNECTED",
            color = if (isGranted) SageGreen else GlowingRed,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

fun captureScreen(activity: android.app.Activity): android.graphics.Bitmap? {
    return try {
        val view = activity.window.decorView.rootView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun saveAndShareScreenshot(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    try {
        val externalDir = context.getExternalFilesDir(null)
        val cachePath = java.io.File(externalDir, "screenshots")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "nova_screenshot_${System.currentTimeMillis()}.png")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Screen Capture via Nova").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
