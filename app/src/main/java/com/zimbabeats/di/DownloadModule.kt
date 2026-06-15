package com.zimbabeats.di

import com.zimbabeats.download.DownloadManager
import com.zimbabeats.download.DownloadTelemetry
import com.zimbabeats.download.YtDlpUpdater
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val downloadModule = module {
    single { DownloadTelemetry(androidContext()) }
    single { DownloadManager(androidContext(), get(), get(), get(), get(), get()) }
    single { YtDlpUpdater(androidContext(), get()) }
}
