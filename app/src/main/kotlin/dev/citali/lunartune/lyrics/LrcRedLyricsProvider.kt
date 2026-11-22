/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import android.util.Log
import dev.citali.lunartune.constants.EnableLrcRedKey
import dev.citali.lunartune.utils.GlobalLog
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * lrc.red lyrics. The search endpoint returns track hits that carry an ISRC, and the
 * matching TTML document is served at /s/{ISRC}.ttml. Hits are matched on title, version
 * words, artist and duration, so a loose search result never becomes the lyrics.
 */
object LrcRedLyricsProvider : LyricsProvider {
    private const val LOG_TAG = "lrc.red"
    private const val BASE_URL = "https://lrc.red"
    private const val USER_AGENT = "LunarTune (https://github.com/cognitiveshadows03/LunarTune)"
    private const val DURATION_TOLERANCE_SECONDS = 3.0

    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")
    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS =
        Regex(
            """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
            RegexOption.IGNORE_CASE,
        )
    private val VERSION_WORDS =
        setOf(
            "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
            "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
            "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
        )

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    /**
     * lrc.red's terms ask every client to identify itself: a User-Agent naming the app with a
     * link and a contact address, and they warn that bulk requests without one can get the
     * caller's address blocked. Both requests carry the same identity.
     */
    private fun HttpRequestBuilder.identityHeaders() {
        header(HttpHeaders.UserAgent, USER_AGENT)
    }

    private data class Hit(
        val isrc: String?,
        val title: String?,
        val artist: String?,
        val duration: Double?,
    )

    override val name = "lrc.red"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcRedKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        runCatching {
            fetchLyrics(title, artist, duration)
                ?: throw IllegalStateException("No lrc.red lyrics for $title — $artist")
        }

    private suspend fun fetchLyrics(
        title: String,
        artist: String,
        duration: Int,
    ): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        return try {
            val hit = search(cleanTitle, cleanArtist, duration) ?: return null
            val isrc = hit.isrc ?: return null
            val response =
                client.get("$BASE_URL/s/$isrc.ttml") {
                    identityHeaders()
                }
            if (response.status.value !in 200..299) {
                GlobalLog.append(Log.INFO, LOG_TAG, "document request failed with status ${response.status.value}")
                null
            } else {
                decode(response.bodyAsText())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            GlobalLog.append(Log.WARN, LOG_TAG, "error fetching lyrics: ${e.message}")
            null
        }
    }

    private suspend fun search(
        title: String,
        artist: String,
        duration: Int,
    ): Hit? {
        val response =
            client.get("$BASE_URL/search.json") {
                identityHeaders()
                parameter("q", "$title $artist")
            }
        if (response.status.value !in 200..299) return null
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        val hits = root["hits"] as? JsonArray ?: return null
        val parsed =
            hits.mapNotNull { element ->
                val hit = element as? JsonObject ?: return@mapNotNull null
                Hit(
                    isrc = hit.text("isrc")?.trim()?.uppercase(Locale.ROOT),
                    title = hit.text("title"),
                    artist = hit.text("artist"),
                    duration = hit["duration"]?.let { (it as? JsonPrimitive)?.doubleOrNull },
                )
            }
        return best(parsed, title, artist, duration)
    }

    private fun best(
        hits: List<Hit>,
        title: String,
        artist: String,
        duration: Int,
    ): Hit? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)
        return hits
            .filter { hit ->
                val name = hit.title ?: return@filter false
                val hitDuration = hit.duration
                ISRC.matches(hit.isrc.orEmpty()) &&
                    coreOf(name) == wantedTitle &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                    (duration <= 0 || hitDuration == null || abs(hitDuration - duration) <= DURATION_TOLERANCE_SECONDS)
            }.minByOrNull { hit ->
                val hitDuration = hit.duration
                if (duration > 0 && hitDuration != null) abs(hitDuration - duration) else 0.0
            }
    }

    private fun decode(body: String): String? {
        val raw = body.removePrefix("\uFEFF").trim()
        if (raw.startsWith("<")) {
            return raw.takeIf { it.contains("<tt", ignoreCase = true) || it.startsWith("<?xml") }
        }
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        return root.text("ttml")?.takeIf { it.contains("<tt", ignoreCase = true) }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    private fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } + " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    private fun normalized(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()
}
