package com.zimbabeats.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zimbabeats.ui.theme.ShimmerBackground
import com.zimbabeats.ui.theme.ShimmerHighlight

/**
 * Shimmer effect modifier - applies animated gradient to any composable
 */
fun Modifier.shimmer(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "shimmer_offset"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                ShimmerBackground,
                ShimmerHighlight,
                ShimmerBackground
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        ),
        shape = RoundedCornerShape(8.dp)
    )
        .onGloballyPositioned { size = it.size }
}

/**
 * Shimmer placeholder for video cards - Spotify style
 */
@Composable
fun VideoCardShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Thumbnail placeholder with 16:9 aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shimmer()
        )

        // Title placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .shimmer()
        )

        // Channel name placeholder
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(12.dp)
                .shimmer()
        )
    }
}

/**
 * Shimmer for horizontal video row
 */
@Composable
fun VideoRowShimmer(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section title shimmer
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(24.dp)
                .shimmer()
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(itemCount) {
                VideoCardShimmer()
            }
        }
    }
}

/**
 * Quick action chips shimmer
 */
@Composable
fun QuickChipsShimmer(
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        userScrollEnabled = false
    ) {
        items(3) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(40.dp)
                    .shimmer()
            )
        }
    }
}

/**
 * Full home screen shimmer skeleton - Spotify style
 */
@Composable
fun HomeShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Quick action chips shimmer
        QuickChipsShimmer()

        // Video sections
        VideoRowShimmer(
            itemCount = 4,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        VideoRowShimmer(
            itemCount = 4,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        VideoRowShimmer(
            itemCount = 4,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Shimmer for playlist item in list
 */
@Composable
fun PlaylistItemShimmer(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Playlist icon placeholder
        Box(
            modifier = Modifier
                .size(56.dp)
                .shimmer()
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Playlist name
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .shimmer()
            )

            // Video count
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp)
                    .shimmer()
            )
        }
    }
}

/**
 * Shimmer for video item in vertical list
 */
@Composable
fun VideoListItemShimmer(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .size(120.dp, 68.dp)
                .shimmer()
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .shimmer()
            )

            // Channel name
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp)
                    .shimmer()
            )
        }
    }
}

/**
 * Shimmer for playlist detail screen
 */
@Composable
fun PlaylistDetailShimmer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shimmer()
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(24.dp)
                        .shimmer()
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .shimmer()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Video list shimmer
        repeat(6) {
            VideoListItemShimmer()
        }
    }
}
