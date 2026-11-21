/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.recap

/*
 * Row types returned by [RecapDao]. Only what the queries actually project lives here — the
 * higher-level recap model this file used to carry was superseded by `ui.screens.yearinmusic`
 * and has been removed with the rest of that first attempt.
 */

data class RecapTotalsRow(val milliseconds: Long, val plays: Long, val tracks: Long)

data class RecapRankedRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val milliseconds: Long,
    val plays: Long,
)

data class RecapDayHourRow(val day: String, val hour: Int, val milliseconds: Long)

data class RecapMonthlyArtistRow(
    val month: Int,
    val id: String,
    val title: String,
    val artwork: String?,
    val milliseconds: Long,
    val plays: Long,
)
