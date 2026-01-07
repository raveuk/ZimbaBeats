package com.zimbabeats.cloud

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Cloud Content Filter - Receives and applies content filtering rules from parent
 *
 * This class handles:
 * - Real-time sync of content filter settings from Firestore
 * - Applying filters to video content
 * - Logging watch history to cloud
 * - Reporting blocked content attempts
 */
class CloudContentFilter(
    private val remoteConfigManager: RemoteConfigManager = RemoteConfigManager(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "CloudContentFilter"
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_SETTINGS = "settings"
        private const val COLLECTION_WATCH_HISTORY = "watch_history"
        private const val COLLECTION_BLOCK_ALERTS = "block_alerts"
        private const val DOC_CONTENT_FILTER = "content_filter"
    }

    private val _filterSettings = MutableStateFlow(CloudFilterSettings())
    val filterSettings: StateFlow<CloudFilterSettings> = _filterSettings.asStateFlow()

    private var settingsListener: ListenerRegistration? = null
    private var parentUid: String? = null
    private var deviceId: String? = null
    private var childName: String? = null

    /**
     * Initialize the content filter with pairing info
     */
    fun initialize(parentUid: String, deviceId: String, childName: String) {
        this.parentUid = parentUid
        this.deviceId = deviceId
        this.childName = childName
        startListening()
    }

    /**
     * Start listening for content filter settings from parent
     */
    private fun startListening() {
        val uid = parentUid ?: return
        stopListening()

        settingsListener = firestore.collection(COLLECTION_FAMILIES)
            .document(uid)
            .collection(COLLECTION_SETTINGS)
            .document(DOC_CONTENT_FILTER)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for filter settings", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val settings = parseFilterSettings(snapshot.data ?: emptyMap())
                        _filterSettings.value = settings
                        Log.d(TAG, "Content filter settings updated: ${settings.blockedKeywords.size} keywords, ${settings.blockedChannels.size} channels")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing filter settings", e)
                    }
                } else {
                    _filterSettings.value = CloudFilterSettings()
                }
            }
    }

    fun stopListening() {
        settingsListener?.remove()
        settingsListener = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFilterSettings(data: Map<String, Any?>): CloudFilterSettings {
        val ageRating = data["ageRating"] as? String ?: "ALL"
        val ageBasedEnabled = data["ageBasedFilteringEnabled"] as? Boolean ?: false

        Log.d(TAG, "Parsing filter settings - ageRating: $ageRating, ageBasedEnabled: $ageBasedEnabled")

        return CloudFilterSettings(
            blockedKeywords = (data["blockedKeywords"] as? List<String>) ?: emptyList(),
            blockedChannels = (data["blockedChannels"] as? List<Map<String, Any?>>)?.map { map ->
                BlockedChannelInfo(
                    channelId = map["channelId"] as? String ?: "",
                    channelName = map["channelName"] as? String ?: ""
                )
            } ?: emptyList(),
            blockedVideoIds = (data["blockedVideoIds"] as? List<String>) ?: emptyList(),
            blockedCategories = (data["blockedCategories"] as? List<String>) ?: emptyList(),
            allowedChannelsOnly = data["allowedChannelsOnly"] as? Boolean ?: false,
            allowedChannels = (data["allowedChannels"] as? List<Map<String, Any?>>)?.map { map ->
                AllowedChannelInfo(
                    channelId = map["channelId"] as? String ?: "",
                    channelName = map["channelName"] as? String ?: ""
                )
            } ?: emptyList(),
            blockedSearchTerms = (data["blockedSearchTerms"] as? List<String>) ?: emptyList(),
            safeSearchEnabled = data["safeSearchEnabled"] as? Boolean ?: true,
            strictModeEnabled = data["strictModeEnabled"] as? Boolean ?: false,
            blockLiveStreams = data["blockLiveStreams"] as? Boolean ?: false,
            blockComments = data["blockComments"] as? Boolean ?: true,
            maxVideoDurationMinutes = (data["maxVideoDurationMinutes"] as? Long)?.toInt() ?: 0,
            watchHistoryEnabled = data["watchHistoryEnabled"] as? Boolean ?: true,
            blockAlertsEnabled = data["blockAlertsEnabled"] as? Boolean ?: true,

            // AGE-BASED FILTERING (NEW)
            ageRating = ageRating,
            ageBasedFilteringEnabled = ageBasedEnabled,
            ageBlockedKeywords = (data["ageBlockedKeywords"] as? List<String>) ?: emptyList(),
            ageMaxDurationSeconds = (data["ageMaxDurationSeconds"] as? Long) ?: 0,

            // Music-specific settings
            blockedArtists = (data["blockedArtists"] as? List<Map<String, Any?>>)?.map { map ->
                BlockedArtistInfo(
                    artistId = map["artistId"] as? String ?: "",
                    artistName = map["artistName"] as? String ?: ""
                )
            } ?: emptyList(),
            allowedArtists = (data["allowedArtists"] as? List<Map<String, Any?>>)?.map { map ->
                AllowedArtistInfo(
                    artistId = map["artistId"] as? String ?: "",
                    artistName = map["artistName"] as? String ?: ""
                )
            } ?: emptyList(),
            blockExplicitMusic = data["blockExplicitMusic"] as? Boolean ?: true,
            maxMusicDurationMinutes = (data["maxMusicDurationMinutes"] as? Long)?.toInt() ?: 0
        )
    }

    // ==================== CONTENT FILTERING ====================

    /**
     * Check if content should be blocked
     */
    fun shouldBlockContent(
        videoId: String,
        title: String,
        channelId: String,
        channelName: String,
        description: String? = null,
        durationSeconds: Long = 0,
        isLiveStream: Boolean = false,
        category: String? = null
    ): BlockResult {
        // Layer 1: Check global blocks (developer-controlled)
        val globalKeywordCheck = remoteConfigManager.isGloballyBlocked("$title $channelName ${description ?: ""}")
        if (globalKeywordCheck.isBlocked) {
            return BlockResult(true, BlockReason.BLOCKED_KEYWORD, globalKeywordCheck.message)
        }

        val globalChannelCheck = remoteConfigManager.isChannelGloballyBlocked(channelId, channelName)
        if (globalChannelCheck.isBlocked) {
            return BlockResult(true, BlockReason.BLOCKED_CHANNEL, globalChannelCheck.message)
        }

        // Layer 2: Check parent blocks (family-specific)
        val settings = _filterSettings.value

        // Check video ID blocklist
        if (videoId in settings.blockedVideoIds) {
            return BlockResult(true, BlockReason.BLOCKED_VIDEO, "This video has been blocked")
        }

        // Check channel blocklist
        if (settings.blockedChannels.any {
            it.channelId.equals(channelId, ignoreCase = true) ||
            it.channelName.equals(channelName, ignoreCase = true)
        }) {
            return BlockResult(true, BlockReason.BLOCKED_CHANNEL, "This channel has been blocked")
        }

        // Whitelist mode check
        if (settings.allowedChannelsOnly) {
            val isAllowed = settings.allowedChannels.any {
                it.channelId.equals(channelId, ignoreCase = true) ||
                it.channelName.equals(channelName, ignoreCase = true)
            }
            if (!isAllowed) {
                return BlockResult(true, BlockReason.NOT_WHITELISTED, "Only approved channels are allowed")
            }
        }

        // Live stream check
        if (isLiveStream && settings.blockLiveStreams) {
            return BlockResult(true, BlockReason.LIVE_STREAM, "Live streams are not allowed")
        }

        // Duration check
        if (settings.maxVideoDurationMinutes > 0) {
            val maxSeconds = settings.maxVideoDurationMinutes * 60L
            if (durationSeconds > maxSeconds) {
                return BlockResult(true, BlockReason.TOO_LONG, "Video is too long")
            }
        }

        // Keyword check in title and description
        val textToCheck = buildString {
            append(title.lowercase())
            append(" ")
            append(channelName.lowercase())
            description?.let { append(" "); append(it.lowercase()) }
        }

        for (keyword in settings.blockedKeywords) {
            if (textToCheck.contains(keyword.lowercase())) {
                return BlockResult(true, BlockReason.BLOCKED_KEYWORD, "Contains blocked keyword: $keyword")
            }
        }

        // Category check
        if (category != null && category.uppercase() in settings.blockedCategories) {
            return BlockResult(true, BlockReason.BLOCKED_CATEGORY, "This category is blocked")
        }

        // Layer 3: Age-based filtering (synced from parent)
        if (settings.ageBasedFilteringEnabled) {
            // Age-based duration check
            if (settings.ageMaxDurationSeconds > 0 && durationSeconds > settings.ageMaxDurationSeconds) {
                val maxMinutes = settings.ageMaxDurationSeconds / 60
                Log.d(TAG, "Age-based duration block: $durationSeconds sec > ${settings.ageMaxDurationSeconds} sec (max $maxMinutes min for ${settings.ageRating})")
                return BlockResult(true, BlockReason.AGE_RESTRICTED, "Video too long for age group (max ${maxMinutes}min)")
            }

            // Age-based keyword check
            for (keyword in settings.ageBlockedKeywords) {
                if (textToCheck.contains(keyword.lowercase())) {
                    Log.d(TAG, "Age-based keyword block: found '$keyword' in content (age: ${settings.ageRating})")
                    return BlockResult(true, BlockReason.AGE_RESTRICTED, "Contains age-inappropriate content")
                }
            }
        }

        return BlockResult(false, null, null)
    }

    /**
     * Check if search term should be blocked
     */
    fun shouldBlockSearch(query: String): BlockResult {
        val settings = _filterSettings.value
        val queryLower = query.lowercase()

        // Check blocked search terms
        for (term in settings.blockedSearchTerms) {
            if (queryLower.contains(term.lowercase())) {
                return BlockResult(true, BlockReason.BLOCKED_SEARCH, "This search term is not allowed")
            }
        }

        // Also check blocked keywords
        for (keyword in settings.blockedKeywords) {
            if (queryLower.contains(keyword.lowercase())) {
                return BlockResult(true, BlockReason.BLOCKED_KEYWORD, "Search contains blocked keyword")
            }
        }

        // Age-based keyword blocking for search
        if (settings.ageBasedFilteringEnabled) {
            for (keyword in settings.ageBlockedKeywords) {
                if (queryLower.contains(keyword.lowercase())) {
                    Log.d(TAG, "Age-based search block: found '$keyword' in query (age: ${settings.ageRating})")
                    return BlockResult(true, BlockReason.AGE_RESTRICTED, "Search term not appropriate for age group")
                }
            }
        }

        return BlockResult(false, null, null)
    }

    /**
     * Check if comments should be hidden
     */
    fun shouldHideComments(): Boolean = _filterSettings.value.blockComments

    // ==================== MUSIC CONTENT FILTERING ====================

    /**
     * Check if MUSIC content should be blocked
     * This is the enterprise-grade music filtering that mirrors video filtering
     */
    fun shouldBlockMusicContent(
        trackId: String,
        title: String,
        artistId: String,
        artistName: String,
        albumName: String? = null,
        genre: String? = null,
        durationSeconds: Long = 0,
        isExplicit: Boolean = false
    ): BlockResult {
        // Layer 1: Check global blocks (developer-controlled)
        val globalKeywordCheck = remoteConfigManager.isGloballyBlocked("$title $artistName ${albumName ?: ""}")
        if (globalKeywordCheck.isBlocked) {
            return BlockResult(true, BlockReason.BLOCKED_KEYWORD, globalKeywordCheck.message)
        }

        val globalArtistCheck = remoteConfigManager.isArtistGloballyBlocked(artistId, artistName)
        if (globalArtistCheck.isBlocked) {
            return BlockResult(true, BlockReason.BLOCKED_ARTIST, globalArtistCheck.message)
        }

        // Layer 2: Check parent blocks (family-specific)
        val settings = _filterSettings.value

        // Check explicit content
        if (isExplicit && settings.blockExplicitMusic) {
            return BlockResult(true, BlockReason.EXPLICIT_CONTENT, "Explicit content is blocked")
        }

        // Check artist blocklist
        if (settings.blockedArtists.any {
            it.artistId.equals(artistId, ignoreCase = true) ||
            it.artistName.equals(artistName, ignoreCase = true)
        }) {
            return BlockResult(true, BlockReason.BLOCKED_ARTIST, "This artist is blocked")
        }

        // Whitelist mode check for artists
        if (settings.allowedChannelsOnly) {
            val isAllowed = settings.allowedArtists.any {
                it.artistId.equals(artistId, ignoreCase = true) ||
                it.artistName.equals(artistName, ignoreCase = true)
            }
            if (!isAllowed) {
                return BlockResult(true, BlockReason.NOT_WHITELISTED, "Only approved artists are allowed")
            }
        }

        // Duration check for music
        if (settings.maxMusicDurationMinutes > 0) {
            val maxSeconds = settings.maxMusicDurationMinutes * 60L
            if (durationSeconds > maxSeconds) {
                return BlockResult(true, BlockReason.TOO_LONG, "Track is too long")
            }
        }

        // Keyword check in title, artist, and album
        val textToCheck = buildString {
            append(title.lowercase())
            append(" ")
            append(artistName.lowercase())
            albumName?.let { append(" "); append(it.lowercase()) }
        }

        for (keyword in settings.blockedKeywords) {
            if (textToCheck.contains(keyword.lowercase())) {
                return BlockResult(true, BlockReason.BLOCKED_KEYWORD, "Contains blocked keyword: $keyword")
            }
        }

        // Genre/Category check
        if (genre != null && settings.blockedCategories.any { it.equals(genre, ignoreCase = true) }) {
            return BlockResult(true, BlockReason.BLOCKED_CATEGORY, "This genre is blocked")
        }

        // Layer 3: Age-based filtering (synced from parent)
        if (settings.ageBasedFilteringEnabled) {
            // Age-based duration check for music
            if (settings.ageMaxDurationSeconds > 0 && durationSeconds > settings.ageMaxDurationSeconds) {
                val maxMinutes = settings.ageMaxDurationSeconds / 60
                Log.d(TAG, "Age-based music duration block: $durationSeconds sec > ${settings.ageMaxDurationSeconds} sec (max $maxMinutes min for ${settings.ageRating})")
                return BlockResult(true, BlockReason.AGE_RESTRICTED, "Track too long for age group (max ${maxMinutes}min)")
            }

            // Age-based keyword check for music
            for (keyword in settings.ageBlockedKeywords) {
                if (textToCheck.contains(keyword.lowercase())) {
                    Log.d(TAG, "Age-based music keyword block: found '$keyword' in content (age: ${settings.ageRating})")
                    return BlockResult(true, BlockReason.AGE_RESTRICTED, "Contains age-inappropriate content")
                }
            }
        }

        return BlockResult(false, null, null)
    }

    /**
     * Log music listen to cloud for parent monitoring
     */
    suspend fun logMusicHistory(
        trackId: String,
        title: String,
        artistId: String,
        artistName: String,
        albumName: String?,
        thumbnailUrl: String?,
        durationSeconds: Long,
        listenedDurationSeconds: Long,
        wasBlocked: Boolean = false,
        blockReason: String? = null
    ) {
        val uid = parentUid ?: return
        val settings = _filterSettings.value

        if (!settings.watchHistoryEnabled && !wasBlocked) return

        try {
            val historyData = hashMapOf(
                "contentType" to "music",
                "trackId" to trackId,
                "title" to title,
                "artistId" to artistId,
                "artistName" to artistName,
                "albumName" to albumName,
                "thumbnailUrl" to thumbnailUrl,
                "durationSeconds" to durationSeconds,
                "listenedDurationSeconds" to listenedDurationSeconds,
                "watchedAt" to Date(),
                "wasBlocked" to wasBlocked,
                "blockReason" to blockReason,
                "deviceId" to deviceId,
                "childName" to childName
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_WATCH_HISTORY)
                .add(historyData)
                .await()

            Log.d(TAG, "Music history logged: $title by $artistName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log music history", e)
        }
    }

    /**
     * Report blocked music attempt to parent
     */
    suspend fun reportBlockedMusicAttempt(
        trackId: String,
        title: String,
        artistName: String,
        thumbnailUrl: String?,
        blockReason: BlockReason,
        blockMessage: String
    ) {
        val uid = parentUid ?: return
        val settings = _filterSettings.value

        if (!settings.blockAlertsEnabled) return

        try {
            val alertData = hashMapOf(
                "contentType" to "music",
                "trackId" to trackId,
                "title" to title,
                "artistName" to artistName,
                "thumbnailUrl" to thumbnailUrl,
                "blockReason" to blockMessage,
                "blockType" to blockReason.name,
                "attemptedAt" to Date(),
                "deviceId" to deviceId,
                "childName" to childName,
                "reviewed" to false
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_BLOCK_ALERTS)
                .add(alertData)
                .await()

            Log.d(TAG, "Music block alert sent: $title - ${blockReason.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report blocked music attempt", e)
        }
    }

    // ==================== WATCH HISTORY LOGGING ====================

    /**
     * Log video watch to cloud for parent monitoring
     */
    suspend fun logWatchHistory(
        videoId: String,
        title: String,
        channelId: String,
        channelName: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
        watchedDurationSeconds: Long,
        wasBlocked: Boolean = false,
        blockReason: String? = null
    ) {
        val uid = parentUid ?: return
        val settings = _filterSettings.value

        if (!settings.watchHistoryEnabled && !wasBlocked) return

        try {
            val historyData = hashMapOf(
                "videoId" to videoId,
                "title" to title,
                "channelId" to channelId,
                "channelName" to channelName,
                "thumbnailUrl" to thumbnailUrl,
                "durationSeconds" to durationSeconds,
                "watchedDurationSeconds" to watchedDurationSeconds,
                "watchedAt" to Date(),
                "wasBlocked" to wasBlocked,
                "blockReason" to blockReason,
                "deviceId" to deviceId,
                "childName" to childName
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_WATCH_HISTORY)
                .add(historyData)
                .await()

            Log.d(TAG, "Watch history logged: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log watch history", e)
        }
    }

    // ==================== BLOCK ALERTS ====================

    /**
     * Report blocked content attempt to parent
     */
    suspend fun reportBlockedAttempt(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        blockReason: BlockReason,
        blockMessage: String
    ) {
        val uid = parentUid ?: return
        val settings = _filterSettings.value

        if (!settings.blockAlertsEnabled) return

        try {
            val alertData = hashMapOf(
                "videoId" to videoId,
                "title" to title,
                "channelName" to channelName,
                "thumbnailUrl" to thumbnailUrl,
                "blockReason" to blockMessage,
                "blockType" to blockReason.name,
                "attemptedAt" to Date(),
                "deviceId" to deviceId,
                "childName" to childName,
                "reviewed" to false
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_BLOCK_ALERTS)
                .add(alertData)
                .await()

            Log.d(TAG, "Block alert sent: $title - ${blockReason.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report blocked attempt", e)
        }
    }

    fun cleanup() {
        stopListening()
        parentUid = null
        deviceId = null
        childName = null
    }
}

/**
 * Content filter settings received from parent
 * Enterprise-grade unified settings for BOTH Video AND Music
 */
data class CloudFilterSettings(
    // Common settings (apply to both video AND music)
    val blockedKeywords: List<String> = emptyList(),
    val blockedSearchTerms: List<String> = emptyList(),
    val blockedCategories: List<String> = emptyList(),
    val allowedChannelsOnly: Boolean = false, // Whitelist mode
    val safeSearchEnabled: Boolean = true,
    val strictModeEnabled: Boolean = false,
    val watchHistoryEnabled: Boolean = true,
    val blockAlertsEnabled: Boolean = true,

    // AGE-BASED FILTERING (NEW)
    val ageRating: String = "ALL",
    val ageBasedFilteringEnabled: Boolean = false,
    val ageBlockedKeywords: List<String> = emptyList(),
    val ageMaxDurationSeconds: Long = 0,

    // Video-specific settings
    val blockedChannels: List<BlockedChannelInfo> = emptyList(),
    val allowedChannels: List<AllowedChannelInfo> = emptyList(),
    val blockedVideoIds: List<String> = emptyList(),
    val blockLiveStreams: Boolean = false,
    val blockComments: Boolean = true,
    val maxVideoDurationMinutes: Int = 0,

    // Music-specific settings
    val blockedArtists: List<BlockedArtistInfo> = emptyList(),
    val allowedArtists: List<AllowedArtistInfo> = emptyList(),
    val blockExplicitMusic: Boolean = true,
    val maxMusicDurationMinutes: Int = 0
)

data class BlockedChannelInfo(
    val channelId: String,
    val channelName: String
)

data class AllowedChannelInfo(
    val channelId: String,
    val channelName: String
)

data class BlockedArtistInfo(
    val artistId: String,
    val artistName: String
)

data class AllowedArtistInfo(
    val artistId: String,
    val artistName: String
)

/**
 * Result of content blocking check
 */
data class BlockResult(
    val isBlocked: Boolean,
    val reason: BlockReason?,
    val message: String?
)

/**
 * Reason for content being blocked
 * Covers both VIDEO and MUSIC content types
 */
enum class BlockReason {
    // Common
    BLOCKED_KEYWORD,
    BLOCKED_CATEGORY,
    BLOCKED_SEARCH,
    NOT_WHITELISTED,
    TOO_LONG,
    AGE_RESTRICTED,
    STRICT_MODE,
    // Video-specific
    BLOCKED_VIDEO,
    BLOCKED_CHANNEL,
    LIVE_STREAM,
    // Music-specific
    BLOCKED_ARTIST,
    EXPLICIT_CONTENT
}
