# ZimbaBeats Bug Tracker

## Bug #1: Video Not Displaying (Audio Only)
**Reported:** 2026-03-05
**Status:** Fixed (v1.0.54)
**Fixed:** 2026-03-05

### Description
When playing a video, the player shows audio is playing but no video is visible on screen.

### Root Cause Analysis
The NewPipe fallback added in v1.0.53 extracts **audio-only streams** via `extractAudioStream()`. When Innertube combined streams fail, the fallback only provides audio, not video.

**Problem Code:** `VideoPlayerViewModel.kt:284`
```kotlin
val newPipeResult = newPipeExtractor.extractAudioStream(videoId)
// This returns AUDIO stream only, no video!
```

### Fix Applied
- Added new `extractVideoStream()` method to `NewPipeExtractor.kt` that:
  - Prioritizes combined video+audio streams from `StreamInfo.videoStreams`
  - Falls back to video-only + separate audio if needed
  - Returns highest resolution available (prefers mp4)
- Updated `VideoPlayerViewModel.kt` to use `extractVideoStream()` instead of `extractAudioStream()`

**Files Modified:**
- `core/data/src/main/java/com/zimbabeats/core/data/remote/youtube/NewPipeExtractor.kt` - added `extractVideoStream()` method and `VideoExtractionResult` data class
- `app/src/main/java/com/zimbabeats/ui/viewmodel/VideoPlayerViewModel.kt` (line 284) - changed to use `extractVideoStream()`

---

## Bug #2: Next Button Not Visible / Single Video Queue
**Reported:** 2026-03-05
**Status:** Fixed (v1.0.54)
**Fixed:** 2026-03-05

### Description
When playing a video, the next button is not visible. Only one video is in the queue at a time.

### Root Cause Analysis
1. `VideoPlayerScreen.kt` uses ExoPlayer's built-in controls which hide next/previous buttons when queue has only 1 item
2. `ZimbaBeatsPlayer` only supports single video playback - no queue management
3. Related videos are loaded and displayed as clickable UI elements, NOT added to the playback queue
4. The `PlaybackQueue.kt` class exists but is only used for MUSIC, not video

### Fix Applied
Integrated existing `MediaControllerManager` and `PlaybackQueue` with video player:

1. **Added MediaControllerManager to VideoPlayerViewModel** - now manages video queue
2. **Auto-populate queue** - When related videos load, current video + related videos are added to queue
3. **Added queue navigation** - `skipToNext()` and `skipToPrevious()` methods
4. **Added UI buttons** - Previous/Next buttons shown in portrait mode action bar
5. **Queue state tracking** - `hasNextVideo`, `hasPreviousVideo`, `queueSize`, `currentQueueIndex` in UI state

**Files Modified:**
- `app/src/main/java/com/zimbabeats/ui/viewmodel/VideoPlayerViewModel.kt`:
  - Added `MediaControllerManager` dependency
  - Added queue observation in `init` block
  - Added `skipToNext()`, `skipToPrevious()`, `playRelatedVideo()` methods
  - Updated `loadRelatedVideos()` to populate queue
  - Added queue state fields to `VideoPlayerUiState`
- `app/src/main/java/com/zimbabeats/di/ViewModelModule.kt` - Added MediaControllerManager dependency
- `app/src/main/java/com/zimbabeats/ui/screen/player/VideoPlayerScreen.kt`:
  - Added SkipPrevious and SkipNext IconButtons
  - Updated related video click to use `playRelatedVideo()`

---

---

## Bug #3: Mobile Data - Search and Playback Fails on Weak Signal
**Reported:** 2026-03-06
**Status:** Fixed (v1.0.55)
**Fixed:** 2026-03-06

### Description
Video search, music search, and playback don't work on mobile data (only works with strong signal). Works fine on WiFi.

### Root Cause Analysis
Comparing with SimpMusic revealed several network configuration issues:

1. **HTTP/2 Protocol** - Default HTTP/2 causes stream reset errors on unstable mobile connections
2. **Short Timeouts** - Connect timeout of 15s too short for slow mobile networks
3. **No Connection Pooling** - Each request creates new connection (inefficient on mobile)

**ZimbaBeats vs SimpMusic Comparison:**

| Setting | ZimbaBeats (Before) | SimpMusic | Issue |
|---------|---------------------|-----------|-------|
| HTTP Protocol | HTTP/2 | HTTP/1.1 | Stream resets! |
| Connect Timeout | 15s | 20s | Too short |
| Request Timeout | 30s | 20s | OK |
| Connection Pool | None | 20 conn, 5min | No reuse |

### Fix Applied
Updated HTTP client configurations to be mobile-friendly:

**1. RepositoryModule.kt (Ktor HttpClient)**
```kotlin
engine {
    config {
        // Force HTTP/1.1 to prevent stream reset errors
        protocols(listOf(Protocol.HTTP_1_1))
        // Connection pooling for mobile efficiency
        connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
        // Extended timeouts
        connectTimeout(30, TimeUnit.SECONDS)  // Was 15s
        readTimeout(45, TimeUnit.SECONDS)     // Was 30s
    }
}
install(HttpTimeout) {
    requestTimeoutMillis = 60000  // Was 30s
    connectTimeoutMillis = 30000  // Was 15s
    socketTimeoutMillis = 45000   // Was 30s
}
```

**2. NewPipeExtractor.kt (OkHttp client)**
```kotlin
.protocols(listOf(Protocol.HTTP_1_1))
.connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
```

**Files Modified:**
- `core/data/src/main/java/com/zimbabeats/core/data/di/RepositoryModule.kt`
- `core/data/src/main/java/com/zimbabeats/core/data/remote/youtube/NewPipeExtractor.kt`

**Summary of Changes:**

| Setting | Before | After |
|---------|--------|-------|
| Connect Timeout | 15s | 30s |
| Read/Socket Timeout | 30s | 45s |
| Request Timeout | 30s | 60s |
| HTTP Protocol | HTTP/2 | HTTP/1.1 |
| Connection Pool | None | 20 conn, 5min |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| v1.0.55 | 2026-03-06 | Fixed mobile data issues (Bug #3) |
| v1.0.54 | 2026-03-05 | Fixed video not displaying (Bug #1), Restored video queue (Bug #2) |
| v1.0.53 | 2026-03-05 | Added NewPipe fallback (introduced Bug #1) |
| v1.0.52 | 2026-03-04 | Fixed viewing history sync |
| v1.0.51 | 2026-03-02 | Fixed viewing history sync (immutable release issue) |
| v1.0.50 | 2026-03-02 | Fixed video favorites, notification controls, The Weeknd playback |
