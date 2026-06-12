package com.example.data

import android.content.Context
import android.util.Log
import com.example.ui.ConsoleMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

object IntentClassifier {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    enum class Category {
        LIVE_INFORMATION,
        DEVICE_ACTION,
        COMMUNICATION,
        MEMORY,
        GENERAL_CHAT
    }

    /**
     * Determines the category of a query using structured rules first (fast & reliable offline),
     * and queries Gemini for classification if Gemini is available to handle complex linguistic structures.
     */
    suspend fun classify(query: String, context: Context): Category = withContext(Dispatchers.IO) {
        val clean = query.lowercase(Locale.ROOT).trim()

        // 1. Try Deterministic Rules First
        val ruleBasedCategory = classifyRuleBased(clean)
        
        // If the query perfectly matches live info keywords or specific rules, output it instantly
        if (ruleBasedCategory != null) {
            Log.i("IntentClassifier", "Rule-based classifier selected match: $ruleBasedCategory")
            return@withContext ruleBasedCategory
        }

        // 2. Fallback / Augment with Gemini if API is available and enabled
        if (GeminiCognitionEngine.isGeminiAvailable(context)) {
            try {
                val apiKey = GeminiCognitionEngine.getApiKey(context)
                val systemPrompt = """
                    You are the first-stage intent classifier for Nova, a smart assistant.
                    Your task is to classify the user's natural language command into exactly one of these five categories:
                    - LIVE_INFORMATION
                    - DEVICE_ACTION
                    - COMMUNICATION
                    - MEMORY
                    - GENERAL_CHAT

                    Definition/Examples for each:
                    - LIVE_INFORMATION: Real-time/perishable queries. Examples: "weather today", "score", "bitcoin", "IPL table", "news", "stock price", "who won yesterday", "what time is it in London", "tomorrow's weather".
                    - DEVICE_ACTION: Controls device features or launch apps. Examples: "call Nazeer" (placing a hardware call), "open Chrome", "turn on Bluetooth", "take a screenshot", "open Maps", "launch Calculator", "mute phone".
                    - COMMUNICATION: Social messaging and communication. Examples: "message Bittu", "WhatsApp Mom", "send text to Dad saying I'm late", "send sms".
                    - MEMORY: Storing notes, preferences, or recalling thoughts. Examples: "remember this", "save note ...", "brain dump", "what did I tell you", "what are my notes", "remember my name is Bob".
                    - GENERAL_CHAT: Conversational chatter, facts, questions, general explanations. Examples: "explain AI", "what is Java", "who is Einstein", "tell me a joke", "how does a refrigerator work", "hello", "how are you".

                    CRITICAL CLASSIFICATION RULE:
                    If the input requests dynamic real-time current status, sports scores, market prices, standings, news, or weather, classify as LIVE_INFORMATION.
                    Never output any markdown, explanations, or punctuation. Answer with exactly one of the five Category strings above.
                """.trimIndent()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                val reqBodyJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", query)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = reqBodyJson.toString().toRequestBody(mediaType)
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string().orEmpty()
                        val candidates = JSONObject(bodyStr).optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val text = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.getJSONObject(0)
                                ?.optString("text")
                                .orEmpty().trim().uppercase(Locale.ROOT)

                            Log.i("IntentClassifier", "Gemini classified intent response: '$text'")
                            for (cat in Category.values()) {
                                if (text.contains(cat.name)) {
                                    return@withContext cat
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IntentClassifier", "Gemini intent classification fallback exception", e)
            }
        }

        // 3. Fallback to general rules again, defaults to GENERAL_CHAT if all else fails
        return@withContext ruleBasedCategory ?: Category.GENERAL_CHAT
    }

    fun classifyRuleBased(clean: String): Category? {
        // --- 1. LIVE INFORMATION MATCHERS ---
        val liveKeywords = listOf(
            "weather", "forecast", "temperature", "temp", "rain", "rainy", "sunny", "cloudy", "degree",
            "bitcoin", "btc", "crypto", "ether", "ethereum", "solana", "dogecoin", "shiba", "coinbase",
            "points table", "ipl table", "ipl points", "standings", "score", "scores", "cricket", "match", "matches",
            "wickets", "runs", "ipl", "premier league", "who won yesterday", "yesterday's match", "yesterday won",
            "stock price", "share price", "nasdaq", "dow jones", "market price", "current price", "live price", "ticker",
            "news", "headline", "headlines", "yesterday"
        )
        if (liveKeywords.any { clean.contains(it) }) {
            // Check if it's a "weather to notes" command which belongs in memory/notes pipeline
            if (clean.contains("weather") && (clean.contains("note") || clean.contains("notepad") || clean.contains("save") || clean.contains("keep"))) {
                return Category.MEMORY
            }
            return Category.LIVE_INFORMATION
        }

        // --- 2. MEMORY AND CONTEXT MATCHERS ---
        val memoryKeywords = listOf(
            "remember", "save note", "notepad", "brain dump", "keep note", "what did i tell you", 
            "my preference", "recall", "about myself", "my notes", "read notes", "show notes", "what are my notes",
            "get notes", "read my notes"
        )
        if (memoryKeywords.any { clean.contains(it) }) {
            return Category.MEMORY
        }

        // --- 3. COMMUNICATION MATCHERS ---
        val commKeywords = listOf(
            "message", "whatsapp", "sms", "text to", "send text", "telegram", "call nazeer", "call mom", "call dad", "whatsapp mom"
        )
        if (commKeywords.any { clean.contains(it) }) {
            return Category.COMMUNICATION
        }
        if (clean.startsWith("call ") && !clean.contains("chrome") && !clean.contains("browser") && !clean.contains("calculator")) {
            return Category.COMMUNICATION
        }

        // --- 4. DEVICE ACTION MATCHERS ---
        val deviceKeywords = listOf(
            "open chrome", "open browser", "open calculator", "open maps", "open youtube", "open settings",
            "turn on bluetooth", "turn off bluetooth", "turn on wifi", "turn off wifi", "enable bluetooth", "disable bluetooth",
            "take screenshot", "take a screenshot", "screen capture", "turn on flashlight", "turn off flashlight"
        )
        if (deviceKeywords.any { clean.contains(it) } || 
            clean.startsWith("open ") || clean.startsWith("launch ") || clean.startsWith("run ") ||
            clean.startsWith("navigate") || clean.contains("directions to") || clean.startsWith("directions")
        ) {
            return Category.DEVICE_ACTION
        }

        // --- 5. CHAT MATCHERS ---
        val chatKeywords = listOf(
            "hello", "hi", "hey", "greetings", "joke", "story", "explain", "what is", "how do", "why is", "who is", "what are"
        )
        if (chatKeywords.any { clean.contains(it) }) {
            return Category.GENERAL_CHAT
        }

        return null
    }
}
