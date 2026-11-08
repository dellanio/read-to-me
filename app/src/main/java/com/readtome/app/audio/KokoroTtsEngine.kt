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

        // IDs oficiais de falantes em Português do Brasil no Kokoro v1.0 / v1.1
        const val SPEAKER_PF_DORA = 42   // Dora (Feminino BR)
        const val SPEAKER_PM_ALEX = 43   // Alex (Masculino BR)
        const val SPEAKER_PM_SANTA = 44  // Santa (Masculino BR)

        // Exemplos de falantes em Inglês
        const val SPEAKER_AF_SARAH = 9   // Sarah (Feminino US)
        const val SPEAKER_AM_MICHAEL = 16 // Michael (Masculino US)
        const val SPEAKER_BM_GEORGE = 26 // George (Masculino UK)
    }

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
     * Localiza a pasta onde os arquivos do modelo Kokoro estão instalados.
     */
    fun getModelDir(): File? {
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
        Log.i(TAG, "getModelDir() resolvido: ${dir?.absolutePath}")
        return dir
    }

    fun isModelInstalled(): Boolean {
        val installed = getModelDir() != null
        Log.i(TAG, "isModelInstalled() = $installed")
        return installed
    }

    @Synchronized
    private fun ensureTtsInitialized(): Boolean {
        if (tts != null) return true

        val modelDir = getModelDir()
        if (modelDir == null) {
            Log.e(TAG, "Diretório do modelo Kokoro não encontrado.")
            return false
        }

        return try {
            val modelFile = File(modelDir, "model.int8.onnx")
            val voicesFile = File(modelDir, "voices.bin")
            val tokensFile = File(modelDir, "tokens.txt")
            val dataDir = File(modelDir, "espeak-ng-data")
            val dictDir = File(modelDir, "dict")
            val lexiconFile = File(modelDir, "lexicon-us-en.txt")

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = modelFile.absolutePath,
                voices = voicesFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = dataDir.absolutePath,
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
            Log.i(TAG, "Sherpa-ONNX Kokoro TTS inicializado! SampleRate: ${tts?.sampleRate()}, Falantes: ${tts?.numSpeakers()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica ao instanciar Kokoro TTS: ${e.message}", e)
            false
        }
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

                val speakerId = preferences.kokoroSpeakerId
                Log.i(TAG, "Sintetizando com speakerId=$speakerId, speed=$currentSpeed...")
                val synthStart = System.currentTimeMillis()
                val wavFile = withContext(Dispatchers.IO) {
                    val audio = tts!!.generate(text = textToSpeak, sid = speakerId, speed = currentSpeed)
                    if (audio == null || audio.samples.isEmpty()) {
                        throw IllegalStateException("Kokoro retornou áudio vazio.")
                    }

                    val cacheFile = File(context.cacheDir, "kokoro_cache_${System.currentTimeMillis()}.wav")
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
