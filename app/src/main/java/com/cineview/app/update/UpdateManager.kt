package com.cineview.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateManager {
    data class Manifest(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String?,
        val jsUrl: String?,
        val apkSha256: String?,
        val jsSha256: String?
    )

    suspend fun download(
        context: Context,
        url: String,
        name: String,
        expectedSha256: String? = null
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", ignoreCase = true)) { "Only HTTPS updates are allowed" }
        val safeName = File(name).name
        require(safeName.isNotBlank() && safeName != "." && safeName != "..")
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val tmp = File(dir, "$safeName.part")
        val out = File(dir, safeName)

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            require(connection.responseCode in 200..299) {
                "Update download failed: ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (expectedSha256 != null) {
                require(sha256(tmp).equals(expectedSha256.trim(), ignoreCase = true)) {
                    "Update hash mismatch"
                }
            }
            if (out.exists()) out.delete()
            require(tmp.renameTo(out)) { "Unable to finalize update" }
            out
        } catch (error: Throwable) {
            tmp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installApk(context: Context, apk: File) {
        require(apk.isFile) { "APK file does not exist" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
