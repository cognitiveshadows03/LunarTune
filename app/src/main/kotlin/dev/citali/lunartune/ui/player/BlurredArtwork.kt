/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.utils.LyricsArtBlurCache

/**
 * Artwork blurred by whatever means this device actually has.
 *
 * `Modifier.blur` needs `RenderEffect`, which is Android 12 and above — and below that it is a
 * **silent no-op**, not an error. That is what made this hard to spot: the player's blur
 * backgrounds were rendering on old devices with the cover entirely unblurred, with nothing in the
 * logs to explain why. The album backdrop guards for this and falls back to its own CPU path; the
 * background *styles* did not.
 *
 * So this picks the path: GPU blur on 12+, and below it the same CPU stack blur the lyrics
 * backdrop uses, served from the same cache.
 */
@Composable
internal fun BlurredArtwork(
    model: Any?,
    radius: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
) {
    val url = model?.toString()

    if (radius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier.blur(radius),
        )
        return
    }

    // No blur asked for, or nothing to blur it with — show the artwork as it comes.
    val cpuRadius = (radius.value * CpuBlurPixelsPerDp).coerceIn(0f, 48f)
    if (url == null || cpuRadius < 0.5f) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val revision by LyricsArtBlurCache.updates.collectAsState()
    val blurred =
        remember(url, cpuRadius, revision) {
            LyricsArtBlurCache.peek(url, cpuRadius)
        }
    var lastBlurred by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    if (blurred != null) {
        lastBlurred = blurred
    }

    LaunchedEffect(url, cpuRadius) {
        LyricsArtBlurCache.prefetch(context, url, cpuRadius)
    }

    val displayBlurred = blurred ?: lastBlurred
    if (displayBlurred != null) {
        Image(
            bitmap = displayBlurred.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier,
        )
    } else {
        // Still being blurred. Show the plain artwork rather than an empty background — the
        // styles that use this all lay a gradient over the top, so the moment it swaps to the
        // blurred version is far less visible than a hole would be.
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier,
        )
    }
}

/**
 * Pixels of blur on the cached bitmap per dp of blur asked for.
 *
 * The two are not the same unit: the GPU radius is in dp on screen, the stack blur operates on the
 * pixels of a 384px bitmap that is then stretched across the screen. This matches the ratio the
 * album backdrop's own pre-S path already uses — 44dp of GPU blur against 25px on a 500px decode —
 * scaled down to this cache's 384px.
 */
private const val CpuBlurPixelsPerDp = 0.44f
