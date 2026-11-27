/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.remote

import dev.citali.lunartune.modules.AccountState
import dev.citali.lunartune.modules.DiagnosticStep
import dev.citali.lunartune.modules.FieldKind
import dev.citali.lunartune.modules.HttpRequest
import dev.citali.lunartune.modules.HttpResponse
import dev.citali.lunartune.modules.MatchedTrack
import dev.citali.lunartune.modules.ModuleChoice
import dev.citali.lunartune.modules.ModuleField
import dev.citali.lunartune.modules.ModuleHost
import dev.citali.lunartune.modules.ResolveResult
import dev.citali.lunartune.modules.SourceModule
import dev.citali.lunartune.modules.SourceModuleInfo
import dev.citali.lunartune.modules.SourceStream
import dev.citali.lunartune.modules.StreamFormat
import dev.citali.lunartune.modules.StreamOption
import dev.citali.lunartune.modules.TrackMatcher
import dev.citali.lunartune.modules.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.Base64

/**
 * A module that came from a document rather than from this source tree.
 *
 * To everything downstream of [SourceModule] this is an ordinary module: it
 * answers the same calls, reports the same kinds of result, and sits in the same
 * priority list. The difference is only in how it decides what to do — instead of
 * Kotlin it reads request templates out of its document.
 *
 * That means a loaded module needs no engine, no interpreter and no sandbox,
 * because there is nothing executing: every behaviour it has is one of the three
 * shapes below (search, stream, extract) with its own URLs in them.
 */
class RemoteModule(
    private val document: RemoteModuleDocument,
) : SourceModule {

    override val info =
        SourceModuleInfo(
            id = document.id,
            name = document.name,
            version = document.version.ifBlank { "1.0" },
            description = document.description,
            sourceUrl = document.homepage,
            author = document.author,
        )

    private lateinit var host: ModuleHost

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    // ---- what it offers ------------------------------------------------------

    override val choices: List<ModuleChoice> =
        document.choices.map { choice ->
            ModuleChoice(
                id = choice.id,
                label = choice.label,
                options = choice.options.map { StreamOption(it.id, it.label, it.description) },
            )
        }

    override val fields: List<ModuleField> =
        document.fields.map { field ->
            ModuleField(
                id = field.id,
                label = field.label,
                kind =
                    when (field.kind) {
                        RemoteFieldKind.Text -> FieldKind.Text
                        RemoteFieldKind.Password -> FieldKind.Text
                        RemoteFieldKind.Select -> FieldKind.Select
                        RemoteFieldKind.Toggle -> FieldKind.Toggle
                    },
                options = field.options.map { StreamOption(it.id, it.label, it.description) },
                default = field.default,
                hint = field.hint,
                description = field.description,
            )
        }

    override fun fieldValue(field: ModuleField): String =
        host.storage.get(field.id)?.takeIf { it.isNotBlank() } ?: field.default

    override fun setFieldValue(field: ModuleField, value: String?) {
        host.storage.set(field.id, value?.takeIf { it.isNotBlank() })
    }

    /** a module with an API-key field is remembered as configured, not as signed in */
    override fun accountState(): AccountState {
        val required = document.fields.filter { it.kind != RemoteFieldKind.Toggle && it.default.isBlank() }
        if (required.isEmpty()) return AccountState.SignedIn(dev.citali.lunartune.modules.NO_ACCOUNT_LABEL)
        val missing = required.filter { host.storage.get(it.id).isNullOrBlank() }
        return if (missing.isEmpty()) {
            AccountState.SignedIn("Configured")
        } else {
            AccountState.Expired("Set ${missing.joinToString { it.label }}")
        }
    }

    override fun lastStreamLabel(): String? = host.storage.get(LastStreamKey)

    override fun clearLastStream() {
        host.storage.set(LastStreamKey, null)
    }

    // ---- resolving -----------------------------------------------------------

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        resolveInternal(track, picks, report = null)

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult = resolveInternal(track, picks, report)

    private fun resolveInternal(
        track: TrackQuery,
        picks: Map<String, String>,
        report: ((DiagnosticStep) -> Unit)?,
    ): ResolveResult {
        val capability = document.capabilities.stream ?: return ResolveResult.NotFound(
            "this module only searches; it does not serve audio",
        )
        val values = baseValues(track, picks)
        val step = { name: String, detail: String -> report?.invoke(DiagnosticStep(name, true, detail)) }

        return try {
            // 1. find the service's own record for this track
            val searchRequest = render(capability.search, values, urlEncode = true)
            step("Search", "asked ${capability.search.method} ${redact(searchRequest.url)}")
            val searchAnswer = request(searchRequest)
            if (!searchAnswer.ok) {
                report?.invoke(DiagnosticStep("Search", false, "answered ${searchAnswer.code}"))
                return ResolveResult.Unavailable("the search answered ${searchAnswer.code}")
            }
            val searchJson = parseJson(searchAnswer.text) ?: run {
                report?.invoke(DiagnosticStep("Search", false, "the answer was not JSON"))
                return ResolveResult.Unavailable("the search answer was not JSON")
            }
            val candidates = JsonPath.all(searchJson, capability.match.resultsPath)
            step("Search", "${candidates.size} candidate${if (candidates.size == 1) "" else "s"}")
            if (candidates.isEmpty()) return ResolveResult.NotFound("the search found nothing")

            val match = bestMatch(candidates, capability.match, track) ?: run {
                report?.invoke(DiagnosticStep("Match", false, "nothing close enough to the track"))
                return ResolveResult.NotFound("nothing in the catalogue matches this track")
            }
            step("Match", "\"${match.title}\" at ${(match.confidence * 100).toInt()}% (${match.id})")

            // 2. read the audio address out of that record
            val streamValues = values.withMatch(
                "id" to match.id,
                "title" to match.title,
                "artist" to match.artist,
                "album" to match.album.orEmpty(),
            )
            val streamRequest = render(capability.stream, streamValues, urlEncode = true)
            step("Stream", "asked ${capability.stream.method} ${redact(streamRequest.url)}")
            val streamAnswer = request(streamRequest)
            if (!streamAnswer.ok) {
                report?.invoke(DiagnosticStep("Stream", false, "answered ${streamAnswer.code}"))
                return ResolveResult.Unavailable("the stream answered ${streamAnswer.code}")
            }
            val streamJson = parseJson(streamAnswer.text)
            val address = readAddress(capability.extract, streamJson) ?: run {
                report?.invoke(DiagnosticStep("Stream", false, "no address at ${capability.extract.urlPath}"))
                return ResolveResult.NotFound("the service did not return a stream address")
            }
            step("Stream", redact(address))

            val codec = capability.codec
                ?: JsonPath.string(streamJson, capability.extract.codecPath)
            val quality = capability.quality.ifBlank { codec.orEmpty() }
            rememberStream(quality.ifBlank { capability.extract.format })

            ResolveResult.Found(
                SourceStream(
                    url = address,
                    format = streamFormat(capability.extract.format),
                    headers = extractHeaders(capability.extract, streamJson),
                    sourceLabel = document.name,
                    qualityLabel = quality.ifBlank { capability.extract.format },
                    matched = match.toMatchedTrack(),
                    codec = codec,
                    sampleRateHz = JsonPath.number(streamJson, capability.extract.sampleRatePath)?.toInt(),
                    bitrateBps = JsonPath.number(streamJson, capability.extract.bitratePath)?.toInt(),
                ),
            )
        } catch (e: Exception) {
            Timber.tag("Module:${document.id}").w(e, "resolve failed")
            report?.invoke(DiagnosticStep("Request", false, e.message ?: "failed"))
            ResolveResult.Unavailable(e.message ?: "the module could not be asked")
        }
    }

    // ---- pieces --------------------------------------------------------------

    private fun baseValues(track: TrackQuery, picks: Map<String, String>): Map<String, String> {
        val values = HashMap<String, String>()
        values["title"] = track.title
        values["artist"] = track.artists.firstOrNull().orEmpty()
        values["artists"] = track.artists.joinToString(" ")
        values["album"] = track.album.orEmpty()
        values["duration"] = track.durationMs?.let { (it / 1000L).toString() }.orEmpty()
        values["isrc"] = track.isrc.orEmpty()
        picks.forEach { (choiceId, optionId) ->
            values["picks.$choiceId"] = optionId
            // a module that wants the option's *label* rather than its id can ask
            // for it, which is common where a service names its quality tiers
            document.choices.firstOrNull { it.id == choiceId }
                ?.options?.firstOrNull { it.id == optionId }
                ?.let { values["picks.${choiceId}.label"] = it.label }
        }
        // fields carry the user's own settings; an unset field falls back to its default
        document.fields.forEach { field ->
            values["fields.${field.id}"] = host.storage.get(field.id)?.takeIf { it.isNotBlank() } ?: field.default
        }
        return values
    }

    private fun render(
        template: RemoteRequestTemplate,
        values: Map<String, String>,
        urlEncode: Boolean,
    ): HttpRequest {
        var url = Template.render(template.url, values, urlEncode)
        if (template.keyParam != null && template.keyField != null) {
            val key = values["fields.${template.keyField}"].orEmpty()
            if (key.isNotBlank()) {
                val separator = if (url.contains('?')) '&' else '?'
                url = "$url$separator${template.keyParam}=${Template.percentEncode(key)}"
            }
        }
        val headers =
            template.headers.mapValues { (_, value) -> Template.render(value, values, urlEncode = false) }
        val body = template.body?.let { Template.render(it, values, urlEncode = false) }
        return HttpRequest(
            url = url,
            method = template.method.uppercase(),
            headers = headers,
            body = body?.encodeToByteArray(),
            contentType = template.contentType ?: body?.let { "application/json" },
        )
    }

    private fun request(request: HttpRequest): HttpResponse = host.http(request)

    private fun parseJson(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun readAddress(extract: RemoteExtract, root: JsonElement?): String? {
        val primary = JsonPath.string(root, extract.urlPath)
        val fallback = extract.fallbackUrlPath.takeIf { it.isNotBlank() }?.let { JsonPath.string(root, it) }
        val raw = primary ?: fallback ?: return null
        val decoded =
            if (extract.base64) {
                runCatching { Base64.getDecoder().decode(raw).decodeToString() }
                    .getOrElse { Base64.getUrlDecoder().decode(raw).decodeToString() }
            } else {
                raw
            }
        if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded
        val prefix = extract.urlPrefix.takeIf { it.isNotBlank() } ?: return decoded.takeIf { decoded.startsWith("/") }?.let { null }
        return prefix.trimEnd('/') + "/" + decoded.trimStart('/')
    }

    private fun extractHeaders(extract: RemoteExtract, root: JsonElement?): Map<String, String> {
        val path = extract.headersPath.takeIf { it.isNotBlank() } ?: return emptyMap()
        val element = JsonPath.first(root, path) as? JsonObject ?: return emptyMap()
        return element.mapValues { (_, value) ->
            when (value) {
                is kotlinx.serialization.json.JsonPrimitive -> value.contentOrNull.orEmpty()
                else -> value.toString()
            }
        }.filterValues { it.isNotBlank() }
    }

    private fun streamFormat(raw: String): StreamFormat =
        when (raw.lowercase()) {
            "hls", "m3u8" -> StreamFormat.Hls
            "dash", "mpd" -> StreamFormat.Dash
            else -> StreamFormat.Progressive
        }

    private fun rememberStream(label: String) {
        host.storage.set(LastStreamKey, label)
    }

    /**
     * Picks the candidate that is most probably the same recording.
     *
     * The comparison is deliberately conservative: a candidate whose duration is
     * far from the track's is rejected outright, because playing the wrong song
     * is worse than falling back to YouTube Music. Among the survivors the one
     * whose title and artist contain the most of what was asked for wins.
     */
    private fun bestMatch(
        candidates: List<JsonElement>,
        match: RemoteMatch,
        track: TrackQuery,
    ): Candidate? {
        val wantedTitle = normalise(track.title)
        val wantedArtist = normalise(track.artists.firstOrNull().orEmpty())
        val wantedSeconds = track.durationMs?.div(1000L)
        val tolerance = TrackMatcher.toleranceSeconds

        return candidates
            .mapNotNull { element ->
                val id = JsonPath.string(element, match.idPath) ?: return@mapNotNull null
                val title = JsonPath.string(element, match.titlePath).orEmpty()
                val artist = JsonPath.string(element, match.artistPath).orEmpty()
                val album = JsonPath.string(element, match.albumPath)
                val seconds = JsonPath.number(element, match.durationPath)?.toLong()
                if (wantedSeconds != null && seconds != null && seconds > 0) {
                    if (kotlin.math.abs(seconds - wantedSeconds) > tolerance) return@mapNotNull null
                }
                if (wantedTitle.isNotBlank() && !normalise(title).contains(wantedTitle)) return@mapNotNull null
                Candidate(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    confidence = confidence(wantedTitle, wantedArtist, normalise(title), normalise(artist)),
                )
            }.maxByOrNull { it.confidence }
    }

    private fun confidence(
        wantedTitle: String,
        wantedArtist: String,
        title: String,
        artist: String,
    ): Float {
        var score = 0f
        if (wantedTitle.isNotBlank()) {
            score += if (title == wantedTitle) 0.6f else if (title.contains(wantedTitle)) 0.5f else 0f
        }
        if (wantedArtist.isNotBlank()) {
            score += if (artist == wantedArtist) 0.4f else if (artist.contains(wantedArtist) || wantedArtist.contains(artist)) 0.3f else 0f
        }
        // a module that names no title or artist paths still gets a usable score
        if (wantedTitle.isBlank() && wantedArtist.isBlank()) score = 0.5f
        return score.coerceIn(0f, 1f)
    }

    /** lowercased, punctuation and bracketed asides removed, spacing collapsed */
    private fun normalise(value: String): String =
        value
            .lowercase()
            .replace(BracketedAside, " ")
            .replace(NonWord, " ")
            .trim()
            .replace(Whitespace, " ")

    /** a module's answers are logged with the key taken out of them */
    private fun redact(url: String): String = url.replace(ApiKeyParam, "$1=…")

    private data class Candidate(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val confidence: Float,
    ) {
        fun toMatchedTrack(): MatchedTrack =
            MatchedTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = null,
                confidence = confidence,
            )
    }

    private companion object {
        const val LastStreamKey = "last_stream"
        val BracketedAside = Regex("[(\\[][^)\\]]*[)\\]]")
        val NonWord = Regex("[^a-z0-9 ]")
        val Whitespace = Regex("\\s+")
        val ApiKeyParam = Regex("([?&][^=&]*key[^=&]*)=[^&]*", RegexOption.IGNORE_CASE)
    }
}
