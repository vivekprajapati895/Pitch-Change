package com.pitchplayer.app.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Turns a YouTube page URL into playable stream URLs.
 *
 * Only progressive HTTP streams are kept. DASH and HLS manifests would also
 * play, but the exporter needs to pull byte ranges from a plain URL, and
 * keeping one code path for both playback and export avoids a whole class of
 * "it plays but it will not save" bugs.
 */
object YouTubeResolver {

    @Volatile
    private var initialised = false

    private fun ensureInit() {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            NewPipe.init(
                NewPipeDownloader.getInstance(),
                Localization("en", "IN"),
                org.schabi.newpipe.extractor.localization.ContentCountry("IN")
            )
            initialised = true
        }
    }

    /** Cheap check so the UI can tell a URL from a search phrase. */
    fun looksLikeUrl(input: String): Boolean {
        val t = input.trim()
        return t.startsWith("http://", true) ||
            t.startsWith("https://", true) ||
            t.startsWith("www.", true) ||
            t.startsWith("youtu.be/", true) ||
            t.startsWith("youtube.com/", true)
    }

    /** Pulls the first URL out of shared text like "Check this out https://youtu.be/xyz". */
    fun extractUrl(sharedText: String): String? =
        Regex("""https?://\S+""").find(sharedText)?.value?.trimEnd('.', ',', ')')

    private fun normalise(input: String): String {
        var url = input.trim()
        if (!url.startsWith("http", true)) url = "https://$url"
        // The mobile host resolves fine, but m.youtube.com occasionally serves a
        // reduced page, so pin everything to the canonical host.
        return url.replace("m.youtube.com", "www.youtube.com")
    }

    suspend fun resolve(rawUrl: String): MediaSource.Remote = withContext(Dispatchers.IO) {
        ensureInit()
        val url = normalise(rawUrl)

        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: Exception) {
            throw ResolveException(friendlyMessage(e), e)
        }

        val muxed = info.getVideoStreams()
            .filter { it.isProgressive() && it.height() > 0 }
            .map { it.toTrack(videoOnly = false) }

        val videoOnly = info.getVideoOnlyStreams()
            .filter { it.isProgressive() && it.height() > 0 }
            .map { it.toTrack(videoOnly = true) }

        val audio = info.getAudioStreams()
            .filter { it.isProgressive() }
            .map { it.toTrack() }
            .sortedByDescending { it.bitrateKbps }

        if (audio.isEmpty() && muxed.isEmpty()) {
            throw ResolveException(
                "YouTube did not return any downloadable stream for this video. " +
                    "This usually means the video is age restricted, region locked, " +
                    "or served through a protected format."
            )
        }

        MediaSource.Remote(
            title = info.getName() ?: "Untitled",
            durationMs = info.getDuration().coerceAtLeast(0) * 1000L,
            pageUrl = info.getUrl() ?: url,
            uploader = info.getUploaderName() ?: "",
            thumbnailUrl = info.getThumbnails()?.maxByOrNull { it.getHeight() }?.getUrl(),
            muxed = muxed,
            videoOnly = videoOnly,
            audio = audio
        )
    }

    // NewPipe exposes these as Java getters. Calling them explicitly avoids any
    // chance of Kotlin resolving `format` or `content` to something else in scope.
    private fun VideoStream.isProgressive() =
        getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP && !getContent().isNullOrBlank()

    private fun AudioStream.isProgressive() =
        getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP && !getContent().isNullOrBlank()

    private fun VideoStream.height(): Int =
        getResolution().substringBefore('p').filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun VideoStream.toTrack(videoOnly: Boolean) = VideoTrack(
        url = getContent(),
        height = height(),
        label = getResolution().substringBefore('p') + "p",
        mimeType = getFormat()?.getMimeType() ?: "video/mp4",
        videoOnly = videoOnly,
        approxBytes = -1L
    )

    private fun AudioStream.toTrack() = AudioTrack(
        url = getContent(),
        bitrateKbps = getAverageBitrate().takeIf { it > 0 } ?: 0,
        mimeType = getFormat()?.getMimeType() ?: "audio/mp4",
        approxBytes = -1L
    )

    private fun friendlyMessage(e: Exception): String {
        val raw = e.message.orEmpty().lowercase()
        return when {
            "recaptcha" in raw ->
                "YouTube served a captcha. Wait a minute and try again, or switch networks."
            "age" in raw ->
                "This video is age restricted and cannot be opened without signing in."
            "private" in raw || "unavailable" in raw ->
                "This video is private or unavailable."
            "geo" in raw || "country" in raw ->
                "This video is not available in your region."
            "unable to resolve host" in raw || "timeout" in raw || "timed out" in raw ->
                "No network connection."
            else ->
                "Could not read this video. YouTube changes its internals often, so " +
                    "updating the NewPipeExtractor version usually fixes this."
        }
    }
}
