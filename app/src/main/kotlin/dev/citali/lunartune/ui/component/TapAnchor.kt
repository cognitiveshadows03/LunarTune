/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Where the finger last went down, in root coordinates.
 *
 * ColorOS menus grow out of the element you touched rather than appearing from an edge.
 * Reproducing that needs to know where the touch happened, and the app has 109
 * `menuState.show { }` call sites - threading a position through every one of them would
 * be a huge, error-prone change.
 *
 * The position is therefore captured once, at the root of the composition, and read
 * where it is needed.
 *
 * App-wide rather than a CompositionLocal on purpose: there is exactly one root and one
 * window, so a local would only add provider plumbing (and another brace to keep
 * balanced in a 3,000-line activity) without buying any extra isolation.
 *
 * Cost: one float pair. Written on touch-down only - never on move - so scrolling and
 * dragging do not touch it. The capture reads at
 * [androidx.compose.ui.input.pointer.PointerEventPass.Initial] and consumes nothing, so
 * every gesture underneath behaves exactly as it did before.
 */
object TapAnchor {
    var position: Offset? by mutableStateOf(null)
        private set

    fun record(offset: Offset) {
        position = offset
    }

    /** The last touch-down point, or null before the first touch of the session. */
    fun touchDownOrNull(): Offset? = position
}
