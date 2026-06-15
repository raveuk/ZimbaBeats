package com.zimbabeats.core.data.remote.youtube.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs YouTube's BotGuard challenge inside a hidden [WebView] to obtain an `integrityToken`,
 * which is then used to mint `poToken`s (proof-of-origin tokens). This is required by YouTube's
 * WEB client to avoid HTTP 403 responses on streaming URLs.
 *
 * Ported from NewPipe's `PoTokenWebView` (RxJava-based) to coroutines.
 */
@SuppressLint("SetJavaScriptEnabled")
class PoTokenWebView private constructor(context: Context) : PoTokenGenerator {

    private val webView = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val poTokenContinuations = mutableListOf<Pair<String, Continuation<String>>>()
    private var initContinuation: Continuation<PoTokenWebView>? = null
    private var expirationTimeMs: Long = 0L

    init {
        val webViewSettings = webView.settings
        webViewSettings.javaScriptEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webViewSettings, false)
        }
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView itself does not need internet access

        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.message().contains("Uncaught")) {
                    val fmt = "\"${message.message()}\", source: ${message.sourceId()} (${message.lineNumber()})"
                    Log.e(TAG, "This WebView implementation is broken: $fmt")
                    onInitializationError(BadWebViewException(fmt))
                    popAllPoTokenContinuations().forEach { (_, cont) ->
                        cont.resumeWithException(BadWebViewException(fmt))
                    }
                }
                return super.onConsoleMessage(message)
            }
        }
    }

    private suspend fun loadHtmlAndObtainBotguard(context: Context): PoTokenWebView =
        suspendCancellableCoroutine { cont ->
            initContinuation = cont
            scope.launch {
                try {
                    val html = withContext(Dispatchers.IO) {
                        context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                    }
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html.replaceFirst(
                            "</script>",
                            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
                        ),
                        "text/html",
                        "utf-8",
                        null,
                    )
                } catch (e: Exception) {
                    onInitializationError(e)
                }
            }
            cont.invokeOnCancellation { close() }
        }

    /** Called by the JS appended in [loadHtmlAndObtainBotguard] once the page has loaded. */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        scope.launch {
            try {
                val responseBody = postBotguard(
                    "https://www.youtube.com/api/jnn/v1/Create",
                    "[ \"$REQUEST_KEY\" ]",
                )
                val parsedChallengeData = parseChallengeData(responseBody)
                webView.evaluateJavascript(
                    """try {
                        data = $parsedChallengeData
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""",
                    null,
                )
            } catch (e: Exception) {
                onInitializationError(e)
            }
        }
    }

    /** Called from JS when an uncaught error occurs during BotGuard initialization. */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        Log.e(TAG, "Initialization error from JavaScript: $error")
        onInitializationError(buildExceptionForJsError(error))
    }

    /** Called from JS once BotGuard has produced a `botguardResponse`. */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        scope.launch {
            try {
                val responseBody = postBotguard(
                    "https://www.youtube.com/api/jnn/v1/GenerateIT",
                    "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
                )
                val (integrityToken, expirationTimeSeconds) = parseIntegrityTokenData(responseBody)
                // leave 10 minutes of margin just to be sure
                expirationTimeMs = System.currentTimeMillis() + (expirationTimeSeconds - 600) * 1000

                webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                    initContinuation?.resume(this@PoTokenWebView)
                    initContinuation = null
                }
            } catch (e: Exception) {
                onInitializationError(e)
            }
        }
    }

    private fun onInitializationError(error: Throwable) {
        scope.launch {
            initContinuation?.resumeWithException(error)
            initContinuation = null
            close()
        }
    }

    override suspend fun generatePoToken(identifier: String): String =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                addPoTokenContinuation(identifier, cont)
                val u8Identifier = stringToU8(identifier)
                webView.evaluateJavascript(
                    """try {
                            identifier = "$identifier"
                            u8Identifier = $u8Identifier
                            poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                            poTokenU8String = ""
                            for (i = 0; i < poTokenU8.length; i++) {
                                if (i != 0) poTokenU8String += ","
                                poTokenU8String += poTokenU8[i]
                            }
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                        }""",
                    null,
                )
            }
        }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        popPoTokenContinuation(identifier)?.resumeWithException(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            popPoTokenContinuation(identifier)?.resumeWithException(t)
            return
        }
        popPoTokenContinuation(identifier)?.resume(poToken)
    }

    override fun isExpired(): Boolean = System.currentTimeMillis() >= expirationTimeMs

    override fun close() {
        scope.cancel()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    private suspend fun postBotguard(url: String, data: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", GOOGLE_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .post(data.toRequestBody("application/json+protobuf".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PoTokenException("Invalid response code: ${response.code}")
            }
            response.body?.string() ?: throw PoTokenException("Empty response body")
        }
    }

    private fun addPoTokenContinuation(identifier: String, cont: Continuation<String>) {
        synchronized(poTokenContinuations) {
            poTokenContinuations.add(identifier to cont)
        }
    }

    private fun popPoTokenContinuation(identifier: String): Continuation<String>? = synchronized(poTokenContinuations) {
        val index = poTokenContinuations.indexOfFirst { it.first == identifier }
        if (index < 0) null else poTokenContinuations.removeAt(index).second
    }

    private fun popAllPoTokenContinuations(): List<Pair<String, Continuation<String>>> =
        synchronized(poTokenContinuations) {
            val result = poTokenContinuations.toList()
            poTokenContinuations.clear()
            result
        }

    companion object : PoTokenGenerator.Factory {
        private const val TAG = "PoTokenWebView"

        // Public API key used by BotGuard, found by observing BotGuard network requests.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val JS_INTERFACE = "PoTokenWebView"

        override suspend fun newPoTokenGenerator(context: Context): PoTokenGenerator =
            withContext(Dispatchers.Main.immediate) {
                val webView = PoTokenWebView(context)
                webView.loadHtmlAndObtainBotguard(context)
            }
    }
}
