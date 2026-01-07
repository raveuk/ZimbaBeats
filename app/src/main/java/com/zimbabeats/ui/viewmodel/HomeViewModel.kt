package com.zimbabeats.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.SearchRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

/**
 * Video category for browsing
 */
data class VideoCategory(
    val id: String,
    val name: String,
    val icon: String? = null, // emoji or icon name
    val query: String // search query for this category
)

/**
 * Popular channel for browsing
 */
data class PopularChannel(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val query: String // search query for this channel
)

/**
 * Mood-based video section
 */
data class MoodSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val videos: List<Video> = emptyList(),
    val recentlyWatched: List<Video> = emptyList(),
    val mostWatched: List<Video> = emptyList(),
    val favorites: List<Video> = emptyList(),
    // New sections
    val quickPicks: List<Video> = emptyList(),
    val categories: List<VideoCategory> = emptyList(),
    val popularChannels: List<PopularChannel> = emptyList(),
    val moodSections: List<MoodSection> = emptyList(),
    val categoryVideos: Map<String, List<Video>> = emptyMap(), // categoryId -> videos
    val channelVideos: Map<String, List<Video>> = emptyMap(), // channelId -> videos
    // State
    val error: String? = null,
    // Companion app integration
    val companionAppConnected: Boolean = false,
    val parentalControlEnabled: Boolean = false,
    val selectedAgeLevel: AgeRating = AgeRating.ALL
)

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val searchRepository: SearchRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    // Cloud-based content filter (Firebase)
    private val contentFilter get() = cloudPairingClient.contentFilter

    companion object {
        // ========== AGE-SPECIFIC MOOD SECTIONS ==========

        private fun getMoodSectionsForAge(ageLevel: com.zimbabeats.core.domain.model.AgeRating): List<MoodSection> {
            return when (ageLevel) {
                com.zimbabeats.core.domain.model.AgeRating.FIVE_PLUS -> listOf(
                    MoodSection("naptime", "Nap Time", "Soothing videos for little ones"),
                    MoodSection("learning", "First Learning", "ABCs, colors & shapes"),
                    MoodSection("playtime", "Play Time", "Fun sensory videos"),
                    MoodSection("storytime", "Story Time", "Simple animated stories")
                )
                com.zimbabeats.core.domain.model.AgeRating.EIGHT_PLUS -> listOf(
                    MoodSection("naptime", "Rest Time", "Calming videos"),
                    MoodSection("learning", "Learning", "Fun educational content"),
                    MoodSection("playtime", "Play Time", "Games & activities"),
                    MoodSection("storytime", "Stories", "Animated adventures")
                )
                com.zimbabeats.core.domain.model.AgeRating.TEN_PLUS -> listOf(
                    MoodSection("bedtime", "Bedtime", "Calm & relaxing videos"),
                    MoodSection("learning", "Learning Time", "Educational content"),
                    MoodSection("singalong", "Sing Along", "Fun music & dance"),
                    MoodSection("adventure", "Adventure", "Exciting cartoon episodes")
                )
                com.zimbabeats.core.domain.model.AgeRating.TWELVE_PLUS -> listOf(
                    MoodSection("chill", "Chill Time", "Relaxing videos"),
                    MoodSection("creative", "Get Creative", "DIY & crafts"),
                    MoodSection("explore", "Explore", "Science & discovery"),
                    MoodSection("music", "Music", "Fun songs & dance")
                )
                com.zimbabeats.core.domain.model.AgeRating.THIRTEEN_PLUS -> listOf(
                    MoodSection("chill", "Chill", "Relaxing content"),
                    MoodSection("creative", "Creative", "DIY & art projects"),
                    MoodSection("explore", "Explore", "Science & nature"),
                    MoodSection("gaming", "Gaming", "Game videos & streams")
                )
                com.zimbabeats.core.domain.model.AgeRating.FOURTEEN_PLUS -> listOf(
                    MoodSection("trending", "Trending Now", "What's popular"),
                    MoodSection("skills", "Learn Skills", "How-to videos"),
                    MoodSection("entertainment", "Entertainment", "Shows & vlogs"),
                    MoodSection("music", "Music", "Popular songs")
                )
                com.zimbabeats.core.domain.model.AgeRating.SIXTEEN_PLUS -> listOf(
                    MoodSection("vibes", "Good Vibes", "Music & chill content"),
                    MoodSection("sports", "Sports", "Highlights & action"),
                    MoodSection("comedy", "Comedy", "Funny videos"),
                    MoodSection("music", "Music", "Top hits")
                )
                else -> listOf(
                    MoodSection("trending", "Trending", "Popular right now"),
                    MoodSection("music", "Music", "Top hits"),
                    MoodSection("entertainment", "Entertainment", "Shows & vlogs"),
                    MoodSection("discover", "Discover", "Something new")
                )
            }
        }

        private fun getMoodQueriesForAge(ageLevel: com.zimbabeats.core.domain.model.AgeRating): Map<String, String> {
            return when (ageLevel) {
                com.zimbabeats.core.domain.model.AgeRating.FIVE_PLUS -> mapOf(
                    "naptime" to "baby lullaby sleep music",
                    "learning" to "abc colors shapes toddler",
                    "playtime" to "hey bear sensory baby",
                    "storytime" to "simple stories for toddlers"
                )
                com.zimbabeats.core.domain.model.AgeRating.TEN_PLUS -> mapOf(
                    "bedtime" to "lullaby songs for kids sleep",
                    "learning" to "educational videos for kids",
                    "singalong" to "kids songs dance along",
                    "adventure" to "peppa pig bluey full episodes"
                )
                com.zimbabeats.core.domain.model.AgeRating.TWELVE_PLUS -> mapOf(
                    "chill" to "relaxing music for kids",
                    "creative" to "diy crafts for kids",
                    "explore" to "science experiments for kids",
                    "music" to "kids pop songs dance"
                )
                com.zimbabeats.core.domain.model.AgeRating.FOURTEEN_PLUS -> mapOf(
                    "trending" to "trending videos for teens",
                    "skills" to "how to tutorials teens",
                    "entertainment" to "teen shows vlogs",
                    "music" to "popular music songs teens"
                )
                com.zimbabeats.core.domain.model.AgeRating.SIXTEEN_PLUS -> mapOf(
                    "vibes" to "popular music videos 2024",
                    "sports" to "sports highlights best moments",
                    "comedy" to "funny videos compilation",
                    "music" to "top music hits 2024"
                )
                else -> mapOf(
                    "trending" to "trending videos today",
                    "music" to "music videos 2024",
                    "entertainment" to "entertainment videos",
                    "discover" to "viral videos"
                )
            }
        }

        // ========== AGE-SPECIFIC CATEGORIES ==========

        private fun getCategoriesForAge(ageLevel: com.zimbabeats.core.domain.model.AgeRating): List<VideoCategory> {
            return when (ageLevel) {
                com.zimbabeats.core.domain.model.AgeRating.FIVE_PLUS -> listOf(
                    VideoCategory("nursery", "Nursery Rhymes", "\uD83C\uDFB5", "cocomelon nursery rhymes"),
                    VideoCategory("sensory", "Sensory", "\uD83C\uDF08", "hey bear sensory videos"),
                    VideoCategory("learning", "First Learning", "\uD83D\uDCDA", "abc learning toddler"),
                    VideoCategory("animals", "Animals", "\uD83D\uDC3B", "animal sounds for babies"),
                    VideoCategory("songs", "Baby Songs", "\uD83C\uDFB6", "super simple songs"),
                    VideoCategory("stories", "Simple Stories", "\uD83D\uDCD6", "stories for toddlers")
                )
                com.zimbabeats.core.domain.model.AgeRating.TEN_PLUS -> listOf(
                    VideoCategory("cartoons", "Cartoons", "\uD83C\uDFAC", "peppa pig bluey paw patrol"),
                    VideoCategory("nursery", "Nursery Rhymes", "\uD83C\uDFB5", "nursery rhymes songs"),
                    VideoCategory("educational", "Learning", "\uD83D\uDCDA", "educational videos for kids"),
                    VideoCategory("music", "Kids Music", "\uD83C\uDFA4", "kids songs dance"),
                    VideoCategory("stories", "Story Time", "\uD83D\uDCD6", "bedtime stories for kids"),
                    VideoCategory("animals", "Animals", "\uD83D\uDC3E", "animals for kids learning")
                )
                com.zimbabeats.core.domain.model.AgeRating.TWELVE_PLUS -> listOf(
                    VideoCategory("cartoons", "Cartoons", "\uD83C\uDFAC", "cartoon network nickelodeon"),
                    VideoCategory("gaming", "Gaming", "\uD83C\uDFAE", "minecraft roblox for kids"),
                    VideoCategory("science", "Science", "\uD83D\uDD2C", "science experiments for kids"),
                    VideoCategory("crafts", "DIY & Crafts", "\u2702\uFE0F", "diy crafts for kids"),
                    VideoCategory("music", "Music", "\uD83C\uDFB5", "kids pop songs"),
                    VideoCategory("animals", "Animals & Nature", "\uD83C\uDF0D", "wild animals documentary kids")
                )
                com.zimbabeats.core.domain.model.AgeRating.FOURTEEN_PLUS -> listOf(
                    VideoCategory("gaming", "Gaming", "\uD83C\uDFAE", "popular gaming videos"),
                    VideoCategory("music", "Music", "\uD83C\uDFB5", "pop music videos"),
                    VideoCategory("sports", "Sports", "\u26BD", "sports highlights"),
                    VideoCategory("tech", "Tech", "\uD83D\uDCBB", "tech videos for teens"),
                    VideoCategory("howto", "How-To", "\uD83D\uDCA1", "tutorials for teens"),
                    VideoCategory("vlogs", "Vlogs", "\uD83D\uDCF9", "teen vlogs")
                )
                com.zimbabeats.core.domain.model.AgeRating.SIXTEEN_PLUS -> listOf(
                    VideoCategory("music", "Music", "\uD83C\uDFB5", "music videos 2024"),
                    VideoCategory("gaming", "Gaming", "\uD83C\uDFAE", "gaming videos"),
                    VideoCategory("sports", "Sports", "\u26BD", "sports highlights"),
                    VideoCategory("comedy", "Comedy", "\uD83D\uDE02", "funny videos"),
                    VideoCategory("tech", "Tech", "\uD83D\uDCBB", "tech reviews"),
                    VideoCategory("vlogs", "Vlogs", "\uD83D\uDCF9", "daily vlogs")
                )
                else -> listOf(
                    VideoCategory("music", "Music", "\uD83C\uDFB5", "music videos 2024"),
                    VideoCategory("gaming", "Gaming", "\uD83C\uDFAE", "gaming videos"),
                    VideoCategory("comedy", "Comedy", "\uD83D\uDE02", "funny videos"),
                    VideoCategory("sports", "Sports", "\u26BD", "sports highlights"),
                    VideoCategory("tech", "Tech", "\uD83D\uDCBB", "tech reviews"),
                    VideoCategory("vlogs", "Vlogs", "\uD83D\uDCF9", "daily vlogs")
                )
            }
        }

        // ========== AGE-SPECIFIC CHANNELS ==========

        private fun getChannelsForAge(ageLevel: com.zimbabeats.core.domain.model.AgeRating): List<PopularChannel> {
            return when (ageLevel) {
                com.zimbabeats.core.domain.model.AgeRating.FIVE_PLUS -> listOf(
                    PopularChannel("heybear", "Hey Bear Sensory", null, "hey bear sensory"),
                    PopularChannel("cocomelon", "Cocomelon", null, "cocomelon"),
                    PopularChannel("supersimple", "Super Simple", null, "super simple songs"),
                    PopularChannel("littlebabybum", "Little Baby Bum", null, "little baby bum"),
                    PopularChannel("babybus", "BabyBus", null, "babybus"),
                    PopularChannel("msrachel", "Ms Rachel", null, "ms rachel songs for littles")
                )
                com.zimbabeats.core.domain.model.AgeRating.TEN_PLUS -> listOf(
                    PopularChannel("cocomelon", "Cocomelon", null, "cocomelon"),
                    PopularChannel("peppapig", "Peppa Pig", null, "peppa pig"),
                    PopularChannel("pawpatrol", "Paw Patrol", null, "paw patrol"),
                    PopularChannel("bluey", "Bluey", null, "bluey"),
                    PopularChannel("babyshark", "Baby Shark", null, "baby shark pinkfong"),
                    PopularChannel("disney", "Disney Junior", null, "disney junior")
                )
                com.zimbabeats.core.domain.model.AgeRating.TWELVE_PLUS -> listOf(
                    PopularChannel("cartoonnetwork", "Cartoon Network", null, "cartoon network"),
                    PopularChannel("nickelodeon", "Nickelodeon", null, "nickelodeon"),
                    PopularChannel("minecraft", "Minecraft", null, "minecraft gameplay"),
                    PopularChannel("roblox", "Roblox", null, "roblox gameplay"),
                    PopularChannel("natgeo", "Nat Geo Kids", null, "national geographic kids"),
                    PopularChannel("dude", "Dude Perfect", null, "dude perfect")
                )
                com.zimbabeats.core.domain.model.AgeRating.FOURTEEN_PLUS -> listOf(
                    PopularChannel("mrbeast", "MrBeast", null, "mrbeast"),
                    PopularChannel("markiplier", "Markiplier", null, "markiplier"),
                    PopularChannel("smosh", "Smosh", null, "smosh"),
                    PopularChannel("dude", "Dude Perfect", null, "dude perfect"),
                    PopularChannel("unspeakable", "Unspeakable", null, "unspeakable"),
                    PopularChannel("sssniperwolf", "SSSniperwolf", null, "sssniperwolf")
                )
                com.zimbabeats.core.domain.model.AgeRating.SIXTEEN_PLUS -> listOf(
                    PopularChannel("mrbeast", "MrBeast", null, "mrbeast"),
                    PopularChannel("pewdiepie", "PewDiePie", null, "pewdiepie"),
                    PopularChannel("mkbhd", "MKBHD", null, "mkbhd"),
                    PopularChannel("veritasium", "Veritasium", null, "veritasium"),
                    PopularChannel("kurzgesagt", "Kurzgesagt", null, "kurzgesagt"),
                    PopularChannel("linus", "Linus Tech Tips", null, "linus tech tips")
                )
                else -> listOf(
                    PopularChannel("mrbeast", "MrBeast", null, "mrbeast"),
                    PopularChannel("pewdiepie", "PewDiePie", null, "pewdiepie"),
                    PopularChannel("mkbhd", "MKBHD", null, "mkbhd"),
                    PopularChannel("veritasium", "Veritasium", null, "veritasium"),
                    PopularChannel("kurzgesagt", "Kurzgesagt", null, "kurzgesagt"),
                    PopularChannel("linus", "Linus Tech Tips", null, "linus tech tips")
                )
            }
        }

        // Age-specific content queries for quick picks and curated content
        private fun getAgeSpecificQueries(ageLevel: com.zimbabeats.core.domain.model.AgeRating): List<String> {
            return when (ageLevel) {
                com.zimbabeats.core.domain.model.AgeRating.FIVE_PLUS -> listOf(
                    "baby sensory videos",
                    "cocomelon nursery rhymes",
                    "super simple songs",
                    "hey bear sensory"
                )
                com.zimbabeats.core.domain.model.AgeRating.TEN_PLUS -> listOf(
                    "peppa pig full episodes",
                    "paw patrol",
                    "bluey episodes",
                    "disney junior"
                )
                com.zimbabeats.core.domain.model.AgeRating.TWELVE_PLUS -> listOf(
                    "cartoon network",
                    "nickelodeon shows",
                    "minecraft for kids",
                    "roblox gameplay"
                )
                com.zimbabeats.core.domain.model.AgeRating.FOURTEEN_PLUS -> listOf(
                    "teen shows",
                    "family friendly gaming",
                    "science experiments for kids",
                    "diy crafts"
                )
                com.zimbabeats.core.domain.model.AgeRating.SIXTEEN_PLUS -> listOf(
                    "popular music videos",
                    "gaming videos",
                    "funny videos compilation",
                    "sports highlights"
                )
                else -> listOf(
                    "trending videos",
                    "music videos 2024",
                    "popular videos"
                )
            }
        }

        // Legacy static lists (kept for general mode)
        private val KIDS_CATEGORIES = listOf(
            VideoCategory("cartoons", "Cartoons", "\uD83C\uDFAC", "cartoons for kids"),
            VideoCategory("nursery", "Nursery Rhymes", "\uD83C\uDFB5", "nursery rhymes songs"),
            VideoCategory("educational", "Learning", "\uD83D\uDCDA", "educational videos for kids"),
            VideoCategory("music", "Kids Music", "\uD83C\uDFA4", "kids songs dance"),
            VideoCategory("stories", "Story Time", "\uD83D\uDCD6", "bedtime stories for kids"),
            VideoCategory("animals", "Animals", "\uD83D\uDC3E", "animals for kids learning")
        )

        private val KIDS_CHANNELS = listOf(
            PopularChannel("cocomelon", "Cocomelon", null, "cocomelon"),
            PopularChannel("peppapig", "Peppa Pig", null, "peppa pig"),
            PopularChannel("pawpatrol", "Paw Patrol", null, "paw patrol"),
            PopularChannel("bluey", "Bluey", null, "bluey"),
            PopularChannel("babyshark", "Baby Shark", null, "baby shark pinkfong"),
            PopularChannel("disney", "Disney Junior", null, "disney junior")
        )

        private val KIDS_MOODS = listOf(
            MoodSection("bedtime", "Bedtime", "Calm & relaxing videos"),
            MoodSection("learning", "Learning Time", "Educational content"),
            MoodSection("singalong", "Sing Along", "Fun music & dance"),
            MoodSection("storytime", "Story Time", "Animated stories")
        )

        // ========== GENERAL MODE CONTENT (Parental Control OFF) ==========

        private val GENERAL_CATEGORIES = listOf(
            VideoCategory("music", "Music", "🎵", "music videos 2024"),
            VideoCategory("gaming", "Gaming", "🎮", "gaming videos"),
            VideoCategory("comedy", "Comedy", "😂", "funny videos"),
            VideoCategory("sports", "Sports", "⚽", "sports highlights"),
            VideoCategory("tech", "Tech", "💻", "tech reviews"),
            VideoCategory("vlogs", "Vlogs", "📹", "daily vlogs")
        )

        private val GENERAL_CHANNELS = listOf(
            PopularChannel("mrbeast", "MrBeast", null, "mrbeast"),
            PopularChannel("pewdiepie", "PewDiePie", null, "pewdiepie"),
            PopularChannel("mkbhd", "MKBHD", null, "mkbhd"),
            PopularChannel("veritasium", "Veritasium", null, "veritasium"),
            PopularChannel("kurzgesagt", "Kurzgesagt", null, "kurzgesagt"),
            PopularChannel("linus", "Linus Tech Tips", null, "linus tech tips")
        )

        private val GENERAL_MOODS = listOf(
            MoodSection("chill", "Chill Vibes", "Relaxing content"),
            MoodSection("hype", "Get Hyped", "Exciting & energetic"),
            MoodSection("learn", "Learn Something", "Educational content"),
            MoodSection("laugh", "Make Me Laugh", "Comedy & humor")
        )

        private val GENERAL_MOOD_QUERIES = mapOf(
            "chill" to "chill music videos lofi",
            "hype" to "epic moments compilation",
            "learn" to "interesting facts documentary",
            "laugh" to "funny videos compilation"
        )
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var previousParentalEnabled: Boolean? = null
    private var previousAgeLevel: AgeRating? = null

    init {
        // Start observing parental control bridge state
        observeBridgeState()
        // Load user-specific content (watch history, favorites, most watched)
        loadUserContent()
        // Note: No auto-refresh - content refreshes on:
        // 1. Pull-to-refresh by user
        // 2. Parental settings change (from companion app)
        // 3. App resume after extended background time
    }

    /**
     * Convert cloud age rating string to AgeRating enum
     */
    private fun parseAgeRating(ageRatingString: String?): AgeRating {
        return when (ageRatingString) {
            "FIVE_PLUS" -> AgeRating.FIVE_PLUS
            "EIGHT_PLUS" -> AgeRating.EIGHT_PLUS
            "TEN_PLUS" -> AgeRating.TEN_PLUS
            "TWELVE_PLUS" -> AgeRating.TWELVE_PLUS
            "THIRTEEN_PLUS" -> AgeRating.THIRTEEN_PLUS
            "FOURTEEN_PLUS" -> AgeRating.FOURTEEN_PLUS
            "SIXTEEN_PLUS" -> AgeRating.SIXTEEN_PLUS
            else -> AgeRating.ALL
        }
    }

    /**
     * Observe parental control state from Firebase cloud
     * When not linked to family, operates in unrestricted mode
     */
    private fun observeBridgeState() {
        // Observe pairing status
        viewModelScope.launch {
            cloudPairingClient.pairingStatus.collect { pairingStatus ->
                val isLinkedToFamily = pairingStatus is com.zimbabeats.cloud.PairingStatus.Paired
                val isEnabled = contentFilter?.filterSettings?.value?.let { true } ?: false

                // Get age rating from cloud settings (synced from Family app)
                val cloudAgeRating = cloudPairingClient.cloudSettings.value?.ageRating
                val currentAgeLevel = parseAgeRating(cloudAgeRating)

                // Kids mode is ON when linked to family
                val isKidsMode = isLinkedToFamily

                Log.d(TAG, "Cloud state received - linkedToFamily: $isLinkedToFamily, enabled: $isEnabled, age level: ${currentAgeLevel.displayName}, isKidsMode: $isKidsMode")
                Log.d(TAG, "Previous values - enabled: $previousParentalEnabled, age: ${previousAgeLevel?.displayName}")

                // IMPORTANT: Check for initial load BEFORE updating previous values
                val isInitialLoad = previousParentalEnabled == null

                // Check if parental control state OR age level changed
                val enabledChanged = previousParentalEnabled != null && previousParentalEnabled != isKidsMode
                val ageLevelChanged = previousAgeLevel != null && previousAgeLevel != currentAgeLevel
                val settingsChanged = enabledChanged || ageLevelChanged

                // Update previous values AFTER calculating changes
                previousParentalEnabled = isKidsMode
                previousAgeLevel = currentAgeLevel

                // Set categories, channels, and moods based on parental control AND age level
                val ageCategories = if (isKidsMode) getCategoriesForAge(currentAgeLevel) else GENERAL_CATEGORIES
                val ageChannels = if (isKidsMode) getChannelsForAge(currentAgeLevel) else GENERAL_CHANNELS
                val ageMoods = if (isKidsMode) getMoodSectionsForAge(currentAgeLevel) else GENERAL_MOODS

                Log.d(TAG, "Setting age-specific content for ${currentAgeLevel.displayName}")
                Log.d(TAG, "Categories: ${ageCategories.map { it.name }}")
                Log.d(TAG, "Channels: ${ageChannels.map { it.name }}")
                Log.d(TAG, "Moods: ${ageMoods.map { it.title }}")

                _uiState.value = _uiState.value.copy(
                    companionAppConnected = isLinkedToFamily,
                    parentalControlEnabled = isKidsMode,
                    selectedAgeLevel = currentAgeLevel,
                    categories = ageCategories,
                    popularChannels = ageChannels,
                    moodSections = ageMoods
                )

                // If parental control state or age level changed, reload all content
                if (settingsChanged) {
                    Log.d(TAG, "=== PARENTAL SETTINGS CHANGED ===")
                    Log.d(TAG, "enabled changed: $enabledChanged, age changed: $ageLevelChanged")
                    Log.d(TAG, "New mode: isKidsMode=$isKidsMode, ageLevel=${currentAgeLevel.displayName}")
                    Log.d(TAG, "Clearing content and reloading with age-specific queries...")
                    // Clear ALL existing content including mood sections to show refresh happening
                    _uiState.value = _uiState.value.copy(
                        videos = emptyList(),
                        quickPicks = emptyList(),
                        categoryVideos = emptyMap(),
                        channelVideos = emptyMap(),
                        moodSections = ageMoods, // Reset mood sections with age-specific templates
                        isLoading = true
                    )
                    loadVideoContent()
                    loadMoodSections(currentAgeLevel)
                } else if (isInitialLoad) {
                    // Initial load - load content with correct parental settings
                    Log.d(TAG, "=== INITIAL BRIDGE STATE LOAD ===")
                    Log.d(TAG, "isKidsMode=$isKidsMode, ageLevel=${currentAgeLevel.displayName}")
                    loadVideoContent()
                    loadMoodSections(currentAgeLevel)
                }
            }
        }

        // Also observe cloud settings changes (e.g., parent changes age rating)
        viewModelScope.launch {
            cloudPairingClient.cloudSettings.collect { cloudSettings ->
                if (cloudSettings == null) return@collect

                val pairingStatus = cloudPairingClient.pairingStatus.value
                val isLinkedToFamily = pairingStatus is com.zimbabeats.cloud.PairingStatus.Paired
                if (!isLinkedToFamily) return@collect

                val newAgeLevel = parseAgeRating(cloudSettings.ageRating)
                val currentAgeLevel = _uiState.value.selectedAgeLevel

                // Check if age level actually changed
                if (newAgeLevel != currentAgeLevel) {
                    Log.d(TAG, "=== CLOUD SETTINGS CHANGED ===")
                    Log.d(TAG, "Age rating changed: ${currentAgeLevel.displayName} -> ${newAgeLevel.displayName}")

                    // Update UI state with new age level
                    val ageCategories = getCategoriesForAge(newAgeLevel)
                    val ageChannels = getChannelsForAge(newAgeLevel)
                    val ageMoods = getMoodSectionsForAge(newAgeLevel)

                    _uiState.value = _uiState.value.copy(
                        selectedAgeLevel = newAgeLevel,
                        categories = ageCategories,
                        popularChannels = ageChannels,
                        moodSections = ageMoods,
                        videos = emptyList(),
                        quickPicks = emptyList()
                    )

                    // Reload content with new age level
                    loadVideoContent()
                    loadMoodSections(newAgeLevel)
                }
            }
        }
    }

    /**
     * Load video content (trending, curated, quick picks)
     */
    private fun loadVideoContent() {
        viewModelScope.launch {
            Log.d(TAG, "Starting to load video content...")
            _uiState.value = _uiState.value.copy(isLoading = true)

            Log.d(TAG, "Fetching trending videos...")
            when (val result = videoRepository.fetchTrendingVideos(20)) {
                is Resource.Success -> {
                    Log.d(TAG, "Trending videos fetched: ${result.data.size} videos")
                    if (result.data.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            videos = filterVideosByBridge(result.data),
                            isLoading = false
                        )
                    } else {
                        Log.d(TAG, "Trending empty, loading curated content...")
                        loadCuratedVideoContent()
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to fetch trending: ${result.message}")
                    loadCuratedVideoContent()
                }
                else -> {}
            }
        }

        // Load quick picks
        viewModelScope.launch {
            loadQuickPicks()
        }
    }

    /**
     * Load user-specific content (watch history, favorites, most watched)
     */
    private fun loadUserContent() {
        // Load recently watched
        viewModelScope.launch {
            videoRepository.getWatchHistory(10).collect { history ->
                _uiState.value = _uiState.value.copy(
                    recentlyWatched = filterVideosByBridge(history)
                )
            }
        }

        // Load most watched
        viewModelScope.launch {
            videoRepository.getMostWatched(10).collect { mostWatched ->
                _uiState.value = _uiState.value.copy(
                    mostWatched = filterVideosByBridge(mostWatched)
                )
            }
        }

        // Load favorites
        viewModelScope.launch {
            videoRepository.getFavoriteVideos().collect { favorites ->
                _uiState.value = _uiState.value.copy(
                    favorites = filterVideosByBridge(favorites)
                )
            }
        }
    }

    /**
     * Load mood-based sections with videos using age-specific queries
     */
    private fun loadMoodSections(ageLevel: com.zimbabeats.core.domain.model.AgeRating) {
        val isKidsMode = _uiState.value.parentalControlEnabled
        val moodQueries = if (isKidsMode) getMoodQueriesForAge(ageLevel) else GENERAL_MOOD_QUERIES
        val moods = if (isKidsMode) getMoodSectionsForAge(ageLevel) else GENERAL_MOODS

        Log.d(TAG, "Loading mood sections for ${ageLevel.displayName}")
        Log.d(TAG, "Mood queries: $moodQueries")

        moods.forEach { mood ->
            viewModelScope.launch {
                val query = moodQueries[mood.id] ?: return@launch
                Log.d(TAG, "Loading mood '${mood.title}' with query: $query")
                try {
                    when (val result = searchRepository.searchVideos(query, 8)) {
                        is Resource.Success -> {
                            val filteredVideos = filterVideosByBridge(result.data.videos)

                            // Update the mood section with videos
                            val updatedMoods = _uiState.value.moodSections.map { section ->
                                if (section.id == mood.id) {
                                    section.copy(videos = filteredVideos, isLoading = false)
                                } else section
                            }
                            _uiState.value = _uiState.value.copy(moodSections = updatedMoods)
                            Log.d(TAG, "Loaded ${filteredVideos.size} videos for mood: ${mood.title} (query: $query)")
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Failed to load mood ${mood.id}: ${result.message}")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading mood ${mood.id}: ${e.message}")
                }
            }
        }
    }

    /**
     * Load quick picks - personalized suggestions or defaults
     */
    private suspend fun loadQuickPicks() {
        val isKidsMode = _uiState.value.parentalControlEnabled
        val ageLevel = _uiState.value.selectedAgeLevel
        // Use first age-specific query for quick picks
        val query = if (isKidsMode) {
            getAgeSpecificQueries(ageLevel).firstOrNull() ?: "popular kids videos"
        } else {
            "trending videos today"
        }
        Log.d(TAG, "Loading quick picks with query: $query")

        try {
            when (val result = searchRepository.searchVideos(query, 12)) {
                is Resource.Success -> {
                    val filteredVideos = filterVideosByBridge(result.data.videos)
                    _uiState.value = _uiState.value.copy(quickPicks = filteredVideos)
                    Log.d(TAG, "Loaded ${filteredVideos.size} quick picks")
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load quick picks: ${result.message}")
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading quick picks: ${e.message}")
        }
    }

    /**
     * Load videos for a specific category
     */
    fun loadCategoryVideos(category: VideoCategory) {
        viewModelScope.launch {
            try {
                when (val result = searchRepository.searchVideos(category.query, 10)) {
                    is Resource.Success -> {
                        val filteredVideos = filterVideosByBridge(result.data.videos)
                        val updatedMap = _uiState.value.categoryVideos.toMutableMap()
                        updatedMap[category.id] = filteredVideos
                        _uiState.value = _uiState.value.copy(categoryVideos = updatedMap)
                        Log.d(TAG, "Loaded ${filteredVideos.size} videos for category: ${category.name}")
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to load category ${category.id}: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading category ${category.id}: ${e.message}")
            }
        }
    }

    /**
     * Load videos for a specific channel
     */
    fun loadChannelVideos(channel: PopularChannel) {
        viewModelScope.launch {
            try {
                when (val result = searchRepository.searchVideos(channel.query, 10)) {
                    is Resource.Success -> {
                        val filteredVideos = filterVideosByBridge(result.data.videos)
                        val updatedMap = _uiState.value.channelVideos.toMutableMap()
                        updatedMap[channel.id] = filteredVideos
                        _uiState.value = _uiState.value.copy(channelVideos = updatedMap)
                        Log.d(TAG, "Loaded ${filteredVideos.size} videos for channel: ${channel.name}")
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to load channel ${channel.id}: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading channel ${channel.id}: ${e.message}")
            }
        }
    }

    private suspend fun loadCuratedVideoContent() {
        val isKidsMode = _uiState.value.parentalControlEnabled
        val ageLevel = _uiState.value.selectedAgeLevel
        Log.d(TAG, "Loading curated video content... isKidsMode=$isKidsMode, ageLevel=${ageLevel.displayName}")
        val allVideos = mutableListOf<Video>()

        // Use age-specific curated queries for different age groups
        val curatedQueries = if (isKidsMode) {
            getAgeSpecificQueries(ageLevel)
        } else {
            listOf(
                "music videos 2024",
                "trending videos",
                "popular videos today",
                "viral videos"
            )
        }

        // Search for each curated query and collect videos
        for (query in curatedQueries) {
            try {
                when (val result = searchRepository.searchVideos(query, 5)) {
                    is Resource.Success -> {
                        Log.d(TAG, "Curated search '$query': ${result.data.videos.size} videos")
                        allVideos.addAll(result.data.videos)
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Curated search '$query' failed: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Curated search '$query' exception: ${e.message}")
            }

            // Stop if we have enough videos
            if (allVideos.size >= 20) break
        }

        Log.d(TAG, "Total curated videos loaded: ${allVideos.size}")
        _uiState.value = _uiState.value.copy(
            videos = filterVideosByBridge(allVideos.distinctBy { it.id }.take(20)),
            isLoading = false
        )
    }

    /**
     * Filter videos using Cloud Content Filter (Firebase-based).
     * When not linked to family, all videos are allowed (unrestricted mode).
     * When linked, each video is checked against parent-defined restrictions.
     */
    private fun filterVideosByBridge(videos: List<Video>): List<Video> {
        // If not linked to family, allow all videos (unrestricted mode)
        val filter = contentFilter
        if (filter == null) {
            Log.d(TAG, "Not linked to family - unrestricted mode, allowing all ${videos.size} videos")
            return videos
        }

        Log.d(TAG, "Filtering ${videos.size} videos via Firebase cloud filter")

        val filteredVideos = videos.filter { video ->
            val blockResult = filter.shouldBlockContent(
                videoId = video.id,
                title = video.title,
                channelId = video.channelId,
                channelName = video.channelName,
                description = video.description,
                durationSeconds = video.duration,
                isLiveStream = false, // Video model doesn't track live streams
                category = video.category?.name
            )
            if (blockResult.isBlocked) {
                Log.d(TAG, "BLOCKED video '${video.title}': ${blockResult.message}")
            }
            !blockResult.isBlocked
        }

        Log.d(TAG, "Filtered to ${filteredVideos.size} videos (${videos.size - filteredVideos.size} blocked)")
        return filteredVideos
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            when (val result = videoRepository.toggleFavorite(videoId)) {
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {
                    // Success - favorites will update via Flow
                }
            }
        }
    }

    /**
     * Manual refresh - pull to refresh
     * Provides smooth refresh experience without clearing existing content
     */
    fun refreshVideos() {
        viewModelScope.launch {
            Log.d(TAG, "Manual refresh triggered")
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            // Load fresh content (keeps existing content visible during load)
            loadVideoContent()
            // Also reload mood sections with age-specific content
            loadMoodSections(_uiState.value.selectedAgeLevel)
            // Refresh user content (watch history, favorites)
            loadUserContent()

            // Small delay for smooth UI transition
            kotlinx.coroutines.delay(300)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
            Log.d(TAG, "Manual refresh complete")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
