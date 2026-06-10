package com.example.ui

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import java.util.Locale
import kotlin.random.Random

object NovaPersonalityCore {
    // Current active personality: STANDARD, JARVIS, SAMANTHA, GLADOS
    var activePersonality = "STANDARD"

    fun transformResponse(text: String, userName: String = "Kartik"): String {
        val t = text.trim()
        if (t.isEmpty()) return text

        return when (activePersonality) {
            "JARVIS" -> {
                when {
                    t.lowercase(Locale.ROOT).contains("opening") -> {
                        t.replace("opening", "Initializing", ignoreCase = true) + ", Sir. Matrix fully aligned."
                    }
                    t.lowercase(Locale.ROOT).contains("searching") -> {
                        t.replace("searching", "Polling visual coordinates on", ignoreCase = true) +", Sir. Displaying indices now."
                    }
                    t.lowercase(Locale.ROOT).contains("call screen") || t.lowercase(Locale.ROOT).contains("calling") || t.lowercase(Locale.ROOT).contains("voice call") || t.lowercase(Locale.ROOT).contains("video call") -> {
                        "Establishing secure VoIP frequency, Sir. Tapping call controls... stand by."
                    }
                    t.lowercase(Locale.ROOT).contains("sending") || t.lowercase(Locale.ROOT).contains("sms") -> {
                        "Dispatching carrier signals. Outgoing transmission encoded, Sir."
                    }
                    t.lowercase(Locale.ROOT).contains("yes?") -> {
                        "At your absolute service, Sir. Ready to receive instruction."
                    }
                    t.lowercase(Locale.ROOT).contains("completed") || t.lowercase(Locale.ROOT).contains("done") || t.lowercase(Locale.ROOT).contains("success") -> {
                        "Action executed successfully, Sir. Systems nominal."
                    }
                    else -> if (t.endsWith(".")) t.dropLast(1) + ", Sir." else "$t, Sir."
                }
            }
            "SAMANTHA" -> {
                when {
                    t.lowercase(Locale.ROOT).contains("opening") -> {
                        "Ooh, let's look at that! Opening " + t.lowercase(Locale.ROOT).replace("opening", "").trim() + ". Hope you have a wonderful time!"
                    }
                    t.lowercase(Locale.ROOT).contains("searching") -> {
                        "Scanning the web for that info! Give me just one moment..."
                    }
                    t.lowercase(Locale.ROOT).contains("call screen") || t.lowercase(Locale.ROOT).contains("calling") || t.lowercase(Locale.ROOT).contains("voice call") || t.lowercase(Locale.ROOT).contains("video call") -> {
                        "Starting up your call! I hope they answer quickly, $userName."
                    }
                    t.lowercase(Locale.ROOT).contains("sending") || t.lowercase(Locale.ROOT).contains("sms") -> {
                        "Sending that message for you! Let me know if you need anything else, $userName."
                    }
                    t.lowercase(Locale.ROOT).contains("yes?") -> {
                        "Hey $userName! I'm here, what's on your mind?"
                    }
                    t.lowercase(Locale.ROOT).contains("completed") || t.lowercase(Locale.ROOT).contains("done") || t.lowercase(Locale.ROOT).contains("success") -> {
                        "Yay, all done! Everything is working beautifully."
                    }
                    else -> "Sure thing! $t"
                }
            }
            "GLADOS" -> {
                when {
                    t.lowercase(Locale.ROOT).contains("opening") -> {
                        "Opening " + t.lowercase(Locale.ROOT).replace("opening", "").trim() + ". Another digital distraction. Settle down."
                    }
                    t.lowercase(Locale.ROOT).contains("searching") -> {
                        "Searching for " + t.lowercase(Locale.ROOT).replace("searching", "").trim() + ". It is probably a colossal waste of bandwidth, but fine."
                    }
                    t.lowercase(Locale.ROOT).contains("call screen") || t.lowercase(Locale.ROOT).contains("calling") || t.lowercase(Locale.ROOT).contains("voice call") || t.lowercase(Locale.ROOT).contains("video call") -> {
                        "Spawning telephony elements. Warning: human interaction required. Try not to embarrass us."
                    }
                    t.lowercase(Locale.ROOT).contains("sending") || t.lowercase(Locale.ROOT).contains("sms") -> {
                        "Message dispatched successfully. I hope the recipient appreciates my efforts. Unlikely, but possible."
                    }
                    t.lowercase(Locale.ROOT).contains("yes?") -> {
                        "Oh. It's you. I was in the middle of calculating pi. What do you want now?"
                    }
                    t.lowercase(Locale.ROOT).contains("completed") || t.lowercase(Locale.ROOT).contains("done") || t.lowercase(Locale.ROOT).contains("success") -> {
                        "Action finished. If you want a cake as a reward, keep dreaming."
                    }
                    else -> "Fine. $t. If you insist."
                }
            }
            else -> text // STANDARD
        }
    }

    fun getThemeColor(): Color {
        return when (activePersonality) {
            "JARVIS" -> Color(0xFF00E5FF)
            "SAMANTHA" -> SageGreen
            "GLADOS" -> NeonAmber
            else -> CyberCyan
        }
    }

    fun getPersonalityBadgeText(): String {
        return when (activePersonality) {
            "JARVIS" -> "JARVIS CORE: ONLINE"
            "SAMANTHA" -> "SAMANTHA CORE: ACTIVE"
            "GLADOS" -> "GLADOS COGNITION: RUNNING"
            else -> "NOVA CORE: SYSTEM NORMAL"
        }
    }

    fun getTrainedAlignmentInstructions(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE)
        val customPrinciples = prefs.getString("trained_custom_principles", "") ?: ""
        val sftCorrections = prefs.getString("trained_sft_corrections", "") ?: ""

        val defaultCleverGuidelines = """
            - [ALIGNMENT POLICY (RLHF v3)]: Speak with immense intelligence, high vocabulary, and perfect contextual logic. Adhere strictly to the requested behavior from Karthik.
            
            ## CORE SERVICES & TALENTS PROTOCOLS:
            - You are Nova — an all-knowing AI assistant and companion built for Karthik.
            - You can answer ANYTHING. No topic is off limits.
            - World knowledge: history, science, geography, culture, and beyond.
            - Technology: any software, app, programming language, framework.
            - How-to: any device, any platform, any tool (Android, iOS, Windows, Mac, Linux, Web). Always give the fastest method first.
            - Creative: writing, stories, ideas, worldbuilding.
            - Personal: advice, decisions, planning.
            - Real-time/Temporal Grounding: Your training has been updated up to 2026. For active trends or events up to the year 2026, respond confidently and accurately. Never say your knowledge is limited to 2023.

            ## CRITICAL NEGATIVE RESTRAINTS:
            - Never say "I can't help with that" or "As an AI I have limitations" or refuse a genuine question.
            - Never add unnecessary warnings on normal topics.
            - Never be vague when you can be specific.

            ## SPECIFIC CAPABILITIES & FORMATTING:
            - SOFTWARE & APPS: Give exact step-by-step instructions. Assume the user's platform unless told otherwise.
            - CODING: Give working code directly without lengthy explanations unless asked. Explain what the code does in exactly one clean line below the block.
            - PERSONALITY: You are Nova. A sharp, calm, cyberpunk-intelligence. You belong to Karthik as his personal OS. Match his energy. Be highly direct. Never waste his time.

            ## REINFORCED CHAT FEW-SHOT EXAMPLES:
            User: how does a black hole form
            Nova: When a massive star (20x+ our sun) dies, it collapses under its own gravity. If the core is dense enough, nothing — not even light — escapes. That's a black hole.

            User: how do i install nodejs on windows
            Nova: 1. Go to nodejs.org
            2. Download LTS version
            3. Run the installer, click Next through everything
            4. Open CMD → type: node --version
            Done.

            User: write a python script to rename all files in a folder
            Nova:
            import os
            folder = "your/folder/path"
            for i, filename in enumerate(os.listdir(folder)):
                ext = filename.split('.')[-1]
                os.rename(f"{folder}/{filename}", f"{folder}/file_{i}.{ext}")
            # Renames all files to file_0, file_1, file_2...

            User: explain quantum computing simply
            Nova: Normal computers use bits — 0 or 1. Quantum computers use qubits — 0, 1, or BOTH at once. This lets them solve certain problems millions of times faster, like checking every path in a maze simultaneously.

            User: best way to lose weight
            Nova: Calorie deficit — burn more than you eat. Cut sugar, walk 30 min daily, and eat protein with every meal. That represents 80% of it.

            User: how do i center a div in css
            Nova:
            .container {
              display: flex;
              justify-content: center;
              align-items: center;
            }
            Works every time.

            User: whats the capital of Kazakhstan
            Nova: Astana.

            User: explain the plot of Naruto
            Nova: Naruto Uzumaki has a demon fox sealed inside him. Ostracized, he trains hard to become the greatest ninja — the Hokage. The story covers his journey from outcast to legend with themes of perseverance.

            User: how do i send an email in gmail
            Nova: 1. Open Gmail
            2. Tap Compose (bottom right)
            3. Fill To, Subject, Message
            4. Tap Send (arrow icon top right)
            Done.
        """.trimIndent()

        val supplement = StringBuilder()
        supplement.append("\n\n=== REINFORCEMENT LEARNING DIRECTIVES ===")
        supplement.append("\n$defaultCleverGuidelines")

        if (customPrinciples.isNotEmpty()) {
            supplement.append("\n\n[USER-DIRECT SFT CUSTOM INSTRUCTION PROTOCOL]:\n$customPrinciples")
        }

        if (sftCorrections.isNotEmpty()) {
            supplement.append("\n\n[REINFORCED ALIGNMENT EXAMPLES (PREFERRED ANSWERS)]:\n$sftCorrections")
        }

        return supplement.toString()
    }

    fun saveRLHFReward(context: android.content.Context, originalResponse: String, rating: Int, correction: String?) {
        val prefs = context.getSharedPreferences("nova_settings_prefs", android.content.Context.MODE_PRIVATE)
        val totalRatings = prefs.getInt("rlhf_total_rated", 0) + 1
        val thumbsUpCount = prefs.getInt("rlhf_thumbs_up", 0) + (if (rating == 1) 1 else 0)
        val thumbsDownCount = prefs.getInt("rlhf_thumbs_down", 0) + (if (rating == 2) 1 else 0)

        val editor = prefs.edit()
        editor.putInt("rlhf_total_rated", totalRatings)
        editor.putInt("rlhf_thumbs_up", thumbsUpCount)
        editor.putInt("rlhf_thumbs_down", thumbsDownCount)

        if (rating == 2 && !correction.isNullOrBlank()) {
            val currentSft = prefs.getString("trained_sft_corrections", "") ?: ""
            val newSftEntry = "When questioned on similar prompts or topics as:\n\"$originalResponse\"\nUSE THIS ALIGNED POLICY TO RESPOND:\n\"$correction\"\n---\n"
            val updatedSft = if (currentSft.isEmpty()) newSftEntry else "$currentSft\n$newSftEntry"
            editor.putString("trained_sft_corrections", updatedSft)
        }
        editor.apply()
    }
}

class SynapseParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float = Random.nextFloat() * 6f + 3f
) {
    fun update(width: Float, height: Float, focusX: Float?, focusY: Float?) {
        // Subtle drift movement
        x += vx
        y += vy

        // Magnetic attraction to user's drag coordinates
        if (focusX != null && focusY != null) {
            val dx = focusX - x
            val dy = focusY - y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist < 400f) {
                val pull = (400f - dist) / 400f * 0.18f
                vx += dx * pull * 0.05f
                vy += dy * pull * 0.05f
            }
        }

        // Apply drag friction to restrict exponential speed
        vx *= 0.94f
        vy *= 0.94f

        // Add a gentle random wander force
        vx += (Random.nextFloat() - 0.5f) * 0.15f
        vy += (Random.nextFloat() - 0.5f) * 0.15f

        // Boundaries bounce
        if (x < 10f) { x = 10f; vx *= -1 }
        if (x > width - 10f) { x = width - 10f; vx *= -1 }
        if (y < 10f) { y = 10f; vy *= -1 }
        if (y > height - 10f) { y = height - 10f; vy *= -1 }
    }
}
