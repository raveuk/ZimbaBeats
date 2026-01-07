package com.zimbabeats.core.data.di

import com.zimbabeats.core.data.remote.youtube.music.YouTubeMusicClient
import com.zimbabeats.core.data.repository.music.MusicRepositoryImpl
import com.zimbabeats.core.domain.repository.MusicRepository
import org.koin.dsl.module

/**
 * Koin module for music-related dependencies
 */
val musicModule = module {

    // YouTube Music API Client
    single { YouTubeMusicClient(get()) }

    // Music Repository
    single<MusicRepository> {
        MusicRepositoryImpl(
            musicClient = get(),
            trackDao = get(),
            musicPlaylistDao = get(),
            favoriteTrackDao = get(),
            listeningHistoryDao = get()
        )
    }
}
