package com.pitchplayer.app.player

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pitchplayer.app.core.Pitch
import com.pitchplayer.app.export.ExportManager
import com.pitchplayer.app.export.ExportRequest
import com.pitchplayer.app.export.ExportState
import com.pitchplayer.app.media.MediaSource
import com.pitchplayer.app.media.ResolveException
import com.pitchplayer.app.media.YouTubeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Player }

data class PlayerUiState(
    val screen: Screen = Screen.Home,
    val urlInput: String = "",
    val busy: Boolean = false,
    val busyMessage: String = "",
    val error: String? = null,
    val source: MediaSource? = null,
    val semitones: Int = 0,
    val speedPercent: Int = 100,
    val selectedHeight: Int? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val showExportSheet: Boolean = false
) {
    val hasVideo: Boolean
        get() = when (val s = source) {
            is MediaSource.Remote -> s.hasVideo
            is MediaSource.Local -> s.hasVideo
            null -> false
        }
}

@UnstableApi
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    val exportState: StateFlow<ExportState> = ExportManager.state

    val player: ExoPlayer = PlayerFactory.build(app).also { p ->
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _ui.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _ui.update {
                        it.copy(
                            busy = false,
                            durationMs = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _ui.update {
                    it.copy(
                        busy = false,
                        error = "Playback failed: ${error.errorCodeName}. " +
                            "Stream links from YouTube expire after a while, so try loading the video again."
                    )
                }
            }
        })
    }

    init {
        // Drives the seek bar. 200 ms is smooth enough and costs nothing.
        viewModelScope.launch {
            while (true) {
                if (_ui.value.screen == Screen.Player) {
                    _ui.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                            durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs
                        )
                    }
                }
                delay(200)
            }
        }
    }

    // ---------------------------------------------------------------- input

    fun onUrlChange(value: String) = _ui.update { it.copy(urlInput = value, error = null) }

    fun dismissError() = _ui.update { it.copy(error = null) }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val url = YouTubeResolver.extractUrl(text) ?: return
                _ui.update { it.copy(urlInput = url) }
                loadUrl(url)
            }
            Intent.ACTION_VIEW -> intent.data?.let { loadLocal(it, persist = false) }
        }
    }

    // ---------------------------------------------------------------- load

    fun loadUrl(raw: String = _ui.value.urlInput) {
        val url = raw.trim()
        if (url.isEmpty()) return
        if (!YouTubeResolver.looksLikeUrl(url)) {
            _ui.update { it.copy(error = "That does not look like a link. Paste a full YouTube URL.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(busy = true, busyMessage = "Reading video…", error = null) }
            try {
                val remote = YouTubeResolver.resolve(url)
                val best = remote.selectableVideo.firstOrNull { it.height <= 720 }
                    ?: remote.selectableVideo.firstOrNull()
                _ui.update {
                    it.copy(
                        source = remote,
                        selectedHeight = best?.height,
                        screen = Screen.Player,
                        durationMs = remote.durationMs,
                        busyMessage = "Buffering…"
                    )
                }
                prepare()
            } catch (e: ResolveException) {
                _ui.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = e.message ?: "Something went wrong.") }
            }
        }
    }

    fun loadLocal(uri: Uri, persist: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, busyMessage = "Opening file…", error = null) }
            try {
                if (persist) runCatching {
                    getApplication<Application>().contentResolver
                        .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val local = withContext(Dispatchers.IO) { inspectLocal(uri) }
                _ui.update {
                    it.copy(
                        source = local,
                        selectedHeight = null,
                        screen = Screen.Player,
                        durationMs = local.durationMs,
                        busyMessage = "Buffering…"
                    )
                }
                prepare()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = "Could not open that file.") }
            }
        }
    }

    private fun inspectLocal(uri: Uri): MediaSource.Local {
        val ctx = getApplication<Application>()
        var name = "Local file"
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0).substringBeforeLast('.')
                }
        }
        var duration = 0L
        var hasVideo = false
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, uri)
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (_: Exception) {
            // Some containers refuse metadata extraction; playback usually still works.
        } finally {
            runCatching { mmr.release() }
        }
        return MediaSource.Local(name, duration, uri, hasVideo)
    }

    // ---------------------------------------------------------------- playback

    private fun prepare(startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        val state = _ui.value
        val factory = PlayerFactory.localFactory(getApplication())

        val mediaSource = when (val s = state.source) {
            is MediaSource.Local ->
                PlayerFactory.progressive(factory, s.uri.toString())

            is MediaSource.Remote -> {
                val video = s.selectableVideo.firstOrNull { it.height == state.selectedHeight }
                val audio = s.bestAudio
                when {
                    video == null && audio != null ->
                        PlayerFactory.progressive(factory, audio.url)
                    video != null && !video.videoOnly ->
                        PlayerFactory.progressive(factory, video.url)
                    video != null && audio != null ->
                        PlayerFactory.merged(factory, video.url, audio.url)
                    else -> null
                }
            }

            null -> null
        } ?: run {
            _ui.update { it.copy(busy = false, error = "No playable stream found.") }
            return
        }

        player.setMediaSource(mediaSource, startPositionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        applyParameters()
    }

    private fun applyParameters() {
        val s = _ui.value
        player.playbackParameters = PlaybackParameters(
            /* speed = */ s.speedPercent / 100f,
            /* pitch = */ Pitch.factor(s.semitones)
        )
    }

    fun setSemitones(value: Int) {
        val clamped = value.coerceIn(Pitch.MIN_SEMITONES, Pitch.MAX_SEMITONES)
        _ui.update { it.copy(semitones = clamped) }
        applyParameters()
    }

    fun nudgeSemitones(delta: Int) = setSemitones(_ui.value.semitones + delta)

    fun setSpeedPercent(value: Int) {
        _ui.update { it.copy(speedPercent = value.coerceIn(50, 150)) }
        applyParameters()
    }

    fun resetPitch() = setSemitones(0)

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0L))

    fun skip(deltaMs: Long) = player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))

    fun selectQuality(height: Int) {
        if (height == _ui.value.selectedHeight) return
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady
        _ui.update { it.copy(selectedHeight = height) }
        prepare(position, wasPlaying)
    }

    // ---------------------------------------------------------------- export

    fun openExportSheet() {
        player.pause()
        _ui.update { it.copy(showExportSheet = true) }
    }

    fun closeExportSheet() = _ui.update { it.copy(showExportSheet = false) }

    fun startExport(audioOnly: Boolean) {
        val s = _ui.value
        val source = s.source ?: return
        val request = ExportRequest(
            source = source,
            semitones = s.semitones,
            speedPercent = s.speedPercent,
            audioOnly = audioOnly,
            videoHeight = s.selectedHeight
        )
        _ui.update { it.copy(showExportSheet = false) }
        ExportManager.enqueue(getApplication(), request)
    }

    fun dismissExportState() = ExportManager.reset()

    // ---------------------------------------------------------------- nav

    fun backToHome() {
        player.stop()
        player.clearMediaItems()
        _ui.update {
            PlayerUiState(urlInput = it.urlInput, semitones = it.semitones)
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
