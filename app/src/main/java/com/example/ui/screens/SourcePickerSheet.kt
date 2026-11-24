package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.addons.AddonStreamFetchResult
import com.example.data.addons.InstalledAddon
import com.example.data.addons.PlayableStream
import com.example.data.addons.PlaybackTarget
import com.example.data.addons.SubtitleTrackItem
import com.example.data.player.PlaybackBookmark
import com.example.data.player.formatDurationMs
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerBottomSheet(
    target: PlaybackTarget,
    bookmark: PlaybackBookmark?,
    addons: List<InstalledAddon>,
    streamFetchState: UiState<AddonStreamFetchResult>,
    onRefreshStreams: () -> Unit,
    onManageAddons: () -> Unit,
    onSelectAddonStream: (PlayableStream, List<SubtitleTrackItem>) -> Unit,
    onPlayDirectUrl: (url: String, subtitleUrl: String?, extraSubs: List<SubtitleTrackItem>) -> Unit,
    onPlayLocalFile: (videoUri: Uri, fileName: String, subtitleUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(if (addons.any { it.enabled }) 0 else 1) }

    var directUrl by remember { mutableStateOf("") }
    var subtitleUrl by remember { mutableStateOf("") }

    var localVideoUri by remember { mutableStateOf<Uri?>(null) }
    var localVideoName by remember { mutableStateOf<String?>(null) }
    var localSubUri by remember { mutableStateOf<Uri?>(null) }
    var localSubName by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            localVideoUri = uri
            localVideoName = queryDisplayName(context, uri)
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            localSubUri = uri
            localSubName = queryDisplayName(context, uri)
        }
    }

    val fetchedSubtitles = (streamFetchState as? UiState.Success)?.data?.subtitles.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = strings.chooseSourceTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(target.title, target.subtitle).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (bookmark != null && bookmark.isMeaningfulResume) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = strings.resumeFromFormat.format(formatDurationMs(bookmark.positionMs)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(strings.tabAddons, strings.tabDirectUrl, strings.tabLocalFile)
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        modifier = Modifier.testTag("source_tab_$index")
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    AddonsTabContent(
                        addons = addons,
                        streamFetchState = streamFetchState,
                        onRefresh = onRefreshStreams,
                        onManageAddons = onManageAddons,
                        onSelectStream = { stream ->
                            if (stream.isDirectlyPlayable) {
                                onSelectAddonStream(stream, fetchedSubtitles)
                            } else if (!stream.externalUrl.isNullOrBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(stream.externalUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    )
                }

                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = directUrl,
                            onValueChange = { directUrl = it },
                            label = { Text(strings.directUrlLabel) },
                            placeholder = {
                                Text(strings.directUrlHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_url_input")
                        )

                        OutlinedTextField(
                            value = subtitleUrl,
                            onValueChange = { subtitleUrl = it },
                            label = { Text(strings.subtitleUrlOptionalLabel) },
                            placeholder = {
                                Text(strings.subtitleUrlHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_subtitle_input")
                        )

                        Button(
                            onClick = {
                                onPlayDirectUrl(directUrl, subtitleUrl, fetchedSubtitles)
                            },
                            enabled = directUrl.trim().startsWith("http://", ignoreCase = true) ||
                                directUrl.trim().startsWith("https://", ignoreCase = true),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("play_direct_url_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.playUrlButton)
                        }
                    }
                }

                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { videoPicker.launch(arrayOf("video/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pick_local_video_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.pickLocalVideo)
                        }

                        if (localVideoName != null) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.VideoFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.selectedFileLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = localVideoName.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                subtitlePicker.launch(
                                    arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pick_local_subtitle_button")
                        ) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = localSubName?.let { "${strings.selectedSubtitleLabel}: $it" }
                                    ?: strings.pickLocalSubtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val uri = localVideoUri ?: return@Button
                                onPlayLocalFile(uri, localVideoName ?: target.title, localSubUri)
                            },
                            enabled = localVideoUri != null,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("play_local_file_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.playLocalButton)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonsTabContent(
    addons: List<InstalledAddon>,
    streamFetchState: UiState<AddonStreamFetchResult>,
    onRefresh: () -> Unit,
    onManageAddons: () -> Unit,
    onSelectStream: (PlayableStream) -> Unit
) {
    val strings = LocalStrings.current
    val enabledCount = addons.count { it.enabled }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.activeAddonsCountFormat.format(enabledCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabledCount > 0) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_streams_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = strings.retry)
                    }
                }
                TextButton(
                    onClick = onManageAddons,
                    modifier = Modifier.testTag("manage_addons_button")
                ) {
                    Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.manageAddons)
                }
            }
        }

        if (enabledCount == 0) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.noAddonsTitle, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.noAddonsBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onManageAddons,
                        modifier = Modifier.testTag("empty_manage_addons_button")
                    ) {
                        Text(strings.manageAddons)
                    }
                }
            }
            return
        }

        when (streamFetchState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = strings.loadingStreams,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is UiState.Error -> {
                Text(
                    text = streamFetchState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is UiState.Success -> {
                val streams = streamFetchState.data.streams
                if (streams.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.noStreamsFound,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(streams, key = { it.id }) { stream ->
                            StreamOptionCard(
                                stream = stream,
                                onClick = { onSelectStream(stream) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamOptionCard(
    stream: PlayableStream,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val enabled = stream.isDirectlyPlayable || !stream.externalUrl.isNullOrBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("stream_card_${stream.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (stream.isDirectlyPlayable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (stream.isDirectlyPlayable) {
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.AutoMirrored.Rounded.OpenInNew
                        },
                        contentDescription = null,
                        tint = if (stream.isDirectlyPlayable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stream.addonName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (stream.qualityScore >= 720) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (stream.qualityScore >= 2160) "4K" else "${stream.qualityScore}p",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!stream.isDirectlyPlayable) {
                    Text(
                        text = if (!stream.externalUrl.isNullOrBlank()) {
                            strings.openExternalOption
                        } else {
                            strings.torrentUnsupportedHint
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!stream.description.isNullOrBlank()) {
                    Text(
                        text = stream.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }.getOrNull() ?: uri.lastPathSegment
}
