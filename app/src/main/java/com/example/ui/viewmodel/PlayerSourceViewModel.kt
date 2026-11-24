package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.addons.ActivePlaybackSession
import com.example.data.addons.AddonStreamFetchResult
import com.example.data.addons.InstalledAddon
import com.example.data.addons.PlayableStream
import com.example.data.addons.PlaybackTarget
import com.example.data.addons.StreamSourceType
import com.example.data.addons.StremioAddonRepository
import com.example.data.addons.SubtitleTrackItem
import com.example.data.player.LocalPlaybackStore
import com.example.data.player.PlaybackBookmark
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddonInstallState {
    data object Idle : AddonInstallState
    data object Installing : AddonInstallState
    data class Installed(val addon: InstalledAddon) : AddonInstallState
    data class Error(val message: String) : AddonInstallState
}

class PlayerSourceViewModel(
    private val addonRepository: StremioAddonRepository,
    private val playbackStore: LocalPlaybackStore
) : ViewModel() {

    val installedAddons: StateFlow<List<InstalledAddon>> = addonRepository.installedAddonsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val bookmarks: StateFlow<Map<String, PlaybackBookmark>> = playbackStore.bookmarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyMap()
        )

    private val _addonInstallState = MutableStateFlow<AddonInstallState>(AddonInstallState.Idle)
    val addonInstallState: StateFlow<AddonInstallState> = _addonInstallState.asStateFlow()

    private val _sourcePickerTarget = MutableStateFlow<PlaybackTarget?>(null)
    val sourcePickerTarget: StateFlow<PlaybackTarget?> = _sourcePickerTarget.asStateFlow()

    private val _streamFetchState = MutableStateFlow<UiState<AddonStreamFetchResult>>(
        UiState.Success(AddonStreamFetchResult())
    )
    val streamFetchState: StateFlow<UiState<AddonStreamFetchResult>> = _streamFetchState.asStateFlow()

    private val _activeSession = MutableStateFlow<ActivePlaybackSession?>(null)
    val activeSession: StateFlow<ActivePlaybackSession?> = _activeSession.asStateFlow()

    private var fetchJob: Job? = null

    fun openSourcePicker(target: PlaybackTarget) {
        _sourcePickerTarget.value = target
        fetchStreamsFor(target)
    }

    fun dismissSourcePicker() {
        _sourcePickerTarget.value = null
        fetchJob?.cancel()
    }

    fun refreshStreams() {
        val target = _sourcePickerTarget.value ?: return
        fetchStreamsFor(target)
    }

    private fun fetchStreamsFor(target: PlaybackTarget) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _streamFetchState.value = UiState.Loading
            val result = addonRepository.fetchStreamsAndSubtitles(
                mediaType = target.mediaType,
                streamId = target.streamId
            )
            _streamFetchState.value = UiState.Success(result)
        }
    }

    fun startAddonStream(
        target: PlaybackTarget,
        stream: PlayableStream,
        extraSubtitles: List<SubtitleTrackItem>
    ) {
        _sourcePickerTarget.value = null
        val combinedSubs = (stream.subtitles + extraSubtitles).distinctBy { it.url }
        _activeSession.value = ActivePlaybackSession(
            target = target,
            stream = stream,
            extraSubtitles = combinedSubs
        )
    }

    fun startDirectUrl(
        target: PlaybackTarget,
        rawUrl: String,
        subtitleUrl: String?,
        extraSubtitles: List<SubtitleTrackItem> = emptyList()
    ) {
        val trimmedUrl = rawUrl.trim()
        if (trimmedUrl.isBlank()) return
        val manualSubs = buildList {
            val cleanSub = subtitleUrl?.trim().orEmpty()
            if (cleanSub.isNotBlank()) {
                add(
                    SubtitleTrackItem(
                        id = "manual_sub",
                        url = cleanSub,
                        lang = "und",
                        label = "External Subtitle"
                    )
                )
            }
            addAll(extraSubtitles)
        }.distinctBy { it.url }

        val stream = PlayableStream(
            id = "direct_${trimmedUrl.hashCode()}",
            addonName = "Direct URL",
            title = target.title,
            description = trimmedUrl,
            url = trimmedUrl,
            sourceType = StreamSourceType.DIRECT_URL,
            subtitles = manualSubs,
            qualityScore = 1000
        )
        _sourcePickerTarget.value = null
        _activeSession.value = ActivePlaybackSession(
            target = target,
            stream = stream,
            extraSubtitles = manualSubs
        )
    }

    fun startLocalFile(
        target: PlaybackTarget,
        videoUri: Uri,
        displayName: String,
        subtitleUri: Uri?
    ) {
        val stream = PlayableStream(
            id = "local_${videoUri.toString().hashCode()}",
            addonName = "Local File",
            title = displayName,
            description = videoUri.lastPathSegment,
            url = videoUri.toString(),
            sourceType = StreamSourceType.LOCAL_FILE,
            qualityScore = 1000
        )
        _sourcePickerTarget.value = null
        _activeSession.value = ActivePlaybackSession(
            target = target,
            stream = stream,
            localSubtitleUri = subtitleUri
        )
    }

    fun closePlayer() {
        _activeSession.value = null
    }

    fun switchToNextEpisode(currentTarget: PlaybackTarget) {
        val nextStreamId = currentTarget.nextEpisodeStreamId ?: return
        val nextPlaybackKey = currentTarget.nextEpisodePlaybackKey ?: return
        val nextTitle = currentTarget.nextEpisodeTitle ?: currentTarget.title
        _activeSession.value = null
        openSourcePicker(
            PlaybackTarget(
                playbackKey = nextPlaybackKey,
                mediaId = currentTarget.mediaId,
                mediaType = currentTarget.mediaType,
                streamId = nextStreamId,
                title = currentTarget.title,
                subtitle = currentTarget.nextEpisodeSubtitle ?: nextTitle,
                poster = currentTarget.poster
            )
        )
    }

    fun savePlaybackPosition(
        target: PlaybackTarget,
        sourceTitle: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            playbackStore.savePosition(
                PlaybackBookmark(
                    playbackKey = target.playbackKey,
                    mediaId = target.mediaId,
                    mediaType = target.mediaType,
                    title = target.title,
                    subtitle = target.subtitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    sourceTitle = sourceTitle,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearBookmark(playbackKey: String) {
        viewModelScope.launch {
            playbackStore.clearPosition(playbackKey)
        }
    }

    fun installAddon(rawUrl: String) {
        viewModelScope.launch {
            _addonInstallState.value = AddonInstallState.Installing
            val result = addonRepository.installAddon(rawUrl)
            result.fold(
                onSuccess = { addon ->
                    _addonInstallState.value = AddonInstallState.Installed(addon)
                    _sourcePickerTarget.value?.let { fetchStreamsFor(it) }
                },
                onFailure = { err ->
                    _addonInstallState.value = AddonInstallState.Error(
                        err.localizedMessage ?: "Failed to install addon"
                    )
                }
            )
        }
    }

    fun resetAddonInstallState() {
        _addonInstallState.value = AddonInstallState.Idle
    }

    fun toggleAddon(addonId: String, enabled: Boolean) {
        viewModelScope.launch {
            addonRepository.toggleAddon(addonId, enabled)
            _sourcePickerTarget.value?.let { fetchStreamsFor(it) }
        }
    }

    fun removeAddon(addonId: String) {
        viewModelScope.launch {
            addonRepository.removeAddon(addonId)
            _sourcePickerTarget.value?.let { fetchStreamsFor(it) }
        }
    }
}
