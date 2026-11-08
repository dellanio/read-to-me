package com.readtome.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.speech.tts.Voice
import com.readtome.app.data.UserPreferencesRepository
import java.util.Locale
import java.util.UUID

/**
 * Motor nativo de TTS do Android com controle completo de pausa, retomada e parada.
 */
class AndroidNativeTtsEngine(
    private val context: Context,
    private val preferences: UserPreferencesRepository? = null
) : TtsEngine, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "NativeTtsEngine"
        private const val UTTERANCE_PREFIX = "readtome_utt_"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override var currentSpeed: Float = 1.0f
        private set

    private var sentences: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var isPaused = false

    private val audioFocusController = AudioFocusController(
        context = context,
        onFocusLost = { isTransient ->
            if (_playbackState.value is PlaybackState.Playing) {
                pause()
            }
        },
        onFocusGained = {
            if (isPaused) {
                resume()
            }
        }
    )

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsInstance = tts ?: return
            applyLanguageAndVoice()
            ttsInstance.setSpeechRate(currentSpeed)
            setupUtteranceListener(ttsInstance)
            isInitialized = true
            Log.i(TAG, "TextToSpeech inicializado com sucesso.")
        } else {
            Log.e(TAG, "Falha ao inicializar o motor nativo TextToSpeech.")
            _playbackState.value = PlaybackState.Error("Falha ao inicializar o sintetizador de voz do Android.")
        }
    }

    fun applyLanguageAndVoice(localeTag: String? = null, preferMale: Boolean? = null) {
        val ttsInstance = tts ?: return
        val targetTag = localeTag ?: preferences?.speechLanguage ?: "pt-BR"
        val isMale = preferMale ?: preferences?.nativeVoiceMale ?: true

        val targetLocale = Locale.forLanguageTag(targetTag)
        val result = ttsInstance.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "$targetTag não disponível, usando idioma padrão.")
            ttsInstance.language = Locale.getDefault()
        }

        try {
            val availableVoices = ttsInstance.voices ?: emptySet()
            val localeVoices = availableVoices.filter {
                it.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                (!it.isNetworkConnectionRequired)
            }

            if (isMale && localeVoices.isNotEmpty()) {
                val maleVoice = localeVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    name.contains("male") || name.contains("man") || name.contains("homem") ||
                    name.contains("masc") || name.contains("afs") || name.contains("yfj") ||
                    name.contains("ptd") || name.contains("br-local-m") || name.contains("en-us-x-sfg") ||
                    name.contains("en-us-x-iom")
                } ?: localeVoices.firstOrNull { voice ->
                    voice.features?.any { it.contains("male", ignoreCase = true) } == true
                }

                if (maleVoice != null) {
                    ttsInstance.voice = maleVoice
                    ttsInstance.setPitch(1.0f)
                    Log.i(TAG, "Voz nativa masculina selecionada: ${maleVoice.name}")
                } else {
                    ttsInstance.setPitch(0.85f)
                    Log.i(TAG, "Nenhuma voz explicitamente masculina. Aplicando tom 0.85f para timbre masculino.")
                }
            } else if (!isMale && localeVoices.isNotEmpty()) {
                val femaleVoice = localeVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    name.contains("female") || name.contains("mulher") || name.contains("fem") ||
                    name.contains("br-local-f") || name.contains("en-us-x-tpf")
                }
                if (femaleVoice != null) {
                    ttsInstance.voice = femaleVoice
                }
                ttsInstance.setPitch(1.0f)
            } else if (isMale) {
                ttsInstance.setPitch(0.85f)
            } else {
                ttsInstance.setPitch(1.0f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao configurar voz nativa: ${e.message}")
            if (isMale) {
                ttsInstance.setPitch(0.85f)
            }
        }
    }

    private fun setupUtteranceListener(ttsInstance: TextToSpeech) {
        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    if (currentIndex < sentences.size && !isPaused) {
                        _playbackState.value = PlaybackState.Playing(
                            currentSentence = sentences[currentIndex],
                            currentIndex = currentIndex,
                            totalSentences = sentences.size,
                            speed = currentSpeed
                        )
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (isPaused) return@launch

                    currentIndex++
                    if (currentIndex < sentences.size) {
                        speakCurrentSentence()
                    } else {
                        // Concluiu todo o texto
                        _playbackState.value = PlaybackState.Idle
                        audioFocusController.abandonFocus()
                    }
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Chamado pelo Android quando tts.stop() é executado (pause/stop intencional)
                Log.d(TAG, "Utterance interrompida intencionalmente: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    if (isPaused || sentences.isEmpty()) {
                        // Interrupção intencional para pause ou stop, ignora falso erro
                        return@launch
                    }
                    Log.w(TAG, "Erro na síntese da frase: $utteranceId")
                    _playbackState.value = PlaybackState.Error("Erro durante a síntese de voz nativa.")
                    audioFocusController.abandonFocus()
                }
            }
        })
    }

    override fun speak(text: String, speed: Float) {
        if (text.isBlank()) return
        currentSpeed = speed

        val chunks = TextChunker.splitIntoSentences(text)
        if (chunks.isEmpty()) return

        sentences = chunks
        currentIndex = 0
        isPaused = false

        if (!audioFocusController.requestFocus()) {
            Log.w(TAG, "AudioFocus não concedido. Abortando fala.")
            return
        }

        _playbackState.value = PlaybackState.Buffering("Iniciando leitura...")

        if (isInitialized) {
            tts?.setSpeechRate(currentSpeed)
            speakCurrentSentence()
        } else {
            scope.launch {
                var waitCount = 0
                while (!isInitialized && waitCount < 30) {
                    kotlinx.coroutines.delay(100)
                    waitCount++
                }
                if (isInitialized) {
                    tts?.setSpeechRate(currentSpeed)
                    speakCurrentSentence()
                } else {
                    _playbackState.value = PlaybackState.Error("Tempo limite para inicializar voz esgotado.")
                }
            }
        }
    }

    private fun speakCurrentSentence() {
        if (currentIndex !in sentences.indices || isPaused) {
            return
        }

        val text = sentences[currentIndex]
        val utteranceId = "$UTTERANCE_PREFIX${UUID.randomUUID()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun pause() {
        if (_playbackState.value is PlaybackState.Playing) {
            isPaused = true
            tts?.stop()
            val currentSentence = sentences.getOrElse(currentIndex) { "" }
            _playbackState.value = PlaybackState.Paused(
                currentSentence = currentSentence,
                currentIndex = currentIndex,
                totalSentences = sentences.size
            )
            Log.i(TAG, "Leitura pausada na sentença $currentIndex/${sentences.size}")
        }
    }

    override fun resume() {
        if (isPaused && sentences.isNotEmpty() && currentIndex < sentences.size) {
            isPaused = false
            audioFocusController.requestFocus()
            val currentSentence = sentences.getOrElse(currentIndex) { "" }
            _playbackState.value = PlaybackState.Playing(
                currentSentence = currentSentence,
                currentIndex = currentIndex,
                totalSentences = sentences.size,
                speed = currentSpeed
            )
            Log.i(TAG, "Retomando leitura na sentença $currentIndex/${sentences.size}")
            speakCurrentSentence()
        }
    }

    override fun stop() {
        isPaused = false
        sentences = emptyList()
        currentIndex = 0
        tts?.stop()
        _playbackState.value = PlaybackState.Idle
        audioFocusController.abandonFocus()
        Log.i(TAG, "Leitura interrompida pelo usuário.")
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        tts?.setSpeechRate(speed)
        val currentState = _playbackState.value
        if (currentState is PlaybackState.Playing) {
            _playbackState.value = currentState.copy(speed = speed)
        }
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
