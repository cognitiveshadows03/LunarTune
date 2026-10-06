/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.citali.lunartune.ui.component

import android.graphics.Movie
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Scrollable ROM-inspired image header with a readability scrim. */
@Composable
fun ScreenImageHeader(
    imageUri: String,
    modifier: Modifier = Modifier,
    blendColor: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit = {},
) {
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
        // Multi-stop scrim stays light over dark images while becoming strong enough
        // for bright images near the title/content boundary.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .30f),
                    .45f to Color.Black.copy(alpha = .36f),
                    .72f to blendColor.copy(alpha = .48f),
                    1f to blendColor,
                ),
            ),
        )
        content()
    }
}


private data class GifRenderState(
    val movie: Movie,
    val epochMs: Long,
)

private val gifAnimationEpochs = ConcurrentHashMap<String, Long>()
private val gifByteCache =
    object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

private fun decodeGifMovie(context: android.content.Context, imageUri: String): Movie? {
    val bytes =
        gifByteCache.get(imageUri)
            ?: runCatching {
                val uri = Uri.parse(imageUri)
                val loaded =
                    when (uri.scheme?.lowercase()) {
                        "file" -> File(requireNotNull(uri.path)).readBytes()
                        "http", "https" -> {
                            val connection = URL(imageUri).openConnection().apply {
                                connectTimeout = 8_000
                                readTimeout = 15_000
                            }
                            connection.getInputStream().use { it.readBytes() }
                        }
                        null ->
                            File(imageUri).takeIf { it.isFile }?.readBytes()
                                ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                loaded?.also { gifByteCache.put(imageUri, it) }
            }.getOrNull()
            ?: return null

    return runCatching { Movie.decodeStream(ByteArrayInputStream(bytes)) }.getOrNull()
}

/** Draws a per-composition Movie against a persistent clock, avoiding cross-tab decoder races. */
@Composable
private fun PersistentGifImage(
    imageUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var animation by remember(imageUri) { mutableStateOf<GifRenderState?>(null) }
    LaunchedEffect(context, imageUri) {
        val decodedMovie = withContext(Dispatchers.IO) { decodeGifMovie(context, imageUri) }
        animation =
            decodedMovie?.let { movie ->
                val now = SystemClock.elapsedRealtime()
                val epoch = gifAnimationEpochs.putIfAbsent(imageUri, now) ?: now
                GifRenderState(movie = movie, epochMs = epoch)
            }
    }
    var frameClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(animation) {
        if (animation != null) {
            while (isActive) {
                withFrameNanos { frameClock = SystemClock.elapsedRealtime() }
            }
        }
    }
    Box(modifier) {
        // Coil's decoder supplies an early frame while the independent Movie decoder loads.
        if (animation == null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val current = animation ?: return@Canvas
            val gif = current.movie
            val duration = gif.duration().takeIf { it > 0 } ?: 1_000
            val elapsed = (frameClock - current.epochMs).coerceAtLeast(0L)
            gif.setTime((elapsed % duration).toInt())
            val movieWidth = gif.width().coerceAtLeast(1).toFloat()
            val movieHeight = gif.height().coerceAtLeast(1).toFloat()
            val scale = maxOf(size.width / movieWidth, size.height / movieHeight)
            val left = (size.width / scale - movieWidth) / 2f
            val top = (size.height / scale - movieHeight) / 2f
            drawContext.canvas.nativeCanvas.run {
                save()
                scale(scale, scale)
                gif.draw(this, left, top)
                restore()
            }
        }
    }
}
