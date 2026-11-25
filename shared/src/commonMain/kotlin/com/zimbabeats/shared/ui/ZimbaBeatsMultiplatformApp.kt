package com.zimbabeats.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zimbabeats.shared.filter.KidzSafeFilter
import com.zimbabeats.shared.model.AgeRatingFilter
import com.zimbabeats.shared.model.SharedVideo
import com.zimbabeats.shared.network.SharedInnertubeClient
import com.zimbabeats.shared.player.PlatformVideoPlayer
import kotlinx.coroutines.launch

private val ZimbaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    secondary = Color(0xFF4ECDC4),
    tertiary = Color(0xFFFFE66D),
    background = Color(0xFF121318),
    surface = Color(0xFF1E2028)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZimbaBeatsMultiplatformApp() {
    val scope = rememberCoroutineScope()
    val innertubeClient = remember { SharedInnertubeClient() }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Kids Songs & Nursery Rhymes") }
    var selectedAgeRating by remember { mutableStateOf(AgeRatingFilter.UNDER_8) }

    var videos by remember { mutableStateOf<List<SharedVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var activeVideo by remember { mutableStateOf<SharedVideo?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var isResolvingStream by remember { mutableStateOf(false) }

    val kidCategories = listOf(
        "Kids Songs & Nursery Rhymes",
        "Cartoons for Kids",
        "Learning & Science for Kids",
        "Bedtime Lullabies",
        "Animal Videos for Kids"
    )

    fun loadContent(query: String) {
        scope.launch {
            isLoading = true
            val raw = innertubeClient.searchKidSafeVideos(query)
            videos = KidzSafeFilter.filterVideos(raw, selectedAgeRating)
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory, selectedAgeRating) {
        loadContent(selectedCategory)
    }

    MaterialTheme(colorScheme = ZimbaDarkColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ZimbaBeats",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = true,
                            onClick = {
                                val nextOrd = (selectedAgeRating.ordinal + 1) % AgeRatingFilter.entries.size
                                selectedAgeRating = AgeRatingFilter.entries[nextOrd]
                            },
                            label = { Text("KidzSafe: ${selectedAgeRating.label}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            selectedCategory = "Kids Music & Sing Along"
                        },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                // Active Video Player Drawer
                activeVideo?.let { currentVideo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isResolvingStream -> CircularProgressIndicator()
                                    activeStreamUrl != null -> PlatformVideoPlayer(
                                        streamUrl = activeStreamUrl!!,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    else -> Text(
                                        text = "Unable to load stream",
                                        color = Color.White
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentVideo.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentVideo.channelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = {
                                    activeVideo = null
                                    activeStreamUrl = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Player")
                                }
                            }
                        }
                    }
                }

                if (selectedTab == 2) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search safe videos & songs...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                if (searchQuery.isNotBlank()) {
                                    loadContent("$searchQuery for kids")
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Run Search")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(kidCategories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) }
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos, key = { it.id }) { video ->
                            VideoCardItem(
                                video = video,
                                onClick = {
                                    activeVideo = video
                                    activeStreamUrl = null
                                    isResolvingStream = true
                                    scope.launch {
                                        val streams = innertubeClient.resolveStreamUrls(video.id)
                                        activeStreamUrl = streams.firstOrNull { !it.isAudioOnly }?.url
                                            ?: streams.firstOrNull()?.url
                                        isResolvingStream = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCardItem(
    video: SharedVideo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (video.durationText.isNotEmpty()) {
                        Text(
                            text = " • ${video.durationText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
