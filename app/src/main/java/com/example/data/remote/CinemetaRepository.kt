package com.example.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class CinemetaRepository(private val api: CinemetaApi = CinemetaApi.create()) {

    private val detailCache = mutableMapOf<String, MediaDetails>()

    suspend fun getCatalog(
        type: String,
        genre: String? = null,
        catalogId: String = "top"
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val response = if (!genre.isNullOrBlank()) {
                api.getCatalogByGenre(type = type, catalogId = catalogId, genre = genre)
            } else {
                api.getCatalog(type = type, catalogId = catalogId)
            }
            Result.success(response.metas.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAll(query: String, typeFilter: String? = null): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())
            try {
                coroutineScope {
                    val types = when (typeFilter) {
                        "movie" -> listOf("movie")
                        "series" -> listOf("series")
                        else -> listOf("movie", "series")
                    }
                    val results = types.map { mediaType ->
                        async {
                            api.searchCatalog(type = mediaType, catalogId = "top", query = trimmed)
                                .metas
                                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                                .map { it.toDomain() }
                        }
                    }.awaitAll().flatten()
                    Result.success(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getDetails(type: String, id: String, forceRefresh: Boolean = false): Result<MediaDetails> =
        withContext(Dispatchers.IO) {
            val cacheKey = "$type:$id"
            if (!forceRefresh) {
                detailCache[cacheKey]?.let { return@withContext Result.success(it) }
            }
            try {
                val meta = api.getMeta(type = type, id = id).meta
                    ?: return@withContext Result.failure(IllegalStateException("Metadata not found"))
                val details = meta.toDomain()
                detailCache[cacheKey] = details
                Result.success(details)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun MetaPreviewDto.toDomain(): MediaItem = MediaItem(
        id = id,
        type = type.ifBlank { "movie" },
        name = name,
        poster = poster?.takeIf { it.isNotBlank() } ?: "https://images.metahub.space/poster/medium/$id/img",
        background = background?.takeIf { it.isNotBlank() } ?: "https://images.metahub.space/background/medium/$id/img",
        description = description?.takeIf { it.isNotBlank() },
        releaseInfo = releaseInfo?.takeIf { it.isNotBlank() },
        imdbRating = imdbRating?.takeIf { it.isNotBlank() },
        genres = genres.orEmpty().filter { it.isNotBlank() }
    )

    private fun MetaDetailDto.toDomain(): MediaDetails {
        val mergedTrailers = buildList {
            trailerStreams.orEmpty().forEach { stream ->
                if (stream.ytId.isNotBlank()) {
                    add(TrailerItem(ytId = stream.ytId, title = stream.title?.takeIf { it.isNotBlank() } ?: "Trailer"))
                }
            }
            trailers.orEmpty().forEach { trailer ->
                if (trailer.source.isNotBlank() && none { it.ytId == trailer.source }) {
                    add(TrailerItem(ytId = trailer.source, title = trailer.type.ifBlank { "Trailer" }))
                }
            }
        }

        val mappedEpisodes = videos.orEmpty().mapNotNull { video ->
            val episodeNum = video.episode ?: video.number ?: return@mapNotNull null
            val seasonNum = video.season ?: 1
            val epId = video.id.takeIf { it.isNotBlank() } ?: "$id:$seasonNum:$episodeNum"
            EpisodeItem(
                id = epId,
                season = seasonNum,
                episode = episodeNum,
                title = (video.title ?: video.name)?.takeIf { it.isNotBlank() } ?: "Episode $episodeNum",
                overview = (video.overview ?: video.description)?.takeIf { it.isNotBlank() },
                releasedIso = (video.released ?: video.firstAired)?.takeIf { it.isNotBlank() },
                thumbnail = video.thumbnail?.takeIf { it.isNotBlank() }
            )
        }

        return MediaDetails(
            id = id,
            type = type.ifBlank { "movie" },
            name = name,
            poster = poster?.takeIf { it.isNotBlank() } ?: "https://images.metahub.space/poster/medium/$id/img",
            background = background?.takeIf { it.isNotBlank() } ?: "https://images.metahub.space/background/medium/$id/img",
            logo = logo?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            releaseInfo = releaseInfo?.takeIf { it.isNotBlank() },
            runtime = runtime?.takeIf { it.isNotBlank() },
            imdbRating = imdbRating?.takeIf { it.isNotBlank() },
            genres = genres.orEmpty().filter { it.isNotBlank() },
            cast = cast.orEmpty().filter { it.isNotBlank() },
            director = director.orEmpty().filter { it.isNotBlank() },
            trailers = mergedTrailers,
            episodes = mappedEpisodes
        )
    }

    companion object {
        val POPULAR_GENRES = listOf(
            "Action",
            "Adventure",
            "Animation",
            "Comedy",
            "Crime",
            "Documentary",
            "Drama",
            "Fantasy",
            "Horror",
            "Mystery",
            "Romance",
            "Sci-Fi",
            "Thriller"
        )
    }
}
