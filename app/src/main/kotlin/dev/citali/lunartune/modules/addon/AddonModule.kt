/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.addon

import dev.citali.lunartune.modules.AccountState
import dev.citali.lunartune.modules.DiagnosticStep
import dev.citali.lunartune.modules.MatchedTrack
import dev.citali.lunartune.modules.ModuleChoice
import dev.citali.lunartune.modules.ModuleHost
import dev.citali.lunartune.modules.NO_ACCOUNT_LABEL
import dev.citali.lunartune.modules.ResolveResult
import dev.citali.lunartune.modules.SourceModule
import dev.citali.lunartune.modules.SourceModuleInfo
import dev.citali.lunartune.modules.SourceStream
import dev.citali.lunartune.modules.StreamFormat
import dev.citali.lunartune.modules.StreamOption
import dev.citali.lunartune.modules.TrackMatcher
import dev.citali.lunartune.modules.TrackQuery
import timber.log.Timber

/**
 * A BitChord addon, seen as one of our modules.
 *
 * The protocol and ours ask the same two questions — where is this track in your
 * catalogue, and where is its audio — so an addon plugs in at the same seam the
 * built-in modules do. Nothing downstream knows the difference: it takes part in
 * the same priority order, is switched on the same way, falls back to YouTube
 * Music the same way, and reports through the same rows and detail sheet.
 *
 * [address] is the base the user pasted; [label] is what it calls itself once
 * its manifest has been read.
 */
class AddonModule(
    override val info: SourceModuleInfo,
    private val address: String,
) : SourceModule {

    private var host: ModuleHost? = null

    /** the manifest as it was last read, kept for this run of the app */
    private var cached: AddonManifest? = null

    private val client: AddonClient? get() = host?.let { AddonClient(address, it) }

    /**
     * Attaches the host and nothing else.
     *
     * No request is made here. Attaching happens at start-up and again whenever
     * the addon list changes, so a fetch would put one round trip per addon — each
     * with a timeout — in front of the first screen. The manifest arrives when it
     * is first needed, on the worker that needs it; until then the row shows the
     * name and version recorded when the addon was added, which is enough to draw
     * it and enough to switch it on.
     */
    override fun attach(host: ModuleHost) {
        this.host = host
    }

    /**
     * The manifest, fetched at most once per run.
     *
     * Called from [resolve] and from the settings page; both are off the main
     * thread, so the blocking request inside is where it belongs.
     */
    private fun manifest(client: AddonClient): Result<AddonManifest> {
        cached?.let { return Result.success(it) }
        // the copy saved when the addon was added, so the page can show its
        // switches before anything has been served
        val seeded = AddonStore.savedManifest(address)
        if (seeded != null) {
            cached = seeded
            return Result.success(seeded)
        }
        return client.manifest().onSuccess { cached = it }
    }

    /**
     * The addon's own select-type settings, read from the manifest saved when it
     * was added — so the page can offer them before the addon has been called.
     */
    override val choices: List<ModuleChoice>
        get() =
            (cached ?: AddonStore.savedManifest(address))?.settings.orEmpty()
                .filter { it.type.equals("select", true) && it.options.isNotEmpty() }
                .map { setting ->
                    ModuleChoice(
                        id = setting.key,
                        label = setting.name.ifBlank { setting.key },
                        options = setting.options.map { StreamOption(it.value, it.name.ifBlank { it.value }, "") },
                    )
                }

    override fun accountState(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    override fun lastStreamLabel(): String? = host?.storage?.get(LastStreamKey)

    override fun clearLastStream() {
        host?.storage?.set(LastStreamKey, null)
    }

    // ---- resolving -----------------------------------------------------------

    override suspend fun resolve(
        track: TrackQuery,
        picks: Map<String, String>,
    ): ResolveResult = resolveInternal(track, picks, report = null)

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
        val client = client ?: return ResolveResult.Unavailable("the addon has not been set up")
        val manifest = manifest(client).getOrElse { error ->
            report?.invoke(DiagnosticStep("Manifest", false, error.message.orEmpty()))
            return ResolveResult.Unavailable(error.message ?: "the addon did not describe itself")
        }
        report?.invoke(DiagnosticStep("Manifest", true, "${manifest.name.ifBlank { address }} ${manifest.version}".trim()))

        // the addon's own defaults, with the user's picks on top — an addon that
        // gates lossless behind a quality switch reads it from here
        val params = client.manifestParams(manifest) + picks
        val query = listOfNotNull(track.title, track.artists.firstOrNull()).joinToString(" ").trim()
        if (query.isEmpty()) return ResolveResult.NotFound("the track has no title to search for")

        val tracks = client.search(query, params).getOrElse { error ->
            report?.invoke(DiagnosticStep("Search", false, error.message.orEmpty()))
            return when (error) {
                is AddonException.NotFound -> ResolveResult.NotFound(error.message.orEmpty())
                else -> ResolveResult.Unavailable(error.message ?: "the addon could not be asked")
            }
        }
        report?.invoke(DiagnosticStep("Search", true, "${tracks.size} candidate${if (tracks.size == 1) "" else "s"}"))
        if (tracks.isEmpty()) return ResolveResult.NotFound("the addon's catalogue has nothing for this track")

        val match = bestMatch(tracks, track) ?: run {
            report?.invoke(DiagnosticStep("Match", false, "nothing close enough to the track"))
            return ResolveResult.NotFound("nothing in the addon's catalogue matches this track")
        }
        report?.invoke(
            DiagnosticStep("Match", true, "\"${match.track.title}\" at ${(match.confidence * 100).toInt()}%"),
        )

        // the protocol lets an addon put the address on the row; when it does,
        // one round trip is skipped on the path that has to finish before audio
        val direct = match.track.streamURL?.takeIf { it.isNotBlank() }
        val stream =
            if (direct != null) {
                report?.invoke(DiagnosticStep("Stream", true, "given by the row"))
                AddonStream(url = direct, format = match.track.format, quality = match.track.audioQuality)
            } else {
                client.stream(match.track.id, params).getOrElse { error ->
                    report?.invoke(DiagnosticStep("Stream", false, error.message.orEmpty()))
                    return when (error) {
                        is AddonException.NotFound -> ResolveResult.NotFound(error.message.orEmpty())
                        else -> ResolveResult.Unavailable(error.message ?: "the addon could not serve this track")
                    }
                }
            }

        stream.error?.takeIf { it.isNotBlank() }?.let { reason ->
            // the addon answered with its own explanation: a missing credential, a
            // backend it could not reach. That is worth passing on verbatim.
            report?.invoke(DiagnosticStep("Stream", false, reason))
            return ResolveResult.NotFound(reason)
        }
        if (!stream.playable) {
            report?.invoke(DiagnosticStep("Stream", false, "no playable address"))
            return ResolveResult.NotFound("the addon returned nothing playable")
        }
        report?.invoke(DiagnosticStep("Stream", true, stream.qualityText.ifBlank { stream.format }))

        val quality = stream.qualityText.ifBlank { stream.statedCodec.orEmpty() }
        rememberStream(quality)

        return ResolveResult.Found(
            SourceStream(
                url = stream.url,
                format = streamFormat(stream),
                headers = emptyMap(),
                sourceLabel = info.name,
                qualityLabel = quality.ifBlank { "Addon" },
                matched =
                    MatchedTrack(
                        id = match.track.id,
                        title = match.track.title,
                        artist = match.track.artist,
                        album = match.track.album.ifBlank { null },
                        durationMs = match.track.durationSec?.toLong()?.times(1000L),
                        confidence = match.confidence,
                    ),
                codec = stream.statedCodec ?: match.track.format.ifBlank { null },
                sampleRateHz = stream.sampleRate?.toInt(),
                bitrateBps = stream.bitrate?.toInt(),
            ),
        )
    }

    private fun streamFormat(stream: AddonStream): StreamFormat {
        val manifest = (stream.manifest ?: stream.mediaType).orEmpty().lowercase()
        return when {
            manifest.contains("hls") -> StreamFormat.Hls
            manifest.contains("dash") -> StreamFormat.Dash
            stream.url.contains(".m3u8", true) -> StreamFormat.Hls
            stream.url.contains(".mpd", true) -> StreamFormat.Dash
            else -> StreamFormat.Progressive
        }
    }

    private fun rememberStream(label: String) {
        if (label.isNotBlank()) host?.storage?.set(LastStreamKey, label)
    }

    // ---- matching ------------------------------------------------------------

    /**
     * Picks the addon's row most likely to be the same recording.
     *
     * Addons are catalogues of whole services, so the same song appears several
     * times — a remaster, a live take, a remix — and taking the first result is
     * how a lossless source ends up playing the wrong thing. The comparison
     * rejects outright when the durations disagree by more than the app's own
     * tolerance, then scores the survivors on title and artist.
     */
    private fun bestMatch(
        tracks: List<AddonTrack>,
        track: TrackQuery,
    ): Candidate? {
        val wantedTitle = normalise(track.title)
        val wantedArtist = normalise(track.artists.firstOrNull().orEmpty())
        val wantedSeconds = track.durationMs?.div(1000L)
        val tolerance = TrackMatcher.toleranceSeconds

        return tracks
            .asSequence()
            .filter { it.id.isNotBlank() || !it.streamURL.isNullOrBlank() }
            .filter { candidate ->
                val seconds = candidate.durationSec?.toLong() ?: return@filter true
                val wanted = wantedSeconds ?: return@filter true
                kotlin.math.abs(seconds - wanted) <= tolerance
            }
            .filter { candidate ->
                // a candidate with no title at all is still worth considering: some
                // addons return only ids for a direct-address row
                wantedTitle.isBlank() ||
                    candidate.title.isBlank() ||
                    normalise(candidate.title).contains(wantedTitle)
            }
            .map { candidate ->
                Candidate(
                    track = candidate,
                    confidence = confidence(wantedTitle, wantedArtist, candidate),
                )
            }.maxByOrNull { it.confidence }
    }

    private fun confidence(
        wantedTitle: String,
        wantedArtist: String,
        candidate: AddonTrack,
    ): Float {
        val title = normalise(candidate.title)
        val artist = normalise(candidate.artist)
        var score = 0f
        if (wantedTitle.isNotBlank() && title.isNotBlank()) {
            score += if (title == wantedTitle) 0.6f else if (title.contains(wantedTitle)) 0.5f else 0f
        }
        if (wantedArtist.isNotBlank() && artist.isNotBlank()) {
            score += when {
                artist == wantedArtist -> 0.4f
                artist.contains(wantedArtist) || wantedArtist.contains(artist) -> 0.3f
                else -> 0f
            }
        }
        // nothing to compare against: accept it, but do not claim to be sure
        if (wantedTitle.isBlank() && wantedArtist.isBlank()) score = 0.5f
        return score.coerceIn(0f, 1f)
    }

    private fun normalise(value: String): String =
        value
            .lowercase()
            .replace(BracketedAside, " ")
            .replace(NonWord, " ")
            .trim()
            .replace(Whitespace, " ")

    private data class Candidate(
        val track: AddonTrack,
        val confidence: Float,
    )

    private companion object {
        const val LastStreamKey = "last_stream"
        val BracketedAside = Regex("[(\\[][^)\\]]*[)\\]]")
        val NonWord = Regex("[^a-z0-9 ]")
        val Whitespace = Regex("\\s+")
    }
}

/** logs an addon's failure the way the module log lines are written elsewhere */
internal fun reportAddonFailure(
    id: String,
    error: Throwable,
) {
    Timber.tag("Addon").d(error, "addon %s produced nothing", id)
}
