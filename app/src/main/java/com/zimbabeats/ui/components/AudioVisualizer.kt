package com.zimbabeats.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Audio visualizer progress bar with animated bars.
 * Shows playback progress with animated waveform effect when playing.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    progress: Float,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier
) {
    val barCount = 40

    // Create infinite transition for animation
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    // Random seed for each bar's animation offset
    val barOffsets = remember { List(barCount) { Random.nextFloat() * 1000f } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val barProgress = index.toFloat() / barCount
            val isActive = barProgress <= progress

            // Animate height when playing
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (300 + barOffsets[index].toInt() % 400),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val height = when {
                isPlaying && isActive -> animatedHeight
                isActive -> 0.6f
                else -> 0.2f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                    .background(
                        if (isActive) {
                            Brush.verticalGradient(
                                colors = listOf(primaryColor, secondaryColor)
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.3f),
                                    secondaryColor.copy(alpha = 0.3f)
                                )
                            )
                        }
                    )
            )
        }
    }
}

/**
 * Simpler waveform visualizer that shows progress with subtle animation
 */
@Composable
fun WaveformProgress(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val barCount = 50

    // Subtle pulse animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val barProgress = index.toFloat() / barCount
            val isActive = barProgress <= progress

            // Create wave pattern for visual interest
            val waveHeight = when {
                index % 4 == 0 -> 1f
                index % 4 == 1 -> 0.7f
                index % 4 == 2 -> 0.85f
                else -> 0.6f
            }

            val finalHeight = if (isActive) waveHeight else 0.3f
            val alpha = if (isPlaying && isActive) pulseAlpha else 1f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(finalHeight)
                    .background(
                        color = if (isActive) {
                            accentColor.copy(alpha = alpha)
                        } else {
                            accentColor.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}
