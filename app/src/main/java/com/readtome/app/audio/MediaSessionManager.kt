package com.readtome.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.readtome.app.ui.MainActivity

/**
 * Gerencia a MediaSession do Android para controles de fone de ouvido Bluetooth,
 * botões de mídia físicos e notificação em primeiro plano com ações de Play/Pause/Stop.
 */
class MediaSessionManager(
    private val context: Context,
    private val ttsEngine: TtsEngine
) {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "read_to_me_playback_channel"
        private const val TAG = "MediaSessionManager"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val mediaSession = MediaSessionCompat(context, "ReadToMeMediaSession").apply {
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                ttsEngine.resume()
            }

            override fun onPause() {
                ttsEngine.pause()
            }

            override fun onStop() {
                ttsEngine.stop()
            }
        })
        isActive = true
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reprodução de Voz ReadToMe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução de voz e leitura de tela"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updatePlaybackState(state: PlaybackState) {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )

        when (state) {
            is PlaybackState.Playing -> {
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    state.speed
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                showNotification(state.currentSentence, isPlaying = true)
            }
            is PlaybackState.Paused -> {
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                showNotification(state.currentSentence, isPlaying = false)
            }
            is PlaybackState.Idle,
            is PlaybackState.Error -> {
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                hideNotification()
            }
            is PlaybackState.Buffering -> {
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_BUFFERING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                mediaSession.setPlaybackState(stateBuilder.build())
            }
        }
    }

    private fun showNotification(content: String, isPlaying: Boolean) {
        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ReadToMe Lendo")
            .setContentText(content)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
        hideNotification()
    }
}
