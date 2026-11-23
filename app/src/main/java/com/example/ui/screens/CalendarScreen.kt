package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.ErrorStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.CalendarEpisodeEntry
import com.example.ui.viewmodel.CalendarSections
import com.example.ui.viewmodel.UiState
import java.text.DateFormat
import java.util.Date

@Composable
fun CalendarScreen(
    calendarState: UiState<CalendarSections>,
    onRetry: () -> Unit,
    onExploreClick: () -> Unit,
    onOpenSeries: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    when (calendarState) {
        is UiState.Loading -> LoadingStateBox(strings.loading, modifier)
        is UiState.Error -> ErrorStateBox(calendarState.message, onRetry, modifier)
        is UiState.Success -> {
            val sections = calendarState.data
            if (!sections.hasSeriesInLibrary) {
                EmptyStateBox(
                    icon = Icons.Rounded.CalendarMonth,
                    title = strings.emptyCalendarTitle,
                    body = strings.emptyCalendarBody,
                    actionLabel = strings.exploreCatalog,
                    onAction = onExploreClick,
                    modifier = modifier
                )
            } else if (sections.today.isEmpty() && sections.upcoming.isEmpty() && sections.recent.isEmpty()) {
                EmptyStateBox(
                    icon = Icons.Rounded.CalendarMonth,
                    title = strings.calendarTitle,
                    body = strings.noScheduledEpisodes,
                    modifier = modifier
                )
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "cal_subtitle") {
                        Text(
                            text = strings.calendarSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (sections.today.isNotEmpty()) {
                        item(key = "header_today") {
                            CalendarSectionHeader(strings.todayLabel)
                        }
                        items(sections.today, key = { "today_${it.episode.id}" }) { entry ->
                            CalendarEpisodeCard(entry = entry, onClick = { onOpenSeries(entry.seriesId) })
                        }
                    }

                    if (sections.upcoming.isNotEmpty()) {
                        item(key = "header_upcoming") {
                            CalendarSectionHeader(strings.upcomingLabel)
                        }
                        items(sections.upcoming, key = { "upcoming_${it.episode.id}" }) { entry ->
                            CalendarEpisodeCard(entry = entry, onClick = { onOpenSeries(entry.seriesId) })
                        }
                    }

                    if (sections.recent.isNotEmpty()) {
                        item(key = "header_recent") {
                            CalendarSectionHeader(strings.recentLabel)
                        }
                        items(sections.recent, key = { "recent_${it.episode.id}" }) { entry ->
                            CalendarEpisodeCard(entry = entry, onClick = { onOpenSeries(entry.seriesId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CalendarEpisodeCard(
    entry: CalendarEpisodeEntry,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val formattedDate = remember(entry.airDateMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.airDateMillis))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("calendar_episode_${entry.episode.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = entry.episode.thumbnail ?: entry.seriesPoster,
                contentDescription = entry.seriesTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = strings.episodeShortFormat.format(
                                entry.episode.season,
                                entry.episode.episode
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = entry.seriesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = entry.episode.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (entry.isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (entry.isWatched) strings.watchedLabel else strings.markUnwatched,
                tint = if (entry.isWatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
    }
}
