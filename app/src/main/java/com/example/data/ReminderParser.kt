package com.example.data

import java.util.*

object ReminderParser {
    
    // Parses the query to extract a title (subject) and a trigger time (epoch millis)
    fun parseReminderQuery(query: String): Pair<String, Long>? {
        val clean = query.lowercase(Locale.ROOT).trim()
        
        // 1. Extract raw title by stripping common starting prefixes
        var title = query
        val prefixes = listOf(
            "remind me to ", "remind me about ", "create reminder for ", "reminder for ", 
            "remind me ", "add reminder to ", "add reminder for ", "new reminder "
        )
        for (prefix in prefixes) {
            if (title.lowercase(Locale.ROOT).startsWith(prefix)) {
                title = title.substring(prefix.length)
                break
            }
        }
        
        // Now parse the target trigger time
        val triggerTime = parseTime(clean) ?: return null
        
        // 2. Truncate trailing expressions matching the parsed times to keep title pristine
        var cleanTitle = title
        val atIndex = cleanTitle.lowercase(Locale.ROOT).lastIndexOf(" at ")
        if (atIndex > 0) {
            cleanTitle = cleanTitle.substring(0, atIndex)
        }
        val inIndex = cleanTitle.lowercase(Locale.ROOT).lastIndexOf(" in ")
        if (inIndex > 0) {
            cleanTitle = cleanTitle.substring(0, inIndex)
        }
        val onIndex = cleanTitle.lowercase(Locale.ROOT).lastIndexOf(" on ")
        if (onIndex > 0) {
            cleanTitle = cleanTitle.substring(0, onIndex)
        }
        val tomorrowIndex = cleanTitle.lowercase(Locale.ROOT).lastIndexOf(" tomorrow")
        if (tomorrowIndex > 0) {
            cleanTitle = cleanTitle.substring(0, tomorrowIndex)
        }
        val tonightIndex = cleanTitle.lowercase(Locale.ROOT).lastIndexOf(" tonight")
        if (tonightIndex > 0) {
            cleanTitle = cleanTitle.substring(0, tonightIndex)
        }
        
        cleanTitle = cleanTitle.trim().replace(Regex("""^[.,!?\s\-]+|[.,!?\s\-]+$"""), "")
        if (cleanTitle.isEmpty()) {
            cleanTitle = "Reminder Task"
        } else {
            cleanTitle = cleanTitle.substring(0, 1).uppercase() + cleanTitle.substring(1)
        }
        
        return Pair(cleanTitle, triggerTime)
    }

    private fun parseTime(clean: String): Long? {
        val now = Calendar.getInstance()
        
        // Case A: "in [X] [hours/minutes/seconds/days/weeks]"
        val inPattern = Regex("""in\s+(\d+|one|two|three|four|five|six|seven|eight|nine|ten)\s*(hour|hr|minute|min|sec|second|day|week|s)s?""")
        val inMatch = inPattern.find(clean)
        if (inMatch != null) {
            val amountStr = inMatch.groupValues[1]
            val unit = inMatch.groupValues[2]
            val amount = when (amountStr) {
                "one", "a", "an" -> 1
                "two" -> 2
                "three" -> 3
                "four" -> 4
                "five" -> 5
                "six" -> 6
                "seven" -> 7
                "eight" -> 8
                "nine" -> 9
                "ten" -> 10
                else -> amountStr.toIntOrNull() ?: 1
            }
            val cal = Calendar.getInstance()
            when {
                unit.startsWith("hour") || unit.startsWith("hr") -> cal.add(Calendar.HOUR_OF_DAY, amount)
                unit.startsWith("minute") || unit.startsWith("min") -> cal.add(Calendar.MINUTE, amount)
                unit.startsWith("sec") || unit == "s" -> cal.add(Calendar.SECOND, amount)
                unit.startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, amount)
                unit.startsWith("week") -> cal.add(Calendar.WEEK_OF_YEAR, amount)
            }
            return cal.timeInMillis
        }

        // Case B: "next [day of week]" (e.g., "next Monday")
        val daysOfWeek = mapOf(
            "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
        )
        for ((name, dayConst) in daysOfWeek) {
            if (clean.contains("next $name")) {
                val cal = Calendar.getInstance()
                // Move forward to find the next day matching the target day of week
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) != dayConst)
                
                val parsedTime = extractHourMinute(clean)
                if (parsedTime != null) {
                    cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
                    cal.set(Calendar.MINUTE, parsedTime.second)
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 9) // Default: 9 AM
                    cal.set(Calendar.MINUTE, 0)
                }
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
        }

        // Case C: "tomorrow morning/evening/afternoon/night" or "tomorrow"
        if (clean.contains("tomorrow")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            
            val parsedTime = extractHourMinute(clean)
            if (parsedTime != null) {
                cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
                cal.set(Calendar.MINUTE, parsedTime.second)
            } else {
                when {
                    clean.contains("morning") -> cal.set(Calendar.HOUR_OF_DAY, 9)
                    clean.contains("afternoon") -> cal.set(Calendar.HOUR_OF_DAY, 14)
                    clean.contains("evening") -> cal.set(Calendar.HOUR_OF_DAY, 18)
                    clean.contains("night") -> cal.set(Calendar.HOUR_OF_DAY, 20)
                    else -> cal.set(Calendar.HOUR_OF_DAY, 9) // Default to 9:00 AM tomorrow
                }
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        // Case D: "tonight"
        if (clean.contains("tonight")) {
            val cal = Calendar.getInstance()
            val parsedTime = extractHourMinute(clean)
            if (parsedTime != null) {
                cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
                cal.set(Calendar.MINUTE, parsedTime.second)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 20) // Default tonight: 8:00 PM
                cal.set(Calendar.MINUTE, 0)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            // If the time is in the past for today, treat it as tomorrow night contextually
            if (cal.before(Calendar.getInstance())) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // Case E: Simple "at [time]" or absolute hours match on the same day
        val parsedTime = extractHourMinute(clean)
        if (parsedTime != null) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
            cal.set(Calendar.MINUTE, parsedTime.second)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            if (cal.before(Calendar.getInstance())) {
                // If it was parsed as early morning (AM) but shifting to PM in the same day resolves it to future
                if (parsedTime.first < 12 && !clean.contains("am")) {
                    val calPm = Calendar.getInstance()
                    calPm.set(Calendar.HOUR_OF_DAY, parsedTime.first + 12)
                    calPm.set(Calendar.MINUTE, parsedTime.second)
                    calPm.set(Calendar.SECOND, 0)
                    calPm.set(Calendar.MILLISECOND, 0)
                    if (calPm.after(Calendar.getInstance())) {
                        return calPm.timeInMillis
                    }
                }
                // Otherwise rolls over to tomorrow automatically
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        return null
    }

    private fun extractHourMinute(clean: String): Pair<Int, Int>? {
        // 1. Match colon-formatted time: HH:MM with optional AM/PM
        val colonRegex = Regex("""\b(?:at\s+)?(\d{1,2}):(\d{2})\s*(am|pm|a\.m\.|p\.m\.)?\b""")
        val colonMatch = colonRegex.find(clean)
        if (colonMatch != null) {
            return parseMatch(colonMatch.groupValues[1].toInt(), colonMatch.groupValues[2].toInt(), colonMatch.groupValues[3], clean)
        }

        // 2. Match hour with am/pm: HH am/pm
        val amPmRegex = Regex("""\b(?:at\s+)?(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)\b""")
        val amPmMatch = amPmRegex.find(clean)
        if (amPmMatch != null) {
            return parseMatch(amPmMatch.groupValues[1].toInt(), 0, amPmMatch.groupValues[2], clean)
        }

        // 3. Match hour preceded by "at": at HH
        val atRegex = Regex("""\bat\s+(\d{1,2})\b""")
        val atMatch = atRegex.find(clean)
        if (atMatch != null) {
            return parseMatch(atMatch.groupValues[1].toInt(), 0, "", clean)
        }

        return null
    }

    private fun parseMatch(hourRaw: Int, minute: Int, amPmStr: String, clean: String): Pair<Int, Int>? {
        var hour = hourRaw
        val amPm = amPmStr.lowercase(Locale.ROOT)
        
        val isPm = amPm.contains("pm") || clean.contains("night") || clean.contains("tonight") || clean.contains("evening") || clean.contains("afternoon")
        val isAm = amPm.contains("am") || clean.contains("morning")
        
        if (isPm && hour < 12) {
            hour += 12
        } else if (isAm && hour == 12) {
            hour = 0
        } else if (!isPm && !isAm) {
            // Contextual AM/PM assessment helper
            val cal = Calendar.getInstance()
            val currHour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour in 1..11) {
                val currentAm = currHour < 12
                val currentHour12 = if (currHour % 12 == 0) 12 else currHour % 12
                
                if (currentAm) {
                    if (hour <= currentHour12) {
                        // Hour parsed is physically in the past for today's morning, so select afternoon/evening (PM)
                        hour += 12
                    }
                } else {
                    if (hour > currentHour12) {
                        // Target hour is larger than current Afternoon time, therefore scheduling tonight (PM)
                        hour += 12
                    }
                }
            }
        }
        
        if (hour in 0..23 && minute in 0..59) {
            return Pair(hour, minute)
        }
        return null
    }
}
