package com.zimbabeats.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reason for content being blocked - used to display appropriate block screen
 */
sealed class BlockReason {
    data class Bedtime(val message: String, val endTime: String) : BlockReason()
    data class ScreenTime(val usedMinutes: Int, val limitMinutes: Int) : BlockReason()
    data class ContentBlocked(val reason: String) : BlockReason()
}

/**
 * Block Screen - Shows when content is blocked due to bedtime or screen time limits
 *
 * This is a kid-friendly screen that explains why they can't watch right now.
 */
@Composable
fun BlockScreen(
    blockReason: BlockReason,
    onParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (blockReason) {
        is BlockReason.Bedtime -> BedtimeBlockScreen(
            message = blockReason.message,
            endTime = blockReason.endTime,
            onParentUnlock = onParentUnlock,
            modifier = modifier
        )
        is BlockReason.ScreenTime -> ScreenTimeBlockScreen(
            usedMinutes = blockReason.usedMinutes,
            limitMinutes = blockReason.limitMinutes,
            onParentUnlock = onParentUnlock,
            modifier = modifier
        )
        is BlockReason.ContentBlocked -> ContentBlockedScreen(
            reason = blockReason.reason,
            onParentUnlock = onParentUnlock,
            modifier = modifier
        )
    }
}

@Composable
private fun BedtimeBlockScreen(
    message: String,
    endTime: String,
    onParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animated moon
    val infiniteTransition = rememberInfiniteTransition(label = "bedtime")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "moonScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a237e), // Deep blue
                        Color(0xFF0d1b3e), // Darker blue
                        Color(0xFF000814)  // Almost black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Stars decoration
        StarsDecoration()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Moon icon
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale),
                tint = Color(0xFFFFF59D) // Yellow moon
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Time for Bed!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sweet dreams!",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB39DDB), // Light purple
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Parent unlock button (small, not prominent)
            TextButton(
                onClick = onParentUnlock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parent Access", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScreenTimeBlockScreen(
    usedMinutes: Int,
    limitMinutes: Int,
    onParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animated hourglass
    val infiniteTransition = rememberInfiniteTransition(label = "screentime")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "hourglassRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF6F00), // Orange
                        Color(0xFFE65100), // Deep orange
                        Color(0xFFBF360C)  // Dark orange
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Timer icon
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Screen Time's Up!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time stats
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You watched for",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = formatMinutes(usedMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Today's limit: ${formatMinutes(limitMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Time to do something else!\nPlay outside, read a book, or rest your eyes.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Parent unlock button
            TextButton(
                onClick = onParentUnlock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parent Access", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ContentBlockedScreen(
    reason: String,
    onParentUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF7B1FA2), // Purple
                        Color(0xFF4A148C), // Deep purple
                        Color(0xFF311B92)  // Dark purple
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Content Not Available",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Try watching something else!",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFCE93D8), // Light purple
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(
                onClick = onParentUnlock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parent Access", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StarsDecoration() {
    // Simple star effect
    val stars = remember {
        List(20) {
            Triple(
                (0..100).random() / 100f, // x position
                (0..100).random() / 100f, // y position
                (3..8).random().dp // size
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stars")

    stars.forEachIndexed { index, (x, y, size) ->
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000 + (index * 100),
                    easing = EaseInOut
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "star$index"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(
                    x = (x * 400).dp - 200.dp,
                    y = (y * 600).dp - 300.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .alpha(alpha)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

/**
 * Screen Time Warning Dialog - Shows when approaching limit
 */
@Composable
fun ScreenTimeWarningDialog(
    remainingMinutes: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = if (remainingMinutes == 1) "1 Minute Left!" else "$remainingMinutes Minutes Left!",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Your screen time is almost up. Start finishing what you're watching!",
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("OK")
            }
        }
    )
}

/**
 * Parent Unlock Dialog - PIN entry to unlock restrictions
 */
@Composable
fun ParentUnlockDialog(
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    pinError: String? = null,
    options: List<UnlockOption> = listOf(
        UnlockOption.EXTEND_30_MIN,
        UnlockOption.EXTEND_60_MIN,
        UnlockOption.UNLOCK_TODAY
    )
) {
    var pin by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf(options.first()) }
    var showOptions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Parent Access", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter your PIN to unlock",
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            pin = it
                        }
                    },
                    label = { Text("PIN") },
                    isError = pinError != null,
                    singleLine = true,
                    modifier = Modifier.width(150.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (pinError != null) {
                    Text(
                        text = pinError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (showOptions) {
                    Text(
                        text = "Select unlock option:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption == option,
                                onClick = { selectedOption = option }
                            )
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 4) {
                        onPinEntered("$pin:${selectedOption.name}")
                    }
                },
                enabled = pin.length == 4
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class UnlockOption(val displayName: String, val minutes: Int) {
    EXTEND_30_MIN("Extend 30 minutes", 30),
    EXTEND_60_MIN("Extend 1 hour", 60),
    UNLOCK_TODAY("Unlock for today", -1)
}

private fun formatMinutes(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
}
