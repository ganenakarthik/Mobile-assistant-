package com.example

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AssistActivity : ComponentActivity() {
    private val viewModel: NovaViewModel by viewModels()

    companion object {
        @Volatile
        var isCurrentOverlayActive: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Let the activity display over secure lock guards and wake screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AssistOverlayScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isCurrentOverlayActive = true
    }

    override fun onStop() {
        isCurrentOverlayActive = false
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistOverlayScreen(
    viewModel: NovaViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tasks by viewModel.tasksStream.collectAsStateWithLifecycle()
    val inventory by viewModel.inventoryStream.collectAsStateWithLifecycle()

    var statusMessage by remember { mutableStateOf("Ready to receive vocal command...") }
    var inputQueryText by remember { mutableStateOf("") }
    
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeakingActive by remember { mutableStateOf(false) }

    // Floating particles state for alive look (no static screens)
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

    // Tick background particles physics
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
            delay(32)
        }
    }

    // TTS Synthesis
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Setup TTS Engine with beautiful voice pitches
    LaunchedEffect(Unit) {
        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.setLanguage(Locale.US)
                ttsEngine?.setPitch(1.02f)
                ttsEngine?.setSpeechRate(0.98f)
                isTtsReady = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
            speechRecognizer?.destroy()
        }
    }

    // Speak helper that handles UI state smoothly
    val speakTts = remember(ttsEngine, isTtsReady) {
        { text: String ->
            if (isTtsReady && ttsEngine != null) {
                coroutineScope.launch {
                    isSpeakingActive = true
                    ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NOVA_ASSIST_AUDIO")
                    // Estimate duration
                    val delayVal = (text.length * 75).coerceIn(1500, 5000).toLong()
                    delay(delayVal)
                    isSpeakingActive = false
                }
            }
        }
    }

    // Initializer for the speech recognition service
    val initSpeechRecognizer = {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        statusMessage = "Listening..."
                    }

                    override fun onBeginningOfSpeech() {
                        statusMessage = "Recording vocal signals..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        isThinking = true
                        statusMessage = "Interpreting on-device algorithms..."
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        isThinking = false
                        val errorDesc = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio record error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No command detected"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence threshold met"
                            else -> "Hardware busy"
                        }
                        statusMessage = "Vocal Bypass: $errorDesc. Manual entry active."
                    }

                    override fun onResults(results: Bundle?) {
                        isThinking = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val query = matches?.firstOrNull() ?: ""
                        if (query.isNotBlank()) {
                            statusMessage = "Command captured: \"$query\""
                            // Process command manually
                            processVocalDirective(
                                cmd = query,
                                context = context,
                                viewModel = viewModel,
                                tasksList = tasks,
                                itemsList = inventory,
                                speakTts = { text ->
                                    statusMessage = text
                                    speakTts(text)
                                },
                                uName = "Commander",
                                onLogUpdate = { /* No persistent logs needed for instant Overlay popup */ },
                                onActionAppend = { /* Action appended successfully */ }
                            )
                        } else {
                            statusMessage = "Ready. Tap sphere to speak."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer = recognizer
            } catch (e: Exception) {
                statusMessage = "Mic systems locked. Keyboard console online."
            }
        }
    }

    val micPermissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted.value = granted
        if (granted) {
            initSpeechRecognizer()
            statusMessage = "Mic unlocked. Tap Orb."
        } else {
            statusMessage = "Permission denied. Keyboard available."
        }
    }

    val startVoiceRecog = {
        if (!micPermissionGranted.value) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            if (speechRecognizer == null) {
                initSpeechRecognizer()
            }
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                isListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                statusMessage = "Voice sensors failure."
            }
        }
    }

    // Auto-trigger voice listening on launch, exactly like Gemini!
    LaunchedEffect(isTtsReady) {
        if (isTtsReady) {
            speakTts("Yes?")
            delay(1000) // Brief aesthetic pause for "Yes?" voice output to finish
            startVoiceRecog()
        }
    }

    // Background scrim and the floating panel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = !isListening && !isThinking && !isSpeakingActive) { onFinish() }, // Dismiss on touching the background area only when idle
        contentAlignment = Alignment.BottomCenter
    ) {
        // Aesthetic Glass panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding() // Adjust padding when keyboard opens
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TechCard, SpaceBlack)
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(BorderSlate.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(enabled = false) {} // Disable clicks passing through
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Slide indicator pill
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CharcoalMuted.copy(alpha = 0.4f))
            )

            // Header info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isListening) GlowingRed else CyberCyan)
                    )
                    Text(
                        text = "NOVA COGNATIVE BRIDGE",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                IconButton(
                    onClick = onFinish,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss assist card",
                        tint = CharcoalMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // High Fidelity energy orb
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clickable { startVoiceRecog() },
                contentAlignment = Alignment.Center
            ) {
                NovaCinematicEnergyOrb(
                    isListening = isListening,
                    isThinking = isThinking,
                    isSpeaking = isSpeakingActive,
                    floatParticles = particles
                )
            }

            // Real-time status display
            Text(
                text = statusMessage.uppercase(),
                color = if (isListening) GlowingRed else if (isSpeakingActive) CyberCyan else PureWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            // Suggestion shortcuts
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val tags = listOf(
                    "Launch Maps" to "open maps",
                    "YouTube Stream" to "open youtube",
                    "Add task Milk" to "add task buy milk",
                    "System Health" to "system health"
                )
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(CyberSlate, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSlate.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable {
                                processVocalDirective(
                                    cmd = tag.second,
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasks,
                                    itemsList = inventory,
                                    speakTts = { text ->
                                        statusMessage = text
                                        speakTts(text)
                                    },
                                    uName = "Commander",
                                    onLogUpdate = {},
                                    onActionAppend = {}
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tag.first,
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Keyboard/Type console layout input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSlate, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSlate.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputQueryText,
                    onValueChange = { inputQueryText = it },
                    placeholder = {
                        Text(
                            "Type command instruction...",
                            fontSize = 11.sp,
                            color = CharcoalMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    ),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputQueryText.isNotBlank()) {
                                val query = inputQueryText
                                inputQueryText = ""
                                processVocalDirective(
                                    cmd = query,
                                    context = context,
                                    viewModel = viewModel,
                                    tasksList = tasks,
                                    itemsList = inventory,
                                    speakTts = { text ->
                                        statusMessage = text
                                        speakTts(text)
                                    },
                                    uName = "Commander",
                                    onLogUpdate = {},
                                    onActionAppend = {}
                                )
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (inputQueryText.isNotBlank()) {
                            val query = inputQueryText
                            inputQueryText = ""
                            processVocalDirective(
                                cmd = query,
                                context = context,
                                viewModel = viewModel,
                                tasksList = tasks,
                                itemsList = inventory,
                                speakTts = { text ->
                                    statusMessage = text
                                    speakTts(text)
                                },
                                uName = "Commander",
                                onLogUpdate = {},
                                onActionAppend = {}
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Transmit keyboard query",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
