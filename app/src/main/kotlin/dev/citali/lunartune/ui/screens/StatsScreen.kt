/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.StatPeriod
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.ListeningBySlot
import dev.citali.lunartune.db.entities.ListeningSummary
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.db.entities.SongWithStats
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.ChoiceChipsRow
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.ItemThumbnail
import dev.citali.lunartune.ui.component.LocalAlbumsGrid
import dev.citali.lunartune.ui.component.LocalArtistsGrid
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.menu.AlbumMenu
import dev.citali.lunartune.ui.menu.ArtistMenu
import dev.citali.lunartune.ui.menu.SongMenu
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.joinByBullet
import dev.citali.lunartune.utils.makeTimeString
import dev.citali.lunartune.viewmodels.ListeningPatternSlot
import dev.citali.lunartune.viewmodels.StatsScreenState
import dev.citali.lunartune.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayingFlow: Flow<Boolean> =
        remember(playerConnection) { playerConnection?.isPlaying ?: flowOf(false) }
    val mediaMetadataFlow: Flow<MediaMetadata?> =
        remember(playerConnection) { playerConnection?.mediaMetadata ?: flowOf(null) }
    val isPlaying by isPlayingFlow.collectAsStateWithLifecycle(initialValue = false)
    val mediaMetadata by mediaMetadataFlow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isYearPickerOpen by viewModel.yearPickerOpen.collectAsStateWithLifecycle()

    val data =
        when (val state = screenState) {
            StatsScreenState.Loading -> {
                StatsStatusScreen(
                    navController = navController,
                    loading = true,
                )
                return
            }

            StatsScreenState.Empty -> {
                StatsStatusScreen(navController = navController)
                return
            }

            is StatsScreenState.Error -> {
                StatsStatusScreen(
                    navController = navController,
                    errorMessage = stringResource(state.messageResId),
                    onRetry = viewModel::retry,
                )
                return
            }

            is StatsScreenState.Success -> {
                state.data
            }
        }

    val indexChips = data.selectedPeriodIndex
    val mostPlayedSongs = data.mostPlayedSongs
    val mostPlayedSongsStats = data.visibleRankedSongs
    val mostPlayedArtists = data.mostPlayedArtists
    val mostPlayedAlbums = data.mostPlayedAlbums
    val firstEvent = data.firstEvent
    val selectedOption = data.selectedOption
    val listeningByHour = data.listeningByHour
    val listeningByDayOfWeek = data.listeningByDayOfWeek
    val listeningSummary = data.listeningSummary
    val songsById = remember(mostPlayedSongs) { mostPlayedSongs.associateBy { it.id } }

    val coroutineScope = rememberCoroutineScope()
    val currentDate = remember { LocalDateTime.now() }

    val availableYears =
        remember(currentDate, firstEvent) {
            val startYear = firstEvent?.event?.timestamp?.year ?: currentDate.year
            (currentDate.year downTo startYear).toList()
        }

    val weeklyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(first.event.timestamp.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")
                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)
                    val text =
                        when {
                            date.year != currentDate.year -> "$startDateFormatted, ${date.year} - $endDateFormatted, ${endDate.year}"
                            date.month != endDate.month -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    Pair(index, text)
                }.toList()
        }

    val monthlyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) { it.minusMonths(1) }
                .takeWhile { it.isAfter(first.event.timestamp.withDayOfMonth(1)) }
                .mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val text = if (date.year != currentDate.year) "${formatter.format(date)} ${date.year}" else formatter.format(date)
                    Pair(index, text)
                }.toList()
        }

    val yearlyDates =
        remember(currentDate, firstEvent) {
            val first = firstEvent ?: return@remember emptyList<Pair<Int, String>>()
            generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) { it.minusYears(1) }
                .takeWhile { it.isAfter(first.event.timestamp) }
                .mapIndexed { index, date -> Pair(index, "${date.year}") }
                .toList()
        }

    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = stringResource(R.string.settings_stats_subtitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showYearPicker,
                        onLongClick = {},
                    ) {
                        Icon(
                            painterResource(R.drawable.auto_awesome),
                            contentDescription = stringResource(R.string.year_in_music),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                modifier =
                    Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = scaffoldPadding.calculateTopPadding()),
            ) {
                item(key = "rangeControls", contentType = "controls") {
                    StatsFilterPanel(modifier = Modifier.animateItem()) {
                        ChoiceChipsRow(
                            chips =
                                when (selectedOption) {
                                    OptionStats.WEEKS -> {
                                        weeklyDates
                                    }

                                    OptionStats.MONTHS -> {
                                        monthlyDates
                                    }

                                    OptionStats.YEARS -> {
                                        yearlyDates
                                    }

                                    OptionStats.CONTINUOUS -> {
                                        listOf(
                                            StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
                                            StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
                                            StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
                                            StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
                                            StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
                                            StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                                        )
                                    }
                                },
                            options =
                                listOf(
                                    OptionStats.CONTINUOUS to stringResource(R.string.continuous),
                                    OptionStats.WEEKS to stringResource(R.string.weeks),
                                    OptionStats.MONTHS to stringResource(R.string.months),
                                    OptionStats.YEARS to stringResource(R.string.years),
                                ),
                            selectedOption = selectedOption,
                            onSelectionChange = viewModel::onOptionSelected,
                            currentValue = indexChips,
                            onValueUpdate = viewModel::onChipIndexChanged,
                        )
                    }
                }

                if (!data.hasPlaysInPeriod) {
                    item(key = "periodEmpty", contentType = "status") {
                        StatsPeriodEmptyMessage(modifier = Modifier.animateItem())
                    }
                    item(key = "listeningPatternsEmptyPeriod", contentType = "insights") {
                        StatsListeningPatterns(
                            daySlots = listeningByDayOfWeek,
                            hourSlots = listeningByHour,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    return@LazyColumn
                }

                item(key = "overview", contentType = "overview") {
                    StatsSummarySection(
                        summary = listeningSummary,
                        trendBuckets = data.listeningTrendBuckets,
                        previousPeriodTimeListened = data.previousPeriodTimeListened,
                        comparisonLabelResId = data.comparisonLabelResId,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "artistDistribution", contentType = "insights") {
                    if (mostPlayedArtists.isNotEmpty()) {
                        Column(modifier = Modifier.animateItem()) {
                            StatsSectionHeader(
                                title = stringResource(R.string.stats_artist_breakdown),
                                supportingText = mostPlayedArtists.take(5).size.toString(),
                            )
                            SegmentedArtistChart(
                                artists = mostPlayedArtists,
                                totalTimeListened = listeningSummary.totalTimeListened,
                                animationKey = selectedOption to indexChips,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                item(key = "spotlights", contentType = "spotlights") {
                    val topSong = mostPlayedSongsStats.firstOrNull()
                    StatsHighlightsSection(
                        topArtist = mostPlayedArtists.firstOrNull(),
                        topSong = topSong,
                        topSongEntity =
                            topSong?.let { rankedSong ->
                                mostPlayedSongs.firstOrNull { it.id == rankedSong.id }
                            },
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "listeningPatterns", contentType = "insights") {
                    StatsListeningPatterns(
                        daySlots = listeningByDayOfWeek,
                        hourSlots = listeningByHour,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "mostPlayedSongsHeader", contentType = "sectionHeader") {
                    StatsSongsHeader(
                        title = stringResource(R.string.stats_top_songs),
                        count = data.rankedSongCount,
                        shuffleEnabled = playerConnection != null && mostPlayedSongs.isNotEmpty(),
                        onShuffle = {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.most_played_songs),
                                    items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                                ),
                            )
                        },
                        modifier = Modifier.animateItem(),
                    )
                }

                val visibleRankedSongs = mostPlayedSongsStats

                itemsIndexed(
                    items = visibleRankedSongs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "ranked_song" },
                ) { index, song ->
                    val songEntity = songsById[song.id] ?: return@itemsIndexed
                    RankedSongItem(
                        song = song,
                        rank = index + 1,
                        count = visibleRankedSongs.size,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection?.player?.togglePlayPause()
                            } else {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        endpoint = WatchEndpoint(song.id),
                                        preloadItem = songEntity.toMediaMetadata(),
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = songEntity,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (data.canExpandSongList) {
                    item(key = "songListExpansion", contentType = "sectionAction") {
                        TextButton(
                            onClick = viewModel::toggleSongListExpanded,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .animateItem(),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (data.isSongListExpanded) {
                                            R.drawable.expand_less
                                        } else {
                                            R.drawable.expand_more
                                        },
                                    ),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text =
                                    if (data.isSongListExpanded) {
                                        stringResource(R.string.stats_show_top_songs)
                                    } else {
                                        stringResource(R.string.stats_show_all_songs, data.rankedSongCount)
                                    },
                            )
                        }
                    }
                }

                item(key = "mostPlayedArtists", contentType = "sectionHeader") {
                    StatsSectionHeader(
                        title = stringResource(R.string.artists),
                        supportingText = mostPlayedArtists.size.toString(),
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "artistsShelf", contentType = "artists_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = mostPlayedArtists,
                            key = { artist -> artist.id },
                            contentType = { "artist" },
                        ) { artist ->
                            LocalArtistsGrid(
                                title = artist.artist.name,
                                subtitle =
                                    joinByBullet(
                                        pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                                        makeTimeString(artist.timeListened?.toLong()),
                                    ),
                                thumbnailUrl = artist.artist.thumbnailUrl,
                                modifier =
                                    Modifier
                                        .width(164.dp)
                                        .combinedClickable(
                                            onClick = { navController.navigate("artist/${artist.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = artist,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                            )
                        }
                    }
                }

                item(key = "mostPlayedAlbumsHeader", contentType = "sectionHeader") {
                    StatsSectionHeader(
                        title = stringResource(R.string.albums),
                        supportingText = mostPlayedAlbums.size.toString(),
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "albumsRow", contentType = "albums_row") {
                    if (mostPlayedAlbums.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(
                                items = mostPlayedAlbums,
                                key = { _, album -> album.id },
                                contentType = { _, _ -> "album_grid" },
                            ) { index, album ->
                                val playCount = album.songCountListened ?: 0
                                LocalAlbumsGrid(
                                    title = "${index + 1}. ${album.album.title}",
                                    subtitle =
                                        joinByBullet(
                                            pluralStringResource(R.plurals.n_time, playCount, playCount),
                                            makeTimeString(album.timeListened?.toLong()),
                                        ),
                                    thumbnailUrl = album.album.thumbnailUrl,
                                    isActive = album.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    modifier =
                                        Modifier
                                            .width(172.dp)
                                            .combinedClickable(
                                                onClick = { navController.navigate("album/${album.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            if (isYearPickerOpen) {
                StatsYearPickerDialog(
                    availableYears = availableYears,
                    selectedYear = currentDate.year,
                    onSelectYear = { year ->
                        viewModel.dismissYearPicker()
                        navController.navigate("year_in_music?year=$year")
                    },
                    onDismiss = viewModel::dismissYearPicker,
                )
            }
        }
    }
}

@Composable
private fun StatsStatusScreen(
    navController: NavController,
    loading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text =
                            if (errorMessage == null) {
                                stringResource(R.string.stats_empty_title)
                            } else {
                                errorMessage
                            },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (errorMessage == null) {
                        Text(
                            text = stringResource(R.string.stats_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (onRetry != null) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPeriodEmptyMessage(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_period_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.stats_period_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsFilterPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_time_range),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun StatsSongsHeader(
    title: String,
    count: Int,
    shuffleEnabled: Boolean,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = shuffleEnabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
}

@Composable
private fun StatsListeningPatterns(
    daySlots: List<ListeningPatternSlot>,
    hourSlots: List<ListeningPatternSlot>,
    modifier: Modifier = Modifier,
) {
    if (daySlots.isEmpty() && hourSlots.isEmpty()) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_listening_patterns),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        BoxWithConstraints {
            if (maxWidth >= 720.dp && daySlots.isNotEmpty() && hourSlots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListeningByDayChart(
                        slots = daySlots,
                        modifier = Modifier.weight(1f),
                    )
                    ListeningByHourChart(
                        slots = hourSlots,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (daySlots.isNotEmpty()) {
                        ListeningByDayChart(
                            slots = daySlots,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hourSlots.isNotEmpty()) {
                        ListeningByHourChart(
                            slots = hourSlots,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSectionHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RankedSongItem(
    song: SongWithStats,
    rank: Int,
    count: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowShapes = ListItemDefaults.segmentedShapes(index = rank - 1, count = count)
    val click = remember(song.id, onClick) { onClick }
    val longClick = remember(song.id, onLongClick) { onLongClick }

    SegmentedListItem(
        onClick = click,
        onLongClick = longClick,
        shapes = rowShapes,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 1.dp),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (rank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.small,
                    maxSizePx = 200,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text =
                    joinByBullet(
                        pluralStringResource(
                            R.plurals.n_time,
                            song.songCountListened,
                            song.songCountListened,
                        ),
                        makeTimeString(song.timeListened),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsYearPickerDialog(
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.year_in_music),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    items = availableYears,
                    key = { year -> year },
                    contentType = { "year_chip" },
                ) { year ->
                    val isSelected = year == selectedYear
                    Text(
                        text = year.toString(),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    },
                                ).clickable { onSelectYear(year) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
    )
}

@Composable
private fun StatsSummarySection(
    summary: ListeningSummary,
    trendBuckets: List<ListeningBySlot>,
    previousPeriodTimeListened: Long?,
    comparisonLabelResId: Int?,
    modifier: Modifier = Modifier,
) {
    if (summary.totalPlayCount == 0) return

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
            val expanded = maxWidth >= 680.dp
            if (expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatsListeningTimeHero(
                        summary = summary,
                        trendBuckets = trendBuckets,
                        previousPeriodTimeListened = previousPeriodTimeListened,
                        comparisonLabelResId = comparisonLabelResId,
                        modifier = Modifier.weight(1.2f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatsListeningTimeHero(
                        summary = summary,
                        trendBuckets = trendBuckets,
                        previousPeriodTimeListened = previousPeriodTimeListened,
                        comparisonLabelResId = comparisonLabelResId,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsListeningTimeHero(
    summary: ListeningSummary,
    trendBuckets: List<ListeningBySlot>,
    previousPeriodTimeListened: Long?,
    comparisonLabelResId: Int?,
    modifier: Modifier = Modifier,
) {
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    val comparisonLabel =
        if (comparisonLabelResId != null) {
            stringResource(comparisonLabelResId)
        } else {
            null
        }
    val trendText =
        when {
            comparisonLabel == null -> stringResource(R.string.stats_trend_all_time)
            previousPeriodTimeListened == null -> stringResource(R.string.stats_trend_no_comparison)
            previousPeriodTimeListened <= 0L -> stringResource(R.string.stats_trend_no_previous_listening)
            else -> {
                val percentChange =
                    (
                        (summary.totalTimeListened.toDouble() - previousPeriodTimeListened.toDouble()) /
                            previousPeriodTimeListened.toDouble() * 100.0
                    ).roundToInt()
                when {
                    percentChange > 0 ->
                        stringResource(R.string.stats_trend_increase, percentChange, comparisonLabel)

                    percentChange < 0 ->
                        stringResource(R.string.stats_trend_decrease, -percentChange, comparisonLabel)

                    else -> stringResource(R.string.stats_trend_unchanged, comparisonLabel)
                }
            }
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_total_time_listened),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
            )
            Text(
                text = makeTimeString(summary.totalTimeListened) ?: "-",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
            Text(
                text = trendText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
            )
            StatsTrendMiniChart(
                buckets = trendBuckets,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatsTrendMiniChart(
    buckets: List<ListeningBySlot>,
    modifier: Modifier = Modifier,
) {
    val values =
        remember(buckets) {
            val bucketMap = buckets.associateBy { it.slot }
            (0 until 7).map { bucketMap[it]?.timeListened ?: 0L }
        }
    val maxTime = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_listening_trend),
            style = MaterialTheme.typography.labelSmall,
            color = barColor.copy(alpha = 0.72f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val fraction = (value.toDouble() / maxTime).toFloat().coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(350),
                    label = "trend_bucket_$index",
                )
                Box(
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.42f)
                                .height((28 * animatedFraction).dp.coerceAtLeast(2.dp))
                                .clip(CircleShape)
                                .background(barColor.copy(alpha = 0.3f + 0.7f * animatedFraction)),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsHighlightsSection(
    topArtist: Artist?,
    topSong: SongWithStats?,
    topSongEntity: Song?,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_artist),
                mainText = topArtist.artist.name,
                subText = "${topArtist.songCount} ${stringResource(
                    R.string.songs,
                ).lowercase()} • ${makeTimeString(topArtist.timeListened?.toLong())}",
                imageUrl = topArtist.artist.thumbnailUrl,
                useCircleShape = true,
                onClick = { navController.navigate("artist/${topArtist.id}") },
            )
        }
        if (topSong != null && topSongEntity != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_song),
                mainText = topSong.title,
                subText = "${pluralStringResource(
                    R.plurals.n_time,
                    topSong.songCountListened,
                    topSong.songCountListened,
                )} • ${makeTimeString(topSong.timeListened)}",
                imageUrl = topSong.thumbnailUrl,
                useCircleShape = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    useCircleShape: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(if (useCircleShape) CircleShape else MaterialTheme.shapes.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class ArtistBreakdownSegment(
    val key: String,
    val artist: Artist?,
    val startAngle: Float,
    val sweepAngle: Float,
    val percentage: Int,
)

private fun buildArtistBreakdownSegments(
    artists: List<Artist>,
    totalTimeListened: Long,
): List<ArtistBreakdownSegment> {
    val rankedArtists =
        artists
            .mapNotNull { artist ->
                val time = artist.timeListened?.toLong() ?: 0L
                if (time <= 0L) null else artist to time
            }.sortedByDescending { it.second }
    val artistTotal = rankedArtists.sumOf { it.second }
    val displayTotal = totalTimeListened.takeIf { it > 0L } ?: artistTotal
    val allocationTotal = maxOf(displayTotal, artistTotal)
    if (allocationTotal <= 0L) return emptyList()

    val topArtists = rankedArtists.take(5)
    val topArtistTime = topArtists.sumOf { it.second }
    val otherTime = (allocationTotal - topArtistTime).coerceAtLeast(0L)
    val weightedSegments =
        buildList<Pair<Artist?, Long>> {
            topArtists.forEach { (artist, time) -> add(artist to time) }
            if (otherTime > 0L) add(null to otherTime)
        }
    val weights = weightedSegments.map { it.second }
    val percentages = allocatePercentagesToHundred(weights)

    var startAngle = -90f
    return weightedSegments.mapIndexed { index, (artist, weight) ->
        val sweep =
            if (index == weightedSegments.lastIndex) {
                (270f - startAngle).coerceAtLeast(0f)
            } else {
                (weight.toDouble() / allocationTotal.toDouble() * 360.0).toFloat()
            }
        ArtistBreakdownSegment(
            key = artist?.id ?: "other",
            artist = artist,
            startAngle = startAngle,
            sweepAngle = sweep,
            percentage = percentages[index],
        ).also {
            startAngle += sweep
        }
    }
}

private fun allocatePercentagesToHundred(weights: List<Long>): List<Int> {
    if (weights.isEmpty()) return emptyList()
    val total = weights.sum().toDouble()
    if (total <= 0.0) return List(weights.size) { 0 }

    val exactPercentages = weights.map { it.toDouble() * 100.0 / total }
    val percentages = exactPercentages.map { it.toInt() }.toMutableList()
    val remainder = 100 - percentages.sum()
    exactPercentages.indices
        .sortedByDescending { exactPercentages[it] - percentages[it] }
        .take(remainder)
        .forEach { percentages[it] += 1 }
    return percentages
}

@Composable
private fun SegmentedArtistChart(
    artists: List<Artist>,
    totalTimeListened: Long,
    animationKey: Any,
    modifier: Modifier = Modifier,
) {
    val segmentData = remember(artists, totalTimeListened) { buildArtistBreakdownSegments(artists, totalTimeListened) }
    if (segmentData.isEmpty()) return

    val context = LocalContext.current
    val photoColors = remember { mutableStateOf<Map<String, Color>>(emptyMap()) }
    val photoSources =
        remember(segmentData) {
            segmentData
                .mapNotNull { segment ->
                    segment.artist?.let { artist ->
                        artist.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { artist.id to it }
                    }
                }.distinctBy { it.first }
        }
    LaunchedEffect(photoSources) {
        val artworkBitmaps =
            withContext(Dispatchers.IO) {
                val loaded = mutableListOf<Pair<String, android.graphics.Bitmap>>()
                for ((artistId, url) in photoSources) {
                    val bitmap =
                        runCatching {
                            val request =
                                ImageRequest
                                    .Builder(context)
                                    .data(url)
                                    .size(96, 96)
                                    .allowHardware(false)
                                    .build()
                            context.imageLoader.execute(request).image?.toBitmap()
                        }.getOrNull()
                    if (bitmap != null) loaded += artistId to bitmap
                }
                loaded
            }
        photoColors.value =
            withContext(Dispatchers.Default) {
                artworkBitmaps.mapNotNull { (artistId, bitmap) ->
                    val palette =
                        runCatching {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(16)
                                .resizeBitmapArea(64 * 64)
                                .generate()
                        }.getOrNull()
                    val swatch =
                        palette?.vibrantSwatch
                            ?: palette?.lightVibrantSwatch
                            ?: palette?.mutedSwatch
                            ?: palette?.dominantSwatch
                    swatch?.let { artistId to Color(it.rgb) }
                }.toMap()
            }
    }

    val segmentKeys = remember(segmentData) { segmentData.map { it.key } }
    val selectedKey = remember(animationKey, segmentKeys) { mutableStateOf(segmentKeys.firstOrNull()) }
    val selectedIndex = segmentData.indexOfFirst { it.key == selectedKey.value }.let { if (it >= 0) it else 0 }
    val selectedSegment = segmentData[selectedIndex]
    val sweepAnimation = remember(animationKey) { Animatable(0f) }
    LaunchedEffect(sweepAnimation) {
        sweepAnimation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 950),
        )
    }
    val sweepProgress = sweepAnimation.value

    val primaryColor = MaterialTheme.colorScheme.primary
    val fallbackColors =
        remember(primaryColor, segmentData.size) {
            createDistinctArtistColors(
                seedColor = primaryColor,
                count = segmentData.size,
            )
        }
    val otherColor = MaterialTheme.colorScheme.tertiary
    val segmentColors =
        remember(segmentData, photoColors.value, fallbackColors, otherColor) {
            segmentData.mapIndexed { index, segment ->
                if (segment.artist == null) {
                    otherColor
                } else {
                    photoColors.value[segment.key] ?: fallbackColors[index]
                }
            }
        }
    val selectedColor = segmentColors[selectedIndex]
    val topArtist = segmentData.firstOrNull { it.artist != null }?.artist

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(140.dp)
                            .drawWithCache {
                                val strokeWidth = size.width * 0.18f
                                val inset = strokeWidth / 2f
                                val arcRect =
                                    Rect(
                                        left = inset,
                                        top = inset,
                                        right = size.width - inset,
                                        bottom = size.height - inset,
                                    )
                                val selectedOffsetPx = 5.dp.toPx()
                                onDrawBehind {
                                    val revealEnd = 360f * sweepProgress
                                    segmentData.forEachIndexed { index, segment ->
                                        val segmentStartFromTop = segment.startAngle + 90f
                                        val segmentEndFromTop = segmentStartFromTop + segment.sweepAngle
                                        val revealedSweep =
                                            (minOf(segmentEndFromTop, revealEnd) - segmentStartFromTop)
                                                .coerceAtLeast(0f)
                                        val gapDegrees =
                                            if (segmentData.size > 1) minOf(1.5f, segment.sweepAngle * 0.3f) else 0f
                                        val reachesSegmentEnd = revealedSweep >= segment.sweepAngle
                                        val visibleSweep =
                                            if (reachesSegmentEnd) {
                                                (segment.sweepAngle - gapDegrees).coerceAtLeast(0f)
                                            } else {
                                                (revealedSweep - gapDegrees / 2f).coerceAtLeast(0f)
                                            }
                                        if (visibleSweep > 0.25f) {
                                            val isSelected = index == selectedIndex
                                            val middleAngle = segment.startAngle + segment.sweepAngle / 2f
                                            val radians = middleAngle.toDouble() * PI / 180.0
                                            val dx = if (isSelected) cos(radians).toFloat() * selectedOffsetPx else 0f
                                            val dy = if (isSelected) sin(radians).toFloat() * selectedOffsetPx else 0f
                                            withTransform({ translate(left = dx, top = dy) }) {
                                                drawArc(
                                                    color =
                                                        segmentColors[index].copy(
                                                            alpha = if (isSelected) 1f else 0.36f,
                                                        ),
                                                    startAngle = segment.startAngle + gapDegrees / 2f,
                                                    sweepAngle = visibleSweep,
                                                    useCenter = false,
                                                    topLeft = arcRect.topLeft,
                                                    size = Size(arcRect.width, arcRect.height),
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                                )
                                            }
                                        }
                                    }
                                }
                            }.pointerInput(animationKey, segmentData) {
                                detectTapGestures { offset ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val distance = sqrt(dx * dx + dy * dy)
                                    val outerRadius = size.minDimension / 2f
                                    val innerRadius = outerRadius - size.width * 0.18f
                                    val hitSlop = 8.dp.toPx()
                                    if (distance < innerRadius - hitSlop || distance > outerRadius + hitSlop) {
                                        return@detectTapGestures
                                    }

                                    val rawAngle = atan2(dy, dx) * 180f / PI.toFloat()
                                    val angleFromTop = (rawAngle + 90f + 360f) % 360f
                                    var sweepStart = 0f
                                    val tappedIndex =
                                        segmentData.indexOfFirst { segment ->
                                            val containsAngle =
                                                angleFromTop >= sweepStart &&
                                                    angleFromTop < sweepStart + segment.sweepAngle
                                            sweepStart += segment.sweepAngle
                                            containsAngle
                                        }
                                    if (tappedIndex >= 0) selectedKey.value = segmentData[tappedIndex].key
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.width(86.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (selectedSegment.artist != null) {
                            AsyncImage(
                                model = selectedSegment.artist.thumbnailUrl,
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.person),
                                error = painterResource(R.drawable.person),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(34.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(selectedColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_horiz),
                                    contentDescription = null,
                                    tint = selectedColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Text(
                            text = "${selectedSegment.percentage}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = selectedSegment.artist?.artist?.name ?: stringResource(R.string.stats_other),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    segmentData.forEachIndexed { index, segment ->
                        val isSelected = index == selectedIndex
                        val alpha = if (isSelected) 1f else 0.48f
                        val rowColor = if (isSelected) segmentColors[index].copy(alpha = 0.12f) else Color.Transparent
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(rowColor)
                                    .clickable { selectedKey.value = segment.key }
                                    .padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (segment.artist != null) {
                                AsyncImage(
                                    model = segment.artist.thumbnailUrl,
                                    contentDescription = null,
                                    placeholder = painterResource(R.drawable.person),
                                    error = painterResource(R.drawable.person),
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .graphicsLayer { this.alpha = alpha },
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(segmentColors[index].copy(alpha = alpha)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_horiz),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiary.copy(alpha = alpha),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Text(
                                text = segment.artist?.artist?.name ?: stringResource(R.string.stats_other),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${segment.percentage}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            )
                        }
                    }
                }
            }

            if (topArtist != null) {
                Text(
                    text =
                        stringResource(
                            R.string.stats_top_artist_time,
                            topArtist.artist.name,
                            makeTimeString(topArtist.timeListened?.toLong() ?: 0L) ?: "-",
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun createDistinctArtistColors(
    seedColor: Color,
    count: Int,
): List<Color> {
    if (count <= 0) return emptyList()

    val seedHsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColor.toArgb(), seedHsv)
    val saturation = seedHsv[1].coerceAtLeast(0.62f)
    val brightness = seedHsv[2].coerceIn(0.68f, 0.88f)
    val hueStep = 360f / count

    return List(count) { index ->
        Color.hsv(
            hue = (seedHsv[0] + hueStep * index) % 360f,
            saturation = saturation,
            value = brightness,
        )
    }
}

@Composable
private fun ListeningByDayChart(
    slots: List<ListeningPatternSlot>,
    modifier: Modifier = Modifier,
) {
    val dayLabels =
        listOf(
            R.string.day_sun,
            R.string.day_mon,
            R.string.day_tue,
            R.string.day_wed,
            R.string.day_thu,
            R.string.day_fri,
            R.string.day_sat,
        )
    val weekdayPluralNames = stringArrayResource(R.array.stats_pattern_weekday_plural)
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = (slots.maxOfOrNull { it.averageTimeListened } ?: 0L).coerceAtLeast(1L)
    val peakSlot = slots.maxByOrNull { it.averageTimeListened }?.takeIf { it.averageTimeListened > 0L }?.slot
    val comparisonSlot =
        slots
            .filter { it.comparisonPercent != null }
            .maxByOrNull { it.currentMonthAverageTimeListened }
    val selectedSlot = remember(slots) { mutableIntStateOf(-1) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val insight =
        peakSlot?.let { stringResource(R.string.stats_pattern_insight_day, weekdayPluralNames[it]) }
            ?: stringResource(R.string.stats_pattern_no_history)
    val comparison =
        comparisonSlot?.let { slot ->
            stringResource(
                R.string.stats_pattern_comparison,
                weekdayPluralNames[slot.slot],
                formatSignedPercent(slot.comparisonPercent ?: 0),
            )
        }

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_listening_by_day),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            PatternChartMeta(insight = insight, comparison = comparison)
            val selected = slotMap[selectedSlot.intValue]
            if (selected != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PatternTooltip(
                    text =
                        stringResource(
                            R.string.stats_pattern_tooltip_day,
                            stringResource(dayLabels[selected.slot]),
                            formatAverageListeningTime(selected.averageTimeListened),
                            selected.occurrenceCount,
                            weekdayPluralNames[selected.slot],
                        ),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                for (day in 0..6) {
                    val slot = slotMap[day]
                    val time = slot?.averageTimeListened ?: 0L
                    val fraction = time.toFloat() / maxTime
                    val isPeak = day == peakSlot
                    val isSelected = day == selectedSlot.intValue
                    val barColor = if (isPeak || isSelected) primaryColor else containerColor
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(400),
                        label = "bar_$day",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedSlot.intValue = day }
                                .padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(24.dp)
                                    .height(80.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(24.dp)
                                        .height((80 * animatedFraction).dp.coerceAtLeast(2.dp))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(barColor),
                            )
                        }
                        Text(
                            text = stringResource(dayLabels[day]),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPeak || isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isPeak || isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningByHourChart(
    slots: List<ListeningPatternSlot>,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h a", Locale.getDefault()) }
    val hourLabels = remember(formatter) { (0..23).map { LocalTime.of(it, 0).format(formatter) } }
    val timeLabels = remember(formatter) { listOf(0, 6, 12, 18, 0).map { LocalTime.of(it, 0).format(formatter) } }
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = (slots.maxOfOrNull { it.averageTimeListened } ?: 0L).coerceAtLeast(1L)
    val peakSlot = slots.maxByOrNull { it.averageTimeListened }?.takeIf { it.averageTimeListened > 0L }?.slot
    val comparisonSlot =
        slots
            .filter { it.comparisonPercent != null }
            .maxByOrNull { it.currentMonthAverageTimeListened }
    val selectedSlot = remember(slots) { mutableIntStateOf(-1) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val insight =
        peakSlot?.let { stringResource(R.string.stats_pattern_insight_hour, hourLabels[it]) }
            ?: stringResource(R.string.stats_pattern_no_history)
    val comparison =
        comparisonSlot?.let { slot ->
            stringResource(
                R.string.stats_pattern_comparison,
                hourLabels[slot.slot],
                formatSignedPercent(slot.comparisonPercent ?: 0),
            )
        }

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_listening_by_hour),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            PatternChartMeta(insight = insight, comparison = comparison)
            val selected = slotMap[selectedSlot.intValue]
            if (selected != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PatternTooltip(
                    text =
                        stringResource(
                            R.string.stats_pattern_tooltip_hour,
                            hourLabels[selected.slot],
                            formatAverageListeningTime(selected.averageTimeListened),
                            selected.occurrenceCount,
                        ),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(slots) {
                            detectTapGestures { offset ->
                                val slotWidth = size.width.toFloat() / 24f
                                selectedSlot.intValue = (offset.x / slotWidth).toInt().coerceIn(0, 23)
                            }
                        },
                verticalAlignment = Alignment.Bottom,
            ) {
                for (hour in 0..23) {
                    val slot = slotMap[hour]
                    val time = slot?.averageTimeListened ?: 0L
                    val fraction = time.toFloat() / maxTime
                    val isPeak = hour == peakSlot
                    val isSelected = hour == selectedSlot.intValue
                    val barColor =
                        if (isPeak || isSelected) {
                            primaryColor
                        } else {
                            containerColor.copy(alpha = 0.6f + fraction * 0.4f)
                        }
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(400),
                        label = "hour_$hour",
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp)
                                    .height((48 * animatedFraction).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(barColor),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                timeLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternChartMeta(
    insight: String,
    comparison: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = insight,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.stats_pattern_period_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (comparison != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = comparison,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PatternTooltip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatAverageListeningTime(durationMs: Long): String {
    if (durationMs <= 0L) return "0 min"
    val minutes = ((durationMs + 30_000L) / 60_000L).coerceAtLeast(1L)
    return when {
        minutes < 60L -> "$minutes min"
        minutes % 60L == 0L -> "${minutes / 60L} hr"
        else -> "${minutes / 60L} hr ${minutes % 60L} min"
    }
}

private fun formatSignedPercent(value: Int): String =
    if (value > 0) "+$value%" else "$value%"

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
