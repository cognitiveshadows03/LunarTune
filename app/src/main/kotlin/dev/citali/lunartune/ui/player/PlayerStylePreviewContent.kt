/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.PlayerDesignStyle

/**
 * Everything a style preview needs to draw itself. Deliberately a plain frame of
 * data rather than live playback state: the carousel shows a frozen last-played
 * song, so a preview never subscribes to the player, opens a queue, or fetches
 * lyrics.
 */
@Immutable
data class PlayerStylePreviewData(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val elapsedLabel: String,
    val totalLabel: String,
    val progress: Float = 0.35f,
    val qualityLabel: String? = null,
    val lyricLine: String? = null,
)

/**
 * Draws one player style at card size.
 *
 * Sizes are derived from the card width, so the same code renders both the small
 * carousel card and the full-screen card a preview grows into before the player
 * takes over. The layout of each branch mirrors the matching style composable:
 * when a style's player changes, its preview is meant to change with it.
 */
@Composable
fun PlayerStylePreviewContent(
    style: PlayerDesignStyle,
    data: PlayerStylePreviewData,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val unit = maxWidth
        val corner = unit * 0.1f
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner)),
        ) {
            when (style) {
                PlayerDesignStyle.V2 -> ModernPreview(unit, data)
                PlayerDesignStyle.V4 -> CinematicPreview(unit, data)
                PlayerDesignStyle.V5 -> LittlePreview(unit, data)
                PlayerDesignStyle.V6 -> ExpressivePreview(unit, data)
                PlayerDesignStyle.V7 -> ImmersivePreview(unit, data, extended = false)
                PlayerDesignStyle.V8 -> ImmersivePreview(unit, data, extended = true)
                PlayerDesignStyle.V9 -> MaterialPreview(unit, data)
                PlayerDesignStyle.V10 -> FullArtPreview(unit, data)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* style bodies                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun ModernPreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    Box(Modifier.fillMaxSize()) {
        PreviewBackdrop(data.artworkUrl, unit, blurRadius = unit * 0.07f, alpha = 0.55f)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.06f, vertical = unit * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewActions(unit, tint = Color.White, alignment = Alignment.CenterHorizontally)
            Spacer(Modifier.height(unit * 0.05f))
            PreviewArt(data.artworkUrl, size = unit * 0.72f, shape = RoundedCornerShape(unit * 0.045f))
            Spacer(Modifier.height(unit * 0.06f))
            PreviewTitleBlock(data, unit, titleFraction = 0.058f, subFraction = 0.042f)
            Spacer(Modifier.height(unit * 0.05f))
            PreviewProgressBar(data.progress, height = unit * 0.011f)
            Spacer(Modifier.height(unit * 0.018f))
            PreviewTimes(data, fractionSp(unit, 0.033f))
            Spacer(Modifier.height(unit * 0.03f))
            PreviewTransport(unit * 0.095f, tint = Color.White)
        }
    }
}

@Composable
private fun CinematicPreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    Box(Modifier.fillMaxSize()) {
        PreviewBackdrop(data.artworkUrl, unit, blurRadius = unit * 0.07f, alpha = 0.5f)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.06f, vertical = unit * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewArt(data.artworkUrl, size = unit * 0.84f, shape = RoundedCornerShape(unit * 0.03f))
            Spacer(Modifier.weight(1f))
            PreviewTitleBlock(
                data,
                unit,
                titleFraction = 0.066f,
                subFraction = 0.044f,
                align = TextAlign.Start,
            )
            Spacer(Modifier.height(unit * 0.04f))
            PreviewProgressBar(data.progress, height = unit * 0.009f)
            Spacer(Modifier.height(unit * 0.016f))
            PreviewTimes(data, fractionSp(unit, 0.033f), startAligned = true)
            Spacer(Modifier.height(unit * 0.03f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewIcon(R.drawable.skip_previous, unit * 0.082f)
                PreviewIcon(R.drawable.play, unit * 0.095f)
                PreviewIcon(R.drawable.skip_next, unit * 0.082f)
            }
        }
    }
}

@Composable
private fun LittlePreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    Box(Modifier.fillMaxSize()) {
        PreviewBackdrop(data.artworkUrl, unit, blurRadius = unit * 0.1f, alpha = 0.75f)
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = unit * 0.05f)
                    .clip(RoundedCornerShape(unit * 0.06f))
                    .background(Color.White.copy(alpha = 0.13f))
                    .padding(unit * 0.045f),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewArt(data.artworkUrl, size = unit * 0.21f, shape = RoundedCornerShape(unit * 0.03f))
                    Spacer(Modifier.width(unit * 0.034f))
                    PreviewTitleBlock(
                        data,
                        unit,
                        titleFraction = 0.05f,
                        subFraction = 0.038f,
                        align = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(unit * 0.04f))
                PreviewProgressBar(data.progress, height = unit * 0.009f)
                Spacer(Modifier.height(unit * 0.028f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewIcon(R.drawable.skip_previous, unit * 0.072f)
                    PreviewIcon(R.drawable.play, unit * 0.082f)
                    PreviewIcon(R.drawable.skip_next, unit * 0.072f)
                }
            }
        }
    }
}

@Composable
private fun ExpressivePreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        PreviewBackdrop(data.artworkUrl, unit, blurRadius = unit * 0.07f, alpha = 0.55f)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.06f, vertical = unit * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewActions(unit, tint = Color.White, alignment = Alignment.CenterHorizontally)
            Spacer(Modifier.height(unit * 0.05f))
            PreviewArt(data.artworkUrl, size = unit * 0.62f, shape = CircleShape)
            Spacer(Modifier.height(unit * 0.055f))
            PreviewTitleBlock(data, unit, titleFraction = 0.062f, subFraction = 0.042f)
            Spacer(Modifier.height(unit * 0.04f))
            PreviewProgressBar(data.progress, height = unit * 0.011f, fillColor = accent)
            Spacer(Modifier.height(unit * 0.018f))
            PreviewTimes(data, fractionSp(unit, 0.033f))
            Spacer(Modifier.height(unit * 0.04f))
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = unit * 0.06f, vertical = unit * 0.025f),
                horizontalArrangement = Arrangement.spacedBy(unit * 0.055f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewIcon(R.drawable.skip_previous, unit * 0.07f, tint = MaterialTheme.colorScheme.onPrimary)
                PreviewIcon(R.drawable.play, unit * 0.08f, tint = MaterialTheme.colorScheme.onPrimary)
                PreviewIcon(R.drawable.skip_next, unit * 0.07f, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun ImmersivePreview(
    unit: Dp,
    data: PlayerStylePreviewData,
    extended: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        PreviewArt(
            data.artworkUrl,
            size = null,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.26f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.14f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.055f, vertical = unit * 0.055f),
        ) {
            PreviewHeaderRow(unit * 0.055f)
            if (extended) {
                Spacer(Modifier.height(unit * 0.03f))
                PreviewLyricLine(data.lyricLine, unit * 0.045f)
            }
            Spacer(Modifier.weight(1f))
            PreviewTitleBlock(data, unit, titleFraction = 0.066f, subFraction = 0.044f, align = TextAlign.Start)
            if (extended && !data.qualityLabel.isNullOrBlank()) {
                Spacer(Modifier.height(unit * 0.02f))
                PreviewQualityChip(data.qualityLabel, unit * 0.031f)
            }
            Spacer(Modifier.height(unit * 0.035f))
            PreviewProgressBar(data.progress, height = unit * 0.01f)
            Spacer(Modifier.height(unit * 0.016f))
            PreviewTimes(data, fractionSp(unit, 0.032f), startAligned = true)
            Spacer(Modifier.height(unit * 0.028f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewIcon(R.drawable.shuffle, unit * 0.068f, alpha = 0.7f)
                PreviewIcon(R.drawable.skip_previous, unit * 0.078f)
                PreviewIcon(R.drawable.play, unit * 0.09f)
                PreviewIcon(R.drawable.skip_next, unit * 0.078f)
                PreviewIcon(R.drawable.repeat, unit * 0.068f, alpha = 0.7f)
            }
            Spacer(Modifier.height(unit * 0.03f))
            PreviewVolumeBar(unit)
        }
    }
}

@Composable
private fun MaterialPreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.055f, vertical = unit * 0.055f),
        ) {
            PreviewHeaderRow(unit * 0.055f, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(unit * 0.03f))
            PreviewArt(
                data.artworkUrl,
                size = unit * 0.7f,
                shape = RoundedCornerShape(unit * 0.05f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(unit * 0.03f))
            data.qualityLabel?.let { label ->
                Spacer(Modifier.height(unit * 0.026f))
                Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    PreviewQualityChip(label, fractionSp(unit, 0.031f))
                }
            }
            Spacer(Modifier.weight(1f))
            PreviewTitleBlock(
                data,
                unit,
                titleFraction = 0.06f,
                subFraction = 0.042f,
                align = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(unit * 0.03f))
            PreviewProgressBar(data.progress, height = unit * 0.01f, fillColor = accent)
            Spacer(Modifier.height(unit * 0.016f))
            PreviewTimes(data, fractionSp(unit, 0.032f), startAligned = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(unit * 0.026f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewIcon(
                    R.drawable.shuffle,
                    unit * 0.065f,
                    tint = MaterialTheme.colorScheme.onSurface,
                    alpha = 0.7f,
                )
                PreviewIcon(R.drawable.skip_previous, unit * 0.075f, tint = MaterialTheme.colorScheme.onSurface)
                Box(
                    modifier =
                        Modifier
                            .size(unit * 0.13f)
                            .clip(CircleShape)
                            .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(unit * 0.066f),
                    )
                }
                PreviewIcon(R.drawable.skip_next, unit * 0.075f, tint = MaterialTheme.colorScheme.onSurface)
                PreviewIcon(
                    R.drawable.repeat,
                    unit * 0.065f,
                    tint = MaterialTheme.colorScheme.onSurface,
                    alpha = 0.7f,
                )
            }
        }
    }
}

@Composable
private fun FullArtPreview(
    unit: Dp,
    data: PlayerStylePreviewData,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PreviewBackdrop(data.artworkUrl, unit, blurRadius = unit * 0.11f, alpha = 0.3f)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = unit * 0.055f, vertical = unit * 0.055f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewArt(
                data.artworkUrl,
                size = unit * 0.78f,
                shape = RoundedCornerShape(unit * 0.03f),
                modifier = Modifier.padding(top = unit * 0.05f),
            )
            Spacer(Modifier.height(unit * 0.045f))
            PreviewTitleBlock(
                data,
                unit,
                titleFraction = 0.056f,
                subFraction = 0.042f,
                align = TextAlign.Start,
                weight = FontWeight.SemiBold,
            )
            if (!data.qualityLabel.isNullOrBlank()) {
                Spacer(Modifier.height(unit * 0.02f))
                PreviewQualityChip(data.qualityLabel, unit * 0.03f)
            }
            Spacer(Modifier.height(unit * 0.045f))
            PreviewProgressBar(data.progress, height = unit * 0.008f)
            Spacer(Modifier.height(unit * 0.016f))
            PreviewTimes(data, fractionSp(unit, 0.032f), startAligned = true)
            Spacer(Modifier.height(unit * 0.028f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewIcon(R.drawable.shuffle, unit * 0.062f, alpha = 0.75f)
                PreviewIcon(R.drawable.skip_previous, unit * 0.075f)
                PreviewIcon(R.drawable.play, unit * 0.088f)
                PreviewIcon(R.drawable.skip_next, unit * 0.075f)
                PreviewIcon(R.drawable.repeat, unit * 0.062f, alpha = 0.75f)
            }
            Spacer(Modifier.weight(1f))
            data.lyricLine?.let { line ->
                Text(
                    text = line,
                    fontSize = fractionSp(unit, 0.038f),
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* shared pieces                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun PreviewBackdrop(
    artworkUrl: String?,
    unit: Dp,
    blurRadius: Dp,
    alpha: Float,
) {
    val blurModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier
    AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxSize()
                .then(blurModifier)
                .graphicsLayer { this.alpha = alpha },
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.66f),
                    ),
                ),
    )
}

@Composable
private fun PreviewArt(
    artworkUrl: String?,
    size: Dp?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            (if (size != null) modifier.size(size) else modifier)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PreviewTitleBlock(
    data: PlayerStylePreviewData,
    unit: Dp,
    titleFraction: Float,
    subFraction: Float,
    align: TextAlign = TextAlign.Center,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally) {
        Text(
            text = data.title,
            fontSize = fractionSp(unit, titleFraction),
            fontWeight = weight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(unit * 0.008f))
        Text(
            text = data.artist,
            fontSize = fractionSp(unit, subFraction),
            color = color.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewProgressBar(
    progress: Float,
    height: Dp,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    fillColor: Color = Color.White,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(trackColor),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fillColor),
        )
    }
}

@Composable
private fun PreviewTimes(
    data: PlayerStylePreviewData,
    size: TextUnit,
    startAligned: Boolean = false,
    color: Color = Color.White.copy(alpha = 0.5f),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (startAligned) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        Text(text = data.elapsedLabel, fontSize = size, color = color, maxLines = 1)
        if (startAligned) {
            Text(text = data.totalLabel, fontSize = size, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun PreviewIcon(
    drawableRes: Int,
    size: Dp,
    tint: Color = Color.White,
    alpha: Float = 1f,
) {
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = null,
        tint = tint.copy(alpha = tint.alpha * alpha),
        modifier = Modifier.size(size),
    )
}

@Composable
private fun PreviewTransport(
    size: Dp,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(size * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewIcon(R.drawable.skip_previous, size, tint = tint)
        PreviewIcon(R.drawable.play, size * 1.15f, tint = tint)
        PreviewIcon(R.drawable.skip_next, size, tint = tint)
    }
}

@Composable
private fun PreviewHeaderRow(
    size: Dp,
    tint: Color = Color.White,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewIcon(R.drawable.expand_more, size, tint = tint, alpha = 0.85f)
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.9f)) {
            PreviewIcon(R.drawable.queue_music, size, tint = tint, alpha = 0.85f)
            PreviewIcon(R.drawable.lyrics, size, tint = tint, alpha = 0.85f)
        }
    }
}

@Composable
private fun PreviewActions(
    unit: Dp,
    tint: Color,
    alignment: Alignment.Horizontal,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (alignment == Alignment.CenterHorizontally) {
                Arrangement.Center
            } else {
                Arrangement.End
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(R.drawable.share, R.drawable.favorite, R.drawable.more_vert).forEach { res ->
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = unit * 0.012f)
                        .size(unit * 0.095f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                PreviewIcon(res, unit * 0.045f, tint = tint, alpha = 0.9f)
            }
        }
    }
}

@Composable
private fun PreviewQualityChip(
    label: String,
    size: TextUnit,
) {
    Text(
        text = label,
        fontSize = size,
        color = Color.White.copy(alpha = 0.82f),
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(size.value * 0.4f))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = size.value.dp * 0.7f, vertical = size.value.dp * 0.25f),
    )
}

@Composable
private fun PreviewVolumeBar(unit: Dp) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(unit * 0.008f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun PreviewLyricLine(
    line: String?,
    size: TextUnit,
) {
    if (line.isNullOrBlank()) return
    Text(
        text = line,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.9f),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun fractionSp(
    unit: Dp,
    fraction: Float,
) = (unit.value * fraction).sp
