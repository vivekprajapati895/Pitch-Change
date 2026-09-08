package com.pitchplayer.app.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Copies an existing video track and an already pitch-shifted audio track into
 * one MP4 without touching either encoder.
 *
 * This is the whole reason exports are quick. Re-encoding 720p video on a
 * mid-range phone takes minutes and throws away quality for no reason, since
 * the pitch change only ever affects audio.
 */
object Remuxer {

    private const val FALLBACK_BUFFER = 1 shl 20 // 1 MB

    suspend fun mux(
        context: Context,
        videoSource: Any,        // File or Uri holding the video track
        pitchedAudio: File,
        output: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            when (videoSource) {
                is File -> videoExtractor.setDataSource(videoSource.absolutePath)
                is Uri -> videoExtractor.setDataSource(context, videoSource, null)
                else -> throw ExportFailure("Unsupported video source.")
            }
            audioExtractor.setDataSource(pitchedAudio.absolutePath)

            val videoTrack = selectTrack(videoExtractor, "video/")
                ?: throw ExportFailure("No video track found in the downloaded stream.")
            val audioTrack = selectTrack(audioExtractor, "audio/")
                ?: throw ExportFailure("No audio track found after pitch shifting.")

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Portrait recordings carry their orientation as metadata rather than
            // rotated pixels. Losing it here would show the export sideways.
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }

            val outVideo = muxer.addTrack(videoFormat)
            val outAudio = muxer.addTrack(audioFormat)
            muxer.start()

            val videoDuration = videoFormat.durationOrZero()
            copyTrack(videoExtractor, muxer, outVideo, videoFormat) { us ->
                if (videoDuration > 0) onProgress(0.6f * (us.toFloat() / videoDuration))
            }

            val audioDuration = audioFormat.durationOrZero()
            copyTrack(audioExtractor, muxer, outAudio, audioFormat) { us ->
                if (audioDuration > 0) onProgress(0.6f + 0.4f * (us.toFloat() / audioDuration))
            }

            muxer.stop()
            onProgress(1f)
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun MediaFormat.durationOrZero(): Long =
        if (containsKey(MediaFormat.KEY_DURATION)) getLong(MediaFormat.KEY_DURATION) else 0L

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return i
        }
        return null
    }

    private suspend fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outputIndex: Int,
        format: MediaFormat,
        onSampleTime: (Long) -> Unit
    ) {
        val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(FALLBACK_BUFFER)
        } else {
            FALLBACK_BUFFER
        }

        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()
        var counter = 0

        while (true) {
            coroutineContext.ensureActive()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags.toMuxerFlags()

            muxer.writeSampleData(outputIndex, buffer, info)

            if (counter++ % 60 == 0) onSampleTime(info.presentationTimeUs)
            extractor.advance()
        }
    }

    /** MediaExtractor sample flags and MediaCodec buffer flags are different enums. */
    private fun Int.toMuxerFlags(): Int {
        var flags = 0
        if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }
}
