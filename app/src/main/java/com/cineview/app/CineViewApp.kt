package com.cineview.app

import android.app.Application
import com.cineview.app.notify.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CineViewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        if (FirebaseApp.initializeApp(this) != null) {
            runCatching { FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true) }
            runCatching { FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true }
        }
    }
}
