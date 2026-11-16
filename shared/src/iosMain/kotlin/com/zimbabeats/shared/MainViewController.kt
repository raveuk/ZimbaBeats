package com.zimbabeats.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.zimbabeats.shared.ui.ZimbaBeatsMultiplatformApp
import platform.UIKit.UIViewController

/**
 * Entry point called from Swift (iOS app) to host ZimbaBeats Compose Multiplatform UI.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    ZimbaBeatsMultiplatformApp()
}
