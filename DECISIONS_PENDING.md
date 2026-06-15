# Decisions Pending

A list of small UX/behaviour decisions that have been deferred. Come back to these when there's signal from real usage.

---

## PiP back-button gating (v1.0.70)

**Where:** `app/src/main/java/com/zimbabeats/ui/screen/player/VideoPlayerScreen.kt`, the `BackHandler` block near the `DisposableEffect` that registers PiP state.

**Current behaviour** (shipped in v1.0.70):
```kotlin
BackHandler(enabled = playerState.isPlaying && !isInPipMode) {
    activity?.enterPipMode()
}
```

- Press **back** while video is **playing** → enter PiP, playback continues in floating window.
- Press **back** while video is **paused** → default back, screen pops, player released.

Rationale: a paused video reads as "user is done watching" → exit. A playing video reads as "user wants to multitask" → PiP.

**Alternative to consider** if the gated behaviour feels wrong in practice:
- Always enter PiP on back regardless of play state — change `enabled = playerState.isPlaying && !isInPipMode` to `enabled = !isInPipMode`. User would need to dismiss the PiP window explicitly to fully exit.
- Show a tiny prompt ("Continue in mini window?") on first back-press in a session. Discoverable but adds friction.

**How to decide:** watch telemetry once Piece 5 events have a few weeks of data — specifically how often `dl_download_completed` fires vs. user re-engagement with the PiP window. If users rarely come back to a paused-then-PiP'd video, the current gate is fine.

---

## Unified video MediaSession (v1.0.72)

**Where:** `VideoPlayerViewModel` owns its own ExoPlayer instance via `viewModel.getPlayer()`. The `PlaybackService` MediaLibraryService owns a separate ExoPlayer that's used for music + Android Auto. The MiniPlayer's `MediaControllerManager` connects to PlaybackService's session.

**Current behaviour (shipped in v1.0.72):**
- Mini-player's play button calls `onExpand(videoId)` instead of `mediaController.play()` because the video player's ExoPlayer is released when the video screen leaves the composition.
- Pause works because pause is a no-op against a dead player (the visible state is already "paused").
- Tile-tap, play-button-tap, and back-from-PiP all converge on "expand to full player", which rebuilds the player and resumes from saved progress.

**The proper fix:**
Route video playback through the same `PlaybackService` MediaLibraryService that music uses. One unified ExoPlayer instance, one MediaSession, both music and video share the controller. Benefits:
- Mini-player can truly play/pause without re-entering full screen.
- Background audio playback survives screen rotation, app backgrounding without needing PiP.
- Notification controls work for both media types from one code path.
- Casting / Bluetooth controls work uniformly.

**Why not now:**
- Video has different requirements (PiP, fullscreen toggle, custom controls overlay, gesture handling on the surface) that the PlaybackService currently isn't set up to handle.
- Risk: regressing the recently-shipped Android Auto music flow if we touch PlaybackService.
- Refactor scope: probably 2–3 days of careful work + thorough regression testing on music + Android Auto.

**When to decide:** if `dl_download_completed` telemetry shows a lot of users tapping play in the mini-player (which means they expect inline playback), prioritise this. Otherwise the current "expand to play" is fine — it just means video doesn't have true mini-player playback.

---
