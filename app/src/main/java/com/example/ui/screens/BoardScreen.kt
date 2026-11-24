package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.firestore.LibraryEntry
import com.example.data.firestore.WatchStatus
import com.example.data.remote.MediaItem
import com.example.ui.components.ContinueWatchingCard
import com.example.ui.components.ErrorStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.MediaPosterCard
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.BoardData
import com.example.ui.viewmodel.UiState

@Composable
fun BoardScreen(
    boardState: UiState<BoardData>,
    libraryMap: Map<String, LibraryEntry>,
    continueWatching: List<LibraryEntry>,
    isGuest: Boolean,
    onRequestSignIn: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: (type: String, id: String) -> Unit,
    onQuickAdd: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    when (boardState) {
        is UiState.Loading -> LoadingStateBox(strings.loading, modifier)
        is UiState.Error -> ErrorStateBox(strings.networkError, onRetry, modifier)
        is UiState.Success -> {
            val data = boardState.data
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (data.spotlight.isNotEmpty()) {
                    item(key = "spotlight") {
                        SpotlightHeroCarousel(
                            items = data.spotlight,
                            libraryMap = libraryMap,
                            onOpenDetails = onOpenDetails,
                            onQuickAdd = onQuickAdd
                        )
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item(key = "continue_watching") {
                        Column {
                            SectionHeader(title = strings.continueWatching)
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(continueWatching, key = { "cw_${it.id}" }) { entry ->
                                    ContinueWatchingCard(
                                        entry = entry,
                                        onClick = { onOpenDetails(entry.mediaType, entry.id) }
                                    )
                                }
                            }
                        }
                    }
                } else if (isGuest) {
                    item(key = "guest_resume_banner") {
                        GuestResumeBanner(
                            onSignInClick = onRequestSignIn,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (data.trendingMovies.isNotEmpty()) {
                    item(key = "trending_movies") {
                        MediaHorizontalSection(
                            title = strings.trendingMovies,
                            items = data.trendingMovies,
                            libraryMap = libraryMap,
                            onOpenDetails = onOpenDetails
                        )
                    }
                }

                if (data.popularSeries.isNotEmpty()) {
                    item(key = "popular_series") {
                        MediaHorizontalSection(
                            title = strings.popularSeries,
                            items = data.popularSeries,
                            libraryMap = libraryMap,
                            onOpenDetails = onOpenDetails
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightHeroCarousel(
    items: List<MediaItem>,
    libraryMap: Map<String, LibraryEntry>,
    onOpenDetails: (type: String, id: String) -> Unit,
    onQuickAdd: (MediaItem) -> Unit
) {
    val strings = LocalStrings.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = items[page]
            val inLibrary = libraryMap.containsKey(item.id)

            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { onOpenDetails(item.type, item.id) }
                    .testTag("spotlight_card_${item.id}")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = item.background ?: item.poster,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.12f),
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = strings.spotlightBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (!item.imdbRating.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = item.imdbRating,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.genres.isNotEmpty() || !item.releaseInfo.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            val metaLine = buildList {
                                item.releaseInfo?.let { add(it) }
                                addAll(item.genres.take(2).map { strings.localizeGenre(it) })
                            }.joinToString(" • ")
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onOpenDetails(item.type, item.id) },
                                modifier = Modifier.testTag("spotlight_details_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.viewDetails)
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (inLibrary) onOpenDetails(item.type, item.id)
                                    else onQuickAdd(item)
                                },
                                modifier = Modifier.testTag("spotlight_add_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (inLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (inLibrary) strings.inLibrary else strings.addToLibrary)
                            }
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaHorizontalSection(
    title: String,
    items: List<MediaItem>,
    libraryMap: Map<String, LibraryEntry>,
    onOpenDetails: (type: String, id: String) -> Unit
) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.type}_${it.id}" }) { item ->
                MediaPosterCard(
                    item = item,
                    libraryEntry = libraryMap[item.id],
                    onClick = { onOpenDetails(item.type, item.id) },
                    modifier = Modifier.width(136.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun GuestResumeBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.guestSyncTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings.guestSyncDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onSignInClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("board_guest_signin_button")
            ) {
                Text(strings.signInWithGoogle)
            }
        }
    }
}
