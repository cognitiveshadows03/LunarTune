/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.component

import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.lyrics.LyricsEntry
import dev.citali.lunartune.lyrics.LyricsSourceFormat
import dev.citali.lunartune.lyrics.LyricsSyncType
import dev.citali.lunartune.lyrics.LyricsUtils.findCurrentLineIndex
import dev.citali.lunartune.lyrics.LyricsUtils.providedTranslationTextForEntry
import dev.citali.lunartune.lyrics.WordTimestamp
import dev.citali.lunartune.lyrics.toLyricsEntries
import dev.citali.lunartune.ui.component.shimmer.ShimmerHost
import dev.citali.lunartune.ui.component.shimmer.TextPlaceholder
import dev.citali.lunartune.ui.theme.rememberLunarTuneLyricsFontFamily
import dev.citali.lunartune.ui.utils.smoothFadingEdge
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.viewmodels.LyricsRenderScreenState
import dev.citali.lunartune.viewmodels.LyricsRenderViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

// ──────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────

/** Lead time offset for LRC-style line-synced lyrics (ms). */
// Lines flip at their exact onset so the pane does not scroll before the vocal arrives.
private const val LRC_LEAD_MS = 0L

/** Lead time offset for word-synced lyrics (ms). */
private const val WORD_SYNC_LEAD_MS = 0L

/** Seconds to wait before auto-scroll resumes after manual scroll. */
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/** Sentinel entry prepended so auto-scroll has headroom above the first line. */
private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")

/** How far down the pane the focused line sits, as a fraction of the viewport. */
private const val LYRICS_FOCUS_HEIGHT_RATIO = 0.35f

/** Height of the fade drawn over the top and bottom of the V2 list. */
internal val LyricsV2EdgeFade = 80.dp

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return false
        }
    }
    return false
}

// ──────────────────────────────────────────────────────────────────────
// Main Composable
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsV2(
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    /** Parent-owned state for hosts that temporarily remove this pane from composition. */
    rememberedListState: LazyListState? = null,
    lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel(),
    isActive: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val animationsDisabled = LocalAnimationsDisabled.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val preparedLyrics = (lyricsState as? LyricsRenderScreenState.Success)?.lyrics
    val preferences = preparedLyrics?.preferences
    val lyricsClick = preferences?.clickEnabled ?: true
    val lyricsScroll = preferences?.scrollEnabled ?: true
    val lyricsTextSize = preferences?.textSizeSp ?: 26f
    val lyricsLineSpacing = preferences?.lineSpacing ?: 1.3f
    val lyricsLineBlurPreference = preferences?.lineBlurEnabled ?: true
    val bounceFactor = preferences?.v2BounceFactor ?: 1f
    val glowFactor = preferences?.v2GlowFactor ?: 1f
    val fillTransitionWidth = preferences?.v2FillTransitionWidthDp ?: 8f
    val lrcBounceEnabled = preferences?.v2LrcBounceEnabled ?: true
    val lyricsFontFamily = rememberLunarTuneLyricsFontFamily()
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)

    // ── Text colour derived from background style ──
    val textColor =
        textColorOverride ?: if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.White
        }
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    val inactiveAlpha = 0.35f

    // ── Selection mode state ──
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 5
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val showTranslations = preparedLyrics?.hasTranslations == true && preferences?.showTranslations == true
    val isSynced = preparedLyrics?.syncType != null && preparedLyrics.syncType != LyricsSyncType.PLAIN
    val isWordSyncedFormat = preparedLyrics?.syncType == LyricsSyncType.WORD

    val lyricsEntries: List<LyricsEntry> =
        remember(preparedLyrics?.lines, preparedLyrics?.sourceFormat, preparedLyrics?.syncType) {
            val parsed = preparedLyrics?.toLyricsEntries().orEmpty()
            if (parsed.isNotEmpty() && parsed.first().time >= 0) {
                listOf(HEAD_LYRICS_ENTRY) + parsed
            } else {
                parsed
            }
        }

    val entriesWithWords: List<LyricsEntry> = lyricsEntries

    // ── Playback position tracking ──
    val leadMs =
        when {
            preparedLyrics?.sourceFormat == LyricsSourceFormat.TTML -> WORD_SYNC_LEAD_MS
            isWordSyncedFormat -> WORD_SYNC_LEAD_MS
            else -> LRC_LEAD_MS
        }
    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestAnimationsDisabled = rememberUpdatedState(animationsDisabled)
    val latestSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val clockPositionState = lyricsRenderViewModel.playbackPositionMs.collectAsStateWithLifecycle()

    DisposableEffect(player, lyricsRenderViewModel) {
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (
                        events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                        events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                    ) {
                        lyricsRenderViewModel.onPlaybackPositionChanged(
                            latestSliderPositionProvider.value() ?: player.currentPosition,
                        )
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, lifecycleOwner, lyricsRenderViewModel, isSynced, isActive) {
        if (!isSynced || !isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            lyricsRenderViewModel.onPlaybackPositionChanged(
                latestSliderPositionProvider.value() ?: player.currentPosition,
            )
            while (coroutineContext.isActive) {
                withFrameNanos {
                    val sliderPosition = latestSliderPositionProvider.value()
                    lyricsRenderViewModel.onPlaybackFrame(
                        positionMs = sliderPosition ?: player.currentPosition,
                        sampleTimeNanos = SystemClock.elapsedRealtimeNanos(),
                        playbackSpeed = player.playbackParameters.speed,
                        isAdvancing = player.isPlaying && sliderPosition == null && !latestAnimationsDisabled.value,
                        durationMs = player.duration,
                    )
                }
            }
        }
    }

    val playbackPositionProvider: () -> Long =
        remember(clockPositionState, player) {
            {
                val clockedMs = clockPositionState.value
                val baseMs =
                    latestSliderPositionProvider.value()
                        ?: if (clockedMs == 0L && player.currentPosition > 0L) {
                            player.currentPosition
                        } else {
                            clockedMs
                        }
                (baseMs + latestSyncOffset.value.toLong()).coerceAtLeast(0L)
            }
        }
    val currentLineIndex by remember(entriesWithWords, isSynced, leadMs) {
        derivedStateOf {
            if (!isSynced || entriesWithWords.isEmpty()) {
                0
            } else {
                findCurrentLineIndex(
                    entriesWithWords,
                    (playbackPositionProvider() + leadMs).coerceAtLeast(0L),
                    0L,
                )
            }
        }
    }

    // ── Scroll State ──
    val listState = rememberedListState ?: rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect manual scrolling
    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        isManualScrolling = true
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Offset.Zero
                }
            }
        }

    // Resume auto-scroll after timeout
    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Auto-scroll to active line
    var wasActive by remember { mutableStateOf(isActive) }
    LaunchedEffect(currentLineIndex, isManualScrolling, lyricsScroll, isActive) {
        val justActivated = !wasActive && isActive
        wasActive = isActive
        if (!isActive || !lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * LYRICS_FOCUS_HEIGHT_RATIO).toInt()

        if (justActivated) {
            listState.scrollToItem(currentLineIndex, -targetOffset)
            return@LaunchedEffect
        }

        val distance = abs(currentLineIndex - listState.firstVisibleItemIndex)
        if (distance > 15) {
            // Far jump — snap first, then settle.
            listState.scrollToItem(
                (currentLineIndex - 2).coerceAtLeast(0),
                0,
            )
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset,
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    // ── Keep screen alive ──
    val activity = context as? android.app.Activity
    DisposableEffect(activity, isActive) {
        if (isActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose { }
        }
    }

    // ── Render ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = LyricsPaneBottomPadding),
    ) {
        if (lyricsState is LyricsRenderScreenState.Loading) {
            ShimmerHost {
                repeat(6) {
                    TextPlaceholder()
                }
            }
            return@BoxWithConstraints
        }

        if (lyricsState !is LyricsRenderScreenState.Success) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .smoothFadingEdge(vertical = LyricsV2EdgeFade)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" },
                contentType = { _, entry ->
                    when {
                        entry == HEAD_LYRICS_ENTRY -> "head"
                        entry.isInstrumental -> "instrumental"
                        entry.words != null && isSynced -> "wordSynced"
                        else -> "lineSynced"
                    }
                },
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }

                // ── Instrumental break icon ──
                if (item.isInstrumental && isSynced) {
                    val startTimeMs = item.time
                    val endTimeMs = item.time + item.durationMs
                    val isActive by remember(startTimeMs, endTimeMs) {
                        derivedStateOf { playbackPositionProvider() in startTimeMs until endTimeMs }
                    }
                    val distanceFromActive = abs(index - currentLineIndex)
                    val instrAlpha =
                        when {
                            isActive -> {
                                1f
                            }

                            isManualScrolling -> {
                                when {
                                    distanceFromActive == 1 -> 0.72f
                                    distanceFromActive == 2 -> 0.56f
                                    distanceFromActive == 3 -> 0.40f
                                    else -> 0.28f
                                }
                            }

                            distanceFromActive == 1 -> {
                                0.52f
                            }

                            distanceFromActive == 2 -> {
                                0.30f
                            }

                            distanceFromActive == 3 -> {
                                0.18f
                            }

                            else -> {
                                inactiveAlpha
                            }
                        }
                    val animatedInstrAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = instrAlpha,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = if (isActive) 330 else 500,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalAlpha",
                    )
                    val animatedInstrScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.95f,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = 166,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalScale",
                    )
                    val targetInstrBlur =
                        when {
                            !isSynced || isActive || isManualScrolling -> 0f
                            distanceFromActive == 1 -> 2f
                            distanceFromActive == 2 -> 5f
                            else -> 12f
                        }
                    val animatedInstrBlur by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = targetInstrBlur,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = 300,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        label = "v2InstrumentalBlur",
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top =
                                        if (index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (isAndroid12OrLater && lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedInstrBlur.dp,
                                            radiusY = animatedInstrBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedInstrScale
                                    scaleY = animatedInstrScale
                                    alpha = animatedInstrAlpha
                                }.then(
                                    if (lyricsClick && item.time > 0) {
                                        Modifier.clickable { player.seekTo(item.time) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        InstrumentalBreakItem(
                            durationMs = item.durationMs,
                            currentPositionMs = if (isActive) playbackPositionProvider() else if (index < currentLineIndex) endTimeMs else 0L,
                            startTimeMs = startTimeMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                        )
                    }
                    return@itemsIndexed
                }

                // ── Agent-based positioning ──
                // v1 or null -> Start, v2 -> End, others -> Center
                val textAlign =
                    when (item.agent?.lowercase()) {
                        "v1", null -> TextAlign.Start
                        "v2" -> TextAlign.End
                        else -> TextAlign.Center
                    }
                val horizontalAlignment =
                    when (item.agent?.lowercase()) {
                        "v1", null -> Alignment.Start
                        "v2" -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isFuture = isSynced && index > currentLineIndex
                val isSelected = selectedIndices.contains(index)

                // Distance-based alpha for non-active lines
                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha =
                    when {
                        !isSynced -> {
                            0.92f
                        }

                        isActive -> {
                            1f
                        }

                        isManualScrolling -> {
                            when {
                                distanceFromActive == 1 -> 0.72f
                                distanceFromActive == 2 -> 0.56f
                                distanceFromActive == 3 -> 0.40f
                                else -> 0.28f
                            }
                        }

                        distanceFromActive == 1 -> {
                            0.52f
                        }

                        distanceFromActive == 2 -> {
                            0.30f
                        }

                        distanceFromActive == 3 -> {
                            0.18f
                        }

                        else -> {
                            0.10f
                        }
                    }
                val targetBlur =
                    when {
                        !isSynced || isActive || (isSelectionModeActive && isSelected) || isManualScrolling -> 0f
                        distanceFromActive == 1 -> 2f
                        distanceFromActive == 2 -> 5f
                        else -> 12f
                    }
                val animatedBlur by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetBlur,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 300,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LyricBlur",
                )
                val animatedLineScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.95f,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 166,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineScale",
                )
                val animatedLineAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = lineAlpha,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = if (isActive) 330 else 500,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineAlpha",
                )
                val lineTransformOrigin =
                    remember(item.agent) {
                        when (item.agent?.lowercase()) {
                            "v2" -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(1f, 0.5f)
                            }

                            "v1", null -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(0f, 0.5f)
                            }

                            else -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(0.5f, 0.5f)
                            }
                        }
                    }

                // Background vocal detection
                val hasBackgroundWords = item.words?.any { it.isBackground } == true
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true
                val baseLayoutDirection = LocalLayoutDirection.current
                val lineText =
                    remember(item.text, item.words) {
                        item.words
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?: item.text
                    }
                val lineIsRtl = remember(item.isRtl, lineText) { item.isRtl ?: isRtlText(lineText) }
                val lineLayoutDirection =
                    remember(lineIsRtl, baseLayoutDirection) {
                        if (lineIsRtl) LayoutDirection.Rtl else baseLayoutDirection
                    }

                CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color =
                                        if (isSelected && isSelectionModeActive) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        } else {
                                            Color.Transparent
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(
                                    start = if (isAllBackground) 28.dp else 24.dp,
                                    end = 24.dp,
                                    top =
                                        if (index == 0 ||
                                            (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)
                                        ) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (isAndroid12OrLater && lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedBlur.dp,
                                            radiusY = animatedBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedLineScale
                                    scaleY = animatedLineScale
                                    alpha = animatedLineAlpha
                                    transformOrigin = lineTransformOrigin
                                }.combinedClickable(
                                    enabled = true,
                                    onClick = {
                                        if (isSelectionModeActive) {
                                            if (isSelected) {
                                                selectedIndices.remove(index)
                                                if (selectedIndices.isEmpty()) {
                                                    isSelectionModeActive = false
                                                }
                                            } else {
                                                if (selectedIndices.size < maxSelectionLimit) {
                                                    selectedIndices.add(index)
                                                } else {
                                                    showMaxSelectionToast = true
                                                }
                                            }
                                        } else if (lyricsClick && isSynced && item.time > 0) {
                                            player.seekTo(item.time)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionModeActive) {
                                            isSelectionModeActive = true
                                            selectedIndices.add(index)
                                        } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                            selectedIndices.add(index)
                                        } else if (!isSelected) {
                                            showMaxSelectionToast = true
                                        }
                                    },
                                ),
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        val romanizedText = item.providerRomanizedText
                        val translationText =
                            remember(showTranslations, item.providerTranslationText, item.text) {
                                if (showTranslations) providedTranslationTextForEntry(item) else null
                            }
                        val supplementaryBaseTextStyle = MaterialTheme.typography.bodyMedium
                        val supplementaryTextStyle =
                            remember(supplementaryBaseTextStyle, lyricsTextSize, lyricsFontFamily, isAllBackground) {
                                supplementaryBaseTextStyle.copy(
                                    fontSize = (lyricsTextSize * 0.56f).sp,
                                    lineHeight = (lyricsTextSize * 0.76f).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = lyricsFontFamily ?: supplementaryBaseTextStyle.fontFamily,
                                )
                            }
                        val translationTextStyle =
                            remember(supplementaryBaseTextStyle, lyricsTextSize, lyricsFontFamily, isAllBackground) {
                                supplementaryBaseTextStyle.copy(
                                    fontSize = (lyricsTextSize * 0.62f).sp,
                                    lineHeight = (lyricsTextSize * 0.84f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = lyricsFontFamily ?: supplementaryBaseTextStyle.fontFamily,
                                )
                            }

                        val softBlurFraction =
                            if (!isAndroid12OrLater && lyricsLineBlur) {
                                (animatedBlur / 4f).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        val softBlurRadiusPx =
                            if (softBlurFraction > 0.01f) {
                                with(density) { (animatedBlur * 1.35f).dp.toPx() }.coerceIn(0.5f, 24f)
                            } else {
                                0f
                            }

                        if (romanizedText != null) {
                            val baseRomColor = textColor.copy(alpha = if (isActive) 0.78f else 0.68f)
                            val (romStyle, romColor) =
                                applySoftTextBlur(
                                    style = supplementaryTextStyle,
                                    color = baseRomColor,
                                    blurRadiusPx = softBlurRadiusPx,
                                    blurFraction = softBlurFraction,
                                )
                            Text(
                                text = romanizedText,
                                style = romStyle,
                                color = romColor,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = (lyricsTextSize * 0.18f).dp),
                            )
                        }

                        if (item.words != null && isSynced) {
                            val lineWords = item.words!!
                            val scopedPositionMs =
                                when {
                                    isActive -> (playbackPositionProvider() + leadMs).coerceAtLeast(0L)
                                    isPast -> Long.MAX_VALUE
                                    else -> 0L
                                }
                            LyricsLineV2(
                                words = lineWords,
                                isActive = isActive,
                                isPast = isPast,
                                currentPositionMs = scopedPositionMs,
                                textColor = textColor,
                                inactiveAlpha = inactiveAlpha,
                                baseFontSize = lyricsTextSize,
                                isLineAllBackground = isAllBackground,
                                textAlign = textAlign,
                                lyricsFontFamily = lyricsFontFamily,
                                isRtl = lineIsRtl,
                                bounceFactor = bounceFactor,
                                glowFactor = glowFactor,
                                fillTransitionWidth = fillTransitionWidth,
                                softBlurRadiusPx = softBlurRadiusPx,
                                softBlurFraction = softBlurFraction,
                            )
                        } else if (isSynced) {
                            LyricsLineLrcBounce(
                                text = item.text,
                                isActive = isActive,
                                textColor = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                fontSize = lyricsTextSize,
                                lineSpacing = lyricsLineSpacing,
                                isAllBackground = isAllBackground,
                                lyricsFontFamily = lyricsFontFamily,
                                textAlign = textAlign,
                                bounceFactor = if (lrcBounceEnabled) bounceFactor else 0f,
                                softBlurRadiusPx = softBlurRadiusPx,
                                softBlurFraction = softBlurFraction,
                            )
                        } else {
                            val plainStyleBase =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                    fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                                )
                            val plainColorBase = textColor.copy(alpha = if (isActive) 1f else 0.52f)
                            val (plainStyle, plainColor) =
                                applySoftTextBlur(
                                    style = plainStyleBase,
                                    color = plainColorBase,
                                    blurRadiusPx = softBlurRadiusPx,
                                    blurFraction = softBlurFraction,
                                )
                            Text(
                                text = item.text,
                                style = plainStyle,
                                color = plainColor,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (translationText != null) {
                            val baseTransColor = textColor.copy(alpha = if (isActive) 0.80f else 0.72f)
                            val (transStyle, transColor) =
                                applySoftTextBlur(
                                    style = translationTextStyle,
                                    color = baseTransColor,
                                    blurRadiusPx = softBlurRadiusPx,
                                    blurFraction = softBlurFraction,
                                )
                            Text(
                                text = translationText,
                                style = transStyle,
                                color = transColor,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = (lyricsTextSize * 0.28f).dp),
                            )
                        }
                    }
                }
            }

            // Bottom spacer for overscroll
            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        // ── Resume auto-scroll button ──
        if (isManualScrolling && isSynced) {
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * LYRICS_FOCUS_HEIGHT_RATIO).toInt(),
                        )
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (isSelectionModeActive) {
            mediaMetadata?.let { metadata ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                    ).clickable {
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(
                            modifier =
                                Modifier
                                    .background(
                                        color =
                                            if (selectedIndices.isNotEmpty()) {
                                                Color.White.copy(alpha = 0.9f)
                                            } else {
                                                Color.White.copy(alpha = 0.5f)
                                            },
                                        shape = RoundedCornerShape(24.dp),
                                    ).clickable(enabled = selectedIndices.isNotEmpty()) {
                                        if (selectedIndices.isNotEmpty()) {
                                            val sortedIndices = selectedIndices.sorted()
                                            val selectedLyricsText =
                                                sortedIndices
                                                    .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                                                    .joinToString("\n")

                                            if (selectedLyricsText.isNotBlank()) {
                                                shareDialogData =
                                                    Triple(
                                                        selectedLyricsText,
                                                        metadata.title ?: "",
                                                        metadata.artists.joinToString { it.name },
                                                    )
                                                showShareDialog = true
                                            }
                                            isSelectionModeActive = false
                                            selectedIndices.clear()
                                        }
                                    }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = stringResource(R.string.share_selected),
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.share),
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = LyricsSharePayload(lyricsText, songTitle, artists),
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareDialogData = Triple(lyricsText, songTitle, artists)
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = LyricsSharePayload(lyricsText, songTitle, artists),
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Line-level composable: renders words with fluid fill animation
// ──────────────────────────────────────────────────────────────────────

private fun applySoftTextBlur(
    style: TextStyle,
    color: Color,
    blurRadiusPx: Float,
    blurFraction: Float,
): Pair<TextStyle, Color> {
    if (blurFraction <= 0.01f || blurRadiusPx <= 0.4f) return style to color
    val coreAlpha = (color.alpha * (1f - 0.88f * blurFraction)).coerceIn(0f, 1f)
    val blurredShadow =
        Shadow(
            color = color.copy(alpha = (color.alpha * 0.96f * blurFraction).coerceIn(0f, 1f)),
            offset = Offset.Zero,
            blurRadius = blurRadiusPx,
        )
    return style.copy(shadow = blurredShadow) to color.copy(alpha = coreAlpha)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
    softBlurRadiusPx: Float = 0f,
    softBlurFraction: Float = 0f,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    // Split words into main and background
    val mainWords = remember(words) { words.filter { !it.isBackground } }
    val bgWords = remember(words) { words.filter { it.isBackground } }

    // Fast single-Text path for static unfocused lines (much faster mount + clean pre-Android-12 optical blur)
    if (!isActive) {
        val headlineBase = MaterialTheme.typography.headlineMedium
        if (mainWords.isNotEmpty()) {
            val mainText = remember(mainWords) { mainWords.joinToString(separator = "") { it.text } }
            val mainFontSize = if (isLineAllBackground) baseFontSize * 0.82f * 0.85f else baseFontSize
            val mainWeight = if (isPast) FontWeight.ExtraBold else FontWeight.SemiBold
            val baseMainColor =
                textColor.copy(
                    alpha =
                        if (isPast) {
                            if (isLineAllBackground) 0.75f else 1f
                        } else {
                            if (isLineAllBackground) inactiveAlpha * 0.7f else inactiveAlpha
                        },
                )
            val (mainStyle, mainColor) =
                applySoftTextBlur(
                    style =
                        headlineBase.copy(
                            fontSize = mainFontSize.sp,
                            fontWeight = mainWeight,
                            fontStyle = FontStyle.Normal,
                            lineHeight = (mainFontSize * 1.35f).sp,
                            fontFamily = lyricsFontFamily ?: headlineBase.fontFamily,
                        ),
                    color = baseMainColor,
                    blurRadiusPx = softBlurRadiusPx,
                    blurFraction = softBlurFraction,
                )
            Text(
                text = mainText,
                style = mainStyle,
                color = mainColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (bgWords.isNotEmpty()) {
            if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(4.dp))
            val bgText = remember(bgWords) { bgWords.joinToString(separator = "") { it.text } }
            val bgFontSize = baseFontSize * 0.65f * 0.85f
            val bgWeight = if (isPast) FontWeight.ExtraBold else FontWeight.SemiBold
            val baseBgColor =
                textColor.copy(
                    alpha = (if (isPast) 0.75f else inactiveAlpha * 0.7f) * 0.85f,
                )
            val (bgStyle, bgColor) =
                applySoftTextBlur(
                    style =
                        headlineBase.copy(
                            fontSize = bgFontSize.sp,
                            fontWeight = bgWeight,
                            fontStyle = FontStyle.Normal,
                            lineHeight = (bgFontSize * 1.35f).sp,
                            fontFamily = lyricsFontFamily ?: headlineBase.fontFamily,
                        ),
                    color = baseBgColor,
                    blurRadiusPx = softBlurRadiusPx,
                    blurFraction = softBlurFraction,
                )
            Text(
                text = bgText,
                style = bgStyle,
                color = bgColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    // 1. Render main words First (if any)
    if (mainWords.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            mainWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }

    // 2. Render background words explicitly on a NEW line, noticeably smaller
    if (bgWords.isNotEmpty()) {
        val spacerHeight = if (mainWords.isNotEmpty()) 4.dp else 0.dp
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(spacerHeight))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f), // Slightly dimmer overall
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (baseFontSize * 0.65f).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex + mainWords.size,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f, // ~65% size of main text
                    isBackground = true, // Force dimmer styling inside AnimatedWordV2
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Word-level composable: liquid fill sweep + glow + bounce
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    wordIndex: Int,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val wordStartMs = (word.startTime * 1000).roundToLong()
    val wordEndMs = (word.endTime * 1000).roundToLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    // Perfect linear progress [0..1] that matches individual word timings
    val progress =
        when {
            isWordComplete -> 1f
            currentPositionMs <= wordStartMs -> 0f
            else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }

    // ── Bounce and Float animation ──
    // Subtle scale up peaking halfway through the word. Exact timing sync!
    val sinProgress = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
    val wordScale = 1f + (0.015f * bounceFactor * sinProgress)

    // Float is only applied when the word is actively sung, making it pop from the line.
    // We use animateFloatAsState so that when it finishes (and drops to 0f),
    // it smoothly decays back into place rather than a harsh mathematical snap.
    val targetFloat = if (isWordActive) -4f * bounceFactor * sinProgress else 0f
    val floatOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFloat,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = if (isWordActive) 50 else 350,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2FloatOffset",
    )

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = if (isLineActive || isLinePast) FontWeight.ExtraBold else FontWeight.SemiBold
    val glowPadding = 10.dp
    val headlineBase = MaterialTheme.typography.headlineMedium
    val baseWordTextStyle =
        remember(headlineBase, actualFontSize, fontWeight, lyricsFontFamily) {
            headlineBase.copy(
                fontSize = actualFontSize.sp,
                fontWeight = fontWeight,
                fontStyle = FontStyle.Normal,
                lineHeight = (actualFontSize * 1.35f).sp,
                fontFamily = lyricsFontFamily ?: headlineBase.fontFamily,
            )
        }
    val showStaticWordGlow = isWordActive && glowFactor > 0.01f
    val activeWordTextStyle =
        remember(baseWordTextStyle, showStaticWordGlow, textColor, glowFactor) {
            if (showStaticWordGlow) {
                baseWordTextStyle.copy(
                    shadow =
                        Shadow(
                            color = textColor.copy(alpha = (0.40f * glowFactor).coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = (11f * glowFactor).coerceAtLeast(1f),
                        ),
                )
            } else {
                baseWordTextStyle
            }
        }

    // ── Two-layer rendering: dim base + liquid fill overlay ──
    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val glowPaddingPx = glowPadding.roundToPx()
                    val boundedMaxWidth =
                        if (constraints.hasBoundedWidth) {
                            constraints.maxWidth
                        } else {
                            constraints.maxWidth.coerceAtMost(Int.MAX_VALUE / 8)
                        }
                    val looseConstraints =
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = boundedMaxWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                    val placeable = measurable.measure(looseConstraints)

                    val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                    val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)

                    layout(coreWidth, coreHeight) {
                        placeable.place(-glowPaddingPx, -glowPaddingPx)
                    }
                }.graphicsLayer {
                    clip = false
                    translationY = floatOffset * density
                    scaleX = wordScale
                    scaleY = wordScale
                },
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style = baseWordTextStyle,
            color = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.7f else inactiveAlpha),
            modifier = Modifier.padding(glowPadding),
        )

        // Layer 2: Filled overlay with liquid sweep mask + static cached glow
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style = activeWordTextStyle,
                color =
                    textColor.copy(
                        alpha = if (isBackground) 0.75f else 1f,
                    ),
                modifier =
                    if (isWordActive && !isWordComplete) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val edgeWidth = fillTransitionWidth.dp.toPx()
                                val center =
                                    if (isRtl) {
                                        size.width - ((size.width + edgeWidth * 2) * progress - edgeWidth)
                                    } else {
                                        (size.width + edgeWidth * 2) * progress - edgeWidth
                                    }
                                drawRect(
                                    brush =
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors =
                                                if (isRtl) {
                                                    listOf(Color.Transparent, Color.Black)
                                                } else {
                                                    listOf(Color.Black, Color.Transparent)
                                                },
                                            startX = center - edgeWidth,
                                            endX = center + edgeWidth,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }.padding(glowPadding)
                    } else {
                        Modifier.padding(glowPadding)
                    },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// LRC bounce: word-by-word spring bounce for line-synced lyrics
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineLrcBounce(
    text: String,
    isActive: Boolean,
    textColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    isAllBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    textAlign: TextAlign,
    bounceFactor: Float,
    softBlurRadiusPx: Float = 0f,
    softBlurFraction: Float = 0f,
) {
    val effectiveFontSize = if (isAllBackground) fontSize * 0.82f else fontSize
    val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal
    if (!isActive || bounceFactor == 0f) {
        val (style, resolvedColor) =
            applySoftTextBlur(
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = effectiveFontSize.sp,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        lineHeight = (effectiveFontSize * lineSpacing).sp,
                        fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                    ),
                color = textColor,
                blurRadiusPx = softBlurRadiusPx,
                blurFraction = softBlurFraction,
            )
        Text(
            text = text,
            style = style,
            color = resolvedColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val words = remember(text) { text.toLyricsWrappingUnits() }
    val scaleAnimatables = remember(words.size) { List(words.size) { Animatable(1f) } }
    val floatAnimatables = remember(words.size) { List(words.size) { Animatable(0f) } }

    LaunchedEffect(isActive) {
        if (!isActive || bounceFactor == 0f) return@LaunchedEffect
        words.indices.forEach { i ->
            launch {
                delay(i * 40L)
                try {
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f + 0.045f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { scaleAnimatables[i].snapTo(1f) }
                }
            }
            launch {
                delay(i * 40L)
                try {
                    floatAnimatables[i].animateTo(
                        targetValue = -5f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    floatAnimatables[i].animateTo(
                        targetValue = 0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { floatAnimatables[i].snapTo(0f) }
                }
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            when (textAlign) {
                TextAlign.Center -> Arrangement.Center
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Start
            },
    ) {
        words.forEachIndexed { i, word ->
            LrcBouncingWord(
                text = word,
                scaleAnim = scaleAnimatables[i],
                floatAnim = floatAnimatables[i],
                color = textColor,
                fontSize = effectiveFontSize,
                lineSpacing = lineSpacing,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lyricsFontFamily = lyricsFontFamily,
            )
        }
    }
}

@Composable
private fun LrcBouncingWord(
    text: String,
    scaleAnim: Animatable<Float, AnimationVector1D>,
    floatAnim: Animatable<Float, AnimationVector1D>,
    color: Color,
    fontSize: Float,
    lineSpacing: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    lyricsFontFamily: FontFamily?,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lineHeight = (fontSize * lineSpacing).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
            ),
        color = color,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = floatAnim.value
            },
    )
}

// ──────────────────────────────────────────────────────────────────────
// Instrumental break icon: music-note filled bottom-to-top over the gap
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun InstrumentalBreakItem(
    durationMs: Long,
    currentPositionMs: Long,
    startTimeMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
) {
    val musicNotePath =
        remember {
            androidx.compose.ui.graphics.vector
                .PathParser()
                .parsePathString(
                    "M10 21q-1.65 0-2.825-1.175T6 17t1.175-2.825T10 13q.575 0 1.063.138t.937.412V4" +
                        "q0-.425.288-.712T13 3h4q.425 0 .713.288T18 4v2q0 .425-.288.713T17 7h-3v10" +
                        "q0 1.65-1.175 2.825T10 21",
                ).toPath()
        }

    val targetFillFraction =
        when {
            durationMs <= 0L -> {
                0f
            }

            currentPositionMs <= startTimeMs -> {
                0f
            }

            currentPositionMs >= startTimeMs + durationMs -> {
                1f
            }

            else -> {
                ((currentPositionMs - startTimeMs).toDouble() / durationMs.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
    val fillFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFillFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessHigh,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "instrumentalFill",
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.size(48.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val pivot = androidx.compose.ui.geometry.Offset.Zero

        withTransform(
            transformBlock = { scale(scaleX, scaleY, pivot) },
        ) {
            drawPath(path = musicNotePath, color = textColor.copy(alpha = inactiveAlpha))
        }

        if (fillFraction > 0f) {
            val clipTop = size.height * (1f - fillFraction)
            clipRect(
                left = 0f,
                top = clipTop,
                right = size.width,
                bottom = size.height,
            ) {
                withTransform(
                    transformBlock = { scale(scaleX, scaleY, pivot) },
                ) {
                    drawPath(path = musicNotePath, color = textColor)
                }
            }
        }
    }
}
