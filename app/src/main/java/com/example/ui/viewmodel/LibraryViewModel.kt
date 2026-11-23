package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.LibraryEntry
import com.example.data.firestore.LibraryRepository
import com.example.data.firestore.SeriesProgress
import com.example.data.firestore.WatchStatus
import com.example.data.remote.CinemetaRepository
import com.example.data.remote.EpisodeItem
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CalendarEpisodeEntry(
    val seriesId: String,
    val seriesTitle: String,
    val seriesPoster: String?,
    val episode: EpisodeItem,
    val airDateMillis: Long,
    val isWatched: Boolean
)

data class CalendarSections(
    val hasSeriesInLibrary: Boolean = false,
    val today: List<CalendarEpisodeEntry> = emptyList(),
    val upcoming: List<CalendarEpisodeEntry> = emptyList(),
    val recent: List<CalendarEpisodeEntry> = emptyList()
)

enum class LibraryMessageType {
    ADDED,
    STATUS_UPDATED,
    REMOVED,
    EPISODE_UPDATED,
    SEASON_UPDATED,
    ERROR
}

data class LibraryFeedbackEvent(
    val type: LibraryMessageType,
    val detail: String? = null
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val cinemetaRepository: CinemetaRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<LibraryFeedbackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryFeedbackEvent> = _events.asSharedFlow()

    val libraryState: StateFlow<UiState<List<LibraryEntry>>> =
        libraryRepository.observeLibrary(currentUserId)
            .map<List<LibraryEntry>, UiState<List<LibraryEntry>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing library", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load library"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val progressMap: StateFlow<Map<String, SeriesProgress>> =
        libraryRepository.observeAllProgress(currentUserId)
            .map { list -> list.associateBy { it.id } }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing progress", firestoreError ?: error)
                emit(emptyMap())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyMap()
            )

    private val _calendarState = MutableStateFlow<UiState<CalendarSections>>(UiState.Loading)
    val calendarState: StateFlow<UiState<CalendarSections>> = _calendarState.asStateFlow()

    init {
        observeCalendarFromLibrary()
    }

    private fun observeCalendarFromLibrary() {
        viewModelScope.launch {
            libraryState.collectLatest { state ->
                when (state) {
                    is UiState.Loading -> _calendarState.value = UiState.Loading
                    is UiState.Error -> _calendarState.value = UiState.Error(state.message)
                    is UiState.Success -> {
                        val seriesEntries = state.data.filter { it.mediaType == "series" }
                        if (seriesEntries.isEmpty()) {
                            _calendarState.value = UiState.Success(CalendarSections(hasSeriesInLibrary = false))
                        } else {
                            loadCalendarEpisodes(seriesEntries)
                        }
                    }
                }
            }
        }
    }

    fun refreshCalendar() {
        val current = (libraryState.value as? UiState.Success)?.data ?: return
        val seriesEntries = current.filter { it.mediaType == "series" }
        if (seriesEntries.isEmpty()) {
            _calendarState.value = UiState.Success(CalendarSections(hasSeriesInLibrary = false))
            return
        }
        viewModelScope.launch {
            loadCalendarEpisodes(seriesEntries)
        }
    }

    private suspend fun loadCalendarEpisodes(seriesEntries: List<LibraryEntry>) {
        _calendarState.value = UiState.Loading
        try {
            val watchedBySeries = progressMap.value
            val detailsList = coroutineScope {
                seriesEntries.take(20).map { entry ->
                    async {
                        val details = cinemetaRepository.getDetails("series", entry.id).getOrNull()
                        entry to details
                    }
                }.awaitAll()
            }

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfToday = cal.timeInMillis
            val endOfToday = startOfToday + DAY_MS
            val recentCutoff = startOfToday - (45L * DAY_MS)
            val upcomingCutoff = startOfToday + (60L * DAY_MS)

            val todayList = mutableListOf<CalendarEpisodeEntry>()
            val upcomingList = mutableListOf<CalendarEpisodeEntry>()
            val recentList = mutableListOf<CalendarEpisodeEntry>()

            detailsList.forEach { (entry, details) ->
                if (details == null) return@forEach
                val watchedIds = watchedBySeries[entry.id]?.watchedEpisodeIds.orEmpty().toSet()
                details.episodes.forEach { ep ->
                    if (ep.season <= 0) return@forEach
                    val airMillis = parseIsoDateMillis(ep.releasedIso) ?: return@forEach
                    val item = CalendarEpisodeEntry(
                        seriesId = entry.id,
                        seriesTitle = entry.title,
                        seriesPoster = entry.poster,
                        episode = ep,
                        airDateMillis = airMillis,
                        isWatched = ep.id in watchedIds
                    )
                    when {
                        airMillis in startOfToday until endOfToday -> todayList.add(item)
                        airMillis in endOfToday..upcomingCutoff -> upcomingList.add(item)
                        airMillis in recentCutoff until startOfToday -> recentList.add(item)
                    }
                }
            }

            _calendarState.value = UiState.Success(
                CalendarSections(
                    hasSeriesInLibrary = true,
                    today = todayList.sortedBy { it.episode.episode },
                    upcoming = upcomingList.sortedBy { it.airDateMillis },
                    recent = recentList.sortedByDescending { it.airDateMillis }.take(30)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _calendarState.value = UiState.Error(e.localizedMessage ?: "Failed to build calendar")
        }
    }

    fun saveMediaItem(item: MediaItem, status: WatchStatus = WatchStatus.WATCHLIST) {
        viewModelScope.launch {
            val result = libraryRepository.upsertLibraryEntry(
                userId = currentUserId,
                mediaId = item.id,
                mediaType = item.type,
                title = item.name,
                poster = item.poster,
                background = item.background,
                releaseInfo = item.releaseInfo,
                imdbRating = item.imdbRating,
                status = status
            )
            emitResultFeedback(result, LibraryMessageType.ADDED)
        }
    }

    fun updateMediaStatus(details: MediaDetails, status: WatchStatus, isAlreadyInLibrary: Boolean) {
        viewModelScope.launch {
            val totalEps = details.episodes.count { it.season > 0 }
            val result = libraryRepository.upsertLibraryEntry(
                userId = currentUserId,
                mediaId = details.id,
                mediaType = details.type,
                title = details.name,
                poster = details.poster,
                background = details.background,
                releaseInfo = details.releaseInfo,
                imdbRating = details.imdbRating,
                status = status,
                totalEpisodes = totalEps
            )
            emitResultFeedback(
                result,
                if (isAlreadyInLibrary) LibraryMessageType.STATUS_UPDATED else LibraryMessageType.ADDED
            )
        }
    }

    fun updateEntryStatus(entry: LibraryEntry, status: WatchStatus) {
        viewModelScope.launch {
            val result = libraryRepository.upsertLibraryEntry(
                userId = currentUserId,
                mediaId = entry.id,
                mediaType = entry.mediaType,
                title = entry.title,
                poster = entry.poster,
                background = entry.background,
                releaseInfo = entry.releaseInfo,
                imdbRating = entry.imdbRating,
                status = status,
                totalEpisodes = entry.totalEpisodes.toInt()
            )
            emitResultFeedback(result, LibraryMessageType.STATUS_UPDATED)
        }
    }

    fun removeFromLibrary(mediaId: String) {
        viewModelScope.launch {
            val result = libraryRepository.removeFromLibrary(currentUserId, mediaId)
            emitResultFeedback(result, LibraryMessageType.REMOVED)
        }
    }

    fun toggleEpisodeWatched(details: MediaDetails, episode: EpisodeItem) {
        viewModelScope.launch {
            val currentWatched = progressMap.value[details.id]?.watchedEpisodeIds.orEmpty().toMutableSet()
            val wasWatched = episode.id in currentWatched
            if (wasWatched) {
                currentWatched.remove(episode.id)
            } else {
                currentWatched.add(episode.id)
            }
            val totalEps = details.episodes.count { it.season > 0 }
                .takeIf { it > 0 } ?: details.episodes.size

            val latestEp = if (!wasWatched) {
                episode
            } else {
                details.episodes.lastOrNull { it.id in currentWatched }
            }

            val result = libraryRepository.updateSeriesEpisodes(
                userId = currentUserId,
                mediaId = details.id,
                title = details.name,
                poster = details.poster,
                background = details.background,
                releaseInfo = details.releaseInfo,
                imdbRating = details.imdbRating,
                totalEpisodes = totalEps,
                newWatchedEpisodeIds = currentWatched.toList(),
                lastEpisodeId = latestEp?.id,
                lastSeason = latestEp?.season ?: 0,
                lastEpisode = latestEp?.episode ?: 0,
                lastWatchedLabel = latestEp?.let { "S${it.season} E${it.episode}" }
            )
            emitResultFeedback(result, LibraryMessageType.EPISODE_UPDATED)
        }
    }

    fun markSeasonWatched(details: MediaDetails, season: Int, markWatched: Boolean) {
        viewModelScope.launch {
            val seasonEpisodes = details.episodesForSeason(season)
            if (seasonEpisodes.isEmpty()) return@launch

            val currentWatched = progressMap.value[details.id]?.watchedEpisodeIds.orEmpty().toMutableSet()
            val seasonIds = seasonEpisodes.map { it.id }.toSet()
            if (markWatched) {
                currentWatched.addAll(seasonIds)
            } else {
                currentWatched.removeAll(seasonIds)
            }

            val totalEps = details.episodes.count { it.season > 0 }
                .takeIf { it > 0 } ?: details.episodes.size
            val latestEp = if (markWatched) {
                seasonEpisodes.lastOrNull()
            } else {
                details.episodes.lastOrNull { it.id in currentWatched }
            }

            val result = libraryRepository.updateSeriesEpisodes(
                userId = currentUserId,
                mediaId = details.id,
                title = details.name,
                poster = details.poster,
                background = details.background,
                releaseInfo = details.releaseInfo,
                imdbRating = details.imdbRating,
                totalEpisodes = totalEps,
                newWatchedEpisodeIds = currentWatched.toList(),
                lastEpisodeId = latestEp?.id,
                lastSeason = latestEp?.season ?: 0,
                lastEpisode = latestEp?.episode ?: 0,
                lastWatchedLabel = latestEp?.let { "S${it.season} E${it.episode}" }
            )
            emitResultFeedback(result, LibraryMessageType.SEASON_UPDATED)
        }
    }

    private suspend fun emitResultFeedback(result: Result<Unit>, successType: LibraryMessageType) {
        result.fold(
            onSuccess = { _events.emit(LibraryFeedbackEvent(successType)) },
            onFailure = { err ->
                _events.emit(LibraryFeedbackEvent(LibraryMessageType.ERROR, err.localizedMessage))
            }
        )
    }

    private fun parseIsoDateMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(iso)
                if (date != null) return date.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private companion object {
        const val TAG = "LibraryVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DAY_MS = 86_400_000L
    }
}
