/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.player

/*
 * Apple Music player design (PlayerDesignStyle.V10). Lyrics behaviour ported from
 * 4nx3b/ArchiveTune (AppleMusicPlayer.kt): inline word-synced lyrics under a mini header,
 * auto-hiding controls, and the lyrics menu on the header overflow button.
 */

import kotlinx.coroutines.flow.first
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.lazy.items
import dev.citali.lunartune.extensions.metadata
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.EnableHapticFeedbackKey
import dev.citali.lunartune.constants.LyricsAutoHidePlayerControlsKey
import dev.citali.lunartune.constants.LyricsMode
import dev.citali.lunartune.constants.LyricsModeKey
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerHorizontalPadding
import dev.citali.lunartune.constants.ShowLyricsPlayerControlsKey
import dev.citali.lunartune.constants.SliderStyle
import dev.citali.lunartune.db.entities.FormatEntity
import dev.citali.lunartune.db.entities.codecLabel
import dev.citali.lunartune.extensions.togglePlayPause
import dev.citali.lunartune.extensions.toggleRepeatMode
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.playback.PlayerConnection
import dev.citali.lunartune.ui.component.BottomSheetPageState
import dev.citali.lunartune.ui.component.BottomSheetState
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.LyricsEnhanced
import dev.citali.lunartune.ui.component.LyricsV2
import dev.citali.lunartune.ui.component.MenuState
import dev.citali.lunartune.ui.component.PlayerSliderTrack
import dev.citali.lunartune.ui.component.ResizableIconButton
import dev.citali.lunartune.ui.menu.LyricsMenu
import dev.citali.lunartune.ui.menu.PlayerMenu
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.ui.theme.PlayerBackgroundColorUtils
import dev.citali.lunartune.ui.theme.PlayerSliderColors
import dev.citali.lunartune.ui.utils.ShowMediaInfo
import dev.citali.lunartune.ui.utils.highRes
import dev.citali.lunartune.utils.makeTimeString
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberLowDataModeActive
import dev.citali.lunartune.utils.rememberPreference
import kotlinx.coroutines.delay
import me.saket.squiggles.SquigglySlider

private val AppleMusicGutter = 26.dp

/** Apple Music style player: blurred backdrop, full-bleed artwork fading into it, Apple controls. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AppleMusicPortraitContent(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    onCloseQueue: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onCloseLyrics: () -> Unit,
    mediaMetadata: MediaMetadata,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    showVolumeBar: Boolean,
    currentFormat: FormatEntity?,
    foreground: Color,
    secondaryForeground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lyrics (ported from ArchiveTune's AppleMusicPlayer): the artwork collapses into a 56dp mini
    // header thumbnail beside the credits, the word-synced lyrics (LyricsEnhanced / LyricsV2, per the
    // lyrics mode setting) fade in below after a short deferral, and the controls auto-hide after 5s
    // while reading — a tap anywhere brings them back.
    val collapse by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = 450,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "appleMusicLyricsCollapse",
    )
    val collapseProvider = androidx.compose.runtime.rememberUpdatedState(collapse)
    val p: () -> Float = { collapseProvider.value }
    // The status bar is hidden in this style, so fall back to the display cutout to clear the
    // camera hole / notch.
    // The status bar is hidden in this style: use its height even while hidden, and never less than
    // the display cutout, so the header always clears the camera hole / notch.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusTop =
        maxOf(
            androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility
                .asPaddingValues()
                .calculateTopPadding(),
            androidx.compose.foundation.layout.WindowInsets.displayCutout
                .asPaddingValues()
                .calculateTopPadding(),
            with(density) {
                (
                    androidx.compose.ui.platform.LocalView.current.rootWindowInsets
                        ?.displayCutout
                        ?.safeInsetTop ?: 0
                ).toDp()
            },
        )
    // Lyrics menu + the lyrics-controls preferences it toggles (same keys as the lyrics page).
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val currentLyrics by (
        playerConnection?.currentLyrics ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    ).collectAsStateWithLifecycle(initialValue = null)
    // The inline lyrics page has no LyricsScreen to fetch lyrics, and fetches tied to the page's own
    // composition got cancelled and restarted whenever it opened/closed. Fetch here instead, keyed only
    // on the song, so lyrics are already loading (or ready) by the time the page is opened.
    val amContext = androidx.compose.ui.platform.LocalContext.current
    val amDatabase = dev.citali.lunartune.LocalDatabase.current
    val amLyricsHelper =
        remember(amContext) {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(amContext.applicationContext, dev.citali.lunartune.di.LyricsHelperEntryPoint::class.java)
                .lyricsHelper()
        }
    val hasLyrics = currentLyrics != null
    // Same method as the other players' LyricsScreen: fetch while the lyrics page is shown,
    // keyed on the song and the current lyrics.
    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics, lyricsOpen) {
        if (!lyricsOpen || currentLyrics != null) return@LaunchedEffect
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (amDatabase.lyrics(mediaMetadata.id).first() != null) return@withContext
                val fetched = amLyricsHelper.getLyricsWithSource(mediaMetadata)
                amDatabase.query {
                    insertLyricsIfAbsent(id = mediaMetadata.id, lyrics = fetched.lyrics, source = fetched.providerName)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
    val showLyricsControlsState = rememberPreference(ShowLyricsPlayerControlsKey, true)
    val autoHideLyricsControlsState = rememberPreference(LyricsAutoHidePlayerControlsKey, false)
    val latestMetadata = androidx.compose.runtime.rememberUpdatedState(mediaMetadata)
    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { latestMetadata.value },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                showPlayerControlsState = showLyricsControlsState,
                onShowPlayerControlsChange = { showLyricsControlsState.value = it },
                autoHidePlayerControlsState = autoHideLyricsControlsState,
                onAutoHidePlayerControlsChange = { autoHideLyricsControlsState.value = it },
                onDismiss = menuState::dismiss,
            )
        }
    }
    val thumb = 56.dp
    val thumbStart = AppleMusicGutter
    val thumbTop = 16.dp
    val miniHeaderHeight = statusTop + thumbTop + thumb + 8.dp
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)

    var lyricsContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen) {
        lyricsContentReady = false
        if (lyricsOpen) {
            kotlinx.coroutines.delay(160L)
            lyricsContentReady = true
        }
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsPoke by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val autoHideLyricsControls = autoHideLyricsControlsState.value
    val showLyricsControls = showLyricsControlsState.value
    LaunchedEffect(lyricsOpen, controlsPoke, sliderPosition != null, autoHideLyricsControls, showLyricsControls) {
        controlsVisible = true
        if (!lyricsOpen) {
            controlsPoke = 0
            return@LaunchedEffect
        }
        if (sliderPosition != null) return@LaunchedEffect
        when {
            autoHideLyricsControls -> {
                kotlinx.coroutines.delay(5_000L)
                controlsVisible = false
            }
            // Controls turned off in the lyrics menu: hide them, a tap shows them for 5s.
            !showLyricsControls -> {
                if (controlsPoke == 0) {
                    controlsVisible = false
                } else {
                    kotlinx.coroutines.delay(5_000L)
                    controlsVisible = false
                }
            }
        }
    }
    val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (controlsVisible) 180 else 140),
        label = "appleMusicControlsAlpha",
    )

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(lyricsOpen) {
                    if (!lyricsOpen) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controlsPoke++
                    }
                },
    ) {
        val compactHeight = maxHeight < 720.dp
        val gap = if (compactHeight) 8.dp else 16.dp
        val fullArt = maxWidth
        // Like the immersive style: the artwork grows down into whatever the controls leave free
        // (e.g. with the volume bar hidden), staying at least square and cropping to fill.
        var controlsHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val controlsHeight = with(androidx.compose.ui.platform.LocalDensity.current) { controlsHeightPx.toDp() }
        val artHeight =
            if (controlsHeightPx == 0) {
                fullArt
            } else {
                maxOf(fullArt, maxHeight - controlsHeight + 56.dp).coerceAtMost(maxHeight)
            }

        Box(
            modifier =
                Modifier
                    .layout { measurable, _ ->
                        val t = p()
                        val w = androidx.compose.ui.unit.lerp(fullArt, thumb, t).roundToPx()
                        val h = androidx.compose.ui.unit.lerp(artHeight, thumb, t).roundToPx()
                        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                        layout(w, h) {
                            placeable.place(
                                androidx.compose.ui.unit.lerp(0.dp, thumbStart, t).roundToPx(),
                                androidx.compose.ui.unit.lerp(0.dp, statusTop + thumbTop, t).roundToPx(),
                            )
                        }
                    }.graphicsLayer {
                        val t = p()
                        shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(0.dp, 8.dp, t))
                        clip = t > 0f
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }.drawWithContent {
                        drawContent()
                        val t = p()
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.72f to Color.Black,
                                    1f to Color.Black.copy(alpha = t),
                                ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
        ) {
            CrossfadingPlayerArtwork(
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
            )
        }

        // Mini header: credits, star and menu beside the collapsed thumbnail.
        if (collapse > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = thumbStart + thumb + 12.dp, end = AppleMusicGutter - 8.dp, top = statusTop + thumbTop)
                        .height(thumb)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val t = p()
                            alpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            translationY = (1f - t) * 24.dp.toPx()
                        },
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = { if (queueOpen) onCloseQueue() else onCloseLyrics() },
                            ),
                ) {
                    Text(
                        text = mediaMetadata.title,
                        color = foreground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = artists.joinToString { it.name },
                        color = foreground.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppleMusicPlainIcon(
                    iconRes = if (currentSongLiked) R.drawable.star_filled else R.drawable.star,
                    tint = if (currentSongLiked) Color(0xFFFFC83D) else foreground.copy(alpha = 0.55f),
                    iconSize = 22.dp,
                    onClick = onToggleLike,
                )
                AppleMusicPlainIcon(
                    iconRes = R.drawable.more_horiz,
                    tint = foreground.copy(alpha = 0.8f),
                    iconSize = 24.dp,
                    onClick = { if (lyricsOpen) showLyricsMenu() else onMenuClick() },
                )
            }
        }

        // Lyrics pane between the mini header and the controls (full height once they auto-hide).
        androidx.compose.animation.AnimatedVisibility(
            visible = lyricsOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        ) {
            val lyricsBottom by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (controlsVisible) controlsHeight else 0.dp,
                animationSpec = androidx.compose.animation.core.tween(300),
                label = "appleMusicLyricsBottom",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = miniHeaderHeight, bottom = lyricsBottom)
                        .padding(horizontal = AppleMusicGutter - 16.dp),
            ) {
                if (lyricsContentReady) {
                    val provider = androidx.compose.runtime.rememberUpdatedState(sliderPosition)
                    when (lyricsMode) {
                        LyricsMode.V2 ->
                            LyricsV2(
                                sliderPositionProvider = { provider.value },
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier.fillMaxSize(),
                                // Always on the dark blurred backdrop, so ignore light theme colours.
                                textColorOverride = Color.White,
                            )
                        LyricsMode.ENHANCED ->
                            LyricsEnhanced(
                                sliderPositionProvider = { provider.value },
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier.fillMaxSize(),
                                // Always on the dark blurred backdrop, so ignore light theme colours.
                                textColorOverride = Color.White,
                                alwaysFocusActiveLine = true,
                            )
                    }
                }
            }
        }

        // Queue pane: same slot and motion as the lyrics pane.
        androidx.compose.animation.AnimatedVisibility(
            visible = queueOpen,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        ) {
            var queueReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(160L)
                queueReady = true
            }
            if (queueReady) {
                AppleMusicQueuePane(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = miniHeaderHeight, bottom = controlsHeight),
                )
            }
        }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { controlsHeightPx = it.height }
                        .graphicsLayer {
                            alpha = controlsAlpha
                            translationY = (1f - controlsAlpha) * 24.dp.toPx()
                        }
                        .padding(horizontal = AppleMusicGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.graphicsLayer {
                            val t = p()
                            alpha = (1f - t / 0.6f).coerceIn(0f, 1f)
                            translationY = -t * 24.dp.toPx()
                        },
                ) {
                    AppleMusicMetadataRow(
                        title = mediaMetadata.title,
                        artists = artists,
                        liked = currentSongLiked,
                        foreground = foreground,
                        onMenuClick = onMenuClick,
                        onToggleLike = onToggleLike,
                        onTitleClick = onTitleClick,
                        onArtistClick = onArtistClick,
                    )
                }
                Spacer(Modifier.height(if (compactHeight) 10.dp else 14.dp))
                AppleMusicProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    currentFormat = currentFormat,
                    foreground = foreground,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )
                Spacer(Modifier.height(gap))
                AppleMusicTransport(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    foreground = foreground,
                    onPreviousClick = onPreviousClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                )
                // Always shown in the Apple Music style, regardless of the volume bar setting.
                Spacer(Modifier.height(gap))
                AppleMusicVolume(
                    volume = volume,
                    foreground = foreground,
                    onVolumeChange = onVolumeChange,
                )
                Spacer(Modifier.height(2.dp))
            }
    }
}

@Composable
private fun AppleMusicMetadataRow(
    title: String,
    artists: List<MediaMetadata.Artist>,
    liked: Boolean,
    foreground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = foreground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onTitleClick,
                        ),
            )
            Text(
                text = artists.joinToString { it.name },
                color = foreground.copy(alpha = 0.6f),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { artists.firstOrNull()?.id?.let(onArtistClick) },
            )
        }
        AppleMusicPlainIcon(
            iconRes = if (liked) R.drawable.star_filled else R.drawable.star,
            tint = if (liked) Color(0xFFFFC83D) else foreground.copy(alpha = 0.55f),
            iconSize = 22.dp,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleLike()
            },
        )
        AppleMusicPlainIcon(
            iconRes = R.drawable.more_horiz,
            tint = foreground.copy(alpha = 0.8f),
            iconSize = 24.dp,
            onClick = onMenuClick,
        )
    }
}

@Composable
private fun AppleMusicPlainIcon(
    iconRes: Int,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: androidx.compose.ui.unit.Dp = 40.dp,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(boxSize)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AppleMusicProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    currentFormat: FormatEntity?,
    foreground: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L || duration == C.TIME_UNSET) 0f else duration.toFloat()
    val current = (sliderPosition ?: position).coerceAtLeast(0L)
    val safeValue = current.toFloat().coerceIn(0f, safeDuration.coerceAtLeast(0f))
    val dimmed = foreground.copy(alpha = 0.55f)
    Column(modifier = Modifier.fillMaxWidth()) {
        V8FlatSlider(
            value = safeValue,
            valueRange = 0f..safeDuration.coerceAtLeast(0f),
            activeColor = foreground.copy(alpha = 0.85f),
            inactiveColor = foreground.copy(alpha = 0.22f),
            trackHeight = if (sliderPosition != null) 10.dp else 6.dp,
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = safeDuration > 0f,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                text = makeTimeString(current),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = dimmed,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (currentFormat != null) {
                val label = remember(currentFormat.mimeType, currentFormat.codecs) { currentFormat.codecLabel() }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dimmed,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Text(
                text =
                    if (safeDuration > 0f) {
                        "-" + makeTimeString((duration - current).coerceAtLeast(0L))
                    } else {
                        ""
                    },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = dimmed,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun AppleMusicTransport(
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    foreground: Color,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleMusicPlainIcon(
            iconRes = R.drawable.skip_previous,
            tint = foreground,
            iconSize = 40.dp,
            boxSize = 72.dp,
            enabled = canSkipPrevious,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPreviousClick()
            },
        )
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = foreground, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            } else {
                AppleMusicPlainIcon(
                    iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                    tint = foreground,
                    iconSize = 54.dp,
                    boxSize = 80.dp,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPauseClick()
                    },
                )
            }
        }
        AppleMusicPlainIcon(
            iconRes = R.drawable.skip_next,
            tint = foreground,
            iconSize = 40.dp,
            boxSize = 72.dp,
            enabled = canSkipNext,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNextClick()
            },
        )
    }
}

@Composable
private fun AppleMusicVolume(
    volume: Float,
    foreground: Color,
    onVolumeChange: (Float) -> Unit,
) {
    val dimmed = foreground.copy(alpha = 0.55f)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.volume_off), null, tint = dimmed, modifier = Modifier.size(16.dp))
        V8FlatSlider(
            value = volume.coerceIn(0f, 1f),
            valueRange = 0f..1f,
            activeColor = foreground.copy(alpha = 0.85f),
            inactiveColor = foreground.copy(alpha = 0.22f),
            trackHeight = 6.dp,
            onValueChange = onVolumeChange,
            onValueChangeFinished = {},
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Icon(painterResource(R.drawable.volume_up), null, tint = dimmed, modifier = Modifier.size(18.dp))
    }
}

private val androidx.media3.common.Timeline.Window.amQueueKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())

/**
 * Apple Music style "Playing Next" queue, shown inline under the collapsed artwork header (same
 * open/close motion as the lyrics page). Shuffle / repeat / autoplay pills, then the upcoming songs:
 * tap to play, drag the handle to reorder (disabled while shuffle is on).
 */
@Composable
private fun AppleMusicQueuePane(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val shuffleOn by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    var autoplay by rememberPreference(dev.citali.lunartune.constants.AutoLoadMoreKey, defaultValue = true)

    val upcoming = remember { androidx.compose.runtime.mutableStateListOf<androidx.media3.common.Timeline.Window>() }
    var dragStartKey by remember { mutableStateOf<Long?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState =
        sh.calvin.reorderable.rememberReorderableLazyListState(listState) { from, to ->
            upcoming.add(to.index, upcoming.removeAt(from.index))
        }
    LaunchedEffect(queueWindows, currentWindowIndex, reorderState.isAnyItemDragging) {
        if (reorderState.isAnyItemDragging) return@LaunchedEffect
        val key = dragStartKey
        if (key != null) {
            dragStartKey = null
            // Commit the finished drag: move the dragged song to the slot it was dropped on.
            val from = queueWindows.indexOfFirst { it.amQueueKey == key }
            val newLocal = upcoming.indexOfFirst { it.amQueueKey == key }
            val to = currentWindowIndex + 1 + newLocal
            if (from >= 0 && newLocal >= 0 && from != to && to in queueWindows.indices) {
                player.moveMediaItem(queueWindows[from].firstPeriodIndex, queueWindows[to].firstPeriodIndex)
                return@LaunchedEffect
            }
        }
        upcoming.clear()
        upcoming.addAll(queueWindows.drop((currentWindowIndex + 1).coerceAtLeast(0)))
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppleMusicGutter).padding(top = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Playing Next", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (!queueTitle.isNullOrBlank()) {
                    Text(
                        "From $queueTitle",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppleMusicGutter),
        ) {
            @Composable
            fun pill(icon: Int, active: Boolean, onClick: () -> Unit) {
                val bg by animateColorAsState(
                    if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f),
                    label = "amQueuePill",
                )
                val fg by animateColorAsState(
                    if (active) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f),
                    label = "amQueuePillFg",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable(onClick = onClick),
                ) {
                    Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(20.dp))
                }
            }
            pill(R.drawable.shuffle, shuffleOn) { player.shuffleModeEnabled = !shuffleOn }
            pill(
                if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
            ) {
                player.repeatMode =
                    when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    }
            }
            pill(R.drawable.all_inclusive, autoplay) { autoplay = !autoplay }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Soft fade at the top and bottom edges, like Apple Music.
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.04f to Color.Black,
                                    0.94f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
        ) {
            items(upcoming.size, key = { upcoming[it].amQueueKey }) { i ->
                val window = upcoming[i]
                ReorderableItem(reorderState, key = window.amQueueKey) { dragging ->
                    val meta = window.mediaItem.metadata
                    val lift by animateFloatAsState(if (dragging) 1f else 0f, label = "amQueueLift")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = 1f + 0.03f * lift
                                    scaleY = 1f + 0.03f * lift
                                }.background(Color.White.copy(alpha = 0.1f * lift), RoundedCornerShape(10.dp))
                                .clickable {
                                    val index = window.firstPeriodIndex
                                    if (!playerConnection.service.manualSeekToIndexWithCrossfade(index)) {
                                        player.seekToDefaultPosition(index)
                                        player.playWhenReady = true
                                    }
                                }.padding(horizontal = AppleMusicGutter, vertical = 7.dp),
                    ) {
                        AsyncImage(
                            model = meta?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                meta?.title ?: window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                meta?.artists?.joinToString { it.name }.orEmpty(),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!shuffleOn) {
                            Icon(
                                painterResource(R.drawable.drag_handle),
                                null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .padding(8.dp)
                                        .draggableHandle(onDragStarted = { dragStartKey = window.amQueueKey }),
                            )
                        }
                    }
                }
            }
        }
    }
}
