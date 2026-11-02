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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.constants.BottomSheetAnimationSpec
import dev.citali.lunartune.constants.BottomSheetSoftAnimationSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign
import dev.citali.lunartune.LocalAnimationsDisabled

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
    /**
     * What a tap on the collapsed row does. Defaults to the quiet settle, which is
     * right for the player; a sheet that is opened by a gesture elsewhere passes
     * [BottomSheetState.openByGesture] instead, so both routes reach the same place.
     */
    collapsedClick: (() -> Unit)? = null,
    /** Extra downward drift (px) for the collapsed row while the navigation
     * bar is hidden; scaled down as the sheet expands so the player is untouched. */
    collapsedOffsetPx: () -> Float = { 0f },
    /**
     * Height of the part of the collapsed slot that is actually the row. The
     * rest of the slot is the navigation bar's footprint and must stay see-
     * through: making the whole slot clickable meant a tap in that empty band
     * opened the player instead of reaching the page or the bar underneath.
     * Defaults to the full slot for sheets that have no such empty band.
     */
    collapsedContentHeight: Dp? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    // A gesture interrupted by the sheet leaving composition never reaches
    // performFling; without this the drag flag would stay set and a stale raw
    // value would keep being drawn after the sheet comes back.
    DisposableEffect(state) {
        onDispose { state.onGestureScopeLeft() }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Draw phase, not layout. This used to be `offset { }`, which re-laid out
                // the whole player (and the mini player inside it) on every frame of the
                // spring — the open/collapse stutter. A translation here is a redraw.
                .graphicsLayer {
                    // Draw from the value the sheet is actually resting on, not from the
                    // raw animatable value. The minimise keeps its rebound (a settle reads
                    // the raw value), and a drag reads it too, but at rest the anchor is
                    // the only legitimate position. A route change gains or loses the
                    // navigation bar in one frame; the row has to follow in that frame,
                    // whatever a stale value, a cancelled settle or stale bookkeeping
                    // still says. Drawing the raw value is what left the row a whole bar
                    // too high on a page without one - an empty band under it - and a
                    // whole bar too low on a page with one, behind the bar.
                    translationY =
                        (state.expandedBound - state.effectiveValue).toPx().coerceAtLeast(0f)
                }.then(
                    if (collapsedGesturesEnabled || state.isAwayFromCollapsedAnchor) {
                        Modifier.bottomSheetDraggable(state, onDismiss)
                    } else {
                        Modifier
                    },
                )
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
                                    onClick = collapsedClick ?: state::expandSoft,
                                )
                            } else {
                                Modifier
                            },
                        ).fillMaxWidth()
                        .height(collapsedContentHeight ?: state.collapsedBound),
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
    private val density: Density,
    collapsedBound: Dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
) : DraggableState by draggableState {
    /**
     * Not a constructor constant: the collapsed anchor moves whenever the window
     * insets move (see [reanchorTo]), and the sheet has to follow it instead
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

    /**
     * The value the sheet is drawn and measured against.
     *
     * At rest the sheet belongs on an anchor, no matter what the raw animatable
     * value happens to hold: a route change moves the collapsed anchor by a whole
     * navigation bar in one frame, and the row has to be there in that frame - not
     * at the anchor a previous page, a cancelled settle or stale bookkeeping left
     * behind. Only a running settle (the rebound) or a finger on the sheet may sit
     * away from an anchor; those two read the raw value.
     *
     * Every visual decision goes through this - the translation, progress (and so
     * the scrim and the row's alpha), the collapsed/expanded checks - so a stale
     * raw value can no longer wash the page out, compose the full player behind a
     * collapsed row, or leave an empty band where the row belongs.
     */
    val effectiveValue: Dp
        get() =
            if (isSettling || isDragging) {
                value
            } else {
                when {
                    value >= expandedBound - HalfDp -> expandedBound
                    value <= dismissedBound + HalfDp -> dismissedBound
                    else -> collapsedBound
                }
            }

    val isDismissed by derivedStateOf {
        effectiveValue == animatable.lowerBound!!
    }

    /**
     * Tolerant on purpose. The collapsed anchor moves whenever the window insets or
     * the navigation bar move, and a spring that was aimed at the old anchor stops a
     * fraction of a dp off the new one. An exact comparison read that speck as "the
     * sheet is not collapsed", which re-armed the drag gesture on a collapsed sheet -
     * visible as a swipe up on the row opening the queue in the styles that ask for
     * no such gesture.
     */
    val isCollapsed by derivedStateOf {
        abs((effectiveValue - collapsedBound).value) < HalfDp.value
    }

    /**
     * True once the sheet has actually been pulled clear of its collapsed anchor, so
     * a caller that forbids the gesture can still let a finger drag an already-open
     * sheet back down.
     */
    val isAwayFromCollapsedAnchor: Boolean by derivedStateOf {
        effectiveValue > collapsedBound + HalfDp
    }

    val isExpanded by derivedStateOf {
        effectiveValue >= animatable.upperBound!! - HalfDp
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - effectiveValue) / (animatable.upperBound!! - collapsedBound)
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    /** Only the newest settle may clear the bookkeeping of the one it replaced. */
    private var settleGeneration = 0

    /**
     * True while a settle is running. It is the only time the sheet may legitimately
     * sit below its collapsed anchor (the rebound); at rest it must never be, and the
     * draw phase below uses that to guarantee it.
     */
    val isSettling: Boolean
        get() = settleJob?.isActive == true

    /**
     * True while a pointer drag owns the sheet. Geometry changes must not snap the
     * row out from under a finger; they are parked until the fling settles. A
     * snapshot state, because the draw-phase guard above reads it.
     */
    var isDragging by mutableStateOf(false)
        private set

    /** The settle in flight, so a geometry change can retarget it instead of killing it. */
    private var settleJob: Job? = null
    private var settleAnchor: Int? = null
    private var settleSpec: AnimationSpec<Dp>? = null

    /**
     * Geometry that asked to change while a settle was in flight.
     *
     * Applying it mid-flight is what used to break the collapse on the styles that
     * hide the status bar: the bars come back the instant collapsing starts, the
     * window loses that much height, and moving the bounds under a running spring
     * either snaps the sheet down (value coerced into the new, smaller range) or
     * relaunches it from a standstill. Either way the sheet loses a beat, the row
     * dips, and the navigation bar - whose slot travels on the sheet - arrives late.
     * So the change is parked here and taken up once the settle has landed.
     */
    private var pendingGeometry: Triple<Dp, Dp, Dp>? = null

    /** Apply a parked geometry change, if there is one. */
    private fun applyPendingGeometry() {
        val pending = pendingGeometry ?: return
        pendingGeometry = null
        animatable.updateBounds(pending.first.coerceAtMost(pending.third), pending.third)
        collapsedBound = pending.second
    }

    /**
     * Move to wherever the sheet is anchored now, in this frame. This is paper
     * moving under something already at rest, not a new motion: the collapsed row
     * is drawn against a second surface (the navigation bar) whose footprint
     * arrives in one frame. An anchor that arrives with the bar and a row that
     * eases to it on a spring are two clocks on one slot - exactly how the row
     * ended up inside the bar, or left an empty band where it belonged.
     */
    private fun reanchorQuietly() {
        val target =
            when (targetAnchor) {
                EXPANDED_ANCHOR -> expandedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> collapsedBound
            }
        if (abs((target - value).value) < HalfDp.value) return
        settleJob?.cancel()
        ++settleGeneration
        settleAnchor = null
        settleSpec = null
        settleJob = null
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(target)
        }
    }

    /**
     * Every settle goes through here so [onWindowBoundsChanged] knows what is in flight and
     * on which spec to continue it.
     */
    private fun settleTo(
        animationSpec: AnimationSpec<Dp>,
        anchor: Int,
        initialVelocityPx: Float = 0f,
    ) {
        // Every settle starts where a drag ended (or where a quiet reanchor asked
        // the sheet to move), so the finger no longer owns the value.
        isDragging = false
        // A new settle is a good moment to adopt geometry that was parked: the
        // animation starts from the current value either way.
        applyPendingGeometry()
        updateAnchor(anchor)
        // Resolved here, after the geometry is up to date, so a settle can never be
        // aimed at an anchor that has since moved.
        val target =
            when (anchor) {
                EXPANDED_ANCHOR -> expandedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> collapsedBound
            }
        settleJob?.cancel()
        val generation = ++settleGeneration
        settleAnchor = anchor
        settleSpec = animationSpec
        settleJob =
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    animatable.animateTo(
                        targetValue = target,
                        animationSpec = animationSpec,
                        // A flick throws the sheet at the anchor, so the spring starts
                        // with that speed and the rebound grows with the flick. Ported
                        // from the reference implementation's motion, which is the only
                        // part of it worth having.
                        initialVelocity = with(density) { initialVelocityPx.toDp() },
                    )
                } finally {
                    if (settleGeneration == generation) {
                        settleAnchor = null
                        settleSpec = null
                        settleJob = null
                    }
                    // Outside the generation check on purpose: if this settle was
                    // superseded or cancelled, the change parked under it still has to
                    // be taken up, or the sheet keeps the old geometry for good.
                    if (pendingGeometry != null && settleJob?.isActive != true) {
                        applyPendingGeometry()
                        // The settle just landed on the old geometry. Land on the new
                        // anchor in this frame rather than easing there on another
                        // soft spring: that spring is exactly how the row spent a
                        // second in the navigation bar (or above an empty band).
                        reanchorQuietly()
                    }
                    // The collapsed anchor can keep travelling after a settle aimed
                    // at its earlier value (a minimise during a route change). Put
                    // the value back on the anchor the sheet is resting on now.
                    if (!isDragging && !isSettling &&
                        abs((value - collapsedBound).value) >= HalfDp.value
                    ) {
                        reanchorQuietly()
                    }
                }
            }
    }

    /**
     * The window itself changed size (status bar hidden in the player, insets
     * moving). The collapsed anchor has its own clock - [reanchorTo] - so this only
     * moves the outer bounds and leaves that anchor alone.
     */
    fun onWindowBoundsChanged(
        dismissed: Dp,
        expanded: Dp,
    ) {
        if (pendingGeometry == null &&
            dismissedBound == dismissed &&
            expandedBound == expanded
        ) {
            return
        }
        pendingGeometry = Triple(dismissed, collapsedBound, expanded)
        if (isDragging || isSettling) return
        applyPendingGeometry()
        reanchorQuietly()
    }

    /**
     * Follow a collapsed anchor that is itself animating. The navigation bar's
     * footprint arrives over a spring, and the row travels on that same spring:
     * at every frame the row sits exactly where the arriving bar leaves room for
     * it. Jumping straight to the final anchor is what opened a blank band under
     * the row on the way back from a page with no bar, and what dropped the row
     * behind the bar on the way in.
     *
     * A resting sheet snaps to each frame's anchor (there is no second spring to
     * fall behind); a settle or a drag keeps its value and only adopts the bound.
     * [NonCancellable] keeps the bound and the value consistent even if the
     * collection is torn down mid-frame.
     */
    internal suspend fun reanchorTo(newCollapsedBound: Dp) {
        val previous = collapsedBound
        if (newCollapsedBound == previous) return
        val current = value
        val restingAtCollapsed =
            !isSettling && !isDragging &&
                abs((current - previous).value) < HalfDp.value
        withContext(NonCancellable) {
            if (restingAtCollapsed &&
                abs((newCollapsedBound - current).value) >= HalfDp.value
            ) {
                animatable.snapTo(newCollapsedBound)
            }
            collapsedBound = newCollapsedBound
        }
    }

    /** The sheet left composition, so no gesture can still own its value. */
    fun onGestureScopeLeft() {
        isDragging = false
    }

    /** A pointer drag owns the sheet, so no settle may be resumed under it. */
    fun onDragStart() {
        isDragging = true
        ++settleGeneration
        settleAnchor = null
        settleSpec = null
        settleJob = null
        // The finger owns the sheet now; it can move on the new geometry directly.
        applyPendingGeometry()
    }

    fun collapse(
        animationSpec: AnimationSpec<Dp>,
        velocityPx: Float = 0f,
    ) {
        settleTo(
            animationSpec = animationSpec,
            anchor = COLLAPSED_ANCHOR,
            initialVelocityPx = velocityPx,
        )
    }

    fun expand(
        animationSpec: AnimationSpec<Dp>,
        velocityPx: Float = 0f,
    ) {
        settleTo(
            animationSpec = animationSpec,
            anchor = EXPANDED_ANCHOR,
            initialVelocityPx = velocityPx,
        )
    }

    /**
     * The mini player's own spring: under-damped, so it arrives a touch late and
     * rebounds. Used for expand, minimise, fling-collapse and dismiss alike, which is
     * what makes those four read as one motion rather than four.
     */
    private fun collapse(velocityPx: Float = 0f) {
        collapse(
            if (animationsDisabled) snap() else BottomSheetAnimationSpec,
            velocityPx,
        )
    }

    private fun expand(velocityPx: Float = 0f) {
        expand(
            if (animationsDisabled) snap() else BottomSheetAnimationSpec,
            velocityPx,
        )
    }

    /**
     * Open the sheet the way a swipe-up does.
     *
     * A swipe is not just "settle to the expanded anchor": the drag first takes the
     * sheet, which drops the settle bookkeeping, cancels whatever settle was in
     * flight and takes up any geometry parked under it - and only then does the fling
     * ask for the expanded anchor, on the shared motion spring and with the finger's
     * speed. A tap has none of that behind it. It arrives with the sheet at rest, so
     * it asks for the same anchor from a different starting state, and on the styles
     * where the collapsed row is only reachable by touch it asked for it and nothing
     * happened.
     *
     * So a tap does what the drag does first, then asks for the anchor the same way.
     * Same start, same spring, same arrival: a tap and a swipe now open the sheet
     * identically instead of by two routes.
     */
    fun openByGesture(velocityPx: Float = OpenByGestureVelocityPx) {
        onDragStart()
        performFling(velocityPx, null)
    }

    /** The quiet pair, for the back gesture and for the mini player tap. */
    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun dismiss() {
        settleTo(
            animationSpec = if (animationsDisabled) snap() else BottomSheetAnimationSpec,
            anchor = DISMISSED_ANCHOR,
        )
    }

    fun snapTo(value: Dp) {
        isDragging = false
        settleJob?.cancel()
        applyPendingGeometry()
        updateAnchor(
            when (value) {
                expandedBound -> EXPANDED_ANCHOR
                collapsedBound -> COLLAPSED_ANCHOR
                dismissedBound -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            },
        )
        settleJob = null
        settleAnchor = null
        settleSpec = null
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        // The finger is gone even in the branches that dismiss without a settle.
        isDragging = false
        if (velocity > 250) {
            expand(velocity)
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse(velocity)
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
                density = density,
                collapsedBound = collapsedBound,
                initialAnchor = previousAnchor,
            )
        }

    // Window bounds are layout facts: apply them as they arrive, without
    // rebuilding the state (a settle keeps running through them).
    LaunchedEffect(state, dismissedBound, expandedBound) {
        state.onWindowBoundsChanged(dismissedBound, expandedBound)
    }

    // Follow the collapsed anchor in one coroutine, snapping to each value it
    // is given. What animates is decided by the caller: the navigation bar's
    // share of the anchor is driven by the bar's own spring, while window
    // insets and status-bar changes are layout facts and land immediately.
    // Springing those here made the row keep drifting after a minimise had
    // already settled - the small "adjustment" at the end of the motion.
    // The insets read state.collapsedBound during layout, so every page's
    // bottom padding follows the same value without recomposing the tree.
    val currentCollapsedBound = rememberUpdatedState(collapsedBound)
    LaunchedEffect(state) {
        snapshotFlow { currentCollapsedBound.value }.collect { state.reanchorTo(it) }
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
            try {
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
            } finally {
                // A gesture cancelled by the pointer system (or by this modifier
                // leaving the tree) never reaches performFling. Release the value
                // so a stale drag flag cannot keep the sheet off its anchor.
                state.onGestureScopeLeft()
            }
        }
    }

private const val SheetDragSlopMultiplier = 2.5f

/** Tolerance for "has arrived": a spring lands on its anchor, floating point does not. */
private val HalfDp = 0.5.dp

/** A gentle swipe-up, reused when a tap asks for the same thing. */
private const val OpenByGestureVelocityPx = 600f
