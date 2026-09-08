package com.cineview.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "cineview_general"
    const val UPDATE_CHANNEL_ID = "cineview_updates"
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "CineView", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(UPDATE_CHANNEL_ID, "CineView updates", NotificationManager.IMPORTANCE_LOW))
        }
    }
    fun show(context: Context, id: Int, title: String, text: String) {
        createChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title).setContentText(text).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
