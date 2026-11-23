package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.firestore.LibraryEntry
import com.example.data.firestore.SeriesProgress
import com.example.data.firestore.WatchStatus
import com.example.data.remote.EpisodeItem
import com.example.data.remote.MediaDetails
import com.example.data.remote.TrailerItem
import com.example.ui.components.ErrorStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.openYouTubeTrailer
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    mediaType: String,
    mediaId: String,
    detailsState: UiState<MediaDetails>,
    libraryEntry: LibraryEntry?,
    progress: SeriesProgress?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateStatus: (MediaDetails, WatchStatus, Boolean) -> Unit,
    onRemoveFromLibrary: (String) -> Unit,
    onToggleEpisode: (MediaDetails, EpisodeItem) -> Unit,
    onMarkSeasonWatched: (MediaDetails, Int, Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var showTrailerDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (detailsState) {
            is UiState.Loading -> {
                LoadingStateBox(strings.loading)
                DetailBackButton(
                    onBack = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                )
            }
            is UiState.Error -> {
                ErrorStateBox(strings.networkError, onRetry)
                DetailBackButton(
                    onBack = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                )
            }
            is UiState.Success -> {
                val details = detailsState.data
                val watchedIds = remember(progress) {
                    progress?.watchedEpisodeIds.orEmpty().toSet()
                }
                val seasons = remember(details) { details.seasons }
                var selectedSeasonIndex by remember(details.id, seasons) { mutableIntStateOf(0) }
                val activeSeason = seasons.getOrNull(selectedSeasonIndex)
                val seasonEpisodes = remember(details, activeSeason) {
                    if (activeSeason != null) details.episodesForSeason(activeSeason) else emptyList()
                }
                val allSeasonWatched = remember(seasonEpisodes, watchedIds) {
                    seasonEpisodes.isNotEmpty() && seasonEpisodes.all { it.id in watchedIds }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item(key = "hero_header") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            AsyncImage(
                                model = details.background ?: details.poster,
                                contentDescription = details.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            DetailBackButton(
                                onBack = onBack,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(12.dp)
                            )
                        }
                    }

                    item(key = "title_and_poster") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                modifier = Modifier
                                    .width(114.dp)
                                    .height(170.dp)
                            ) {
                                AsyncImage(
                                    model = details.poster,
                                    contentDescription = details.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!details.imdbRating.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = details.imdbRating,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                    if (!details.releaseInfo.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = details.releaseInfo,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (!details.runtime.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = details.runtime,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "actions_and_trailers") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        when {
                                            details.trailers.isEmpty() -> {
                                                onShowMessage(strings.noTrailerAvailable)
                                            }
                                            details.trailers.size == 1 -> {
                                                val ok = openYouTubeTrailer(context, details.trailers.first().ytId)
                                                if (!ok) onShowMessage(strings.noTrailerAvailable)
                                            }
                                            else -> showTrailerDialog = true
                                        }
                                    },
                                    enabled = details.trailers.isNotEmpty(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("watch_trailer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (details.trailers.isNotEmpty()) strings.watchTrailer else strings.noTrailerAvailable,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (libraryEntry != null) {
                                    FilledTonalButton(
                                        onClick = { onRemoveFromLibrary(details.id) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .height(50.dp)
                                            .testTag("remove_library_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = strings.removeFromLibrary
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WatchStatus.entries.forEach { status ->
                                    val selected = libraryEntry?.watchStatus == status
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            onUpdateStatus(details, status, libraryEntry != null)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(strings.statusLabel(status)) },
                                        modifier = Modifier.testTag("detail_status_${status.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }

                    if (!details.description.isNullOrBlank()) {
                        item(key = "synopsis") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = strings.synopsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = details.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    if (details.genres.isNotEmpty()) {
                        item(key = "genres") {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                details.genres.forEach { genre ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(strings.localizeGenre(genre)) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (details.director.isNotEmpty() || details.cast.isNotEmpty()) {
                        item(key = "credits") {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (details.director.isNotEmpty()) {
                                        Text(
                                            text = "${strings.directorLabel}: ${details.director.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (details.cast.isNotEmpty()) {
                                        Text(
                                            text = "${strings.castLabel}: ${details.cast.take(8).joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    if (details.type == "series") {
                        item(key = "seasons_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.seasonsLabel,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (activeSeason != null && seasonEpisodes.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                onMarkSeasonWatched(details, activeSeason, !allSeasonWatched)
                                            },
                                            modifier = Modifier.testTag("mark_season_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DoneAll,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (allSeasonWatched) strings.markSeasonUnwatched
                                                else strings.markSeasonWatched
                                            )
                                        }
                                    }
                                }

                                if (seasons.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    ScrollableTabRow(
                                        selectedTabIndex = selectedSeasonIndex.coerceIn(0, seasons.lastIndex),
                                        edgePadding = 16.dp
                                    ) {
                                        seasons.forEachIndexed { index, seasonNumber ->
                                            Tab(
                                                selected = selectedSeasonIndex == index,
                                                onClick = { selectedSeasonIndex = index },
                                                text = {
                                                    Text(
                                                        if (seasonNumber == 0) strings.specialsLabel
                                                        else strings.seasonNumberFormat.format(seasonNumber)
                                                    )
                                                },
                                                modifier = Modifier.testTag("season_tab_$seasonNumber")
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = strings.noEpisodesAvailable,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        items(seasonEpisodes, key = { it.id }) { ep ->
                            val isWatched = ep.id in watchedIds
                            EpisodeChecklistCard(
                                episode = ep,
                                isWatched = isWatched,
                                onToggleWatched = { onToggleEpisode(details, ep) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (showTrailerDialog) {
                    TrailerPickerDialog(
                        trailers = details.trailers,
                        onSelect = { trailer ->
                            showTrailerDialog = false
                            val ok = openYouTubeTrailer(context, trailer.ytId)
                            if (!ok) onShowMessage(strings.noTrailerAvailable)
                        },
                        onDismiss = { showTrailerDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        modifier = modifier
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("detail_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = strings.back
            )
        }
    }
}

@Composable
private fun EpisodeChecklistCard(
    episode: EpisodeItem,
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWatched) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (!episode.overview.isNullOrBlank()) expanded = !expanded
            }
            .testTag("episode_card_${episode.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!episode.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.thumbnail,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(88.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${episode.episode}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${strings.episodeShortFormat.format(episode.season, episode.episode)} • ${episode.title}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!episode.releasedIso.isNullOrBlank()) {
                        Text(
                            text = episode.releasedIso.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleWatched,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_episode_${episode.id}")
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (isWatched) strings.markWatched else strings.markUnwatched,
                        tint = if (isWatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            AnimatedVisibility(visible = expanded && !episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TrailerPickerDialog(
    trailers: List<TrailerItem>,
    onSelect: (TrailerItem) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectTrailer) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trailers.forEach { trailer ->
                    OutlinedButton(
                        onClick = { onSelect(trailer) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(trailer.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}
