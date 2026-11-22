/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.yearinmusic

import androidx.compose.runtime.Immutable
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.SongWithStats

/**
 * One drifting tile on the totals page: an artist portrait (circle) or an album cover (square).
 */
/** Which of the year's lists a drifting cover came from. */
internal enum class RecapArtKind { SONG, ALBUM, ARTIST }

@Immutable
internal data class RecapFloatingArt(
    val artwork: String?,
    val kind: RecapArtKind,
) {
    val circle: Boolean get() = kind == RecapArtKind.ARTIST
}

/**
 * One card of the recap reel.
 *
 * A card carries only the values it draws; the visible labels live in string resources resolved
 * by the renderer, so translations stay in `values/`.
 */
@Immutable
internal sealed interface YearInMusicRecapCard {
    val id: String

    /** Whether a tap anywhere on the page should move the reel on. */
    val tapAdvances: Boolean
        get() = true

    data class Empty(
        val year: Int,
    ) : YearInMusicRecapCard {
        override val id: String = "empty_$year"
    }

    /** The opener: a giant year above the two words that name the reel. */
    data class Intro(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
    ) : YearInMusicRecapCard {
        override val id: String = "intro_$year"
    }

    /** "You listened for 5d 0h 33m 0s of music." plus the play count. */
    data class Totals(
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        /** Cover art drifting behind the sentence, drawn from this year's top lists. */
        val floating: List<RecapFloatingArt> = emptyList(),
    ) : YearInMusicRecapCard {
        override val id: String = "totals"
    }

    /**
     * The longest run of consecutive listening days in the year.
     *
     * The reel answers the sentence with the year's own album covers: one tile per day of the
     * streak, each a cover that was played on it, so a six-day streak is six square cells of
     * artwork rather than an abstract number.
     */
    data class LongestStreak(
        val days: Int,
        val startDay: String,
        val endDay: String,
        val covers: List<String?>,
    ) : YearInMusicRecapCard {
        override val id: String = "longest_streak_${startDay}_$days"
    }

    /** Ranks 1–5 with cover art, name and time. */
    data class TopSongs(
        val songs: List<SongWithStats>,
    ) : YearInMusicRecapCard {
        override val id: String = "top_songs_${songs.joinToString("_") { it.id }}"
    }

    /** The top artist in a big circle, then ranks 2–5 underneath. */
    data class TopArtists(
        val artists: List<Artist>,
    ) : YearInMusicRecapCard {
        override val id: String = "top_artists_${artists.joinToString("_") { it.id }}"
    }

    /** Album artwork tiles in rank order, each captioned underneath. */
    data class TopAlbums(
        val albums: List<Album>,
    ) : YearInMusicRecapCard {
        override val id: String = "top_albums_${albums.joinToString("_") { it.id }}"
    }

    /** The Replay-style stat block that closes the reel. */
    data class Summary(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val activeDays: Int,
        val topSongs: List<SongWithStats>,
        val topArtists: List<Artist>,
        val topAlbums: List<Album>,
    ) : YearInMusicRecapCard {
        override val id: String = "summary_$year"
    }
}
