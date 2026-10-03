/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.monochrome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * Minimal client for the Monochrome lossless API — the public Tidal-catalogue
 * frontend that serves genuine Hi-Res FLAC streams.
 *
 * Endpoints (official instance: [DEFAULT_INSTANCE]):
 *   GET {instance}/search/?s={query}
 *       -> { data: { items: [ { id, title, artist{name}, album{title, cover},
 *                               duration, audioQuality, streamReady, ... } ] } }
 *   GET {instance}/track/?id={id}&quality={quality}
 *       -> { data: { manifest: <base64 JSON { urls[], mimeType, codecs }>,
 *                    bitDepth, sampleRate, audioQuality } }
 *
 * Community API instances are listed in the Monochrome project's
 * INSTANCES.md and can be configured in settings.
 */
object MonochromeAudioProvider {

    const val DEFAULT_INSTANCE = "https://tracks.monochrome.st"

    /** Retired endpoints (Monochrome moved from monochrome.tf to monochrome.st). */
    private val RETIRED_INSTANCES = setOf("https://api.monochrome.tf", "http://api.monochrome.tf")

    /** Maps blank or retired instance URLs (e.g. a saved old default) to the current default. */
    fun effectiveInstance(instanceUrl: String): String {
        val trimmed = instanceUrl.trim().trimEnd('/')
        return if (trimmed.isBlank() || trimmed.lowercase() in RETIRED_INSTANCES) DEFAULT_INSTANCE else trimmed
    }

    private val SNOWFLAKE = Regex("^\\d{15,20}$")

    /** New tracks API (tracks.monochrome.st): GET /search/tracks?q=&limit= -> { tracks: [...] }. */
    private fun searchTracksApi(base: String, query: String): List<MonochromeTrack>? {
        val payload = getJson("$base/search/tracks?q=${URLEncoder.encode(query, "UTF-8")}&limit=8") ?: return null
        val arr = payload.optJSONArray("tracks") ?: return null
        return buildList {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val id = (t.optString("trackId").ifBlank { t.optString("id") }).takeIf { it.isNotBlank() } ?: continue
                val title = t.optString("title").takeIf { it.isNotBlank() } ?: continue
                val names = t.optJSONArray("artistNames")
                val artist = names?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
                add(
                    MonochromeTrack(
                        id = id,
                        title = title,
                        artistName = artist,
                        albumTitle = null,
                        coverUuid = null,
                        durationSec = t.optLong("duration", -1L).takeIf { it > 0 }?.let { (it / 1000).toInt() },
                        audioQuality = "LOSSLESS",
                        streamReady = t.optBoolean("playable", true),
                    ),
                )
            }
        }
    }

    private const val COVER_BASE = "https://resources.tidal.com/images"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val STATUS_TIMEOUT_MS = 5_000

    data class MonochromeTrack(
        val id: String,
        val title: String,
        val artistName: String,
        val albumTitle: String?,
        val coverUuid: String?,
        val durationSec: Int?,
        val audioQuality: String,
        val streamReady: Boolean,
    )

    data class MonochromeStream(
        val url: String,
        val mimeType: String,
        val codecs: String,
        val audioQuality: String,
        val bitDepth: Int?,
        val sampleRate: Int?,
    )

    fun coverUrl(coverUuid: String?): String? =
        coverUuid
            ?.takeIf { it.isNotBlank() }
            ?.let { "$COVER_BASE/${it.replace('-', '/')}/320x320.jpg" }

    suspend fun search(
        instanceUrl: String,
        query: String,
    ): List<MonochromeTrack> =
        withContext(Dispatchers.IO) {
            val instanceUrl = effectiveInstance(instanceUrl)
            runCatching { searchTracksApi(instanceUrl, query) }.getOrNull()?.let { return@withContext it }
            runCatching {
                val payload =
                    getJson(
                        "${instanceUrl.trimEnd('/')}/search/?s=${URLEncoder.encode(query, "UTF-8")}",
                    ) ?: return@runCatching emptyList()
                val items = payload.optJSONObject("data")?.optJSONArray("items")
                    ?: return@runCatching emptyList()
                buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                            ?: continue
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                        val album = item.optJSONObject("album")
                        add(
                            MonochromeTrack(
                                id = id,
                                title = title,
                                artistName = item.optJSONObject("artist")?.optString("name").orEmpty(),
                                albumTitle = album?.optString("title")?.takeIf { it.isNotBlank() },
                                coverUuid = album?.optString("cover")?.takeIf { it.isNotBlank() },
                                durationSec = item.optInt("duration", -1).takeIf { it > 0 },
                                audioQuality = item.optString("audioQuality"),
                                streamReady = item.optBoolean("streamReady", false),
                            ),
                        )
                    }
                }
            }.getOrElse { error ->
                Timber.tag("Monochrome").w(error, "Search failed for %s", query)
                emptyList()
            }
        }

    suspend fun resolveStream(
        instanceUrl: String,
        trackId: String,
        quality: String = "LOSSLESS",
    ): MonochromeStream? =
        withContext(Dispatchers.IO) {
            val instanceUrl = effectiveInstance(instanceUrl)
            // Tracks API ids are snowflakes and stream directly as FLAC from /track/{id}.
            if (SNOWFLAKE.matches(trackId)) {
                return@withContext MonochromeStream(
                    url = "$instanceUrl/track/$trackId",
                    mimeType = "audio/flac",
                    codecs = "flac",
                    audioQuality = "LOSSLESS",
                    bitDepth = null,
                    sampleRate = null,
                )
            }
            runCatching {
                val payload =
                    getJson(
                        "${instanceUrl.trimEnd('/')}/track/?id=${URLEncoder.encode(trackId, "UTF-8")}&quality=$quality",
                    ) ?: return@runCatching null
                val data = payload.optJSONObject("data") ?: return@runCatching null
                val manifestB64 = data.optString("manifest").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val manifest = JSONObject(String(Base64.getDecoder().decode(manifestB64)))
                val url = manifest.optJSONArray("urls")?.optString(0)?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                MonochromeStream(
                    url = url,
                    mimeType = manifest.optString("mimeType", "audio/flac"),
                    codecs = manifest.optString("codecs", "flac"),
                    audioQuality = data.optString("audioQuality", quality),
                    bitDepth = data.optInt("bitDepth", -1).takeIf { it > 0 },
                    sampleRate = data.optInt("sampleRate", -1).takeIf { it > 0 },
                )
            }.getOrElse { error ->
                Timber.tag("Monochrome").w(error, "Stream resolve failed for %s", trackId)
                null
            }
        }

    /** Availability of a Monochrome instance, mirroring how the website surfaces playback status. */
    enum class MonochromeStatus {
        ACTIVE,
        MAINTENANCE,
        DOWN,
        UNKNOWN,
    }

    /**
     * Probes the instance's search endpoint and classifies availability:
     * [MonochromeStatus.ACTIVE] when it answers with payload data,
     * [MonochromeStatus.MAINTENANCE] when the server reports maintenance,
     * [MonochromeStatus.DOWN] when it is unreachable, and [MonochromeStatus.UNKNOWN]
     * for any other answer. Safe to call from the main thread's coroutine.
     */
    suspend fun checkStatus(instanceUrl: String): MonochromeStatus =
        withContext(Dispatchers.IO) {
            val instanceUrl = effectiveInstance(instanceUrl)
            // New tracks API first.
            val newApi =
                runCatching {
                    getJson("$instanceUrl/search/tracks?q=probe&limit=1")?.has("tracks") == true
                }.getOrDefault(false)
            if (newApi) return@withContext MonochromeStatus.ACTIVE
            val probeUrl = "${instanceUrl.trimEnd('/')}/search/?s=probe"
            runCatching {
                val connection = URL(probeUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = STATUS_TIMEOUT_MS
                connection.readTimeout = STATUS_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "LunarTune")
                val code = connection.responseCode
                val body =
                    (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }
                        .orEmpty()
                connection.disconnect()
                val lowered = body.lowercase()
                when {
                    "maintenance" in lowered -> MonochromeStatus.MAINTENANCE
                    code == 503 -> MonochromeStatus.MAINTENANCE
                    code in 200..299 && "\"data\"" in body -> MonochromeStatus.ACTIVE
                    code in 200..299 -> MonochromeStatus.UNKNOWN
                    else -> MonochromeStatus.DOWN
                }
            }.getOrElse { MonochromeStatus.DOWN }
        }

    // The new host intermittently answers 5xx (Cloudflare 521), so retry briefly.
    private fun getJson(url: String): JSONObject? {
        repeat(2) { runCatching { getJsonOnce(url) }.getOrNull()?.let { return it }; Thread.sleep(400) }
        return getJsonOnce(url)
    }

    private fun getJsonOnce(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "LunarTune")
        return try {
            if (connection.responseCode !in 200..299) return null
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
