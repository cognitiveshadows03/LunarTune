/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
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
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLine
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLyrics
import com.mocharealm.accompanist.lyrics.ui.preparation.prepareLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val INTRO_DOTS_MIN_DURATION_MS = 5000
private const val INTERLUDE_DOTS_MIN_GAP_MS = 5000
private const val SYNC_PREPARE_MAX_LINES = 200
private const val MAX_SCENE_CACHE_ENTRIES = 10
private const val CAPTION_REFLOW_DURATION_MS = 340L

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

private data class LunarResourcesCacheKey(
    val colorValue: ULong,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

internal class CachedLunarScene(
    val prepared: PreparedLyrics,
    val items: List<LunarDisplayItem>,
    val breathingLineHeightPx: Float,
) {
    private val resourcesCache = HashMap<LunarResourcesCacheKey, LunarRenderResources>(2)

    fun resourcesFor(
        color: Color,
        density: Density,
        layoutDirection: LayoutDirection,
    ): LunarRenderResources {
        val key =
            LunarResourcesCacheKey(
                colorValue = color.value,
                density = density.density,
                fontScale = density.fontScale,
                layoutDirection = layoutDirection,
            )
        return synchronized(resourcesCache) {
            resourcesCache.getOrPut(key) {
                LunarRenderResources(color, density, layoutDirection)
            }
        }
    }
}

internal class LunarDisplayItem(
    val sourceIndex: Int,
    val itemKey: String,
    val line: ISyncedLine,
    val prepared: PreparedLine?,
    val startMs: Int,
    val endMs: Int,
    val interludeStartMs: Int,
    val interludeEndMs: Int,
) {
    val hasInterlude: Boolean
        get() = interludeEndMs - interludeStartMs > INTERLUDE_DOTS_MIN_GAP_MS
}

@Immutable
internal data class LunarFocusState(
    val firstFocusedItemIndex: Int,
    val lastFocusedItemIndex: Int,
    val scrollTargetItemIndex: Int,
    val focusEndItemIndex: Int,
    val activeInterludeItemIndex: Int,
    val activeIntro: Boolean,
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

internal suspend fun prewarmLunarKaraokeScene(
    lyrics: SyncedLyrics,
    contentWidthPx: Float,
    normalStyle: TextStyle,
    accompanimentStyle: TextStyle,
    phoneticStyle: TextStyle,
    translationStyle: TextStyle,
    textColor: Color,
    density: Density,
    layoutDirection: LayoutDirection,
    measurer: TextMeasurer,
    currentPositionMs: Int,
) {
    if (lyrics.lines.isEmpty() || contentWidthPx <= 1f) return
    val widthPxInt = contentWidthPx.roundToInt().coerceAtLeast(1)
    val cacheKey =
        LunarSceneCacheKey(
            lyrics = lyrics,
            widthPx = widthPxInt,
            normalStyle = normalStyle,
            accompanimentStyle = accompanimentStyle,
            phoneticStyle = phoneticStyle,
            translationStyle = translationStyle,
            showPhonetic = true,
            density = density.density,
            fontScale = density.fontScale,
            layoutDirection = layoutDirection,
        )
    val scene =
        withContext(Dispatchers.Default) {
            LunarKaraokeSceneCache.get(cacheKey)
                ?: buildCachedLunarScene(
                    lyrics = lyrics,
                    prepared =
                        prepareLyrics(
                            lyrics = lyrics,
                            profiles = LunarLyricsProfiles,
                            measurer = measurer,
                            normalStyle = normalStyle,
                            accompanimentStyle = accompanimentStyle,
                            phoneticStyle = phoneticStyle,
                            width = widthPxInt.toFloat(),
                            density = density.density,
                            showPhonetic = true,
                            fontScale = density.fontScale,
                            translationStyle = translationStyle,
                        ),
                ).also { LunarKaraokeSceneCache.put(cacheKey, it) }
        }

    val resources = scene.resourcesFor(textColor, density, layoutDirection)
    val focusIndex = computeLunarFocusState(scene.items, currentPositionMs).scrollTargetItemIndex
    val startIdx = (focusIndex - 4).coerceAtLeast(0)
    val endIdx = (focusIndex + 10).coerceAtMost(scene.items.lastIndex)
    val linesToWarm = ArrayList<PreparedLine>(16)
    for (i in startIdx..endIdx) {
        val preparedLine = scene.items[i].prepared ?: continue
        linesToWarm.add(preparedLine)
        linesToWarm.addAll(preparedLine.before)
        linesToWarm.addAll(preparedLine.after)
    }
    resources.prewarmLines(linesToWarm)
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
        val lineStartMs = minLineStartMs(sourceLine, preparedLine)
        val lineEndMs = maxLineEndMs(sourceLine, preparedLine)
        val interludeStart = if (items.isEmpty()) 0 else prevMaxEndMs
        val interludeEnd = lineStartMs
        items.add(
            LunarDisplayItem(
                sourceIndex = index,
                itemKey = "${sourceLine.start}-${sourceLine.end}-$index",
                line = sourceLine,
                prepared = preparedLine,
                startMs = lineStartMs,
                endMs = lineEndMs,
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

private fun minLineStartMs(
    source: ISyncedLine,
    prepared: PreparedLine?,
): Int {
    var start =
        minOf(
            source.start,
            (source as? KaraokeLine.MainKaraokeLine)?.accompanimentLines?.minOfOrNull { it.start }
                ?: source.start,
        )
    if (prepared != null) {
        for (child in prepared.before) start = minOf(start, child.source.start)
        for (child in prepared.after) start = minOf(start, child.source.start)
    }
    return start
}

private fun maxLineEndMs(
    source: ISyncedLine,
    prepared: PreparedLine?,
): Int {
    var end =
        maxOf(
            source.end,
            (source as? KaraokeLine.MainKaraokeLine)?.accompanimentLines?.maxOfOrNull { it.end }
                ?: source.end,
        )
    if (prepared != null) {
        for (child in prepared.before) end = maxOf(end, child.source.end)
        for (child in prepared.after) end = maxOf(end, child.source.end)
    }
    return end
}

/**
 * Matches [KaraokeLyricsView]'s `LyricsPlaybackTimeline` focus interval rule:
 * - While one or more lines cover `nowMs` (`startMs <= nowMs < endMs`), those lines are focused
 *   and the scroll anchor tracks `firstFocused` with spring-chain focus ending at `lastFocused`.
 * - As soon as a line ends (`nowMs >= endMs`), the scroll target immediately transitions to the
 *   next upcoming line so the 650ms glide + 240Hz spring cascade settles smoothly onto the anchor.
 */
internal fun computeLunarFocusState(
    items: List<LunarDisplayItem>,
    nowMs: Int,
): LunarFocusState {
    if (items.isEmpty()) {
        return LunarFocusState(
            firstFocusedItemIndex = -1,
            lastFocusedItemIndex = -1,
            scrollTargetItemIndex = 0,
            focusEndItemIndex = 0,
            activeInterludeItemIndex = -1,
            activeIntro = false,
        )
    }

    val introEnd = items[0].startMs
    val activeIntro = introEnd > INTRO_DOTS_MIN_DURATION_MS && nowMs in 0 until introEnd

    // Binary search first item with startMs > nowMs (`nextUpcoming`).
    var low = 0
    var high = items.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (items[mid].startMs <= nowMs) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    val nextUpcoming = low
    val candidateLastStarted = nextUpcoming - 1

    var firstFocused = -1
    var lastFocused = -1
    if (candidateLastStarted >= 0) {
        val scanStart = (candidateLastStarted - 6).coerceAtLeast(0)
        for (idx in scanStart..candidateLastStarted) {
            val item = items[idx]
            val active =
                if (item.endMs > item.startMs) {
                    nowMs >= item.startMs && nowMs < item.endMs
                } else {
                    idx == candidateLastStarted && (nextUpcoming >= items.size || nowMs < items[nextUpcoming].startMs)
                }
            if (active) {
                if (firstFocused < 0) firstFocused = idx
                lastFocused = idx
            }
        }
    }

    val activeInterlude =
        if (nextUpcoming in 1 until items.size) {
            val upcoming = items[nextUpcoming]
            if (upcoming.hasInterlude && nowMs >= upcoming.interludeStartMs && nowMs < upcoming.interludeEndMs) {
                nextUpcoming
            } else {
                -1
            }
        } else {
            -1
        }

    val scrollTarget =
        when {
            firstFocused >= 0 -> firstFocused
            nextUpcoming in items.indices -> nextUpcoming
            else -> items.lastIndex
        }
    val focusEnd =
        when {
            lastFocused >= 0 -> lastFocused
            else -> scrollTarget
        }

    return LunarFocusState(
        firstFocusedItemIndex = firstFocused,
        lastFocusedItemIndex = lastFocused,
        scrollTargetItemIndex = scrollTarget,
        focusEndItemIndex = focusEnd,
        activeInterludeItemIndex = activeInterlude,
        activeIntro = activeIntro,
    )
}

private fun PreparedLine.computeLunarSettledHeightPx(
    nowMs: Int,
    translationShown: Boolean,
    phoneticShown: Boolean,
    density: Float,
    anticipateAccompaniment: Boolean = false,
    nested: Boolean = false,
    parentStartMs: Int = source.start,
    parentEndMs: Int = source.end,
): Float {
    if (nested) {
        val visStart = minOf(source.start, parentStartMs)
        val visEnd = maxOf(source.end, parentEndMs)
        if (nowMs !in visStart..visEnd && !anticipateAccompaniment) return 0f
    }
    var extent = height + if (nested) 4f * density else 0f
    if (!phoneticShown) {
        for (row in rows) {
            extent -= row.phoneticHeight + row.phoneticSpacingBefore
        }
    }
    val hasLinePhonetic = phoneticShown && phonetic != null
    val hasLineTranslation = translationShown && translation != null
    if (hasLinePhonetic) {
        extent += 4f * density + (phonetic?.size?.height ?: 0)
    }
    if (hasLineTranslation) {
        extent += (if (hasLinePhonetic) 2f else 4f) * density + (translation?.size?.height ?: 0)
    }
    for (child in before) {
        extent +=
            child.computeLunarSettledHeightPx(
                nowMs = nowMs,
                translationShown = translationShown,
                phoneticShown = phoneticShown,
                density = density,
                anticipateAccompaniment = anticipateAccompaniment,
                nested = true,
                parentStartMs = source.start,
                parentEndMs = source.end,
            )
    }
    for (child in after) {
        extent +=
            child.computeLunarSettledHeightPx(
                nowMs = nowMs,
                translationShown = translationShown,
                phoneticShown = phoneticShown,
                density = density,
                anticipateAccompaniment = anticipateAccompaniment,
                nested = true,
                parentStartMs = source.start,
                parentEndMs = source.end,
            )
    }
    return extent.coerceAtLeast(0f)
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
            fontSize = 34.sp,
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
            fontSize = 22.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        ),
    phoneticTextStyle: TextStyle =
        normalLineTextStyle.copy(
            fontSize = 18.sp,
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
    topFade: LyricsFade = LyricsFade.ToAnchor(16.dp),
    bottomFade: LyricsFade = LyricsFade.Fraction(0.5f),
    keepAliveZone: Dp = 100.dp,
    itemSpacing: Dp = 16.dp,
    blurDelta: Float = 3f,
    scrollAnimationSpec: AnimationSpec<Float> = tween(650, easing = FastOutSlowInEasing),
    autoScrollResumeDelayMillis: Long = 2500L,
    scrollChain: LunarScrollChain? = LunarScrollChain(),
    isActive: Boolean = true,
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
                is LyricsFade.Fixed -> topFade.length
                is LyricsFade.Fraction -> maxHeight * topFade.fraction
                is LyricsFade.ToAnchor -> (anchorOffset - topFade.inset).coerceAtLeast(0.dp)
            }
        val bottomFadeSize =
            when (bottomFade) {
                is LyricsFade.Fixed -> bottomFade.length
                is LyricsFade.Fraction -> maxHeight * bottomFade.fraction
                is LyricsFade.ToAnchor -> (anchorOffset - bottomFade.inset).coerceAtLeast(0.dp)
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
        val resources =
            remember(scene, textColor, density.density, density.fontScale, layoutDirection) {
                scene.resourcesFor(textColor, density, layoutDirection)
            }
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

        // Seed Frame-0 scroll position synchronously before the first LazyLayout measure pass, and
        // also snap onto the active line on the exact frame a pre-warmed view becomes visible so
        // opening lyrics mid-song never sweeps across lines passed while hidden.
        val previousActive = remember(listState) { booleanArrayOf(isActive) }
        val becameActive = !previousActive[0] && isActive
        previousActive[0] = isActive
        if (
            autoScrollEnabled &&
            displayItems.isNotEmpty() &&
            (!listState.hasPositionedInitially || becameActive)
        ) {
            listState.snapToTargetImmediately(
                targetIndex = computeLunarFocusState(displayItems, timeProvider()).scrollTargetItemIndex,
                itemCount = displayItems.size,
            )
        }

        val translationShown = rememberUpdatedState(showTranslation)
        val phoneticShown = rememberUpdatedState(showPhonetic)
        val previousCaptions =
            remember(scene, listState) { booleanArrayOf(showTranslation, showPhonetic) }
        SideEffect {
            if (previousCaptions[0] != showTranslation || previousCaptions[1] != showPhonetic) {
                listState.preserveFollowAnchorForContentChange()
                previousCaptions[0] = showTranslation
                previousCaptions[1] = showPhonetic
            }
        }
        LaunchedEffect(scene, listState, showTranslation, showPhonetic) {
            withFrameNanos { }
            delay(CAPTION_REFLOW_DURATION_MS)
            listState.finishCaptionContentChange()
        }

        SideEffect {
            listState.setInterludeItem(focusState.activeInterludeItemIndex)
        }

        val listItems =
            remember(scene, density.density) {
                displayItems.mapIndexed { itemIndex, item ->
                    val prepared = item.prepared
                    val estimated =
                        ((prepared?.height ?: 0f) +
                            (if (prepared?.phonetic != null) {
                                4f * density.density + prepared.phonetic!!.size.height
                            } else {
                                0f
                            }) +
                            (if (prepared?.translation != null) {
                                (if (prepared.phonetic != null) 2f else 4f) * density.density +
                                    prepared.translation!!.size.height
                            } else {
                                0f
                            }))
                            .roundToInt()
                            .coerceAtLeast(0)
                    LunarListItem(
                        key = item.itemKey,
                        preserveAnchorOnHeightChange = false,
                        estimatedHeightPx = estimated,
                        settledHeightPx =
                            prepared?.let { preparedLine ->
                                {
                                    val f = focusState
                                    val focused =
                                        f.firstFocusedItemIndex >= 0 &&
                                            itemIndex in f.firstFocusedItemIndex..f.lastFocusedItemIndex
                                    preparedLine
                                        .computeLunarSettledHeightPx(
                                            nowMs = timeProvider(),
                                            translationShown = translationShown.value,
                                            phoneticShown = phoneticShown.value,
                                            density = density.density,
                                            anticipateAccompaniment = focused,
                                        ).roundToInt()
                                }
                            },
                    )
                }
            }

        val followModifier =
            lunarLyricsAutoScroll(
                state = listState,
                autoScrollEnabled = autoScrollEnabled && isActive,
                targetIndex = { focusState.scrollTargetItemIndex },
                focusEndIndex = { focusState.focusEndItemIndex },
                animationSpec = scrollAnimationSpec,
                resumeDelayMillis = autoScrollResumeDelayMillis,
                playbackPosition = timeProvider,
            )

        val hardwareBlur = supportsHardwareRenderEffectBlur()
        val focusBlurScaleState =
            animateFloatAsState(
                targetValue = if (listState.isManualScrolling || !useBlurEffect) 0f else 1f,
                animationSpec = tween(durationMillis = if (listState.isManualScrolling) 100 else 300),
                label = "manualScrollFocusBlur",
            )

        LunarLyricsLazyColumn(
            items = listItems,
            state = listState,
            itemSpacing = itemSpacing,
            scrollChain = scrollChain,
            beyondBounds = keepAliveZone,
            contentPadding =
                PaddingValues(
                    top = anchorOffset,
                    bottom = maxHeight + keepAliveZone,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(followModifier)
                    .lunarLyricsEdgeFade(topFadeSize, bottomFadeSize),
        ) { itemIndex ->
            val item = displayItems[itemIndex]
            val f = focusState
            val isFocused =
                f.firstFocusedItemIndex >= 0 &&
                    itemIndex in f.firstFocusedItemIndex..f.lastFocusedItemIndex
            val firstAnchor =
                if (f.firstFocusedItemIndex >= 0) f.firstFocusedItemIndex else f.scrollTargetItemIndex
            val lastAnchor =
                if (f.lastFocusedItemIndex >= 0) f.lastFocusedItemIndex else f.scrollTargetItemIndex
            val distance =
                maxOf(
                    0,
                    firstAnchor - itemIndex,
                    itemIndex - lastAnchor,
                )
            val animatedBlurState =
                animateFloatAsState(
                    targetValue = if (useBlurEffect) distance * blurDelta else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "lineFocusBlur",
                )
            val lineInactiveAlpha =
                if (!hardwareBlur && useBlurEffect && !listState.isManualScrolling) {
                    when {
                        distance <= 1 -> 0.42f
                        distance == 2 -> 0.34f
                        else -> 0.27f
                    }
                } else {
                    0.4f
                }

            Column {
                if (itemIndex == 0 && f.activeIntro) {
                    KaraokeBreathingDots(
                        alignment =
                            if (item.prepared?.rightAligned == true) {
                                KaraokeAlignment.End
                            } else {
                                KaraokeAlignment.Start
                            },
                        startTimeMs = 0,
                        endTimeMs = item.startMs,
                        currentTimeProvider = timeProvider,
                        defaults = breathingDotsDefaults,
                        trailingSpacing = itemSpacing,
                        lineHeight = breathingLineHeight,
                    )
                }
                if (f.activeInterludeItemIndex == itemIndex && itemIndex > 0) {
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

                LunarLyricsLineItem(
                    isFocused = isFocused,
                    isRightAligned = item.prepared?.rightAligned == true,
                    onLineClicked = {
                        listState.resumeAutoScroll(item.line.start, itemIndex)
                        onLineClicked(item.line)
                    },
                    onLinePressed = { onLinePressed(item.line) },
                    blurRadius =
                        if (hardwareBlur) {
                            { animatedBlurState.value * focusBlurScaleState.value }
                        } else {
                            { 0f }
                        },
                    inactiveAlpha = lineInactiveAlpha,
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
                            verticalPadding = 0.dp,
                            softBlurRadiusProvider =
                                if (hardwareBlur) {
                                    { 0f }
                                } else {
                                    { animatedBlurState.value * focusBlurScaleState.value }
                                },
                            modifier =
                                if (item.line is KaraokeLine.AccompanimentKaraokeLine) {
                                    Modifier.padding(horizontal = 16.dp)
                                } else {
                                    Modifier
                                },
                            onChildClick = { child ->
                                listState.resumeAutoScroll(child.source.start, itemIndex)
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
