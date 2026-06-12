package com.example

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

data class ContactCandidate(
    val id: String = "",
    val displayName: String,
    val phoneNumber: String,
    val confidence: Float,
    val label: String? = null,
    val source: String = "System"
) {
    val name: String get() = displayName
    val score: Float get() = confidence
}

sealed class CallResolutionState {
    object NoMatch : CallResolutionState()
    data class SingleMatch(val contact: ContactCandidate) : CallResolutionState()
    data class MultipleMatches(val candidates: List<ContactCandidate>) : CallResolutionState()
    data class BlockedEmergency(val number: String) : CallResolutionState()
}

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
        Pair("Anjali Mehta", "916655443322"),
        Pair("Hemanth College", "919494949494"),
        Pair("Hemanth Home", "919393939393"),
        Pair("Hemanth Old", "919292929292")
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
        
        val knownContactKeywords = listOf("nazeer", "bittu", "mom", "dad", "rahul", "priya", "amit", "anjali", "mohit", "hemanth")
        
        // 0. Extract platform suffixes first to keep the command clean
        var stripped = cmd
        val whatsappSuffixRegex = Regex("(?i)\\s+(on whatsapp|via whatsapp|on telegram|via telegram|on sms|via sms|on wa)$")
        val suffixMatch = whatsappSuffixRegex.find(stripped)
        if (suffixMatch != null) {
            stripped = stripped.substring(0, suffixMatch.range.first).trim()
        }
        
        val prefixRegex = Regex("^(whatsapp\\s+message\\s+to|whatsapp\\s+to|send\\s+whatsapp\\s+notification\\s+to|send\\s+whatsapp\\s+message\\s+to|send\\s+whatsapp\\s+to|send\\s+sms\\s+to|send\\s+message\\s+to|send\\s+text\\s+to|message\\s+to|msg\\s+to|text\\s+to|tell\\s+to|send\\s+to|send\\s+whatsapp|send\\s+sms|send\\s+message|send\\s+text|message|msg|text|tell|send)\\s+", RegexOption.IGNORE_CASE)
        
        // 1. Handle Pattern 1: "{command...} {message} to {contact}"
        val strippedClean = stripped.lowercase(Locale.ROOT)
        if (strippedClean.contains(" to ")) {
            val toIdx = strippedClean.lastIndexOf(" to ")
            if (toIdx != -1) {
                val possibleContact = stripped.substring(toIdx + 4).trim()
                val beforeTo = stripped.substring(0, toIdx).trim()
                val msgPart = beforeTo.replace(prefixRegex, "").trim()
                if (possibleContact.isNotEmpty() && msgPart.isNotEmpty()) {
                    val lowerContact = possibleContact.lowercase(Locale.ROOT)
                    val greetings = setOf("hi", "hello", "hey", "yo", "sup")
                    if (!greetings.contains(lowerContact)) {
                        return Pair(possibleContact, cleanMessageBody(msgPart))
                    }
                }
            }
        }
        
        // Prepare main clean command without root verb
        var body = stripped.replace(prefixRegex, "").trim()
        var bodyClean = body.lowercase(Locale.ROOT)
        
        if (bodyClean.startsWith("to ")) {
            body = body.substring(3).trim()
            bodyClean = body.lowercase(Locale.ROOT)
        }
        
        // 2. Scan if we have a known contact keyword anywhere in the body
        for (contact in knownContactKeywords) {
            val idx = bodyClean.indexOf(contact)
            if (idx != -1) {
                // Known contact found! Let's extract the rest as message
                val contactPart = body.substring(idx, idx + contact.length).trim()
                val leftSide = body.substring(0, idx).trim()
                val rightSide = body.substring(idx + contact.length).trim()
                val msgPart = when {
                    leftSide.isNotEmpty() && rightSide.isNotEmpty() -> "$leftSide $rightSide"
                    leftSide.isNotEmpty() -> leftSide
                    else -> rightSide
                }
                if (contactPart.isNotEmpty() && msgPart.isNotEmpty()) {
                    return Pair(contactPart, cleanMessageBody(msgPart))
                }
            }
        }
        
        // 3. Fallback heuristic: If it starts with common greeting, first word/phrase is message, rest is contact name
        val greetings = setOf("hi", "hello", "hey", "yo", "sup")
        val words = body.split("\\s+".toRegex())
        if (words.size >= 2) {
            val firstWordLower = words.first().lowercase(Locale.ROOT)
            if (greetings.contains(firstWordLower)) {
                val contactPart = body.substring(words.first().length).trim()
                val msgPart = words.first()
                return Pair(contactPart, cleanMessageBody(msgPart))
            }
        }
        
        // 4. Fallback to space split: "{contact} {message}"
        val spaceIdx = body.indexOf(' ')
        if (spaceIdx != -1) {
            val contactPart = body.substring(0, spaceIdx).trim()
            val msgPart = body.substring(spaceIdx + 1).trim()
            return Pair(contactPart, cleanMessageBody(msgPart))
        }
        
        // Final ultimate fallback
        return Pair(body.ifEmpty { "Rahul" }, "hi")
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
                                    val contactId = number.hashCode().toString()
                                    val derivedLabel = if (name.contains(" ")) name.substring(name.indexOf(" ") + 1) else null
                                    candidates.add(ContactCandidate(
                                        id = contactId,
                                        displayName = name,
                                        phoneNumber = number,
                                        confidence = score,
                                        label = derivedLabel,
                                        source = "System"
                                    ))
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
                val systemMatchExists = candidates.any {
                    it.source == "System" && (
                        sanitizeName(it.displayName) == sanitizeName(name) ||
                        sanitizeName(it.displayName).contains(sanitizeName(name)) ||
                        sanitizeName(name).contains(sanitizeName(it.displayName))
                    )
                }
                if (systemMatchExists) {
                    continue
                }

                // If the same number isn't already added from the system
                if (candidates.none { it.phoneNumber == number }) {
                    val contactId = number.hashCode().toString()
                    val derivedLabel = if (name.contains(" ")) name.substring(name.indexOf(" ") + 1) else null
                    candidates.add(ContactCandidate(
                        id = contactId,
                        displayName = name,
                        phoneNumber = number,
                        confidence = score,
                        label = derivedLabel,
                        source = "Simulated"
                    ))
                }
            }
        }

        // Sort descending by score, take top 5
        return candidates.distinctBy { it.phoneNumber }
            .sortedByDescending { it.score }
            .take(5)
    }

    // Resolves cellular voice calls state based on confidence metrics & emergency safety rules
    fun resolveCallState(context: Context, query: String): CallResolutionState {
        val cleanQuery = query.lowercase(Locale.ROOT).trim()

        // Helpfully strip standard calling intent prefix
        var targetSearch = cleanQuery
            .replace(Regex("^(call|dial|phone)\\s+"), "")
            .replace("and put on speaker", "")
            .replace("on speaker", "")
            .trim()

        if (targetSearch.isEmpty()) {
            return CallResolutionState.NoMatch
        }

        // Emergency safety blocklist (Never auto-call)
        val emergencyNumbers = listOf("100", "101", "102", "108", "112", "911", "999")
        val numericOnly = targetSearch.filter { it.isDigit() }
        if (emergencyNumbers.contains(numericOnly) || emergencyNumbers.contains(targetSearch)) {
            return CallResolutionState.BlockedEmergency(targetSearch)
        }

        // Direct dial verification
        val isDirectDial = targetSearch.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }
        if (isDirectDial) {
            val digits = targetSearch.filter { it.isDigit() || it == '+' }
            // Double check emergency numbers inside digits
            val cleanDigits = digits.filter { it.isDigit() }
            if (emergencyNumbers.contains(cleanDigits)) {
                return CallResolutionState.BlockedEmergency(digits)
            }
            return CallResolutionState.SingleMatch(
                ContactCandidate(
                    id = "direct",
                    displayName = targetSearch,
                    phoneNumber = digits,
                    confidence = 1.0f,
                    label = "Direct Dial",
                    source = "Direct"
                )
            )
        }

        // Query contact candidates
        val candidates = resolveContact(context, targetSearch)
        if (candidates.isEmpty()) {
            return CallResolutionState.NoMatch
        }

        // Confidence Rules:
        // - confidence >= 0.85 and one candidate -> SingleMatch (direct call)
        // - multiple candidates above threshold -> MultipleMatches (ask selection)
        // - confidence below threshold -> MultipleMatches (ask selection or fail safely)
        val strongCandidates = candidates.filter { it.confidence >= 0.85f }

        return when {
            strongCandidates.size == 1 -> {
                CallResolutionState.SingleMatch(strongCandidates.first())
            }
            strongCandidates.size > 1 -> {
                CallResolutionState.MultipleMatches(strongCandidates)
            }
            else -> {
                // If there's no strong candidate above 0.85, ask to select from candidates
                CallResolutionState.MultipleMatches(candidates)
            }
        }
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
        
        // Word boundary startsWith or word matches (guarantees hi doesn't match Mohit)
        val words = cleanContactName.split(" ")
        for (word in words) {
            if (word == cleanQuery) {
                return 0.95f
            }
            if (word.startsWith(cleanQuery)) {
                val ratio = cleanQuery.length.toFloat() / word.length.toFloat()
                return 0.75f + (ratio * 0.20f)
            }
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
