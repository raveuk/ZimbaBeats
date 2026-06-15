package com.zimbabeats.core.data.di

import com.zimbabeats.core.data.remote.youtube.NewPipeStreamExtractor
import com.zimbabeats.core.data.remote.youtube.YtDlpStreamResolver
import com.zimbabeats.core.data.remote.youtube.music.YouTubeMusicClient
import com.zimbabeats.core.data.repository.music.MusicRepositoryImpl
import com.zimbabeats.core.domain.repository.MusicRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for music-related dependencies
 */
val musicModule = module {

    // NewPipe Extractor for stream extraction (handles "n" parameter decryption)
    single { NewPipeStreamExtractor(androidContext()) }

    // yt-dlp resolver (third-tier fallback when InnerTube and NewPipe both fail).
    single { YtDlpStreamResolver() }

    // YouTube Music API Client with NewPipe support
    single {
        YouTubeMusicClient(get()).apply {
            newPipeExtractor = get()
        }
    }

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
