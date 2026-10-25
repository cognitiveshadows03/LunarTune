/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.exp

/**
 * Scroll state for [LunarKaraokeLyricsView] backed by Compose's hardware-prefetched [LazyListState]
 * with instant Frame-0 initial positioning and coupled cascade spring offsets during line steps.
 */
@Stable
class LunarLyricsListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    hasPositionedInitially: Boolean = false,
) {
    val lazyListState =
        LazyListState(
            firstVisibleItemIndex = initialFirstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset.coerceAtLeast(0),
        )

    var hasPositionedInitially by mutableStateOf(hasPositionedInitially)
        internal set

    var isManualScrolling by mutableStateOf(false)
        internal set

    internal var clickSeekTargetIndex by mutableIntStateOf(-1)
    internal var manualResumeTrigger by mutableIntStateOf(0)

    internal val cascade = LunarScrollCascade()

    internal fun ensureInitialPosition(
        targetIndex: Int,
        itemCount: Int,
    ) {
        if (hasPositionedInitially || itemCount <= 0) return
        val clamped = targetIndex.coerceIn(0, itemCount - 1)
        Snapshot.withoutReadObservation {
            hasPositionedInitially = true
            if (
                lazyListState.firstVisibleItemIndex != clamped ||
                lazyListState.firstVisibleItemScrollOffset != 0
            ) {
                lazyListState.requestScrollToItem(clamped, 0)
            }
        }
    }

    fun resumeAutoScroll(targetIndex: Int = -1) {
        isManualScrolling = false
        if (targetIndex >= 0) {
            clickSeekTargetIndex = targetIndex
        }
        manualResumeTrigger++
    }

    companion object {
        val Saver: Saver<LunarLyricsListState, Any> =
            listSaver(
                save = { state ->
                    listOf(
                        state.lazyListState.firstVisibleItemIndex,
                        state.lazyListState.firstVisibleItemScrollOffset,
                        if (state.hasPositionedInitially) 1 else 0,
                    )
                },
                restore = { saved ->
                    LunarLyricsListState(
                        initialFirstVisibleItemIndex = saved.getOrElse(0) { 0 },
                        initialFirstVisibleItemScrollOffset = saved.getOrElse(1) { 0 },
                        hasPositionedInitially = saved.getOrElse(2) { 0 } == 1,
                    )
                },
            )
    }
}

@Composable
fun rememberLunarLyricsListState(
    initialTargetIndex: Int = 0,
): LunarLyricsListState =
    rememberSaveable(saver = LunarLyricsListState.Saver) {
        LunarLyricsListState(
            initialFirstVisibleItemIndex = initialTargetIndex.coerceAtLeast(0),
            initialFirstVisibleItemScrollOffset = 0,
            hasPositionedInitially = initialTargetIndex > 0,
        )
    }

/**
 * Lightweight analytical damped-spring cascade giving trailing items a subtle Apple-Music-like
 * elastic wave during line transitions without per-item array allocations.
 */
@Stable
internal class LunarScrollCascade {
    private var anchorIndex by mutableIntStateOf(0)
    private var impulsePx by mutableFloatStateOf(0f)

    fun trigger(
        focusIndex: Int,
        scrollDeltaPx: Float,
    ) {
        if (abs(scrollDeltaPx) < 4f) return
        anchorIndex = focusIndex
        // Clamp impulse so fast multi-line skips never over-stretch spacing.
        impulsePx = (impulsePx * 0.35f + scrollDeltaPx * 0.22f).coerceIn(-44f, 44f)
    }

    fun step(dtSeconds: Float): Boolean {
        val current = impulsePx
        if (abs(current) < 0.25f) {
            if (current != 0f) impulsePx = 0f
            return false
        }
        val decay = exp(-14f * dtSeconds.coerceIn(0f, 0.05f))
        val next = current * decay
        impulsePx = if (abs(next) < 0.25f) 0f else next
        return impulsePx != 0f
    }

    fun clear() {
        if (impulsePx != 0f) impulsePx = 0f
    }

    fun offsetFor(itemIndex: Int): Float {
        val current = impulsePx
        if (current == 0f) return 0f
        val relative = itemIndex - anchorIndex
        if (relative <= 0) return 0f
        val weight = (relative.coerceAtMost(5)) * 0.22f
        return current * weight
    }

    val isActive: Boolean
        get() = impulsePx != 0f
}

@Composable
internal fun lunarLyricsAutoScroll(
    state: LunarLyricsListState,
    autoScrollEnabled: Boolean,
    targetIndexProvider: () -> Int,
    itemCount: Int,
    resumeDelayMillis: Long = 2000L,
): Modifier {
    val latestTargetProvider by rememberUpdatedState(targetIndexProvider)
    val latestItemCount by rememberUpdatedState(itemCount)
    var userInteractionTick by remember(state) { mutableIntStateOf(0) }
    var isDragging by remember(state) { mutableStateOf(false) }

    DisposableEffect(state) {
        onDispose {
            state.isManualScrolling = false
            state.cascade.clear()
        }
    }

    val nestedScrollConnection =
        remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        state.isManualScrolling = true
                        state.clickSeekTargetIndex = -1
                        state.cascade.clear()
                        userInteractionTick++
                    }
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(state) {
        state.lazyListState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isDragging = true
                    state.isManualScrolling = true
                    state.clickSeekTargetIndex = -1
                    state.cascade.clear()
                    userInteractionTick++
                }
                is DragInteraction.Stop,
                is DragInteraction.Cancel,
                -> {
                    isDragging = false
                    userInteractionTick++
                }
            }
        }
    }

    LaunchedEffect(state, resumeDelayMillis) {
        snapshotFlow {
            Triple(userInteractionTick, isDragging, state.lazyListState.isScrollInProgress)
        }.collectLatest { (_, dragging, scrolling) ->
            if (state.isManualScrolling && !dragging && !scrolling) {
                delay(resumeDelayMillis)
                state.isManualScrolling = false
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.cascade.isActive }
            .collectLatest { active ->
                if (!active) return@collectLatest
                var prevNanos = withFrameNanos { it }
                while (state.cascade.isActive) {
                    val keepGoing =
                        withFrameNanos { nowNanos ->
                            val dt = (nowNanos - prevNanos) / 1_000_000_000f
                            prevNanos = nowNanos
                            state.cascade.step(dt)
                        }
                    if (!keepGoing) break
                }
            }
    }

    LaunchedEffect(state, autoScrollEnabled, itemCount) {
        if (!autoScrollEnabled || itemCount <= 0) return@LaunchedEffect

        if (!state.hasPositionedInitially) {
            val initialIndex = latestTargetProvider().coerceIn(0, itemCount - 1)
            state.hasPositionedInitially = true
            if (
                state.lazyListState.firstVisibleItemIndex != initialIndex ||
                state.lazyListState.firstVisibleItemScrollOffset != 0
            ) {
                state.lazyListState.scrollToItem(initialIndex, 0)
            }
        }

        val scrollAnimatable: Animatable<Float, AnimationVector1D> = Animatable(0f)
        snapshotFlow {
            val count = latestItemCount
            if (count <= 0 || state.isManualScrolling) {
                -1
            } else {
                val clickTarget = state.clickSeekTargetIndex
                val rawTarget = if (clickTarget in 0 until count) clickTarget else latestTargetProvider()
                rawTarget.coerceIn(0, count - 1)
            }
        }.collectLatest { targetIndex ->
            if (targetIndex < 0) return@collectLatest
            if (state.clickSeekTargetIndex == targetIndex && latestTargetProvider() == targetIndex) {
                state.clickSeekTargetIndex = -1
            }

            val visibleInfo =
                state.lazyListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }

            if (visibleInfo != null) {
                val deltaPx = visibleInfo.offset.toFloat()
                if (abs(deltaPx) <= 1.5f) return@collectLatest
                state.cascade.trigger(targetIndex, deltaPx)
                var consumed = 0f
                scrollAnimatable.snapTo(0f)
                state.lazyListState.scroll {
                    scrollAnimatable.animateTo(
                        targetValue = deltaPx,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    ) {
                        val step = value - consumed
                        if (step != 0f) {
                            consumed += scrollBy(step)
                        }
                    }
                    val settledInfo =
                        state.lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == targetIndex }
                    if (settledInfo != null && abs(settledInfo.offset) in 1..24 && !state.isManualScrolling) {
                        scrollBy(settledInfo.offset.toFloat())
                    }
                }
            } else {
                val currentFirst = state.lazyListState.firstVisibleItemIndex
                if (abs(targetIndex - currentFirst) > 10) {
                    val jumpTarget =
                        if (targetIndex > currentFirst) {
                            (targetIndex - 3).coerceAtLeast(0)
                        } else {
                            (targetIndex + 3).coerceAtMost(latestItemCount - 1)
                        }
                    state.lazyListState.scrollToItem(jumpTarget, 0)
                }
                state.lazyListState.animateScrollToItem(targetIndex, 0)
            }
        }
    }

    return Modifier.nestedScroll(nestedScrollConnection)
}

internal fun Modifier.lunarLyricsEdgeFade(
    topFade: Dp,
    bottomFade: Dp,
): Modifier {
    if (topFade <= 0.dp && bottomFade <= 0.dp) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx().coerceIn(0f, size.height * 0.5f)
            val bottomPx = bottomFade.toPx().coerceIn(0f, size.height * 0.5f)
            if (topPx > 0f) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black,
                            startY = 0f,
                            endY = topPx,
                        ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, topPx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomPx > 0f) {
                val startY = size.height - bottomPx
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = startY,
                            endY = size.height,
                        ),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, bottomPx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
