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
import java.util.concurrent.TimeUnit

object WeatherIntegrationEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchWeather(
        context: Context,
        query: String, // e.g., "weather in Tokyo" or "check weather"
        onResult: (String, Map<String, Any>?) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Extract city or detect via IP
                val cleanQuery = query.lowercase().trim()
                var cityName = ""
                
                val indicators = listOf("weather in", "weather for", "forecast in", "forecast for", "temperature in", "temperature for")
                for (indicator in indicators) {
                    if (cleanQuery.contains(indicator)) {
                        val parts = cleanQuery.split(indicator)
                        if (parts.size > 1) {
                            cityName = parts[1].replace("?", "").replace(".", "").trim()
                            break
                        }
                    }
                }

                if (cityName.isEmpty() && cleanQuery.contains("weather")) {
                    val parts = cleanQuery.split("weather")
                    val leftPart = parts[0].replace(Regex("\\b(search|check|for|the|get|show|what is|whats)\\b"), "").replace("?", "").replace(".", "").trim()
                    if (leftPart.isNotEmpty()) {
                        cityName = leftPart
                    } else if (parts.size > 1) {
                        val rightPart = parts[1].replace(Regex("\\b(and|save|to|notes|notepad|it|for|in|from)\\b"), "").replace("?", "").replace(".", "").trim()
                        if (rightPart.isNotEmpty()) {
                            cityName = rightPart
                        }
                    }
                }

                var lat = 37.7749 // Default SF
                var lon = -122.4194
                var finalCityLabel = "San Francisco"

                if (cityName.isNotEmpty()) {
                    // Geocode city using Open-Meteo's free geocoding API
                    try {
                        val encodedCity = URLEncoder.encode(cityName, "UTF-8")
                        val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
                        val geoRequest = Request.Builder().url(geocodeUrl).get().build()
                        client.newCall(geoRequest).execute().use { geoResponse ->
                            if (geoResponse.isSuccessful) {
                                val bodyStr = geoResponse.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                if (json.has("results")) {
                                    val results = json.getJSONArray("results")
                                    if (results.length() > 0) {
                                        val first = results.getJSONObject(0)
                                        lat = first.getDouble("latitude")
                                        lon = first.getDouble("longitude")
                                        finalCityLabel = first.optString("name", cityName)
                                        val country = first.optString("country", "")
                                        if (country.isNotEmpty()) {
                                            finalCityLabel += ", $country"
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WeatherEngine", "Geocoding failed for $cityName, falling back to IP", e)
                    }
                } else {
                    // Detect via free IP-API
                    try {
                        val ipApiUrl = "http://ip-api.com/json/"
                        val ipRequest = Request.Builder().url(ipApiUrl).get().build()
                        client.newCall(ipRequest).execute().use { ipRes ->
                            if (ipRes.isSuccessful) {
                                val bodyStr = ipRes.body?.string() ?: ""
                                val json = JSONObject(bodyStr)
                                if (json.optString("status", "") == "success") {
                                    lat = json.getDouble("lat")
                                    lon = json.getDouble("lon")
                                    finalCityLabel = json.optString("city", "Your Location")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WeatherEngine", "IP Geo-location fallback failed", e)
                    }
                }

                // Step 2: Fetch weather from Open-Meteo (100% Free, Key-Free)
                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                val weatherRequest = Request.Builder().url(weatherUrl).get().build()

                client.newCall(weatherRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        if (json.has("current")) {
                            val currentObj = json.getJSONObject("current")
                            val temp = currentObj.getDouble("temperature_2m")
                            val humidity = currentObj.getDouble("relative_humidity_2m")
                            val wind = currentObj.getDouble("wind_speed_10m")
                            val weatherCode = currentObj.getInt("weather_code")

                            val condition = getWeatherConditionByCode(weatherCode)
                            val readableOutput = """
                                Weather Profile for $finalCityLabel:
                                - Climate Condition: $condition
                                - Active Temperature: ${temp}°C
                                - Humidification Metric: $humidity%
                                - Velocity Current of Winds: $wind km/h
                            """.trimIndent()

                            val telemetry = mapOf(
                                "city" to finalCityLabel,
                                "temp" to temp,
                                "humidity" to humidity,
                                "wind" to wind,
                                "condition" to condition,
                                "lat" to lat,
                                "lon" to lon
                            )

                            withContext(Dispatchers.Main) {
                                onResult(readableOutput, telemetry)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onError("Malformed weather telemetric payload.")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onError("Free weather network gateway reported offline (${response.code}).")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherEngine", "Error fetching weather", e)
                withContext(Dispatchers.Main) {
                    onError("Atmospheric telemetry error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun getWeatherConditionByCode(code: Int): String {
        return when (code) {
            0 -> "Clear skies (optimal visual scan range)"
            1, 2, 3 -> "Partly Cloudy sequences active"
            45, 48 -> "Visual field foggy/dense fog advisory"
            51, 53, 55 -> "Light atmospheric drizzle"
            61, 63, 65 -> "Precipitation stream: Rain showers active"
            71, 73, 75 -> "Crystalline ice deposit precipitation: Snow active"
            80, 81, 82 -> "Flash rainfall clusters cascading"
            95, 96, 99 -> "Severe electrical thunder and storm fronts"
            else -> "Atmospheric haze coordinates normal"
        }
    }
}
