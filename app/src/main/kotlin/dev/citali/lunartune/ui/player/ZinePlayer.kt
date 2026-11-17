/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dev.citali.lunartune.R
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.utils.makeTimeString
import kotlin.math.abs
import kotlin.random.Random

/**
 * Risograph zine: the player as a two-colour print.
 *
 * A real Risograph makes each colour a separate pass through the drum, so a print carries
 * the fingerprints of that process: the second pass sits a little off the first, every tone
 * is a visible dot screen, crossing inks multiply instead of covering, and the sheet is
 * paper rather than white. Those facts are the whole style.
 *
 * The halftone plate is the only expensive part, and it is built once per song rather than
 * per frame: the dots are drawn into a bitmap with a software Canvas off the main thread,
 * then the player just draws the finished image. Playback drives the colour bar and nothing
 * else, so a playback tick invalidates a draw, not a recomposition.
 */

// ---------------------------------------------------------------------------------------
// Inks
// ---------------------------------------------------------------------------------------

/** The inks a Risograph actually ships, near enough to read as the real thing. */
internal object RisoInks {
    val FluorescentPink = Color(0xFFFF48B0)
    val Blue = Color(0xFF0078BF)
    val Yellow = Color(0xFFFFE800)
    val Green = Color(0xFF00A95C)
    val Orange = Color(0xFFFF6C2F)
    val Teal = Color(0xFF00838A)
    val Purple = Color(0xFF765BA7)
    val Black = Color(0xFF1A1512)

    val All = listOf(FluorescentPink, Blue, Yellow, Green, Orange, Teal, Purple)
}

/** A resolved two-ink pairing, and the paper it is printed on. */
internal data class ZineInks(
    val first: Color,
    val second: Color,
    val paper: Color,
    val ink: Color,
)

/** Paper stock: warm, fibrous, never actually white. */
internal val ZinePaper = Color(0xFFF4F1EA)

/**
 * Turns whatever the current artwork suggests into a legal zine pairing.
 *
 * Two rules keep the print readable. The passes have to sit far enough apart in hue to read
 * as two inks rather than one smudge, and the ink used for type has to clear a contrast
 * floor against the paper - which is why yellow never ends up carrying a title.
 */
internal fun zineInksFor(candidate: Color?): ZineInks {
    val first = nearestRisoInk(candidate ?: RisoInks.FluorescentPink)
    val second =
        RisoInks.All
            .filter { it != first && hueDistance(it, first) > 40f }
            .maxByOrNull { contrastWith(it, first) }
            ?: RisoInks.Black
    // Body ink is whichever pass reads better on paper; black when neither clears the floor.
    val body =
        listOf(first, second, RisoInks.Black).firstOrNull { contrastOnPaper(it) >= 3.2f }
            ?: RisoInks.Black
    return ZineInks(
        first = first,
        second = second,
        paper = ZinePaper,
        ink = body,
    )
}

private fun nearestRisoInk(color: Color): Color = RisoInks.All.minByOrNull { hueDistance(it, color) } ?: RisoInks.Black

private fun rgb(color: Color): Triple<Int, Int, Int> {
    val argb = color.toArgb()
    return Triple(
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
    )
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

private fun hueDistance(a: Color, b: Color): Float {
    val d = abs(hueOf(a) - hueOf(b))
    return minOf(d, 360f - d)
}

/** Perceptual luma, 0..1. */
private fun luma(color: Color): Float {
    val (r, g, b) = rgb(color)
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}

/** Rough contrast of an ink against the sheet, which is a known near-white. */
private fun contrastOnPaper(color: Color): Float {
    val inkLuma = luma(color)
    val paperLuma = luma(ZinePaper)
    return (maxOf(inkLuma, paperLuma) + 0.05f) / (minOf(inkLuma, paperLuma) + 0.05f)
}

/** How distinguishable two inks are, for picking the second pass. */
private fun contrastWith(a: Color, b: Color): Float = abs(luma(a) - luma(b)) + hueDistance(a, b) / 360f

// ---------------------------------------------------------------------------------------
// The plate
// ---------------------------------------------------------------------------------------

/**
 * Registration offset, seeded from the song: a sheet prints the same way twice, and an
 * offset that re-rolled on every recomposition would read as a bug rather than as ink.
 */
internal fun zineOffsetFor(seed: String?): Pair<Float, Float> {
    val random = Random(seed?.hashCode() ?: 0)
    return 1.0f + random.nextFloat() * 1.4f to 1.0f + random.nextFloat() * 1.4f
}

private const val HalftonePitch = 5
private const val HalftoneSide = 384
private const val HalftoneCacheLimit = 4

private val halftoneCache =
    object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > HalftoneCacheLimit
    }

/**
 * Prints [thumbnailUrl] into a two-ink halftone sheet, cached per song and ink pair.
 *
 * The screen is generated the way a real one works: the source is sampled on a fixed grid
 * and each cell gets one dot whose radius tracks how much ink belongs there. The two passes
 * describe different things - the first follows luminance, the second follows saturation -
 * and the second is shifted, which is the misregistration.
 */
@Composable
internal fun rememberZinePlate(
    thumbnailUrl: String?,
    inks: ZineInks,
    seed: String?,
): ImageBitmap? {
    val context = LocalContext.current
    val key = "$thumbnailUrl|${inks.first.toArgb()}|${inks.second.toArgb()}|$seed"
    val state by produceState(
        initialValue = synchronized(halftoneCache) { halftoneCache[key] },
        key,
    ) {
        if (value != null || thumbnailUrl.isNullOrBlank()) return@produceState
        value =
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val source =
                        context.imageLoader
                            .execute(
                                ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .size(HalftoneSide)
                                    .allowHardware(false)
                                    .build(),
                            ).image
                            ?.toBitmap()
                            ?: return@withContext null
                    val plate = printHalftone(source, inks, seed).asImageBitmap()
                    synchronized(halftoneCache) { halftoneCache[key] = plate }
                    plate
                }
            }.getOrNull()
    }
    return state
}

private fun printHalftone(
    source: Bitmap,
    inks: ZineInks,
    seed: String?,
): Bitmap {
    val side = HalftoneSide
    val scaled = Bitmap.createScaledBitmap(source, side, side, true)
    val pixels = IntArray(side * side)
    scaled.getPixels(pixels, 0, side, 0, 0, side, side)

    val sheet = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(inks.paper.toArgb())

    val (dx, dy) = zineOffsetFor(seed)
    val radius = HalftonePitch / 2f
    val paint = Paint().apply { isAntiAlias = true }

    // Pass one: luminance screen, in the first ink.
    paint.color = inks.first.toArgb()
    for (y in 0 until side step HalftonePitch) {
        for (x in 0 until side step HalftonePitch) {
            val coverage = 1f - channelAt(pixels, side, x, y, Chroma.Luma)
            if (coverage <= 0.05f) continue
            canvas.drawCircle(x + radius, y + radius, radius * coverage, paint)
        }
    }

    // Pass two: saturation screen, shifted, in the second ink.
    paint.color = inks.second.toArgb()
    for (y in 0 until side step HalftonePitch) {
        for (x in 0 until side step HalftonePitch) {
            val coverage = channelAt(pixels, side, x, y, Chroma.Saturation)
            if (coverage <= 0.12f) continue
            canvas.drawCircle(x + radius + dx, y + radius + dy, radius * coverage * 0.85f, paint)
        }
    }

    return sheet
}

private enum class Chroma { Luma, Saturation }

private fun channelAt(
    pixels: IntArray,
    side: Int,
    x: Int,
    y: Int,
    channel: Chroma,
): Float {
    val pixel = pixels[y * side + x]
    return when (channel) {
        Chroma.Luma ->
            (
                0.299f * android.graphics.Color.red(pixel) +
                    0.587f * android.graphics.Color.green(pixel) +
                    0.114f * android.graphics.Color.blue(pixel)
            ) / 255f

        Chroma.Saturation -> {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(pixel, hsv)
            hsv[1]
        }
    }
}

// ---------------------------------------------------------------------------------------
// The player
// ---------------------------------------------------------------------------------------

/**
 * Zine, portrait.
 *
 * Reading order is a printed page: plate at the top, a rule, the title set tight and large,
 * the colophon line under it, then the colour bar that doubles as the progress reading.
 * Nothing moves while a track plays except that bar, which is what keeps this style calm
 * next to the blurred ones.
 */
@Composable
internal fun ZinePortraitContent(
    mediaMetadata: MediaMetadata,
    artworkUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Long,
    duration: Long,
    currentSongLiked: Boolean,
    dominantHint: Color?,
    onToggleLike: () -> Unit,
    onMenuClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inks = remember(dominantHint) { zineInksFor(dominantHint) }
    val plate = rememberZinePlate(thumbnailUrl = artworkUrl, inks = inks, seed = mediaMetadata.id)
    val offset = remember(mediaMetadata.id) { zineOffsetFor(mediaMetadata.id) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(inks.paper)
                .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(28.dp))

        // The plate. The second ink sits under the sheet, off-register, the way it comes off
        // a drum that is a fraction out of alignment - which is every drum.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = offset.first * 1.8f
                            translationY = offset.second * 1.8f
                        }.background(inks.second.copy(alpha = 0.30f)),
            )
            if (plate != null) {
                Image(
                    bitmap = plate,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize().background(inks.first.copy(alpha = 0.14f)))
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(inks.ink.copy(alpha = 0.85f)))
        Spacer(Modifier.height(14.dp))

        // Title, set like a headline rather than like a label.
        Text(
            text = mediaMetadata.title,
            color = inks.ink,
            fontSize = 30.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = mediaMetadata.artists.joinToString { it.name }.uppercase(),
            color = inks.second,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.weight(1f))

        ZineColourBar(
            position = position,
            duration = duration,
            ink = inks.ink,
            accent = inks.first,
            onSeek = { fraction -> if (duration > 0L) onSeekFinished((duration * fraction).toLong()) },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = makeTimeString(position),
                color = inks.ink.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = makeTimeString(duration),
                color = inks.ink.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ZineIconButton(
                iconRes = if (currentSongLiked) R.drawable.star_filled else R.drawable.star,
                tint = if (currentSongLiked) inks.first else inks.ink,
                onClick = onToggleLike,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ZineIconButton(R.drawable.skip_previous, inks.ink, onPreviousClick, size = 28.dp)
                ZineIconButton(
                    iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                    tint = if (isLoading) inks.ink.copy(alpha = 0.45f) else inks.ink,
                    onClick = onPlayPauseClick,
                    size = 36.dp,
                )
                ZineIconButton(R.drawable.skip_next, inks.ink, onNextClick, size = 28.dp)
            }
            ZineIconButton(R.drawable.more_horiz, inks.ink, onMenuClick)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The printer's colour bar: chunky ticks that fill as the track plays.
 *
 * This is the only place the style draws progress, and it is deliberately a calibration
 * strip rather than a slider track - a row of printed swatches is what a proof sheet has
 * along its edge.
 */
@Composable
private fun ZineColourBar(
    position: Long,
    duration: Long,
    ink: Color,
    accent: Color,
    onSeek: (Float) -> Unit,
) {
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    ComposeCanvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(duration) {
                    detectTapGestures { point ->
                        onSeek((point.x / size.width).coerceIn(0f, 1f))
                    }
                },
    ) {
        val ticks = 32
        val gap = size.width / ticks
        for (i in 0 until ticks) {
            val filled = (i + 1f) / ticks <= fraction
            val left = i * gap + gap * 0.14f
            val right = left + gap * 0.62f
            drawRect(
                color = if (filled) accent else ink.copy(alpha = 0.16f),
                topLeft = Offset(left, size.height * 0.18f),
                size = Size(right - left, size.height * 0.64f),
            )
        }
    }
}

@Composable
private fun ZineIconButton(
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
    size: Dp = 24.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
