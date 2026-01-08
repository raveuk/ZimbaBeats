package com.zimbabeats.cloud

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.zimbabeats.cloud.model.*
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Client for playlist sharing via Firebase Firestore.
 *
 * Features:
 * - Generate 6-character share codes
 * - Share playlists to Firestore
 * - Validate and import shared playlists
 * - Track redeem counts
 * - Auto-expire codes after 7 days
 */
class PlaylistSharingClient(
    private val firestore: FirebaseFirestore,
    private val cloudPairingClient: CloudPairingClient
) {
    companion object {
        private const val TAG = "PlaylistSharingClient"
        private const val SHARE_CODE_LENGTH = 6
        private const val SHARE_EXPIRY_DAYS = 7
        private const val MAX_REDEEMS = 10
        private const val COLLECTION_PLAYLIST_SHARES = "playlist_shares"
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_SHARED_PLAYLISTS = "shared_playlists"

        // Characters that are easy to read and type (no 0/O, 1/I/L confusion)
        private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }

    /**
     * Generate a unique 6-character share code.
     */
    private fun generateShareCode(): String {
        return (1..SHARE_CODE_LENGTH)
            .map { CODE_CHARS.random() }
            .joinToString("")
    }

    /**
     * Check if the device is paired to a family.
     */
    fun isPaired(): Boolean {
        return cloudPairingClient.pairingStatus.value is PairingStatus.Paired
    }

    /**
     * Get the current pairing info.
     */
    private fun getPairingInfo(): PairingStatus.Paired? {
        return cloudPairingClient.pairingStatus.value as? PairingStatus.Paired
    }

    /**
     * Share a playlist and get a share code.
     *
     * @param playlist The playlist to share
     * @param videos Videos in the playlist
     * @param tracks Music tracks in the playlist
     * @return ShareResult with the code or error
     */
    suspend fun sharePlaylist(
        playlist: Playlist,
        videos: List<Video>,
        tracks: List<Track>
    ): ShareResult {
        val pairingInfo = getPairingInfo()
        if (pairingInfo == null) {
            return ShareResult.Error("Device must be linked to a family to share playlists")
        }

        // If playlist already has a share code, return it
        val existingCode = playlist.shareCode
        if (existingCode != null) {
            return ShareResult.Success(existingCode)
        }

        return try {
            // Generate unique code
            var shareCode: String
            var attempts = 0
            do {
                shareCode = generateShareCode()
                val exists = firestore.collection(COLLECTION_PLAYLIST_SHARES)
                    .document(shareCode)
                    .get()
                    .await()
                    .exists()
                attempts++
            } while (exists && attempts < 10)

            if (attempts >= 10) {
                return ShareResult.Error("Failed to generate unique code, please try again")
            }

            val now = System.currentTimeMillis()
            val expiresAt = now + (SHARE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)

            // Convert videos to shareable format
            val videoList = videos.map { video ->
                mapOf(
                    "videoId" to video.id,
                    "title" to video.title,
                    "channelId" to video.channelId,
                    "channelName" to video.channelName,
                    "thumbnailUrl" to (video.thumbnailUrl ?: ""),
                    "durationSeconds" to video.duration
                )
            }

            // Convert tracks to shareable format
            val trackList = tracks.map { track ->
                mapOf(
                    "trackId" to track.id,
                    "title" to track.title,
                    "artistId" to track.artistId,
                    "artistName" to track.artistName,
                    "albumName" to (track.albumName ?: ""),
                    "thumbnailUrl" to (track.thumbnailUrl ?: ""),
                    "durationSeconds" to (track.duration / 1000)  // Convert MS to seconds
                )
            }

            // Verify pairing is still active before Firebase write
            val currentPairingInfo = getPairingInfo()
            if (currentPairingInfo == null || currentPairingInfo.parentUid != pairingInfo.parentUid) {
                return ShareResult.Error("Device was unlinked during share operation")
            }

            // Create share document
            val shareData = mapOf(
                "shareCode" to shareCode,
                "playlistId" to playlist.id,
                "playlistName" to playlist.name,
                "description" to (playlist.description ?: ""),
                "color" to playlist.color.hex,
                "createdByChildName" to pairingInfo.childName,
                "createdByDeviceId" to pairingInfo.deviceId,
                "createdByFamilyId" to pairingInfo.parentUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt,
                "redeemCount" to 0,
                "maxRedeems" to MAX_REDEEMS,
                "isActive" to true,
                "videos" to videoList,
                "tracks" to trackList
            )

            // Save to Firestore
            firestore.collection(COLLECTION_PLAYLIST_SHARES)
                .document(shareCode)
                .set(shareData)
                .await()

            // Also save to family's shared_playlists for parent visibility
            val familyShareData = mapOf(
                "shareCode" to shareCode,
                "playlistName" to playlist.name,
                "sharedByChildName" to pairingInfo.childName,
                "sharedAt" to FieldValue.serverTimestamp(),
                "itemCount" to (videos.size + tracks.size),
                "redeemedBy" to emptyList<Map<String, Any>>(),
                "status" to "active"
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(pairingInfo.parentUid)
                .collection(COLLECTION_SHARED_PLAYLISTS)
                .document(shareCode)
                .set(familyShareData)
                .await()

            Log.d(TAG, "Playlist shared with code: $shareCode")
            ShareResult.Success(shareCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share playlist", e)
            ShareResult.Error("Failed to share playlist: ${e.message}")
        }
    }

    /**
     * Validate a share code and get preview info.
     */
    suspend fun validateShareCode(code: String): ValidateResult {
        val normalizedCode = code.uppercase().replace("-", "").replace(" ", "").trim()

        if (normalizedCode.length != SHARE_CODE_LENGTH) {
            return ValidateResult.Invalid("Code must be $SHARE_CODE_LENGTH characters")
        }

        return try {
            val doc = firestore.collection(COLLECTION_PLAYLIST_SHARES)
                .document(normalizedCode)
                .get()
                .await()

            if (!doc.exists()) {
                return ValidateResult.Invalid("Invalid share code")
            }

            val data = doc.data ?: return ValidateResult.Invalid("Invalid share code")

            val isActive = data["isActive"] as? Boolean ?: false
            if (!isActive) {
                return ValidateResult.Invalid("This share code has been revoked")
            }

            val expiresAt = data["expiresAt"] as? Long ?: 0L
            if (System.currentTimeMillis() > expiresAt) {
                return ValidateResult.Invalid("This share code has expired")
            }

            val redeemCount = (data["redeemCount"] as? Long)?.toInt() ?: 0
            val maxRedeems = (data["maxRedeems"] as? Long)?.toInt() ?: MAX_REDEEMS
            if (redeemCount >= maxRedeems) {
                return ValidateResult.Invalid("This share code has reached its maximum uses")
            }

            val videos = data["videos"] as? List<*> ?: emptyList<Any>()
            val tracks = data["tracks"] as? List<*> ?: emptyList<Any>()

            val preview = SharedPlaylistPreview(
                shareCode = normalizedCode,
                playlistName = data["playlistName"] as? String ?: "Shared Playlist",
                description = data["description"] as? String,
                sharedByChildName = data["createdByChildName"] as? String ?: "A friend",
                videoCount = videos.size,
                trackCount = tracks.size,
                expiresAt = expiresAt,
                redeemCount = redeemCount,
                maxRedeems = maxRedeems
            )

            ValidateResult.Valid(preview)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate share code", e)
            ValidateResult.Error("Failed to validate code: ${e.message}")
        }
    }

    /**
     * Import a shared playlist.
     *
     * @param code The share code
     * @return SharedPlaylistData with all content
     */
    suspend fun importPlaylist(code: String): Result<SharedPlaylistData> {
        val normalizedCode = code.uppercase().replace("-", "").replace(" ", "").trim()

        return try {
            val doc = firestore.collection(COLLECTION_PLAYLIST_SHARES)
                .document(normalizedCode)
                .get()
                .await()

            if (!doc.exists()) {
                return Result.failure(Exception("Invalid share code"))
            }

            val data = doc.data ?: return Result.failure(Exception("Invalid share code"))

            // Validate again
            val isActive = data["isActive"] as? Boolean ?: false
            if (!isActive) {
                return Result.failure(Exception("This share code has been revoked"))
            }

            val expiresAt = data["expiresAt"] as? Long ?: 0L
            if (System.currentTimeMillis() > expiresAt) {
                return Result.failure(Exception("This share code has expired"))
            }

            val redeemCount = (data["redeemCount"] as? Long)?.toInt() ?: 0
            val maxRedeems = (data["maxRedeems"] as? Long)?.toInt() ?: MAX_REDEEMS
            if (redeemCount >= maxRedeems) {
                return Result.failure(Exception("This share code has reached its maximum uses"))
            }

            // Parse videos
            val videosRaw = data["videos"] as? List<*> ?: emptyList<Any>()
            val videos = videosRaw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                SharedVideoInfo(
                    videoId = map["videoId"] as? String ?: return@mapNotNull null,
                    title = map["title"] as? String ?: "",
                    channelId = map["channelId"] as? String ?: "",
                    channelName = map["channelName"] as? String ?: "",
                    thumbnailUrl = map["thumbnailUrl"] as? String,
                    durationSeconds = (map["durationSeconds"] as? Long) ?: 0L
                )
            }

            // Parse tracks
            val tracksRaw = data["tracks"] as? List<*> ?: emptyList<Any>()
            val tracks = tracksRaw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                SharedTrackInfo(
                    trackId = map["trackId"] as? String ?: return@mapNotNull null,
                    title = map["title"] as? String ?: "",
                    artistId = map["artistId"] as? String ?: "",
                    artistName = map["artistName"] as? String ?: "",
                    albumName = map["albumName"] as? String,
                    thumbnailUrl = map["thumbnailUrl"] as? String,
                    durationSeconds = (map["durationSeconds"] as? Long) ?: 0L
                )
            }

            // Use batch writes for atomic operations
            val batch = firestore.batch()
            val pairingInfo = getPairingInfo()

            // 1. Increment redeem count
            val shareDocRef = firestore.collection(COLLECTION_PLAYLIST_SHARES)
                .document(normalizedCode)
            batch.update(shareDocRef, "redeemCount", FieldValue.increment(1))

            // 2. Record who redeemed (for parent visibility)
            if (pairingInfo != null) {
                val creatorFamilyId = data["createdByFamilyId"] as? String
                if (creatorFamilyId != null) {
                    val redeemInfo = mapOf(
                        "childName" to pairingInfo.childName,
                        "deviceId" to pairingInfo.deviceId,
                        "familyId" to pairingInfo.parentUid,
                        "redeemedAt" to FieldValue.serverTimestamp()
                    )

                    val creatorPlaylistDocRef = firestore.collection(COLLECTION_FAMILIES)
                        .document(creatorFamilyId)
                        .collection(COLLECTION_SHARED_PLAYLISTS)
                        .document(normalizedCode)
                    batch.update(creatorPlaylistDocRef, "redeemedBy", FieldValue.arrayUnion(redeemInfo))
                }

                // 3. Create import alert for importing child's parent
                val importAlert = mapOf(
                    "childName" to pairingInfo.childName,
                    "playlistName" to (data["playlistName"] as? String ?: ""),
                    "importedFrom" to (data["createdByChildName"] as? String ?: ""),
                    "itemCount" to (videos.size + tracks.size),
                    "importedAt" to FieldValue.serverTimestamp()
                )

                // Generate a unique ID for the import alert document
                val importAlertDocRef = firestore.collection(COLLECTION_FAMILIES)
                    .document(pairingInfo.parentUid)
                    .collection("import_alerts")
                    .document()  // Generate ID
                batch.set(importAlertDocRef, importAlert)
            }

            // Commit all Firebase writes atomically
            batch.commit().await()

            val sharedData = SharedPlaylistData(
                shareCode = normalizedCode,
                playlistName = data["playlistName"] as? String ?: "Shared Playlist",
                description = data["description"] as? String,
                color = data["color"] as? String ?: "#FF6B9D",
                sharedByChildName = data["createdByChildName"] as? String ?: "A friend",
                sharedByFamilyId = data["createdByFamilyId"] as? String ?: "",
                videos = videos,
                tracks = tracks
            )

            Log.d(TAG, "Imported playlist: ${sharedData.playlistName} with ${videos.size} videos and ${tracks.size} tracks")
            Result.success(sharedData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Revoke a share code.
     */
    suspend fun revokeShareCode(code: String): Result<Unit> {
        val normalizedCode = code.uppercase().replace("-", "").replace(" ", "").trim()

        return try {
            val pairingInfo = getPairingInfo()
                ?: return Result.failure(Exception("Not linked to a family"))

            // Update share document
            firestore.collection(COLLECTION_PLAYLIST_SHARES)
                .document(normalizedCode)
                .update("isActive", false)
                .await()

            // Update family document
            firestore.collection(COLLECTION_FAMILIES)
                .document(pairingInfo.parentUid)
                .collection(COLLECTION_SHARED_PLAYLISTS)
                .document(normalizedCode)
                .update("status", "revoked")
                .await()

            Log.d(TAG, "Revoked share code: $normalizedCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke share code", e)
            Result.failure(e)
        }
    }

    /**
     * Get flow of playlists shared by this device.
     */
    fun getMySharedPlaylists(): Flow<List<SharedPlaylistInfo>> = callbackFlow {
        val pairingInfo = getPairingInfo()
        if (pairingInfo == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(COLLECTION_PLAYLIST_SHARES)
            .whereEqualTo("createdByDeviceId", pairingInfo.deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to shared playlists", error)
                    return@addSnapshotListener
                }

                val playlists = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    SharedPlaylistInfo(
                        shareCode = doc.id,
                        playlistId = (data["playlistId"] as? Long) ?: 0L,
                        playlistName = data["playlistName"] as? String ?: "",
                        sharedAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                            ?: System.currentTimeMillis(),
                        expiresAt = data["expiresAt"] as? Long ?: 0L,
                        redeemCount = (data["redeemCount"] as? Long)?.toInt() ?: 0,
                        maxRedeems = (data["maxRedeems"] as? Long)?.toInt() ?: MAX_REDEEMS,
                        isActive = data["isActive"] as? Boolean ?: false
                    )
                } ?: emptyList()

                trySend(playlists)
            }

        awaitClose { listener.remove() }
    }
}
