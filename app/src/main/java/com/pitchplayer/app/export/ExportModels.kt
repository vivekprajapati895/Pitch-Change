package com.pitchplayer.app.export

import com.pitchplayer.app.media.MediaSource

data class ExportRequest(
    val source: MediaSource,
    val semitones: Int,
    val speedPercent: Int,
    val audioOnly: Boolean,
    val videoHeight: Int?
)

sealed interface ExportState {

    data object Idle : ExportState

    data class Running(
        val title: String,
        val stage: String,
        val percent: Int,
        val audioOnly: Boolean
    ) : ExportState

    data class Done(
        val displayName: String,
        val folder: String,
        val audioOnly: Boolean
    ) : ExportState

    data class Failed(val message: String) : ExportState
}
