package com.example.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

@JsonClass(generateAdapter = true)
data class PlaybackBookmark(
    val playbackKey: String,
    val mediaId: String,
    val mediaType: String,
    val title: String,
    val subtitle: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val sourceTitle: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isMeaningfulResume: Boolean
        get() = positionMs >= 5_000L && (durationMs <= 0L || progressFraction < 0.96f)
}

@JsonClass(generateAdapter = true)
data class PlaybackBookmarksEnvelope(
    val items: List<PlaybackBookmark> = emptyList()
)

private val Context.playbackBookmarksStore: DataStore<Preferences> by preferencesDataStore(
    name = "cineboard_playback_bookmarks"
)

class LocalPlaybackStore(private val context: Context) {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(PlaybackBookmarksEnvelope::class.java)
    private val keyBookmarksJson = stringPreferencesKey("bookmarks_json")

    val bookmarksFlow: Flow<Map<String, PlaybackBookmark>> = context.playbackBookmarksStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val raw = prefs[keyBookmarksJson]
            if (raw.isNullOrBlank()) {
                emptyMap()
            } else {
                val parsed = runCatching { adapter.fromJson(raw)?.items }.getOrNull().orEmpty()
                parsed.associateBy { it.playbackKey }
            }
        }

    suspend fun savePosition(bookmark: PlaybackBookmark) {
        if (bookmark.positionMs < 3_000L) return
        context.playbackBookmarksStore.edit { prefs ->
            val current = decodeList(prefs[keyBookmarksJson]).toMutableList()
            current.removeAll { it.playbackKey == bookmark.playbackKey }
            current.add(0, bookmark)
            val trimmed = current.sortedByDescending { it.updatedAtMs }.take(MAX_BOOKMARKS)
            prefs[keyBookmarksJson] = adapter.toJson(PlaybackBookmarksEnvelope(trimmed))
        }
    }

    suspend fun clearPosition(playbackKey: String) {
        context.playbackBookmarksStore.edit { prefs ->
            val current = decodeList(prefs[keyBookmarksJson]).toMutableList()
            val changed = current.removeAll { it.playbackKey == playbackKey }
            if (changed) {
                prefs[keyBookmarksJson] = adapter.toJson(PlaybackBookmarksEnvelope(current))
            }
        }
    }

    private fun decodeList(raw: String?): List<PlaybackBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(raw)?.items }.getOrNull().orEmpty()
    }

    private companion object {
        const val MAX_BOOKMARKS = 150
    }
}

fun formatDurationMs(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
