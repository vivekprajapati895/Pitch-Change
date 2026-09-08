package com.pitchplayer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchplayer.app.core.Pitch
import kotlin.math.roundToInt

/** The panel the user actually came here for. */
@Composable
fun PitchCard(
    semitones: Int,
    onNudge: (Int) -> Unit,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val active = semitones != 0
    val valueColor by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurface,
        label = "pitchColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PITCH",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            if (active) {
                TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton(
                icon = Icons.Rounded.Remove,
                enabled = semitones > Pitch.MIN_SEMITONES,
                onClick = { onNudge(-1) }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Pitch.label(semitones),
                    style = MaterialTheme.typography.displayLarge,
                    color = valueColor
                )
                Text(
                    text = Pitch.description(semitones),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            StepButton(
                icon = Icons.Rounded.Add,
                enabled = semitones < Pitch.MAX_SEMITONES,
                onClick = { onNudge(1) }
            )
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = semitones.toFloat(),
            onValueChange = { onSet(it.roundToInt()) },
            valueRange = Pitch.MIN_SEMITONES.toFloat()..Pitch.MAX_SEMITONES.toFloat(),
            steps = (Pitch.MAX_SEMITONES - Pitch.MIN_SEMITONES) - 1,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-12", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("0", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("+12", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }

        if (Pitch.isStretched(semitones)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Large shifts can sound slightly artificial on sustained notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(60.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .background(scheme.surfaceVariant, CircleShape)
            .border(1.dp, scheme.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(26.dp))
    }
}

/** Tempo, independent of pitch. Useful for learning a fast passage. */
@Composable
fun TempoRow(
    speedPercent: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TEMPO", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(
                "$speedPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = if (speedPercent == 100) scheme.onSurface else scheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = speedPercent.toFloat(),
            onValueChange = { onChange((it / 5).roundToInt() * 5) },
            valueRange = 50f..150f,
            colors = SliderDefaults.colors(
                thumbColor = scheme.secondary,
                activeTrackColor = scheme.secondary,
                inactiveTrackColor = scheme.outlineVariant
            )
        )
        Text(
            "Playback only. Downloads always render at normal tempo so the video stays in sync.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun QualityRow(
    heights: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (heights.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "QUALITY",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.width(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            heights.take(5).forEach { h ->
                val isSelected = h == selected
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) scheme.primaryContainer else scheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) scheme.primary else scheme.outlineVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(h) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        "${h}p",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scheme.primary else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = fraction, label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}
