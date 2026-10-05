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
import androidx.compose.runtime.produceState
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


private val gifAnimationEpochs = ConcurrentHashMap<String, Long>()
private val gifMovieCache = ConcurrentHashMap<String, Movie>()

/** Draws GIF frames against a process-wide clock so navigation does not restart animation. */
@Composable
private fun PersistentGifImage(
    imageUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val movie by produceState<Movie?>(gifMovieCache[imageUri], imageUri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(imageUri)
                    val decoded =
                        when (uri.scheme) {
                            "file" -> Movie.decodeFile(File(requireNotNull(uri.path)).absolutePath)
                            else -> context.contentResolver.openInputStream(uri)?.use(Movie::decodeStream)
                        }
                    decoded?.also { gifMovieCache[imageUri] = it }
                }.getOrNull()
            }
        }
    }
    val epoch = remember(imageUri) {
        gifAnimationEpochs.getOrPut(imageUri) { SystemClock.elapsedRealtime() }
    }
    var frameClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(movie, epoch) {
        while (isActive) {
            withFrameNanos { frameClock = SystemClock.elapsedRealtime() }
        }
    }
    Canvas(modifier) {
        val gif = movie ?: return@Canvas
        val duration = gif.duration().takeIf { it > 0 } ?: 1_000
        gif.setTime(((frameClock - epoch) % duration).toInt())
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
