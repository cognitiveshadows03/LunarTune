/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens

import android.content.Intent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.DisableBlurKey
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.db.entities.SongWithStats
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.menu.ArtistMenu
import dev.citali.lunartune.ui.menu.SongMenu
import dev.citali.lunartune.utils.ComposeToImage
import dev.citali.lunartune.utils.joinByBullet
import dev.citali.lunartune.utils.makeTimeString
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.YearInMusicUiState
import dev.citali.lunartune.viewmodels.YearInMusicViewModel
import java.text.NumberFormat
import kotlin.math.absoluteValue
import kotlin.coroutines.resume

private val RecapBlack = Color(0xFF070707)
private val RecapSurfaceHigh = Color(0xFF1D1D1D)
private val RecapRed = Color(0xFFFF0033)
private val RecapRedDeep = Color(0xFFB60024)
private val RecapCream = Color(0xFFFFF7EF)
private val RecapYellow = Color(0xFFFFD447)
private val RecapGreen = Color(0xFF1ED760)
private val RecapPurple = Color(0xFF8A2CFF)
private val RecapBlue = Color(0xFF7CB7FF)
private val RecapPink = Color(0xFFFF8BDE)
private val RecapLime = Color(0xFFDFFF3E)
private val RecapInk = Color(0xFF151515)

private object RecapTokens {
    val ItemRadius = 18.dp
    val ShareVerticalPadding = 14.dp
}

@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    YearInMusicRoute(
        navController = navController,
        viewModel = viewModel,
        initialYear = initialYear,
    )
}

@Composable
private fun YearInMusicRoute(
    navController: NavController,
    viewModel: YearInMusicViewModel,
    initialYear: Int?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialYear) {
        if (initialYear != null) {
            viewModel.selectYear(initialYear)
        }
    }

    YearInMusicRecapScreen(
        navController = navController,
        uiState = uiState,
    )
}

@Composable
private fun YearInMusicRecapScreen(
    navController: NavController,
    uiState: YearInMusicUiState,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val content = uiState as YearInMusicUiState.Content
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    var isGeneratingImage by remember { mutableStateOf(false) }
    var isShareCaptureMode by remember { mutableStateOf(false) }
    var currentCardBounds by remember { mutableStateOf<Rect?>(null) }

    val cards = rememberYearInMusicCards(content)
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val currentPage by remember(cards, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, cards.lastIndex.coerceAtLeast(0)) }
    }
    val canShare by remember(content, isShareCaptureMode, cards, currentPage) {
        derivedStateOf { content.hasData && !isShareCaptureMode && cards.getOrNull(currentPage) != null }
    }

    LaunchedEffect(content.selectedYear) {
        pagerState.scrollToPage(0)
    }

    LaunchedEffect(cards) {
        if (pagerState.currentPage > cards.lastIndex) {
            pagerState.scrollToPage(cards.lastIndex.coerceAtLeast(0))
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RecapBlack),
    ) {
        RecapBackdrop(
            enabled = !disableBlur && !isShareCaptureMode,
            modifier = Modifier.fillMaxSize(),
        )

        RecapCardPager(
            cards = cards,
            pagerState = pagerState,
            isShareCaptureMode = isShareCaptureMode,
            onCardBoundsChanged = { currentCardBounds = it },
            onTopSongLongClick = { song ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    SongMenu(
                        originalSong = song,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onTopArtistLongClick = { artist ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxSize(),
        )

        if (canShare) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                RecapShareButton(
                    isGenerating = isGeneratingImage,
                    onClick = {
                        if (isGeneratingImage) return@RecapShareButton
                        isGeneratingImage = true
                        coroutineScope.launch {
                            try {
                                isShareCaptureMode = true
                                awaitNextPreDraw(view)
                                awaitNextPreDraw(view)

                                val raw =
                                    ComposeToImage.captureViewBitmap(
                                        view = view,
                                        backgroundColor = RecapBlack.toArgb(),
                                    )
                                val bounds = currentCardBounds
                                val cardBitmap =
                                    if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
                                        ComposeToImage.cropBitmap(
                                            source = raw,
                                            left = bounds.left.toInt().coerceAtLeast(0),
                                            top = bounds.top.toInt().coerceAtLeast(0),
                                            width = bounds.width.toInt().coerceAtLeast(1),
                                            height = bounds.height.toInt().coerceAtLeast(1),
                                        )
                                    } else {
                                        raw
                                    }
                                val fitted =
                                    ComposeToImage.fitBitmap(
                                        source = cardBitmap,
                                        targetWidth = 1080,
                                        targetHeight = 1920,
                                        backgroundColor = RecapBlack.toArgb(),
                                    )
                                val uri =
                                    ComposeToImage.saveBitmapAsFile(
                                        context = context,
                                        bitmap = fitted,
                                        fileName = "LunarTune_YearInMusic_${content.selectedYear}_${currentPage + 1}",
                                    )
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.share_summary),
                                    ),
                                )
                            } finally {
                                isShareCaptureMode = false
                                isGeneratingImage = false
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                            ).padding(end = 16.dp, bottom = 16.dp)
                            .widthIn(max = 168.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberYearInMusicCards(content: YearInMusicUiState.Content): List<YearInMusicRecapCard> {
    val introLabel = stringResource(R.string.year_in_music_recap)
    val totalsLabel = stringResource(R.string.total_listening_time)
    val topTrackLabel = stringResource(R.string.year_in_music_top_track)
    val artistsLabel = stringResource(R.string.year_in_music_ranked_artists)
    val albumsLabel = stringResource(R.string.year_in_music_ranked_albums)
    val summaryLabel = stringResource(R.string.share_summary)
    val orbitLabel = stringResource(R.string.year_in_music_orbit_title)
    val emptyLabel = stringResource(R.string.no_listening_data)

    return remember(
        content.selectedYear,
        content.totalListeningTime,
        content.totalSongsPlayed,
        content.activeDays,
        content.topSongsStats,
        content.topSongs,
        content.topArtists,
        content.topAlbums,
        introLabel,
        totalsLabel,
        topTrackLabel,
        artistsLabel,
        albumsLabel,
        summaryLabel,
        orbitLabel,
        emptyLabel,
    ) {
        if (!content.hasData) {
            listOf(YearInMusicRecapCard.Empty(content.selectedYear, emptyLabel))
        } else {
            buildList {
                add(
                    YearInMusicRecapCard.Intro(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        label = introLabel,
                    ),
                )
                add(
                    YearInMusicRecapCard.Totals(
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        label = totalsLabel,
                    ),
                )
                add(
                    YearInMusicRecapCard.ListeningOrbit(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        activeDays = content.activeDays,
                        label = orbitLabel,
                    ),
                )
                content.topSongsStats.firstOrNull()?.let { topSong ->
                    add(
                        YearInMusicRecapCard.TopSong(
                            song = topSong,
                            originalSong = content.topSongs.firstOrNull { it.id == topSong.id } ?: content.topSongs.firstOrNull(),
                            label = topTrackLabel,
                        ),
                    )
                }
                if (content.topArtists.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedArtists(
                            artists = content.topArtists,
                            label = artistsLabel,
                        ),
                    )
                }
                if (content.topAlbums.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedAlbums(
                            albums = content.topAlbums,
                            label = albumsLabel,
                        ),
                    )
                }
                add(
                    YearInMusicRecapCard.Summary(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        topSongs = content.topSongsStats,
                        topArtists = content.topArtists,
                        topAlbums = content.topAlbums,
                        label = summaryLabel,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecapCardPager(
    cards: List<YearInMusicRecapCard>,
    pagerState: PagerState,
    isShareCaptureMode: Boolean,
    onCardBoundsChanged: (Rect) -> Unit,
    onTopSongLongClick: (Song) -> Unit,
    onTopArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val verticalPadding = if (isShareCaptureMode) RecapTokens.ShareVerticalPadding else 0.dp

    HorizontalPager(
        state = pagerState,
        key = { index -> cards.getOrNull(index)?.id ?: "stale_year_in_music_page_$index" },
        userScrollEnabled = !isShareCaptureMode,
        modifier = modifier.padding(vertical = verticalPadding),
    ) { page ->
        val card = cards.getOrNull(page)
        if (card == null) {
            Box(modifier = Modifier.fillMaxSize())
            return@HorizontalPager
        }
        val canAdvance = !isShareCaptureMode && page == pagerState.currentPage && page < cards.lastIndex
        RecapCardFrame(
            card = card,
            applySafeContentInsets = !isShareCaptureMode,
            onCardClick = {
                if (canAdvance) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page + 1)
                    }
                }
            },
            canAdvance = canAdvance,
            onTopSongLongClick = onTopSongLongClick,
            onTopArtistLongClick = onTopArtistLongClick,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageDistance = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
                        val transition = pageDistance.coerceIn(0f, 1f)
                        alpha = 1f - transition * 0.16f
                        scaleX = 1f - transition * 0.018f
                        scaleY = 1f - transition * 0.018f
                    }
                    .onGloballyPositioned { coordinates ->
                        if (page == pagerState.currentPage) {
                            onCardBoundsChanged(coordinates.boundsInRoot())
                        }
                    },
        )
    }
}

@Composable
private fun RecapCardFrame(
    card: YearInMusicRecapCard,
    applySafeContentInsets: Boolean,
    onCardClick: () -> Unit,
    canAdvance: Boolean,
    onTopSongLongClick: (Song) -> Unit,
    onTopArtistLongClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient =
        remember(card.id) {
            when (card) {
                is YearInMusicRecapCard.Empty -> listOf(RecapSurfaceHigh, RecapBlack)
                is YearInMusicRecapCard.Intro -> listOf(Color(0xFF43131D), RecapBlack, Color(0xFF17121B))
                is YearInMusicRecapCard.Totals -> listOf(Color(0xFF32100F), RecapBlack, Color(0xFF171217))
                is YearInMusicRecapCard.TopSong -> listOf(RecapBlack, Color(0xFF251015), RecapBlack)
                is YearInMusicRecapCard.RankedArtists -> listOf(Color(0xFF101D18), RecapBlack, Color(0xFF101714))
                is YearInMusicRecapCard.RankedAlbums -> listOf(Color(0xFF231B0A), RecapBlack, Color(0xFF17140D))
                is YearInMusicRecapCard.Summary -> listOf(Color(0xFFE9B4FF), Color(0xFFD7E7FF), Color(0xFFFF8CA1))
                is YearInMusicRecapCard.ListeningOrbit -> listOf(Color(0xFF263A2A), RecapBlack, Color(0xFF21150E))
            }
        }

    Surface(
        modifier = modifier.clickable(enabled = canAdvance, onClick = onCardClick),
        color = RecapBlack,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradient)),
        ) {
            RecapNoiseOverlay(modifier = Modifier.fillMaxSize())
            RecapAtmosphere(
                accent = recapAccentFor(card),
                polygon = recapPolygonFor(card),
                modifier = Modifier.fillMaxSize(),
            )

            when (card) {
                is YearInMusicRecapCard.Empty -> {
                    EmptyRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Intro -> {
                    IntroRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Totals -> {
                    TotalsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.TopSong -> {
                    TopSongRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                        onClick = onCardClick,
                        onLongClick = { card.originalSong?.let(onTopSongLongClick) },
                    )
                }

                is YearInMusicRecapCard.RankedArtists -> {
                    RankedArtistsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                        onArtistLongClick = onTopArtistLongClick,
                    )
                }

                is YearInMusicRecapCard.RankedAlbums -> {
                    RankedAlbumsRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.Summary -> {
                    SummaryRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }

                is YearInMusicRecapCard.ListeningOrbit -> {
                    ListeningOrbitRecapCard(
                        card = card,
                        applySafeContentInsets = applySafeContentInsets,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecapCard(
    card: YearInMusicRecapCard.Empty,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.year_in_music_recap),
        footer = joinByBullet(stringResource(R.string.app_name), card.year.toString()),
        verticalArrangement = Arrangement.Center,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        IconBadge(
            icon = R.drawable.stats,
            background = RecapRed,
            tint = RecapCream,
            modifier = Modifier.size(78.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.year_in_music_empty_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.year_in_music_empty_subtitle, card.year),
            style = MaterialTheme.typography.bodyLarge,
            color = RecapCream.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IntroRecapCard(
    card: YearInMusicRecapCard.Intro,
    applySafeContentInsets: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF56C8F2),
                                Color(0xFF77D77C),
                                RecapPink,
                                Color(0xFFB7B5FF),
                            ),
                    ),
                ).then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(176.dp)
                    .graphicsLayer {
                        rotationZ = -18f
                        translationX = 44f
                        translationY = 30f
                    }.clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFE569), RecapPink, Color(0xFFFF8A42)),
                        ),
                    ),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(11) { index ->
                Box(
                    modifier =
                        Modifier
                            .width(5.dp)
                            .height((92 - index * 3).dp)
                            .background(if (index % 2 == 0) RecapLime else RecapBlue.copy(alpha = 0.76f)),
                )
            }
        }

        LunarTuneBrand(
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = card.year.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 70.sp,
            )
            Text(
                text = stringResource(R.string.year_in_music_recap_word),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 66.sp,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.year_in_music_intro_hero),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 30.sp,
            )
            Text(
                text = stringResource(R.string.year_in_music_intro_details),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.82f),
                lineHeight = 19.sp,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(RecapInk)
                        .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.year_in_music_swipe_begin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TotalsRecapCard(
    card: YearInMusicRecapCard.Totals,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.total_listening_time),
        footer = stringResource(R.string.year_in_music_totals_title),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(
                    when {
                        card.totalListeningTime < 60L * 60_000L -> R.string.recap_humour_short
                        card.totalListeningTime < 1_000L * 60_000L -> R.string.recap_humour_medium
                        else -> R.string.recap_humour_long
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 32.sp,
            )
            Text(
                text = makeTimeString(card.totalListeningTime),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp),
                fontWeight = FontWeight.Black,
                color = RecapYellow,
                lineHeight = 58.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = NumberFormat.getIntegerInstance().format(card.totalSongsPlayed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.year_in_music_plays_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RecapCream.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopSongRecapCard(
    card: YearInMusicRecapCard.TopSong,
    applySafeContentInsets: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val imageModel = rememberShareSafeImageRequest(card.song.thumbnailUrl)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(18.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    RecapBlack.copy(alpha = 0.28f),
                                    RecapBlack.copy(alpha = 0.74f),
                                    RecapBlack.copy(alpha = 0.96f),
                                ),
                        ),
                    ),
        )
        RecapCardContent(
            badge = "#1 ${stringResource(R.string.year_in_music_top_track)}",
            footer = stringResource(R.string.year_in_music_top_pick),
            verticalArrangement = Arrangement.SpaceBetween,
            applySafeContentInsets = applySafeContentInsets,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(RecapSurfaceHigh)
                            .border(
                                width = 2.dp,
                                color = RecapCream.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(26.dp),
                            ),
                )
                Text(
                    text = card.song.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = RecapCream,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 34.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecapChip(
                    icon = R.drawable.play,
                    text =
                        pluralStringResource(
                            R.plurals.n_time,
                            card.song.songCountListened,
                            card.song.songCountListened,
                        ),
                    color = RecapRed,
                )
                RecapChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(card.song.timeListened),
                    color = RecapYellow,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankedArtistsRecapCard(
    card: YearInMusicRecapCard.RankedArtists,
    applySafeContentInsets: Boolean,
    onArtistLongClick: (Artist) -> Unit,
) {
    val standout = card.artists.firstOrNull()
    RecapCardContent(
        badge = stringResource(R.string.top_artists),
        footer = stringResource(R.string.year_in_music_ranked_artists),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        applySafeContentInsets = applySafeContentInsets,
    ) {
        if (standout != null) {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = {},
                    onLongClick = { onArtistLongClick(standout) },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier.size(116.dp).clip(CircleShape).background(RecapSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = rememberShareSafeImageRequest(standout.artist.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                    Box(Modifier.fillMaxSize().border(2.dp, RecapGreen.copy(alpha = 0.8f), CircleShape))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.top_artists).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = RecapGreen,
                    )
                    Text(
                        text = standout.artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = RecapCream,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 30.sp,
                    )
                    Text(
                        text = makeTimeString(standout.timeListened?.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = RecapCream.copy(alpha = 0.76f),
                        maxLines = 1,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.artists.drop(1).take(4).forEachIndexed { index, artist ->
                RankedArtistRow(
                    rank = index + 2,
                    artist = artist,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onArtistLongClick(artist) }),
                )
            }
        }
    }
}

@Composable
private fun RankedAlbumsRecapCard(
    card: YearInMusicRecapCard.RankedAlbums,
    applySafeContentInsets: Boolean,
) {
    val standout = card.albums.firstOrNull()
    RecapCardContent(
        badge = stringResource(R.string.albums),
        footer = stringResource(R.string.year_in_music_ranked_albums),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        applySafeContentInsets = applySafeContentInsets,
    ) {
        if (standout != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AsyncImage(
                    model = rememberShareSafeImageRequest(standout.thumbnailUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(124.dp).clip(RoundedCornerShape(22.dp)).background(RecapSurfaceHigh),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.albums).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = RecapYellow,
                    )
                    Text(
                        text = standout.album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = RecapCream,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 29.sp,
                    )
                    Text(
                        text = standout.artists.take(2).joinToString(" / ") { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = RecapCream.copy(alpha = 0.76f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.albums.drop(1).take(4).forEachIndexed { index, album ->
                RankedAlbumRow(rank = index + 2, album = album)
            }
        }
    }
}

@Composable
private fun SummaryRecapCard(
    card: YearInMusicRecapCard.Summary,
    applySafeContentInsets: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFFD66E), Color(0xFFFF9E89), Color(0xFFE7B5FF)),
                    ),
                ).then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = RecapInk.copy(alpha = 0.68f),
                )
                Text(
                    text = "${card.year} ${stringResource(R.string.year_in_music_recap_word)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = RecapInk,
                    lineHeight = 42.sp,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.year_in_music_minutes_label).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = RecapInk.copy(alpha = 0.72f),
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = formatListeningMinutes(card.totalListeningTime),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                        fontWeight = FontWeight.Black,
                        color = RecapInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.year_in_music_minutes_unit).uppercase(),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = RecapInk.copy(alpha = 0.75f),
                    )
                }
                Text(
                    text = "${NumberFormat.getIntegerInstance().format(card.totalSongsPlayed)} ${stringResource(R.string.year_in_music_plays_short)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RecapInk.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                card.topSongs.firstOrNull()?.let { song ->
                    FinalePick(
                        label = stringResource(R.string.year_in_music_top_track),
                        title = song.title,
                        imageData = song.thumbnailUrl,
                    )
                }
                card.topArtists.firstOrNull()?.let { artist ->
                    FinalePick(
                        label = stringResource(R.string.top_artists),
                        title = artist.artist.name,
                        imageData = artist.artist.thumbnailUrl,
                    )
                }
                card.topAlbums.firstOrNull()?.let { album ->
                    FinalePick(
                        label = stringResource(R.string.albums),
                        title = album.album.title,
                        imageData = album.thumbnailUrl,
                    )
                }
            }

            LunarTuneBrand(contentColor = RecapInk)
        }
    }
}

@Composable
private fun FinalePick(
    label: String,
    title: String?,
    imageData: Any?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(RecapInk.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = rememberShareSafeImageRequest(imageData),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = RecapInk.copy(alpha = 0.65f),
                maxLines = 1,
            )
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = RecapInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ListeningOrbitRecapCard(
    card: YearInMusicRecapCard.ListeningOrbit,
    applySafeContentInsets: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ListeningOrbitAtmosphere(modifier = Modifier.fillMaxSize())
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    RecapBlack.copy(alpha = 0.26f),
                                    Color.Transparent,
                                    RecapBlack.copy(alpha = 0.44f),
                                ),
                        ),
                    ).then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                    .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // One centred stack, like the reference card: the line sits just
                // above the totals instead of being pinned to the top edge.
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = RecapCream,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                    OrbitRecapMetric(
                        value = NumberFormat.getIntegerInstance().format(card.activeDays),
                        label = stringResource(R.string.year_in_music_active_days),
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrbitRecapMetric(
                            value = NumberFormat.getIntegerInstance().format(card.totalSongsPlayed),
                            label = stringResource(R.string.year_in_music_plays_short),
                            modifier = Modifier.weight(1f),
                        )
                        OrbitRecapMetric(
                            value = formatListeningMinutes(card.totalListeningTime),
                            label = stringResource(R.string.year_in_music_minutes_unit),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Reference-style badge: the mark beside a stacked year and
                // feature name, rather than the wordmark left and year right.
                Row(
                    modifier = Modifier.align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.lunartune_brand_round),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                    )
                    Column {
                        Text(
                            text = "${card.year}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = RecapCream,
                        )
                        Text(
                            text = stringResource(R.string.year_in_music),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RecapCream.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitRecapMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Oversized and tight-tracked like the reference card, with the label
        // tucked under the digits: the line height sits below the font size so
        // the pair reads as one lockup instead of two separated rows.
        Text(
            text = value,
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = 48.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-1.5).sp,
                ),
            fontWeight = FontWeight.Black,
            color = RecapCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = RecapCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListeningOrbitAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Fully gradient backdrop: a deep green base, two warm glows and a
        // vignette. The drawn swirl that preceded this read as a cartoon
        // spiral on real hardware; gradients keep the colour story without
        // the shape.
        val baseCenter = Offset(size.width * 0.48f, size.height * 0.46f)
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color(0xFF354B35), Color(0xFF172118), RecapBlack),
                    center = baseCenter,
                    radius = size.maxDimension * 0.78f,
                ),
            size = size,
        )

        val warmCenter = Offset(size.width * 0.82f, size.height * 0.23f)
        val warmRadius = size.minDimension * 0.52f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color(0xBFFF4326), Color(0x44FF4B28), Color.Transparent),
                    center = warmCenter,
                    radius = warmRadius,
                ),
            center = warmCenter,
            radius = warmRadius,
        )

        val lowerCenter = Offset(size.width * 0.22f, size.height * 0.84f)
        val lowerRadius = size.minDimension * 0.46f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color(0x99FF8A38), Color(0x33FF5A2C), Color.Transparent),
                    center = lowerCenter,
                    radius = lowerRadius,
                ),
            center = lowerCenter,
            radius = lowerRadius,
        )

        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, RecapBlack.copy(alpha = 0.42f)),
                    center = baseCenter,
                    radius = size.maxDimension * 0.74f,
                ),
            size = size,
        )
    }
}

@Composable
private fun LunarTuneBrand(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.lunartune_brand_round),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = contentColor,
        )
    }
}

private fun formatListeningMinutes(duration: Long): String {
    val minutes = (duration / 60_000L).coerceAtLeast(if (duration > 0L) 1L else 0L)
    return NumberFormat.getIntegerInstance().format(minutes)
}

@Composable
private fun RecapCardContent(
    badge: String,
    footer: String,
    verticalArrangement: Arrangement.Vertical,
    applySafeContentInsets: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(24.dp),
        verticalArrangement = verticalArrangement,
    ) {
        RecapBadge(text = badge)
        content()
        Text(
            text = footer,
            style = MaterialTheme.typography.labelMedium,
            color = RecapCream.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RankedArtistRow(
    rank: Int,
    artist: Artist,
    modifier: Modifier = Modifier,
) {
    val imageModel = rememberShareSafeImageRequest(artist.artist.thumbnailUrl)

    RankedRowContainer(modifier = modifier) {
        RankNumber(rank = rank, color = RecapRed)
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = artist.artist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = makeTimeString(artist.timeListened?.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RecapRed,
        )
    }
}

@Composable
private fun RankedAlbumRow(
    rank: Int,
    album: Album,
) {
    val imageModel = rememberShareSafeImageRequest(album.thumbnailUrl)
    val artistNames =
        remember(album.artists) {
            album.artists.take(2).joinToString(" / ") { it.name }
        }

    RankedRowContainer {
        RankNumber(rank = rank, color = RecapYellow)
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = album.album.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistNames.ifBlank { makeTimeString(album.timeListened) },
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = makeTimeString(album.timeListened),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RecapYellow,
        )
    }
}

@Composable
private fun RankedRowContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RecapTokens.ItemRadius))
                .background(RecapSurfaceHigh.copy(alpha = 0.82f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(RecapTokens.ItemRadius),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun RankNumber(
    rank: Int,
    color: Color,
) {
    val rankShape = MaterialShapes.Cookie6Sided.toShape()
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(rankShape)
                .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

@Composable
private fun RecapChip(
    icon: Int,
    text: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.2f))
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = RecapCream,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecapBadge(text: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RecapCream.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RecapRed),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconBadge(
    icon: Int,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun recapAccentFor(card: YearInMusicRecapCard): Color =
    when (card) {
        is YearInMusicRecapCard.Empty -> RecapRed
        is YearInMusicRecapCard.Intro -> RecapRed
        is YearInMusicRecapCard.Totals -> RecapYellow
        is YearInMusicRecapCard.ListeningOrbit -> RecapBlue
        is YearInMusicRecapCard.TopSong -> RecapPink
        is YearInMusicRecapCard.RankedArtists -> RecapGreen
        is YearInMusicRecapCard.RankedAlbums -> RecapLime
        is YearInMusicRecapCard.Summary -> RecapPurple
    }

private fun recapPolygonFor(card: YearInMusicRecapCard): RoundedPolygon =
    when (card) {
        is YearInMusicRecapCard.Empty -> MaterialShapes.Cookie9Sided
        is YearInMusicRecapCard.Intro -> MaterialShapes.Cookie9Sided
        is YearInMusicRecapCard.Totals -> MaterialShapes.Sunny
        is YearInMusicRecapCard.ListeningOrbit -> MaterialShapes.Clover8Leaf
        is YearInMusicRecapCard.TopSong -> MaterialShapes.Clover4Leaf
        is YearInMusicRecapCard.RankedArtists -> MaterialShapes.Cookie6Sided
        is YearInMusicRecapCard.RankedAlbums -> MaterialShapes.Puffy
        is YearInMusicRecapCard.Summary -> MaterialShapes.Cookie12Sided
    }

/** Soft accent glow plus one large, faint Material expressive shape behind card content. */
@Composable
private fun RecapAtmosphere(
    accent: Color,
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
) {
    val shape = polygon.toShape()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val glowCenter = Offset(w * 0.85f, h * 0.12f)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.08f), Color.Transparent),
                    center = glowCenter,
                    radius = w * 0.9f,
                ),
            radius = w * 0.9f,
            center = glowCenter,
        )

        val side = w * 0.95f
        val outline = shape.createOutline(Size(side, side), layoutDirection, this)
        if (outline is Outline.Generic) {
            withTransform({
                translate(left = -w * 0.18f, top = h * 0.55f)
                rotate(degrees = 16f, pivot = Offset(side / 2f, side / 2f))
            }) {
                drawPath(outline.path, accent.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun RecapBackdrop(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    colors =
                        if (enabled) {
                            listOf(
                                RecapRed.copy(alpha = 0.38f),
                                RecapPurple.copy(alpha = 0.22f),
                                RecapBlack,
                            )
                        } else {
                            listOf(RecapBlack, RecapBlack)
                        },
                    radius = 1200f,
                ),
            ),
    )
}

@Composable
private fun RecapNoiseOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                        ),
                ),
            ),
    )
}

@Composable
private fun RecapShareButton(
    isGenerating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = RecapRed,
        contentColor = RecapCream,
        shape = MaterialTheme.shapes.large,
        icon = {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shareProgress",
            ) { generating ->
                if (generating) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = RecapCream,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                    )
                }
            }
        },
        text = {
            Text(
                text = stringResource(R.string.year_in_music_current_card),
                fontWeight = FontWeight.Black,
            )
        },
    )
}

@Composable
private fun rememberShareSafeImageRequest(data: Any?): Any? {
    val context = LocalContext.current
    return remember(data, context) {
        data?.let {
            ImageRequest
                .Builder(context)
                .data(it)
                .allowHardware(false)
                .build()
        }
    }
}

private suspend fun awaitNextPreDraw(view: View) {
    suspendCancellableCoroutine { cont ->
        val vto = view.viewTreeObserver
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (vto.isAlive) vto.removeOnPreDrawListener(this)
                    cont.resume(Unit)
                    return true
                }
            }
        vto.addOnPreDrawListener(listener)
        cont.invokeOnCancellation {
            if (vto.isAlive) vto.removeOnPreDrawListener(listener)
        }
        view.invalidate()
    }
}

@Immutable
private sealed interface YearInMusicRecapCard {
    val id: String
    val label: String

    data class Empty(
        val year: Int,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "empty_$year"
    }

    data class Intro(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "intro_$year"
    }

    data class Totals(
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "totals"
    }

    data class TopSong(
        val song: SongWithStats,
        val originalSong: Song?,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "top_song_${song.id}"
    }

    data class RankedArtists(
        val artists: List<Artist>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "artists_${artists.joinToString("_") { it.id }}"
    }

    data class RankedAlbums(
        val albums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "albums_${albums.joinToString("_") { it.id }}"
    }

    data class Summary(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val topSongs: List<SongWithStats>,
        val topArtists: List<Artist>,
        val topAlbums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "summary_$year"
    }

    data class ListeningOrbit(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val activeDays: Int,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "listening_orbit_$year"
    }
}
