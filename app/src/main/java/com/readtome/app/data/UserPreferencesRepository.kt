package com.readtome.app.data

import android.content.Context
import android.content.SharedPreferences

enum class VoiceEngineType {
    AUTO_CASCADE, // ReadToMe Neural (Offline) -> ElevenLabs -> MiniMax -> Nativo (Fallback Final)
    ELEVEN_LABS,
    KOKORO,       // ReadToMe Neural (Kokoro-82M Offline)
    MINIMAX,
    NATIVE
}

class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("read_to_me_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_ELEVEN_LABS_API_KEY = "eleven_labs_api_key"
        private const val KEY_ELEVEN_LABS_VOICE_ID = "eleven_labs_voice_id"
        private const val KEY_MINIMAX_API_KEY = "minimax_api_key"
        private const val KEY_MINIMAX_VOICE_ID = "minimax_voice_id"
        private const val KEY_MINIMAX_MODEL = "minimax_model"
        private const val KEY_KOKORO_SPEAKER_ID = "kokoro_speaker_id"
        private const val KEY_VOICE_ENGINE = "voice_engine_type"
        private const val KEY_CLEAN_OCR_WITH_AI = "clean_ocr_with_ai"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_SHOW_FLOATING_BUBBLE = "show_floating_bubble"
        
        // Padrões
        const val DEFAULT_ELEVEN_VOICE_ID = "JBFqnCBsd6RMkjVDRZzb" // George (Premade - Grátis e Pago)
        const val DEFAULT_MINIMAX_VOICE_ID = "Portuguese_CaptivatingStoryteller"
        const val DEFAULT_MINIMAX_MODEL = "speech-2.8-hd"
        const val DEFAULT_KOKORO_SPEAKER_ID = 0 // Faber (Piper VITS On-Device BR)
    }

    var kokoroSpeakerId: Int
        get() = prefs.getInt(KEY_KOKORO_SPEAKER_ID, DEFAULT_KOKORO_SPEAKER_ID)
        set(value) = prefs.edit().putInt(KEY_KOKORO_SPEAKER_ID, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var elevenLabsApiKey: String
        get() = prefs.getString(KEY_ELEVEN_LABS_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ELEVEN_LABS_API_KEY, value.trim()).apply()

    var elevenLabsVoiceId: String
        get() = prefs.getString(KEY_ELEVEN_LABS_VOICE_ID, DEFAULT_ELEVEN_VOICE_ID) ?: DEFAULT_ELEVEN_VOICE_ID
        set(value) = prefs.edit().putString(KEY_ELEVEN_LABS_VOICE_ID, value.trim()).apply()

    var minimaxApiKey: String
        get() = prefs.getString(KEY_MINIMAX_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MINIMAX_API_KEY, value.trim()).apply()

    var minimaxVoiceId: String
        get() = prefs.getString(KEY_MINIMAX_VOICE_ID, DEFAULT_MINIMAX_VOICE_ID) ?: DEFAULT_MINIMAX_VOICE_ID
        set(value) = prefs.edit().putString(KEY_MINIMAX_VOICE_ID, value.trim()).apply()

    var minimaxModel: String
        get() = prefs.getString(KEY_MINIMAX_MODEL, DEFAULT_MINIMAX_MODEL) ?: DEFAULT_MINIMAX_MODEL
        set(value) = prefs.edit().putString(KEY_MINIMAX_MODEL, value.trim()).apply()

    var voiceEngine: VoiceEngineType
        get() {
            val name = prefs.getString(KEY_VOICE_ENGINE, VoiceEngineType.AUTO_CASCADE.name)
            return try {
                VoiceEngineType.valueOf(name ?: VoiceEngineType.AUTO_CASCADE.name)
            } catch (e: Exception) {
                VoiceEngineType.AUTO_CASCADE
            }
        }
        set(value) = prefs.edit().putString(KEY_VOICE_ENGINE, value.name).apply()

    var isAiCleaningEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLEAN_OCR_WITH_AI, true)
        set(value) = prefs.edit().putBoolean(KEY_CLEAN_OCR_WITH_AI, value).apply()

    var isFloatingBubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FLOATING_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_FLOATING_BUBBLE, value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
}
