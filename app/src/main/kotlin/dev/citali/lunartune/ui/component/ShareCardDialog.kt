/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.player.BlurredArtwork
import dev.citali.lunartune.ui.utils.highRes
import dev.citali.lunartune.utils.ComposeToImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** What the card shows: the song, or the lyric that is playing. */
private enum class ShareCardMode {
    SONG,
    LYRICS,
}

/** Light or dark card surface. */
private enum class ShareCardTone {
    LIGHT,
    DARK,
}

/** The tint of the card: a small fixed set instead of a colour picker. */
private val ShareCardThemes =
    listOf(
        Color(0xFFC9B6FF),
        Color(0xFFFFC2CE),
        Color(0xFFB9E4D0),
        Color(0xFFE7D3B4),
        Color(0xFF9EC5FF),
    )

/**
 * Share a card of what is playing, or of the lyric that is playing.
 *
 * Two things to choose: which card, and whether it is light or dark - plus one of a
 * few tints. The card is drawn in the Immersive (V7) player style; that is the only
 * look this feature has while it is experimental, so no style selector is offered.
 */
@Composable
fun ShareCardDialog(
    mediaMetadata: MediaMetadata,
    artworkUrl: String?,
    lyrics: String?,
    currentLyricLine: String?,
    position: Long,
    duration: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    var mode by rememberSaveable { mutableStateOf(ShareCardMode.SONG) }
    var tone by rememberSaveable { mutableStateOf(ShareCardTone.DARK) }
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    val theme = ShareCardThemes[themeIndex.coerceIn(ShareCardThemes.indices)]
    val lyricLines = remember(lyrics, currentLyricLine) { shareCardLyricLines(lyrics, currentLyricLine) }
    val hasLyrics = lyricLines.isNotEmpty()

    // The card is composed once and recorded through its own graphics layer, so what
    // is exported is exactly what the preview shows rather than a second rendering.
    val cardLayer = rememberGraphicsLayer()
    val shareScale = 3f

    suspend fun cardBitmap(): Bitmap? = runCatching { cardLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.share_card),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ShareCardMode.SONG,
                        onClick = { mode = ShareCardMode.SONG },
                        label = { Text(stringResource(R.string.song_card)) },
                    )
                    FilterChip(
                        selected = mode == ShareCardMode.LYRICS && hasLyrics,
                        onClick = { if (hasLyrics) mode = ShareCardMode.LYRICS },
                        enabled = hasLyrics,
                        label = { Text(stringResource(R.string.lyrics_card)) },
                    )
                    FilterChip(
                        selected = tone == ShareCardTone.LIGHT,
                        onClick = { tone = ShareCardTone.LIGHT },
                        label = { Text(stringResource(R.string.card_light)) },
                    )
                    FilterChip(
                        selected = tone == ShareCardTone.DARK,
                        onClick = { tone = ShareCardTone.DARK },
                        label = { Text(stringResource(R.string.card_dark)) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .height(360.dp)
                            .aspectRatio(ShareCardAspect)
                            .clip(RoundedCornerShape(20.dp))
                            .drawWithContent {
                                cardLayer.record(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    size =
                                        IntSize(
                                            (size.width * shareScale).roundToInt().coerceAtLeast(1),
                                            (size.height * shareScale).roundToInt().coerceAtLeast(1),
                                        ),
                                ) {
                                    scale(shareScale) { this@drawWithContent.drawContent() }
                                }
                                // Drawn as well as recorded: the node on screen is the
                                // preview, and the layer is what gets exported.
                                this@drawWithContent.drawContent()
                            },
                ) {
                    ShareCard(
                        showLyrics = mode == ShareCardMode.LYRICS && hasLyrics,
                        light = tone == ShareCardTone.LIGHT,
                        accent = theme,
                        artworkUrl = artworkUrl,
                        title = mediaMetadata.title,
                        artists = mediaMetadata.artists.joinToString { it.name },
                        position = position,
                        duration = duration,
                        lyricLines = lyricLines,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.card_theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShareCardThemes.forEachIndexed { index, color ->
                        val selected = index == themeIndex
                        Box(
                            modifier =
                                Modifier
                                    .size(if (selected) 26.dp else 20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).clickable { themeIndex = index },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_button))
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            coroutineScope.launch {
                                val bitmap = cardBitmap()
                                val saved =
                                    bitmap?.let {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                ComposeToImage.saveBitmapAsFile(
                                                    context = context,
                                                    bitmap = it,
                                                    fileName = shareCardFileName(),
                                                )
                                            }.getOrNull()
                                        }
                                    }
                                busy = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (saved != null) R.string.card_saved else R.string.error_unknown,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            coroutineScope.launch {
                                val bitmap = cardBitmap()
                                val uri =
                                    bitmap?.let {
                                        withContext(Dispatchers.IO) { writeShareCardToCache(context, it) }
                                    }
                                busy = false
                                if (uri == null) {
                                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(
                                        Intent.createChooser(intent, context.getString(R.string.share_card)),
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share))
                    }
                }
            }
        }
    }
}

/**
 * One-tap export of the player card. Portrait only, blurred artwork backdrop,
 * the hardcoded immersive player drawn into it - the same look the card dialog
 * uses, but without any options to pick first. The bitmap is built and handed
 * straight to the share sheet.
 */
fun sharePlayerCard(
    context: Context,
    scope: CoroutineScope,
    mediaMetadata: MediaMetadata?,
    artworkUrl: String?,
    position: Long,
    duration: Long,
) {
    val metadata = mediaMetadata ?: return
    scope.launch {
        val bitmap =
            runCatching {
                ComposeToImage.createPlayerCardImage(
                    context = context,
                    coverArtUrl = artworkUrl,
                    songTitle = metadata.title,
                    artistName = metadata.artists.joinToString { it.name },
                    positionMs = position,
                    durationMs = duration,
                )
            }.getOrNull()
        val uri =
            bitmap?.let {
                withContext(Dispatchers.IO) { writeShareCardToCache(context, it) }
            }
        if (uri == null) {
            Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
            return@launch
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_card)))
    }
}

private const val ShareCardAspect = 0.8f

/**
 * The card itself.
 *
 * Deliberately the Immersive (V7) player: the cover, blurred wide behind a tinted
 * scrim, the artwork on top of it and the words under that. Nothing here reads the
 * user's chosen player style - this is one look, fixed, while the feature is new.
 */
@Composable
private fun ShareCard(
    showLyrics: Boolean,
    light: Boolean,
    accent: Color,
    artworkUrl: String?,
    title: String,
    artists: String,
    position: Long,
    duration: Long,
    lyricLines: List<String>,
    modifier: Modifier = Modifier,
) {
    val onCard = if (light) Color(0xFF1B1519) else Color.White
    val secondaryOnCard = onCard.copy(alpha = 0.72f)
    val scrimBase = if (light) Color(0xFFF8F0EB) else Color(0xFF14100F)
    val backdropAlpha = if (light) 0.34f else 0.62f

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(scrimBase),
    ) {
        if (artworkUrl != null) {
            BlurredArtwork(
                model = artworkUrl.highRes(),
                radius = 48.dp,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            alpha = backdropAlpha
                        },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to accent.copy(alpha = if (light) 0.34f else 0.26f),
                            0.55f to scrimBase.copy(alpha = if (light) 0.9f else 0.82f),
                            1f to scrimBase.copy(alpha = if (light) 0.98f else 0.96f),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryOnCard,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent.copy(alpha = 0.24f)),
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl.highRes(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (!showLyrics) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (artists.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryOnCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                val fraction =
                    if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(accent),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = shareCardTime(position),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryOnCard,
                    )
                    Text(
                        text = shareCardTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryOnCard,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    lyricLines.take(4).forEachIndexed { index, line ->
                        Text(
                            text = line,
                            style =
                                if (index == 0) {
                                    MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                                },
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) onCard else secondaryOnCard,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (artists.isBlank()) title else "$title · $artists",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryOnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryOnCard.copy(alpha = 0.7f),
            )
        }
    }
}

private fun shareCardTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * The lines to put on a lyric card: the one that is playing, then the ones after it.
 *
 * Timestamps are dropped, blank lines skipped, and when the playing line is not known
 * the card starts at the top of the lyrics instead of guessing.
 */
private fun shareCardLyricLines(
    lyrics: String?,
    currentLine: String?,
): List<String> {
    val raw = lyrics?.takeIf { it.isNotBlank() } ?: return emptyList()
    val timestamp = Regex("""^\[\d{1,2}:\d{1,2}([.:]\d{1,3})?\]""")
    val cleaned = { text: String -> text.replace(timestamp, "").trim() }
    val lines = raw.lineSequence().map(cleaned).filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return emptyList()
    val startIndex =
        currentLine
            ?.let(cleaned)
            ?.takeIf { it.isNotBlank() }
            ?.let { needle -> lines.indexOfFirst { it.equals(needle, ignoreCase = true) } }
            ?.takeIf { it >= 0 }
            ?: 0
    return lines.drop(startIndex)
}

private fun shareCardFileName(): String = "LunarTune-card-${System.currentTimeMillis()}"

private fun writeShareCardToCache(
    context: Context,
    bitmap: Bitmap,
): Uri? =
    runCatching {
        val directory = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(directory, "${shareCardFileName()}.png")
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }.getOrNull()
