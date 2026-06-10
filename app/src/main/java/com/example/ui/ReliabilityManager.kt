package com.example.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReliabilityStats(
    val callsAttempted: Int = 0,
    val callsSucceeded: Int = 0,
    val alarmsAttempted: Int = 0,
    val alarmsSucceeded: Int = 0,
    val messagesAttempted: Int = 0,
    val messagesSucceeded: Int = 0,
    val launchesAttempted: Int = 0,
    val launchesSucceeded: Int = 0
) {
    val callSuccessRate: Float
        get() = if (callsAttempted > 0) (callsSucceeded.toFloat() / callsAttempted) * 100f else 100f

    val alarmSuccessRate: Float
        get() = if (alarmsAttempted > 0) (alarmsSucceeded.toFloat() / alarmsAttempted) * 100f else 100f

    val messageSuccessRate: Float
        get() = if (messagesAttempted > 0) (messagesSucceeded.toFloat() / messagesAttempted) * 100f else 100f

    val launchSuccessRate: Float
        get() = if (launchesAttempted > 0) (launchesSucceeded.toFloat() / launchesAttempted) * 100f else 100f

    val overallSuccessRate: Float
        get() {
            val totalAttempted = callsAttempted + alarmsAttempted + messagesAttempted + launchesAttempted
            val totalSucceeded = callsSucceeded + alarmsSucceeded + messagesSucceeded + launchesSucceeded
            return if (totalAttempted > 0) (totalSucceeded.toFloat() / totalAttempted) * 100f else 100f
        }
}

object ReliabilityManager {
    private const val PREFS_NAME = "nova_reliability_stats"
    
    private val _stats = MutableStateFlow(ReliabilityStats())
    val stats = _stats.asStateFlow()

    fun init(context: Context) {
        loadStats(context)
    }

    private fun loadStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _stats.value = ReliabilityStats(
            callsAttempted = prefs.getInt("calls_attempted", 0),
            callsSucceeded = prefs.getInt("calls_succeeded", 0),
            alarmsAttempted = prefs.getInt("alarms_attempted", 0),
            alarmsSucceeded = prefs.getInt("alarms_succeeded", 0),
            messagesAttempted = prefs.getInt("messages_attempted", 0),
            messagesSucceeded = prefs.getInt("messages_succeeded", 0),
            launchesAttempted = prefs.getInt("launches_attempted", 0),
            launchesSucceeded = prefs.getInt("launches_succeeded", 0)
        )
    }

    fun recordCallAttempt(context: Context, succeeded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempted = prefs.getInt("calls_attempted", 0) + 1
        val succeededCount = prefs.getInt("calls_succeeded", 0) + if (succeeded) 1 else 0
        prefs.edit().apply {
            putInt("calls_attempted", attempted)
            putInt("calls_succeeded", succeededCount)
            apply()
        }
        loadStats(context)
    }

    fun recordAlarmAttempt(context: Context, succeeded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempted = prefs.getInt("alarms_attempted", 0) + 1
        val succeededCount = prefs.getInt("alarms_succeeded", 0) + if (succeeded) 1 else 0
        prefs.edit().apply {
            putInt("alarms_attempted", attempted)
            putInt("alarms_succeeded", succeededCount)
            apply()
        }
        loadStats(context)
    }

    fun recordMessageAttempt(context: Context, succeeded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempted = prefs.getInt("messages_attempted", 0) + 1
        val succeededCount = prefs.getInt("messages_succeeded", 0) + if (succeeded) 1 else 0
        prefs.edit().apply {
            putInt("messages_attempted", attempted)
            putInt("messages_succeeded", succeededCount)
            apply()
        }
        loadStats(context)
    }

    fun recordLaunchAttempt(context: Context, succeeded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempted = prefs.getInt("launches_attempted", 0) + 1
        val succeededCount = prefs.getInt("launches_succeeded", 0) + if (succeeded) 1 else 0
        prefs.edit().apply {
            putInt("launches_attempted", attempted)
            putInt("launches_succeeded", succeededCount)
            apply()
        }
        loadStats(context)
    }

    fun resetStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        loadStats(context)
    }
}
