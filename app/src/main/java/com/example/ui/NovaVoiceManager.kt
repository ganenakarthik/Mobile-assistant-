package com.example.ui

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

object NovaVoiceManager {

    private const val PREFS_NAME = "nova_voice_system_prefs"
    private const val KEY_PROFILE = "pref_voice_profile"
    private const val KEY_SPEED = "pref_voice_speed"
    private const val KEY_PITCH = "pref_voice_pitch"
    private const val KEY_ENGINE_TYPE = "pref_engine_type"
    private const val KEY_COQUI_URL = "pref_coqui_url"
    private const val KEY_PIPER_URL = "pref_piper_url"

    data class VoiceProfile(
        val id: String,
        val name: String,
        val pitch: Float,
        val speed: Float,
        val gender: String = "Female",
        val age: String = "21 years",
        val accent: String = "US Soft",
        val description: String
    )

    val profiles = listOf(
        VoiceProfile(
            id = "soft",
            name = "Nova Soft",
            pitch = 1.15f,
            speed = 0.92f,
            description = "Gentle, warm, and highly relaxed with tender undertones."
        ),
        VoiceProfile(
            id = "professional",
            name = "Nova Professional",
            pitch = 1.02f,
            speed = 1.05f,
            accent = "US Neutral",
            description = "Clear, articulate, highly confident, and sharp pronunciation."
        ),
        VoiceProfile(
            id = "energetic",
            name = "Nova Energetic",
            pitch = 1.25f,
            speed = 1.18f,
            accent = "US Bright",
            description = "Enthusiastic, rapid pacing, playful, and cheerful."
        ),
        VoiceProfile(
            id = "calm",
            name = "Nova Calm",
            pitch = 0.95f,
            speed = 0.82f,
            accent = "US Serene",
            description = "Slow, reassuringly quiet, natural pauses, peaceful."
        )
    )

    private val _currentProfile = MutableStateFlow(profiles[0])
    val currentProfile = _currentProfile.asStateFlow()

    private val _customSpeed = MutableStateFlow(1.0f)
    val customSpeed = _customSpeed.asStateFlow()

    private val _customPitch = MutableStateFlow(1.0f)
    val customPitch = _customPitch.asStateFlow()

    private val _engineType = MutableStateFlow("NEURAL_SYSTEM") // NEURAL_SYSTEM, COQUI_LOCAL, PIPER_LOCAL
    val engineType = _engineType.asStateFlow()

    private val _coquiUrl = MutableStateFlow("http://10.0.2.2:5002/api/tts")
    val coquiUrl = _coquiUrl.asStateFlow()

    private val _piperUrl = MutableStateFlow("http://10.0.2.2:5000/tts")
    val piperUrl = _piperUrl.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing = _isSynthesizing.asStateFlow()

    private val _synthLogMessage = MutableStateFlow<String?>(null)
    val synthLogMessage = _synthLogMessage.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var currentMediaPlayer: MediaPlayer? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val profileId = prefs.getString(KEY_PROFILE, "soft") ?: "soft"
        val speedVal = prefs.getFloat(KEY_SPEED, 0.92f)
        val pitchVal = prefs.getFloat(KEY_PITCH, 1.15f)
        val engine = prefs.getString(KEY_ENGINE_TYPE, "NEURAL_SYSTEM") ?: "NEURAL_SYSTEM"
        val coqui = prefs.getString(KEY_COQUI_URL, "http://10.0.2.2:5002/api/tts") ?: "http://10.0.2.2:5002/api/tts"
        val piper = prefs.getString(KEY_PIPER_URL, "http://10.0.2.2:5000/tts") ?: "http://10.0.2.2:5000/tts"

        val matchingProfile = profiles.find { it.id == profileId } ?: profiles[0]
        _currentProfile.value = matchingProfile
        _customSpeed.value = speedVal
        _customPitch.value = pitchVal
        _engineType.value = engine
        _coquiUrl.value = coqui
        _piperUrl.value = piper
    }

    fun selectProfile(context: Context, profile: VoiceProfile) {
        _currentProfile.value = profile
        _customSpeed.value = profile.speed
        _customPitch.value = profile.pitch

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PROFILE, profile.id)
            .putFloat(KEY_SPEED, profile.speed)
            .putFloat(KEY_PITCH, profile.pitch)
            .apply()

        _synthLogMessage.value = "Calibrated to profile: ${profile.name} (Pitch: ${profile.pitch}, Speed: ${profile.speed})"
    }

    fun updateCustomSpeed(context: Context, speed: Float) {
        _customSpeed.value = speed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun updateCustomPitch(context: Context, pitch: Float) {
        _customPitch.value = pitch
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_PITCH, pitch).apply()
    }

    fun updateEngineType(context: Context, type: String) {
        _engineType.value = type
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENGINE_TYPE, type).apply()
        _synthLogMessage.value = "Engine switched to: $type"
    }

    fun updateCoquiUrl(context: Context, url: String) {
        _coquiUrl.value = url
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COQUI_URL, url).apply()
    }

    fun updatePiperUrl(context: Context, url: String) {
        _piperUrl.value = url
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIPER_URL, url).apply()
    }

    /**
     * Applies vocal speed & pitch parameter states directly into Sys TextToSpeech
     */
    fun applySettings(instance: TextToSpeech?) {
        if (instance == null) return
        try {
            // Calibrate to US Female voice characteristics
            instance.language = Locale.US
            
            // Set speed rate
            instance.setSpeechRate(_customSpeed.value)
            
            // Set fundamental frequency scale (pitch multiplier)
            instance.setPitch(_customPitch.value)
            
            // Query google engines for female voices
            val voices = instance.voices
            if (voices != null && voices.isNotEmpty()) {
                val bestVoice = voices.find { voice ->
                    val voiceName = voice.name.lowercase(Locale.ROOT)
                    (voiceName.contains("en-us") || voiceName.contains("en-gb")) &&
                            (voiceName.contains("female") || voiceName.contains("f-local") || voiceName.contains("x-f"))
                } ?: voices.find { it.locale.language == "en" }
                
                if (bestVoice != null) {
                    instance.voice = bestVoice
                }
            }
        } catch (e: Exception) {
            Log.e("NovaVoiceManager", "Error applying engine params: ${e.message}")
        }
    }

    /**
     * Triggers playback. Support system TTS fallback & external server synth.
     */
    fun speakInteractive(
        context: Context,
        text: String,
        tts: TextToSpeech?,
        completionId: String = "NOVA_DYNAMIC_SPEACH_ID",
        onComplete: (() -> Unit)? = null
    ) {
        val type = _engineType.value
        Log.i("NovaVoiceManager", "Speak active requested: '$text' with Type: $type")

        if (type == "FREE_AI_FEMALE") {
            _isSynthesizing.value = true
            _synthLogMessage.value = "Initiating acoustic cloud-stream link..."
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    stopCurrentMedia()
                    val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
                    val url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=en&client=tw-ob&q=$encodedText"
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw Exception("HTTP status: ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Null stream")
                    val byteStream = body.byteStream()

                    val cacheFile = File(context.cacheDir, "nova_acoustic_female.mp3")
                    if (cacheFile.exists()) { cacheFile.delete() }

                    val out = FileOutputStream(cacheFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    out.close()
                    byteStream.close()

                    mainScope.launch {
                        _isSynthesizing.value = false
                        _synthLogMessage.value = "Acoustic stream successfully synchronized."
                        playMediaFile(cacheFile, onComplete)
                    }
                } catch (e: Exception) {
                    Log.w("NovaVoiceManager", "Acoustic translation failed: ${e.message}")
                    mainScope.launch {
                        _isSynthesizing.value = false
                        _synthLogMessage.value = "Online stream offline. Shifting parameters to local TTS."
                        
                        applySettings(tts)
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, completionId)
                        onComplete?.invoke()
                    }
                }
            }
            return
        }

        if (type == "NEURAL_SYSTEM" || tts == null) {
            // Standard system TTS with applied parameters
            applySettings(tts)
            _synthLogMessage.value = "System text-to-speech output active at ${_customSpeed.value}x tempo"
            
            if (tts != null) {
                if (onComplete != null) {
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            mainScope.launch { onComplete() }
                        }
                        override fun onError(utteranceId: String?) {
                            mainScope.launch { onComplete() }
                        }
                    })
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, completionId)
            } else {
                onComplete?.invoke()
            }
            return
        }

        // External Neural Generation
        _isSynthesizing.value = true
        _synthLogMessage.value = "Connecting to $type synthesis network..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Terminate any running media players
                stopCurrentMedia()

                val request: Request
                if (type == "COQUI_LOCAL") {
                    val url = _coquiUrl.value
                    _synthLogMessage.value = "POST payload submission to Coqui server at $url"
                    
                    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                    val payload = """
                        {
                            "text": ${escapeJsonText(text)},
                            "speaker_id": "Nova",
                            "language_id": "en"
                        }
                    """.trimIndent()
                    
                    request = Request.Builder()
                        .url(url)
                        .post(payload.toRequestBody(jsonMediaType))
                        .build()
                } else {
                    // Piper TTS usually acts as a standard HTTP query GET or POST
                    val url = _piperUrl.value
                    _synthLogMessage.value = "Submitting REST query to Piper server at $url"
                    
                    val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
                    request = Request.Builder()
                        .url("$url?text=$encodedText&speaker=en_US-nova-medium")
                        .get()
                        .build()
                }

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Server responded with HTTP code: ${response.code}")
                }

                val body = response.body ?: throw Exception("Received null response body from neural host")
                val byteStream = body.byteStream()

                // Save audio file to cache folder
                val cacheFile = File(context.cacheDir, "nova_network_synthesis.wav")
                if (cacheFile.exists()) { cacheFile.delete() }

                val out = FileOutputStream(cacheFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                out.close()
                byteStream.close()

                mainScope.launch {
                    _isSynthesizing.value = false
                    _synthLogMessage.value = "${type} speech stream synthesis success! Playing audio pipeline."
                    playMediaFile(cacheFile, onComplete)
                }

            } catch (e: Exception) {
                Log.w("NovaVoiceManager", "Neural server connection failure: ${e.message}")
                mainScope.launch {
                    _isSynthesizing.value = false
                    _synthLogMessage.value = "Neural Host Server Unreachable. Dropping to Simulated System TTS."
                    
                    // Fallback to high-quality system TTS
                    applySettings(tts)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, completionId)
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun playMediaFile(file: File, onComplete: (() -> Unit)?) {
        try {
            currentMediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (currentMediaPlayer == it) {
                        currentMediaPlayer = null
                    }
                    onComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e("NovaVoiceManager", "Error playing synthesizer audio: ${e.message}")
            onComplete?.invoke()
        }
    }

    fun stopCurrentMedia() {
        try {
            currentMediaPlayer?.let {
                if (it.isPlaying) {
                     it.stop()
                }
                it.release()
            }
            currentMediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun escapeJsonText(text: String): String {
        return "\"" + text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }
}
