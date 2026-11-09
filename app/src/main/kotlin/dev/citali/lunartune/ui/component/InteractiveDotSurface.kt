/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.LocalAnimationsDisabled

/** Passive pointer observation: never consumes taps, swipes or child gestures. */
@Composable
fun Modifier.interactiveDotSurface(background: Color, ink: Color, scrolling: Boolean): Modifier {
    val reducedMotion = LocalAnimationsDisabled.current
    var touch by remember { mutableStateOf<Offset?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val glow = animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(if (reducedMotion) 0 else 450), label = "settingsDotTouch",
    )
    val energy = animateFloatAsState(
        if (scrolling && !reducedMotion) 1f else 0f,
        tween(if (reducedMotion) 0 else 250), label = "settingsDotScroll",
    )
    return background(background).drawBehind {
        val step = 22.dp.toPx()
        val radius = 1.dp.toPx()
        val point = touch
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                val proximity = point?.let {
                    (1f - (it - Offset(x, y)).getDistance() / (step * 3.4f)).coerceIn(0f, 1f)
                } ?: 0f
                val lift = proximity * glow.value
                drawCircle(
                    ink.copy(alpha = (0.05f * (1f + energy.value) + 0.26f * lift).coerceIn(0f, 1f)),
                    radius * (1f + 0.35f * energy.value + 1.5f * lift), Offset(x, y),
                )
                x += step
            }
            y += step
        }
    }.pointerInput(Unit) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    pressed = event.changes.any { it.pressed }
                    if (pressed) touch = change.position
                }
            }
        } finally { pressed = false }
    }
}
