/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerButtonsStyle
import dev.citali.lunartune.constants.PlayerButtonsStyleKey
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerDesignStyleKey
import dev.citali.lunartune.constants.ShowPlayerVolumeBarKey
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.FrostedFallbackAlpha
import dev.citali.lunartune.ui.component.PlayerSheetPresenter
import dev.citali.lunartune.ui.component.frostedNavBarStyle
import dev.citali.lunartune.ui.player.RealPlayerStylePreview
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.PlayerStylePreviewViewModel
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Player styles picked in a focus carousel instead of a list, showing the player's
 * real layout for each style around the newest entry in play history.
 *
 * The rail changes axis with the styles: Modern, Cinematic, Little and Apple Music
 * page sideways, while Expressive, Immersive, Immersive Extended and Material
 * Extended stack in a column you scroll up and down, because those four are the
 * ones you compare as a set.
 *
 * [PlayerDesignStyle] is only written when the user asks for it, so backing out
 * leaves the app untouched; asking for it hands the previewed song to the player
 * and grows the focused card into the sheet.
 */
/**
 * One page of the rail: a style, plus the style that belongs beneath it.
 *
 * Immersive carries Immersive Extended and Expressive carries Material Extended,
 * because those pairs are the ones worth comparing side by side - so they share a
 * page, stacked, while every group still pages horizontally.
 */
private data class StyleGroup(
    val primary: PlayerDesignStyle,
    val secondary: PlayerDesignStyle? = null,
) {
    val members: List<PlayerDesignStyle>
        get() = listOfNotNull(primary, secondary)
}

private val StyleGroups =
    listOf(
        StyleGroup(PlayerDesignStyle.V2),
        StyleGroup(PlayerDesignStyle.V4),
        StyleGroup(PlayerDesignStyle.V5),
        StyleGroup(PlayerDesignStyle.V6, PlayerDesignStyle.V9),
        StyleGroup(PlayerDesignStyle.V7, PlayerDesignStyle.V8),
        StyleGroup(PlayerDesignStyle.V10),
    )

/**
 * Player styles picked in a focus carousel instead of a list, showing the player's
 * real layout for each style around the newest entry in play history.
 *
 * Pages page sideways. Immersive / Immersive Extended and Expressive / Material
 * Extended share a page with the second style stacked under the first, so both are
 * visible at once; tapping either card focuses it for Set.
 *
 * [PlayerDesignStyle] is only written when the user asks for it, so backing out
 * leaves the app untouched.
 */
@Composable
fun PlayerStyleCarousel(
    navController: NavHostController,
    playerSheetPresenter: PlayerSheetPresenter,
) {
    val (selectedStyle, onSelectedStyleChange) =
        rememberEnumPreference(
            key = PlayerDesignStyleKey,
            defaultValue = PlayerDesignStyle.V7,
        )
    val (backgroundStyle, onBackgroundStyleChange) =
        rememberEnumPreference(
            key = PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (buttonsStyle, onButtonsStyleChange) =
        rememberEnumPreference(
            key = PlayerButtonsStyleKey,
            defaultValue = PlayerButtonsStyle.DEFAULT,
        )
    val (showVolumeBar, onShowVolumeBarChange) =
        rememberPreference(
            key = ShowPlayerVolumeBarKey,
            defaultValue = true,
        )

    val viewModel: PlayerStylePreviewViewModel = hiltViewModel()
    val lastPlayedSong by viewModel.lastPlayedSong.collectAsStateWithLifecycle()
    val previewMetadata = remember(lastPlayedSong) { lastPlayedSong?.toMediaMetadata() }
    val playerConnection = LocalPlayerConnection.current

    val initialPage =
        StyleGroups.indexOfFirst { selectedStyle in it.members }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { StyleGroups.size }
    val scope = rememberCoroutineScope()

    // Within a paired page either card can be the one Set applies.
    var focusedStyle by remember { mutableStateOf(selectedStyle) }
    LaunchedEffect(pagerState.currentPage) {
        focusedStyle = StyleGroups[pagerState.currentPage.coerceIn(StyleGroups.indices)].primary
    }

    val hazeState = rememberHazeState()
    // colours are read in composition, so the style is built from captured values
    val popupScrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val popupFallbackScrim = MaterialTheme.colorScheme.surface.copy(alpha = FrostedFallbackAlpha)
    val popupStyle =
        remember(popupScrim, popupFallbackScrim) {
            frostedNavBarStyle(
                scrim = popupScrim,
                fallbackScrim = popupFallbackScrim,
                blurRadiusDp = 18f,
            )
        }

    var showEditSheet by remember { mutableStateOf(false) }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var screenBounds by remember { mutableStateOf<Rect?>(null) }
    var keepMorphPreview by remember { mutableStateOf(false) }
    val morphProgress = remember { Animatable(0f) }

    val finish = {
        onSelectedStyleChange(focusedStyle)
        lastPlayedSong?.let { song ->
            playerConnection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        }
        playerSheetPresenter.expandPlayer()
        navController.navigateUp()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { screenBounds = it.boundsInWindow() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
        ) {
            StylesHeader(
                styleName = focusedStyle.displayName(),
                onBack = navController::navigateUp,
            )

            StylesPager(
                groups = StyleGroups,
                pagerState = pagerState,
                metadata = previewMetadata,
                focusedStyle = focusedStyle,
                onStyleFocus = { focusedStyle = it },
                onFocusedCardBoundsChange = { cardBounds = it },
                navController = navController,
                modifier = Modifier.weight(1f),
            )

            PagerDots(
                groups = StyleGroups,
                selectedPage = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            StylesActionBar(
                editEnabled = focusedStyle.supportsCustomization(),
                onEdit = { showEditSheet = true },
                onSet = {
                    val from = cardBounds
                    val to = screenBounds
                    if (from == null || to == null || !from.hasArea()) {
                        finish()
                    } else {
                        keepMorphPreview = true
                        scope.launch {
                            morphProgress.animateTo(1f, animationSpec = tween(durationMillis = 420))
                            finish()
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 16.dp),
            )
        }

        val from = cardBounds
        val to = screenBounds
        if (keepMorphPreview && from != null && to != null) {
            MorphingPreview(
                from = from,
                to = to,
                progress = morphProgress.value,
                style = focusedStyle,
                metadata = previewMetadata,
                navController = navController,
            )
        }

        AnimatedVisibility(
            visible = showEditSheet,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            StylesEditOverlay(
                styleName = focusedStyle.displayName(),
                hazeState = hazeState,
                popupStyle = popupStyle,
                backgroundStyle = backgroundStyle,
                onBackgroundStyleChange = onBackgroundStyleChange,
                buttonsStyle = buttonsStyle,
                onButtonsStyleChange = onButtonsStyleChange,
                showVolumeBar = showVolumeBar,
                onShowVolumeBarChange = onShowVolumeBarChange,
                onDismiss = { showEditSheet = false },
            )
        }
    }
}

private fun Rect.hasArea(): Boolean = width > 1f && height > 1f

/**
 * Focuses a card on tap.
 *
 * The preview inside the card consumes every pointer event so the player's own
 * handlers stay inert, and that would also eat a normal click on the card. This
 * watches the initial pass, which reaches this modifier before any descendant can
 * consume it, and only fires when the finger stayed inside the touch slop - so a
 * drag still pages the rail.
 */
private fun Modifier.tapToFocus(onFocus: () -> Unit): Modifier =
    pointerInput(onFocus) {
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.pressed } ?: continue
                val start = down.position
                var released: androidx.compose.ui.input.pointer.PointerInputChange? = null
                while (true) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial)
                            .changes
                            .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        released = change
                        break
                    }
                }
                val end = released
                if (end != null && (end.position - start).getDistance() < viewConfiguration.touchSlop) {
                    onFocus()
                }
            }
        }
    }


/* ------------------------------------------------------------------ */
/* rail                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun StylesPager(
    groups: List<StyleGroup>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    metadata: MediaMetadata?,
    focusedStyle: PlayerDesignStyle,
    onStyleFocus: (PlayerDesignStyle) -> Unit,
    onFocusedCardBoundsChange: (Rect) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val screenWidth = configuration.screenWidthDp.dp
        val cardWidth =
            (screenWidth * 0.66f)
                .coerceAtMost(320.dp)
                .coerceAtLeast(180.dp)
        val gap = 12.dp
        val available = (maxHeight - 20.dp).coerceAtLeast(cardWidth * 1.4f)

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val group = groups[page]
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val distance = offset.absoluteValue.coerceIn(0f, 1f)

            val primaryHeight =
                if (group.secondary == null) {
                    available
                } else {
                    ((available - gap) * 0.58f).coerceAtLeast(cardWidth * 1.1f)
                }
            val secondaryHeight =
                ((available - gap - primaryHeight).coerceAtLeast(cardWidth * 0.55f))

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - distance * 0.06f
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - distance * 0.35f
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StyleCard(
                    style = group.primary,
                    metadata = metadata,
                    navController = navController,
                    cardWidth = cardWidth,
                    cardHeight = primaryHeight,
                    focused = page == pagerState.currentPage && focusedStyle == group.primary,
                    onBoundsChange = onFocusedCardBoundsChange,
                    onFocus = { onStyleFocus(group.primary) },
                )
                group.secondary?.let { secondary ->
                    Spacer(Modifier.height(gap))
                    StyleCard(
                        style = secondary,
                        metadata = metadata,
                        navController = navController,
                        cardWidth = cardWidth,
                        cardHeight = secondaryHeight,
                        focused = page == pagerState.currentPage && focusedStyle == secondary,
                        onBoundsChange = onFocusedCardBoundsChange,
                        onFocus = { onStyleFocus(secondary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: PlayerDesignStyle,
    metadata: MediaMetadata?,
    navController: NavHostController,
    cardWidth: Dp,
    cardHeight: Dp,
    focused: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onFocus: () -> Unit,
) {
    val boundsModifier =
        if (focused) {
            Modifier.onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
        } else {
            Modifier
        }
    Surface(
        shape = RoundedCornerShape(cardWidth * 0.09f),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            if (focused) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        shadowElevation = if (focused) 16.dp else 4.dp,
        modifier =
            Modifier
                .size(width = cardWidth, height = cardHeight)
                .then(boundsModifier)
                .tapToFocus(onFocus),
    ) {
        RealPlayerStylePreview(
            style = style,
            mediaMetadata = metadata,
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PagerDots(
    groups: List<StyleGroup>,
    selectedPage: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { index, _ ->
            val selected = index == selectedPage
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (selected) 16.dp else 5.dp, height = 5.dp)
                        .background(
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* chrome                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun StylesHeader(
    styleName: String,
    onBack: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.player_style_carousel_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = styleName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StylesActionBar(
    editEnabled: Boolean,
    onEdit: () -> Unit,
    onSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionPill(
            label = stringResource(R.string.player_style_carousel_edit),
            enabled = editEnabled,
            onClick = onEdit,
            modifier = Modifier.weight(1f),
        )
        ActionPill(
            label = stringResource(R.string.player_style_carousel_set),
            enabled = true,
            primary = true,
            onClick = onSet,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color =
            when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                primary -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        modifier =
            modifier
                .height(50.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        primary -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* edit overlay                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun StylesEditOverlay(
    styleName: String,
    hazeState: HazeState,
    popupStyle: HazeBlurStyle,
    backgroundStyle: PlayerBackgroundStyle,
    onBackgroundStyleChange: (PlayerBackgroundStyle) -> Unit,
    buttonsStyle: PlayerButtonsStyle,
    onButtonsStyleChange: (PlayerButtonsStyle) -> Unit,
    showVolumeBar: Boolean,
    onShowVolumeBarChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 24.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 92.dp)
                    .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = popupStyle,
                                performanceMode = HazePerformanceMode.Performance,
                            ),
                )
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.player_style_carousel_edit),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = styleName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        label = stringResource(R.string.player_background_style),
                        options =
                            listOf(
                                PlayerBackgroundStyle.DEFAULT to stringResource(R.string.follow_theme),
                                PlayerBackgroundStyle.GRADIENT to stringResource(R.string.gradient),
                                PlayerBackgroundStyle.BLUR to stringResource(R.string.blur),
                                PlayerBackgroundStyle.GLOW to stringResource(R.string.glow),
                            ),
                        selected = backgroundStyle,
                        onSelect = onBackgroundStyleChange,
                    )
                    Spacer(Modifier.height(12.dp))
                    ChoiceRow(
                        label = stringResource(R.string.player_buttons_style),
                        options =
                            listOf(
                                PlayerButtonsStyle.DEFAULT to stringResource(R.string.default_style),
                                PlayerButtonsStyle.SECONDARY to stringResource(R.string.secondary_color_style),
                            ),
                        selected = buttonsStyle,
                        onSelect = onButtonsStyleChange,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.show_player_volume_bar),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(checked = showVolumeBar, onCheckedChange = onShowVolumeBarChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    shape = CircleShape,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        },
                    modifier =
                        Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(value) },
                            ),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* morph                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun MorphingPreview(
    from: Rect,
    to: Rect,
    progress: Float,
    style: PlayerDesignStyle,
    metadata: dev.citali.lunartune.models.MediaMetadata?,
    navController: NavHostController,
) {
    // The card grows toward the sheet's frame on the same curve the sheet uses,
    // then hands over: the two never have to line up pixel for pixel.
    val p = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val left = from.left + (to.left - from.left) * p
    val top = from.top + (to.top - from.top) * p
    val width = from.width + (to.width - from.width) * p
    val height = from.height + (to.height - from.height) * p

    with(density) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(width = width.toDp(), height = height.toDp())
                    .graphicsLayer { alpha = 1f - (p * 0.12f) },
        ) {
            RealPlayerStylePreview(
                style = style,
                mediaMetadata = metadata,
                navController = navController,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* helpers                                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun PlayerDesignStyle.displayName(): String =
    when (this) {
        PlayerDesignStyle.V2 -> stringResource(R.string.player_design_v2)
        PlayerDesignStyle.V4 -> stringResource(R.string.player_design_v4)
        PlayerDesignStyle.V5 -> stringResource(R.string.player_design_v5)
        PlayerDesignStyle.V6 -> stringResource(R.string.player_design_v6)
        PlayerDesignStyle.V7 -> stringResource(R.string.player_design_v7)
        PlayerDesignStyle.V8 -> stringResource(R.string.player_design_v8)
        PlayerDesignStyle.V9 -> stringResource(R.string.player_design_v9)
        PlayerDesignStyle.V10 -> stringResource(R.string.player_design_v10)
    }

/** The four styles whose background and button options the player honours. */
private fun PlayerDesignStyle.supportsCustomization(): Boolean =
    this == PlayerDesignStyle.V2 ||
        this == PlayerDesignStyle.V4 ||
        this == PlayerDesignStyle.V5 ||
        this == PlayerDesignStyle.V6
