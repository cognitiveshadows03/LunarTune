/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.StatPeriod
import dev.citali.lunartune.constants.statToPeriod
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.EventWithSong
import dev.citali.lunartune.db.entities.ListeningBySlot
import dev.citali.lunartune.db.entities.ListeningSummary
import dev.citali.lunartune.db.entities.ListeningTotals
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.db.entities.SongWithStats
import moe.rukamori.archivetune.innertube.YouTube
import dev.citali.lunartune.ui.screens.OptionStats
import dev.citali.lunartune.utils.reportException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

sealed interface StatsScreenState {
    data object Loading : StatsScreenState

    data class Success(
        val data: StatsUiData,
    ) : StatsScreenState

    data object Empty : StatsScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : StatsScreenState
}

@Immutable
data class ListeningPatternSlot(
    val slot: Int,
    /** Average milliseconds listened per calendar occurrence in the previous full month (chart bars). */
    val averageTimeListened: Long,
    val occurrenceCount: Int,
    /**
     * Current month-to-date average, per occurrence, for the change chip. Null while the
     * month has no completed days yet, so the chip stays hidden instead of claiming -100%.
     */
    val monthToDateAverageTimeListened: Long?,
)

@Immutable
data class StatsUiData(
    val selectedOption: OptionStats,
    val selectedPeriodIndex: Int,
    val mostPlayedSongs: List<Song>,
    val visibleRankedSongs: List<SongWithStats>,
    val rankedSongCount: Int,
    val mostPlayedArtists: List<Artist>,
    val mostPlayedAlbums: List<Album>,
    val listeningByHour: List<ListeningPatternSlot>,
    val listeningByDayOfWeek: List<ListeningPatternSlot>,
    val listeningTrendBuckets: List<ListeningBySlot>,
    val previousPeriodTimeListened: Long?,
    @StringRes val comparisonLabelResId: Int?,
    val listeningSummary: ListeningSummary,
    val firstEvent: EventWithSong?,
    val isSongListExpanded: Boolean,
    val canExpandSongList: Boolean,
    /** False when the library has listening history but none of it falls inside the selected range. */
    val hasPlaysInPeriod: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
        private val indexChips = MutableStateFlow(0)
        private val isSongListExpanded = MutableStateFlow(false)
        private val isYearPickerOpen = MutableStateFlow(false)
        private val refreshRequest = MutableStateFlow(0L)

        /** Set once the opening time range has been chosen from the actual history. */
        private val initialPeriodResolved = MutableStateFlow(false)

        val yearPickerOpen: StateFlow<Boolean> = isYearPickerOpen

        fun onOptionSelected(option: OptionStats) {
            if (selectedOption.value == option) return
            selectedOption.value = option
            indexChips.value = 0
            isSongListExpanded.value = false
        }

        fun onChipIndexChanged(index: Int) {
            if (indexChips.value == index) return
            indexChips.value = index
            isSongListExpanded.value = false
        }

        fun toggleSongListExpanded() {
            isSongListExpanded.value = !isSongListExpanded.value
        }

        fun showYearPicker() {
            isYearPickerOpen.value = true
        }

        fun dismissYearPicker() {
            isYearPickerOpen.value = false
        }

        fun retry() {
            refreshRequest.value += 1L
        }

        private fun periodPair() = combine(selectedOption, indexChips) { opt, idx -> Pair(opt, idx) }

        private fun weekdayOccurrenceCounts(
            startInclusive: LocalDate,
            endExclusive: LocalDate,
        ): IntArray {
            val counts = IntArray(7)
            var date = startInclusive
            while (date.isBefore(endExclusive)) {
                counts[date.dayOfWeek.value % 7] += 1
                date = date.plusDays(1)
            }
            return counts
        }

        private fun wallClockTimestamp(date: LocalDate): Long =
            date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        private fun buildPatternSlots(
            baselineTotals: List<ListeningBySlot>,
            currentTotals: List<ListeningBySlot>,
            baselineOccurrences: IntArray,
            currentOccurrences: IntArray,
            slotCount: Int,
        ): List<ListeningPatternSlot> {
            val baselineBySlot = baselineTotals.associateBy { it.slot }
            val currentBySlot = currentTotals.associateBy { it.slot }

            return (0 until slotCount).map { slot ->
                val baselineCount = baselineOccurrences.getOrElse(slot) { 0 }
                val currentCount = currentOccurrences.getOrElse(slot) { 0 }
                val baselineTotal = baselineBySlot[slot]?.timeListened ?: 0L
                val currentTotal = currentBySlot[slot]?.timeListened
                ListeningPatternSlot(
                    slot = slot,
                    averageTimeListened = if (baselineCount > 0) baselineTotal / baselineCount else 0L,
                    occurrenceCount = baselineCount,
                    monthToDateAverageTimeListened =
                        if (currentCount > 0 && currentTotal != null) {
                            currentTotal / currentCount
                        } else {
                            null
                        },
                )
            }
        }

        private fun toTimestamp(
            selection: OptionStats,
            t: Int,
            now: LocalDateTime = LocalDateTime.now(),
        ): Long =
            if (selection == OptionStats.CONTINUOUS || t == 0) {
                now.toInstant(ZoneOffset.UTC).toEpochMilli()
            } else {
                statToPeriod(selection, t - 1, now)
            }

        private fun periodWindow(
            selection: OptionStats,
            index: Int,
            now: LocalDateTime,
        ): PeriodWindow {
            val fromTimestamp = statToPeriod(selection, index, now)
            val toTimestamp = toTimestamp(selection, index, now)
            val comparison =
                when (selection) {
                    OptionStats.CONTINUOUS -> {
                        val period = StatPeriod.entries.getOrNull(index)
                        val previousStart =
                            when (period) {
                                StatPeriod.WEEK_1 -> now.minusWeeks(2)
                                StatPeriod.MONTH_1 -> now.minusMonths(2)
                                StatPeriod.MONTH_3 -> now.minusMonths(6)
                                StatPeriod.MONTH_6 -> now.minusMonths(12)
                                StatPeriod.YEAR_1 -> now.minusYears(2)
                                StatPeriod.ALL, null -> null
                            }
                        val labelResId =
                            when (period) {
                                StatPeriod.WEEK_1 -> R.string.stats_trend_vs_last_week
                                StatPeriod.MONTH_1 -> R.string.stats_trend_vs_last_month
                                StatPeriod.MONTH_3 -> R.string.stats_trend_vs_previous_three_months
                                StatPeriod.MONTH_6 -> R.string.stats_trend_vs_previous_six_months
                                StatPeriod.YEAR_1 -> R.string.stats_trend_vs_last_year
                                StatPeriod.ALL, null -> null
                            }
                        if (previousStart != null && labelResId != null) {
                            ComparisonWindow(
                                fromTimestamp = previousStart.toInstant(ZoneOffset.UTC).toEpochMilli(),
                                toTimestamp = fromTimestamp,
                                labelResId = labelResId,
                            )
                        } else {
                            null
                        }
                    }

                    OptionStats.WEEKS -> {
                        val previousStart =
                            if (index == 0) {
                                now.minusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli()
                            } else {
                                statToPeriod(selection, index + 1, now)
                            }
                        ComparisonWindow(
                            fromTimestamp = previousStart,
                            toTimestamp = fromTimestamp,
                            labelResId =
                                if (index == 0) {
                                    R.string.stats_trend_vs_yesterday
                                } else {
                                    R.string.stats_trend_vs_previous_week
                                },
                        )
                    }

                    OptionStats.MONTHS -> {
                        ComparisonWindow(
                            fromTimestamp = statToPeriod(selection, index + 1, now),
                            toTimestamp = fromTimestamp,
                            labelResId = R.string.stats_trend_vs_previous_month,
                        )
                    }

                    OptionStats.YEARS -> {
                        ComparisonWindow(
                            fromTimestamp = statToPeriod(selection, index + 1, now),
                            toTimestamp = fromTimestamp,
                            labelResId = R.string.stats_trend_vs_previous_year,
                        )
                    }
                }

            return PeriodWindow(
                fromTimestamp = fromTimestamp,
                toTimestamp = toTimestamp,
                comparison = comparison,
            )
        }

        private val mostPlayedSongsStats =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp = toTimestamp(selection, t),
                    )
                }

        private val mostPlayedSongs =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedSongs(
                            fromTimeStamp = statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { songs -> songs.filter { song -> song.artists.none { it.blockedAt != null } } }
                }

        private val mostPlayedArtists =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedArtists(
                            statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { artists ->
                            artists.filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                        }
                }

        private val mostPlayedAlbums =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedAlbums(
                            statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { albums -> albums.filter { album -> album.artists.none { it.blockedAt != null } } }
                }

        // The listening-pattern charts average the last complete calendar month on purpose:
        // full weeks make per-slot averages stable, while short selected ranges would render
        // one noisy day as a spike. The change chip compares this month (to date) against
        // that monthly baseline for the highlighted slot only.
        private val listeningPatterns =
            refreshRequest.flatMapLatest {
                val today = LocalDate.now()
                val currentMonthStart = today.withDayOfMonth(1)
                val previousMonthStart = currentMonthStart.minusMonths(1)
                val previousMonthDays =
                    ChronoUnit.DAYS
                        .between(previousMonthStart, currentMonthStart)
                        .toInt()
                val currentCompletedDays = ChronoUnit.DAYS.between(currentMonthStart, today).toInt()
                val previousWeekdayOccurrences = weekdayOccurrenceCounts(previousMonthStart, currentMonthStart)
                val currentWeekdayOccurrences = weekdayOccurrenceCounts(currentMonthStart, today)
                val previousMonthFrom = wallClockTimestamp(previousMonthStart) - 1L
                val previousMonthTo = wallClockTimestamp(currentMonthStart) - 1L
                val currentMonthFrom = wallClockTimestamp(currentMonthStart) - 1L
                val currentMonthTo = wallClockTimestamp(today) - 1L

                combine(
                    database.listeningByHour(previousMonthFrom, previousMonthTo),
                    database.listeningByHour(currentMonthFrom, currentMonthTo),
                    database.listeningByDayOfWeek(previousMonthFrom, previousMonthTo),
                    database.listeningByDayOfWeek(currentMonthFrom, currentMonthTo),
                ) { baselineHours, currentHours, baselineDays, currentDays ->
                    ListeningPatternData(
                        byHour =
                            buildPatternSlots(
                                baselineTotals = baselineHours,
                                currentTotals = currentHours,
                                baselineOccurrences = IntArray(24) { previousMonthDays },
                                currentOccurrences = IntArray(24) { currentCompletedDays },
                                slotCount = 24,
                            ),
                        byDay =
                            buildPatternSlots(
                                baselineTotals = baselineDays,
                                currentTotals = currentDays,
                                baselineOccurrences = previousWeekdayOccurrences,
                                currentOccurrences = currentWeekdayOccurrences,
                                slotCount = 7,
                            ),
                    )
                }
            }

        private val listeningTotals =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.listeningTotals(
                        fromTimestamp = statToPeriod(selection, t),
                        toTimestamp = toTimestamp(selection, t),
                    )
                }

        private val listeningTrendStats =
            periodPair()
                .flatMapLatest { (selection, index) ->
                    val window = periodWindow(selection, index, LocalDateTime.now())
                    val trendBuckets =
                        database.listeningTrendBuckets(
                            fromTimestamp = window.fromTimestamp,
                            toTimestamp = window.toTimestamp,
                            bucketCount = LISTENING_TREND_BUCKET_COUNT,
                        )
                    val previousTime =
                        window.comparison?.let { comparison ->
                            database
                                .listeningTotals(
                                    fromTimestamp = comparison.fromTimestamp,
                                    toTimestamp = comparison.toTimestamp,
                                ).map { it.totalTimeListened }
                        } ?: flowOf<Long?>(null)
                    combine(trendBuckets, previousTime) { buckets, previousTimeListened ->
                        ListeningTrendStats(
                            buckets = buckets,
                            previousPeriodTimeListened = previousTimeListened,
                            comparisonLabelResId = window.comparison?.labelResId,
                        )
                    }
                }

        private val firstEvent =
            database
                .firstEvent()

        private val latestEventTimestamp =
            database
                .latestEventTimestamp()

        private val primaryStats =
            combine(
                mostPlayedSongsStats,
                mostPlayedSongs,
                mostPlayedArtists,
                mostPlayedAlbums,
            ) { rankedSongs, songs, artists, albums ->
                PrimaryStats(
                    rankedSongs = rankedSongs,
                    songs = songs,
                    artists = artists,
                    albums = albums,
                )
            }

        private val listeningStats =
            combine(
                combine(
                    listeningPatterns,
                    listeningTotals,
                    firstEvent,
                    latestEventTimestamp,
                ) { patterns, totals, first, latest ->
                    ListeningStats(
                        byHour = patterns.byHour,
                        byDay = patterns.byDay,
                        totals = totals,
                        firstEvent = first,
                        latestEventTimestamp = latest,
                    )
                },
                listeningTrendStats,
            ) { stats, trend ->
                stats.copy(
                    listeningTrendBuckets = trend.buckets,
                    previousPeriodTimeListened = trend.previousPeriodTimeListened,
                    comparisonLabelResId = trend.comparisonLabelResId,
                )
            }

        val screenState: StateFlow<StatsScreenState> =
            combine(refreshRequest, initialPeriodResolved) { request, resolved -> request to resolved }
                // Hold the first frame until the opening range is known, so the screen
                // never flashes an empty "last week" before jumping to the right period.
                .filter { (_, resolved) -> resolved }
                .flatMapLatest {
                    combine(
                        primaryStats,
                        listeningStats,
                        selectedOption,
                        indexChips,
                        isSongListExpanded,
                    ) { primary, listening, option, periodIndex, expanded ->
                        val summary =
                            ListeningSummary(
                                totalPlayCount = listening.totals.totalPlayCount,
                                totalTimeListened = listening.totals.totalTimeListened,
                                uniqueSongsCount = primary.rankedSongs.size,
                                uniqueArtistsCount = primary.artists.size,
                                uniqueAlbumsCount = primary.albums.size,
                            )
                        // Only a library with no history at all is "empty". A range without
                        // plays keeps the full screen (and its range chips) so the user can
                        // widen the period instead of being told there are no stats.
                        if (listening.latestEventTimestamp == null) {
                            StatsScreenState.Empty
                        } else {
                            StatsScreenState.Success(
                                StatsUiData(
                                    selectedOption = option,
                                    selectedPeriodIndex = periodIndex,
                                    mostPlayedSongs = primary.songs,
                                    visibleRankedSongs =
                                        if (expanded) {
                                            primary.rankedSongs
                                        } else {
                                            primary.rankedSongs.take(COLLAPSED_SONG_COUNT)
                                        },
                                    rankedSongCount = primary.rankedSongs.size,
                                    mostPlayedArtists = primary.artists,
                                    mostPlayedAlbums = primary.albums,
                                    listeningByHour = listening.byHour,
                                    listeningByDayOfWeek = listening.byDay,
                                    listeningTrendBuckets = listening.listeningTrendBuckets,
                                    previousPeriodTimeListened = listening.previousPeriodTimeListened,
                                    comparisonLabelResId = listening.comparisonLabelResId,
                                    listeningSummary = summary,
                                    firstEvent = listening.firstEvent,
                                    isSongListExpanded = expanded,
                                    canExpandSongList = primary.rankedSongs.size > COLLAPSED_SONG_COUNT,
                                    hasPlaysInPeriod = summary.totalPlayCount > 0 || primary.rankedSongs.isNotEmpty(),
                                ),
                            )
                        }
                    }.catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        reportException(throwable)
                        emit(StatsScreenState.Error(R.string.error_unknown))
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = StatsScreenState.Loading,
                )

        init {
            viewModelScope.launch {
                // Open on the shortest continuous range that actually contains plays. A
                // library restored from a backup, or simply a listening break, would
                // otherwise greet the user with an empty last-week view and no hint that
                // older stats exist.
                val latest = runCatching { database.latestEventTimestamp().first() }.getOrNull()
                if (latest != null && selectedOption.value == OptionStats.CONTINUOUS && indexChips.value == 0) {
                    val period = StatPeriod.entries.firstOrNull { it.toTimeMillis() < latest } ?: StatPeriod.ALL
                    indexChips.value = period.ordinal
                }
                initialPeriodResolved.value = true
            }
            viewModelScope.launch {
                mostPlayedArtists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.thumbnailUrl == null || Duration.between(
                                it.lastUpdateTime,
                                LocalDateTime.now(),
                            ) > Duration.ofDays(10)
                        }.forEach { artist ->
                            YouTube.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }
            viewModelScope.launch {
                mostPlayedAlbums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
                            YouTube
                                .album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
        }

        private data class PrimaryStats(
            val rankedSongs: List<SongWithStats>,
            val songs: List<Song>,
            val artists: List<Artist>,
            val albums: List<Album>,
        )

        private data class ListeningPatternData(
            val byHour: List<ListeningPatternSlot>,
            val byDay: List<ListeningPatternSlot>,
        )

        private data class ListeningStats(
            val byHour: List<ListeningPatternSlot>,
            val byDay: List<ListeningPatternSlot>,
            val totals: ListeningTotals,
            val firstEvent: EventWithSong?,
            val latestEventTimestamp: Long?,
            val listeningTrendBuckets: List<ListeningBySlot> = emptyList(),
            val previousPeriodTimeListened: Long? = null,
            @StringRes val comparisonLabelResId: Int? = null,
        )

        private data class ListeningTrendStats(
            val buckets: List<ListeningBySlot>,
            val previousPeriodTimeListened: Long?,
            @StringRes val comparisonLabelResId: Int?,
        )

        private data class PeriodWindow(
            val fromTimestamp: Long,
            val toTimestamp: Long,
            val comparison: ComparisonWindow?,
        )

        private data class ComparisonWindow(
            val fromTimestamp: Long,
            val toTimestamp: Long,
            @StringRes val labelResId: Int,
        )

        private companion object {
            const val COLLAPSED_SONG_COUNT = 5
            const val LISTENING_TREND_BUCKET_COUNT = 7
        }
    }
