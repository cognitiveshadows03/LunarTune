/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.recap

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import java.time.LocalDate

enum class RecapEntryKind { SONG, ARTIST, ALBUM }

enum class RecapChapter { INTRO, TIME, TRACK, SONGS, ARTISTS, ALBUMS, MONTHS, HABITS, SUMMARY, END }

@Immutable
data class RecapEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val milliseconds: Long,
    val plays: Long,
    val kind: RecapEntryKind,
)

@Immutable
data class RecapMonth(
    val month: Int,
    val milliseconds: Long,
    val available: Boolean,
    val artist: RecapEntry? = null,
)

@Immutable
data class RecapHabits(
    val activeDays: Int,
    val longestStreak: Int,
    val busiestMonth: Int?,
    val weekday: Int?,
    val hour: Int?,
)

@Immutable
data class RecapData(
    val year: Int,
    val month: Int?,
    val throughDate: LocalDate,
    val milliseconds: Long,
    val plays: Long,
    val distinctTracks: Long,
    val songs: ImmutableList<RecapEntry>,
    val artists: ImmutableList<RecapEntry>,
    val albums: ImmutableList<RecapEntry>,
    val months: ImmutableList<RecapMonth>,
    val habits: RecapHabits,
    val chapters: ImmutableList<RecapChapter>,
)

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

internal data class RecapSnapshot(
    val firstTimestamp: Long?,
    val totals: RecapTotalsRow,
    val songs: List<RecapRankedRow>,
    val artists: List<RecapRankedRow>,
    val albums: List<RecapRankedRow>,
    val dayHours: List<RecapDayHourRow>,
    val monthlyArtists: List<RecapMonthlyArtistRow>,
)

internal data class RecapResult(val data: RecapData, val years: ImmutableList<Int>)
