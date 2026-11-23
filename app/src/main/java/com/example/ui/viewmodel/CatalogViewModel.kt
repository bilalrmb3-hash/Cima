package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CinemetaRepository
import com.example.data.remote.MediaDetails
import com.example.data.remote.MediaItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoardData(
    val spotlight: List<MediaItem>,
    val trendingMovies: List<MediaItem>,
    val popularSeries: List<MediaItem>
)

data class DiscoverFilterState(
    val selectedType: String = "movie",
    val selectedGenre: String? = null,
    val searchQuery: String = ""
)

@OptIn(FlowPreview::class)
class CatalogViewModel(
    private val cinemetaRepository: CinemetaRepository = CinemetaRepository()
) : ViewModel() {

    private val _boardState = MutableStateFlow<UiState<BoardData>>(UiState.Loading)
    val boardState: StateFlow<UiState<BoardData>> = _boardState.asStateFlow()

    private val _discoverFilter = MutableStateFlow(DiscoverFilterState())
    val discoverFilter: StateFlow<DiscoverFilterState> = _discoverFilter.asStateFlow()

    private val _discoverState = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
    val discoverState: StateFlow<UiState<List<MediaItem>>> = _discoverState.asStateFlow()

    private val _detailsStates = MutableStateFlow<Map<String, UiState<MediaDetails>>>(emptyMap())
    val detailsStates: StateFlow<Map<String, UiState<MediaDetails>>> = _detailsStates.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var discoverJob: Job? = null

    init {
        loadBoard()
        observeDiscoverFilters()
    }

    fun loadBoard() {
        viewModelScope.launch {
            _boardState.value = UiState.Loading
            try {
                coroutineScope {
                    val moviesDeferred = async { cinemetaRepository.getCatalog("movie") }
                    val seriesDeferred = async { cinemetaRepository.getCatalog("series") }
                    val moviesResult = moviesDeferred.await()
                    val seriesResult = seriesDeferred.await()

                    val movies = moviesResult.getOrNull().orEmpty()
                    val series = seriesResult.getOrNull().orEmpty()

                    if (movies.isEmpty() && series.isEmpty()) {
                        val err = moviesResult.exceptionOrNull() ?: seriesResult.exceptionOrNull()
                        _boardState.value = UiState.Error(err?.localizedMessage ?: "Failed to load catalog")
                    } else {
                        val spotlight = (movies.take(3) + series.take(3))
                            .sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
                            .take(5)
                        _boardState.value = UiState.Success(
                            BoardData(
                                spotlight = spotlight,
                                trendingMovies = movies.take(25),
                                popularSeries = series.take(25)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _boardState.value = UiState.Error(e.localizedMessage ?: "Failed to load board")
            }
        }
    }

    private fun observeDiscoverFilters() {
        viewModelScope.launch {
            val debouncedQuery = searchQueryFlow
                .debounce(350L)
                .distinctUntilChanged()

            combine(
                _discoverFilter,
                debouncedQuery
            ) { filter, query ->
                filter.copy(searchQuery = query)
            }
                .distinctUntilChanged()
                .collectLatest { filter ->
                    fetchDiscover(filter)
                }
        }
    }

    private fun fetchDiscover(filter: DiscoverFilterState) {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            _discoverState.value = UiState.Loading
            val result = if (filter.searchQuery.isNotBlank()) {
                cinemetaRepository.searchAll(
                    query = filter.searchQuery,
                    typeFilter = filter.selectedType
                )
            } else {
                cinemetaRepository.getCatalog(
                    type = filter.selectedType,
                    genre = filter.selectedGenre
                )
            }

            result.fold(
                onSuccess = { items -> _discoverState.value = UiState.Success(items) },
                onFailure = { err ->
                    _discoverState.value = UiState.Error(err.localizedMessage ?: "Search failed")
                }
            )
        }
    }

    fun setDiscoverType(type: String) {
        _discoverFilter.update { it.copy(selectedType = type) }
    }

    fun setDiscoverGenre(genre: String?) {
        _discoverFilter.update { it.copy(selectedGenre = genre) }
    }

    fun setSearchQuery(query: String) {
        _discoverFilter.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun retryDiscover() {
        fetchDiscover(_discoverFilter.value)
    }

    fun loadDetails(type: String, id: String, forceRefresh: Boolean = false) {
        val key = "$type:$id"
        val current = _detailsStates.value[key]
        if (!forceRefresh && current is UiState.Success) return

        viewModelScope.launch {
            _detailsStates.update { it + (key to UiState.Loading) }
            cinemetaRepository.getDetails(type = type, id = id, forceRefresh = forceRefresh).fold(
                onSuccess = { details ->
                    _detailsStates.update { it + (key to UiState.Success(details)) }
                },
                onFailure = { err ->
                    _detailsStates.update {
                        it + (key to UiState.Error(err.localizedMessage ?: "Failed to load details"))
                    }
                }
            )
        }
    }
}
