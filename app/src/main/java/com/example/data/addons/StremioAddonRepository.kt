package com.example.data.addons

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.stremioAddonsStore: DataStore<Preferences> by preferencesDataStore(
    name = "cineboard_stremio_addons"
)

class StremioAddonRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(InstalledAddonsEnvelope::class.java)
    private val keyAddonsJson = stringPreferencesKey("installed_addons_json")

    val installedAddonsFlow: Flow<List<InstalledAddon>> = context.stremioAddonsStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> decodeAddons(prefs[keyAddonsJson]) }

    suspend fun installAddon(rawUrl: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        try {
            val manifestUrl = normalizeManifestUrl(rawUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid manifest URL"))
            val body = executeGet(manifestUrl)
            val json = JSONObject(body)
            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Invalid Stremio manifest"))
            }
            val version = json.optString("version", "1.0.0")
            val description = json.optString("description", "")
            val resources = parseResources(json.optJSONArray("resources"))
            val types = parseStringList(json.optJSONArray("types"))
            val baseUrl = manifestUrl.removeSuffix("/manifest.json").trimEnd('/')

            val addon = InstalledAddon(
                id = id,
                name = name,
                version = version,
                description = description,
                manifestUrl = manifestUrl,
                baseUrl = baseUrl,
                resources = resources,
                types = types,
                enabled = true
            )

            context.stremioAddonsStore.edit { prefs ->
                val list = decodeAddons(prefs[keyAddonsJson]).toMutableList()
                list.removeAll { it.id == addon.id || it.manifestUrl == addon.manifestUrl }
                list.add(0, addon)
                prefs[keyAddonsJson] = adapter.toJson(InstalledAddonsEnvelope(list))
            }

            Result.success(addon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAddon(addonId: String, enabled: Boolean) {
        context.stremioAddonsStore.edit { prefs ->
            val updated = decodeAddons(prefs[keyAddonsJson]).map { addon ->
                if (addon.id == addonId) addon.copy(enabled = enabled) else addon
            }
            prefs[keyAddonsJson] = adapter.toJson(InstalledAddonsEnvelope(updated))
        }
    }

    suspend fun removeAddon(addonId: String) {
        context.stremioAddonsStore.edit { prefs ->
            val updated = decodeAddons(prefs[keyAddonsJson]).filterNot { it.id == addonId }
            prefs[keyAddonsJson] = adapter.toJson(InstalledAddonsEnvelope(updated))
        }
    }

    suspend fun fetchStreamsAndSubtitles(
        mediaType: String,
        streamId: String
    ): AddonStreamFetchResult = withContext(Dispatchers.IO) {
        val enabledAddons = installedAddonsFlow.first().filter { it.enabled && it.supportsType(mediaType) }
        if (enabledAddons.isEmpty()) {
            return@withContext AddonStreamFetchResult()
        }

        val encodedType = Uri.encode(mediaType)
        val encodedId = Uri.encode(streamId)

        coroutineScope {
            val streamAddons = enabledAddons.filter { it.supportsResource("stream") }
            val subtitleAddons = enabledAddons.filter { it.supportsResource("subtitles") }

            val streamTasks = streamAddons.map { addon ->
                async {
                    try {
                        val url = "${addon.baseUrl}/stream/$encodedType/$encodedId.json"
                        val body = executeGet(url)
                        val streams = parseStreams(addon.name, JSONObject(body))
                        Result.success(addon.name to streams)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(AddonException(addon.name, e))
                    }
                }
            }

            val subtitleTasks = subtitleAddons.map { addon ->
                async {
                    try {
                        val url = "${addon.baseUrl}/subtitles/$encodedType/$encodedId.json"
                        val body = executeGet(url)
                        parseSubtitles(addon.name, JSONObject(body))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            val streamResults = streamTasks.awaitAll()
            val subtitleResults = subtitleTasks.awaitAll().flatten()

            val allStreams = mutableListOf<PlayableStream>()
            val failedAddons = mutableListOf<String>()

            streamResults.forEach { res ->
                res.fold(
                    onSuccess = { (_, list) -> allStreams.addAll(list) },
                    onFailure = { err ->
                        val name = (err as? AddonException)?.addonName ?: "Addon"
                        failedAddons.add(name)
                    }
                )
            }

            val sortedStreams = allStreams.sortedWith(
                compareByDescending<PlayableStream> { it.isDirectlyPlayable }
                    .thenByDescending { it.qualityScore }
                    .thenBy { it.addonName }
            )

            AddonStreamFetchResult(
                streams = sortedStreams,
                subtitles = subtitleResults.distinctBy { it.url },
                failedAddons = failedAddons
            )
        }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CineBoard/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseResources(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is String -> if (item.isNotBlank()) result.add(item)
                is JSONObject -> {
                    val name = item.optString("name")
                    if (name.isNotBlank()) result.add(name)
                }
            }
        }
        return result.distinct()
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }

    private fun parseStreams(addonName: String, root: JSONObject): List<PlayableStream> {
        val array = root.optJSONArray("streams") ?: return emptyList()
        val result = mutableListOf<PlayableStream>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val directUrl = obj.optString("url").trim()
            val externalUrl = obj.optString("externalUrl").trim().takeIf { it.isNotBlank() }
            val infoHash = obj.optString("infoHash").trim()
            val title = obj.optString("title").trim()
                .ifBlank { obj.optString("name").trim() }
                .ifBlank { addonName }
            val nameBadge = obj.optString("name").trim()
            val description = obj.optString("description").trim()
                .ifBlank { if (nameBadge.isNotBlank() && nameBadge != title) nameBadge else "" }
                .takeIf { it.isNotBlank() }

            val headers = parseHeaders(obj.optJSONObject("behaviorHints"))
            val embeddedSubs = parseSubtitles(addonName, obj)
            val qualityScore = detectQualityScore("$nameBadge $title ${description.orEmpty()}")

            when {
                directUrl.startsWith("http://", ignoreCase = true) ||
                    directUrl.startsWith("https://", ignoreCase = true) -> {
                    result.add(
                        PlayableStream(
                            id = "${addonName}_$i",
                            addonName = addonName,
                            title = title,
                            description = description,
                            url = directUrl,
                            sourceType = StreamSourceType.ADDON_HTTP,
                            headers = headers,
                            subtitles = embeddedSubs,
                            qualityScore = qualityScore
                        )
                    )
                }
                externalUrl != null || infoHash.isNotBlank() -> {
                    result.add(
                        PlayableStream(
                            id = "${addonName}_ext_$i",
                            addonName = addonName,
                            title = title,
                            description = description,
                            url = "",
                            sourceType = StreamSourceType.ADDON_EXTERNAL,
                            subtitles = embeddedSubs,
                            qualityScore = qualityScore,
                            externalUrl = externalUrl,
                            isTorrentOnly = infoHash.isNotBlank()
                        )
                    )
                }
            }
        }
        return result
    }

    private fun parseSubtitles(addonName: String, root: JSONObject): List<SubtitleTrackItem> {
        val array = root.optJSONArray("subtitles") ?: return emptyList()
        val list = mutableListOf<SubtitleTrackItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                continue
            }
            val lang = obj.optString("lang").trim().ifBlank { "und" }
            val label = obj.optString("label").trim().ifBlank { "$lang ($addonName)" }
            val id = obj.optString("id").trim().ifBlank { "${addonName}_${lang}_$i" }
            list.add(SubtitleTrackItem(id = id, url = url, lang = lang, label = label))
        }
        return list
    }

    private fun parseHeaders(hints: JSONObject?): Map<String, String> {
        if (hints == null) return emptyMap()
        val proxyHeaders = hints.optJSONObject("proxyHeaders") ?: return emptyMap()
        val requestHeaders = proxyHeaders.optJSONObject("request") ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = requestHeaders.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = requestHeaders.optString(k)
            if (k.isNotBlank() && v.isNotBlank()) {
                map[k] = v
            }
        }
        return map
    }

    private fun decodeAddons(raw: String?): List<InstalledAddon> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(raw)?.addons }.getOrNull().orEmpty()
    }

    private fun normalizeManifestUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val httpNormalized = when {
            trimmed.startsWith("stremio://", ignoreCase = true) ->
                "https://" + trimmed.substring("stremio://".length)
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        val cleanNoQuery = httpNormalized.trimEnd('/')
        return if (cleanNoQuery.endsWith("manifest.json", ignoreCase = true)) {
            cleanNoQuery
        } else {
            "$cleanNoQuery/manifest.json"
        }
    }

    private fun detectQualityScore(text: String): Int {
        val lower = text.lowercase()
        return when {
            "2160p" in lower || "4k" in lower || "uhd" in lower -> 2160
            "1440p" in lower || "2k" in lower -> 1440
            "1080p" in lower || "fhd" in lower -> 1080
            "720p" in lower || "hd" in lower -> 720
            "480p" in lower || "sd" in lower -> 480
            "360p" in lower -> 360
            else -> 500
        }
    }

    private class AddonException(val addonName: String, cause: Throwable) : Exception(cause)
}
