/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.citali.lunartune.BuildConfig
import dev.citali.lunartune.constants.UpdateChannel
import dev.citali.lunartune.defaultUpdateChannel
import dev.citali.lunartune.musicrecognition.MusicRecognitionDetailsRoute
import dev.citali.lunartune.musicrecognition.MusicRecognitionRoute
import dev.citali.lunartune.ui.component.PrimaryRootDepthState
import dev.citali.lunartune.ui.component.PrimaryRootNavProxy
import dev.citali.lunartune.ui.screens.artist.ArtistAlbumsScreen
import dev.citali.lunartune.ui.screens.artist.ArtistItemsScreen
import dev.citali.lunartune.ui.screens.artist.ArtistScreen
import dev.citali.lunartune.ui.screens.artist.ArtistSongsScreen
import dev.citali.lunartune.ui.screens.library.LocalSongScreen
import dev.citali.lunartune.ui.screens.musicrecognition.MusicRecognitionDetailsScreen
import dev.citali.lunartune.ui.screens.musicrecognition.MusicRecognitionScreen
import dev.citali.lunartune.ui.screens.playlist.AutoPlaylistScreen
import dev.citali.lunartune.ui.screens.playlist.CachePlaylistScreen
import dev.citali.lunartune.ui.screens.playlist.LocalPlaylistScreen
import dev.citali.lunartune.ui.screens.playlist.OnlinePlaylistScreen
import dev.citali.lunartune.ui.screens.playlist.SpotifyPlaylistScreen
import dev.citali.lunartune.ui.screens.playlist.TopPlaylistScreen
import dev.citali.lunartune.ui.screens.search.OnlineSearchResult
import dev.citali.lunartune.ui.screens.search.OnlineSearchResultArgument
import dev.citali.lunartune.ui.screens.search.OnlineSearchResultRoute
import dev.citali.lunartune.ui.screens.search.OnlineSearchResultRoutePrefix
import dev.citali.lunartune.ui.screens.settings.AboutScreen
import dev.citali.lunartune.ui.screens.settings.AccountSettings
import dev.citali.lunartune.ui.screens.settings.AiIntegrationSettings
import dev.citali.lunartune.ui.screens.settings.AodCustomizedScreen
import dev.citali.lunartune.ui.screens.settings.AppearanceSettings
import dev.citali.lunartune.ui.screens.settings.BackupAndRestore
import dev.citali.lunartune.ui.screens.settings.ChangelogScreen
import dev.citali.lunartune.ui.screens.settings.ChiperSettings
import dev.citali.lunartune.ui.screens.settings.ContentSettings
import dev.citali.lunartune.ui.screens.settings.CustomizeBackground
import dev.citali.lunartune.ui.screens.settings.DebugSettings
import dev.citali.lunartune.ui.screens.settings.DiscordSettings
import dev.citali.lunartune.ui.screens.settings.HiddenPlaylistsScreen
import dev.citali.lunartune.ui.screens.settings.IconScreen
import dev.citali.lunartune.ui.screens.settings.IntegrationScreen
import dev.citali.lunartune.ui.screens.settings.InternetSettings
import dev.citali.lunartune.ui.screens.settings.LastFMSettings
import dev.citali.lunartune.ui.screens.settings.LogcatScreen
import dev.citali.lunartune.ui.screens.settings.LyricsAnimationSettings
import dev.citali.lunartune.ui.screens.settings.LyricsSettings
import dev.citali.lunartune.ui.screens.settings.MusicTogetherScreen
import dev.citali.lunartune.ui.screens.settings.MusicTransferScreen
import dev.citali.lunartune.ui.screens.settings.NavigationBarSettings
import dev.citali.lunartune.ui.screens.settings.PalettePickerScreen
import dev.citali.lunartune.ui.screens.settings.PlayerSettings
import dev.citali.lunartune.ui.screens.settings.PrivacySettings
import dev.citali.lunartune.ui.screens.settings.SettingsScreen
import dev.citali.lunartune.ui.screens.settings.StorageSettings
import dev.citali.lunartune.ui.screens.settings.StreamSourcesSettings
import dev.citali.lunartune.ui.screens.settings.ThemeCreatorScreen
import dev.citali.lunartune.ui.screens.settings.UpdateScreen
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.viewmodels.OnlineSearchSort

private val DrillTransitionCornerShape = RoundedCornerShape(24.dp)

@Composable
private fun AnimatedContentScope.DrillPageContainer(
    pureBlack: Boolean,
    content: @Composable () -> Unit,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (transition.isRunning) {
                        shape = DrillTransitionCornerShape
                        clip = true
                    } else {
                        shape = RectangleShape
                        clip = false
                    }
                }.background(containerColor),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: () -> String,
    primaryRootDepthState: PrimaryRootDepthState,
    onSelectPrimaryTab: (Screens) -> Unit,
    pureBlack: Boolean = false,
    disableAnimations: Boolean = false,
    onClearUpdateBadge: () -> Unit = {},
    onlineSearchSort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
) {
    composable(Screens.Home.route) {
        PrimaryRootNavProxy(
            depthState = primaryRootDepthState,
            disableAnimations = disableAnimations,
        )
    }
    composable(
        route = Screens.Library.route,
        enterTransition = { fadeIn(tween(0)) },
        exitTransition = { fadeOut(tween(0)) },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
    ) {
        LaunchedEffect(Unit) {
            onSelectPrimaryTab(Screens.Library)
            if (!navController.popBackStack(Screens.Home.route, inclusive = false)) {
                navController.navigate(Screens.Home.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
    composable(
        route = Screens.Search.route,
        enterTransition = { fadeIn(tween(0)) },
        exitTransition = { fadeOut(tween(0)) },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
    ) {
        LaunchedEffect(Unit) {
            onSelectPrimaryTab(Screens.Search)
            if (!navController.popBackStack(Screens.Home.route, inclusive = false)) {
                navController.navigate(Screens.Home.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
    composable("local_songs") {
        DrillPageContainer(pureBlack) {
            LocalSongScreen(navController)
        }
    }
    composable("history") {
        DrillPageContainer(pureBlack) {
            HistoryScreen(navController)
        }
    }
    composable("stats") {
        DrillPageContainer(pureBlack) {
            StatsScreen(navController)
        }
    }
    composable("news") {
        DrillPageContainer(pureBlack) {
            NewsScreen(navController)
        }
    }
    composable(
        route = "view_news/{newsId}",
        arguments =
            listOf(
                navArgument("newsId") { type = NavType.StringType },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            ViewNewsScreen(navController)
        }
    }
    composable(
        route = "year_in_music?year={year}",
        arguments =
            listOf(
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { backStackEntry ->
        val selectedYear = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
        DrillPageContainer(pureBlack) {
            YearInMusicScreen(
                navController = navController,
                initialYear = selectedYear,
            )
        }
    }
    composable(MusicRecognitionRoute) {
        DrillPageContainer(pureBlack) {
            MusicRecognitionScreen(navController)
        }
    }
    composable(MusicRecognitionDetailsRoute) { backStackEntry ->
        val encodedTrack = backStackEntry.arguments?.getString("encodedTrack").orEmpty()
        DrillPageContainer(pureBlack) {
            MusicRecognitionDetailsScreen(navController, encodedTrack)
        }
    }
    composable(Screens.MoodAndGenres.route) {
        DrillPageContainer(pureBlack) {
            MoodAndGenresScreen(navController)
        }
    }
    composable("account") {
        DrillPageContainer(pureBlack) {
            AccountScreen(navController, scrollBehavior)
        }
    }
    composable("new_release") {
        DrillPageContainer(pureBlack) {
            NewReleaseScreen(navController, scrollBehavior)
        }
    }
    composable("charts_screen") {
        DrillPageContainer(pureBlack) {
            ChartsScreen(navController)
        }
    }
    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            BrowseScreen(
                navController,
                scrollBehavior,
                it.arguments?.getString("browseId"),
            )
        }
    }
    composable(
        route = OnlineSearchResultRoute,
        arguments =
            listOf(
                navArgument(OnlineSearchResultArgument) {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            when {
                disableAnimations -> fadeIn(tween(0))
                initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true ->
                    fadeIn(tween(180))
                else -> LunarMotion.depthPageEnter()
            }
        },
        exitTransition = {
            when {
                disableAnimations -> fadeOut(tween(0))
                targetState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true ->
                    fadeOut(tween(140))
                else -> LunarMotion.depthPageExit()
            }
        },
        popEnterTransition = {
            when {
                disableAnimations -> fadeIn(tween(0))
                initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true ->
                    fadeIn(tween(180))
                else -> LunarMotion.depthPagePopEnter()
            }
        },
        popExitTransition = {
            when {
                disableAnimations -> fadeOut(tween(0))
                targetState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true ->
                    fadeOut(tween(140))
                else -> LunarMotion.depthPagePopExit()
            }
        },
    ) {
        DrillPageContainer(pureBlack) {
            OnlineSearchResult(
                navController = navController,
                searchSort = onlineSearchSort,
            )
        }
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            AlbumScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            ArtistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            ArtistSongsScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            ArtistAlbumsScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            ArtistItemsScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            OnlinePlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            SpotifyPlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "auto_playlist/{playlist}?tab={tab}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "downloaded"
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            CachePlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            TopPlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        DrillPageContainer(pureBlack) {
            YouTubeBrowseScreen(navController)
        }
    }
    composable("settings") {
        DrillPageContainer(pureBlack) {
            SettingsScreen(navController, latestVersionName())
        }
    }
    composable("settings/account") {
        DrillPageContainer(pureBlack) {
            AccountSettings(navController, latestVersionName())
        }
    }
    composable("settings/hidden_playlists") {
        DrillPageContainer(pureBlack) {
            HiddenPlaylistsScreen(navController)
        }
    }
    composable("settings/appearance") {
        DrillPageContainer(pureBlack) {
            AppearanceSettings(navController)
        }
    }
    composable("settings/appearance/icon") {
        DrillPageContainer(pureBlack) {
            IconScreen(navController)
        }
    }
    composable("settings/appearance/navigation_bar") {
        DrillPageContainer(pureBlack) {
            NavigationBarSettings(navController)
        }
    }
    composable("settings/appearance/aod_customized") {
        DrillPageContainer(pureBlack) {
            AodCustomizedScreen(navController)
        }
    }
    composable("settings/appearance/palette_picker") {
        DrillPageContainer(pureBlack) {
            PalettePickerScreen(navController)
        }
    }
    composable("settings/appearance/lyrics_animations") {
        DrillPageContainer(pureBlack) {
            LyricsAnimationSettings(navController)
        }
    }
    composable("settings/appearance/theme_creator") {
        DrillPageContainer(pureBlack) {
            ThemeCreatorScreen(navController)
        }
    }
    composable("settings/content") {
        DrillPageContainer(pureBlack) {
            ContentSettings(navController)
        }
    }
    composable("settings/lyrics") {
        DrillPageContainer(pureBlack) {
            LyricsSettings(navController)
        }
    }
    composable("settings/internet") {
        DrillPageContainer(pureBlack) {
            InternetSettings(navController)
        }
    }
    composable("settings/player") {
        DrillPageContainer(pureBlack) {
            PlayerSettings(navController)
        }
    }
    composable("settings/player/stream_sources") {
        DrillPageContainer(pureBlack) {
            StreamSourcesSettings(navController)
        }
    }
    composable("settings/player/chiper") {
        DrillPageContainer(pureBlack) {
            ChiperSettings(navController)
        }
    }
    composable("settings/storage") {
        DrillPageContainer(pureBlack) {
            StorageSettings(navController)
        }
    }
    composable("settings/privacy") {
        DrillPageContainer(pureBlack) {
            PrivacySettings(navController)
        }
    }
    composable("settings/backup_restore") {
        DrillPageContainer(pureBlack) {
            BackupAndRestore(navController)
        }
    }
    composable("settings/music_transfer") {
        DrillPageContainer(pureBlack) {
            MusicTransferScreen(navController)
        }
    }
    composable("settings/discord") {
        DrillPageContainer(pureBlack) {
            DiscordSettings(navController)
        }
    }
    composable("settings/integration") {
        DrillPageContainer(pureBlack) {
            IntegrationScreen(navController)
        }
    }
    composable("settings/ai_integration") {
        DrillPageContainer(pureBlack) {
            AiIntegrationSettings(navController)
        }
    }
    composable("settings/music_together") {
        DrillPageContainer(pureBlack) {
            MusicTogetherScreen(navController)
        }
    }
    composable("settings/lastfm") {
        DrillPageContainer(pureBlack) {
            LastFMSettings(navController)
        }
    }
    composable("settings/discord/experimental") {
        DrillPageContainer(pureBlack) {
            dev.citali.lunartune.ui.screens.settings
                .DiscordExperimental(navController)
        }
    }
    composable("settings/misc") {
        DrillPageContainer(pureBlack) {
            DebugSettings(navController)
        }
    }
    composable("settings/logcat") {
        DrillPageContainer(pureBlack) {
            LogcatScreen(navController)
        }
    }
    if (BuildConfig.UPDATER_AVAILABLE) {
        composable("settings/update") {
            DrillPageContainer(pureBlack) {
                UpdateScreen(navController, onUpToDate = onClearUpdateBadge)
            }
        }
    }
    composable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel = UpdateChannel.fromStoredName(channelName, defaultUpdateChannel)
        DrillPageContainer(pureBlack) {
            ChangelogScreen(navController, channel = channel)
        }
    }
    composable("settings/about") {
        DrillPageContainer(pureBlack) {
            AboutScreen(navController)
        }
    }
    composable("customize_background") {
        DrillPageContainer(pureBlack) {
            CustomizeBackground(navController)
        }
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        DrillPageContainer(pureBlack) {
            LoginScreen(
                navController,
                startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
            )
        }
    }
}
