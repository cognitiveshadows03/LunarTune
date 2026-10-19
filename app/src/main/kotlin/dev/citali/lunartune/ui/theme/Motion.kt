package dev.citali.lunartune.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring

/**
 * Central motion specs for LunarTune.
 *
 * Springs replace the default stiff tweens on interactive motion (presses, slides, chevrons,
 * snap-backs) so movement settles organically instead of stopping dead. Curves intentionally use
 * literal stiffness/damping values rather than the [Spring] presets so the feel stays identical
 * across Compose versions.
 *
 * Deliberately NOT sprung: color morphs (channels overshoot into flashes), alpha fades, lyric
 * motion, ambient/AOD motion, and loading choreography — tweens remain correct there.
 *
 * Momentum rule: a drag that is released must never animate from rest. Pass the release
 * velocity as `initialVelocity` to [settle] (snap-back) or [flingOut] (dismissal) so the
 * element continues at the speed the finger left at. [clampVelocity] trims flicks that are
 * too fast for a spring to settle cleanly.
 *
 * These values are fixed: the user-tunable Motion Lab (LunarMotion tuning) was removed; the
 * stock feel below is the only feel, and the mini player's bounce lives in
 * [dev.citali.lunartune.constants.BottomSheetAnimationSpec].
 */
object LunarMotion {
    /** MD3 emphasized decelerate — elements entering the screen. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** MD3 emphasized accelerate — elements leaving the screen. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Gentle bounce for entrances, icon rotations, sheet panels and swipe snap-backs.
     * Settles in ~350ms with one soft overshoot.
     */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.75f, stiffness = 500f)

    /**
     * Organic settle with zero overshoot, for progress/shape-driven motion (search morph,
     * crossfades with scale) where overshooting past the target would distort layout.
     */
    fun <T> smooth() = spring<T>(dampingRatio = 1f, stiffness = 700f)

    /**
     * Fast retract for exits (FAB scrolling away, banners dismissing). Barely-there give,
     * gone in ~200ms.
     */
    fun <T> snappy() = spring<T>(dampingRatio = 0.95f, stiffness = 1500f)

    /**
     * Full-screen glide for nav transitions (tab blooms, drill slides). A whisper of
     * overshoot over ~520ms so page changes read clearly without seasickness.
     */
    fun <T> glide() = spring<T>(dampingRatio = 0.88f, stiffness = 350f)

    /** Tactile press bounce for [dev.citali.lunartune.ui.component.IconButton]. */
    fun press() = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)

    /**
     * Snap-back after a drag is released. Intended to be paired with the finger's release
     * velocity as `initialVelocity`, so the element leaves at the speed it was travelling
     * rather than restarting from rest. Damping is a touch above [bouncy] because a released
     * fling already carries energy; adding overshoot on top reads as sloppy, not lively.
     */
    fun <T> settle() = spring<T>(dampingRatio = 0.82f, stiffness = 520f)

    /**
     * Dismissal that continues the flick instead of replaying a fixed exit curve. Critically
     * damped: the target is off-screen, so any overshoot is invisible work, and bouncing
     * back off the screen edge reads as a bug.
     */
    fun <T> flingOut() = spring<T>(dampingRatio = 1f, stiffness = 700f)

    /**
     * Settle for a scroll-driven hide (the navigation bar retreating under the finger).
     *
     * Matched to the lyric motion the app already ships - DampingRatioLowBouncy at
     * StiffnessMedium - but with the ratio pulled back from 0.5 to 0.72. The bar is a
     * large surface travelling a short distance, so the lyrics bounce would read as a
     * wobble; 0.72 keeps one visible overshoot on release without the bar springing back
     * into view. Critically damped (the old default) killed that overshoot entirely,
     * which is what made the motion look flat.
     */
    fun scrollHide() = spring<Float>(dampingRatio = 0.72f, stiffness = 500f)

    /**
     * Tab switches (top-level route to top-level route).
     *
     * Stiffness is deliberately high. A spring and a tween cost the same per frame -
     * both drive the same layer properties - but a spring's duration is emergent, and a
     * lazy one runs 600ms or more with two full screens being composited every frame.
     * On a 636 that window, not the curve, is what made an earlier spring-based transition
     * pass feel worse than the tweens it replaced. 900 settles in roughly a third of a
     * second: momentum without the compositing bill.
     */
    fun <T> tabEnter() = spring<T>(dampingRatio = 0.80f, stiffness = 340f)

    // Generic in the value type on purpose: fadeIn/scaleIn want FiniteAnimationSpec<Float>
    // while slideInHorizontally wants FiniteAnimationSpec<IntOffset>, and both should share
    // one spring definition rather than drifting apart.
    //
    // Stiffness is what makes motion readable, not duration. A stiff spring (900+) covers
    // ~90% of its travel in the first 70ms and then crawls through the last tenth, so it
    // reads as an instant cut with a stutter on the end rather than as movement. Lowering
    // stiffness lengthens the natural period, which spreads the same distance over visible
    // frames. 340 puts ~95% of the travel at around 0.25s and settles near 0.45s.
    //
    // The cost is real and worth stating: every extra frame is another frame with two full
    // screens composited. Below ~300 stiffness that window grows past half a second, which
    // is where an earlier spring pass on this app was reverted for feeling worse on slow
    // hardware. These sit just above that line.
    /** The outgoing screen must not overshoot - it is on its way out. */
    fun <T> tabExit() = spring<T>(dampingRatio = 1f, stiffness = 400f)

    /**
     * Drill transitions (list to detail, and back out). Travels further, so softer still -
     * a long slide at tab stiffness would look hurried.
     */
    fun <T> drillEnter() = spring<T>(dampingRatio = 0.88f, stiffness = 260f)

    fun <T> drillExit() = spring<T>(dampingRatio = 1f, stiffness = 320f)

    /** Scale target while pressed. */
    const val PressedScale = 0.85f

    /**
     * Largest stretch, as a fraction of size, applied along the drag axis while a surface is
     * being pulled. Deliberately tiny — the deformation should be felt during the gesture and
     * be gone before the eye can name it.
     */
    const val MaxStretch = 0.04f

    /**
     * Ceiling on momentum carried out of a fling, in px/s. A violent swipe can report
     * 15 000 px/s; fed straight into a spring that overshoots far enough to look like a
     * glitch. 6 000 px/s still reads as "thrown" while staying settleable.
     */
    const val MaxCarriedVelocity = 6_000f

    /** Clamp a raw release velocity into a range a spring can settle from cleanly. */
    fun clampVelocity(velocity: Float): Float =
        velocity.coerceIn(-MaxCarriedVelocity, MaxCarriedVelocity)
}
