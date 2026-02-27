package com.zimbabeats.media.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.zimbabeats.media.R

/**
 * Custom notification provider that ensures both Previous and Next buttons
 * are always shown in the media notification, regardless of queue state.
 */
@UnstableApi
class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    init {
        setSmallIcon(R.drawable.ic_notification)
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val buttons = mutableListOf<CommandButton>()

        // Always add Previous button
        buttons.add(
            CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName("Previous")
                .build()
        )

        // Add Play/Pause button
        buttons.add(
            CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName(if (showPauseButton) "Pause" else "Play")
                .build()
        )

        // Always add Next button
        buttons.add(
            CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setDisplayName("Next")
                .build()
        )

        return ImmutableList.copyOf(buttons)
    }
}
