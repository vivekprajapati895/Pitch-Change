package com.pitchplayer.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pitchplayer.app.media.NewPipeDownloader

@UnstableApi
object PlayerFactory {

    fun httpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

    fun build(context: Context): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            // Compressed audio offload hands decoding to the DSP, which bypasses
            // Sonic entirely. Pitch changes would silently do nothing. Off it goes.
            parameters = buildUponParameters()
                .setAudioOffloadPreferences(
                    AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                        .build()
                )
                .build()
        }

        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        // Karaoke tracks are long and networks in a classroom are shared, so buffer
        // generously rather than stalling mid-phrase.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .build()

        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
            }
    }

    fun progressive(factory: DataSource.Factory, url: String): MediaSource =
        ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(url))

    /**
     * YouTube serves anything above 360p as a silent video track plus a separate
     * audio track. MergingMediaSource plays them as one timeline.
     */
    fun merged(factory: DataSource.Factory, videoUrl: String, audioUrl: String): MediaSource =
        MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ true,
            /* clipDurations = */ true,
            progressive(factory, videoUrl),
            progressive(factory, audioUrl)
        )

    fun localFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context, httpDataSourceFactory())
}
