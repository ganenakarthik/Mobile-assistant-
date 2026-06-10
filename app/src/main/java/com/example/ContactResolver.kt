package com.example

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

data class ContactCandidate(
    val name: String,
    val phoneNumber: String,
    val score: Float,
    val source: String // "System" or "Simulated"
)

object ContactResolver {

    // Default simulated contact database for testing fallbacks or offline simulations
    val simulatedContacts = listOf(
        Pair("Nazeer ❤️", "919876543210"),
        Pair("Bittu 😎", "918765432109"),
        Pair("Mom 🥰", "917654321098"),
        Pair("Dad 👑", "916543210987"),
        Pair("Rahul Sharma", "919988776655"),
        Pair("Priya Kapoor", "918877665544"),
        Pair("Amit Patel", "917766554433"),
        Pair("Anjali Mehta", "916655443322")
    )

    // Normalizes name by lowering case and stripping common emojis & symbols
    fun sanitizeName(name: String): String {
        val sb = StringBuilder()
        for (ch in name.lowercase(Locale.ROOT)) {
            if (ch.isLetterOrDigit() || ch == ' ') {
                sb.append(ch)
            }
        }
        return sb.toString().trim().replace("\\s+".toRegex(), " ")
    }

    // Parses command into matching contact name and message body based on patterns:
    // Pattern 1: send {message} to {contact}
    // Pattern 2: message {contact} {message}
    // Pattern 3: send {contact} {message}
    fun parseMessageAndContact(command: String): Pair<String, String> {
        val cmd = command.trim()
        val clean = cmd.lowercase(Locale.ROOT)
        
        val knownContactKeywords = listOf("nazeer", "bittu", "mom", "dad", "rahul", "priya", "amit", "anjali")
        
        // Let's normalize the prefix first by stripping off common command action keywords
        val prefixRegex = Regex("^(send whatsapp notification to|send whatsapp message to|send whatsapp to|send sms to|send message to|send text to|message to|msg to|text to|tell to|send to|send whatsapp|send sms|send message|send text|message|msg|text|tell|send)\\s+", RegexOption.IGNORE_CASE)

        // 0. Handle Pattern 1: "send {message} to {contact}" (by splitting on the last " to " if it separates a message from a known or potential contact)
        if (clean.contains(" to ")) {
            val toIdx = clean.lastIndexOf(" to ")
            if (toIdx != -1) {
                val possibleContact = cmd.substring(toIdx + 4).trim()
                val beforeTo = cmd.substring(0, toIdx).trim()
                val msgPart = beforeTo.replace(prefixRegex, "").trim()
                if (possibleContact.isNotEmpty() && msgPart.isNotEmpty()) {
                    return Pair(possibleContact, cleanMessageBody(msgPart))
                }
            }
        }

        var stripped = cmd.replace(prefixRegex, "").trim()
        var strippedClean = stripped.lowercase(Locale.ROOT)
        
        // If it still starts with "to ", strip it
        if (strippedClean.startsWith("to ")) {
            stripped = stripped.substring(3).trim()
            strippedClean = stripped.lowercase(Locale.ROOT)
        }
        
        // 1. Try matching with known contacts first
        for (contact in knownContactKeywords) {
            if (strippedClean.startsWith(contact)) {
                val contactLen = contact.length
                val contactPart = stripped.substring(0, contactLen).trim()
                val msgPart = stripped.substring(contactLen).trim()
                // Strip leading spacer or punctuation from message like "saying ", "that ", ":", ","
                val msgClean = cleanMessageBody(msgPart)
                return Pair(contactPart, msgClean)
            }
        }
        
        // 2. Fallback to space-split if we don't start with a known contact
        val spaceIdx = stripped.indexOf(' ')
        if (spaceIdx != -1) {
            val contactPart = stripped.substring(0, spaceIdx).trim()
            val msgPart = stripped.substring(spaceIdx + 1).trim()
            return Pair(contactPart, cleanMessageBody(msgPart))
        }
        
        // 3. Fallback to universal substring extract
        var contactPart = "Rahul"
        var msgPart = "hi"
        
        if (clean.contains(" saying ")) {
            val lastIdx = clean.indexOf(" saying ")
            msgPart = cmd.substring(lastIdx + 8).trim()
            val before = cmd.substring(0, lastIdx).trim()
            contactPart = before.replace(prefixRegex, "").trim()
            if (contactPart.lowercase(Locale.ROOT).startsWith("to ")) {
                contactPart = contactPart.substring(3).trim()
            }
        } else {
            for (contact in knownContactKeywords) {
                if (clean.contains(contact)) {
                    contactPart = contact
                    val contactIdx = clean.indexOf(contact)
                    val afterContact = cmd.substring(contactIdx + contact.length).trim()
                    if (afterContact.isNotEmpty()) {
                        msgPart = afterContact
                    }
                    break
                }
            }
        }
        
        return Pair(contactPart, cleanMessageBody(msgPart))
    }

    private fun cleanMessageBody(msg: String): String {
        var clean = msg.trim()
        val prefixesToStrip = listOf("saying", "that", "message", "contents", "body", "txt")
        for (prefix in prefixesToStrip) {
            val lower = clean.lowercase(Locale.ROOT)
            if (lower.startsWith("$prefix ")) {
                clean = clean.substring(prefix.length + 1).trim()
            } else if (lower.startsWith("$prefix:")) {
                clean = clean.substring(prefix.length + 1).trim()
            }
        }
        // strip leading colons, commas, dashes
        while (clean.startsWith(":") || clean.startsWith(",") || clean.startsWith("-") || clean.startsWith(" ")) {
            clean = clean.substring(1).trim()
        }
        return clean
    }

    // Resolves simple common nicknames to full name targets
    fun resolveNickname(search: String): String {
        val sanitized = search.lowercase(Locale.ROOT).trim()
        val nicknameMap = mapOf(
            "mom" to "mom",
            "mother" to "mom",
            "mummy" to "mom",
            "dad" to "dad",
            "father" to "dad",
            "papa" to "dad",
            "nazeer" to "nazeer",
            "bittu" to "bittu",
            "rahul" to "rahul",
            "priya" to "priya",
            "amit" to "amit"
        )
        return nicknameMap[sanitized] ?: sanitized
    }

    // Levenshtein Distance calculation for fuzzy matching spelling mistakes
    fun getLevenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) {
            dp[i][0] = i
        }
        for (j in 0..len2) {
            dp[0][j] = j
        }

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[len1][len2]
    }

    // Resolves potential contacts based on query, looking in device contacts and simulated contacts list
    fun resolveContact(context: Context, query: String): List<ContactCandidate> {
        val resolvedSearch = resolveNickname(sanitizeName(query))
        
        val candidates = mutableListOf<ContactCandidate>()
        
        // 1. Fetch system contacts if permission is active
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val cursor = resolver.query(uri, projection, null, null, null)
                cursor?.use {
                    val nameInx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numInx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        if (nameInx >= 0 && numInx >= 0) {
                            val name = it.getString(nameInx) ?: ""
                            val number = it.getString(numInx) ?: ""
                            if (name.isNotBlank() && number.isNotBlank()) {
                                val score = calculateMatchScore(name, resolvedSearch)
                                if (score > 0.35f) {
                                    candidates.add(ContactCandidate(name, number, score, "System"))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fetch from Simulated database for robust fallback testing
        for ((name, number) in simulatedContacts) {
            val score = calculateMatchScore(name, resolvedSearch)
            if (score > 0.35f) {
                // If the same number isn't already added from the system
                if (candidates.none { it.phoneNumber == number }) {
                    candidates.add(ContactCandidate(name, number, score, "Simulated"))
                }
            }
        }

        // Sort descending by score, take top 5
        return candidates.distinctBy { it.phoneNumber }
            .sortedByDescending { it.score }
            .take(5)
    }

    // Helper to parse robust YouTube search/play query string
    fun parseYouTubeQuery(command: String): String {
        val cmd = command.lowercase(Locale.ROOT)
        var clean = cmd
            .replace("on youtube", "")
            .replace("on yt", "")
            .trim()
            
        val prefixes = listOf(
            "open youtube and play",
            "open youtube and search",
            "open yt and play",
            "open yt and search",
            "play on youtube",
            "play on yt",
            "search on youtube",
            "search on yt",
            "play",
            "search",
            "open youtube",
            "open yt"
        )
        
        for (prefix in prefixes) {
            if (clean.startsWith(prefix + " ")) {
                clean = clean.substring(prefix.length).trim()
                break
            } else if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length).trim()
                break
            }
        }
        
        if (clean.startsWith("and play ")) {
            clean = clean.substring(9).trim()
        } else if (clean.startsWith("and search ")) {
            clean = clean.substring(11).trim()
        }
        
        return clean.trim()
    }

    // Parses command into matching music app platform and search query based on explicit or implied platform:
    // Returns Pair(App Name [youtube, spotify, saavn], Query)
    fun parseMusicCommand(command: String): Pair<String, String> {
        val cmd = command.lowercase(Locale.ROOT)
        
        // 1. Determine platform
        var platform = "youtube"
        if (cmd.contains("spotify") || cmd.contains("spofity")) {
            platform = "spotify"
        } else if (cmd.contains("saavn") || cmd.contains("savn") || cmd.contains("savan") || cmd.contains("jio saavn") || cmd.contains("jiosaavn")) {
            platform = "saavn"
        }
        
        // 2. Extract query
        var clean = cmd
            .replace("on youtube", "")
            .replace("on yt", "")
            .replace("on spotify", "")
            .replace("on spofity", "")
            .replace("on saavn", "")
            .replace("on jiosaavn", "")
            .replace("on jio saavn", "")
            .trim()
            
        val prefixes = listOf(
            "open youtube and play",
            "open youtube and search",
            "open yt and play",
            "open yt and search",
            "play on youtube",
            "play on yt",
            "search on youtube",
            "search on yt",
            "open spotify and play",
            "open spotify and search",
            "play on spotify",
            "search on spotify",
            "open saavn and play",
            "open saavn and search",
            "open jiosaavn and play",
            "play on saavn",
            "play on jiosaavn",
            "search on saavn",
            "play",
            "search",
            "open youtube",
            "open yt",
            "open spotify",
            "open saavn",
            "open jiosaavn",
            "song called",
            "song"
        )
        
        for (prefix in prefixes) {
            if (clean.startsWith(prefix + " ")) {
                clean = clean.substring(prefix.length).trim()
                break
            } else if (clean.startsWith(prefix)) {
                clean = clean.substring(prefix.length).trim()
                break
            }
        }
        
        if (clean.startsWith("and play ")) {
            clean = clean.substring(9).trim()
        } else if (clean.startsWith("and search ")) {
            clean = clean.substring(11).trim()
        }
        
        return Pair(platform, clean.trim())
    }

    // Calculates matching confidence score between a contact name and search query (0.0 to 1.0)
    fun calculateMatchScore(contactName: String, query: String): Float {
        val cleanContactName = sanitizeName(contactName)
        val cleanQuery = sanitizeName(query)

        if (cleanContactName.isBlank() || cleanQuery.isBlank()) return 0.0f
        
        // Exact match
        if (cleanContactName == cleanQuery) return 1.0f
        
        // Multi-word exact matches
        if (cleanContactName.contains(" $cleanQuery") || cleanContactName.startsWith("$cleanQuery ")) return 0.95f
        
        // General contains query
        if (cleanContactName.contains(cleanQuery) || cleanQuery.contains(cleanContactName)) {
            val ratio = minOf(cleanQuery.length.toFloat() / cleanContactName.length.toFloat(), 1.0f)
            return 0.80f + (ratio * 0.15f) // Up to 0.95
        }

        // Spelling mistakes (Fuzzy Match using Lev Distance)
        val distance = getLevenshteinDistance(cleanContactName, cleanQuery)
        val maxLen = maxOf(cleanContactName.length, cleanQuery.length)
        if (maxLen > 0) {
            val score = 1.0f - (distance.toFloat() / maxLen.toFloat())
            if (score > 0.60f) {
                return score - 0.10f // Penalty for spelling mistake fuzzy
            }
        }

        return 0.0f
    }
}
