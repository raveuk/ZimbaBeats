package com.zimbabeats.di

import com.zimbabeats.ui.viewmodel.DownloadViewModel
import com.zimbabeats.ui.viewmodel.FavoritesViewModel
import com.zimbabeats.ui.viewmodel.HomeViewModel
import com.zimbabeats.ui.viewmodel.LibraryViewModel
import com.zimbabeats.ui.viewmodel.OnboardingViewModel
import com.zimbabeats.ui.viewmodel.ParentalControlViewModel
import com.zimbabeats.ui.viewmodel.ParentalDashboardViewModel
import com.zimbabeats.ui.viewmodel.PlaylistDetailViewModel
import com.zimbabeats.ui.viewmodel.PlaylistViewModel
import com.zimbabeats.ui.viewmodel.SearchViewModel
import com.zimbabeats.ui.viewmodel.SettingsViewModel
import com.zimbabeats.ui.viewmodel.VideoPlayerViewModel
import com.zimbabeats.ui.viewmodel.WatchHistoryViewModel
import com.zimbabeats.ui.viewmodel.music.AlbumDetailViewModel
import com.zimbabeats.ui.viewmodel.music.ArtistDetailViewModel
import com.zimbabeats.ui.viewmodel.music.MusicHomeViewModel
import com.zimbabeats.ui.viewmodel.music.MusicLibraryViewModel
import com.zimbabeats.ui.viewmodel.music.MusicPlayerViewModel
import com.zimbabeats.ui.viewmodel.music.MusicPlaylistDetailViewModel
import com.zimbabeats.ui.viewmodel.music.MusicSearchViewModel
import com.zimbabeats.ui.viewmodel.music.YouTubeMusicPlaylistViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    // Onboarding screen (with cloud-based family pairing support)
    viewModel { OnboardingViewModel(get(), get()) }

    // Home screen (uses CloudPairingClient + VideoContentFilter for dual-layer filtering)
    viewModel { HomeViewModel(get(), get(), get(), get()) }

    // Search screen (uses CloudPairingClient for Firebase-based content filtering)
    viewModel { SearchViewModel(get(), get()) }

    // Playlist screen
    viewModel { PlaylistViewModel(get()) }

    // Playlist Detail screen
    viewModel { (playlistId: Long) ->
        PlaylistDetailViewModel(playlistId, get())
    }

    // Video Player screen (uses ParentalControlBridge for permissions)
    viewModel { (videoId: String) ->
        VideoPlayerViewModel(androidApplication(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    // Downloads screen
    viewModel { DownloadViewModel(get(), get(), get()) }

    // Favorites screen
    viewModel { FavoritesViewModel(get()) }

    // Watch History screen
    viewModel { WatchHistoryViewModel(get()) }

    // Settings screen
    viewModel { SettingsViewModel(androidApplication(), get(), get(), get(), get()) }

    // Parental Control screen (shows companion app status and redirect)
    viewModel { ParentalControlViewModel(get()) }

    // Parental Dashboard screen (shows watch history and companion app status)
    viewModel { ParentalDashboardViewModel(get(), get()) }

    // Library screen
    viewModel { LibraryViewModel(get(), get(), get()) }

    // ==================== Music ViewModels ====================

    // Music Home screen (uses ParentalControlBridge for content filtering)
    viewModel { MusicHomeViewModel(get(), get()) }

    // Music Search (uses ParentalControlBridge)
    viewModel { MusicSearchViewModel(get(), get()) }

    // Music Player screen (with unified playlist support and shared playback manager)
    viewModel { MusicPlayerViewModel(androidApplication(), get(), get(), get()) }

    // Artist Detail screen
    viewModel { (artistId: String) ->
        ArtistDetailViewModel(artistId, get())
    }

    // Album Detail screen (uses CloudPairingClient for content filtering)
    viewModel { (albumId: String) ->
        AlbumDetailViewModel(albumId, get(), get())
    }

    // Music Playlist Detail screen
    viewModel { (playlistId: Long) ->
        MusicPlaylistDetailViewModel(playlistId, get())
    }

    // YouTube Music Playlist Detail screen (for playlists from browse/search)
    viewModel { (playlistId: String) ->
        YouTubeMusicPlaylistViewModel(playlistId, get())
    }

    // Music Library screen (uses CloudPairingClient for content filtering)
    viewModel { MusicLibraryViewModel(get(), get()) }
}
