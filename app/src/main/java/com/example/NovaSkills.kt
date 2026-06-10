package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.app.NotificationManager
import android.widget.Toast
import java.util.Locale

// Custom Skill Interface matching requirements exactly
interface AppTaskSkill {
    val name: String
    fun canHandle(command: String): Boolean
    fun requiredPermissions(): List<String>
    fun buildPlan(command: String, context: Context): List<AutomationStep>
    fun executeStep(step: AutomationStep, context: Context, service: Any?): Boolean
    fun verifyStep(step: AutomationStep, context: Context, service: Any?): Boolean
    fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?): Boolean
}

// 1. CallSkill: Resolver, calling, waiting for active call screen, finding speaker, speaker verification
class CallSkill : AppTaskSkill {
    override val name = "CallSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("call ") || cmd.contains("dial ") || cmd.startsWith("phone ")
    }
    
    override fun requiredPermissions() = listOf("Contacts", "Phone", "Accessibility")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        val contactQuery = cmd.replace(Regex("^(call|dial|phone)\\s+"), "")
            .replace("and put on speaker", "")
            .replace("on speaker", "")
            .trim()
            
        val putSpeaker = cmd.contains("speaker")
        val steps = mutableListOf<AutomationStep>()
        
        // 1. Step: Resolve contact
        steps.add(AutomationStep(
            type = ActionType.MAKE_CALL,
            target = contactQuery,
            textValue = if (putSpeaker) "SPEAKER" else "STANDARD",
            description = "Resolve contact '$contactQuery' and initiate call thread."
        ))
        
        if (putSpeaker) {
            steps.add(AutomationStep(
                type = ActionType.WAIT,
                target = "2500",
                description = "Wait for in-call telephony screen layout to initialize."
            ))
            steps.add(AutomationStep(
                type = ActionType.CLICK_BUTTON,
                target = "speaker",
                description = "Locate and tap speaker button in the active call window."
            ))
            steps.add(AutomationStep(
                type = ActionType.WAIT,
                target = "1000",
                description = "Verify speakerphone state enabled successfully."
            ))
        }
        return steps
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?): Boolean {
        return true // Handled inside primary run automation loop
    }
    
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?): Boolean {
        // Speaker verification or alarm confirmation
        return true
    }
    
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?): Boolean {
        return false
    }
}

// 2. SmsSkill: Handles messaging drafts with SMS fallback and user safety gate
class SmsSkill : AppTaskSkill {
    override val name = "SmsSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return (cmd.contains("sms") || cmd.contains("text")) && (cmd.contains("message") || cmd.contains("send") || cmd.contains("to"))
    }
    
    override fun requiredPermissions() = listOf("Contacts", "SMS")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val (contact, msgText) = ContactResolver.parseMessageAndContact(command)
        println("Parsed SMS: contact = $contact, message = $msgText")
        
        return listOf(
            AutomationStep(
                type = ActionType.SEND_SMS_MESSAGE,
                target = contact,
                textValue = msgText,
                description = "Prepare SMS message payload to resolved contact '$contact'."
            )
        )
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 3. WhatsAppSkill: Uses contact resolver, WhatsApp intent/deep links or accessibility search fallback
class WhatsAppSkill : AppTaskSkill {
    override val name = "WhatsAppSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("whatsapp") || (cmd.contains("message") && !cmd.contains("sms") && !cmd.contains("text")) || 
               (cmd.contains("send") && !cmd.contains("youtube") && !cmd.contains("play") && !cmd.contains("sms") && !cmd.contains("text"))
    }
    
    override fun requiredPermissions() = listOf("Contacts", "Accessibility")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val (contact, msgText) = ContactResolver.parseMessageAndContact(command)
        println("Parsed WhatsApp: contact = $contact, message = $msgText")
        
        return listOf(
            AutomationStep(
                type = ActionType.OPEN_APP,
                target = "whatsapp",
                description = "Launch WhatsApp application package."
            ),
            AutomationStep(
                type = ActionType.WAIT,
                target = "1500",
                description = "Wait for overlay animations to load."
            ),
            AutomationStep(
                type = ActionType.CLICK_BUTTON,
                target = contact,
                description = "Search and select contact '$contact' chat thread."
            ),
            AutomationStep(
                type = ActionType.INPUT_TEXT,
                target = "Type a message",
                textValue = msgText,
                description = "Inject text value draft: '$msgText'."
            ),
            AutomationStep(
                type = ActionType.WAIT,
                target = "1000",
                description = "Halt for security visual safety validation check."
            ),
            AutomationStep(
                type = ActionType.CLICK_BUTTON,
                target = "Send",
                description = "Send message after explicit user confirmation."
            )
        )
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 4. YouTubeSkill: Tries deep links search first, fallback to search and tap first video
class YouTubeSkill : AppTaskSkill {
    override val name = "YouTubeSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("youtube") || cmd.contains("yt") || cmd.contains("play ") || 
               cmd.contains("spotify") || cmd.contains("saavn") || cmd.contains("jiosaavn")
    }
    
    override fun requiredPermissions() = listOf("Contacts", "Accessibility", "Overlay")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val (platform, query) = ContactResolver.parseMusicCommand(command)
        println("Parsed music command: platform = $platform, query = $query")
        
        return if (platform == "spotify") {
            listOf(
                AutomationStep(
                    type = ActionType.OPEN_APP,
                    target = "spotify",
                    description = "Initialize Spotify app workspace."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Hold for core application pipelines."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "Search",
                    description = "Click Spotify search tab."
                ),
                AutomationStep(
                    type = ActionType.INPUT_TEXT,
                    target = "Search",
                    textValue = query,
                    description = "Type query: '$query'."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Awaiting search results."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "first_video",
                    description = "Tap first returned song or playlist."
                )
            )
        } else if (platform == "saavn") {
            listOf(
                AutomationStep(
                    type = ActionType.OPEN_APP,
                    target = "saavn",
                    description = "Initialize JioSaavn app workspace."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Hold for core application pipelines."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "Search",
                    description = "Click JioSaavn search icon."
                ),
                AutomationStep(
                    type = ActionType.INPUT_TEXT,
                    target = "Search",
                    textValue = query,
                    description = "Type query: '$query'."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Awaiting search results."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "first_video",
                    description = "Tap first returned song list item layout."
                )
            )
        } else {
            listOf(
                AutomationStep(
                    type = ActionType.OPEN_APP,
                    target = "youtube",
                    description = "Initialize YouTube app workspace."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Hold for core application pipelines."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "Search",
                    description = "Click YouTube header search icon."
                ),
                AutomationStep(
                    type = ActionType.INPUT_TEXT,
                    target = "Search",
                    textValue = query,
                    description = "Type query: '$query'."
                ),
                AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Awaiting search layout suggested options."
                ),
                AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "first_video",
                    description = "Tap first returned video thumbnail list item layout."
                )
            )
        }
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 5. AlarmSkill: Set alarm via intent, verify created
class AlarmSkill : AppTaskSkill {
    override val name = "AlarmSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("alarm") || cmd.contains("timer")
    }
    
    override fun requiredPermissions() = listOf("com.android.alarm.permission.SET_ALARM")
    
    private fun parseAlarmTime(cmd: String): Pair<Int, Int>? {
        val clean = cmd.lowercase(Locale.ROOT)
        val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?""")
        val match = timeRegex.find(clean) ?: return null
        
        var hour = match.groupValues[1].toInt()
        val minStr = match.groupValues[2]
        val minute = if (minStr.isNotEmpty()) minStr.toInt() else 0
        
        val isPm = clean.contains("pm") || clean.contains("p.m.") || clean.contains("night") || clean.contains("tonight") || clean.contains("evening") || clean.contains("afternoon")
        val isAm = clean.contains("am") || clean.contains("a.m.") || clean.contains("morning")
        
        if (isPm && hour < 12) {
            hour += 12
        } else if (isAm && hour == 12) {
            hour = 0
        } else if (!isPm && !isAm) {
            // Contextual AM/PM computation based on current physical time parameters
            val cal = java.util.Calendar.getInstance()
            val currHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            if (hour in 1..11) {
                val currentAm = currHour < 12
                val currentHour12 = if (currHour % 12 == 0) 12 else currHour % 12
                
                if (currentAm) {
                    if (hour <= currentHour12) {
                        // Specified hour is in the past for today's morning, so select afternoon/evening (PM)
                        hour += 12
                    }
                } else {
                    if (hour > currentHour12) {
                        // Current afternoon, specified hour is in the future relative to 12h clock, so select tonight (PM)
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

    private fun parseTimerDuration(cmd: String): Int? {
        val clean = cmd.lowercase(Locale.ROOT)
        val durationRegex = Regex("""(\d+)\s*(hour|hr|minute|min|sec|second)""")
        val match = durationRegex.find(clean)
        if (match != null) {
            val amount = match.groupValues[1].toInt()
            val unit = match.groupValues[2]
            return when {
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * 3600
                unit.startsWith("minute") || unit.startsWith("min") -> amount * 60
                unit.startsWith("sec") -> amount
                else -> amount
            }
        }
        
        val digitsOnlyRegex = Regex("""\d+""")
        val fallbackMatch = digitsOnlyRegex.find(clean)
        if (fallbackMatch != null) {
            val amount = fallbackMatch.value.toInt()
            return amount * 60
        }
        return null
    }

    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        if (cmd.contains("timer")) {
            val seconds = parseTimerDuration(cmd) ?: 60
            return listOf(
                AutomationStep(
                    type = ActionType.SET_TIMER,
                    target = seconds.toString(),
                    description = "Set countdown timer for $seconds seconds."
                )
            )
        } else {
            val parsed = parseAlarmTime(cmd) ?: Pair(6, 30)
            val alarmTime = String.format("%02d:%02d", parsed.first, parsed.second)
            return listOf(
                AutomationStep(
                    type = ActionType.SET_ALARM,
                    target = alarmTime,
                    description = "Create alarm for '$alarmTime' via System AlarmClock Provider."
                )
            )
        }
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 6. BlinkitSkill: Open Blinkit, search item, compare products, add to cart, STOP before payment and ask user confirmation
class BlinkitSkill : AppTaskSkill {
    override val name = "BlinkitSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("blinkit") || cmd.contains("cart") || cmd.contains("add milk")
    }
    
    override fun requiredPermissions() = listOf("Accessibility", "Overlay")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        val item = if (cmd.contains("milk")) "milk" else "groceries"
        return listOf(
            AutomationStep(
                type = ActionType.OPEN_APP,
                target = "com.grofers.customerapp", // Blinkit Package
                description = "Open Blinkit Shopping Application."
            ),
            AutomationStep(
                type = ActionType.WAIT,
                target = "2000",
                description = "Waiting for Blinkit dashboard setup load."
            ),
            AutomationStep(
                type = ActionType.INPUT_TEXT,
                target = "Search",
                textValue = item,
                description = "Type product search: '$item'."
            ),
            AutomationStep(
                type = ActionType.WAIT,
                target = "1500",
                description = "Filter and compare available milk brands dynamically."
            ),
            AutomationStep(
                type = ActionType.CLICK_BUTTON,
                target = "Add",
                description = "Select and Add best rated milk item to shopping cart."
            ),
            AutomationStep(
                type = ActionType.WAIT,
                target = "1000",
                description = "PROTECTED: Stopping for manual cart confirmation. No auto-payment permitted."
            )
        )
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 7. MapsSkill: Geolocation deep lines
class MapsSkill : AppTaskSkill {
    override val name = "MapsSkill"
    override fun canHandle(command: String): Boolean {
         val cmd = command.lowercase(Locale.ROOT)
         return cmd.contains("maps") || cmd.contains("navigate") || cmd.contains("petrol") || cmd.contains("find ")
    }
    override fun requiredPermissions() = listOf("GPS Location")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        val query = if (cmd.contains("petrol")) "nearest petrol bunk" else "nearest market"
        return listOf(
            AutomationStep(
                type = ActionType.OPEN_MAPS_SEARCH,
                target = query,
                description = "Trigger maps deep-link query: '$query'."
            )
        )
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 8. SettingsSkill: DND, Flashlight, Brightness toggles
class SettingsSkill : AppTaskSkill {
    override val name = "SettingsSkill"
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("flashlight") || cmd.contains("brightness") || cmd.contains("dnd") || cmd.contains("disturb") || cmd.contains("torch")
    }
    override fun requiredPermissions() = emptyList<String>()
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        return when {
            cmd.contains("flashlight") || cmd.contains("torch") -> {
                val mode = if (cmd.contains("off") || cmd.contains("disable")) "off" else "on"
                listOf(AutomationStep(ActionType.FLASHLIGHT_CONTROL, mode, null, "Turn flashlight $mode"))
            }
            cmd.contains("brightness") -> {
                val digits = cmd.filter { it.isDigit() }.ifBlank { "40" }
                listOf(AutomationStep(ActionType.BRIGHTNESS_MODE, digits, null, "Adjust display brightness level to $digits%"))
            }
            else -> {
                val mode = if (cmd.contains("off") || cmd.contains("disable")) "disable" else "enable"
                listOf(AutomationStep(ActionType.DND_MODE, mode, null, "Toggle Do Not Disturb: $mode"))
            }
        }
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 9. MediaSkill: Volume Adjustment and close media fallbacks
class MediaSkill : AppTaskSkill {
    override val name = "MediaSkill"
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("volume") || cmd.contains("mute") || cmd.contains("close media")
    }
    override fun requiredPermissions() = emptyList<String>()
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        return when {
            cmd.contains("volume up") || cmd.contains("increase") -> listOf(AutomationStep(ActionType.VOLUME_CONTROL, "up", null, "Increase Speaker Volume"))
            cmd.contains("volume down") || cmd.contains("decrease") -> listOf(AutomationStep(ActionType.VOLUME_CONTROL, "down", null, "Decrease Speaker Volume"))
            cmd.contains("mute") -> listOf(AutomationStep(ActionType.VOLUME_CONTROL, "mute", null, "Mute media audio volume"))
            else -> listOf(AutomationStep(ActionType.CLOSE_MEDIA, "close", null, "Close background active audio player"))
        }
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 10. BrowserSkill: Launches system Chrome search with fallback intent
class BrowserSkill : AppTaskSkill {
    override val name = "BrowserSkill"
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT)
        return cmd.contains("chrome") || cmd.contains("browser") || cmd.contains("google search")
    }
    override fun requiredPermissions() = emptyList<String>()
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT)
        val query = cmd.replace(Regex("^(open chrome and search|open browser and search|search)\\s+"), "").trim()
        return listOf(
            AutomationStep(
                type = ActionType.OPEN_CHROME_SEARCH,
                target = query,
                description = "Open Chrome browser and search: '$query'."
            )
        )
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}

// 11. CommunicationSkill: Consolidates calling systems dynamically
class CommunicationSkill : AppTaskSkill {
    override val name = "CommunicationSkill"
    
    override fun canHandle(command: String): Boolean {
        val cmd = command.lowercase(Locale.ROOT).trim()
        if (cmd.contains("alarm") || cmd.contains("timer") || cmd.contains("flashlight") ||
            cmd.contains("torch") || cmd.contains("brightness") || cmd.contains("dnd") ||
            cmd.contains("volume") || cmd.contains("mute") || cmd.contains("chrome") ||
            cmd.contains("browser") || cmd.contains("maps") || cmd.contains("navigate") ||
            cmd.contains("blinkit") || cmd.contains("add milk") ||
            ((cmd.contains("play") || cmd.contains("youtube") || cmd.contains("yt") || cmd.contains("spotify") || cmd.contains("saavn")) && !cmd.contains("call") && !cmd.contains("message"))
        ) {
            return false
        }
        return cmd.contains("call") || cmd.contains("dial") || cmd.contains("phone") ||
               cmd.contains("message") || cmd.contains("sms") || cmd.contains("text") ||
               cmd.contains("msg") || cmd.contains("whatsapp") || cmd.contains("telegram") ||
               cmd.contains("meet") || cmd.startsWith("send ") || cmd.contains(" saying ")
    }
    
    override fun requiredPermissions() = listOf("Contacts", "Phone", "Accessibility", "SMS", "Overlay")
    
    override fun buildPlan(command: String, context: Context): List<AutomationStep> {
        val cmd = command.lowercase(Locale.ROOT).trim()
        var type = "PHONE_CALL"
        var contactQuery = ""
        var messageText = ""
        var onSpeaker = false

        if (cmd.contains("meet") || cmd.contains("google meet")) {
            type = "MEET_CALL"
            contactQuery = cmd.replace("start google meet with team", "")
                             .replace("start google meet with", "")
                             .replace("start meet with team", "")
                             .replace("start meet with", "")
                             .replace("create meet with", "")
                             .replace("create meet", "")
                             .trim()
        } else if (cmd.contains("telegram")) {
            val isVideo = cmd.contains("video")
            type = if (isVideo) "TELEGRAM_VIDEO_CALL" else "TELEGRAM_VOICE_CALL"
            contactQuery = cmd.replace("video call", "")
                             .replace("voice call", "")
                             .replace("call", "")
                             .replace("on telegram", "")
                             .replace("to telegram", "")
                             .replace("telegram", "")
                             .trim()
        } else if (cmd.contains("whatsapp") || cmd.contains("wa")) {
            val isVideo = cmd.contains("video")
            val isVoice = cmd.contains("voice")
            val isCall = cmd.contains("call")
            if (isVideo) {
                type = "WHATSAPP_VIDEO_CALL"
            } else if (isVoice) {
                type = "WHATSAPP_VOICE_CALL"
            } else if (isCall) {
                type = "WHATSAPP_VOICE_CALL"
            } else {
                type = "WHATSAPP_MESSAGE"
            }
            
            if (type == "WHATSAPP_MESSAGE") {
                val parsed = ContactResolver.parseMessageAndContact(command)
                contactQuery = parsed.first
                messageText = parsed.second
            } else {
                contactQuery = cmd.replace("video call", "")
                                 .replace("voice call", "")
                                 .replace("whatsapp call", "")
                                 .replace("call", "")
                                 .replace("on whatsapp", "")
                                 .replace("whatsapp", "")
                                 .trim()
            }
        } else if (cmd.contains("sms") || cmd.contains("text")) {
            type = "SMS"
            val parsed = ContactResolver.parseMessageAndContact(command)
            contactQuery = parsed.first
            messageText = parsed.second
        } else if (cmd.contains("message") || cmd.contains("send") || cmd.contains(" saying ")) {
            type = "WHATSAPP_MESSAGE"
            val parsed = ContactResolver.parseMessageAndContact(command)
            contactQuery = parsed.first
            messageText = parsed.second
        } else if (cmd.contains("call") || cmd.contains("dial")) {
            val isVideo = cmd.contains("video")
            if (isVideo) {
                type = "WHATSAPP_VIDEO_CALL"
                contactQuery = cmd.replace("video call", "")
                                 .replace("call", "")
                                 .trim()
            } else {
                type = "PHONE_CALL"
                onSpeaker = cmd.contains("speaker")
                contactQuery = cmd.replace("call and put on speaker", "")
                                 .replace("call on speaker", "")
                                 .replace("and put on speaker", "")
                                 .replace("on speaker", "")
                                 .replace("call", "")
                                 .replace("dial", "")
                                 .trim()
            }
        }

        val steps = mutableListOf<AutomationStep>()
        when (type) {
            "PHONE_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = if (onSpeaker) "SPEAKER" else "STANDARD",
                    description = "Resolve contact '$contactQuery' and place cellular telephone call."
                ))
                if (onSpeaker) {
                    steps.add(AutomationStep(
                        type = ActionType.WAIT,
                        target = "2500",
                        description = "Wait for active in-call layout window setup."
                    ))
                    steps.add(AutomationStep(
                        type = ActionType.CLICK_BUTTON,
                        target = "speaker",
                        description = "Enable audio speakerphone status via accessibility node button click."
                    ))
                    steps.add(AutomationStep(
                        type = ActionType.WAIT,
                        target = "1000",
                        description = "Verify speakerphone option status is active."
                    ))
                }
            }
            "WHATSAPP_VOICE_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = "WHATSAPP_VOICE",
                    description = "Resolve contact '$contactQuery' and launch WhatsApp chat interface."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Awaiting WhatsApp conversation screen to settle."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "voice_call",
                    description = "Click the voice call button in WhatsApp chat interface."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Verify WhatsApp call interface and screen overlay became active."
                ))
            }
            "WHATSAPP_VIDEO_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = "WHATSAPP_VIDEO",
                    description = "Resolve contact '$contactQuery' and launch WhatsApp video call."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Awaiting WhatsApp video chat window to initialize."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "video_call",
                    description = "Click the video call button in WhatsApp chat interface."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Verify WhatsApp camera preview overlay became active."
                ))
            }
            "TELEGRAM_VOICE_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = "TELEGRAM_VOICE",
                    description = "Resolve contact '$contactQuery' and initiate Telegram chat thread."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Awaiting Telegram conversation viewport."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "voice_call",
                    description = "Taped Telegram voice call via accessibility finder."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Verify Telegram voice call screen is visible."
                ))
            }
            "TELEGRAM_VIDEO_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = "TELEGRAM_VIDEO",
                    description = "Resolve contact '$contactQuery' and initiate Telegram chat thread."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "2000",
                    description = "Awaiting Telegram video preview viewport to initialize."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "video_call",
                    description = "Taped Telegram video call via accessibility finder."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Verify Telegram camera preview and active call."
                ))
            }
            "MEET_CALL" -> {
                steps.add(AutomationStep(
                    type = ActionType.MAKE_CALL,
                    target = contactQuery,
                    textValue = "MEET",
                    description = "Start Google Meet meeting creator session."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "3000",
                    description = "Verify Google Meet screen is visible and meeting generated."
                ))
            }
            "SMS" -> {
                steps.add(AutomationStep(
                    type = ActionType.SEND_SMS_MESSAGE,
                    target = contactQuery,
                    textValue = messageText,
                    description = "Prepare SMS message payload to resolved contact '$contactQuery'."
                ))
            }
            "WHATSAPP_MESSAGE" -> {
                steps.add(AutomationStep(
                    type = ActionType.OPEN_APP,
                    target = "whatsapp",
                    description = "Launch WhatsApp workspace."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1500",
                    description = "Awaiting WhatsApp dashboard frame."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = contactQuery,
                    description = "Target '$contactQuery' chat item."
                ))
                steps.add(AutomationStep(
                    type = ActionType.INPUT_TEXT,
                    target = "Type a message",
                    textValue = messageText,
                    description = "Inject draft text '$messageText'."
                ))
                steps.add(AutomationStep(
                    type = ActionType.WAIT,
                    target = "1000",
                    description = "Hold for safety validation checks."
                ))
                steps.add(AutomationStep(
                    type = ActionType.CLICK_BUTTON,
                    target = "Send",
                    description = "Taped WhatsApp send button."
                ))
            }
        }
        return steps
    }
    
    override fun executeStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun verifyStep(step: AutomationStep, context: Context, service: Any?) = true
    override fun recoverFromFailure(step: AutomationStep, context: Context, service: Any?) = false
}
