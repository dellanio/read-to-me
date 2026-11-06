package com.readtome.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Motor de síntese neural de voz de ultra-realismo usando ElevenLabs API (v1).
 * Suporta o plano gratuito da ElevenLabs (10.000 caracteres/mês).
 * Sintetiza em streaming/arquivo MP3 e reproduz via MediaPlayer com controle total.
 */
class ElevenLabsTtsEngine(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) : TtsEngine {

    companion object {
        private const val TAG = "ElevenLabsTts"
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var synthesisJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override var currentSpeed: Float = 1.0f
        private set

    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = 0
    private var isPaused = false

    /** Callback para fallback automático quando os créditos da ElevenLabs acabarem */
    var onQuotaExceeded: ((remainingText: String, speed: Float) -> Unit)? = null

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

    override fun speak(text: String, speed: Float) {
        stop()
        currentSpeed = speed

        val apiKey = preferences.elevenLabsApiKey.trim()
        if (apiKey.isBlank()) {
            _playbackState.value = PlaybackState.Error("Chave da API ElevenLabs não configurada. Insira sua chave ou use o motor Nativo.")
            return
        }

        // Divide o texto em blocos coerentes (até ~400 caracteres por bloco para baixa latência)
        chunks = TextChunker.splitIntoSentences(text, maxChunkLength = 400)
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
                val voiceId = preferences.elevenLabsVoiceId.ifBlank { UserPreferencesRepository.DEFAULT_ELEVEN_VOICE_ID }
                val apiKey = preferences.elevenLabsApiKey.trim()

                val audioFile = downloadElevenLabsAudio(textToSpeak, voiceId, apiKey)

                if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                    throw IllegalStateException("Falha ao gerar o arquivo de áudio da ElevenLabs.")
                }

                if (isPaused) return@launch

                playAudioFile(audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na síntese ElevenLabs: ${e.message}", e)
                val isQuotaIssue = e.message?.let {
                    it.contains("401") || it.contains("402") || it.contains("429") ||
                    it.contains("quota", ignoreCase = true) || it.contains("credit", ignoreCase = true)
                } ?: true

                if (isQuotaIssue && onQuotaExceeded != null) {
                    val remaining = getRemainingText().ifBlank { textToSpeak }
                    Log.w(TAG, "Créditos ou cota da ElevenLabs esgotados. Disparando Fallback automático...")
                    audioFocusController.abandonFocus()
                    onQuotaExceeded?.invoke(remaining, currentSpeed)
                    return@launch
                }

                audioFocusController.abandonFocus()
                _playbackState.value = PlaybackState.Error(
                    "Erro ElevenLabs: ${e.message ?: "Falha de conexão ou cota excedida"}"
                )
            }
        }
    }

    private suspend fun downloadElevenLabsAudio(text: String, voiceId: String, apiKey: String): File? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$BASE_URL/$voiceId?optimize_streaming_latency=3")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("xi-api-key", apiKey)
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "audio/mpeg")
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.doOutput = true
                connection.doInput = true

                val payload = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_multilingual_v2")
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.75)
                    })
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    Log.e(TAG, "ElevenLabs API erro: $responseCode - $errorMsg")
                    throw IllegalStateException("ElevenLabs [$responseCode]: $errorMsg")
                }

                val outputFile = File(context.cacheDir, "eleven_${System.currentTimeMillis()}_$currentChunkIndex.mp3")
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                outputFile
            } finally {
                connection?.disconnect()
            }
        }

    private fun playAudioFile(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val params = PlaybackParams()
                    params.speed = currentSpeed
                    playbackParams = params
                } catch (e: Exception) {
                    Log.w(TAG, "Não foi possível definir velocidade no MediaPlayer", e)
                }
            }

            setOnCompletionListener {
                file.delete()
                currentChunkIndex++
                playNextChunk()
            }

            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer erro: what=$what extra=$extra")
                file.delete()
                _playbackState.value = PlaybackState.Error("Falha na reprodução do áudio.")
                true
            }

            start()
        }
    }

    override fun pause() {
        if (_playbackState.value is PlaybackState.Playing) {
            isPaused = true
            synthesisJob?.cancel()
            mediaPlayer?.pause()
            _playbackState.value = PlaybackState.Paused(
                currentSentence = chunks.getOrNull(currentChunkIndex) ?: "",
                currentIndex = currentChunkIndex,
                totalSentences = chunks.size
            )
        }
    }

    override fun resume() {
        if (isPaused) {
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

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar MediaPlayer", e)
        }

        audioFocusController.abandonFocus()
        chunks = emptyList()
        currentChunkIndex = 0
        _playbackState.value = PlaybackState.Idle
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer?.isPlaying == true) {
            try {
                val params = mediaPlayer?.playbackParams ?: PlaybackParams()
                params.speed = speed
                mediaPlayer?.playbackParams = params
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao mudar velocidade de reprodução", e)
            }
        }
    }

    override fun release() {
        stop()
    }
}
