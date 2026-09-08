package com.cineview.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        player = ExoPlayer.Builder(this).build().also { it.setAudioAttributes(audio, true); it.setHandleAudioBecomingNoisy(true) }
        player?.let { session = MediaSession.Builder(this, it).setId("CineViewSession").build() }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    fun play(url: String, title: String? = null) {
        val item = MediaItem.Builder().setUri(url).setMediaId(url).setTag(title).build()
        player?.setMediaItem(item); player?.prepare(); player?.play()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        if (!url.isNullOrBlank()) play(url, intent.getStringExtra("title"))
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onDestroy() {
        session?.release(); session = null
        player?.release(); player = null
        super.onDestroy()
    }
}
