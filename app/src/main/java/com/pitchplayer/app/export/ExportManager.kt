package com.pitchplayer.app.export

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for export progress.
 *
 * The work runs inside a foreground service so it survives the user leaving the
 * app, but the UI should not have to bind to that service just to draw a
 * progress bar, so state lives here instead.
 */
@UnstableApi
object ExportManager {

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    @Volatile
    internal var pending: ExportRequest? = null
        private set

    val isBusy: Boolean get() = _state.value is ExportState.Running

    fun enqueue(context: Context, request: ExportRequest) {
        if (isBusy) return
        pending = request
        _state.value = ExportState.Running(
            title = request.source.title,
            stage = "Starting",
            percent = 0,
            audioOnly = request.audioOnly
        )
        val intent = Intent(context, ExportService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    internal fun consumePending(): ExportRequest? = pending.also { pending = null }

    internal fun publishProgress(stage: String, fraction: Float) {
        val current = _state.value
        if (current !is ExportState.Running) return
        val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
        if (current.stage == stage && current.percent == percent) return
        _state.value = current.copy(stage = stage, percent = percent)
    }

    internal fun publishDone(saved: MediaStoreWriter.Saved, audioOnly: Boolean) {
        _state.value = ExportState.Done(saved.displayName, saved.folder, audioOnly)
    }

    internal fun publishFailure(message: String) {
        _state.value = ExportState.Failed(message)
    }

    fun reset() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }
}
