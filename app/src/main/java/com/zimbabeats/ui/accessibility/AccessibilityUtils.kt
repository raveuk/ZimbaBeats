package com.zimbabeats.ui.accessibility

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import kotlinx.coroutines.delay

/**
 * Accessibility utilities for TalkBack support
 */
object AccessibilityUtils {

    /**
     * Check if TalkBack or other accessibility service is enabled
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isEnabled == true
    }

    /**
     * Check if TalkBack specifically is enabled
     */
    fun isTalkBackEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isTouchExplorationEnabled == true
    }

    /**
     * Announce a message via TalkBack
     */
    fun announce(view: View, message: String) {
        view.announceForAccessibility(message)
    }

    /**
     * Send an accessibility event
     */
    fun sendAccessibilityEvent(view: View, eventType: Int = AccessibilityEvent.TYPE_ANNOUNCEMENT) {
        view.sendAccessibilityEvent(eventType)
    }
}

/**
 * Composable helper to announce messages for TalkBack
 */
@Composable
fun rememberAccessibilityAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return remember(view) { { message: String ->
        view.announceForAccessibility(message)
    }}
}

/**
 * Effect to announce a message when it changes
 * Only announces non-null, non-empty messages
 */
@Composable
fun AccessibilityAnnouncement(message: String?) {
    val view = LocalView.current
    LaunchedEffect(message) {
        if (!message.isNullOrEmpty()) {
            view.announceForAccessibility(message)
        }
    }
}

/**
 * Format duration for accessibility (e.g., "3 minutes 45 seconds")
 */
fun formatDurationForAccessibility(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append("$hours hour${if (hours != 1L) "s" else ""}")
            if (minutes > 0 || seconds > 0) append(" ")
        }
        if (minutes > 0) {
            append("$minutes minute${if (minutes != 1L) "s" else ""}")
            if (seconds > 0) append(" ")
        }
        if (seconds > 0 || (hours == 0L && minutes == 0L)) {
            append("$seconds second${if (seconds != 1L) "s" else ""}")
        }
    }
}

/**
 * Format view count for accessibility
 */
fun formatViewCountForAccessibility(viewCount: Long): String {
    return when {
        viewCount >= 1_000_000_000 -> "${viewCount / 1_000_000_000} billion views"
        viewCount >= 1_000_000 -> "${viewCount / 1_000_000} million views"
        viewCount >= 1_000 -> "${viewCount / 1_000} thousand views"
        viewCount == 1L -> "1 view"
        else -> "$viewCount views"
    }
}

/**
 * Format progress percentage for accessibility
 */
fun formatProgressForAccessibility(progress: Float): String {
    val percentage = (progress * 100).toInt()
    return "$percentage percent complete"
}

/**
 * Format file size for accessibility
 */
fun formatFileSizeForAccessibility(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1_000_000_000 -> String.format("%.1f gigabytes", sizeBytes / 1_000_000_000.0)
        sizeBytes >= 1_000_000 -> String.format("%.1f megabytes", sizeBytes / 1_000_000.0)
        sizeBytes >= 1_000 -> String.format("%.1f kilobytes", sizeBytes / 1_000.0)
        else -> "$sizeBytes bytes"
    }
}

/**
 * Content descriptions for common actions
 */
object ContentDescriptions {
    // Navigation
    const val NAVIGATE_BACK = "Go back"
    const val OPEN_SETTINGS = "Open settings"
    const val OPEN_SEARCH = "Search for videos"
    const val OPEN_LIBRARY = "Open library"
    const val OPEN_PLAYLISTS = "Open playlists"
    const val NOTIFICATIONS = "Notifications"

    // Player controls
    const val PLAY_VIDEO = "Play video"
    const val PAUSE_VIDEO = "Pause video"
    const val SKIP_FORWARD = "Skip forward 10 seconds"
    const val SKIP_BACKWARD = "Skip backward 10 seconds"
    const val NEXT_VIDEO = "Next video"
    const val PREVIOUS_VIDEO = "Previous video"
    const val TOGGLE_FULLSCREEN = "Toggle fullscreen"
    const val EXPAND_PLAYER = "Expand player"
    const val CLOSE_PLAYER = "Close player"

    // Video actions
    const val ADD_TO_FAVORITES = "Add to favorites"
    const val REMOVE_FROM_FAVORITES = "Remove from favorites"
    const val ADD_TO_PLAYLIST = "Add to playlist"
    const val DOWNLOAD_VIDEO = "Download video"
    const val SHARE_VIDEO = "Share video"
    const val DELETE_VIDEO = "Delete video"

    // Downloads
    const val PAUSE_DOWNLOAD = "Pause download"
    const val RESUME_DOWNLOAD = "Resume download"
    const val CANCEL_DOWNLOAD = "Cancel download"

    // Settings
    const val OPEN_PARENTAL_CONTROLS = "Open parental controls"
    const val TOGGLE_DARK_MODE = "Toggle dark mode"
    const val CHANGE_THEME = "Change theme"
    const val CHANGE_ACCENT_COLOR = "Change accent color"

    // Video card
    fun videoCard(title: String, channelName: String, duration: String): String {
        return "$title by $channelName, duration $duration"
    }

    fun videoCardWithProgress(title: String, channelName: String, progressPercent: Int): String {
        return "$title by $channelName, $progressPercent percent watched"
    }

    fun playButton(isPlaying: Boolean): String {
        return if (isPlaying) PAUSE_VIDEO else PLAY_VIDEO
    }

    fun favoriteButton(isFavorite: Boolean): String {
        return if (isFavorite) REMOVE_FROM_FAVORITES else ADD_TO_FAVORITES
    }

    fun downloadButton(state: String): String {
        return when (state) {
            "downloading" -> "Downloading, tap to pause"
            "paused" -> "Download paused, tap to resume"
            "completed" -> "Downloaded"
            else -> DOWNLOAD_VIDEO
        }
    }
}

/**
 * Accessibility announcements for state changes
 */
object Announcements {
    // Playback
    fun playbackStarted(videoTitle: String) = "Now playing: $videoTitle"
    fun playbackPaused() = "Playback paused"
    fun playbackResumed() = "Playback resumed"
    fun seekForward(seconds: Int) = "Skipped forward $seconds seconds"
    fun seekBackward(seconds: Int) = "Skipped backward $seconds seconds"
    fun videoEnded() = "Video ended"

    // Downloads
    fun downloadStarted(videoTitle: String) = "Download started for $videoTitle"
    fun downloadCompleted(videoTitle: String) = "Download completed for $videoTitle"
    fun downloadFailed(videoTitle: String) = "Download failed for $videoTitle"
    fun downloadPaused(videoTitle: String) = "Download paused for $videoTitle"
    fun downloadResumed(videoTitle: String) = "Download resumed for $videoTitle"
    fun downloadCanceled(videoTitle: String) = "Download canceled for $videoTitle"
    const val NETWORK_RESTRICTION = "Download blocked. Wi-Fi required for downloads."

    // Parental controls
    const val CONTENT_BLOCKED = "This content is restricted by parental controls"
    const val SEARCH_BLOCKED = "Search is restricted by parental controls"
    fun ageRestricted(ageLevel: String) = "Content restricted to $ageLevel and under"

    // Favorites & Playlists
    fun addedToFavorites(videoTitle: String) = "$videoTitle added to favorites"
    fun removedFromFavorites(videoTitle: String) = "$videoTitle removed from favorites"
    fun addedToPlaylist(videoTitle: String, playlistName: String) = "$videoTitle added to $playlistName"

    // Navigation
    fun screenOpened(screenName: String) = "$screenName screen"
    fun searchResults(count: Int) = "$count search result${if (count != 1) "s" else ""} found"
    const val LOADING = "Loading"
    const val LOADING_COMPLETE = "Content loaded"

    // Errors
    fun error(message: String) = "Error: $message"
    const val NETWORK_ERROR = "Network error. Please check your connection."
}

/**
 * Remember TalkBack enabled state with listener for changes
 */
@Composable
fun rememberTalkBackState(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(AccessibilityUtils.isTalkBackEnabled(context)) }

    DisposableEffect(context) {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            state.value = enabled
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)

        onDispose {
            accessibilityManager?.removeTouchExplorationStateChangeListener(listener)
        }
    }

    return state
}

/**
 * Remember accessibility enabled state
 */
@Composable
fun rememberAccessibilityState(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(AccessibilityUtils.isAccessibilityEnabled(context)) }

    DisposableEffect(context) {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            state.value = enabled
        }
        accessibilityManager?.addAccessibilityStateChangeListener(listener)

        onDispose {
            accessibilityManager?.removeAccessibilityStateChangeListener(listener)
        }
    }

    return state
}

/**
 * Focus requester for screen title - requests focus when screen opens
 * Only requests focus when TalkBack is enabled
 */
@Composable
fun rememberScreenTitleFocusRequester(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val isTalkBackEnabled = AccessibilityUtils.isTalkBackEnabled(context)

    LaunchedEffect(Unit) {
        if (isTalkBackEnabled) {
            // Small delay to ensure composition is complete
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request may fail if component not yet ready
            }
        }
    }

    return focusRequester
}

/**
 * Focus requester for dialog first actionable element
 * Requests focus when dialog opens (only when TalkBack is enabled)
 */
@Composable
fun rememberDialogFocusRequester(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val isTalkBackEnabled = AccessibilityUtils.isTalkBackEnabled(context)

    LaunchedEffect(Unit) {
        if (isTalkBackEnabled) {
            // Small delay for dialog animation
            delay(200)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request may fail if component not yet ready
            }
        }
    }

    return focusRequester
}

/**
 * Modifier extension for screen title that handles focus for accessibility
 */
fun Modifier.screenTitleFocus(focusRequester: FocusRequester): Modifier {
    return this
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * Modifier extension for dialog first actionable element
 */
fun Modifier.dialogFirstFocus(focusRequester: FocusRequester): Modifier {
    return this
        .focusRequester(focusRequester)
        .focusable()
}
