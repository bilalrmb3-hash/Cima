package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.example.data.firestore.LibraryEntry
import com.example.data.firestore.WatchStatus
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.ErrorStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.UiState

@Composable
fun LibraryScreen(
    isGuest: Boolean,
    libraryState: UiState<List<LibraryEntry>>,
    onSignInClick: () -> Unit,
    onOpenDetails: (type: String, id: String) -> Unit,
    onUpdateStatus: (LibraryEntry, WatchStatus) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var selectedStatus by rememberSaveable { mutableStateOf<WatchStatus?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }

    if (isGuest) {
        GuestTrackPromptBox(
            icon = Icons.Rounded.CloudSync,
            title = strings.guestLibraryTitle,
            body = strings.guestLibraryBody,
            signInLabel = strings.signInWithGoogle,
            exploreLabel = strings.exploreCatalog,
            onSignInClick = onSignInClick,
            onExploreClick = onExploreClick,
            signInTestTag = "library_signin_button",
            modifier = modifier
        )
        return
    }

    when (libraryState) {
        is UiState.Loading -> LoadingStateBox(strings.loading, modifier)
        is UiState.Error -> ErrorStateBox(libraryState.message, onExploreClick, modifier)
        is UiState.Success -> {
            val allEntries = libraryState.data
            if (allEntries.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Rounded.Bookmark,
                    title = strings.emptyLibraryTitle,
                    body = strings.emptyLibraryBody,
                    actionLabel = strings.exploreCatalog,
                    onAction = onExploreClick,
                    modifier = modifier
                )
            } else {
                val filtered = allEntries.filter { entry ->
                    (selectedStatus == null || entry.watchStatus == selectedStatus) &&
                        (selectedType == null || entry.mediaType == selectedType)
                }

                Column(modifier = modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedStatus == null,
                                onClick = { selectedStatus = null },
                                label = { Text("${strings.all} (${allEntries.size})") },
                                modifier = Modifier.testTag("lib_filter_all")
                            )
                        }
                        WatchStatus.entries.forEach { status ->
                            val count = allEntries.count { it.watchStatus == status }
                            item(key = status.name) {
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = {
                                        selectedStatus = if (selectedStatus == status) null else status
                                    },
                                    label = { Text("${strings.statusLabel(status)} ($count)") },
                                    modifier = Modifier.testTag("lib_filter_${status.name.lowercase()}")
                                )
                            }
                        }
                        item {
                            FilterChip(
                                selected = selectedType == "movie",
                                onClick = { selectedType = if (selectedType == "movie") null else "movie" },
                                label = { Text(strings.movies) },
                                modifier = Modifier.testTag("lib_type_movie")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedType == "series",
                                onClick = { selectedType = if (selectedType == "series") null else "series" },
                                label = { Text(strings.series) },
                                modifier = Modifier.testTag("lib_type_series")
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        EmptyStateBox(
                            icon = Icons.Rounded.Bookmark,
                            title = strings.noResultsTitle,
                            body = strings.noResultsBody,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                LibraryEntryRowCard(
                                    entry = entry,
                                    onClick = { onOpenDetails(entry.mediaType, entry.id) },
                                    onUpdateStatus = { status -> onUpdateStatus(entry, status) },
                                    onRemove = { onRemoveEntry(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEntryRowCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onUpdateStatus: (WatchStatus) -> Unit,
    onRemove: () -> Unit
) {
    val strings = LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("library_card_${entry.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = entry.poster,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(74.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (entry.mediaType == "series") strings.series else strings.movies,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (!entry.imdbRating.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = entry.imdbRating,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!entry.releaseInfo.isNullOrBlank()) {
                    Text(
                        text = entry.releaseInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = strings.statusLabel(entry.watchStatus),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (!entry.lastWatchedLabel.isNullOrBlank()) {
                        Text(
                            text = entry.lastWatchedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (entry.mediaType == "series" && entry.totalEpisodes > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { entry.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.episodesProgressFormat.format(
                            entry.watchedCount.toInt(),
                            entry.totalEpisodes.toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("library_menu_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = entry.title
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    WatchStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(strings.statusLabel(status)) },
                            onClick = {
                                menuExpanded = false
                                onUpdateStatus(status)
                            }
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        text = {
                            Text(
                                strings.removeFromLibrary,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GuestTrackPromptBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    signInLabel: String,
    exploreLabel: String,
    onSignInClick: () -> Unit,
    onExploreClick: () -> Unit,
    signInTestTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onSignInClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag(signInTestTag)
            ) {
                Text(signInLabel)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onExploreClick,
                modifier = Modifier.testTag("${signInTestTag}_explore")
            ) {
                Text(exploreLabel)
            }
        }
    }
}
