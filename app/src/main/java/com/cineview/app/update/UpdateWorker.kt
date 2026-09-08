package com.cineview.app.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("apkUrl") ?: return Result.failure()
        return try {
            val file = UpdateManager.download(applicationContext, url, "CineView-update.apk", inputData.getString("sha256"))
            UpdateManager.installApk(applicationContext, file)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
