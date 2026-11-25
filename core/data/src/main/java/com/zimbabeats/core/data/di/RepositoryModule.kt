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
import okhttp3.ConnectionPool
import okhttp3.Protocol
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val repositoryModule = module {
    // HTTP Client for YouTube scraping
    // Optimized for mobile data with extended timeouts and HTTP/1.1
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    // Prefer HTTP/2 multiplexing with HTTP/1.1 fallback so concurrent InnerTube
                    // requests share a single TLS connection instead of queuing behind 5 sockets
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

                    // Connection pooling for mobile efficiency (reuse connections)
                    connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))

                    // Extended timeouts for slow mobile networks
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 20000
                socketTimeoutMillis = 30000
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
