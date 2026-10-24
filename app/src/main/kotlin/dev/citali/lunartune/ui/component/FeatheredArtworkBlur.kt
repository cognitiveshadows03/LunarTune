/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.utils.LyricsArtBlurCache

private const val CpuBlurPixelsPerDp = 0.44f

/**
 * Progressive bottom artwork blur inspired by Telegram's profile header: the upper portion of the
 * card stays tack-sharp while the lower text region smoothly melts into a clamped Gaussian blur of
 * the underlying cover art, paired with a light legibility veil instead of a heavy dark gradient.
 */
@Composable
internal fun FeatheredBottomArtworkBlur(
    artworkModel: Any?,
    cacheKeyUrl: String?,
    blurRadius: Dp,
    fadeStartFraction: Float = 0.54f,
    scrimMaxAlpha: Float = 0.24f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { blurRadius.toPx() }.coerceAtLeast(1f)
    val hardwareBlurEffect =
        remember(radiusPx) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp,
                ).takeIf(RenderEffect::isSupported)
            } else {
                null
            }
        }

    val s0 = fadeStartFraction.coerceIn(0f, 0.88f)
    val span = 1f - s0
    val maskBrush =
        remember(s0) {
            Brush.verticalGradient(
                s0 to Color.Transparent,
                (s0 + span * 0.20f) to Color.Black.copy(alpha = 0.25f),
                (s0 + span * 0.38f) to Color.Black.copy(alpha = 0.76f),
                (s0 + span * 0.58f) to Color.Black.copy(alpha = 0.96f),
                1f to Color.Black,
            )
        }
    val scrimBrush =
        remember(s0, scrimMaxAlpha) {
            val clampedAlpha = scrimMaxAlpha.coerceIn(0f, 1f)
            Brush.verticalGradient(
                (s0 + span * 0.16f) to Color.Transparent,
                (s0 + span * 0.52f) to Color.Black.copy(alpha = clampedAlpha * 0.52f),
                1f to Color.Black.copy(alpha = clampedAlpha),
            )
        }
    val fallbackScrimBrush =
        remember(s0) {
            Brush.verticalGradient(
                s0 to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.62f),
            )
        }

    Box(modifier = modifier.fillMaxSize()) {
        val hasBlurLayer =
            if (hardwareBlurEffect != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = maskBrush,
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            .graphicsLayer {
                                clip = true
                                renderEffect = hardwareBlurEffect
                            },
                )
                true
            } else {
                val context = LocalContext.current
                val cpuRadius = (blurRadius.value * CpuBlurPixelsPerDp).coerceAtLeast(1f)
                val cacheRevision by LyricsArtBlurCache.revisionFlow.collectAsState()
                val blurredBitmap =
                    remember(cacheKeyUrl, cpuRadius, cacheRevision) {
                        cacheKeyUrl?.let { url ->
                            LyricsArtBlurCache.peek(url, cpuRadius)?.asImageBitmap()
                        }
                    }

                LaunchedEffect(cacheKeyUrl, cpuRadius) {
                    if (!cacheKeyUrl.isNullOrBlank()) {
                        LyricsArtBlurCache.prefetch(context, cacheKeyUrl, cpuRadius)
                    }
                }

                if (blurredBitmap != null) {
                    Image(
                        bitmap = blurredBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = maskBrush,
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                    )
                    true
                } else {
                    false
                }
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(if (hasBlurLayer) scrimBrush else fallbackScrimBrush),
        )
    }
}
