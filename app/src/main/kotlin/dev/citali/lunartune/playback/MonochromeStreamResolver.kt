/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import dev.citali.lunartune.monochrome.MonochromeAudioProvider
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Matches a library song against the Monochrome lossless catalogue and
 * resolves a direct FLAC stream for it. MusicService tries this ahead of
 * regular YouTube Music resolution when the lossless source is enabled.
 *
 * Resolved streams are cached under a dedicated cache key prefix
 * ([CACHE_KEY_PREFIX]) so lossless audio never mixes with YouTube Music
 * spans in the Media3 caches for the same song.
 */
object MonochromeStreamResolver {

    const val CACHE_KEY_PREFIX = "mono:"

    fun cacheKeyFor(songId: String): String = CACHE_KEY_PREFIX + songId

    fun songIdFromCacheKey(key: String): String = key.removePrefix(CACHE_KEY_PREFIX)

    data class ResolvedLossless(
        val url: String,
        val mimeType: String,
        val audioQuality: String,
    )

    private data class CachedStream(
        val stream: ResolvedLossless,
        val expiresAtMs: Long,
    )

    private val resolvedCache = ConcurrentHashMap<String, CachedStream>()
    private val negativeCache = ConcurrentHashMap<String, Long>()

    private const val POSITIVE_TTL_MS = 30L * 60L * 1000L
    // Keep transient public-instance failures short; a timeout must not force YouTube fallback
    // for the next ten minutes after the service becomes responsive again.
    private const val NEGATIVE_TTL_MS = 30L * 1000L
    private const val MAX_DURATION_DELTA_SEC = 4

    suspend fun resolve(
        songId: String,
        title: String,
        artists: List<String>,
        durationSec: Int?,
        instanceUrl: String,
    ): ResolvedLossless? {
        val now = System.currentTimeMillis()
        resolvedCache[songId]?.takeIf { it.expiresAtMs > now }?.let { return it.stream }
        negativeCache[songId]?.takeIf { it > now }?.let { return null }

        val query = "$title ${artists.firstOrNull().orEmpty()}".trim()
        val best =
            findBestMatch(
                tracks = MonochromeAudioProvider.search(instanceUrl, query),
                title = title,
                artists = artists,
                durationSec = durationSec,
            )
        val stream =
            best?.let { track ->
                MonochromeAudioProvider.resolveStream(instanceUrl, track.id)?.let {
                    ResolvedLossless(
                        url = it.url,
                        mimeType = it.mimeType,
                        audioQuality = it.audioQuality,
                    )
                }
            }
        if (stream != null) {
            resolvedCache[songId] = CachedStream(stream, now + POSITIVE_TTL_MS)
            negativeCache.remove(songId)
            Timber.tag("Monochrome").d("Matched %s to lossless track %s (%s)", songId, best.id, stream.audioQuality)
        } else {
            negativeCache[songId] = now + NEGATIVE_TTL_MS
        }
        return stream
    }

    private fun findBestMatch(
        tracks: List<MonochromeAudioProvider.MonochromeTrack>,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): MonochromeAudioProvider.MonochromeTrack? =
        tracks
            .filter { isMatch(it, title, artists, durationSec) }
            .minByOrNull { qualityRank(it.audioQuality) }

    private fun qualityRank(quality: String): Int =
        when (quality) {
            "HI_RES_LOSSLESS" -> 0
            "LOSSLESS" -> 1
            else -> 2
        }

    private fun isMatch(
        track: MonochromeAudioProvider.MonochromeTrack,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): Boolean {
        if (!track.streamReady) return false
        if (track.audioQuality != "HI_RES_LOSSLESS" && track.audioQuality != "LOSSLESS") return false
        val songTitle = normalize(title)
        val candidateTitle = normalize(track.title)
        if (songTitle.isEmpty() || candidateTitle.isEmpty()) return false
        val titleMatches =
            candidateTitle == songTitle ||
                candidateTitle.contains(songTitle) ||
                songTitle.contains(candidateTitle)
        if (!titleMatches) return false
        val candidateArtist = normalize(track.artistName)
        val artistMatches =
            artists.any { artist ->
                normalize(artist).let {
                    it.isNotEmpty() && (candidateArtist.contains(it) || it.contains(candidateArtist))
                }
            }
        if (!artistMatches) return false
        if (durationSec != null && track.durationSec != null &&
            abs(durationSec - track.durationSec) > MAX_DURATION_DELTA_SEC
        ) {
            return false
        }
        return true
    }

    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
