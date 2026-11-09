/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.component

import android.os.SystemClock
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

/** Observes without consuming: child buttons and scrolling retain their gestures. */
@Composable
fun Modifier.dotTextureHaptics(): Modifier {
    val context = LocalContext.current
    val (enabled) = rememberPreference(EnableHapticFeedbackKey, true)
    val (strength) = rememberPreference(HapticStrengthKey, 25)
    val step = with(LocalDensity.current) { 22.dp.toPx() }
    return pointerInput(enabled, strength, step) {
        if (!enabled || strength <= 0) return@pointerInput
        var lastPoint: Offset? = null
        var lastCell: Pair<Int, Int>? = null
        var lastTick = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (!change.pressed || event.changes.count { it.pressed } > 1) {
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
