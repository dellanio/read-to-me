package com.readtome.app

import android.app.Application
import com.readtome.app.audio.MediaSessionManager
import com.readtome.app.audio.SpeechManager
import com.readtome.app.audio.TtsEngine
import com.readtome.app.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReadToMeApp : Application() {

    lateinit var preferences: UserPreferencesRepository
        private set

    lateinit var ttsEngine: TtsEngine
        private set

    lateinit var mediaSessionManager: MediaSessionManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferences = UserPreferencesRepository(this)
        ttsEngine = SpeechManager(this, preferences)
        mediaSessionManager = MediaSessionManager(this, ttsEngine)

        // Sincroniza o estado de reprodução com a MediaSession
        applicationScope.launch {
            ttsEngine.playbackState.collect { state ->
                mediaSessionManager.updatePlaybackState(state)
            }
        }
    }

    override fun onTerminate() {
        ttsEngine.release()
        mediaSessionManager.release()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: ReadToMeApp
            private set
    }
}
