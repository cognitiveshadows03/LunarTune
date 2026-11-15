/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.util.Log
import dev.citali.lunartune.utils.GlobalLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Direct client for the Better Lyrics API.
 *
 * Replaces the bundled SDK for this provider. The SDK is fine, but it is pinned to the
 * old host and it cannot see HTTP status, which is the one thing that matters now: the
 * API has moved to a cache-first model where a *cache hit* is free and any *cache miss*
 * needs an API key that is currently not being issued. Roughly half of a request's
 * usefulness therefore depends on the exact query, and the whole request budget depends
 * on noticing a 401 quickly.
 *
 * Three things follow from that, all measured against the live API:
 *
 * 1. **Album is tried second.** The docs say to always send `al` because the cache key
 *    includes it, while measurement says the opposite for some entries (`Tum Hi Ho` and
 *    `Shape of You` hit bare and miss with `al`). Both are true - the cache key contains the
 *    album, so an entry stored with one is invisible to a query without it, and vice versa.
 *    The query therefore goes out in the order least likely to waste a request and retries
 *    the other way only after a 401: bare first with no key (a bare hit is the common case),
 *    album first with a key (a miss is then a real fetch, and the album is what makes it
 *    accurate). A retry costs one request, and only on a miss.
 * 2. **Duration is always sent when known.** Duration is tolerated within a small
 *    tolerance (200 and 201 both hit), and having it present is what lets the server
 *    match an entry at all in most cases.
 * 3. **A 401 pauses only that endpoint.** Each endpoint has its own cache, so a miss on
 *    `getLyrics` says nothing about `qq/getLyrics`. Backing off the whole host (or breaking
 *    out of the loop) silently dropped cached results from the other two endpoints.
 */
internal object BetterLyricsClient {
    private const val LOG_TAG = "BetterLyrics"

    /** Current documented host. The old lyrics-api.boidu.dev still answers but is legacy. */
    private const val BASE_URL = "https://api.betterlyrics.org/"

    private const val LYRICS_PATH = "getLyrics"
    private const val KUGOU_PATH = "kugou/getLyrics"
    private const val PORTATO_PATH = "qq/getLyrics"

    private const val MAX_UNWRAP_DEPTH = 2

    /** After a 401, how long to stop asking. Cached entries are added over time. */
    private const val AUTH_BACKOFF_MS = 10 * 60 * 1000L

    /** Per-endpoint 401 backoff: one cache miss must not silence the other endpoints. */
    private val blockedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Optional key for uncached queries. Setting or clearing it lifts the backoff so the new
     * credential is tried immediately. Null/blank keeps the current cache-only behaviour.
     */
    @Volatile
    var apiKey: String? = null
        set(value) {
            val normalized = value?.trim()?.takeIf { it.isNotBlank() }
            if (field != normalized) {
                field = normalized
                // A new credential deserves an immediate retry, but re-applying the
                // same value (e.g. every settings recomposition) must not lift the backoff.
                blockedUntil.clear()
            }
        }

    private val allEndpoints = listOf(LYRICS_PATH, KUGOU_PATH, PORTATO_PATH)

    /** The QQ/Portato endpoint on its own, for the provider that exists just for it. */
    val portatoEndpoints: List<String> = listOf(PORTATO_PATH)

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            // Handle non-2xx ourselves: a 401 is information, not an exception.
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    private fun isBlocked(endpoint: String): Boolean =
        (blockedUntil[endpoint] ?: 0L) > System.currentTimeMillis()

    /**
     * Fetch every result the API will give for a track.
     *
     * Returns the decoded lyric payloads in endpoint order; empty when nothing is
     * available. Failures are logged and swallowed on purpose - this provider is one
     * entry in a chain, and a lyrics miss must never propagate as a crash.
     */
    suspend fun fetch(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int,
        endpoints: List<String> = allEndpoints,
    ): List<String> {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanAlbum = album?.trim()?.takeIf { it.isNotBlank() }
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        for (endpoint in endpoints) {
            if (isBlocked(endpoint)) continue
            val lyrics = fetchEndpoints(endpoint, cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
            if (lyrics != null) results += lyrics
            // Keep going: the next endpoint maintains its own cache and may still have a hit.
        }
        return results
    }

    /**
     * One endpoint, asked at most twice.
     *
     * The two queries differ only in whether the album rides along, and the order follows
     * [fetch]'s reasoning: the cheap/likely one first, the other only after a 401. A 401 on
     * every attempt is what pauses the endpoint; a 200 with an unparseable body just moves to
     * the next attempt, because a different cache entry can decode where another does not.
     */
    private suspend fun fetchEndpoints(
        endpoint: String,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): String? {
        val attempts = if (apiKey == null) listOf(null, album) else listOf(album, null)
        var sawUnauthorized = false
        // A blank album makes both attempts identical, so run it once.
        for (attemptAlbum in attempts.distinct()) {
            try {
                GlobalLog.append(
                    Log.INFO,
                    LOG_TAG,
                    "Sending request to $BASE_URL$endpoint (s=$title, a=$artist, " +
                        "al=${attemptAlbum ?: "-"}, d=$durationSeconds)",
                )

                val response =
                    client.get(BASE_URL + endpoint) {
                        parameter("s", title)
                        parameter("a", artist)
                        attemptAlbum?.let { parameter("al", it) }
                        if (durationSeconds > 0) parameter("d", durationSeconds)
                        apiKey?.let { header("X-API-Key", it) }
                    }

                val status = response.status.value
                GlobalLog.append(Log.INFO, LOG_TAG, "$endpoint response status: $status")

                if (status == 401) {
                    sawUnauthorized = true
                    continue
                }
                if (status !in 200..299) {
                    GlobalLog.append(Log.INFO, LOG_TAG, "$endpoint request failed with status: $status")
                    return null
                }

                val payload = decode(response.bodyAsText())
                if (payload != null) return payload
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                GlobalLog.append(Log.WARN, LOG_TAG, "$endpoint error fetching lyrics: ${e.message}")
                return null
            }
        }

        if (sawUnauthorized) {
            blockedUntil[endpoint] = System.currentTimeMillis() + AUTH_BACKOFF_MS
            GlobalLog.append(
                Log.WARN,
                LOG_TAG,
                "$endpoint is serving cached lyrics only; pausing that endpoint for " +
                    "${AUTH_BACKOFF_MS / 60_000} minutes (the other endpoints stay live)",
            )
        }
        return null
    }

    /**
     * Unwrap a response to the lyric payload.
     *
     * The API returns either raw TTML, or JSON that nests the payload under one of a few
     * keys - and older endpoints nest it twice as a JSON string inside JSON.
     */
    private fun decode(body: String): String? {
        val raw = body.removePrefix("\uFEFF")
        if (isLyricPayload(raw)) return raw

        val root =
            runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return unwrap(root, depth = 0)
    }

    private fun unwrap(
        element: JsonElement,
        depth: Int,
    ): String? {
        if (depth > MAX_UNWRAP_DEPTH) return null
        return when (element) {
            is JsonObject -> {
                val payload =
                    element["ttml"]
                        ?: element["lyrics"]
                        ?: element["data"]
                        ?: element["result"]
                        ?: element["response"]
                        ?: return null
                unwrap(payload, depth + 1)
            }

            is JsonPrimitive -> {
                val content = element.contentOrNull ?: return null
                if (isLyricPayload(content)) {
                    content
                } else {
                    val nested =
                        runCatching { json.parseToJsonElement(content) }.getOrNull() ?: return null
                    unwrap(nested, depth + 1)
                }
            }

            else -> null
        }
    }

    private fun isLyricPayload(text: String): Boolean {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("<")) {
            // TTML, or the QRC document the QQ endpoint returns.
            return trimmed.contains("<tt", ignoreCase = true) ||
                trimmed.contains("QrcInfos", ignoreCase = true) ||
                trimmed.startsWith("<?xml")
        }
        return trimmed.startsWith("[") && trimmed.contains("[")
    }
}
