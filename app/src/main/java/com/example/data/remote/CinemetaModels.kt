package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CatalogResponseDto(
    val metas: List<MetaPreviewDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MetaResponseDto(
    val meta: MetaDetailDto? = null
)

@JsonClass(generateAdapter = true)
data class MetaPreviewDto(
    val id: String = "",
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MetaDetailDto(
    val id: String = "",
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val cast: List<String>? = null,
    val director: List<String>? = null,
    val trailers: List<TrailerDto>? = null,
    val trailerStreams: List<TrailerStreamDto>? = null,
    val videos: List<VideoDto>? = null
)

@JsonClass(generateAdapter = true)
data class TrailerDto(
    val source: String = "",
    val type: String = "Trailer"
)

@JsonClass(generateAdapter = true)
data class TrailerStreamDto(
    val title: String? = null,
    val ytId: String = ""
)

@JsonClass(generateAdapter = true)
data class VideoDto(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val number: Int? = null,
    val released: String? = null,
    val firstAired: String? = null,
    val overview: String? = null,
    val description: String? = null,
    val thumbnail: String? = null
)

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val description: String?,
    val releaseInfo: String?,
    val imdbRating: String?,
    val genres: List<String>
)

data class TrailerItem(
    val ytId: String,
    val title: String
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String?,
    val releasedIso: String?,
    val thumbnail: String?
)

data class MediaDetails(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val runtime: String?,
    val imdbRating: String?,
    val genres: List<String>,
    val cast: List<String>,
    val director: List<String>,
    val trailers: List<TrailerItem>,
    val episodes: List<EpisodeItem>
) {
    val seasons: List<Int>
        get() = episodes.map { it.season }.distinct().sortedWith(
            compareBy<Int> { if (it == 0) Int.MAX_VALUE else it }
        )

    fun episodesForSeason(season: Int): List<EpisodeItem> =
        episodes.filter { it.season == season }.sortedBy { it.episode }
}
