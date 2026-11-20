/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.citali.lunartune.ui.recap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.recap.RecapChapter
import dev.citali.lunartune.recap.RecapEntry
import dev.citali.lunartune.recap.RecapEntryKind
import dev.citali.lunartune.ui.player.rememberOfflineArtworkImageRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Actions the highlight reel can request from its host. */
@Immutable
internal data class RecapCallbacks(
    val story: (Boolean) -> Unit,
    val chapter: (RecapChapter) -> Unit,
    val replay: () -> Unit,
)

@Composable
internal fun RecapAtmosphere(colors: List<Color>, animate: Boolean, modifier: Modifier = Modifier, base: Color? = null) {
    val phase = if (animate) {
        rememberInfiniteTransition("recap_atmosphere").animateFloat(0f, 1f,
            infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "recap_atmosphere_phase")
    } else null
    Canvas(modifier) {
        if (base != null) drawRect(base)
        val turn = (phase?.value ?: 0.12f) * 2f * PI.toFloat()
        colors.forEachIndexed { index, color ->
            val angle = turn + index * 1.9f
            val center = Offset(size.width * (0.5f + 0.4f * cos(angle)), size.height * (0.45f + 0.32f * sin(angle * 0.7f + index)))
            val radius = size.maxDimension * (0.55f + 0.07f * index)
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}

@Composable
internal fun RecapArtwork(entry: RecapEntry, modifier: Modifier = Modifier, radius: Int = 12, shape: Shape? = null) {
    val request = rememberOfflineArtworkImageRequest(entry.artwork)
    val resolved = shape ?: remember(entry.kind, radius) {
        if (entry.kind == RecapEntryKind.ARTIST) CircleShape else RoundedCornerShape(radius.dp)
    }
    Box(modifier.clip(resolved).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.small_icon), null, Modifier.fillMaxSize(0.38f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(request, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun RecapIcon(icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(22.dp))
    }
}
