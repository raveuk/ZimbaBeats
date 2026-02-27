package com.zimbabeats.media.datasource

import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Factory that resolves track IDs to stream URLs on-demand.
 * This allows adding tracks to ExoPlayer's queue by ID only,
 * with stream URLs resolved when playback actually begins.
 *
 * Approach copied from SimpMusic's ResolvingDataSource pattern.
 */
@UnstableApi
class StreamResolvingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory
) : DataSource.Factory {

    companion object {
        private const val TAG = "StreamResolver"
    }

    // Cache resolved URLs to avoid re-fetching during same session
    private val urlCache = mutableMapOf<String, String>()

    override fun createDataSource(): DataSource {
        return ResolvingDataSource(
            upstreamFactory.createDataSource(),
            Resolver()
        )
    }

    private inner class Resolver : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val mediaId = dataSpec.key ?: dataSpec.uri.toString()
            Log.d(TAG, "=== RESOLVING STREAM ===")
            Log.d(TAG, "  mediaId (key): ${dataSpec.key}")
            Log.d(TAG, "  uri: ${dataSpec.uri}")
            Log.d(TAG, "  resolved mediaId: $mediaId")

            // Check if already a full URL (http/https)
            if (mediaId.startsWith("http://") || mediaId.startsWith("https://")) {
                Log.d(TAG, "Already a URL, using as-is")
                return dataSpec
            }

            // Check cache first
            urlCache[mediaId]?.let { cachedUrl ->
                Log.d(TAG, "Using cached URL for: $mediaId")
                Log.d(TAG, "  cached URL: ${cachedUrl.take(100)}...")
                return dataSpec.withUri(cachedUrl.toUri())
            }

            // Get resolver from singleton holder
            val resolver = StreamResolverHolder.resolver
            if (resolver == null) {
                Log.e(TAG, "StreamResolver not set! Cannot resolve $mediaId")
                return dataSpec
            }

            // Resolve stream URL
            Log.d(TAG, "Cache miss - resolving stream for: $mediaId")
            Log.d(TAG, "Current cache state: ${urlCache.keys.joinToString(", ")}")
            val streamUrl = runBlocking(Dispatchers.IO) {
                try {
                    resolver.resolveStreamUrl(mediaId)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception resolving stream", e)
                    null
                }
            }

            return if (streamUrl != null) {
                Log.d(TAG, "Resolved stream URL for: $mediaId")
                Log.d(TAG, "  new URL: ${streamUrl.take(100)}...")
                urlCache[mediaId] = streamUrl
                dataSpec.withUri(streamUrl.toUri())
            } else {
                // Return original spec - will likely fail but lets ExoPlayer handle error
                Log.e(TAG, "Could not resolve stream for $mediaId, returning original")
                dataSpec
            }
        }
    }

    /**
     * Clear the URL cache (e.g., when URLs might have expired)
     */
    fun clearCache() {
        urlCache.clear()
    }

    /**
     * Pre-cache a URL if already known
     */
    fun cacheUrl(mediaId: String, streamUrl: String) {
        urlCache[mediaId] = streamUrl
    }
}
