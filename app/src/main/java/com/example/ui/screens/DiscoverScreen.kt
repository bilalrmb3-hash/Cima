package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.firestore.LibraryEntry
import com.example.data.remote.CinemetaRepository
import com.example.data.remote.MediaItem
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.ErrorStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.MediaPosterCard
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.DiscoverFilterState
import com.example.ui.viewmodel.UiState

@Composable
fun DiscoverScreen(
    filterState: DiscoverFilterState,
    discoverState: UiState<List<MediaItem>>,
    libraryMap: Map<String, LibraryEntry>,
    onTypeSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChanged("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.close)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_search_input")
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = filterState.selectedType == "movie",
                    onClick = { onTypeSelected("movie") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Movie, contentDescription = null) },
                    modifier = Modifier.testTag("filter_type_movie")
                ) {
                    Text(strings.movies)
                }
                SegmentedButton(
                    selected = filterState.selectedType == "series",
                    onClick = { onTypeSelected("series") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                    modifier = Modifier.testTag("filter_type_series")
                ) {
                    Text(strings.series)
                }
            }
        }

        if (filterState.searchQuery.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "genre_all") {
                    FilterChip(
                        selected = filterState.selectedGenre == null,
                        onClick = { onGenreSelected(null) },
                        label = { Text(strings.allGenres) },
                        modifier = Modifier.testTag("genre_chip_all")
                    )
                }
                items(CinemetaRepository.POPULAR_GENRES, key = { it }) { genre ->
                    FilterChip(
                        selected = filterState.selectedGenre == genre,
                        onClick = {
                            onGenreSelected(if (filterState.selectedGenre == genre) null else genre)
                        },
                        label = { Text(strings.localizeGenre(genre)) },
                        modifier = Modifier.testTag("genre_chip_$genre")
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        when (discoverState) {
            is UiState.Loading -> LoadingStateBox(strings.loading, Modifier.weight(1f))
            is UiState.Error -> ErrorStateBox(strings.networkError, onRetry, Modifier.weight(1f))
            is UiState.Success -> {
                val items = discoverState.data
                if (items.isEmpty()) {
                    EmptyStateBox(
                        icon = Icons.Rounded.SearchOff,
                        title = strings.noResultsTitle,
                        body = strings.noResultsBody,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 116.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(items, key = { "${it.type}_${it.id}" }) { item ->
                            MediaPosterCard(
                                item = item,
                                libraryEntry = libraryMap[item.id],
                                onClick = { onOpenDetails(item.type, item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
