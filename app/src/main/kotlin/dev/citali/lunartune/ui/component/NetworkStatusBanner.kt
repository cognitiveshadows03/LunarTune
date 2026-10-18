/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.network.NetworkBannerUiState
import dev.citali.lunartune.ui.theme.LunarMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class NetworkBannerVisuals(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
fun NetworkStatusBanner(
    state: NetworkBannerUiState,
    modifier: Modifier = Modifier,
) {
    var lastVisibleState by remember { mutableStateOf<NetworkBannerUiState>(NetworkBannerUiState.Offline) }
    // Flicked away by the reader, like a notification. Remembered as the state that was dismissed
    // rather than as a plain flag, so the banner staying down never gets in the way of the next
    // thing the network has to say; cleared once the banner has gone on its own.
    var dismissedState by remember { mutableStateOf<NetworkBannerUiState?>(null) }
    var offsetXPx by remember { mutableFloatStateOf(0f) }
    var pillWidthPx by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val dragState =
        rememberDraggableState { delta ->
            settleJob?.cancel()
            val travel = pillWidthPx.toFloat().coerceAtLeast(1f)
            offsetXPx = (offsetXPx + delta).coerceIn(-travel, travel)
        }

    if (state != NetworkBannerUiState.Hidden) {
        lastVisibleState = state
    }

    LaunchedEffect(state) {
        if (state == NetworkBannerUiState.Hidden) dismissedState = null
        settleJob?.cancel()
        offsetXPx = 0f
    }

    val visuals =
        when (lastVisibleState) {
            NetworkBannerUiState.Hidden,
            NetworkBannerUiState.Offline,
            -> {
                NetworkBannerVisuals(
                    message = "No internet connection",
                    icon = Icons.Default.CloudOff,
                    containerColor = Color(0xFF7F1D1D),
                    contentColor = Color.White,
                )
            }

            NetworkBannerUiState.BackOnline -> {
                NetworkBannerVisuals(
                    message = "Back online",
                    icon = Icons.Default.CloudDone,
                    containerColor = Color(0xFF1E8E3E),
                    contentColor = Color.White,
                )
            }
        }

    AnimatedVisibility(
        visible = state != NetworkBannerUiState.Hidden && state != dismissedState,
        modifier = modifier,
        enter =
            slideInVertically(animationSpec = tween(durationMillis = 250)) { -it } +
                fadeIn(animationSpec = tween(durationMillis = 180)),
        exit =
            slideOutVertically(animationSpec = tween(durationMillis = 220)) { -it } +
                fadeOut(animationSpec = tween(durationMillis = 180)),
        label = "networkStatusBanner",
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = visuals.containerColor,
            contentColor = visuals.contentColor,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .onSizeChanged { pillWidthPx = it.width }
                    // Held at the width it is dragged by, whether that is the reader's finger or
                    // the animation carrying it off, so the pill can leave the screen either way.
                    .graphicsLayer {
                        translationX = offsetXPx
                        val travel = pillWidthPx.toFloat().coerceAtLeast(1f)
                        alpha = (1f - abs(offsetXPx) / travel * 0.7f).coerceIn(0f, 1f)
                        // Flexible feedback: stretch along the drag axis, squash across it.
                        // Derived purely from the offset, so the pill relaxes on the very spring
                        // that carries it home — no second animation to keep in sync, and the
                        // read stays in the draw phase so dragging never recomposes.
                        val stretch =
                            (abs(offsetXPx) / travel * LunarMotion.MaxStretch)
                                .coerceIn(0f, LunarMotion.MaxStretch)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.5f
                    }.draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            val travel = pillWidthPx.toFloat().coerceAtLeast(1f)
                            val shouldDismiss = abs(offsetXPx) > travel * 0.28f || abs(velocity) > 900f
                            // A decisive flick is the reader's intent, so it outranks the
                            // offset. Preferring the offset sent a hard flick from a few px of
                            // travel off in the opposite direction — invisible with a tween
                            // replay, but a visible stutter once the velocity is carried.
                            val direction =
                                when {
                                    abs(velocity) > 900f -> if (velocity >= 0f) 1f else -1f
                                    abs(offsetXPx) > 1f -> if (offsetXPx >= 0f) 1f else -1f
                                    else -> 1f
                                }
                            val target = if (shouldDismiss) travel * 1.35f * direction else 0f
                            settleJob =
                                scope.launch {
                                    Animatable(offsetXPx).animateTo(
                                        targetValue = target,
                                        animationSpec =
                                            if (shouldDismiss) {
                                                LunarMotion.flingOut()
                                            } else {
                                                LunarMotion.settle()
                                            },
                                        // Leave at the speed the finger left at, then let friction
                                        // and the spring bring it to rest. The old tween replay
                                        // started from zero, so a hard flick and a slow nudge
                                        // looked identical.
                                        initialVelocity = LunarMotion.clampVelocity(velocity),
                                    ) { offsetXPx = value }
                                    if (shouldDismiss) dismissedState = state
                                }
                        },
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = visuals.contentColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = visuals.message,
                    color = visuals.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
