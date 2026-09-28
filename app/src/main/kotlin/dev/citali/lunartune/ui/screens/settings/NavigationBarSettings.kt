/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.HideNavigationBarLabelsKey
import dev.citali.lunartune.constants.NavBarLongPressActionsKey
import dev.citali.lunartune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import dev.citali.lunartune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import dev.citali.lunartune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import dev.citali.lunartune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import dev.citali.lunartune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import dev.citali.lunartune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import dev.citali.lunartune.constants.NavigationBarCornerRadiusKey
import dev.citali.lunartune.constants.NavigationBarHeight
import dev.citali.lunartune.constants.NavigationBarHeightKey
import dev.citali.lunartune.constants.NavigationBarLabelSpacingKey
import dev.citali.lunartune.constants.NavigationBarOpacityKey
import dev.citali.lunartune.constants.NavigationBarStyle
import dev.citali.lunartune.constants.NavigationBarStyleKey
import dev.citali.lunartune.constants.NavigationBarTransparencyKey
import dev.citali.lunartune.constants.NavigationBarWidthKey
import dev.citali.lunartune.ui.component.DefaultDialog
import dev.citali.lunartune.ui.component.EnumListPreference
import dev.citali.lunartune.ui.component.FrostedBgGlowDefault
import dev.citali.lunartune.ui.component.FrostedBgGlowKey
import dev.citali.lunartune.ui.component.FrostedBlurDefault
import dev.citali.lunartune.ui.component.FrostedBlurKey
import dev.citali.lunartune.ui.component.FrostedBlurMax
import dev.citali.lunartune.ui.component.FrostedDimDefault
import dev.citali.lunartune.ui.component.FrostedDimKey
import dev.citali.lunartune.ui.component.FrostedFallbackAlpha
import dev.citali.lunartune.ui.component.FrostedGlowDefault
import dev.citali.lunartune.ui.component.FrostedGlowKey
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.component.frostedNavBarStyle
import dev.citali.lunartune.ui.screens.Screens
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NavigationBarSettings(navController: NavController) {
    val (navigationBarStyle, onNavigationBarStyleChange) =
        rememberEnumPreference(
            NavigationBarStyleKey,
            defaultValue = NavigationBarStyle.DEFAULT,
        )
    val (hideNavigationBarLabels, onHideNavigationBarLabelsChange) =
        rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)
    val (navBarLongPressActions, onNavBarLongPressActionsChange) =
        rememberPreference(NavBarLongPressActionsKey, defaultValue = true)
    val (navigationBarWidth, onNavigationBarWidthChange) =
        rememberPreference(NavigationBarWidthKey, defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT)
    val (navigationBarHeight, onNavigationBarHeightChange) =
        rememberPreference(NavigationBarHeightKey, defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT)
    val (navigationBarOpacity, onNavigationBarOpacityChange) =
        rememberPreference(NavigationBarOpacityKey, defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT)
    val (navigationBarTransparency, onNavigationBarTransparencyChange) =
        rememberPreference(
            NavigationBarTransparencyKey,
            defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
        )
    val (navigationBarLabelSpacing, onNavigationBarLabelSpacingChange) =
        rememberPreference(
            NavigationBarLabelSpacingKey,
            defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
        )
    val (navigationBarCornerRadius, onNavigationBarCornerRadiusChange) =
        rememberPreference(
            NavigationBarCornerRadiusKey,
            defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
        )
    val (frostedDim, onFrostedDimChange) =
        rememberPreference(FrostedDimKey, defaultValue = FrostedDimDefault)
    val (frostedBlur, onFrostedBlurChange) =
        rememberPreference(FrostedBlurKey, defaultValue = FrostedBlurDefault)
    val (frostedGlow, onFrostedGlowChange) =
        rememberPreference(FrostedGlowKey, defaultValue = FrostedGlowDefault)
    val (frostedBgGlow, onFrostedBgGlowChange) =
        rememberPreference(FrostedBgGlowKey, defaultValue = FrostedBgGlowDefault)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navigation_bar_settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.general)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.navigation_bar_style)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = navigationBarStyle,
                        onValueSelected = onNavigationBarStyleChange,
                        valueText = {
                            when (it) {
                                NavigationBarStyle.DEFAULT ->
                                    stringResource(R.string.navigation_bar_style_default)
                                NavigationBarStyle.FLOATING ->
                                    stringResource(R.string.navigation_bar_style_floating)
                                NavigationBarStyle.FROSTED ->
                                    stringResource(R.string.navigation_bar_style_frosted)
                                NavigationBarStyle.OUTLINED ->
                                    stringResource(R.string.navigation_bar_style_outlined)
                                NavigationBarStyle.CAPSULE ->
                                    stringResource(R.string.navigation_bar_style_capsule)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_navigation_bar_labels)) },
                        description = stringResource(R.string.hide_navigation_bar_labels_desc),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        checked = hideNavigationBarLabels,
                        onCheckedChange = onHideNavigationBarLabelsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.navbar_long_press_actions)) },
                        description = stringResource(R.string.navbar_long_press_actions_desc),
                        icon = { Icon(painterResource(R.drawable.shuffle), null) },
                        checked = navBarLongPressActions,
                        onCheckedChange = onNavBarLongPressActionsChange,
                    )
                }
            }

            if (navigationBarStyle == NavigationBarStyle.FROSTED) {
                PreferenceGroup(title = stringResource(R.string.navigation_bar_style_frosted)) {
                    item {
                        SliderPreferenceRow(
                            title = stringResource(R.string.frosted_dim),
                            description = stringResource(R.string.frosted_dim_desc),
                            iconRes = R.drawable.tune,
                            value = frostedDim,
                            onValueChange = onFrostedDimChange,
                            range = 0f..0.9f,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            default = FrostedDimDefault,
                            preview = { tempDim ->
                                NavBarPreview(
                                    widthFraction = navigationBarWidth,
                                    heightMultiplier = navigationBarHeight,
                                    opacity = navigationBarOpacity,
                                    transparency = navigationBarTransparency,
                                    labelSpacing = navigationBarLabelSpacing,
                                    cornerRadius = navigationBarCornerRadius,
                                    style = navigationBarStyle,
                                    frostedDim = tempDim,
                                )
                            },
                        )
                    }

                    item {
                        SliderPreferenceRow(
                            title = stringResource(R.string.frosted_blur),
                            description = stringResource(R.string.frosted_blur_desc),
                            iconRes = R.drawable.tune,
                            value = frostedBlur,
                            onValueChange = onFrostedBlurChange,
                            range = 0f..FrostedBlurMax,
                            valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                            default = FrostedBlurDefault,
                            preview = { tempBlur ->
                                NavBarPreview(
                                    widthFraction = navigationBarWidth,
                                    heightMultiplier = navigationBarHeight,
                                    opacity = navigationBarOpacity,
                                    transparency = navigationBarTransparency,
                                    labelSpacing = navigationBarLabelSpacing,
                                    cornerRadius = navigationBarCornerRadius,
                                    style = navigationBarStyle,
                                    frostedBlur = tempBlur,
                                )
                            },
                        )
                    }

                    item {
                        SliderPreferenceRow(
                            title = stringResource(R.string.frosted_glow),
                            description = stringResource(R.string.frosted_glow_desc),
                            iconRes = R.drawable.tune,
                            value = frostedGlow,
                            onValueChange = onFrostedGlowChange,
                            range = 0f..1f,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            default = FrostedGlowDefault,
                            preview = { tempGlow ->
                                NavBarPreview(
                                    widthFraction = navigationBarWidth,
                                    heightMultiplier = navigationBarHeight,
                                    opacity = navigationBarOpacity,
                                    transparency = navigationBarTransparency,
                                    labelSpacing = navigationBarLabelSpacing,
                                    cornerRadius = navigationBarCornerRadius,
                                    style = navigationBarStyle,
                                    frostedGlow = tempGlow,
                                )
                            },
                        )
                    }

                    item {
                        SliderPreferenceRow(
                            title = stringResource(R.string.frosted_bg_glow),
                            description = stringResource(R.string.frosted_bg_glow_desc),
                            iconRes = R.drawable.tune,
                            value = frostedBgGlow,
                            onValueChange = onFrostedBgGlowChange,
                            range = 0f..1f,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            default = FrostedBgGlowDefault,
                            preview = { tempBgGlow ->
                                NavBarPreview(
                                    widthFraction = navigationBarWidth,
                                    heightMultiplier = navigationBarHeight,
                                    opacity = navigationBarOpacity,
                                    transparency = navigationBarTransparency,
                                    labelSpacing = navigationBarLabelSpacing,
                                    cornerRadius = navigationBarCornerRadius,
                                    style = navigationBarStyle,
                                    frostedBgGlow = tempBgGlow,
                                )
                            },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.navigation_bar_dimensions)) {
                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_width),
                        description = stringResource(R.string.navigation_bar_width_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarWidth,
                        onValueChange = onNavigationBarWidthChange,
                        range = 0.5f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_WIDTH_DEFAULT,
                        preview = { tempWidth ->
                            NavBarPreview(
                                widthFraction = tempWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_height),
                        description = stringResource(R.string.navigation_bar_height_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarHeight,
                        onValueChange = onNavigationBarHeightChange,
                        range = 0.8f..1.4f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_HEIGHT_DEFAULT,
                        preview = { tempHeight ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = tempHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_opacity),
                        description = stringResource(R.string.navigation_bar_opacity_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarOpacity,
                        onValueChange = onNavigationBarOpacityChange,
                        range = 0.2f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_OPACITY_DEFAULT,
                        preview = { tempOpacity ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = tempOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_transparency),
                        description = stringResource(R.string.navigation_bar_transparency_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarTransparency,
                        onValueChange = onNavigationBarTransparencyChange,
                        range = 0.0f..0.95f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        default = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
                        preview = { tempTransparency ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = tempTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_label_spacing),
                        description = stringResource(R.string.navigation_bar_label_spacing_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarLabelSpacing,
                        onValueChange = onNavigationBarLabelSpacingChange,
                        range = 0f..16f,
                        valueLabel = { "${it.roundToInt()} dp" },
                        default = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
                        preview = { tempSpacing ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = tempSpacing,
                                cornerRadius = navigationBarCornerRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_corner_radius),
                        description = stringResource(R.string.navigation_bar_corner_radius_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarCornerRadius,
                        onValueChange = onNavigationBarCornerRadiusChange,
                        range = 0f..48f,
                        valueLabel = { "${it.roundToInt()} dp" },
                        default = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
                        preview = { tempRadius ->
                            NavBarPreview(
                                widthFraction = navigationBarWidth,
                                heightMultiplier = navigationBarHeight,
                                opacity = navigationBarOpacity,
                                transparency = navigationBarTransparency,
                                labelSpacing = navigationBarLabelSpacing,
                                cornerRadius = tempRadius,
                                style = navigationBarStyle,
                            )
                        },
                    )
                }

                item {
                    val allDefaults =
                        navigationBarWidth == NAVIGATION_BAR_WIDTH_DEFAULT &&
                            navigationBarHeight == NAVIGATION_BAR_HEIGHT_DEFAULT &&
                            navigationBarOpacity == NAVIGATION_BAR_OPACITY_DEFAULT &&
                            navigationBarTransparency == NAVIGATION_BAR_TRANSPARENCY_DEFAULT &&
                            navigationBarLabelSpacing == NAVIGATION_BAR_LABEL_SPACING_DEFAULT &&
                            navigationBarCornerRadius == NAVIGATION_BAR_CORNER_RADIUS_DEFAULT

                    OutlinedButton(
                        onClick = {
                            onNavigationBarWidthChange(NAVIGATION_BAR_WIDTH_DEFAULT)
                            onNavigationBarHeightChange(NAVIGATION_BAR_HEIGHT_DEFAULT)
                            onNavigationBarOpacityChange(NAVIGATION_BAR_OPACITY_DEFAULT)
                            onNavigationBarTransparencyChange(NAVIGATION_BAR_TRANSPARENCY_DEFAULT)
                            onNavigationBarLabelSpacingChange(NAVIGATION_BAR_LABEL_SPACING_DEFAULT)
                            onNavigationBarCornerRadiusChange(NAVIGATION_BAR_CORNER_RADIUS_DEFAULT)
                        },
                        enabled = !allDefaults,
                        shapes = ButtonDefaults.shapes(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.restore),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.navigation_bar_reset_dimensions))
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderPreferenceRow(
    title: String,
    description: String,
    iconRes: Int,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    default: Float? = null,
    preview: (@Composable (Float) -> Unit)? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        var tempValue by remember { mutableFloatStateOf(value) }

        DefaultDialog(
            onDismiss = {
                tempValue = value
                showDialog = false
            },
            buttons = {
                if (default != null) {
                    TextButton(
                        onClick = { tempValue = default },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        tempValue = value
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onValueChange(tempValue)
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (preview != null) {
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    preview(tempValue)
                    Spacer(modifier = Modifier.padding(top = 16.dp))
                }

                Text(
                    text = valueLabel(tempValue),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Slider(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    PreferenceEntry(
        title = { Text(title) },
        description = valueLabel(value),
        icon = { Icon(painterResource(iconRes), null) },
        onClick = { showDialog = true },
    )
}

@Composable
private fun NavBarPreview(
    widthFraction: Float,
    heightMultiplier: Float,
    opacity: Float,
    transparency: Float,
    labelSpacing: Float,
    cornerRadius: Float,
    style: NavigationBarStyle,
    frostedDim: Float? = null,
    frostedBlur: Float? = null,
    frostedGlow: Float? = null,
    frostedBgGlow: Float? = null,
) {
    val (storedDim) = rememberPreference(FrostedDimKey, defaultValue = FrostedDimDefault)
    val (storedBlur) = rememberPreference(FrostedBlurKey, defaultValue = FrostedBlurDefault)
    val (storedGlow) = rememberPreference(FrostedGlowKey, defaultValue = FrostedGlowDefault)
    val (storedBgGlow) = rememberPreference(FrostedBgGlowKey, defaultValue = FrostedBgGlowDefault)
    val dim = (frostedDim ?: storedDim).coerceIn(0f, 0.9f)
    val blur = (frostedBlur ?: storedBlur).coerceIn(0f, FrostedBlurMax)
    val glow = (frostedGlow ?: storedGlow).coerceIn(0f, 1f)
    val bgGlow = (frostedBgGlow ?: storedBgGlow).coerceIn(0f, 1f)
    val isFloating =
        style == NavigationBarStyle.FLOATING || style == NavigationBarStyle.FROSTED ||
            style == NavigationBarStyle.OUTLINED
    val isOutlined = style == NavigationBarStyle.OUTLINED
    val isFrosted = style == NavigationBarStyle.FROSTED
    val previewHazeState = rememberHazeState()
    val resolvedBarHeight = NavigationBarHeight * heightMultiplier
    val shape =
        if (isFrosted || isOutlined) {
            RoundedCornerShape(percent = 50)
        } else if (isFloating) {
            RoundedCornerShape(cornerRadius.dp)
        } else {
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = cornerRadius.dp,
                bottomEnd = cornerRadius.dp,
            )
        }
    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    val effectiveAlpha = opacity * (1f - transparency)
    val barColor = baseColor.copy(alpha = effectiveAlpha.coerceIn(0.05f, 1f))
    val indicatorColor =
        if (isFloating) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val fauxScreenBrush =
        Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceVariant,
                ),
        )

    val previewFrostedStyle =
        remember(isFrosted, baseColor, dim, blur, bgGlow) {
            frostedNavBarStyle(
                scrim = baseColor.copy(alpha = dim),
                fallbackScrim = baseColor.copy(alpha = FrostedFallbackAlpha),
                blurRadiusDp = blur,
                bgGlow = bgGlow,
            )
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(fauxScreenBrush)
                .then(
                    if (isFrosted) Modifier.hazeSource(previewHazeState) else Modifier,
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(
                        bottom = if (isFloating) 16.dp else 0.dp,
                        start = if (isFloating) 16.dp else 0.dp,
                        end = if (isFloating) 16.dp else 0.dp,
                    ).fillMaxWidth(if (isFloating) widthFraction.coerceIn(0.5f, 1f) else 1f)
                    .height(resolvedBarHeight),
            shape = shape,
            color = if (isFrosted || isOutlined) Color.Transparent else barColor,
            tonalElevation = if (isFrosted || isOutlined) 0.dp else NavigationBarDefaults.Elevation,
            shadowElevation = if (isFloating) 8.dp else NavigationBarDefaults.Elevation,
            border =
                if (isOutlined) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                } else if (isFrosted) {
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = glow),
                    )
                } else {
                    null
                },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isFrosted) {
                    // Same clipped frost layer as the real bar: the blur has no
                    // shape parameter, so without this its square corners would
                    // poke outside the pill outline in the preview too.
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clip(shape)
                                .hazeBlur(
                                    input = HazeInput.Sources(previewHazeState),
                                    style = previewFrostedStyle,
                                    performanceMode = HazePerformanceMode.Performance,
                                )
                                .background(
                                    baseColor.copy(alpha = dim),
                                ),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val items = Screens.MainScreens
                items.forEachIndexed { index, screen ->
                    val selected = index == 0
                    val selectedColor = MaterialTheme.colorScheme.primary
                    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(if (selected) indicatorColor else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (selected) screen.iconIdActive else screen.iconIdInactive,
                                    ),
                                contentDescription = null,
                                tint = if (selected) selectedColor else unselectedColor,
                            )
                        }
                        Spacer(Modifier.height(labelSpacing.dp))
                        Text(
                            text = stringResource(screen.titleId),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) selectedColor else unselectedColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                }
            }
        }
    }
}
