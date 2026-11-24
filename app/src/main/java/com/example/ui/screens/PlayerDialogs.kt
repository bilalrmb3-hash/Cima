package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.TrackGroup
import com.example.ui.i18n.LocalStrings

data class VideoQualityOption(
    val id: String,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val isSelected: Boolean
)

data class MediaTrackOption(
    val id: String,
    val label: String,
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val isSelected: Boolean
)

@Composable
fun QualityPickerDialog(
    options: List<VideoQualityOption>,
    isAuto: Boolean,
    onSelectAuto: () -> Unit,
    onSelectOption: (VideoQualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.qualityLabel) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                item(key = "auto") {
                    TrackOptionRow(
                        label = strings.autoHighestQuality,
                        selected = isAuto,
                        onClick = onSelectAuto
                    )
                }
                items(options, key = { it.id }) { option ->
                    TrackOptionRow(
                        label = option.label,
                        selected = !isAuto && option.isSelected,
                        onClick = { onSelectOption(option) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
fun SubtitlePickerDialog(
    options: List<MediaTrackOption>,
    isOff: Boolean,
    onSelectOff: () -> Unit,
    onSelectOption: (MediaTrackOption) -> Unit,
    onPickLocalSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.subtitlesLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    item(key = "sub_off") {
                        TrackOptionRow(
                            label = strings.subtitlesOff,
                            selected = isOff,
                            onClick = onSelectOff
                        )
                    }
                    items(options, key = { it.id }) { option ->
                        TrackOptionRow(
                            label = option.label,
                            selected = !isOff && option.isSelected,
                            onClick = { onSelectOption(option) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onPickLocalSubtitle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.externalSubtitleOption)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
fun AudioTrackPickerDialog(
    options: List<MediaTrackOption>,
    onSelectOption: (MediaTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.audioLabel) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(options, key = { it.id }) { option ->
                    TrackOptionRow(
                        label = option.label,
                        selected = option.isSelected,
                        onClick = { onSelectOption(option) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
fun SpeedPickerDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.speedLabel) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                items(speeds, key = { it }) { speed ->
                    val label = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                    TrackOptionRow(
                        label = label,
                        selected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                        onClick = { onSelectSpeed(speed) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun TrackOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
