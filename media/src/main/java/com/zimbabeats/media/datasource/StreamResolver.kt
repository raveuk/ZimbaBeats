package com.zimbabeats.media.datasource

/**
 * Interface for resolving track IDs to stream URLs.
 * Implemented by the app layer and provided to the media layer.
 */
interface StreamResolver {
    /**
     * Resolve a track ID to its stream URL.
     * This should be called from a background thread.
     *
     * @param trackId The YouTube video ID
     * @return The stream URL, or null if resolution failed
     */
    suspend fun resolveStreamUrl(trackId: String): String?
}

/**
 * Singleton holder for the StreamResolver.
 * Set this from the app layer before PlaybackService starts.
 */
object StreamResolverHolder {
    var resolver: StreamResolver? = null
}
