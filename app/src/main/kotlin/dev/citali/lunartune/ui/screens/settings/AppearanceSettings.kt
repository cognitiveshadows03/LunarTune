/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AppFontPreference
import dev.citali.lunartune.constants.LunarTuneCanvasKey
import dev.citali.lunartune.constants.BackdropBlurAmountKey
import dev.citali.lunartune.constants.BackdropEnabledKey
import dev.citali.lunartune.constants.BlurRadiusKey
import dev.citali.lunartune.constants.ChipSortTypeKey
import dev.citali.lunartune.constants.CropThumbnailToSquareKey
import dev.citali.lunartune.constants.CustomFontNameKey
import dev.citali.lunartune.constants.CustomFontUriKey
import dev.citali.lunartune.constants.DarkModeKey
import dev.citali.lunartune.constants.DefaultLibraryFilterOrder
import dev.citali.lunartune.constants.DefaultLibraryFilterOrderPreference
import dev.citali.lunartune.constants.DefaultOpenTabKey
import dev.citali.lunartune.constants.DisableAnimationsKey
import dev.citali.lunartune.constants.DisableBlurKey
import dev.citali.lunartune.constants.DynamicThemeKey
import dev.citali.lunartune.constants.FontPreferenceKey
import dev.citali.lunartune.constants.ForceHighRefreshRateKey
import dev.citali.lunartune.constants.GridItemSize
import dev.citali.lunartune.constants.GridItemsSizeKey
import dev.citali.lunartune.constants.HidePlayerThumbnailKey
import dev.citali.lunartune.constants.LibraryChipOrderKey
import dev.citali.lunartune.constants.LibraryFilter
import dev.citali.lunartune.constants.LyricsBackgroundStyle
import dev.citali.lunartune.constants.LyricsBackgroundStyleKey
import dev.citali.lunartune.constants.UseGpuBlurKey
import dev.citali.lunartune.constants.MiniPlayerBackgroundStyle
import dev.citali.lunartune.constants.MiniPlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerButtonsStyle
import dev.citali.lunartune.constants.PlayerButtonsStyleKey
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerDesignStyleKey
import dev.citali.lunartune.constants.PlaylistTagOrderKey
import dev.citali.lunartune.constants.PureBlackKey
import dev.citali.lunartune.constants.QuickPicksDisplayMode
import dev.citali.lunartune.constants.QuickPicksDisplayModeKey
import dev.citali.lunartune.constants.RandomThemeOnStartupKey
import dev.citali.lunartune.constants.ShowHomeCategoryChipsKey
import dev.citali.lunartune.constants.SharedScreenHeaderImageUriKey
import dev.citali.lunartune.constants.HomeHeaderImageUriKey
import dev.citali.lunartune.constants.SettingsHeaderImageUriKey
import dev.citali.lunartune.constants.ShowLibraryCardCachedKey
import dev.citali.lunartune.constants.ShowLibraryCardLikedKey
import dev.citali.lunartune.constants.ShowLibraryCardLocalKey
import dev.citali.lunartune.constants.ShowLibraryCardMyTopKey
import dev.citali.lunartune.constants.ShowLibraryCardOfflineKey
import dev.citali.lunartune.constants.ShowLibraryCardWatermarksKey
import dev.citali.lunartune.constants.ShowPlayerVolumeBarKey
import dev.citali.lunartune.constants.ShowTagsInLibraryKey
import dev.citali.lunartune.constants.SliderStyle
import dev.citali.lunartune.constants.SliderStyleKey
import dev.citali.lunartune.constants.SwipeSensitivityKey
import dev.citali.lunartune.constants.SwipeThumbnailKey
import dev.citali.lunartune.constants.SwipeToSongKey
import dev.citali.lunartune.constants.ThumbnailCornerRadiusKey
import dev.citali.lunartune.constants.toLibraryFilterOrder
import dev.citali.lunartune.constants.toLibraryFilterPreference
import dev.citali.lunartune.constants.toPlaylistTagOrder
import dev.citali.lunartune.constants.toPlaylistTagPreference
import dev.citali.lunartune.ui.component.DefaultDialog
import dev.citali.lunartune.ui.component.EnumListPreference
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.LibraryChipOrderDialog
import dev.citali.lunartune.ui.component.ListPreference
import dev.citali.lunartune.ui.component.PlaylistTagOrderDialog
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.component.TagsManagementDialog
import dev.citali.lunartune.ui.component.ThumbnailCornerRadiusSelectorButton
import dev.citali.lunartune.ui.player.StyledPlaybackSlider
import dev.citali.lunartune.ui.theme.CustomFontLoader
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.ui.utils.rememberBannerImageCropper
import dev.citali.lunartune.utils.isLowRamDevice
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.PlaylistTagUiModel
import dev.citali.lunartune.viewmodels.PlaylistTagsScreenState
import dev.citali.lunartune.viewmodels.PlaylistTagsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController) {
    val playlistTagsViewModel: PlaylistTagsViewModel = hiltViewModel()
    val playlistTagsState by playlistTagsViewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val (sharedHeaderImageUri, onSharedHeaderImageUriChange) = rememberPreference(SharedScreenHeaderImageUriKey, "")
    val (homeHeaderImageUri, onHomeHeaderImageUriChange) = rememberPreference(HomeHeaderImageUriKey, "")
    val (settingsHeaderImageUri, onSettingsHeaderImageUriChange) = rememberPreference(SettingsHeaderImageUriKey, "")
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (dynamicTheme, onDynamicThemeChange) =
        rememberPreference(
            DynamicThemeKey,
            defaultValue = true,
        )
    val (randomThemeOnStartup, onRandomThemeOnStartupChange) =
        rememberPreference(
            RandomThemeOnStartupKey,
            defaultValue = false,
        )
    val (darkMode, onDarkModeChange) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )
    val (playerDesignStyle, onPlayerDesignStyleChange) =
        rememberEnumPreference(
            PlayerDesignStyleKey,
            defaultValue = PlayerDesignStyle.V4,
        )
    val (showPlayerVolumeBar, onShowPlayerVolumeBarChange) =
        rememberPreference(
            ShowPlayerVolumeBarKey,
            defaultValue = true,
        )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) =
        rememberPreference(
            HidePlayerThumbnailKey,
            defaultValue = false,
        )
    val (archiveTuneCanvasEnabled, onLunarTuneCanvasEnabledChange) =
        rememberPreference(
            LunarTuneCanvasKey,
            defaultValue = false,
        )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) =
        rememberPreference(
            key = ThumbnailCornerRadiusKey,
            defaultValue = 16f, // default dp
        )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) =
        rememberPreference(
            CropThumbnailToSquareKey,
            defaultValue = false,
        )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (configuredLyricsBackground, onLyricsBackgroundChange) =
        rememberEnumPreference(
            LyricsBackgroundStyleKey,
            defaultValue = LyricsBackgroundStyle.DEFAULT,
        )
    val (miniPlayerBackground, onMiniPlayerBackgroundChange) =
        rememberEnumPreference(
            MiniPlayerBackgroundStyleKey,
            defaultValue = MiniPlayerBackgroundStyle.THEME,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(
            DisableAnimationsKey,
            defaultValue = defaultDisableAnimations,
        )
    val (forceHighRefreshRate, onForceHighRefreshRateChange) =
        rememberPreference(
            ForceHighRefreshRateKey,
            defaultValue = true,
        )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 48f)
    val (useGpuBlur, onUseGpuBlurChange) = rememberPreference(UseGpuBlurKey, defaultValue = true)
    val (backdropEnabled, onBackdropEnabledChange) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount, onBackdropBlurAmountChange) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) =
        rememberEnumPreference(
            PlayerButtonsStyleKey,
            defaultValue = PlayerButtonsStyle.DEFAULT,
        )
    val (sliderStyle, onSliderStyleChange) =
        rememberEnumPreference(
            SliderStyleKey,
            defaultValue = SliderStyle.Standard,
        )
    val (swipeThumbnail, onSwipeThumbnailChange) =
        rememberPreference(
            SwipeThumbnailKey,
            defaultValue = true,
        )
    val (swipeSensitivity, onSwipeSensitivityChange) =
        rememberPreference(
            SwipeSensitivityKey,
            defaultValue = 0.73f,
        )
    val (gridItemSize, onGridItemSizeChange) =
        rememberEnumPreference(
            GridItemsSizeKey,
            defaultValue = GridItemSize.SMALL,
        )

    val (swipeToSong, onSwipeToSongChange) =
        rememberPreference(
            SwipeToSongKey,
            defaultValue = false,
        )

    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(
            ShowTagsInLibraryKey,
            defaultValue = true,
        )
    val (showLibraryCardLiked, onShowLibraryCardLikedChange) =
        rememberPreference(ShowLibraryCardLikedKey, defaultValue = true)
    val (showLibraryCardOffline, onShowLibraryCardOfflineChange) =
        rememberPreference(ShowLibraryCardOfflineKey, defaultValue = true)
    val (showLibraryCardCached, onShowLibraryCardCachedChange) =
        rememberPreference(ShowLibraryCardCachedKey, defaultValue = true)
    val (showLibraryCardLocal, onShowLibraryCardLocalChange) =
        rememberPreference(ShowLibraryCardLocalKey, defaultValue = true)
    val (showLibraryCardMyTop, onShowLibraryCardMyTopChange) =
        rememberPreference(ShowLibraryCardMyTopKey, defaultValue = true)
    val (showLibraryCardWatermarks, onShowLibraryCardWatermarksChange) =
        rememberPreference(ShowLibraryCardWatermarksKey, defaultValue = true)
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) =
        rememberPreference(
            ShowHomeCategoryChipsKey,
            defaultValue = true,
        )
    val (libraryChipOrderPreference, onLibraryChipOrderChange) =
        rememberPreference(
            LibraryChipOrderKey,
            defaultValue = DefaultLibraryFilterOrderPreference,
        )
    val libraryChipOrder =
        remember(libraryChipOrderPreference) {
            libraryChipOrderPreference.toLibraryFilterOrder()
        }
    val (playlistTagOrderPreference, onPlaylistTagOrderChange) =
        rememberPreference(
            PlaylistTagOrderKey,
            defaultValue = "",
        )
    val availablePlaylistTags =
        (playlistTagsState as? PlaylistTagsScreenState.Success)?.tags.orEmpty()
    val playlistTagOrder =
        remember(availablePlaylistTags, playlistTagOrderPreference) {
            val tagsById = availablePlaylistTags.associateBy(PlaylistTagUiModel::id)
            playlistTagOrderPreference
                .toPlaylistTagOrder(availablePlaylistTags.map(PlaylistTagUiModel::id))
                .mapNotNull { tagId -> tagsById[tagId] }
        }
    val (quickPicksDisplayMode, onQuickPicksDisplayModeChange) =
        rememberEnumPreference(
            QuickPicksDisplayModeKey,
            defaultValue = QuickPicksDisplayMode.CARD,
        )

    val pickSharedHeader = rememberBannerImageCropper("shared_header") { onSharedHeaderImageUriChange(it.toString()) }
    val pickHomeHeader = rememberBannerImageCropper("home_header") { onHomeHeaderImageUriChange(it.toString()) }
    val pickSettingsHeader = rememberBannerImageCropper("settings_header") { onSettingsHeaderImageUriChange(it.toString()) }

    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    val availableBackgroundStyles =
        PlayerBackgroundStyle.entries.filter {
            it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
    val availableLyricsBackgroundStyles =
        remember {
            listOf(
                LyricsBackgroundStyle.DEFAULT,
                LyricsBackgroundStyle.FOLLOW_THEME,
                LyricsBackgroundStyle.COLORING,
                LyricsBackgroundStyle.MOVING_BLUR,
            )
        }
    val lyricsBackground = configuredLyricsBackground.resolveFor(playerBackground)
    val isPlayerBackgroundCustomizationEnabled =
        when (playerDesignStyle) {
            PlayerDesignStyle.V7,
            PlayerDesignStyle.V7_LEGACY,
            PlayerDesignStyle.V8,
            PlayerDesignStyle.V10,
            PlayerDesignStyle.V9,
            -> false

            else -> true
        }
    // ArchiveTune 13.4.0 Immersive kept button colors + slider style available.
    val isPlayerControlsCustomizationEnabled =
        playerDesignStyle == PlayerDesignStyle.V7_LEGACY || isPlayerBackgroundCustomizationEnabled
    val isVolumeBarSupported =
        playerDesignStyle == PlayerDesignStyle.V7 ||
            (playerDesignStyle == PlayerDesignStyle.V8 || playerDesignStyle == PlayerDesignStyle.V10)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )
    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > HIGH_REFRESH_RATE_THRESHOLD_FPS

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showLibraryChipOrderDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showPlaylistTagOrderDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showTagsManagementDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(isPlayerBackgroundCustomizationEnabled, playerBackground) {
        if (!isPlayerBackgroundCustomizationEnabled && playerBackground != PlayerBackgroundStyle.DEFAULT) {
            onPlayerBackgroundChange(PlayerBackgroundStyle.DEFAULT)
        }
    }

    LaunchedEffect(isPlayerControlsCustomizationEnabled) {
        if (!isPlayerControlsCustomizationEnabled) {
            showSliderOptionDialog = false
        }
    }

    if (showSliderOptionDialog && isPlayerControlsCustomizationEnabled) {
        val sliderStyles =
            remember {
                listOf(
                    SliderStyle.Standard,
                    SliderStyle.Wavy,
                    SliderStyle.Thick,
                    SliderStyle.Circular,
                    SliderStyle.Simple,
                )
            }
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sliderStyles.chunked(3).forEach { styleRow ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        styleRow.forEach { style ->
                            SliderStyleOptionCard(
                                sliderStyle = style,
                                selected = sliderStyle == style,
                                onClick = {
                                    onSliderStyleChange(style)
                                    showSliderOptionDialog = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - styleRow.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showLibraryChipOrderDialog) {
        LibraryChipOrderDialog(
            initialOrder = libraryChipOrder,
            onDismiss = { showLibraryChipOrderDialog = false },
            onConfirm = { newOrder ->
                onLibraryChipOrderChange(newOrder.toLibraryFilterPreference())
                showLibraryChipOrderDialog = false
            },
        )
    }

    if (showPlaylistTagOrderDialog) {
        PlaylistTagOrderDialog(
            state = playlistTagsState,
            initialOrder = playlistTagOrder,
            onDismiss = { showPlaylistTagOrderDialog = false },
            onConfirm = { newOrder ->
                onPlaylistTagOrderChange(
                    newOrder.map(PlaylistTagUiModel::id).toPlaylistTagPreference(),
                )
                showPlaylistTagOrderDialog = false
            },
        )
    }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            onDismiss = { showTagsManagementDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
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
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.theme)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        checked = dynamicTheme,
                        onCheckedChange = onDynamicThemeChange,
                    )
                }

                item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.random_theme_on_startup)) },
                        description = stringResource(R.string.random_theme_on_startup_desc),
                        icon = { Icon(painterResource(R.drawable.shuffle), null) },
                        checked = randomThemeOnStartup,
                        onCheckedChange = onRandomThemeOnStartupChange,
                    )
                }

                item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.color_palette)) },
                        description = stringResource(R.string.customize_theme_colors),
                        icon = { Icon(painterResource(R.drawable.format_paint), null) },
                        onClick = { navController.navigate("settings/appearance/palette_picker") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_icon)) },
                        description = stringResource(R.string.app_icon_description),
                        icon = { Icon(painterResource(R.drawable.app_icon_small), null) },
                        onClick = { navController.navigate("settings/appearance/icon") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.dark_theme)) },
                        icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                        selectedValue = darkMode,
                        onValueSelected = onDarkModeChange,
                        valueText = {
                            when (it) {
                                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                            }
                        },
                    )
                }

                item(visible = useDarkTheme) {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pure_black)) },
                        icon = { Icon(painterResource(R.drawable.contrast), null) },
                        checked = pureBlack,
                        onCheckedChange = onPureBlackChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_blur)) },
                        description = stringResource(R.string.disable_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_off), null) },
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_animations)) },
                        description = stringResource(R.string.disable_animations_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = disableAnimations,
                        onCheckedChange = onDisableAnimationsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.force_high_refresh_rate)) },
                        description =
                            stringResource(
                                R.string.max_supported_refresh_rate,
                                supportedHighestFps.roundToInt(),
                            ),
                        icon = { Icon(painterResource(R.drawable.speed), null) },
                        checked = forceHighRefreshRate,
                        onCheckedChange = onForceHighRefreshRateChange,
                        isEnabled = isHighRefreshRateSupported,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.use_gpu_blur)) },
                        description = stringResource(R.string.use_gpu_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = useGpuBlur,
                        onCheckedChange = onUseGpuBlurChange,
                        isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.blur_intensity)) },
                        description = stringResource(R.string.blur_intensity_value, blurRadius.roundToInt()),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = !disableBlur,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = blurRadius,
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..64f,
                                steps = 63,
                                enabled = !disableBlur,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.album_backdrop)) },
                        description = stringResource(R.string.album_backdrop_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = backdropEnabled,
                        onCheckedChange = onBackdropEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.backdrop_blur_amount)) },
                        description = stringResource(R.string.backdrop_blur_amount_value, backdropBlurAmount),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = backdropEnabled,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = backdropBlurAmount.toFloat(),
                                onValueChange = { onBackdropBlurAmountChange(it.roundToInt()) },
                                valueRange = 0f..100f,
                                steps = 19,
                                enabled = backdropEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.font_preference)) },
                        description = stringResource(R.string.font_preference_desc),
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        selectedValue = fontPreference,
                        onValueSelected = onFontPreferenceSelected,
                        valueText = {
                            when (it) {
                                AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                                AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                                AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                            }
                        },
                    )
                }

                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    val customFontDescription =
                        if (customFontName.isNotBlank()) {
                            customFontName
                        } else if (customFontUri.isBlank()) {
                            stringResource(R.string.custom_font_desc)
                        } else {
                            customFontUri
                        }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontDescription,
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        onClick = pickCustomFont,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.screen_header_images)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.shared_header_image)) },
                        description = stringResource(if (sharedHeaderImageUri.isBlank()) R.string.header_image_not_selected else R.string.header_image_selected_cropped),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        onClick = pickSharedHeader,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.home_header_image_override)) },
                        description = stringResource(if (homeHeaderImageUri.isBlank()) R.string.header_override_shared else R.string.header_override_custom),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        onClick = pickHomeHeader,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.settings_header_image_override)) },
                        description = stringResource(if (settingsHeaderImageUri.isBlank()) R.string.header_override_shared else R.string.header_override_custom),
                        icon = { Icon(painterResource(R.drawable.settings), null) },
                        onClick = pickSettingsHeader,
                    )
                }
                if (homeHeaderImageUri.isNotBlank() || settingsHeaderImageUri.isNotBlank()) item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.reset_header_overrides)) },
                        description = stringResource(R.string.reset_header_overrides_desc),
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        onClick = {
                            onHomeHeaderImageUriChange("")
                            onSettingsHeaderImageUriChange("")
                        },
                    )
                }
                if (sharedHeaderImageUri.isNotBlank()) item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.remove_all_header_images)) },
                        icon = { Icon(painterResource(R.drawable.delete), null) },
                        onClick = {
                            onSharedHeaderImageUriChange("")
                            onHomeHeaderImageUriChange("")
                            onSettingsHeaderImageUriChange("")
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.player)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_design_style)) },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = playerDesignStyle,
                        onValueSelected = onPlayerDesignStyleChange,
                        valueText = {
                            when (it) {
                                PlayerDesignStyle.V1 -> stringResource(R.string.player_design_v1)
                                PlayerDesignStyle.V2 -> stringResource(R.string.player_design_v2)
                                PlayerDesignStyle.V3 -> stringResource(R.string.player_design_v3)
                                PlayerDesignStyle.V4 -> stringResource(R.string.player_design_v4)
                                PlayerDesignStyle.V5 -> stringResource(R.string.player_design_v5)
                                PlayerDesignStyle.V6 -> stringResource(R.string.player_design_v6)
                                PlayerDesignStyle.V7 -> stringResource(R.string.player_design_v7)
                                PlayerDesignStyle.V7_LEGACY -> stringResource(R.string.player_design_v7_legacy)
                                PlayerDesignStyle.V8 -> stringResource(R.string.player_design_v8)
                                PlayerDesignStyle.V9 -> stringResource(R.string.player_design_v9)
                                PlayerDesignStyle.V10 -> stringResource(R.string.player_design_v10)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_player_volume_bar)) },
                        description =
                            if (isVolumeBarSupported) {
                                null
                            } else {
                                stringResource(R.string.player_volume_bar_v7_v8_only)
                            },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = showPlayerVolumeBar,
                        onCheckedChange = onShowPlayerVolumeBarChange,
                        isEnabled = isVolumeBarSupported,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_background_style)) },
                        description =
                            if (isPlayerBackgroundCustomizationEnabled) {
                                null
                            } else {
                                stringResource(R.string.player_background_style_v8_v9_desc)
                            },
                        icon = { Icon(painterResource(R.drawable.gradient), null) },
                        selectedValue = playerBackground,
                        onValueSelected = { selectedBackground ->
                            onPlayerBackgroundChange(selectedBackground)
                            when {
                                selectedBackground == PlayerBackgroundStyle.CUSTOM -> {
                                    onLyricsBackgroundChange(LyricsBackgroundStyle.CUSTOM)
                                }

                                configuredLyricsBackground == LyricsBackgroundStyle.CUSTOM -> {
                                    onLyricsBackgroundChange(LyricsBackgroundStyle.DEFAULT)
                                }
                            }
                        },
                        isEnabled = isPlayerBackgroundCustomizationEnabled,
                        valueText = {
                            when (it) {
                                PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                                PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                PlayerBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
                                PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                PlayerBackgroundStyle.COLORING -> stringResource(R.string.coloring)
                                PlayerBackgroundStyle.BLUR_GRADIENT -> stringResource(R.string.blur_gradient)
                                PlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                                PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow Animated"
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.lyrics_background_style)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        selectedValue = lyricsBackground,
                        values = availableLyricsBackgroundStyles,
                        onValueSelected = onLyricsBackgroundChange,
                        isEnabled = playerBackground != PlayerBackgroundStyle.CUSTOM,
                        valueText = {
                            when (it) {
                                LyricsBackgroundStyle.DEFAULT -> stringResource(R.string.lyrics_background_default)
                                LyricsBackgroundStyle.FOLLOW_THEME -> stringResource(R.string.follow_theme)
                                LyricsBackgroundStyle.COLORING -> stringResource(R.string.coloring)
                                LyricsBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
                                LyricsBackgroundStyle.MOVING_BLUR -> stringResource(R.string.lyrics_background_moving_blur)
                            }
                        },
                    )
                }

                item(visible = playerBackground == PlayerBackgroundStyle.CUSTOM) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.customized_background)) },
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        onClick = { navController.navigate("customize_background") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.mini_player_background_style)) },
                        icon = { Icon(painterResource(R.drawable.gradient), null) },
                        selectedValue = miniPlayerBackground,
                        onValueSelected = onMiniPlayerBackgroundChange,
                        valueText = {
                            when (it) {
                                MiniPlayerBackgroundStyle.THEME -> stringResource(R.string.follow_theme)
                                MiniPlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                MiniPlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                                MiniPlayerBackgroundStyle.FROSTED -> stringResource(R.string.mini_player_frosted_glow)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_player_thumbnail)) },
                        description = stringResource(R.string.hide_player_thumbnail_desc),
                        icon = { Icon(painterResource(R.drawable.hide_image), null) },
                        checked = hidePlayerThumbnail,
                        onCheckedChange = onHidePlayerThumbnailChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lunartune_canvas)) },
                        description = stringResource(R.string.lunartune_canvas_desc),
                        icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                        checked = archiveTuneCanvasEnabled,
                        onCheckedChange = onLunarTuneCanvasEnabledChange,
                    )
                }

                item {
                    ThumbnailCornerRadiusSelectorButton(
                        onRadiusSelected = {},
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                        description = stringResource(R.string.crop_thumbnail_to_square_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = cropThumbnailToSquare,
                        onCheckedChange = onCropThumbnailToSquareChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aod_customize_title)) },
                        description = stringResource(R.string.aod_customize_entry_desc),
                        icon = { Icon(painterResource(R.drawable.bedtime), null) },
                        onClick = { navController.navigate("settings/appearance/aod_customized") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_buttons_style)) },
                        description =
                            if (isPlayerControlsCustomizationEnabled) {
                                null
                            } else {
                                stringResource(R.string.player_background_style_v8_v9_desc)
                            },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = playerButtonsStyle,
                        onValueSelected = onPlayerButtonsStyleChange,
                        isEnabled = isPlayerControlsCustomizationEnabled,
                        valueText = {
                            when (it) {
                                PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                                PlayerButtonsStyle.SECONDARY -> stringResource(R.string.secondary_color_style)
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.player_slider_style)) },
                        description = sliderStyleLabel(sliderStyle),
                        icon = { Icon(painterResource(R.drawable.sliders), null) },
                        onClick = {
                            showSliderOptionDialog = true
                        },
                        isEnabled = isPlayerControlsCustomizationEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeThumbnail,
                        onCheckedChange = onSwipeThumbnailChange,
                    )
                }

                item(visible = swipeThumbnail) {
                    var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }

                    if (showSensitivityDialog) {
                        var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }

                        DefaultDialog(
                            onDismiss = {
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false
                            },
                            buttons = {
                                TextButton(
                                    onClick = {
                                        tempSensitivity = 0.73f
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.reset))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(
                                    onClick = {
                                        tempSensitivity = swipeSensitivity
                                        showSensitivityDialog = false
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        onSwipeSensitivityChange(tempSensitivity)
                                        showSensitivityDialog = false
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
                                    text = stringResource(R.string.swipe_sensitivity),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Text(
                                    text = stringResource(R.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Slider(
                                    value = tempSensitivity,
                                    onValueChange = { tempSensitivity = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.swipe_sensitivity)) },
                        description = stringResource(R.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { showSensitivityDialog = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.library_shortcut_cards)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liked_songs)) },
                        description = stringResource(R.string.library_shortcut_cards_desc),
                        icon = { Icon(painterResource(R.drawable.favorite), null) },
                        checked = showLibraryCardLiked,
                        onCheckedChange = onShowLibraryCardLikedChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.offline_shortcut)) },
                        icon = { Icon(painterResource(R.drawable.offline), null) },
                        checked = showLibraryCardOffline,
                        onCheckedChange = onShowLibraryCardOfflineChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.cached)) },
                        icon = { Icon(painterResource(R.drawable.cached), null) },
                        checked = showLibraryCardCached,
                        onCheckedChange = onShowLibraryCardCachedChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.local_files)) },
                        icon = { Icon(painterResource(R.drawable.snippet_folder), null) },
                        checked = showLibraryCardLocal,
                        onCheckedChange = onShowLibraryCardLocalChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.my_top)) },
                        icon = { Icon(painterResource(R.drawable.trending_up), null) },
                        checked = showLibraryCardMyTop,
                        onCheckedChange = onShowLibraryCardMyTopChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.library_card_watermarks)) },
                        description = stringResource(R.string.library_card_watermarks_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = showLibraryCardWatermarks,
                        onCheckedChange = onShowLibraryCardWatermarksChange,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.misc)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.quick_picks_display_mode)) },
                        icon = { Icon(painterResource(R.drawable.grid_view), null) },
                        selectedValue = quickPicksDisplayMode,
                        onValueSelected = onQuickPicksDisplayModeChange,
                        valueText = {
                            when (it) {
                                QuickPicksDisplayMode.CARD -> stringResource(R.string.quick_picks_display_mode_card)
                                QuickPicksDisplayMode.LIST -> stringResource(R.string.quick_picks_display_mode_list)
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.navigation_bar_settings_title)) },
                        description = stringResource(R.string.navigation_bar_settings_subtitle),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        onClick = { navController.navigate("settings/appearance/navigation_bar") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.default_open_tab)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = defaultOpenTab,
                        onValueSelected = onDefaultOpenTabChange,
                        valueText = {
                            when (it) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.default_lib_chips)) },
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        selectedValue = defaultChip,
                        values = DefaultLibraryFilterOrder,
                        valueText = {
                            when (it) {
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                        onValueSelected = onDefaultChipChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.arrange_library_chips)) },
                        description = stringResource(R.string.arrange_library_chips_desc),
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        onClick = { showLibraryChipOrderDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.arrange_playlist_tags)) },
                        description = stringResource(R.string.arrange_playlist_tags_desc),
                        icon = { Icon(painterResource(R.drawable.style), null) },
                        onClick = { showPlaylistTagOrderDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.manage_playlist_tags)) },
                        description = stringResource(R.string.manage_playlist_tags_desc),
                        icon = { Icon(painterResource(R.drawable.style), null) },
                        onClick = { showTagsManagementDialog = true },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_home_category_chips)) },
                        description = stringResource(R.string.show_home_category_chips_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        checked = showHomeCategoryChips,
                        onCheckedChange = onShowHomeCategoryChipsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_tags_in_library)) },
                        description = stringResource(R.string.show_tags_in_library_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = showTagsInLibrary,
                        onCheckedChange = onShowTagsInLibraryChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.swipe_song_to_add)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeToSong,
                        onCheckedChange = onSwipeToSongChange,
                    )
                }

            }

            PreferenceGroup(title = stringResource(R.string.experimental_features)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.motion_settings_title)) },
                        description = stringResource(R.string.motion_settings_subtitle),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        onClick = { navController.navigate("settings/appearance/motion") },
                    )
                }
            }
        }
    }
}

@Composable
fun ApplyRefreshRate(
    isEnabled: Boolean,
    targetFps: Float,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val requestedFps = if (isEnabled) targetFps else DEFAULT_REFRESH_RATE_REQUEST

    DisposableEffect(view, activity, requestedFps) {
        applyRefreshRate(
            view = view,
            activity = activity,
            requestedFps = requestedFps,
        )

        onDispose {
            applyRefreshRate(
                view = view,
                activity = activity,
                requestedFps = DEFAULT_REFRESH_RATE_REQUEST,
            )
        }
    }
}

/**
 * Runs the UI at the display's fastest refresh rate for as long as something is actually moving.
 *
 * Android 15 and above can take a frame-rate request per view and act on it without switching the
 * display's mode, so the request can come and go as the UI starts and stops. Below that the only
 * lever is the window's display mode, and changing *that* mid-animation is itself a hitch — so the
 * old behaviour is kept there: pin it for as long as the setting is on.
 */
@Composable
internal fun ApplyForcedRefreshRate(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        BoostRefreshRateWhileAnimating(enabled = enabled)
    } else {
        val supportedHighestFps = rememberSupportedHighestFps()
        ApplyRefreshRate(
            isEnabled = enabled && supportedHighestFps > HIGH_REFRESH_RATE_THRESHOLD_FPS,
            targetFps = supportedHighestFps,
        )
    }
}

/**
 * Holds a high frame-rate request for as long as the view keeps drawing, and drops it once it has
 * been still for [BOOST_RELEASE_DELAY_MS].
 *
 * A draw pass is the one signal that covers *every* animation whatever drives it — a spring, a
 * transition, a fling, the moving blur's drift — without this code having to know about any of
 * them. It also costs nothing while nothing is happening: no draw, no callback, no request.
 */
@Composable
private fun BoostRefreshRateWhileAnimating(enabled: Boolean) {
    val view = LocalView.current
    val targetFps = rememberSupportedHighestFps()

    // Nothing to gain on a display that cannot go past the standard rate.
    if (!enabled || targetFps <= HIGH_REFRESH_RATE_THRESHOLD_FPS) return

    DisposableEffect(view, targetFps) {
        val boost = AnimationFrameRateBoost(view, targetFps)
        boost.start()
        onDispose { boost.stop() }
    }
}

/**
 * How long the view has to sit still before the request is dropped. Long enough that the gap
 * between two animations does not drop the display back down and then have to climb again.
 */
private const val BOOST_RELEASE_DELAY_MS = 900L

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class AnimationFrameRateBoost(
    private val view: View,
    private val fps: Float,
) : ViewTreeObserver.OnPreDrawListener {
    private val handler = Handler(Looper.getMainLooper())
    private var requested = false

    private val release =
        Runnable {
            if (!requested) return@Runnable
            requested = false
            view.setRequestedFrameRate(DEFAULT_REFRESH_RATE_REQUEST)
        }

    fun start() {
        if (view.isAttachedToWindow) {
            view.viewTreeObserver.addOnPreDrawListener(this)
        }
    }

    fun stop() {
        if (view.isAttachedToWindow) {
            view.viewTreeObserver.removeOnPreDrawListener(this)
        }
        handler.removeCallbacks(release)
        release.run()
    }

    override fun onPreDraw(): Boolean {
        if (!requested) {
            requested = true
            view.setRequestedFrameRate(fps)
        }
        handler.removeCallbacks(release)
        handler.postDelayed(release, BOOST_RELEASE_DELAY_MS)
        return true
    }
}

@Composable
internal fun rememberSupportedHighestFps(): Float {
    val view = LocalView.current

    return remember(view) {
        val display = view.display
        display
            ?.supportedModes
            ?.maxOfOrNull { mode -> mode.refreshRate }
            ?: display?.refreshRate
            ?: DEFAULT_STANDARD_REFRESH_RATE_FPS
    }
}

private fun applyRefreshRate(
    view: View,
    activity: Activity?,
    requestedFps: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        view.setRequestedFrameRate(requestedFps)
    }

    activity?.window?.let { window ->
        val attributes = window.attributes
        val targetModeId =
            if (requestedFps <= 0f) {
                0
            } else {
                view.display
                    ?.supportedModes
                    ?.filter { it.refreshRate >= requestedFps - 0.01f }
                    ?.minByOrNull { it.refreshRate }
                    ?.modeId
                    ?: view.display?.supportedModes?.maxByOrNull { it.refreshRate }?.modeId
                    ?: 0
            }
        if (attributes.preferredDisplayModeId != targetModeId) {
            attributes.preferredDisplayModeId = targetModeId
            window.attributes = attributes
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
private const val DEFAULT_STANDARD_REFRESH_RATE_FPS = 60f
private const val DEFAULT_REFRESH_RATE_REQUEST = 0f

@Composable
private fun SliderStyleOptionCard(
    sliderStyle: SliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember {
        mutableFloatStateOf(0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(16.dp),
    ) {
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {},
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )

        Text(
            text = sliderStyleLabel(sliderStyle),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun sliderStyleLabel(sliderStyle: SliderStyle): String =
    when (sliderStyle) {
        SliderStyle.Standard -> stringResource(R.string.slider_style_standard)
        SliderStyle.Wavy -> stringResource(R.string.slider_style_wavy)
        SliderStyle.Thick -> stringResource(R.string.slider_style_thick)
        SliderStyle.Circular -> stringResource(R.string.slider_style_circular)
        SliderStyle.Simple -> stringResource(R.string.slider_style_simple)
    }

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
