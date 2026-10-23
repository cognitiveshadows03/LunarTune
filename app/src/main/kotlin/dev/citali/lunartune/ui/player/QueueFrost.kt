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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.citali.lunartune.utils.LyricsArtBlurCache
import kotlin.math.roundToInt

/**
 * A pane of glass made from the playing track's cover.
 *
 * Draws the track's blurred artwork across this node and tints it with [scrim] - the player's own
 * background colour - so a block of interface can sit on the music instead of on a flat sheet. The
 * blur itself comes from [LyricsArtBlurCache], which already blurs one cover per track for the
 * lyrics page and the player backgrounds, so this costs no extra work after the first frame.
 *
 * Nothing is drawn until the blurred cover exists; the node's own background shows through in the
 * meantime, which is why callers give it one.
 */
@Composable
internal fun Modifier.queueAlbumFrost(
    model: Any?,
    scrim: Color,
): Modifier {
    val url = model?.toString() ?: return this
    val context = LocalContext.current
    val revision by LyricsArtBlurCache.updates.collectAsState()
    val artwork =
        remember(url, revision) {
            LyricsArtBlurCache.peek(url)?.asImageBitmap()
        }

    LaunchedEffect(url) {
        LyricsArtBlurCache.prefetch(context, url)
    }

    return this.drawBehind {
        val art = artwork
        if (art != null) {
            drawImage(
                image = art,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
        // The player's colour, not a neutral scrim: the glass keeps the identity of the track.
        drawRect(
            brush =
                Brush.verticalGradient(
                    listOf(
                        scrim.copy(alpha = 0.42f),
                        scrim.copy(alpha = 0.72f),
                    ),
                ),
        )
    }
}
