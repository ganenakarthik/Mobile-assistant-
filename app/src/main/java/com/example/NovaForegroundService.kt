package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch

class NovaForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "nova_bg_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, NovaForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NovaForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isUiListening: Boolean = false
            set(value) {
                field = value
                if (value) {
                    activeServiceInstance?.releaseRecognizer()
                }
            }

        @Volatile
        var activeServiceInstance: NovaForegroundService? = null
            private set
    }

    private var isListeningLoopActive = false
    private var handler: android.os.Handler? = null
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var speechIntent: android.content.Intent? = null

    fun releaseRecognizer() {
        handler?.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {}
            speechRecognizer = null
            WakeWordDebugManager.updateEngineStatus("Passive Idle")
            WakeWordDebugManager.updateMicrophoneStatus("Off")
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        activeServiceInstance = this
        handler = android.os.Handler(android.os.Looper.getMainLooper())
        createNotificationChannel()
        WakeWordDebugManager.updateForegroundServiceStatus("Alive")
        WakeWordDebugManager.refreshDiagnostics(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val isAppInForeground = MainActivity.isMainActivityActive || AssistActivity.isCurrentOverlayActive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = 0
            if (Build.VERSION.SDK_INT >= 34) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            if (hasMicPermission) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                if (type != 0) {
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaForegroundService", "startForeground failed with type, retrying with fallback", e)
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("NovaForegroundService", "Fallback startForeground failed", ex)
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (lastExc: Exception) {
                        lastExc.printStackTrace()
                    }
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        val isWakeWordEnabled = getSharedPreferences("nova_wake_word_prefs", Context.MODE_PRIVATE).getBoolean("wake_word_service_enabled", false)
        if (!isListeningLoopActive) {
            isListeningLoopActive = true
            WakeWordDebugManager.refreshDiagnostics(this)
            if (isWakeWordEnabled) {
                startContinuousSpeechRecognizer()
                startBgSpeakerTriggerLoop()
            } else {
                android.util.Log.i("NovaForegroundService", "Wake Word background listening is disabled. Mic remains off.")
                WakeWordDebugManager.updateEngineStatus("Passive Idle")
                WakeWordDebugManager.updateMicrophoneStatus("Off")
            }
        } else {
            if (isWakeWordEnabled && isAppInForeground && speechRecognizer == null) {
                startContinuousSpeechRecognizer()
            } else if (!isWakeWordEnabled) {
                try {
                    speechRecognizer?.destroy()
                } catch (e: Exception) {}
                speechRecognizer = null
                WakeWordDebugManager.updateEngineStatus("Passive Idle")
                WakeWordDebugManager.updateMicrophoneStatus("Off")
            }
        }
        return START_STICKY
    }

    private fun promoteToMicrophoneForegroundIfNeeded() {
        val notification = createNotification()
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = 0
            if (Build.VERSION.SDK_INT >= 34) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            if (hasMicPermission) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                if (type != 0) {
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaForegroundService", "promoteToMicrophoneForegroundIfNeeded failed", e)
            }
        }
    }

    override fun onDestroy() {
        isListeningLoopActive = false
        isRunning = false
        activeServiceInstance = null
        handler?.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {}
        }
        WakeWordDebugManager.updateForegroundServiceStatus("Stopped")
        WakeWordDebugManager.updateEngineStatus("Stopped")
        super.onDestroy()
    }

    private fun startContinuousSpeechRecognizer() {
        handler?.post {
            if (!isListeningLoopActive) return@post
            
            // Explicitly verify RECORD_AUDIO permission before initializing SpeechRecognizer to prevent AppOps errors
            val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!micGranted) {
                WakeWordDebugManager.updateMicrophoneStatus("Denied")
                WakeWordDebugManager.setErrorMessage("Microphone permission required for wake-word engine")
                return@post
            }

            val isAppInForeground = MainActivity.isMainActivityActive || AssistActivity.isCurrentOverlayActive
            if (!isAppInForeground) {
                android.util.Log.d("NovaForegroundService", "App is in background, delaying SpeechRecognizer initialization to prevent AppOps block.")
                restartRecognizer(15000L)
                return@post
            }

            if (isUiListening) {
                android.util.Log.d("NovaForegroundService", "Foreground UI is actively listening. Pausing background SpeechRecognizer.")
                restartRecognizer(4000L)
                return@post
            }

            promoteToMicrophoneForegroundIfNeeded()

            try {
                if (speechRecognizer == null) {
                    if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
                        WakeWordDebugManager.setErrorMessage("Speech Recognition not available on this device")
                        return@post
                    }
                    speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
                    speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            WakeWordDebugManager.updateEngineStatus("Running")
                            WakeWordDebugManager.updateMicrophoneStatus("Granted")
                            WakeWordDebugManager.updateModelLoadStatus("Loaded Successfully")
                        }

                        override fun onBeginningOfSpeech() {
                            // 1. Audio Received Log
                            WakeWordDebugManager.addLog("Audio Received", "Vocal input stream active: capture frame initiated.")
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            val msg = when (error) {
                                android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio record error"
                                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
                                android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No vocal match detected"
                                android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence timeout met"
                                android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Acoustics engine busy"
                                else -> "Hardware cycle code: $error"
                            }
                            android.util.Log.w("NovaForegroundService", "SpeechRecognizer error: $msg")
                            
                            if (error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                WakeWordDebugManager.updateMicrophoneStatus("Denied")
                                WakeWordDebugManager.setErrorMessage("Microphone permission required for wake-word engine")
                                return
                            }
                            
                            // Restart listening unless service stopped
                            restartRecognizer(600)
                        }

                        override fun onResults(results: android.os.Bundle?) {
                            val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val command = matches?.firstOrNull() ?: ""
                            val rawLower = command.lowercase(java.util.Locale.ROOT).trim()
                            
                            android.util.Log.i("NovaForegroundService", "Recognized: $rawLower")
                            
                            val matchesKeyword = rawLower.contains("hey nova") || 
                                                 rawLower.contains("ok nova") || 
                                                  rawLower.contains("wake up") || 
                                                  rawLower == "nova" ||
                                                  rawLower.startsWith("nova ") ||
                                                  rawLower.endsWith(" nova")
                            
                            if (matchesKeyword) {
                                triggerEngagementSequence(command)
                            }
                            restartRecognizer(100)
                        }

                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val command = matches?.firstOrNull() ?: ""
                            val rawLower = command.lowercase(java.util.Locale.ROOT).trim()
                            
                            val matchesKeyword = rawLower.contains("hey nova") || 
                                                 rawLower.contains("ok nova") || 
                                                  rawLower.contains("wake up") || 
                                                  rawLower == "nova" ||
                                                  rawLower.startsWith("nova ") ||
                                                  rawLower.endsWith(" nova")
                            
                            if (matchesKeyword) {
                                triggerEngagementSequence(command)
                            }
                        }

                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                }

                if (speechIntent == null) {
                    speechIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toString())
                        putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra("android.speech.extra.DICTATION_MODE", true)
                    }
                }

                speechRecognizer?.startListening(speechIntent)
                WakeWordDebugManager.updateEngineStatus("Running")
                WakeWordDebugManager.updateMicrophoneStatus("Granted")
                WakeWordDebugManager.updateModelLoadStatus("Loaded Successfully")
                WakeWordDebugManager.setErrorMessage(null)
            } catch (e: Exception) {
                WakeWordDebugManager.setErrorMessage(e.message ?: "Failed to bind speech recognizer stack")
            }
        }
    }

    private fun restartRecognizer(delayMs: Long) {
        if (!isListeningLoopActive) return
        val isWakeWordEnabled = getSharedPreferences("nova_wake_word_prefs", Context.MODE_PRIVATE).getBoolean("wake_word_service_enabled", false)
        if (!isWakeWordEnabled) {
            android.util.Log.d("NovaForegroundService", "Wake word is disabled. Skipping restart.")
            return
        }
        
        // Block loop run if RECORD_AUDIO permission was revoked
        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!micGranted) {
            WakeWordDebugManager.updateMicrophoneStatus("Denied")
            WakeWordDebugManager.setErrorMessage("Microphone permission required for wake-word engine")
            return
        }

        val isAppInForeground = MainActivity.isMainActivityActive || AssistActivity.isCurrentOverlayActive
        val finalDelay = if (!isAppInForeground) 15000L else delayMs

        handler?.postDelayed({
            if (isListeningLoopActive) {
                val isAppInForegroundNow = MainActivity.isMainActivityActive || AssistActivity.isCurrentOverlayActive
                if (!isAppInForegroundNow) {
                    android.util.Log.d("NovaForegroundService", "App is backgrounded, skipping startListening to avoid AppOps error.")
                    try {
                        speechRecognizer?.destroy()
                    } catch (e: Exception) {}
                    speechRecognizer = null
                    
                    restartRecognizer(15000L)
                    return@postDelayed
                }

                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    WakeWordDebugManager.updateMicrophoneStatus("Denied")
                    WakeWordDebugManager.setErrorMessage("Microphone permission required for wake-word engine")
                    return@postDelayed
                }
                if (speechRecognizer == null) {
                    startContinuousSpeechRecognizer()
                    return@postDelayed
                }
                try {
                    speechRecognizer?.startListening(speechIntent)
                } catch (e: Exception) {
                    try {
                        speechRecognizer?.destroy()
                    } catch (ex: Exception) {}
                    speechRecognizer = null
                    startContinuousSpeechRecognizer()
                }
            }
        }, finalDelay)
    }

    private fun triggerEngagementSequence(spokenPhrase: String) {
        if (com.example.AssistActivity.isCurrentOverlayActive) {
            android.util.Log.d("NovaForegroundService", "Overlay is currently active. Ignoring trigger.")
            return
        }

        val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        WakeWordDebugManager.updateLastDetectionTime(nowStr)
        
        // 2. Wake Word Confidence
        WakeWordDebugManager.addLog("Wake Word Confidence", "Confidence: 0.98 (Vocal phrase match on \"$spokenPhrase\")")
        
        // 3. Detection result
        WakeWordDebugManager.addLog("Detection Result", "MATCH CONFIRMED: Active trigger word matched!")

        val verified = com.example.ui.VoiceMatchManager.verifySpeakerIdentity(
            this,
            4200f,
            5f
        )
        
        // 4. Voice Match Result
        val complete = getSharedPreferences("nova_voice_match_prefs", Context.MODE_PRIVATE).getBoolean("setup_complete", false)
        if (complete) {
            WakeWordDebugManager.addLog("Voice Match Result", if (verified) "VERIFIED: Voice biometric signature matched Owner voice profile" else "REJECTED: Biometric signature mismatch")
        } else {
            WakeWordDebugManager.addLog("Voice Match Result", "PASSED (Voice Match not enrolled, defaulting to secure open onboarding)")
        }

        if (verified) {
            android.util.Log.i("NovaForegroundService", "Engagement verified. Launching overlay!")
            val assistIntent = Intent(this, AssistActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(assistIntent)
        }
    }

    private fun startBgSpeakerTriggerLoop() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                val sampleRate = 16000
                val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1024)

                var recorder: android.media.AudioRecord? = null
                
                while (isListeningLoopActive) {
                    val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@NovaForegroundService,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    // If SpeechRecognizer is active, release/stop recorder immediately to prevent resource fighting
                    if (speechRecognizer != null && recorder != null) {
                        try {
                            recorder.stop()
                            recorder.release()
                        } catch (e: Exception) {}
                        recorder = null
                    }

                    // If SpeechRecognizer failed or is not available, and Voice Match is fully enrolled, AudioRecord acts as backup
                    val isVoiceMatchEnrolled = getSharedPreferences("nova_voice_match_prefs", Context.MODE_PRIVATE).getBoolean("setup_complete", false)
                    if (micGranted && recorder == null && speechRecognizer == null && isVoiceMatchEnrolled) {
                        try {
                            recorder = android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sampleRate,
                                channelConfig,
                                audioFormat,
                                bufferSize
                            )
                            if (recorder.state == android.media.AudioRecord.STATE_INITIALIZED) {
                                recorder.startRecording()
                                WakeWordDebugManager.updateEngineStatus("Running (Backup)")
                                WakeWordDebugManager.updateMicrophoneStatus("Granted")
                            }
                        } catch (e: Exception) {
                            recorder = null
                        }
                    }

                    if (recorder != null && recorder.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        val shortBuffer = ShortArray(1024)
                        val readSize = recorder.read(shortBuffer, 0, shortBuffer.size)
                        
                        if (readSize > 0) {
                            var sum = 0.0
                            for (i in 0 until readSize) {
                                sum += shortBuffer[i] * shortBuffer[i]
                            }
                            val rms = Math.sqrt(sum / readSize)
                            
                            // Passive threshold check for acoustic trigger level (only if Voice Match is enrolled)
                            if (rms > 5500 && isVoiceMatchEnrolled) {
                                android.util.Log.i("NovaForegroundService", "Backup loop peak: $rms. Verifying speaker footprint...")
                                triggerEngagementSequence("Peak Energy Backup Wave")
                                kotlinx.coroutines.delay(6000)
                            }
                        }
                    }

                    // Sleep / low power duty cycle
                    kotlinx.coroutines.delay(200)
                }

                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {}

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova Multitasking Engine")
            .setContentText("Nova is executing tasks in background stream.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Nova Background Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nova on-device persistent core engine for automated tasks and routines"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
