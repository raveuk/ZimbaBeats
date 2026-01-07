package com.zimbabeats.media.di

import com.zimbabeats.media.controller.MediaControllerManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mediaModule = module {
    // MediaController for background playback
    single { MediaControllerManager(androidContext()) }
}
