package com.cineview.app.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object DownloadManagerCompat {
    fun enqueue(context: Context, url: String, title: String): Long {
        require(url.startsWith("https://", ignoreCase = true)) { "Only HTTPS downloads are allowed" }
        val base = title.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "CineView-download" }
            .take(120)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(base)
            .setDescription("CineView download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_MOVIES,
                "CineView/$base.mp4"
            )

        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
