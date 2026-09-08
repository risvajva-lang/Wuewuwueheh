package com.cineview.app.update

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object JsUpdateManager {
    private const val PREFS = "js_updates"
    private const val KEY_FILE = "active_file"

    fun activeFile(context: Context): File? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FILE, null)
            ?.let { File(it).takeIf(File::isFile) }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_FILE, null)?.let { File(it).delete() }
        prefs.edit().remove(KEY_FILE).apply()
    }

    fun downloadAndActivate(context: Context, url: String, sha256: String): File {
        require(url.startsWith("https://", ignoreCase = true))
        require(sha256.isNotBlank())
        val dir = File(context.filesDir, "updates/js").apply { mkdirs() }
        val tmp = File(dir, "index.js.part")
        val out = File(dir, "index.js")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            require(connection.responseCode in 200..299) {
                "JS update download failed: ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }

            val actual = sha256(tmp)
            require(actual.equals(sha256.trim(), ignoreCase = true)) { "JS hash mismatch" }

            if (out.exists()) out.delete()
            require(tmp.renameTo(out)) { "Cannot activate JS update" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FILE, out.absolutePath)
                .apply()
            return out
        } catch (error: Throwable) {
            tmp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun check(url: String): JSONObject {
        require(url.startsWith("https://", ignoreCase = true))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            connection.connect()
            require(connection.responseCode in 200..299) {
                "Update manifest request failed: ${connection.responseCode}"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
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
