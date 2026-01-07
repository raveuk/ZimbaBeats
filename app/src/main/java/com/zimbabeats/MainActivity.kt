package com.zimbabeats

import android.content.Intent
import android.os.Bundle
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
import com.zimbabeats.ui.screen.ScreenTimeWarningDialog
import com.zimbabeats.ui.theme.ZimbaBeatsTheme
import com.zimbabeats.ui.util.LocalWindowSizeClass
import com.zimbabeats.family.ipc.PlaybackVerdict
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val appPreferences: AppPreferences by inject()
    private val parentalControlBridge: ParentalControlBridge by inject()

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
                    if (showBlockScreen != null) {
                        BlockScreen(
                            blockReason = showBlockScreen!!,
                            onParentUnlock = {
                                // Open companion app for parent unlock
                                openCompanionApp()
                            }
                        )
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
