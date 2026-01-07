package com.zimbabeats.core.data.di

import com.zimbabeats.core.data.remote.youtube.YouTubeHistorySync
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.data.repository.*
import com.zimbabeats.core.domain.repository.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val repositoryModule = module {
    // HTTP Client for YouTube scraping
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000  // 30 seconds for full request
                connectTimeoutMillis = 15000  // 15 seconds to establish connection
                socketTimeoutMillis = 30000   // 30 seconds for socket read/write
            }
        }
    }

    // YouTube Service (HTTP scraping)
    single { YouTubeService(get()) }

    // YouTube History Sync (for recommendations)
    single { YouTubeHistorySync(get()) }

    // Repository implementations
    single<VideoRepository> { VideoRepositoryImpl(get(), get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get()) }
    single<DownloadRepository> { DownloadRepositoryImpl(get()) }
    single<SearchRepository> { SearchRepositoryImpl(get(), get()) }
    // Note: ParentalControlRepository moved to companion app
    single<UsageRepository> { UsageRepositoryImpl(get()) }
}
