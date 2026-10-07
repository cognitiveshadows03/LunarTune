/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playlistimport

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleMusicPlaylistRepository
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(20, TimeUnit.SECONDS)
                        writeTimeout(15, TimeUnit.SECONDS)
                        retryOnConnectionFailure(false)
                    }
                }
            }

        internal suspend fun fetchPlaylist(shareUrl: String): AppleMusicPlaylist {
            val requestUrl = normalizeAppleMusicPlaylistUrl(shareUrl)
            val response =
                client.get(requestUrl) {
                    headers {
                        append(HttpHeaders.UserAgent, APPLE_MUSIC_USER_AGENT)
                        append(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                    }
                }
            if (response.status.value !in 200..299) {
                throw IOException("Apple Music couldn't load that playlist. Check the link and try again.")
            }

            return parseAppleMusicPlaylistPage(response.bodyAsText())
        }

        private companion object {
            const val APPLE_MUSIC_USER_AGENT =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
    }

internal data class AppleMusicPlaylist(
    val title: String,
    val artworkUrl: String?,
    val tracks: List<AppleMusicTrack>,
)

internal data class AppleMusicTrack(
    val id: String?,
    val title: String,
    val artists: List<String>,
    val durationSeconds: Int,
    val artworkUrl: String?,
)

internal fun normalizeAppleMusicPlaylistUrl(value: String): String {
    val uri =
        runCatching { URI(value.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Enter an Apple Music playlist link.")
    val host = uri.host?.lowercase(java.util.Locale.ROOT)
    require(uri.scheme.equals("https", ignoreCase = true)) {
        "Apple Music links must use HTTPS."
    }
    require(host != null && host in APPLE_MUSIC_HOSTS && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)) {
        "Use a link from music.apple.com or itunes.apple.com."
    }

    val pathSegments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    require(pathSegments.any { it.equals("playlist", ignoreCase = true) } && pathSegments.any { it.startsWith("pl.") }) {
        "That link doesn't look like an Apple Music playlist."
    }

    // Ignore tracking and selected-track query parameters; the transfer always imports the playlist.
    val validHost = requireNotNull(host)
    val rawPath = requireNotNull(uri.rawPath)
    val normalizedHost = if (validHost == "www.music.apple.com") "music.apple.com" else validHost
    return "https://$normalizedHost$rawPath"
}

internal fun parseAppleMusicPlaylistPage(html: String): AppleMusicPlaylist {
    val document = Jsoup.parse(html)
    val json = Json { ignoreUnknownKeys = true }
    val schemaElement = document.getElementById("schema:music-playlist")
    val schema =
        schemaElement
            ?.data()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { raw -> runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() }

    val serializedServerData =
        document
            .getElementById("serialized-server-data")
            ?.data()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    val pageTracks =
        serializedServerData
            ?.let { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
            ?.let(::tracksFromApplePageModel)
            .orEmpty()

    val schemaTracks = schema?.get("track")?.let(::tracksFromSchema).orEmpty()
    val tracks = pageTracks.ifEmpty { schemaTracks }
    if (tracks.isEmpty()) {
        throw IOException("No playlist tracks were found. The playlist may be private or unavailable to share.")
    }

    val title =
        schema?.stringValue("name")
            ?: document.title().substringBefore(" - Playlist").trim().takeIf(String::isNotBlank)
            ?: "Apple Music playlist"
    val artworkUrl =
        document
            .selectFirst("meta[property=og:image]")
            ?.attr("content")
            ?.takeIf(String::isNotBlank)

    return AppleMusicPlaylist(
        title = title,
        artworkUrl = artworkUrl,
        tracks = tracks,
    )
}

private fun tracksFromApplePageModel(root: JsonElement): List<AppleMusicTrack> {
    val tracks = mutableListOf<AppleMusicTrack>()

    fun visit(element: JsonElement) {
        when (element) {
            is JsonArray -> element.forEach(::visit)
            is JsonObject -> {
                if (element.stringValue("itemKind") == "trackLockup") {
                    element["items"]
                        ?.let { items -> runCatching { items.jsonArray }.getOrNull() }
                        ?.forEach { item ->
                            parseApplePageTrack(item)?.let(tracks::add)
                        }
                }
                element.values.forEach(::visit)
            }

            else -> Unit
        }
    }

    visit(root)
    return tracks
}

private fun parseApplePageTrack(element: JsonElement): AppleMusicTrack? {
    val track = element as? JsonObject ?: return null
    val descriptor = track["contentDescriptor"] as? JsonObject ?: return null
    if (descriptor.stringValue("kind") != "song") return null

    val title = track.stringValue("title")?.trim()?.takeIf(String::isNotBlank) ?: return null
    val descriptorIdentifiers = descriptor["identifiers"] as? JsonObject
    val id = descriptorIdentifiers?.stringValue("storeAdamID")
    val artistsFromLinks =
        track["subtitleLinks"]
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { link -> (link as? JsonObject)?.stringValue("title")?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()
    val artists =
        artistsFromLinks.ifEmpty {
            track.stringValue("artistName")
                ?.split(" & ")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                .orEmpty()
        }
    val durationPrimitive = track["duration"]?.jsonPrimitive
    val durationMillis = durationPrimitive?.longOrNull ?: durationPrimitive?.doubleOrNull?.toLong() ?: 0L
    val artwork =
        (track["artwork"] as? JsonObject)
            ?.get("dictionary")
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.stringValue("url")
            ?.let(::expandAppleArtworkUrl)

    return AppleMusicTrack(
        id = id,
        title = title,
        artists = artists,
        durationSeconds = (durationMillis / 1_000L).coerceAtLeast(0L).toInt(),
        artworkUrl = artwork,
    )
}

private fun tracksFromSchema(element: JsonElement): List<AppleMusicTrack> {
    val entries =
        when (element) {
            is JsonArray -> element
            else -> JsonArray(listOf(element))
        }

    return entries.mapNotNull { entry ->
        val track = entry as? JsonObject ?: return@mapNotNull null
        val title = track.stringValue("name")?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val songUrl = track.stringValue("url")
        val id = songUrl?.substringAfterLast('/')?.takeIf { it.all(Char::isDigit) }
        val audio = track["audio"] as? JsonObject
        val image = audio?.stringValue("thumbnailUrl")
        val duration =
            (track.stringValue("duration") ?: audio?.stringValue("duration"))
                ?.let(::parseIsoDurationSeconds)
                ?: 0

        AppleMusicTrack(
            id = id,
            title = title,
            artists = emptyList(),
            durationSeconds = duration,
            artworkUrl = image,
        )
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)

private fun expandAppleArtworkUrl(value: String): String =
    value
        .replace("{w}", "640")
        .replace("{h}", "640")
        .replace("{f}", "jpg")

private fun parseIsoDurationSeconds(value: String): Int =
    runCatching {
        java.time.Duration.parse(value).seconds.coerceAtLeast(0).toInt()
    }.getOrDefault(0)

private val APPLE_MUSIC_HOSTS =
    setOf(
        "music.apple.com",
        "www.music.apple.com",
        "itunes.apple.com",
        "www.itunes.apple.com",
    )
