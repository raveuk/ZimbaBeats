package com.zimbabeats.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import com.zimbabeats.core.domain.model.music.MusicSearchResult
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.repository.SearchRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GlobalSearchProvider : ContentProvider(), KoinComponent {

    companion object {
        private const val TAG = "GlobalSearchProvider"
        const val AUTHORITY = "com.zimbabeats.search"
        
        private val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION
        )
    }

    private val searchRepository: SearchRepository by inject()
    private val musicRepository: MusicRepository by inject()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val query = uri.lastPathSegment?.lowercase() ?: return null
        if (query == SearchManager.SUGGEST_URI_PATH_QUERY) return null

        Log.d(TAG, "Global search query: $query")

        val cursor = MatrixCursor(COLUMNS)

        runBlocking {
            // 1. Search Videos
            val videoResult = searchRepository.searchVideos(query, maxResults = 5)
            if (videoResult is Resource.Success) {
                videoResult.data.videos.forEachIndexed { index, video ->
                    cursor.addRow(arrayOf(
                        index,
                        video.title,
                        video.channelName,
                        "zimbabeats://video/${video.id}",
                        video.thumbnailUrl,
                        "android.intent.action.VIEW"
                    ))
                }
            }

            // 2. Search Music
            val musicResult = musicRepository.searchMusic(query)
            if (musicResult is Resource.Success) {
                musicResult.data.filterIsInstance<MusicSearchResult.TrackResult>()
                    .take(5)
                    .forEachIndexed { index, result ->
                        val track = result.track
                        cursor.addRow(arrayOf(
                            index + 100, // Offset IDs to avoid collision
                            track.title,
                            track.artistName,
                            "zimbabeats://track/${track.id}",
                            track.thumbnailUrl,
                            "android.intent.action.VIEW"
                        ))
                    }
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String? = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
