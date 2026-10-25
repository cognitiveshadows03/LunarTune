/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeBreathingDots
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeBreathingDotsDefaults
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsFade
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsLineItem
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLine
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLyrics
import com.mocharealm.accompanist.lyrics.ui.preparation.prepareLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val INTRO_DOTS_MIN_DURATION_MS = 5000
private const val INTERLUDE_DOTS_MIN_GAP_MS = 5000
private const val SYNC_PREPARE_MAX_LINES = 200
private const val MAX_SCENE_CACHE_ENTRIES = 10
private const val MAX_FOCUS_BLUR_DISTANCE = 3

private data class LunarSceneCacheKey(
    val lyrics: SyncedLyrics,
    val widthPx: Int,
    val normalStyle: TextStyle,
    val accompanimentStyle: TextStyle,
    val phoneticStyle: TextStyle,
    val translationStyle: TextStyle,
    val showPhonetic: Boolean,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

internal class CachedLunarScene(
    val prepared: PreparedLyrics,
    val items: List<LunarDisplayItem>,
    val breathingLineHeightPx: Float,
)

internal class LunarDisplayItem(
    val sourceIndex: Int,
    val itemKey: String,
    val line: ISyncedLine,
    val prepared: PreparedLine?,
    val interludeStartMs: Int,
    val interludeEndMs: Int,
) {
    val hasInterlude: Boolean
        get() = interludeEndMs - interludeStartMs >= INTERLUDE_DOTS_MIN_GAP_MS
}

@Immutable
internal data class LunarFocusState(
    val firstFocusedItemIndex: Int,
    val lastFocusedItemIndex: Int,
    val scrollTargetItemIndex: Int,
)

private object LunarKaraokeSceneCache {
    private val map =
        object : LinkedHashMap<LunarSceneCacheKey, CachedLunarScene>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<LunarSceneCacheKey, CachedLunarScene>?,
            ): Boolean = size > MAX_SCENE_CACHE_ENTRIES
        }

    fun get(key: LunarSceneCacheKey): CachedLunarScene? =
        synchronized(map) { map[key] }

    fun put(
        key: LunarSceneCacheKey,
        scene: CachedLunarScene,
    ) {
        synchronized(map) { map[key] = scene }
    }
}

private fun buildCachedLunarScene(
    lyrics: SyncedLyrics,
    prepared: PreparedLyrics,
): CachedLunarScene {
    val embeddedSet = HashSet<PreparedLine>()
    fun collectEmbedded(parent: PreparedLine) {
        for (child in parent.before) {
            if (embeddedSet.add(child)) collectEmbedded(child)
        }
        for (child in parent.after) {
            if (embeddedSet.add(child)) collectEmbedded(child)
        }
    }
    for (line in prepared.lines) {
        if (line != null) collectEmbedded(line)
    }

    val items = ArrayList<LunarDisplayItem>(lyrics.lines.size)
    var prevMaxEndMs = 0
    for (index in lyrics.lines.indices) {
        val preparedLine = prepared.lines.getOrNull(index)
        if (preparedLine != null && preparedLine in embeddedSet) continue
        val sourceLine = lyrics.lines[index]
        val lineStartMs = sourceLine.start
        val lineEndMs = maxLineEndMs(sourceLine, preparedLine)
        val interludeStart = if (items.isEmpty()) 0 else prevMaxEndMs
        val interludeEnd = lineStartMs
        items.add(
            LunarDisplayItem(
                sourceIndex = index,
                itemKey = "${sourceLine.start}-${sourceLine.end}-$index",
                line = sourceLine,
                prepared = preparedLine,
                interludeStartMs = interludeStart,
                interludeEndMs = interludeEnd,
            ),
        )
        prevMaxEndMs = maxOf(prevMaxEndMs, lineEndMs)
    }

    val maxMainRowHeight =
        prepared.lines
            .asSequence()
            .filterNotNull()
            .filter { it.source is KaraokeLine.MainKaraokeLine }
            .flatMap { it.rows.asSequence() }
            .map { (it.height - it.phoneticHeight).coerceAtLeast(0f) }
            .maxOrNull() ?: 0f

    return CachedLunarScene(
        prepared = prepared,
        items = items,
        breathingLineHeightPx = maxMainRowHeight,
    )
}

private fun maxLineEndMs(
    source: ISyncedLine,
    prepared: PreparedLine?,
): Int {
    var end = source.end
    if (prepared != null) {
        for (child in prepared.before) end = maxOf(end, child.source.end)
        for (child in prepared.after) end = maxOf(end, child.source.end)
    }
    return end
}

internal fun computeLunarFocusState(
    items: List<LunarDisplayItem>,
    nowMs: Int,
): LunarFocusState {
    if (items.isEmpty()) {
        return LunarFocusState(
            firstFocusedItemIndex = -1,
            lastFocusedItemIndex = -1,
            scrollTargetItemIndex = 0,
        )
    }

    // Binary search last item with line.start <= nowMs.
    var low = 0
    var high = items.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (items[mid].line.start <= nowMs) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    val candidateLastStarted = low - 1
    if (candidateLastStarted < 0) {
        // Before first lyric line (intro).
        return LunarFocusState(
            firstFocusedItemIndex = -1,
            lastFocusedItemIndex = -1,
            scrollTargetItemIndex = 0,
        )
    }

    var firstFocused = -1
    var lastFocused = -1
    val scanStart = (candidateLastStarted - 6).coerceAtLeast(0)
    for (idx in scanStart..candidateLastStarted) {
        val item = items[idx]
        val endMs = maxLineEndMs(item.line, item.prepared)
        if (nowMs in item.line.start..endMs) {
            if (firstFocused < 0) firstFocused = idx
            lastFocused = idx
        }
    }

    if (firstFocused >= 0) {
        return LunarFocusState(
            firstFocusedItemIndex = firstFocused,
            lastFocusedItemIndex = lastFocused,
            scrollTargetItemIndex = firstFocused,
        )
    }

    // Between two lines: if the upcoming line has an active interlude, scroll to it so the
    // breathing dots sit right on the anchor; otherwise hold on the just-finished line until
    // ~250ms before the next line starts.
    val nextIdx = candidateLastStarted + 1
    if (nextIdx < items.size) {
        val nextItem = items[nextIdx]
        if (nextItem.hasInterlude && nowMs in nextItem.interludeStartMs..nextItem.interludeEndMs) {
            return LunarFocusState(
                firstFocusedItemIndex = -1,
                lastFocusedItemIndex = -1,
                scrollTargetItemIndex = nextIdx,
            )
        }
        if (nextItem.line.start - nowMs <= 220) {
            return LunarFocusState(
                firstFocusedItemIndex = nextIdx,
                lastFocusedItemIndex = nextIdx,
                scrollTargetItemIndex = nextIdx,
            )
        }
    }

    return LunarFocusState(
        firstFocusedItemIndex = candidateLastStarted,
        lastFocusedItemIndex = candidateLastStarted,
        scrollTargetItemIndex = candidateLastStarted,
    )
}

@Composable
fun LunarKaraokeLyricsView(
    listState: LunarLyricsListState,
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollEnabled: Boolean = true,
    normalLineTextStyle: TextStyle =
        LocalTextStyle.current.copy(
            fontSize = 30.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        ),
    translationTextStyle: TextStyle =
        LocalTextStyle.current.copy(
            fontSize = 18.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.SemiBold,
            textMotion = TextMotion.Static,
        ),
    accompanimentLineTextStyle: TextStyle =
        LocalTextStyle.current.copy(
            fontSize = 21.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        ),
    phoneticTextStyle: TextStyle =
        normalLineTextStyle.copy(
            fontSize = 16.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.SemiBold,
            textMotion = TextMotion.Static,
        ),
    textColor: Color = Color.White,
    breathingDotsDefaults: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
    blendMode: BlendMode = BlendMode.SrcOver,
    useBlurEffect: Boolean = true,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    anchor: LyricsAnchor = LyricsAnchor.Fixed(64.dp),
    topFade: LyricsFade = LyricsFade.Fixed(28.dp),
    bottomFade: LyricsFade = LyricsFade.Fixed(56.dp),
    itemSpacing: Dp = 14.dp,
    blurDelta: Float = 2.5f,
    autoScrollResumeDelayMillis: Long = 2000L,
) {
    val currentProvider by rememberUpdatedState(currentPosition)
    val timeProvider = remember { { currentProvider() } }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val anchorOffset =
            when (anchor) {
                is LyricsAnchor.Fixed -> anchor.offset.coerceIn(0.dp, maxHeight)
                is LyricsAnchor.Fraction -> maxHeight * anchor.fraction.coerceIn(0f, 1f)
            }
        val topFadeSize =
            when (topFade) {
                is LyricsFade.None -> 0.dp
                is LyricsFade.Fixed -> topFade.size
                is LyricsFade.Fraction -> maxHeight * topFade.fraction
                is LyricsFade.ToAnchor -> (anchorOffset - topFade.gap).coerceAtLeast(0.dp)
            }
        val bottomFadeSize =
            when (bottomFade) {
                is LyricsFade.None -> 0.dp
                is LyricsFade.Fixed -> bottomFade.size
                is LyricsFade.Fraction -> maxHeight * bottomFade.fraction
                is LyricsFade.ToAnchor -> (maxHeight - anchorOffset - bottomFade.gap).coerceAtLeast(0.dp)
            }

        val contentWidthPx = with(density) { (maxWidth - 32.dp).toPx() }.coerceAtLeast(1f)
        val cacheKey =
            remember(
                lyrics,
                contentWidthPx.roundToInt(),
                normalLineTextStyle,
                accompanimentLineTextStyle,
                phoneticTextStyle,
                translationTextStyle,
                density.density,
                density.fontScale,
                layoutDirection,
            ) {
                LunarSceneCacheKey(
                    lyrics = lyrics,
                    widthPx = contentWidthPx.roundToInt().coerceAtLeast(1),
                    normalStyle = normalLineTextStyle,
                    accompanimentStyle = accompanimentLineTextStyle,
                    phoneticStyle = phoneticTextStyle,
                    translationStyle = translationTextStyle,
                    // Always measure phonetics when present so toggling showPhonetic reveals in-place.
                    showPhonetic = true,
                    density = density.density,
                    fontScale = density.fontScale,
                    layoutDirection = layoutDirection,
                )
            }

        // Synchronous Frame-0 hit from LRU scene cache or fast arithmetic preparation for normal songs.
        val syncPrepared =
            remember(cacheKey) {
                LunarKaraokeSceneCache.get(cacheKey)
                    ?: if (lyrics.lines.size <= SYNC_PREPARE_MAX_LINES) {
                        val prepared =
                            prepareLyrics(
                                lyrics = lyrics,
                                profiles = LunarLyricsProfiles,
                                measurer = measurer,
                                normalStyle = normalLineTextStyle,
                                accompanimentStyle = accompanimentLineTextStyle,
                                phoneticStyle = phoneticTextStyle,
                                width = cacheKey.widthPx.toFloat(),
                                density = density.density,
                                showPhonetic = true,
                                fontScale = density.fontScale,
                                translationStyle = translationTextStyle,
                            )
                        buildCachedLunarScene(lyrics, prepared).also { built ->
                            LunarKaraokeSceneCache.put(cacheKey, built)
                        }
                    } else {
                        null
                    }
            }

        var asyncScene by remember(cacheKey) { mutableStateOf(syncPrepared) }
        if (asyncScene == null) {
            LaunchedEffect(cacheKey) {
                val built =
                    withContext(Dispatchers.Default) {
                        LunarKaraokeSceneCache.get(cacheKey)
                            ?: buildCachedLunarScene(
                                lyrics = lyrics,
                                prepared =
                                    prepareLyrics(
                                        lyrics = lyrics,
                                        profiles = LunarLyricsProfiles,
                                        measurer = measurer,
                                        normalStyle = normalLineTextStyle,
                                        accompanimentStyle = accompanimentLineTextStyle,
                                        phoneticStyle = phoneticTextStyle,
                                        width = cacheKey.widthPx.toFloat(),
                                        density = density.density,
                                        showPhonetic = true,
                                        fontScale = density.fontScale,
                                        translationStyle = translationTextStyle,
                                    ),
                            ).also { LunarKaraokeSceneCache.put(cacheKey, it) }
                    }
                asyncScene = built
            }
        }

        val scene = syncPrepared ?: asyncScene ?: return@BoxWithConstraints
        val resources = rememberLunarRenderResources(scene.prepared, textColor)
        val displayItems = scene.items
        val breathingLineHeight =
            if (scene.breathingLineHeightPx > 0f) {
                with(density) { scene.breathingLineHeightPx.toDp() }
            } else {
                40.dp
            }

        val focusState by remember(displayItems) {
            derivedStateOf {
                computeLunarFocusState(displayItems, timeProvider())
            }
        }

        if (autoScrollEnabled && !listState.hasPositionedInitially && displayItems.isNotEmpty()) {
            listState.ensureInitialPosition(
                targetIndex = computeLunarFocusState(displayItems, timeProvider()).scrollTargetItemIndex,
                itemCount = displayItems.size,
            )
        }

        val autoScrollModifier =
            lunarLyricsAutoScroll(
                state = listState,
                autoScrollEnabled = autoScrollEnabled,
                targetIndexProvider = { focusState.scrollTargetItemIndex },
                itemCount = displayItems.size,
                resumeDelayMillis = autoScrollResumeDelayMillis,
            )

        val focusBlurScale by animateFloatAsState(
            targetValue = if (listState.isManualScrolling || !useBlurEffect) 0f else 1f,
            animationSpec = tween(durationMillis = if (listState.isManualScrolling) 100 else 260),
            label = "manualScrollBlurScale",
        )

        val bottomPadding = (maxHeight - anchorOffset).coerceAtLeast(80.dp)

        LazyColumn(
            state = listState.lazyListState,
            contentPadding =
                PaddingValues(
                    top = anchorOffset,
                    bottom = bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(autoScrollModifier)
                    .lunarLyricsEdgeFade(topFadeSize, bottomFadeSize),
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, item -> item.itemKey },
                contentType = { _, _ -> "karaoke_line" },
            ) { itemIndex, item ->
                val isFocused by remember(itemIndex, displayItems) {
                    derivedStateOf {
                        val f = focusState
                        f.firstFocusedItemIndex >= 0 &&
                            itemIndex in f.firstFocusedItemIndex..f.lastFocusedItemIndex
                    }
                }
                val distanceBucket by remember(itemIndex, displayItems) {
                    derivedStateOf {
                        val f = focusState
                        if (f.firstFocusedItemIndex < 0) {
                            itemIndex.coerceAtMost(MAX_FOCUS_BLUR_DISTANCE)
                        } else {
                            maxOf(
                                0,
                                f.firstFocusedItemIndex - itemIndex,
                                itemIndex - f.lastFocusedItemIndex,
                            ).coerceAtMost(MAX_FOCUS_BLUR_DISTANCE)
                        }
                    }
                }
                val targetBlur = if (useBlurEffect) distanceBucket * blurDelta else 0f
                val animatedBlur by animateFloatAsState(
                    targetValue = targetBlur,
                    animationSpec = tween(durationMillis = 260),
                    label = "lineFocusBlur",
                )

                val showInterludeDots by remember(item) {
                    derivedStateOf {
                        if (!item.hasInterlude) {
                            false
                        } else if (itemIndex == 0) {
                            item.interludeEndMs >= INTRO_DOTS_MIN_DURATION_MS &&
                                timeProvider() in 0..item.interludeEndMs
                        } else {
                            timeProvider() in item.interludeStartMs..item.interludeEndMs
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier.graphicsLayer {
                            translationY = listState.cascade.offsetFor(itemIndex)
                        },
                ) {
                    if (showInterludeDots) {
                        KaraokeBreathingDots(
                            alignment =
                                if (item.prepared?.rightAligned == true) {
                                    KaraokeAlignment.End
                                } else {
                                    KaraokeAlignment.Start
                                },
                            startTimeMs = item.interludeStartMs,
                            endTimeMs = item.interludeEndMs,
                            currentTimeProvider = timeProvider,
                            defaults = breathingDotsDefaults,
                            trailingSpacing = itemSpacing,
                            lineHeight = breathingLineHeight,
                        )
                    }

                    LyricsLineItem(
                        isFocused = isFocused,
                        isRightAligned = item.prepared?.rightAligned == true,
                        onLineClicked = {
                            listState.resumeAutoScroll(itemIndex)
                            onLineClicked(item.line)
                        },
                        onLinePressed = { onLinePressed(item.line) },
                        blurRadius = { animatedBlur * focusBlurScale },
                        activeAlpha = 1f,
                        inactiveAlpha = 0.42f,
                        blendMode = blendMode,
                    ) {
                        val preparedLine = item.prepared
                        if (preparedLine != null) {
                            LunarPreparedLineText(
                                prepared = preparedLine,
                                currentTimeProvider = timeProvider,
                                resources = resources,
                                showPhonetic = showPhonetic,
                                showTranslation = showTranslation,
                                verticalPadding = 2.dp,
                                onChildClick = { child ->
                                    listState.resumeAutoScroll(itemIndex)
                                    onLineClicked(child.source)
                                },
                                onChildLongClick = { child -> onLinePressed(child.source) },
                            )
                        }
                    }
                }
            }
        }
    }
}
