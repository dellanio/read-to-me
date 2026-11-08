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
 * 1. ElevenLabs (Principal: Ultra-realismo de estúdio)
 * 2. MiniMax (Fallback: Vozes naturais em Português quando acabar créditos da ElevenLabs)
 * 3. Android Nativo (Fallback Final: 100% offline e ilimitado para garantir que a fala nunca pare)
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
    val miniMaxEngine = MiniMaxTtsEngine(context, preferences)

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override val currentSpeed: Float
        get() = activeEngine.currentSpeed

    private var activeEngine: TtsEngine = nativeEngine

    init {
        // Observa os estados dos 3 motores
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
            miniMaxEngine.playbackState.collect { state ->
                if (activeEngine === miniMaxEngine) {
                    _playbackState.value = state
                }
            }
        }

        // Configuração da Cascata de Fallback: ElevenLabs -> MiniMax
        elevenLabsEngine.onQuotaExceeded = { remainingText, speed ->
            Log.w(TAG, "ElevenLabs sem créditos/cota. Ativando fallback para MiniMax...")
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "ElevenLabs sem cota/créditos. Usando voz de contingência...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            if (preferences.minimaxApiKey.isNotBlank()) {
                activeEngine = miniMaxEngine
                miniMaxEngine.speak(remainingText, speed)
            } else {
                Log.w(TAG, "MiniMax não configurada. Ativando fallback para Android Nativo...")
                activeEngine = nativeEngine
                nativeEngine.speak(remainingText, speed)
            }
        }

        // Fallback da MiniMax -> Android Nativo
        miniMaxEngine.onQuotaExceeded = { remainingText, speed ->
            Log.w(TAG, "MiniMax sem créditos/cota. Ativando fallback para Android Nativo...")
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "MiniMax sem saldo (cota 1008). Usando voz Nativa offline.",
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
                // Cascata inteligente: ElevenLabs primeiro se configurada, senão MiniMax, senão Nativo
                if (preferences.elevenLabsApiKey.isNotBlank()) {
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
        nativeEngine.stop()
        elevenLabsEngine.stop()
        miniMaxEngine.stop()
        _playbackState.value = PlaybackState.Idle
    }

    override fun setPlaybackSpeed(speed: Float) {
        nativeEngine.setPlaybackSpeed(speed)
        elevenLabsEngine.setPlaybackSpeed(speed)
        miniMaxEngine.setPlaybackSpeed(speed)
    }

    override fun release() {
        nativeEngine.release()
        elevenLabsEngine.release()
        miniMaxEngine.release()
    }
}
