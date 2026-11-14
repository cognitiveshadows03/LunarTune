/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.citali.lunartune.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.*
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.ui.player.RealPlayerStylePreview
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.PlayerStylePreviewViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.citali.lunartune.ui.component.HazeLiveBlurSupported
import dev.citali.lunartune.ui.component.frostedTopMenuStyle
import dev.citali.lunartune.ui.player.CustomisablePlayerStyles
import dev.citali.lunartune.ui.player.StyleOverrides

@Composable
fun PlayerStyleCarousel(
    navController: NavHostController,
    viewModel: PlayerStylePreviewViewModel = hiltViewModel(),
) {
    val configuration = LocalConfiguration.current
    val deviceRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val currentMetadata by remember(connection) { connection?.mediaMetadata ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lastSong by viewModel.lastPlayedSong.collectAsStateWithLifecycle()
    val metadata = currentMetadata ?: lastSong?.toMediaMetadata()
    val (savedStyle) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V7)
    val (savedSlider) = rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val (savedButtons) = rememberEnumPreference(PlayerButtonsStyleKey, PlayerButtonsStyle.DEFAULT)
    val (savedBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val (customImage) = rememberPreference(PlayerCustomImageUriKey, "")
    val families = remember {
        listOf(
            listOf(PlayerDesignStyle.V2), listOf(PlayerDesignStyle.V4), listOf(PlayerDesignStyle.V5),
            listOf(PlayerDesignStyle.V6, PlayerDesignStyle.V9),
            listOf(PlayerDesignStyle.V7, PlayerDesignStyle.V8), listOf(PlayerDesignStyle.V10),
        )
    }
    val familyPagers = families.map { family ->
        rememberPagerState(initialPage = family.indexOf(savedStyle).coerceAtLeast(0)) { family.size }
    }
    val pager = rememberPagerState(initialPage = families.indexOfFirst { savedStyle in it }.coerceAtLeast(0)) { families.size }
    // rememberPagerState is saveable down to the fractional scroll offset, so a restored
    // state can leave the selected card resting between slots. Snap back on entry.
    LaunchedEffect(Unit) {
        if (pager.currentPageOffsetFraction != 0f) pager.scrollToPage(pager.currentPage)
        familyPagers.forEach { state ->
            if (state.currentPageOffsetFraction != 0f) state.scrollToPage(state.currentPage)
        }
    }
    val selected = families[pager.currentPage][familyPagers[pager.currentPage].currentPage]
    var draftSlider by rememberSaveable { mutableStateOf(savedSlider) }
    var draftButtons by rememberSaveable { mutableStateOf(savedButtons) }
    var draftBackground by rememberSaveable { mutableStateOf(savedBackground) }
    val hazeState = remember { HazeState() }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var optionsTab by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val customizable = selected in CustomisablePlayerStyles
    // Every style keeps its own slider/buttons/background; switching the selection
    // loads that style's stored look back into the drafts.
    LaunchedEffect(selected) {
        draftSlider = StyleOverrides.sliderStyle(selected) ?: savedSlider
        draftButtons = StyleOverrides.buttonsStyle(selected) ?: savedButtons
        draftBackground = StyleOverrides.backgroundStyle(selected) ?: savedBackground
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding())
            .hazeSource(hazeState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { navController.navigateUp() }, enabled = !saving) {
                Text(stringResource(R.string.player_styles_exit))
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !saving && !pager.isScrollInProgress && !familyPagers[pager.currentPage].isScrollInProgress,
                onClick = {
                    val styleToApply = selected
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            PreferenceStore.editAndPublish(context) {
                                this[PlayerDesignStyleKey] = styleToApply.name
                                // Per-style, so switching styles later brings each
                                // one's own look back instead of overwriting it.
                                this[SliderStyleKeyFor(styleToApply.name)] = draftSlider.name
                                this[PlayerButtonsStyleKeyFor(styleToApply.name)] = draftButtons.name
                                this[PlayerBackgroundStyleKeyFor(styleToApply.name)] = draftBackground.name
                                if (draftBackground == PlayerBackgroundStyle.CUSTOM) {
                                    this[LyricsBackgroundStyleKey] = LyricsBackgroundStyle.CUSTOM.name
                                } else if (this[LyricsBackgroundStyleKey] == LyricsBackgroundStyle.CUSTOM.name) {
                                    this[LyricsBackgroundStyleKey] = LyricsBackgroundStyle.DEFAULT.name
                                }
                            }
                            navController.navigateUp()
                        } catch (_: Exception) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.player_styles_apply)) }
        }
        Text(
            stringResource(R.string.player_design_style),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // HyperOS-small cards: the card slot is centered vertically; the sibling
            // (base/extended) peeks as a strip from the bottom edge (next) or the top
            // edge (previous), always aligned to the same slot as the selected card.
            val pageGap = 20.dp
            val topPeekHeight = maxHeight * 0.10f
            val cardHeight = maxHeight - topPeekHeight * 2 - pageGap * 2
            val cardWidth = minOf(maxWidth * 0.72f, cardHeight * deviceRatio)
            val cardH = cardWidth / deviceRatio
            // Center the card slot vertically so the selected card always lands in the
            // same position and sibling peeks are symmetric strips at both edges.
            val slotPadding = ((maxHeight - cardH) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0,
                flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = LunarMotion.floaty()),
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val offset = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                // Full-size pager with symmetric content padding: the selected card
                // always lands in the centered slot, the sibling peeks as a clean
                // strip from the bottom (next) or top (previous) edge, and no card
                // is ever clipped mid-way.
                Box(Modifier.fillMaxSize().clipToBounds()) {
                VerticalPager(
                    state = familyPagers[page],
                    pageSize = PageSize.Fixed(cardH),
                    pageSpacing = pageGap,
                    contentPadding = PaddingValues(top = slotPadding, bottom = slotPadding),
                    userScrollEnabled = families[page].size > 1,
                    flingBehavior = PagerDefaults.flingBehavior(state = familyPagers[page], snapAnimationSpec = LunarMotion.floaty()),
                    modifier = Modifier.fillMaxSize(),
                ) { row ->
                val cardStyle = families[page][row]
                val verticalOffset = abs(familyPagers[page].currentPage - row + familyPagers[page].currentPageOffsetFraction).coerceIn(0f, 1f)
                val isSelected = page == pager.currentPage && row == familyPagers[page].currentPage
                Surface(
                    modifier = Modifier.width(cardWidth).aspectRatio(deviceRatio).graphicsLayer {
                        // HyperOS-style: focused card full size, side cards and the
                        // sibling peek shrink to ~85% like the Magazine picker.
                        val squeeze = 1f - verticalOffset * 0.15f
                        scaleX = (1f - offset * 0.15f) * squeeze
                        scaleY = (1f - offset * 0.15f) * squeeze
                        alpha = (1f - offset * 0.35f) * (1f - verticalOffset * 0.4f)
                    },
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                        if (isSelected) Color.White else MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
                        if (metadata != null && connection != null) {
                            RealPlayerStylePreview(
                                style = cardStyle, mediaMetadata = metadata, navController = navController,
                                modifier = Modifier.fillMaxSize(),
                                sliderOverride = if (isSelected) draftSlider else null,
                                buttonsOverride = if (isSelected) draftButtons else null,
                                backgroundOverride = if (isSelected) draftBackground else null,
                            )
                        } else {
                            Text(
                                stringResource(if (metadata == null) R.string.player_styles_no_history else R.string.player_styles_connecting),
                                modifier = Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        // This sibling wins hit testing over the real player. It never consumes,
                        // so the ancestor pager owns swipes; player buttons never see a touch.
                        Box(Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope { while (true) awaitPointerEvent() }
                        })
                        if (isSelected && cardStyle in CustomisablePlayerStyles) {
                            FilledTonalButton(
                                onClick = { showOptions = true },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.65f),
                                    contentColor = Color.White,
                                ),
                            ) { Text(stringResource(R.string.player_styles_customize)) }
                        }

                    }
                }
                }
                }
            }
            // Floating indicators — no background, hovering in the strip/gap below the
            // card. Horizontal always shows the family page; vertical only appears when
            // the selected family ships a base and an extended version.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = slotPadding + cardH + pageGap / 2 - 3.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                families.forEachIndexed { index, _ ->
                    PageDot(active = pager.currentPage == index)
                }
            }
            if (families[pager.currentPage].size > 1) {
                val familyState = familyPagers[pager.currentPage]
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    families[pager.currentPage].indices.forEach { index ->
                        PageDot(active = familyState.currentPage == index)
                    }
                }
            }
        }
        if (saveFailed) Text(stringResource(R.string.player_styles_save_failed), color = MaterialTheme.colorScheme.error)
    }
    if (showOptions && customizable) {
        val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
        val sheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
        val sheetScrim = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.26f),
            shape = sheetShape,
            dragHandle = null,
        ) {
            // Frosted like the top-bar sheet: the carousel behind is the haze source,
            // so the style cards stay visible and blurred under the options.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .clip(sheetShape)
                    .then(
                        if (HazeLiveBlurSupported) {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style =
                                    frostedTopMenuStyle(
                                        scrim = sheetScrim,
                                        fallbackScrim =
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
                                    ),
                                performanceMode = HazePerformanceMode.Performance,
                            )
                        } else {
                            Modifier.background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
                            )
                        },
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.22f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), sheetShape),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 10.dp, bottom = 20.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            onClick = { showOptions = false },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.player_styles_done),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        StyleOptionTabs(
                            selected = optionsTab,
                            onSelect = { optionsTab = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (optionsTab) {
                            0 -> {
                                SliderStyle.entries.toList().chunked(2).forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        row.forEach { option ->
                                            SliderPreviewChoice(
                                                style = option,
                                                selected = draftSlider == option,
                                                modifier = Modifier.weight(1f),
                                                onClick = { draftSlider = option },
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            1 -> {
                                PlayerButtonsStyle.entries.forEach { option ->
                                    StyleOptionCard(
                                        label = stringResource(
                                            if (option == PlayerButtonsStyle.DEFAULT) R.string.default_style
                                            else R.string.secondary_color_style,
                                        ),
                                        selected = draftButtons == option,
                                        onClick = { draftButtons = option },
                                    )
                                }
                            }

                            else -> {
                                PlayerBackgroundStyle.entries.forEach { option ->
                                    StyleOptionCard(
                                        label = backgroundName(option),
                                        selected = draftBackground == option,
                                        onClick = { draftBackground = option },
                                    )
                                }
                                if (customImage.isBlank()) {
                                    Text(
                                        stringResource(R.string.player_styles_custom_image_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.player_styles_draft_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** HyperOS-style segmented control: one capsule, the chosen tab sliding into it. */
@Composable
private fun StyleOptionTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<Int> =
        listOf(
            R.string.player_styles_tab_slider,
            R.string.player_styles_tab_buttons,
            R.string.player_styles_tab_background,
        ),
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, titleRes ->
            val active = selected == index
            val background by animateColorAsState(
                targetValue =
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                animationSpec = LunarMotion.floaty(),
                label = "styleTabBackground",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A HyperOS-style option card: full-width, ringed when chosen, dot on the left. */
@Composable
private fun StyleOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.40f),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 6.dp else 1.5.dp,
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PageDot(active: Boolean) {
    val dotSize by animateDpAsState(
        targetValue = if (active) 7.dp else 5.dp,
        animationSpec = LunarMotion.floaty(),
        label = "pageDot",
    )
    Box(
        Modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun SliderPreviewChoice(
    style: SliderStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.40f),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        modifier = modifier.height(104.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val y = size.height / 2
                if (style == SliderStyle.Circular) {
                    drawArc(tint.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(4.dp.toPx()))
                    drawArc(tint, -90f, 220f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                } else if (style == SliderStyle.Wavy) {
                    val path = Path()
                    for (i in 0..60) {
                        val x = size.width * i / 60f
                        val wave = kotlin.math.sin(i / 60f * 6f * kotlin.math.PI.toFloat()) * 4.dp.toPx()
                        if (i == 0) path.moveTo(x, y + wave) else path.lineTo(x, y + wave)
                    }
                    drawPath(path, tint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    val thickness = if (style == SliderStyle.Thick) 10.dp.toPx() else 3.dp.toPx()
                    drawLine(tint.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), thickness, StrokeCap.Round)
                    drawLine(tint, Offset(0f, y), Offset(size.width * 0.6f, y), thickness, StrokeCap.Round)
                    if (style == SliderStyle.Standard) drawCircle(tint, 5.dp.toPx(), Offset(size.width * 0.6f, y))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(sliderName(style), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun playerStyleName(style: PlayerDesignStyle): String = stringResource(when (style) {
    PlayerDesignStyle.V2 -> R.string.player_design_v2
    PlayerDesignStyle.V4 -> R.string.player_design_v4
    PlayerDesignStyle.V5 -> R.string.player_design_v5
    PlayerDesignStyle.V6 -> R.string.player_design_v6
    PlayerDesignStyle.V7 -> R.string.player_design_v7
    PlayerDesignStyle.V8 -> R.string.player_design_v8
    PlayerDesignStyle.V9 -> R.string.player_design_v9
    PlayerDesignStyle.V10 -> R.string.player_design_v10
})

@Composable
private fun sliderName(style: SliderStyle): String = stringResource(when (style) {
    SliderStyle.Standard -> R.string.slider_style_standard
    SliderStyle.Wavy -> R.string.slider_style_wavy
    SliderStyle.Thick -> R.string.slider_style_thick
    SliderStyle.Circular -> R.string.slider_style_circular
    SliderStyle.Simple -> R.string.slider_style_simple
})

@Composable
private fun backgroundName(style: PlayerBackgroundStyle): String = when (style) {
    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
    PlayerBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
    PlayerBackgroundStyle.COLORING -> stringResource(R.string.coloring)
    PlayerBackgroundStyle.BLUR_GRADIENT -> stringResource(R.string.blur_gradient)
    PlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
    PlayerBackgroundStyle.GLOW_ANIMATED -> stringResource(R.string.player_styles_animated_glow)
}
