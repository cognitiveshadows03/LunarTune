/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import android.os.Build
import android.os.SystemClock
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.citali.lunartune.utils.ImageBlurUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val QueueCpuBlurDownscale = 6f
private const val QueueCpuBlurRadius = 10f
private const val QueueCpuBlurIntervalMs = 180L
private val QueueLiveBlurRadius = 30.dp

/**
 * Live CPU blur state for Android 11 and below (API < 31), where hardware `RenderEffect` is
 * unavailable. Captures the actual rendered Player Screen into a downscaled graphics layer while
 * the queue is visible and blurs it off the main thread. On Android 12+ `Haze`'s GPU `RenderEffect`
 * path handles the live blur directly at full frame rate, so this state is unused (`null`).
 */
internal class QueueLivePlayerBlurState {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    var captureScale: Float = 1f
        private set

    var sourceLeftInRoot: Float = 0f
    var sourceTopInRoot: Float = 0f

    internal var samplingActive: Boolean = false
    internal var lastRecordAtMs: Long = 0L
    internal var recordVersion: Int = 0
    internal var recordedScale: Float = 1f

    internal fun publish(
        blurred: ImageBitmap,
        scale: Float,
    ) {
        captureScale = scale
        image = blurred
    }
}

@Composable
internal fun rememberQueueLivePlayerBlurState(): QueueLivePlayerBlurState? =
    remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            QueueLivePlayerBlurState()
        } else {
            null
        }
    }

/**
 * Marks the live rendered Player Screen tree as the blur source behind the Queue sheet.
 *
 * Whatever player design style (`V1`–`V10`), background mode, dynamic palette, or animated canvas
 * is currently rendered inside this node is sampled directly, with zero duplicate player
 * composition and zero recursion (the Queue sits as a sibling above this node).
 */
@Composable
internal fun Modifier.queueLivePlayerSource(
    hazeState: HazeState?,
    liveBlurState: QueueLivePlayerBlurState?,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (hazeState != null) this.hazeSource(hazeState) else this
    }

    val blurState = liveBlurState ?: return this
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(blurState, layer) {
        var lastVersion = 0
        while (isActive) {
            delay(QueueCpuBlurIntervalMs)
            if (!blurState.samplingActive) continue
            val version = blurState.recordVersion
            if (version == lastVersion) continue
            lastVersion = version
            val scale = blurState.recordedScale
            val shot = runCatching { layer.toImageBitmap() }.getOrNull() ?: continue
            val blurred =
                withContext(Dispatchers.Default) {
                    runCatching {
                        ImageBlurUtils.blur(shot.asAndroidBitmap(), QueueCpuBlurRadius).asImageBitmap()
                    }.getOrNull()
                } ?: continue
            blurState.publish(blurred, scale)
        }
    }

    return this
        .onGloballyPositioned { coordinates ->
            val pos = coordinates.positionInRoot()
            blurState.sourceLeftInRoot = pos.x
            blurState.sourceTopInRoot = pos.y
        }.drawWithContent {
            val now = SystemClock.uptimeMillis()
            if (
                blurState.samplingActive &&
                size.width > QueueCpuBlurDownscale &&
                size.height > QueueCpuBlurDownscale &&
                now - blurState.lastRecordAtMs >= QueueCpuBlurIntervalMs
            ) {
                val layerWidth = (size.width / QueueCpuBlurDownscale).roundToInt().coerceAtLeast(1)
                val layerHeight = (size.height / QueueCpuBlurDownscale).roundToInt().coerceAtLeast(1)
                val recorded =
                    runCatching {
                        layer.record(
                            density = density,
                            layoutDirection = layoutDirection,
                            size = IntSize(layerWidth, layerHeight),
                        ) {
                            scale(
                                scaleX = 1f / QueueCpuBlurDownscale,
                                scaleY = 1f / QueueCpuBlurDownscale,
                                pivot = Offset.Zero,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }.isSuccess
                if (recorded) {
                    blurState.lastRecordAtMs = now
                    blurState.recordedScale = layerWidth.toFloat() / size.width
                    blurState.recordVersion++
                }
            }
            drawContent()
        }
}

/**
 * Cohesive Android-16 notification-shade frosted-glass palette derived from the active theme and
 * current album artwork colors (`MaterialTheme.colorScheme`).
 */
@Immutable
internal data class QueueGlassPalette(
    val isDark: Boolean,
    val accent: Color,
    val glassBaseTint: Color,
    val sheetHazeStyle: HazeBlurStyle,
    val sheetScrimBrush: Brush,
    val fallbackScrimBrush: Brush,
    val sheetTopRimColor: Color,
    val dragHandleColor: Color,
    val headerPanelBrush: Brush,
    val headerPanelBorderBrush: Brush,
    val infoPillFill: Color,
    val infoPillBorder: Color,
    val artworkBorder: Color,
    val heartPillFill: Color,
    val heartPillActiveFill: Color,
    val heartPillBorder: Color,
    val tileActiveBrush: Brush,
    val tileActiveBorderBrush: Brush,
    val tileActiveBadgeFill: Color,
    val tileActiveContentColor: Color,
    val tileIdleBrush: Brush,
    val tileIdleBorderBrush: Brush,
    val tileIdleBadgeFill: Color,
    val tileIdleContentColor: Color,
    val tileClearBrush: Brush,
    val tileClearBorderBrush: Brush,
    val tileClearBadgeFill: Color,
    val tileClearContentColor: Color,
    val listOuterTrayBrush: Brush,
    val listOuterTrayBorderBrush: Brush,
    val listInnerCardBrush: Brush,
    val listInnerCardBorderBrush: Brush,
    val listScrollEdgeColor: Color,
    val rowDividerColor: Color,
    val rowActiveBrush: Brush,
    val rowActiveBorder: Color,
    val rowSelectedBrush: Brush,
    val rowSelectedBorder: Color,
    val rowTitleColor: Color,
    val rowSubtitleColor: Color,
    val rowPositionColor: Color,
    val rowTrailingIconColor: Color,
)

@Composable
internal fun rememberQueueGlassPalette(
    backgroundColor: Color,
    onBackgroundColor: Color,
    pureBlack: Boolean,
): QueueGlassPalette {
    val colorScheme: ColorScheme = MaterialTheme.colorScheme
    return remember(
        backgroundColor,
        onBackgroundColor,
        pureBlack,
        colorScheme.primary,
        colorScheme.secondaryContainer,
        colorScheme.error,
    ) {
        val isDark = backgroundColor.luminance() < 0.5f
        if (isDark) {
            val softLavender = Color(0xFFBDBAF2)
            val accent = lerp(colorScheme.primary, softLavender, 0.28f)
            val albumWash = lerp(backgroundColor, colorScheme.secondaryContainer, 0.28f)
            val glassBase =
                lerp(
                    Color(0xFF161822),
                    albumWash,
                    if (pureBlack) 0.36f else 0.70f,
                )
            val softError = lerp(colorScheme.error, Color(0xFFF39CA5), 0.28f)

            val sheetHazeStyle =
                HazeBlurStyle {
                    blurEnabled(true)
                    blurRadius(QueueLiveBlurRadius)
                    backgroundColor(glassBase)
                    colorEffects(emptyList())
                    fallbackColorEffect(HazeColorEffect.tint(glassBase.copy(alpha = 0.86f)))
                    noiseFactor(0f)
                }

            val topAlpha = if (pureBlack) 0.42f else 0.30f
            val midAlpha = if (pureBlack) 0.50f else 0.36f
            val bottomAlpha = if (pureBlack) 0.56f else 0.42f

            QueueGlassPalette(
                isDark = true,
                accent = accent,
                glassBaseTint = glassBase,
                sheetHazeStyle = sheetHazeStyle,
                sheetScrimBrush =
                    Brush.verticalGradient(
                        listOf(
                            glassBase.copy(alpha = topAlpha),
                            glassBase.copy(alpha = midAlpha),
                            glassBase.copy(alpha = bottomAlpha),
                        ),
                    ),
                fallbackScrimBrush =
                    Brush.verticalGradient(
                        listOf(
                            glassBase.copy(alpha = 0.86f),
                            glassBase.copy(alpha = 0.92f),
                        ),
                    ),
                sheetTopRimColor = Color.White.copy(alpha = 0.16f),
                dragHandleColor = Color.White.copy(alpha = 0.38f),
                headerPanelBrush =
                    Brush.verticalGradient(
                        listOf(
                            lerp(Color.White, accent, 0.20f).copy(alpha = 0.12f),
                            lerp(Color.White, accent, 0.14f).copy(alpha = 0.065f),
                        ),
                    ),
                headerPanelBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                infoPillFill = Color.Black.copy(alpha = 0.20f),
                infoPillBorder = Color.White.copy(alpha = 0.11f),
                artworkBorder = Color.White.copy(alpha = 0.14f),
                heartPillFill = Color.White.copy(alpha = 0.08f),
                heartPillActiveFill = accent.copy(alpha = 0.24f),
                heartPillBorder = Color.White.copy(alpha = 0.14f),
                tileActiveBrush =
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.38f),
                            accent.copy(alpha = 0.25f),
                        ),
                    ),
                tileActiveBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.56f),
                            accent.copy(alpha = 0.28f),
                        ),
                    ),
                tileActiveBadgeFill = accent.copy(alpha = 0.34f),
                tileActiveContentColor = lerp(Color.White, accent, 0.12f),
                tileIdleBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.White.copy(alpha = 0.055f),
                        ),
                    ),
                tileIdleBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                tileIdleBadgeFill = Color.White.copy(alpha = 0.10f),
                tileIdleContentColor = onBackgroundColor.copy(alpha = 0.90f),
                tileClearBrush =
                    Brush.verticalGradient(
                        listOf(
                            softError.copy(alpha = 0.13f),
                            softError.copy(alpha = 0.06f),
                        ),
                    ),
                tileClearBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            softError.copy(alpha = 0.30f),
                            softError.copy(alpha = 0.15f),
                        ),
                    ),
                tileClearBadgeFill = softError.copy(alpha = 0.16f),
                tileClearContentColor = lerp(softError, Color.White, 0.18f),
                listOuterTrayBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.24f),
                        ),
                    ),
                listOuterTrayBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.07f),
                        ),
                    ),
                listInnerCardBrush =
                    Brush.verticalGradient(
                        listOf(
                            lerp(Color.White, accent, 0.16f).copy(alpha = 0.115f),
                            lerp(Color.White, accent, 0.10f).copy(alpha = 0.065f),
                        ),
                    ),
                listInnerCardBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ),
                listScrollEdgeColor = glassBase.copy(alpha = 0.34f),
                rowDividerColor = Color.White.copy(alpha = 0.085f),
                rowActiveBrush =
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.20f),
                            accent.copy(alpha = 0.08f),
                        ),
                    ),
                rowActiveBorder = accent.copy(alpha = 0.28f),
                rowSelectedBrush =
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.28f),
                            accent.copy(alpha = 0.14f),
                        ),
                    ),
                rowSelectedBorder = accent.copy(alpha = 0.42f),
                rowTitleColor = Color.White.copy(alpha = 0.95f),
                rowSubtitleColor = Color.White.copy(alpha = 0.68f),
                rowPositionColor = Color.White.copy(alpha = 0.60f),
                rowTrailingIconColor = Color.White.copy(alpha = 0.76f),
            )
        } else {
            val softSlateLavender = Color(0xFF47638C)
            val accent = lerp(colorScheme.primary, softSlateLavender, 0.32f)
            val glassBase = lerp(Color(0xFFE7EBF5), backgroundColor, 0.58f)
            val softError = lerp(colorScheme.error, Color(0xFFB83B4A), 0.25f)

            val sheetHazeStyle =
                HazeBlurStyle {
                    blurEnabled(true)
                    blurRadius(QueueLiveBlurRadius)
                    backgroundColor(glassBase)
                    colorEffects(emptyList())
                    fallbackColorEffect(HazeColorEffect.tint(glassBase.copy(alpha = 0.88f)))
                    noiseFactor(0f)
                }

            QueueGlassPalette(
                isDark = false,
                accent = accent,
                glassBaseTint = glassBase,
                sheetHazeStyle = sheetHazeStyle,
                sheetScrimBrush =
                    Brush.verticalGradient(
                        listOf(
                            glassBase.copy(alpha = 0.36f),
                            glassBase.copy(alpha = 0.44f),
                            glassBase.copy(alpha = 0.50f),
                        ),
                    ),
                fallbackScrimBrush =
                    Brush.verticalGradient(
                        listOf(
                            glassBase.copy(alpha = 0.88f),
                            glassBase.copy(alpha = 0.94f),
                        ),
                    ),
                sheetTopRimColor = Color.White.copy(alpha = 0.72f),
                dragHandleColor = onBackgroundColor.copy(alpha = 0.30f),
                headerPanelBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.60f),
                            Color.White.copy(alpha = 0.46f),
                        ),
                    ),
                headerPanelBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.82f),
                            Color.White.copy(alpha = 0.45f),
                        ),
                    ),
                infoPillFill = Color.Black.copy(alpha = 0.05f),
                infoPillBorder = Color.White.copy(alpha = 0.62f),
                artworkBorder = Color.Black.copy(alpha = 0.08f),
                heartPillFill = Color.White.copy(alpha = 0.52f),
                heartPillActiveFill = accent.copy(alpha = 0.18f),
                heartPillBorder = Color.White.copy(alpha = 0.75f),
                tileActiveBrush =
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.86f),
                            accent.copy(alpha = 0.74f),
                        ),
                    ),
                tileActiveBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.92f),
                            accent.copy(alpha = 0.68f),
                        ),
                    ),
                tileActiveBadgeFill = Color.White.copy(alpha = 0.22f),
                tileActiveContentColor = Color.White,
                tileIdleBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.64f),
                            Color.White.copy(alpha = 0.50f),
                        ),
                    ),
                tileIdleBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.84f),
                            Color.White.copy(alpha = 0.52f),
                        ),
                    ),
                tileIdleBadgeFill = Color.Black.copy(alpha = 0.06f),
                tileIdleContentColor = onBackgroundColor.copy(alpha = 0.88f),
                tileClearBrush =
                    Brush.verticalGradient(
                        listOf(
                            softError.copy(alpha = 0.12f),
                            softError.copy(alpha = 0.07f),
                        ),
                    ),
                tileClearBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            softError.copy(alpha = 0.30f),
                            softError.copy(alpha = 0.18f),
                        ),
                    ),
                tileClearBadgeFill = softError.copy(alpha = 0.14f),
                tileClearContentColor = softError,
                listOuterTrayBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.26f),
                        ),
                    ),
                listOuterTrayBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.42f),
                        ),
                    ),
                listInnerCardBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.64f),
                            Color.White.copy(alpha = 0.52f),
                        ),
                    ),
                listInnerCardBorderBrush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.84f),
                            Color.White.copy(alpha = 0.50f),
                        ),
                    ),
                listScrollEdgeColor = glassBase.copy(alpha = 0.36f),
                rowDividerColor = onBackgroundColor.copy(alpha = 0.08f),
                rowActiveBrush =
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            accent.copy(alpha = 0.06f),
                        ),
                    ),
                rowActiveBorder = accent.copy(alpha = 0.28f),
                rowSelectedBrush =
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.24f),
                            accent.copy(alpha = 0.12f),
                        ),
                    ),
                rowSelectedBorder = accent.copy(alpha = 0.40f),
                rowTitleColor = onBackgroundColor.copy(alpha = 0.94f),
                rowSubtitleColor = onBackgroundColor.copy(alpha = 0.66f),
                rowPositionColor = onBackgroundColor.copy(alpha = 0.58f),
                rowTrailingIconColor = onBackgroundColor.copy(alpha = 0.72f),
            )
        }
    }
}

/**
 * Applies the live-blurred Player Screen glass backdrop to the outer Queue sheet container,
 * preserving its outer rounded silhouette and drawing a subtle specular rim along its top edge.
 */
@Composable
internal fun Modifier.queueLiveSheetGlass(
    shape: Shape,
    palette: QueueGlassPalette,
    hazeState: HazeState?,
    liveBlurState: QueueLivePlayerBlurState?,
): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        return this
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = palette.sheetHazeStyle,
                performanceMode = HazePerformanceMode.Performance,
            ).drawBehind {
                drawRect(brush = palette.sheetScrimBrush)
                drawLine(
                    color = palette.sheetTopRimColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
    }

    var sheetLeftInRoot by remember { mutableStateOf(0f) }
    var sheetTopInRoot by remember { mutableStateOf(0f) }

    return this
        .clip(shape)
        .onGloballyPositioned { coordinates ->
            val pos = coordinates.positionInRoot()
            sheetLeftInRoot = pos.x
            sheetTopInRoot = pos.y
        }.drawBehind {
            val captured = liveBlurState?.image
            val scale = liveBlurState?.captureScale ?: 0f
            if (captured != null && captured.width > 0 && captured.height > 0 && scale > 0f) {
                val relLeft = (sheetLeftInRoot - (liveBlurState.sourceLeftInRoot)) * scale
                val relTop = (sheetTopInRoot - (liveBlurState.sourceTopInRoot)) * scale
                val srcWidth = (size.width * scale).roundToInt().coerceIn(1, captured.width)
                val srcHeight = (size.height * scale).roundToInt().coerceIn(1, captured.height)
                val srcLeft = relLeft.roundToInt().coerceIn(0, (captured.width - srcWidth).coerceAtLeast(0))
                val srcTop = relTop.roundToInt().coerceIn(0, (captured.height - srcHeight).coerceAtLeast(0))

                drawImage(
                    image = captured,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcWidth, srcHeight),
                    dstOffset = IntOffset.Zero,
                    dstSize =
                        IntSize(
                            size.width.roundToInt().coerceAtLeast(1),
                            size.height.roundToInt().coerceAtLeast(1),
                        ),
                    filterQuality = FilterQuality.Low,
                )
                drawRect(brush = palette.sheetScrimBrush)
            } else {
                drawRect(brush = palette.fallbackScrimBrush)
            }
            drawLine(
                color = palette.sheetTopRimColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
            )
        }
}
