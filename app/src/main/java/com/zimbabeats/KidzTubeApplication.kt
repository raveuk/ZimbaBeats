package com.zimbabeats

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.data.di.getDataModules
import com.zimbabeats.core.data.remote.youtube.NewPipeStreamExtractor
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.di.appModule
import com.zimbabeats.di.downloadModule
import com.zimbabeats.di.viewModelModule
import com.zimbabeats.di.zimbaSafeContentModule
import com.zimbabeats.media.controller.MediaControllerManager
import com.zimbabeats.media.di.mediaModule
import com.zimbabeats.update.UpdateChecker
import com.zimbabeats.update.UpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ZimbaBeatsApplication : Application(), SingletonImageLoader.Factory {

    // Global update state - can be observed by any screen
    private val _updateState = MutableStateFlow<UpdateResult?>(null)
    val updateState: StateFlow<UpdateResult?> = _updateState.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin DI
        try {
            startKoin {
                androidLogger(Level.ERROR) // Changed from DEBUG to reduce startup overhead
                androidContext(this@ZimbaBeatsApplication)
                // Load data layer modules (database, repositories)
                modules(getDataModules())
                // Load media layer modules (playback, queue)
                modules(mediaModule)
                // Load download module
                modules(downloadModule)
                // Load app preferences module (includes ParentalControlBridge)
                modules(appModule)
                // Load content filtering module (VideoContentFilter, MusicContentFilter)
                modules(zimbaSafeContentModule)
                // Load app layer modules (viewModels)
                modules(viewModelModule)
            }
            android.util.Log.d("ZimbaBeats", "Koin initialization successful")

            // Initialize Parental Control Bridge (connects to companion app)
            initializeParentalControlBridge()

            // Fetch global content filter settings from Remote Config
            initializeRemoteConfig()

            // Initialize cloud sync if previously paired
            initializeCloudSync()

            // Initialize YouTube authentication (auto-login if cookies exist)
            initializeYouTubeAuth()

            // Initialize NewPipe Extractor for stream extraction (handles n-parameter decryption)
            initializeNewPipeExtractor()

            // Clean up any cached dummy videos from old development builds
            cleanupDummyVideos()

            // Check for app updates automatically
            checkForAppUpdate()
        } catch (e: Exception) {
            android.util.Log.e("ZimbaBeats", "Koin initialization failed", e)
            throw e
        }
    }

    /**
     * Check for app updates on startup.
     * Respects user preference. Always checks on launch (no cooldown).
     */
    private fun checkForAppUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appPreferences: AppPreferences = get()

                // Small delay to ensure DataStore has loaded preferences
                kotlinx.coroutines.delay(500)

                // Check if auto-update is enabled
                val autoUpdateEnabled = appPreferences.isAutoUpdateCheckEnabled()
                android.util.Log.d("ZimbaBeats", "Auto-update check setting: $autoUpdateEnabled")

                if (!autoUpdateEnabled) {
                    android.util.Log.d("ZimbaBeats", "Auto-update check disabled by user")
                    return@launch
                }

                // Perform update check (always check on launch)
                android.util.Log.d("ZimbaBeats", "Checking for app updates...")
                val updateChecker = UpdateChecker(this@ZimbaBeatsApplication)
                val result = updateChecker.checkForUpdate()

                // Store result globally
                _updateState.value = result

                when (result) {
                    is UpdateResult.UpdateAvailable -> {
                        android.util.Log.d("ZimbaBeats", "Update available: v${result.version}")
                    }
                    is UpdateResult.NoUpdate -> {
                        android.util.Log.d("ZimbaBeats", "App is up to date")
                    }
                    is UpdateResult.Error -> {
                        android.util.Log.e("ZimbaBeats", "Update check failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZimbaBeats", "Failed to check for updates", e)
            }
        }
    }

    private fun initializeParentalControlBridge() {
        try {
            val bridge: ParentalControlBridge = get()
            bridge.initialize()
            android.util.Log.d("ZimbaBeats", "ParentalControlBridge initialized")
        } catch (e: Exception) {
            android.util.Log.e("ZimbaBeats", "Failed to initialize ParentalControlBridge", e)
        }
    }

    private fun initializeRemoteConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteConfigManager = RemoteConfigManager()
                remoteConfigManager.fetchAndActivate()
                android.util.Log.d("ZimbaBeats", "Remote Config fetched successfully")
            } catch (e: Exception) {
                android.util.Log.e("ZimbaBeats", "Failed to fetch Remote Config", e)
            }
        }
    }

    private fun initializeCloudSync() {
        try {
            val cloudPairingClient: CloudPairingClient = get()
            if (cloudPairingClient.isPaired()) {
                cloudPairingClient.startSettingsSync()
                android.util.Log.d("ZimbaBeats", "Cloud sync started - linked to family")
            } else {
                android.util.Log.d("ZimbaBeats", "Not linked to family - skipping cloud sync")
            }
        } catch (e: Exception) {
            android.util.Log.e("ZimbaBeats", "Failed to initialize cloud sync", e)
        }
    }

    /**
     * Initialize NewPipe Extractor for YouTube stream extraction.
     * This handles the critical "n" parameter decryption that YouTube uses
     * to throttle/block stream access.
     *
     * Based on how SimpMusic handles stream extraction.
     */
    private fun initializeNewPipeExtractor() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("ZimbaBeats", "Initializing NewPipe extractor...")
                val newPipeExtractor: NewPipeStreamExtractor = get()
                val initialized = newPipeExtractor.initialize()
                if (initialized) {
                    android.util.Log.d("ZimbaBeats", "NewPipe extractor initialized successfully")
                } else {
                    android.util.Log.w("ZimbaBeats", "NewPipe extractor initialization failed - will use fallback methods")
                }
            } catch (e: Exception) {
                android.util.Log.e("ZimbaBeats", "Failed to initialize NewPipe extractor", e)
            }
        }
    }

    /**
     * Initialize YouTube authentication on app startup.
     * If user previously logged in, their cookies are automatically loaded
     * and applied to the music client for authenticated requests.
     *
     * This enables "automatic sign-in" - users only need to log in once,
     * and their session persists across app restarts.
     */
    private fun initializeYouTubeAuth() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appPreferences: AppPreferences = get()
                val musicRepository: MusicRepository = get()

                // Small delay to ensure DataStore has loaded
                kotlinx.coroutines.delay(200)

                // Check if user has saved YouTube cookies
                val cookie = appPreferences.getYouTubeCookie()
                val isLoggedIn = appPreferences.isYouTubeLoggedIn()

                if (isLoggedIn && cookie.isNotEmpty()) {
                    // Apply saved cookies to music client for authenticated requests
                    musicRepository.setYouTubeCookie(cookie)
                    android.util.Log.d("ZimbaBeats", "YouTube authentication restored - user is signed in")
                } else {
                    android.util.Log.d("ZimbaBeats", "No YouTube session found - using unauthenticated mode")
                }
            } catch (e: Exception) {
                android.util.Log.e("ZimbaBeats", "Failed to initialize YouTube auth", e)
            }
        }
    }

    private fun cleanupDummyVideos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videoRepository: VideoRepository = get()
                // Clear ALL cached videos to remove any old dummy data
                // Search results are no longer cached, so this is safe
                videoRepository.clearAllCachedVideos()
                android.util.Log.d("ZimbaBeats", "Cleared all cached videos from database")
            } catch (e: Exception) {
                android.util.Log.e("ZimbaBeats", "Failed to cleanup videos", e)
            }
        }
    }

    // Coil image loader configuration for kid-friendly thumbnails
    // Note: This is created lazily only when first image is loaded
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { OkHttpClient() }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // Reduced from 0.25 to 0.20
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
}
