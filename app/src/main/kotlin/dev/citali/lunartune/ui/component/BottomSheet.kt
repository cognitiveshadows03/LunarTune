/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.constants.BottomSheetCalmAnimationSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.constants.BottomSheetAnimationSpec
import dev.citali.lunartune.constants.BottomSheetCollapseAnimationSpec
import dev.citali.lunartune.constants.BottomSheetSoftAnimationSpec

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onDismiss: (() -> Unit)? = null,
    backHandlerEnabled: Boolean = true,
    collapsedGesturesEnabled: Boolean = true,
    /** Extra downward drift (px) for the collapsed row while the navigation
     * bar is hidden; scaled down as the sheet expands so the player is untouched. */
    collapsedOffsetPx: () -> Float = { 0f },
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Draw phase, not layout. This used to be `offset { }`, which re-laid out
                // the whole player (and the mini player inside it) on every frame of the
                // spring — the open/collapse stutter. A translation here is a redraw.
                .graphicsLayer {
                    var y = (state.expandedBound - state.value).toPx().coerceAtLeast(0f)
                    // A minimise spring settles below the collapsed anchor before it
                    // rebounds (damping 0.85). Rendering that dip is what slid the
                    // collapsed row down into the navigation bar's footprint. The settle
                    // keeps its feel; only the part of it that goes below the anchor is
                    // not drawn. Drags, dismissal and the dismissed → collapsed restore
                    // keep their full travel.
                    if (state.isMinimising) {
                        y = y.coerceAtMost((state.expandedBound - state.collapsedBound).toPx())
                    }
                    translationY = y
                }.then(if (collapsedGesturesEnabled || !state.isCollapsed) Modifier.bottomSheetDraggable(state, onDismiss) else Modifier)
                .clip(
                    RoundedCornerShape(
                        topStart = if (!state.isExpanded) 16.dp else 0.dp,
                        topEnd = if (!state.isExpanded) 16.dp else 0.dp,
                    ),
                )
                // drawBehind, not background(): reading progress during composition
                // recomposed this node and its content on every drag frame.
                .drawBehind {
                    drawRect(
                        backgroundColor.copy(
                            alpha = backgroundColor.alpha * state.progress.coerceIn(0f, 1f),
                        ),
                    )
                },
    ) {
        if (state.isExpandedOrExpanding && backHandlerEnabled) {
            BackHandler(onBack = state::collapseSoft)
        }

        if (!state.isCollapsed) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((state.progress - 0.25f) * 4).coerceIn(0f, 1f)
                        },
                content = content,
            )
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            // progress is clamped: during a minimise the underlying value
                            // dips below the anchor, which used to make (1 - progress)
                            // greater than 1 and drift the row further than the bar's
                            // footprint on top of the sheet's own dip.
                            val progress = state.progress.coerceIn(0f, 1f)
                            translationY = collapsedOffsetPx() * (1f - progress)
                            alpha = 1f - (progress * 4).coerceAtMost(1f)
                        }.then(
                            if (collapsedGesturesEnabled) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = state::expandSoft,
                                )
                            } else {
                                Modifier
                            },
                        ).fillMaxWidth()
                        .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    private val animationsDisabled: Boolean,
    private val motionMiniBounce: Boolean = true,
    collapsedBound: Dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
) : DraggableState by draggableState {
    /**
     * Not a constructor constant: the collapsed anchor moves whenever the window
     * insets move (see [onBoundsChanged]), and the sheet has to follow it instead
     * of being rebuilt.
     */
    var collapsedBound by mutableStateOf(collapsedBound)
        private set

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    /** Only the newest settle may clear [isMinimising]. */
    private var settleGeneration = 0

    /**
     * True while a minimise settle (expanded → collapsed) is running. The spring may
     * rebound, but its excursion below the collapsed anchor is not rendered — that dip
     * is what pushed the collapsed row down into the navigation bar.
     */
    var isMinimising by mutableStateOf(false)
        private set

    /** The settle in flight, so a geometry change can retarget it instead of killing it. */
    private var settleJob: Job? = null
    private var settleAnchor: Int? = null
    private var settleSpec: AnimationSpec<Dp>? = null

    private fun beginSettle(minimising: Boolean): Int {
        val generation = ++settleGeneration
        isMinimising = minimising
        return generation
    }

    /**
     * Every settle goes through here so [onBoundsChanged] knows what is in flight and
     * on which spec to continue it.
     */
    private fun settleTo(
        target: Dp,
        animationSpec: AnimationSpec<Dp>,
        anchor: Int,
        minimising: Boolean,
    ) {
        updateAnchor(anchor)
        settleJob?.cancel()
        val generation = beginSettle(minimising)
        settleAnchor = anchor
        settleSpec = animationSpec
        settleJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    animatable.animateTo(target, animationSpec)
                } finally {
                    if (settleGeneration == generation) {
                        settleAnchor = null
                        settleSpec = null
                        settleJob = null
                    }
                    endSettle(generation)
                }
            }
    }

    /**
     * The window insets moved under the sheet.
     *
     * Hiding the status bar in the player takes the bottom inset away, and bringing
     * it back gives it again, so the collapsed anchor shifts by that inset exactly
     * while a minimise is running. Rebuilding the state for that (as `remember(...)`
     * used to) handed the sheet a fresh spring with no floor and no anchor, which is
     * the overshoot that only appeared after the bars had been hidden. Here the same
     * instance keeps its settle, its anchor and its floor and simply follows the new
     * geometry.
     */
    fun onBoundsChanged(
        dismissed: Dp,
        collapsed: Dp,
        expanded: Dp,
    ) {
        if (dismissedBound == dismissed && collapsedBound == collapsed && expandedBound == expanded) return
        // Captured before the bounds move: after that these comparisons are gone.
        val anchor = settleAnchor
        val runningSpec = settleSpec
        val wasMinimising = isMinimising
        val atExpanded = isExpanded
        val atCollapsed = isCollapsed
        val atDismissed = isDismissed
        animatable.updateBounds(dismissed.coerceAtMost(expanded), expanded)
        collapsedBound = collapsed
        val target =
            when (anchor) {
                EXPANDED_ANCHOR -> expanded
                COLLAPSED_ANCHOR -> collapsed
                DISMISSED_ANCHOR -> dismissed
                else -> null
            }
        if (anchor != null && target != null) {
            // Same settle, new anchor: the floor (a minimise stays a minimise) and the
            // spec are carried over so the motion is continuous, not restarted.
            settleTo(target, runningSpec ?: BottomSheetAnimationSpec, anchor, wasMinimising)
        } else if (atExpanded || atCollapsed || atDismissed) {
            // Idle on an anchor that just moved: follow it, nothing is in flight.
            val restingAnchor =
                when {
                    atExpanded -> EXPANDED_ANCHOR
                    atDismissed -> DISMISSED_ANCHOR
                    else -> COLLAPSED_ANCHOR
                }
            // Quiet on purpose: re-anchoring is the paper moving under a sheet that is
            // already at rest, so it must not be another bounce.
            settleTo(
                when (restingAnchor) {
                    EXPANDED_ANCHOR -> expanded
                    DISMISSED_ANCHOR -> dismissed
                    else -> collapsed
                },
                BottomSheetSoftAnimationSpec,
                restingAnchor,
                minimising = false,
            )
        }
    }

    private fun endSettle(generation: Int) {
        if (settleGeneration == generation) isMinimising = false
    }

    /** A pointer drag owns the sheet, so no settle floor may apply to it. */
    fun onDragStart() {
        ++settleGeneration
        isMinimising = false
        settleAnchor = null
        settleSpec = null
        settleJob = null
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        // Restoring from the dismissed anchor travels upwards from below and must keep
        // its slide-in; only a minimise can overshoot past the anchor.
        settleTo(
            target = collapsedBound,
            animationSpec = animationSpec,
            anchor = COLLAPSED_ANCHOR,
            minimising = !animationsDisabled && value >= collapsedBound,
        )
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        settleTo(
            target = expandedBound,
            animationSpec = animationSpec,
            anchor = EXPANDED_ANCHOR,
            minimising = false,
        )
    }

    private fun collapse() {
        collapse(if (animationsDisabled) snap() else if (motionMiniBounce) BottomSheetAnimationSpec else BottomSheetCalmAnimationSpec)
    }

    private fun expand() {
        expand(if (animationsDisabled) snap() else if (motionMiniBounce) BottomSheetAnimationSpec else BottomSheetCalmAnimationSpec)
    }

    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else BottomSheetCollapseAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun dismiss() {
        settleTo(
            target = dismissedBound,
            animationSpec =
                if (animationsDisabled) {
                    snap()
                } else if (motionMiniBounce) {
                    BottomSheetAnimationSpec
                } else {
                    BottomSheetCalmAnimationSpec
                },
            anchor = DISMISSED_ANCHOR,
            minimising = false,
        )
    }

    fun snapTo(value: Dp) {
        settleAnchor = null
        settleSpec = null
        updateAnchor(
            when (value) {
                expandedBound -> EXPANDED_ANCHOR
                collapsedBound -> COLLAPSED_ANCHOR
                dismissedBound -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            },
        )
        beginSettle(false)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> {
                    collapse()
                }

                in l2..l3 -> {
                    expand()
                }

                else -> {
                    Unit
                }
            }
        }
    }

    // One stable instance: a getter made a fresh connection on every recomposition, so a drag
    // that recomposed the player mid-gesture lost isTopReached and never settled (sheet stuck).
    val preUpPostDownNestedScrollConnection: NestedScrollConnection by lazy {
            object : NestedScrollConnection {
                var isTopReached = false

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (isExpanded && available.y < 0) {
                        isTopReached = false
                    }

                    return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                        dispatchRawDelta(available.y)
                        available
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isTopReached) {
                        isTopReached = consumed.y == 0f && available.y > 0
                    }

                    return if (isTopReached && source == NestedScrollSource.UserInput) {
                        dispatchRawDelta(available.y)
                        available
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity =
                    if (isTopReached) {
                        val velocity = -available.y
                        performFling(velocity, null)

                        available
                    } else {
                        Velocity.Zero
                    }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    isTopReached = false
                    return Velocity.Zero
                }
            }
    }
}

const val EXPANDED_ANCHOR = 2
const val COLLAPSED_ANCHOR = 1
const val DISMISSED_ANCHOR = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val animationsDisabled = LocalAnimationsDisabled.current

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable =
        remember {
            Animatable(0.dp, Dp.VectorConverter)
        }

    val state =
        remember(coroutineScope, animationsDisabled) {
            val initialValue =
                when (previousAnchor) {
                    EXPANDED_ANCHOR -> expandedBound
                    COLLAPSED_ANCHOR -> collapsedBound
                    DISMISSED_ANCHOR -> dismissedBound
                    else -> error("Unknown BottomSheet anchor")
                }

            animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.animateTo(initialValue, if (animationsDisabled) snap() else BottomSheetAnimationSpec)
            }

            BottomSheetState(
                draggableState =
                    DraggableState { delta ->
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                        }
                    },
                onAnchorChanged = { previousAnchor = it },
                coroutineScope = coroutineScope,
                animatable = animatable,
                animationsDisabled = animationsDisabled,
                // LunarMotion is gone; the mini player keeps its bounce.
                motionMiniBounce = true,
                collapsedBound = collapsedBound,
                initialAnchor = previousAnchor,
            )
        }

    // Geometry changes are handed to the state instead of rebuilding it (see
    // BottomSheetState.onBoundsChanged): a settle keeps running through them.
    LaunchedEffect(state, dismissedBound, expandedBound, collapsedBound) {
        state.onBoundsChanged(dismissedBound, collapsedBound, expandedBound)
    }

    return state
}

@Composable
fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
): Modifier =
    this.pointerInput(state) {
        val velocityTracker = VelocityTracker()
        // Some devices (seen on vivo/Funtouch) report a few pixels of jitter during a normal tap.
        // With the platform touch slop that already starts a sheet drag, which cancels the tap on
        // the button underneath (queue, lyrics...) and the sheet just twitches and settles back.
        // Require a clearly deliberate movement before the sheet takes over the gesture.
        val dragSlop = viewConfiguration.touchSlop * SheetDragSlopMultiplier

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            var totalDy = 0f
            var dragStarted = false

            while (!dragStarted) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture // a tap: the child handles it
                if (change.isConsumed) return@awaitEachGesture // a child (e.g. a list) took it
                totalDy += change.positionChange().y
                if (abs(totalDy) > dragSlop) {
                    dragStarted = true
                    state.onDragStart()
                    velocityTracker.resetTracking()
                    velocityTracker.addPointerInputChange(change)
                    change.consume()
                    state.dispatchRawDelta(totalDy - sign(totalDy) * dragSlop)
                }
            }

            val completed =
                verticalDrag(pointerId) { change ->
                    velocityTracker.addPointerInputChange(change)
                    state.dispatchRawDelta(change.positionChange().y)
                    change.consume()
                }
            val velocity = -velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            state.performFling(if (completed) velocity else 0f, onDismiss)
        }
    }

private const val SheetDragSlopMultiplier = 2.5f
