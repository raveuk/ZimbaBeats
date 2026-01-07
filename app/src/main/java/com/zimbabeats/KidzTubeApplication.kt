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
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.di.appModule
import com.zimbabeats.di.downloadModule
import com.zimbabeats.di.viewModelModule
import com.zimbabeats.di.zimbaSafeContentModule
import com.zimbabeats.media.controller.MediaControllerManager
import com.zimbabeats.media.di.mediaModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ZimbaBeatsApplication : Application(), SingletonImageLoader.Factory {

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

            // Clean up any cached dummy videos from old development builds
            cleanupDummyVideos()
        } catch (e: Exception) {
            android.util.Log.e("ZimbaBeats", "Koin initialization failed", e)
            throw e
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
