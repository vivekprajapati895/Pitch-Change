package com.pitchplayer.app.media

import android.net.Uri

/** A single selectable video rendition. */
data class VideoTrack(
    val url: String,
    val height: Int,
    val label: String,
    val mimeType: String,
    val videoOnly: Boolean,
    val approxBytes: Long = -1L
)

/** A single selectable audio rendition. */
data class AudioTrack(
    val url: String,
    val bitrateKbps: Int,
    val mimeType: String,
    val approxBytes: Long = -1L
) {
    val label: String get() = if (bitrateKbps > 0) "$bitrateKbps kbps" else "Audio"
}

/**
 * Everything the player and the exporter need about one piece of media,
 * whether it came from a URL or from local storage.
 */
sealed interface MediaSource {

    val title: String
    val durationMs: Long

    /** A file the user already has. Nothing to resolve, nothing to download. */
    data class Local(
        override val title: String,
        override val durationMs: Long,
        val uri: Uri,
        val hasVideo: Boolean
    ) : MediaSource

    /**
     * A remote stream. YouTube serves anything above 360p as separate video-only
     * and audio-only tracks, so both lists matter. [muxed] holds the legacy
     * combined renditions when they are still offered.
     */
    data class Remote(
        override val title: String,
        override val durationMs: Long,
        val pageUrl: String,
        val uploader: String,
        val thumbnailUrl: String?,
        val muxed: List<VideoTrack>,
        val videoOnly: List<VideoTrack>,
        val audio: List<AudioTrack>
    ) : MediaSource {

        val hasVideo: Boolean get() = muxed.isNotEmpty() || videoOnly.isNotEmpty()

        /** Highest bitrate audio, which is what we always want for pitch work. */
        val bestAudio: AudioTrack? get() = audio.maxByOrNull { it.bitrateKbps }

        /**
         * Video renditions offered in the quality picker: adaptive tracks first
         * (they go higher), then any muxed fallbacks, deduplicated by height.
         */
        val selectableVideo: List<VideoTrack>
            get() = (videoOnly + muxed)
                .distinctBy { it.height }
                .sortedByDescending { it.height }
    }
}

class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)
