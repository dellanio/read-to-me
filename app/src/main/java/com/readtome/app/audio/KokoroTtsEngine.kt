package com.readtome.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.readtome.app.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Motor de síntese de voz 100% On-Device (Offline) de alta fidelidade
 * baseado no modelo Kokoro-82M executado localmente via Sherpa-ONNX.
 *
 * Não consome internet, não tem custo de API e não expõe dados do usuário.
 */
class KokoroTtsEngine(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) : TtsEngine {

    companion object {
        private const val TAG = "KokoroTts"

        // Vozes em Português do Brasil
        const val SPEAKER_FABER_BR = 0   // Faber (Masculino BR - Piper VITS On-Device)
        const val SPEAKER_PF_DORA = 42   // Dora (Feminino BR - Kokoro)
        const val SPEAKER_PM_ALEX = 43   // Alex (Masculino BR - Kokoro)
        const val SPEAKER_PM_SANTA = 44  // Santa (Masculino BR - Kokoro)

        // Vozes em Inglês
        const val SPEAKER_AF_SARAH = 9   // Sarah (Feminino US)
        const val SPEAKER_AM_MICHAEL = 16 // Michael (Masculino US)
        const val SPEAKER_BM_GEORGE = 26 // George (Masculino UK)
    }

    enum class EngineBackend {
        SUPERTONIC,
        PIPER_VITS,
        KOKORO
    }

    private var activeBackend: EngineBackend? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var synthesisJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: OfflineTts? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override var currentSpeed: Float = 1.0f
        private set

    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = 0
    private var isPaused = false

    /** Callback para fallback automático caso ocorra erro crítico ou falta de modelo */
    var onErrorFallback: ((remainingText: String, speed: Float) -> Unit)? = null

    fun getRemainingText(): String {
        return chunks.drop(currentChunkIndex).joinToString(". ")
    }

    private val audioFocusController = AudioFocusController(
        context = context,
        onFocusLost = {
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

    /**
     * Localiza a pasta onde os arquivos do modelo Supertonic-3 (Português & Multilíngue) estão instalados.
     */
    fun getSupertonicModelDir(): File? {
        val candidates = listOfNotNull(
            File(context.filesDir, "supertonic"),
            File(context.filesDir, "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"),
            context.getExternalFilesDir(null)?.resolve("supertonic"),
            File("/sdcard/Android/data/${context.packageName}/files/supertonic")
        )
        val dir = candidates.firstOrNull { File(it, "duration_predictor.int8.onnx").exists() && File(it, "voice.bin").exists() }
        Log.i(TAG, "getSupertonicModelDir() resolvido: ${dir?.absolutePath}")
        return dir
    }

    /**
     * Localiza a pasta onde os arquivos do modelo Piper VITS (pt-BR) estão instalados.
     */
    fun getPiperModelDir(): File? {
        val candidates = listOfNotNull(
            File(context.filesDir, "piper-pt_BR"),
            File(context.filesDir, "piper"),
            File(context.filesDir, "vits-piper-pt_BR-faber-medium"),
            context.getExternalFilesDir(null)?.resolve("piper-pt_BR"),
            context.getExternalFilesDir(null)?.resolve("piper"),
            File("/sdcard/Android/data/${context.packageName}/files/piper-pt_BR"),
            File("/sdcard/Android/data/${context.packageName}/files/piper")
        )
        val dir = candidates.firstOrNull { d ->
            d.exists() && d.listFiles()?.any { it.name.endsWith(".onnx") && !it.name.contains("kokoro") } == true
        }
        Log.i(TAG, "getPiperModelDir() resolvido: ${dir?.absolutePath}")
        return dir
    }

    /**
     * Localiza a pasta espeak-ng-data necessária para fonetização dinâmica.
     */
    fun getEspeakDataDir(): File? {
        val candidates = listOfNotNull(
            File(context.filesDir, "piper-pt_BR/espeak-ng-data"),
            File(context.filesDir, "kokoro/kokoro-int8-multi-lang-v1_1/espeak-ng-data"),
            File(context.filesDir, "espeak-ng-data"),
            context.getExternalFilesDir(null)?.resolve("piper-pt_BR/espeak-ng-data"),
            context.getExternalFilesDir(null)?.resolve("kokoro/kokoro-int8-multi-lang-v1_1/espeak-ng-data"),
            context.getExternalFilesDir(null)?.resolve("espeak-ng-data"),
            File("/sdcard/Android/data/${context.packageName}/files/piper-pt_BR/espeak-ng-data"),
            File("/sdcard/Android/data/${context.packageName}/files/kokoro/kokoro-int8-multi-lang-v1_1/espeak-ng-data")
        )
        val dir = candidates.firstOrNull { it.exists() && (File(it, "phondata").exists() || File(it, "pt_dict").exists()) }
        Log.i(TAG, "getEspeakDataDir() resolvido: ${dir?.absolutePath}")
        return dir
    }

    /**
     * Localiza a pasta onde os arquivos do modelo Kokoro estão instalados.
     */
    fun getKokoroModelDir(): File? {
        val candidates = listOfNotNull(
            File(context.filesDir, "kokoro/kokoro-int8-multi-lang-v1_1"),
            File(context.filesDir, "kokoro-model/kokoro-int8-multi-lang-v1_1"),
            File(context.filesDir, "kokoro"),
            context.getExternalFilesDir(null)?.resolve("kokoro/kokoro-int8-multi-lang-v1_1"),
            context.getExternalFilesDir(null)?.resolve("kokoro"),
            File("/sdcard/Android/data/${context.packageName}/files/kokoro/kokoro-int8-multi-lang-v1_1"),
            File("/sdcard/Android/data/${context.packageName}/files/kokoro")
        )
        val dir = candidates.firstOrNull { File(it, "model.int8.onnx").exists() }
        Log.i(TAG, "getKokoroModelDir() resolvido: ${dir?.absolutePath}")
        return dir
    }

    fun getModelDir(): File? {
        return getSupertonicModelDir() ?: getPiperModelDir() ?: getKokoroModelDir()
    }

    fun isModelInstalled(): Boolean {
        val hasSupertonic = getSupertonicModelDir() != null
        val hasPiper = getPiperModelDir() != null && getEspeakDataDir() != null
        val hasKokoro = getKokoroModelDir() != null
        val installed = hasSupertonic || hasPiper || hasKokoro
        Log.i(TAG, "isModelInstalled() = $installed (Supertonic: $hasSupertonic, Piper: $hasPiper, Kokoro: $hasKokoro)")
        return installed
    }

    @Synchronized
    private fun ensureTtsInitialized(): Boolean {
        if (tts != null) return true

        // Prioridade 1: Supertonic-3 (Fonetização nativa end-to-end com unicode indexer para 31 idiomas incluindo Português)
        val supertonicDir = getSupertonicModelDir()
        if (supertonicDir != null) {
            try {
                Log.i(TAG, "Inicializando Sherpa-ONNX Supertonic-3 Neural TTS (Português & Multilíngue)...")
                val supertonicConfig = OfflineTtsSupertonicModelConfig(
                    durationPredictor = File(supertonicDir, "duration_predictor.int8.onnx").absolutePath,
                    textEncoder = File(supertonicDir, "text_encoder.int8.onnx").absolutePath,
                    vectorEstimator = File(supertonicDir, "vector_estimator.int8.onnx").absolutePath,
                    vocoder = File(supertonicDir, "vocoder.int8.onnx").absolutePath,
                    ttsJson = File(supertonicDir, "tts.json").absolutePath,
                    unicodeIndexer = File(supertonicDir, "unicode_indexer.bin").absolutePath,
                    voiceStyle = File(supertonicDir, "voice.bin").absolutePath
                )

                val modelConfig = OfflineTtsModelConfig(
                    supertonic = supertonicConfig,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1
                )

                tts = OfflineTts(assetManager = null, config = ttsConfig)
                activeBackend = EngineBackend.SUPERTONIC
                Log.i(TAG, "Sherpa-ONNX Supertonic-3 inicializado com sucesso! SampleRate: ${tts?.sampleRate()}, Falantes: ${tts?.numSpeakers()}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao instanciar Supertonic-3: ${e.message}", e)
            }
        }

        // Prioridade 2: Piper VITS
        val piperDir = getPiperModelDir()
        val espeakDir = getEspeakDataDir()
        if (piperDir != null && espeakDir != null) {
            try {
                Log.i(TAG, "Inicializando Sherpa-ONNX Piper VITS (Português do Brasil)...")
                val onnxFile = piperDir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") && !it.name.contains("kokoro") }
                    ?: File(piperDir, "pt_BR-faber-medium.onnx")
                val tokensFile = piperDir.listFiles()?.firstOrNull { it.name == "tokens.txt" }
                    ?: File(piperDir, "tokens.txt")

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = onnxFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = espeakDir.absolutePath,
                    lexicon = "",
                    dictDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1
                )

                tts = OfflineTts(assetManager = null, config = ttsConfig)
                activeBackend = EngineBackend.PIPER_VITS
                Log.i(TAG, "Sherpa-ONNX Piper VITS inicializado com sucesso! Modelo: ${onnxFile.name}, SampleRate: ${tts?.sampleRate()}, Falantes: ${tts?.numSpeakers()}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao instanciar Piper VITS: ${e.message}", e)
            }
        }

        // Prioridade 3: Kokoro 82M
        val kokoroDir = getKokoroModelDir()
        if (kokoroDir != null) {
            try {
                Log.i(TAG, "Inicializando Sherpa-ONNX Kokoro 82M...")
                val modelFile = File(kokoroDir, "model.int8.onnx")
                val voicesFile = File(kokoroDir, "voices.bin")
                val tokensFile = File(kokoroDir, "tokens.txt")
                val dataDir = File(kokoroDir, "espeak-ng-data")
                val dictDir = File(kokoroDir, "dict")
                val lexiconFile = File(kokoroDir, "lexicon-us-en.txt")

                val kokoroConfig = OfflineTtsKokoroModelConfig(
                    model = modelFile.absolutePath,
                    voices = voicesFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = if (dataDir.exists()) dataDir.absolutePath else (espeakDir?.absolutePath ?: ""),
                    dictDir = if (dictDir.exists()) dictDir.absolutePath else "",
                    lexicon = if (lexiconFile.exists()) lexiconFile.absolutePath else "",
                    lang = "pt-br",
                    lengthScale = 1.0f
                )

                val modelConfig = OfflineTtsModelConfig(
                    kokoro = kokoroConfig,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1
                )

                tts = OfflineTts(assetManager = null, config = ttsConfig)
                activeBackend = EngineBackend.KOKORO
                Log.i(TAG, "Sherpa-ONNX Kokoro TTS inicializado! SampleRate: ${tts?.sampleRate()}, Falantes: ${tts?.numSpeakers()}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Falha crítica ao instanciar Kokoro TTS: ${e.message}", e)
            }
        }

        Log.e(TAG, "Nenhum diretório de modelo neural offline válido foi encontrado.")
        return false
    }

    override fun speak(text: String, speed: Float) {
        stop()
        currentSpeed = speed

        if (!isModelInstalled()) {
            val errorMsg = "Modelo neural Kokoro não encontrado no aparelho. Baixe o pacote de voz nas configurações ou use a voz Nativa."
            Log.w(TAG, errorMsg)
            if (onErrorFallback != null) {
                onErrorFallback?.invoke(text, speed)
                return
            }
            _playbackState.value = PlaybackState.Error(errorMsg)
            return
        }

        // Divide o texto em sentenças de tamanho adequado para início ultra rápido
        chunks = TextChunker.splitIntoSentences(text, maxChunkLength = 250)
        if (chunks.isEmpty()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

        currentChunkIndex = 0
        isPaused = false
        audioFocusController.requestFocus()

        playNextChunk()
    }

    private fun playNextChunk() {
        if (isPaused) return

        if (currentChunkIndex >= chunks.size) {
            audioFocusController.abandonFocus()
            _playbackState.value = PlaybackState.Idle
            return
        }

        val textToSpeak = chunks[currentChunkIndex]
        _playbackState.value = PlaybackState.Playing(
            currentSentence = textToSpeak,
            currentIndex = currentChunkIndex,
            totalSentences = chunks.size,
            speed = currentSpeed
        )

        synthesisJob = scope.launch {
            try {
                Log.i(TAG, "Iniciando síntese Kokoro: \"$textToSpeak\"")
                val initStart = System.currentTimeMillis()
                val initialized = withContext(Dispatchers.IO) {
                    ensureTtsInitialized()
                }

                if (!initialized || tts == null) {
                    throw IllegalStateException("Falha ao inicializar motor de voz neural offline.")
                }

                val speakerId = when (activeBackend) {
                    EngineBackend.SUPERTONIC -> {
                        val count = tts!!.numSpeakers()
                        if (count > 0) preferences.kokoroSpeakerId.coerceIn(0, count - 1) else 0
                    }
                    EngineBackend.PIPER_VITS -> {
                        val count = tts!!.numSpeakers()
                        if (count > 1) preferences.kokoroSpeakerId.coerceIn(0, count - 1) else 0
                    }
                    else -> preferences.kokoroSpeakerId
                }
                Log.i(TAG, "Sintetizando [$activeBackend] com speakerId=$speakerId, speed=$currentSpeed...")
                val synthStart = System.currentTimeMillis()
                val wavFile = withContext(Dispatchers.IO) {
                    val audio = tts!!.generate(text = textToSpeak, sid = speakerId, speed = currentSpeed)
                    if (audio == null || audio.samples.isEmpty()) {
                        throw IllegalStateException("Motor neural retornou áudio vazio.")
                    }

                    val cacheFile = File(context.cacheDir, "neural_cache_${System.currentTimeMillis()}.wav")
                    val saved = audio.save(cacheFile.absolutePath)
                    if (!saved || !cacheFile.exists() || cacheFile.length() == 0L) {
                        throw IllegalStateException("Falha ao salvar buffer de áudio sintetizado em disco.")
                    }
                    cacheFile
                }
                val duration = System.currentTimeMillis() - synthStart
                Log.i(TAG, "Áudio gerado em ${duration}ms! Tamanho do WAV: ${wavFile.length()} bytes. Iniciando MediaPlayer...")

                if (isPaused) {
                    wavFile.delete()
                    return@launch
                }

                playAudioFile(wavFile)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na síntese Kokoro: ${e.message}", e)
                if (onErrorFallback != null) {
                    val remaining = getRemainingText().ifBlank { textToSpeak }
                    Log.w(TAG, "Disparando fallback de emergência para motor Nativo...")
                    audioFocusController.abandonFocus()
                    onErrorFallback?.invoke(remaining, currentSpeed)
                    return@launch
                }

                audioFocusController.abandonFocus()
                _playbackState.value = PlaybackState.Error(
                    "Erro Kokoro Neural: ${e.message ?: "Falha na síntese local"}"
                )
            }
        }
    }

    private fun playAudioFile(audioFile: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { mp ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            mp.playbackParams = PlaybackParams().apply {
                                speed = currentSpeed
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Não foi possível aplicar PlaybackParams: ${e.message}")
                        }
                    }
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    audioFile.delete()
                    currentChunkIndex++
                    playNextChunk()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer erro: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    audioFile.delete()
                    audioFocusController.abandonFocus()
                    _playbackState.value = PlaybackState.Error("Erro na reprodução do áudio (código $what).")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao preparar MediaPlayer: ${e.message}", e)
            audioFile.delete()
            audioFocusController.abandonFocus()
            _playbackState.value = PlaybackState.Error("Falha ao reproduzir áudio local.")
        }
    }

    override fun pause() {
        if (_playbackState.value is PlaybackState.Playing) {
            isPaused = true
            synthesisJob?.cancel()
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
            _playbackState.value = PlaybackState.Paused(
                currentSentence = chunks.getOrNull(currentChunkIndex) ?: "",
                currentIndex = currentChunkIndex,
                totalSentences = chunks.size
            )
        }
    }

    override fun resume() {
        if (_playbackState.value is PlaybackState.Paused) {
            isPaused = false
            audioFocusController.requestFocus()

            if (mediaPlayer != null) {
                mediaPlayer?.start()
                _playbackState.value = PlaybackState.Playing(
                    currentSentence = chunks.getOrNull(currentChunkIndex) ?: "",
                    currentIndex = currentChunkIndex,
                    totalSentences = chunks.size,
                    speed = currentSpeed
                )
            } else {
                playNextChunk()
            }
        }
    }

    override fun stop() {
        isPaused = false
        synthesisJob?.cancel()
        synthesisJob = null

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        audioFocusController.abandonFocus()
        _playbackState.value = PlaybackState.Idle
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    try {
                        mp.playbackParams = PlaybackParams().apply {
                            this.speed = speed
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao alterar velocidade no MediaPlayer: ${e.message}")
                    }
                }
            }
        }
    }

    override fun release() {
        stop()
        tts?.release()
        tts = null
    }
}
