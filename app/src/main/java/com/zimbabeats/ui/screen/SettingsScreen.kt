package com.zimbabeats.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zimbabeats.data.AccentColor
import com.zimbabeats.data.AccessibilityMode
import com.zimbabeats.data.DownloadNetworkPreference
import com.zimbabeats.data.ThemeMode
import com.zimbabeats.ui.accessibility.ContentDescriptions
import com.zimbabeats.ui.util.WindowSizeUtil
import com.zimbabeats.ui.components.YouTubeAccountDialog
import com.zimbabeats.ui.viewmodel.SettingsViewModel
import com.zimbabeats.update.DownloadState
import com.zimbabeats.update.UpdateResult
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToParentalControls: () -> Unit,
    onNavigateToYouTubeLogin: () -> Unit = {},  // Deprecated: now uses inline dialog
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentColorDialog by remember { mutableStateOf(false) }
    var showDownloadNetworkDialog by remember { mutableStateOf(false) }
    var showMobileDataWarningDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }
    // Media Storage dialogs
    var showClearPlaybackBufferDialog by remember { mutableStateOf(false) }
    var showClearSavedMediaDialog by remember { mutableStateOf(false) }
    var showClearImageCacheDialog by remember { mutableStateOf(false) }
    var showPlaybackBufferLimitDialog by remember { mutableStateOf(false) }
    var showSavedMediaLimitDialog by remember { mutableStateOf(false) }
    var showImageCacheLimitDialog by remember { mutableStateOf(false) }
    // Family linking dialog
    var showFamilyCodeDialog by remember { mutableStateOf(false) }
    // YouTube account dialog (combined login/logout)
    var showYouTubeAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, ContentDescriptions.NAVIGATE_BACK)
                    }
                }
            )
        }
    ) { paddingValues ->
        val maxContentWidth = WindowSizeUtil.getMaxContentWidth()
        val horizontalPadding = WindowSizeUtil.getHorizontalPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .then(
                        if (maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified)
                            Modifier.widthIn(max = maxContentWidth)
                        else Modifier.fillMaxWidth()
                    ),
                contentPadding = PaddingValues(horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Video Settings Section
            item {
                Text(
                    text = "Video",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.VideoSettings,
                    title = "Preferred Quality",
                    subtitle = uiState.preferredQuality,
                    onClick = { showQualityDialog = true }
                )
            }

            // Audio Settings Section
            item {
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Normalize Audio Volume",
                    subtitle = "Keeps volume consistent across tracks",
                    checked = uiState.normalizeAudioEnabled,
                    onCheckedChange = { viewModel.setNormalizeAudio(it) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Equalizer,
                    title = "System Equalizer",
                    subtitle = if (viewModel.isMusicPlaying()) "Adjust bass, treble, and more" else "Opens system audio settings",
                    onClick = {
                        try {
                            val equalizerIntent = viewModel.getEqualizerIntent()
                            context.startActivity(equalizerIntent)
                        } catch (e: ActivityNotFoundException) {
                            // Try fallback: open general sound settings
                            try {
                                val fallbackIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                                context.startActivity(fallbackIntent)
                                Toast.makeText(
                                    context,
                                    "No equalizer app found. Opening sound settings instead.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e2: Exception) {
                                Toast.makeText(
                                    context,
                                    "No equalizer or sound settings available on this device",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // Download Settings Section
            item {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Wifi,
                    title = "Download Network",
                    subtitle = uiState.downloadNetworkPreference.displayName,
                    onClick = { showDownloadNetworkDialog = true }
                )
            }

            // Parental Controls Section
            item {
                Text(
                    text = "Safety",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Parental Controls",
                    subtitle = if (uiState.parentalControlsEnabled) "Enabled" else "Disabled",
                    onClick = onNavigateToParentalControls
                )
            }

            // Family Section
            item {
                Text(
                    text = "Family",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                if (uiState.isFamilyLinked) {
                    // Show status only - child cannot unlink (must be done from parent app)
                    SettingsItem(
                        icon = Icons.Default.FamilyRestroom,
                        title = "Connected to Family",
                        subtitle = "Managed by parent via ZimbaBeats Family app",
                        onClick = { } // No action - status display only
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.Link,
                        title = "Link to Family",
                        subtitle = "Enter family code to enable parental controls",
                        onClick = { showFamilyCodeDialog = true }
                    )
                }
            }

            // YouTube Account Section
            item {
                Text(
                    text = "YouTube Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = if (uiState.isYouTubeLoggedIn) Icons.Default.AccountCircle else Icons.Default.MusicNote,
                    title = if (uiState.isYouTubeLoggedIn) "YouTube Music Connected" else "Guest Mode",
                    subtitle = if (uiState.isYouTubeLoggedIn)
                        "Tap to manage your account"
                    else
                        "Streaming works great! Tap for options",
                    onClick = { showYouTubeAccountDialog = true }
                )
            }

            // Appearance Section
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = uiState.themeMode.displayName,
                    onClick = { showThemeDialog = true }
                )
            }

            item {
                SettingsItemWithPreview(
                    icon = Icons.Default.Palette,
                    title = "Accent Color",
                    subtitle = uiState.accentColor.displayName,
                    previewColor = uiState.accentColor.primary,
                    onClick = { showAccentColorDialog = true }
                )
            }

            // Accessibility Section
            item {
                Text(
                    text = "Accessibility",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Screen Reader Optimizations",
                    subtitle = buildString {
                        append(uiState.accessibilityMode.displayName)
                        if (uiState.accessibilityOptimizationsEnabled) {
                            append(" • Active")
                        }
                    },
                    onClick = { showAccessibilityDialog = true }
                )
            }

            // Media Storage Section
            item {
                Text(
                    text = "Media Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                MediaStorageItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Playback Buffer",
                    subtitle = "Cached stream data for smooth playback",
                    currentSize = uiState.playbackBufferSize,
                    maxSize = uiState.playbackBufferLimit,
                    formatFileSize = viewModel::formatFileSize,
                    onClear = { showClearPlaybackBufferDialog = true },
                    onAdjustLimit = { showPlaybackBufferLimitDialog = true }
                )
            }

            item {
                MediaStorageItem(
                    icon = Icons.Default.Download,
                    title = "Saved Media",
                    subtitle = "Downloaded content for offline playback",
                    currentSize = uiState.savedMediaSize,
                    maxSize = uiState.savedMediaLimit,
                    formatFileSize = viewModel::formatFileSize,
                    onClear = { showClearSavedMediaDialog = true },
                    onAdjustLimit = { showSavedMediaLimitDialog = true }
                )
            }

            item {
                MediaStorageItem(
                    icon = Icons.Default.Image,
                    title = "Image Cache",
                    subtitle = "Thumbnails and album art",
                    currentSize = uiState.imageCacheSize,
                    maxSize = uiState.imageCacheLimit,
                    formatFileSize = viewModel::formatFileSize,
                    onClear = { showClearImageCacheDialog = true },
                    onAdjustLimit = { showImageCacheLimitDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "Clear All Cache",
                    subtitle = "Total cache: ${viewModel.formatFileSize(uiState.cacheSize)}",
                    onClick = { showClearCacheDialog = true }
                )
            }

            // About Section
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = uiState.appVersion,
                    onClick = { }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Author",
                    subtitle = uiState.author,
                    onClick = { }
                )
            }

            // Updates Section
            item {
                Text(
                    text = "Updates",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Update,
                    title = "Automatic Update Check",
                    subtitle = "Check for new versions on app launch",
                    checked = uiState.autoUpdateCheckEnabled,
                    onCheckedChange = { viewModel.setAutoUpdateCheck(it) }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.CloudDownload,
                    title = "Update Channel",
                    subtitle = "GitHub Release",
                    onClick = { }
                )
            }

            item {
                SettingsItemWithAction(
                    icon = Icons.Default.Refresh,
                    title = "Check for Update",
                    subtitle = "Last checked: ${viewModel.formatLastUpdateCheck(uiState.lastUpdateCheck)}",
                    isLoading = uiState.isCheckingForUpdate,
                    onClick = {
                        viewModel.checkForUpdate()
                    }
                )
            }

            // Show update available if there is one
            if (uiState.updateResult is UpdateResult.UpdateAvailable) {
                item {
                    UpdateAvailableCard(
                        version = (uiState.updateResult as UpdateResult.UpdateAvailable).version,
                        onClick = { showUpdateAvailableDialog = true }
                    )
                }
            }

            // Support Section
            item {
                Text(
                    text = "Support",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { heading() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Coffee,
                    title = "Buy Me a Coffee",
                    subtitle = "Support development",
                    onClick = { viewModel.openBuyMeCoffee() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Gavel,
                    title = "License",
                    subtitle = "GPL-3.0 (Open Source)",
                    onClick = { showLicenseDialog = true }
                )
            }

            // Copyright
            item {
                Text(
                    text = "© 2024-2030 ZimbaBeats. All rights reserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            }
        }
    }

    // Quality selection dialog
    if (showQualityDialog) {
        QualitySelectionDialog(
            currentQuality = uiState.preferredQuality,
            onQualitySelected = { quality ->
                viewModel.setPreferredQuality(quality)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Clear Cache") },
            text = { Text("This will delete ${viewModel.formatFileSize(uiState.cacheSize)} of cached data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // License dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null) },
            title = { Text("License Information") },
            text = {
                Column {
                    Text(
                        text = "ZimbaBeats",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Copyright (C) 2024-2030 ZimbaBeats",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "GNU General Public License v3.0",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This program is free software: you can redistribute it and/or modify " +
                                "it under the terms of the GNU General Public License as published by " +
                                "the Free Software Foundation, either version 3 of the License, or " +
                                "(at your option) any later version.\n\n" +
                                "This program is distributed in the hope that it will be useful, " +
                                "but WITHOUT ANY WARRANTY; without even the implied warranty of " +
                                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Update Available dialog with in-app download
    if (showUpdateAvailableDialog && uiState.updateResult is UpdateResult.UpdateAvailable) {
        val updateInfo = uiState.updateResult as UpdateResult.UpdateAvailable
        val downloadState = uiState.downloadState

        AlertDialog(
            onDismissRequest = {
                // Don't dismiss during download
                if (downloadState !is DownloadState.Downloading) {
                    showUpdateAvailableDialog = false
                    viewModel.clearUpdateResult()
                }
            },
            icon = {
                Icon(
                    imageVector = when (downloadState) {
                        is DownloadState.Downloading -> Icons.Default.Download
                        is DownloadState.Completed -> Icons.Default.CheckCircle
                        is DownloadState.Failed -> Icons.Default.Error
                        else -> Icons.Default.SystemUpdate
                    },
                    contentDescription = null,
                    tint = when (downloadState) {
                        is DownloadState.Completed -> MaterialTheme.colorScheme.primary
                        is DownloadState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            },
            title = {
                Text(
                    when (downloadState) {
                        is DownloadState.Downloading -> "Downloading..."
                        is DownloadState.Completed -> "Download Complete"
                        is DownloadState.Failed -> "Download Failed"
                        else -> "Update Available"
                    }
                )
            },
            text = {
                Column {
                    Text(
                        text = "Version ${updateInfo.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show download progress
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            Text(
                                text = "Downloading update... ${downloadState.progress}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please wait...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is DownloadState.Completed -> {
                            Text(
                                text = "Download complete! Tap 'Install' to update.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is DownloadState.Failed -> {
                            Text(
                                text = "Error: ${downloadState.error}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You can try again or download from browser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = updateInfo.releaseName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Show APK size if available
                            if (updateInfo.apkSize > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Download size: ${viewModel.formatFileSize(updateInfo.apkSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Release Notes:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.notes.take(500) + if (updateInfo.notes.length > 500) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        OutlinedButton(onClick = { viewModel.cancelDownload() }) {
                            Text("Cancel")
                        }
                    }
                    is DownloadState.Completed -> {
                        Button(onClick = { viewModel.installDownloadedUpdate() }) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install")
                        }
                    }
                    is DownloadState.Failed -> {
                        Button(onClick = { viewModel.downloadUpdate() }) {
                            Text("Retry")
                        }
                    }
                    else -> {
                        Button(onClick = { viewModel.downloadUpdate() }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download")
                        }
                    }
                }
            },
            dismissButton = {
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        // No dismiss during download
                    }
                    is DownloadState.Completed -> {
                        TextButton(onClick = {
                            showUpdateAvailableDialog = false
                            viewModel.clearUpdateResult()
                        }) {
                            Text("Later")
                        }
                    }
                    is DownloadState.Failed -> {
                        TextButton(onClick = {
                            viewModel.openUpdateDownloadPage()
                            showUpdateAvailableDialog = false
                        }) {
                            Text("Open Browser")
                        }
                    }
                    else -> {
                        TextButton(onClick = {
                            showUpdateAvailableDialog = false
                            viewModel.clearUpdateResult()
                        }) {
                            Text("Later")
                        }
                    }
                }
            }
        )
    }

    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = uiState.themeMode,
            onThemeSelected = { theme ->
                viewModel.setThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Accent color dialog
    if (showAccentColorDialog) {
        AccentColorDialog(
            currentColor = uiState.accentColor,
            onColorSelected = { color ->
                viewModel.setAccentColor(color)
                showAccentColorDialog = false
            },
            onDismiss = { showAccentColorDialog = false }
        )
    }

    // Download network dialog
    if (showDownloadNetworkDialog) {
        DownloadNetworkDialog(
            currentPreference = uiState.downloadNetworkPreference,
            onPreferenceSelected = { preference ->
                if (preference == DownloadNetworkPreference.WIFI_AND_MOBILE) {
                    // Show warning before enabling mobile data
                    showDownloadNetworkDialog = false
                    showMobileDataWarningDialog = true
                } else {
                    viewModel.setDownloadNetworkPreference(preference)
                    showDownloadNetworkDialog = false
                }
            },
            onDismiss = { showDownloadNetworkDialog = false }
        )
    }

    // Mobile data warning dialog
    if (showMobileDataWarningDialog) {
        AlertDialog(
            onDismissRequest = { showMobileDataWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Mobile Data Warning") },
            text = {
                Text(
                    "Downloading over mobile data may incur charges from your network provider. " +
                    "Are you sure you want to enable mobile data downloads?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDownloadNetworkPreference(DownloadNetworkPreference.WIFI_AND_MOBILE)
                        showMobileDataWarningDialog = false
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMobileDataWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Accessibility mode dialog
    if (showAccessibilityDialog) {
        AccessibilityModeDialog(
            currentMode = uiState.accessibilityMode,
            isOptimizationsActive = uiState.accessibilityOptimizationsEnabled,
            onModeSelected = { mode ->
                viewModel.setAccessibilityMode(mode)
                showAccessibilityDialog = false
            },
            onDismiss = { showAccessibilityDialog = false }
        )
    }

    // Clear Playback Buffer dialog
    if (showClearPlaybackBufferDialog) {
        AlertDialog(
            onDismissRequest = { showClearPlaybackBufferDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Clear Playback Buffer") },
            text = { Text("This will delete ${viewModel.formatFileSize(uiState.playbackBufferSize)} of cached stream data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPlaybackBuffer()
                    showClearPlaybackBufferDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPlaybackBufferDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Saved Media dialog
    if (showClearSavedMediaDialog) {
        AlertDialog(
            onDismissRequest = { showClearSavedMediaDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Clear Saved Media") },
            text = { Text("This will delete ${viewModel.formatFileSize(uiState.savedMediaSize)} of downloaded content. This cannot be undone. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSavedMedia()
                    showClearSavedMediaDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSavedMediaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Image Cache dialog
    if (showClearImageCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageCacheDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Clear Image Cache") },
            text = { Text("This will delete ${viewModel.formatFileSize(uiState.imageCacheSize)} of cached thumbnails and album art. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearImageCache()
                    showClearImageCacheDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Playback Buffer Limit dialog
    if (showPlaybackBufferLimitDialog) {
        StorageLimitDialog(
            title = "Playback Buffer Limit",
            currentLimit = uiState.playbackBufferLimit,
            minLimit = 50L * 1024 * 1024,  // 50 MB
            maxLimit = 500L * 1024 * 1024, // 500 MB
            formatFileSize = viewModel::formatFileSize,
            onLimitSelected = { limit ->
                viewModel.setPlaybackBufferLimit(limit)
                showPlaybackBufferLimitDialog = false
            },
            onDismiss = { showPlaybackBufferLimitDialog = false }
        )
    }

    // Saved Media Limit dialog
    if (showSavedMediaLimitDialog) {
        StorageLimitDialog(
            title = "Saved Media Limit",
            currentLimit = uiState.savedMediaLimit,
            minLimit = 500L * 1024 * 1024,   // 500 MB
            maxLimit = 5L * 1024 * 1024 * 1024, // 5 GB
            formatFileSize = viewModel::formatFileSize,
            onLimitSelected = { limit ->
                viewModel.setSavedMediaLimit(limit)
                showSavedMediaLimitDialog = false
            },
            onDismiss = { showSavedMediaLimitDialog = false }
        )
    }

    // Image Cache Limit dialog
    if (showImageCacheLimitDialog) {
        StorageLimitDialog(
            title = "Image Cache Limit",
            currentLimit = uiState.imageCacheLimit,
            minLimit = 50L * 1024 * 1024,  // 50 MB
            maxLimit = 500L * 1024 * 1024, // 500 MB
            formatFileSize = viewModel::formatFileSize,
            onLimitSelected = { limit ->
                viewModel.setImageCacheLimit(limit)
                showImageCacheLimitDialog = false
            },
            onDismiss = { showImageCacheLimitDialog = false }
        )
    }

    // Family Code dialog
    if (showFamilyCodeDialog) {
        FamilyCodeDialog(
            isLinking = uiState.isLinkingFamily,
            error = uiState.familyLinkingError,
            onCodeEntered = { code ->
                viewModel.linkToFamily(code)
            },
            onDismiss = {
                showFamilyCodeDialog = false
                viewModel.clearFamilyLinkError()
            },
            onSuccess = {
                showFamilyCodeDialog = false
            }
        )
    }

    // Handle successful family link
    LaunchedEffect(uiState.isFamilyLinked) {
        if (uiState.isFamilyLinked && showFamilyCodeDialog) {
            showFamilyCodeDialog = false
        }
    }

    // YouTube Account dialog (combined login/logout)
    if (showYouTubeAccountDialog) {
        YouTubeAccountDialog(
            isLoggedIn = uiState.isYouTubeLoggedIn,
            onDismiss = { showYouTubeAccountDialog = false },
            onSignOut = {
                viewModel.signOutYouTube()
                showYouTubeAccountDialog = false
            }
        )
    }
}

@Composable
private fun FamilyCodeDialog(
    isLinking: Boolean,
    error: String?,
    onCodeEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var familyCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLinking) onDismiss() },
        icon = { Icon(Icons.Default.Link, contentDescription = null) },
        title = { Text("Link to Family") },
        text = {
            Column {
                Text(
                    text = "Enter the 6-digit code from the ZimbaBeats Family app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = familyCode,
                    onValueChange = { newValue ->
                        familyCode = newValue.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    },
                    label = { Text("Family Code") },
                    placeholder = { Text("ABC123") },
                    singleLine = true,
                    enabled = !isLinking,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCodeEntered(familyCode) },
                enabled = familyCode.length == 6 && !isLinking
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Link")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLinking
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityDescription = "$title, currently set to $subtitle. Double tap to change."

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityDescription
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Part of parent semantics
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null, // Decorative
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityDescription = "$title. $subtitle. Currently ${if (checked) "on" else "off"}."

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityDescription
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Part of parent semantics
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun QualitySelectionDialog(
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf("Auto", "High (1080p)", "Medium (720p)", "Low (480p)")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Quality") },
        text = {
            Column {
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == currentQuality,
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(quality)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(theme.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AccentColorDialog(
    currentColor: AccentColor,
    onColorSelected: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent Color") },
        text = {
            Column {
                AccentColor.entries.forEach { color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onColorSelected(color) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = color == currentColor,
                            onClick = { onColorSelected(color) }
                        )
                        // Color preview circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color.primary)
                                .then(
                                    if (color == currentColor) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        Text(color.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DownloadNetworkDialog(
    currentPreference: DownloadNetworkPreference,
    onPreferenceSelected: (DownloadNetworkPreference) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Network") },
        text = {
            Column {
                DownloadNetworkPreference.entries.forEach { preference ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPreferenceSelected(preference) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preference == currentPreference,
                            onClick = { onPreferenceSelected(preference) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(preference.displayName)
                            if (preference == DownloadNetworkPreference.WIFI_AND_MOBILE) {
                                Text(
                                    text = "May incur data charges",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SettingsItemWithPreview(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    previewColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Color preview circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(previewColor)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccessibilityModeDialog(
    currentMode: AccessibilityMode,
    isOptimizationsActive: Boolean,
    onModeSelected: (AccessibilityMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Accessibility, contentDescription = null) },
        title = { Text("Screen Reader Optimizations") },
        text = {
            Column {
                Text(
                    text = "Configure TalkBack and screen reader support",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isOptimizationsActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Optimizations are currently active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AccessibilityMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(mode.displayName)
                            Text(
                                text = when (mode) {
                                    AccessibilityMode.AUTO -> "Enables when TalkBack is active"
                                    AccessibilityMode.ALWAYS_ON -> "Always enable enhanced accessibility"
                                    AccessibilityMode.ALWAYS_OFF -> "Disable accessibility optimizations"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MediaStorageItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    currentSize: Long,
    maxSize: Long,
    formatFileSize: (Long) -> String,
    onClear: () -> Unit,
    onAdjustLimit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (maxSize > 0) (currentSize.toFloat() / maxSize.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when {
        progress > 0.9f -> MaterialTheme.colorScheme.error
        progress > 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar showing usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Size info and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatFileSize(currentSize)} / ${formatFileSize(maxSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAdjustLimit) {
                        Text("Limit")
                    }
                    TextButton(
                        onClick = onClear,
                        enabled = currentSize > 0
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageLimitDialog(
    title: String,
    currentLimit: Long,
    minLimit: Long,
    maxLimit: Long,
    formatFileSize: (Long) -> String,
    onLimitSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(
        ((currentLimit - minLimit).toFloat() / (maxLimit - minLimit).toFloat()).coerceIn(0f, 1f)
    ) }

    val selectedLimit = (minLimit + (sliderValue * (maxLimit - minLimit)).toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Set the maximum storage limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = formatFileSize(selectedLimit),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(minLimit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(maxLimit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLimitSelected(selectedLimit) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsItemWithAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityDescription = "$title. $subtitle. Double tap to activate."

    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityDescription
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    version: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Version $version is ready to download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download update",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
