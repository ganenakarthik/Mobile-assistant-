package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveReminderManager {
    private val _activeTriggeredReminder = MutableStateFlow<Reminder?>(null)
    val activeTriggeredReminder = _activeTriggeredReminder.asStateFlow()

    fun triggerReminder(reminder: Reminder) {
        _activeTriggeredReminder.value = reminder
    }

    fun dismissReminder() {
        _activeTriggeredReminder.value = null
    }
}
