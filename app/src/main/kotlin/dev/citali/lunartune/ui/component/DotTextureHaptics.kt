/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.component

import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.constants.EnableHapticFeedbackKey
import dev.citali.lunartune.constants.HapticStrengthKey
import dev.citali.lunartune.utils.performAppHaptic
import dev.citali.lunartune.utils.rememberPreference

/** Hit regions are scoped to the visible textured page, never shared between screens. */
class DotTextureRegions {
    val covered = mutableMapOf<Any, Rect>()
}
val LocalDotTextureRegions = staticCompositionLocalOf<DotTextureRegions?> { null }

@Composable
fun Modifier.excludeDotTexture(): Modifier {
    val regions = LocalDotTextureRegions.current ?: return this
    val key = remember { Any() }
    DisposableEffect(regions, key) { onDispose { regions.covered.remove(key) } }
    return onGloballyPositioned { regions.covered[key] = it.boundsInRoot() }
}

/** Observes without consuming: child buttons and scrolling retain their gestures. */
@Composable
fun Modifier.dotTextureHaptics(): Modifier {
    val regions = LocalDotTextureRegions.current
    val origin = remember { arrayOf(Offset.Zero) }
    val context = LocalContext.current
    val (enabled) = rememberPreference(EnableHapticFeedbackKey, true)
    val (strength) = rememberPreference(HapticStrengthKey, 25)
    val step = with(LocalDensity.current) { 22.dp.toPx() }
    return onGloballyPositioned { origin[0] = it.positionInRoot() }.pointerInput(enabled, strength, step, regions) {
        if (!enabled || strength <= 0) return@pointerInput
        var lastPoint: Offset? = null
        var lastCell: Pair<Int, Int>? = null
        var lastTick = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (!change.pressed || event.changes.count { it.pressed } > 1 ||
                    regions?.covered?.values?.any { it.contains(change.position + origin[0]) } == true) {
                    lastPoint = null
                    lastCell = null
                    continue
                }
                val point = change.position
                val cell = (point.x / step).toInt() to (point.y / step).toInt()
                if (lastCell == null) {
                    lastCell = cell
                    lastPoint = point
                } else if (cell != lastCell) {
                    lastCell = cell
                    val now = SystemClock.uptimeMillis()
                    if ((point - (lastPoint ?: point)).getDistance() >= step * 0.65f && now - lastTick >= 65L) {
                        lastPoint = point
                        lastTick = now
                        performAppHaptic(context, strength, texture = true)
                    }
                }
            }
        }
    }
}
