package com.cineview.app.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : ComponentActivity() {
    private lateinit var view: PlayerView
    private var controllerFuture: ListenableFuture<MediaController>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = PlayerView(this).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) }
        setContentView(view)
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val title = intent.getStringExtra("title")
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java).putExtra("url", url).putExtra("title", title))
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({ runCatching { view.player = future.get() } }, ContextCompat.getMainExecutor(this))
        }
    }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}
