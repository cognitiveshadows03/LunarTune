/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import kotlin.math.abs

/** A song in a service's catalog, as its search returns it. */
class CatalogTrack(
    val id: String,
    val title: String,
    /** every credited artist, as one line ("benny blanco, Halsey & Khalid") */
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

/**
 * Finds the catalog song that is the same recording as the one LunarTune is playing,
 * for any module. Titles are compared without the decorations sources add
 * ("feat.", "Remastered", "Official Video"), but the words that make it a different
 * recording (remix, live, acoustic, sped up...) count against it.
 */
object TrackMatcher {

    private val versionWords = listOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "sped up", "slowed", "nightcore",
        "demo", "cover", "edit", "mix", "version", "unplugged", "orchestral", "piano",
    )
    private val versionPatterns = versionWords.map { Regex("""\b${Regex.escape(it)}\b""") }

    class Scored(val track: CatalogTrack, val score: Float)

    fun best(query: TrackQuery, candidates: List<CatalogTrack>): Scored? = ranked(query, candidates).firstOrNull()

    /**
     * Every acceptable match, best first. The same recording often sits in the catalog
     * several times (the single, the album, a deluxe, a compilation), and they differ in
     * which streams they carry, so the caller tries the near-equal ones in turn.
     */
    fun ranked(query: TrackQuery, candidates: List<CatalogTrack>): List<Scored> {
        val isrc = query.isrc
        val scored = candidates.map { song ->
            Scored(song, if (isrc != null && song.isrc.equals(isrc, ignoreCase = true)) 1f else score(query, song))
        }
        return scored.filter { it.score >= 0.7f }.sortedByDescending { it.score }
    }

    fun score(query: TrackQuery, song: CatalogTrack): Float {
        val qTitle = core(query.title)
        val sTitle = core(song.title)
        val title = similarity(qTitle, sTitle)
        if (title < 0.55f) return 0f

        val songArtists = words(song.artist)
        val artist = query.artists.maxOfOrNull { a ->
            val w = words(a)
            if (w.isEmpty()) 0f else w.count { it in songArtists }.toFloat() / w.size
        } ?: 0f
        if (artist < 0.5f) return 0f

        val wantMs = query.durationMs
        val haveMs = song.durationMs
        val duration = if (wantMs != null && haveMs != null) {
            val diff = abs(wantMs - haveMs) / 1000f
            when {
                diff <= 2f -> 1f
                diff >= 15f -> 0f
                else -> 1f - (diff - 2f) / 13f
            }
        } else 0.6f

        // a remix for an original (or the other way round) is a different recording; whole words
        // only, so "Alive" isn't "live" and a remix isn't also a "mix"
        val qRaw = query.title.lowercase()
        val sRaw = song.title.lowercase()
        val mismatch = versionPatterns.count { it.containsMatchIn(qRaw) != it.containsMatchIn(sRaw) }
        val penalty = 0.25f * mismatch

        return (0.5f * title + 0.3f * artist + 0.2f * duration - penalty).coerceIn(0f, 1f)
    }

    /** the title without what sources hang on it */
    fun core(title: String): String {
        var t = title.lowercase()
        t = t.replace(Regex("""[(\[](?:feat|ft|with|prod)\.?\s[^)\]]*[)\]]"""), " ")
        t = t.replace(Regex("""\s[-–]\s.*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv)\b.*$"""), " ")
        t = t.replace(Regex("""[(\[][^)\]]*\b(?:remaster(?:ed)?|official|video|audio|lyrics?|visuali[sz]er|mv|explicit|clean)\b[^)\]]*[)\]]"""), " ")
        t = t.replace(Regex("""\b(?:feat|ft)\.?\s.*$"""), " ")
        return words(t).joinToString(" ")
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() && it != "the" && it != "and" }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        return (wa intersect wb).size.toFloat() / (wa union wb).size
    }
}
