package com.zimbabeats.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import com.zimbabeats.core.domain.repository.SearchRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            "suggest_intent_data", // Samsung/Legacy
            "suggest_intent_extra_data" // Samsung
        )
    }

    private val searchRepository: SearchRepository by inject()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val query = uri.lastPathSegment?.lowercase()?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: uri.getQueryParameter("query")
            ?: return null

        Log.d(TAG, "Global search query: $query (URI: $uri)")

        val cursor = MatrixCursor(COLUMNS)

        // Add a primary action to search within the app - this is instant and prevents ANRs
        cursor.addRow(arrayOf(
            0,
            "Search ZimbaBeats for '$query'",
            "Find videos and music",
            "zimbabeats://search?q=$query",
            android.R.drawable.ic_menu_search,
            "android.intent.action.VIEW",
            "text/plain",
            "zimbabeats://search?q=$query",
            query
        ))

        // Only attempt local search if query is specific enough and avoid blocking main thread indefinitely
        runBlocking {
            try {
                withTimeout(400) { // Reduced timeout for faster response
                    val videoResult = searchRepository.searchVideosLocally(query).firstOrNull()
                    videoResult?.take(3)?.forEachIndexed { index, video ->
                        cursor.addRow(arrayOf(
                            index + 1,
                            video.title,
                            video.channelName,
                            "zimbabeats://video/${video.id}",
                            video.thumbnailUrl,
                            "android.intent.action.VIEW",
                            "video/*",
                            "zimbabeats://video/${video.id}",
                            video.id
                        ))
                    }
                }
            } catch (e: Exception) {
                // Silently ignore failures - keeping the primary "Search within app" result
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String? = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
