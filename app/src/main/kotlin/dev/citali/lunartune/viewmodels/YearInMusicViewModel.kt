/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.ListeningByDate
import dev.citali.lunartune.db.entities.ListeningTotals
import dev.citali.lunartune.db.entities.SongWithStats
import moe.rukamori.archivetune.innertube.YouTube
import dev.citali.lunartune.utils.reportException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearInMusicViewModel
    @Inject
    constructor(
        val database: MusicDatabase,
    ) : ViewModel() {
        private val selectedYear = MutableStateFlow(LocalDateTime.now().year)

        private val firstEvent =
            database
                .firstEvent()
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        private val availableYears =
            firstEvent
                .map { event ->
                    val startYear = event?.event?.timestamp?.year ?: LocalDateTime.now().year
                    val currentYear = LocalDateTime.now().year
                    (currentYear downTo startYear).toList()
                }.stateIn(viewModelScope, SharingStarted.Lazily, listOf(LocalDateTime.now().year))

        private fun getYearStartTimestamp(year: Int): Long =
            LocalDateTime
                .of(year, 1, 1, 0, 0, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

        private fun getYearEndTimestamp(year: Int): Long =
            LocalDateTime
                .of(year, 12, 31, 23, 59, 59)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

        private val topSongsStats =
            selectedYear
                .flatMapLatest { year ->
                    database.mostPlayedSongsStats(
                        fromTimeStamp = getYearStartTimestamp(year),
                        limit = 5,
                        toTimeStamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val topArtists =
            selectedYear
                .flatMapLatest { year ->
                    database
                        .mostPlayedArtists(
                            fromTimeStamp = getYearStartTimestamp(year),
                            limit = 5,
                            toTimeStamp = getYearEndTimestamp(year),
                        ).map { artists ->
                            artists.filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                        }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val topAlbums =
            selectedYear
                .flatMapLatest { year ->
                    database
                        .mostPlayedAlbums(
                            fromTimeStamp = getYearStartTimestamp(year),
                            limit = 5,
                            toTimeStamp = getYearEndTimestamp(year),
                        ).map { albums -> albums.filter { album -> album.artists.none { it.blockedAt != null } } }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val listeningTotals =
            selectedYear
                .flatMapLatest { year ->
                    database.listeningTotals(
                        fromTimestamp = getYearStartTimestamp(year),
                        toTimestamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, ListeningTotals(0, 0L))

        private val activeDays =
            selectedYear
                .flatMapLatest { year ->
                    database.listeningActiveDays(
                        fromTimestamp = getYearStartTimestamp(year),
                        toTimestamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

        /**
         * Per-day plays for the selected year.
         *
         * The calendar query groups raw events into wall-clock days, which is exactly the
         * granularity a streak needs; the run itself is folded here rather than in the UI so the
         * card receives one finished fact.
         */
        private val listeningDays =
            selectedYear
                .flatMapLatest { year ->
                    database.listeningByDate(
                        fromTimestamp = getYearStartTimestamp(year),
                        toTimestamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val recapData =
            combine(
                topSongsStats,
                topArtists,
                topAlbums,
                listeningTotals,
            ) { songStats, artists, albums, totals ->
                YearInMusicRecapData(
                    topSongsStats = songStats,
                    topArtists = artists,
                    topAlbums = albums,
                    totalListeningTime = totals.totalTimeListened,
                    totalSongsPlayed = totals.totalPlayCount.toLong(),
                )
            }

        val uiState: StateFlow<YearInMusicUiState> =
            combine(
                selectedYear,
                availableYears,
                recapData,
                activeDays,
                listeningDays,
            ) { year, years, data, days, dayRows ->
                YearInMusicUiState.Content(
                    selectedYear = year,
                    availableYears = years,
                    topSongsStats = data.topSongsStats,
                    topArtists = data.topArtists,
                    topAlbums = data.topAlbums,
                    totalListeningTime = data.totalListeningTime,
                    totalSongsPlayed = data.totalSongsPlayed,
                    activeDays = days,
                    longestStreak = longestStreak(dayRows),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    YearInMusicUiState.Content(
                        selectedYear = LocalDateTime.now().year,
                        availableYears = listOf(LocalDateTime.now().year),
                    ),
            )

        fun selectYear(year: Int) {
            selectedYear.value = year
        }

        init {
            viewModelScope.launch {
                topArtists.collect { artists ->
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
                topAlbums.collect { albums ->
                    albums
                        .filter { it.album.songCount == 0 }
                        .forEach { album ->
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
    }

/**
 * The longest run of consecutive days with listening in [days], and where it fell.
 *
 * Rows arrive day-ordered with one key per calendar day, but a repeated or out-of-order key
 * would otherwise inflate a run silently, so consecutive means exactly one day apart.
 */
private fun longestStreak(days: List<ListeningByDate>): YearInMusicStreak? {
    if (days.isEmpty()) return null
    var bestDays = 0
    var bestStart: LocalDate? = null
    var bestEnd: LocalDate? = null
    var run = 0
    var runStart: LocalDate? = null
    var previous: LocalDate? = null
    days.forEach { row ->
        val date = runCatching { LocalDate.parse(row.day) }.getOrNull() ?: return@forEach
        val previousDay = previous
        val continues = previousDay != null && previousDay.plusDays(1) == date
        run = if (continues) run + 1 else 1
        if (!continues) runStart = date
        if (run > bestDays) {
            bestDays = run
            bestStart = runStart
            bestEnd = date
        }
        previous = date
    }
    val start = bestStart ?: return null
    val end = bestEnd ?: return null
    return YearInMusicStreak(days = bestDays, start = start, end = end)
}

/** A finished streak fact: how many days, and the window they fell in. */
@Immutable
data class YearInMusicStreak(
    val days: Int,
    val start: LocalDate,
    val end: LocalDate,
)

@Immutable
private data class YearInMusicRecapData(
    val totalListeningTime: Long,
    val totalSongsPlayed: Long,
    val topSongsStats: List<SongWithStats>,
    val topArtists: List<Artist>,
    val topAlbums: List<Album>,
)

sealed interface YearInMusicUiState {
    val selectedYear: Int
    val availableYears: List<Int>

    @Immutable
    data class Content(
        override val selectedYear: Int,
        override val availableYears: List<Int>,
        val totalListeningTime: Long = 0L,
        val totalSongsPlayed: Long = 0L,
        val activeDays: Int = 0,
        val longestStreak: YearInMusicStreak? = null,
        val topSongsStats: List<SongWithStats> = emptyList(),
        val topArtists: List<Artist> = emptyList(),
        val topAlbums: List<Album> = emptyList(),
    ) : YearInMusicUiState {
        val hasData: Boolean
            get() = topSongsStats.isNotEmpty() || topArtists.isNotEmpty() || topAlbums.isNotEmpty()
    }
}
