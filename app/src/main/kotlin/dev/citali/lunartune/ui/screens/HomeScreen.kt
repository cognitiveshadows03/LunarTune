/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.QuickPicks
import dev.citali.lunartune.constants.HomeHeaderImageUriKey
import dev.citali.lunartune.constants.SharedScreenHeaderImageUriKey

import dev.citali.lunartune.home.HomeAction
import dev.citali.lunartune.home.HomeScreenState
import dev.citali.lunartune.home.HomeUiState
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.playback.PlayerConnection
import dev.citali.lunartune.ui.component.ExpressivePullToRefreshBox
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.MenuState
import dev.citali.lunartune.ui.component.ScreenImageHeader
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.ui.utils.SnapLayoutInfoProvider
import dev.citali.lunartune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.innertube.pages.HomePage

private val HomeFeedMaxWidth = 1_200.dp
private val HomeSectionSpacing = 18.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val forgottenFavoritesGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val successState = screenState as? HomeScreenState.Success
    val uiState = successState?.uiState
    val selectedChip = uiState?.selectedChip

    LaunchedEffect(uiState?.homePage?.continuation) {
        val continuation = uiState?.homePage?.continuation ?: return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.onAction(HomeAction.LoadMore(continuation))
            }
        }
    }

    LaunchedEffect(uiState?.forgottenFavorites) {
        if (uiState != null) {
            forgottenFavoritesGridState.scrollToItem(0)
        }
    }

    // A category is its own feed, so start it from the top instead of leaving the
    // scroll position of the personalised home.
    LaunchedEffect(selectedChip) {
        if (selectedChip != null) {
            lazyListState.scrollToItem(0)
        }
    }

    if (selectedChip != null) {
        BackHandler {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    LaunchedEffect(uiState?.showCategoryChips, selectedChip) {
        if (uiState?.showCategoryChips == false && selectedChip != null) {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    // Attach the shell's floating-header connection inside this screen (Step 2b) so
    // Home's scroll/fling writes Home's own header state and can't leak into another
    // route's header. Bubbling reaches this ancestor Box before any shell connection.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        when (val state = screenState) {
            HomeScreenState.Loading -> {
                HomeStatePane(
                    iconResId = null,
                    messageResId = null,
                    showLoadingIndicator = true,
                )
            }

            HomeScreenState.Empty -> {
                HomeStatePane(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.no_results_found,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Error -> {
                HomeStatePane(
                    iconResId = R.drawable.info,
                    messageResId = state.messageResId,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Success -> {
                HomeContent(
                    uiState = state.uiState,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    lazyListState = lazyListState,
                    forgottenFavoritesGridState = forgottenFavoritesGridState,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatePane(
    @DrawableRes iconResId: Int?,
    @StringRes messageResId: Int?,
    modifier: Modifier = Modifier,
    @StringRes actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                LoadingIndicator()
            } else {
                iconResId?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                messageResId?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    FilledTonalButton(onClick = onAction) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    forgottenFavoritesGridState: LazyGridState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val (sharedHeaderImageUri) = rememberPreference(SharedScreenHeaderImageUriKey, "")
    val (homeHeaderImageUri) = rememberPreference(HomeHeaderImageUriKey, "")
    val effectiveHeaderImageUri = homeHeaderImageUri.ifBlank { sharedHeaderImageUri }
    val remoteQuickPicks =
        uiState
            .takeIf { it.quickPicksMode == QuickPicks.QUICK_PICKS }
            ?.remoteQuickPicks
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.showTonalBackdrop) {
            Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .align(Alignment.TopCenter)
                    .drawWithCache {
                        val brush =
                            Brush.verticalGradient(
                                0f to tonalStart.copy(alpha = 0.30f),
                                0.42f to tonalMiddle.copy(alpha = 0.14f),
                                1f to Color.Transparent,
                            )
                        onDrawBehind { drawRect(brush) }
                    },
            )
        }

        val headerOffset =
            if (lazyListState.firstVisibleItemIndex == 0) lazyListState.firstVisibleItemScrollOffset else 1000
        // The header's local text dim rides the same fade as the artwork: once
        // the image has scrolled away there is nothing left to dim against.
        val headerDimAlpha = (1f - headerOffset / 300f).coerceIn(0f, 1f)
        if (effectiveHeaderImageUri.isNotBlank()) {
            ScreenImageHeader(
                imageUri = effectiveHeaderImageUri,
                blendColor = MaterialTheme.colorScheme.surface,
                veil = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = -headerOffset.toFloat()
                        alpha = (1f - headerOffset / 300f).coerceIn(0f, 1f)
                    },
            )
        }

        ExpressivePullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val forgottenItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val forgottenItemWidth = maxWidth.coerceAtMost(HomeFeedMaxWidth) * forgottenItemWidthFactor
                val forgottenSnapLayoutInfoProvider =
                    remember(forgottenFavoritesGridState, forgottenItemWidthFactor) {
                        SnapLayoutInfoProvider(
                            lazyGridState = forgottenFavoritesGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                layoutSize * forgottenItemWidthFactor / 2f - itemSize / 2f
                            },
                        )
                    }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier =
                        Modifier
                            .widthIn(max = HomeFeedMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                ) {
                    if (uiState.showCategoryChips) {
                        item(
                            key = "home_category_chips",
                            contentType = "category_chips",
                        ) {
                            HomeCategoryChips(
                                chips = uiState.homePage?.chips.orEmpty(),
                                selectedChip = uiState.selectedChip,
                                onChipSelected = { onAction(HomeAction.SelectChip(it)) },
                            )
                        }
                    }

                    val chipSelected = uiState.selectedChip != null
                    if (chipSelected) {
                        // A category replaces the personalised feed: only its own
                        // shelves are shown, right under the chip row.
                        if (uiState.isChipLoading) {
                            item(
                                key = "home_chip_loading",
                                contentType = "loading",
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 64.dp),
                                ) {
                                    LoadingIndicator()
                                }
                            }
                        } else {
                            val chipSections = uiState.homePage?.sections.orEmpty()
                            if (chipSections.isEmpty()) {
                                item(
                                    key = "home_chip_empty",
                                    contentType = "chip_empty",
                                ) {
                                    HomeChipEmptyState(
                                        onRetry = { onAction(HomeAction.RetryChip) },
                                    )
                                }
                            } else {
                                homePageSections(
                                    sections = chipSections,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                )
                            }
                        }
                    } else {
                        if (remoteQuickPicks?.items?.isNotEmpty() == true) {
                            item(
                                key = "home_remote_quick_picks_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.quick_picks),
                                    dimAlpha = headerDimAlpha,
                                )
                            }
                            item(
                                key = "home_remote_quick_picks",
                                contentType = "quick_picks",
                            ) {
                                RemoteQuickPicksSection(
                                    section = remoteQuickPicks,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    displayMode = uiState.quickPicksDisplayMode,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                )
                            }
                        } else if (
                            uiState.quickPicksMode != QuickPicks.DONT_SHOW &&
                                uiState.quickPicks.isNotEmpty()
                        ) {
                            item(
                                key = "home_quick_picks_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.quick_picks),
                                    dimAlpha = headerDimAlpha,
                                )
                            }
                            item(
                                key = "home_quick_picks",
                                contentType = "quick_picks",
                            ) {
                                QuickPicksSection(
                                    quickPicks = uiState.quickPicks,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    displayMode = uiState.quickPicksDisplayMode,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                )
                            }
                        }

                        if (uiState.speedDialItems.isNotEmpty()) {
                            sectionSpacer("speed_dial")
                            item(
                                key = "home_speed_dial_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.speed_dial),
                                )
                            }
                            item(
                                key = "home_speed_dial",
                                contentType = "speed_dial",
                            ) {
                                SpeedDialSection(
                                    speedDialItems = uiState.speedDialItems,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                )
                            }
                        }

                        if (uiState.keepListening.isNotEmpty()) {
                            sectionSpacer("keep_listening")
                            item(
                                key = "home_keep_listening_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.keep_listening),
                                )
                            }
                            item(
                                key = "home_keep_listening",
                                contentType = "media_shelf",
                            ) {
                                KeepListeningSection(
                                    keepListening = uiState.keepListening,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                )
                            }
                        }

                        if (uiState.accountPlaylists.isNotEmpty()) {
                            sectionSpacer("account_playlists")
                            item(
                                key = "home_account_playlists",
                                contentType = "media_shelf",
                            ) {
                                Column {
                                    AccountPlaylistsTitle(
                                        accountName = uiState.accountName,
                                        accountImageUrl = uiState.accountImageUrl,
                                        onClick = { navController.navigate("account") },
                                    )
                                    AccountPlaylistsSection(
                                        accountPlaylists = uiState.accountPlaylists,
                                        mediaMetadata = mediaMetadata,
                                        isPlaying = isPlaying,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        haptic = haptic,
                                        scope = scope,
                                    )
                                }
                            }
                        }

                        if (uiState.forgottenFavorites.isNotEmpty()) {
                            sectionSpacer("forgotten_favorites")
                            item(
                                key = "home_forgotten_favorites_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = stringResource(R.string.forgotten_favorites),
                                )
                            }
                            item(
                                key = "home_forgotten_favorites",
                                contentType = "song_shelf",
                            ) {
                                ForgottenFavoritesSection(
                                    forgottenFavorites = uiState.forgottenFavorites,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    horizontalLazyGridItemWidth = forgottenItemWidth,
                                    lazyGridState = forgottenFavoritesGridState,
                                    snapLayoutInfoProvider = forgottenSnapLayoutInfoProvider,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                )
                            }
                        }

                        uiState.similarRecommendations.forEach { recommendation ->
                            sectionSpacer("similar_${recommendation.title.id}")
                            item(
                                key = "home_similar_header_${recommendation.title.id}",
                                contentType = "section_header",
                            ) {
                                SimilarRecommendationsTitle(
                                    recommendation = recommendation,
                                    navController = navController,
                                )
                            }
                            item(
                                key = "home_similar_${recommendation.title.id}",
                                contentType = "media_shelf",
                            ) {
                                SimilarRecommendationsSection(
                                    recommendation = recommendation,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                )
                            }
                        }

                        homePageSections(
                            sections = uiState.homePage?.sections.orEmpty(),
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                        )

                    }
                    if (uiState.isLoadingMore) {
                        item(
                            key = "home_loading_more",
                            contentType = "loading",
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),

                            ) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
private fun LazyListScope.homePageSections(
    sections: List<HomePage.Section>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
) {
    sections.forEachIndexed { index, section ->
        val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
        sectionSpacer("remote_$sectionKey")
        item(
            key = "home_remote_header_$sectionKey",
            contentType = "section_header",
        ) {
            HomePageSectionTitle(
                section = section,
                navController = navController,
            )
        }
        item(
            key = "home_remote_$sectionKey",
            contentType = "media_shelf",
        ) {
            HomePageSectionContent(
                section = section,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * Shown when a home category is selected but YouTube Music returned no shelves for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeChipEmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_results_found),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionSpacer(key: String) {
    item(
        key = "home_section_spacer_$key",
        contentType = "section_spacer",
    ) {
        Spacer(Modifier.height(HomeSectionSpacing))
    }
}
