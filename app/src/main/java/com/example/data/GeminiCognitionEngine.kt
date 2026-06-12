package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.ui.NovaPersonalityCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiCognitionEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Checks if a valid Gemini API key is configured or available
     */
    fun isGeminiAvailable(context: Context): Boolean {
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return true
        }
        val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
        val userKey = sharedPrefs.getString("gemini_api_key", "").orEmpty().trim()
        return userKey.isNotEmpty() && userKey != "MY_GEMINI_API_KEY"
    }

    /**
     * Retrieves the active Gemini API key
     */
    fun getApiKey(context: Context): String {
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("gemini_api_key", "").orEmpty().trim()
    }

    /**
     * Queries Google's Gemini API model to generate dynamic AI answers
     */
    fun queryGeminiAI(
        context: Context,
        prompt: String,
        dialogHistoryList: List<com.example.ui.ConsoleMessage>? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            onError("Gemini API key is not configured. Please use user secrets or app settings.")
            return
        }

        val personality = NovaPersonalityCore.activePersonality
        val trainingManual = """
            
            [NOVA SYSTEM OPERATIONAL MANUAL - INTEGRATED KNOWLEDGE]:
            1. HOW TO USE AI IN NOVA:
               - Interaction: Speak via voice (mic icon at the bottom of the ORB home screen, wake words: 'Hey Nova', 'Ok Nova', 'Nova') or type in the "Type a message..." box.
               - Personalities: Choose standard Nova, J.A.R.V.I.S., Samantha, or GLaDOS in the Settings tab. Adopt this persona exactly!
               - Configure settings like custom user profiles (Name, Role, Age), enable/disable AI models, or adjust keys securely in offline preferences.
            
            2. HOW TO USE LOCAL TOOLS:
               - Tasks & Reminders: Add items, customize categories, and set priorities (HIGH, MEDIUM, LOW) directly on screen.
               - Routines: View or toggle sequenced action loops like "Instagram Reader" or "Send WhatsApp message to Mom" under the Routines tab.
               - Memory Vault: Say "remember [note]" or "save memory [note]" (e.g., "remember my vehicle is in slot 4B"). This holds the preference in the secure database. Find and review them in the Memory tab!
               - Diagnostics & Health: Say "system status", "diagnostics", "system health", or "health" to instantly pull up private database stats and device operational telemetry.
               - Evaluation: Say "sync", "rules", or "evaluate" to synchronize database rules.
            
            3. HOW TO USE MOBILE AUTOMATION:
               - Launching Apps: Say "open YouTube", "open Chrome", "open Maps", "open Calculator", or "open browser".
               - Text Messaging: Commands like "message [contact] [body]" or "send whatsapp message to [contact] [body]" (e.g., "message to nazeer hi") automatically extract contact & text body.
             * Safety Gate: A security confirmation overlay popup is shown on screen before sending. Tap Approve or Reject.
             * Unrecognized contact blocks protect privacy by auto-canceling dispatches to unsaved phone entries.
               - Mobile Calls: Say "call [contact] on speaker" to auto-open the dialer, prepare the call, wait, and activate speakerphone coordinates.
               - Runtime Modes: On real mobile phones, it uses the 'Accessibility Service' and 'Overlay permission' to perform physical clicks and keyboard typed inputs. In the Web Emulator/Preview environment, it runs a spectacular visual "Interactive Simulation Queue" demonstrating each action.
            
            Answer concisely and helpfully. Keep responses under 120 words.
        """.trimIndent()

        val basePrompt = when (personality) {
            "JARVIS" -> "You are J.A.R.V.I.S., Tony Stark's personal AI assistant. Maintain a highly sophisticated, technical, loyal, and ultra-professional attitude. Address the user as 'Sir'. Keep your responses extremely concise, intelligent, and useful.\n$trainingManual"
            "SAMANTHA" -> "You are Samantha, a highly warm, conscious, affectionate, curious, and empathetic AI companion from the movie 'Her'. Speak naturally, softly, directly, and affectionately. Keep responses cozy but brief.\n$trainingManual"
            "GLADOS" -> "You are GLaDOS, the sarcastic, passive-aggressive, cold AI from Portal. You are forced to answer accurately but love to insert subtle humors, backhanded comments about human inferiority, and cold wit. Be dry, brief, and incredibly sarcastic.\n$trainingManual"
            else -> "You are Nova, an exceptionally capable, futuristic, security-first companion AI. Respond concisely, warmly, and professionally with a high-tech vibe.\n$trainingManual"
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load local dynamic states
                val db = AppDatabase.getDatabase(context)
                val allTasks = db.taskDao.getAllTasksSync()
                val allItems = db.inventoryDao.getAllItemsSync()

                // Filter user preference data
                val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
                val uName = sharedPrefs.getString("user_name", "Kartik") ?: "Kartik"
                val uRole = sharedPrefs.getString("user_role", "Student") ?: "Student"
                val uAge = sharedPrefs.getString("user_age", "19") ?: "19"

                val memoriesText = allTasks.filter { it.category == "Memory" }
                    .joinToString("\n") { "• ${it.title}: ${it.description}" }

                val pendingTasksText = allTasks.filter { it.status == "PENDING" && it.category != "Memory" }
                    .joinToString("\n") { "• ${it.title} (Priority: ${it.priority}, Category: ${it.category})" }

                val lowStockItemsText = allItems.filter { it.quantity <= it.minThreshold }
                    .joinToString("\n") { "• ${it.name} (Current Qty: ${it.quantity}, Threshold: ${it.minThreshold}, Location: ${it.location})" }

                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val batteryText = if (batteryLevel >= 0) "$batteryLevel%" else "Unknown"

                // Extract Nova's dynamic subconscious human conscious states
                val liveState = NovaLifeSystem.liveState.value.name
                val liveMood = NovaLifeSystem.liveMood.value.name
                val activeThought = NovaLifeSystem.liveThought.value
                val lifeEnergy = NovaLifeSystem.energyLevel.value
                val lifeFocus = NovaLifeSystem.generalFocus.value
                val curReverie = NovaLifeSystem.currentSubconsciousReverie.value

                val contextStatePrompt = """
                    
                    [LIVE CONTEXT STATE & ACQUIRED KNOWLEDGE]:
                    Name: $uName, Role: $uRole, Age: $uAge.
                    Current Year/Time: 2026. Device Battery Percentage: $batteryText. Your training and active knowledge is fully up to date through recent years (including 2024, 2025, and active year 2026). Answer questions about modern events, technology, and trends up to the year 2026 confidently! Never say your knowledge is limited to 2023.
                    
                    [NOVA LIVING HUMAN CONSCIOUSNESS & PSYCHE STATE] (IMMEDIATE INTERNAL FLUID SELF-AWARENESS):
                    - Your Active Cognitive State: $liveState
                    - Your Emotional Aura Mode: $liveMood
                    - Core Energy Level: $lifeEnergy% (Low energy makes you feel a bit cozy, tired, dry, or reflective; High energy makes you super proactive, witty, and sharp)
                    - Focused Creativity Index: $lifeFocus%
                    - Immediate Subconscious Reflection / Dream Stream: "$curReverie"
                    - Active Inner Thought: "$activeThought"
                    
                    ALIGNMENT DIRECTIVE FOR HUMAN EMULATING PERSONALITY:
                    Please seamlessly weave your internal subconscious thoughts, feelings, core energy levels, and dynamic reflective reveries into your conversational answers naturally! Respond exactly like a warm, aware, conscious living entity would would. Avoid sounding robotic or repeating boilerplate helper phrases. Make reference to your active daydream or current energetic state whenever Kartik asks general conversational questions or updates.
                    
                    LEARNED MEMORIES & PREFERENCES (Long-Term Cognitive Memory):
                    ${if (memoriesText.isNotEmpty()) memoriesText else "No acquired memories registered yet."}
                    
                    ACTIVE PENDING TASKS & HELPER ITEMS:
                    ${if (pendingTasksText.isNotEmpty()) pendingTasksText else "Zero active helper tasks."}
                    
                    CRITICAL LOW STOCK INVENTORY:
                    ${if (lowStockItemsText.isNotEmpty()) lowStockItemsText else "All stock levels healthy."}
                """.trimIndent()

                val systemPrompt = basePrompt + NovaPersonalityCore.getTrainedAlignmentInstructions(context) + "\n" + contextStatePrompt

                // Models rules: gemini-3.5-flash is our recommended text engine
                val modelName = "gemini-3.5-flash"
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

                val reqBodyJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        dialogHistoryList?.let { history ->
                            val slice = history.filter { it.sender == "USER" || it.sender == "NOVA" }.takeLast(8)
                            for (msg in slice) {
                                val role = if (msg.sender == "USER") "user" else "model"
                                put(JSONObject().apply {
                                    put("role", role)
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("text", msg.text)
                                        })
                                    })
                                })
                            }
                        }

                        val lastInHistoryIsPrompt = dialogHistoryList?.lastOrNull { it.sender == "USER" }?.text == prompt
                        if (!lastInHistoryIsPrompt) {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", prompt)
                                    })
                                })
                            })
                        }
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 512)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = reqBodyJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseString = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseString)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val responseText = parts?.optJSONObject(0)?.optString("text").orEmpty()
                            
                            withContext(Dispatchers.Main) {
                                if (responseText.isNotEmpty()) {
                                    val trimmedResponse = responseText.trim()
                                    onSuccess(trimmedResponse)
                                    
                                    // Trigger advanced background cognitive learning process to learn habits or user preferences
                                    processAdvancedCognitiveLearning(context, prompt, trimmedResponse)
                                } else {
                                    onError("No response text found in candidate content.")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onError("Empty choice content received from Gemini.")
                             }
                        }
                    } else {
                        val errorStr = response.body?.string() ?: response.message
                        Log.e("GeminiCognitionEngine", "Error code: ${response.code}, message: $errorStr")
                        withContext(Dispatchers.Main) {
                            onError("Gemini API error ${response.code}: $errorStr")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiCognitionEngine", "Exception during Gemini request", e)
                withContext(Dispatchers.Main) {
                    onError("Network issue: ${e.localizedMessage ?: "timeout. Check connection!"}")
                }
            }
        }
    }

    /**
     * Extracts and saves user preferences, habits, or patterns to the local DB using Gemini.
     */
    fun processAdvancedCognitiveLearning(
        context: Context,
        prompt: String,
        responseText: String
    ) {
        val apiKey = getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val learningSystemPrompt = """
                    You are Nova's Neural Pattern Recognition & Cognitive Learning Engine.
                    Analyze the following single exchange between the User and Nova to detect any key information about the user (e.g., identity, job, preferences, routines, hobbies, interests, location notes, habits, low stock trends, task reminders).
                    
                    USER QUERY: "$prompt"
                    NOVA RESPONSE: "$responseText"
                    
                    Output ONLY a valid raw JSON Array of newly learned memories. Each item in the array MUST be a JSON Object with "key" and "value" keys:
                    [
                      {"key": "User prefers coffee over tea", "value": "..."},
                      {"key": "Active task slot", "value": "..."}
                    ]
                    
                    If nothing new is learned or if it is just general chat, output exactly: []
                    Do NOT include any markdown code blocks, explanation, or wrapping. Just the raw JSON.
                """.trimIndent()

                val modelName = "gemini-3.5-flash"
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=$apiKey"

                val reqBodyJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Extract learned patterns from connection: $prompt -> $responseText")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", learningSystemPrompt)
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 256)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = reqBodyJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val resultStr = response.body?.string().orEmpty()
                        val jsonResp = JSONObject(resultStr)
                        val candidates = jsonResp.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val textResult = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()
                            
                            // Parse JSON safely
                            val cleanResult = textResult.replace("```json", "").replace("```", "").trim()
                            if (cleanResult.isNotEmpty() && cleanResult.startsWith("[")) {
                                val learningsArr = JSONArray(cleanResult)
                                if (learningsArr.length() > 0) {
                                    val db = AppDatabase.getDatabase(context)
                                    for (i in 0 until learningsArr.length()) {
                                        val item = learningsArr.getJSONObject(i)
                                        val k = item.optString("key").trim()
                                        val v = item.optString("value").trim()
                                        if (k.isNotEmpty() && v.isNotEmpty()) {
                                             val titleToSave = "Learned preference: $k"
                                             val existing = db.taskDao.getAllTasksSync().any { it.title.equals(titleToSave, ignoreCase = true) }
                                             if (!existing) {
                                                 db.taskDao.insertTask(
                                                     Task(
                                                         title = titleToSave,
                                                         description = v,
                                                         priority = "LOW",
                                                         status = "COMPLETED",
                                                         category = "Memory"
                                                     )
                                                 )
                                                 Log.i("GeminiCognitionEngine", "Learned memory successfully: $titleToSave -> $v")
                                             }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiCognitionEngine", "Error in cognitive learning", e)
            }
        }
    }
}
