package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    // Get playlists
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(playlistId: Long): Flow<Playlist?>
    fun getFavoritePlaylists(): Flow<List<Playlist>>

    // Create/Update playlists
    suspend fun createPlaylist(name: String, description: String?, color: PlaylistColor): Resource<Long>
    suspend fun updatePlaylist(playlist: Playlist): Resource<Unit>
    suspend fun deletePlaylist(playlistId: Long): Resource<Unit>
    suspend fun updateFavoriteStatus(playlistId: Long, isFavorite: Boolean): Resource<Unit>

    // Playlist videos
    fun getVideosInPlaylist(playlistId: Long): Flow<List<Video>>
    suspend fun addVideoToPlaylist(playlistId: Long, videoId: String): Resource<Unit>
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String): Resource<Unit>
    suspend fun moveVideoInPlaylist(playlistId: Long, videoId: String, newPosition: Int): Resource<Unit>
    suspend fun clearPlaylist(playlistId: Long): Resource<Unit>

    // Playlist tracks (music) - unified playlist support
    fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>>
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Resource<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Resource<Unit>
    suspend fun moveTrackInPlaylist(playlistId: Long, trackId: String, newPosition: Int): Resource<Unit>
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean
}
