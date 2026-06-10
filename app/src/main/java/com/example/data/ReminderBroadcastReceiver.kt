package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class ReminderBroadcastReceiver : BroadcastReceiver() {
    private companion object {
        const val CHANNEL_ID = "nova_reminders_channel"
        const val NOTIFICATION_ID_BASE = 2000
    }

    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ReminderReceiver", "onReceive triggered with action: $action")
        
        if (action == "android.intent.action.BOOT_COMPLETED") {
            Log.d("ReminderReceiver", "Boot Completed received. Restoring alarms...")
            ReminderScheduler.restoreAllAlarms(context)
            return
        }

        if (action == "com.example.ACTION_TRIGGER_REMINDER") {
            val reminderId = intent.getIntExtra("reminder_id", -1)
            val reminderTitle = intent.getStringExtra("reminder_title") ?: "Reminder"
            val reminderTime = intent.getStringExtra("reminder_time") ?: ""
            val reminderDate = intent.getStringExtra("reminder_date") ?: ""

            Log.d("ReminderReceiver", "Processing triggered broadcast alarm: ID=$reminderId | Title='$reminderTitle'")

            if (reminderId == -1) return

            // 1. Mark status as COMPLETED in Database and notify Active UI
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val reminder = db.reminderDao.getReminderById(reminderId)
                    if (reminder != null) {
                        db.reminderDao.updateReminderStatus(reminderId, "COMPLETED", System.currentTimeMillis())
                        Log.d("ReminderReceiver", "Updated reminder database status to COMPLETED.")
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            ActiveReminderManager.triggerReminder(reminder)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReminderReceiver", "Error accessing database in receiver", e)
                }
            }

            // 2. Vibrate and play Ringtone
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 400, 200, 400), -1)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Vibe trigger failed", e)
            }

            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) 
                                   ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Audio alert fail", e)
            }

            // 3. System notification setup
            showSystemNotification(context, reminderId, reminderTitle, reminderTime)

            // 4. TTS speech alert: "SYSTEM ALERT: CRICKET AT 8 IS DUE"
            speakTts(context, "SYSTEM ALERT: $reminderTitle is due!")

            // 5. Open Nova overlay if enabled (overlay permission is granted)
            if (android.provider.Settings.canDrawOverlays(context)) {
                try {
                    val overlayIntent = Intent(context, com.example.AssistActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(overlayIntent)
                    Log.d("ReminderReceiver", "Launched AssistActivity overlay automatically on reminder trigger.")
                } catch (e: Exception) {
                    Log.e("ReminderReceiver", "Failed to launch AssistActivity overlay", e)
                }
            }
        }
    }

    private fun showSystemNotification(context: Context, id: Int, title: String, time: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nova Active Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical time-sensitive reminders scheduled by Nova Assistant"
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, id, mainIntent, pFlags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Reminder Alert")
            .setContentText(title)
            .setSubText(time.ifEmpty { "Active Alert" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID_BASE + id, builder.build())
    }

    private fun speakTts(context: Context, text: String) {
        var localTts: TextToSpeech? = null
        localTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.US
                localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder_tts_id")
            }
        }
        tts = localTts
    }
}
