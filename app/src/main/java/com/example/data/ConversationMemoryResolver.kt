package com.example.data

import android.util.Log
import java.util.Locale

object ConversationMemoryResolver {
    data class ConversationContext(
        var lastIntent: String? = null,
        var lastPerson: String? = null,
        var lastTopic: String? = null,
        var lastSearchQuery: String? = null,
        var lastResult: String? = null,
        var lastApp: String? = null,
        var timestamp: Long = 0L
    )

    private var currentContext = ConversationContext()
    private const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000 // 5 minutes inactivity timeout

    private fun logInfo(msg: String) {
        try {
            Log.i("ConversationMemory", msg)
        } catch (e: Throwable) {
            println("[ConversationMemory] $msg")
        }
    }

    fun getContext(): ConversationContext {
        checkExpiry()
        return currentContext
    }

    fun resetContext() {
        currentContext = ConversationContext()
        logInfo("Conversation context explicitly reset.")
    }

    private fun checkExpiry() {
        val now = System.currentTimeMillis()
        if (currentContext.timestamp > 0 && (now - currentContext.timestamp > INACTIVITY_TIMEOUT_MS)) {
            logInfo("Conversation context expired due to inactivity.")
            currentContext = ConversationContext()
        }
    }

    fun updateContext(
        intent: String? = null,
        person: String? = null,
        topic: String? = null,
        searchQuery: String? = null,
        result: String? = null,
        app: String? = null
    ) {
        checkExpiry()
        val now = System.currentTimeMillis()
        currentContext.timestamp = now
        
        if (intent != null) currentContext.lastIntent = intent
        if (person != null) currentContext.lastPerson = person
        if (topic != null) currentContext.lastTopic = topic
        if (searchQuery != null) currentContext.lastSearchQuery = searchQuery
        if (result != null) currentContext.lastResult = result
        if (app != null) currentContext.lastApp = app
        
        logInfo("Updated context: $currentContext")
    }

    /**
     * Determines whether there are reference words in the user query that need resolution.
     */
    fun hasReferenceWords(query: String): Boolean {
        val clean = query.lowercase(Locale.ROOT).trim()
        val words = clean.split(Regex("\\s+"))
        val referenceWords = setOf("him", "her", "them", "it", "this", "that", "again", "same", "refresh", "update", "now", "latest")
        return words.any { referenceWords.contains(it) } || clean == "now" || clean == "update" || clean == "refresh" || clean == "latest"
    }

    sealed class Resolution {
        data class Resolved(val query: String) : Resolution()
        data class Ambiguous(val prompt: String) : Resolution()
    }

    /**
     * Resolves references in a query based on previous conversation context.
     * If confidence is low, returns Resolution.Ambiguous with a helpful user prompt.
     */
    fun resolve(query: String, context: android.content.Context? = null): Resolution {
        checkExpiry()
        val clean = query.lowercase(Locale.ROOT).trim()
        
        // 1. Explicit clean / reset context request
        if (clean == "reset" || clean == "reset context" || clean == "clear memory" || clean == "forget everything") {
            resetContext()
            return Resolution.Resolved(query)
        }

        // 2. Resolve "him", "her", "them" or matching "call him" / "message him" etc.
        val hasPersonRef = clean.contains(Regex("\\bhim\\b")) || clean.contains(Regex("\\bher\\b")) || clean.contains(Regex("\\bthem\\b"))
        if (hasPersonRef) {
            val person = currentContext.lastPerson ?: context?.let {
                val p = it.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE).getString("last_person_contacted", null)
                if (p.isNullOrEmpty()) null else p
            }
            if (person == null) {
                val actionPrompt = if (clean.contains("call") || clean.startsWith("dial")) {
                    "Who would you like to call?"
                } else if (clean.contains("message") || clean.contains("send")) {
                    "Who would you like to message?"
                } else {
                    "Who are you referring to?"
                }
                return Resolution.Ambiguous(actionPrompt)
            } else {
                var resolved = query
                    .replace(Regex("\\bhim\\b", RegexOption.IGNORE_CASE), person)
                    .replace(Regex("\\bher\\b", RegexOption.IGNORE_CASE), person)
                    .replace(Regex("\\bthem\\b", RegexOption.IGNORE_CASE), person)
                
                // Cleanup "again"
                resolved = resolved.replace(Regex("\\bagain\\b", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                return Resolution.Resolved(resolved)
            }
        }

        // 3. Resolve "now", "update", "refresh", "latest" or matching "again", "same"
        val isPureUpdate = clean == "now" || clean == "update" || clean == "refresh" || clean == "latest" || clean == "now?" || clean == "update?" || clean == "refresh?" || clean == "latest?"
        if (isPureUpdate || clean == "again" || clean == "same" || clean == "refresh same" || clean == "update same") {
            val lastTopic = currentContext.lastTopic
            val lastIntent = currentContext.lastIntent
            val lastApp = currentContext.lastApp
            val lastPerson = currentContext.lastPerson
            val lastSearchQuery = currentContext.lastSearchQuery

            if (lastTopic != null) {
                if (lastTopic.lowercase(Locale.ROOT).contains("weather") || lastTopic.lowercase(Locale.ROOT).contains("score") || lastTopic.lowercase(Locale.ROOT).contains("bitcoin") || lastTopic.lowercase(Locale.ROOT).contains("table")) {
                    return Resolution.Resolved("refresh $lastTopic")
                }
                return Resolution.Resolved(lastTopic)
            }
            if (lastSearchQuery != null) {
                return Resolution.Resolved("search $lastSearchQuery")
            }
            if (lastIntent != null) {
                if (lastIntent.startsWith("call") && lastPerson != null) {
                    return Resolution.Resolved("call $lastPerson")
                }
                if (lastIntent.startsWith("open") && lastApp != null) {
                    return Resolution.Resolved("open $lastApp")
                }
            }
            return Resolution.Ambiguous("I don't have enough context to repeat or refresh that. What would you like me to do?")
        }

        // 4. Resolve "it", "this", "that"
        val hasItRef = clean.contains(Regex("\\bit\\b")) || clean.contains(Regex("\\bthis\\b")) || clean.contains(Regex("\\bthat\\b"))
        if (hasItRef) {
            val lastApp = currentContext.lastApp
            val lastTopic = currentContext.lastTopic
            val lastSearchQuery = currentContext.lastSearchQuery

            // Handle "open it" or "close it"
            if (clean.contains("open") || clean.contains("close") || clean.contains("launch") || clean.contains("stop") || clean.contains("quit")) {
                if (lastApp != null) {
                    val op = if (clean.contains("close") || clean.contains("stop") || clean.contains("quit")) "close" else "open"
                    return Resolution.Resolved("$op $lastApp")
                } else {
                    return Resolution.Ambiguous("Which application are you referring to?")
                }
            }

            // Handle "save it" or "save this" or "save that"
            if (clean.startsWith("save it") || clean.startsWith("save this") || clean.startsWith("save that") || clean.contains("save to notes")) {
                val searchOrTopic = lastTopic ?: lastSearchQuery
                if (searchOrTopic != null) {
                    if (searchOrTopic.lowercase(Locale.ROOT).contains("weather")) {
                        val city = searchOrTopic.lowercase(Locale.ROOT).replace("weather", "").replace("search", "").trim()
                        if (city.isNotEmpty()) {
                            return Resolution.Resolved("$city weather save to notes")
                        } else {
                            return Resolution.Resolved("weather save to notes")
                        }
                    } else {
                        return Resolution.Resolved("save note $searchOrTopic")
                    }
                } else {
                    return Resolution.Ambiguous("What would you like me to save?")
                }
            }

            // General "it", "this", "that" resolution using search or topic or app
            val fallback = lastTopic ?: lastSearchQuery ?: lastApp
            if (fallback != null) {
                val resolved = query
                    .replace(Regex("\\bit\\b", RegexOption.IGNORE_CASE), fallback)
                    .replace(Regex("\\bthis\\b", RegexOption.IGNORE_CASE), fallback)
                    .replace(Regex("\\bthat\\b", RegexOption.IGNORE_CASE), fallback)
                    .replace(Regex("\\bagain\\b", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                return Resolution.Resolved(resolved)
            } else {
                return Resolution.Ambiguous("What are you referring to?")
            }
        }

        return Resolution.Resolved(query)
    }

    /**
     * Extracts and updates the context variables automatically based on successfully processed clean commands.
     */
    fun parseAndUpdateContext(clean: String, originalQuery: String) {
        val cleanLower = clean.lowercase(Locale.ROOT).trim()
        
        // 1. Check for CALL
        if (cleanLower.startsWith("call ")) {
            val who = cleanLower.replace("call ", "").trim()
            if (who.isNotEmpty() && !who.contains("it") && !who.contains("him") && !who.contains("her") && !who.contains("them")) {
                val capitalizedWho = who.substring(0, 1).uppercase(Locale.ROOT) + who.substring(1)
                updateContext(
                    intent = "call",
                    person = capitalizedWho,
                    app = "Phone",
                    topic = "Calling $capitalizedWho"
                )
            }
        }
        
        // 2. Check for MESSAGING
        else if (cleanLower.startsWith("message ") || cleanLower.startsWith("whatsapp ") || cleanLower.contains("whatsapp mom") || cleanLower.contains("message bittu")) {
            var who = ""
            if (cleanLower.startsWith("message ")) {
                who = cleanLower.replace("message ", "").substringBefore(" ").trim()
            } else if (cleanLower.startsWith("whatsapp ")) {
                who = cleanLower.replace("whatsapp ", "").substringBefore(" ").trim()
            } else if (cleanLower.contains("whatsapp mom")) {
                who = "mom"
            } else if (cleanLower.contains("message bittu")) {
                who = "bittu"
            }
            if (who.isNotEmpty() && !who.contains("it") && !who.contains("him") && !who.contains("her") && !who.contains("them")) {
                val capitalizedWho = who.substring(0, 1).uppercase(Locale.ROOT) + who.substring(1)
                updateContext(
                    intent = "message",
                    person = capitalizedWho,
                    app = "WhatsApp",
                    topic = "Message to $capitalizedWho"
                )
            }
        }
        
        // 3. Check for OPEN APP
        else if (cleanLower.startsWith("open ") || cleanLower.startsWith("launch ")) {
            val app = cleanLower.replace("open ", "").replace("launch ", "").trim()
            if (app.isNotEmpty() && !app.contains("it") && !app.contains("this") && !app.contains("that")) {
                val capitalizedApp = app.replaceFirstChar { it.uppercase() }
                updateContext(
                    intent = "open",
                    app = capitalizedApp,
                    topic = "Opening $capitalizedApp"
                )
            }
        }
        
        // 4. Check for Specific Apps
        else if (cleanLower.contains("youtube") || cleanLower.contains("yt")) {
            updateContext(app = "YouTube", intent = "open", topic = "YouTube")
        } else if (cleanLower.contains("instagram") || cleanLower.contains("insta")) {
            updateContext(app = "Instagram", intent = "open", topic = "Instagram")
        } else if (cleanLower.contains("chrome") || cleanLower.contains("browser")) {
            updateContext(app = "Chrome", intent = "open", topic = "Chrome")
        } else if (cleanLower.contains("calculator") || cleanLower.contains("calc")) {
            updateContext(app = "Calculator", intent = "open", topic = "Calculator")
        } else if (cleanLower.contains("maps") || cleanLower.contains("navigation")) {
            updateContext(app = "Maps", intent = "open", topic = "Maps")
        }
        
        // 5. Check for LIVE INFORMATION Topics
        if (cleanLower.contains("weather") || cleanLower.contains("forecast") || cleanLower.contains("temperature") || cleanLower.contains("rain") || cleanLower.contains("climate")) {
            var location = "weather"
            if (cleanLower.contains("weather")) {
                val locPart = cleanLower.replace("weather", "").replace("search", "").replace("today", "").trim()
                if (locPart.isNotEmpty()) {
                    location = "$locPart weather"
                }
            }
            updateContext(
                intent = "weather",
                topic = location,
                searchQuery = originalQuery
            )
        } else if (cleanLower.contains("score") || cleanLower.contains("cricket")) {
            updateContext(
                intent = "live_info",
                topic = "cricket score",
                searchQuery = originalQuery
            )
        } else if (cleanLower.contains("bitcoin") || cleanLower.contains("btc")) {
            updateContext(
                intent = "live_info",
                topic = "bitcoin price",
                searchQuery = originalQuery
            )
        } else if (cleanLower.contains("ipl table") || cleanLower.contains("points table") || cleanLower.contains("standings")) {
            updateContext(
                intent = "live_info",
                topic = "IPL points table",
                searchQuery = originalQuery
            )
        }
        
        // 6. Reminders and Notes
        else if (cleanLower.contains("remind") || cleanLower.contains("reminder")) {
            updateContext(
                intent = "remind",
                topic = "reminder"
            )
        } else if (cleanLower.contains("note") || cleanLower.contains("notepad") || cleanLower.contains("save") || cleanLower.contains("keep")) {
            updateContext(
                intent = "notes",
                topic = cleanLower.replace("save", "").replace("note", "").trim()
            )
        }
    }
}
