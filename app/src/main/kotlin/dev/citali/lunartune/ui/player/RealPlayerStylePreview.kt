/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.runtime.produceState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.citali.lunartune.ui.theme.PlayerColorExtractor
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
import dev.citali.lunartune.models.ActiveOutputDevice
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
 * Layout is done at true device size in dp and scaled down by [scaleToFit], because
 * these styles adapt to phone-sized constraints (`maxHeight < 760.dp` and
 * friends); handing them a 260dp card would drop them all into their most
 * cramped branch and stop looking like the player at all. The dp layout is exact
 * while the pixels behind it are cut to the size the card is drawn at, so the
 * styles look right without being rasterised at full screen resolution.
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
    // The carousel resolves the output device once and hands it to every card. Each
    // card collecting the flow itself meant one subscriber per visible preview for a
    // value they all render identically.
    audioDevice: ActiveOutputDevice? = null,
) {
    val playerConnection = LocalPlayerConnection.current
    if (mediaMetadata == null || playerConnection == null) return

    val context = LocalContext.current
    val resolvedAudioDevice =
        audioDevice ?: playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle().value
    val savedSliderStyle = rememberSliderStyleFor(style)
    val savedButtonsStyle = rememberButtonsStyleFor(style)
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
    val savedBackground = rememberBackgroundStyleFor(style)
    val background = backgroundOverride ?: savedBackground
    val (customImage) = rememberPreference(PlayerCustomImageUriKey, "")
    val (customBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (customContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (customBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 30f)
    val durationMs = (mediaMetadata.duration * 1000L).coerceAtLeast(1L)
    val positionMs = (durationMs * 0.35f).toLong()

    val fallback = MaterialTheme.colorScheme.primary
    // Only V9 and the styles that fall through to the shared background read the
    // extracted gradient. Cinematic, Little, Modern and Expressive paint their own
    // backdrops, so for them the decode and the Palette run are skipped outright -
    // and a carousel page shows three or more cards at once.
    val paintsFromPalette =
        style != PlayerDesignStyle.V5 &&
            style != PlayerDesignStyle.V7 &&
            style != PlayerDesignStyle.V8 &&
            style != PlayerDesignStyle.V10
    val extractedColors by produceState(
        initialValue = PreviewColorCache.None,
        mediaMetadata.thumbnailUrl,
        fallback,
        paintsFromPalette,
    ) {
        if (!paintsFromPalette) return@produceState
        val url = mediaMetadata.thumbnailUrl ?: return@produceState
        // The same artwork is behind every card, so one extraction serves the page
        // instead of one per card.
        PreviewColorCache.get(url)?.let {
            value = it
            return@produceState
        }
        val extracted = runCatching {
            withContext(Dispatchers.IO) {
                val image = context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(url).size(128).allowHardware(false).build()).image
                image?.toBitmap()?.let { bitmap ->
                    val palette = Palette.from(bitmap).clearFilters()
                        .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                        .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA).generate()
                    PlayerColorExtractor.extractGradientColors(palette, fallback.toArgb())
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (extracted != null) {
            PreviewColorCache.put(url, extracted)
            value = extracted
        }
    }
    val colors = extractedColors.ifEmpty { listOf(fallback) }
    val dominant = colors.firstOrNull() ?: fallback
    val dark = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    val v9Text = remember(dominant, dark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
        hsv[1] = if (dark) hsv[1].coerceAtMost(0.12f) else hsv[1].coerceIn(0.12f, 0.35f)
        hsv[2] = if (dark) 0.96f else 0.08f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val v9Icon = if (0.299f * dominant.red + 0.587f * dominant.green + 0.114f * dominant.blue > 0.5f) Color.Black else Color.White

    val textColor = if (background == PlayerBackgroundStyle.DEFAULT) MaterialTheme.colorScheme.onSurface else Color.White
    val (textBackgroundColor, textButtonColor, iconButtonColor) =
        when (style) {
            PlayerDesignStyle.V9 ->
                Triple(
                    v9Text, dominant, v9Icon,
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
                        previewMode = true,
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = if (style == PlayerDesignStyle.V10) 92.dp else 108.dp),
                    )
                    if (style == PlayerDesignStyle.V10) {
                        QueueCollapsedContentAppleMusic(
                            textBackgroundColor = Color.White, onExpandQueue = {}, onShowLyrics = {},
                            onDeviceClick = {}, device = resolvedAudioDevice,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        )
                    } else {
                        QueueCollapsedContentV7(
                            showCodecOnPlayer = false, currentFormat = null, textBackgroundColor = Color.White,
                            sleepTimerEnabled = false, sleepTimerTimeLeft = 0L,
                            onExpandQueue = {}, onShowLyrics = {}, onSleepTimerClick = {},
                            onDeviceClick = {}, device = resolvedAudioDevice,
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
                        onExpandQueue = {}, onShowLyrics = {}, onSleepTimerClick = {}, onDeviceClick = {}, device = resolvedAudioDevice,
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
                        gradientColors = colors,
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 100.dp),
                    )
                    QueueCollapsedContentV9(
                        showCodecOnPlayer = false, currentFormat = null,
                        textBackgroundColor = textBackgroundColor,
                        sleepTimerEnabled = false, sleepTimerTimeLeft = 0L,
                        shuffleModeEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
                        onShuffleClick = {}, onRepeatModeClick = {}, onMenuClick = {}, onSleepTimerClick = {},
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    )
                }

                else -> {
                    PlayerBackground(
                        playerBackground = if (background == PlayerBackgroundStyle.GLOW_ANIMATED) PlayerBackgroundStyle.GLOW else background,
                        mediaMetadata = mediaMetadata,
                        gradientColors = colors,
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
 * The gradient behind the card, memoised by artwork URL.
 *
 * Every card on the carousel draws the same song, so without this each one decoded
 * the artwork and ran the Palette quantiser for an identical result. Bounded and
 * insertion-ordered, so a few songs' worth of colours live here and no more.
 */
private object PreviewColorCache {
    val None: List<Color> = emptyList()
    private const val MaxEntries = 6
    private val entries =
        object : LinkedHashMap<String, List<Color>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>?): Boolean =
                size > MaxEntries
        }

    fun get(url: String): List<Color>? = synchronized(entries) { entries[url] }

    fun put(url: String, colors: List<Color>) {
        synchronized(entries) { entries[url] = colors }
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
            screenWidthDp = actualConfiguration.screenWidthDp.coerceAtLeast(1)
            screenHeightDp = actualConfiguration.screenHeightDp.coerceAtLeast(1)
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
    }
    val density = LocalDensity.current
    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val designWidthDp = virtualConfiguration.screenWidthDp.coerceAtLeast(1)
        val designHeightDp = virtualConfiguration.screenHeightDp.coerceAtLeast(1)
        val designWidthPx = designWidthDp * density.density
        // Rasterise at the size the card is actually drawn at instead of at full device
        // size and shrinking the result. The styles adapt to dp, so the layout stays the
        // same either way - but the pixels do not: a card a little under three quarters
        // of the screen wide was paying for a full-screen-size layer, and the blurred
        // backdrops are priced in pixels, so every visible card was blurring frames of a
        // 1080p surface to show them at 700px. Supersampling keeps text crisp once the
        // layer is scaled back up, and the floor keeps a tiny card legible.
        val rasterScale =
            ((width / designWidthPx) * PreviewSupersample)
                .coerceIn(PreviewMinRasterScale, 1f)
        val rasterDensity = Density(density.density * rasterScale, density.fontScale)
        val childWidth = (designWidthDp * rasterDensity.density).roundToInt().coerceAtLeast(1)
        val childHeight = (designHeightDp * rasterDensity.density).roundToInt().coerceAtLeast(1)
        val child =
            subcompose(Unit) {
                CompositionLocalProvider(
                    LocalConfiguration provides virtualConfiguration,
                    LocalDensity provides rasterDensity,
                ) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }.first().measure(Constraints.fixed(childWidth, childHeight))
        layout(width, height) {
            val scale = minOf(width.toFloat() / childWidth, height.toFloat() / childHeight)
            child.placeWithLayer(
                ((width - childWidth * scale) / 2).roundToInt(),
                ((height - childHeight * scale) / 2).roundToInt(),
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** Extra resolution over the drawn size, so scaled text stays sharp. */
private const val PreviewSupersample = 1.15f

/** Below this the card would rasterise visibly soft, whatever the savings. */
private const val PreviewMinRasterScale = 0.5f
