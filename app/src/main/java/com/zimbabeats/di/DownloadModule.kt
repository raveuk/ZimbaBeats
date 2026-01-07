package com.zimbabeats.di

import com.zimbabeats.download.DownloadManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val downloadModule = module {
    single { DownloadManager(androidContext(), get(), get()) }
}
