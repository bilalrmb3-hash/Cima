package com.example.data.firestore

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WatchStatus {
    WATCHLIST,
    WATCHING,
    COMPLETED;

    companion object {
        fun fromRaw(raw: String?): WatchStatus =
            entries.firstOrNull { it.name == raw } ?: WATCHLIST
    }
}

@Keep
@IgnoreExtraProperties
data class LibraryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mediaType: String = "movie",
    val title: String = "",
    val poster: String? = null,
    val background: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val status: String = WatchStatus.WATCHLIST.name,
    val totalEpisodes: Long = 0L,
    val watchedCount: Long = 0L,
    val lastWatchedEpisodeId: String? = null,
    val lastWatchedLabel: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val watchStatus: WatchStatus
        get() = WatchStatus.fromRaw(status)

    val progressFraction: Float
        get() = when {
            mediaType == "movie" -> if (watchStatus == WatchStatus.COMPLETED || watchedCount > 0L) 1f else 0f
            totalEpisodes <= 0L -> 0f
            else -> (watchedCount.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
        }
}

@Keep
@IgnoreExtraProperties
data class SeriesProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mediaType: String = "series",
    val watchedEpisodeIds: List<String> = emptyList(),
    val lastEpisodeId: String? = null,
    val lastSeason: Long = 0L,
    val lastEpisode: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
