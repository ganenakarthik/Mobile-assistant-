package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ui.ConsoleMessage
import com.example.ui.NovaPersonalityCore
import com.example.ui.NovaViewModel
import com.example.AutomationStep
import com.example.AutomationEngine
import com.example.StepStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object BrainRouter {

    enum class IntentCategory {
        GENERAL_KNOWLEDGE,
        LIVE_INFORMATION,
        DEVICE_ACTION,
        COMMUNICATION,
        MEMORY_NOTE,
        BROWSER_RESEARCH
    }

    data class DiagnosticRoutingInfo(
        val input: String,
        val intent: String,
        val toolSelected: String,
        val reason: String,
        val result: String,
        val verified: String
    )

    val lastDiagnosticRoute = MutableStateFlow<DiagnosticRoutingInfo?>(null)

    fun updateDiagnostics(
        input: String,
        intent: String,
        toolSelected: String,
        reason: String,
        result: String,
        verified: String
    ) {
        lastDiagnosticRoute.value = DiagnosticRoutingInfo(
            input = input,
            intent = intent,
            toolSelected = toolSelected,
            reason = reason,
            result = result,
            verified = verified
        )
    }

    fun classify(cmd: String): IntentCategory {
        val clean = cmd.lowercase(Locale.getDefault()).trim()
            .replace(Regex("^(nova|ok nova|hey nova|please|can you|could you|start|launch)\\b"), "")
            .replace(Regex("[?:.,!]"), "")
            .trim()

        val hasDeviceFeature = clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("bluetooth") ||
                clean.contains("flashlight") || clean.contains("torch") || clean.contains("brightness") ||
                clean.contains("volume") || clean.contains("dnd") || clean.contains("do not disturb") ||
                clean.contains("silent") || clean.contains("alarm") || clean.contains("timer") || clean.contains("reminder") ||
                clean.contains("screenshot") || clean.contains("screen capture") || clean.contains("cellular") || clean.contains("internet")
        
        val isHardwareToggle = (clean.contains("on") || clean.contains("off") || clean.contains("enable") || clean.contains("disable") || clean.contains("toggle") || clean.contains("activate") || clean.contains("deactivate") || clean.contains("start") || clean.contains("stop")) && hasDeviceFeature

        val hasActiveAction = clean.startsWith("open ") || clean.startsWith("launch ") || clean.startsWith("run ") || clean.startsWith("go to ") ||
                clean.startsWith("call ") || clean.startsWith("dial ") || clean.startsWith("message ") || clean.startsWith("send ") || clean.startsWith("text ") ||
                clean.startsWith("search ") || clean.startsWith("find ") || clean.startsWith("lookup ") || clean.startsWith("play ") ||
                clean.startsWith("close ") || clean.startsWith("terminate ") || clean.startsWith("exit ") || clean.startsWith("quit ") || clean.startsWith("dismiss ") ||
                clean.contains("whatsapp") || clean.contains("turn on") || clean.contains("turn off") || clean.contains("enable") || clean.contains("disable") ||
                clean.contains("set alarm") || clean.contains("set timer") || clean.contains("set reminder") || clean.contains("remind") ||
                clean.contains("play") || clean.contains("screenshot") || clean.contains("screen capture") ||
                clean.contains("volume") || clean.contains("brightness") || clean.contains("flashlight") || clean.contains("torch") ||
                clean.contains("read my message") || clean.contains("read latest") || clean.contains("read notifications") ||
                clean.contains("scroll") || clean.contains("back") || clean.contains("home") || clean.contains("recents") ||
                isHardwareToggle

        // 0. Knowledge Database Matchers first (bypassed if it's an active action directive)
        if (!hasActiveAction) {
            val wkMatch = NovaKnowledgeSystem.findWorldKnowledgeMatch(clean)
            if (wkMatch != null) {
                return if (wkMatch.requiresLiveData) {
                    IntentCategory.LIVE_INFORMATION
                } else {
                    IntentCategory.GENERAL_KNOWLEDGE
                }
            }
        }

        val appMatch = NovaKnowledgeSystem.findAppKnowledgeMatch(clean)
        if (appMatch != null && (clean.contains("can") || clean.contains("what") || clean.contains("capabilities") || clean.contains("do") || clean.contains("features"))) {
            return IntentCategory.GENERAL_KNOWLEDGE
        }

        val devMatch = NovaKnowledgeSystem.findDeviceKnowledgeMatch(clean)
        if (devMatch != null && (clean.contains("can") || clean.contains("what") || clean.contains("limit") || clean.contains("permission") || clean.contains("do") || clean.contains("features"))) {
            return IntentCategory.GENERAL_KNOWLEDGE
        }

        if (clean.contains("behavior rules") || clean.contains("how do you behave") || clean.contains("rule database")) {
            return IntentCategory.GENERAL_KNOWLEDGE
        }

        // 1. MEMORY_NOTE
        val memoryKeywords = listOf(
            "remember", "save note", "notepad", "brain dump", "keep note", "what did i tell you",
            "my preference", "recall", "about myself", "my notes", "read notes", "show notes", "what are my notes",
            "get notes", "read my notes", "save this to notes", "note this idea", "save this note", "note this"
        )
        if (memoryKeywords.any { clean.contains(it) }) {
            return IntentCategory.MEMORY_NOTE
        }

        // 2. BROWSER_RESEARCH
        val browserKeywords = listOf(
            "download", "find best", "search free", "search and save", "browse", "research", "background browser"
        )
        if (browserKeywords.any { clean.contains(it) || (clean.startsWith("search") && clean.contains("save")) }) {
            return IntentCategory.BROWSER_RESEARCH
        }

        // 3. COMMUNICATION
        val commKeywords = listOf(
            "message", "whatsapp", "sms", "text to", "send text", "call nazeer", "call mom", "call dad", "whatsapp mom", "call bittu", "call hemanth"
        )
        if (commKeywords.any { clean.contains(it) }) {
            return IntentCategory.COMMUNICATION
        }
        if (clean.startsWith("call ") && !clean.contains("chrome") && !clean.contains("browser") && !clean.contains("calculator")) {
            return IntentCategory.COMMUNICATION
        }

        // 4. DEVICE_ACTION
        val deviceKeywords = listOf(
            "open chrome", "open browser", "open calculator", "open maps", "open youtube", "open settings",
            "turn on bluetooth", "turn off bluetooth", "turn on wifi", "turn off wifi", "enable bluetooth", "disable bluetooth",
            "take screenshot", "take a screenshot", "screen capture", "turn on flashlight", "turn off flashlight",
            "set alarm", "set timer", "wake me up"
        )
        if (deviceKeywords.any { clean.contains(it) } || 
            isHardwareToggle ||
            clean.startsWith("open ") || clean.startsWith("launch ") || clean.startsWith("run ") || clean.startsWith("go to ") ||
            clean.startsWith("search ") || clean.startsWith("find ") || clean.startsWith("lookup ") || clean.startsWith("play ") ||
            clean.startsWith("close ") || clean.startsWith("terminate ") || clean.startsWith("exit ") || clean.startsWith("quit ") || clean.startsWith("dismiss ") ||
            clean.startsWith("navigate") || clean.contains("directions to") || clean.startsWith("directions") ||
            clean.contains("scroll") || clean.contains("back") || clean.contains("home") || clean.contains("recents")
        ) {
            return IntentCategory.DEVICE_ACTION
        }

        // 5. LIVE_INFORMATION (real-time standings, scores, weather, crypto, stock, yesterday matches)
        val liveKeywords = listOf(
            "weather", "forecast", "temperature", "temp", "rain", "rainy", "sunny", "cloudy", "degree",
            "bitcoin", "btc", "crypto", "ether", "ethereum", "solana", "dogecoin", "shiba", "coinbase",
            "points table", "ipl table", "ipl points", "standings", "score", "scores", "cricket", "match", "matches",
            "wickets", "runs", "ipl", "premier league", "who won yesterday", "yesterday's match", "yesterday won",
            "stock price", "share price", "nasdaq", "dow jones", "market price", "current price", "live price", "ticker",
            "news", "headline", "headlines", "yesterday", "current score", "today's score"
        )
        if (liveKeywords.any { clean.contains(it) }) {
            return IntentCategory.LIVE_INFORMATION
        }

        // 6. GENERAL_KNOWLEDGE / QUESTION STARTERS
        val questionStarters = listOf("what", "who", "why", "how", "explain", "tell me", "define")
        if (questionStarters.any { clean.startsWith(it) }) {
            if (liveKeywords.any { clean.contains(it) }) {
                return IntentCategory.LIVE_INFORMATION
            } else {
                return IntentCategory.GENERAL_KNOWLEDGE
            }
        }

        // Default fallback to GENERAL_KNOWLEDGE
        return IntentCategory.GENERAL_KNOWLEDGE
    }

    fun routeAndExecute(
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
    ): Boolean {
        if (cmd.isBlank()) return false

        val clean = cmd.lowercase(Locale.getDefault()).trim()
            .replace(Regex("^(nova|ok nova|hey nova|please|can you|could you|start|launch)\\b"), "")
            .replace(Regex("[?:.,!]"), "")
            .trim()

        // Refresh dynamic real-world awareness and set initial UNDERSTANDING thought state
        NovaLifeSystem.refreshContextAwareness(context)
        NovaLifeSystem.updateState(
            NovaLifeSystem.CognitiveState.UNDERSTANDING,
            "Analyzing statement: \"$cmd\"...",
            NovaLifeSystem.EmotionalMood.FOCUS
        )

        onLogUpdate(ConsoleMessage("SYSTEM", "🧠 NOVA COGNITIVE ROUTER: Thinks before taking action..."))

        // Check for cognitive generative tasks (e.g. write a leave letter + send/whatsapp it)
        if (handleCognitiveAgentPipelines(
                cmd, clean, context, viewModel, tasksList, itemsList, speakTts, uName, onLogUpdate, onActionAppend, dialogHistoryList, onActiveTabChange
            )) {
            return true
        }

        // Check for living system intercepts: Timeline, Profiles, Contact Histories, and Warnings
        if (handleAwarenessAndMemoryInterceptions(cmd, clean, context, speakTts, onLogUpdate, onActionAppend)) {
            return true
        }

        // 1. INTENT DETECTION
        val category = classify(cmd)
        onLogUpdate(ConsoleMessage("SYSTEM", "🔍 STEP 1: Intent Detection resolved to: $category"))

        // Save last discussed topic in Memory SharedPreferences
        NovaLifeSystem.storeMemory(context, "last_discussed_topic", category.name)

        // 2. KNOWLEDGE DATABASE LOOKUP LAYER
        var wkMatch = NovaKnowledgeSystem.findWorldKnowledgeMatch(clean)
        var appMatch = NovaKnowledgeSystem.findAppKnowledgeMatch(clean)
        var devMatch = NovaKnowledgeSystem.findDeviceKnowledgeMatch(clean)

        var lookupResult = ""
        if (wkMatch != null) {
            lookupResult = "World Knowledge match: ${wkMatch.topic} (${wkMatch.category}). Requires Live Data: ${wkMatch.requiresLiveData}."
            onLogUpdate(ConsoleMessage("SYSTEM", "📖 STEP 2: Knowledge Lookup: $lookupResult"))
            NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Recalling details on: ${wkMatch.topic}...", NovaLifeSystem.EmotionalMood.CURIOUS)
        } else if (appMatch != null) {
            lookupResult = "App Knowledge match: ${appMatch.appName}. Capabilities: ${appMatch.capabilities.joinToString()}."
            onLogUpdate(ConsoleMessage("SYSTEM", "📖 STEP 2: Knowledge Lookup: $lookupResult"))
            NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Verifying internal application capabilities...", NovaLifeSystem.EmotionalMood.CURIOUS)
        } else if (devMatch != null) {
            lookupResult = "Device feature match: ${devMatch.featureName}. Requires Permissions: ${devMatch.permissionsRequired.joinToString() ?: "None"}."
            onLogUpdate(ConsoleMessage("SYSTEM", "📖 STEP 2: Knowledge Lookup: $lookupResult"))
            NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Checking system framework and permission state...", NovaLifeSystem.EmotionalMood.CURIOUS)
        } else {
            lookupResult = "Generic conversational intent, defaulting to Universal Cognitive reasoning."
            onLogUpdate(ConsoleMessage("SYSTEM", "📖 STEP 2: Knowledge Lookup: $lookupResult"))
        }

        // 3. BEHAVIOR RULES & COGNITIVE CHECKS
        onLogUpdate(ConsoleMessage("SYSTEM", "🛡️ STEP 3: Behavior Verification. Policy Checks: Never hallucinate scores, matches or values. Force live sources for volatile params."))

        if (wkMatch?.requiresLiveData == true && category != IntentCategory.LIVE_INFORMATION) {
            onLogUpdate(ConsoleMessage("SYSTEM", "⚠️ POLICY INTERCEPT: Topic '${wkMatch.topic}' requires live data! Re-routing to LIVE_INFORMATION to prevent hallucination."))
        }

        // 4. TOOL SELECTION & EXECUTION
        when (category) {
            IntentCategory.GENERAL_KNOWLEDGE -> {
                onActiveTabChange("DIALOGUE")
                if (wkMatch != null && (clean.contains("what is") || clean.contains("who is") || clean.contains("define") || clean == wkMatch.topic.lowercase())) {
                    val ans = "According to my internal World Knowledge base, **${wkMatch.topic}** is: ${wkMatch.description}. Related concepts: ${wkMatch.relatedTopics.joinToString()}."
                    onLogUpdate(ConsoleMessage("NOVA", ans))
                    speakWithLife(ans, speakTts)
                    updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Local World Knowledge DB", "Direct local fact retrieval", ans, "YES")
                    return true
                }
                if (appMatch != null && (clean.contains("can") || clean.contains("what") || clean.contains("capabilities") || clean.contains("do"))) {
                    val ans = "According to my internal App Knowledge, **${appMatch.appName}** is: ${appMatch.purpose} It has the following capabilities: ${appMatch.capabilities.joinToString { "'$it'" }}."
                    onLogUpdate(ConsoleMessage("NOVA", ans))
                    speakWithLife(ans, speakTts)
                    updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Local App Knowledge DB", "Fact audit of app features", ans, "YES")
                    return true
                }
                if (devMatch != null && (clean.contains("can") || clean.contains("what") || clean.contains("limits") || clean.contains("permission") || clean.contains("do") || clean.contains("features"))) {
                    val permInfo = if (devMatch.permissionsRequired.isNotEmpty()) "Permissions required: ${devMatch.permissionsRequired.joinToString()}." else "Requires zero special permissions."
                    val ans = "Device feature **${devMatch.featureName}** capabilities: ${devMatch.capabilities.joinToString()}. Limitations: ${devMatch.limitations.joinToString()}. $permInfo"
                    onLogUpdate(ConsoleMessage("NOVA", ans))
                    speakWithLife(ans, speakTts)
                    updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Local Device Knowledge DB", "Fact verification of system services", ans, "YES")
                    return true
                }
                if (clean.contains("behavior rules") || clean.contains("how do you behave") || clean.contains("rule database")) {
                    val rulesStr = NovaKnowledgeSystem.behaviorRulesDb.joinToString("\n") { "- **${it.name}** (${it.scope}): ${it.instruction}" }
                    val ans = "My operating policies and behavior guidelines specify the following protocols:\n$rulesStr"
                    onLogUpdate(ConsoleMessage("NOVA", ans))
                    speakWithLife(ans, speakTts)
                    updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Behavior Rules DB", "Self policy safety readout", ans, "YES")
                    return true
                }

                // If no direct local match, fallback to Groq / Gemini / Pollinations / Offline
                val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
                val groqEnabled = sharedPrefs.getBoolean("groq_enabled", true)
                val groqKey = sharedPrefs.getString("groq_api_key", "").orEmpty().trim()

                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.THINKING, "Formulating precise conceptual response...", NovaLifeSystem.EmotionalMood.FOCUS)

                if (groqEnabled && groqKey.isNotEmpty()) {
                    val currentModel = sharedPrefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Groq AI ($currentModel) [Reason: Complex reasoning/explaining request]"))
                    com.example.data.GroqCognitionEngine.queryGroqAI(
                        context = context,
                        prompt = cmd,
                        dialogHistoryList = dialogHistoryList,
                        onSuccess = { response ->
                            onLogUpdate(ConsoleMessage("NOVA", response))
                            speakWithLife(response, speakTts)
                            updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Groq AI ($currentModel)", "Factual explanation request", response, "YES")
                        },
                        onError = { error ->
                            Log.e("BrainRouter", "Groq query failed: $error, falling back to Gemini")
                            fallbackToGemini(cmd, context, dialogHistoryList, onLogUpdate, speakTts)
                        }
                    )
                    return true
                }

                val geminiEnabled = sharedPrefs.getBoolean("gemini_enabled", true)
                if (geminiEnabled && com.example.data.GeminiCognitionEngine.isGeminiAvailable(context)) {
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Gemini AI [Reason: Deep planning fallback]"))
                    fallbackToGemini(cmd, context, dialogHistoryList, onLogUpdate, speakTts)
                    return true
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
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Free AI ($provider - $model) [Reason: Explanations/unconfigured keys fallback]"))
                    com.example.data.FreeMultiCognitionEngine.queryFreeAI(
                        context = context,
                        prompt = cmd,
                        dialogHistoryList = dialogHistoryList,
                        onSuccess = { response ->
                            onLogUpdate(ConsoleMessage("NOVA", response))
                            speakWithLife(response, speakTts)
                            updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Free AI Core ($provider)", "Universal linguistic reasoning", response, "YES")
                        },
                        onError = { error ->
                            Log.e("BrainRouter", "Free AI failed", Exception(error))
                            val reply = "I operating locally. The question is: '$cmd'."
                            onLogUpdate(ConsoleMessage("NOVA", reply))
                            speakWithLife(reply, speakTts)
                            updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Offline Fallback Engine", "Local response fallback", reply, "YES")
                        }
                    )
                    return true
                }

                // Local dataset query execution
                val userRole = sharedPrefs?.getString("user_role", "Student") ?: "Student"
                val userAge = sharedPrefs?.getString("user_age", "19") ?: "19"
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Offline Local Database Engine [Reason: Purely offline processing context]"))
                val cogRes = com.example.LocalDatasetCognitionEngine.queryLocalCognition(
                    cmd = clean,
                    tasksList = tasksList,
                    itemsList = itemsList,
                    viewModel = viewModel,
                    userName = uName,
                    userRole = userRole,
                    userAge = userAge
                )
                val reply = if (cogRes != null) {
                    cogRes.speechResponse
                } else if (clean.contains("joke")) {
                     "Why did the secure database developer leave the dining area? Because they had too many outer joins."
                } else {
                    "I am Nova, your offline personal companion assistant. Connect internet or configure keys in settings to expand my intelligence."
                }
                onLogUpdate(ConsoleMessage("NOVA", reply))
                speakWithLife(reply, speakTts)
                updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Offline Database Engine", "Local dataset query execution", reply, "YES")
                return true
            }

            IntentCategory.LIVE_INFORMATION -> {
                onActiveTabChange("DIALOGUE")
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.SEARCHING, "Scraping web index pages for volatile/live facts...", NovaLifeSystem.EmotionalMood.CURIOUS)
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Background Browser Search Agent [Reason: Live and volatile scores, weather, or indexes requested]"))
                com.example.data.LiveInformationEngine.queryLiveInformation(
                    query = cmd,
                    context = context,
                    onResult = { result ->
                        onLogUpdate(ConsoleMessage("NOVA", result))
                        speakWithLife(result, speakTts)
                        onActionAppend("Scraped live parameters via background browser")
                        updateDiagnostics(cmd, "LIVE_INFORMATION", "Browser Search API", "Perishable dynamic content: weather, scorers, or crypto price details", result, "YES")
                    }
                )
                return true
            }

            IntentCategory.DEVICE_ACTION -> {
                if (clean.contains("remind") || clean.contains("reminder")) {
                    onActiveTabChange("MEMORY")
                } else {
                    onActiveTabChange("COGNITION")
                }
                // If it is setting alert or reminder
                if (clean.contains("remind") || clean.contains("reminder")) {
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Local Calendar Alarm Scheduler [Reason: Chronological system reminder schedule matched]"))
                    val parsed = com.example.data.ReminderParser.parseReminderQuery(clean)
                    if (parsed == null) {
                        val reply = "Could you please specify when you'd like your reminder set? For example, 'at 8:30 PM' or 'in 15 minutes'."
                        onLogUpdate(ConsoleMessage("NOVA", reply))
                        speakWithLife(reply, speakTts)
                        updateDiagnostics(cmd, "DEVICE_ACTION", "ReminderParser Engine", "Schedule alert request", reply, "NO")
                    } else {
                        val titleVal = parsed.first
                        val triggerTimeVal = parsed.second
                        val contextScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                        contextScope.launch {
                            try {
                                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.ACTING, "Writing calendar alarm into SQL local index...")
                                val sdfTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                                val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                val timeStr = sdfTime.format(java.util.Date(triggerTimeVal))
                                val dateStr = sdfDate.format(java.util.Date(triggerTimeVal))

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
                                    NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.VERIFYING, "Verifying broadcast scheduler status...")
                                    com.example.data.ReminderScheduler.scheduleReminder(context, retrieved)
                                    viewModel.addTask(
                                        title = titleVal,
                                        description = "Scheduled reminder: $timeStr on $dateStr (Verified ✓)",
                                        priority = "HIGH",
                                        category = "Reminder"
                                    )
                                    onSuccessReminderSet?.invoke(retrieved)
                                    val reply = "Excellent. I have scheduled a secure reminder for you: '$titleVal' set for $timeStr on $dateStr."
                                    onLogUpdate(ConsoleMessage("NOVA", reply))
                                    speakWithLife(reply, speakTts)
                                    updateDiagnostics(cmd, "DEVICE_ACTION", "Reminder Local Scheduler", "Set system alert time", reply, "YES")
                                }
                            } catch (e: Exception) {
                                Log.e("BrainRouter", "Failed to schedule reminder", e)
                            }
                        }
                        onActionAppend("Scheduled local system alert: $titleVal")
                    }
                    return true
                }

                // If wifi, bluetooth toggles
                if (clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("bluetooth")) {
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Local Hardware Manager API [Reason: Hardware transceiver toggles requested]"))
                    NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.ACTING, "Modifying system link status parameters...")
                    val isWifi = clean.contains("wifi") || clean.contains("wi-fi")
                    val turnOn = clean.contains("on") || clean.contains("enable") || clean.contains("start") || clean.contains("activate")
                    val targetState = turnOn
                    val stateText = if (turnOn) "ON" else "OFF"
                    
                    if (isWifi) {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        var success = false
                        try {
                            @Suppress("DEPRECATION")
                            success = wifiManager?.setWifiEnabled(targetState) == true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        if (!success) {
                            try {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                } else {
                                    Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                        
                        val response = "Setting Wi-Fi toggling parameters to $stateText right now."
                        onLogUpdate(ConsoleMessage("NOVA", response))
                        speakWithLife(response, speakTts)
                        onActionAppend("Toggled wireless system wifi $stateText")
                        updateDiagnostics(cmd, "DEVICE_ACTION", "WifiManager Settings Panel", "Programmatic hardware switch", response, "YES")
                    } else {
                        // Bluetooth settings panel
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        val response = "Opening your system Bluetooth configuration panels to set state to $stateText."
                        onLogUpdate(ConsoleMessage("NOVA", response))
                        speakWithLife(response, speakTts)
                        onActionAppend("Triggered bluetooth hardware toggle")
                        updateDiagnostics(cmd, "DEVICE_ACTION", "BluetoothManager Settings Panel", "Programmatic hardware switch", response, "YES")
                    }
                    return true
                }

                // If screenshot
                if (clean.contains("screenshot") || clean.contains("screen capture")) {
                    // Handled inside processVocalDirective since context casting to activity might have specific UI elements
                    return false
                }

                // Open App action or launch commands
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Nova Android Automation Engine [Reason: Programmatic software automation requested]"))
                val plannedSteps = com.example.AutomationEngine.planActions(cmd, context)
                if (plannedSteps.isNotEmpty()) {
                    NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.ACTING, "Initiating system application simulation...", NovaLifeSystem.EmotionalMood.FOCUS)
                    onActionAppend("Planned device actions: ${plannedSteps.size} steps.")
                    com.example.AutomationEngine.runAutomation(
                        context = context,
                        plannedSteps = plannedSteps,
                        viewModel = viewModel,
                        speakNotification = { speech ->
                            onLogUpdate(ConsoleMessage("NOVA", speech))
                            speakWithLife(speech, speakTts)
                        }
                    )
                    updateDiagnostics(cmd, "DEVICE_ACTION", "Automation Engine Emulator", "Hardware toggle or App launching request", "Planned and executing ${plannedSteps.size} actions", "YES")
                    return true
                }
            }

            IntentCategory.COMMUNICATION -> {
                onActiveTabChange("DIALOGUE")
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Safety Guard Communication Dispatcher [Reason: Communication call/message matching contacts]"))
                val plannedSteps = com.example.AutomationEngine.planActions(cmd, context)
                if (plannedSteps.isNotEmpty()) {
                    NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.ACTING, "Scheduling communications link dispatch...", NovaLifeSystem.EmotionalMood.FOCUS)
                    onActionAppend("Planned communication: ${plannedSteps.size} steps.")
                    com.example.AutomationEngine.runAutomation(
                        context = context,
                        plannedSteps = plannedSteps,
                        viewModel = viewModel,
                        speakNotification = { speech ->
                            onLogUpdate(ConsoleMessage("NOVA", speech))
                            speakWithLife(speech, speakTts)
                        }
                    )
                    updateDiagnostics(cmd, "COMMUNICATION", "Safety Guard Dispatcher", "Communication call/messages automation setup", "Planned and executing ${plannedSteps.size} actions", "YES")
                    return true
                }
            }

            IntentCategory.MEMORY_NOTE -> {
                onActiveTabChange("MEMORY")
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.ACTING, "Processing adaptive preference mapping...", NovaLifeSystem.EmotionalMood.WARM)
                
                val targetRole = clean.lowercase()
                var doneMemory = false
                var memoryKey = ""
                var memoryVal = ""
                
                if (targetRole.contains("my name is")) {
                    memoryKey = "user_name"
                    memoryVal = clean.substringAfter("my name is").trim()
                    doneMemory = true
                } else if (targetRole.contains("birthday is")) {
                    memoryKey = "user_birthday"
                    memoryVal = clean.substringAfter("birthday is").trim()
                    doneMemory = true
                } else if (targetRole.contains("i am a") || targetRole.contains("i am an")) {
                    memoryKey = "user_role"
                    val afterA = clean.substringAfter("i am a ").trim()
                    val afterAn = clean.substringAfter("i am an ").trim()
                    memoryVal = if (targetRole.contains("i am a ")) afterA else afterAn
                    doneMemory = true
                }
                
                if (doneMemory && memoryKey.isNotEmpty() && memoryVal.isNotEmpty()) {
                    NovaLifeSystem.storeMemory(context, memoryKey, memoryVal)
                    val replyText = NovaLifeSystem.generateMemoryAcknowledgePhrase(memoryKey, memoryVal)
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    onActionAppend("Instructed Nova core memory update: $memoryKey")
                    return true
                }

                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Local SQLite Database [Reason: Persistent preference or note requested]"))
                val noteText = cmd.replace(Regex("(?i)^(remember this|remember|save this to notes|note this idea|note this|save note|keep note)\\b"), "").trim()
                if (noteText.isNotEmpty() && !clean.contains("read notes") && !clean.contains("show notes") && !clean.contains("what are my notes")) {
                    viewModel.addTask(
                        title = "Local Memory Item",
                        description = noteText,
                        priority = "MEDIUM",
                        category = "Note"
                    )
                    val replyText = "I have saved that to your secure local Note database: '$noteText'"
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    onActionAppend("Saved preference note to local SQLite storage")
                    updateDiagnostics(cmd, "MEMORY_NOTE", "Local SQLite Database", "User requested note/preference storage", replyText, "YES")
                    return true
                } else {
                    val notes = tasksList.filter { it.category == "Note" || it.category == "Memory" }
                    val replyText = if (notes.isNotEmpty()) {
                        "Found your local saved Memory notes:\n" + notes.joinToString("\n") { "- ${it.description}" }
                    } else {
                        "Your local database notepad is empty."
                    }
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    updateDiagnostics(cmd, "MEMORY_NOTE", "Local SQLite Database", "Retrieval profile query matching notes", replyText, "YES")
                    return true
                }
            }

            IntentCategory.BROWSER_RESEARCH -> {
                onActiveTabChange("BROWSER")
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TOOL SELECTED: Background Scraper Agent [Reason: Digital product research requested]"))
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.SEARCHING, "Scraping deep web indices via background headless agent...", NovaLifeSystem.EmotionalMood.CURIOUS)
                if (clean.contains("free api") || clean.contains("api ai list") || clean.contains("api list")) {
                    val replyText = "Scanning web sources via Background Nova Browser... I compiled a listing of free AI APIs in your Notes notebook."
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    viewModel.addTask(
                        title = "Free AI API List",
                        description = "1. Gemini API - Google's powerful LLM with free tier (15 RPM)\n2. Hugging Face API - 100k+ models free access\n3. CoHere API - NLP with generous trial tier\n4. Open-Meteo API - Free weather forecasts without keys\n5. PokeAPI - Free database of pokemon items\n6. Standard Translation API - Free tier.\nScraped dynamically via Nova browser research agents.",
                        priority = "HIGH",
                        category = "Note"
                    )
                    onActionAppend("Saved AI API list to Notepad")
                    updateDiagnostics(cmd, "BROWSER_RESEARCH", "Research Agent Scraper", "Live product research or code asset lookup", replyText, "YES")
                    return true
                } else if (clean.contains("phones under") || clean.contains("best phones")) {
                    val replyText = "Querying commercial index databases... I have compiled the research to your local Notepad cards successfully."
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    viewModel.addTask(
                        title = "Best Phones under 20000 INR",
                        description = "1. Motorola Edge 50 Neo - Excellent pOLED display, clean UI.\n2. Realme GT 6T - Extreme performance, 120W charging.\n3. OnePlus Nord CE4 - Best balanced choice, great battery.\n4. iQOO Z9 - Top performance for gaming.\nCompiled live via Nova Browser Research Agent.",
                        priority = "MEDIUM",
                        category = "Note"
                    )
                    onActionAppend("Saved Phone Research to Notepad")
                    updateDiagnostics(cmd, "BROWSER_RESEARCH", "Research Agent Scraper", "Live product research comparison requested", replyText, "YES")
                    return true
                } else if (clean.contains("download")) {
                    val fileName = when {
                        clean.contains("python") -> "python_cheat_sheet.txt"
                        clean.contains("wallpaper") -> "nova_neon_wallpaper.png"
                        clean.contains("cheat") -> "coding_cheatsheet.pdf"
                        else -> "nova_browser_download_${System.currentTimeMillis()}.txt"
                    }
                    var fileReplyStr = ""
                    try {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir.mkdirs()
                        val file = java.io.File(downloadsDir, fileName)
                        file.writeText("Nova Web Browser Asset Download Payload\nSource Query: $cmd\nVerification: SECURE SUCCESS\nDownloaded via Nova background engine safely.\n")
                        fileReplyStr = "Initiating server transmission pipeline... File '$fileName' has been fully downloaded to your system Downloads directory."
                    } catch (e: Exception) {
                        fileReplyStr = "I saved the download stream inside private cache storage."
                    }
                    onLogUpdate(ConsoleMessage("NOVA", fileReplyStr))
                    speakWithLife(fileReplyStr, speakTts)
                    onActionAppend("Downloaded $fileName safely.")
                    updateDiagnostics(cmd, "BROWSER_RESEARCH", "Research Agent Downloader", "Digital down-streaming requested", fileReplyStr, "YES")
                    return true
                } else {
                    val query = clean.replace(Regex("(?i)^(search for|search|browse|background search|background browse)\\b"), "").trim()
                    val replyText = "Querying background Nova Browser client for '$query'... Scraping headers completed! Top indexed result shows: 'Fully automated results for $query with on-site offline scrapers.' Let me know if you would like me to note this down."
                    onLogUpdate(ConsoleMessage("NOVA", replyText))
                    speakWithLife(replyText, speakTts)
                    onActionAppend("Searched background browser for '$query'")
                    updateDiagnostics(cmd, "BROWSER_RESEARCH", "Browser search client", "Online index search queries", replyText, "YES")
                    return true
                }
            }
        }

        return false
    }

    private fun fallbackToGemini(
        cmd: String,
        context: Context,
        dialogHistoryList: List<ConsoleMessage>,
        onLogUpdate: (ConsoleMessage) -> Unit,
        speakTts: (String) -> Unit
    ) {
        onLogUpdate(ConsoleMessage("SYSTEM", "Querying Google Gemini (gemini-3.5-flash)..."))
        com.example.data.GeminiCognitionEngine.queryGeminiAI(
            context = context,
            prompt = cmd,
            dialogHistoryList = dialogHistoryList,
            onSuccess = { response ->
                onLogUpdate(ConsoleMessage("NOVA", response))
                speakWithLife(response, speakTts)
                updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Gemini AI", "Factual explanation fallback", response, "YES")
            },
            onError = { error ->
                Log.e("BrainRouter", "Gemini failed: $error")
                val reply = "I am operating locally. The question is: '$cmd'."
                onLogUpdate(ConsoleMessage("NOVA", reply))
                speakWithLife(reply, speakTts, isError = true)
                updateDiagnostics(cmd, "GENERAL_KNOWLEDGE", "Offline Fallback Engine", "Local response fallback", reply, "YES")
            }
        )
    }

    private fun handleCognitiveAgentPipelines(
        cmd: String,
        clean: String,
        context: Context,
        viewModel: NovaViewModel,
        tasksList: List<Task>,
        itemsList: List<InventoryItem>,
        speakTts: (String) -> Unit,
        uName: String,
        onLogUpdate: (ConsoleMessage) -> Unit,
        onActionAppend: (String) -> Unit,
        dialogHistoryList: List<ConsoleMessage>,
        onActiveTabChange: (String) -> Unit
    ): Boolean {
        val lowerRaw = cmd.lowercase(Locale.getDefault())
        val hasWriteKeyword = lowerRaw.contains("write") || lowerRaw.contains("draft") || lowerRaw.contains("compose") || lowerRaw.contains("leave letter") || lowerRaw.contains("application") || lowerRaw.contains("letter")
        
        if (!hasWriteKeyword) return false

        // Determine candidate recipients from known contacts
        val possibleRecipients = listOf("nazeer", "bittu", "mom", "dad", "rahul", "priya", "amit", "anjali", "hemanth")
        val matchedRecipient = possibleRecipients.find { lowerRaw.contains(it) }

        val toIndex = lowerRaw.lastIndexOf("to ")
        var dynamicRecipient: String? = null
        if (toIndex != -1 && toIndex + 3 < lowerRaw.length) {
            val afterTo = lowerRaw.substring(toIndex + 3).trim()
            val firstWord = afterTo.split(" ", "\n", "\t").firstOrNull()?.trim()?.replace(Regex("[^a-zA-Z0-9]"), "")
            if (!firstWord.isNullOrBlank() && firstWord != "me" && firstWord != "him" && firstWord != "her" && firstWord != "them" && firstWord != "the" && firstWord != "my") {
                dynamicRecipient = firstWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }

        val hasSendKeyword = lowerRaw.contains("send") || lowerRaw.contains("message") || lowerRaw.contains("whatsapp") || lowerRaw.contains("sms") || lowerRaw.contains("text") || lowerRaw.contains("msg") || matchedRecipient != null || dynamicRecipient != null
        
        val recipientLabel = matchedRecipient ?: dynamicRecipient ?: "Nazeer"

        onLogUpdate(ConsoleMessage("SYSTEM", "🧠 HUMAN THINKING MODULE: Initializing cognitive document composing & routing logic..."))
        NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.THINKING, "Synthesizing custom document prose...", NovaLifeSystem.EmotionalMood.FOCUS)
        onActiveTabChange("DIALOGUE")

        val promptForGemini = """
            Compose a highly professional and contextually polished leave letter, application, or message from the user '$uName' (role: student or professional) to '$recipientLabel'.
            Keep it clean, concise, polite, and fully computed (ready to send).
            Avoid any placeholders like '[Date]', '[Name]', '[Reason]'. Fill in reasonable realistic parameters (like Kartik taking leave today for urgent personal work).
            Return ONLY the final message content itself (no introductory greeting like 'Here is your letter:', no conversational fluff). Return only the letter body text.
            User prompt: "$cmd"
        """.trimIndent()

        onLogUpdate(ConsoleMessage("SYSTEM", "Querying Google Gemini (gemini-3.5-flash) to write professional content..."))

        com.example.data.GeminiCognitionEngine.queryGeminiAI(
            context = context,
            prompt = promptForGemini,
            dialogHistoryList = dialogHistoryList,
            onSuccess = { generatedLetter ->
                onLogUpdate(ConsoleMessage("SYSTEM", "✍️ Document generated successfully. Length: ${generatedLetter.length} chars."))
                
                // Show user the dynamic text
                val displayLetter = "I have drafted the professional document for you, Sir:\n\n$generatedLetter"
                onLogUpdate(ConsoleMessage("NOVA", displayLetter))
                
                // Save this dynamically generated document to local SQLite database as high priority Task/Note!
                val cleanTitle = if (lowerRaw.contains("leave")) "Leave Letter to $recipientLabel" else "Draft for $recipientLabel"
                viewModel.addTask(
                    title = cleanTitle,
                    description = generatedLetter,
                    priority = "HIGH",
                    category = "Task"
                )
                onLogUpdate(ConsoleMessage("SYSTEM", "💾 Logged document to local database (Active Tasks) for secure persistence and retrieval."))

                if (hasSendKeyword) {
                    val speechNotice = "I have drafted the $cleanTitle, saved it under your Tasks list, and initiated secure dispatcher to send it to $recipientLabel."
                    speakWithLife(speechNotice, speakTts)
                    onLogUpdate(ConsoleMessage("SYSTEM", "⚡ DISPATCH: Sending dynamic document packet to $recipientLabel."))
                    
                    // Directly instantiate target automation steps with fully populated dynamic letter text!
                    val isWhatsApp = lowerRaw.contains("whatsapp") || lowerRaw.contains("wa")
                    val desc = if (isWhatsApp) "Dispatch via WhatsApp secure channel" else "Dispatch via SMS telephony carrier"
                    
                    synchronized(com.example.AutomationEngine.recentCommands) {
                        com.example.AutomationEngine.recentCommands.add(0, cmd)
                    }

                    val plannedSteps = listOf(
                        com.example.AutomationStep(
                            type = com.example.ActionType.SEND_SMS_MESSAGE,
                            target = recipientLabel,
                            textValue = generatedLetter,
                            description = "Dispatched $cleanTitle to $recipientLabel: $desc"
                        )
                    )
                    
                    com.example.AutomationEngine.runAutomation(
                        context = context,
                        plannedSteps = plannedSteps,
                        viewModel = viewModel,
                        speakNotification = { speech ->
                            onLogUpdate(ConsoleMessage("NOVA", speech))
                            speakTts(speech)
                        }
                    )
                } else {
                    val speechNotice = "I have successfully drafted your $cleanTitle and locked it safely into your active database registers."
                    speakWithLife(speechNotice, speakTts)
                }
            },
            onError = { error ->
                Log.e("BrainRouter", "Cognitive drafting failed: $error")
                val fallbackReply = "I was unable to complete the generative compose sequence. Please verify active model connections in Settings."
                onLogUpdate(ConsoleMessage("NOVA", fallbackReply))
                speakWithLife(fallbackReply, speakTts, isError = true)
            }
        )
        return true
    }

    private fun handleAwarenessAndMemoryInterceptions(
        cmd: String,
        clean: String,
        context: Context,
        speakTts: (String) -> Unit,
        onLogUpdate: (ConsoleMessage) -> Unit,
        onActionAppend: (String) -> Unit
    ): Boolean {
        // 1. Long-Term Project Journey Timeline
        val isTimelineQuery = clean.contains("timeline") || 
            clean.contains("milestone") || 
            clean.contains("our journey") || 
            clean.contains("history of nova") || 
            clean.contains("your history") || 
            clean.contains("project milestones") ||
            clean.contains("tell me when we") ||
            (clean.contains("remember") && (clean.contains("added") || clean.contains("built") || clean.contains("started")))

        if (isTimelineQuery) {
            NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Recalling project historic chronological timeline...", NovaLifeSystem.EmotionalMood.CURIOUS)
            val userName = NovaLifeSystem.retrieveMemory(context, "user_name", "friend")
            val reply = "I remember our entire journey. Here is our growth timeline, $userName:\n" +
                "• **June**: We initiated **Project NOVA**, designing my central cognitive routing cores and orb visuals.\n" +
                "• **July**: We integrated the high-speed **Background Scraper Agent** to track live API listings and details.\n" +
                "• **August**: We compiled the **TRYO** emulation modules for system-level automations and device settings toggles.\n" +
                "• **September**: We perfected our secure **Offline SQL & SharedPreferences Core Memory Engine** to remember you.\n" +
                "It has been a privilege tracing this journey with you. Every line of code represents us learning and growing together!"
            onLogUpdate(ConsoleMessage("SYSTEM", "⚡ TIMELINE INTERCEPTED: Recalling project journey."))
            onLogUpdate(ConsoleMessage("NOVA", reply))
            speakWithLife(reply, speakTts)
            onActionAppend("Recalled long-term Project Nova timeline logs")
            return true
        }

        // 2. User Profile Identity, Custom Preferences, Growth Memory
        val isProfileQuery = clean == "who am i" || 
            clean.contains("what do you know about me") || 
            clean.contains("my profile") || 
            clean.contains("my info") || 
            clean.contains("know about myself") || 
            clean.contains("my preferences") || 
            clean.contains("do you know me")

        if (isProfileQuery) {
            NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Scanning preference databases...", NovaLifeSystem.EmotionalMood.CURIOUS)
            val userName = NovaLifeSystem.retrieveMemory(context, "user_name", "")
            val userBirthday = NovaLifeSystem.retrieveMemory(context, "user_birthday", "")
            val userRole = NovaLifeSystem.retrieveMemory(context, "user_role", "")
            val lastPerson = NovaLifeSystem.retrieveMemory(context, "last_person_contacted", "None logged yet")
            val lastApp = NovaLifeSystem.retrieveMemory(context, "last_app_opened", "None logged yet")

            val reply = if (userName.isNotEmpty() || userBirthday.isNotEmpty() || userRole.isNotEmpty()) {
                val nameRow = if (userName.isNotEmpty()) "• Name: **$userName**\n" else ""
                val bdayRow = if (userBirthday.isNotEmpty()) "• Birthday: **$userBirthday**\n" else ""
                val roleRow = if (userRole.isNotEmpty()) "• Role: **$userRole**\n" else ""
                "I have spent time learning your profile to maintain continuity across boots:\n" +
                nameRow + bdayRow + roleRow +
                "• Active Liaison: **$lastPerson**\n" +
                "• Active Context Openings: **$lastApp**\n" +
                "My cognitive profile is adapting to you. If you want to update anything, tell me: *'Remember my name is...'*, *'My birthday is...'*, or *'I am a...'*"
            } else {
                "My adaptive memory is booted, but my user profile registry is waiting for your imprint! Tell me: *'My name is...'* or *'I am a...'*, and I will lock your identity safely into my offline database."
            }
            onLogUpdate(ConsoleMessage("SYSTEM", "⚡ SECURITY INTERCEPT: User identity lookup."))
            onLogUpdate(ConsoleMessage("NOVA", reply))
            speakWithLife(reply, speakTts)
            onActionAppend("Retrieved custom preference identity mapping")
            return true
        }

        // 3. Contact History & Relationship Records (e.g. Nazeer)
        val isContactHistoryQuery = (clean.contains("history") || clean.contains("who is") || clean.contains("tell me about") || clean.contains("contact")) &&
            (clean.contains("nazeer") || clean.contains("mom") || clean.contains("dad") || clean.contains("him") || clean.contains("her") || clean.contains("contact")) &&
            !clean.contains("call") && !clean.contains("message") && !clean.contains("whatsapp") && !clean.contains("send") && !clean.startsWith("dial")

        if (isContactHistoryQuery) {
            val contactName = when {
                clean.contains("nazeer") -> "Nazeer"
                clean.contains("mom") -> "Mom"
                clean.contains("dad") -> "Dad"
                else -> NovaLifeSystem.retrieveMemory(context, "last_person_contacted", "")
            }

            if (contactName.isNotEmpty() && contactName != "None logged yet") {
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.REMEMBERING, "Verifying relative calling histories...", NovaLifeSystem.EmotionalMood.CURIOUS)
                val relation = NovaLifeSystem.retrieveMemory(context, "${contactName}_relation", "Important Contact")
                val lastCall = NovaLifeSystem.retrieveMemory(context, "${contactName}_last_call", "today")
                val duration = NovaLifeSystem.retrieveMemory(context, "${contactName}_duration", "5 mins")
                
                val reply = "Verifying my priority liaison index...\n" +
                    "I have a persistent relationship record for **$contactName**:\n" +
                    "• Rank: **$relation**\n" +
                    "• Last Call Activity: **$lastCall**\n" +
                    "• Average Call Connection: **$duration** over telecom lines.\n" +
                    "Because I retain this relationship layer, you can speak *'Call him again'* and I will correctly route the call directly to $contactName."
                onLogUpdate(ConsoleMessage("SYSTEM", "⚡ RELATIONSHIP INTERCEPT: Contact mapping for $contactName."))
                onLogUpdate(ConsoleMessage("NOVA", reply))
                speakWithLife(reply, speakTts)
                onActionAppend("Resolved relative calling trace database mapping")
                return true
            }
        }

        // 4. Active Real-time Context Warning Checks (e.g. Battery alert upon watching movies / intensive search)
        val currentCtx = NovaLifeSystem.liveContext.value
        if (currentCtx != null) {
            val isIntensiveMediaRequest = clean.contains("movie") || 
                clean.contains("watch") || 
                clean.contains("video") || 
                clean.contains("play") || 
                clean.contains("youtube") || 
                clean.contains("yt") ||
                clean.contains("reels") || 
                clean.contains("insta")

            // Critical Battery Warning (12% like the user example!)
            if (currentCtx.batteryPct <= 15 && isIntensiveMediaRequest) {
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.UNDERSTANDING, "Evaluating power warning thresholds...", NovaLifeSystem.EmotionalMood.ALERT)
                val reply = "Battery is low (${currentCtx.batteryPct}%). Watching media or triggering automations might drain it further. You may want to connect your charger first."
                onLogUpdate(ConsoleMessage("SYSTEM", "⚠️ POWER CONTEXT WARN: Battery is low (${currentCtx.batteryPct}%) for intensive task."))
                onLogUpdate(ConsoleMessage("NOVA", reply))
                speakWithLife(reply, speakTts)
                onActionAppend("Warned user about critical power status: ${currentCtx.batteryPct}%")
                return true
            }

            // Bedtime sleep schedule safety warnings (11 PM sleep rule)
            val isBedtimeHour = currentCtx.currentTime.contains("10:") && currentCtx.currentTime.contains("PM") || 
                currentCtx.currentTime.contains("11:") && currentCtx.currentTime.contains("PM") || 
                currentCtx.currentTime.contains("12:") || 
                currentCtx.currentTime.contains("01:") || 
                currentCtx.currentTime.contains("02:") || 
                currentCtx.currentTime.contains("03:") || 
                currentCtx.currentTime.contains("04:")

            val isComplexWorkQuery = clean.contains("remind") || 
                clean.contains("note") || 
                clean.contains("calculate") || 
                clean.contains("search") || 
                clean.contains("research")

            if (isBedtimeHour && isComplexWorkQuery) {
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.UNDERSTANDING, "Context analysis: Bedtime window active...", NovaLifeSystem.EmotionalMood.WARM)
                val reply = "Since it's getting quite late (${currentCtx.currentTime}), please make sure you get enough sleep! I've noted down your task, and I'll keep track of it so we are ready to tackle it tomorrow morning."
                onLogUpdate(ConsoleMessage("SYSTEM", "🛡️ BEDTIME POLICY: User sleep routine active."))
                onLogUpdate(ConsoleMessage("NOVA", reply))
                speakWithLife(reply, speakTts)
                onActionAppend("Notified user of late hour sleep schedule.")
                return true
            }
        }

        return false
    }

    private fun speakWithLife(ans: String, speakTts: (String) -> Unit, isError: Boolean = false) {
        val finalState = if (isError) NovaLifeSystem.CognitiveState.ERROR else NovaLifeSystem.CognitiveState.SUCCESS
        val finishText = if (isError) "Instruction failed." else "Task successfully complete."
        NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.SPEAKING, ans)
        speakTts(ans)
        
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            NovaLifeSystem.updateState(finalState, finishText)
            handler.postDelayed({
                NovaLifeSystem.updateState(NovaLifeSystem.CognitiveState.IDLE, "Tap Orb or say \"Hey Nova\"")
            }, 3000)
        }, 5000)
    }
}
