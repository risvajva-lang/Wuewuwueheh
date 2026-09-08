package com.cineview.app

import com.cineview.app.notify.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "CineView"
        val body = message.notification?.body ?: message.data["body"] ?: "لديك إشعار جديد"
        NotificationHelper.show(this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), title, body)
    }
}
