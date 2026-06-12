package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object LiveInformationEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Executes real browser/search requests for real-time information queries, ensuring we never hallucinate.
     */
    fun queryLiveInformation(
        query: String,
        context: Context,
        onResult: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val clean = query.lowercase(Locale.ROOT).trim()
            try {
                // 1. Core Weather Route
                if (clean.contains("weather") || clean.contains("forecast") || clean.contains("temperature") || clean.contains("rain") || clean.contains("climate") || clean.contains("degree")) {
                    WeatherIntegrationEngine.fetchWeather(
                        context = context,
                        query = query,
                        onResult = { resultText, telemetry ->
                            // Ensure we don't return San Francisco by default if geocoding actually failed or was invalid
                            val city = telemetry?.get("city") as? String
                            val actualQueryHadCity = query.lowercase().replace("weather", "").replace("today", "").trim().isNotEmpty()
                            
                            if (actualQueryHadCity && (city == null || city.contains("San Francisco") && !query.lowercase().contains("francisco"))) {
                                // City/Geocoding failed for the actual requested city
                                onResult("I couldn't retrieve current information for that location.")
                            } else {
                                onResult(resultText)
                            }
                        },
                        onError = { error ->
                            Log.e("LiveInformationEngine", "Weather retrieval failed: $error")
                            onResult("I couldn't retrieve current information.")
                        }
                    )
                    return@launch
                }

                // 2. Core Bitcoin/Crypto Route
                if (clean.contains("bitcoin") || clean.contains("btc")) {
                    val priceUrl = "https://api.coindesk.com/v1/bpi/currentprice.json"
                    val request = Request.Builder().url(priceUrl).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string().orEmpty()
                            val json = JSONObject(bodyStr)
                            val bpi = json.optJSONObject("bpi")
                            val usd = bpi?.optJSONObject("USD")
                            val rate = usd?.optString("rate")
                            if (rate != null) {
                                val reply = "According to real-time crypto exchanges, the live price of Bitcoin is $$rate USD."
                                withContext(Dispatchers.Main) { onResult(reply) }
                                return@launch
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { onResult("I couldn't retrieve current information.") }
                    return@launch
                }

                // 3. Core Search Feed and Real-Time Live Cricket/Sports Scores
                if (clean.contains("cricket") || clean.contains("score") || clean.contains("match") || clean.contains("ipl") || clean.contains("runs") || clean.contains("wickets") || clean.contains("another") || clean.contains("not that") || clean.contains("next")) {
                    // Pull actual live matches directly from ESPNCricinfo RSS first to guarantee fresh scores
                    val cricinfoUrl = "https://www.espncricinfo.com/rss/livescores.xml"
                    val rssRequest = Request.Builder()
                        .url(cricinfoUrl)
                        .header("User-Agent", "Mozilla/5.0 NovaAssistant_LiveScores")
                        .build()
                    var liveScoresOutput = ""
                    try {
                        client.newCall(rssRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val xml = response.body?.string().orEmpty()
                                val items = mutableListOf<String>()
                                val matches = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                                for (m in matches) {
                                    val itemXml = m.groupValues[1]
                                    val titleMatch = Regex("<title>(.*?)</title>").find(itemXml)
                                    val descMatch = Regex("<description>(.*?)</description>").find(itemXml)
                                    if (titleMatch != null) {
                                        val title = titleMatch.groupValues[1]
                                            .replace("&amp;", "&")
                                            .replace("&#39;", "'")
                                            .replace("&quot;", "\"")
                                            .replace("<![CDATA[", "")
                                            .replace("]]>", "")
                                            .trim()
                                        val desc = descMatch?.groupValues?.get(1)
                                            ?.replace("&amp;", "&")
                                            ?.replace("&#39;", "'")
                                            ?.replace("&quot;", "\"")
                                            ?.replace("<![CDATA[", "")
                                            ?.replace("]]>", "")
                                            ?.trim() ?: ""
                                        
                                        val statusRow = if (desc.isNotEmpty()) "   Current Status: $desc" else "   Status: Match in progress..."
                                        items.add("🏏 $title\n$statusRow")
                                        if (items.size >= 5) break
                                    }
                                }
                                if (items.isNotEmpty()) {
                                    liveScoresOutput = "According to real-time ESPNCricinfo live matches:\n\n" + items.joinToString("\n\n")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LiveInformationEngine", "ESPNCricinfo live scores retrieval failed, falling back to Google News IN", e)
                    }

                    // Augment or fallback with regionalized Google News search targeted for live sports
                    val (hl, gl, ceid) = if (clean.contains("cricket") || clean.contains("ipl")) {
                        Triple("en-IN", "IN", "IN:en")
                    } else if (clean.contains("premier league") || clean.contains("soccer") || clean.contains("football") || clean.contains("chelsea") || clean.contains("arsenal") || clean.contains("manchester")) {
                        Triple("en-GB", "GB", "GB:en")
                    } else {
                        Triple("en-US", "US", "US:en")
                    }

                    val searchTopic = when {
                        clean.contains("ipl points") || clean.contains("ipl table") || clean.contains("points table") -> "IPL points table standings"
                        clean.contains("score") || clean.contains("cricket") || clean.contains("another") || clean.contains("not that") || clean.contains("next") -> "live cricket score matches"
                        clean.contains("who won yesterday") || clean.contains("won yesterday") -> "yesterday cricket match results winner"
                        clean.contains("stock price") || clean.contains("share price") -> "$clean stock price live market news"
                        else -> query
                    }

                    val encodedTopic = URLEncoder.encode(searchTopic, "UTF-8")
                    val rssUrl = "https://news.google.com/rss/search?q=$encodedTopic&hl=$hl&gl=$gl&ceid=$ceid"
                    val request = Request.Builder()
                        .url(rssUrl)
                        .header("User-Agent", "Mozilla/5.0 NovaAssistant_BrowserSearch")
                        .build()

                    val newsItems = mutableListOf<String>()
                    val introText = when {
                        clean.contains("ipl points") || clean.contains("ipl table") || clean.contains("points table") -> "Retrieving live IPL standings & points:"
                        clean.contains("who won yesterday") || clean.contains("won yesterday") -> "Checking sports results for yesterday's matches:"
                        else -> "Recent dynamic headlines and match analysis:"
                    }

                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val xml = response.body?.string().orEmpty()
                                val matches = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                                for (m in matches) {
                                    val itemXml = m.groupValues[1]
                                    val titleMatch = Regex("<title>(.*?)</title>").find(itemXml)
                                    if (titleMatch != null) {
                                        val cTitle = titleMatch.groupValues[1]
                                            .replace("&amp;", "&")
                                            .replace("&#39;", "'")
                                            .replace("&quot;", "\"")
                                            .replace("<![CDATA[", "")
                                            .replace("]]>", "")
                                            .trim()
                                        newsItems.add("- $cTitle")
                                        if (newsItems.size >= 4) break
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LiveInformationEngine", "Google News regional search failed", e)
                    }

                    val finalOutput = when {
                        liveScoresOutput.isNotEmpty() && newsItems.isNotEmpty() -> 
                            "$liveScoresOutput\n\n📢 $introText\n" + newsItems.joinToString("\n")
                        liveScoresOutput.isNotEmpty() -> liveScoresOutput
                        newsItems.isNotEmpty() -> "According to regional live browser search updates:\n\n📢 $introText\n" + newsItems.joinToString("\n")
                        else -> "I couldn't retrieve current information."
                    }

                    withContext(Dispatchers.Main) { onResult(finalOutput) }
                    return@launch
                }

                // 4. Default Search Feed Route for other topics (US-centered)
                val searchTopic = query
                val encodedTopic = URLEncoder.encode(searchTopic, "UTF-8")
                val rssUrl = "https://news.google.com/rss/search?q=$encodedTopic&hl=en-US&gl=US&ceid=US:en"
                val request = Request.Builder()
                    .url(rssUrl)
                    .header("User-Agent", "Mozilla/5.0 NovaAssistant_BrowserSearch")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val xmlResponse = response.body?.string().orEmpty()
                        val items = mutableListOf<String>()
                        val itemMatches = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xmlResponse)
                        
                        for (m in itemMatches) {
                            val itemXml = m.groupValues[1]
                            val titleMatch = Regex("<title>(.*?)</title>").find(itemXml)
                            val pubDateMatch = Regex("<pubDate>(.*?)</pubDate>").find(itemXml)
                            if (titleMatch != null) {
                                val cleanTitle = titleMatch.groupValues[1]
                                    .replace("&amp;", "&")
                                    .replace("&#39;", "'")
                                    .replace("&quot;", "\"")
                                    .replace("<![CDATA[", "")
                                    .replace("]]>", "")
                                    .trim()
                                val rawDate = pubDateMatch?.groupValues[1].orEmpty()
                                val cleanDate = if (rawDate.length > 16) rawDate.substring(0, 16) else rawDate
                                items.add("- $cleanTitle (${cleanDate})")
                                if (items.size >= 4) break
                            }
                        }

                        if (items.isNotEmpty()) {
                            val intro = "According to live browser search updates on '$query':"
                            val output = "$intro\n\n" + items.joinToString("\n")
                            withContext(Dispatchers.Main) { onResult(output) }
                        } else {
                            withContext(Dispatchers.Main) { onResult("I couldn't retrieve current information.") }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onResult("I couldn't retrieve current information.") }
                    }
                }

            } catch (e: Exception) {
                Log.e("LiveInformationEngine", "Error querying live information", e)
                withContext(Dispatchers.Main) { onResult("I couldn't retrieve current information.") }
            }
        }
    }
}
