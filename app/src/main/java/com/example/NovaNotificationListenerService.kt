package com.example

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NovaNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isClearable: Boolean,
    val key: String
)

class NovaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NovaNotificationListener"
        
        var instance: NovaNotificationListenerService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        private val _notifications = MutableStateFlow<List<NovaNotification>>(emptyList())
        val notifications = _notifications.asStateFlow()

        /**
         * Dismiss/cancel a notification by key
         */
        fun dismissNotification(key: String): Boolean {
            val serviceInstance = instance
            if (serviceInstance != null) {
                try {
                    serviceInstance.cancelNotification(key)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel notification: ${e.localizedMessage}")
                }
            }
            return false
        }

        /**
         * Reply to a target notification using RemoteInput if present
         */
        fun replyToNotification(key: String, replyMessage: String): Boolean {
            val serviceInstance = instance ?: return false
            try {
                val active = serviceInstance.activeNotifications ?: return false
                val targetSbn = active.firstOrNull { it.key == key } ?: return false
                val notification = targetSbn.notification ?: return false
                
                // Find Wear/RemoteInput actions
                val actions = notification.actions ?: return false
                for (action in actions) {
                    val remoteInputs = action.remoteInputs ?: continue
                    for (remoteInput in remoteInputs) {
                        val replyBundle = Bundle().apply {
                            putCharSequence(remoteInput.resultKey, replyMessage)
                        }
                        val intent = android.content.Intent()
                        val resultsContext = Bundle()
                        android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, replyBundle)
                        
                        action.actionIntent.send(serviceInstance, 0, intent)
                        Log.i(TAG, "Notification reply sent via RemoteInput successfully.")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error replying to notification: ${e.localizedMessage}")
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotifications()
        Log.i(TAG, "Nova Notification Listener Core fully connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "Nova Notification Listener Core disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateNotifications()
    }

    private fun updateNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = active.mapNotNull { sbn ->
                val extras = sbn.notification?.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
                
                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                NovaNotification(
                    id = sbn.id.toString(),
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    isClearable = sbn.isClearable,
                    key = sbn.key
                )
            }.sortedByDescending { it.postTime }

            _notifications.value = list.take(20)
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating notification list: ${e.localizedMessage}")
        }
    }
}
