/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/** jiosaavn's catalog as plain aac files up to 320 kbps, no account and no drm; it has no lossless and misses many big international labels */
class JioSaavnModule : SourceModule {

    override val info = SourceModuleInfo(
        id = Id,
        name = "JioSaavn",
        version = "1.0",
        description = "JioSaavn's catalog in AAC up to 320 kbps, no account needed. Strongest on Indian music",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, SaavnSong>()
    private val misses = ConcurrentHashMap<String, Long>()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(Option320, "AAC 320", "JioSaavn's best, 160 for the songs that don't have it"),
                StreamOption(Option160, "AAC 160", "Half the data, still clear"),
                StreamOption(Option96, "AAC 96", "For slow or metered networks"),
            ),
        ),
    )

    // the same short pause monochrome takes: a server that didn't answer is left alone for a bit instead of costing every song a timeout
    override fun accountState(): AccountState {
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override suspend fun verifyAccount(): AccountState {
        val response = runCatching { host.http(HttpRequest(searchUrl("test", 1), headers = headers)) }.getOrNull()
        return if (response?.ok == true) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · server reachable")
        } else {
            val reason = "JioSaavn didn't answer (${response?.code ?: "offline"})"
            markUnreachable(reason)
            AccountState.Expired(reason)
        }
    }

    /** true while a recent failure still stands: playback is not held up by it */
    private fun isCoolingDown(): Boolean {
        if (host.storage.get("unreachable") == null) return false
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: return false
        return System.currentTimeMillis() - markedAt < SkipAfterFailureMs
    }

    override fun lastStreamLabel(): String? = host.storage.get(LastQualityKey)

    override fun clearLastStream() = host.storage.set(LastQualityKey, null)

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        if (isCoolingDown()) {
            report(DiagnosticStep("Find the song", null, "JioSaavn didn't answer a moment ago"))
            return ResolveResult.Unavailable("JioSaavn didn't answer a moment ago")
        }
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on JioSaavn"))
            return ResolveResult.NotFound("Not on JioSaavn")
        }

        report(DiagnosticStep("Find the song", null, "Searching JioSaavn…"))
        val song = matches[key] ?: run {
            val query = "${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim()
            val response = runCatching { host.http(HttpRequest(searchUrl(query, 10), headers = headers)) }.getOrElse {
                report(DiagnosticStep("Find the song", false, "JioSaavn didn't answer"))
                markUnreachable("JioSaavn didn't answer")
                return ResolveResult.Unavailable("JioSaavn didn't answer")
            }
            if (!response.ok) {
                report(DiagnosticStep("Find the song", false, "JioSaavn answered ${response.code}"))
                return ResolveResult.Unavailable("JioSaavn answered ${response.code}")
            }
            host.storage.set("unreachable", null)
            val songs = songsIn(response.text)
            val best = TrackMatcher.ranked(track, songs.map { it.catalog }).firstOrNull()
                ?.let { scored -> songs.first { it.catalog === scored.track } }
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on JioSaavn")
            }
            matches[key] = best
            best
        }
        report(DiagnosticStep("Find the song", true, "${song.catalog.title} · ${song.catalog.artist}"))

        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val given = decryptMediaUrl(song.encryptedUrl)
        if (given == null) {
            report(DiagnosticStep("Stream", false, "JioSaavn's link couldn't be read"))
            return ResolveResult.Unavailable("JioSaavn's link couldn't be read")
        }
        val wanted = when (picks[ChoiceQuality] ?: Option320) {
            Option96 -> 96
            Option160 -> 160
            else -> if (song.has320) 320 else 160
        }
        val stream = streamAt(given, wanted)
        val qualityLabel = "AAC ${stream.kbps ?: "?"} kbps"
        host.storage.set(LastQualityKey, qualityLabel)
        report(DiagnosticStep("Stream", true, qualityLabel))

        return ResolveResult.Found(
            SourceStream(
                url = stream.url,
                format = StreamFormat.Progressive,
                headers = streamHeaders,
                sourceLabel = "JioSaavn",
                qualityLabel = qualityLabel,
                codec = "AAC",
                sampleRateHz = 44_100,
                bitrateBps = stream.kbps?.times(1000),
                contentLengthBytes = stream.totalBytes,
                matched = MatchedTrack(
                    id = song.catalog.id,
                    title = song.catalog.title,
                    artist = song.catalog.artist,
                    album = song.catalog.album,
                    durationMs = song.catalog.durationMs,
                    confidence = TrackMatcher.score(track, song.catalog),
                ),
            )
        )
    }

    /** a song from the search, with what's needed to stream it */
    internal class SaavnSong(val catalog: CatalogTrack, val encryptedUrl: String, val has320: Boolean)

    internal class SaavnStream(val url: String, val kbps: Int?, val totalBytes: Long? = null)

    internal fun songsIn(text: String): List<SaavnSong> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            val more = song["more_info"] as? JsonObject ?: return@mapNotNull null
            val encrypted = more.string("encrypted_media_url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artists = ((more["artistMap"] as? JsonObject)?.get("primary_artists") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.string("name")?.let(::unescape) }
                .orEmpty()
            SaavnSong(
                catalog = CatalogTrack(
                    id = song.string("id") ?: return@mapNotNull null,
                    title = song.string("title")?.let(::unescape) ?: return@mapNotNull null,
                    artist = artists.joinToString(", "),
                    album = more.string("album")?.let(::unescape),
                    durationMs = more.string("duration")?.toLongOrNull()?.times(1000),
                ),
                encryptedUrl = encrypted,
                has320 = more.string("320kbps").equals("true", ignoreCase = true),
            )
        }
    }

    /** the link jiosaavn hides behind des, the same fixed key its apps use */
    internal fun decryptMediaUrl(encrypted: String): String? = runCatching {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(MediaKey.toByteArray(), "DES"))
        cipher.doFinal(Base64.Mime.decode(encrypted.trim())).decodeToString().trim()
    }.getOrNull()?.takeIf { it.startsWith("http") }

    // the link names one bitrate's file, the others sit beside it under the same name; query parameters after it are kept
    internal fun streamAt(given: String, kbps: Int): SaavnStream {
        val rendition = RenditionPattern.find(given) ?: return SaavnStream(given, null)
        val offered = rendition.groupValues[1].toInt()
        if (offered == kbps) return SaavnStream(given, offered)
        val url = given.replaceRange(rendition.range, "_$kbps.${rendition.groupValues[2]}")
        // a file the cdn doesn't have would fail mid-queue, so it's checked with one tiny
        // request first - which also reports the file's real size
        val probe = runCatching { host.http(HttpRequest(url, headers = streamHeaders + ("Range" to "bytes=0-1"))) }.getOrNull()
        val totalBytes =
            probe?.header("content-range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                ?: probe?.header("content-length")?.trim()?.toLongOrNull()
        return if (probe?.ok == true) SaavnStream(url, kbps, totalBytes) else SaavnStream(given, offered)
    }

    private fun markUnreachable(reason: String) {
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun searchUrl(query: String, count: Int) =
        "$Api?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=android&p=1&n=$count&q=${enc(query)}"

    // titles come html-escaped ("Tum Hi Ho (From &quot;Aashiqui 2&quot;)")
    private fun unescape(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    companion object {
        const val Id = "jiosaavn"
        const val ChoiceQuality = "quality"
        const val Option320 = "320"
        const val Option160 = "160"
        const val Option96 = "96"
        private const val Api = "https://www.jiosaavn.com/api.php"
        private const val LastQualityKey = "last_quality"
        private const val MediaKey = "38346591"
        private const val MissMemoryMs = 30L * 60 * 1000
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val SkipAfterFailureMs = 60L * 1000
        private val RenditionPattern = Regex("_(12|48|96|160|320)\\.(mp4|m4a|aac|mp3)(?=[?#]|$)", RegexOption.IGNORE_CASE)
        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        // the explicit cookie lets the uncensored versions show up next to the clean ones
        private val headers = mapOf("Accept" to "application/json", "User-Agent" to UserAgent, "Cookie" to "explicit_content=1")
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
    }
}
