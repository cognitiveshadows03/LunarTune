/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * A record player for the player surface, plus the poster and timeline surfaces
 * beside it. The ideas and their proportions are ported from Mochy-Music
 * (GPL-3.0, github.com/realidkroo/Mochy-Music), which draws its turntable, its
 * poster cover and its timeline rail as shapes rather than pictures. Everything
 * here is written against LunarTune's own player state, so the same code runs in
 * the player and in the design-style selector.
 */

package dev.citali.lunartune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.models.MediaMetadata
import kotlin.math.cos
import kotlin.math.sin

/** The accent the ported surfaces share: warm, and readable on a dark plinth. */
private val PortAccent = Color(0xFFF0A6B8)
private val PortSurface = Color(0xFF111113)

/** m:ss, or h:mm:ss for anything past an hour. */
private fun label(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/* ------------------------------------------------------------------ */
/* the full player                                                     */
/* ------------------------------------------------------------------ */

/**
 * The record player as a full player surface.
 *
 * Deliberately takes only LunarTune types: the caller owns the connection, the
 * sheet, the queue and the lyrics page, so this stays a view and nothing else.
 */
@Composable
fun TurntablePlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isShuffleOn: Boolean,
    isRepeatOn: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = sliderPosition ?: position
    val progress =
        if (duration > 0L) (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val artistLine = mediaMetadata.artists.joinToString(", ") { it.name }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(PortSurface),
        contentAlignment = Alignment.Center,
    ) {
        // the disc never outgrows the shorter side, so landscape still fits
        val disc = minOf(maxWidth, maxHeight) * 0.72f
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PortIconButton(R.drawable.expand_more, onCollapseClick, 40.dp, rotateDegrees = 180f)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.player_style_now_playing),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = mediaMetadata.album?.title.orEmpty(),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                PortIconButton(R.drawable.more_vert, onMenuClick, 40.dp)
            }

            Spacer(Modifier.height(20.dp))
            Platter(
                artworkUrl = mediaMetadata.thumbnailUrl,
                isPlaying = isPlaying,
                disc = disc,
            )
            Spacer(Modifier.height(22.dp))

            Text(
                text = mediaMetadata.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artistLine,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            PlinthSeekBar(
                progress = progress,
                enabled = duration > 0L,
                elapsed = label(shown),
                total = label(duration),
                onSeek = { fraction -> onSliderValueChange((fraction * duration).toLong()) },
                onSeekFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PortIconButton(
                    icon = R.drawable.shuffle,
                    onClick = onShuffleClick,
                    size = 40.dp,
                    tint = if (isShuffleOn) PortAccent else Color.White.copy(alpha = 0.55f),
                )
                PortIconButton(
                    icon = R.drawable.skip_previous,
                    onClick = onSkipPrevious,
                    size = 44.dp,
                    enabled = canSkipPrevious,
                )
                PortIconButton(
                    icon = if (isPlaying) R.drawable.pause else R.drawable.play,
                    onClick = onPlayPause,
                    size = 66.dp,
                    filled = true,
                )
                PortIconButton(
                    icon = R.drawable.skip_next,
                    onClick = onSkipNext,
                    size = 44.dp,
                    enabled = canSkipNext,
                )
                PortIconButton(
                    icon = R.drawable.repeat,
                    onClick = onRepeatClick,
                    size = 40.dp,
                    tint = if (isRepeatOn) PortAccent else Color.White.copy(alpha = 0.55f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PortIconButton(
                    icon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    onClick = onToggleLike,
                    size = 40.dp,
                    tint = if (isLiked) PortAccent else Color.White.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(8.dp))
                PortPill("Lyrics", onLyricsClick)
                Spacer(Modifier.width(8.dp))
                PortPill("Queue", onQueueClick)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * The platter: plinth shadow, the record, the label, and the arm. Split out so the
 * selector card can reuse it at a fraction of the size.
 */
@Composable
private fun Platter(
    artworkUrl: String?,
    isPlaying: Boolean,
    disc: Dp,
) {
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 26f else 6f,
        animationSpec = tween(durationMillis = 900),
        label = "tonearm",
    )
    val labelSpin by animateFloatAsState(
        targetValue = if (isPlaying) 360f else 0f,
        animationSpec = tween(durationMillis = 24_000),
        label = "labelSpin",
    )

    Box(modifier = Modifier.size(disc), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawPlinthShadow() },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(disc * 0.03f)
                    .drawBehind {
                        drawVinyl(
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension / 2f,
                        )
                    },
        )
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(disc * 0.42f)
                    .clip(CircleShape)
                    .rotate(labelSpin),
        )
        Box(
            modifier =
                Modifier
                    .size(disc * 0.032f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.88f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawTonearm(restingAngle = armAngle) },
        )
    }
}

/** The seek bar, sitting in the plinth like a groove marker. */
@Composable
private fun PlinthSeekBar(
    progress: Float,
    enabled: Boolean,
    elapsed: String,
    total: String,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            enabled = enabled,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.92f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(elapsed, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(total, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PortIconButton(
    icon: Int,
    onClick: () -> Unit,
    size: Dp,
    tint: Color = Color.White.copy(alpha = 0.9f),
    enabled: Boolean = true,
    filled: Boolean = false,
    rotateDegrees: Float = 0f,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (filled) PortAccent else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .rotate(rotateDegrees),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint =
                if (filled) {
                    PortSurface
                } else {
                    if (enabled) tint else tint.copy(alpha = 0.28f)
                },
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

@Composable
private fun PortPill(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.09f))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/* ------------------------------------------------------------------ */
/* drawing                                                             */
/* ------------------------------------------------------------------ */

private fun DrawScope.drawPlinthShadow() {
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.5f),
                1f to Color.Transparent,
                center = Offset(size.width / 2f, size.height * 0.58f),
                radius = size.minDimension * 0.56f,
            ),
        radius = size.minDimension * 0.56f,
        center = Offset(size.width / 2f, size.height * 0.58f),
    )
}

/**
 * The record: a dark disc, then the bands the music leaves in it, then a sheen
 * that sits square across the grooves. The light never moves, so only the label
 * turns.
 */
private fun DrawScope.drawVinyl(
    center: Offset,
    radius: Float,
) {
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to Color(0xFF141416),
                0.72f to Color(0xFF0C0C0E),
                1f to Color(0xFF060607),
                center = center,
                radius = radius,
            ),
        radius = radius,
        center = center,
    )

    // the music's bands: uneven rings, so the surface never looks machined
    var band = 0.30f
    while (band < 0.98f) {
        val wobble = 0.006f + 0.004f * sin(band * 37f)
        drawCircle(
            color = Color.Black.copy(alpha = 0.34f),
            radius = radius * band,
            center = center,
            style = Stroke(width = radius * wobble),
        )
        band += 0.014f + 0.006f * cos(band * 19f)
    }

    // light from the top-left, square across the grooves: two white lobes
    drawCircle(
        brush =
            Brush.sweepGradient(
                0f to Color.Transparent,
                0.06f to Color.White.copy(alpha = 0.15f),
                0.14f to Color.White.copy(alpha = 0.03f),
                0.5f to Color.Transparent,
                0.56f to Color.White.copy(alpha = 0.15f),
                0.64f to Color.White.copy(alpha = 0.03f),
                1f to Color.Transparent,
                center = center,
            ),
        radius = radius * 0.97f,
        center = center,
    )

    // the rim
    drawCircle(
        color = Color.White.copy(alpha = 0.06f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.012f),
    )
}

/** The arm: a pivot at the top-right, a shaft across the record, a headshell at its end. */
private fun DrawScope.drawTonearm(restingAngle: Float) {
    val pivot = Offset(size.width * 0.9f, size.height * 0.1f)
    val length = size.minDimension * 0.78f
    val angleDeg = 118f + restingAngle
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val end =
        Offset(
            pivot.x + (cos(angleRad) * length).toFloat(),
            pivot.y + (sin(angleRad) * length).toFloat(),
        )

    // the arm's shadow, falling down-right
    drawLine(
        color = Color.Black.copy(alpha = 0.35f),
        start = pivot + Offset(6f, 10f),
        end = end + Offset(6f, 10f),
        strokeWidth = size.minDimension * 0.026f,
        cap = StrokeCap.Round,
    )
    // the shaft
    drawLine(
        brush =
            Brush.linearGradient(
                0f to Color(0xFFE9E9EE),
                0.5f to Color(0xFFBFBFC6),
                1f to Color(0xFF8E8E96),
                start = pivot,
                end = end,
            ),
        start = pivot,
        end = end,
        strokeWidth = size.minDimension * 0.022f,
        cap = StrokeCap.Round,
    )
    // the pivot housing
    drawCircle(color = Color(0xFF2A2A2E), radius = size.minDimension * 0.075f, center = pivot)
    drawCircle(
        color = Color.White.copy(alpha = 0.10f),
        radius = size.minDimension * 0.075f,
        center = pivot,
        style = Stroke(width = size.minDimension * 0.006f),
    )
    // the headshell, angled with the arm, in the accent so it reads as the needle
    rotate(degrees = angleDeg, pivot = end) {
        drawRoundRect(
            color = Color(0xFF1D1D20),
            topLeft = Offset(end.x - size.minDimension * 0.055f, end.y - size.minDimension * 0.028f),
            size = Size(size.minDimension * 0.11f, size.minDimension * 0.056f),
            cornerRadius = CornerRadius(size.minDimension * 0.014f),
        )
        drawCircle(color = PortAccent, radius = size.minDimension * 0.012f, center = end)
    }
}
