/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Portions adapted from Accompanist Lyrics UI (Copyright 2026 Accompanist contributors),
 * licensed under the Apache License, Version 2.0.
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private const val CLICK_FOLLOW_TIMEOUT_MILLIS = 1500L

/**
 * The leading item's scroll keeps its configured duration/easing; following items spring behind it.
 */
@Immutable
data class LunarScrollChain(
    val stiffness: Float = 100f,
    val damping: Float = 12f,
    val coupling: Float = 0.65f,
    val distanceFalloff: Float = 0.25f,
    val minResponse: Float = 0.35f,
) {
    init {
        require(stiffness.isFinite() && stiffness in 1f..1000f)
        require(damping.isFinite() && damping in 1f..100f)
        require(coupling.isFinite() && coupling in 0f..0.95f)
        require(distanceFalloff.isFinite() && distanceFalloff in 0f..1f)
        require(minResponse.isFinite() && minResponse in 0.05f..1f)
    }
}

@Immutable
internal data class LunarListItem(
    val key: Any,
    val estimatedHeightPx: Int,
    val settledHeightPx: (() -> Int)? = null,
    val focusOffsetPx: (() -> Int)? = null,
    val preserveAnchorOnHeightChange: Boolean = true,
) {
    init {
        require(estimatedHeightPx >= 0)
    }
}

internal data class LunarFollowRequest(
    val index: Int,
    val focusEnd: Int,
)

private sealed interface LunarFollowEvent {
    data class Request(
        val value: LunarFollowRequest,
    ) : LunarFollowEvent

    data object Geometry : LunarFollowEvent

    data object AnimationFrame : LunarFollowEvent

    data object Finished : LunarFollowEvent
}

/** Fenwick tree over item heights + spacing for O(log N) prefix top and item lookup. */
internal class LunarHeightIndex(
    heights: IntArray,
    private val spacing: Int,
) {
    private val values = heights.copyOf()
    private val tree = DoubleArray(values.size + 1)

    val size: Int
        get() = values.size

    private var totalExtent = 0.0
    private val highestBit =
        if (values.isEmpty()) 0 else 1 shl (31 - values.size.countLeadingZeroBits())

    init {
        for (i in values.indices) {
            val value = extent(i)
            totalExtent += value
            tree[i + 1] += value
            val parent = (i + 1) + ((i + 1) and -(i + 1))
            if (parent < tree.size) tree[parent] += tree[i + 1]
        }
    }

    private fun extent(index: Int): Double =
        values[index].toDouble() + if (index < size - 1) spacing else 0

    fun height(index: Int): Int = values[index]

    fun update(
        index: Int,
        height: Int,
    ) {
        val delta = height.toDouble() - values[index]
        if (delta == 0.0) return
        totalExtent += delta
        values[index] = height
        var i = index + 1
        while (i < tree.size) {
            tree[i] += delta
            i += i and -i
        }
    }

    fun top(index: Int): Double {
        var i = index.coerceIn(0, size)
        var sum = 0.0
        while (i > 0) {
            sum += tree[i]
            i -= i and -i
        }
        return sum
    }

    val total: Double
        get() = totalExtent

    fun itemAt(position: Double): Int {
        if (size == 0) return 0
        var index = 0
        var sum = 0.0
        var bit = highestBit
        while (bit != 0) {
            val next = index + bit
            if (next <= size && sum + tree[next] <= position) {
                sum += tree[next]
                index = next
            }
            bit = bit shr 1
        }
        return index.coerceAtMost(size - 1)
    }
}

/**
 * Carries incoming velocity smoothly across duration-based retargets via a cubic Hermite
 * velocity-correction term that vanishes with zero slope at the endpoint.
 */
internal fun lunarFollowRetargetSpec(spec: AnimationSpec<Float>): AnimationSpec<Float> =
    if (spec is DurationBasedAnimationSpec<Float>) LunarFollowRetargetSpec(spec) else spec

internal class LunarFollowRetargetSpec(
    source: DurationBasedAnimationSpec<Float>,
) : FloatAnimationSpec {
    private val native = source.vectorize(Float.VectorConverter)
    private var initial = Float.NaN
    private var target = Float.NaN
    private var velocity = Float.NaN
    private lateinit var initialVector: AnimationVector1D
    private lateinit var targetVector: AnimationVector1D
    private lateinit var velocityVector: AnimationVector1D
    private var duration = 0L
    private var durationSeconds = 0f
    private var velocityCorrection = 0f

    private fun bind(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ) {
        if (initial == initialValue && target == targetValue && velocity == initialVelocity) return
        initial = initialValue
        target = targetValue
        velocity = initialVelocity
        initialVector = AnimationVector1D(initialValue)
        targetVector = AnimationVector1D(targetValue)
        velocityVector = AnimationVector1D(initialVelocity)
        duration = native.getDurationNanos(initialVector, targetVector, velocityVector)
        durationSeconds = duration / 1_000_000_000f
        val sampleTime = minOf(1_000_000L, duration)
        val start = native.getValueFromNanos(0L, initialVector, targetVector, velocityVector).value
        val next = native.getValueFromNanos(sampleTime, initialVector, targetVector, velocityVector).value
        val nativeStartVelocity =
            if (sampleTime > 0) (next - start) / (sampleTime / 1_000_000_000f) else 0f
        velocityCorrection = initialVelocity - nativeStartVelocity
    }

    override fun getDurationNanos(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Long {
        bind(initialValue, targetValue, initialVelocity)
        return duration
    }

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        bind(initialValue, targetValue, initialVelocity)
        val value = native.getValueFromNanos(playTimeNanos, initialVector, targetVector, velocityVector).value
        if (duration <= 0) return value
        val t = (playTimeNanos.toDouble() / duration).toFloat().coerceIn(0f, 1f)
        return value + velocityCorrection * durationSeconds * t * (1f - t) * (1f - t)
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        bind(initialValue, targetValue, initialVelocity)
        if (playTimeNanos <= 0L && duration > 0L) return initialVelocity
        val value = native.getVelocityFromNanos(playTimeNanos, initialVector, targetVector, velocityVector).value
        if (duration <= 0) return value
        val t = (playTimeNanos.toDouble() / duration).toFloat().coerceIn(0f, 1f)
        return value + velocityCorrection * (1f - t) * (1f - 3f * t)
    }
}

/**
 * 240Hz sub-stepped coupled per-row Hookean spring cascade.
 * Focused and preceding items ride the scroll actor directly; trailing items spring behind their
 * immediate predecessor with distance-decayed stiffness and damping.
 */
@Stable
internal class LunarScrollChainState {
    private var settings: LunarScrollChain? = null
    private var positions = DoubleArray(0)
    private var velocities = FloatArray(0)
    private var offsets = emptyArray<MutableFloatState>()
    private var stiffness = FloatArray(0)
    private var damping = FloatArray(0)
    private var layoutTops = DoubleArray(0)
    private var base = 0.0
    private var limit = 1200f
    private var focus = 0
    private var first = 0
    private var end = 0

    var active by mutableStateOf(false)
        private set

    fun configure(
        count: Int,
        chain: LunarScrollChain?,
        position: Double,
        viewport: Int,
    ) {
        val resized = count != offsets.size
        val changed = settings != chain || resized
        settings = chain
        limit = viewport.coerceAtLeast(1) * 1.5f
        if (resized) {
            positions = DoubleArray(count) { position }
            velocities = FloatArray(count)
            offsets = Array(count) { mutableFloatStateOf(0f) }
            layoutTops = DoubleArray(count) { Double.NaN }
            first = 0
            end = 0
        }
        if (chain == null) {
            reset(position)
            return
        }
        if (changed) {
            stiffness =
                FloatArray(64) { distance ->
                    val response =
                        if (distance == 0) {
                            1f
                        } else {
                            maxOf(chain.minResponse, (1f - chain.distanceFalloff).pow(distance))
                        }
                    chain.stiffness * response
                }
            damping =
                FloatArray(64) { distance ->
                    val response =
                        if (distance == 0) {
                            1f
                        } else {
                            maxOf(chain.minResponse, (1f - chain.distanceFalloff).pow(distance))
                        }
                    chain.damping * response.pow(0.3f)
                }
            if (resized) reset(position)
        }
    }

    fun offset(index: Int): Float =
        if (settings == null || index !in offsets.indices) 0f else offsets[index].floatValue

    fun focusAt(
        index: Int,
        scrollVelocity: Float = 0f,
    ) {
        val next = index.coerceAtLeast(0)
        val previous = focus
        if (settings != null && next != previous && scrollVelocity != 0f) {
            for (i in first until end) {
                val wasDirect = i <= previous
                val isDirect = i <= next
                if (wasDirect && !isDirect) {
                    velocities[i] += scrollVelocity
                } else if (!wasDirect && isDirect) {
                    velocities[i] -= scrollVelocity
                }
            }
        }
        focus = next
    }

    fun reset(position: Double) {
        base = position
        for (i in first until end) {
            positions[i] = position
            velocities[i] = 0f
            offsets[i].floatValue = 0f
        }
        active = false
    }

    fun rebase(position: Double) {
        val delta = position - base
        if (delta == 0.0) return
        base = position
        for (i in first until end) positions[i] += delta
    }

    fun retain(
        newFirst: Int,
        newEnd: Int,
    ) {
        val clampedFirst = newFirst.coerceIn(0, offsets.size)
        val clampedEnd = newEnd.coerceIn(clampedFirst, offsets.size)
        for (i in first until minOf(end, clampedFirst)) clear(i)
        for (i in maxOf(first, clampedEnd) until end) clear(i)
        for (i in clampedFirst until minOf(first, clampedEnd)) seed(i)
        for (i in maxOf(end, clampedFirst) until clampedEnd) seed(i)
        first = clampedFirst
        end = clampedEnd
    }

    private fun clear(index: Int) {
        positions[index] = base
        velocities[index] = 0f
        offsets[index].floatValue = 0f
        layoutTops[index] = Double.NaN
    }

    private fun seed(index: Int) {
        val reference =
            when {
                first >= end -> -1
                index < first -> first
                index >= end -> end - 1
                else -> index
            }
        val offset =
            if (settings == null || reference !in offsets.indices || index <= focus) {
                0f
            } else {
                offsets[reference].floatValue
            }
        positions[index] = base - offset
        velocities[index] =
            if (reference in velocities.indices && index > focus) velocities[reference] else 0f
        offsets[index].floatValue = offset
        layoutTops[index] = Double.NaN
    }

    fun applyContentShift(
        fromExclusive: Int,
        delta: Double,
    ) {
        if (delta == 0.0) return
        val start = maxOf(first, fromExclusive + 1)
        for (i in start until end) {
            if (!layoutTops[i].isNaN()) layoutTops[i] += delta
        }
    }

    fun layoutAt(
        index: Int,
        itemTop: Double,
        anchorCorrection: Double = 0.0,
        preserveScreenPosition: Boolean = true,
    ) {
        if (index !in layoutTops.indices) return
        val previous = layoutTops[index]
        layoutTops[index] = itemTop
        if (settings == null || previous.isNaN()) return
        val delta = itemTop - anchorCorrection - previous
        if (abs(delta) < 0.5) return
        if (!preserveScreenPosition) return
        val offset = (offsets[index].floatValue - delta.toFloat()).coerceIn(-limit, limit)
        positions[index] = base - offset
        offsets[index].floatValue = offset
        if (abs(offset) > 0.08f) active = true
    }

    fun followScrollTo(position: Double) {
        if (settings == null) {
            rebase(position)
            return
        }
        val delta = position - base
        if (delta == 0.0) return
        base = position
        var moving = false
        for (i in first until end) {
            if (i <= focus) positions[i] += delta
            val offset = (base - positions[i]).toFloat().coerceIn(-limit, limit)
            positions[i] = base - offset
            offsets[i].floatValue = offset
            if (abs(offset) > 0.08f || abs(velocities[i]) > 0.08f) moving = true
        }
        active = moving
    }

    fun advance(seconds: Float) {
        val config = settings ?: return
        if (!active || seconds <= 0f) return
        val dt = seconds.coerceAtMost(0.05f)
        val steps = ceil(dt / (1f / 240f)).toInt().coerceAtLeast(1)
        val step = dt / steps
        repeat(steps) {
            for (i in end - 1 downTo first) {
                val distance = (i - focus).coerceAtLeast(0).coerceAtMost(stiffness.lastIndex)
                val neighbour = if (i > focus && i > first) positions[i - 1] - base else 0.0
                val target = base + neighbour * config.coupling
                val acceleration =
                    -stiffness[distance] * (positions[i] - target) -
                        damping[distance] * velocities[i]
                velocities[i] += (acceleration * step).toFloat()
                positions[i] += velocities[i] * step
                if (abs(positions[i] - base) > limit) {
                    positions[i] =
                        base + (positions[i] - base).coerceIn(-limit.toDouble(), limit.toDouble())
                    velocities[i] = 0f
                }
            }
        }
        var moving = false
        for (i in first until end) {
            if (abs(positions[i] - base) > 0.08 || abs(velocities[i]) > 0.08f) moving = true
            offsets[i].floatValue = (base - positions[i]).toFloat()
        }
        if (!moving) reset(base)
    }
}

private fun prefetchClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeNanoseconds }
}

@OptIn(ExperimentalFoundationApi::class)
internal class LunarItemPrefetch(
    val state: LazyLayoutPrefetchState,
    private val nowNanos: () -> Long = prefetchClock(),
    private val request: (Int, Constraints) -> (() -> Unit) = { index, constraints ->
        val handle = state.schedulePrecompositionAndPremeasure(index, constraints)
        val cancel: () -> Unit = { handle.cancel() }
        cancel
    },
) {
    private var first = 0
    private var end = 0
    private var count = 0
    private var forward = true
    private var constraints = Constraints()
    private var averageExtent = 200f
    private var viewport = 600f
    private var velocity = 0f
    private var lastSample = Long.MIN_VALUE
    private var hasDirection = false
    private var pendingReverseDistance = 0f
    private var scheduledNext = Int.MIN_VALUE
    private var scheduledDirection = 0
    private var scheduledDesired = -1
    private val requested = IntArray(3) { -1 }
    private val cancellations = arrayOfNulls<() -> Unit>(3)

    fun measured(
        first: Int,
        end: Int,
        count: Int,
        constraints: Constraints,
        averageExtent: Float = 200f,
        viewport: Float = 600f,
    ) {
        if (this.constraints != constraints) cancel()
        this.first = first
        this.end = end
        this.count = count
        this.constraints = constraints
        this.averageExtent = averageExtent.coerceAtLeast(1f)
        this.viewport = viewport.coerceAtLeast(1f)
        if (pendingReverseDistance == 0f) schedule()
    }

    fun onScroll(
        delta: Float,
        retainDirectionOnSmallReversal: Boolean = false,
    ) {
        if (delta == 0f) return
        val now = nowNanos()
        val elapsedMs = if (lastSample == Long.MIN_VALUE) 16f else (now - lastSample) / 1_000_000f
        val newForward = delta > 0f
        var directionChanged = false
        if (!hasDirection) {
            directionChanged = newForward != forward
            forward = newForward
            hasDirection = true
        } else if (newForward != forward) {
            if (retainDirectionOnSmallReversal) {
                pendingReverseDistance += abs(delta)
                if (pendingReverseDistance < maxOf(24f, averageExtent * 0.25f)) {
                    velocity = 0f
                    lastSample = now
                    return
                }
            }
            forward = newForward
            pendingReverseDistance = 0f
            velocity = 0f
            directionChanged = true
        } else {
            pendingReverseDistance = 0f
        }
        if (elapsedMs > 250f) velocity = 0f
        val sample = abs(delta) / elapsedMs.coerceAtLeast(8f)
        velocity = if (velocity == 0f) sample else velocity * 0.65f + sample * 0.35f
        lastSample = now
        if (directionChanged || desiredCount() != scheduledDesired) schedule()
    }

    private fun schedule() {
        val desired = desiredCount()
        val next = if (forward) end else first - 1
        val direction = if (forward) 1 else -1
        if (
            next == scheduledNext &&
            direction == scheduledDirection &&
            desired == scheduledDesired
        ) {
            return
        }
        scheduledNext = next
        scheduledDirection = direction
        scheduledDesired = desired
        for (slot in requested.indices) {
            val distance = (requested[slot] - next) * direction
            if (
                requested[slot] >= 0 &&
                (distance !in 0 until desired || requested[slot] !in 0 until count)
            ) {
                cancellations[slot]?.invoke()
                cancellations[slot] = null
                requested[slot] = -1
            }
        }
        for (distance in 0 until desired) {
            val index = next + distance * direction
            if (index !in 0 until count || index in requested) continue
            val slot = requested.indexOf(-1)
            if (slot < 0) break
            requested[slot] = index
            cancellations[slot] = request(index, constraints)
        }
    }

    private fun desiredCount(): Int {
        val stale = lastSample != Long.MIN_VALUE && nowNanos() - lastSample > 250_000_000L
        if (stale) velocity = 0f
        val horizon = (velocity * 120f).coerceAtMost(viewport)
        return ceil(horizon / averageExtent).toInt().coerceIn(1, 3)
    }

    fun cancel() {
        for (slot in requested.indices) {
            cancellations[slot]?.invoke()
            cancellations[slot] = null
            requested[slot] = -1
        }
        velocity = 0f
        lastSample = Long.MIN_VALUE
        hasDirection = false
        pendingReverseDistance = 0f
        scheduledNext = Int.MIN_VALUE
        scheduledDirection = 0
        scheduledDesired = -1
    }
}

/**
 * Lyrics-specific vertical virtual list state driving [LunarLyricsLazyColumn] with velocity-continuous
 * retargeting and [LunarScrollChainState] per-row spring cascade physics, plus instant Frame-0 initial
 * positioning when opening cached lyrics mid-song.
 */
@Stable
class LunarLyricsListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    hasPositionedInitially: Boolean = false,
) : ScrollableState {
    var firstVisibleItemIndex by mutableIntStateOf(initialFirstVisibleItemIndex.coerceAtLeast(0))
        private set

    var firstVisibleItemScrollOffset by mutableIntStateOf(initialFirstVisibleItemScrollOffset.coerceAtLeast(0))
        private set

    var hasPositionedInitially by mutableStateOf(hasPositionedInitially)
        internal set

    internal val chain = LunarScrollChainState()
    private var followingChain = false
    private var predictingFollow = false
    internal var followAnchorIndex = -1
        private set

    internal var interludeItemIndex = -1
    private var clearInterludeAfterMeasure = false
    internal var anchoringContentChange = false
        private set

    private var contentCoordinateShift = 0.0
    private var onCaptionGeometryChange: (() -> Unit)? = null
    private var captionReflowRunning = false

    internal var layoutRevision by mutableIntStateOf(0)
        private set

    internal fun recordLayoutChange() {
        layoutRevision++
    }

    internal fun preserveFollowAnchorForContentChange() {
        captionReflowRunning = true
        if (!isManualScrolling && followAnchorIndex in items.indices) {
            anchoringContentChange = true
            onCaptionGeometryChange?.invoke()
        }
    }

    internal fun finishCaptionContentChange() {
        captionReflowRunning = false
    }

    internal fun setInterludeItem(index: Int) {
        if (index >= 0) {
            interludeItemIndex = index
            clearInterludeAfterMeasure = false
        } else if (interludeItemIndex >= 0) {
            clearInterludeAfterMeasure = true
        }
    }

    internal fun finishInterludeMeasure() {
        if (clearInterludeAfterMeasure) {
            interludeItemIndex = -1
            clearInterludeAfterMeasure = false
        }
    }

    internal fun compensateContentHeightChange(delta: Int) {
        position += delta
        contentCoordinateShift += delta
    }

    internal val predictedFollowActive: Boolean
        get() = predictingFollow && followingChain

    internal val hasPredictedScrollGeometry: Boolean
        get() = predictingFollow && (followingChain || chain.active)

    internal var retainedFirst = 0
    internal var retainedEnd = 0
    private var autoFollowSuspended = false

    internal var position by mutableDoubleStateOf(0.0)
    internal var maxPosition by mutableDoubleStateOf(0.0)
    internal var ready by mutableStateOf(false)
    internal var remeasurement: Remeasurement? = null
    internal var tryPlacementScroll: (() -> Boolean)? = null
    internal var onScrollPrefetch: ((Float, Boolean) -> Unit)? = null

    internal var heights = LunarHeightIndex(intArrayOf(), 0)
    internal var items: List<LunarListItem> = emptyList()
    private var configuredWidth = -1
    private var configuredSpacing = -1
    private var keys: Map<Any, Int> = emptyMap()
    private var pendingIndex: Int? = initialFirstVisibleItemIndex.coerceAtLeast(0)
    private var pendingOffset = initialFirstVisibleItemScrollOffset.coerceAtLeast(0)

    val interactionSource = MutableInteractionSource()

    var isManualScrolling by mutableStateOf(false)
        internal set

    internal var resumeRequest by mutableIntStateOf(0)
        private set

    internal var onImmediateFollowRequest: ((Int) -> Unit)? = null

    internal val requestedClickTarget: Int
        get() = clickTargetIndex

    internal val clickTargetRevision: Int
        get() = clickTargetRevisionState

    private var clickPosition: Int? = null
    private var clickTime = TimeSource.Monotonic.markNow()
    private var clickTargetIndex by mutableIntStateOf(-1)
    private var clickTargetRevisionState by mutableIntStateOf(0)

    /**
     * Seeds the initial scroll target synchronously before Frame 0's first measure pass so
     * opening cached lyrics mid-song lands immediately on the active line without animating
     * from item 0.
     */
    internal fun ensureInitialPosition(
        targetIndex: Int,
        itemCount: Int,
    ) {
        if (hasPositionedInitially || itemCount <= 0) return
        snapToTargetImmediately(targetIndex, itemCount)
    }

    internal fun snapToTargetImmediately(
        targetIndex: Int,
        itemCount: Int,
    ) {
        if (itemCount <= 0 || isManualScrolling) return
        val clamped = targetIndex.coerceIn(0, itemCount - 1)
        Snapshot.withoutReadObservation {
            hasPositionedInitially = true
            followAnchorIndex = clamped
            chain.focusAt(clamped)
            if (!ready || items.isEmpty()) {
                firstVisibleItemIndex = clamped
                firstVisibleItemScrollOffset = 0
                pendingIndex = clamped
                pendingOffset = 0
            } else {
                position = heights.top(clamped.coerceAtMost(items.lastIndex)).coerceIn(0.0, maxPosition)
                firstVisibleItemIndex = heights.itemAt(position)
                firstVisibleItemScrollOffset =
                    (position - heights.top(firstVisibleItemIndex)).coerceAtLeast(0.0).roundToInt()
                chain.reset(position)
                remeasurement?.forceRemeasure()
            }
        }
    }

    internal fun suspendAutoFollow() {
        anchoringContentChange = false
        autoFollowSuspended = true
        predictingFollow = false
        clickTargetIndex = -1
        clickTargetRevisionState++
    }

    fun resumeAutoScroll(
        seekPosition: Int? = null,
        targetIndex: Int? = null,
    ) {
        require(targetIndex == null || targetIndex >= 0)
        clickPosition = seekPosition
        clickTime = TimeSource.Monotonic.markNow()
        clickTargetIndex = targetIndex ?: -1
        clickTargetRevisionState++
        isManualScrolling = false
        resumeRequest++
        targetIndex?.let { onImmediateFollowRequest?.invoke(it) }
    }

    internal fun clearClickTarget(revision: Int) {
        if (clickTargetRevisionState == revision) {
            clickTargetIndex = -1
        }
    }

    internal fun consumeClickSeek(time: Int): Boolean {
        val expected = clickPosition ?: return false
        if (clickTime.elapsedNow().inWholeMilliseconds > CLICK_FOLLOW_TIMEOUT_MILLIS) {
            clickPosition = null
            return false
        }
        if (abs(time.toLong() - expected) > 600) return false
        clickPosition = null
        return true
    }

    private val scrollState =
        ScrollableState { delta ->
            val previous = position
            position = (previous + delta).coerceIn(0.0, maxPosition)
            if (position != previous) {
                if (followingChain && !autoFollowSuspended) {
                    chain.followScrollTo(position)
                } else {
                    chain.rebase(position)
                }
                if (tryPlacementScroll?.invoke() != true) remeasurement?.forceRemeasure()
                onScrollPrefetch?.invoke((position - previous).toFloat(), followingChain)
            }
            (position - previous).toFloat()
        }

    override val isScrollInProgress: Boolean
        get() = scrollState.isScrollInProgress

    override val canScrollForward: Boolean
        get() = position < maxPosition

    override val canScrollBackward: Boolean
        get() = position > 0.0

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) = scrollState.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float = scrollState.dispatchRawDelta(delta)

    internal fun configure(
        newItems: List<LunarListItem>,
        newKeys: Map<Any, Int>,
        width: Int,
        spacing: Int,
        top: Int,
        bottom: Int,
        viewport: Int,
    ) {
        if (items !== newItems || configuredWidth != width || configuredSpacing != spacing) {
            val anchorKey = items.getOrNull(firstVisibleItemIndex)?.key
            val anchorOffset = firstVisibleItemScrollOffset
            keys = newKeys
            items = newItems
            heights = LunarHeightIndex(IntArray(items.size) { items[it].estimatedHeightPx }, spacing)
            configuredWidth = width
            configuredSpacing = spacing
            recordLayoutChange()
            chain.reset(position)
            if (pendingIndex == null) {
                val anchor =
                    (keys[anchorKey] ?: firstVisibleItemIndex).coerceIn(
                        0,
                        (items.size - 1).coerceAtLeast(0),
                    )
                position = heights.top(anchor) + anchorOffset
            }
        }
        updateRange(top, bottom, viewport)
        pendingIndex?.let {
            if (items.isNotEmpty()) {
                position =
                    (heights.top(it.coerceAtMost(items.lastIndex)) + pendingOffset).coerceIn(
                        0.0,
                        maxPosition,
                    )
                firstVisibleItemIndex = heights.itemAt(position)
                firstVisibleItemScrollOffset =
                    (position - heights.top(firstVisibleItemIndex)).coerceAtLeast(0.0).roundToInt()
                chain.reset(position)
                pendingIndex = null
            }
        }
        ready = true
    }

    internal fun updateRange(
        top: Int,
        bottom: Int,
        viewport: Int,
    ) {
        val previousMaxPosition = maxPosition
        maxPosition =
            if (heights.size == 0) {
                0.0
            } else {
                (top + heights.total + bottom - viewport).coerceAtLeast(0.0)
            }
        if (maxPosition != previousMaxPosition) recordLayoutChange()
        position = position.coerceIn(0.0, maxPosition)
        firstVisibleItemIndex = heights.itemAt(position)
        firstVisibleItemScrollOffset =
            (position - heights.top(firstVisibleItemIndex)).coerceAtLeast(0.0).roundToInt()
    }

    suspend fun scrollToItem(
        index: Int,
        scrollOffset: Int = 0,
    ) {
        require(index >= 0)
        snapshotFlow { ready }.first { it }
        if (items.isEmpty()) return
        scroll {
            suspendAutoFollow()
            position =
                (heights.top(index.coerceAtMost(items.lastIndex)) + scrollOffset).coerceIn(
                    0.0,
                    maxPosition,
                )
            chain.reset(position)
            remeasurement?.forceRemeasure()
        }
    }

    suspend fun animateScrollToItem(
        index: Int,
        scrollOffset: Int = 0,
        animationSpec: AnimationSpec<Float> = tween(650, easing = FastOutSlowInEasing),
    ) {
        require(index >= 0)
        snapshotFlow { ready }.first { it }
        if (items.isEmpty()) return
        scroll {
            followingChain = false
            autoFollowSuspended = true
            chain.focusAt(index)
            chain.reset(position)
            val targetIndex = index.coerceAtMost(items.lastIndex)
            val start = position
            fun targetPosition() = (heights.top(targetIndex) + scrollOffset).coerceIn(0.0, maxPosition)
            val distance = targetPosition() - start
            if (abs(distance) < 0.5) return@scroll
            animate(0f, distance.toFloat(), animationSpec = animationSpec) { travelled, _ ->
                val fraction = travelled / distance
                val target = targetPosition()
                scrollBy((start + (target - start) * fraction - position).toFloat())
            }
            scrollBy((targetPosition() - position).toFloat())
        }
    }

    /**
     * Keeps one scroll mutation alive while following changing lyric targets, preserving
     * position, velocity, and frame timestamps across retargets.
     */
    internal suspend fun animateFollowRequests(
        requests: ReceiveChannel<LunarFollowRequest>,
        animationSpec: AnimationSpec<Float>,
    ) = coroutineScope {
        var interruptFollow: ((Int) -> Unit)? = null
        val previousImmediateFollowRequest = onImmediateFollowRequest
        val previousCaptionGeometryChange = onCaptionGeometryChange
        val immediateFollowRequest: (Int) -> Unit = { index ->
            previousImmediateFollowRequest?.invoke(index)
            interruptFollow?.invoke(index)
        }
        onImmediateFollowRequest = immediateFollowRequest
        try {
            while (isActive) {
                var initial = requests.receive()
                if (initial.index < 0) continue
                snapshotFlow { ready && items.isNotEmpty() }.first { it }
                while (true) {
                    val pending = requests.tryReceive().getOrNull() ?: break
                    initial = pending
                }
                if (initial.index < 0) continue

                val scrollJob =
                    launch {
                        try {
                            scroll {
                                val scrollScope = this
                                followingChain = true
                                autoFollowSuspended = false
                                var animator =
                                    AnimationState((position - contentCoordinateShift).toFloat())
                                var request by mutableStateOf(initial)
                                var requestCoordinateShift = contentCoordinateShift
                                var captionTargetOffset = 0.0
                                var measuredCaptionTarget = anchoringContentChange

                                fun updateRequest(
                                    next: LunarFollowRequest,
                                    scrollVelocity: Float = animator.velocity,
                                ) {
                                    if (followAnchorIndex != next.index || captionReflowRunning) {
                                        anchoringContentChange = captionReflowRunning
                                    }
                                    followAnchorIndex = next.index
                                    captionTargetOffset = 0.0
                                    measuredCaptionTarget = anchoringContentChange
                                    request = next
                                    requestCoordinateShift = contentCoordinateShift
                                    followingChain = true
                                    autoFollowSuspended = false
                                    chain.focusAt(next.focusEnd, scrollVelocity)
                                    val targetIndex = next.index.coerceAtMost(items.lastIndex)
                                    predictingFollow =
                                        items[targetIndex].settledHeightPx != null &&
                                            targetIndex in retainedFirst until retainedEnd
                                }

                                fun targetPosition(target: LunarFollowRequest): Double {
                                    val targetIndex = target.index.coerceAtMost(items.lastIndex)
                                    var destination = heights.top(targetIndex)
                                    val predictSettledHeight =
                                        items[targetIndex].settledHeightPx != null &&
                                            targetIndex in retainedFirst until retainedEnd
                                    if (predictSettledHeight) {
                                        for (i in retainedFirst until minOf(retainedEnd, targetIndex)) {
                                            items[i].settledHeightPx?.let {
                                                destination += it() - heights.height(i)
                                            }
                                        }
                                        destination += items[targetIndex].focusOffsetPx?.invoke() ?: 0
                                        destination += contentCoordinateShift - requestCoordinateShift
                                    }
                                    return destination.coerceIn(0.0, maxPosition)
                                }

                                fun logicalTarget(target: LunarFollowRequest): Double {
                                    if (!measuredCaptionTarget) {
                                        return targetPosition(target) - contentCoordinateShift
                                    }
                                    val index = target.index.coerceAtMost(items.lastIndex)
                                    return heights.top(index) +
                                        (items[index].focusOffsetPx?.invoke() ?: 0) -
                                        contentCoordinateShift +
                                        if (target.index == request.index) captionTargetOffset else 0.0
                                }

                                updateRequest(initial)
                                coroutineScope {
                                    val geometryUpdates = Channel<Unit>(Channel.CONFLATED)
                                    val animationFrames = Channel<Unit>(Channel.CONFLATED)

                                    fun startAnimation(
                                        destination: Double,
                                        spec: AnimationSpec<Float>,
                                        initialVelocity: Float,
                                        retargeted: Boolean = false,
                                        carryFrameTime: Boolean = retargeted,
                                    ): Job =
                                        launch {
                                            animator =
                                                AnimationState(
                                                    animator.value,
                                                    initialVelocity,
                                                    lastFrameTimeNanos = animator.lastFrameTimeNanos,
                                                )
                                            animator.animateTo(
                                                destination.toFloat(),
                                                spec,
                                                sequentialAnimation = carryFrameTime,
                                            ) {
                                                scrollScope.scrollBy(
                                                    (value + contentCoordinateShift - position).toFloat(),
                                                )
                                                animationFrames.trySend(Unit)
                                            }
                                        }

                                    val geometryObserver =
                                        launch {
                                            snapshotFlow { layoutRevision }.collect {
                                                geometryUpdates.trySend(Unit)
                                            }
                                        }

                                    var animation =
                                        startAnimation(
                                            logicalTarget(initial),
                                            animationSpec,
                                            animator.velocity,
                                        )
                                    var animationDestination = logicalTarget(initial)
                                    val captionGeometryChange: () -> Unit = {
                                        measuredCaptionTarget = true
                                        captionTargetOffset = 0.0
                                        captionTargetOffset = animationDestination - logicalTarget(request)
                                    }
                                    onCaptionGeometryChange = captionGeometryChange
                                    var handoffVelocity: Float? = null
                                    interruptFollow = { index ->
                                        val next = LunarFollowRequest(index, index)
                                        if (
                                            animation.isActive &&
                                            abs(logicalTarget(next) - logicalTarget(request)) >= 0.5
                                        ) {
                                            handoffVelocity = animator.velocity
                                            animation.cancel()
                                        }
                                    }
                                    var finished = false
                                    try {
                                        while (!finished) {
                                            val event =
                                                select<LunarFollowEvent> {
                                                    requests.onReceive {
                                                        LunarFollowEvent.Request(it)
                                                    }
                                                    geometryUpdates.onReceive {
                                                        LunarFollowEvent.Geometry
                                                    }
                                                    animationFrames.onReceive {
                                                        LunarFollowEvent.AnimationFrame
                                                    }
                                                    animation.onJoin {
                                                        requests.tryReceive().getOrNull()?.let {
                                                            LunarFollowEvent.Request(it)
                                                        } ?: LunarFollowEvent.Finished
                                                    }
                                                }
                                            when (event) {
                                                is LunarFollowEvent.Request -> {
                                                    val next = event.value
                                                    if (next.index < 0 || items.isEmpty()) {
                                                        animation.cancelAndJoin()
                                                        finished = true
                                                    } else {
                                                        val carriedVelocity = handoffVelocity
                                                        handoffVelocity = null
                                                        val wasAnimating =
                                                            animation.isActive || carriedVelocity != null
                                                        val initialVelocity =
                                                            carriedVelocity
                                                                ?: if (wasAnimating) animator.velocity else 0f
                                                        val previousDestination = logicalTarget(request)
                                                        updateRequest(next, initialVelocity)
                                                        val destination = logicalTarget(next)
                                                        val destinationChanged =
                                                            abs(destination - previousDestination) >= 0.5
                                                        val clickCancelledAnimation =
                                                            carriedVelocity != null && !animation.isActive
                                                        if (destinationChanged || clickCancelledAnimation) {
                                                            val retargetSpec =
                                                                if (wasAnimating) {
                                                                    lunarFollowRetargetSpec(animationSpec)
                                                                } else {
                                                                    animationSpec
                                                                }
                                                            animation.cancelAndJoin()
                                                            animationDestination = destination
                                                            animation =
                                                                startAnimation(
                                                                    destination,
                                                                    retargetSpec,
                                                                    initialVelocity,
                                                                    retargeted = wasAnimating,
                                                                )
                                                        }
                                                    }
                                                }

                                                LunarFollowEvent.Geometry -> {
                                                    val wasAnimating = animation.isActive
                                                    if (wasAnimating && animationFrames.tryReceive().isFailure) {
                                                        select<Unit> {
                                                            animationFrames.onReceive { }
                                                            animation.onJoin { }
                                                        }
                                                    }
                                                    val destination = logicalTarget(request)
                                                    if (abs(destination - animationDestination) >= 0.5) {
                                                        val initialVelocity =
                                                            if (wasAnimating) animator.velocity else 0f
                                                        animation.cancelAndJoin()
                                                        animationDestination = destination
                                                        animation =
                                                            startAnimation(
                                                                destination,
                                                                lunarFollowRetargetSpec(animationSpec),
                                                                initialVelocity,
                                                                retargeted = true,
                                                                carryFrameTime = wasAnimating,
                                                            )
                                                    }
                                                }

                                                LunarFollowEvent.AnimationFrame -> Unit

                                                LunarFollowEvent.Finished -> {
                                                    val destination = logicalTarget(request)
                                                    if (abs(destination - animationDestination) >= 0.5) {
                                                        animationDestination = destination
                                                        animation =
                                                            startAnimation(
                                                                destination,
                                                                lunarFollowRetargetSpec(animationSpec),
                                                                0f,
                                                                retargeted = true,
                                                                carryFrameTime = false,
                                                            )
                                                    } else {
                                                        finished = true
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        if (onCaptionGeometryChange === captionGeometryChange) {
                                            onCaptionGeometryChange = previousCaptionGeometryChange
                                        }
                                        interruptFollow = null
                                        geometryObserver.cancelAndJoin()
                                        geometryUpdates.close()
                                        animationFrames.close()
                                    }
                                }
                            }
                        } finally {
                            interruptFollow = null
                            followingChain = false
                            predictingFollow = false
                        }
                    }
                scrollJob.join()
            }
        } finally {
            interruptFollow = null
            if (onImmediateFollowRequest === immediateFollowRequest) {
                onImmediateFollowRequest = previousImmediateFollowRequest
            }
        }
    }

    companion object {
        val Saver: Saver<LunarLyricsListState, Any> =
            listSaver(
                save = { state ->
                    listOf(
                        state.firstVisibleItemIndex,
                        state.firstVisibleItemScrollOffset,
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

private data class LunarFollowTarget(
    val index: Int,
    val end: Int,
    val seek: Int,
    val resume: Int,
)

@Composable
internal fun lunarLyricsAutoScroll(
    state: LunarLyricsListState,
    autoScrollEnabled: Boolean,
    targetIndex: () -> Int,
    focusEndIndex: () -> Int = targetIndex,
    animationSpec: AnimationSpec<Float> = tween(650, easing = FastOutSlowInEasing),
    resumeDelayMillis: Long = 2500L,
    playbackPosition: (() -> Int)? = null,
): Modifier {
    val target by rememberUpdatedState(targetIndex)
    val focusEnd by rememberUpdatedState(focusEndIndex)
    val playback by rememberUpdatedState(playbackPosition)
    val autoEnabled by rememberUpdatedState(autoScrollEnabled)
    var seekRevision by remember(state) { mutableIntStateOf(0) }

    DisposableEffect(state) {
        onDispose {
            state.isManualScrolling = false
            state.clearClickTarget(state.clickTargetRevision)
        }
    }

    var dragging by remember(state) { mutableStateOf(false) }
    var interaction by remember(state) { mutableIntStateOf(0) }
    val connection =
        remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        state.suspendAutoFollow()
                        state.isManualScrolling = true
                        interaction++
                    }
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { event ->
            when (event) {
                is DragInteraction.Start -> {
                    state.suspendAutoFollow()
                    dragging = true
                    state.isManualScrolling = true
                    interaction++
                }

                is DragInteraction.Stop,
                is DragInteraction.Cancel,
                -> {
                    dragging = false
                    interaction++
                }
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.resumeRequest }.collect { state.isManualScrolling = false }
    }

    LaunchedEffect(state, resumeDelayMillis) {
        snapshotFlow { Triple(interaction, dragging, state.isScrollInProgress) }
            .collectLatest { (_, isDragging, scrolling) ->
                if (state.isManualScrolling && !isDragging && !scrolling) {
                    delay(resumeDelayMillis.coerceAtLeast(0L))
                    state.isManualScrolling = false
                }
            }
    }

    LaunchedEffect(state) {
        var previous: Int? = null
        snapshotFlow { playback?.invoke() }
            .collect { time ->
                val last = previous
                if (
                    time != null &&
                    last != null &&
                    (time.toLong() - last < -1L || time.toLong() - last > 600L)
                ) {
                    if (!state.consumeClickSeek(time)) seekRevision++
                }
                previous = time
            }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.clickTargetRevision to state.clickTargetRevision.let { state.requestedClickTarget } }
            .collectLatest { (revision, index) ->
                if (index >= 0) {
                    withTimeoutOrNull(CLICK_FOLLOW_TIMEOUT_MILLIS) {
                        snapshotFlow { target() }.first { it == index }
                    }
                    state.clearClickTarget(revision)
                }
            }
    }

    LaunchedEffect(state, animationSpec) {
        val requests = Channel<LunarFollowRequest>(Channel.CONFLATED)
        val immediateFollowRequest: (Int) -> Unit = { index ->
            requests.trySend(LunarFollowRequest(index, index))
        }
        state.onImmediateFollowRequest = immediateFollowRequest
        try {
            coroutineScope {
                launch {
                    snapshotFlow {
                        val clickTarget = state.requestedClickTarget
                        val resolvedIndex =
                            when {
                                !autoEnabled -> clickTarget.takeIf { it >= 0 } ?: -1
                                state.isManualScrolling -> -1
                                else -> clickTarget.takeIf { it >= 0 } ?: target()
                            }
                        LunarFollowTarget(
                            index = resolvedIndex,
                            end = if (clickTarget >= 0) clickTarget else focusEnd(),
                            seek = seekRevision,
                            resume = state.resumeRequest,
                        )
                    }.collect { request ->
                        requests.send(LunarFollowRequest(request.index, request.end))
                    }
                }
                state.animateFollowRequests(requests, animationSpec)
            }
        } finally {
            if (state.onImmediateFollowRequest === immediateFollowRequest) {
                state.onImmediateFollowRequest = null
            }
        }
    }

    return Modifier.nestedScroll(connection)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LunarLyricsLazyColumn(
    items: List<LunarListItem>,
    state: LunarLyricsListState,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    beyondBounds: Dp = 100.dp,
    userScrollEnabled: Boolean = true,
    scrollChain: LunarScrollChain? = null,
    itemContent: @Composable (Int) -> Unit,
) {
    require(itemSpacing.value.isFinite() && itemSpacing >= 0.dp)
    require(beyondBounds.value.isFinite() && beyondBounds >= 0.dp)
    val scope = rememberCoroutineScope()
    val content by rememberUpdatedState(itemContent)
    val prefetch = remember(items, state) { LunarItemPrefetch(LazyLayoutPrefetchState()) }
    DisposableEffect(prefetch, state) {
        state.onScrollPrefetch = prefetch::onScroll
        onDispose {
            prefetch.cancel()
            state.onScrollPrefetch = null
        }
    }
    val indices =
        remember(items, state) {
            items.withIndex().associate { it.value.key to it.index }.also {
                require(it.size == items.size) { "Lyrics list keys must be unique" }
            }
        }
    val provider =
        remember(items, state, indices) {
            object : LazyLayoutItemProvider {
                override val itemCount: Int
                    get() = items.size

                override fun getKey(index: Int): Any = items[index].key

                override fun getIndex(key: Any): Int = indices[key] ?: -1

                @Composable
                override fun Item(
                    index: Int,
                    key: Any,
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { translationY = state.chain.offset(index) }
                            .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
                    ) {
                        content(index)
                    }
                }
            }
        }
    val remeasureModifier =
        remember(state) {
            object : RemeasurementModifier {
                override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                    state.remeasurement = remeasurement
                }
            }
        }
    DisposableEffect(state) {
        onDispose {
            state.remeasurement = null
            state.tryPlacementScroll = null
            state.ready = false
        }
    }
    LaunchedEffect(state, scrollChain) {
        snapshotFlow { state.chain.active }
            .collectLatest { active ->
                if (active) {
                    var previous = withFrameNanos { it }
                    while (state.chain.active) {
                        withFrameNanos { now ->
                            state.chain.advance((now - previous) / 1_000_000_000f)
                            previous = now
                        }
                    }
                }
            }
    }
    LazyLayout(
        itemProvider = { provider },
        prefetchState = prefetch.state,
        modifier =
            modifier
                .then(remeasureModifier)
                .clipToBounds()
                .scrollable(
                    state,
                    Orientation.Vertical,
                    enabled = userScrollEnabled,
                    reverseDirection = true,
                    interactionSource = state.interactionSource,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                ).semantics {
                    isTraversalGroup = true
                    collectionInfo = CollectionInfo(items.size, 1)
                    verticalScrollAxisRange =
                        ScrollAxisRange(
                            value = { state.position.toFloat() },
                            maxValue = { state.maxPosition.toFloat() },
                        )
                    if (userScrollEnabled) {
                        scrollBy { _, y ->
                            scope.launch { state.animateScrollBy(y) }
                            true
                        }
                        scrollToIndex { index ->
                            if (index !in items.indices) {
                                false
                            } else {
                                scope.launch { state.scrollToItem(index) }
                                true
                            }
                        }
                    }
                },
        measurePolicy =
            LazyLayoutMeasurePolicy { constraints ->
                require(constraints.hasBoundedHeight && constraints.hasBoundedWidth) {
                    "LunarLyricsLazyColumn needs bounded width and height"
                }
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val left = contentPadding.calculateLeftPadding(layoutDirection).roundToPx()
                val right = contentPadding.calculateRightPadding(layoutDirection).roundToPx()
                val top = contentPadding.calculateTopPadding().roundToPx()
                val bottom = contentPadding.calculateBottomPadding().roundToPx()
                val spacing = itemSpacing.roundToPx()
                val buffer =
                    beyondBounds.roundToPx() +
                        if (scrollChain != null && state.chain.active) height else 0
                val childWidth = (width - left - right).coerceAtLeast(0)
                Snapshot.withoutReadObservation {
                    state.configure(items, indices, childWidth, spacing, top, bottom, height)
                    state.chain.configure(items.size, scrollChain, state.position, height)
                }
                val position = Snapshot.withoutReadObservation { state.position }
                val startIndex =
                    state.heights.itemAt((position - top - buffer).coerceAtLeast(0.0))
                val measured = ArrayList<Placeable>()
                val anchor =
                    Snapshot.withoutReadObservation {
                        if (state.anchoringContentChange) {
                            state.followAnchorIndex
                        } else {
                            state.firstVisibleItemIndex
                        }
                    }
                val startTop = state.heights.top(startIndex)
                var itemTop = startTop
                var y = top + itemTop - position
                var index = startIndex
                var layoutChanged = false
                val childConstraints = Constraints(minWidth = childWidth, maxWidth = childWidth)
                while (index < items.size && y < height + buffer) {
                    val placeable = compose(index).single().measure(childConstraints)
                    val previousHeight = state.heights.height(index)
                    state.heights.update(index, placeable.height)
                    val heightDelta = placeable.height - previousHeight
                    if (heightDelta != 0) layoutChanged = true
                    if (index == state.interludeItemIndex && heightDelta != 0) {
                        state.chain.applyContentShift(index, heightDelta.toDouble())
                    }
                    if (index < anchor && state.anchoringContentChange) {
                        Snapshot.withoutReadObservation {
                            state.compensateContentHeightChange(placeable.height - previousHeight)
                        }
                    } else if (
                        index < anchor &&
                        items[index].preserveAnchorOnHeightChange &&
                        !state.hasPredictedScrollGeometry
                    ) {
                        Snapshot.withoutReadObservation {
                            state.position += placeable.height - previousHeight
                        }
                    }
                    measured.add(placeable)
                    index++
                    itemTop += placeable.height.toDouble() + spacing
                    y = top + itemTop - Snapshot.withoutReadObservation { state.position }
                }
                Snapshot.withoutReadObservation {
                    if (layoutChanged) state.recordLayoutChange()
                    state.updateRange(top, bottom, height)
                    state.chain.rebase(state.position)
                }
                state.chain.retain(startIndex, index)
                state.finishInterludeMeasure()
                state.retainedFirst = startIndex
                state.retainedEnd = index
                val anchorCorrection =
                    Snapshot.withoutReadObservation { state.position } - position
                val positions = DoubleArray(measured.size)
                itemTop = startTop
                for (i in measured.indices) {
                    val itemIndex = startIndex + i
                    val preserveScreenPosition =
                        (state.anchoringContentChange || !state.predictedFollowActive) &&
                            (items[itemIndex].preserveAnchorOnHeightChange || itemIndex > anchor)
                    state.chain.layoutAt(
                        startIndex + i,
                        itemTop,
                        anchorCorrection,
                        preserveScreenPosition,
                    )
                    positions[i] = top + itemTop
                    itemTop += measured[i].height.toDouble() + spacing
                }
                val measuredEnd = index
                val endTop = itemTop
                val chainWasActive = state.chain.active
                state.tryPlacementScroll = {
                    val current = state.position
                    val first =
                        state.heights.itemAt((current - top - buffer).coerceAtLeast(0.0))
                    val covered =
                        measuredEnd == items.size || top + endTop >= current + height + buffer
                    if (
                        state.items === items &&
                        first == startIndex &&
                        covered &&
                        state.chain.active == chainWasActive
                    ) {
                        state.updateRange(top, bottom, height)
                        true
                    } else {
                        false
                    }
                }
                prefetch.measured(
                    startIndex,
                    measuredEnd,
                    items.size,
                    childConstraints,
                    ((endTop - startTop) / measured.size.coerceAtLeast(1)).toFloat(),
                    height.toFloat(),
                )
                layout(width, height) {
                    val current = state.position
                    for (i in measured.indices) {
                        measured[i].place(
                            left,
                            (positions[i] - current).roundToInt(),
                        )
                    }
                }
            },
    )
}

internal fun Modifier.lunarLyricsEdgeFade(
    topFade: Dp,
    bottomFade: Dp,
): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx().coerceIn(0f, size.height * 0.45f)
            if (topPx > 0.5f) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = topPx,
                        ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, topPx),
                    blendMode = BlendMode.DstIn,
                )
            }
            val bottomPx = bottomFade.toPx().coerceIn(0f, size.height * 0.55f)
            if (bottomPx > 0.5f) {
                val startY = size.height - bottomPx
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = startY,
                            endY = size.height,
                        ),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, bottomPx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
