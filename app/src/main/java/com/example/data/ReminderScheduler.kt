package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_REMINDER"
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_time", reminder.time)
            putExtra("reminder_date", reminder.date)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            flags
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled reminder '${reminder.title}' at ${reminder.triggerTime}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm, falling safe back...", e)
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTime,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Failed entirely to schedule alarm", ex)
            }
        }
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_REMINDER"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            flags
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for reminder ID $reminderId")
    }

    fun restoreAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val pendingReminders = db.reminderDao.getPendingReminders()
                val now = System.currentTimeMillis()
                for (reminder in pendingReminders) {
                    if (reminder.triggerTime > now) {
                        scheduleReminder(context, reminder)
                    } else {
                        db.reminderDao.updateReminderStatus(reminder.id, "COMPLETED", now)
                        Log.d(TAG, "Marked elapsed pending reminder '${reminder.title}' as COMPLETED")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring alarms from database", e)
            }
        }
    }
}
