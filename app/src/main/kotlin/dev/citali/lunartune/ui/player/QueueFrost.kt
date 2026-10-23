/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.utils.LyricsArtBlurCache
import kotlin.math.roundToInt

/**
 * The queue sheet's glass.
 *
 * Draws the playing track's blurred artwork filling this node and tints it with the player's own
 * colour, so the sheet reads as a pane of frosted glass over the player rather than as an opaque
 * panel with a picture on it.
 *
 * The artwork is anchored to the **screen**, not to the sheet: the sheet's own position in the
 * window is subtracted from where the image is drawn, so dragging the sheet up and down makes the
 * player appear to slide behind a pane that stays still - the way the shade slides over the home
 * screen. Recomposition is avoided on the drag path by reading the position only on layout and
 * doing all of the offsetting in the draw phase.
 *
 * The image is the cache the lyrics page and the player backgrounds already fill (one blur per
 * track, GPU on Android 12+, CPU stack blur below it), so this costs nothing after the first
 * frame of a new song.
 */
@Composable
internal fun Modifier.queueSheetGlass(
    model: Any?,
    tint: Color,
    /** How much of the tint sits on top of the artwork; 1f hides it completely. */
    tintTop: Float = 0.40f,
    tintBottom: Float = 0.80f,
): Modifier {
    val url = model?.toString()
    val context = LocalContext.current
    val revision by LyricsArtBlurCache.updates.collectAsState()
    val artwork =
        remember(url, revision) {
            url?.let { LyricsArtBlurCache.peek(it)?.asImageBitmap() }
        }

    LaunchedEffect(url) {
        if (url != null) LyricsArtBlurCache.prefetch(context, url)
    }

    var topInRoot by remember { mutableStateOf(0f) }

    return this
        .onGloballyPositioned { topInRoot = it.positionInRoot().y }
        .drawBehind {
            val art = artwork
            if (art != null) {
                // The picture is drawn one sheet-offset higher than the sheet itself, which puts
                // it back where it would be if the sheet were still fully open: it stays on the
                // screen while the sheet travels over it, so a drag reveals a different part of
                // the same artwork instead of dragging the artwork along.
                drawImage(
                    image = art,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(art.width, art.height),
                    dstOffset = IntOffset(0, -topInRoot.roundToInt()),
                    dstSize =
                        IntSize(
                            size.width.roundToInt().coerceAtLeast(1),
                            size.height.roundToInt().coerceAtLeast(1),
                        ),
                    filterQuality = FilterQuality.Low,
                )
            }
            // The player's colour keeps the sheet recognisably part of the player, and the
            // heavier foot darkens where the list starts so the rows keep their contrast.
            drawRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = tintTop),
                            tint.copy(alpha = tintBottom),
                        ),
                    ),
            )
            // A single hairline along the top edge, the way the shade separates itself from
            // what is behind it.
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }
}
