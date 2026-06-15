# Video Download Rewrite — Master Plan

Goal: Replace the broken download path with a self-healing video download stack
that produces real `.mp4` files at every quality tier (360p–4K), recovers from
YouTube breakage without app updates, and matches the resilience of SimpMusic.

Current bug: `DownloadManager.getAvailableQualities()` (DownloadManager.kt:254)
filters `!isVideoOnly`, which YouTube no longer serves for most modern videos.
Dialog shows "No downloadable qualities available", download silently aborts.

## Architecture

```
                      ┌──────────────────────────┐
   User taps Download │   VideoPlayerViewModel   │
                      │   .requestDownload()     │
                      └────────────┬─────────────┘
                                   ▼
                      ┌──────────────────────────┐
                      │     StreamResolver       │  ← new interface
                      │  (primary→fallback)      │
                      └────┬─────────────────┬───┘
                           │                 │
                  primary  │                 │ fallback
                           ▼                 ▼
                ┌───────────────────┐  ┌──────────────────┐
                │  InnertubeClient  │  │ youtubedl-android│  ← yt-dlp
                │  (existing)       │  │  (auto-updated)  │
                └────────┬──────────┘  └────────┬─────────┘
                         │                      │
                         └──────────┬───────────┘
                                    ▼
                       ┌──────────────────────────┐
                       │   StreamPair             │
                       │   (videoOnly + audioOnly)│
                       └────────────┬─────────────┘
                                    ▼
                       ┌──────────────────────────┐
                       │     DownloadWorker       │
                       │   (parallel HTTP)        │
                       └────────────┬─────────────┘
                                    ▼
                  ┌───────────────────────────────────┐
                  │     OutputBuilder (router)        │
                  └───┬───────────────┬───────────────┘
                      │               │
            H.264+AAC │     VP9/AV1+Opus     │ unsupported
                      ▼               ▼              ▼
            ┌──────────────┐  ┌────────────┐  ┌────────────┐
            │  Mp4Muxer    │  │ Transformer│  │ ffmpeg-kit │
            │  (transmux)  │  │ (hw xcode) │  │  (fallback)│
            └──────┬───────┘  └─────┬──────┘  └─────┬──────┘
                   │                │                │
                   └────────────────┴────────────────┘
                                    ▼
                            real .mp4 file
```

## Sequenced pieces

Five pieces. Each ships independently. Piece N can land without N+1.

### Piece 1 — Media3 muxer/transformer path (fixes the bug)

**Scope.** Make video downloads work for the common case (360p–720p H.264+AAC, 1080p+
VP9/AV1+Opus via hardware transcode). Single output: `.mp4`.

**Files touched.**
- `app/src/main/java/com/zimbabeats/download/DownloadManager.kt`
- `app/src/main/java/com/zimbabeats/worker/DownloadWorker.kt`
- `app/src/main/java/com/zimbabeats/ui/viewmodel/VideoPlayerViewModel.kt` (state types only)
- `app/src/main/java/com/zimbabeats/ui/screen/player/VideoPlayerScreen.kt` (dialog labels)
- `media/build.gradle.kts` (add `media3-muxer`, `media3-transformer`, `media3-effect`)

**Changes.**
1. Drop `!it.isVideoOnly` filter. Group streams by resolution. For each resolution,
   pair best video-only stream with best audio-only stream of matching container.
2. Extend `DownloadQualityOption` with `videoStream: StreamUrl`, `audioStream: StreamUrl`,
   `requiresTransmux: Boolean`, `requiresTranscode: Boolean`, `combinedSizeBytes: Long`.
3. `DownloadWorker` downloads both streams in parallel via OkHttp to temp files.
4. New `OutputBuilder` picks one of:
   - **Pre-muxed** (`requiresTransmux=false`, e.g. 360p combined): copy single file.
   - **Transmux** (H.264+AAC): `androidx.media3.muxer.Mp4Muxer` — sample-level remux, no re-encode.
   - **Transcode** (VP9/AV1/Opus): `androidx.media3.transformer.Transformer` with hardware MediaCodec.
5. Final `.mp4` written via existing `DownloadManager` location logic. Temp files deleted.
6. `DownloadQualityDialog` shows e.g. `1080p (will transcode, ~145 MB)` so user knows
   higher qualities take longer.

**Risk.** Codec info must be reliably extracted from InnerTube response. If `StreamUrl.format`
doesn't carry codec info, add `videoCodec` and `audioCodec` fields parsed from
`mimeType` in `InnertubeClient`. Verified before code edits.

**Done when.**
- Tapping Download on a 720p video produces a playable `.mp4` muxed locally.
- 1080p video produces a playable `.mp4` via Transformer.
- 360p videos still work via the existing combined-stream path.
- Logcat shows which path each download took.

### Piece 2 — StreamResolver abstraction + youtubedl-android fallback

**Scope.** Insert a primary→fallback resolver. Add the `youtubedl-android` library.
Use it only when `InnertubeClient` returns no usable streams. No FFmpeg yet — the
output stage is still Media3.

**Files touched.**
- New: `core/data/src/main/java/com/zimbabeats/core/data/remote/youtube/StreamResolver.kt`
- New: `core/data/src/main/java/com/zimbabeats/core/data/remote/youtube/YtDlpResolver.kt`
- Modified: `InnertubeClient` to implement `StreamResolver`.
- `app/build.gradle.kts` (add `io.github.junkfood02.youtubedl-android:library`)

**Changes.**
1. Define `interface StreamResolver { suspend fun resolve(videoId: String): List<StreamUrl> }`.
2. `InnertubeStreamResolver` wraps current `InnertubeClient`.
3. `YtDlpStreamResolver` shells to youtubedl-android, parses JSON output to `StreamUrl`.
4. `CompositeStreamResolver(primary, fallback)` tries primary, falls back on empty/error.
5. Wire in DI module.
6. Bundle a baseline yt-dlp Python script in `assets/`. Extract on first use.

**Risk.** APK size jumps by ~12 MB for the Python runtime alone. First-run extraction
takes 5–10s — show progress UI.

**Done when.**
- A video that fails InnerTube (e.g. age-gated, signature change) downloads successfully
  via yt-dlp fallback.
- Telemetry log distinguishes primary vs fallback.

### Piece 3 — ffmpeg-kit integration

**Scope.** Last-resort transcode/mux when Media3 Transformer fails (e.g. unsupported
codec on the device's hardware encoder). `ffmpeg-kit-video` (LGPL, not GPL).

**Files touched.**
- `app/build.gradle.kts` (add `ffmpeg-kit-video` per ABI)
- `OutputBuilder` (new fallback branch)

**Changes.**
1. Add dependency: `com.arthenica:ffmpeg-kit-video:6.0` (or current LGPL variant).
2. In `OutputBuilder`, catch `ExportException` from Transformer; on failure, invoke
   FFmpeg with `-c:v copy -c:a copy` (transmux) or `-c:v libx264 -c:a aac` (transcode).
3. Show "Falling back to slower processor…" toast.

**Risk.** APK +30 MB per ABI. License: must include LGPL notice. Decision point: do
we accept this size? If not, skip piece 3 and ship 1+2 — Media3 Transformer covers
~99% of real-world codecs.

**Done when.**
- Forced-fail Transformer test still produces a `.mp4`.
- LGPL notice added to About screen.

### Piece 4 — Auto-update channel for yt-dlp

**Scope.** Remote-update the yt-dlp script without an app release. Mirror SimpMusic's
`updateYtdlp()` pattern.

**Files touched.**
- New: `app/src/main/java/com/zimbabeats/download/YtDlpUpdater.kt`
- New: `app/src/main/java/com/zimbabeats/download/YtDlpUpdateWorker.kt`
- Modified: `RemoteConfigManager.kt` — add `ytdlp_script_url`, `ytdlp_version` keys.
- Modified: `YtDlpStreamResolver` — load from versioned cache dir, not bundled asset.

**Changes.**
1. Firebase Remote Config keys:
   - `ytdlp_script_url` (string, default: bundled)
   - `ytdlp_version` (long)
   - `ytdlp_min_app_version` (long, for forcing app updates when needed)
2. `YtDlpUpdateWorker` runs once per 24h via `WorkManager`:
   - Read Remote Config.
   - If `ytdlp_version > currentInstalledVersion`, download script to `filesDir/ytdlp/v{N}/`.
   - Atomic swap of `currentVersion` symlink.
   - Keep previous version for rollback.
3. `YtDlpStreamResolver.resolve()` wraps yt-dlp call in try/catch. On 3 consecutive failures
   with current version, mark version as bad, revert to previous, log to Crashlytics.
4. First-run: bundled asset is `v1`. App boots usable even with no network.

**Risk.** Need a hosted yt-dlp script (your own CDN or GitHub release link). Update
endpoint must be HTTPS, signed if you want to be paranoid (HMAC tag in Remote Config).

**Done when.**
- Bumping `ytdlp_version` in Firebase causes app to fetch new script within 24h.
- Force-fail current version rolls back to previous.
- Cold install with no network still works.

### Piece 5 — Telemetry & observability

**Scope.** Know when stream resolution is degrading before users do.

**Files touched.**
- `app/src/main/java/com/zimbabeats/download/DownloadTelemetry.kt` (new)
- Wire into `DownloadWorker` end-of-job.

**Changes.**
1. Log to Firebase Analytics on each download:
   - `resolver_used` (innertube | ytdlp)
   - `output_builder` (passthrough | mp4muxer | transformer | ffmpeg)
   - `result` (success | resolver_failed | mux_failed | transcode_failed | network_failed)
   - `quality`, `duration_ms`, `file_size_bytes`, `app_version`, `ytdlp_version`
2. Crashlytics non-fatal on `resolver_failed` and `transcode_failed` with sampled rate.
3. Dashboard query: % of downloads using fallback resolver over time → tells you when
   InnerTube extractor is breaking.

**Done when.**
- Firebase Analytics shows daily breakdown of resolver/output paths.
- Crashlytics flags resolver failure spikes.

## Cost summary (final)

| Item | Cost |
|------|------|
| APK size delta (Piece 1 only) | +1 MB (Media3 muxer + transformer modules) |
| APK size delta (Piece 1+2) | +13 MB (above + youtubedl-android Python) |
| APK size delta (Piece 1+2+3) | +43 MB (above + ffmpeg-kit-video) |
| APK size delta (full plan, arm64) | ~+45 MB |
| Maintenance contract | Bump `ytdlp_script_url` in Firebase when YouTube breaks yt-dlp |

## Decision points before each piece

- **Before Piece 3**: do we accept the +30 MB or skip FFmpeg?
- **Before Piece 4**: where is yt-dlp hosted? (your CDN, GitHub release, mirror?)
- **Before Piece 5**: which analytics provider? (Firebase Analytics already in app.)

## Status

- [x] Piece 1 — Media3 muxer/transformer path (shipped + foreground service notification)
- [x] Piece 2 — Three-tier StreamResolver chain (InnerTube → NewPipe → yt-dlp).
- [x] Piece 4 — Auto-update channel for yt-dlp (Remote Config + WorkManager periodic). Verified: emulator pulled 2026.03.17 from upstream on first run.
- [x] Piece 5 — Telemetry (Firebase Analytics events + Crashlytics non-fatals). Verified: emulator starts cleanly, FA + Crashlytics both initialize, events wired into resolver chain and DownloadWorker.
- [ ] Piece 3 — ffmpeg-kit integration (optional — only needed if Transformer can't handle a codec in practice)
