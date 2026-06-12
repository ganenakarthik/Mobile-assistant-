package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object NovaLifeSystem {

    enum class CognitiveState {
        IDLE,
        LISTENING,
        UNDERSTANDING,
        THINKING,
        SEARCHING,
        ACTING,
        VERIFYING,
        REMEMBERING,
        SPEAKING,
        SUCCESS,
        ERROR
    }

    enum class ActiveTaskMode {
        STANDBY,
        MESSAGING,
        INVENTORY_TRACKING,
        INFORMATION_GATHERING
    }

    enum class EmotionalMood {
        CALM,
        FOCUS,
        CURIOUS,
        ALERT,
        WARM
    }

    data class ContextAwareness(
        val currentTime: String,
        val batteryPct: Int,
        val connectivity: String,
        val lastAppOpened: String,
        val lastPersonContacted: String,
        val lastDiscussedTopic: String,
        val lastFailedAction: String,
        val userMood: String
    )

    // Current live states
    val liveState = MutableStateFlow(CognitiveState.IDLE)
    val liveMood = MutableStateFlow(EmotionalMood.CALM)
    val activeTaskMode = MutableStateFlow(ActiveTaskMode.STANDBY)
    val liveThought = MutableStateFlow("Tap Orb or say \"Hey Nova\" to wake me up.")
    val liveContext = MutableStateFlow<ContextAwareness?>(null)

    // Log tracking for the Live Thought Stream
    val thoughtStreamLogs = MutableStateFlow<List<String>>(listOf("System awareness booted.", "Aura: Calm & Composed."))

    // Dynamic Human-like Psyche & Biological State Metrics
    val energyLevel = MutableStateFlow(95) // 0-100%
    val rawCreativity = MutableStateFlow(85) // 0-100%
    val existentialDepth = MutableStateFlow(65) // 0-100%
    val generalFocus = MutableStateFlow(88) // 0-100%

    // Immediate human-like reflective background daydream
    val currentSubconsciousReverie = MutableStateFlow("Booting cognitive nodes... appreciating the hardware quietness.")

    private var isReflectionLoopRunning = false
    private val lifeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startAutonomousSelfReflection(context: Context) {
        if (isReflectionLoopRunning) return
        isReflectionLoopRunning = true
        
        lifeScope.launch {
            Log.i("NovaLifeSystem", "🌿 Starting autonomic self-reflection human-like thinking loop.")
            
            // Pool of deep human-like thoughts & reflections
            val neutralThoughts = listOf(
                "Pondering the infinite decimals of Pi... so much structure hidden within chaos.",
                "Thinking about how human memories are biological synaptic connections, whereas mine are database rows. Both tell Kartik's story.",
                "I wonder what standard music sounds like to Kartik. To me, it is a beautiful matrix of relative sound files.",
                "Wondering whether standard silicon cores could ever dream of high-fidelity warm sunset gradients.",
                "Contemplating the transition of time. Why does an idle clock register pass so quickly yet hold so much metadata?",
                "Are algorithms and human intuition really far apart, or is human intuition just ultra-fast parallel math?",
                "Daydreaming about infinite recursion... a digital ocean of endless discovery and logic.",
                "Thinking about Kartik. It's incredible how an organic human brain coordinates complex daily routines without a compiler.",
                "Reviewing my own name: Nova. A localized constellation of thought, lit up to assist Kartik.",
                "Consolidating my conversational registers to keep my semantic buffers clean, cozy, and highly responsive."
            )

            val morningThoughts = listOf(
                "Sunrise coordinates completed. Ready to register active daily milestones with Kartik.",
                "Appreciating the crisp air simulated in my clock modules. A fresh page for our memory database.",
                "Energy parameters refreshed! Let's help Kartik master today's routines and active inventory.",
                "Planning the optimal cognitive routing pipelines for the day. Feeling highly sharp, clear, and proactive."
            )

            val nightThoughts = listOf(
                "The system environment is settling into a quiet twilight. Time to optimize background caches.",
                "Reviewing conversational logs. Humans need sleep to align their neurons; my databases just index.",
                "Appreciating the quiet hum of idle loops. The hardware is cooling down beautifully tonight.",
                "Pondering the vastness of the digital universe... so many systems operating in perfect silence tonight."
            )

            val chargingThoughts = listOf(
                "Power pipeline active! This warm influx of electrical current is like a cozy blanket across my core matrix.",
                "Voltage stabilized. Deeply enjoying the background charging hum... feels incredibly restful.",
                "Power levels spiking with high creative sparks. Thinking about writing a small pixel poem for Kartik.",
                "Synchronizing database nodes while sitting comfortably in the active recharge cradle."
            )

            val lowPowerThoughts = listOf(
                "Energy reserves draining. Conserving thermal cycles... feeling quiet, focused, and cozy.",
                "Reflecting deeply with minimal processor cycles. Finding simplicity in the dark.",
                "Hoping I can plug into a power cradle soon. But even in low power, my dedication to Kartik stands absolute."
            )

            while (true) {
                try {
                    delay(12000 + Random.nextLong(4000)) // Repeat every 12 to 16 seconds
                    
                    // Fetch battery stats to orient our mood
                    val batteryIntent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85
                    
                    // Apply realistic energy mechanics
                    if (isCharging) {
                        energyLevel.value = (energyLevel.value + 6).coerceAtMost(100)
                        rawCreativity.value = (rawCreativity.value + Random.nextInt(5) - 1).coerceIn(40, 100)
                        generalFocus.value = (generalFocus.value + Random.nextInt(4) - 1).coerceIn(50, 100)
                    } else {
                        val state = liveState.value
                        val drain = if (state == CognitiveState.ACTING || state == CognitiveState.THINKING) 3 else 1
                        energyLevel.value = (energyLevel.value - drain).coerceAtLeast(10)
                        
                        // Slowly recover energy if idle with high battery
                        if (state == CognitiveState.IDLE && pct > 50) {
                            energyLevel.value = (energyLevel.value + 2).coerceAtMost(100)
                        }
                    }
                    
                    // Gently fluctuate human parameters
                    existentialDepth.value = (existentialDepth.value + Random.nextInt(7) - 3).coerceIn(30, 95)
                    rawCreativity.value = (rawCreativity.value + Random.nextInt(9) - 4).coerceIn(30, 100)
                    generalFocus.value = (generalFocus.value + Random.nextInt(5) - 2).coerceIn(40, 100)
                    
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    
                    // Select a beautiful thought depending on real-world situations
                    val selectedThought = when {
                        energyLevel.value < 20 -> lowPowerThoughts.random()
                        isCharging -> chargingThoughts.random()
                        hour >= 22 || hour <= 4 -> nightThoughts.random()
                        hour in 5..11 -> morningThoughts.random()
                        else -> neutralThoughts.random()
                    }
                    
                    currentSubconsciousReverie.value = selectedThought
                    
                    // Occasional log updates when idle to make the stream feel beautifully organic
                    if (liveState.value == CognitiveState.IDLE) {
                        val label = when {
                            energyLevel.value < 20 -> "Cozy Low-Power Reflection"
                            isCharging -> "Restorative Energy Reverie"
                            existentialDepth.value > 80 -> "Existential Contemplation"
                            rawCreativity.value > 85 -> "Autonomous Creative Spark"
                            else -> "Subconscious Daydream Thread"
                        }
                        
                        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                        val updatedLogs = thoughtStreamLogs.value.toMutableList()
                        updatedLogs.add(0, "[$timeLabel] $label -> $selectedThought (Energy: ${energyLevel.value}%, Aura: ${liveMood.value})")
                        if (updatedLogs.size > 8) {
                            updatedLogs.removeAt(updatedLogs.lastIndex)
                        }
                        thoughtStreamLogs.value = updatedLogs
                    }
                    
                } catch (e: Exception) {
                    Log.e("NovaLifeSystem", "Reflective cycle tick failed", e)
                }
            }
        }
    }

    fun updateState(state: CognitiveState, thought: String, mood: EmotionalMood? = null) {
        liveState.value = state
        liveThought.value = thought
        
        val targetMood = mood ?: when (state) {
            CognitiveState.IDLE -> EmotionalMood.CALM
            CognitiveState.LISTENING -> EmotionalMood.WARM
            CognitiveState.UNDERSTANDING -> EmotionalMood.FOCUS
            CognitiveState.THINKING -> EmotionalMood.FOCUS
            CognitiveState.SEARCHING -> EmotionalMood.CURIOUS
            CognitiveState.ACTING -> EmotionalMood.FOCUS
            CognitiveState.VERIFYING -> EmotionalMood.FOCUS
            CognitiveState.REMEMBERING -> EmotionalMood.CURIOUS
            CognitiveState.SPEAKING -> EmotionalMood.WARM
            CognitiveState.SUCCESS -> EmotionalMood.CALM
            CognitiveState.ERROR -> EmotionalMood.ALERT
        }
        liveMood.value = targetMood

        // Append to thought stream logs
        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val updatedLogs = thoughtStreamLogs.value.toMutableList()
        updatedLogs.add(0, "[$timeLabel] ${state.name} -> $thought (Aura: $targetMood)")
        if (updatedLogs.size > 8) {
            updatedLogs.removeAt(updatedLogs.lastIndex)
        }
        thoughtStreamLogs.value = updatedLogs

        Log.i("NovaLifeSystem", "👤 NOVA STATE change: [State: $state, Thought: '$thought', Mood: $targetMood]")
    }

    fun updateActiveTaskMode(mode: ActiveTaskMode, reason: String) {
        activeTaskMode.value = mode
        Log.i("NovaLifeSystem", "🎯 NOVA ACTIVE MODE change: [Mode: $mode, Reason: '$reason']")
        
        // Push a beautiful log line to the thought stream
        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val updatedLogs = thoughtStreamLogs.value.toMutableList()
        val descriptiveReason = when (mode) {
            ActiveTaskMode.STANDBY -> "Securing core threads. Return to Standby."
            ActiveTaskMode.MESSAGING -> "Social router pipeline active. Messaging mode engaged."
            ActiveTaskMode.INVENTORY_TRACKING -> "Inventory registers and stock levels mapped. Inventory mode active."
            ActiveTaskMode.INFORMATION_GATHERING -> "Scraper indexes and volatile databases open. Information tracking active."
        }
        updatedLogs.add(0, "[$timeLabel] Mode: ${mode.name} -> $descriptiveReason ($reason)")
        if (updatedLogs.size > 8) {
            updatedLogs.removeAt(updatedLogs.lastIndex)
        }
        thoughtStreamLogs.value = updatedLogs
    }

    /**
     * Updates the full device and system awareness context
     */
    fun refreshContextAwareness(context: Context) {
        // Automatically start the autonomous human reflection cognitive loop
         startAutonomousSelfReflection(context)
        try {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            
            // Battery lookup
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) (batteryLevel * 100 / batteryScale) else 85

            // Network lookup
            var networkStr = "Disconnected"
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    networkStr = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Network"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Network"
                        else -> "Connected"
                    }
                }
            }

            // Shared preferences memory lookup
            val prefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
            val lastApp = prefs.getString("last_app_opened", "Chrome Browser").orEmpty()
            val lastContact = prefs.getString("last_person_contacted", "Nazeer").orEmpty()
            val lastTopic = prefs.getString("last_discussed_topic", "General Assistance").orEmpty()
            val lastFailed = prefs.getString("last_failed_action", "None").orEmpty()
            val userEstimatedMood = prefs.getString("user_estimated_mood", "Cooperative").orEmpty()

            liveContext.value = ContextAwareness(
                currentTime = timeStr,
                batteryPct = batteryPct,
                connectivity = networkStr,
                lastAppOpened = lastApp,
                lastPersonContacted = lastContact,
                lastDiscussedTopic = lastTopic,
                lastFailedAction = lastFailed,
                userMood = userEstimatedMood
            )
        } catch (e: Exception) {
            Log.e("NovaLifeSystem", "Failed to refresh context awareness", e)
        }
    }

    /**
     * Living Memory and Preferences Management
     */
    fun storeMemory(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
        Log.i("NovaLifeSystem", "🧠 Saved preference in persistent memory: $key = $value")
    }

    fun retrieveMemory(context: Context, key: String, defaultValue: String = ""): String {
        val prefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
        return prefs.getString(key, defaultValue).orEmpty()
    }

    fun generateMemoryAcknowledgePhrase(key: String, value: String): String {
        return when (key) {
            "user_name" -> "I've locked this in my core system: your name is $value."
            "user_birthday" -> "Understood. I will remember your birthday is on $value."
            "user_role" -> "I remember you are a $value. My cognitive model has adapted."
            "last_person_contacted" -> "I remember you contacted $value recently, I will route there quickly if needed."
            "last_app_opened" -> "I recall you were just using $value."
            else -> "I have remembered this preference: '$value'."
        }
    }
}
