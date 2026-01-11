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
 * Cloud Music Filter - Separate music-only content filtering with WHITELIST approach
 *
 * For ages 5-14: WHITELIST-ONLY mode
 * - All music is BLOCKED by default
 * - Parent adds allowed: artist names, song titles, keywords
 * - Only content matching whitelist is allowed
 *
 * For age 16+/ALL: No restrictions (or just block explicit)
 *
 * This is SEPARATE from video content filtering (CloudContentFilter)
 */
class CloudMusicFilter(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "CloudMusicFilter"
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_CHILDREN = "children"  // NEW: Per-child settings
        private const val COLLECTION_SETTINGS = "settings"
        private const val DOC_MUSIC_FILTER = "music_filter"
        private const val COLLECTION_MUSIC_HISTORY = "music_history"
        private const val COLLECTION_MUSIC_ALERTS = "music_alerts"

        /**
         * Default kids-safe artists - always allowed when defaultKidsArtistsEnabled = true
         */
        val DEFAULT_KIDS_ARTISTS = setOf(
            // Nursery rhymes & kids music
            "cocomelon", "coco melon",
            "pinkfong", "baby shark",
            "little baby bum",
            "super simple songs", "super simple",
            "dave and ava",
            "bounce patrol",
            "chu chu tv", "chuchu tv",
            "babybus", "baby bus",
            "little angel",
            "moonbug kids",
            "super jojo",
            "mother goose club",
            "hey bear sensory",
            "ms rachel", "ms. rachel",
            "songs for littles",
            "blippi",

            // Disney & family
            "disney",
            "pixar",
            "dreamworks",
            "nickelodeon",
            "sesame street",

            // Classical (safe for all)
            "mozart for babies",
            "beethoven for babies",
            "classical baby",
            "baby einstein",

            // Family-friendly
            "kidz bop",
            "the wiggles", "wiggles",
            "raffi",
            "laurie berkner"
        )
    }

    private val _musicSettings = MutableStateFlow(MusicFilterSettings())
    val musicSettings: StateFlow<MusicFilterSettings> = _musicSettings.asStateFlow()

    private var settingsLoaded = false
    private var settingsListener: ListenerRegistration? = null
    private var parentUid: String? = null
    private var deviceId: String? = null
    private var childName: String? = null
    private var childId: String? = null  // NEW: For per-child settings

    /**
     * Check if music filter settings have been loaded from Firestore
     */
    fun hasLoadedSettings(): Boolean = settingsLoaded

    /**
     * Get current age rating
     */
    fun getAgeRating(): String = _musicSettings.value.ageRating

    /**
     * Check if whitelist mode is active (ages 5-14)
     */
    fun isWhitelistModeActive(): Boolean {
        val settings = _musicSettings.value
        return settings.whitelistModeEnabled && settings.ageRating in listOf(
            "FIVE_PLUS", "EIGHT_PLUS", "TEN_PLUS", "TWELVE_PLUS", "THIRTEEN_PLUS", "FOURTEEN_PLUS"
        )
    }

    /**
     * Initialize the music filter with pairing info
     *
     * @param parentUid Parent's Firebase UID
     * @param deviceId This device's ID
     * @param childName Child's display name
     * @param childId Optional child profile ID for per-child settings
     */
    fun initialize(parentUid: String, deviceId: String, childName: String, childId: String? = null) {
        this.parentUid = parentUid
        this.deviceId = deviceId
        this.childName = childName
        this.childId = childId
        startListening()
    }

    /**
     * Start listening for music filter settings from parent
     * Uses per-child path if childId is set, otherwise uses legacy family-level path
     */
    private fun startListening() {
        val uid = parentUid ?: return
        stopListening()

        // Determine settings path based on whether we have a childId
        val settingsPath = if (childId != null) {
            // Per-child settings: families/{uid}/children/{childId}/settings/
            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_CHILDREN)
                .document(childId!!)
                .collection(COLLECTION_SETTINGS)
        } else {
            // Legacy family-level settings: families/{uid}/settings/
            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_SETTINGS)
        }

        val pathInfo = if (childId != null) "child $childId" else "family (legacy)"
        Log.d(TAG, "Starting to listen for music filter at $pathInfo")

        settingsListener = settingsPath.document(DOC_MUSIC_FILTER)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for music filter settings", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val settings = parseMusicSettings(snapshot.data ?: emptyMap())
                        _musicSettings.value = settings
                        settingsLoaded = true
                        Log.d(TAG, "Music filter settings loaded: whitelistMode=${settings.whitelistModeEnabled}, " +
                                "allowedArtists=${settings.allowedArtists.size}, " +
                                "allowedKeywords=${settings.allowedKeywords.size}, " +
                                "ageRating=${settings.ageRating}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing music filter settings", e)
                    }
                } else {
                    // No music_filter document - use defaults based on content_filter age
                    _musicSettings.value = MusicFilterSettings()
                    settingsLoaded = true
                    Log.d(TAG, "No music_filter document - using default settings")
                }
            }
    }

    fun stopListening() {
        settingsListener?.remove()
        settingsListener = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMusicSettings(data: Map<String, Any?>): MusicFilterSettings {
        return MusicFilterSettings(
            // Age rating (synced from main settings or set separately)
            ageRating = data["ageRating"] as? String ?: "ALL",

            // Whitelist mode - enabled for ages 5-14
            whitelistModeEnabled = data["whitelistModeEnabled"] as? Boolean ?: true,

            // WHITELIST: Allowed artists (parent enters artist names)
            allowedArtists = (data["allowedArtists"] as? List<String>) ?: emptyList(),

            // WHITELIST: Allowed song titles/keywords
            allowedKeywords = (data["allowedKeywords"] as? List<String>) ?: emptyList(),

            // WHITELIST: Allowed album names
            allowedAlbums = (data["allowedAlbums"] as? List<String>) ?: emptyList(),

            // Default allowed kids content (pre-populated safe artists)
            defaultKidsArtistsEnabled = data["defaultKidsArtistsEnabled"] as? Boolean ?: true,

            // Block explicit regardless of whitelist
            blockExplicit = data["blockExplicit"] as? Boolean ?: true,

            // Max duration in seconds (0 = no limit)
            maxDurationSeconds = (data["maxDurationSeconds"] as? Long) ?: 0,

            // Logging settings
            historyEnabled = data["historyEnabled"] as? Boolean ?: true,
            alertsEnabled = data["alertsEnabled"] as? Boolean ?: true
        )
    }

    // ==================== MUSIC CONTENT FILTERING ====================

    /**
     * Check if music track should be blocked
     *
     * WHITELIST MODE (ages 5-14):
     * - Block ALL by default
     * - Allow ONLY if matches: allowedArtists, allowedKeywords, allowedAlbums, or defaultKidsArtists
     *
     * NON-WHITELIST MODE (age 16+/ALL):
     * - Allow all except explicit content
     */
    fun shouldBlockMusic(
        trackId: String,
        title: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Long = 0,
        isExplicit: Boolean = false
    ): MusicBlockResult {
        val settings = _musicSettings.value

        Log.d(TAG, "=== Checking music: '$title' by '$artistName' ===")
        Log.d(TAG, "Settings: whitelistMode=${settings.whitelistModeEnabled}, age=${settings.ageRating}, " +
                "defaultKidsEnabled=${settings.defaultKidsArtistsEnabled}, " +
                "allowedArtists=${settings.allowedArtists.size}, allowedKeywords=${settings.allowedKeywords.size}")

        // Always block explicit content if setting enabled
        if (isExplicit && settings.blockExplicit) {
            Log.d(TAG, "BLOCKED: Explicit content")
            return MusicBlockResult(
                isBlocked = true,
                reason = MusicBlockReason.EXPLICIT_CONTENT,
                message = "Explicit content is not allowed"
            )
        }

        // Duration check
        if (settings.maxDurationSeconds > 0 && durationSeconds > settings.maxDurationSeconds) {
            val maxMinutes = settings.maxDurationSeconds / 60
            Log.d(TAG, "BLOCKED: Duration $durationSeconds > max ${settings.maxDurationSeconds}")
            return MusicBlockResult(
                isBlocked = true,
                reason = MusicBlockReason.TOO_LONG,
                message = "Track too long (max ${maxMinutes} minutes)"
            )
        }

        // If whitelist mode is NOT enabled (age 16+ or ALL), allow everything (except explicit)
        if (!settings.whitelistModeEnabled || settings.ageRating in listOf("SIXTEEN_PLUS", "ALL")) {
            Log.d(TAG, "ALLOWED: Whitelist mode disabled or age unrestricted")
            return MusicBlockResult(isBlocked = false)
        }

        // === WHITELIST MODE ACTIVE (Ages 5-14) ===
        // Everything is blocked UNLESS it matches whitelist

        val titleLower = title.lowercase().trim()
        val artistLower = artistName.lowercase().trim()
        val albumLower = albumName?.lowercase()?.trim() ?: ""

        // Check 1: Default kids artists (if enabled)
        if (settings.defaultKidsArtistsEnabled) {
            if (isDefaultKidsArtist(artistLower)) {
                Log.d(TAG, "ALLOWED: Default kids artist match - '$artistName'")
                return MusicBlockResult(isBlocked = false)
            }
        }

        // Check 2: Parent-added allowed artists
        for (allowedArtist in settings.allowedArtists) {
            val allowedLower = allowedArtist.lowercase().trim()
            if (artistLower.contains(allowedLower) || allowedLower.contains(artistLower)) {
                Log.d(TAG, "ALLOWED: Parent allowed artist match - '$artistName' matches '$allowedArtist'")
                return MusicBlockResult(isBlocked = false)
            }
        }

        // Check 3: Parent-added allowed keywords (matches title, artist, or album)
        val fullText = "$titleLower $artistLower $albumLower"
        for (keyword in settings.allowedKeywords) {
            val keywordLower = keyword.lowercase().trim()
            if (fullText.contains(keywordLower)) {
                Log.d(TAG, "ALLOWED: Parent allowed keyword match - found '$keyword'")
                return MusicBlockResult(isBlocked = false)
            }
        }

        // Check 4: Parent-added allowed albums
        if (albumLower.isNotEmpty()) {
            for (allowedAlbum in settings.allowedAlbums) {
                val allowedLower = allowedAlbum.lowercase().trim()
                if (albumLower.contains(allowedLower) || allowedLower.contains(albumLower)) {
                    Log.d(TAG, "ALLOWED: Parent allowed album match - '$albumName' matches '$allowedAlbum'")
                    return MusicBlockResult(isBlocked = false)
                }
            }
        }

        // Not in whitelist - BLOCK
        Log.d(TAG, "BLOCKED: Not in whitelist - '$title' by '$artistName'")
        return MusicBlockResult(
            isBlocked = true,
            reason = MusicBlockReason.NOT_WHITELISTED,
            message = "This music is not in the allowed list"
        )
    }

    /**
     * Check if search query is allowed
     */
    fun isSearchAllowed(query: String): MusicBlockResult {
        val settings = _musicSettings.value

        // If whitelist mode disabled, allow all searches
        if (!settings.whitelistModeEnabled || settings.ageRating in listOf("SIXTEEN_PLUS", "ALL")) {
            return MusicBlockResult(isBlocked = false)
        }

        val queryLower = query.lowercase().trim()

        // Check if query matches allowed artists
        for (artist in settings.allowedArtists) {
            if (queryLower.contains(artist.lowercase()) || artist.lowercase().contains(queryLower)) {
                return MusicBlockResult(isBlocked = false)
            }
        }

        // Check if query matches allowed keywords
        for (keyword in settings.allowedKeywords) {
            if (queryLower.contains(keyword.lowercase()) || keyword.lowercase().contains(queryLower)) {
                return MusicBlockResult(isBlocked = false)
            }
        }

        // Check if query matches default kids artists
        if (settings.defaultKidsArtistsEnabled && isDefaultKidsArtist(queryLower)) {
            return MusicBlockResult(isBlocked = false)
        }

        // Generic kids-safe search terms always allowed
        val safeSearchTerms = listOf(
            "kids", "children", "nursery", "lullaby", "disney",
            "cartoon", "baby", "toddler", "cocomelon", "pinkfong"
        )
        if (safeSearchTerms.any { queryLower.contains(it) }) {
            return MusicBlockResult(isBlocked = false)
        }

        return MusicBlockResult(
            isBlocked = true,
            reason = MusicBlockReason.SEARCH_NOT_ALLOWED,
            message = "Search for allowed artists or kids content"
        )
    }

    /**
     * Check if artist is in default kids artists list
     */
    private fun isDefaultKidsArtist(artistNameLower: String): Boolean {
        return DEFAULT_KIDS_ARTISTS.any { kidsArtist ->
            artistNameLower.contains(kidsArtist.lowercase()) ||
            kidsArtist.lowercase().contains(artistNameLower)
        }
    }

    // ==================== HISTORY & ALERTS ====================

    /**
     * Log music listen to cloud
     */
    suspend fun logMusicHistory(
        trackId: String,
        title: String,
        artistName: String,
        albumName: String?,
        thumbnailUrl: String?,
        durationSeconds: Long,
        listenedSeconds: Long,
        wasBlocked: Boolean = false,
        blockReason: String? = null
    ) {
        val uid = parentUid ?: return
        val settings = _musicSettings.value

        if (!settings.historyEnabled && !wasBlocked) return

        try {
            val historyData = hashMapOf(
                "trackId" to trackId,
                "title" to title,
                "artistName" to artistName,
                "albumName" to albumName,
                "thumbnailUrl" to thumbnailUrl,
                "durationSeconds" to durationSeconds,
                "listenedSeconds" to listenedSeconds,
                "listenedAt" to Date(),
                "wasBlocked" to wasBlocked,
                "blockReason" to blockReason,
                "deviceId" to deviceId,
                "childName" to childName
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_MUSIC_HISTORY)
                .add(historyData)
                .await()

            Log.d(TAG, "Music history logged: $title by $artistName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log music history", e)
        }
    }

    /**
     * Report blocked music attempt
     */
    suspend fun reportBlockedAttempt(
        trackId: String,
        title: String,
        artistName: String,
        thumbnailUrl: String?,
        reason: MusicBlockReason,
        message: String
    ) {
        val uid = parentUid ?: return
        val settings = _musicSettings.value

        if (!settings.alertsEnabled) return

        try {
            val alertData = hashMapOf(
                "trackId" to trackId,
                "title" to title,
                "artistName" to artistName,
                "thumbnailUrl" to thumbnailUrl,
                "blockReason" to message,
                "blockType" to reason.name,
                "attemptedAt" to Date(),
                "deviceId" to deviceId,
                "childName" to childName,
                "reviewed" to false
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(uid)
                .collection(COLLECTION_MUSIC_ALERTS)
                .add(alertData)
                .await()

            Log.d(TAG, "Music block alert sent: $title - ${reason.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report blocked music", e)
        }
    }

    fun cleanup() {
        stopListening()
        parentUid = null
        deviceId = null
        childName = null
        childId = null  // NEW: Clear child ID
    }
}

/**
 * Music filter settings - SEPARATE from video content_filter
 */
data class MusicFilterSettings(
    // Age rating - default to restrictive (EIGHT_PLUS) when no settings exist
    // This ensures content is blocked by default until parent configures settings
    val ageRating: String = "EIGHT_PLUS",

    // WHITELIST MODE - enabled for ages 5-14
    val whitelistModeEnabled: Boolean = true,

    // Parent-added allowed artists (whitelist)
    val allowedArtists: List<String> = emptyList(),

    // Parent-added allowed keywords (matches title/album/artist)
    val allowedKeywords: List<String> = emptyList(),

    // Parent-added allowed albums
    val allowedAlbums: List<String> = emptyList(),

    // Include default kids artists in whitelist
    val defaultKidsArtistsEnabled: Boolean = true,

    // Always block explicit content
    val blockExplicit: Boolean = true,

    // Max track duration (0 = no limit)
    val maxDurationSeconds: Long = 0,

    // Logging
    val historyEnabled: Boolean = true,
    val alertsEnabled: Boolean = true
)

/**
 * Result of music block check
 */
data class MusicBlockResult(
    val isBlocked: Boolean,
    val reason: MusicBlockReason? = null,
    val message: String? = null
)

/**
 * Reason for music being blocked
 */
enum class MusicBlockReason {
    NOT_WHITELISTED,
    EXPLICIT_CONTENT,
    TOO_LONG,
    BLOCKED_ARTIST,
    BLOCKED_KEYWORD,
    SEARCH_NOT_ALLOWED
}
