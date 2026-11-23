package com.example.data.firestore

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        }
        return uid
    }

    private fun sanitizeMediaId(id: String): String {
        val cleaned = id.trim()
        require(cleaned.isNotBlank() && cleaned.length <= 128 && ID_REGEX.matches(cleaned)) {
            "Invalid mediaId for Firestore document path"
        }
        return cleaned
    }

    fun observeLibrary(userId: String): Flow<List<LibraryEntry>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/library"
        emitAll(
            db.collection("users").document(userId).collection("library")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LibraryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllProgress(userId: String): Flow<List<SeriesProgress>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/progress"
        emitAll(
            db.collection("users").document(userId).collection("progress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SeriesProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertLibraryEntry(
        userId: String,
        mediaId: String,
        mediaType: String,
        title: String,
        poster: String?,
        background: String?,
        releaseInfo: String?,
        imdbRating: String?,
        status: WatchStatus,
        totalEpisodes: Int = 0,
        watchedCount: Int? = null,
        lastWatchedEpisodeId: String? = null,
        lastWatchedLabel: String? = null
    ): Result<Unit> {
        var docPath = "users/$userId/library/$mediaId"
        return try {
            val uid = requireUserId(userId)
            val validId = sanitizeMediaId(mediaId)
            val normalizedType = if (mediaType == "series") "series" else "movie"
            val safeTitle = title.trim().ifBlank { "Untitled" }.take(200)
            val safeTotal = totalEpisodes.coerceIn(0, 5000).toLong()

            val docRef = db.collection("users").document(uid).collection("library").document(validId)
            docPath = docRef.path

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val initialWatched = (watchedCount ?: if (normalizedType == "movie" && status == WatchStatus.COMPLETED) 1 else 0)
                    .coerceIn(0, 5000).toLong()
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "mediaType" to normalizedType,
                    "title" to safeTitle,
                    "status" to status.name,
                    "totalEpisodes" to safeTotal,
                    "watchedCount" to initialWatched,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                poster?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["poster"] = it.take(500) }
                background?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["background"] = it.take(500) }
                releaseInfo?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["releaseInfo"] = it.take(60) }
                imdbRating?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["imdbRating"] = it.take(16) }
                lastWatchedEpisodeId?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["lastWatchedEpisodeId"] = it.take(128) }
                lastWatchedLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { createPayload["lastWatchedLabel"] = it.take(80) }

                docRef.set(createPayload).await()
            } else {
                val currentWatched = (existing.getLong("watchedCount") ?: 0L).toInt()
                val resolvedWatched = (watchedCount ?: when {
                    normalizedType == "movie" && status == WatchStatus.COMPLETED -> 1
                    normalizedType == "movie" -> 0
                    else -> currentWatched
                }).coerceIn(0, 5000).toLong()

                val updatePayload = mutableMapOf<String, Any>(
                    "title" to safeTitle,
                    "status" to status.name,
                    "totalEpisodes" to safeTotal.coerceAtLeast(existing.getLong("totalEpisodes") ?: 0L),
                    "watchedCount" to resolvedWatched,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                poster?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["poster"] = it.take(500) }
                background?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["background"] = it.take(500) }
                releaseInfo?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["releaseInfo"] = it.take(60) }
                imdbRating?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["imdbRating"] = it.take(16) }
                lastWatchedEpisodeId?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["lastWatchedEpisodeId"] = it.take(128) }
                lastWatchedLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { updatePayload["lastWatchedLabel"] = it.take(80) }

                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFromLibrary(userId: String, mediaId: String): Result<Unit> {
        var docPath = "users/$userId/library/$mediaId"
        return try {
            val uid = requireUserId(userId)
            val validId = sanitizeMediaId(mediaId)
            val libRef = db.collection("users").document(uid).collection("library").document(validId)
            val progRef = db.collection("users").document(uid).collection("progress").document(validId)
            docPath = libRef.path
            libRef.delete().await()
            progRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSeriesEpisodes(
        userId: String,
        mediaId: String,
        title: String,
        poster: String?,
        background: String?,
        releaseInfo: String?,
        imdbRating: String?,
        totalEpisodes: Int,
        newWatchedEpisodeIds: List<String>,
        lastEpisodeId: String?,
        lastSeason: Int,
        lastEpisode: Int,
        lastWatchedLabel: String?
    ): Result<Unit> {
        var docPath = "users/$userId/progress/$mediaId"
        return try {
            val uid = requireUserId(userId)
            val validId = sanitizeMediaId(mediaId)
            val safeWatchedList = newWatchedEpisodeIds
                .distinct()
                .filter { it.isNotBlank() && it.length <= 128 }
                .take(2000)

            val progressRef = db.collection("users").document(uid).collection("progress").document(validId)
            docPath = progressRef.path

            val existingProgress = progressRef.get().await()
            val safeLastSeason = lastSeason.coerceIn(0, 200).toLong()
            val safeLastEpisode = lastEpisode.coerceIn(0, 2000).toLong()

            if (!existingProgress.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "mediaType" to "series",
                    "watchedEpisodeIds" to safeWatchedList,
                    "lastSeason" to safeLastSeason,
                    "lastEpisode" to safeLastEpisode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                lastEpisodeId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    createPayload["lastEpisodeId"] = it.take(128)
                }
                progressRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "watchedEpisodeIds" to safeWatchedList,
                    "lastSeason" to safeLastSeason,
                    "lastEpisode" to safeLastEpisode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                lastEpisodeId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    updatePayload["lastEpisodeId"] = it.take(128)
                }
                progressRef.update(updatePayload).await()
            }

            val computedStatus = when {
                totalEpisodes > 0 && safeWatchedList.size >= totalEpisodes -> WatchStatus.COMPLETED
                safeWatchedList.isNotEmpty() -> WatchStatus.WATCHING
                else -> WatchStatus.WATCHLIST
            }

            upsertLibraryEntry(
                userId = uid,
                mediaId = validId,
                mediaType = "series",
                title = title,
                poster = poster,
                background = background,
                releaseInfo = releaseInfo,
                imdbRating = imdbRating,
                status = computedStatus,
                totalEpisodes = totalEpisodes,
                watchedCount = safeWatchedList.size,
                lastWatchedEpisodeId = lastEpisodeId,
                lastWatchedLabel = lastWatchedLabel
            ).getOrThrow()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
