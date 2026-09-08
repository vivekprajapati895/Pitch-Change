package com.pitchplayer.app.export

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.pitchplayer.app.core.Pitch
import com.pitchplayer.app.core.asSafeFileName
import com.pitchplayer.app.media.MediaSource
import com.pitchplayer.app.media.VideoTrack
import java.io.File

/**
 * Turns an [ExportRequest] into a file in the user's storage.
 *
 * The pipeline never re-encodes video:
 *   download (if remote) -> pitch-shift audio only -> lossless remux -> publish
 */
@UnstableApi
class Exporter(private val context: Context) {

    fun interface Progress {
        fun update(stage: String, fraction: Float)
    }

    suspend fun run(request: ExportRequest, progress: Progress): MediaStoreWriter.Saved {
        val work = File(context.cacheDir, "export").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            return when (val source = request.source) {
                is MediaSource.Local -> exportLocal(source, request, work, progress)
                is MediaSource.Remote -> exportRemote(source, request, work, progress)
            }
        } finally {
            work.deleteRecursively()
        }
    }

    // ------------------------------------------------------------ local files

    private suspend fun exportLocal(
        source: MediaSource.Local,
        request: ExportRequest,
        work: File,
        progress: Progress
    ): MediaStoreWriter.Saved {

        val wantsVideo = !request.audioOnly && source.hasVideo
        val pitched = File(work, "pitched.m4a")

        progress.update("Shifting pitch", 0f)
        PitchTransformer.renderAudio(context, source.uri, request.semitones, pitched) { f ->
            progress.update("Shifting pitch", f * if (wantsVideo) 0.6f else 0.85f)
        }

        val (finalFile, audioOnly) = if (wantsVideo) {
            val out = File(work, "out.mp4")
            Remuxer.mux(context, source.uri, pitched, out) { f ->
                progress.update("Combining tracks", 0.6f + f * 0.3f)
            }
            out to false
        } else {
            pitched to true
        }

        return publish(finalFile, source.title, request, audioOnly, progress)
    }

    // ---------------------------------------------------------- remote videos

    private suspend fun exportRemote(
        source: MediaSource.Remote,
        request: ExportRequest,
        work: File,
        progress: Progress
    ): MediaStoreWriter.Saved {

        if (request.audioOnly) return exportRemoteAudio(source, request, work, progress)

        val videoTrack = source.exportableVideo(request.videoHeight)
            ?: throw ExportFailure(
                "No MP4 video stream is available for this video, so it cannot be saved " +
                    "with video. Try the audio-only download instead."
            )

        val audioTrack = source.bestAudio

        // A muxed rendition already carries its own audio, so one download does both jobs.
        val videoFile = File(work, "video.mp4")
        val audioSource: Uri

        if (videoTrack.videoOnly && audioTrack != null) {
            progress.update("Downloading video", 0f)
            Http.download(videoTrack.url, videoFile) { f ->
                progress.update("Downloading video", f * 0.35f)
            }
            val audioFile = File(work, "audio.tmp")
            progress.update("Downloading audio", 0.35f)
            Http.download(audioTrack.url, audioFile) { f ->
                progress.update("Downloading audio", 0.35f + f * 0.15f)
            }
            audioSource = audioFile.toUri()
        } else {
            progress.update("Downloading video", 0f)
            Http.download(videoTrack.url, videoFile) { f ->
                progress.update("Downloading video", f * 0.5f)
            }
            audioSource = videoFile.toUri()
        }

        val pitched = File(work, "pitched.m4a")
        progress.update("Shifting pitch", 0.5f)
        PitchTransformer.renderAudio(context, audioSource, request.semitones, pitched) { f ->
            progress.update("Shifting pitch", 0.5f + f * 0.28f)
        }

        val out = File(work, "out.mp4")
        progress.update("Combining tracks", 0.78f)
        Remuxer.mux(context, videoFile, pitched, out) { f ->
            progress.update("Combining tracks", 0.78f + f * 0.14f)
        }

        return publish(out, source.title, request, audioOnly = false, progress = progress)
    }

    private suspend fun exportRemoteAudio(
        source: MediaSource.Remote,
        request: ExportRequest,
        work: File,
        progress: Progress
    ): MediaStoreWriter.Saved {

        // Prefer a dedicated audio rendition. Falling back to a muxed video
        // rendition costs bandwidth but still yields correct audio.
        val url = source.bestAudio?.url
            ?: source.muxed.maxByOrNull { it.height }?.url
            ?: throw ExportFailure("No audio stream is available for this video.")

        val downloaded = File(work, "audio.tmp")
        progress.update("Downloading audio", 0f)
        Http.download(url, downloaded) { f ->
            progress.update("Downloading audio", f * 0.45f)
        }

        val pitched = File(work, "pitched.m4a")
        progress.update("Shifting pitch", 0.45f)
        PitchTransformer.renderAudio(context, downloaded.toUri(), request.semitones, pitched) { f ->
            progress.update("Shifting pitch", 0.45f + f * 0.45f)
        }

        return publish(pitched, source.title, request, audioOnly = true, progress = progress)
    }

    // ---------------------------------------------------------------- output

    private suspend fun publish(
        file: File,
        title: String,
        request: ExportRequest,
        audioOnly: Boolean,
        progress: Progress
    ): MediaStoreWriter.Saved {
        progress.update("Saving", 0.94f)
        val name = buildFileName(title, request.semitones, audioOnly)
        val saved = MediaStoreWriter.publish(context, file, name, audioOnly)
        progress.update("Saved", 1f)
        return saved
    }

    private fun buildFileName(title: String, semitones: Int, audioOnly: Boolean): String {
        val suffix = if (semitones == 0) "original key" else "${Pitch.label(semitones)} semitones"
        val extension = if (audioOnly) "m4a" else "mp4"
        return "${title.asSafeFileName()} ($suffix).$extension"
    }
}

/**
 * Picks the video rendition to save. MediaMuxer can only write MP4-family
 * codecs, so WebM renditions are filtered out even when they look higher
 * quality in the playback picker.
 */
private fun MediaSource.Remote.exportableVideo(preferredHeight: Int?): VideoTrack? {
    val candidates = (videoOnly + muxed)
        .filter { it.mimeType.contains("mp4", ignoreCase = true) }
        .sortedByDescending { it.height }

    if (candidates.isEmpty()) return null

    return preferredHeight?.let { target ->
        candidates.firstOrNull { it.height == target }
            ?: candidates.firstOrNull { it.height <= target }
    } ?: candidates.first()
}
