/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class ListeningBySlot(
    val slot: Int,
    val timeListened: Long,
)

@Immutable
data class ListeningTotals(
    val totalPlayCount: Int,
    val totalTimeListened: Long,
)

/**
 * One calendar day of listening, as stored in [Event] — the `day` key is the wall-clock
 * `yyyy-MM-dd` the event timestamps resolve to, so the calendar never has to re-derive
 * timezones on the UI side.
 */
@Immutable
data class ListeningByDate(
    val day: String,
    val timeListened: Long,
    val playCount: Int,
)

@Immutable
data class ListeningSummary(
    val totalPlayCount: Int,
    val totalTimeListened: Long,
    val uniqueSongsCount: Int,
    val uniqueArtistsCount: Int,
    val uniqueAlbumsCount: Int,
)
