package com.readtome.app.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface desacoplada para motores de síntese de voz.
 * Permite alternar perfeitamente entre TextToSpeech nativo do Android
 * e síntese remota de alta fidelidade (Gemini / Cloud Audio).
 */
interface TtsEngine {
    val playbackState: StateFlow<PlaybackState>
    val currentSpeed: Float

    /**
     * Inicia a leitura do texto fornecido.
     * Deve realizar chunking por sentenças para baixa latência de início.
     */
    fun speak(text: String, speed: Float = currentSpeed)

    /**
     * Pausa a leitura atual.
     */
    fun pause()

    /**
     * Retoma a leitura da sentença pausada.
     */
    fun resume()

    /**
     * Interrompe imediatamente qualquer áudio e limpa a fila.
     */
    fun stop()

    /**
     * Ajusta a velocidade de reprodução (ex: 1.0f, 1.25f, 1.5f, 2.0f).
     */
    fun setPlaybackSpeed(speed: Float)

    /**
     * Libera recursos de áudio e conexões.
     */
    fun release()
}
