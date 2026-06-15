package com.zimbabeats.core.data.remote.youtube.potoken

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Generates `poToken`s for the YouTube WEB client using a hidden WebView running YouTube's
 * BotGuard challenge (see [PoTokenWebView]). Without this, googlevideo.com stream URLs returned
 * for the WEB client respond with HTTP 403.
 *
 * Registered via [org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setPoTokenProvider].
 *
 * NOTE: the `getXxxPoToken` methods block the calling thread (NewPipeExtractor's API is
 * synchronous) and must therefore never be called from the main thread.
 */
class PoTokenProviderImpl(private val context: Context) : PoTokenProvider {

    companion object {
        private const val TAG = "PoTokenProvider"
    }

    private val lock = Any()

    @Volatile private var webViewBadImpl = false
    private var webPoTokenGenerator: PoTokenGenerator? = null
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null

    override fun getWebClientPoToken(videoId: String): PoTokenResult? = getPoToken("web", videoId)

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = getPoToken("android", videoId)

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = getPoToken("ios", videoId)

    private fun getPoToken(client: String, videoId: String): PoTokenResult? {
        Log.d(TAG, "get${client}ClientPoToken called for $videoId (webViewBadImpl=$webViewBadImpl)")
        if (webViewBadImpl) {
            return null
        }
        return try {
            val result = getWebClientPoTokenBlocking(videoId, forceRecreate = false)
            Log.d(TAG, "get${client}ClientPoToken success for $videoId: visitorData=${result.visitorData?.take(20)}, playerPot=${result.playerRequestPoToken?.take(20)}, streamingPot=${result.streamingDataPoToken?.take(20)}")
            result
        } catch (e: BadWebViewException) {
            Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
            webViewBadImpl = true
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain poToken for $videoId", e)
            null
        }
    }

    private fun getWebClientPoTokenBlocking(videoId: String, forceRecreate: Boolean): PoTokenResult {
        data class State(
            val generator: PoTokenGenerator,
            val visitorData: String,
            val streamingPot: String,
            val recreated: Boolean,
        )

        val state = synchronized(lock) {
            val shouldRecreate = webPoTokenGenerator == null || forceRecreate || webPoTokenGenerator!!.isExpired()

            if (shouldRecreate) {
                val innertubeClientRequestInfo = InnertubeClientRequestInfo.ofWebClient()
                innertubeClientRequestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()

                webPoTokenVisitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                    innertubeClientRequestInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false,
                )

                val previousGenerator = webPoTokenGenerator
                webPoTokenGenerator = runBlocking(Dispatchers.Main.immediate) {
                    previousGenerator?.let {
                        // Close the old WebView before discarding it.
                        launch { it.close() }
                    }
                    PoTokenWebView.newPoTokenGenerator(context)
                }

                // The streaming poToken needs to be generated exactly once before generating any
                // other (player) tokens.
                webPoTokenStreamingPot = runBlocking(Dispatchers.Main.immediate) {
                    webPoTokenGenerator!!.generatePoToken(webPoTokenVisitorData!!)
                }
            }

            State(webPoTokenGenerator!!, webPoTokenVisitorData!!, webPoTokenStreamingPot!!, shouldRecreate)
        }

        val playerPot = try {
            runBlocking(Dispatchers.Main.immediate) { state.generator.generatePoToken(videoId) }
        } catch (e: Exception) {
            if (state.recreated) {
                // already recreated once for this call, nothing more we can do
                throw e
            } else {
                Log.w(TAG, "Failed to obtain poToken, retrying with a fresh WebView", e)
                return getWebClientPoTokenBlocking(videoId, forceRecreate = true)
            }
        }

        return PoTokenResult(state.visitorData, playerPot, state.streamingPot)
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null
}
