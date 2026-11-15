/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import android.util.Log
import dev.citali.lunartune.constants.EnableSimpMusicLyricsKey
import dev.citali.lunartune.utils.GlobalLog
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * SimpMusic lyrics, fetched here rather than through the bundled client.
 *
 * The bundled client answers with the *line-synced* text and leaves the payload's
 * `richSyncLyrics` on the floor. That field is enhanced LRC - a line timestamp with a
 * `<mm:ss.xx>` in front of every word - which is exactly what this app's parser already turns
 * into word-by-word highlighting. So a provider that advertises rich sync was delivering
 * karaoke as plain lines, and the fix is not a new source: it is reading the field that was
 * already arriving.
 *
 * Three things this provider therefore does differently, all measured against the live API:
 *
 * 1. **Rich sync first,** then line sync, then plain text. A missing rich version just falls
 *    through; it never blocks the other two.
 * 2. **The client user agent is mandatory.** The API answers a generic client with
 *    `403 "This API is only available to SimpMusic clients"`, so a plain request looks
 *    identical to "no lyrics for this song" - which is how a working provider can appear dead.
 * 3. **Tracks are picked by duration.** The endpoint returns every submitted version of a
 *    song; taking the first one can pair the right words with the wrong recording.
 */
object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicLyricsKey] ?: true

    private const val LOG_TAG = "SimpMusic"
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
    private const val USER_AGENT = "SimpMusicLyrics/1.0"

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            // Non-2xx is information here: a 403 means the user agent was rejected.
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    private data class Track(
        val durationSeconds: Int?,
        val richSync: String?,
        val synced: String?,
        val plain: String?,
    ) {
        /** The best version this track offers, richest first. */
        val best: String?
            get() =
                richSync?.takeIf { it.isNotBlank() }
                    ?: synced?.takeIf { it.isNotBlank() }
                    ?: plain?.takeIf { it.isNotBlank() }
    }

    private suspend fun tracksFor(videoId: String): List<Track> =
        try {
            val response =
                client.get(BASE_URL + videoId) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                }
            val status = response.status.value
            if (status !in 200..299) {
                GlobalLog.append(Log.WARN, LOG_TAG, "$videoId returned status $status")
                return emptyList()
            }
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            root
                ?.get("data")
                ?.jsonArray
                ?.mapNotNull { element ->
                    val entry = element as? JsonObject ?: return@mapNotNull null
                    Track(
                        durationSeconds = entry["durationSeconds"]?.jsonPrimitive?.intOrNull,
                        richSync = entry["richSyncLyrics"]?.jsonPrimitive?.contentOrNull,
                        synced = entry["syncedLyrics"]?.jsonPrimitive?.contentOrNull,
                        plain = entry["plainLyric"]?.jsonPrimitive?.contentOrNull,
                    ).takeIf { it.best != null }
                }.orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            GlobalLog.append(Log.WARN, LOG_TAG, "Lookup for $videoId failed: ${e.message}")
            emptyList()
        }

    private fun bestFor(
        tracks: List<Track>,
        duration: Int,
    ): Track? =
        if (duration > 0 && tracks.size > 1) {
            tracks.minByOrNull { track -> abs((track.durationSeconds ?: 0) - duration) }
        } else {
            tracks.firstOrNull()
        }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val lyrics = bestFor(tracksFor(id), duration)?.best
        return if (lyrics != null) {
            Result.success(lyrics)
        } else {
            Result.failure(IllegalStateException("Lyrics unavailable"))
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        // Every submitted version, richest first, so the picker shows real alternatives
        // instead of the same song twice - one with words and one without.
        tracksFor(id)
            .sortedByDescending { track -> if (track.richSync.isNullOrBlank()) 0 else 1 }
            .forEach { track ->
                track.best?.let(callback)
            }
    }
}
