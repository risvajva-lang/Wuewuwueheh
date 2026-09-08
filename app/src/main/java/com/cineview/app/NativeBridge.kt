package com.cineview.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.cineview.app.download.DownloadManagerCompat
import com.cineview.app.player.PlayerActivity
import com.cineview.app.security.IntegrityChecker
import com.cineview.app.update.JsUpdateManager
import com.cineview.app.update.UpdateManager
import com.cineview.vip.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class NativeBridge(
    private val context: Context,
    private val backendUrl: String,
    private val webView: WebView? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JavascriptInterface
    fun getRuntimeInfo(): String = JSONObject().apply {
        put("platform", "android")
        put("native", true)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("backendUrl", backendUrl)
        put("webView", true)
        put("nativePlayer", true)
        put("backgroundPlayback", true)
        put("downloads", true)
        put("firebase", true)
        put("apkUpdates", true)
        put("jsUpdates", true)
    }.toString()

    @JavascriptInterface
    fun share(text: String, title: String) {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    title.ifBlank { "CineView" }
                )
            )
        }
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        val uri = parseHttps(url) ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    @JavascriptInterface
    fun playNative(url: String, title: String?) {
        val safeUrl = parseHttps(url) ?: return
        runCatching {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra("url", safeUrl.toString())
                    .putExtra("title", title)
            )
        }
    }

    @JavascriptInterface
    fun download(url: String, title: String): Long {
        val safeUrl = parseHttps(url) ?: return -1L
        return runCatching {
            DownloadManagerCompat.enqueue(context, safeUrl.toString(), title)
        }.getOrDefault(-1L)
    }

    @JavascriptInterface
    fun downloadAndInstallApk(url: String, sha256: String): Boolean {
        val safeUrl = parseHttps(url) ?: return false
        if (sha256.isBlank()) return false
        scope.launch(Dispatchers.IO) {
            runCatching {
                val apk = UpdateManager.download(context, safeUrl.toString(), "CineView-update.apk", sha256)
                UpdateManager.installApk(context, apk)
            }
        }
        return true
    }

    /**
     * Compatibility method: never performs network I/O on the WebView thread.
     * Use checkJsUpdateAsync from JavaScript when the result is required.
     */
    @JavascriptInterface
    fun checkJsUpdate(manifestUrl: String): String {
        return if (parseHttps(manifestUrl) != null) {
            JSONObject().apply {
                put("scheduled", true)
                put("async", true)
            }.toString()
        } else {
            JSONObject().put("error", "Only HTTPS update URLs are allowed").toString()
        }
    }

    @JavascriptInterface
    fun checkJsUpdateAsync(manifestUrl: String, callback: String): Boolean {
        val safeUrl = parseHttps(manifestUrl) ?: return false
        if (!CALLBACK_NAME.matches(callback)) return false
        scope.launch(Dispatchers.IO) {
            val result = runCatching { JsUpdateManager.check(safeUrl.toString()).toString() }
                .getOrElse { JSONObject().put("error", it.message ?: "update check failed").toString() }
            postCallback(callback, result)
        }
        return true
    }

    @JavascriptInterface
    fun installJsUpdate(url: String, sha256: String): Boolean {
        val safeUrl = parseHttps(url) ?: return false
        if (sha256.isBlank()) return false
        scope.launch(Dispatchers.IO) {
            runCatching { JsUpdateManager.downloadAndActivate(context, safeUrl.toString(), sha256) }
        }
        return true
    }

    @JavascriptInterface
    fun installJsUpdateAsync(url: String, sha256: String, callback: String): Boolean {
        val safeUrl = parseHttps(url) ?: return false
        if (sha256.isBlank() || !CALLBACK_NAME.matches(callback)) return false
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                JsUpdateManager.downloadAndActivate(context, safeUrl.toString(), sha256)
                JSONObject().put("ok", true).toString()
            }.getOrElse {
                JSONObject().put("ok", false).put("error", it.message ?: "JS update failed").toString()
            }
            postCallback(callback, result)
        }
        return true
    }

    @JavascriptInterface
    fun rollbackJsUpdate(): Boolean =
        runCatching {
            JsUpdateManager.clear(context)
            true
        }.getOrDefault(false)

    @JavascriptInterface
    fun integrity(): String {
        val result = IntegrityChecker.check(context)
        return JSONObject().apply {
            put("ok", result.ok)
            put("debuggable", result.debuggable)
            put("installer", result.installer)
            put("signingSha256", result.signingSha256)
            put("emulator", result.emulator)
            put("rooted", result.rooted)
        }.toString()
    }

    fun close() {
        scope.cancel()
    }

    private fun parseHttps(value: String): Uri? {
        return runCatching {
            Uri.parse(value).takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
        }.getOrNull()
    }

    private fun postCallback(callback: String, payload: String) {
        val view = webView ?: return
        val quoted = JSONObject.quote(payload)
        view.post {
            view.evaluateJavascript("$callback($quoted);", null)
        }
    }

    companion object {
        private val CALLBACK_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    }
}
