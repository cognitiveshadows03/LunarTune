/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.*
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.ui.player.RealPlayerStylePreview
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.PlayerStylePreviewViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerStyleCarousel(
    navController: NavHostController,
    viewModel: PlayerStylePreviewViewModel = hiltViewModel(),
) {
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
    val styles = remember { PlayerDesignStyle.entries.toList() }
    val pager = rememberPagerState(initialPage = styles.indexOf(savedStyle).coerceAtLeast(0)) { styles.size }
    val selected = styles[pager.currentPage]
    var draftSlider by rememberSaveable { mutableStateOf(savedSlider) }
    var draftButtons by rememberSaveable { mutableStateOf(savedButtons) }
    var draftBackground by rememberSaveable { mutableStateOf(savedBackground) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val customizable = selected in listOf(PlayerDesignStyle.V2, PlayerDesignStyle.V4, PlayerDesignStyle.V5, PlayerDesignStyle.V6)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding())
            .blur(if (showOptions) 16.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { navController.navigateUp() }, enabled = !saving) {
                Text(stringResource(R.string.player_styles_exit))
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                enabled = !saving && !pager.isScrollInProgress,
                onClick = {
                    val styleToApply = selected
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            PreferenceStore.editAndPublish(context) {
                                this[PlayerDesignStyleKey] = styleToApply.name
                                this[SliderStyleKey] = draftSlider.name
                                this[PlayerButtonsStyleKey] = draftButtons.name
                                this[PlayerBackgroundStyleKey] = draftBackground.name
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
        Text(stringResource(R.string.player_design_style), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(playerStyleName(selected), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = minOf(maxWidth * 0.78f, maxHeight * (393f / 852f))
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val offset = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                Surface(
                    modifier = Modifier.width(cardWidth).aspectRatio(393f / 852f).graphicsLayer {
                        scaleX = 1f - offset * 0.055f
                        scaleY = 1f - offset * 0.055f
                        alpha = 1f - offset * 0.25f
                    },
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(if (page == pager.currentPage) 2.dp else 1.dp,
                        if (page == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
                        if (metadata != null && connection != null) {
                            RealPlayerStylePreview(
                                style = styles[page], mediaMetadata = metadata, navController = navController,
                                modifier = Modifier.fillMaxSize(), sliderOverride = draftSlider,
                                buttonsOverride = draftButtons, backgroundOverride = draftBackground,
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
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            styles.forEachIndexed { index, _ ->
                Box(Modifier.size(if (pager.currentPage == index) 7.dp else 5.dp).clip(CircleShape)
                    .background(if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        if (saveFailed) Text(stringResource(R.string.player_styles_save_failed), color = MaterialTheme.colorScheme.error)
        if (customizable) {
            OutlinedButton(onClick = { showOptions = true }, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.player_styles_customize))
            }
        } else {
            Text(stringResource(R.string.player_styles_ready), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
    if (showOptions && customizable) {
        Dialog(onDismissRequest = { showOptions = false }) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.player_styles_customize), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.player_styles_draft_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.player_slider_style), style = MaterialTheme.typography.titleSmall)
                    SliderStyle.entries.forEach { option ->
                        DraftChoice(sliderName(option), draftSlider == option) { draftSlider = option }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.player_buttons_style), style = MaterialTheme.typography.titleSmall)
                    PlayerButtonsStyle.entries.forEach { option ->
                        DraftChoice(stringResource(if (option == PlayerButtonsStyle.DEFAULT) R.string.default_style else R.string.secondary_color_style), draftButtons == option) { draftButtons = option }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.player_background_style), style = MaterialTheme.typography.titleSmall)
                    PlayerBackgroundStyle.entries.filter {
                        (it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= 31) &&
                            (it != PlayerBackgroundStyle.CUSTOM || customImage.isNotBlank())
                    }.forEach { option ->
                        DraftChoice(backgroundName(option), draftBackground == option) { draftBackground = option }
                    }
                    TextButton(onClick = { showOptions = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
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
