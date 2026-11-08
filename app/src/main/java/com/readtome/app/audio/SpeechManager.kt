package com.readtome.app.audio

import android.content.Context
import android.util.Log
import com.readtome.app.data.UserPreferencesRepository
import com.readtome.app.data.VoiceEngineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gerenciador unificado de síntese de voz com Cascata Inteligente de Fallback:
 * 1. ReadToMe Neural Offline (Principal: Ultra-rápido, privacidade total e sem consumo de dados)
 * 2. ElevenLabs (Fallback Nuvem: Ultra-realismo de estúdio com IA generativa)
 * 3. MiniMax (Fallback Alternativo Nuvem)
 * 4. Android Nativo (Fallback Final: 100% offline para garantir que a fala nunca pare)
 */
class SpeechManager(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) : TtsEngine {

    companion object {
        private const val TAG = "SpeechManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val nativeEngine = AndroidNativeTtsEngine(context)
    val elevenLabsEngine = ElevenLabsTtsEngine(context, preferences)
    val kokoroEngine = KokoroTtsEngine(context, preferences)
    val miniMaxEngine = MiniMaxTtsEngine(context, preferences)

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override val currentSpeed: Float
        get() = activeEngine.currentSpeed

    private var activeEngine: TtsEngine = nativeEngine

    init {
        // Observa os estados dos motores
        scope.launch {
            nativeEngine.playbackState.collect { state ->
                if (activeEngine === nativeEngine) {
                    _playbackState.value = state
                }
            }
        }
        scope.launch {
            elevenLabsEngine.playbackState.collect { state ->
                if (activeEngine === elevenLabsEngine) {
                    _playbackState.value = state
                }
            }
        }
        scope.launch {
            kokoroEngine.playbackState.collect { state ->
                if (activeEngine === kokoroEngine) {
                    _playbackState.value = state
                }
            }
        }
        scope.launch {
            miniMaxEngine.playbackState.collect { state ->
                if (activeEngine === miniMaxEngine) {
                    _playbackState.value = state
                }
            }
        }

        // Fallback do ReadToMe Neural (Kokoro Offline) -> ElevenLabs -> MiniMax -> Nativo
        kokoroEngine.onErrorFallback = { remainingText, speed ->
            Log.w(TAG, "ReadToMe Neural Offline falhou ou modelo ausente. Ativando fallback...")
            val fallbackEngine = if (preferences.voiceEngine == VoiceEngineType.AUTO_CASCADE) {
                if (preferences.elevenLabsApiKey.isNotBlank()) {
                    elevenLabsEngine
                } else if (preferences.minimaxApiKey.isNotBlank()) {
                    miniMaxEngine
                } else {
                    nativeEngine
                }
            } else {
                nativeEngine
            }

            scope.launch(Dispatchers.Main) {
                val label = when (fallbackEngine) {
                    elevenLabsEngine -> "ElevenLabs (Nuvem)"
                    miniMaxEngine -> "MiniMax"
                    else -> "Android Nativo"
                }
                android.widget.Toast.makeText(
                    context,
                    "ReadToMe Neural indisponível. Alternando para $label.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            activeEngine = fallbackEngine
            fallbackEngine.speak(remainingText, speed)
        }

        // Fallback da ElevenLabs -> MiniMax -> Android Nativo
        elevenLabsEngine.onQuotaExceeded = { remainingText, speed ->
            Log.w(TAG, "ElevenLabs sem créditos/cota. Ativando fallback...")
            val fallbackEngine = if (preferences.minimaxApiKey.isNotBlank()) {
                miniMaxEngine
            } else {
                nativeEngine
            }

            scope.launch(Dispatchers.Main) {
                val label = if (fallbackEngine === miniMaxEngine) "MiniMax" else "Android Nativo offline"
                android.widget.Toast.makeText(
                    context,
                    "ElevenLabs sem cota. Alternando para $label...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            activeEngine = fallbackEngine
            fallbackEngine.speak(remainingText, speed)
        }

        // Fallback da MiniMax -> Android Nativo
        miniMaxEngine.onQuotaExceeded = { remainingText, speed ->
            Log.w(TAG, "MiniMax sem créditos/cota. Ativando fallback para Android Nativo...")
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "MiniMax sem saldo. Usando voz Nativa offline.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            activeEngine = nativeEngine
            nativeEngine.speak(remainingText, speed)
        }
    }

    override fun speak(text: String, speed: Float) {
        val targetEngine = determineInitialEngine()

        if (activeEngine !== targetEngine) {
            activeEngine.stop()
            activeEngine = targetEngine
        }

        Log.i(TAG, "Iniciando síntese de voz usando: ${activeEngine.javaClass.simpleName}")
        activeEngine.speak(text, speed)
    }

    private fun determineInitialEngine(): TtsEngine {
        return when (preferences.voiceEngine) {
            VoiceEngineType.AUTO_CASCADE -> {
                // Cascata inteligente solicitada: ReadToMe Neural (Offline) ➔ ElevenLabs ➔ MiniMax ➔ Nativo
                if (kokoroEngine.isModelInstalled()) {
                    kokoroEngine
                } else if (preferences.elevenLabsApiKey.isNotBlank()) {
                    elevenLabsEngine
                } else if (preferences.minimaxApiKey.isNotBlank()) {
                    miniMaxEngine
                } else {
                    nativeEngine
                }
            }
            VoiceEngineType.ELEVEN_LABS -> {
                if (preferences.elevenLabsApiKey.isNotBlank()) elevenLabsEngine else nativeEngine
            }
            VoiceEngineType.KOKORO -> {
                if (kokoroEngine.isModelInstalled()) kokoroEngine else nativeEngine
            }
            VoiceEngineType.MINIMAX -> {
                if (preferences.minimaxApiKey.isNotBlank()) miniMaxEngine else nativeEngine
            }
            VoiceEngineType.NATIVE -> nativeEngine
        }
    }

    override fun pause() {
        activeEngine.pause()
    }

    override fun resume() {
        activeEngine.resume()
    }

    override fun stop() {
        kokoroEngine.stop()
        nativeEngine.stop()
        elevenLabsEngine.stop()
        miniMaxEngine.stop()
        _playbackState.value = PlaybackState.Idle
    }

    override fun setPlaybackSpeed(speed: Float) {
        kokoroEngine.setPlaybackSpeed(speed)
        nativeEngine.setPlaybackSpeed(speed)
        elevenLabsEngine.setPlaybackSpeed(speed)
        miniMaxEngine.setPlaybackSpeed(speed)
    }

    override fun release() {
        kokoroEngine.release()
        nativeEngine.release()
        elevenLabsEngine.release()
        miniMaxEngine.release()
    }
}
