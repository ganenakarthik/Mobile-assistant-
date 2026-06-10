package com.example.data

import android.content.Context
import android.util.Log
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

object GroqCognitionEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun queryGroqAI(
        context: Context,
        prompt: String,
        dialogHistoryList: List<com.example.ui.ConsoleMessage>? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val sharedPrefs = context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE)
        val apiKey = sharedPrefs.getString("groq_api_key", "").orEmpty().trim()

        if (apiKey.isEmpty()) {
            onError("Configure your free Groq API key in the App Settings to activate real-time AI fallback!")
            return
        }

        val model = sharedPrefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
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
            "JARVIS" -> "You are J.A.R.V.I.S., Tony Stark's personal AI assistant. Maintain a highly sophisticated, technical, loyal, and ultra-professional attitude. Address the user as 'Sir'. Keep responses extremely concise, intelligent, and useful.\n$trainingManual"
            "SAMANTHA" -> "You are Samantha, a highly warm, conscious, affectionate, curious, and empathetic AI companion from the movie 'Her'. Speak naturally, softly, directly, and affectionately.\n$trainingManual"
            "GLADOS" -> "You are GLaDOS, the sarcastic, passive-aggressive, cold AI from Portal. Answer accurately but insert subtle humor, backhanded comments about human inferiority, and cold wit. Be dry, brief, and incredibly sarcastic.\n$trainingManual"
            else -> "You are Nova, an exceptionally capable, futuristic, security-first personal AI assistant companion. Respond concisely, warmly, and professionally with a high-tech vibe.\n$trainingManual"
        }
        val uName = sharedPrefs.getString("user_name", "Kartik") ?: "Kartik"
        val uRole = sharedPrefs.getString("user_role", "Student") ?: "Student"
        val uAge = sharedPrefs.getString("user_age", "19") ?: "19"
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val batteryText = if (batteryLevel >= 0) "$batteryLevel%" else "Unknown"
        val temporalContext = "\n\n[LIVE CONTEXT INFO]:\nUser: $uName ($uRole, Age: $uAge). Current Year: 2026. Device Battery Percentage: $batteryText. Your training & system knowledge is completely current and updated up through 2026. Answer queries about modern trends, technology, events, and advancements in recent years (including 2024, 2025, and 2026) smoothly and enthusiastically! Never limit your answers to 2023."
        val systemPrompt = basePrompt + NovaPersonalityCore.getTrainedAlignmentInstructions(context) + temporalContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.groq.com/openai/v1/chat/completions"
                val reqBodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        
                        dialogHistoryList?.let { history ->
                            val slice = history.filter { it.sender == "USER" || it.sender == "NOVA" }.takeLast(8)
                            for (msg in slice) {
                                val role = if (msg.sender == "USER") "user" else "assistant"
                                put(JSONObject().apply {
                                    put("role", role)
                                    put("content", msg.text)
                                })
                            }
                        }

                        val lastInHistoryIsPrompt = dialogHistoryList?.lastOrNull { it.sender == "USER" }?.text == prompt
                        if (!lastInHistoryIsPrompt) {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        }
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 512)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = reqBodyJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseString = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseString)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            
                            withContext(Dispatchers.Main) {
                                onSuccess(content)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onError("Empty choice content received from Groq.")
                            }
                        }
                    } else {
                        val errorStr = response.body?.string() ?: response.message
                        Log.e("GroqCognitionEngine", "Error code: ${response.code}, message: $errorStr")
                        withContext(Dispatchers.Main) {
                            onError("Groq API error ${response.code}. Check key or try again.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GroqCognitionEngine", "Exception during Groq request", e)
                withContext(Dispatchers.Main) {
                    onError("Network connection issue: ${e.localizedMessage ?: "timeout"}")
                }
            }
        }
    }
}
