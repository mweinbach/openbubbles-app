package app.openbubbles.nativeapp.facetime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI

internal fun secureWebOrigin(url: String): URI? = runCatching {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
    URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, null, null, null)
}.getOrNull()

internal fun matchesWebOrigin(origin: URI, candidate: String): Boolean = runCatching {
    val uri = URI(candidate)
    uri.scheme.equals(origin.scheme, ignoreCase = true) &&
        uri.host.equals(origin.host, ignoreCase = true) &&
        effectivePort(uri) == effectivePort(origin)
}.getOrDefault(false)

private fun effectivePort(uri: URI): Int = when {
    uri.port >= 0 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    else -> -1
}

@SuppressLint("SetJavaScriptEnabled")
class CachedWebview(context: Context, name: String?, desc: String, url: String) {
    val webView = WebView(context)
    private val allowedOrigin = requireNotNull(secureWebOrigin(url)) { "FaceTime URL must use HTTPS" }

    var mirrorReady = false
    var mirrorReadyCall: (() -> Unit)? = null
    var endTask: () -> Unit = {
        webView.destroy()
        FaceTimeActivity.cachedWebview = null
    }

    val deferredRequests = arrayListOf<PermissionRequest>()
    var deferredRequestsUpdated: () -> Unit = {}

    fun getScriptData(request: WebResourceRequest, client: OkHttpClient, name: String?, desc: String): String {
        Log.i("FT", "Getting script")
        // OKHTTP should handle caching for us
        val okhttp = Request.Builder()
            .method(request.method, null)
            .headers(request.requestHeaders.entries.fold(Headers.Builder()) { acc, e ->
                acc.set(e.key, e.value)
                acc
            }.build())
            .url(request.url.toString())
            .build()

        val call = client.newCall(okhttp)
        val response = call.execute()
        if (response.code != 200) {
            throw Exception("Failed to load resource! $response")
        }
        val body = response.body ?: throw Exception("Failed to load resource! Empty body!")
        var string = body.string()
            .replace(""""GenericToast\.Waiting": *"Waiting to be let in…",""".toRegex(), """"GenericToast.Waiting":"Connecting…",""")
            .replace(""""SessionBanner\.FaceTime": *"FaceTime Call",""".toRegex(), """"SessionBanner.FaceTime":"$desc",""")
            .replace("this.onLeave.notifyListeners()", "Native.leave(), this.onLeave.notifyListeners()")

        if (name != null) {
            string = string.replace("(submitName: *([a-zA-Z]+?)[ a-zA-Z,}=:]*?;)".toRegex(), "$1 $2(\"$name\").then(() => Native.mirrored());")
        }

        return string
    }

    init {
        val client = OkHttpClient.Builder()
            .cache(
                Cache(
                    File(context.cacheDir, "http_cache"),
                    10L * 1024L * 1024L // 10 MiB
                )
            )
            .build()
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                return !matchesWebOrigin(allowedOrigin, request.url.toString())
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request == null) return null
                if (!matchesWebOrigin(allowedOrigin, request.url.toString())) return null
                if (!request.url.toString().endsWith("main.js")) return null
                // intercept and patch request

                val scriptData = getScriptData(request, client, name, desc)

                return WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    ByteArrayInputStream(scriptData.encodeToByteArray())
                )
            }
        }
        webView.setBackgroundColor(Color.BLACK)

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun leave() {
                endTask()
            }
            @JavascriptInterface
            fun mirrored() {
                // takes a second for the mirror to be ready
                Handler(Looper.getMainLooper()).postDelayed({
                    mirrorReady = true
                    mirrorReadyCall?.let {
                        it()
                    }
                }, 250)
                Log.i("Got Mirror", "")
            }
        }, "Native")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val allowedResources = setOf(
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                )
                if (!matchesWebOrigin(allowedOrigin, request.origin.toString()) ||
                    request.resources.any { it !in allowedResources }
                ) {
                    request.deny()
                    return
                }
                deferredRequests.add(request)
                deferredRequestsUpdated()
            }

            override fun getDefaultVideoPoster(): Bitmap {
                return createBitmap(1, 1, Bitmap.Config.RGB_565)
            }
        }

        webView.loadUrl(url)
    }

    fun cancelDeferredPermissions() {
        deferredRequests.forEach(PermissionRequest::deny)
        deferredRequests.clear()
    }

}
