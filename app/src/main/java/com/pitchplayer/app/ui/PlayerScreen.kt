package com.pitchplayer.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pitchplayer.app.core.asDuration
import com.pitchplayer.app.export.ExportState
import com.pitchplayer.app.media.MediaSource
import com.pitchplayer.app.player.PlayerUiState

@UnstableApi
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    exportState: ExportState,
    player: ExoPlayer,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSemitoneChange: (Int) -> Unit,
    onNudge: (Int) -> Unit,
    onResetPitch: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onOpenExport: () -> Unit,
    onCloseExport: () -> Unit,
    onExport: (Boolean) -> Unit,
    onDismissExportState: () -> Unit,
    onDismissError: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val source = state.source

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // -------------------------------------------------- header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = source?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // -------------------------------------------------- surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (state.hasVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                this.player = player
                            }
                        },
                        onRelease = { it.player = null }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(scheme.surfaceVariant, scheme.surface)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = scheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                if (state.busy) {
                    CircularProgressIndicator(color = scheme.primary, strokeWidth = 3.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------- seek bar
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // -------------------------------------------------- transport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSkip(-5_000) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Replay5, contentDescription = "Back 5 seconds", modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(20.dp))
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(68.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                IconButton(onClick = { onSkip(5_000) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Forward5, contentDescription = "Forward 5 seconds", modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.error != null) {
                    ErrorCard(message = state.error, onDismiss = onDismissError)
                }

                PitchCard(
                    semitones = state.semitones,
                    onNudge = onNudge,
                    onSet = onSemitoneChange,
                    onReset = onResetPitch
                )

                TempoRow(speedPercent = state.speedPercent, onChange = onSpeedChange)

                if (source is MediaSource.Remote && source.selectableVideo.size > 1) {
                    QualityRow(
                        heights = source.selectableVideo.map { it.height },
                        selected = state.selectedHeight,
                        onSelect = onSelectQuality
                    )
                }

                when (exportState) {
                    is ExportState.Running -> ExportProgressCard(exportState)
                    is ExportState.Done -> ExportDoneCard(exportState, onDismissExportState)
                    is ExportState.Failed -> ErrorCard(exportState.message, onDismissExportState)
                    ExportState.Idle -> DownloadButton(onClick = onOpenExport)
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        if (state.showExportSheet) {
            ExportSheet(
                semitones = state.semitones,
                canExportVideo = state.hasVideo,
                onDismiss = onCloseExport,
                onExport = onExport
            )
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val max = durationMs.coerceAtLeast(1L).toFloat()
    val value = if (scrubbing) scrubValue else positionMs.coerceIn(0L, durationMs).toFloat()

    Column(modifier) {
        Slider(
            value = value.coerceIn(0f, max),
            onValueChange = {
                scrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                onSeek(scrubValue.toLong())
                scrubbing = false
            },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                value.toLong().asDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                durationMs.asDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(scheme.secondaryContainer, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Download,
            contentDescription = null,
            tint = scheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Save with this pitch",
            color = scheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ExportProgressCard(state: ExportState.Running) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                state.stage,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface
            )
            Text(
                "${state.percent}%",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(fraction = state.percent / 100f)
        Spacer(Modifier.height(8.dp))
        Text(
            "You can leave the app. The download continues in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportDoneCard(state: ExportState.Done, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer, RoundedCornerShape(18.dp))
            .clickable(onClick = onDismiss)
            .padding(18.dp)
    ) {
        Text(
            "Saved",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "in ${state.folder}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
    }
}
