package com.example.data.addons

import android.net.Uri
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InstalledAddon(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val manifestUrl: String,
    val baseUrl: String,
    val resources: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    fun supportsResource(resource: String): Boolean =
        resources.any { it.equals(resource, ignoreCase = true) }

    fun supportsType(type: String): Boolean =
        types.isEmpty() || types.any { it.equals(type, ignoreCase = true) }
}

@JsonClass(generateAdapter = true)
data class InstalledAddonsEnvelope(
    val addons: List<InstalledAddon> = emptyList()
)

data class SubtitleTrackItem(
    val id: String,
    val url: String,
    val lang: String,
    val label: String
)

enum class StreamSourceType {
    ADDON_HTTP,
    ADDON_EXTERNAL,
    DIRECT_URL,
    LOCAL_FILE
}

data class PlayableStream(
    val id: String,
    val addonName: String,
    val title: String,
    val description: String? = null,
    val url: String,
    val sourceType: StreamSourceType,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrackItem> = emptyList(),
    val qualityScore: Int = 0,
    val externalUrl: String? = null,
    val isTorrentOnly: Boolean = false
) {
    val isDirectlyPlayable: Boolean
        get() = sourceType == StreamSourceType.DIRECT_URL ||
            sourceType == StreamSourceType.LOCAL_FILE ||
            (sourceType == StreamSourceType.ADDON_HTTP && url.isNotBlank())
}

data class AddonStreamFetchResult(
    val streams: List<PlayableStream> = emptyList(),
    val subtitles: List<SubtitleTrackItem> = emptyList(),
    val failedAddons: List<String> = emptyList()
)

data class PlaybackTarget(
    val playbackKey: String,
    val mediaId: String,
    val mediaType: String,
    val streamId: String,
    val title: String,
    val subtitle: String? = null,
    val poster: String? = null,
    val nextEpisodeStreamId: String? = null,
    val nextEpisodePlaybackKey: String? = null,
    val nextEpisodeTitle: String? = null,
    val nextEpisodeSubtitle: String? = null
)

data class ActivePlaybackSession(
    val target: PlaybackTarget,
    val stream: PlayableStream,
    val extraSubtitles: List<SubtitleTrackItem> = emptyList(),
    val localSubtitleUri: Uri? = null
)
