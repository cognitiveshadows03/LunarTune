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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

// lossless flac from monochrome's own track streamer (tracks.monochrome.st)
class MonochromeModule : SourceModule {

    override val info = SourceModuleInfo(
        id = Id,
        name = "Monochrome",
        version = "2.1",
        description = "Lossless FLAC for every song Monochrome has, no account needed",
        sourceUrl = "https://github.com/monochrome-music/monochrome",
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, CatalogTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    override val choices: List<ModuleChoice> = emptyList()

    /** the streamer's address is the one thing that varies between installs */
    override val fields: List<ModuleField> =
        listOf(
            ModuleField(
                id = ServerField,
                label = "Server",
                kind = FieldKind.Text,
                default = DefaultServer,
                hint = DefaultServer,
                description = "Point Monochrome at a mirror if the default one is blocked or slow.",
            ),
        )

    override fun fieldValue(field: ModuleField): String =
        if (field.id != ServerField) {
            field.default
        } else {
            host.storage.get(ServerField)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
                ?: DefaultServer
        }

    override fun setFieldValue(field: ModuleField, value: String?) {
        if (field.id != ServerField) return
        val cleaned = value?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        // the default is not worth storing, and an empty box means "go back to it"
        host.storage.set(ServerField, cleaned?.takeIf { it != DefaultServer })
        // a different server has a different catalog and its own idea of being down
        matches.clear()
        misses.clear()
        clearDown()
    }

    /** whichever server the user settled on */
    private val server: String
        get() {
            val custom = host.storage.get(ServerField)?.trim()?.trimEnd('/')
            return custom?.takeIf { it.isNotBlank() } ?: DefaultServer
        }

    // a failed server is only left alone for a while, since playback passes over a module that isn't signed in and nothing would check it again
    override fun accountState(): AccountState {
        val reason = host.storage.get(DownKey) ?: return AccountState.SignedIn(NO_ACCOUNT_LABEL)
        val markedAt = host.storage.get(DownAtKey)?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) AccountState.Expired(reason) else AccountState.SignedIn(NO_ACCOUNT_LABEL)
    }

    override suspend fun verifyAccount(): AccountState {
        val response = host.httpOrNull(searchRequest("test", 1))
        return if (response?.ok == true) {
            clearDown()
            AccountState.SignedIn("$NO_ACCOUNT_LABEL · server reachable")
        } else {
            val reason = "Monochrome didn't answer (${response?.code ?: "offline"})"
            markDown(reason)
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        val key = "${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on Monochrome"))
            return ResolveResult.NotFound("Not on Monochrome")
        }

        report(DiagnosticStep("Find the song", null, "Searching Monochrome…"))
        val found = matches[key] ?: run {
            val query = "${track.artists.firstOrNull().orEmpty()} ${TrackMatcher.core(track.title)}".trim()
            val response = host.httpOrNull(searchRequest(query, SearchLimit)) ?: run {
                report(DiagnosticStep("Find the song", false, "Monochrome didn't answer"))
                markDown("Monochrome didn't answer")
                return ResolveResult.Unavailable("Monochrome didn't answer")
            }
            if (!response.ok) {
                report(DiagnosticStep("Find the song", false, "Monochrome answered ${response.code}"))
                return ResolveResult.Unavailable("Monochrome answered ${response.code}")
            }
            clearDown()
            val best = TrackMatcher.ranked(track, tracksIn(response.text)).firstOrNull()
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on Monochrome")
            }
            matches[key] = best.track
            best.track
        }
        report(DiagnosticStep("Find the song", true, "${found.title} · ${found.artist}"))

        // the head of the file: it should open as flac, and its streaminfo says how fine it is
        report(DiagnosticStep("Stream", null, "Opening the stream…"))
        val url = "$server/track/${found.id}"
        val opened = host.httpOrNull(HttpRequest(url, headers = streamHeaders + HeadRange))?.takeIf { it.ok }
        if (opened == null) {
            report(DiagnosticStep("Stream", false, "Monochrome gave no stream"))
            return ResolveResult.Unavailable("Monochrome gave no stream")
        }
        val info = flacInfo(opened.body)
        if (info == null) {
            report(DiagnosticStep("Stream", false, "Not a FLAC file"))
            return ResolveResult.Unavailable("Monochrome's stream isn't FLAC")
        }
        report(DiagnosticStep("Stream", true, info.label))

        return ResolveResult.Found(
            SourceStream(
                url = url,
                format = StreamFormat.Progressive,
                headers = streamHeaders,
                sourceLabel = "Monochrome",
                qualityLabel = info.shortLabel,
                codec = "FLAC",
                sampleRateHz = info.sampleRate,
                matched = MatchedTrack(
                    id = found.id,
                    title = found.title,
                    artist = found.artist,
                    album = found.album,
                    durationMs = found.durationMs,
                    confidence = TrackMatcher.score(track, found),
                ),
            )
        )
    }

    internal fun tracksIn(text: String): List<CatalogTrack> {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val tracks = root["tracks"] as? JsonArray ?: return emptyList()
        return tracks.mapNotNull { element ->
            val t = element as? JsonObject ?: return@mapNotNull null
            if ((t["playable"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val id = t.string("trackId") ?: t.string("id") ?: return@mapNotNull null
            val artists = (t["artistNames"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            // the streamer gives milliseconds; older answers gave seconds
            val duration = (t["duration"] as? JsonPrimitive)?.longOrNull?.let { if (it > 10_000) it else it * 1000 }
            CatalogTrack(
                id = id,
                title = t.string("title") ?: return@mapNotNull null,
                artist = artists.joinToString(", "),
                album = t.string("albumTitle") ?: t.string("releaseTitle"),
                durationMs = duration,
                isrc = t.string("isrc"),
            )
        }
    }

    private fun searchRequest(query: String, limit: Int) =
        HttpRequest("$server/search/tracks?q=${enc(query)}&limit=$limit", headers = headers)

    private fun markDown(reason: String) {
        host.storage.set(DownKey, reason)
        host.storage.set(DownAtKey, System.currentTimeMillis().toString())
    }

    private fun clearDown() = host.storage.set(DownKey, null)

    /** what a FLAC file's STREAMINFO says: its bit depth and sample rate */
    internal class FlacInfo(val bitsPerSample: Int, val sampleRate: Int) {
        val hiRes: Boolean get() = bitsPerSample > 16 || sampleRate > 48_000
        private val rate: String get() = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else String.format("%.1f", sampleRate / 1000.0)
        val shortLabel: String get() = if (hiRes) "Hi-Res FLAC" else "FLAC"
        val label: String get() = "$shortLabel · $bitsPerSample-bit / $rate kHz"
    }

    /**
     * "fLaC", then the STREAMINFO block: 4 bytes of header, then (after the block and frame
     * sizes, 10 bytes) 20 bits of sample rate, 3 of channels - 1, 5 of bits per sample - 1.
     */
    internal fun flacInfo(bytes: ByteArray): FlacInfo? {
        if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != "fLaC") return null
        if (bytes.size < 4 + 4 + 18) return FlacInfo(16, 44_100)
        val b = bytes.copyOfRange(8, 26).map { it.toInt() and 0xff }
        val sampleRate = (b[10] shl 12) or (b[11] shl 4) or (b[12] shr 4)
        val bits = (((b[12] and 0x01) shl 4) or (b[13] shr 4)) + 1
        return if (sampleRate in 8_000..768_000 && bits in 8..32) FlacInfo(bits, sampleRate) else FlacInfo(16, 44_100)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun ModuleHost.httpOrNull(request: HttpRequest): HttpResponse? = runCatching { http(request) }.getOrNull()

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    companion object {
        const val Id = "monochrome"
        private const val ServerField = "server"
        private const val DefaultServer = "https://tracks.monochrome.st"
        private const val DownKey = "unreachable"
        private const val DownAtKey = "unreachable_at"
        private const val SearchLimit = 8
        private const val MissMemoryMs = 30L * 60 * 1000
        // how long a server that didn't answer is left alone before it's tried again
        private const val UnreachableRetryMs = 10L * 60 * 1000
        private const val UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val headers = mapOf("Accept" to "application/json", "User-Agent" to UserAgent)
        private val streamHeaders = mapOf("User-Agent" to UserAgent)
        private val HeadRange = "Range" to "bytes=0-41"
    }
}
