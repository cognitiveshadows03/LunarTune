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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.constants.BottomSheetCalmAnimationSpec
import dev.citali.lunartune.ui.theme.LunarMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.constants.BottomSheetAnimationSpec
import dev.citali.lunartune.constants.BottomSheetCollapseAnimationSpec
import dev.citali.lunartune.constants.BottomSheetSoftAnimationSpec

private val SheetCollapsedShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private const val ExpandedPrewarmDelayMs = 350L

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    scrimColorProvider: ((Float) -> Color)? = null,
    keepExpandedContentWarm: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    backHandlerEnabled: Boolean = true,
    collapsedGesturesEnabled: Boolean = true,
    /** Extra downward drift (px) for the collapsed row while the navigation
     * bar is hidden; scaled down as the sheet expands so the player is untouched. */
    collapsedOffsetPx: () -> Float = { 0f },
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val isAboveCollapsed by remember(state) {
        derivedStateOf { state.value > state.collapsedBound }
    }
    var expandedContentWarmed by remember { mutableStateOf(false) }
    if (isAboveCollapsed && keepExpandedContentWarm && !expandedContentWarmed) {
        expandedContentWarmed = true
    } else if (state.isDismissed && expandedContentWarmed) {
        expandedContentWarmed = false
    }

    LaunchedEffect(keepExpandedContentWarm, state.isCollapsed, state.isDismissed) {
        if (keepExpandedContentWarm && state.isCollapsed && !state.isDismissed && !expandedContentWarmed) {
            delay(ExpandedPrewarmDelayMs)
            if (state.isCollapsed && !state.isDismissed) {
                expandedContentWarmed = true
            }
        }
    }

    val shouldMountExpandedContent =
        isAboveCollapsed || (keepExpandedContentWarm && expandedContentWarmed && !state.isDismissed)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .offset {
                    val y =
                        (state.expandedBound - state.value)
                            .roundToPx()
                            .coerceAtLeast(0)
                    // A minimise spring settles below the collapsed anchor before it
                    // rebounds. Drawing that overshoot slides the collapsed row down
                    // into the navigation bar's footprint, so floor the sheet at the
                    // anchor for the duration of the settle. Drags, dismissal and the
                    // dismissed -> collapsed restore still travel freely below it.
                    val flooredY =
                        if (state.isMinimising) {
                            y.coerceAtMost((state.expandedBound - state.collapsedBound).roundToPx())
                        } else {
                            y
                        }
                    IntOffset(x = 0, y = flooredY)
                }.then(
                    if (collapsedGesturesEnabled || isAboveCollapsed) {
                        Modifier.bottomSheetDraggable(state, onDismiss)
                    } else {
                        Modifier
                    },
                ).clip(if (!state.isExpanded) SheetCollapsedShape else RectangleShape)
                // drawBehind, not background(): the sheet's value moves every frame of a
                // drag, so reading `progress` here during composition recomposed this node
                // and its whole content on every frame. Deferring the read to the draw
                // phase turns a drag into a redraw instead of a recomposition.
                .drawBehind {
                    val progress = state.progress.coerceIn(0f, 1f)
                    val color =
                        scrimColorProvider?.invoke(progress)
                            ?: backgroundColor.copy(alpha = backgroundColor.alpha * progress)
                    if (color.alpha > 0f) {
                        drawRect(color)
                    }
                },
    ) {
        if (state.isExpandedOrExpanding && backHandlerEnabled) {
            BackHandler(onBack = state::collapseSoft)
        }

        if (shouldMountExpandedContent) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((state.progress - 0.25f) * 4f).coerceIn(0f, 1f)
                            translationY =
                                if (state.value <= state.collapsedBound) {
                                    size.height.toFloat().coerceAtLeast(10_000f)
                                } else {
                                    0f
                                }
                        }.drawWithContent {
                            if (state.progress > 0.25f) {
                                drawContent()
                            }
                        },
                content = content,
            )
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            val clampedProgress = state.progress.coerceIn(0f, 1f)
                            // The navbar-hide drift is only meaningful at or above the
                            // collapsed anchor. While a minimise spring rebounds below it,
                            // adding the drift would push the row even further into the
                            // navigation bar's footprint.
                            val drift =
                                if (state.value < state.collapsedBound) {
                                    0f
                                } else {
                                    collapsedOffsetPx()
                                }
                            translationY = drift * (1f - clampedProgress)
                            alpha = 1f - (clampedProgress * 4f).coerceAtMost(1f)
                        }.drawWithContent {
                            if (state.progress < 0.25f) {
                                drawContent()
                            }
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
    private val density: Density,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    private val animationsDisabled: Boolean,
    private val motionMiniBounce: Boolean = true,
    val collapsedBound: Dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
) : DraggableState by draggableState {
    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    /** Only the newest settle may clear [isMinimising]. */
    private var settleGeneration = 0

    /**
     * True while a settle towards the collapsed anchor is running. The collapse spring
     * is underdamped (dampingRatio 0.85), so it can dip below the anchor before
     * rebounding; that downward overshoot is not rendered. Rendering it slides the
     * collapsed mini player into the navigation bar's footprint, where the bar (drawn
     * afterwards in the scaffold's bottomBar) covers it.
     */
    var isMinimising by mutableStateOf(false)
        private set

    private fun beginSettle(minimising: Boolean): Int {
        val generation = ++settleGeneration
        isMinimising = minimising
        return generation
    }

    private fun endSettle(generation: Int) {
        if (settleGeneration == generation) isMinimising = false
    }

    /** A pointer drag owns the sheet, so no settle floor may apply to it. */
    fun onDragStart() {
        ++settleGeneration
        isMinimising = false
    }

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

    fun collapse(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        updateAnchor(COLLAPSED_ANCHOR)
        // Any settle to the collapsed anchor can dip below it: a spring arriving from
        // above, or a swipe-down drag released just below the anchor carrying downward
        // momentum. Both draw the collapsed row inside the navigation bar's footprint
        // before they rebound, which is exactly the "mini player hides behind the nav"
        // regression. Only a restore from the dismissed anchor (starting at the very
        // bottom with no downward momentum) keeps its slide-in from below.
        // Do not gate this on animationsDisabled: callers can still pass a spring()
        // spec while reduced motion is on (rail/TV navigation), and snap() settles
        // at the anchor anyway, so the floor is free in that case.
        val restoringFromDismissed = value <= dismissedBound && initialVelocity >= 0.dp
        val generation = beginSettle(!restoringFromDismissed)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                animatable.animateTo(collapsedBound, animationSpec, initialVelocity = initialVelocity)
            } finally {
                endSettle(generation)
            }
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        updateAnchor(EXPANDED_ANCHOR)
        beginSettle(false)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.upperBound!!, animationSpec, initialVelocity = initialVelocity)
        }
    }

    /** Spec used for a released drag; snap() must never inherit momentum. */
    private fun anchorSpec(): AnimationSpec<Dp> =
        if (animationsDisabled) snap() else if (motionMiniBounce) BottomSheetAnimationSpec else BottomSheetCalmAnimationSpec

    private fun collapse(initialVelocity: Dp = 0.dp) =
        collapse(anchorSpec(), if (animationsDisabled) 0.dp else initialVelocity)

    private fun expand(initialVelocity: Dp = 0.dp) =
        expand(anchorSpec(), if (animationsDisabled) 0.dp else initialVelocity)

    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else BottomSheetCollapseAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun dismiss(initialVelocity: Dp = 0.dp) {
        updateAnchor(DISMISSED_ANCHOR)
        beginSettle(false)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(
                animatable.lowerBound!!,
                anchorSpec(),
                initialVelocity = if (animationsDisabled) 0.dp else initialVelocity,
            )
        }
    }

    fun snapTo(value: Dp) {
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
        // velocity arrives in px/s with "up the screen" positive, matching the animatable's
        // growth toward the expanded anchor. Trimmed so a violent flick still settles.
        val carried = with(density) { LunarMotion.clampVelocity(velocity).toDp() }

        // Direction chosen by the flick: the finger already told us where this is going, so
        // the sheet continues at the speed it was thrown instead of starting from rest.
        if (velocity > 250) {
            expand(carried)
            return
        }
        if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss(carried)
                onDismiss.invoke()
            } else {
                collapse(carried)
            }
            return
        }

        // Slow release: position decides the anchor, not speed. Carrying the (small) velocity
        // here would be wrong — it can point the opposite way to the chosen anchor and read as
        // a stutter at the start of the settle.
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

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope, animationsDisabled) {
        val initialValue =
            when (previousAnchor) {
                EXPANDED_ANCHOR -> expandedBound
                COLLAPSED_ANCHOR -> collapsedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> error("Unknown BottomSheet anchor")
            }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        // The state is recreated whenever a bar/inset bound changes. Re-playing a spring
        // from the old value towards the new collapsed anchor overshoots below it and
        // draws the mini player under the navigation bar. Collapsed/dismissed restores
        // adopt the new bound instantly; only restoring an expanded player keeps its
        // upward spring (it cannot overshoot downwards).
        val initialSpec: AnimationSpec<Dp> =
            if (animationsDisabled || previousAnchor != EXPANDED_ANCHOR) {
                snap()
            } else {
                BottomSheetAnimationSpec
            }
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(initialValue, initialSpec)
        }

        BottomSheetState(
            density = density,
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
