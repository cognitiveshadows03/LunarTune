/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.player

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import android.os.Build
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import dev.citali.lunartune.LocalDatabase
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.BlurRadiusKey
import dev.citali.lunartune.constants.DisableBlurKey
import dev.citali.lunartune.constants.EnableHapticFeedbackKey
import dev.citali.lunartune.constants.LyricsAutoHidePlayerControlsKey
import dev.citali.lunartune.constants.LyricsBackgroundStyle
import dev.citali.lunartune.constants.LyricsBackgroundStyleKey
import dev.citali.lunartune.constants.UseGpuBlurKey
import dev.citali.lunartune.constants.LyricsMode
import dev.citali.lunartune.constants.LyricsModeKey
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerCustomBlurKey
import dev.citali.lunartune.constants.PlayerCustomBrightnessKey
import dev.citali.lunartune.constants.PlayerCustomContrastKey
import dev.citali.lunartune.constants.PlayerCustomImageUriKey
import dev.citali.lunartune.constants.ShowLyricsPlayerControlsKey
import dev.citali.lunartune.extensions.togglePlayPause
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.LyricsEnhanced
import dev.citali.lunartune.ui.component.LyricsEnhancedBottomFade
import dev.citali.lunartune.ui.component.LyricsPaneBottomPadding
import dev.citali.lunartune.ui.component.LyricsV2
import dev.citali.lunartune.ui.component.LyricsV2EdgeFade
import dev.citali.lunartune.ui.component.PlayerSliderTrack
import dev.citali.lunartune.ui.menu.LyricsMenu
import dev.citali.lunartune.ui.utils.SmoothFadingEdgeStops
import dev.citali.lunartune.ui.theme.PlayerColorExtractor
import dev.citali.lunartune.utils.LyricsArtBlurCache
import dev.citali.lunartune.utils.makeTimeString
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import kotlin.coroutines.cancellation.CancellationException

private val AppleMusicFallbackGradient =
    listOf(
        Color(0xFF202020),
        Color(0xFF141414),
        Color(0xFF050505),
    )

/** How long the player controls stay on screen after the last touch before they fade. */
private const val LYRICS_CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L

/**
 * Timing for the controls leaving and returning. The controls only ever fade — their layout size
 * is never animated. Resizing the lyrics pane frame by frame made the lyrics list re-measure and
 * re-anchor its focused line on every one of those frames, which is heavy enough on weaker phones
 * to swallow the whole fade (most visibly while an instrumental section keeps the breathing dots
 * redrawing). Instead the pane changes size in a single step and a soft edge sweeps across the
 * space the controls give up or take back; see [LyricsWithControls]. The opacity still runs
 * ahead of the edge on the way out (the edge sets off once the controls have started to thin) and
 * behind it on the way back, so the hand-off reads as one motion in both directions.
 */
private val ControlsFadeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val LyricsEdgeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val CONTROLS_FADE_OUT_MS = 300
private const val LYRICS_REVEAL_MS = 520
private const val LYRICS_REVEAL_DELAY_MS = 120
private const val CONTROLS_FADE_IN_MS = 450
private const val CONTROLS_FADE_IN_DELAY_MS = 60
private const val LYRICS_COVER_MS = 320

private fun controlsFadeOutSpec() = tween<Float>(durationMillis = CONTROLS_FADE_OUT_MS, easing = ControlsFadeEasing)

private fun controlsFadeInSpec() =
    tween<Float>(
        durationMillis = CONTROLS_FADE_IN_MS,
        delayMillis = CONTROLS_FADE_IN_DELAY_MS,
        easing = ControlsFadeEasing,
    )

private fun lyricsRevealSpec() =
    tween<Float>(
        durationMillis = LYRICS_REVEAL_MS,
        delayMillis = LYRICS_REVEAL_DELAY_MS,
        easing = LyricsEdgeEasing,
    )

private fun lyricsCoverSpec() = tween<Float>(durationMillis = LYRICS_COVER_MS, easing = LyricsEdgeEasing)

/**
 * How a lyrics pane fades out at its bottom: the fade's height and its opacity profile from fully
 * visible (0) to gone (1). The moving edge in [LyricsWithControls] copies the pane's own fade
 * exactly, so swapping one for the other is invisible: the Enhanced list fades linearly over its
 * last 100 dp (fixed inside the lyrics library), the V2 list over its last 80 dp with the profile
 * of `smoothFadingEdge`.
 */
@Immutable
private class LyricsPaneEdge(
    val fade: Dp,
    val stops: Array<Pair<Float, Color>>,
)

private val LyricsEnhancedPaneEdge =
    LyricsPaneEdge(
        fade = LyricsEnhancedBottomFade,
        stops = arrayOf(0f to Color.Black, 1f to Color.Transparent),
    )

private val LyricsV2PaneEdge =
    LyricsPaneEdge(
        fade = LyricsV2EdgeFade,
        stops = SmoothFadingEdgeStops,
    )

@Suppress("UNUSED_PARAMETER")
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onQueueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val view = LocalView.current

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val configuredLyricsBackground by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val lyricsBackground = configuredLyricsBackground.resolveFor(playerBackground)
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val useGpuBlur by rememberPreference(UseGpuBlurKey, true)
    val blurRadius by rememberPreference(BlurRadiusKey, 48f)
    val playerCustomImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    val playerCustomBlur by rememberPreference(PlayerCustomBlurKey, 0f)
    val playerCustomContrast by rememberPreference(PlayerCustomContrastKey, 1f)
    val playerCustomBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    val foregroundColor =
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val showPlayerControlsState =
        rememberPreference(ShowLyricsPlayerControlsKey, true)
    val showPlayerControls by showPlayerControlsState
    val onShowPlayerControlsChange =
        remember(showPlayerControlsState) {
            { showControls: Boolean ->
                showPlayerControlsState.value = showControls
            }
        }
    val autoHidePlayerControlsState =
        rememberPreference(LyricsAutoHidePlayerControlsKey, false)
    val autoHidePlayerControls by autoHidePlayerControlsState
    val onAutoHidePlayerControlsChange =
        remember(autoHidePlayerControlsState) {
            { autoHide: Boolean ->
                autoHidePlayerControlsState.value = autoHide
            }
        }
    // Auto-hide implies the controls exist (and then fade), so it overrides a switched-off
    // "Show player controls" without touching the stored preference.
    val controlsEnabled = showPlayerControls || autoHidePlayerControls

    // Apple Music style auto-hide: the controls stay for a few seconds after the last
    // interaction and then fade away; any tap on the page brings them back and restarts
    // the timer. `controlsRevealed` is the visible/hidden state, `controlsInteraction`
    // only exists to restart the countdown while the controls are already showing.
    var controlsRevealed by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    val revealControls: () -> Unit =
        remember {
            {
                controlsInteraction++
                controlsRevealed = true
            }
        }

    val hapticClick =
        remember(enableHapticFeedback, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
            }
        }
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    dev.citali.lunartune.di.LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
        }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics) {
        if (currentLyrics != null) return@LaunchedEffect
        try {
            val existingLyrics =
                withContext(Dispatchers.IO) {
                    database.lyrics(mediaMetadata.id).first()
                }
            if (existingLyrics != null) return@LaunchedEffect

            val lyrics =
                withContext(Dispatchers.IO) {
                    lyricsHelper.getLyrics(mediaMetadata)
                }
            withContext(Dispatchers.IO) {
                database.query {
                    insertLyricsIfAbsent(
                        id = mediaMetadata.id,
                        lyrics = lyrics,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val positionState = remember(mediaMetadata.id) { mutableLongStateOf(0L) }
    val durationState = remember(mediaMetadata.id) { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember(mediaMetadata.id) { mutableStateOf<Long?>(null) }
    var gradientColors by remember(mediaMetadata.thumbnailUrl) { mutableStateOf(AppleMusicFallbackGradient) }

    val isScrubbing = sliderPosition != null
    LaunchedEffect(autoHidePlayerControls, controlsRevealed, controlsInteraction, isScrubbing) {
        if (!autoHidePlayerControls) {
            controlsRevealed = true
            return@LaunchedEffect
        }
        // A finger on the progress slider keeps the controls up; the countdown starts over
        // once it lifts.
        if (!controlsRevealed || isScrubbing) return@LaunchedEffect
        delay(LYRICS_CONTROLS_AUTO_HIDE_DELAY_MS)
        controlsRevealed = false
    }
    val controlsVisible = controlsEnabled && controlsRevealed

    val gradientColorsCache =
        remember {
            object : LinkedHashMap<String, List<Color>>(20, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>) = size > 20
            }
        }
    val fallbackColor = remember { Color.Black.toArgb() }

    LaunchedEffect(mediaMetadata.id, mediaMetadata.thumbnailUrl, lyricsBackground) {
        if (lyricsBackground != LyricsBackgroundStyle.DEFAULT && lyricsBackground != LyricsBackgroundStyle.COLORING) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }
        val thumbnailUrl = mediaMetadata.thumbnailUrl
        if (thumbnailUrl == null) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }

        gradientColorsCache[thumbnailUrl]?.let {
            gradientColors = it
            return@LaunchedEffect
        }

        gradientColors = AppleMusicFallbackGradient

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val image =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }.image
                if (image == null) {
                    null
                } else {
                    val bitmap = image.toBitmap()
                    withContext(Dispatchers.Default) {
                        val palette =
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        gradientColors = extractedColors ?: AppleMusicFallbackGradient
        gradientColorsCache[thumbnailUrl] = gradientColors
    }

    LaunchedEffect(player, playbackState, mediaMetadata.id) {
        if (playbackState != STATE_READY && playbackState != STATE_BUFFERING) return@LaunchedEffect
        while (isActive) {
            positionState.longValue = player.currentPosition.coerceAtLeast(0L)
            durationState.longValue = player.duration
            delay(250)
        }
    }

    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                showPlayerControlsState = showPlayerControlsState,
                onShowPlayerControlsChange = onShowPlayerControlsChange,
                autoHidePlayerControlsState = autoHidePlayerControlsState,
                onAutoHidePlayerControlsChange = onAutoHidePlayerControlsChange,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .observeTaps(onTap = revealControls),
    ) {
        LyricsScreenBackground(
            style = lyricsBackground,
            mediaMetadata = mediaMetadata,
            gradientColors = gradientColors,
            disableBlur = disableBlur,
            useGpuBlur = useGpuBlur,
            blurRadius = blurRadius,
            playerCustomImageUri = playerCustomImageUri,
            playerCustomBlur = playerCustomBlur,
            playerCustomContrast = playerCustomContrast,
            playerCustomBrightness = playerCustomBrightness,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(onClick = onBackClick)
            AppleMusicTrackHeader(
                mediaMetadata = mediaMetadata,
                foregroundColor = foregroundColor,
                onMoreClick = showLyricsMenu,
                onDismissClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE && controlsEnabled) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleMusicLyricsPane(
                        lyricsMode = lyricsMode,
                        foregroundColor = foregroundColor,
                        sliderPositionProvider = { sliderPosition },
                        lyricsSyncOffset = lyricsSyncOffset,
                        modifier =
                            Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .padding(end = 32.dp),
                    )

                    // Landscape keeps the two-pane layout and only fades the controls in place,
                    // so the lyrics column does not reflow sideways every time they come and go.
                    // Faded-out controls stop taking touches; a tap there just brings them back.
                    val controlsAlpha by animateFloatAsState(
                        targetValue = if (controlsVisible) 1f else 0f,
                        animationSpec = if (controlsVisible) controlsFadeInSpec() else controlsFadeOutSpec(),
                        label = "lyricsPlayerControlsAlpha",
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(0.85f)
                                .widthIn(max = 420.dp)
                                .graphicsLayer { alpha = controlsAlpha }
                                .blockPointerInput(enabled = !controlsVisible),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppleMusicControls(
                            positionProvider = { positionState.longValue },
                            durationProvider = { durationState.longValue },
                            sliderPosition = sliderPosition,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            volume = deviceMusicVolumeController.volumeFraction,
                            onPositionChange = { sliderPosition = it },
                            onPositionChangeFinished = {
                                sliderPosition?.let {
                                    player.seekTo(it)
                                    positionState.longValue = it
                                }
                                sliderPosition = null
                                revealControls()
                            },
                            onVolumeChange = {
                                revealControls()
                                onVolumeChange(it)
                            },
                            onPreviousClick = {
                                hapticClick()
                                revealControls()
                                playerConnection.seekToPrevious()
                            },
                            onPlayPauseClick = {
                                hapticClick()
                                revealControls()
                                player.togglePlayPause()
                            },
                            onNextClick = {
                                hapticClick()
                                revealControls()
                                playerConnection.seekToNext()
                            },
                            foregroundColor = foregroundColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                LyricsWithControls(
                    controlsVisible = controlsVisible,
                    paneEdge = if (lyricsMode == LyricsMode.V2) LyricsV2PaneEdge else LyricsEnhancedPaneEdge,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    lyrics = { focusAnchorHeight ->
                        AppleMusicLyricsPane(
                            lyricsMode = lyricsMode,
                            foregroundColor = foregroundColor,
                            sliderPositionProvider = { sliderPosition },
                            lyricsSyncOffset = lyricsSyncOffset,
                            focusAnchorHeight = focusAnchorHeight,
                        )
                    },
                    controls = {
                        AppleMusicControls(
                            positionProvider = { positionState.longValue },
                            durationProvider = { durationState.longValue },
                            sliderPosition = sliderPosition,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            volume = deviceMusicVolumeController.volumeFraction,
                            onPositionChange = { sliderPosition = it },
                            onPositionChangeFinished = {
                                sliderPosition?.let {
                                    player.seekTo(it)
                                    positionState.longValue = it
                                }
                                sliderPosition = null
                                revealControls()
                            },
                            onVolumeChange = {
                                revealControls()
                                onVolumeChange(it)
                            },
                            onPreviousClick = {
                                hapticClick()
                                revealControls()
                                playerConnection.seekToPrevious()
                            },
                            onPlayPauseClick = {
                                hapticClick()
                                revealControls()
                                player.togglePlayPause()
                            },
                            onNextClick = {
                                hapticClick()
                                revealControls()
                                playerConnection.seekToNext()
                            },
                            foregroundColor = foregroundColor,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LyricsScreenBackground(
    style: LyricsBackgroundStyle,
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    useGpuBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == LyricsBackgroundStyle.FOLLOW_THEME) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> {
                AppleMusicBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                )
            }

            LyricsBackgroundStyle.FOLLOW_THEME -> Unit

            LyricsBackgroundStyle.MOVING_BLUR -> {
                MovingBlurBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    useGpuBlur = useGpuBlur,
                    blurRadius = blurRadius,
                    disableBlur = disableBlur,
                )
            }

            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.CUSTOM,
            -> {
                PlayerBackground(
                    playerBackground =
                        if (style == LyricsBackgroundStyle.CUSTOM) {
                            PlayerBackgroundStyle.CUSTOM
                        } else {
                            PlayerBackgroundStyle.COLORING
                        },
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    disableBlur = disableBlur,
                    blurRadius = blurRadius,
                    playerCustomImageUri = playerCustomImageUri,
                    playerCustomBlur = playerCustomBlur,
                    playerCustomContrast = playerCustomContrast,
                    playerCustomBrightness = playerCustomBrightness,
                )
            }
        }
    }
}

@Composable
private fun AppleMusicBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnailUrl = mediaMetadata.thumbnailUrl
    val cacheRevision by LyricsArtBlurCache.updates.collectAsState()
    val blurredArt =
        remember(thumbnailUrl, cacheRevision) {
            LyricsArtBlurCache.peek(thumbnailUrl)
        }

    LaunchedEffect(thumbnailUrl) {
        LyricsArtBlurCache.prefetch(context, thumbnailUrl)
    }

    // The look this had before the moving-blur work: the blurred cover, full strength, under a
    // flat black scrim. The palette-tinted version that replaced it was richer but buried the
    // artwork, and this one is the one that reads as a blurred cover rather than as a colour.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        // Keyed on the bitmap, not the url: the outgoing artwork stays on screen until the incoming
        // one is actually ready, so a track change never flashes an empty backdrop.
        Crossfade(
            targetState = blurredArt,
            animationSpec = tween(BACKDROP_FADE_MS),
            label = "appleMusicBackdrop",
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = AppleMusicBackdropScale
                        scaleY = AppleMusicBackdropScale
                    },
        ) { art ->
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = AppleMusicScrimAlpha)),
        )
    }
}

private const val AppleMusicBackdropScale = 1.12f

/**
 * The flat scrim laid over the blurred artwork. Shared with the moving backdrop's plain variant so
 * the two read as the same picture — one still, one drifting.
 */
private const val AppleMusicScrimAlpha = 0.52f

/**
 * How long the backdrop takes to trade one track's artwork for the next. Without it the backdrop
 * pops the instant the new bitmap lands, which is the one thing that makes the whole effect read
 * as a glitch rather than as a finish.
 */
private const val BACKDROP_FADE_MS = 700

@Composable
private fun MovingBlurBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    useGpuBlur: Boolean,
    blurRadius: Float,
    disableBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    // Blurring this hard averages a cover towards its own mean, which costs it both colour and
    // brightness — so the artwork gets a light saturation correction and a small lift on the way
    // in. Only giving back what the blur averaged away, not a look of its own: both dials are
    // constants below, and if this still reads pale or dim they are the two to turn.
    //
    // Built by hand because androidx.compose.ui.graphics.ColorMatrix has no setSaturation(): these
    // are the standard Rec. 709 saturation terms, and at s = 1 the matrix is the identity.
    val vibrancyColorFilter =
        remember {
            val s = MOVING_BLUR_SATURATION
            val gain = MOVING_BLUR_BRIGHTNESS
            // The rows are deliberately *not* identical: each output channel keeps its own channel
            // at (luma + s) and takes the other two at (luma * (1 - s)). Writing one row three
            // times — which is what the ported version of this did — computes the same weighted sum
            // for R, G and B, and that is a greyscale conversion however high s goes: a blue sky
            // came out grey, and turning the saturation up only made the grey brighter.
            val lr = 0.213f * (1f - s)
            val lg = 0.715f * (1f - s)
            val lb = 0.072f * (1f - s)
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        (lr + s) * gain, lg * gain, lb * gain, 0f, 0f,
                        lr * gain, (lg + s) * gain, lb * gain, 0f, 0f,
                        lr * gain, lg * gain, (lb + s) * gain, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }

    val context = LocalContext.current
    val thumbnailUrl = mediaMetadata.thumbnailUrl
    val cacheRevision by LyricsArtBlurCache.updates.collectAsState()
    val blurredArt =
        remember(thumbnailUrl, cacheRevision) {
            LyricsArtBlurCache.peek(thumbnailUrl)
        }

    LaunchedEffect(thumbnailUrl) {
        LyricsArtBlurCache.prefetch(context, thumbnailUrl)
    }

    // Modifier.blur is a no-op below Android 12 — it needs RenderEffect, API 31+ — so pre-S, and
    // Android 12+ with the toggle off, draw the bitmap LyricsArtBlurCache blurred once on the CPU
    // instead. The drift is a graphicsLayer transform either way, and a canvas transform is
    // something every API level can do, so the backdrop still moves on old devices. (Animating a
    // pre-blurred bitmap with a layout-phase Modifier.offset instead is what tears on them.)
    val gpuBlur = useGpuBlur && !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val gpuRequest =
        remember(context, thumbnailUrl) {
            thumbnailUrl?.let { url ->
                ImageRequest
                    .Builder(context)
                    .data(url)
                    // The blur destroys the detail anyway, and the layer is rasterised at the
                    // footprint size below, so a small decode keeps both the bitmap and the
                    // per-frame GPU blur cheap.
                    .size(MOVING_BLUR_ART_PX)
                    .build()
            }
        }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        DriftingBackdrop(active = thumbnailUrl != null) {
            if (gpuBlur && gpuRequest != null) {
                // Blur only when there is something to blur: a zero radius is not a no-op at the
                // RenderEffect level, it is an invalid argument.
                val blurModifier =
                    if (blurRadius > 0.5f) {
                        Modifier.blur((blurRadius * MOVING_BLUR_BLUR_GAIN / MOVING_BLUR_SCALE).dp)
                    } else {
                        Modifier
                    }

                // The crossfade sits under the blur, so what fades is the artwork and not the
                // finished blurred result — no sharp edge is ever visible mid-transition. The blur
                // is applied inside the walk's transform, so the artwork is blurred while it is
                // still centred and only then moved: the blur never samples the transparent area
                // behind the layer's trailing edge.
                Crossfade(
                    targetState = gpuRequest,
                    animationSpec = tween(BACKDROP_FADE_MS),
                    label = "movingBlurBackdrop",
                    modifier = Modifier.fillMaxSize().then(blurModifier),
                ) { request ->
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = vibrancyColorFilter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (blurredArt != null) {
                // Keyed on the bitmap, so the old artwork holds the frame until the new one has
                // been blurred and cached — the CPU path is the one that would otherwise flash.
                Crossfade(
                    targetState = blurredArt,
                    animationSpec = tween(BACKDROP_FADE_MS),
                    label = "movingBlurBackdrop",
                    modifier = Modifier.fillMaxSize(),
                ) { art ->
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = vibrancyColorFilter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // The still backdrop's own scrim, over the still backdrop's own black — so this reads as
        // the same picture, in motion.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = AppleMusicScrimAlpha)),
        )
    }
}

/**
 * Puts [content] on the walk.
 *
 * The layer cannot simply be screen-shaped: rotated by the walk, a screen-sized rectangle only
 * covers its inscribed circle, and a black wedge sweeps through a corner. So it is sized to the
 * container's furthest corner instead.
 *
 * Every read here is draw-phase — the walk's position is read inside the graphicsLayer lambda — so
 * nothing recomposes or re-measures while it moves. Content is drawn inside the transform, which
 * is what lets the moving blur apply its own blur *before* the walk displaces it.
 */
@Composable
private fun DriftingBackdrop(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val wander = rememberBlurWanderDrift(active = active)

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val footprint =
            remember(maxWidth, maxHeight) {
                blurBackdropFootprint(
                    width = maxWidth,
                    height = maxHeight,
                    restScale = MOVING_BLUR_SCALE,
                    driftScale = MOVING_BLUR_SCALE,
                )
            }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .requiredSize(footprint)
                        .graphicsLayer {
                            scaleX = MOVING_BLUR_SCALE
                            scaleY = MOVING_BLUR_SCALE
                            // Deferred, draw-phase reads — see BlurWanderDrift.
                            translationX = wander.xDp.floatValue.dp.toPx()
                            translationY = wander.yDp.floatValue.dp.toPx()
                            // Rotation is the only part of the walk that can carry a colour across
                            // the whole surface; translation moves every colour by the same vector.
                            rotationZ = wander.rotationDeg.floatValue
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .alpha(MOVING_BLUR_ALPHA),
            ) {
                content()
            }
        }
    }
}

private const val MOVING_BLUR_SCALE = 2.4f

/**
 * The layer is drawn scaled by [MOVING_BLUR_SCALE] and Compose scales the blur along with it, so
 * the blur modifier divides by that scale to cancel it out — leaving the on-screen radius as
 * `radius * MOVING_BLUR_BLUR_GAIN`.
 *
 * Turned down from 1.6, which put ~77dp of blur on screen at the slider's default. That much
 * averaging pulls a cover towards its own mean, which was fine while the palette wash was
 * unification-by-colour; stripped back to the plain artwork it read pale and mushy at the same
 * time. This lands nearer 55dp — still soft enough to hide the walk, but the cover keeps enough of
 * its own structure and colour to read as a cover.
 */
private const val MOVING_BLUR_BLUR_GAIN = 1.15f

private const val MOVING_BLUR_ALPHA = 1f

/**
 * Light saturation for the drifting artwork. Blurring averages colour away, so this gives a little
 * of it back. A correction, not a look: the earlier version ran this at 1.6 and laid a palette wash
 * over the top as well, which turned the backdrop into a glow rather than a cover.
 */
private const val MOVING_BLUR_SATURATION = 1.3f

/**
 * A small brightness lift on the same reasoning. The blur averages a cover towards its own mean,
 * and that mean sits below the cover's own highlights, so the backdrop reads dimmer than the still
 * one under the same scrim. Kept small on purpose — past roughly 1.15 the highlights start to blow.
 */
private const val MOVING_BLUR_BRIGHTNESS = 1.08f

/** Decode size for the drifting artwork — the blur hides everything finer than this. */
private const val MOVING_BLUR_ART_PX = 256

@Composable
private fun AppleMusicGrabber(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.close)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = closeDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    )
}

@Composable
private fun AppleMusicTrackHeader(
    mediaMetadata: MediaMetadata,
    foregroundColor: Color,
    onMoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText =
        remember(mediaMetadata.id, mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }

    val backToPlayerDescription = stringResource(R.string.lyrics_back_to_player)

    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tapping the shrunken artwork brings the user back to the main player,
        // mirroring the close button so the large target is reachable one-handed.
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(foregroundColor.copy(alpha = 0.18f))
                    .semantics { contentDescription = backToPlayerDescription }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onDismissClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (mediaMetadata.thumbnailUrl == null) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = foregroundColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                color = foregroundColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.close,
            contentDescription = stringResource(R.string.close),
            foregroundColor = foregroundColor,
            onClick = onDismissClick,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.more_horiz,
            contentDescription = stringResource(R.string.more_options),
            foregroundColor = foregroundColor,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foregroundColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicLyricsPane(
    lyricsMode: LyricsMode,
    foregroundColor: Color,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    focusAnchorHeight: Dp? = null,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        sliderPositionProvider = sliderPositionProvider,
        lyricsSyncOffset = lyricsSyncOffset,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        textColor = foregroundColor,
        focusAnchorHeight = focusAnchorHeight,
    )
}

private enum class LyricsWithControlsSlot { Lyrics, Controls }

/** Measured geometry shared between the measure pass and the edge drawing; deliberately not state. */
private class LyricsWithControlsGeometry {
    var controlsHeightPx = 0
}

/**
 * Portrait arrangement of the lyrics pane with the player controls beneath it.
 *
 * The controls only fade; their layout size is never animated. When they hide, the pane is handed
 * the full height in one step and a soft edge — shaped like the pane's own bottom fade — sweeps
 * down over the freshly exposed lines, so the lyrics appear to flow into the space while the
 * controls dissolve above them. Showing the controls runs the sequence backwards: they fade in over
 * the lyrics while the edge sweeps back up, and the pane gives the space back only once the edge
 * rests where the pane's own fade will be, which makes that step invisible. Every frame in between
 * is alpha and draw work; the lyrics list is measured once per transition instead of once per
 * frame, which is what let the breathing dots of an instrumental section turn the old
 * shrink-and-fade into a stutter and an instant cut on slower phones.
 *
 * The pane keeps positioning its current line against the height it has while the controls are
 * shown (the `focusAnchorHeight` handed to [lyrics]), so the line stays exactly where it is in both
 * directions and the extra room only ever shows more of the upcoming lines.
 *
 * Controls on their way out stop taking touches and leave the composition once faded, so the lines
 * that now occupy their space can be scrolled and tapped like any others.
 */
@Composable
private fun LyricsWithControls(
    controlsVisible: Boolean,
    paneEdge: LyricsPaneEdge,
    modifier: Modifier = Modifier,
    lyrics: @Composable (focusAnchorHeight: Dp?) -> Unit,
    controls: @Composable () -> Unit,
) {
    val controlsAlpha = remember { Animatable(if (controlsVisible) 1f else 0f) }
    // 0f while the controls own the bottom of the layout, 1f once the lyrics have all of it.
    val lyricsReveal = remember { Animatable(if (controlsVisible) 0f else 1f) }
    var controlsComposed by remember { mutableStateOf(controlsVisible) }
    var controlsHoldSpace by remember { mutableStateOf(controlsVisible) }
    val geometry = remember { LyricsWithControlsGeometry() }
    val revealLayerPaint = remember { Paint() }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            controlsComposed = true
            if (controlsAlpha.value < 1f) launch { controlsAlpha.animateTo(1f, controlsFadeInSpec()) }
            if (lyricsReveal.value > 0f) lyricsReveal.animateTo(0f, lyricsCoverSpec())
            controlsHoldSpace = true
        } else {
            controlsHoldSpace = false
            if (lyricsReveal.value < 1f) launch { lyricsReveal.animateTo(1f, lyricsRevealSpec()) }
            if (controlsAlpha.value > 0f) controlsAlpha.animateTo(0f, controlsFadeOutSpec())
            controlsComposed = false
        }
    }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val controlsPlaceable =
            if (controlsComposed) {
                subcompose(LyricsWithControlsSlot.Controls) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer { alpha = controlsAlpha.value }
                                .blockPointerInput(enabled = !controlsVisible),
                    ) {
                        controls()
                    }
                }.first().measure(constraints.copy(minHeight = 0))
            } else {
                null
            }
        // Remembered while the controls are gone: the edge still has to finish its sweep and the
        // pane keeps anchoring its current line as if they were there.
        controlsPlaceable?.let { geometry.controlsHeightPx = it.height }
        val controlsHeightPx = geometry.controlsHeightPx

        val reservedHeight = if (controlsHoldSpace) controlsHeightPx else 0
        val lyricsConstraints: Constraints
        val focusAnchorHeight: Dp?
        if (constraints.hasBoundedHeight) {
            val lyricsHeight = (constraints.maxHeight - reservedHeight).coerceAtLeast(0)
            lyricsConstraints = constraints.copy(minHeight = lyricsHeight, maxHeight = lyricsHeight)
            focusAnchorHeight = (constraints.maxHeight - controlsHeightPx).coerceAtLeast(0).toDp()
        } else {
            lyricsConstraints = constraints.copy(minHeight = 0)
            focusAnchorHeight = null
        }
        val lyricsPlaceable =
            subcompose(LyricsWithControlsSlot.Lyrics) {
                Box(
                    modifier =
                        Modifier.drawWithContent {
                            val reveal = lyricsReveal.value
                            val edgeControlsHeightPx = geometry.controlsHeightPx
                            if (controlsHoldSpace || edgeControlsHeightPx <= 0 || reveal >= 1f) {
                                drawContent()
                                return@drawWithContent
                            }
                            val fadePx = paneEdge.fade.toPx()
                            val bottomPaddingPx = LyricsPaneBottomPadding.toPx()
                            // Where the pane's list ended while the controls held the space, and
                            // where the edge has to get to before it stops touching anything the
                            // pane draws.
                            val restingEdgeY = size.height - edgeControlsHeightPx - bottomPaddingPx
                            val clearedEdgeY = size.height - bottomPaddingPx + fadePx
                            val edgeY = restingEdgeY + (clearedEdgeY - restingEdgeY) * reveal
                            // Only the strip the edge travels through goes through a layer;
                            // everything above it is drawn as is.
                            val stripTop = floor(restingEdgeY - fadePx).coerceAtLeast(0f)
                            clipRect(bottom = stripTop) {
                                this@drawWithContent.drawContent()
                            }
                            clipRect(top = stripTop) {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(0f, stripTop, size.width, size.height), revealLayerPaint)
                                    // Nothing below the edge is drawn at all; above it the fade
                                    // is applied as a DstIn mask.
                                    clipRect(bottom = edgeY) {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                *paneEdge.stops,
                                                startY = edgeY - fadePx,
                                                endY = edgeY,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    canvas.restore()
                                }
                            }
                        },
                ) {
                    lyrics(focusAnchorHeight)
                }
            }.first().measure(lyricsConstraints)

        val width = constraints.constrainWidth(maxOf(lyricsPlaceable.width, controlsPlaceable?.width ?: 0))
        val height =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                constraints.constrainHeight(lyricsPlaceable.height + reservedHeight)
            }
        layout(width, height) {
            lyricsPlaceable.place(0, 0)
            controlsPlaceable?.let { it.place(0, height - it.height) }
        }
    }
}

@Composable
private fun AppleMusicControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val hasDuration = duration != C.TIME_UNSET && duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = foregroundColor.copy(alpha = 0.94f),
            inactiveColor = foregroundColor.copy(alpha = 0.28f),
            trackHeight = 8.dp,
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.widget_previous),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onPreviousClick,
            )
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(74.dp),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = foregroundColor,
                    )
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription =
                            if (isPlaying) {
                                stringResource(R.string.widget_pause)
                            } else {
                                stringResource(R.string.play)
                            },
                        tint = foregroundColor,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_next,
                contentDescription = stringResource(R.string.next),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onNextClick,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = stringResource(R.string.minimum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(17.dp),
            )
            AppleMusicSlider(
                value = volume.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                activeColor = foregroundColor.copy(alpha = 0.88f),
                inactiveColor = foregroundColor.copy(alpha = 0.24f),
                trackHeight = 8.dp,
                onValueChange = onVolumeChange,
                onValueChangeFinished = {},
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.maximum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    contentDescription: String?,
    iconSize: Dp,
    touchSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(touchSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foregroundColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = sliderColors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun LyricsContent(
    lyricsMode: LyricsMode,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    modifier: Modifier = Modifier,
    focusAnchorHeight: Dp? = null,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> {
            LyricsV2(
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
                focusAnchorHeight = focusAnchorHeight,
            )
        }

        LyricsMode.ENHANCED -> {
            LyricsEnhanced(
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
                focusAnchorHeight = focusAnchorHeight,
            )
        }
    }
}
