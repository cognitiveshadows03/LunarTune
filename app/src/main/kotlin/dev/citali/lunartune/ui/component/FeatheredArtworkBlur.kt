/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dev.citali.lunartune.utils.ImageBlurUtils
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val BlurSampleWidthPx = 192
private const val BlurSampleRadiusPx = 15f
private const val MaxCachedCardBlurs = 24

private object FeatheredCardBlurCache {
    private val cache = LruCache<String, ImageBitmap>(MaxCachedCardBlurs)
    private val semaphore = Semaphore(2)

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(
        key: String,
        bitmap: ImageBitmap,
    ) {
        synchronized(cache) {
            cache.put(key, bitmap)
        }
    }

    suspend fun loadOrCreate(
        context: android.content.Context,
        key: String,
        url: String,
        aspectRatio: Float,
        fadeStartFraction: Float,
    ): ImageBitmap? {
        get(key)?.let { return it }

        val appContext = context.applicationContext
        val fullWidth = BlurSampleWidthPx
        val fullHeight = (BlurSampleWidthPx * aspectRatio).roundToInt().coerceIn(64, 384)

        val decoded =
            withContext(Dispatchers.IO) {
                val request =
                    ImageRequest
                        .Builder(appContext)
                        .data(url)
                        .size(Size(fullWidth, fullHeight))
                        .allowHardware(false)
                        .build()
                runCatching { appContext.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            } ?: return null

        return semaphore.withPermit {
            get(key)?.let { return@withPermit it }
            withContext(Dispatchers.Default) {
                val sw = decoded.width.coerceAtLeast(1)
                val sh = decoded.height.coerceAtLeast(1)
                val scale = max(fullWidth.toFloat() / sw, fullHeight.toFloat() / sh)
                val drawW = sw * scale
                val drawH = sh * scale
                val dx = (fullWidth - drawW) * 0.5f
                val dy = (fullHeight - drawH) * 0.5f

                val cropped = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)
                Canvas(cropped).drawBitmap(
                    decoded,
                    null,
                    RectF(dx, dy, dx + drawW, dy + drawH),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )

                val blurred = ImageBlurUtils.blur(cropped, BlurSampleRadiusPx)
                if (blurred !== cropped) {
                    cropped.recycle()
                }

                val s0 = fadeStartFraction.coerceIn(0f, 0.88f)
                val stripHeight = (fullHeight * (1f - s0)).roundToInt().coerceIn(1, fullHeight)
                val stripTop = fullHeight - stripHeight

                val strip = Bitmap.createBitmap(fullWidth, stripHeight, Bitmap.Config.ARGB_8888)
                val stripCanvas = Canvas(strip)
                stripCanvas.drawBitmap(blurred, 0f, -stripTop.toFloat(), null)
                blurred.recycle()

                val maskPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        shader =
                            LinearGradient(
                                0f,
                                0f,
                                0f,
                                stripHeight.toFloat(),
                                intArrayOf(
                                    0x00000000,
                                    0x40000000,
                                    0xC2000000.toInt(),
                                    0xF5000000.toInt(),
                                    0xFF000000.toInt(),
                                ),
                                floatArrayOf(0f, 0.20f, 0.38f, 0.58f, 1f),
                                Shader.TileMode.CLAMP,
                            )
                    }
                stripCanvas.drawRect(0f, 0f, fullWidth.toFloat(), stripHeight.toFloat(), maskPaint)

                strip.asImageBitmap().also { put(key, it) }
            }
        }
    }
}

/**
 * Progressive bottom artwork blur inspired by Telegram's profile header, pre-composited once per
 * artwork URL off the main thread into a compact cached strip so scrolling pays zero GPU offscreen
 * layer or `RenderEffect` cost per frame.
 */
@Composable
internal fun BoxScope.FeatheredBottomArtworkBlur(
    artworkUrl: String?,
    aspectRatio: Float,
    fadeStartFraction: Float = 0.54f,
    scrimMaxAlpha: Float = 0.24f,
) {
    val context = LocalContext.current
    val s0 = fadeStartFraction.coerceIn(0f, 0.88f)
    val span = 1f - s0
    val cacheKey =
        remember(artworkUrl, aspectRatio, s0) {
            artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                "$url|${(aspectRatio * 100f).roundToInt()}|${(s0 * 100f).roundToInt()}"
            }
        }

    var featheredStrip by
        remember(cacheKey) {
            mutableStateOf(cacheKey?.let(FeatheredCardBlurCache::get))
        }

    LaunchedEffect(cacheKey, artworkUrl) {
        if (featheredStrip == null && cacheKey != null && !artworkUrl.isNullOrBlank()) {
            featheredStrip =
                FeatheredCardBlurCache.loadOrCreate(
                    context = context,
                    key = cacheKey,
                    url = artworkUrl,
                    aspectRatio = aspectRatio,
                    fadeStartFraction = s0,
                )
        }
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

    val strip = featheredStrip
    if (strip != null) {
        Image(
            bitmap = strip,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(span),
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (strip != null) scrimBrush else fallbackScrimBrush),
    )
}
