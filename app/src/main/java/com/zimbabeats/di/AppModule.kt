package com.zimbabeats.di

import android.app.Application
import com.zimbabeats.admin.DeviceAdminManager
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.media.music.MusicPlaybackManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { AppPreferences(androidContext()) }

    // Cloud Pairing Client - Firebase-based cross-device pairing
    // Connects child device to parent's ZimbaBeats Family app
    // Must be defined BEFORE MusicPlaybackManager since it depends on this
    single { CloudPairingClient(androidContext()) }

    // Music playback manager singleton for mini player support
    // Integrates with CloudPairingClient for parental content filtering
    single {
        MusicPlaybackManager(
            application = androidApplication(),
            musicRepository = get(),
            cloudPairingClient = get()  // Enterprise content filtering
        )
    }

    // Parental Control Bridge - unified parental control interface
    // Integrates both cloud-based (CloudPairingClient) and local AIDL controls
    single { ParentalControlBridge(androidContext(), get()) }

    // Device Admin Manager - uninstall protection
    // Prevents app removal without parent PIN verification
    single { DeviceAdminManager(androidContext()) }
}
