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
 * Motor de síntese de voz usando a API aberta MiniMax T2A v2 (platform.minimax.io).
 * Modelos: speech-2.8-hd (alta fidelidade) e speech-2.8-turbo (baixa latência).
 * Suporte nativo a language_boost: "Portuguese" e vozes em Português.
 */
class MiniMaxTtsEngine(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) : TtsEngine {

    companion object {
        private const val TAG = "MiniMaxTts"
        private const val BASE_URL = "https://api.minimax.io/v1/t2a_v2"
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

    /** Callback para fallback automático quando os créditos da MiniMax acabarem */
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

        val apiKey = preferences.minimaxApiKey.trim()
        if (apiKey.isBlank()) {
            _playbackState.value = PlaybackState.Error(
                "Chave da API MiniMax não configurada. Insira sua chave da platform.minimax.io ou selecione o motor Nativo."
            )
            return
        }

        // Divide o texto em blocos de até ~600 caracteres para início rápido de reprodução
        chunks = TextChunker.splitIntoSentences(text, maxChunkLength = 600)
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
                val voiceId = preferences.minimaxVoiceId.ifBlank { UserPreferencesRepository.DEFAULT_MINIMAX_VOICE_ID }
                val model = preferences.minimaxModel.ifBlank { UserPreferencesRepository.DEFAULT_MINIMAX_MODEL }
                val apiKey = preferences.minimaxApiKey.trim()

                val audioFile = requestMiniMaxAudio(textToSpeak, model, voiceId, apiKey)

                if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                    throw IllegalStateException("Falha ao gerar o arquivo de áudio da MiniMax.")
                }

                if (isPaused) return@launch

                playAudioFile(audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na síntese MiniMax: ${e.message}", e)
                val isQuotaIssue = e.message?.let {
                    it.contains("401") || it.contains("402") || it.contains("429") ||
                    it.contains("1004") || it.contains("1005") || it.contains("1008") ||
                    it.contains("balance", ignoreCase = true) ||
                    it.contains("quota", ignoreCase = true) || it.contains("credit", ignoreCase = true)
                } ?: true

                if (isQuotaIssue && onQuotaExceeded != null) {
                    val remaining = getRemainingText().ifBlank { textToSpeak }
                    Log.w(TAG, "Créditos ou cota da MiniMax esgotados. Disparando Fallback para Nativo...")
                    audioFocusController.abandonFocus()
                    onQuotaExceeded?.invoke(remaining, currentSpeed)
                    return@launch
                }

                audioFocusController.abandonFocus()
                _playbackState.value = PlaybackState.Error(
                    "Erro MiniMax: ${e.message ?: "Falha de conexão com a API MiniMax"}"
                )
            }
        }
    }

    private suspend fun requestMiniMaxAudio(
        text: String,
        model: String,
        voiceId: String,
        apiKey: String
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(BASE_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.doInput = true

            val payload = JSONObject().apply {
                put("model", model)
                put("text", text)
                put("stream", false)
                put("language_boost", "Portuguese")
                put("voice_setting", JSONObject().apply {
                    put("voice_id", voiceId)
                    put("speed", 1.0)
                    put("vol", 1.0)
                    put("pitch", 0)
                })
                put("audio_setting", JSONObject().apply {
                    put("sample_rate", 32000)
                    put("bitrate", 128000)
                    put("format", "mp3")
                    put("channel", 1)
                })
                put("output_format", "hex")
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStreamText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.e(TAG, "MiniMax API HTTP erro: $responseCode - $errorStreamText")
                throw IllegalStateException("MiniMax [$responseCode]: $errorStreamText")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val baseResp = json.optJSONObject("base_resp")
            val statusCode = baseResp?.optInt("status_code", 0) ?: 0
            if (statusCode != 0) {
                val statusMsg = baseResp?.optString("status_msg") ?: "Erro desconhecido"
                Log.e(TAG, "MiniMax base_resp status_code: $statusCode, msg: $statusMsg")
                throw IllegalStateException("MiniMax ($statusCode): $statusMsg")
            }

            val dataObj = json.optJSONObject("data")
                ?: throw IllegalStateException("Resposta da MiniMax não contém o campo 'data'.")

            val audioData = dataObj.optString("audio", "")
            val outputFile = File(context.cacheDir, "minimax_${System.currentTimeMillis()}_$currentChunkIndex.mp3")

            if (audioData.startsWith("http://", ignoreCase = true) || audioData.startsWith("https://", ignoreCase = true)) {
                // Se a API retornou URL direta do áudio
                val audioUrl = URL(audioData)
                val downloadConn = audioUrl.openConnection() as HttpURLConnection
                downloadConn.connectTimeout = 15000
                downloadConn.readTimeout = 25000
                downloadConn.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                downloadConn.disconnect()
            } else if (audioData.isNotBlank()) {
                // MiniMax t2a_v2 retorna áudio codificado em HEX
                val audioBytes = hexStringToByteArray(audioData)
                FileOutputStream(outputFile).use { output ->
                    output.write(audioBytes)
                }
            } else {
                throw IllegalStateException("A MiniMax não retornou nenhum áudio válido.")
            }

            outputFile
        } finally {
            connection?.disconnect()
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            val high = Character.digit(cleanHex[i], 16)
            val low = Character.digit(cleanHex[i + 1], 16)
            if (high != -1 && low != -1) {
                data[i / 2] = ((high shl 4) + low).toByte()
            }
            i += 2
        }
        return data
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
                _playbackState.value = PlaybackState.Error("Falha na reprodução do áudio MiniMax.")
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
                Log.w(TAG, "Erro ao alterar velocidade de reprodução", e)
            }
        }
    }

    override fun release() {
        stop()
    }
}
