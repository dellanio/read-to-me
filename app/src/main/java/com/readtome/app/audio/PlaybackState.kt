package com.readtome.app.audio

/**
 * Representa os estados possíveis da reprodução de áudio no ReadToMe.
 */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val message: String = "Preparando áudio...") : PlaybackState
    data class Playing(
        val currentSentence: String,
        val currentIndex: Int,
        val totalSentences: Int,
        val speed: Float = 1.0f
    ) : PlaybackState
    data class Paused(
        val currentSentence: String,
        val currentIndex: Int,
        val totalSentences: Int
    ) : PlaybackState
    data class Error(val message: String) : PlaybackState
}
