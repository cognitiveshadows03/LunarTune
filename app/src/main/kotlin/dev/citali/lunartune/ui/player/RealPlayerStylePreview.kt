/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

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
 * Those handlers are why the whole preview sits behind [ConsumeAllPointerInput]:
 * a tap or drag on a card must move the carousel, never the player underneath.
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
) {
    val playerConnection = LocalPlayerConnection.current
    if (mediaMetadata == null || playerConnection == null) return

    val context = LocalContext.current
    val (sliderStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val (buttonsStyle) = rememberEnumPreference(PlayerButtonsStyleKey, PlayerButtonsStyle.DEFAULT)
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

    val durationMs = (mediaMetadata.duration * 1000L).coerceAtLeast(1L)
    val positionMs = (durationMs * 0.35f).toLong()

    val textColor = Color.White
    val (textBackgroundColor, textButtonColor, iconButtonColor) =
        when (style) {
            PlayerDesignStyle.V9 ->
                Triple(
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                )

            else -> Triple(textColor, textColor.copy(alpha = 0.24f), textColor)
        }

    scaleToFit(modifier = modifier) {
        Box(modifier = ConsumeAllPointerInput) {
            when (style) {
                PlayerDesignStyle.V7,
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
                        isPlaying = true,
                        isLoading = false,
                        canSkipPrevious = true,
                        canSkipNext = true,
                        currentSongLiked = true,
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
                        modifier = Modifier.fillMaxSize(),
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
                        isPlaying = true,
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
                            isPlaying = true,
                            isLoading = false,
                            repeatMode = Player.REPEAT_MODE_OFF,
                            canSkipPrevious = true,
                            canSkipNext = true,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            textBackgroundColor = textBackgroundColor,
                            icBackgroundColor = textColor,
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
    BoxWithConstraints(modifier = modifier) {
        val designWidth = 393.dp
        val scale = (maxWidth / designWidth).coerceAtLeast(0.05f)
        val designHeight = (maxHeight / scale).coerceAtLeast(200.dp)
        Box(
            modifier =
                Modifier
                    .size(width = designWidth, height = designHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
        ) {
            content()
        }
    }
}

/**
 * Eats every pointer event in the initial pass, so the style composables below can
 * carry their real click handlers without the carousel ever pressing them.
 */
private val ConsumeAllPointerInput =
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
