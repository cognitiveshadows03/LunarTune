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
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Scrollable ROM-inspired image header with a readability scrim. */
@Composable
fun ScreenImageHeader(
    imageUri: String,
    modifier: Modifier = Modifier,
    blendColor: Color = Color.Black,
    veil: Boolean = true,
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
                    if (veil) {
                        listOf(
                            0f to Color.Black.copy(alpha = .30f),
                            .45f to Color.Black.copy(alpha = .36f),
                            .72f to blendColor.copy(alpha = .48f),
                            1f to blendColor,
                        )
                    } else {
                        // No full-band veil: the header dims only where its text
                        // sits (see headerTextDim), so the artwork stays clear.
                        listOf(
                            0f to Color.Transparent,
                            .55f to Color.Transparent,
                            .78f to blendColor.copy(alpha = .48f),
                            1f to blendColor,
                        )
                    },
                ),
            ),
        )
        content()
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
