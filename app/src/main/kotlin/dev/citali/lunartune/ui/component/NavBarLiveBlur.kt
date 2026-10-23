/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.citali.lunartune.utils.ImageBlurUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The strip behind the bar is recorded at a fraction of its real size. Blurring a sixth of the
 * screen costs a fraction of blurring the screen, and scaling the result back up is itself part
 * of what makes it read as frosted glass.
 */
private const val NavBarBlurDownscale = 6f

/** Blur radius in pixels of the shrunken strip - roughly 48px once it is scaled back up. */
private const val NavBarBlurRadius = 8f

/**
 * How often the strip is re-recorded while the bar is on screen.
 *
 * Five times a second is chosen against the worst case the bar can sit over: an animated GIF
 * banner. It is far under the frame rate on purpose - a blurred backdrop that lags a moving
 * banner by a fifth of a second still reads as live, and this is the whole CPU cost of the
 * feature.
 */
private const val NavBarBlurIntervalMs = 220L

/** Opacity of the capsule over the blur, so the glass still reads as a surface and not a hole. */
const val NavBarBlurSurfaceAlpha = 0.55f

/**
 * State shared between the content that gets sampled and the bar that shows the sample.
 *
 * This exists because of how the blur has to be built on Android 11 and below. Compose has no
 * RenderEffect there, so `Modifier.blur` is a no-op and the blur has to happen on the CPU; and
 * the capture has to exclude the bar itself, or the bar would blur its own previous frame.
 * Both problems go away by recording the *content* slot - which by construction does not
 * contain the bottom bar - into a small graphics layer and reading that back.
 */
class NavBarBlurState {
    /** Latest blurred strip, or null until the first one lands. */
    var image by mutableStateOf<ImageBitmap?>(null)

    /** Image pixels per content pixel, for placing the strip under the bar. */
    var captureScale: Float = 1f
        private set

    /** Where the captured strip started, in root coordinates. */
    var captureTopInRoot: Float = 0f
        private set

    /** Written by the bar on every layout pass; read by the source in its draw phase. */
    internal var barTopInRoot: Float = 0f
    internal var barLeftInRoot: Float = 0f
    internal var barHeightPx: Float = 0f

    // Everything below is plain (non-snapshot) state on purpose: it is written from the draw
    // phase and read from a coroutine, and neither side should trigger recomposition.

    internal var lastRecordAtMs = 0L

    /** Bumped every time the layer is re-recorded, so the reader knows there is something new. */
    internal var recordVersion = 0

    internal var recordedScale: Float = 1f
    internal var recordedStripTop: Float = 0f

    internal fun publish(
        blurred: ImageBitmap,
        scale: Float,
        topInRoot: Float,
    ) {
        captureScale = scale
        captureTopInRoot = topInRoot
        image = blurred
    }
}

/**
 * Marks the content the navigation bar should blur, by recording the strip just above and behind
 * the bar into a small layer a few times a second and publishing the blurred result to [state].
 *
 * Everything about this is skipped when [state] is null, which is the case on Android 12+ (where
 * the bar uses a RenderEffect instead) and whenever the user has not turned the feature on.
 */
@Composable
fun Modifier.navBarLiveBlurSource(state: NavBarBlurState?): Modifier {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    if (state == null) return this
    val blurState = state

    LaunchedEffect(blurState, layer) {
        var lastVersion = 0
        while (isActive) {
            delay(NavBarBlurIntervalMs)
            val version = blurState.recordVersion
            if (version == lastVersion) continue
            lastVersion = version
            // Read the metadata before the pixels: they describe the record we are about to
            // rasterise, and a bar that moved since then is corrected for by the backdrop,
            // which crops against its live position anyway.
            val scale = blurState.recordedScale
            val stripTop = blurState.recordedStripTop
            // Stays on the composition's thread: rasterising a layer is a main-thread
            // operation, and this runs once every couple of hundred milliseconds.
            val shot = runCatching { layer.toImageBitmap() }.getOrNull() ?: continue
            val blurred =
                withContext(Dispatchers.Default) {
                    runCatching {
                        ImageBlurUtils.blur(shot.asAndroidBitmap(), NavBarBlurRadius).asImageBitmap()
                    }.getOrNull()
                } ?: continue
            blurState.publish(blurred, scale, stripTop)
        }
    }

    return this.drawWithContent {
        val barTop = blurState.barTopInRoot
        val barHeight = blurState.barHeightPx
        val now = SystemClock.uptimeMillis()
        if (
            barHeight > 1f &&
            size.width > NavBarBlurDownscale &&
            now - blurState.lastRecordAtMs >= NavBarBlurIntervalMs
        ) {
            // One bar-height of headroom above the bar, so a bar sliding away on scroll keeps
            // sampling content it has not reached yet instead of running off the top.
            val stripTop = (barTop - barHeight).coerceAtLeast(0f)
            val stripHeight = (barHeight * 2f).coerceAtMost(size.height)
            val layerWidth = (size.width / NavBarBlurDownscale).roundToInt().coerceAtLeast(1)
            val layerHeight = (stripHeight / NavBarBlurDownscale).roundToInt().coerceAtLeast(1)
            val recorded =
                runCatching {
                    layer.record(
                        density = density,
                        layoutDirection = layoutDirection,
                        size = IntSize(layerWidth, layerHeight),
                    ) {
                        scale(
                            scaleX = 1f / NavBarBlurDownscale,
                            scaleY = 1f / NavBarBlurDownscale,
                            pivot = Offset.Zero,
                        ) {
                            translate(left = 0f, top = -stripTop) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                }.isSuccess
            if (recorded) {
                blurState.lastRecordAtMs = now
                blurState.recordedScale = layerWidth.toFloat() / size.width
                blurState.recordedStripTop = stripTop
                blurState.recordVersion++
            }
        }
        drawContent()
    }
}

/**
 * Paints the slice of the last capture that sits behind this composable, so a translucent bar can
 * show the content it covers instead of a flat scrim.
 *
 * The strip is cropped against the bar's live position rather than the position it had when it
 * was captured, so it stays glued to the content while the bar animates.
 */
@Composable
fun NavBarBlurBackdrop(
    state: NavBarBlurState?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    val blurState = state
    Box(
        modifier =
            modifier
                .onGloballyPositioned { coordinates ->
                    blurState.barLeftInRoot = coordinates.positionInRoot().x
                    blurState.barTopInRoot = coordinates.positionInRoot().y
                    blurState.barHeightPx = coordinates.size.height.toFloat()
                }.drawBehind {
                    val image = blurState.image
                    val scale = blurState.captureScale
                    if (image != null && image.width > 0 && image.height > 0 && scale > 0f) {
                        val srcWidth = (size.width * scale).roundToInt().coerceIn(1, image.width)
                        val srcHeight = (size.height * scale).roundToInt().coerceIn(1, image.height)
                        val srcLeft =
                            (blurState.barLeftInRoot * scale).roundToInt().coerceIn(0, image.width - srcWidth)
                        val srcTop =
                            ((blurState.barTopInRoot - blurState.captureTopInRoot) * scale)
                                .roundToInt()
                                .coerceIn(0, image.height - srcHeight)

                        drawImage(
                            image = image,
                            srcOffset = IntOffset(srcLeft, srcTop),
                            srcSize = IntSize(srcWidth, srcHeight),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    // The surface tint goes on top of the sample, exactly where the solid
                    // capsule colour used to be: same colour, less of it.
                    drawRect(color = tint.copy(alpha = NavBarBlurSurfaceAlpha))
                },
    )
}
