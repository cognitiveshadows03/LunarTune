/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.citali.lunartune.aod.ACTION_AOD_MODE
import dev.citali.lunartune.constants.AodModeEnabledKey
import dev.citali.lunartune.constants.AppBarHeight
import dev.citali.lunartune.constants.AppFontPreference
import dev.citali.lunartune.constants.AppLanguageKey
import dev.citali.lunartune.constants.CustomFontUriKey
import dev.citali.lunartune.constants.CustomThemeColorKey
import dev.citali.lunartune.constants.DarkModeKey
import dev.citali.lunartune.constants.DefaultOpenTabKey
import dev.citali.lunartune.constants.DisableAnimationsKey
import dev.citali.lunartune.constants.DisableScreenshotKey
import dev.citali.lunartune.constants.DynamicThemeKey
import dev.citali.lunartune.constants.EnableHapticFeedbackKey
import dev.citali.lunartune.constants.FloatingNavigationBarBottomPadding
import dev.citali.lunartune.constants.FloatingNavigationBarHorizontalPadding
import dev.citali.lunartune.constants.FontPreferenceKey
import dev.citali.lunartune.constants.ForceHighRefreshRateKey
import dev.citali.lunartune.constants.HasPressedStarKey
import dev.citali.lunartune.constants.LaunchCountKey
import dev.citali.lunartune.constants.MiniPlayerBackgroundStyle
import dev.citali.lunartune.constants.MiniPlayerBackgroundStyleKey
import dev.citali.lunartune.constants.MiniPlayerBottomSpacing
import dev.citali.lunartune.constants.MiniPlayerHeight
import dev.citali.lunartune.constants.MiniPlayerLastAnchorKey
import dev.citali.lunartune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import dev.citali.lunartune.constants.NavBarHideOnScrollKey
import dev.citali.lunartune.constants.NavigationBarAnimationSpec
import dev.citali.lunartune.constants.NavigationBarBottomPadding
import dev.citali.lunartune.constants.NavigationBarHeight
import dev.citali.lunartune.constants.NavigationBarHeightKey
import dev.citali.lunartune.constants.NavigationBarLegacyLiveBlurKey
import dev.citali.lunartune.constants.NavigationBarHorizontalPadding
import dev.citali.lunartune.constants.NavigationBarStyle
import dev.citali.lunartune.constants.NavigationBarStyleKey
import dev.citali.lunartune.constants.PauseSearchHistoryKey
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerDesignStyleKey
import dev.citali.lunartune.constants.PlayerDesignStyleOverridesKey
import dev.citali.lunartune.constants.PureBlackKey
import dev.citali.lunartune.constants.RemindAfterKey
import dev.citali.lunartune.constants.SYSTEM_DEFAULT
import dev.citali.lunartune.constants.SearchSource
import dev.citali.lunartune.constants.SearchSourceKey
import dev.citali.lunartune.constants.StopMusicOnTaskClearKey
import dev.citali.lunartune.constants.UpdateChannel
import dev.citali.lunartune.constants.UpdateChannelKey
import dev.citali.lunartune.constants.UseSystemFontKey
import dev.citali.lunartune.constants.UseSystemLanguageKey
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.Playlist
import dev.citali.lunartune.db.entities.SearchHistory
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.musicrecognition.ACTION_MUSIC_RECOGNITION
import dev.citali.lunartune.musicrecognition.MusicRecognitionRoute
import dev.citali.lunartune.musicrecognition.openMusicRecognition
import dev.citali.lunartune.onboarding.OnboardingScreenState
import dev.citali.lunartune.onboarding.OnboardingViewModel
import dev.citali.lunartune.playback.DownloadUtil
import dev.citali.lunartune.playback.MusicService
import dev.citali.lunartune.playback.MusicService.MusicBinder
import dev.citali.lunartune.playback.PlayerConnection
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.playback.queues.LocalAlbumRadio
import dev.citali.lunartune.playback.queues.Queue
import dev.citali.lunartune.playback.queues.YouTubeAlbumRadio
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.BottomSheetMenu
import dev.citali.lunartune.ui.component.BottomSheetPage
import dev.citali.lunartune.ui.component.BottomSheetState
import dev.citali.lunartune.ui.component.COLLAPSED_ANCHOR
import dev.citali.lunartune.ui.component.PrimaryTabsHost
import dev.citali.lunartune.ui.component.rememberPrimaryRootDepthState
import dev.citali.lunartune.ui.component.DISMISSED_ANCHOR
import dev.citali.lunartune.ui.component.EXPANDED_ANCHOR
import dev.citali.lunartune.ui.component.FloatingNavigationToolbar
import dev.citali.lunartune.ui.component.FrostedFallbackAlpha
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.LocalBottomSheetPageState
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.MarkdownText
import dev.citali.lunartune.ui.component.NavBarBlurState
import dev.citali.lunartune.ui.component.NetworkStatusBanner
import dev.citali.lunartune.ui.component.TapAnchor
import dev.citali.lunartune.ui.component.TopSearch
import dev.citali.lunartune.ui.component.TvNavigationRail
import dev.citali.lunartune.ui.component.frostedTopMenuStyle
import dev.citali.lunartune.ui.component.navBarLiveBlurSource
import dev.citali.lunartune.ui.component.rememberBottomSheetState
import dev.citali.lunartune.ui.component.shimmer.ShimmerTheme
import dev.citali.lunartune.ui.menu.YouTubeSongMenu
import dev.citali.lunartune.ui.player.BottomSheetPlayer
import dev.citali.lunartune.ui.screens.LOGIN_URL_ARGUMENT
import dev.citali.lunartune.ui.screens.Screens
import dev.citali.lunartune.ui.screens.buildLoginRoute
import dev.citali.lunartune.ui.screens.navigationBuilder
import dev.citali.lunartune.ui.screens.onboarding.OnboardingRoute
import dev.citali.lunartune.ui.screens.search.LocalSearchScreen
import dev.citali.lunartune.ui.screens.search.OnlineSearchResultArgument
import dev.citali.lunartune.ui.screens.search.OnlineSearchResultRoutePrefix
import dev.citali.lunartune.ui.screens.search.OnlineSearchScreen
import dev.citali.lunartune.ui.screens.search.decodeOnlineSearchQuery
import dev.citali.lunartune.ui.screens.search.onlineSearchResultRoute
import dev.citali.lunartune.ui.screens.settings.ApplyForcedRefreshRate
import dev.citali.lunartune.ui.screens.settings.DarkMode
import dev.citali.lunartune.ui.screens.settings.NavigationTab
import dev.citali.lunartune.ui.theme.ColorSaver
import dev.citali.lunartune.ui.theme.DefaultThemeColor
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.ui.theme.LunarTuneTheme
import dev.citali.lunartune.ui.theme.extractThemeColor
import dev.citali.lunartune.ui.utils.appBarScrollBehavior
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.ui.utils.resetHeightOffset
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.SyncUtils
import dev.citali.lunartune.utils.Updater
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import dev.citali.lunartune.utils.isLowRamDevice
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.utils.reportException
import dev.citali.lunartune.utils.resolvePlayerDesignStyle
import dev.citali.lunartune.utils.setAppLocale
import dev.citali.lunartune.viewmodels.BackupCategory
import dev.citali.lunartune.viewmodels.BackupRestoreViewModel
import dev.citali.lunartune.viewmodels.GatekeeperViewModel
import dev.citali.lunartune.viewmodels.HomeViewModel
import dev.citali.lunartune.viewmodels.NetworkBannerViewModel
import dev.citali.lunartune.viewmodels.OnlineSearchSort
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkQueue: Queue? = null
    private var pendingVoiceSearchQuery: String? = null
    private var pendingAodModeRequest = false
    private var pendingAodModeJob: Job? = null
    private var aodModeLaunchRequestCount by mutableIntStateOf(0)
    private var pendingTogetherJoinLink: String? = null
    private var pendingBackupRestoreUri by mutableStateOf<Uri?>(null)
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    private var latestUpdateChannel by mutableStateOf(defaultUpdateChannel)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false
    private var immersiveStatusBarsHidden = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkQueueIfReady()
                    playPendingVoiceSearchIfReady()
                    openPendingAodModeIfReady()
                    joinPendingTogetherIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                pendingAodModeJob?.cancel()
                pendingAodModeJob = null
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private fun playPendingDeepLinkQueueIfReady() {
        val pending = pendingDeepLinkQueue ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkQueue = null
        connection.playQueue(pending)
    }

    private fun playPendingVoiceSearchIfReady() {
        val query = pendingVoiceSearchQuery ?: return
        val connection = playerConnection ?: return
        pendingVoiceSearchQuery = null
        connection.playFromVoiceSearch(query)
    }

    private fun requestAodMode() {
        if (!dataStore.get(AodModeEnabledKey, true)) return
        pendingAodModeRequest = true
        startMusicServiceSafely()
        openPendingAodModeIfReady()
    }

    private fun openPendingAodModeIfReady() {
        if (!pendingAodModeRequest) return
        val connection = playerConnection ?: return
        pendingAodModeRequest = false
        pendingAodModeJob?.cancel()
        pendingAodModeJob =
            lifecycleScope.launch {
                connection.queueRestoreCompleted.first { it }
                if (awaitRestorablePlayback(connection)) {
                    aodModeLaunchRequestCount++
                }
            }
    }

    private fun joinPendingTogetherIfReady() {
        val pending = pendingTogetherJoinLink ?: return
        val connection = playerConnection ?: return
        pendingTogetherJoinLink = null
        lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching { dataStore.data.first()[dev.citali.lunartune.constants.TogetherDisplayNameKey] }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Build.MODEL ?: getString(R.string.app_name) }
            withContext(Dispatchers.Main) {
                connection.service.joinTogether(pending, displayName)
            }
        }
    }

    private suspend fun awaitRestorablePlayback(connection: PlayerConnection): Boolean {
        repeat(15) {
            if (
                connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
            ) {
                return true
            }
            delay(100)
        }

        return (
            connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
        )
    }

    override fun onStart() {
        super.onStart()
        isMusicServiceBound =
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        playPendingDeepLinkQueueIfReady()
        openPendingAodModeIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        if (!isMusicServiceBound || playerConnection?.aodModeEnabled?.value == true) {
            super.onStop()
            return
        }
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        val shouldStopOnTaskClear =
            if (!isFinishing) {
                false
            } else {
                dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveStatusBarsHidden) {
            setStatusBarsHidden(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }

        val initialLocale =
            if (PreferenceStore.get(UseSystemLanguageKey) ?: true) {
                Locale.getDefault()
            } else {
                Locale.ENGLISH
            }
        setAppLocale(this, initialLocale)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.data.first().let { prefs ->
                    if (prefs[UseSystemLanguageKey] ?: true) {
                        Locale.getDefault()
                    } else {
                        Locale.ENGLISH
                    }
                }
            }.onSuccess { targetLocale ->
                if (targetLocale != initialLocale) {
                    withContext(Dispatchers.Main) {
                        setAppLocale(this@MainActivity, targetLocale)
                        recreate()
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            // Cold start: hold a plain starting surface over everything (including
            // the navigation bar) until the app has bound and produced a frame.
            // This removes the empty screen shell / skeleton flash that used to
            // appear before any content was ready.
            var appContentReady by remember { mutableStateOf(false) }
            val startupSurface = colorResource(R.color.window_background)

            LaunchedEffect(Unit) {
                withFrameNanos { }
                withTimeoutOrNull(2_000L) {
                    while (playerConnection == null) delay(50L)
                }
                withFrameNanos { }
                appContentReady = true
            }

            val gatekeeperViewModel: GatekeeperViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                gatekeeperViewModel.blockedMessages.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            val updateChannel by rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)

            LaunchedEffect(Unit) {
                while (playerConnection == null) {
                    delay(100)
                }
                delay(500)

                try {
                    val redownload = withContext(Dispatchers.IO) {
                        dataStore.data.first()[dev.citali.lunartune.constants.RedownloadOnRestoreKey] ?: false
                    }
                    if (redownload) {
                        val downloaded = withContext(Dispatchers.IO) {
                            database.downloadedSongsList()
                        }
                        if (downloaded.isNotEmpty()) {
                            downloaded.forEach { song ->
                                val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest
                                    .Builder(song.id, song.id.toUri())
                                    .setCustomCacheKey(song.id)
                                    .setData(song.title.toByteArray())
                                    .build()
                                androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                                    this@MainActivity,
                                    dev.citali.lunartune.playback.ExoDownloadService::class.java,
                                    downloadRequest,
                                    false,
                                )
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Re-downloading ${downloaded.size} offline songs...", Toast.LENGTH_LONG).show()
                            }
                        }
                        withContext(Dispatchers.IO) {
                            dataStore.edit { prefs ->
                                prefs[dev.citali.lunartune.constants.RedownloadOnRestoreKey] = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    dev.citali.lunartune.utils.reportException(e)
                }

                if (BuildConfig.UPDATER_AVAILABLE) {
                    if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                        val channelString = withContext(Dispatchers.IO) { dataStore.data.first()[UpdateChannelKey] }
                        val actualChannel = UpdateChannel.fromStoredName(channelString, defaultUpdateChannel)
                        val versionResult =
                            when (actualChannel) {
                                UpdateChannel.CANARY -> Updater.getLatestCanaryVersionName()
                                UpdateChannel.STABLE -> Updater.getLatestVersionName()
                            }
                        versionResult.onSuccess {
                            if (Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME)) {
                                latestUpdateChannel = actualChannel
                                latestVersionName = it
                            }
                        }
                    }
                    dev.citali.lunartune.utils.UpdateNotificationManager
                        .checkForUpdates(this@MainActivity)
                }
            }

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState =
                remember {
                    dev.citali.lunartune.ui.component
                        .BottomSheetPageState()
                }
            val menuState =
                remember {
                    dev.citali.lunartune.ui.component
                        .MenuState()
                }
            val releaseNotesState = remember { mutableStateOf<String?>(null) }
            val updateSheetContent: @Composable ColumnScope.() -> Unit = {
                // receiver: ColumnScope
                Text(
                    text = stringResource(R.string.new_update_available),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp),
                )

                Spacer(Modifier.height(8.dp))

                androidx.compose.material3.OutlinedButton(
                    onClick = {},
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 5.dp,
                            vertical = 5.dp,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = latestVersionName, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    val notes = releaseNotesState.value
                    if (notes != null && notes.isNotBlank()) {
                        MarkdownText(
                            markdown = notes,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                androidx.compose.material3.Button(
                    onClick = {
                        bottomSheetPageState.dismiss()
                        this@MainActivity.navController.navigate("settings/update") {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.update_text))
                }
            }

            // fetch release notes and show sheet when a new version is detected
            LaunchedEffect(latestVersionName, latestUpdateChannel, updateChannel) {
                if (
                    BuildConfig.UPDATER_AVAILABLE &&
                    latestUpdateChannel == updateChannel &&
                    Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
                ) {
                    val releaseNotesResult =
                        when (latestUpdateChannel) {
                            UpdateChannel.CANARY -> Updater.getLatestCanaryReleaseNotes()
                            UpdateChannel.STABLE -> Updater.getLatestReleaseNotes()
                        }
                    releaseNotesResult
                        .onSuccess {
                            releaseNotesState.value = it
                        }.onFailure {
                            releaseNotesState.value = null
                        }

                    bottomSheetPageState.show(updateSheetContent)
                }
            }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val defaultDisableAnimations = remember(this@MainActivity) { applicationContext.isLowRamDevice() }
            val disableAnimations by rememberPreference(
                DisableAnimationsKey,
                defaultValue = defaultDisableAnimations,
            )
            val forceHighRefreshRate by rememberPreference(
                ForceHighRefreshRateKey,
                defaultValue = true,
            )
            ApplyForcedRefreshRate(enabled = forceHighRefreshRate)
            val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
            val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
            val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme

            val customThemeSeedPalette =
                remember(customThemeColorValue) {
                    if (customThemeColorValue.startsWith("#")) {
                        null
                    } else if (customThemeColorValue.startsWith("seedPalette:")) {
                        dev.citali.lunartune.ui.theme.ThemeSeedPaletteCodec
                            .decodeFromPreference(customThemeColorValue)
                    } else {
                        dev.citali.lunartune.ui.screens.settings.ThemePalettes
                            .findById(customThemeColorValue)
                            ?.let {
                                dev.citali.lunartune.ui.theme.ThemeSeedPalette(
                                    primary = it.primary,
                                    secondary = it.secondary,
                                    tertiary = it.tertiary,
                                    neutral = it.neutral,
                                )
                            }
                    }
                }

            val customThemeColor =
                remember(customThemeColorValue, customThemeSeedPalette) {
                    if (customThemeColorValue.startsWith("#")) {
                        try {
                            val colorString = customThemeColorValue.removePrefix("#")
                            Color(android.graphics.Color.parseColor("#$colorString"))
                        } catch (e: Exception) {
                            DefaultThemeColor
                        }
                    } else {
                        customThemeSeedPalette?.primary ?: DefaultThemeColor
                    }
                }

            // Open on the colour the user settled on. Starting from the stock
            // accent and letting the effect below correct it meant every launch
            // flashed the default palette before their own appeared.
            val savedThemeColor =
                if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(savedThemeColor)
            }

            LaunchedEffect(legacyUseSystemFont) {
                if (!legacyUseSystemFont) return@LaunchedEffect
                val preferences = dataStore.data.first()
                if (preferences[FontPreferenceKey] == null) {
                    dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
                }
            }

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .build(),
                                    )
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                withContext(Dispatchers.Main) {
                                    themeColor = extractedColor ?: DefaultThemeColor
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeColor = DefaultThemeColor
                                }
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            themeColor = DefaultThemeColor
                        } else {
                            themeColor = customThemeColor
                        }
                    }
                }
            }

            LunarTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                seedPalette = if (!enableDynamicTheme) customThemeSeedPalette else null,
                disableAnimations = disableAnimations,
                fontPreference = fontPreference,
                customFontUri = customFontUri,
            ) {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                val onboardingState by onboardingViewModel.screenState.collectAsStateWithLifecycle()
                val shouldShowOnboarding =
                    when (val state = onboardingState) {
                        OnboardingScreenState.Loading -> true
                        OnboardingScreenState.Empty -> true
                        is OnboardingScreenState.Error -> false
                        is OnboardingScreenState.Success -> state.uiState.shouldShowOnboarding
                    }

                if (shouldShowOnboarding) {
                    OnboardingRoute(viewModel = onboardingViewModel)
                    return@LunarTuneTheme
                }

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                if (appContentReady) {
                                    drawContent()
                                } else {
                                    drawRect(startupSurface)
                                }
                            }
                            .then(
                                if (appContentReady) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                },
                            )
                            // Records where the finger went down so menus can grow out of
                            // the touched point. Reads at Initial (before children) and
                            // consumes nothing, so gestures underneath are unchanged. Only
                            // touch-down writes; moves are ignored.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type == PointerEventType.Press) {
                                            event.changes.firstOrNull()?.let { change ->
                                                TapAnchor.record(change.position)
                                            }
                                        }
                                    }
                                }
                            }
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            ),
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                    val isTvDevice = remember { applicationContext.isTvDevice() }
                    val useRail =
                        isTvDevice ||
                            currentWindowAdaptiveInfo()
                                .windowSizeClass
                                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                    val navController = rememberNavController()
                    DisposableEffect(navController) {
                        this@MainActivity.navController = navController
                        onDispose {}
                    }
                    val coroutineScope = rememberCoroutineScope()
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
                    val allLocalItems by homeViewModel.allLocalItems.collectAsState()
                    val allYtItems by homeViewModel.allYtItems.collectAsState()
                    val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val onlineSearchEncodedQuery =
                        navBackStackEntry
                            ?.takeIf {
                                it.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                            }?.arguments
                            ?.getString(OnlineSearchResultArgument)
                    var onlineSearchSort by rememberSaveable { mutableStateOf(OnlineSearchSort.DEFAULT) }

                    val navigationItems =
                        remember(isTvDevice) {
                            if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
                        }
                    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) =
                        rememberPreference(
                            MiniPlayerLastAnchorKey,
                            defaultValue = COLLAPSED_ANCHOR,
                        )
                    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
                    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }

                    val initialPrimaryTab =
                        remember(defaultOpenTab, tabOpenedFromShortcut) {
                            when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                NavigationTab.HOME -> Screens.Home
                                NavigationTab.SEARCH -> Screens.Search
                                NavigationTab.LIBRARY -> Screens.Library
                                else -> Screens.Home
                            }
                        }
                    var selectedPrimaryTabRoute by rememberSaveable { mutableStateOf(initialPrimaryTab.route) }
                    val selectedPrimaryTab =
                        remember(selectedPrimaryTabRoute, navigationItems) {
                            navigationItems.fastFirstOrNull { it.route == selectedPrimaryTabRoute } ?: Screens.Home
                        }
                    val primaryRootDepthState = rememberPrimaryRootDepthState()

                    val rawNavRoute = navBackStackEntry?.destination?.route
                    val isAtPrimaryRoot = rawNavRoute == null || rawNavRoute == Screens.Home.route
                    val currentRoute = if (isAtPrimaryRoot) selectedPrimaryTab.route else rawNavRoute
                    val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true

                    val topLevelScreens =
                        remember(navigationItems) {
                            navigationItems.map(Screens::route)
                        }

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (isAtPrimaryRoot) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }
                    val tvRailFocusRequester = remember { FocusRequester() }
                    val contentAreaFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(onlineSearchEncodedQuery) {
                        onlineSearchSort = OnlineSearchSort.DEFAULT
                    }

                    val openSearch: () -> Unit = {
                        onActiveChange(true)
                        searchBarFocusRequester.requestFocus()
                    }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate(onlineSearchResultRoute(it))
                            if (!pauseSearchHistory) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldShowSearchBar =
                        remember(active, isAtPrimaryRoot, rawNavRoute) {
                            active ||
                                isAtPrimaryRoot ||
                                rawNavRoute?.startsWith(OnlineSearchResultRoutePrefix) == true
                        }

                    val shouldShowNavigationBar =
                        remember(isAtPrimaryRoot, active) {
                            isAtPrimaryRoot && !active
                        }

                    val shouldShowHomeShuffleButton =
                        currentRoute == Screens.Home.route &&
                            (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())

                    val navigationBarStyle by rememberEnumPreference(
                        NavigationBarStyleKey,
                        defaultValue = NavigationBarStyle.DEFAULT,
                    )
                    val miniPlayerBackgroundStyle by rememberEnumPreference(
                        MiniPlayerBackgroundStyleKey,
                        defaultValue = MiniPlayerBackgroundStyle.THEME,
                    )
                    val isMiniPlayerFrosted = miniPlayerBackgroundStyle == MiniPlayerBackgroundStyle.FROSTED
                    val hazeState = rememberHazeState()
                    val isFrostedNavBar = navigationBarStyle == NavigationBarStyle.FROSTED
                    // The capsule bar has no RenderEffect path of its own, on any version, so it
                    // blurs by sampling the content behind it and computing the blur - opt-in,
                    // because that work is real even when it is small.
                    val (legacyNavBarBlurEnabled, _) =
                        rememberPreference(NavigationBarLegacyLiveBlurKey, defaultValue = false)
                    val capsuleLiveBlurWanted =
                        legacyNavBarBlurEnabled && navigationBarStyle == NavigationBarStyle.CAPSULE
                    val navBarBlurState = remember { NavBarBlurState() }
                    val capsuleLiveBlurSource = if (capsuleLiveBlurWanted) navBarBlurState else null
                    // The bar only stops being solid once there is actually a sample to show,
                    // so a device where the capture could not be set up keeps the plain capsule
                    // instead of turning into a hole. This flips exactly once.
                    val hasNavBarSample by
                        remember { derivedStateOf { navBarBlurState.image != null } }
                    val capsuleLiveBlur =
                        if (capsuleLiveBlurWanted && hasNavBarSample) navBarBlurState else null
                    // The top-bar menu owns its frost: while it is open the content source
                    // is attached just for the menu, and the (static) blur runs on every
                    // API level instead of only Android 12+.
                    var topBarMenuExpanded by rememberSaveable { mutableStateOf(false) }
                    val navigationBarHeightMultiplier by rememberPreference(
                        NavigationBarHeightKey,
                        defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT,
                    )
                    val isFloatingNavBar =
                        navigationBarStyle == NavigationBarStyle.FLOATING || isFrostedNavBar ||
                            navigationBarStyle == NavigationBarStyle.CAPSULE
                    val floatingBarsBottomPadding =
                        if (isFloatingNavBar) FloatingNavigationBarBottomPadding else NavigationBarBottomPadding
                    val navBarHorizontalPadding =
                        if (isFloatingNavBar) FloatingNavigationBarHorizontalPadding else NavigationBarHorizontalPadding
                    val navVisibleHeight = NavigationBarHeight * navigationBarHeightMultiplier

                    // Beta: scroll-driven navigation bar hide, reimplemented from
                    // scratch after the ArchiveTune port proved unsound: hiding now
                    // changes no layout at all. One Animatable progress (0 visible,
                    // 1 away) is dragged 1:1 by consumed scroll over a fixed travel,
                    // settled by a spring at fling end, and read only by graphics
                    // layers (bar translation + fade, mini player drift), so the
                    // slot, the sheet and every content padding stay frozen while
                    // the bar moves - nothing can seam, jump or repaint.
                    val navBarHideOnScroll by
                        rememberPreference(NavBarHideOnScrollKey, defaultValue = false)
                    val navHideAnim = remember { Animatable(0f) }
                    val navHideScope = rememberCoroutineScope()
                    val navHideTravelPx = with(LocalDensity.current) { 140.dp.toPx() }
                    var navHideFlingWindow by remember { mutableStateOf(false) }
                    var navHideDragJob by remember { mutableStateOf<Job?>(null) }

                    // A freshly opened/re-entered tab always starts with the bar
                    // home; the spring back becomes part of the route transition.
                    LaunchedEffect(currentRoute) {
                        navHideAnim.animateTo(0f)
                    }

                    val navBarScrollHideConnection =
                        // disableAnimations is a key so toggling reduced motion takes effect
                        // on the next fling instead of being baked into the captured lambda.
                        remember(navHideAnim, navHideTravelPx, disableAnimations) {
                            object : NestedScrollConnection {
                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource,
                                ): Offset {
                                    // Only real user gestures move the bar: the drag
                                    // itself plus the fling that follows it, bracketed
                                    // by pre/post-fling so programmatic scrolls
                                    // (position restore, settings auto-scroll) cannot.
                                    val userDriven =
                                        source == NestedScrollSource.UserInput ||
                                            (navHideFlingWindow && source == NestedScrollSource.SideEffect)
                                    if (navBarHideOnScroll && userDriven && consumed.y != 0f) {
                                        // Scrolling down consumes negative y: map the
                                        // travel 1:1 so the bar tracks the finger.
                                        val next =
                                            (navHideAnim.value - consumed.y / navHideTravelPx)
                                                .coerceIn(0f, 1f)
                                        navHideDragJob?.cancel()
                                        // UNDISPATCHED: snapTo is applied inline in the
                                        // scroll callback instead of being queued to the
                                        // main dispatcher. Without this the bar tracked the
                                        // finger a frame or two late, which is half of why
                                        // the motion felt disconnected from the gesture.
                                        navHideDragJob =
                                            navHideScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                navHideAnim.snapTo(next)
                                            }
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity {
                                    navHideFlingWindow = true
                                    return Velocity.Zero
                                }

                                override suspend fun onPostFling(
                                    consumed: Velocity,
                                    available: Velocity,
                                ): Velocity {
                                    navHideFlingWindow = false
                                    // Settle with the fling: a decisive downward fling
                                    // hides, upward restores, timid ones snap to the
                                    // nearer end so the bar never rests half-way.
                                    val target =
                                        when {
                                            consumed.y < -navHideTravelPx -> 1f
                                            consumed.y > navHideTravelPx -> 0f
                                            else -> if (navHideAnim.value > 0.5f) 1f else 0f
                                        }
                                    navHideDragJob?.cancel()
                                    // Carry the fling's own speed into the settle, the way
                                    // the lyric lines already do. Without this a decisive
                                    // flick and a hesitant nudge played the exact same
                                    // animation from a standstill, which is the other half
                                    // of why the motion read as flat.
                                    //
                                    // The Animatable speaks in 0..1 progress, so the
                                    // velocity is clamped in px/s first (a hard fling can
                                    // report thousands) and then divided by the travel
                                    // distance to become progress-per-second. Scrolling
                                    // down consumes negative y and drives progress toward
                                    // 1, hence the sign flip.
                                    val velocityProgress =
                                        LunarMotion.clampVelocity(-consumed.y) / navHideTravelPx
                                    navHideDragJob =
                                        navHideScope.launch {
                                            navHideAnim.animateTo(
                                                targetValue = target,
                                                animationSpec =
                                                    if (disableAnimations) {
                                                        snap()
                                                    } else {
                                                        LunarMotion.scrollHide()
                                                    },
                                                initialVelocity = velocityProgress,
                                            )
                                        }
                                    return Velocity.Zero
                                }
                            }
                        }

                    fun getBottomNavPadding(): Dp =
                        if (shouldShowNavigationBar && !useRail) {
                            navVisibleHeight
                        } else {
                            0.dp
                        }

                    // The bar animates its own height on the navigation spring, and the
                    // hide progress is read off that animation: the bar and the mini
                    // player that drifts with it move on the same bounce.
                    val bottomNavigationBarHeight by animateDpAsState(
                        targetValue =
                            if (shouldShowNavigationBar && !useRail) {
                                navVisibleHeight
                            } else {
                                0.dp
                            },
                        animationSpec = if (disableAnimations) snap() else NavigationBarAnimationSpec,
                        label = "",
                    )

                    // Deferred 0..1 hide state for the active search bar / rail, read inside the
                    // bar's draw lambda and combined with the route hide fraction.
                    val activeSearchBarHideState =
                        animateFloatAsState(
                            targetValue = if (active || useRail) 1f else 0f,
                            animationSpec =
                                if (disableAnimations) {
                                    snap()
                                } else {
                                    tween(
                                        durationMillis = LunarMotion.DepthZoomPopExitMs,
                                        easing = LunarMotion.SpatialEnterEasing,
                                    )
                                },
                            label = "activeSearchBarHide",
                        )

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound =
                                bottomInset +
                                    (if (shouldShowNavigationBar && !useRail) floatingBarsBottomPadding else 0.dp) +
                                    getBottomNavPadding() +
                                    MiniPlayerBottomSpacing +
                                    MiniPlayerHeight,
                            expandedBound = maxHeight,
                        )
                    var homeOverflowMenuExpanded by rememberSaveable { mutableStateOf(false) }
                    val showHomeOverflowFab = false

                    LaunchedEffect(showHomeOverflowFab) {
                        if (!showHomeOverflowFab) {
                            homeOverflowMenuExpanded = false
                        }
                    }

                    val playerBackground by rememberEnumPreference(
                        key = PlayerBackgroundStyleKey,
                        defaultValue = PlayerBackgroundStyle.DEFAULT,
                    )
                    val settingsPlayerDesignStyle by rememberEnumPreference(
                        key = PlayerDesignStyleKey,
                        defaultValue = PlayerDesignStyle.V7,
                    )
                    val (playerStyleOverrides) = rememberPreference(PlayerDesignStyleOverridesKey, "")
                    val currentPlayerMediaMetadata by remember(playerConnection) {
                        playerConnection?.mediaMetadata
                            ?: MutableStateFlow<dev.citali.lunartune.models.MediaMetadata?>(null)
                    }.collectAsState()
                    val playerDesignStyle =
                        remember(settingsPlayerDesignStyle, playerStyleOverrides, currentPlayerMediaMetadata?.id) {
                            resolvePlayerDesignStyle(
                                currentPlayerMediaMetadata?.id,
                                playerStyleOverrides,
                                settingsPlayerDesignStyle,
                            )
                        }

                    val aodModeEnabled by remember(playerConnection) {
                        playerConnection?.aodModeEnabled ?: MutableStateFlow(false)
                    }.collectAsStateWithLifecycle()

                    val lyricsPageVisible by remember(playerConnection) {
                        playerConnection?.lyricsPageVisible ?: MutableStateFlow(false)
                    }.collectAsStateWithLifecycle()

                    LaunchedEffect(aodModeLaunchRequestCount, playerConnection) {
                        val launchRequestCount = aodModeLaunchRequestCount
                        if (launchRequestCount == 0) return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        if (!awaitRestorablePlayback(connection)) return@LaunchedEffect
                        if (!playerBottomSheetState.isExpandedOrExpanding) {
                            playerBottomSheetState.expandSoft()
                        }
                        connection.aodModeEnabled.value = true
                        if (aodModeLaunchRequestCount == launchRequestCount) {
                            aodModeLaunchRequestCount = 0
                        }
                    }

                    LaunchedEffect(aodModeEnabled) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        if (aodModeEnabled) {
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            @Suppress("DEPRECATION")
                            window.addFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                setShowWhenLocked(true)
                                setTurnScreenOn(true)
                            }
                        } else {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                            @Suppress("DEPRECATION")
                            window.clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                setShowWhenLocked(false)
                                setTurnScreenOn(false)
                            }
                        }
                    }

                    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
                        mutableStateOf(false)
                    }

                    var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableStateOf(-1) }

                    PlayerSheetSystemEffects(
                        playerBottomSheetState = playerBottomSheetState,
                        useDarkTheme = useDarkTheme,
                        playerBackground = playerBackground,
                        playerDesignStyle = playerDesignStyle,
                        aodModeEnabled = aodModeEnabled,
                        isYearInMusicScreen = isYearInMusicScreen,
                        lyricsPageVisible = lyricsPageVisible,
                        miniPlayerAnchorPersistenceEnabled = miniPlayerAnchorPersistenceEnabled,
                        onSetSystemBarAppearance = ::setSystemBarAppearance,
                        onSaveMiniPlayerAnchor = setSavedMiniPlayerAnchor,
                        onSetStatusBarsHidden = ::setStatusBarsHidden,
                    )

                    LaunchedEffect(isYearInMusicScreen, playerConnection) {
                        val connection = playerConnection ?: return@LaunchedEffect
                        val player = connection.player

                        if (isYearInMusicScreen) {
                            if (yearInMusicSavedPlayerAnchor == -1) {
                                yearInMusicSavedPlayerAnchor =
                                    when {
                                        playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                        playerBottomSheetState.isCollapsed -> COLLAPSED_ANCHOR
                                        playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                        else -> COLLAPSED_ANCHOR
                                    }
                            }

                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else if (yearInMusicSavedPlayerAnchor != -1) {
                            val anchorToRestore = yearInMusicSavedPlayerAnchor
                            yearInMusicSavedPlayerAnchor = -1

                            if (!awaitRestorablePlayback(connection)) {
                                playerBottomSheetState.dismiss()
                            } else {
                                when (anchorToRestore) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    }

                    val playerAwareWindowInsets =
                        remember(
                            windowsInsets,
                            useRail,
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                            navVisibleHeight,
                            floatingBarsBottomPadding,
                        ) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar && !useRail) {
                                bottom += getBottomNavPadding() + floatingBarsBottomPadding
                            }
                            if (!playerBottomSheetState.isDismissed) {
                                bottom += MiniPlayerHeight + MiniPlayerBottomSpacing
                            }
                            windowsInsets
                                .only(
                                    (
                                        if (useRail) {
                                            WindowInsetsSides.Right
                                        } else {
                                            WindowInsetsSides.Horizontal
                                        }
                                    ) + WindowInsetsSides.Top,
                                ).add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    val homeScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                currentRoute == Screens.Home.route &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )
                    val searchScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                currentRoute == Screens.Search.route &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )
                    val libraryScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                currentRoute == Screens.Library.route &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                !isAtPrimaryRoot &&
                                    rawNavRoute?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    !playerBottomSheetState.isExpandedOrExpanding
                            },
                        )

                    var homeScrollToTopTrigger by remember { mutableIntStateOf(0) }
                    var searchScrollToTopTrigger by remember { mutableIntStateOf(0) }

                    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
                        if (isSelected && isAtPrimaryRoot) {
                            when (screen) {
                                Screens.Search -> {
                                    openSearch()
                                    searchScrollToTopTrigger++
                                    coroutineScope.launch { searchScrollBehavior.state.resetHeightOffset() }
                                }

                                Screens.Home -> {
                                    homeScrollToTopTrigger++
                                    coroutineScope.launch { homeScrollBehavior.state.resetHeightOffset() }
                                }

                                else -> {
                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                }
                            }
                        } else {
                            selectedPrimaryTabRoute = screen.route
                            if (!isAtPrimaryRoot) {
                                if (!navController.popBackStack(Screens.Home.route, inclusive = false)) {
                                    navController.navigate(Screens.Home.route) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(currentRoute) {
                        if (!playerBottomSheetState.isExpanded) {
                            when (currentRoute) {
                                Screens.Home.route -> {
                                    homeScrollBehavior.state.resetHeightOffset()
                                }

                                Screens.Search.route -> {
                                    searchScrollBehavior.state.resetHeightOffset()
                                }

                                else -> {}
                            }
                        }
                    }

                    LaunchedEffect(navBackStackEntry, isAtPrimaryRoot) {
                        val destRoute = navBackStackEntry?.destination?.route

                        val isEnteringSubScreen =
                            !isAtPrimaryRoot &&
                                destRoute != null &&
                                destRoute.startsWith(OnlineSearchResultRoutePrefix) != true
                        if (isEnteringSubScreen || isAtPrimaryRoot) {
                            topAppBarScrollBehavior.state.heightOffset = 0f
                            topAppBarScrollBehavior.state.contentOffset = 0f
                        }

                        if (!isAtPrimaryRoot) {
                            topBarMenuExpanded = false
                        }

                        if ((
                                destRoute?.startsWith("artist/") == true ||
                                    destRoute?.startsWith("album/") == true
                            ) &&
                            playerBottomSheetState.isExpanded
                        ) {
                            playerBottomSheetState.collapseSoft()
                        }

                        if (destRoute?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                            val searchQuery =
                                decodeOnlineSearchQuery(
                                    navBackStackEntry
                                        ?.arguments
                                        ?.getString(OnlineSearchResultArgument)
                                        .orEmpty(),
                                )
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length),
                                ),
                            )
                        } else if (isAtPrimaryRoot) {
                            onQueryChange(TextFieldValue())
                        }
                    }
                    LaunchedEffect(active) {
                        if (active) {
                            when (currentRoute) {
                                Screens.Home.route -> {
                                    homeScrollBehavior.state.resetHeightOffset()
                                }

                                Screens.Search.route -> {
                                    searchScrollBehavior.state.resetHeightOffset()
                                }

                                else -> {}
                            }
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(isTvDevice, useRail, active, currentRoute, shouldShowNavigationBar) {
                        if (
                            isTvDevice &&
                            useRail &&
                            shouldShowNavigationBar &&
                            !active &&
                            currentRoute in topLevelScreens
                        ) {
                            delay(100)
                            tvRailFocusRequester.requestFocus()
                        }
                    }

                    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

                    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusicScreen) {
                        if (restoredMiniPlayerAnchor) return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        connection.queueRestoreCompleted.first { it }
                        if (!awaitRestorablePlayback(connection)) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (!isYearInMusicScreen) {
                                when (savedMiniPlayerAnchor) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        restoredMiniPlayerAnchor = true
                        miniPlayerAnchorPersistenceEnabled = true
                    }

                    val currentPlayerBottomSheetState = rememberUpdatedState(playerBottomSheetState)
                    val currentIsYearInMusicScreen = rememberUpdatedState(isYearInMusicScreen)

                    DisposableEffect(playerConnection) {
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                private fun collapseDismissedMiniPlayerForActivePlayback() {
                                    if (
                                        player.mediaItemCount > 0 &&
                                        player.currentMediaItem != null &&
                                        player.playWhenReady &&
                                        player.playbackState != Player.STATE_IDLE &&
                                        player.playbackState != Player.STATE_ENDED &&
                                        currentPlayerBottomSheetState.value.isDismissed &&
                                        !currentIsYearInMusicScreen.value
                                    ) {
                                        currentPlayerBottomSheetState.value.collapseSoft()
                                    }
                                }

                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onTimelineChanged(
                                    timeline: Timeline,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlayWhenReadyChanged(
                                    playWhenReady: Boolean,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    val shouldShowTopBar = !active && isAtPrimaryRoot
                    val shouldRenderPrimaryTopBar =
                        !active && (isAtPrimaryRoot || primaryRootDepthState.isActiveInHierarchy)

                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleIntent(pendingIntent, navController)
                            pendingIntent = null
                        } else {
                            handleIntent(intent, navController)
                        }
                    }
                    val haptic = LocalHapticFeedback.current
                    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                    val customHaptic =
                        remember(haptic, enableHapticFeedback) {
                            object : HapticFeedback {
                                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                    if (enableHapticFeedback) {
                                        haptic.performHapticFeedback(hapticFeedbackType)
                                    }
                                }
                            }
                        }

                    CompositionLocalProvider(
                        LocalHapticFeedback provides customHaptic,
                        LocalAnimationsDisabled provides disableAnimations,
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        dev.citali.lunartune.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                        dev.citali.lunartune.ui.component.LocalMenuState provides menuState,
                    ) {
                        Row {
                            AnimatedVisibility(
                                visible =
                                    useRail &&
                                        shouldShowNavigationBar &&
                                        (isTvDevice || !playerBottomSheetState.isExpandedOrExpanding),
                                enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
                                exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
                            ) {
                                if (isTvDevice) {
                                    TvNavigationRail(
                                        items = navigationItems,
                                        selectedItemRoute =
                                            if (active) {
                                                Screens.Search.route
                                            } else {
                                                currentRoute
                                            },
                                        modifier = Modifier,
                                        firstItemFocusRequester = tvRailFocusRequester,
                                        contentFocusRequester =
                                            if (active ||
                                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                            ) {
                                                searchBarFocusRequester
                                            } else {
                                                contentAreaFocusRequester
                                            },
                                        onItemClick = { screen ->
                                            val wasPlayerActive = playerBottomSheetState.isExpanded
                                            if (wasPlayerActive) {
                                                playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                            }
                                            val isSelected = isAtPrimaryRoot && selectedPrimaryTab.route == screen.route
                                            if (wasPlayerActive && isSelected) {
                                                return@TvNavigationRail
                                            }
                                            handlePrimaryNavigationClick(screen, isSelected)
                                        },
                                    )
                                } else {
                                    NavigationRail(
                                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        header = { Spacer(Modifier.height(24.dp)) },
                                    ) {
                                        navigationItems.fastForEach { screen ->
                                            val isSelected = isAtPrimaryRoot && selectedPrimaryTab.route == screen.route

                                            NavigationRailItem(
                                                selected = isSelected,
                                                icon = {
                                                    Icon(
                                                        painter =
                                                            painterResource(
                                                                id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                            ),
                                                        contentDescription = null,
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                onClick = {
                                                    val wasPlayerActive = playerBottomSheetState.isExpanded

                                                    if (wasPlayerActive) {
                                                        playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                                    }

                                                    if (wasPlayerActive && isSelected) return@NavigationRailItem
                                                    handlePrimaryNavigationClick(screen, isSelected)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Scaffold(
                                topBar = {
                                    if (shouldRenderPrimaryTopBar) {
                                        val shouldUseFloatingTopBar = true
                                        val shouldShowBlurBackground = true

                                        val surfaceColor = MaterialTheme.colorScheme.surface
                                        val currentScrollBehavior =
                                            when (selectedPrimaryTab) {
                                                Screens.Home -> homeScrollBehavior
                                                Screens.Search -> searchScrollBehavior
                                                Screens.Library -> libraryScrollBehavior
                                                else -> topAppBarScrollBehavior
                                            }
                                        var headerHeightPx by remember { mutableStateOf(0) }
                                        LaunchedEffect(currentScrollBehavior, headerHeightPx) {
                                            if (headerHeightPx > 0) {
                                                val limit = -headerHeightPx.toFloat()
                                                // Drill-in screens always use topAppBarScrollBehavior,
                                                // even when Library owns the primary bar, so both
                                                // states need the measured collapse limit.
                                                listOf(currentScrollBehavior, topAppBarScrollBehavior)
                                                    .distinct()
                                                    .forEach { behavior ->
                                                        val state = behavior.state
                                                        if (state.heightOffsetLimit != limit) {
                                                            state.heightOffsetLimit = limit
                                                            state.heightOffset =
                                                                state.heightOffset.coerceIn(limit, 0f)
                                                        }
                                                    }
                                            }
                                        }

                                        Box(
                                            modifier =
                                                Modifier
                                                    .onSizeChanged { size ->
                                                        if (size.height > 0) headerHeightPx = size.height
                                                    }.offset {
                                                        val scrollOffset =
                                                            currentScrollBehavior.state.heightOffset.roundToInt()
                                                        IntOffset(x = 0, y = scrollOffset)
                                                    }.graphicsLayer {
                                                        val p = primaryRootDepthState.presence.coerceIn(0f, 1f)
                                                        val scale =
                                                            LunarMotion.DepthRecedeTargetScale +
                                                                (1f - LunarMotion.DepthRecedeTargetScale) * p
                                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                                        scaleX = scale
                                                        scaleY = scale
                                                        alpha = p * p
                                                    },
                                        ) {
                                            // Gradient shadow background. It lives inside the
                                            // translating Box (which moves by the full heightOffset,
                                            // so the TopAppBar fully hides), but a counter-offset
                                            // clamps the gradient's net translation to [-appBarHeight, 0]
                                            // so it parks with its top band over the status bar instead
                                            // of sliding fully off â€” a legibility scrim once the header
                                            // is hidden.
                                            if (shouldShowBlurBackground) {
                                                val appBarHeightPx = with(LocalDensity.current) { AppBarHeight.toPx() }
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .offset {
                                                                val raw = currentScrollBehavior.state.heightOffset
                                                                val clamped = raw.coerceAtLeast(-appBarHeightPx)
                                                                IntOffset(x = 0, y = (clamped - raw).roundToInt())
                                                            }.fillMaxWidth()
                                                            .height(
                                                                AppBarHeight +
                                                                    with(LocalDensity.current) {
                                                                        windowsInsets.getTop(LocalDensity.current).toDp()
                                                                    },
                                                            ).background(
                                                                Brush.verticalGradient(
                                                                    colors =
                                                                        listOf(
                                                                            surfaceColor.copy(alpha = 0.95f),
                                                                            surfaceColor.copy(alpha = 0.85f),
                                                                            surfaceColor.copy(alpha = 0.6f),
                                                                            Color.Transparent,
                                                                        ),
                                                                ),
                                                            ),
                                                )
                                            }

                                            val titleShadowBlurPx = with(LocalDensity.current) { 8.dp.toPx() }
                                            // scrim is black in both themes, so derive the shadow
                                            // polarity from the text itself: dark glyphs get a
                                            // light shadow, light glyphs a dark one.
                                            val titleShadowColor =
                                                if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) {
                                                    Color.Black
                                                } else {
                                                    Color.White
                                                }
                                            TopAppBar(
                                                windowInsets =
                                                    windowsInsets.only(
                                                        (
                                                            if (useRail) {
                                                                WindowInsetsSides.Right
                                                            } else {
                                                                WindowInsetsSides.Horizontal
                                                            }
                                                        ) + WindowInsetsSides.Top,
                                                    ),
                                                title = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        // app icon
                                                        Icon(
                                                            painter = painterResource(R.drawable.about_appbar),
                                                            contentDescription = null,
                                                            modifier =
                                                                Modifier
                                                                    .size(35.dp)
                                                                    .padding(end = 3.dp),
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.app_name),
                                                            style =
                                                                MaterialTheme.typography.titleLarge.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    shadow =
                                                                        if (selectedPrimaryTab == Screens.Home) {
                                                                            Shadow(
                                                                                color = titleShadowColor.copy(alpha = 0.72f),
                                                                                blurRadius = titleShadowBlurPx,
                                                                            )
                                                                        } else {
                                                                            null
                                                                        },
                                                                ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    val updateAvailable =
                                                        BuildConfig.UPDATER_AVAILABLE &&
                                                            latestUpdateChannel == updateChannel &&
                                                            Updater.isUpdateAvailable(
                                                                latestVersionName,
                                                                BuildConfig.VERSION_NAME,
                                                            )
                                                    FrostedTopBarMenu(
                                                        hazeState = hazeState,
                                                        expanded = topBarMenuExpanded,
                                                        onExpandedChange = { topBarMenuExpanded = it },
                                                        updateAvailable = updateAvailable,
                                                        onNavigate = { route -> navController.navigate(route) },
                                                    )
                                                },
                                                scrollBehavior = null,
                                                colors =
                                                    TopAppBarDefaults.topAppBarColors(
                                                        containerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        scrolledContainerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    ),
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible =
                                            active ||
                                                rawNavRoute?.startsWith(OnlineSearchResultRoutePrefix) == true,
                                        enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 220)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 160)),
                                    ) {
                                        TopSearch(
                                            query = query,
                                            onQueryChange = onQueryChange,
                                            onSearch = onSearch,
                                            active = active,
                                            onActiveChange = onActiveChange,
                                            placeholder = {
                                                Text(
                                                    text =
                                                        stringResource(
                                                            when (searchSource) {
                                                                SearchSource.LOCAL -> R.string.search_library
                                                                SearchSource.ONLINE -> R.string.search_yt_music
                                                            },
                                                        ),
                                                )
                                            },
                                            leadingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        when {
                                                            active -> {
                                                                onActiveChange(false)
                                                            }

                                                            !isAtPrimaryRoot -> {
                                                                navController.navigateUp()
                                                            }

                                                            else -> {
                                                                onActiveChange(true)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        when {
                                                            active -> {}

                                                            !isAtPrimaryRoot -> {
                                                                navController.backToMain()
                                                            }

                                                            else -> {}
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        painterResource(
                                                            if (active || !isAtPrimaryRoot) {
                                                                R.drawable.arrow_back
                                                            } else {
                                                                R.drawable.search
                                                            },
                                                        ),
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                Row {
                                                    if (active) {
                                                        if (query.text.isNotEmpty()) {
                                                            IconButton(
                                                                onClick = {
                                                                    onQueryChange(
                                                                        TextFieldValue(
                                                                            "",
                                                                        ),
                                                                    )
                                                                },
                                                            ) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.close),
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                searchSource =
                                                                    if (searchSource ==
                                                                        SearchSource.ONLINE
                                                                    ) {
                                                                        SearchSource.LOCAL
                                                                    } else {
                                                                        SearchSource.ONLINE
                                                                    }
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter =
                                                                    painterResource(
                                                                        when (searchSource) {
                                                                            SearchSource.LOCAL -> R.drawable.library_music
                                                                            SearchSource.ONLINE -> R.drawable.language
                                                                        },
                                                                    ),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    } else if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                                                        OnlineSearchSortMenu(
                                                            selectedSort = onlineSearchSort,
                                                            onSortSelected = { onlineSearchSort = it },
                                                        )
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .focusRequester(searchBarFocusRequester)
                                                    .let { with(this@BoxWithConstraints) { it.align(Alignment.TopCenter) } },
                                            focusRequester = searchBarFocusRequester,
                                            leftFocusRequester = tvRailFocusRequester,
                                            colors =
                                                if (pureBlack && active) {
                                                    SearchBarDefaults.colors(
                                                        containerColor = Color.Black,
                                                        dividerColor = Color.DarkGray,
                                                        inputFieldColors =
                                                            TextFieldDefaults.colors(
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.Gray,
                                                                focusedContainerColor = Color.Transparent,
                                                                unfocusedContainerColor = Color.Transparent,
                                                                cursorColor = Color.White,
                                                                focusedIndicatorColor = Color.Transparent,
                                                                unfocusedIndicatorColor = Color.Transparent,
                                                            ),
                                                    )
                                                } else {
                                                    SearchBarDefaults.colors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    )
                                                },
                                        ) {
                                            Crossfade(
                                                targetState = searchSource,
                                                animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300),
                                                label = "",
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(
                                                            bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp,
                                                        ).navigationBarsPadding(),
                                            ) { searchSource ->
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> {
                                                        LocalSearchScreen(
                                                            query = query.text,
                                                            navController = navController,
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }

                                                    SearchSource.ONLINE -> {
                                                        OnlineSearchScreen(
                                                            query = query.text,
                                                            onQueryChange = onQueryChange,
                                                            navController = navController,
                                                            onSearch = {
                                                                navController.navigate(onlineSearchResultRoute(it))
                                                                if (!pauseSearchHistory) {
                                                                    database.query {
                                                                        insert(SearchHistory(query = it))
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                bottomBar = {
                                    Box {
                                        val areBottomBarsPaired =
                                            shouldShowNavigationBar &&
                                                !useRail &&
                                                !isFloatingNavBar &&
                                                !playerBottomSheetState.isDismissed

                                        // Routes that hide the bar animate its height away on
                                        // the navigation spring; the hide progress is read back
                                        // off that animation, so the bar and the mini player
                                        // that leans on it share one bounce.
                                        val routeHideFraction =
                                            1f -
                                                bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                                    navVisibleHeight

                                        // captured here: the drift lambda below runs
                                        // outside composition, where LocalDensity is
                                        // not readable
                                        val navDriftDensity = LocalDensity.current

                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                            isMiniPlayerPairedWithNavigation = areBottomBarsPaired,
                                            hazeState = hazeState,
                                            navbarHiddenOffset = {
                                                // While the bar is away the collapsed mini
                                                // player drifts down into its footprint on
                                                // the same progress; idle there is no row.
                                                if (
                                                    shouldShowNavigationBar && !useRail &&
                                                    !playerBottomSheetState.isDismissed
                                                ) {
                                                    with(navDriftDensity) {
                                                        // Exactly the value the bar hides on — the same
                                                        // inputs, in the same order. The row and
                                                        // the bar are one motion: when the bar started
                                                        // leaving on the search-bar animation while the
                                                        // row only followed the slower route spring,
                                                        // they drifted apart for half a second and the
                                                        // row was left hanging over the bar's slot.
                                                        // The sheet's own progress belongs here too: while
                                                        // the player closes, the bar's slot travels up on
                                                        // that progress (layout, one Box above), so the
                                                        // row has to travel with it. Without this term the
                                                        // row arrived first and sat waiting for the bar
                                                        // to catch up. It is 0 whenever the sheet rests,
                                                        // so nothing else about the drift changes.
                                                        val hide =
                                                            maxOf(
                                                                routeHideFraction,
                                                                activeSearchBarHideState.value,
                                                                navHideAnim.value,
                                                                playerBottomSheetState.progress,
                                                            ).coerceIn(0f, 1f)
                                                        (floatingBarsBottomPadding + navVisibleHeight).toPx() * hide
                                                    }
                                                } else {
                                                    0f
                                                }
                                            },
                                        )

                                        if (useRail) return@Box

                                        val navSlideDistance =
                                            bottomInset + floatingBarsBottomPadding + navVisibleHeight

                                        Box(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .height(navSlideDistance)
                                                    .offset {
                                                        // Only the player expansion slides the
                                                        // slot in layout; the hide progress lives
                                                        // in the graphics layer below, so hiding
                                                        // never re-lays out anything.
                                                        val slideOffset =
                                                            navSlideDistance *
                                                                playerBottomSheetState.progress.coerceIn(
                                                                    0f,
                                                                    1f,
                                                                )
                                                        IntOffset(x = 0, y = slideOffset.roundToPx())
                                                    }.graphicsLayer {
                                                        // The hide motion: translate out and fade a
                                                        // little ahead of arrival so no edge of
                                                        // plate, shadow or frost lingers.
                                                        val routeHide =
                                                            maxOf(
                                                                routeHideFraction,
                                                                activeSearchBarHideState.value,
                                                            )
                                                        val hide =
                                                            maxOf(routeHide, navHideAnim.value)
                                                                .coerceIn(0f, 1f)
                                                        // The whole hide motion: translate out and
                                                        // fade a little ahead of arrival so no
                                                        // edge of plate, shadow or frost lingers.
                                                        translationY = navSlideDistance.toPx() * 1.15f * hide
                                                        alpha = (1f - hide * 1.6f).coerceIn(0f, 1f)
                                                    },
                                        ) {
                                            FloatingNavigationToolbar(
                                                items = navigationItems,
                                                pureBlack = pureBlack,
                                                isPairedWithMiniPlayer = areBottomBarsPaired,
                                                style = navigationBarStyle,
                                                hazeState = hazeState,
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(
                                                            start = navBarHorizontalPadding,
                                                            end = navBarHorizontalPadding,
                                                            bottom = bottomInset + floatingBarsBottomPadding,
                                                        ).height(navVisibleHeight),
                                                isSelected = { screen ->
                                                    isAtPrimaryRoot && selectedPrimaryTab.route == screen.route
                                                },
                                                onItemClick = { screen, isSelected ->
                                                    handlePrimaryNavigationClick(screen, isSelected)
                                                },
                                                onSearchItemDoubleClick = {
                                                    searchSource = SearchSource.ONLINE
                                                    openSearch()
                                                },
                                                liveBlur = capsuleLiveBlur,
                                            )
                                        }

                                        val homeOverflowFabBottomPadding =
                                            bottomInset +
                                                floatingBarsBottomPadding +
                                                navVisibleHeight +
                                                HomeOverflowFabSpacing +
                                                if (!playerBottomSheetState.isDismissed) {
                                                    MiniPlayerHeight + MiniPlayerBottomSpacing
                                                } else {
                                                    0.dp
                                                }
                                        HomeOverflowFabVisibility(
                                            visible = showHomeOverflowFab,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(
                                                        end = NavigationBarHorizontalPadding,
                                                        bottom = homeOverflowFabBottomPadding,
                                                    ),
                                        ) {
                                            HomeOverflowFab(
                                                expanded = homeOverflowMenuExpanded,
                                                pureBlack = pureBlack,
                                                onExpandedChange = { homeOverflowMenuExpanded = it },
                                                onShuffleClick = {
                                                    val useLocalSource =
                                                        when {
                                                            allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> {
                                                                Random.nextFloat() < 0.5f
                                                            }

                                                            allLocalItems.isNotEmpty() -> {
                                                                true
                                                            }

                                                            else -> {
                                                                false
                                                            }
                                                        }

                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        if (useLocalSource) {
                                                            when (val luckyItem = allLocalItems.random()) {
                                                                is Song -> {
                                                                    playerConnection?.playQueue(
                                                                        if (luckyItem.song.isLocal) {
                                                                            ListQueue(items = listOf(luckyItem.toMediaItem()))
                                                                        } else {
                                                                            YouTubeQueue.radio(luckyItem.toMediaMetadata())
                                                                        },
                                                                    )
                                                                }

                                                                is Album -> {
                                                                    val albumWithSongs =
                                                                        withContext(Dispatchers.IO) {
                                                                            database.albumWithSongs(luckyItem.id).first()
                                                                        }

                                                                    albumWithSongs?.let {
                                                                        playerConnection?.playQueue(LocalAlbumRadio(it))
                                                                    }
                                                                }

                                                                is Artist -> {
                                                                    Unit
                                                                }

                                                                is Playlist -> {
                                                                    Unit
                                                                }
                                                            }
                                                        } else {
                                                            when (val luckyItem = allYtItems.random()) {
                                                                is SongItem -> {
                                                                    playerConnection?.playQueue(
                                                                        YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                                                    )
                                                                }

                                                                is AlbumItem -> {
                                                                    playerConnection?.playQueue(
                                                                        YouTubeAlbumRadio(luckyItem.playlistId),
                                                                    )
                                                                }

                                                                is ArtistItem -> {
                                                                    luckyItem.radioEndpoint?.let {
                                                                        playerConnection?.playQueue(YouTubeQueue(it))
                                                                    }
                                                                }

                                                                is PlaylistItem -> {
                                                                    luckyItem.playEndpoint?.let {
                                                                        playerConnection?.playQueue(YouTubeQueue.playlist(it))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onMusicRecognitionClick = {
                                                    navController.navigate(MusicRecognitionRoute)
                                                },
                                                onMusicTogetherClick = {
                                                    navController.navigate("settings/music_together")
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                PrimaryTabRootBackHandler(
                                    isAtPrimaryRoot = isAtPrimaryRoot,
                                    active = active,
                                    playerBottomSheetState = playerBottomSheetState,
                                    selectedPrimaryTabRoute = selectedPrimaryTab.route,
                                    initialPrimaryTabRoute = initialPrimaryTab.route,
                                    onReturnToInitialTab = { selectedPrimaryTabRoute = initialPrimaryTab.route },
                                )

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .drawWithContent {
                                                if (!playerBottomSheetState.isExpanded || aodModeEnabled) {
                                                    drawContent()
                                                }
                                            }.then(
                                                if (isTvDevice) {
                                                    Modifier
                                                        .focusRequester(contentAreaFocusRequester)
                                                        .focusGroup()
                                                        .focusable()
                                                } else {
                                                    Modifier
                                                },
                                            ).then(
                                                // Beta scroll-driven bar hide: registered on the
                                                // shared content host so both warm primary tabs and
                                                // sub-screens drive it seamlessly.
                                                if (navBarHideOnScroll) {
                                                    Modifier.nestedScroll(navBarScrollHideConnection)
                                                } else {
                                                    Modifier
                                                },
                                            ).then(
                                                // Frosted glass source: captures both warm primary
                                                // tabs and NavHost drill-in pages in one pass.
                                                if (
                                                    topBarMenuExpanded ||
                                                    (
                                                        (isFrostedNavBar || isMiniPlayerFrosted) &&
                                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                                    )
                                                ) {
                                                    Modifier.hazeSource(hazeState)
                                                } else {
                                                    Modifier
                                                },
                                            ).then(
                                                if (capsuleLiveBlurSource != null) {
                                                    Modifier.navBarLiveBlurSource(capsuleLiveBlurSource)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    PrimaryTabsHost(
                                        tabs = navigationItems,
                                        selectedTab = selectedPrimaryTab,
                                        isAtPrimaryRoot = isAtPrimaryRoot,
                                        depthState = primaryRootDepthState,
                                        pureBlack = pureBlack,
                                        disableAnimations = disableAnimations,
                                        navController = navController,
                                        homeViewModel = homeViewModel,
                                        scrollBehavior = libraryScrollBehavior,
                                        homeScrollConnection = homeScrollBehavior.nestedScrollConnection,
                                        searchScrollConnection = searchScrollBehavior.nestedScrollConnection,
                                        onOpenSearch = openSearch,
                                        homeScrollToTopTrigger = homeScrollToTopTrigger,
                                        searchScrollToTopTrigger = searchScrollToTopTrigger,
                                    )

                                    NavHost(
                                        navController = navController,
                                        startDestination = Screens.Home.route,
                                        enterTransition = {
                                            when {
                                                disableAnimations -> fadeIn(tween(0))
                                                targetState.destination.route == Screens.Home.route ->
                                                    LunarMotion.depthRootHoldPopEnter()
                                                else -> LunarMotion.depthPageEnter()
                                            }
                                        },
                                        exitTransition = {
                                            when {
                                                disableAnimations -> fadeOut(tween(0))
                                                initialState.destination.route == Screens.Home.route ->
                                                    LunarMotion.depthRootHoldExit()
                                                else -> LunarMotion.depthPageExit()
                                            }
                                        },
                                        popEnterTransition = {
                                            when {
                                                disableAnimations -> fadeIn(tween(0))
                                                targetState.destination.route == Screens.Home.route ->
                                                    LunarMotion.depthRootHoldPopEnter()
                                                else -> LunarMotion.depthPagePopEnter()
                                            }
                                        },
                                        popExitTransition = {
                                            when {
                                                disableAnimations -> fadeOut(tween(0))
                                                else -> LunarMotion.depthPagePopExit()
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                                    ) {
                                        navigationBuilder(
                                            navController = navController,
                                            scrollBehavior = topAppBarScrollBehavior,
                                            latestVersionName = { latestVersionName },
                                            primaryRootDepthState = primaryRootDepthState,
                                            onSelectPrimaryTab = { screen ->
                                                selectedPrimaryTabRoute = screen.route
                                            },
                                            pureBlack = pureBlack,
                                            disableAnimations = disableAnimations,
                                            onClearUpdateBadge = { latestVersionName = BuildConfig.VERSION_NAME },
                                            onlineSearchSort = onlineSearchSort,
                                        )
                                    }
                                }
                            }
                        }

                        PlayerExpandedBackHandler(
                            playerBottomSheetState = playerBottomSheetState,
                            aodModeEnabled = aodModeEnabled,
                        )

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val bannerTopInset =
                            with(LocalDensity.current) {
                                windowsInsets.getTop(LocalDensity.current).toDp()
                            }
                        NetworkStatusBanner(
                            state = networkBannerState,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(
                                        top =
                                            if (shouldShowTopBar) {
                                                bannerTopInset + AppBarHeight + 8.dp
                                            } else {
                                                bannerTopInset + 8.dp
                                            },
                                        start = 16.dp,
                                        end = 16.dp,
                                    ).zIndex(10f),
                        )
                    }

                    pendingBackupRestoreUri?.let { uri ->
                        BackupRestoreFromIntentDialog(uri = uri)
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            openSearchImmediately = false
                        }
                    }

                    val openSearchFromRoute =
                        navBackStackEntry
                            ?.savedStateHandle
                            ?.getStateFlow("openSearch", false)
                            ?.collectAsStateWithLifecycle()

                    LaunchedEffect(openSearchFromRoute?.value) {
                        if (openSearchFromRoute?.value == true) {
                            navBackStackEntry?.savedStateHandle?.set("openSearch", false)
                            openSearch()
                        }
                    }

                }
            }
        }
    }

    private fun isBackupUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.lastPathSegment?.lowercase(Locale.US)
        return path?.endsWith(".backup") == true || uri.toString().lowercase(Locale.US).endsWith(".backup")
    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        if (intent == null) return
        intent.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
            intent.removeExtra("navigate_to")
            return
        }
        val dataUri = intent.data
        if (isBackupUri(dataUri)) {
            pendingBackupRestoreUri = dataUri
            return
        }
        if (intent.action == ACTION_MUSIC_RECOGNITION) {
            navController.openMusicRecognition(0L)
            return
        }
        if (intent.action == ACTION_AOD_MODE) {
            requestAodMode()
            return
        }
        if (intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query =
                (
                    intent.getStringExtra("query")
                        ?: intent.getStringExtra("android.intent.extra.TITLE")
                        ?: ""
                ).trim()
            if (query.isNotBlank()) {
                pendingVoiceSearchQuery = query
                startMusicServiceSafely()
                playPendingVoiceSearchIfReady()
            }
            return
        }
        if (handleExternalAudioIntent(intent)) {
            return
        }
        handleDeepLinkIntent(intent, navController)
    }

    private fun handleExternalAudioIntent(intent: Intent): Boolean {
        val incomingUris =
            buildList {
                intent.data?.let(::add)
                when (intent.action) {
                    Intent.ACTION_SEND -> {
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        addAll(
                            IntentCompat
                                .getParcelableArrayListExtra(
                                    intent,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                ).orEmpty(),
                        )
                    }
                }
            }.distinct()

        if (incomingUris.isEmpty()) return false

        val fallbackMimeType = intent.type
        val playableUris =
            incomingUris.filter { uri ->
                val mimeType = contentResolver.getType(uri)
                mimeType.isAudioMimeType() || fallbackMimeType.isAudioMimeType() || uri.hasAudioExtension()
            }
        if (playableUris.isEmpty()) return false

        pendingDeepLinkQueue = ListQueue(items = playableUris.map(::toExternalAudioMediaItem))
        startMusicServiceSafely()
        playPendingDeepLinkQueueIfReady()
        return true
    }

    private fun toExternalAudioMediaItem(uri: Uri): MediaItem {
        val mediaId = uri.toString()
        val title = resolveExternalAudioTitle(uri)
        val metadata =
            dev.citali.lunartune.models.MediaMetadata(
                id = mediaId,
                title = title,
                artists = emptyList(),
                duration = -1,
            )
        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setTag(metadata)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()
    }

    private fun resolveExternalAudioTitle(uri: Uri): String {
        val displayName =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
                }
            }.getOrNull()
        return displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown)
    }

    private fun String?.isAudioMimeType(): Boolean = this?.startsWith("audio/", ignoreCase = true) == true

    private fun Uri.hasAudioExtension(): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).orEmpty()
        val normalized = extension.lowercase(Locale.US)
        return normalized in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        val authority = uri.authority?.lowercase()
        if (uri.scheme.equals("lunartune", ignoreCase = true) && authority == "together") {
            pendingTogetherJoinLink = uri.toString()
            startMusicServiceSafely()
            joinPendingTogetherIfReady()
            return
        }

        if (uri.scheme.equals("lunartune", ignoreCase = true) && authority == "login") {
            navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                uri.getQueryParameter("list")?.let { playlistId ->
                    if (playlistId.startsWith("OLAK5uy_")) {
                        coroutineScope.launch {
                            YouTube
                                .albumSongs(playlistId)
                                .onSuccess { songs ->
                                    songs.firstOrNull()?.album?.id?.let { browseId ->
                                        navController.navigate("album/$browseId")
                                    }
                                }.onFailure { reportException(it) }
                        }
                    } else {
                        navController.navigate("online_playlist/$playlistId")
                    }
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigate("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigate("artist/$artistId")
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }
                val playlistId = uri.getQueryParameter("list")
                val shouldShufflePlaylist = uri.requestsShuffledPlayback()

                videoId?.let { vid ->
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.queue(listOf(vid), playlistId)
                            }

                        result
                            .onSuccess { queued ->
                                val mediaItem =
                                    queued.firstOrNull { it.id == vid }?.toMediaItem()
                                        ?: queued.firstOrNull()?.toMediaItem()
                                        ?: MediaItem
                                            .Builder()
                                            .setMediaId(vid)
                                            .setUri(vid)
                                            .setCustomCacheKey(vid)
                                            .build()
                                pendingDeepLinkQueue = ListQueue(items = listOf(mediaItem))
                                startMusicServiceSafely()
                                playPendingDeepLinkQueueIfReady()
                            }.onFailure {
                                reportException(it)
                            }
                    }
                    return
                }

                if (path == "watch" && !playlistId.isNullOrBlank()) {
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.playlist(playlistId)
                            }

                        result
                            .onSuccess { playlistPage ->
                                val endpoint =
                                    when {
                                        shouldShufflePlaylist -> {
                                            playlistPage.playlist.shuffleEndpoint
                                                ?: playlistPage.playlist.playEndpoint
                                        }

                                        else -> {
                                            playlistPage.playlist.playEndpoint
                                                ?: playlistPage.playlist.shuffleEndpoint
                                        }
                                    }

                                endpoint?.let {
                                    pendingDeepLinkQueue = YouTubeQueue.playlist(it)
                                    startMusicServiceSafely()
                                    playPendingDeepLinkQueueIfReady()
                                } ?: navController.navigate("online_playlist/$playlistId")
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    private fun android.net.Uri.requestsShuffledPlayback(): Boolean {
        val value = getQueryParameter("shuffle")?.trim()?.lowercase(Locale.US) ?: return false
        return value == "1" || value == "true"
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, dev.citali.lunartune.playback.MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    private fun setStatusBarsHidden(hidden: Boolean) {
        // No "already in this state" shortcut: the platform can bring the status
        // bar back on its own (leaving AOD, a popup closing, a resumed window),
        // and the request used to be dropped because the cached flag still said
        // "hidden" — which is exactly how the bar stayed on screen for a style
        // that asks for it gone. Re-applying is a cheap, idempotent call.
        immersiveStatusBarsHidden = hidden
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (hidden) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Composable
    private fun PlayerSheetSystemEffects(
        playerBottomSheetState: BottomSheetState,
        useDarkTheme: Boolean,
        playerBackground: PlayerBackgroundStyle,
        playerDesignStyle: PlayerDesignStyle,
        aodModeEnabled: Boolean,
        isYearInMusicScreen: Boolean,
        lyricsPageVisible: Boolean,
        miniPlayerAnchorPersistenceEnabled: Boolean,
        onSetSystemBarAppearance: (Boolean) -> Unit,
        onSaveMiniPlayerAnchor: (Int) -> Unit,
        onSetStatusBarsHidden: (Boolean) -> Unit,
    ) {
        LaunchedEffect(useDarkTheme, playerBottomSheetState.isExpanded, playerBackground, aodModeEnabled) {
            if (aodModeEnabled) return@LaunchedEffect
            val isDarkStatusBar =
                if (playerBottomSheetState.isExpanded &&
                    playerBackground != PlayerBackgroundStyle.DEFAULT
                ) {
                    true
                } else {
                    useDarkTheme
                }
            onSetSystemBarAppearance(isDarkStatusBar)
        }

        val miniPlayerAnchor by remember(playerBottomSheetState) {
            derivedStateOf {
                when {
                    playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                    playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                    else -> COLLAPSED_ANCHOR
                }
            }
        }

        LaunchedEffect(miniPlayerAnchor, isYearInMusicScreen, miniPlayerAnchorPersistenceEnabled) {
            if (!isYearInMusicScreen && miniPlayerAnchorPersistenceEnabled) {
                onSaveMiniPlayerAnchor(miniPlayerAnchor)
            }
        }

        // `isExpanded` only flips once the sheet has physically reached the top, so
        // the immersive styles used to keep the status bar for the entire opening
        // spring. The target anchor flips the instant expansion starts (tap, fling
        // or AOD launch) and stays set until a collapse begins, which is the same
        // point at which the status bar is allowed to come back. Every immersive
        // style that owns the whole screen gets the bar taken away, and so does the
        // full-screen lyrics page: it is its own surface with its own close
        // affordance, so the bar only ever sat on top of it.
        val shouldHideStatusBars =
            isYearInMusicScreen ||
                (
                    playerBottomSheetState.isExpandedOrExpanding &&
                        playerDesignStyle.hidesStatusBar
                ) ||
                lyricsPageVisible

        LaunchedEffect(shouldHideStatusBars, aodModeEnabled) {
            if (aodModeEnabled) return@LaunchedEffect
            onSetStatusBarsHidden(shouldHideStatusBars)
        }
    }

    @Composable
    private fun PrimaryTabRootBackHandler(
        isAtPrimaryRoot: Boolean,
        active: Boolean,
        playerBottomSheetState: BottomSheetState,
        selectedPrimaryTabRoute: String,
        initialPrimaryTabRoute: String,
        onReturnToInitialTab: () -> Unit,
    ) {
        BackHandler(
            enabled =
                isAtPrimaryRoot &&
                    !active &&
                    !playerBottomSheetState.isExpanded &&
                    selectedPrimaryTabRoute != initialPrimaryTabRoute,
            onBack = onReturnToInitialTab,
        )
    }

    @Composable
    private fun PlayerExpandedBackHandler(
        playerBottomSheetState: BottomSheetState,
        aodModeEnabled: Boolean,
    ) {
        BackHandler(enabled = playerBottomSheetState.isExpanded && !aodModeEnabled) {
            playerBottomSheetState.collapseSoft()
        }
    }

    @Composable
    private fun BackupRestoreFromIntentDialog(
        uri: Uri,
        viewModel: BackupRestoreViewModel = hiltViewModel(),
    ) {
        var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

        AlertDialog(
            onDismissRequest = { pendingBackupRestoreUri = null },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            title = { Text(stringResource(R.string.restore_options_title)) },
            text = {
                Column {
                    BackupCategory.entries.forEach { category ->
                        val isChecked = category in selected
                        val labelRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library
                                BackupCategory.ACCOUNT -> R.string.backup_category_account
                                BackupCategory.SETTINGS -> R.string.backup_category_settings
                                BackupCategory.DOWNLOADS -> R.string.backup_category_downloads
                            }
                        val descRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                                BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                                BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                                BackupCategory.DOWNLOADS -> R.string.backup_category_downloads_desc
                            }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent,
                            onClick = {
                                selected = if (isChecked) selected - category else selected + category
                            },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp)
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + category else selected - category
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingBackupRestoreUri ?: return@TextButton
                        pendingBackupRestoreUri = null
                        viewModel.restore(this@MainActivity, uri, selected)
                    },
                    enabled = selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBackupRestoreUri = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    companion object {
        const val ACTION_SEARCH = "dev.citali.lunartune.action.SEARCH"
        const val ACTION_LIBRARY = "dev.citali.lunartune.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }

private val HomeOverflowFabSize = 56.dp
private val HomeOverflowFabSpacing = 12.dp
private val HomeOverflowMenuIconSize = 40.dp

@Composable
private fun HomeOverflowFabVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                scaleIn(
                    initialScale = 0.8f,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ),
        exit =
            fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                scaleOut(
                    targetScale = 0.8f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                ),
        label = "homeOverflowFabVisibility",
    ) {
        content()
    }
}

@Composable
private fun HomeOverflowFab(
    expanded: Boolean,
    pureBlack: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onShuffleClick: () -> Unit,
    onMusicRecognitionClick: () -> Unit,
    onMusicTogetherClick: () -> Unit,
) {
    val menuItemColors =
        MenuDefaults.itemColors(
            textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
            leadingIconColor =
                if (pureBlack) {
                    Color.White.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )

    Box {
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.size(HomeOverflowFabSize),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = {
                    onExpandedChange(false)
                    onMusicRecognitionClick()
                },
                leadingIcon = {
                    HomeOverflowMenuIcon(
                        iconRes = R.drawable.mic,
                        contentDescription = stringResource(R.string.music_recognition),
                        pureBlack = pureBlack,
                    )
                },
                colors = menuItemColors,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together_short)) },
                onClick = {
                    onExpandedChange(false)
                    onMusicTogetherClick()
                },
                leadingIcon = {
                    HomeOverflowMenuIcon(
                        iconRes = R.drawable.multi_user,
                        contentDescription = null,
                        pureBlack = pureBlack,
                    )
                },
                colors = menuItemColors,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shuffle)) },
                onClick = {
                    onExpandedChange(false)
                    onShuffleClick()
                },
                leadingIcon = {
                    HomeOverflowMenuIcon(
                        iconRes = R.drawable.shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        pureBlack = pureBlack,
                    )
                },
                colors = menuItemColors,
            )
        }
    }
}

@Composable
private fun HomeOverflowMenuIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    pureBlack: Boolean,
) {
    Surface(
        modifier = Modifier.size(HomeOverflowMenuIconSize),
        shape = CircleShape,
        color =
            if (pureBlack) {
                Color.White.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
            )
        }
    }
}

private const val TopAppBarIconButtonContainerAlpha = 0.48f

@Composable
private fun TranslucentTopAppBarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                        alpha = TopAppBarIconButtonContainerAlpha,
                    ),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        content = content,
    )
}

/** Corner radius of the floating top-bar sheet. */
private val MenuSheetCorner = 26.dp

/** Corner radius of one action bar inside the floating sheet. */
private val MenuBarCorner = 20.dp

/** Width of the sheet's content, before the ring of room for its shadow. */
private val MenuSheetContentWidth = 288.dp

/**
 * Upper bound for the sheet. The width used to be fixed, which ellipsised the
 * longer labels ("New release albu…") as soon as the user picked a wider font;
 * the sheet now grows to fit its longest row and stops here.
 */
private val MenuSheetMaxWidth = 348.dp

/** Ring of room kept inside the menu popup for the sheet's own shadow. */
private val MenuSheetShadowRoom = 8.dp

/** How far the sheet's painted shadow reaches into that ring. */
private val MenuSheetShadowSpread = 8.dp

/**
 * Replaces the top-bar action cluster with a single trigger that opens an
 * Android 16-style floating menu: a transparent, blurred sheet (Haze live blur
 * of the screen behind it) whose actions are separate rounded bars, each
 * floating on its own with gaps and a soft shade — the way notifications
 * stack in Android 16's shade. Everything the sheet needs for depth is painted
 * inside its own popup, because a popup clips at its bounds: an elevation
 * shadow there gets cut into a hard dark edge, and a hairline border reads as
 * a second line along the rim. On devices where the blur has no pixels to
 * show, the bars still float over the dimmed fallback tint.
 */
@Composable
private fun FrostedTopBarMenu(
    hazeState: HazeState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    updateAvailable: Boolean,
    onNavigate: (String) -> Unit,
) {
    val sheetShape = RoundedCornerShape(MenuSheetCorner)
    val barShape = RoundedCornerShape(MenuBarCorner)
    val barColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val sheetShadow = MaterialTheme.colorScheme.scrim

    Box {
        TranslucentTopAppBarIconButton(onClick = { onExpandedChange(!expanded) }) {
            BadgedBox(badge = {
                if (updateAvailable) {
                    Badge()
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.grid_view),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier =
                Modifier
                    .widthIn(min = MenuSheetContentWidth, max = MenuSheetMaxWidth)
                    // A popup clips at its own bounds, so a platform elevation
                    // shadow gets sliced into a hard dark line along the edge.
                    // The sheet keeps a ring of room for a shadow it paints
                    // itself, entirely inside the popup, so nothing is cut off.
                    .padding(MenuSheetShadowRoom)
                    .drawBehind {
                        drawFloatingSheetShadow(
                            cornerRadiusPx = MenuSheetCorner.toPx(),
                            spreadPx = MenuSheetShadowSpread.toPx(),
                            shade = sheetShadow,
                        )
                    }.clip(sheetShape)
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style =
                            frostedTopMenuStyle(
                                // Light enough that the blur from the screen behind
                                // actually reads through the panel: the frost lives in the
                                // gap between the rows, and an opaque fill hid all of it.
                                scrim = barColor.copy(alpha = 0.42f),
                                fallbackScrim = barColor.copy(alpha = FrostedFallbackAlpha),
                            ),
                        performanceMode = HazePerformanceMode.Performance,
                    ).background(barColor.copy(alpha = 0.30f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = sheetShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            FrostedTopBarMenuRow(
                iconRes = R.drawable.history,
                label = stringResource(R.string.history),
                barShape = barShape,
                barColor = barColor,
            ) {
                onExpandedChange(false)
                onNavigate("history")
            }
            FrostedTopBarMenuRow(
                iconRes = R.drawable.trending_up,
                label = stringResource(R.string.charts),
                barShape = barShape,
                barColor = barColor,
            ) {
                onExpandedChange(false)
                onNavigate("charts_screen")
            }
            FrostedTopBarMenuRow(
                iconRes = R.drawable.new_release,
                label = stringResource(R.string.new_release_albums_short),
                barShape = barShape,
                barColor = barColor,
            ) {
                onExpandedChange(false)
                onNavigate("new_release")
            }
            FrostedTopBarMenuRow(
                iconRes = R.drawable.multi_user,
                label = stringResource(R.string.music_together_short),
                barShape = barShape,
                barColor = barColor,
            ) {
                onExpandedChange(false)
                onNavigate("settings/music_together")
            }
            val settingsTrailing: (@Composable () -> Unit)? =
                if (updateAvailable) {
                    @Composable { Badge() }
                } else {
                    null
                }
            FrostedTopBarMenuRow(
                iconRes = R.drawable.settings,
                label = stringResource(R.string.settings),
                barShape = barShape,
                barColor = barColor,
                trailing = settingsTrailing,
            ) {
                onExpandedChange(false)
                onNavigate("settings")
            }
        }
    }
}

/**
 * Soft shadow for the floating menu sheet, drawn in the ring of padding the
 * sheet keeps for it. Stacked translucent rounded rects approximate a blur and
 * stay entirely inside the popup, so no edge is ever sliced flat.
 */
private fun DrawScope.drawFloatingSheetShadow(
    cornerRadiusPx: Float,
    spreadPx: Float,
    shade: Color,
) {
    val steps = 6
    for (step in steps downTo 1) {
        val t = step / steps.toFloat()
        val spread = spreadPx * t
        drawRoundRect(
            color = shade.copy(alpha = 0.028f + 0.032f * (1f - t)),
            topLeft = Offset(-spread, -spread * 0.35f + spreadPx * 0.42f),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius(cornerRadiusPx + spread),
        )
    }
}

/** One floating action bar of [FrostedTopBarMenu]: rounded, self-shadowed, on the blurred sheet. */
@Composable
private fun FrostedTopBarMenuRow(
    @DrawableRes iconRes: Int,
    label: String,
    barShape: Shape,
    barColor: Color,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    // A hair lighter than the panel in dark, a hair darker in light: enough to
    // separate the rows from the sheet without another stack of shadows.
    val rowColor = lerp(barColor, MaterialTheme.colorScheme.onSurface, 0.05f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(barShape)
                .background(rowColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun OnlineSearchSortMenu(
    selectedSort: OnlineSearchSort,
    onSortSelected: (OnlineSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        remember {
            listOf(
                OnlineSearchSort.DEFAULT,
                OnlineSearchSort.VIEWS,
            )
        }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.filter_alt),
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                stringResource(
                                    when (sort) {
                                        OnlineSearchSort.DEFAULT -> R.string.default_style
                                        OnlineSearchSort.VIEWS -> R.string.views
                                    },
                                ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortSelected(sort)
                    },
                    leadingIcon = {
                        if (sort == selectedSort) {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

private fun Context.isTvDevice(): Boolean {
    val isTelevisionUiMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return isTelevisionUiMode ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}

