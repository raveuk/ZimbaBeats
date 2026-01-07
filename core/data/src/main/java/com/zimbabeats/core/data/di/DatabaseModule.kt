package com.zimbabeats.core.data.di

import androidx.room.Room
import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    // Database
    single {
        Room.databaseBuilder(
            androidContext(),
            ZimbaBeatsDatabase::class.java,
            "marelikaybeats_database"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // DAOs
    single { get<ZimbaBeatsDatabase>().videoDao() }
    single { get<ZimbaBeatsDatabase>().videoProgressDao() }
    single { get<ZimbaBeatsDatabase>().watchHistoryDao() }
    single { get<ZimbaBeatsDatabase>().favoriteVideoDao() }
    single { get<ZimbaBeatsDatabase>().playlistDao() }
    single { get<ZimbaBeatsDatabase>().playlistVideoDao() }
    single { get<ZimbaBeatsDatabase>().downloadedVideoDao() }
    single { get<ZimbaBeatsDatabase>().downloadQueueDao() }
    single { get<ZimbaBeatsDatabase>().searchHistoryDao() }
    single { get<ZimbaBeatsDatabase>().searchSuggestionDao() }
    // Note: Parental Control DAOs moved to companion app
    single { get<ZimbaBeatsDatabase>().screenTimeLogDao() }
    single { get<ZimbaBeatsDatabase>().appUsageDao() }

    // Music DAOs
    single { get<ZimbaBeatsDatabase>().trackDao() }
    single { get<ZimbaBeatsDatabase>().musicPlaylistDao() }
    single { get<ZimbaBeatsDatabase>().favoriteTrackDao() }
    single { get<ZimbaBeatsDatabase>().musicListeningHistoryDao() }
}
