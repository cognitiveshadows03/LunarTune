/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.yearinmusic

import android.content.Intent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.DisableAnimationsKey
import dev.citali.lunartune.constants.DisableBlurKey
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.menu.AlbumMenu
import dev.citali.lunartune.ui.menu.ArtistMenu
import dev.citali.lunartune.utils.ComposeToImage
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.YearInMusicUiState
import dev.citali.lunartune.viewmodels.YearInMusicViewModel
import kotlin.coroutines.resume
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** How long one card stays on screen when the reel plays itself. */
private const val CardDurationMs = 6_500L

/** Width of the "go back" tap zone, as a fraction of the card, matching the story convention. */
private const val BackZoneFraction = 1f / 3f

private val GutterBlack = Color(0xFF000000)
private val CardCorner = RoundedCornerShape(26.dp)

@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialYear) {
        if (initialYear != null) viewModel.selectYear(initialYear)
    }

    val content = uiState as? YearInMusicUiState.Content
    if (content == null) {
        Box(modifier = Modifier.fillMaxSize().background(GutterBlack))
        return
    }
    YearInMusicRecapScreen(navController = navController, content = content)
}

@Composable
private fun YearInMusicRecapScreen(
    navController: NavController,
    content: YearInMusicUiState.Content,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, false)
    val scaleFactor = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val motionEnabled = !disableAnimations && scaleFactor > 0f

    val cards = rememberYearInMusicCards(content)
    val pagerState = rememberPagerState(pageCount = { cards.size })

    var isGeneratingImage by remember { mutableStateOf(false) }
    var isShareCaptureMode by remember { mutableStateOf(false) }
    var currentCardBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(content.selectedYear) { pagerState.scrollToPage(0) }

    Box(modifier = Modifier.fillMaxSize().background(GutterBlack)) {
        if (!disableBlur && !isShareCaptureMode) {
            RecapAmbientBackdrop(modifier = Modifier.fillMaxSize())
        }

        RecapCardPager(
            cards = cards,
            motionEnabled = motionEnabled,
            pagerState = pagerState,
            isShareCaptureMode = isShareCaptureMode,
            onCardBoundsChanged = { currentCardBounds = it },
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
            onTopAlbumLongClick = { album ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onBack = { navController.navigateUp() },
            modifier = Modifier.fillMaxSize(),
        )

        if (content.hasData && !isShareCaptureMode) {
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
                                        backgroundColor = android.graphics.Color.BLACK,
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
                                        backgroundColor = android.graphics.Color.BLACK,
                                    )
                                val uri =
                                    ComposeToImage.saveBitmapAsFile(
                                        context = context,
                                        bitmap = fitted,
                                        fileName = "LunarTune_YearInMusic_${content.selectedYear}_${pagerState.currentPage + 1}",
                                    )
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.share_summary)),
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
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                                ),
                            ).padding(end = 16.dp, bottom = 16.dp)
                            .widthIn(max = 178.dp),
                )
            }
        }
    }
}

/**
 * Soft warm wash behind the gutter, so the rounded card has something to sit on.
 *
 * The cards are all high-key now, and a near-black gutter around a cream card reads as a hard
 * black frame. The wash is warm and mid-toned, dark enough that the card's rounded corners are
 * clearly its own edge and light enough that it does not fight the page inside them.
 */
@Composable
private fun RecapAmbientBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF6B4A2E).copy(alpha = 0.55f), Color(0xFF2A2320), Color(0xFF161210)),
                    radius = 1_400f,
                ),
            ),
    )
}

// ---------------------------------------------------------------------------------------------
// Card list
// ---------------------------------------------------------------------------------------------

@Composable
private fun rememberYearInMusicCards(content: YearInMusicUiState.Content): List<YearInMusicRecapCard> =
    remember(
        content.selectedYear,
        content.hasData,
        content.totalListeningTime,
        content.totalSongsPlayed,
        content.activeDays,
        content.topSongsStats,
        content.topArtists,
        content.topAlbums,
        content.longestStreak,
    ) {
        if (!content.hasData) {
            listOf(YearInMusicRecapCard.Empty(content.selectedYear))
        } else {
            buildList {
                add(
                    YearInMusicRecapCard.Intro(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                    ),
                )
                add(
                    YearInMusicRecapCard.Totals(
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        floating = floatingArtwork(content),
                    ),
                )
                // The longest-streak page is parked while its design is reworked. The card
                // variant, its renderer and the streak maths all stay in place so it can come
                // back as one line here; nothing else in the reel depends on it.
                if (content.topSongsStats.isNotEmpty()) {
                    add(YearInMusicRecapCard.TopSongs(songs = content.topSongsStats.take(5)))
                }
                if (content.topArtists.isNotEmpty()) {
                    add(YearInMusicRecapCard.TopArtists(artists = content.topArtists.take(5)))
                }
                if (content.topAlbums.isNotEmpty()) {
                    add(YearInMusicRecapCard.TopAlbums(albums = content.topAlbums.take(5)))
                }
                add(
                    YearInMusicRecapCard.Summary(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        activeDays = content.activeDays,
                        topSongs = content.topSongsStats,
                        topArtists = content.topArtists,
                        topAlbums = content.topAlbums,
                    ),
                )
            }
        }
    }

/**
 * Artwork that drifts behind the totals sentence.
 *
 * Drawn from the year's own standings — artists as circles, songs and albums as squares — and
 * interleaved so no two of the same kind sit side by side. Anything without cover art is skipped
 * rather than leaving a grey placeholder floating across the page.
 */
/**
 * Artwork for the streak grid.
 *
 * Albums lead because a run of days reads better as records than as single tracks, then songs
 * fill in behind them, interleaved and de-duplicated so the grid never shows the same cover in
 * two neighbouring cells.
 */
private fun streakCovers(content: YearInMusicUiState.Content): List<String?> {
    val albums = content.topAlbums.map { it.thumbnailUrl }.filterNot { it.isNullOrBlank() }
    val songs = content.topSongsStats.map { it.thumbnailUrl }.filterNot { it.isNullOrBlank() }
    return buildList {
        for (index in 0 until maxOf(albums.size, songs.size)) {
            albums.getOrNull(index)?.let(::add)
            songs.getOrNull(index)?.let(::add)
        }
    }.distinct()
}

private fun floatingArtwork(content: YearInMusicUiState.Content): List<RecapFloatingArt> {
    fun collect(artwork: List<String?>, kind: RecapArtKind) =
        artwork.filterNot { it.isNullOrBlank() }.map { RecapFloatingArt(artwork = it, kind = kind) }

    val songs = collect(content.topSongsStats.map { it.thumbnailUrl }, RecapArtKind.SONG)
    val albums = collect(content.topAlbums.map { it.thumbnailUrl }, RecapArtKind.ALBUM)
    val artists = collect(content.topArtists.map { it.thumbnailUrl }, RecapArtKind.ARTIST)

    // Songs and albums share the left curtain, artists take the right. Interleave the two
    // square lists so a 5-song year and a 1-album year still produce an alternating strip.
    val squares = mutableListOf<RecapFloatingArt>()
    for (index in 0 until maxOf(songs.size, albums.size)) {
        songs.getOrNull(index)?.let(squares::add)
        albums.getOrNull(index)?.let(squares::add)
    }
    return (squares.distinctBy { it.artwork } + artists.distinctBy { it.artwork }).take(14)
}

// ---------------------------------------------------------------------------------------------
// Pager and story clock
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecapCardPager(
    cards: List<YearInMusicRecapCard>,
    motionEnabled: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isShareCaptureMode: Boolean,
    onCardBoundsChanged: (Rect) -> Unit,
    onTopArtistLongClick: (Artist) -> Unit,
    onTopAlbumLongClick: (Album) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var storyIndex by rememberSaveable { mutableStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var storyElapsed by rememberSaveable { mutableStateOf(0L) }
    // Progress lives in a state holder that only the segment row reads, so a frame tick never
    // recomposes a whole card.
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(cards.size) {
        if (storyIndex !in cards.indices) storyIndex = cards.lastIndex.coerceAtLeast(0)
    }

    // Keep the pager on the story's card. Keyed only on the index, so segment progress updates
    // can never interrupt a scroll.
    LaunchedEffect(storyIndex, cards.size) {
        if (cards.isNotEmpty()) pagerState.animateScrollToPage(storyIndex)
    }

    // Story clock: runs while the reel is allowed to play. Pausing freezes the current segment
    // where it is, and resuming picks it up from that point.
    LaunchedEffect(storyIndex, paused, isShareCaptureMode, cards.size) {
        progress.floatValue = (storyElapsed.toFloat() / CardDurationMs).coerceIn(0f, 1f)
        val card = cards.getOrNull(storyIndex) ?: return@LaunchedEffect
        if (paused || isShareCaptureMode) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (storyElapsed < CardDurationMs) {
            val now = withFrameNanos { it }
            storyElapsed = (storyElapsed + (now - last) / 1_000_000L).coerceAtMost(CardDurationMs)
            last = now
            progress.floatValue = (storyElapsed.toFloat() / CardDurationMs).coerceIn(0f, 1f)
        }
        if (storyIndex < cards.lastIndex) {
            storyIndex += 1
            storyElapsed = 0L
        } else if (cards.size > 1) {
            // The reel ends when the closing card's segment does. There is nowhere further to
            // go, so instead of idling on the last frame with a full bar, it lets the screen go
            // the way the close button would. The lone empty-state card is exempt: it is not a
            // reel, and closing it out from under the reader would only be rude.
            onBack()
        }
    }

    val goTo: (Int) -> Unit = { target ->
        if (target in cards.indices) {
            storyIndex = target
            storyElapsed = 0L
        }
    }

    HorizontalPager(
        state = pagerState,
        key = { index -> cards.getOrNull(index)?.id ?: "stale_year_in_music_page_$index" },
        userScrollEnabled = false,
        modifier = modifier,
    ) { page ->
        val card = cards.getOrNull(page) ?: run {
            Box(modifier = Modifier.fillMaxSize())
            return@HorizontalPager
        }
        val scene = RecapScenes.forCard(card)
        val tone = scene.tone
        val isCurrent = page == storyIndex
        val layoutDirection = LocalLayoutDirection.current

        // Entrance clock: restarts every time this card becomes the active one.
        var cardElapsed by remember(card.id) { mutableLongStateOf(0L) }
        LaunchedEffect(card.id, isCurrent) {
            if (!isCurrent) return@LaunchedEffect
            cardElapsed = 0L
            val started = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                cardElapsed = (now - started) / 1_000_000L
                if (cardElapsed > 12_000L) break
            }
        }
        val motion: () -> Long = { if (motionEnabled) cardElapsed else 12_000L }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (isShareCaptureMode) 8.dp else 0.dp,
                        bottom = if (isShareCaptureMode) 8.dp else 0.dp,
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val distance =
                                (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
                            val transition = distance.coerceIn(0f, 1f)
                            alpha = 1f - transition * 0.3f
                            scaleX = 1f - transition * 0.02f
                            scaleY = 1f - transition * 0.02f
                        }.clip(CardCorner)
                        .background(GutterBlack)
                        .onGloballyPositioned { coordinates ->
                            if (page == pagerState.currentPage) onCardBoundsChanged(coordinates.boundsInRoot())
                        }.pointerInput(card.id, isCurrent, isShareCaptureMode, layoutDirection) {
                            // A parent-level tap detector only fires when nothing above consumed the
                            // press, so buttons and the artwork's own long-press menu keep priority.
                            detectTapGestures { offset ->
                                if (isShareCaptureMode || !isCurrent) return@detectTapGestures
                                // Go back from anywhere, but only forward on pages that say a
                                // tap should advance; the top-track page keeps its own taps.
                                //
                                // The back zone is the leading third of the card, so in RTL the
                                // threshold has to be measured from the right edge instead.
                                val inBackZone =
                                    if (layoutDirection == LayoutDirection.Rtl) {
                                        offset.x > size.width * (1f - BackZoneFraction)
                                    } else {
                                        offset.x < size.width * BackZoneFraction
                                    }
                                if (inBackZone) {
                                    if (storyIndex > 0) goTo(storyIndex - 1)
                                } else if (card.tapAdvances && storyIndex < cards.lastIndex) {
                                    goTo(storyIndex + 1)
                                }
                            }
                        },
            ) {
                RecapCardPage(
                    card = card,
                    scene = scene,
                    tone = tone,
                    motion = motion,
                    motionEnabled = motionEnabled,
                    paused = paused,
                    isCurrent = isCurrent,
                    applySafeContentInsets = true,
                    progress = { progress.floatValue },
                    pageIndex = page,
                    pageCount = cards.size,
                    onBack = onBack,
                    onTogglePause = { paused = !paused },
                    shareCaptureMode = isShareCaptureMode,
                    onTopArtistLongClick = onTopArtistLongClick,
                    onTopAlbumLongClick = onTopAlbumLongClick,
                )
            }
        }
    }
}

@Composable
private fun RecapCardPage(
    card: YearInMusicRecapCard,
    scene: RecapScene,
    tone: RecapTone,
    motion: () -> Long,
    motionEnabled: Boolean,
    paused: Boolean,
    isCurrent: Boolean,
    applySafeContentInsets: Boolean,
    progress: () -> Float,
    pageIndex: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    shareCaptureMode: Boolean,
    onTopArtistLongClick: (Artist) -> Unit,
    onTopAlbumLongClick: (Album) -> Unit,
) {
    val ambientFlow =
        if (motionEnabled) {
            rememberInfiniteTransition(label = "recap_ambient").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 26_000, easing = LinearEasing),
                    ),
                label = "recap_ambient_phase",
            )
        } else {
            remember { mutableStateOf(0.12f) }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        RecapSceneBackdrop(
            scene = scene,
            phase = ambientFlow.value,
            modifier = Modifier.fillMaxSize(),
        )

        when (card) {
            is YearInMusicRecapCard.Empty ->
                EmptyRecapCard(card, tone, motion, applySafeContentInsets)

            is YearInMusicRecapCard.Intro ->
                IntroRecapCard(card, tone, motion, applySafeContentInsets)

            is YearInMusicRecapCard.Totals ->
                TotalsRecapCard(card, scene, tone, motion, applySafeContentInsets)

            is YearInMusicRecapCard.LongestStreak ->
                LongestStreakRecapCard(card, scene, tone, motion, applySafeContentInsets)

            is YearInMusicRecapCard.TopSongs ->
                TopSongsRecapCard(card, tone, motion, applySafeContentInsets)

            is YearInMusicRecapCard.TopArtists ->
                TopArtistsRecapCard(card, scene, tone, motion, applySafeContentInsets, onTopArtistLongClick)

            is YearInMusicRecapCard.TopAlbums ->
                TopAlbumsRecapCard(card, tone, motion, applySafeContentInsets, onTopAlbumLongClick)

            is YearInMusicRecapCard.Summary ->
                SummaryRecapCard(card, tone, motion, applySafeContentInsets)
        }

        // Chrome: story segments plus the Replay-style pause and close buttons.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (applySafeContentInsets) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        },
                    ).padding(top = 12.dp),
        ) {
            if (!shareCaptureMode) {
                RecapStoryProgress(
                    current = pageIndex,
                    count = pageCount,
                    progress = progress,
                    tone = tone,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (!shareCaptureMode && isCurrent) {
                    RecapGlassButton(
                        icon = if (paused) R.drawable.play else R.drawable.pause,
                        label =
                            stringResource(
                                if (paused) R.string.recap_resume_highlights else R.string.recap_pause_highlights,
                            ),
                        onClick = onTogglePause,
                        tone = tone,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (!shareCaptureMode) {
                    RecapGlassButton(
                        icon = R.drawable.close,
                        label = stringResource(R.string.close),
                        onClick = onBack,
                        tone = tone,
                    )
                }
            }
        }
    }
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
        containerColor = Color.White,
        contentColor = RecapInk,
        shape = RoundedCornerShape(50),
        icon = {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shareProgress",
            ) { generating ->
                if (generating) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp), color = RecapInk)
                } else {
                    Icon(painter = painterResource(R.drawable.share), contentDescription = null)
                }
            }
        },
        text = {
            Text(
                text = stringResource(R.string.year_in_music_share_card),
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
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
            // `this` inside the OnPreDrawListener refers to the listener, so removal on
            // cancellation must reach it through the captured reference.
            if (vto.isAlive) vto.removeOnPreDrawListener(listener)
        }
        view.invalidate()
    }
}
