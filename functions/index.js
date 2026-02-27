const functions = require("firebase-functions");
const ytdl = require("@distube/ytdl-core");
const cors = require("cors")({ origin: true });

/**
 * Firebase Cloud Function to extract YouTube audio stream URLs.
 * Handles the "n" parameter decryption that causes 403 errors.
 *
 * Usage: GET /getAudioStream?videoId=VIDEO_ID
 * Returns: { success: true, audioUrl: "...", title: "...", duration: ... }
 */
exports.getAudioStream = functions
  .runWith({
    timeoutSeconds: 60,
    memory: "512MB",
  })
  .https.onRequest((req, res) => {
    cors(req, res, async () => {
      try {
        const videoId = req.query.videoId;

        if (!videoId) {
          return res.status(400).json({
            success: false,
            error: "Missing videoId parameter",
          });
        }

        console.log(`Extracting audio for video: ${videoId}`);

        // Get video info with audio formats
        const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${videoId}`, {
          requestOptions: {
            headers: {
              "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            },
          },
        });

        // Filter for audio-only formats, prefer highest quality
        const audioFormats = ytdl.filterFormats(info.formats, "audioonly");

        if (audioFormats.length === 0) {
          return res.status(404).json({
            success: false,
            error: "No audio formats available",
          });
        }

        // Sort by audio quality (bitrate)
        audioFormats.sort((a, b) => (b.audioBitrate || 0) - (a.audioBitrate || 0));

        const bestAudio = audioFormats[0];

        console.log(`Found audio format: ${bestAudio.mimeType}, bitrate: ${bestAudio.audioBitrate}`);

        // Return the stream URL and metadata
        return res.status(200).json({
          success: true,
          audioUrl: bestAudio.url,
          mimeType: bestAudio.mimeType,
          bitrate: bestAudio.audioBitrate,
          title: info.videoDetails.title,
          author: info.videoDetails.author.name,
          duration: parseInt(info.videoDetails.lengthSeconds),
          thumbnail: info.videoDetails.thumbnails[info.videoDetails.thumbnails.length - 1]?.url,
        });
      } catch (error) {
        console.error("Error extracting audio:", error);

        // Handle specific ytdl errors
        if (error.message.includes("Video unavailable")) {
          return res.status(404).json({
            success: false,
            error: "Video unavailable",
          });
        }

        if (error.message.includes("Sign in")) {
          return res.status(403).json({
            success: false,
            error: "Age-restricted or requires sign-in",
          });
        }

        return res.status(500).json({
          success: false,
          error: error.message || "Failed to extract audio",
        });
      }
    });
  });

/**
 * Batch endpoint for getting multiple audio streams at once.
 * Useful for loading playlist tracks.
 *
 * Usage: POST /getAudioStreamBatch with body { videoIds: ["id1", "id2", ...] }
 */
exports.getAudioStreamBatch = functions
  .runWith({
    timeoutSeconds: 300,
    memory: "1GB",
  })
  .https.onRequest((req, res) => {
    cors(req, res, async () => {
      try {
        if (req.method !== "POST") {
          return res.status(405).json({
            success: false,
            error: "Method not allowed. Use POST.",
          });
        }

        const { videoIds } = req.body;

        if (!videoIds || !Array.isArray(videoIds)) {
          return res.status(400).json({
            success: false,
            error: "Missing or invalid videoIds array",
          });
        }

        // Limit batch size
        const limitedIds = videoIds.slice(0, 10);

        console.log(`Batch extracting ${limitedIds.length} videos`);

        const results = await Promise.allSettled(
          limitedIds.map(async (videoId) => {
            try {
              const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${videoId}`, {
                requestOptions: {
                  headers: {
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                  },
                },
              });

              const audioFormats = ytdl.filterFormats(info.formats, "audioonly");
              audioFormats.sort((a, b) => (b.audioBitrate || 0) - (a.audioBitrate || 0));

              if (audioFormats.length === 0) {
                throw new Error("No audio formats");
              }

              const bestAudio = audioFormats[0];

              return {
                videoId,
                success: true,
                audioUrl: bestAudio.url,
                title: info.videoDetails.title,
                author: info.videoDetails.author.name,
                duration: parseInt(info.videoDetails.lengthSeconds),
              };
            } catch (error) {
              return {
                videoId,
                success: false,
                error: error.message,
              };
            }
          })
        );

        const tracks = results.map((result) =>
          result.status === "fulfilled" ? result.value : result.reason
        );

        return res.status(200).json({
          success: true,
          tracks,
        });
      } catch (error) {
        console.error("Batch error:", error);
        return res.status(500).json({
          success: false,
          error: error.message,
        });
      }
    });
  });

/**
 * Health check endpoint
 */
exports.health = functions.https.onRequest((req, res) => {
  cors(req, res, () => {
    res.status(200).json({
      status: "ok",
      version: "1.0.0",
      timestamp: new Date().toISOString(),
    });
  });
});
