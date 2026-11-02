/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.citali.lunartune.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import dev.citali.lunartune.constants.HeaderImageDimKey
import dev.citali.lunartune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Scrollable ROM-inspired image header with a readability scrim. */
@Composable
fun ScreenImageHeader(
    imageUri: String,
    modifier: Modifier = Modifier,
    blendColor: Color = Color.Black,
    veil: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // How hard the banner dims behind its own text. 1f is the stock look; the slider in
    // Appearance only ever scales what is drawn here, so the page join at the bottom stays
    // whole whatever it is set to.
    val (headerImageDim) = rememberPreference(HeaderImageDimKey, 1f)
    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        if (imageUri.substringBefore('?').endsWith(".gif", ignoreCase = true)) {
            PersistentGifImage(imageUri, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The scrim, drawn seamless. A three- or four-stop gradient is not a smooth ramp: it is
        // straight segments meeting at corners, and on a bright banner each corner reads as a line
        // across the artwork, with the plateau between them stepped by the alpha channel's own
        // resolution - one line, then another, the whole way down. So the ramps here are sampled
        // from smooth curves and then dithered, which is what the eye needs to read them as one
        // continuous shadow (see [BannerScrim]).
        BannerScrim(
            blendColor = blendColor,
            veil = veil,
            strength = headerImageDim,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}


/**
 * The banner's scrim: a shadow over the artwork that reads as one continuous dim rather than as
 * stacked bands.
 *
 * Two things make a gradient band. The first is corners - where one straight segment meets
 * another, the slope jumps and the eye reads the seam as a line - so every ramp here is sampled
 * from a smooth curve rather than written as a handful of stops. The second is the alpha channel's
 * resolution: eight bits means the ramp moves in 1/255 steps, and on a bright image a step of one
 * unit, held across the width of the banner, is a line you can see. So the ramps are drawn through
 * a dithered paint and under a noise field a fraction of one unit deep, which breaks each step
 * into speckle too fine to read as anything.
 *
 * The dim itself and the join into the page underneath are drawn as separate layers on purpose.
 * The join has to stay where it is whatever the dim slider says - scrolled to nothing, the banner
 * would otherwise end in a hard edge against the content below.
 */
@Composable
private fun BannerScrim(
    blendColor: Color,
    veil: Boolean,
    strength: Float,
    modifier: Modifier = Modifier,
) {
    val noise = remember { DITHER_NOISE }
    val dim = strength.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier.drawWithCache {
                val heightPx = size.height.coerceAtLeast(1f)
                val color = blendColor.toArgb()
                val clear = color and 0x00FFFFFF

                // The dim: nothing at the top of the artwork, deepening towards the page below.
                // The full-band veil keeps a floor under the whole banner so text anywhere on it
                // stays readable; the light variant leaves the artwork clear and only dims where
                // the content starts.
                val floor = if (veil) 0.18f else 0f
                val depth = if (veil) 0.48f else 0.64f
                val dimPaint =
                    rampPaint(
                        color = color,
                        clear = clear,
                        height = heightPx,
                        alphaAt = { t -> dim * (floor + depth * smoothStep(t, 0.34f)) },
                    )

                // The shadow over the header's own top - the title and whatever sits beside it -
                // shaped like the bottom's, so the banner is dimmed at both ends instead of only
                // where the content begins.
                val topPaint =
                    rampPaint(
                        color = color,
                        clear = clear,
                        height = heightPx,
                        alphaAt = { t -> dim * (if (veil) 0.14f else 0.28f) * (1f - smoothStep(t / 0.26f, 0f)) },
                    )

                // The join: fixed, unscaled. Full [blendColor] at the foot of the banner, gone by
                // a fifth of the way up, so the artwork meets the page with no edge of its own.
                val joinPaint =
                    rampPaint(
                        color = color,
                        clear = clear,
                        height = heightPx,
                        alphaAt = { t -> smoothStep(t, 0.80f) },
                    )

                val noisePaint =
                    Paint().apply {
                        shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                        isFilterBitmap = false
                    }

                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        val target = canvas.nativeCanvas
                        target.drawRect(0f, 0f, size.width, size.height, topPaint)
                        target.drawRect(0f, 0f, size.width, size.height, dimPaint)
                        target.drawRect(0f, 0f, size.width, size.height, joinPaint)
                        target.drawRect(0f, 0f, size.width, size.height, noisePaint)
                    }
                }
            },
    )
}

/** A gradient this shallow changes by less than one alpha unit from top to bottom. */
private const val SCRIM_RAMP_STEPS = 24

/**
 * Builds one layer of the scrim as a dithered vertical gradient, sampled from [alphaAt] so the
 * ramp has no corners in it.
 */
private fun rampPaint(
    color: Int,
    clear: Int,
    height: Float,
    alphaAt: (Float) -> Float,
): Paint {
    val positions = FloatArray(SCRIM_RAMP_STEPS + 1) { index -> index / SCRIM_RAMP_STEPS.toFloat() }
    val colors =
        IntArray(SCRIM_RAMP_STEPS + 1) { index ->
            val alpha = (alphaAt(positions[index]).coerceIn(0f, 1f) * 255f).roundToInt()
            if (alpha <= 0) clear else (alpha shl 24) or (color and 0x00FFFFFF)
        }
    return Paint().apply {
        isDither = true
        shader =
            LinearGradient(
                0f,
                0f,
                0f,
                height,
                colors,
                positions,
                Shader.TileMode.CLAMP,
            )
    }
}

/** Smoothstep: flat at both ends, so a ramp built from it has no corner where it begins or ends. */
private fun smoothStep(
    value: Float,
    start: Float,
): Float {
    val t = ((value - start) / (1f - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * A tile of noise a hair over one alpha unit deep. Drawn over the scrim it turns each step of the
 * gradient's alpha into speckle, which is what makes the dim read as seamless.
 */
private val DITHER_NOISE: Bitmap by lazy {
    val size = 32
    val random = Random(0x51CE7L)
    val pixels = IntArray(size * size)
    for (index in pixels.indices) {
        val alpha = random.nextInt(2)
        val tone = if (random.nextBoolean()) 0x00FFFFFF else 0x00000000
        pixels[index] = (alpha shl 24) or tone
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

private data class GifPlayback(
    val movie: Movie,
    val epochMs: Long,
)

private val gifPlaybackCache = ConcurrentHashMap<String, GifPlayback>()
private val gifDecodeLocks = ConcurrentHashMap<String, Any>()

private fun decodeGifMovie(context: android.content.Context, imageUri: String): Movie? =
    runCatching {
        val uri = Uri.parse(imageUri)
        when (uri.scheme?.lowercase()) {
            "file" -> Movie.decodeFile(File(requireNotNull(uri.path)).absolutePath)
            "http", "https" -> {
                val connection = URL(imageUri).openConnection().apply {
                    connectTimeout = 8_000
                    readTimeout = 15_000
                }
                connection.getInputStream().use(Movie::decodeStream)
            }
            null ->
                File(imageUri).takeIf { it.isFile }?.let { Movie.decodeFile(it.absolutePath) }
                    ?: context.contentResolver.openInputStream(uri)?.use(Movie::decodeStream)
            else -> context.contentResolver.openInputStream(uri)?.use(Movie::decodeStream)
        }
    }.getOrNull()

private fun getGifPlayback(context: android.content.Context, imageUri: String): GifPlayback? {
    gifPlaybackCache[imageUri]?.let { return it }
    val decodeLock = gifDecodeLocks.computeIfAbsent(imageUri) { Any() }
    return synchronized(decodeLock) {
        gifPlaybackCache[imageUri]
            ?: run {
                val movie = decodeGifMovie(context, imageUri) ?: return@synchronized null
                val playback = GifPlayback(movie, SystemClock.elapsedRealtime())
                gifPlaybackCache.putIfAbsent(imageUri, playback) ?: playback
            }
    }
}

/** Draws a cached GIF Movie against a persistent clock, without restarting on navigation. */
@Composable
private fun PersistentGifImage(
    imageUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playback by remember(imageUri) { mutableStateOf(gifPlaybackCache[imageUri]) }
    LaunchedEffect(context, imageUri) {
        if (playback == null) {
            playback = withContext(Dispatchers.IO) { getGifPlayback(context, imageUri) }
        }
    }
    var frameClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playback) {
        if (playback != null) {
            while (isActive) {
                withFrameNanos { frameClock = SystemClock.elapsedRealtime() }
            }
        }
    }
    Box(modifier) {
        // Coil provides a quick first display only on a cold cache miss.
        if (playback == null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val current = playback ?: return@Canvas
            val gif = current.movie
            val duration = gif.duration().takeIf { it > 0 } ?: 1_000
            val elapsed = (frameClock - current.epochMs).coerceAtLeast(0L)
            val movieWidth = gif.width().coerceAtLeast(1).toFloat()
            val movieHeight = gif.height().coerceAtLeast(1).toFloat()
            val scale = maxOf(size.width / movieWidth, size.height / movieHeight)
            val left = (size.width / scale - movieWidth) / 2f
            val top = (size.height / scale - movieHeight) / 2f
            val nativeCanvas = drawContext.canvas.nativeCanvas
            synchronized(gif) {
                gif.setTime((elapsed % duration).toInt())
                val saveCount = nativeCanvas.save()
                try {
                    nativeCanvas.scale(scale, scale)
                    gif.draw(nativeCanvas, left, top)
                } finally {
                    nativeCanvas.restoreToCount(saveCount)
                }
            }
        }
    }
}
