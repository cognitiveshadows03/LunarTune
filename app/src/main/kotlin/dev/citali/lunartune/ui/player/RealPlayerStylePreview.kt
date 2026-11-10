/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.MaterialTheme
import dev.citali.lunartune.constants.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerButtonsStyle
import dev.citali.lunartune.constants.PlayerButtonsStyleKey
import dev.citali.lunartune.constants.SliderStyle
import dev.citali.lunartune.constants.ShowPlayerVolumeBarKey
import dev.citali.lunartune.constants.SliderStyleKey
import dev.citali.lunartune.constants.ThumbnailCornerRadiusKey
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.component.BottomSheetPageState
import dev.citali.lunartune.ui.component.MenuState
import dev.citali.lunartune.ui.component.rememberBottomSheetState
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference

/**
 * Draws a style by running the player's own composables for it, so a preview can
 * never drift away from the real thing: a layout change lands in the carousel the
 * moment it lands in the player.
 *
 * Two things make that possible without starting a second player:
 *
 * - the styles take their [MediaMetadata] as a parameter, so the card renders the
 *   newest history entry even when the service has nothing loaded;
 * - every playback value is passed as a frozen number. The one thing still read
 *   live is [LocalPlayerConnection], because the style composables require it for
 *   their shuffle/repeat/like state and their click handlers.
 *
 * The selector places a passive hit-test shield over each preview. The shield
 * leaves swipes available to the pager without exposing player click handlers.
 *
 * Layout is done at true device size and scaled down by [scaleToFit], because
 * these styles adapt to phone-sized constraints (`maxHeight < 760.dp` and
 * friends); handing them a 260dp card would drop them all into their most
 * cramped branch and stop looking like the player at all.
 */
@Composable
fun RealPlayerStylePreview(
    style: PlayerDesignStyle,
    mediaMetadata: MediaMetadata?,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    sliderOverride: SliderStyle? = null,
    buttonsOverride: PlayerButtonsStyle? = null,
    backgroundOverride: PlayerBackgroundStyle? = null,
) {
    val playerConnection = LocalPlayerConnection.current
    if (mediaMetadata == null || playerConnection == null) return

    val context = LocalContext.current
    val audioDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()
    val (savedSliderStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val (savedButtonsStyle) = rememberEnumPreference(PlayerButtonsStyleKey, PlayerButtonsStyle.DEFAULT)
    val (showVolumeBar) = rememberPreference(ShowPlayerVolumeBarKey, true)
    val (cornerRadius) = rememberPreference(ThumbnailCornerRadiusKey, 8f)
    val (backdropBlurAmount) =
        rememberPreference(
            dev.citali.lunartune.constants.BackdropBlurAmountKey,
            defaultValue = 60,
        )

    // Sheet state, menu state and page state only ever reach the style composables
    // through click handlers, which the pointer guard above keeps from firing.
    val sheetState =
        rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = 0.dp,
            expandedBound = 900.dp,
        )
    val menuState = remember { MenuState() }
    val bottomSheetPageState = remember { BottomSheetPageState() }

    val sliderStyle = sliderOverride ?: savedSliderStyle
    val buttonsStyle = buttonsOverride ?: savedButtonsStyle
    val (savedBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val background = backgroundOverride ?: savedBackground
    val (customImage) = rememberPreference(PlayerCustomImageUriKey, "")
    val (customBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (customContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (customBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 30f)
    val durationMs = (mediaMetadata.duration * 1000L).coerceAtLeast(1L)
    val positionMs = (durationMs * 0.35f).toLong()

    val textColor = if (background == PlayerBackgroundStyle.DEFAULT) MaterialTheme.colorScheme.onSurface else Color.White
    val (textBackgroundColor, textButtonColor, iconButtonColor) =
        when (style) {
            PlayerDesignStyle.V9 ->
                Triple(
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                )

            else -> Triple(textColor,
                if (buttonsStyle == PlayerButtonsStyle.SECONDARY) MaterialTheme.colorScheme.secondary else textColor,
                if (buttonsStyle == PlayerButtonsStyle.SECONDARY) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.surface,
            )
        }

    scaleToFit(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            when (style) {
                PlayerDesignStyle.V8,
                PlayerDesignStyle.V10,
                -> {
                    V8PlayerBackdrop(
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        backdropBlurAmount = if (style == PlayerDesignStyle.V10) 100 else backdropBlurAmount,
                    )
                    V8PlayerContent(
                        mediaMetadata = mediaMetadata,
                        queueTitle = mediaMetadata.album?.title,
                        playbackState = Player.STATE_READY,
                        isPlaying = false,
                        isLoading = false,
                        canSkipPrevious = true,
                        canSkipNext = true,
                        currentSongLiked = false,
                        sliderPosition = null,
                        position = positionMs,
                        duration = durationMs,
                        volume = 0.6f,
                        showVolumeBar = showVolumeBar,
                        playerConnection = playerConnection,
                        navController = navController,
                        state = sheetState,
                        menuState = menuState,
                        bottomSheetPageState = bottomSheetPageState,
                        currentFormat = null,
                        canvasPrimaryUrl = null,
                        canvasFallbackUrl = null,
                        onSliderValueChange = {},
                        onSliderValueChangeFinished = {},
                        onVolumeChange = {},
                        appleMusic = style == PlayerDesignStyle.V10,
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = if (style == PlayerDesignStyle.V10) 92.dp else 108.dp),
                    )
                    if (style == PlayerDesignStyle.V10) {
                        QueueCollapsedContentAppleMusic(
                            textBackgroundColor = Color.White, onExpandQueue = {}, onShowLyrics = {},
                            onDeviceClick = {}, device = audioDevice,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        )
                    } else {
                        QueueCollapsedContentV7(
                            showCodecOnPlayer = false, currentFormat = null, textBackgroundColor = Color.White,
                            sleepTimerEnabled = false, sleepTimerTimeLeft = 0L,
                            onExpandQueue = {}, onShowLyrics = {}, onSleepTimerClick = {},
                            onDeviceClick = {}, device = audioDevice,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        )
                    }

                }

                PlayerDesignStyle.V5 -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
                        LandscapeLikeBox(Modifier.fillMaxSize()) {
                            LittlePlayerContent(
                                mediaMetadata = mediaMetadata, sliderPosition = null,
                                positionMs = positionMs, durationMs = durationMs,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                liked = false, onCollapse = {}, onToggleLike = {}, onExpandQueue = {}, onMenuClick = {},
                            )
                        }
                    }
                }
                PlayerDesignStyle.V7 -> {
                    V7PlayerBackdrop(
                        thumbnailUrl = mediaMetadata.thumbnailUrl, canvasStaticUrl = null,
                        canvasPrimaryUrl = null, canvasFallbackUrl = null,
                        isPlaying = false, disableBlur = disableBlur,
                        backdropBlurAmount = backdropBlurAmount, label = "stylePreview",
                    )
                    Column(Modifier.align(Alignment.BottomCenter).padding(top = 12.dp, bottom = 108.dp)) {
                        V8PlayerControlsContent(
                            mediaMetadata = mediaMetadata, queueTitle = "", playbackState = Player.STATE_READY,
                            isPlaying = false, isLoading = false, canSkipPrevious = true, canSkipNext = true,
                            currentSongLiked = false, sliderPosition = null, position = positionMs, duration = durationMs,
                            volume = 0.6f, showVolumeBar = showVolumeBar, currentFormat = null,
                            playerConnection = playerConnection, navController = navController,
                            state = sheetState, menuState = menuState, bottomSheetPageState = bottomSheetPageState,
                            onSliderValueChange = {}, onSliderValueChangeFinished = {}, onVolumeChange = {},
                        )
                    }
                    QueueCollapsedContentV7(
                        showCodecOnPlayer = false, currentFormat = null, textBackgroundColor = Color.White,
                        sleepTimerEnabled = false, sleepTimerTimeLeft = 0L,
                        onExpandQueue = {}, onShowLyrics = {}, onSleepTimerClick = {}, onDeviceClick = {}, device = audioDevice,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    )
                }
                PlayerDesignStyle.V9 -> {
                    V9PlayerBackdrop(
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        dimColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    )
                    V9PlayerContent(
                        mediaMetadata = mediaMetadata,
                        playbackState = Player.STATE_READY,
                        isPlaying = false,
                        isLoading = false,
                        canSkipPrevious = true,
                        canSkipNext = true,
                        sliderPosition = null,
                        position = positionMs,
                        duration = durationMs,
                        playerConnection = playerConnection,
                        navController = navController,
                        state = sheetState,
                        textBackgroundColor = textBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        canvasPrimaryUrl = null,
                        canvasFallbackUrl = null,
                        onCollapseClick = {},
                        onQueueClick = {},
                        onLyricsClick = {},
                        onSliderValueChange = {},
                        onSliderValueChangeFinished = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    PlayerBackground(
                        playerBackground = if (background == PlayerBackgroundStyle.GLOW_ANIMATED) PlayerBackgroundStyle.GLOW else background,
                        mediaMetadata = mediaMetadata,
                        gradientColors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                        disableBlur = disableBlur, blurRadius = blurRadius,
                        playerCustomImageUri = customImage, playerCustomBlur = customBlur,
                        playerCustomContrast = customContrast, playerCustomBrightness = customBrightness,
                    )
                    // Modern, Cinematic, Little and Expressive all share the player's
                    // tall layout: artwork on top, then the style's own controls block,
                    // which is where their differences live.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(cornerRadius.dp)),
                            ) {
                                AsyncImage(
                                    model = mediaMetadata.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        PlayerControlsContent(
                            mediaMetadata = mediaMetadata,
                            playerDesignStyle = style,
                            sliderStyle = sliderStyle,
                            playbackState = Player.STATE_READY,
                            isPlaying = false,
                            isLoading = false,
                            repeatMode = Player.REPEAT_MODE_OFF,
                            canSkipPrevious = true,
                            canSkipNext = true,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            textBackgroundColor = textBackgroundColor,
                            icBackgroundColor = if (background == PlayerBackgroundStyle.DEFAULT) MaterialTheme.colorScheme.surface else Color.Black,
                            sliderPosition = null,
                            position = positionMs,
                            duration = durationMs,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = sheetState,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            context = context,
                            onSliderValueChange = {},
                            onSliderValueChangeFinished = {},
                        )
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

/**
 * Renders [content] at phone size and scales it to fit the space it is given.
 *
 * The width is the only thing that matters for the scale: a card keeps the phone's
 * aspect ratio, so the height falls out of it and the styles see a layout they
 * would recognise.
 */
@Composable
private fun scaleToFit(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actualConfiguration = LocalConfiguration.current
    val virtualConfiguration = remember(actualConfiguration) {
        Configuration(actualConfiguration).apply {
            screenWidthDp = 393
            screenHeightDp = 852
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
    }
    Layout(content = {
        CompositionLocalProvider(LocalConfiguration provides virtualConfiguration) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }, modifier = modifier) { measurables, constraints ->
        val designWidth = 393.dp.roundToPx()
        val designHeight = 852.dp.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scale = minOf(width.toFloat() / designWidth, height.toFloat() / designHeight)
        val child = measurables.single().measure(Constraints.fixed(designWidth, designHeight))
        layout(width, height) {
            child.placeWithLayer(
                ((width - designWidth * scale) / 2).toInt(),
                ((height - designHeight * scale) / 2).toInt(),
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
