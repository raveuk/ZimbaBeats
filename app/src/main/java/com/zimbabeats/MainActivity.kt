package com.zimbabeats

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.data.ThemeMode
import com.zimbabeats.ui.screen.BlockReason
import com.zimbabeats.ui.components.BottomNavItem
import com.zimbabeats.ui.components.MiniPlayer
import com.zimbabeats.ui.components.MinimalBottomNavBar
import com.zimbabeats.ui.navigation.ZimbaBeatsNavHost
import com.zimbabeats.ui.navigation.Screen
import com.zimbabeats.ui.screen.BlockScreen
import com.zimbabeats.ui.screen.OnboardingScreen
import com.zimbabeats.ui.screen.ParentUnlockDialog
import com.zimbabeats.ui.screen.ScreenTimeWarningDialog
import com.zimbabeats.ui.screen.UnlockOption
import com.zimbabeats.ui.theme.ZimbaBeatsTheme
import com.zimbabeats.ui.util.LocalWindowSizeClass
import com.zimbabeats.family.ipc.PlaybackVerdict
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val appPreferences: AppPreferences by inject()
    private val parentalControlBridge: ParentalControlBridge by inject()
    private val cloudPairingClient: CloudPairingClient by inject()
    private val musicRepository: MusicRepository by inject()

    // PiP state tracking
    private var isVideoPlayerActive = false
    private var isInPipMode = false

    companion object {
        // Static reference for PiP state access from composables
        var pipStateCallback: ((Boolean) -> Unit)? = null
    }

    /**
     * Set whether video player is currently active (for auto-enter PiP)
     */
    fun setVideoPlayerActive(active: Boolean) {
        isVideoPlayerActive = active
    }

    /**
     * Check if currently in PiP mode
     */
    fun isPipModeActive(): Boolean = isInPipMode

    /**
     * Enter Picture-in-Picture mode manually
     * Called from video player UI - no need to check isVideoPlayerActive since
     * the button is only visible when video player is active
     */
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to enter PiP mode", e)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP when user presses home button during video playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isVideoPlayerActive) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        pipStateCallback?.invoke(isInPictureInPictureMode)
    }

    @androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Calculate WindowSizeClass for adaptive layouts
            val windowSizeClass = androidx.compose.material3.windowsizeclass.calculateWindowSizeClass(this)

            // Observe theme mode and accent color from preferences
            val themeMode by appPreferences.themeModeFlow.collectAsState()
            val accentColor by appPreferences.accentColorFlow.collectAsState()
            val isSystemDark = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Observe first launch status from AppPreferences
            val isFirstLaunchComplete by appPreferences.firstLaunchCompleteFlow.collectAsState()

            // Observe parental control bridge state for restrictions
            val restrictionState by parentalControlBridge.restrictionState.collectAsState()
            val bridgeState by parentalControlBridge.bridgeState.collectAsState()

            // State for showing block screen and warnings
            var showBlockScreen by remember { mutableStateOf<BlockReason?>(null) }
            var showWarningDialog by remember { mutableStateOf(false) }
            var warningMinutes by remember { mutableStateOf(0) }

            // State for parent unlock dialog
            var showParentUnlockDialog by remember { mutableStateOf(false) }
            var pinError by remember { mutableStateOf<String?>(null) }

            // Observe lifecycle for app active/background notification to companion app
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            parentalControlBridge.onAppActive()
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            parentalControlBridge.onAppBackground()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Tick companion app every minute (for screen time tracking)
            LaunchedEffect(bridgeState.isConnected) {
                if (bridgeState.isConnected) {
                    while (true) {
                        delay(60_000L) // 1 minute
                        parentalControlBridge.tick()
                    }
                }
            }

            // Sync YouTube cookie to MusicRepository for authenticated playback
            val youtubeCookie by appPreferences.youtubeCookieFlow.collectAsState()
            LaunchedEffect(youtubeCookie) {
                if (youtubeCookie.isNotEmpty()) {
                    musicRepository.setYouTubeCookie(youtubeCookie)
                }
            }

            // Check for block status from companion app
            LaunchedEffect(restrictionState) {
                // Only check if companion app is connected
                if (parentalControlBridge.isCompanionActive()) {
                    when {
                        restrictionState.isBedtimeCurrentlyBlocking -> {
                            showBlockScreen = BlockReason.Bedtime(
                                message = "It's bedtime! Time to rest your eyes and get some sleep.",
                                endTime = restrictionState.bedtimeEnd ?: "7:00 AM"
                            )
                        }
                        restrictionState.isEnabled &&
                        restrictionState.isScreenTimeLimitActive &&
                        restrictionState.screenTimeRemainingMinutes <= 0 -> {
                            showBlockScreen = BlockReason.ScreenTime(
                                usedMinutes = restrictionState.screenTimeUsedMinutes,
                                limitMinutes = restrictionState.screenTimeLimitMinutes
                            )
                        }
                        else -> {
                            showBlockScreen = null
                            // Check for screen time warning (5 min or less remaining)
                            if (restrictionState.isScreenTimeLimitActive &&
                                restrictionState.screenTimeRemainingMinutes in 1..5) {
                                warningMinutes = restrictionState.screenTimeRemainingMinutes
                                showWarningDialog = true
                            }
                        }
                    }
                } else {
                    // No companion app - unrestricted mode
                    showBlockScreen = null
                }
            }

            // Provide WindowSizeClass to all composables
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                ZimbaBeatsTheme(darkTheme = darkTheme, accentColor = accentColor) {
                    // Show onboarding if first launch is not complete
                    if (!isFirstLaunchComplete) {
                    OnboardingScreen(
                        onComplete = {
                            // AppPreferences will update firstLaunchComplete to true
                        }
                    )
                } else {
                    // Main app content
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()

                    // Track current navigation item
                    var selectedNavItem by remember { mutableStateOf(BottomNavItem.Home) }

                    // Update selected item based on current route
                    val currentRoute = currentBackStackEntry?.destination?.route
                    selectedNavItem = when {
                        currentRoute?.contains("Home") == true -> BottomNavItem.Home
                        currentRoute?.contains("Search") == true -> BottomNavItem.Search
                        currentRoute?.contains("Library") == true ||
                        currentRoute?.contains("Playlists") == true ||
                        currentRoute?.contains("Favorites") == true ||
                        currentRoute?.contains("Downloads") == true -> BottomNavItem.Library
                        else -> selectedNavItem
                    }

                    // Don't show bottom UI on video/music player screens
                    val showBottomUI = currentRoute?.contains("VideoPlayer") != true &&
                            currentRoute?.contains("MusicPlayer") != true

                    // Show block screen if content is blocked
                    val currentBlockScreen = showBlockScreen
                    if (currentBlockScreen != null) {
                        BlockScreen(
                            blockReason = currentBlockScreen,  // Local variable is non-null
                            onParentUnlock = {
                                // Show PIN dialog for parent authentication
                                pinError = null
                                showParentUnlockDialog = true
                            }
                        )

                        // Parent Unlock PIN Dialog
                        if (showParentUnlockDialog) {
                            ParentUnlockDialog(
                                onPinEntered = { pinAndOption ->
                                    // Parse PIN and option from combined string "1234:EXTEND_30_MIN"
                                    val parts = pinAndOption.split(":")
                                    val pin = parts.getOrNull(0) ?: ""
                                    val optionName = parts.getOrNull(1) ?: "EXTEND_30_MIN"

                                    // Try verification - first with companion app (AIDL), then with cloud
                                    val isPinValid = if (parentalControlBridge.isCompanionActive()) {
                                        // Verify PIN with companion app via AIDL
                                        parentalControlBridge.verifyPin(pin).success
                                    } else if (cloudPairingClient.isPaired()) {
                                        // Verify PIN with cloud-stored hash
                                        cloudPairingClient.verifyCloudPin(pin)
                                    } else {
                                        false
                                    }

                                    if (isPinValid) {
                                        // PIN correct - request unlock with selected option
                                        val unlockType = when (optionName) {
                                            "EXTEND_30_MIN" -> 30
                                            "EXTEND_60_MIN" -> 60
                                            "UNLOCK_TODAY" -> -1
                                            else -> 30
                                        }
                                        parentalControlBridge.requestParentUnlock(unlockType)
                                        showParentUnlockDialog = false
                                        showBlockScreen = null
                                    } else {
                                        // PIN incorrect
                                        pinError = "Incorrect PIN. Please try again."
                                    }
                                },
                                onDismiss = {
                                    showParentUnlockDialog = false
                                    pinError = null
                                },
                                pinError = pinError
                            )
                        }
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                if (showBottomUI) {
                                    Column {
                                        // Mini Player above nav bar (supports both video and music)
                                        MiniPlayer(
                                            onExpand = { videoId ->
                                                navController.navigate(Screen.VideoPlayer(videoId))
                                            },
                                            onMusicExpand = { trackId ->
                                                navController.navigate(Screen.MusicPlayer(trackId))
                                            }
                                        )

                                        // Bottom Navigation Bar
                                        MinimalBottomNavBar(
                                            selectedItem = selectedNavItem,
                                            onItemSelected = { item ->
                                                selectedNavItem = item
                                                when (item) {
                                                    BottomNavItem.Home -> navController.navigate(Screen.Home) {
                                                        popUpTo(Screen.Home) { inclusive = true }
                                                    }
                                                    BottomNavItem.Search -> navController.navigate(Screen.Search())
                                                    BottomNavItem.Library -> navController.navigate(Screen.Library)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                ZimbaBeatsNavHost(
                                    navController = navController,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Screen Time Warning Dialog
                    if (showWarningDialog) {
                        ScreenTimeWarningDialog(
                            remainingMinutes = warningMinutes,
                            onDismiss = { showWarningDialog = false }
                        )
                    }
                } // end else (main app content)
                }
            }
        }
    }

    /**
     * Opens the companion app for parent unlock.
     * Screen time and bedtime unlock is now managed by the companion app.
     */
    private fun openCompanionApp() {
        val packageName = "com.zimbabeats.family"
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }
}
