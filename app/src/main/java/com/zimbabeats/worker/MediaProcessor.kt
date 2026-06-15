package com.zimbabeats.worker

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * Combines a video-only file with an audio-only file into a single MP4.
 *
 * Two operations:
 *  - [transmux]: pure remux. Both tracks must already be MP4-compatible
 *    (H.264 video + AAC audio). Uses Android's stock MediaExtractor + MediaMuxer to
 *    copy samples without re-encoding. Fast (seconds), no quality loss.
 *  - [transcode]: re-encodes via Media3 Transformer (hardware MediaCodec backend).
 *    Use when source codecs are VP9/AV1/Opus that can't sit in MP4 cleanly.
 *
 * Both methods return true on success, false on failure. Progress callbacks fire at coarse
 * milestones — fine-grained progress during transcode would require polling on the main
 * thread; a worker-side estimate is fine for now.
 */
class MediaProcessor(private val context: Context) {

    companion object {
        private const val TAG = "MediaProcessor"
    }

    suspend fun transmux(
        videoSrc: File,
        audioSrc: File,
        outFile: File,
        onProgress: suspend (percent: Int) -> Unit
    ): Boolean = runCatching {
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoExtractor = MediaExtractor().apply { setDataSource(videoSrc.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioSrc.absolutePath) }

        val videoTrackIndex = selectTrack(videoExtractor, "video/")
        val audioTrackIndex = selectTrack(audioExtractor, "audio/")
        if (videoTrackIndex < 0 || audioTrackIndex < 0) {
            Log.e(TAG, "Could not find video=$videoTrackIndex audio=$audioTrackIndex tracks")
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
            return false
        }
        videoExtractor.selectTrack(videoTrackIndex)
        audioExtractor.selectTrack(audioTrackIndex)

        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
        val mVideo = muxer.addTrack(videoFormat)
        val mAudio = muxer.addTrack(audioFormat)
        muxer.start()

        val totalDurationUs = maxOf(
            runCatching { videoFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L),
            runCatching { audioFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
        ).coerceAtLeast(1L)

        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()

        copyTrack(videoExtractor, muxer, mVideo, buffer, info, totalDurationUs) { pct ->
            onProgress((pct * 0.6).toInt())
        }
        copyTrack(audioExtractor, muxer, mAudio, buffer, info, totalDurationUs) { pct ->
            onProgress(60 + (pct * 0.4).toInt())
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
        true
    }.getOrElse { e ->
        Log.e(TAG, "transmux failed", e)
        runCatching { outFile.delete() }
        false
    }

    private suspend fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        totalDurationUs: Long,
        onProgress: suspend (percent: Int) -> Unit
    ) {
        var lastReported = -1
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.flags = extractor.sampleFlags
            info.presentationTimeUs = extractor.sampleTime
            muxer.writeSampleData(muxerTrackIndex, buffer, info)
            extractor.advance()

            val pct = ((info.presentationTimeUs * 100) / totalDurationUs).toInt().coerceIn(0, 100)
            if (pct != lastReported) {
                lastReported = pct
                onProgress(pct)
            }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    /**
     * Transcodes video+audio to MP4 (H.264/AAC) via Media3 Transformer. Used when source
     * codecs are VP9/AV1/Opus that don't sit cleanly in MP4. Returns true on success.
     *
     * Transformer is constructed and listened to on the main looper, but the worker thread
     * suspends on the result via [suspendCancellableCoroutine].
     */
    suspend fun transcode(
        videoSrc: File,
        audioSrc: File,
        outFile: File,
        @Suppress("UNUSED_PARAMETER") onProgress: suspend (percent: Int) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        Handler(Looper.getMainLooper()).post {
            try {
                val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(videoSrc)))
                    .setRemoveAudio(true)
                    .build()
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioSrc)))
                    .setRemoveVideo(true)
                    .build()

                val composition = Composition.Builder(
                    EditedMediaItemSequence.Builder(videoItem).build(),
                    EditedMediaItemSequence.Builder(audioItem).build()
                ).build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(comp: Composition, result: ExportResult) {
                            Log.d(TAG, "Transformer completed: ${result.durationMs}ms output")
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(
                            comp: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            Log.e(TAG, "Transformer error: ${exception.message}", exception)
                            runCatching { outFile.delete() }
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()

                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        runCatching { transformer.cancel() }
                    }
                }

                transformer.start(composition, outFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "transcode setup failed", e)
                if (cont.isActive) cont.resume(false)
            }
        }
    }
}
