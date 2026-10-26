/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedGroup
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLine
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedRow
import com.mocharealm.accompanist.lyrics.ui.profile.LyricsProfile
import com.mocharealm.accompanist.lyrics.ui.profile.ProfileTextUnit
import dev.citali.lunartune.utils.ImageBlurUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val FocusedRowUnlitAlpha = 0.24f
internal const val ActiveTranslationAlpha = 0.78f
internal const val ActivePhoneticAlpha = 0.78f

private const val ATLAS_PAGE_SIZE = 2048
private const val GLOW_PAD_PX = 18
private const val GLOW_BLUR_RADIUS_PX = 15f
private const val GLOW_ALPHA_FACTOR = 0.56f

// Pre-Android-12 software line-blur tile constants (0.5x downsampled stack-blur in LunarTextAtlas).
private const val SOFT_BLUR_SCALE = 0.5f
private const val SOFT_BLUR_PAD_HALF_PX = 8
private const val SOFT_BLUR_RADIUS_HALF_PX = 3.2f
private const val SOFT_BLUR_FULL_RADIUS_REF_PX = 4.5f

private object HardwareFocusBlurEffects {
    val effects: Array<RenderEffect?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Array(65) { index ->
            if (index == 0) null else BlurEffect(index.toFloat(), index.toFloat(), TileMode.Clamp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LunarLyricsLineItem(
    isFocused: Boolean,
    isRightAligned: Boolean,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
    blurRadius: () -> Float,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.4f,
    blendMode: BlendMode = BlendMode.SrcOver,
    isInteractive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scaleState by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.98f,
        animationSpec =
            if (isFocused) {
                tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            } else {
                tween(durationMillis = 300, easing = EaseInOut)
            },
        label = "lunarLineScale",
    )

    val alphaState by animateFloatAsState(
        targetValue = if (isFocused) activeAlpha else inactiveAlpha,
        animationSpec = tween(durationMillis = 300, easing = EaseInOut),
        label = "lunarLineAlpha",
    )

    val hardwareBlur = supportsHardwareRenderEffectBlur()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scaleState
                    scaleY = scaleState
                    alpha = alphaState
                    transformOrigin = TransformOrigin(if (isRightAligned) 1f else 0f, 1f)
                    this.blendMode = blendMode
                    compositingStrategy = CompositingStrategy.Auto
                    if (hardwareBlur) {
                        val radius = blurRadius().takeIf { it.isFinite() } ?: 0f
                        renderEffect = HardwareFocusBlurEffects.effects[radius.roundToInt().coerceIn(0, 64)]
                    }
                },
    ) {
        if (isInteractive) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onLineClicked, onLongClick = onLinePressed),
            )
        }
        content()
    }
}

internal class LunarTextLayer(
    private val bitmap: ImageBitmap,
    private val srcOffset: IntOffset,
    private val srcSize: IntSize,
    private val pad: Int = 0,
) {
    fun DrawScope.drawLayer(alpha: Float = 1f) {
        if (alpha <= 0.004f) return
        drawImage(
            image = bitmap,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset(-pad, -pad),
            dstSize = srcSize,
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Low,
        )
    }

    fun DrawScope.drawScaledLayer(
        dstOffset: IntOffset,
        dstSize: IntSize,
        alpha: Float = 1f,
    ) {
        if (alpha <= 0.004f || dstSize.width <= 0 || dstSize.height <= 0) return
        drawImage(
            image = bitmap,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Low,
        )
    }
}

internal class PreparedBlurTile(
    val layer: LunarTextLayer,
    val dstOffset: IntOffset,
    val dstSize: IntSize,
) {
    fun DrawScope.drawBlur(alpha: Float) {
        with(layer) {
            drawScaledLayer(dstOffset = dstOffset, dstSize = dstSize, alpha = alpha)
        }
    }
}

private class LunarAtlasPage(
    val size: Int = ATLAS_PAGE_SIZE,
) {
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    private var cursorX = 0
    private var cursorY = 0
    private var shelfHeight = 0

    fun allocate(
        width: Int,
        height: Int,
    ): IntOffset? {
        if (width > size || height > size) return null
        val allocW = (width + 2).coerceAtMost(size)
        val allocH = (height + 2).coerceAtMost(size)
        if (cursorX + allocW > size) {
            cursorX = 0
            cursorY += shelfHeight
            shelfHeight = 0
        }
        if (cursorY + allocH > size) return null
        val offset = IntOffset(cursorX + 1, cursorY + 1)
        cursorX += allocW
        shelfHeight = maxOf(shelfHeight, allocH)
        return offset
    }
}

internal class LunarTextAtlas {
    private val pages = ArrayList<LunarAtlasPage>(2)
    private val drawScope = CanvasDrawScope()
    private val bitmapPaint = Paint()
    private val cache = HashMap<Any, LunarTextLayer>(256)

    fun getOrPut(
        key: Any?,
        width: Float,
        height: Float,
        density: Density,
        layoutDirection: LayoutDirection,
        pad: Int = 0,
        drawContent: DrawScope.() -> Unit,
    ): LunarTextLayer? {
        if (key != null) {
            cache[key]?.let { return it }
        }
        val contentW = ceil(width).toInt()
        val contentH = ceil(height).toInt()
        if (contentW <= 0 || contentH <= 0) return null
        val totalW = contentW + pad * 2
        val totalH = contentH + pad * 2

        val layer =
            if (totalW > ATLAS_PAGE_SIZE || totalH > ATLAS_PAGE_SIZE) {
                val standalone = ImageBitmap(totalW, totalH)
                val standaloneCanvas = Canvas(standalone)
                drawScope.draw(
                    density = density,
                    layoutDirection = layoutDirection,
                    canvas = standaloneCanvas,
                    size = Size(totalW.toFloat(), totalH.toFloat()),
                ) {
                    translate(pad.toFloat(), pad.toFloat(), drawContent)
                }
                LunarTextLayer(standalone, IntOffset.Zero, IntSize(totalW, totalH), pad)
            } else {
                var page = pages.lastOrNull()
                var offset = page?.allocate(totalW, totalH)
                if (offset == null) {
                    page = LunarAtlasPage()
                    pages.add(page)
                    offset = page.allocate(totalW, totalH) ?: return null
                }
                val targetPage = page!!
                targetPage.canvas.save()
                targetPage.canvas.clipRect(
                    Rect(
                        offset.x.toFloat(),
                        offset.y.toFloat(),
                        (offset.x + totalW).toFloat(),
                        (offset.y + totalH).toFloat(),
                    ),
                )
                targetPage.canvas.translate(
                    (offset.x + pad).toFloat(),
                    (offset.y + pad).toFloat(),
                )
                drawScope.draw(
                    density = density,
                    layoutDirection = layoutDirection,
                    canvas = targetPage.canvas,
                    size = Size(contentW.toFloat(), contentH.toFloat()),
                    block = drawContent,
                )
                targetPage.canvas.restore()
                LunarTextLayer(targetPage.bitmap, offset, IntSize(totalW, totalH), pad)
            }

        if (key != null) {
            cache[key] = layer
        }
        return layer
    }

    fun putBlurredTile(
        key: Any?,
        fullWidthPx: Float,
        fullHeightPx: Float,
        density: Density,
        layoutDirection: LayoutDirection,
        drawFullRes: DrawScope.() -> Unit,
    ): PreparedBlurTile? {
        val fullW = ceil(fullWidthPx).toInt()
        val fullH = ceil(fullHeightPx).toInt()
        if (fullW <= 0 || fullH <= 0) return null
        val halfW = ceil(fullW * SOFT_BLUR_SCALE).toInt().coerceAtLeast(1)
        val halfH = ceil(fullH * SOFT_BLUR_SCALE).toInt().coerceAtLeast(1)
        val padHalf = SOFT_BLUR_PAD_HALF_PX
        val totalHalfW = halfW + padHalf * 2
        val totalHalfH = halfH + padHalf * 2
        val padFull = (padHalf / SOFT_BLUR_SCALE).roundToInt()
        val dstOffset = IntOffset(-padFull, -padFull)
        val dstSize = IntSize((totalHalfW / SOFT_BLUR_SCALE).roundToInt(), (totalHalfH / SOFT_BLUR_SCALE).roundToInt())

        if (key != null) {
            cache[key]?.let { cached ->
                return PreparedBlurTile(cached, dstOffset, dstSize)
            }
        }

        val scratch = ImageBitmap(totalHalfW, totalHalfH)
        val scratchCanvas = Canvas(scratch)
        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = scratchCanvas,
            size = Size(totalHalfW.toFloat(), totalHalfH.toFloat()),
        ) {
            translate(padHalf.toFloat(), padHalf.toFloat()) {
                scale(SOFT_BLUR_SCALE, SOFT_BLUR_SCALE, Offset.Zero, drawFullRes)
            }
        }
        val blurredImage =
            ImageBlurUtils
                .blur(scratch.asAndroidBitmap(), SOFT_BLUR_RADIUS_HALF_PX)
                .asImageBitmap()

        val layer =
            if (totalHalfW > ATLAS_PAGE_SIZE || totalHalfH > ATLAS_PAGE_SIZE) {
                LunarTextLayer(blurredImage, IntOffset.Zero, IntSize(totalHalfW, totalHalfH), 0)
            } else {
                var page = pages.lastOrNull()
                var offset = page?.allocate(totalHalfW, totalHalfH)
                if (offset == null) {
                    page = LunarAtlasPage()
                    pages.add(page)
                    offset = page.allocate(totalHalfW, totalHalfH) ?: return null
                }
                val targetPage = page!!
                targetPage.canvas.drawImage(
                    blurredImage,
                    Offset(offset.x.toFloat(), offset.y.toFloat()),
                    bitmapPaint,
                )
                LunarTextLayer(targetPage.bitmap, offset, IntSize(totalHalfW, totalHalfH), 0)
            }

        if (key != null) {
            cache[key] = layer
        }
        return PreparedBlurTile(layer, dstOffset, dstSize)
    }
}

internal class PreparedUnitLayers(
    val text: LunarTextLayer?,
    val phonetic: LunarTextLayer?,
)

internal class PreparedGroupLayers(
    val combined: LunarTextLayer?,
    val glow: LunarTextLayer?,
    val units: List<PreparedUnitLayers>,
    val phoneticIndices: IntArray,
)

internal class PreparedRowLayers(
    val runs: List<List<PreparedGroupLayers>>,
    val blurredMain: PreparedBlurTile?,
    val blurredPhonetics: PreparedBlurTile?,
)

private data class UnitRasterKey(
    val profile: LyricsProfile,
    val text: String,
    val style: Any,
    val startOffset: Int,
    val endOffset: Int,
    val colorValue: ULong,
    val glow: Boolean,
)

private data class CaptionRasterKey(
    val text: String,
    val style: Any,
    val colorValue: ULong,
    val blurred: Boolean = false,
)

internal class LunarRowRenderState(
    private val row: PreparedRow,
) {
    private var sweepCursor = 0
    private var previousTime = Int.MIN_VALUE
    val layerPaint = Paint()
    val mask: Brush =
        Brush.horizontalGradient(
            if (row.rtl) {
                listOf(Color.White.copy(alpha = FocusedRowUnlitAlpha), Color.White)
            } else {
                listOf(Color.White, Color.White.copy(alpha = FocusedRowUnlitAlpha))
            },
            startX = -row.sweepFadeWidth / 2f,
            endX = row.sweepFadeWidth / 2f,
        )

    fun sweepCenter(now: Int): Float =
        with(row) {
            if (sweepStarts.isEmpty()) return bounds.left
            if (now < previousTime || now.toLong() - previousTime > 1000L) {
                var low = 0
                var high = sweepStarts.size
                while (low < high) {
                    val mid = (low + high) ushr 1
                    if (sweepStarts[mid] <= now) low = mid + 1 else high = mid
                }
                sweepCursor = (low - 1).coerceAtLeast(0)
            }
            while (sweepCursor < sweepStarts.lastIndex && now >= sweepStarts[sweepCursor + 1]) {
                sweepCursor++
            }
            previousTime = now
            val idx = sweepCursor
            val p =
                if (sweepEnds[idx] <= sweepStarts[idx]) {
                    if (now >= sweepStarts[idx]) 1f else 0f
                } else {
                    ((now.toLong() - sweepStarts[idx]).toFloat() /
                        (sweepEnds[idx].toLong() - sweepStarts[idx]).toFloat())
                        .coerceIn(0f, 1f)
                }
            val halfFade = sweepFadeWidth / 2f
            val direction = if (rtl) -1f else 1f
            val from =
                (if (rtl) sweepRight[idx] else sweepLeft[idx]) -
                    if (idx == 0) direction * halfFade else 0f
            val to =
                (if (rtl) sweepLeft[idx] else sweepRight[idx]) +
                    if (idx == sweepStarts.lastIndex) direction * halfFade else 0f
            from + (to - from) * p
        }
}

private fun swellScaleDelta(
    peak: Float,
    progress: Float,
): Float {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f || peak <= 0f) return 0f
    return (sin(PI * t) * peak).toFloat()
}

private fun glowBounceStrength(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return 0f
    return sin(PI * t).toFloat().coerceIn(0f, 1f)
}

@Stable
internal class LunarRenderResources(
    val color: Color,
    val density: Density,
    val layoutDirection: LayoutDirection,
) {
    val phoneticColor: Color = color.copy(alpha = color.alpha * ActivePhoneticAlpha)
    val translationColor: Color = color.copy(alpha = color.alpha * ActiveTranslationAlpha)

    private val atlas = LunarTextAtlas()
    private val rasterMutex = Mutex()
    private val rowStates = HashMap<PreparedRow, LunarRowRenderState>(128)
    val preparedRasters = mutableStateMapOf<PreparedRow, PreparedRowLayers>()
    val blurredCaptions = mutableStateMapOf<TextLayoutResult, PreparedBlurTile>()

    fun stateFor(row: PreparedRow): LunarRowRenderState =
        rowStates.getOrPut(row) { LunarRowRenderState(row) }

    suspend fun ensureRaster(row: PreparedRow): PreparedRowLayers {
        preparedRasters[row]?.let { return it }
        val built =
            withContext(Dispatchers.Default) {
                rasterMutex.withLock {
                    preparedRasters[row]
                        ?: buildRowLayers(row, color, phoneticColor, density, layoutDirection, atlas)
                }
            }
        preparedRasters[row] = built
        return built
    }

    suspend fun ensureCaptionBlur(
        layout: TextLayoutResult,
        captionColor: Color,
    ): PreparedBlurTile? {
        if (supportsHardwareRenderEffectBlur()) return null
        blurredCaptions[layout]?.let { return it }
        val built =
            withContext(Dispatchers.Default) {
                rasterMutex.withLock {
                    blurredCaptions[layout]
                        ?: atlas.putBlurredTile(
                            key =
                                CaptionRasterKey(
                                    text = layout.layoutInput.text.text,
                                    style = layout.layoutInput.style,
                                    colorValue = captionColor.value,
                                    blurred = true,
                                ),
                            fullWidthPx = layout.size.width.toFloat(),
                            fullHeightPx = layout.size.height.toFloat(),
                            density = density,
                            layoutDirection = layoutDirection,
                        ) {
                            drawText(layout, color = captionColor)
                        }
                }
            } ?: return null
        blurredCaptions[layout] = built
        return built
    }

    suspend fun prewarmLines(lines: List<PreparedLine>) {
        if (lines.isEmpty()) return
        val needSoftBlur = !supportsHardwareRenderEffectBlur()
        val builtRows = ArrayList<Pair<PreparedRow, PreparedRowLayers>>(lines.size * 2)
        val builtCaptions = ArrayList<Pair<TextLayoutResult, PreparedBlurTile>>(lines.size * 2)
        withContext(Dispatchers.Default) {
            rasterMutex.withLock {
                for (line in lines) {
                    for (row in line.rows) {
                        if (preparedRasters[row] == null) {
                            builtRows.add(
                                row to buildRowLayers(row, color, phoneticColor, density, layoutDirection, atlas),
                            )
                        }
                    }
                    if (needSoftBlur) {
                        line.phonetic?.let { layout ->
                            if (blurredCaptions[layout] == null) {
                                atlas
                                    .putBlurredTile(
                                        key =
                                            CaptionRasterKey(
                                                text = layout.layoutInput.text.text,
                                                style = layout.layoutInput.style,
                                                colorValue = phoneticColor.value,
                                                blurred = true,
                                            ),
                                        fullWidthPx = layout.size.width.toFloat(),
                                        fullHeightPx = layout.size.height.toFloat(),
                                        density = density,
                                        layoutDirection = layoutDirection,
                                    ) {
                                        drawText(layout, color = phoneticColor)
                                    }?.let { builtCaptions.add(layout to it) }
                            }
                        }
                        line.translation?.let { layout ->
                            if (blurredCaptions[layout] == null) {
                                atlas
                                    .putBlurredTile(
                                        key =
                                            CaptionRasterKey(
                                                text = layout.layoutInput.text.text,
                                                style = layout.layoutInput.style,
                                                colorValue = translationColor.value,
                                                blurred = true,
                                            ),
                                        fullWidthPx = layout.size.width.toFloat(),
                                        fullHeightPx = layout.size.height.toFloat(),
                                        density = density,
                                        layoutDirection = layoutDirection,
                                    ) {
                                        drawText(layout, color = translationColor)
                                    }?.let { builtCaptions.add(layout to it) }
                            }
                        }
                    }
                }
            }
        }
        if (builtRows.isNotEmpty() || builtCaptions.isNotEmpty()) {
            for ((row, layers) in builtRows) {
                preparedRasters[row] = layers
            }
            for ((layout, tile) in builtCaptions) {
                blurredCaptions[layout] = tile
            }
        }
    }
}

private fun buildRowLayers(
    row: PreparedRow,
    color: Color,
    phoneticColor: Color,
    density: Density,
    layoutDirection: LayoutDirection,
    atlas: LunarTextAtlas,
): PreparedRowLayers {
    // Held-word Shadow glow is baked into software ARGB_8888 LunarTextAtlas pages and works on all API levels (API 26+).
    val glowShadow =
        Shadow(
            color = color.copy(alpha = color.alpha * GLOW_ALPHA_FACTOR),
            offset = Offset.Zero,
            blurRadius = GLOW_BLUR_RADIUS_PX,
        )
    val runs =
        row.runs.map { run ->
            run.groups.map { group ->
                val staticUnit = group.staticText
                val combined =
                    staticUnit?.let { unit ->
                        atlas.getOrPut(
                            key = buildUnitKey(run.profile, unit, color, glow = false),
                            width = unit.width,
                            height = unit.height,
                            density = density,
                            layoutDirection = layoutDirection,
                        ) {
                            with(run.profile) { draw(unit, color, Shadow.None) }
                        }
                    }
                val glow =
                    if (group.effects.glow && staticUnit != null) {
                        atlas.getOrPut(
                            key = buildUnitKey(run.profile, staticUnit, color, glow = true),
                            width = staticUnit.width,
                            height = staticUnit.height,
                            density = density,
                            layoutDirection = layoutDirection,
                            pad = GLOW_PAD_PX,
                        ) {
                            with(run.profile) { draw(staticUnit, color, glowShadow) }
                        }
                    } else {
                        null
                    }
                val phoneticIndices =
                    group.units.indices
                        .filter { group.units[it].phonetic != null }
                        .toIntArray()
                val units =
                    group.units.map { unit ->
                        val unitLayer =
                            atlas.getOrPut(
                                key = buildUnitKey(run.profile, unit.text, color, glow = false),
                                width = unit.width,
                                height = unit.text.height,
                                density = density,
                                layoutDirection = layoutDirection,
                            ) {
                                with(run.profile) { draw(unit.text, color, Shadow.None) }
                            }
                        val phoneticLayer =
                            unit.phonetic?.let { phoneticLayout ->
                                atlas.getOrPut(
                                    key =
                                        CaptionRasterKey(
                                            text = phoneticLayout.layoutInput.text.text,
                                            style = phoneticLayout.layoutInput.style,
                                            colorValue = phoneticColor.value,
                                        ),
                                    width = phoneticLayout.size.width.toFloat(),
                                    height = phoneticLayout.size.height.toFloat(),
                                    density = density,
                                    layoutDirection = layoutDirection,
                                ) {
                                    drawText(phoneticLayout, phoneticColor)
                                }
                            }
                        PreparedUnitLayers(unitLayer, phoneticLayer)
                    }
                PreparedGroupLayers(combined, glow, units, phoneticIndices)
            }
        }

    val needSoftBlur = !supportsHardwareRenderEffectBlur()
    val mainHeightPx = (row.height - row.phoneticHeight).coerceAtLeast(0f)
    val rowFullWidthPx = maxOf(row.bounds.right, row.width)
    val blurredMain =
        if (needSoftBlur && mainHeightPx > 0.5f && rowFullWidthPx > 0.5f) {
            atlas.putBlurredTile(
                key = null,
                fullWidthPx = rowFullWidthPx,
                fullHeightPx = mainHeightPx,
                density = density,
                layoutDirection = layoutDirection,
            ) {
                translate(top = -row.top) {
                    for (run in row.runs) {
                        for (group in run.groups) {
                            val staticText = group.staticText
                            if (staticText != null) {
                                translate(group.staticPosition.x, group.staticPosition.y) {
                                    with(run.profile) { draw(staticText, color, Shadow.None) }
                                }
                            } else {
                                for (unit in group.units) {
                                    translate(unit.position.x, unit.position.y) {
                                        with(run.profile) { draw(unit.text, color, Shadow.None) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            null
        }

    val blurredPhonetics =
        if (needSoftBlur && row.phoneticHeight > 0.5f && rowFullWidthPx > 0.5f) {
            atlas.putBlurredTile(
                key = null,
                fullWidthPx = rowFullWidthPx,
                fullHeightPx = row.phoneticHeight,
                density = density,
                layoutDirection = layoutDirection,
            ) {
                translate(top = -(row.top + mainHeightPx)) {
                    for (run in row.runs) {
                        for (group in run.groups) {
                            for (unit in group.units) {
                                val layout = unit.phonetic ?: continue
                                translate(
                                    unit.position.x + unit.phoneticPosition.x,
                                    unit.position.y + unit.phoneticPosition.y,
                                ) {
                                    drawText(layout, phoneticColor)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            null
        }

    return PreparedRowLayers(
        runs = runs,
        blurredMain = blurredMain,
        blurredPhonetics = blurredPhonetics,
    )
}

private fun buildUnitKey(
    profile: LyricsProfile,
    unit: ProfileTextUnit,
    color: Color,
    glow: Boolean,
): UnitRasterKey? {
    val range = unit.sourceRange ?: return null
    val input = unit.layout.layoutInput
    if (range.min != 0 || range.max != input.text.length) return null
    return UnitRasterKey(
        profile = profile,
        text = input.text.text,
        style = input.style,
        startOffset = range.min,
        endOffset = range.max,
        colorValue = color.value,
        glow = glow,
    )
}

internal fun DrawScope.drawLunarPreparedRow(
    row: PreparedRow,
    time: Int,
    state: LunarRowRenderState,
    resources: LunarRenderResources,
    layers: PreparedRowLayers?,
    drawOriginal: Boolean = true,
    drawPhonetics: Boolean = true,
    alphaMultiplier: Float = 1f,
) {
    if (alphaMultiplier <= 0.004f) return
    val canvas = drawContext.canvas
    val inactive = row.animated && time < row.start
    val rowAlpha = (if (inactive) FocusedRowUnlitAlpha else 1f) * alphaMultiplier.coerceIn(0f, 1f)
    val drawColor =
        if (rowAlpha < 0.999f) resources.color.copy(alpha = resources.color.alpha * rowAlpha) else resources.color
    val phoneticColor =
        if (rowAlpha < 0.999f) {
            resources.phoneticColor.copy(alpha = resources.phoneticColor.alpha * rowAlpha)
        } else {
            resources.phoneticColor
        }
    val masked = row.animated && !inactive && time < row.sweepEnd
    if (masked) {
        val bounds =
            if (drawOriginal) {
                row.bounds
            } else {
                row.bounds.copy(
                    top = row.top + row.height - row.phoneticHeight - 2f,
                    bottom = row.top + row.height + 2f,
                )
            }
        canvas.saveLayer(bounds, state.layerPaint)
    }
    try {
        if (drawOriginal) {
            for (runIndex in row.runs.indices) {
                val run = row.runs[runIndex]
                for (groupIndex in run.groups.indices) {
                    val group = run.groups[groupIndex]
                    val cachedGroup = layers?.runs?.getOrNull(runIndex)?.getOrNull(groupIndex)
                    val staticText = group.staticText
                    if (
                        staticText != null &&
                        (!row.animated ||
                            time < group.sourceStart ||
                            time >= group.effectsEnd ||
                            (!group.awesome && group.commonSource))
                    ) {
                        val progress =
                            if (!row.animated) {
                                1f
                            } else {
                                ((time.toDouble() - group.sourceStart) / 700.0)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            }
                        val lift =
                            if (time >= group.effectsEnd || !row.animated) {
                                0f
                            } else if (group.effects.lift) {
                                4f * (1f - progress) * (1f - progress)
                            } else {
                                0f
                            }
                        translate(group.staticPosition.x, group.staticPosition.y + lift) {
                            val combinedLayer = cachedGroup?.combined
                            if (combinedLayer != null) {
                                with(combinedLayer) { drawLayer(rowAlpha) }
                            } else {
                                with(run.profile) { draw(staticText, drawColor, Shadow.None) }
                            }
                        }
                        continue
                    }

                    drawAnimatedGroup(
                        row = row,
                        group = group,
                        profile = run.profile,
                        cachedGroup = cachedGroup,
                        time = time,
                        rowAlpha = rowAlpha,
                        drawColor = drawColor,
                    )
                }
            }
        }

        if (drawPhonetics) {
            for (runIndex in row.runs.indices) {
                val run = row.runs[runIndex]
                for (groupIndex in run.groups.indices) {
                    val group = run.groups[groupIndex]
                    val cachedGroup = layers?.runs?.getOrNull(runIndex)?.getOrNull(groupIndex)
                    val phoneticIndices = cachedGroup?.phoneticIndices
                    if (phoneticIndices != null) {
                        for (unitIndex in phoneticIndices) {
                            val unit = group.units[unitIndex]
                            val layout = unit.phonetic ?: continue
                            translate(
                                unit.position.x + unit.phoneticPosition.x,
                                unit.position.y + unit.phoneticPosition.y,
                            ) {
                                val cachedPhonetic = cachedGroup.units.getOrNull(unitIndex)?.phonetic
                                if (cachedPhonetic != null) {
                                    with(cachedPhonetic) { drawLayer(rowAlpha) }
                                } else {
                                    drawText(layout, phoneticColor)
                                }
                            }
                        }
                    } else {
                        for (unit in group.units) {
                            val layout = unit.phonetic ?: continue
                            translate(
                                unit.position.x + unit.phoneticPosition.x,
                                unit.position.y + unit.phoneticPosition.y,
                            ) {
                                drawText(layout, phoneticColor)
                            }
                        }
                    }
                }
            }
        }

        if (masked) {
            val center = state.sweepCenter(time)
            translate(left = center) {
                drawRect(
                    brush = state.mask,
                    topLeft = Offset(row.bounds.left - center, row.bounds.top),
                    size = row.bounds.size,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    } finally {
        if (masked) {
            canvas.restore()
        }
    }
}

private fun DrawScope.drawAnimatedGroup(
    row: PreparedRow,
    group: PreparedGroup,
    profile: LyricsProfile,
    cachedGroup: PreparedGroupLayers?,
    time: Int,
    rowAlpha: Float,
    drawColor: Color,
) {
    val groupProgress =
        if (!row.animated) {
            1f
        } else {
            ((time.toDouble() - group.start) / group.animationDuration)
                .toFloat()
                .coerceIn(0f, 1f)
        }
    val swellPeak = 0.1f * group.intensity

    // Single pre-baked software atlas glow draw for held words/phrases across all Android versions (zero FBO allocation).
    val glowLayer = cachedGroup?.glow
    if (group.effects.glow && glowLayer != null) {
        val glowStrength = glowBounceStrength(groupProgress)
        if (glowStrength > 0.01f) {
            val groupScale =
                if (group.effects.scale) 1f + swellScaleDelta(swellPeak, groupProgress) else 1f
            val liftProgress =
                if (!row.animated) {
                    1f
                } else {
                    ((time.toDouble() - group.sourceStart) / 700.0).toFloat().coerceIn(0f, 1f)
                }
            val groupLift =
                if (group.effects.lift) 4f * (1f - liftProgress) * (1f - liftProgress) else 0f
            withTransform({
                if (groupScale != 1f) {
                    scale(groupScale, groupScale, group.pivot)
                }
                translate(group.staticPosition.x, group.staticPosition.y + groupLift)
            }) {
                with(glowLayer) { drawLayer(rowAlpha * glowStrength) }
            }
        }
    }

    for (unitIndex in group.units.indices) {
        val unit = group.units[unitIndex]
        val progress =
            if (!row.animated) {
                1f
            } else {
                ((time.toDouble() - unit.animationStart) / group.animationDuration)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        val liftProgress =
            if (!row.animated) {
                1f
            } else {
                ((time.toDouble() - unit.text.animation.start) / 700.0)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        val lift = if (group.effects.lift) 4f * (1f - liftProgress) * (1f - liftProgress) else 0f
        val unitScale =
            if (group.effects.scale) 1f + swellScaleDelta(swellPeak, progress) else 1f
        withTransform({
            if (unitScale != 1f) {
                scale(unitScale, unitScale, group.pivot)
            }
            translate(unit.position.x, unit.position.y + lift)
        }) {
            val cachedUnit = cachedGroup?.units?.getOrNull(unitIndex)?.text
            if (cachedUnit != null) {
                with(cachedUnit) { drawLayer(rowAlpha) }
            } else {
                with(profile) { draw(unit.text, drawColor, Shadow.None) }
            }
        }
    }
}

@Composable
internal fun LunarLyricsReveal(
    visible: Boolean,
    fromBottom: Boolean = false,
    keepContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "lyricsReveal",
    )
    if (!keepContent && !visible && progress <= 0.005f) return
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val targetHeight = (placeable.height * clamped).roundToInt()
                    val yOffset = if (fromBottom) targetHeight - placeable.height else 0
                    layout(placeable.width, targetHeight) {
                        if (targetHeight > 0) {
                            placeable.placeRelative(0, yOffset)
                        }
                    }
                }.graphicsLayer {
                    alpha = clamped
                    clip = clamped < 0.999f
                },
    ) {
        content()
    }
}

private fun computeRowAnimationEndMs(row: PreparedRow): Int {
    if (!row.animated) return row.end
    var maxEnd = maxOf(row.end, row.sweepEnd)
    for (run in row.runs) {
        for (group in run.groups) {
            maxEnd = maxOf(maxEnd, ceil(group.effectsEnd).toInt())
        }
    }
    return maxEnd
}

@Composable
internal fun LunarPreparedLineText(
    prepared: PreparedLine,
    currentTimeProvider: () -> Int,
    resources: LunarRenderResources,
    showPhonetic: Boolean = true,
    showTranslation: Boolean = true,
    verticalPadding: Dp = 8.dp,
    softBlurRadiusProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    onChildClick: (PreparedLine) -> Unit = {},
    onChildLongClick: (PreparedLine) -> Unit = {},
) {
    val currentTime by rememberUpdatedState(currentTimeProvider)
    val softBlurRadius by rememberUpdatedState(softBlurRadiusProvider)
    val density = LocalDensity.current
    val useSoftBlur = !supportsHardwareRenderEffectBlur()
    val isAccompaniment = prepared.source is KaraokeLine.AccompanimentKaraokeLine
    val accompanimentAlpha = if (isAccompaniment) 0.65f else 1f
    val alignment = if (prepared.rightAligned) AbsoluteAlignment.Right else AbsoluteAlignment.Left

    Column(
        modifier =
            modifier
                .graphicsLayer { alpha = accompanimentAlpha }
                .fillMaxWidth()
                .padding(
                    vertical = verticalPadding,
                    horizontal = if (isAccompaniment) 0.dp else 16.dp,
                ),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (child in prepared.before) {
            val visStart = minOf(child.source.start, prepared.source.start)
            val visEnd = maxOf(child.source.end, prepared.source.end)
            val isChildVisible by remember(child, visStart, visEnd) {
                derivedStateOf { currentTime() in visStart..visEnd }
            }
            val isChildFocused by remember(child) {
                derivedStateOf { currentTime() in child.source.start..child.source.end }
            }
            LunarLyricsReveal(
                visible = isChildVisible,
                fromBottom = child.source.start < prepared.source.start,
            ) {
                LunarLyricsLineItem(
                    isFocused = isChildFocused,
                    isRightAligned = child.rightAligned,
                    onLineClicked = { onChildClick(child) },
                    onLinePressed = { onChildLongClick(child) },
                    blurRadius = { 0f },
                    inactiveAlpha = 0.45f,
                ) {
                    LunarPreparedLineText(
                        prepared = child,
                        currentTimeProvider = currentTimeProvider,
                        resources = resources,
                        showPhonetic = showPhonetic,
                        showTranslation = showTranslation,
                        verticalPadding = 2.dp,
                        softBlurRadiusProvider = softBlurRadiusProvider,
                        onChildClick = onChildClick,
                        onChildLongClick = onChildLongClick,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment,
        ) {
            for ((index, row) in prepared.rows.withIndex()) {
                val state = remember(row, resources) { resources.stateFor(row) }
                val rowAnimEnd = remember(row) { computeRowAnimationEndMs(row) }
                // Only actively animating rows subscribe to 120Hz `currentTime()` inside drawBehind;
                // rows before/after their window freeze cleanly at 0 redraws/sec.
                val rowPhase by remember(row, rowAnimEnd) {
                    derivedStateOf {
                        if (!row.animated) {
                            1
                        } else {
                            val now = currentTime()
                            when {
                                now < row.start -> -1
                                now <= rowAnimEnd -> 0
                                else -> 1
                            }
                        }
                    }
                }
                val drawTime =
                    remember(row, rowAnimEnd) {
                        {
                            when (rowPhase) {
                                -1 -> row.start - 1
                                0 -> currentTime()
                                else -> rowAnimEnd + 1
                            }
                        }
                    }

                val layers = resources.preparedRasters[row]
                if (layers == null) {
                    LaunchedEffect(row, resources) {
                        resources.ensureRaster(row)
                    }
                }

                val hasInlinePhonetics = row.phoneticHeight > 0f
                val mainHeightPx = (row.height - row.phoneticHeight).coerceAtLeast(0f)
                val mainHeightDp = with(density) { mainHeightPx.toDp() }
                val followingGapPx = prepared.rows.getOrNull(index + 1)?.phoneticSpacingBefore ?: 0f

                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(mainHeightDp)
                            .drawBehind {
                                val rawBlurPx = if (useSoftBlur) softBlurRadius() else 0f
                                val currentLayers = resources.preparedRasters[row]
                                val now = drawTime()
                                val blurTile = if (useSoftBlur) currentLayers?.blurredMain else null
                                val blurPx = if (blurTile != null) rawBlurPx else 0f
                                val blurMix = (blurPx / SOFT_BLUR_FULL_RADIUS_REF_PX).coerceIn(0f, 1f)
                                if (blurMix < 0.99f) {
                                    translate(top = -row.top) {
                                        drawLunarPreparedRow(
                                            row = row,
                                            time = now,
                                            state = state,
                                            resources = resources,
                                            layers = currentLayers,
                                            drawOriginal = true,
                                            drawPhonetics = false,
                                            alphaMultiplier = 1f - blurMix,
                                        )
                                    }
                                }
                                if (blurMix > 0.01f && blurTile != null) {
                                    val baseRowAlpha = if (row.animated && now < row.start) FocusedRowUnlitAlpha else 1f
                                    with(blurTile) {
                                        drawBlur(alpha = baseRowAlpha * blurMix)
                                    }
                                }
                            },
                )

                if (hasInlinePhonetics) {
                    LunarLyricsReveal(visible = showPhonetic, keepContent = true) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val phoneticHeightDp = with(density) { row.phoneticHeight.toDp() }
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(phoneticHeightDp)
                                        .drawBehind {
                                            val rawBlurPx = if (useSoftBlur) softBlurRadius() else 0f
                                            val currentLayers = resources.preparedRasters[row]
                                            val now = drawTime()
                                            val blurTile = if (useSoftBlur) currentLayers?.blurredPhonetics else null
                                            val blurPx = if (blurTile != null) rawBlurPx else 0f
                                            val blurMix = (blurPx / SOFT_BLUR_FULL_RADIUS_REF_PX).coerceIn(0f, 1f)
                                            if (blurMix < 0.99f) {
                                                translate(top = -(row.top + mainHeightPx)) {
                                                    drawLunarPreparedRow(
                                                        row = row,
                                                        time = now,
                                                        state = state,
                                                        resources = resources,
                                                        layers = currentLayers,
                                                        drawOriginal = false,
                                                        drawPhonetics = true,
                                                        alphaMultiplier = 1f - blurMix,
                                                    )
                                                }
                                            }
                                            if (blurMix > 0.01f && blurTile != null) {
                                                val baseRowAlpha =
                                                    if (row.animated && now < row.start) FocusedRowUnlitAlpha else 1f
                                                with(blurTile) {
                                                    drawBlur(alpha = baseRowAlpha * blurMix)
                                                }
                                            }
                                        },
                            )
                            if (followingGapPx > 0f) {
                                Spacer(Modifier.height(with(density) { followingGapPx.toDp() }))
                            }
                        }
                    }
                }
            }
        }

        val linePhonetic = prepared.phonetic
        if (linePhonetic != null) {
            if (useSoftBlur && resources.blurredCaptions[linePhonetic] == null) {
                LaunchedEffect(linePhonetic, resources) {
                    resources.ensureCaptionBlur(linePhonetic, resources.phoneticColor)
                }
            }
            LunarLyricsReveal(visible = showPhonetic, keepContent = true) {
                val wDp = with(density) { linePhonetic.size.width.toDp() }
                val hDp = with(density) { linePhonetic.size.height.toDp() }
                Spacer(
                    modifier =
                        Modifier
                            .paddingTopDp(4.dp)
                            .width(wDp)
                            .height(hDp)
                            .drawBehind {
                                val rawBlurPx = if (useSoftBlur) softBlurRadius() else 0f
                                val blurTile = if (useSoftBlur) resources.blurredCaptions[linePhonetic] else null
                                val blurPx = if (blurTile != null) rawBlurPx else 0f
                                val blurMix = (blurPx / SOFT_BLUR_FULL_RADIUS_REF_PX).coerceIn(0f, 1f)
                                if (blurMix < 0.99f) {
                                    val c = resources.phoneticColor
                                    drawText(
                                        linePhonetic,
                                        color = if (blurMix > 0.005f) c.copy(alpha = c.alpha * (1f - blurMix)) else c,
                                    )
                                }
                                if (blurMix > 0.01f && blurTile != null) {
                                    with(blurTile) { drawBlur(alpha = blurMix) }
                                }
                            },
                )
            }
        }

        val lineTranslation = prepared.translation
        if (lineTranslation != null) {
            if (useSoftBlur && resources.blurredCaptions[lineTranslation] == null) {
                LaunchedEffect(lineTranslation, resources) {
                    resources.ensureCaptionBlur(lineTranslation, resources.translationColor)
                }
            }
            LunarLyricsReveal(visible = showTranslation, keepContent = true) {
                val wDp = with(density) { lineTranslation.size.width.toDp() }
                val hDp = with(density) { lineTranslation.size.height.toDp() }
                Spacer(
                    modifier =
                        Modifier
                            .paddingTopDp(if (linePhonetic != null && showPhonetic) 2.dp else 4.dp)
                            .width(wDp)
                            .height(hDp)
                            .drawBehind {
                                val rawBlurPx = if (useSoftBlur) softBlurRadius() else 0f
                                val blurTile = if (useSoftBlur) resources.blurredCaptions[lineTranslation] else null
                                val blurPx = if (blurTile != null) rawBlurPx else 0f
                                val blurMix = (blurPx / SOFT_BLUR_FULL_RADIUS_REF_PX).coerceIn(0f, 1f)
                                if (blurMix < 0.99f) {
                                    val c = resources.translationColor
                                    drawText(
                                        lineTranslation,
                                        color = if (blurMix > 0.005f) c.copy(alpha = c.alpha * (1f - blurMix)) else c,
                                    )
                                }
                                if (blurMix > 0.01f && blurTile != null) {
                                    with(blurTile) { drawBlur(alpha = blurMix) }
                                }
                            },
                )
            }
        }

        for (child in prepared.after) {
            val visStart = minOf(child.source.start, prepared.source.start)
            val visEnd = maxOf(child.source.end, prepared.source.end)
            val isChildVisible by remember(child, visStart, visEnd) {
                derivedStateOf { currentTime() in visStart..visEnd }
            }
            val isChildFocused by remember(child) {
                derivedStateOf { currentTime() in child.source.start..child.source.end }
            }
            LunarLyricsReveal(
                visible = isChildVisible,
                fromBottom = child.source.start < prepared.source.start,
            ) {
                LunarLyricsLineItem(
                    isFocused = isChildFocused,
                    isRightAligned = child.rightAligned,
                    onLineClicked = { onChildClick(child) },
                    onLinePressed = { onChildLongClick(child) },
                    blurRadius = { 0f },
                    inactiveAlpha = 0.45f,
                ) {
                    LunarPreparedLineText(
                        prepared = child,
                        currentTimeProvider = currentTimeProvider,
                        resources = resources,
                        showPhonetic = showPhonetic,
                        showTranslation = showTranslation,
                        verticalPadding = 2.dp,
                        softBlurRadiusProvider = softBlurRadiusProvider,
                        onChildClick = onChildClick,
                        onChildLongClick = onChildLongClick,
                    )
                }
            }
        }
    }
}

private fun Modifier.paddingTopDp(top: Dp): Modifier =
    layout { measurable, constraints ->
        val topPx = top.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height + topPx) {
            placeable.placeRelative(0, topPx)
        }
    }
